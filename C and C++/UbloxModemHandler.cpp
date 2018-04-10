/******************************************************************************
*
* File name:	UbloxHandler.c
* Description:	This file contains most of the logic related to managing the
*				modem state, including sending and receiving GPRS and SMS
*				messages.
*
******************************************************************************/

#include "ubloxModemHandler.h"
#include "Commands.h"
#include "Handling_Parameters.h"
#include "pwrmgr.h"
#include "PhoneNumbers.h"

#if defined (__USE_MODEM)

#include "gendefs.h"
#include "ublgsm.h"
#include "io.h"
#include "LegacyMain.h"
#include "AudioCallMgr.h"
#include "rtcgen.h"
#include "pdu.h"
#include "systmr.h"
#include "SysStatus.h"
#include "FlashMgr.h"
#include "eepaddr.h"
#include "SystemTime.h"
#include "Version.h"
#include "AudioCallMgr.h"
#include "utils.h"
#include "MessagingUtilities.h"
#include "IncomingMsg.h"

#include "fields.h"
#include "atcmd.h"
#include "gsm/gsmUART.h"
#include "GmLanDiag32Dtc.h"
#include "UbloxAudioFile.h"
#include "UbloxSslConfiguration.h"
#include "EventRepository.h"
#include "UbloxFtpDownload2FileSystem.h"
#if defined (__USE_P2P)
#include "p2p/P2PSingleton.h"
#endif
#include "UbloxSaveCertificate.h"
#include "UbloxModemFirmwareUpdateOTA.h"
#include "PeriodicTraceMessages.h"
#include <iterator>
#include <string.h>
#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include <cstdlib>
#include <time.h>


#define DTC_InitModemAntennaCounters(s) GD_DTC_InitModemAntennaCounters(s)
#if ( __USE_FORTEMEDIA != 0 )
#define fmm_fmmTaskEndded() fmmTaskEndded()
#define fmm_fmmInitGsmProfile() fmmInitGsmProfile()
#else
#define fmm_fmmTaskEndded() do {} while( 0 );
#define fmm_fmmInitGsmProfile() do {} while( 0 );
#endif




extern SATC UBL_ATC;

/******************************************************************************
* Prototypes
******************************************************************************/

static void UbloxHandler_ClearAllCalls( void );
static EATC_Result UblocHandler_CmdSequenceResponseFlowControlSetCallback(u16 id, u8* data, u16 length);
static EATC_Result UbloxHandler_CmdSequenceResponseCallback(u16 id, u8* data, u16 length);
static void UbloxHandler_CmdSequenceNexts( void );
static void UbloxHandler_CmdSequenceStart( EMDM_State InitialState, const EUBL_Command *CmdSequence, pfCmdSeqExecuteNextCmd pfExecuteNextCmd );
static bool UbloxHandler_CmdSequenceIfDoneNextState( EMDM_State NextState );
static void UbloxHandler_CmdSeqSendFail( void );

bool IsModemInPoweredDownProc( void );
void ResetModemOnMultipleErrorHandler( void );
void UbloxHandler_ResetMultipleErrorCounters( void );

#define ModemGetPowerState()        IO_Get(DIO_Modem_1_8v_On)
#define ModemGetClearToSendState()  IO_Get(DIO_ModemCts)

static bool ReInitBeforSleep = false;
static EResult_MsgStatus_Modem Mdm_MesageStatus;
static SACM_CallInfo TempCall[MAX_CALLES_HANDLED];
static SMDM_Requests Sreq;

static EATC_Result UbloxHandler_CmdSequenceResponseFlowControlSetCallback(u16 id, u8* data, u16 length);
static void UbloxHandler_UpdateSIMStatus ( EMDM_SimStatus status );
static void UbloxOnlineCellStatus(void);
bool UbloxOnlineExternalAntennaMonitor(void);
bool UbloxOnlineMDMJammingMonitor(void);
bool UbloxHandler_FindSocketDestination( u16* SocketDest, u16 SocketId ) ;
void UbloxHandler_SetTrafficDownloadError( u16 error );
void UbloxHandler_SetTrafficDownloadState( EMDM_GprsTrafficDownload state );
void TrafficDownloadHandler( void );
bool UbloxHandler_AreGprsDestinationsSame(void);

bool IsVoiceAndDataCommAllowed(void);

#endif
/******************************************************************************
* External variables
******************************************************************************/
SMDM Smdm;

#if defined (__USE_MODEM)
s8 ConvertBuff[ SMS_MAX_SIZE + 2 ];

#define MDM_SIM_SAMPLE_TIME				SEC2MSEC(60)
#define MDM_SIM_MAX_RETRY_CMDS			2
#define MDM_ANTENNA_MAX_RETRY_CMDS		3
#define MDM_ANTENNA_SAMPLE_TIME 		SEC2MSEC(5)
#define MDM_CELSTATUS_MAX_RETRY_CMDS	3
#define PSD_PROFILE_IDENTIFIER			0
#define MDM_GPRS_ACTIVATION_TIMEOUT		SEC2MSEC(MIN2SEC(1))
#define MDM_IP_ADDRESS_POLLING_TIMEOUT  SEC2MSEC(10)

static struct
{
	u32	Data;
	u32	Timer;
	u16	SeqNum;
	u8	ErrorRetryCount;
	EMDM_CheckSIM Sm;
}SModemCheckSIM = {0, 0, 0, Init_SIM_Monitoring};

static struct
{
	u32 Timer;
	u16 CmdId;
	u16 SeqNum;
	s8 	Data;
	u8	ErrorRetryCount;
	bool ExternalAntennaUsed;
	EMDM_AntennaMonitorState Sm;
}SModemAntenna = {0, UBL_CMD_GetAntennaStatus, 0, 0, 0, EMDMAMS_Idle};

static struct
{
	bool LastConfig;
	u16 SeqNum;
	EMDM_JammingEnaMonitorState Sm;
}SJammingEnaMon = {false, 0, EMDMJMS_Idle};

static struct
{
	u32 Timer;
	u8 FrecuencyMonitor;
	u16 SeqNum;
	s8 	Data;
	u8	ErrorRetryCount;
	EMDCS_FrecuencyCellStatus Sm;
}SModemCellStatus = {0,0, 0, 0, 0, EMDCS_Idle};

static struct
{
    EMDM_CheckIPAddress Sm;
    u32 Timer;
    u16 SeqNum;
    bool isPPPConnected;
}SModemCheckIPAddress = {EMDMCIP_Idle, 0, 0, false};

static EAnt_Type LastModemAntennaType;
EAnt_Type CurrentModemAntennaType;

FATC_ResponseCallback EmergencyCallback = NULL;
FATC_ResponseCallback AssistanceCallback = NULL;

void SetAssistanceCallback(FATC_ResponseCallback callback)
{
	AssistanceCallback = callback;
}

void SetEmergencyCallback(FATC_ResponseCallback callback)
{
	EmergencyCallback = callback;
}

//Internal use
bool  IsMdmHwPowerStateOn( void ){
	return	(ModemGetPowerState() == set)? true:false;
}
bool IsMdmHwSleep( void ){
	return	(ModemGetClearToSendState() == reset)?	true:false;
}

#define UblMdmPowerUp()								UBL_ModemPowerUp()

//system parameters interface
#define UblGetSimLockPIN1()							SParameters.SystemParameters_SIMCardPINCode
#define UblMdmGetApnPtr()							SParameters.MessageLayer_GprsApn//"internet"
#define UblMdmGetUserNamePtr()						SParameters.MessageLayer_ApnUserName//"apnuser"
#define UblMdmGetPasswordPtr()						SParameters.MessageLayer_ApnPassword//"apnpass"
#define UblMdmGetDestination1Ptr()					SParameters.MessageLayer_GPRSDestination1
#define UblMdmGetDestination2Ptr()					SParameters.MessageLayer_GPRSDestination2

//#define CiMdmGetBOOpenTimeOut()						SEC2MSEC(MIN2SEC(SParameters.CallerIDcommand_SocketOpenDurationAfterConnectionEstablishment))
//#define CiMdmGetBOOpenNoTrafficTimeOut()			SEC2MSEC(MIN2SEC(SParameters.CallerIDcommand_SocketOpenTimerAfterTraffic))

//#define CiMdmGetFLOpenTimeOut()						CiMdmGetBOOpenTimeOut()
//#define CiMdmGetFLOpenNoTrafficTimeOut()			CiMdmGetBOOpenNoTrafficTimeOut()

#define GetSendStMsgTimeOut()						SEC2MSEC(SParameters.MessageLayer_WaitTimeBeforeNextRetry)
#define GetSendMsgStRetryCount()					SParameters.MessageLayer_NumberOfRetries
#define GetSendEmgMsgTimeOut()						SEC2MSEC(SParameters.MessageLayer_TopPriorityGPRSConnectionTimeout)
#define GetSendMsgEmgRetryCount()					MDM_MAX_GPRS_EMERGENCY_RETRY
#define DTC_CheckModemAntenna(s)                    GD_DTC_CheckModemAntenna(s)

#define SMS_SEND_TIMEOUT							SEC2MSEC(MIN2SEC(3))
#define SOCKET_CONNECTION_TIMEOUT					SEC2MSEC(MIN2SEC(3))

#define MODEM_AUDIO_CFG_MAGIC_NUMBER	201



static u32 MdmAudioCfgMagic;
static bool PendingSamuCallOnFailure = false;
static struct {
	EMDM_RingToneSelectionState sm;
	EUBL_Command CmdId;
	u16 CmdSeqnum;
	u32 Timeout;
	u8	RetryCount;
}RingToneSelection = {EMDMRTS_Idle, UBL_CMD_None, 0, 0, 0};

//caller ID

struct{
    char DialNumber[PHONE_MAX_SIZE];
    EMSGS_MessageTypeWithPhone ty;
}SMDM_OutMsgDialInfo;


//SMS dest number
#define UblGetSmsDestinationNumber()					SParameters.SystemParameters_BackOfficeSmsNumber//		"+972543262952"//SParameters.


bool UbloxHandler_IsSocketsAreClosed( void );

//Incoming messages module interface
#define MdmNewGprsDataArrived_Dest2(ptr,s)			MSG_IncomingMsgs_Run(Gprs2,ptr, s, 0, false)
#define MdmNewGprsDataArrived_Dest1(ptr,s)			MSG_IncomingMsgs_Run(Gprs1,ptr, s, 0, false)
//#define MdmNewGprsDataArrived_ACP(ptr,s)			ACP_IncomingMsgs(ptr,s)

#if ( __USE_AUDIO != 0 )
#define Mdm_AcmUpdateCallState()					ACM_UpdateCallState(EACMCER_CALL_STAT_CHANGED, Smdm.Call)
#else
#define Mdm_AcmUpdateCallState()					do{} while ( false );
#endif

#define MdmGetLastGprsSendError()					Sreq.CurrentMsgMgrReq.Out.Scheme.s.Sms


char Ublox_GprsDestinationName[50];
char Ublox_GprsDestinationPort[10];

//command set to disable urc before sleep and sleep command
const EUBL_Command UbloxMdmSetSleepCfg[] = {
	UBL_CMD_SetNewSmsIndicator,
	UBL_CMD_CustomizePLMNScan,
	UBL_CMD_CustomizePLMNScanEnableDisable,
	UBL_CMD_MobileEquipmentEventReportingDis,
	UBL_CMD_SetGsmStatUrcDis,
	UBL_CMD_SetGprsStatUrcDis,
	UBL_CMD_FlowControl,
    UBL_CMD_SetUartPowerSavingEnabled,
	UBL_CMD_None,
};

//command set to enter in PowerDown mode in case we are attached to GPRS
//in this case the PDP context should be deactivated before enter to sleep
const EUBL_Command UbloxMdmSetPwrDownCfg1[] = {
	UBL_CMD_SetUartPowerSavingDisabled,
	UBL_CMD_SetGeneralPDPContextAction,		//This command is used to deactivate all PDP context defined
	UBL_CMD_PowerOff,
	UBL_CMD_None,
};

//command set to enter in PowerDown mode in case we are detached to GPRS
const EUBL_Command UbloxMdmSetPwrDownCfg2[] = {
	UBL_CMD_SetUartPowerSavingDisabled,
	UBL_CMD_PowerOff,
	UBL_CMD_None,
};


//command set after wake up enable events and regisretion Urc
const EUBL_Command UbloxMdmSetWakeUpCfg[] = {
	UBL_CMD_SetUartPowerSavingDisabled,
	UBL_CMD_FlowControl,
	UBL_CMD_CustomizePLMNScanEnableDisable,
	UBL_CMD_SetNewSmsIndicator,
	UBL_CMD_MobileEquipmentEventReporting,
    //UBL_CMD_CLCCExecCommand,
	UBL_CMD_SetGsmStatUrc,
	UBL_CMD_SetGprsStatUrc,
	UBL_CMD_SetFullFunctionality,
	UBL_CMD_None,
};

//comand set for basic init
const EUBL_Command UbloxMdmSetGetMdmBasic[] = {
	UBL_CMD_FlowControl,
	UBL_CMD_SetUartPowerSavingDisabled,
	UBL_CMD_SetEchoModeOff,
	UBL_CMD_SetGreetingText,
	UBL_CMD_GetSimUrcConfig,
	UBL_CMD_GetFirmwareVersion,
	UBL_CMD_GetProductTypeNumber,
	UBL_CMD_SetGsmStatUrc,
	UBL_CMD_SetGprsStatUrc,
	UBL_CMD_MessageWaitingIndication,
	UBL_CMD_SetNewSmsIndicator,
	UBL_CMD_CallStatusReportEnable,
	UBL_CMD_EnableSmartTemperatureIndication,
	UBL_CMD_DefaultRingToneDisable,
	UBL_CMD_JammingDetectionDis,
	UBL_CMD_None,
};

//comand set for G basic init
const EUBL_Command UbloxMdmSetGetMdmBasicG[] = {
	UBL_CMD_GetIMEI,
	UBL_CMD_GetICCID,
	UBL_CMD_GetIMSI,
	UBL_CMD_GetOwnNumber,
	UBL_CMD_SetErrorNumericFormat,
	UBL_CMD_UrcIndicationViaRingLine,
	UBL_CMD_SetDtmfToneDuration,
	UBL_CMD_RegisterIndicatorsG,
	UBL_CMD_MobileEquipmentEventReporting,
	UBL_CMD_None,
};


//comand set for audio settings and phone
const EUBL_Command UbloxMdmAudioHwConfiguration[] = {
    UBL_CMD_SetAudioNullPath,
    UBL_CMD_SetI2SInterface,
    UBL_CMD_SetAudioPath0,
    UBL_CMD_SetMicrophoneGain,
    UBL_CMD_SetSpeakerGain,
    UBL_CMD_SideToneConfig,
    UBL_CMD_StoreConfigurationToRAM,
	UBL_CMD_None,
};

/*
Whith the following configuration in the filter coefficients we make that the H(z) = 1 (No Filtering)
	Uplink: 	AT+UUBF=AudioPath,FilterNumber,0,0,0,0,32767
	Downlink: 	AT+UDBF=AudioPath,FilterNumber,0,0,0,0,32767
*/
const SMDM_BiquadFilter UbloxFilterSetup[] = {
	{ 1,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (8KHz) - Uplink Filters
	{ 2,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (8KHz)
	{ 3,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (8KHz)
	{ 4,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (8KHz)
	{ 5,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (16KHz)
	{ 6,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (16KHz)
	{ 7,	0,	0,	0,	0,  32767, },			//Filter related with I2S sample rate (16KHz)
	{ 8,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (16KHz)
	{ 1,	0,	0,	0,	0,	32767, },			//Filter related with I2S sample rate (8KHz) - Downlink Filters
	{ 2,	0,	0,	0,	0, 	32767, },			//Filter related with I2S sample rate (8KHz)
	{ 3,	0,	0,	0,	0, 	32767, },			//Filter related with I2S sample rate (8KHz)
	{ 4,	0,	0,	0,	0, 	32767, },			//Filter related with I2S sample rate (8KHz)
	{ 5,	0,	0,	0,	0, 	32767, },			//Filter related with I2S sample rate (16KHz)
	{ 6,	0,	0,	0,	0, 	32767, },			//Filter related with I2S sample rate (16KHz)
	{ 7,	0,	0,	0,	0, 	32767, },			//Filter related with I2S sample rate (16KHz)
	{ 8,	0,	0,	0,	0,  32767, },			//Filter related with I2S sample rate (16KHz)
};

const EUBL_Command UbloxMdmFilterConfiguration[] = {
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_UplinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_DownlinkDigitalFilterConf,
    UBL_CMD_HandsFreeParametersConf,
    UBL_CMD_StoreConfigurationToRAM,
	UBL_CMD_None,
};


//command set for sms configuretion
const EUBL_Command UbloxMdmSmsConfiguration[] = {
	UBL_CMD_SetSmsFormatToPdu,
	UBL_CMD_SetPreferredSmsMessageStorage,
	UBL_CMD_SetSmsSendIndicationUrc,
	UBL_CMD_None,
};

//command data for tcp ip config
const SMDM_InternetSetup UbloxConnectionSetup[] = {
		{ 1,	(const char*)UblMdmGetApnPtr() },
		{ 2, 	(const char*)UblMdmGetUserNamePtr() },
		{ 3,    (const char*)UblMdmGetPasswordPtr() },
};

//command set for internal tcp/ip stack config
const EUBL_Command UbloxMdmGprsConnectionConfiguration[] = {
	UBL_CMD_SetPacketSwitchedDataConfig,
	UBL_CMD_SetPacketSwitchedDataConfig,
	UBL_CMD_SetPacketSwitchedDataConfig,
	UBL_CMD_SetupDynamicIpAddressAssignment,
	UBL_CMD_None,
};

//command set for get registretion status of gprs and gsm
const EUBL_Command UbloxMdmRegistrationStat[] = {
	UBL_CMD_GetNetworkStatus,
	UBL_CMD_GetGprsNetworkStatus,
	UBL_CMD_None,
};

//command set for query call waiting configuration
const EUBL_Command UbloxQueryCallWaitingCfg[] = {
	UBL_CMD_QueryCallWaitingConfig,
	UBL_CMD_None,
};
//command set for enable waiting call
const EUBL_Command UbloxSetCallWaitingCfg[] = {
	UBL_CMD_SetCallWaitingConfig,
	UBL_CMD_None,
};

static const char* const MODEM_MSG_STATUS[] =
{
	"StillSending",
	"LastMsgSend_Ok",
	"LastMsgSend_Error",
    "MessageAborted",
	"IdleToSend",
	"NoCoverage",
};
//set the sending status
void UbloxHandler_SetModemSendingStatus( EResult_MsgStatus_Modem stat )
{
	Log(LOG_SUB_MDM,LOG_LVL_TRACE,"msg status %s => %s\n", MODEM_MSG_STATUS[Mdm_MesageStatus], MODEM_MSG_STATUS[stat]);

	Mdm_MesageStatus = stat;
}

//interface for outging handler to get the sending status
EResult_MsgStatus_Modem UbloxHandler_GetModemSendingStatus( bool justRead )
{
	EResult_MsgStatus_Modem st =  Mdm_MesageStatus;
	if(!justRead &&( st ==  Modem_LastMsgSend_Ok ||
        st == Modem_LastMsgSend_Error ||
        st == Modem_NoCoverage ||
        st == Modem_MessageAborted))
	{
		UbloxHandler_SetModemSendingStatus(Modem_IdleToSend);
	}

	return st;
}


char* Ublox_GetGprsDestinationNamePtr( char * parm )
{
	char* pTmp;
	u16 Length;

	//SParameters.MessageLayer_GPRSDestination1 = "DestinationName1:Port1"

	BZERO(Ublox_GprsDestinationName);
	pTmp = strstr(parm , ":");
	if( pTmp != NULL ) {
		Length = pTmp - parm;
		memcpy( Ublox_GprsDestinationName,
				 parm,
				 MIN(Length, sizeof(Ublox_GprsDestinationName)-1) );
	}
	else{
		strncpy( Ublox_GprsDestinationName,
			 parm,
			 (sizeof(Ublox_GprsDestinationName)-1) );
	}

    for ( u8 i = strlen( Ublox_GprsDestinationName ); i && Ublox_GprsDestinationName[i - 1] == ' '; i-- ) {
        Ublox_GprsDestinationName[i-1] = '\0';
    }

	return (char*)Ublox_GprsDestinationName;
}

char* Ublox_GetGprsDestinationPortPtr( char * parm )
{
	char* pTmp;

	//SParameters.MessageLayer_GPRSDestination1 = "DestinationName1:Port1"

	BZERO(Ublox_GprsDestinationPort);
	pTmp = strstr(parm , ":");
	if( pTmp != NULL ) {
		pTmp++;
		strncpy( Ublox_GprsDestinationPort,
				 pTmp,
				 sizeof( Ublox_GprsDestinationPort ) -1 );
	}
	else{
		strncpy( Ublox_GprsDestinationPort,
				 "443",
				 (sizeof( Ublox_GprsDestinationPort )-1) );
	}
	Ublox_GprsDestinationPort[sizeof( Ublox_GprsDestinationPort )-1] = '\0';

    for ( u8 i = strlen( Ublox_GprsDestinationName ); i && Ublox_GprsDestinationName[i - 1] == ' '; i-- ) {
        Ublox_GprsDestinationName[i-1] = '\0';
    }

	return (char*)Ublox_GprsDestinationPort;
}


//Interface Api
//enable autoanswer interface
void SetAutoAnswerState( FunctionalState state )
{
	Smdm.AutoAnswer = state;
}

bool IsSysActivePowerMode( ESYS_PowerModes Pm )
{
	return  (Pm == ESYSPM_FullPower || Pm == ESYSPM_StandBy ? true:false);
}

static const char* const MODEM_STATE[] =
{
	"Undefined",
	"Wait Init End",
	"Start Config Basic",
	"Config Basic",
	"Config Basic G",
	"Config Basic G Done",
    "Start RingTone Config",
	"Config Antenna Hw",
	"Start Config Audio Hw",
	"Wait SIM Lock",
	"Config Audio Hw",
	"Start Config Audio Filters",
    "Config Audio Filters",
	"Start Config Sms",
	"Config Sms",
	"Start Config Gprs Connection",
	"Config Gprs Connection",
	"Start Config Indications Events",
	"Config Indications Events",
	"Start Check Network Registration",
	"Check Network Registration",
	"Start Get Call Waiting Configuration",
	"Get Call Waiting Configuration",
	"Start Set Call Waiting Configuration",
	"Set Call Waiting Configuration",
	"Config Error",
	"Config Error with PwrReset",
	"Start Main Mdm Tasks",
	"Main Mdm Task",
	"StartAirplaneMode",
	"StopAirplaneMode",
	"WaitEnterAirplaneMode",
	"WaitExitAirplaneMode",
	"AirplaneMode",
	"DeregisterFromNetwork",
	"DeregisterFromNetwork_Wait",
	"OpSelectionAutomatic",
	"OpSelectionAutomatic_Wait",
    "AbortSelectionOperation",
	"StartSleep",
	"SleepConfig",
	"WaitSleep",
	"Sleep",
	"StartPowerDown",
	"CheckGprsAttachState",
	"StartPowerDownConfig",
	"WaitPowerDownConfig",
	"WaitPowerDown",
	"FinalizePowerDown",
	"PowerDown",
	"StartWakeUp",
	"WaitWakeUp",
	"WakeUpDelay",
};

EMDM_State UbloxHandler_GetMdmState( void )
{
	return Smdm.HandlersState.State;
}

const char* GetMdmStatus( void )
{
	if( Smdm.HandlersState.State < elementsof(MODEM_STATE) )
	{
		return MODEM_STATE[Smdm.HandlersState.State];
	}
	return "?";
}

void UbloxHandler_SetMdmState( EMDM_State state ){

	if( Smdm.HandlersState.State != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"MAIN %s -> %s\n", MODEM_STATE[Smdm.HandlersState.State], MODEM_STATE[state]);
	}

	Smdm.HandlersState.State = state;
	if(EMDMS_ConfigError == state)
		return;
}

EMDM_CallState UbloxHandler_GetMdmCallState( void )
{
	return Smdm.HandlersState.CallState;
}

static const char* const MODEM_CALL_STATE[] =
{
	"CallIdle",
	"SentCallAnswer",
	"WaitCallAnswer",
	"SendCallReject",
	"WaitCallReject",
	"SendCall_HangUp",
	"WaitCall_HangUp",
	"SendCallHold",
	"WaitCallHold",
	"SendCallMerge",
	"WaitCallMerge",
	"SendAll_HangUp",
	"WaitAll_HangUp",
	"SendDial",
	"WaitDial",
	"WaitCallEstablished",
	"SendDtmf",
	"WaitDtmf",
	"ActiveCall",
	"CallError",
};

void UbloxHandler_SetMdmCallState( EMDM_CallState state )
{
	if( Smdm.HandlersState.CallState != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"CALL %s -> %s\n", MODEM_CALL_STATE[Smdm.HandlersState.CallState], MODEM_CALL_STATE[state]);
	}
    if(Smdm.HandlersState.CallState == EMDMCS_WaitDial && state != EMDMCS_WaitCallEstablished) {
        UBL_DisposeCommandWithSeqNum(&Smdm.SeqNum.CallSeqNum);
    }
	Smdm.HandlersState.CallState = state;
}

bool Get_F_MdmGprsReadError( void )
{
	return Smdm.GprsReadError;
}

void Set_F_MdmGprsReadError( bool bValue )
{
	Smdm.GprsReadError = bValue;
}

EMDM_GprsReceiveState UbloxHandler_GetMdmGprsReceiveState( void )
{
	return Smdm.HandlersState.GprsReceiveState;
}

static const char* const MODEM_GPRSRCV_STATE[] =
{
	"GprsDataIdle",
	"CriticalSectionStart",
	"StartReadGprsData",
	"WaitReadGprsData",
	"CriticalSectionEnd",
	"GprsDataAvailable",
};

void UbloxHandler_SetMdmGprsReceiveState( EMDM_GprsReceiveState state )
{
	if( Smdm.HandlersState.GprsReceiveState != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"GPRS RCV %s -> %s\n", MODEM_GPRSRCV_STATE[Smdm.HandlersState.GprsReceiveState], MODEM_GPRSRCV_STATE[state]);
	}
	Smdm.HandlersState.GprsReceiveState = state;
}

EMDM_GprsSendState UbloxHandler_GetMdmGprsSendState( void )
{
	return Smdm.HandlersState.GprsSendState;
}

static const char* const MODEM_GPRSSND_STATE[] =
{
	"GprsDataIdle",
	"CheckGprsRegStatus",
	"CheckGprsAttachStatus",
    "WaitCheckGprsAttachStatus",
	"StartGprsAttach",
	"WaitGprsAttach",
	"StartCheckConnectionStatus",
    "WaitCheckConnectionStatus",
    "StartConnectionDeactivation",
    "WaitConnectionDeactivation",
	"StartConnectionActivation",
	"WaitConnectionActivation",
	"CreateSocket",
	"WaitCreateSocket",
	"StartCheckSocketService",
	"WaitCheckSocketService",
	"WaitStartHttpSslConfiguretion",
	"HttpSslConfiguretion",
	"StartOpenSocketService",
	"WaitOpenSocketService",
	"WaitOpenSocketConnection",
	"CriticalSectionStart",
	"StartSendGprsData",
	"WaitSendGprsData",
	"CriticalSectionEnd",
	"SendGprsOk",
	"SendAckOk",
	"SendGprsError",
	"NetGprsError",
	"CloseCurrentService",
	"StartGprsCloseService",
	"WaitGprsCloseService",
	"StartGprsDetach",
	"WaitGprsDetach",
	"StartGprsCloseConnection",
	"WaitGprsCloseConnection",
	"WaitNetworkRelaxTime",
};

void UbloxHandler_SetMdmGprsSendState( EMDM_GprsSendState state )
{
	if( Smdm.HandlersState.GprsSendState != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"GPRS SND %s -> %s\n", MODEM_GPRSSND_STATE[Smdm.HandlersState.GprsSendState], MODEM_GPRSSND_STATE[state]);
	}
	// Keep track of gprs errors when switching between networks. Too many may mean that modem gprs is stuck.
	// Only relevant if we are registered, or registration is denied.
	if( ( Smdm.NetStat.NewReg ) &&
		( state == EMDMGSS_NetGprsError ) &&
		( ( Smdm.NetStat.Gprs.NetStat == EMDMNS_RegHome ) || ( Smdm.NetStat.Gprs.NetStat == EMDMNS_RegRoam ) || ( Smdm.NetStat.Gprs.NetStat == EMDMNS_RegDenied ) ) )
	{
		Smdm.ErrCount.SendGprsErrorCounter++;
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"gprs send errors = %d, gsmreg = %d, gprsreg = %d\n",
					Smdm.ErrCount.SendGprsErrorCounter,
					Smdm.NetStat.Gsm.NetStat,
					Smdm.NetStat.Gprs.NetStat );
	}
	else if( state == EMDMGSS_SendAckOk )
	{
		// If GPRS is ok, don't need to track gprs errors
		Smdm.ErrCount.SendGprsErrorCounter = 0;
		Smdm.NetStat.NewReg = false;
	}
	else if( IsNoGprsReg() || IsNoGsmReg() )
	{
		// Error counter is only interesting when we are registered
		Smdm.ErrCount.SendGprsErrorCounter = 0;
	}
	Smdm.HandlersState.GprsSendState = state;
}

EMDM_GprsHttpDownload UbloxHandler_GetHttpDownloadState( void )
{
	return Smdm.HandlersState.GprsHttpDownload;
}

static const char* const MODEM_HTTP_DNLD_STATE[] =
{
	"Idle",
	"InitHttpService",
	"HttpCreateSocket",
	"WaitCreateSocket",
	"HttpRequestInfo",
	"HttpRequestInfoWait",
	"WaitWhileReading",
	"WaitWhileProcessingFrame",
	"ProcessingFrameFinished",
	"NewFrameReceived",
	"GprsDownloadEnded",
	"Error",
};

const char* UbloxHandler_GetHttpDownloadStatus( void )
{
	return MODEM_HTTP_DNLD_STATE[ UbloxHandler_GetHttpDownloadState() ];
}

const char* Mdm_GetGprsSendStatus( void )
{
	return MODEM_GPRSSND_STATE[ UbloxHandler_GetMdmGprsSendState() ];
}


void UbloxHandler_SetHttpDownloadState( EMDM_GprsHttpDownload state )
{
	if( Smdm.HandlersState.GprsHttpDownload != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"HTTP DLD %s -> %s\n", MODEM_HTTP_DNLD_STATE[Smdm.HandlersState.GprsHttpDownload], MODEM_HTTP_DNLD_STATE[state]);
	}

	if( state == EMDMGHD_NewFrameReceived )
	{
		if( Smdm.HandlersState.GprsHttpDownload != EMDMGHD_WaitWhileReading )
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "Unexpected change from state %d to NewFrameReceived\n", Smdm.HandlersState.GprsHttpDownload );
			if( Smdm.Gprs[EMDMDSD_Http].ServiceState != EMDMSS_SocketClosing )
			{
				Log(LOG_SUB_MDM,LOG_LVL_WARN, "State changed denied\n" );
				Smdm.Gprs[EMDMDSD_Http].RxDataToRead = 0;
				Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length = 0;
				return;
			}
		}
	}
	Smdm.HandlersState.GprsHttpDownload = state;
	if( state == EMDMGHD_Idle )
	{
		Smdm.Ota.Error = EMDMGHDE_None;
		Smdm.Ota.FrameRetryCount = 0;
		Smdm.Ota.DownloadRetryCount = 0;
		Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length = 0;
		Smdm.Gprs[EMDMDSD_Http].RxDataToRead = 0;
	}else if( state == EMDMGHD_GprsDownloadEnded || state == EMDMGHD_Error)
    {
		if(Smdm.Ota.finishCallback != nullptr){
			Smdm.Ota.finishCallback(state , (const u8*)Smdm.Ota.LastModified,(u32)Smdm.Ota.ContentLength);
			Smdm.Ota.finishCallback = nullptr;
		}
    }
}

void UbloxHandler_CloseSocket( EMDM_DestSocketDef sock )
{
	if(Smdm.Gprs[sock].SocketAssigned != EMDMDSD_Undefined){
	// Close socket, set state undefined
		UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[sock].SocketAssigned);
	    Smdm.Gprs[sock].SocketAssigned = EMDMDSD_Undefined;
	}

	Smdm.Gprs[sock].ServiceState = EMDMSS_SocketUndefined;
	Smdm.Gprs[sock].DataReceiver.Length = 0;
	Smdm.Gprs[sock].RxDataToRead = 0;

	switch(sock)
	{
	case EMDMDSD_Http:
			Smdm.Ota.HttpConnection = false;
	break;
	case EMDMDSD_Traffic:
			Smdm.Traffic.TrafficConnection = false;
	break;
	}

}

void UbloxHandler_SetHttpDownloadError( u16 error )
{
	Log(LOG_SUB_MDM,LOG_LVL_WARN, "HttpDnld Error %d\n", error );
	// Close the download service
	UbloxHandler_SetHttpDownloadState( EMDMGHD_Error );
	UbloxHandler_CloseSocket(EMDMDSD_Http);
	Smdm.Ota.Error = (EMDM_GprsHttpDownloadError)error > EMDMGHDE_InternalErrors? (EMDM_GprsHttpDownloadError)error:EMDMGHDE_SocketError;
}

void UbloxHandler_CancelHttpDownload( void )
{
	if( Smdm.HandlersState.GprsHttpDownload != EMDMGHD_Idle )
	{
		UbloxHandler_SetHttpDownloadState( EMDMGHD_Idle );
		UbloxHandler_CloseSocket(EMDMDSD_Http);
	}
}

EMDM_GprsHttpDownloadError UbloxHandler_GetHttpDownloadError( void )
{
	return Smdm.Ota.Error;
}

EMDM_SmsReceiveState UbloxHandler_GetMdmSmsReceiveState( void )
{
	return Smdm.HandlersState.SmsReceiveState;
}

static const char* const MODEM_SMSRCV_STATE[] =
{
	"SmsIdle",
	"pullPendingSms",
	"WaitPendingSms",
	"StartReadSms",
	"WaitReadSms",
	"StartDeletSms",
	"WaitDeleteSms",
};

void UbloxHandler_SetMdmSmsReceiveState( EMDM_SmsReceiveState state )
{
	if( Smdm.HandlersState.SmsReceiveState != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"SMS RCV %s -> %s\n", MODEM_SMSRCV_STATE[Smdm.HandlersState.SmsReceiveState], MODEM_SMSRCV_STATE[state]);
	}
	Smdm.HandlersState.SmsReceiveState = state;
}


EMDM_SmsSendState UbloxHandler_GetMdmSmsSendState( void )
{
	return Smdm.HandlersState.SmsSendState;
}

static const char* const MODEM_SMSSND_STATE[] =
{
	"SmsIdle",
	"CheckGsmRegStatus",
	"BuildSms",
	"CriticalSectionStart",
	"StartSendSms",
	"WaitSendSms",
	"CriticalSectionEnd",
	"WaitSendIndication",
	"SendSmsOk",
	"SendSmsError",
	"NetSmsError",
};

void UbloxHandler_SetMdmSmsSendState( EMDM_SmsSendState state )
{
	if( Smdm.HandlersState.SmsSendState != state )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"SMS SND %s -> %s\n", MODEM_SMSSND_STATE[Smdm.HandlersState.SmsSendState], MODEM_SMSSND_STATE[state]);
	}
	Smdm.HandlersState.SmsSendState = state;
}

//interface to modem information
//get the modem firmware
char* UbloxHandler_GetMdmFwVersion( void )
{
	return Smdm.Info.SwVer;
}

//callback save the imei of the modem for app usage
EATC_Result UbloxHandler_SetMdmImei(u16 cmdid,char* data, u16 length)
{
    if( length > ATC_MSG_END_LENGTH){
      sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%" XSTR(IMEI_MAX_SIZE_STR) "s", Smdm.Info.imei);
      Log(LOG_SUB_MDM,LOG_LVL_DEBUG,"IMEI = %s\n", Smdm.Info.imei);
      Set_F_IMEI((u8*)Smdm.Info.imei);
      return ATC_Ok;
    }

return ATC_InProc    ;
}

//read the imei number from modem main struct
char* UbloxHandler_GetMdmImei( void )
{
	return Smdm.Info.imei;
}
//callback save the imsi of the sim for app usage
EATC_Result UbloxHandler_SetMdmImsi(u16 cmdid , char* data , u16 length)
{
	if( length <= ATC_MSG_END_LENGTH){
      return ATC_InProc;
	}

	if ( strncasecmp( data, "ERROR",strlen("ERROR") ) == 0)
	{
		Clear_F_IMSI();
		Log(LOG_SUB_MDM,LOG_LVL_WARN, "IMSI = ERROR\n");
	}
	else
	{
		sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%" XSTR(IMSI_MAX_SIZE_STR) "s", Smdm.Info.imsi);
		Set_F_IMSI((u8*)Smdm.Info.imsi);
		Set_F_IMSI_Valid(true);
		Log(LOG_SUB_MDM,LOG_LVL_DEBUG, "IMSI = %s\n", Smdm.Info.imsi);
	}
    return ATC_Ok;
}
//read the imsi number from modem main struct
char* UbloxHandler_GetMdmImsi( void )
{
	return Smdm.Info.imsi;
}
//callback save the iccid of the sim card for app usage
EATC_Result UbloxHandler_SetSimIccid(u16 cmdid, char* data , u16 length )
{
    if( length <= ATC_MSG_END_LENGTH){
      return ATC_InProc;
	}

	if ( strncasecmp( data, "ERROR",strlen("ERROR") ) == 0)
	{
		Clear_F_ICCID();
		Log(LOG_SUB_MDM,LOG_LVL_WARN, "ICCID = ERROR\n");
	}
	else
	{
      sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%" XSTR(ICCID_MAX_SIZE_STR) "s", Smdm.Info.iccid);
      Set_F_ICCID_Valid(Set_F_ICCID((u8*)Smdm.Info.iccid));
      Log(LOG_SUB_MDM,LOG_LVL_DEBUG, "ICCID = %s\n", Smdm.Info.iccid);
    }
    return  ATC_Ok;
}
//read iccid number from modem main struct
char* UbloxHandler_GetSimIccid(void )
{
	return Smdm.Info.iccid;
}
//callback update rssi
void UbloxHandler_UpdateReceivedSignalStrengthIndication(u16 cmdid, char* data)
{
	s16 temp;
	sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%hd", &temp);
	Smdm.NetStat.Rssi = (s8)temp;
}
//callback power down event
void MdmPowerOffEventReceived( void )
{
	if( !IsModemInPoweredDownProc() )
	{
		// Got unexpected shutdown, restart after power down
		Smdm.NeedRestart = true;
	}

	// Wait for modem to power off
	Smdm.Timer = TmrGetTime_ms();
	UbloxHandler_SetMdmState(EMDMS_WaitPowerDown);
}
//+CMGR: 0,,23/r/n
EATC_Result UpdateCmgrParams(s8* buff)
{
	char* ptr;
	u8 ReceivedStrLen = 0;
	u8 BuffIdx = 0;

	ReceivedStrLen = strlen((char*)buff);

	if((ptr = strstr((char*)buff , "+CMGR")) != NULL){
		ptr = &ptr[strlen("+CMGR: ")];
		BuffIdx += strlen("+CMGR: ");

		Smdm.sms.rx.RxData.Cmgr_State = atoi(ptr);
	 	while(BuffIdx < ReceivedStrLen)
        {
            if(*ptr++ == ','){
                break;
            }
            BuffIdx++;
        }

		if(BuffIdx >= ReceivedStrLen)
		{
			return ATC_DataError;
		}

        while(BuffIdx < ReceivedStrLen)
        {
            if(*ptr++ == ','){
                break;
            }
            BuffIdx++;
        }

		if(BuffIdx >= ReceivedStrLen)
		{
			return ATC_DataError;
		}

		Smdm.sms.rx.RxData.Cmgr_Len = atoi(ptr);
		return ATC_Ok;
	}

	return ATC_DataError;
}

//callback if our own phone namber is save to sim we will get it here.
EATC_Result UbloxHandler_MdmOwnPhoneNumber(u16 cmd, char* data, u16 length)
{
	char *pTmp = NULL;
	u8 j;

    if( length <= ATC_MSG_END_LENGTH){
      return ATC_InProc;
    }

	BZERO(Smdm.Info.OwnNum);
	pTmp = strstr((char*)data,"+CNUM: ");

	// Parse: +CNUM: "MINNUMBER","091036119",129
	if( pTmp ) {
		pTmp = strtok(pTmp,",");		// point to "+CNUM.."
		if( pTmp ) {
			pTmp = strtok(NULL,",");	// point to "091036119",
			if( pTmp ) {
				pTmp++;					// Skip first quote
				for(j = 0 ; ((j < PHONE_MAX_SIZE) && (*pTmp != '"')) ; j++)
				{
					Smdm.Info.OwnNum[j] = *pTmp++;
				}
		    }
		 }
    }

	//if not alread saved we save it to sparameters
	if(strcmp(SParameters.SystemParameters_PhoneNumber , Smdm.Info.OwnNum)) {
		SetValueParameter( 	SystemParameters_PhoneNumber,
							Smdm.Info.OwnNum,
							strlen(Smdm.Info.OwnNum),
							Msg_Mode_Normal, Normal, 0 );
	}

return ATC_Ok;
}

char* MdmGetOwnPhoneNum( void )
{
	return Smdm.Info.OwnNum;
}


//refer to the ATCommand AT+USIMSTAT
static const char* const MODEM_SIM_STATES[] =
{
	"SIM Card not present",
	"PIN Needed",
	"PIN Blocked",
	"PUK Blocked",
	"(U)SIM not operational",
	"(U)SIM in restricted use (FDN or BDN active)",
	"(U)SIM operational (registration may be initiated)",
	"SIM phonebook ready to be used (when the SIM application is active)",
	"USIM phonebook ready to be used (when the USIM application is active)",
	"(U)SIM toolkit REFRESH proactive command successfully concluded",
	"(U)SIM toolkit REFRESH proactive command unsuccessfully concluded",
};

void UbloxHandler_MdmSimStates(u16 cmdid, char* data, u16 lengt)
{
	s32 mode;
	u32 states;
	const char* szStates = "Invalid";
	sscanf( &data[strlen(UBL_Commands[cmdid].Response )], "%d,%ud", &mode, &states );
	if (states < elementsof( MODEM_SIM_STATES) ) {
		szStates = MODEM_SIM_STATES[states];
	}
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "%s\n", szStates);
}

void UbloxHandler_MdmAntennaStatus(u16 cmdid, char* data, u16 lengt)
{
    u32 antenna_id;
	u32 antenna_load;
	//Parse:    +UANTR: <antenna_id>,<antenna_load>
    //   Ex:    +UANTR: 0,10

sscanf( &data[strlen(UBL_Commands[cmdid].Response )], "%d,%d", &antenna_id, &antenna_load );
    SModemAntenna.Data = (s8)antenna_load;
}


//callback we receive all +CIEV
void UbloxHandler_MdmEquipmentEventReportingParse(u16 evtid, char* data, u16 lengt)
{
    int descr;
	int value;
	EUBL_Indication Indication;
	//Parse:	+CIEV: <descr>,<value>
    //   Ex:    +CIEV: 2,4

	sscanf( &data[strlen(UBL_Events[evtid].Event )], "%d,%d", &descr, &value );
	Indication = (EUBL_Indication)descr;

	if( Indication == EUIND_Battchg ) {
        //provides the battery charge level (0-5)
		u32 BatteryLevel;
		BatteryLevel = (u32)value;
	}
	else if( Indication == EUIND_Signal ) {
        // provides the signal quality  0: < -105 dBm 1: < -93 dBm
        //2: < -81 dBm  3: < -69 dBm 4: < - 57 dBm  5: >= -57 dBm
		if( value < 5 ) {
			//Example: If value = 0 means Rssi < -105dBm, but we use Rssi <= -106dBm which is a range equivalent
			Smdm.NetStat.Rssi = (-105 + (value * 12)) -1 ;
		}
		else {
			Smdm.NetStat.Rssi = -57;
		}
		Set_F_Active_Cell_RSSI(Smdm.NetStat.Rssi);
        Smdm.NetStat.SignalQuality = value;
	}
	else if( Indication == EUIND_Service ) {
        // provides the network service availability
        //0: not registered to the network
        //1: registered to the network

	}
    else if( Indication == EUIND_Sounder ) {
        // provides the sounder activity
        //0: no sound
        //1: sound is generated
		u32 SoundGenerated;
		SoundGenerated = (u32)value;
	}
    else if( Indication == EUIND_Message ) {
        // provides the unread message available in <mem1> storage
        //0: no messages
        //1: unread message available
		u32 MessageAvailable;
		MessageAvailable = (u32)value;
	}
    else if( Indication == EUIND_Call ) {
        // provides the call in progress
        //0: no call in progress
        //1: call in progress
		u32 CallInProgress;
		CallInProgress = (u32)value;
	}
    else if( Indication == EUIND_Roam ) {
        // provides the registration on a roaming network
        //0: not in roaming
        //1: roaming
		u32 Roaming;
		Roaming = (u32)value;
	}
    else if( Indication == EUIND_SmsFull ) {
        // provides the SMS storage status
        //0: SMS storage not full
        //1: SMS Storage full (an SMS has been rejected with the cause of SMS storage full)
		u32 SmsFull;
		SmsFull = (u32)value;
	}
    else if( Indication == EUIND_Gprs ) {
        //provides the GPRS indication status:
        //0: no GPRS available in the network
        //1: GPRS available in the network but not registered
        //2: registered to GPRS
        u32 GprsState;
		GprsState = (u32)value;
	}
    else if( Indication == EUIND_CallSetup ) {
        //provides the call set-up:
        //0: no call set-up
        //1: incoming call not accepted or rejected
        //2: outgoing call in dialing state
        //3: outgoing call in remote party alerting state
        u32 CallSetup;
		CallSetup = (u32)value;
	}
    else if( Indication == EUIND_CallHeld ) {
        //provides the call on hold:
        //0: no calls on hold
        //1: at least one call on hold
        u32 CallHeld;
		CallHeld = (u32)value;
	}
    else if( Indication == EUIND_SimInd ) {
        //provides the SIM detection
        //0: no SIM detected
        //1: SIM detected
        u32 SimIndication;
		SimIndication = (u32)value;
	}
}

void UbloxHandler_MdmOnSocketCloseEventParse(u16 evtid, char* data, u16 lengt)
{
	u16 Socket = 0;
	data[lengt] = '\0';
	u16 SocketId = 0;
	sscanf( &data[strlen(UBL_Events[evtid].Event)], "%hu", &Socket );

	if(UbloxHandler_FindSocketDestination(&SocketId , Socket)){
		Smdm.Gprs[SocketId].SocketAssigned = EMDMDSD_Undefined;
	}
}

void UbloxHandler_MdmOnSocketConnectionEventParse(u16 evtid, char* data, u16 lengt)
{
	u16 Socket = 0;
	u16 SocketError = 0;
	u16 SocketId = 0;

	//Parse:	+UUSOCO:<socket>,<socket_error>
	//	 Ex:	+UUSOCO: 2,0

	data[lengt] = '\0';
	sscanf( &data[strlen(UBL_Events[evtid].Event)], "%hu,%hu", &Socket, &SocketError );

	if(UbloxHandler_FindSocketDestination(&SocketId , Socket)){
		Smdm.Gprs[SocketId].ErrorOnSocketOperation = SocketError;
	}
}

void UbloxHandler_MdmOnJammingEventParse(u16 evtid, char* data , u16 lenght)
{
	u16 active = 0;
	data[lenght] = '\0';
	SMDMJammingStatusEvent MDMJammingStat;

	//	 Parse: +UCD: <active>
	// Example: +UCD: 3

	sscanf((char*)&data[strlen(UBL_Commands[evtid].Response)], "%hu", &active);
	Smdm.JammingStatus = (EMDM_JamDetectStatus)active;

	MDMJammingStat.MDM_Detect_Value = Smdm.JammingStatus;
	EVENTREP_UpdateRepositoryEvents(EVENTREP_MDMJammingDetection, &MDMJammingStat);

	Log(LOG_SUB_MDM, LOG_LVL_TRACE, "Modem jamming URC issued: %u\n",Smdm.JammingStatus);
}

EMDM_JamDetectStatus UbloxMDM_GetJammingStatus(void)
{
	return Smdm.JammingStatus;
}

/*******************************************************************************
NAME:           UbloxHandler_MdmOnTemperatureStatusEventParse
PARAMETERS:	    u16 evtid
				char* data
				u16 lenght
RETURN VALUES:  void
DESCRIPTION:   	Parse the information received in the +UUSTS URC and updates
				the modem tempertature status by using the Event Repository
				Service
*******************************************************************************/
void UbloxHandler_MdmOnTemperatureStatusEventParse(u16 evtid, char* data, u16 lengt)
{
	int Mode = 0;
	int Event = 0;
	SModemTemperatureStatusEvent MdmTemperature;

	sscanf( &data[strlen(UBL_Events[evtid].Event )], "%d,%d", &Mode, &Event );
	MdmTemperature.Status = (s8)Event;
	EVENTREP_UpdateRepositoryEvents(EVENTREP_ModemTemperatureStatus, &MdmTemperature);
	Log(LOG_SUB_MDM, LOG_LVL_DEBUG, "Modem temperature status changed to %d\n", MdmTemperature.Status);
}


/*******************************************************************************
NAME:           UbloxHandler_MdmOnSmsIndicationEventParse
PARAMETERS:	    u16 	evtid
				char* 	data
				u16 	lenght
RETURN VALUES:  void
DESCRIPTION:   	Parse the information provided in the the +UUCMSRES URC and
				updates the SendingError on the modem global structure (Smdm)
*******************************************************************************/
void UbloxHandler_MdmOnSmsIndicationEventParse(u16 evtid, char* data, u16 lenght)
{
	u16 MsgSendResult = 0;
	u16 MsgReference = 0;

	sscanf( &data[strlen(UBL_Events[evtid].Event )], "%hd,%hd", &MsgSendResult, &MsgReference );
	Smdm.sms.SendingError = MsgSendResult;
}

/*******************************************************************************
NAME:           UbloxHandler_MdmOnPdpContextActivationEventParse
PARAMETERS:	    u16 	evtid
				char* 	data
				u16 	lenght
RETURN VALUES:  void
DESCRIPTION:   	Parse the information provided in the the +UUPSDA URC
*******************************************************************************/
void UbloxHandler_MdmOnPdpContextActivationEventParse(u16 evtid, char* data, u16 lenght)
{

	u16	Result = 0xFFFF;

	//Parse:	+UUPSDA: <result>[,<ip_addr>]
	//	 Ex:	+UUPSDA: 0

	//result	0: action successful
	//			Different values mean an unsuccessful action (the codes are listed in the Appendix A.1)

	sscanf( &data[strlen(UBL_Events[evtid].Event )], "%hd", &Result );
	Smdm.ConnectionActivationResult = Result;

	Log( LOG_SUB_MDM, LOG_LVL_WARN,"GPRS activation process ended %s\n", Result == 0 ? "Successfully":"Unsuccessfully" );
}

//SLCC clear events
void UbloxHandler_ClearCall( SACM_CallInfo *ptr )
{
	ptr->dir = EACMCD_Undefined;
	ptr->state = EACMSTC_Inactive;
	ptr->CallType = EACMCT_Undefined;
	ptr->MultyParty = false;
	ptr->TrafficChAssigned = false;
	memset(ptr->Num, '\0' , PHONE_MAX_SIZE);
	ptr->NumFormat = EACMPF_Undefined;
	ptr->PhoneType = EACMPT_Undefined;
}

static void UbloxHandler_ClearAllCalls( void )
{
	u16 Index;

	for(Index = 0 ;Index < MAX_CALLES_HANDLED ; Index++)
	{
		UbloxHandler_ClearCall(&TempCall[Index]);
		UbloxHandler_ClearCall(&Smdm.Call[Index]);
	}
	Smdm.CallInfoChanged = true;
}

//SLCC here we update the call list for the ACM
bool UpdateCallList( SACM_CallInfo *ptr , SACM_CallInfo *Tempptr , u16 idx)
{
		EMDM_CallerIDCommands cmd;

		if(ptr->state == Tempptr->state) {
			return false;
        }

        ptr->state = Tempptr->state;
		ptr->dir = Tempptr->dir;
		ptr->CallType = Tempptr->CallType;


		if(ptr->state != EACMSTC_Inactive){

			if( PhoneNumbers_IsEmergencyAccidentNumber( Tempptr->Num )){
				ptr->PhoneType = EACMPT_Emergency;
			}
			else if(PhoneNumbers_IsAssistanceNumber( Tempptr->Num )){
				ptr->PhoneType = EACMPT_Assistance;
			}
            else if(ptr->dir == EACMCD_MobileTerminatedCall && ((cmd = IsZiltokCall( Tempptr->Num )) != EMDMCIC_CmdLast)){
				Mdm_AcmRequestReject(idx , NULL);
				if(CommandCallCmd(cmd) != NULL){
					CommandCallCmd(cmd)();
				}
				ptr->PhoneType = EACMPT_ZilTok;
			}
            else if( ( ptr->dir == EACMCD_MobileTerminatedCall) &&
                PhoneNumbers_IsSilentNumber(Tempptr->Num) )
            {
                //SLCC if this incoming call is silent call
                //after found we reset the silent request to prevent unwanted search
				ptr->PhoneType = EACMPT_Silent;
			}
			else if((ptr->state == EACMSTC_Incoming || ptr->state == EACMSTC_Waiting) && Smdm.AutoAnswer == ENABLE){
				Smdm.AutoAnswer = DISABLE;
				//we dont answer from here because it seems the modem cand handle it
				//SetMdmCallState(EMDMCS_SentCallAnswer);
				//Mdm_AcmRequestAnswer(idx , NULL);
				ptr->PhoneType = EACMPT_Emergency;
			}
            else if (PhoneNumbers_IsBO1BO2Number( Tempptr->Num ))
            {
                ptr->PhoneType = EACMPT_Emergency;
            }
            else if (PhoneNumbers_IsBackOfficePhoneNumber( Tempptr->Num ) )
			{
				ptr->PhoneType = EACMPT_Assistance;
			}
            else if ((ptr->dir == EACMCD_MobileOriginatedCall ) && PhoneNumbers_IsSAMUPhoneNumber( Tempptr->Num ) )
			{
				ptr->PhoneType = EACMPT_SAMU;
			}
			else {
				ptr->PhoneType = EACMPT_Other;
			}

			if( ptr->state == EACMSTC_Dialing || ptr->state == EACMSTC_Alerting ) {
				// Dialing succeeded, forget about previous errors
				Smdm.ErrCount.DialErrorCounter = 0;
			}
		}
		else{
			//just im case we missed it
			if(ptr->PhoneType == EACMPT_Emergency || ptr->PhoneType == EACMPT_Assistance || ptr->PhoneType == EACMPT_Accident ){
				SMDM_OutMsgDialInfo.ty = EMSGMT_Normal_Message;
			}

			ptr->PhoneType = Tempptr->PhoneType;
		}


		ptr->MultyParty = Tempptr->MultyParty;
		ptr->TrafficChAssigned = Tempptr->TrafficChAssigned;
		strncpy(ptr->Num, Tempptr->Num , PHONE_MAX_SIZE - 1);
		ptr->Num[PHONE_MAX_SIZE - 1] = '\0';
		ptr->NumFormat = Tempptr->NumFormat;

return true;
}

//get a call status according to a known number
SACM_CallInfo* GetCallStatus( char* number , u16 Index)
{
	u16 i;
	SACM_CallInfo *Call = NULL;

	if(number != NULL){
		for(i = 0 ; i < MAX_CALLES_HANDLED ; i++){
			if(!strncmp((char*)&Smdm.Call[i].Num , number , PHONE_MAX_SIZE)){
				Call = &Smdm.Call[i];
				break;
			}
		}
	}
	else {
		Call = &Smdm.Call[Index];
	}

	return Call;
}


//search for any connected calls
bool AreWeConnectedToOtherSide( void )
{

	u16 i;
	for(i = 0 ; i < MAX_CALLES_HANDLED ; i++){
		if(Smdm.Call[i].dir == EACMCD_MobileOriginatedCall ||
           Smdm.Call[i].state == EACMSTC_Active){
			return true;
		}
	}

	return false;
}

//search for any active calls
bool IsAnyActiveCall( void )
{

	u16 i;
	for(i = 0 ; i < MAX_CALLES_HANDLED ; i++){
		if(Smdm.Call[i].state != EACMSTC_Inactive){
			return true;
		}
	}

	return false;
}

//Check in the Smdm.Call buffer for a single incomming call that has the state indicated
//Used to play the personalized ringtone only when a single is ringing or the same call was answered/rejected
bool CheckForSingleCallState( EACM_StateOfTheCall State )
{
	bool ret;
	u8 CallIndex;
	u8 RingingCalls;
	u8 ActiveCalls;

	ret = false;
	RingingCalls = 0;
	ActiveCalls = 0;

	for(CallIndex = 0 ; CallIndex < MAX_CALLES_HANDLED ; CallIndex++) {
		if( Smdm.Call[CallIndex].state == State ) {
			RingingCalls++;
		}
		if( Smdm.Call[CallIndex].state != EACMSTC_Inactive ) {
			ActiveCalls++;
		}
	}

	if( RingingCalls == 1 && ActiveCalls == 1 ) {
		ret = true;
	}

	return ret;
}


void UbloxHandler_UpdateCallInfo( void )
{

	u16 Index;

	for(Index = 0 ;Index < MAX_CALLES_HANDLED ; Index++){
		Smdm.CallInfoChanged |= UpdateCallList(&Smdm.Call[Index],&TempCall[Index] , Index);
		if(Smdm.Call[Index].PhoneType == EACMPT_ZilTok){
			UbloxHandler_ClearCall(&Smdm.Call[Index]);
		}
	}

	for(Index = 0 ;Index < MAX_CALLES_HANDLED ; Index++){
		UbloxHandler_ClearCall(&TempCall[Index]);
	}
}

void UbloxHandler_OnEventReceivedWhileSleep( void )
{

	EMDM_State state = UbloxHandler_GetMdmState();

	if(state == EMDMS_StartSleep || state == EMDMS_WaitSleep || state == EMDMS_Sleep){
		UbloxHandler_SetMdmState(EMDMS_StartWakeUp);
		PwrMgr32SetWakeupWhileInStandbyOrSleep();
	}
}

//callback we get here the UCALLSTAT information
void UbloxHandler_UpdateCallState(u16 evtid, char* data, u16 lengt)
{

	UbloxHandler_OnEventReceivedWhileSleep();

	//we have call state change send AT+CLCC
	UBL_SendUrgentCommand(true, UBL_CMD_CLCCExecCommand);
}


EATC_Result UbloxHandler_UpdateCallStateExtended(u16 cmdid, char* data , u16 lenght)
{
	char *ptr;
	u16 Index;
	const char CallRespStr[] = {"+CLCC: "};
	u8 CallRespLen;

	data[lenght] = '\0';
	CallRespLen = (u8)strlen(CallRespStr);

	Smdm.AcmPendingDial = false;

	/*
	When AT+CLCC is sent to the modem the following responses can be given:
	1) No Calls
		AT+CLCC
			OK

	2) Single Call
		AT+CLCC
			+CLCC: Call Information 1
			OK

	3) Multiple Calls
		AT+CLCC
			+CLCC: Call Information 1
			+CLCC: Call Information 2
			...
			+CLCC: Call Information n
			OK
	*/

	ptr = strstr((char*)data, CallRespStr);

	if( ptr == NULL) {
		if( strstr((char*)data, "OK") ){
			UbloxHandler_UpdateCallInfo();
			return ATC_Ok;
		}
	}
	else {
		if( lenght > CallRespLen + MDM_MSG_END_LENGTH ) {
			ptr = &data[CallRespLen];
			Index = atoi(ptr);
			if ( Index == 0 || Index >= MAX_CALLES_HANDLED ) {
				Log(LOG_SUB_MDM,LOG_LVL_WARN,"Invalid Index on CLCC response\n");
				return ATC_Ok;
			}
			else {
				Index = Index -1;
			}

			if( (ptr = strtok(ptr, ",")) == NULL ) {
				return ATC_Ok;
			}

			if( Index < MAX_CALLES_HANDLED ){
				if( (ptr = strtok(NULL, ",")) != NULL ) {
					TempCall[Index].dir = (EACM_CallDirection)atoi(ptr);
					if( (ptr = strtok(NULL, ",")) != NULL ) {
						TempCall[Index].state = (EACM_StateOfTheCall)atoi(ptr);
						if( (ptr = strtok(NULL, ",")) != NULL ) {
							TempCall[Index].CallType = (EACM_CallType)atoi(ptr);
							if( (ptr = strtok(NULL, ",")) != NULL ) {
								TempCall[Index].MultyParty = atoi(ptr)? true:false;
								if( ( ptr = strtok(NULL, ",")) != NULL ) {
									ptr++;		// Go past "
									strncpy(&TempCall[Index].Num[0] , ptr, MIN( PHONE_MAX_SIZE - 1, strlen( ptr ) - 1 ) );
									TempCall[Index].Num[PHONE_MAX_SIZE - 1] = '\0';
									if((ptr = strtok(NULL , "\r\n")) != NULL){
										TempCall[Index].NumFormat = (EACM_PhoneFormat)atoi(ptr);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	return ATC_InProc;
}


//+UCELLINFO: syntax for 2G cells: +UCELLINFO: <mode>,<type>,<MCC>, <MNC>,<LAC>,<CI>,<RxLev>[,<t_adv>[,<ch_type>,<ch_mode>]]
//+UCELLINFO: syntax for 3G cells: +UCELLINFO: +UCELLINFO: <mode>,<type>,<MCC>,<MNC>,<LAC>,<CI>,<scrambling_code>,<dl_frequency>,<rscp_lev>,<ecn0_lev>[,<rrc_state>]
//<mode>: 0: periodic reporting disabled 1: periodic reporting enabled
//<type> Number For 2G cells: 0: 2G serving cell 1: neighbour 2G cell
//<type> Number For 3G cells: 2: 3G serving cell or cell belonging to the Active Set 3: cell belonging to the Virtual Active Set 4: detected cell

void UbloxHandler_UpdateCellInfo(u16 evtid, char* data, u16 lengt)
{
	char *ptr;
	u16 Index;
	ptr = 	(char*)data;

    u8 mode =0;
    u8 type =0;
    u8 MCC = 0;     //unused
    u8 MNC = 0;     //unused
    u8 LAC = 0;     //unused
    u8 CI  = 0;     //unused
    u8 RxLev = 0;   //unused

	UbloxHandler_OnEventReceivedWhileSleep();

    if (lengt > strlen("+UCELLINFO: ") + MDM_MSG_END_LENGTH)
	{
		ptr = &data[strlen("+UCELLINFO: ")];
		mode = atoi(ptr);
        if((ptr = strtok(NULL, ",")) != NULL){
            type = atoi(ptr);
        }
	}
}


//+UUSIMSTAT: <state>
//<state> Number 0: SIM card not present 1: PIN needed  2: PIN blocked  3: PUK blocked  4: (U)SIM not operational
//5: (U)SIM in restricted use (FDN or BDN active) 6: (U)SIM operational (registration may be initiated)
//7: SIM phonebook ready to be used (when the SIM application is active) 8: USIM phonebook ready to be used (when the USIM application is active)
//9: (U)SIM toolkit REFRESH proactive command successfully concluded 10: (U)SIM toolkit REFRESH proactive command unsuccessfully concluded

void UbloxHandler_UpdateSimState(u16 evtid, char* data, u16 lengt)
{
	char *ptr;
	u16 Index;
	ptr = 	(char*)data;
    EMDM_SimStatus SimState;

    if (lengt > strlen("+UUSIMSTAT: ") + MDM_MSG_END_LENGTH)
	{
		ptr = &data[strlen("+UUSIMSTAT: ")];
		SimState = (EMDM_SimStatus)atoi(ptr);
		UbloxHandler_UpdateSIMStatus(SimState);
	}
}

void UbloxHandler_TimeZoneManaging (u16 evtid, char* data, u16 lengt)
{
	char *ptr;
	u16 Index;
	ptr = 	(char*)data;
    s8 timeZone = 0;

    if (lengt > strlen("+CTZV: ") + MDM_MSG_END_LENGTH)
	{
		ptr = &data[strlen("+CTZV: ")];
        if((ptr = strtok(NULL, "+")) != NULL){
            timeZone = atoi(ptr);
        }
        else if((ptr = strtok(NULL, "-")) != NULL){
            timeZone = atoi(ptr);
        }
        else {
            return;
        }
        if((ptr = strtok(NULL, ",")) != NULL){
            //Get hour
        }
	}
}

//callback this function is call every time the RING event pops form the modem,
//if we arent in main task we need to trigger the CLCC event
void UbloxHandler_MdmRingEventDetected( void )
{
	UBL_SendUrgentCommand(true, UBL_CMD_CLCCExecCommand);
}

static SMDM_NetworkStatus LastStatus;
static const char* const StatNames[] =
{
	"Not registered, not searching",
	"Registered to home network",
	"Not registered, searching",
	"Registration denied",
	"Unknown",
	"Registered roaming",
	"Illegal Status"
};


void UbloxHandler_SetDnsResolution(u16 cmdid, char* data)
{
	char* ptr;
	u8 	  len;

	BZERO(Smdm.GprsDestinationIP);
	ptr = &data[strlen(UBL_Commands[cmdid].Response)];
	len = MIN(strlen(ptr),IP_ADDRESS_MAX_SIZE_STR);
	memcpy( Smdm.GprsDestinationIP, ptr, len );
	Smdm.GprsDestinationIP[IP_ADDRESS_MAX_SIZE_STR] = '\0';
}

void UbloxHandler_SetTcpSocket(u16 cmdid, char* data)
{
	u32 SockedId;
	sscanf( &data[strlen(UBL_Commands[cmdid].Response )], "%u", &SockedId );
	Smdm.GprsSocket = (u8)SockedId;
}

u8 UbloxHandler_GetTcpSocket(void)
{
	return Smdm.GprsSocket;
}

bool UbloxHandler_isRegistered()
{
	return (((EMDMNS_RegHome == (&LastStatus.Gprs)->NetStat)||(EMDMNS_RegRoam == (&LastStatus.Gprs)->NetStat))? true:false );
}


void UbloxHandler_UpdateLastNetworkStatus( SMDMN_Network *pNetLast, SMDMN_Network *pNetNew, const char* NetTypeName )
{
	EMDM_NetworkStatus Status;
	u8 pBell[] = { 7, 0 };

	if( pNetLast->NetStat != pNetNew->NetStat )
	{
		Status = pNetNew->NetStat;
		if( Status < EMDMNS_NotRegNotSrch || Status > EMDMNS_RegRoam )
		{
			Status = EMDMNS_RegHomeSmsOnly;
		}

		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"%s stat = %s\n", NetTypeName, StatNames[Status]);
	}

	if( pNetLast->Lac != pNetNew->Lac )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"%s Location Area = 0x%X\n", NetTypeName, pNetNew->Lac);
	}

	if( pNetLast->Ci != pNetNew->Ci )
	{
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"%s Cell Id = 0x%X\n", NetTypeName, pNetNew->Ci);
		Log(LOG_SUB_MDM,LOG_LVL_TRACE,"%s\n", pBell);
	}

	*pNetLast = *pNetNew;
}

//callback when gprs or gsm URC are prompt
void UbloxHandler_UpdateNetworkStatus(char* data , SMDM_MessageTypeIs mType , SMDM_NetworkTypeIs nType)
{
	char *ptr, *stat , *Lac , *Ci, *AcT;
	ptr = 	data;

    if((ptr = strtok(ptr , " ,\r\n")) == NULL) {
    	return;
    }
    //ptr = strtok(NULL , " ,\n");

    if(mType == SMDMMTI_CommandRes)
    {
        s8 *mode = (s8*)strtok(NULL , " ,\r\n");
	}

	stat = 	strtok(NULL, " ,\r\n");
	Lac = 	strtok(NULL, " ,\r\n");
	Ci = 	strtok(NULL, " ,\r\n");
	AcT =	strtok(NULL, " ,\r\n");

	if(stat != NULL) {
		s16 temp = atoi(stat);
		if(nType == SMDMNTI_Gprs) {
			Smdm.NetStat.NewReg = Smdm.NetStat.NewReg || ( Smdm.NetStat.Gprs.NetStat != temp );
			Smdm.NetStat.Gprs.NetStat = (EMDM_NetworkStatus)temp;
		}
		else {
			Smdm.NetStat.NewReg = Smdm.NetStat.NewReg || ( Smdm.NetStat.Gsm.NetStat != temp );
			Smdm.NetStat.Gsm.NetStat = (EMDM_NetworkStatus)temp;
		}
	}

	if(Lac != NULL) {
		if(nType == SMDMNTI_Gprs) {
			sscanf((char*)(Lac + 1) , "%x" , &Smdm.NetStat.Gprs.Lac);
		}
		else {
			sscanf((char*)(Lac + 1) , "%x" ,  &Smdm.NetStat.Gsm.Lac);
		}
	}

	if(Ci != NULL) {
		if(nType == SMDMNTI_Gprs) {
			sscanf((char*)(Ci + 1) , "%x" ,  &Smdm.NetStat.Gprs.Ci);
		}
		else {
			sscanf((char*)(Ci + 1) , "%x" ,  &Smdm.NetStat.Gsm.Ci);
		}
	}

	if(AcT != NULL) {
		s16 temp = atoi(AcT);
		if(nType == SMDMNTI_Gprs) {
			Smdm.NetStat.Gprs.RadioAccessTech = (EMDM_RadioAccessTech)temp;
		}
		else {
			Smdm.NetStat.Gsm.RadioAccessTech = (EMDM_RadioAccessTech)temp;
		}
	}

	if( LogIsActive(LOG_SUB_MDM,LOG_LVL_TRACE) )
	{
		if(nType == SMDMNTI_Gprs)
		{
			UbloxHandler_UpdateLastNetworkStatus( &LastStatus.Gprs, &Smdm.NetStat.Gprs, "GPRS" );
		}
		else
		{
			UbloxHandler_UpdateLastNetworkStatus( &LastStatus.Gsm, &Smdm.NetStat.Gsm, "GSM" );
		}
	}
}


EATC_Result UbloxHandler_ReceivedSimUrcConfiguration(u16 cmdid, char* data , u16 lenght)
{
	EATC_Result eResult = ATC_Ok;
	u16 mode = 0;

	data[lenght] = '\0';

	//	 Parse: +USIMSTAT: <mode>
	// Example: +SSIMSTAT: 3

	sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%hu", &mode);

	if( mode == 0 ) {
		Log(LOG_SUB_MDM, LOG_LVL_TRACE, "All SIM URCs are Disabled, Re-Enable SIM URCs\n");
		Smdm.SimUrcEnabled = true;
	}
	else {
		if( mode & EMDMSUBM_InitializationStatus ) {
			Log(LOG_SUB_MDM, LOG_LVL_TRACE, "SIM URCs are Enabled (Regular states from 0 - 6)\n");
		}

		if( mode & EMDMSUBM_PhoneBookInitializationStatus ) {
			Log(LOG_SUB_MDM, LOG_LVL_TRACE, "SIM URCs are Enabled (Phonebook states from 7 - 8)\n");
		}
		else{
			//This is to support the 7- 8 states when modem was previously initilized to give only the 0 - 6 states
			Log(LOG_SUB_MDM, LOG_LVL_TRACE, "SIM Phonebook URCs are Disabled, Re-Enable SIM URCs\n");
			Smdm.SimUrcEnabled = true;
		}

		if( mode & EMDMSUBM_ToolkitRefresh ) {
			Log(LOG_SUB_MDM, LOG_LVL_TRACE, "SIM URCs are Enabled (Toolkit refresh states from 9 - 13)\n");
		}
	}

	return eResult;
}


void UbloxHandler_UpdateGprsAttachStatus(u16 cmdid, char* data)
{
	u32 Status;
	sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%ud", &Status);
	Smdm.GprsAttachStatus = (EMDM_AttachDetach)Status;
	Log(LOG_SUB_MDM,LOG_LVL_WARN,"Gprs Attach Status = %d\n", Smdm.GprsAttachStatus);
}

void UbloxHandler_UpdateConnectionStatus(u16 cmdid, char* data)
{
	char* ptr;
	char* pProfileId;
	char* pParamTag;
	char* pParamVal;

	ptr = data;
	if( (ptr = strtok(ptr , " ")) == NULL ) {
    	return;
	}

    pProfileId = strtok(NULL , ",");
	pParamTag = strtok(NULL , ",");
	pParamVal = strtok(NULL , ",\r");

    if ( pParamVal ){
        Smdm.ConnectionStatus = (atoi(pParamVal) == 1)? true: false;
        Log(LOG_SUB_MDM,LOG_LVL_WARN,"Connection Status = %d\n", Smdm.ConnectionStatus);
    }
    else{
        Log(LOG_SUB_MDM,LOG_LVL_WARN,"UpdateConnectionStatus failed to parse data\n");
    }
}

//callback for +CPMS command to see if there are any sms waiting for us
void UbloxHandler_UpdatePendingSms(u16 cmdid, char* data)
{
	char *ptr,*Max,*Pending;
	ptr = data;

	if((ptr = strtok(ptr  , "," )) ==NULL){
		return;
	}

	Pending = 	strtok(NULL , "," );
	if(Pending != NULL){
		Smdm.sms.rx.RxInfo.Pending = atoi(Pending);
	}

	Max = 		strtok(NULL , "," );
	if(Max != NULL){
		Smdm.sms.rx.RxInfo.MaxLocations = atoi(Max);
	}

	// The response to the CPMS message resets the keepalive logic
	Smdm.KeepAliveTimer = 0;
}

//callback for +CPMS= configuration command to see if there are any sms waiting for us
EATC_Result UbloxHandler_UpdateConfigureSmsStorage(u16 cmdid, char* data , u16 length)
{

	char *ptr,*Max,*Pending;

	if(length <= ATC_MSG_END_LENGTH){
		return ATC_InProc;
	}

	ptr = data + 7;

	Pending = strtok(ptr, "," );
	if(Pending != NULL){
		Smdm.sms.rx.RxInfo.Pending = atoi(Pending);
	}

	Max = strtok(NULL , "," );
	if(Max != NULL){
		Smdm.sms.rx.RxInfo.MaxLocations = atoi(Max);
	}
return ATC_Ok;
}

EATC_Result UbloxHandler_UpdateCallWaitingStatus(u16 cmdid, char* data , u16 length)
{
	EATC_Result ret = ATC_InProc;
    u16 CWStatus = 0;
	u16 CWClassx = 0;

    if( length <= ATC_MSG_END_LENGTH ){
		return ATC_InProc;
	}

	if( strncasecmp(data, "ERROR", strlen("ERROR")) == 0 ) {
		ret = ATC_Error;
	}
	else if( strncasecmp(data, "OK", strlen("OK")) == 0 ) {
		ret = ATC_Ok;
	}
	else {
        if( strstr((char*)data, "+CCWA: ") ) {
            sscanf(data, "+CCWA: %hd,%hd", &CWStatus, &CWClassx);
            if( EUBLCWC_Voice == (EUBL_CallWaitingClassx)CWClassx ) {
                Smdm.CallWaitingStatus = (EUBL_CallWaitingStatus)CWStatus;
            }
        }
        ret = ATC_InProc;
	}

	return ret;
}

EATC_Result UbloxHandler_SetMdmFwVersion(u16 cmdid, char* data , u16 length)
{
    if( length > ATC_MSG_END_LENGTH){
      //FW version of ublox modem is given in this format: 11.40 i.e.
      sscanf(&data[strlen(UBL_Commands[cmdid].Response)], "%" XSTR(FWVER_MAX_SIZE_STR) "s", Smdm.Info.SwVer);
      Log(LOG_SUB_MDM,LOG_LVL_WARN,"Ublox Version %s\n", Smdm.Info.SwVer);
      return ATC_Ok;
    }
return ATC_InProc;
}

EATC_Result UbloxHandler_SetMdmModel(u16 cmdid, char* data , u16 length)
{
     if( length > ATC_MSG_END_LENGTH){
        sscanf(&data[strlen(UBL_Commands[cmdid].Response)], "%" XSTR(MDM_MODEL_MAX_SIZE_STR) "s", Smdm.Info.Model);

        Log(LOG_SUB_MDM,LOG_LVL_WARN,"Ublox Model %s\n", Smdm.Info.Model);
        return ATC_Ok;
     }
return ATC_InProc;
}

struct ModemErrorInfo { EMDM_ServiceLastError err; const char* const str; };
const ModemErrorInfo SocketErrorStrings[] =
{
	{ EMDMSLE_ServiceOk, "ServiceOk" },
	{ EMDMSLE_ServiceAborted, "ServiceAborted" },
	{ EMDMSLE_NoSuchResource, "NoSuchResource" },
	{ EMDMSLE_InterruptedSystemCall, "InterruptedSystemCall" },
	{ EMDMSLE_IOError, "IOError" },
	{ EMDMSLE_BadFileDescriptor, "BadFileDescriptor" },
	{ EMDMSLE_NoChildProcesses, "NoChildProcesses" },
	{ EMDMSLE_CurrentOperationWouldBlock, "CurrentOperationWouldBlock" },
	{ EMDMSLE_OutOfMemory1, "OutOfMemory2" },
    { EMDMSLE_BadAddress, "BadAddress" },
    { EMDMSLE_InvalidArgument, "InvalidArgument" },
    { EMDMSLE_BrokenPipe, "BrokenPipe" },
    { EMDMSLE_FunctionNotImplemented, "FunctionNotImplemented" },
	{ EMDMSLE_ProtocoloNotAvailable, "ProtocoloNotAvailable" },
    { EMDMSLE_AddressAlreadyInUse, "AddressAlreadyInUse" },
    { EMDMSLE_SoftwareCausedConnectionAbort, "SoftwareCausedConnectionAbort" },
    { EMDMSLE_ConnectionResetByPeer, "ConnectionResetByPeer" },
    { EMDMSLE_NoBufferSpaceAvailable, "NoBufferSpaceAvailable" },
    { EMDMSLE_TransportEndpointIsNotConnected, "TransportEndpointIsNotConnected" },
    { EMDMSLE_CannotSendAfterTrasnportEndpointShutdown, "CannotSendAfterTrasnportEndpointShutdown" },
    { EMDMSLE_ConnectionTimeout, "ConnectionTimeout" },
    { EMDMSLE_NoRouteHost, "NoRouteHost" },
    { EMDMSLE_OperationNowInProgress, "OperationNowInProgress" },
    { EMDMSLE_DNSServerReturnedAnswerwithNoData, "DNSServerReturnedAnswerwithNoData" },
    { EMDMSLE_DNSServerClaimsQueryWasMisformatted, "DNSServerClaimsQueryWasMisformatted" },
    { EMDMSLE_DNSServerReturnedGeneralFailure, "DNSServerReturnedGeneralFailure" },
    { EMDMSLE_DomainNameNotFound, "DomainNameNotFound" },
    { EMDMSLE_DNSServerDoesNotImplementRequestedOperation, "DNSServerDoesNotImplementRequestedOperation" },
    { EMDMSLE_DNSServerRefusedQuery, "DNSServerRefusedQuery" },
    { EMDMSLE_MisformattedDNSQuery, "MisformattedDNSQuery" },
    { EMDMSLE_MisformattedDomainName, "MisformattedDomainName" },
    { EMDMSLE_UnsupportedAddressFamily, "UnsupportedAddressFamily" },
    { EMDMSLE_MisformattedDNSReply, "MisformattedDNSReply" },
    { EMDMSLE_CouldNotContatDNSServers, "CouldNotContatDNSServers" },
    { EMDMSLE_TimeoutWhileContactingDNSServers, "TimeoutWhileContactingDNSServers" },
    { EMDMSLE_EndOfFile, "EndOfFile" },
    { EMDMSLE_ErrorReadingFile, "ErrorReadingFile" },
    { EMDMSLE_OutOfMemory2, "OutOfMemory1" },
    { EMDMSLE_ApplicationTerminatedLookup, "ApplicationTerminatedLookup" },
    { EMDMSLE_DomainNameIsTooLong1, "DomainNameIsTooLong1" },
    { EMDMSLE_DamainNameIsTooLong2, "DamainNameIsTooLong2" },

};

static const u8* GetSocketErrorStr( EMDM_ServiceLastError err )
{
	int i;
	for( i = 0; i < elementsof( SocketErrorStrings ); i++ )
	{
		if( SocketErrorStrings[i].err == err )
		{
			return (const u8*)SocketErrorStrings[i].str;
		}
	}
	return "Unknown Socket Status";
}



void UbloxHandler_UpdateLastSocketError(u16 cmdid, char *data)
{
	char* ptr;
	char* pSocketId;
	char* pParamId;
	char* pParamVal;

	u16 SocketId;
	u16 SocketDest;

	ptr = data;
	if( (ptr = strtok(ptr , " ")) == NULL ) {
    	return;
	}

    pSocketId = strtok(NULL , ",");
	pParamId  = strtok(NULL , ",");
	pParamVal = strtok(NULL , ",\r");

	if( pSocketId != NULL ) {
		SocketId = (u16)atoi(pSocketId);
		if ( UbloxHandler_FindSocketDestination(&SocketDest, SocketId) ) {
			if( (EMDM_DestSocketDef)SocketDest == EMDMDSD_GprsDestination_1 ||
				(EMDM_DestSocketDef)SocketDest == EMDMDSD_GprsDestination_2 ||
				(EMDM_DestSocketDef)SocketDest == EMDMDSD_Traffic			||
				(EMDM_DestSocketDef)SocketDest == EMDMDSD_Http ) {
				if( pParamId != NULL && atoi(pParamId) == 1) {
					if( pParamVal != NULL ) {
						Smdm.Gprs[SocketDest].LastError = (EMDM_ServiceLastError)atoi(pParamVal);
						Log(LOG_SUB_MDM, LOG_LVL_TRACE, "Socket Last Error Dest(%d): %d = %s\n", SocketDest,
																								 Smdm.Gprs[SocketDest].LastError,
																								 GetSocketErrorStr(Smdm.Gprs[SocketDest].LastError) );
					}
				}
			}
		}
	}
}

//callback all socket informetion is update here
void UbloxHandler_UpdateInternetServiceInformation(u16 cmdid, char *data)
{
	u16	SocketId = 0;
	u16	SocketDest;
	u16 ServiceState;
    u16 tmpval;

	sscanf( &data[strlen(UBL_Commands[cmdid].Response)], "%hu,%hu,%hu", &SocketId, &tmpval , &ServiceState);

	//Find SocketDest from SocketId;
	if( UbloxHandler_FindSocketDestination(&SocketDest, SocketId) ) {
		if( (EMDM_DestSocketDef)SocketDest == EMDMDSD_GprsDestination_1 ||
			(EMDM_DestSocketDef)SocketDest == EMDMDSD_GprsDestination_2 ||
			(EMDM_DestSocketDef)SocketDest == EMDMDSD_Traffic			||
			(EMDM_DestSocketDef)SocketDest == EMDMDSD_Http ) {
			Smdm.Gprs[SocketDest].ServiceState = (EMDM_ServiceState)ServiceState;
		}
	}
}

bool UbloxHandler_FindSocketDestination( u16* SocketDest, u16 SocketId )
{
    u8 SocketDestIndx;
	for( SocketDestIndx = 0; SocketDestIndx < elementsof(Smdm.Gprs); SocketDestIndx++ ) {
		if( SocketId == Smdm.Gprs[SocketDestIndx].SocketAssigned ) {
            *SocketDest = SocketDestIndx;
			return true;
		}
	}
	return false;
}


//callback update pending data in socket
void UbloxHandler_UpdateSocketReceivedDataSize(u16 evtid, char* data)
{
	u16	SocketId = 0;
	u32	DataLenght = 0;
	u16	SocketDest;

	/* URC is triggered to notify the data availables for reading, either:
	 	1. When buffer is empty and new data arrives or,
		2. After a partial read by the user */

	sscanf(&data[strlen(UBL_Events[evtid].Event)], "%hu, %d", &SocketId , &DataLenght);

	//Find SocketDest from SocketId;
	if( UbloxHandler_FindSocketDestination(&SocketDest, SocketId) ) {
		if( Smdm.Gprs[SocketDest].ServiceState == EMDMSS_SocketEstablished ) {
			Smdm.Gprs[SocketDest].RxDataToRead =  DataLenght;
		}
	}
}

//callback get the socket data here
EATC_Result UbloxHandler_UpdateSocketReceivedData(u16 cmdid, char* data , u16 size)
{
	EATC_Result res = ATC_Ok;
	static u16 SocketId = 0;
	u32 DataReadLenght;
	u16 SocketDestination = 0;
	SMDM_GprsServiceInfo * p = NULL;
	char *ptr = data;
	if(cmdid == UBL_CMD_GetReadSocketInfo)
	{

		//+USORD: 3,16,"16 bytes of data"
		//skip the command
		ptr = &data[strlen(UBL_Commands[cmdid].Response)];
		//get socket number
		SocketId = (u16)atoi(ptr);
		//skip next ,
		ptr = strchr(ptr , ',');
		if(ptr != NULL)
        {
            ptr++;
            //get data length
            DataReadLenght = atoi(ptr);
            //skip next ,
            ptr = strchr(ptr , ',');
            if( ptr != NULL){
              ptr++;
            }
        }

        if( ptr == NULL){
          return ATC_Error;
        }

		if( UbloxHandler_FindSocketDestination(&SocketDestination, SocketId) )
		{
			p = &Smdm.Gprs[SocketDestination];

			p->DataReceiver.IncomingFrameDataSize = (DataReadLenght == 0) ? 0:(s16)DataReadLenght + 4;

			if( p->DataReceiver.IncomingFrameDataSize > MDM_APP_DATA_MAX_LENGTH )
			{
				Log(LOG_SUB_MDM,LOG_LVL_WARN,"Socket Info Illegal size: %d\n", p->DataReceiver.IncomingFrameDataSize);
				ErrorReport(ERR_MDM_INVALID_SOCK_INFO_SIZE);
				p->DataReceiver.IncomingFrameDataSize = MDM_APP_DATA_MAX_LENGTH;
			}

			// Start new frame
			p->DataReceiver.LengthCurrentFrame = 0;

			if(p->DataReceiver.IncomingFrameDataSize == 0)
			{
				p->DataReceiver.Length = 0;
				Smdm.Gprs[SocketDestination].RxDataToRead = 0;
				return res;
			}

			//get the correct size without the message header
            size -= (ptr - data);
		}
	}
	else if(cmdid == UBL_CMD_GetReadSocketData)
	{
		if( UbloxHandler_FindSocketDestination(&SocketDestination, SocketId) )
		{
            p = &Smdm.Gprs[SocketDestination];

			//handle on error while reading data from gprs
			if( Get_F_MdmGprsReadError() )
			{
				Set_F_MdmGprsReadError( false );

				Log(LOG_SUB_MDM,LOG_LVL_WARN,"GprsReadError\n");

				if(Smdm.GprsReadSocketIndex <= EMDMDSD_Http)
				{
				//clear receive info from current socket served
					Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Length = 0;
					Smdm.Gprs[Smdm.GprsReadSocketIndex].RxDataToRead = 0;
				}
				else
				{	//just in case we are here and we dont know why;
					for(SocketDestination = 0; SocketDestination <=EMDMDSD_Http ;SocketDestination++)
					{
						p->DataReceiver.Length = 0;
						Smdm.Gprs[SocketDestination].RxDataToRead = 0;
					}
				}

				//reset static info on error
				p->DataReceiver.IncomingFrameDataSize = 0;
				SocketDestination = 0;
				//we return ATC_OK because the data here is an event and we arrived here after error;
				return ATC_Ok;
			}
		}
	}

    if(p != NULL && p->DataReceiver.IncomingFrameDataSize > 0)
	{
		u16  BytesNeeded = p->DataReceiver.IncomingFrameDataSize - p->DataReceiver.LengthCurrentFrame;
		bool IsFrameComplete = (BytesNeeded == 0) ? true:false;     // Ignore CR/LF at end of frame
		u16  BytesToCopy = IsFrameComplete ? size - 2 : size; // If not end of frame, CR/LF is part of data
		u16  SpaceLeft = sizeof(p->DataReceiver.Data) - p->DataReceiver.Length;

        // Do not allow buffer overrun
		if( SpaceLeft < BytesToCopy )
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN,"Socket Data Illegal size = %d, length = %d\n", size, p->DataReceiver.Length);
			ErrorReport(ERR_MDM_INVALID_SOCK_DATA_SIZE);
			BytesToCopy = SpaceLeft;
		}

		if( !IsFrameComplete ){
            memcpy(&p->DataReceiver.Data[p->DataReceiver.Length], ptr, BytesToCopy);
            p->DataReceiver.Length += BytesToCopy;
            p->DataReceiver.LengthCurrentFrame += BytesToCopy;
            res = ATC_InProc;
        }
	}


	if( !UbloxHandler_FindSocketDestination(&SocketDestination, SocketId) )
	{
		Log(LOG_SUB_MDM,LOG_LVL_WARN,"Socket Info Illegal id: %d\n", SocketDestination);
		ErrorReport(ERR_MDM_INVALID_SOCKET);
		return ATC_Error;
	}

	return res;

}

//direct uart write when modem approved the amount of data to send
void UbloxHandler_SendGprsData( void )
{
	GSMUART.Write(Sreq.CurrentMsgMgrReq.Out.data, Sreq.CurrentMsgMgrReq.Out.length - LOGGER_HEADER_LENGTH);

}
//callback we received the tcp/ip ack event her
void UbloxHandler_UpdateServerAck( u16 cmdid ,  char* data , u16 size, SMDM_MessageTypeIs mType)
{
	u16 param_id = 0;
	u16 param_val = 0;
	u16 SocketId = 0;
	u16 SocketDest = 0;
	//make sure its end where it should
    data[size] = '\0';


    if(mType == SMDMMTI_CommandRes){
    	sscanf( &data[strlen(UBL_Commands[cmdid].Response)], "%hu,%hu,%hu", &SocketId, &param_id, &param_val );


    	if( UbloxHandler_FindSocketDestination(&SocketDest, SocketId) ) {
			if((EMDM_SocketControlRequestIdentifier)param_id == EMDMSCRI_QueryTcpOutgoingUnacknowledgedData && param_val == 0 ) {
				Smdm.Gprs[SocketDest].TxWaitAck = true;
			}
		}
		else
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "Socket (%d) does not correspond with a GPRS socket destination\n", SocketId );
		}
    }
}

//update receive gprs data size
void UbloxHandler_UpdateMessageLocations(u8 *buff , u16 *buffSize , u16 MessageSize )
{
	*buffSize -= MessageSize;
	if(*buffSize) {
		memmove( buff , &buff[MessageSize] , *buffSize);
	}
}

//we search here for RT message format when gprs data arrived
u8* UbloxHandler_FindNewRtMessage(u8 *buff ,s16 buffSize , u16 *msgSize)
{
	u8 end[OFFICE_MSG_END_LENGTH] = OFFICE_MSG_END;
	u8 *StartIdx, *EndIdx;
	bool foundSync = false , foundEnd = false;
    S_OV_Header1* pOVMsg = NULL;
    u16 auxSize;

	if (buffSize < 0)
		return (u8*)0xffffffff;

	StartIdx = (u8*)FindPattern( buff, START_OF_MESSAGE, buffSize, START_OF_MESSAGE_LENGTH );
	foundSync = ( StartIdx != NULL );

	if( !foundSync ) {
		return (u8*)0xffffffff;
	}

    if(buffSize > sizeof(S_OV_Header1)) {
        pOVMsg = (S_OV_Header1*) StartIdx;
    }
    else {
        return NULL;
    }

    if(pOVMsg->type == EMF_O2V_AES128 || pOVMsg->type == EMF_O2V_SET_KEY) {
        // Message encrypted, read the size directly from message
        auxSize = b64ConvertString(pOVMsg->size, SIZE_NUMBER_LENGTH);
        auxSize += ((pOVMsg->size - (char*)StartIdx) + SIZE_NUMBER_LENGTH);
        if(buffSize < auxSize) {
            return NULL;
        }
        else {
            *msgSize = auxSize;
        }
    }
    else {
        EndIdx = (u8*)FindPattern( StartIdx, end, buffSize - ( StartIdx - buff ), OFFICE_MSG_END_LENGTH );
        foundEnd = ( EndIdx != NULL );

        if(!foundSync || !foundEnd) {
            return NULL;
        }

        *msgSize = (EndIdx - StartIdx);
    }

	//this line will fix incoming parssing where SOMEONE assumed there is NULL at the end of the message
	StartIdx[*msgSize] = '\0';

	return StartIdx;

}

//this is the find new message function we call from the main receive state machine
bool UbloxHandler_FindNewMessage(u8 *buff ,u16* buffSize , EMDM_DestSocketDef DestSocket)
{
	u8* RetAddress = (u8*)0xffffffff;
	u16 msgSize = 0;

	while(*buffSize > 0)
	{
		if(((RetAddress = UbloxHandler_FindNewRtMessage(buff, *buffSize, &msgSize)) != NULL) && (RetAddress != (u8*)0xffffffff))
		{

			if (DestSocket == EMDMDSD_GprsDestination_2)
				MdmNewGprsDataArrived_Dest2(RetAddress,msgSize);

			if (DestSocket == EMDMDSD_GprsDestination_1)
				MdmNewGprsDataArrived_Dest1(RetAddress,msgSize);

			msgSize += OFFICE_MSG_END_LENGTH;

		}

		//why we return true here??????
		if (RetAddress  == NULL)
			break;
		else if (RetAddress  == (u8*)0xffffffff)
		{
			*buffSize = 0;
			return false;
		}
		else
			UbloxHandler_UpdateMessageLocations(buff , buffSize , msgSize);
	}

	return true;
}

//DTMF can be only 29 byte at a time so here we split a larger messages befor send
EMDM_SplitResult SplitOutGoingMessageToDtmfMaxSize(char* src ,u32 SrcSize, char* dest , u16 *DestSize , u16 *Index )
{

	EMDM_SplitResult res = EMDMSR_SplitOnGoing;
	u16 DestIndex = 0;
	u16 StartIndex = *Index;

	if(!SrcSize)
		return EMDMSR_ErrMsgEmpty;

	if(SrcSize < MDM_DTMF_MAX_SIZE){
		memcpy(dest , src , SrcSize);
		DestIndex = SrcSize;
		res = EMDMSR_SplitEnd;
	}
	else{
		do{

			dest[DestIndex++] = src[StartIndex++];
			dest[DestIndex] = '\0';

		}while(DestIndex < MDM_DTMF_MAX_SIZE && StartIndex < SrcSize);



		if(StartIndex >= SrcSize)
			res = EMDMSR_SplitEnd;
	}
*Index = StartIndex;
*DestSize = DestIndex;
return res;
}


//set the call state machine to send ATH command
bool CallAbortAny( void )
{
    EMDM_CallState st = UbloxHandler_GetMdmCallState();
	if( st != EMDMCS_WaitAll_HangUp && st != EMDMCS_SendAll_HangUp)
    {
		UbloxHandler_SetMdmCallState(EMDMCS_SendAll_HangUp);
        return true;
	}
return false;
}

//save dial information to be comperd in the SLCC events
bool UbloxHandler_SetNewOutMsgDialInfo( void )
{
	bool ret = false;
	char* ptr;

	ptr = Mdm_GetPhoneNumber((EMSGS_MessageTypeWithPhone)Sreq.CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone);

	if ( ptr && ptr[0] )
	{
		memcpy(SMDM_OutMsgDialInfo.DialNumber, ptr , PHONE_MAX_SIZE);
		SMDM_OutMsgDialInfo.ty = (EMSGS_MessageTypeWithPhone)Sreq.CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone;
		ret = true;
	}

	return ret;
}
//main call state machine we use this machina for complecs dial
//such as emrgency were we it need to be part of a sequence gprs->call>dtmf->sms
//user dial going directlly to the modem

EMDM_CallState UbloxHandler_OnlineVoiceCallTask( SMDM_OutMsgScheme *CScheme)
{

	static u32 timeout;
	static u16 Index;
	EATC_Response response;
	char DtmfStr[MDM_DTMF_MAX_SIZE + 1];
	static
	SACM_CallInfo *CInfo;
	EMDM_CallState sm = UbloxHandler_GetMdmCallState();
	static EMDM_SplitResult SplitRes;
	u16 tmpSize ;

	if(	CScheme->Call.Active == false  	&&
		CScheme->Dtmf.Active == false 	&&
		sm != EMDMCS_WaitAll_HangUp 	&&
		sm !=EMDMCS_SendAll_HangUp){
			UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
	}

	switch(sm)
	{
	case EMDMCS_CallIdle:
		if(CScheme->Call.Active == true  || CScheme->Dtmf.Active == true){
			if(Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRoam){
				if(UbloxHandler_SetNewOutMsgDialInfo() == true){
					UbloxHandler_SetMdmCallState(EMDMCS_SendDial);
				}
				else
					UbloxHandler_SetMdmCallState(EMDMCS_CallError);

			}
			else
				UbloxHandler_SetMdmCallState(EMDMCS_CallError);
		}
	break;

	case EMDMCS_SentCallAnswer:
		UBL_SendCommand(true, UBL_CMD_SetVoiceAnswer);
		UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
	break;
	case EMDMCS_WaitCallAnswer:break;
	case EMDMCS_SendCallReject:break;
	case EMDMCS_WaitCallReject:break;
	case EMDMCS_SendCall_HangUp:break;
	case EMDMCS_WaitCall_HangUp:break;
	case EMDMCS_SendCallHold:break;
	case EMDMCS_WaitCallHold:break;
	case EMDMCS_SendCallMerge:break;
	case EMDMCS_WaitCallMerge:break;
	case EMDMCS_SendAll_HangUp:
		if(UBL_SendCommand(true, UBL_CMD_SetVoiceHangupAll) != 0)
			UbloxHandler_SetMdmCallState(EMDMCS_WaitAll_HangUp);
	break;
	case EMDMCS_WaitAll_HangUp:
		if(IsAnyActiveCall() == false)
			UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
	break;
	case EMDMCS_SendDial:
        if (ACM_Emer_TTSEnded()){ //do not start dial (from message descriptor) until TTS has ended
            if(SMDM_OutMsgDialInfo.ty == EMSGMT_Assistance_button_Press_Alert_Message)
            {
                if ((Smdm.SeqNum.CallSeqNum = UBL_SendCommandOverride(false, AssistanceCallback ,UBL_CMD_SetVoiceDial ,SMDM_OutMsgDialInfo.DialNumber)) != 0)
                {
                    UbloxHandler_SetMdmCallState(EMDMCS_WaitDial);
                }
            }
            else if(SMDM_OutMsgDialInfo.ty ==  EMSGMT_Emergency_button_press_Alert_Message || SMDM_OutMsgDialInfo.ty == EMSGMT_Accident_Alert_Message)
            {
                if ((Smdm.SeqNum.CallSeqNum = UBL_SendCommandOverride(false, EmergencyCallback , UBL_CMD_SetVoiceDial, SMDM_OutMsgDialInfo.DialNumber)) != 0)
                {
                    UbloxHandler_SetMdmCallState(EMDMCS_WaitDial);
                }
            }
            else
            {
                UbloxHandler_SetMdmCallState(EMDMCS_CallError);
            }
        }
	break;
	case EMDMCS_WaitDial:
		if (UBL_GetCommandResponse(Smdm.SeqNum.CallSeqNum, &response) == ATC_Ok) {
			if (response == EAR_Ok){
				timeout = TmrGetTime_ms();
				UbloxHandler_SetMdmCallState(EMDMCS_WaitCallEstablished);
			}
			else if(response == EAR_Timeout || response == 	EAR_Error /*|| response == EAR_Undefined*/){
				UbloxHandler_SetMdmCallState(EMDMCS_CallError);
			}
		}
		else
		{
			UbloxHandler_SetMdmCallState(EMDMCS_CallError);
		}
	break;
	case EMDMCS_WaitCallEstablished:
		CInfo = GetCallStatus(SMDM_OutMsgDialInfo.DialNumber , MAX_CALLES_HANDLED);
		if(CInfo != NULL){
			if(CInfo->state == EACMSTC_Active)
				UbloxHandler_SetMdmCallState(EMDMCS_ActiveCall);
			else
				timeout = TmrGetTime_ms();
		}
		else if(TmrIsTimeOccured_ms(timeout , WAIY_MDM_CALL_ACTIVITY_TIMEOUT_ms)){
			UbloxHandler_SetMdmCallState(EMDMCS_CallError);
		}

	break;
	case EMDMCS_SendDtmf:

		SplitRes = SplitOutGoingMessageToDtmfMaxSize(Sreq.CurrentMsgMgrReq.Out.DtmfStr,
		 											strlen(Sreq.CurrentMsgMgrReq.Out.DtmfStr),
													DtmfStr,
													&tmpSize,
													&Index);

		if(SplitRes == EMDMSR_ErrHeadToBig || SplitRes == EMDMSR_ErrMsgEmpty){
				UbloxHandler_SetMdmCallState(EMDMCS_CallError);
				break;
		}

		if ((Smdm.SeqNum.CallSeqNum = UBL_SendUrgentCommand(false, UBL_CMD_DtmfAndToneGeneration , DtmfStr)) != 0){
		    UbloxHandler_SetMdmCallState(EMDMCS_WaitDtmf);
		}
	break;
	case EMDMCS_WaitDtmf:
		if (UBL_GetCommandResponse(Smdm.SeqNum.CallSeqNum, &response) == ATC_Ok) {
			if (response == EAR_Ok){
				if(SplitRes == EMDMSR_SplitEnd){
					CScheme->Dtmf.Active = false;
					Sreq.CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf = EMSGOT_NoSend;
					UbloxHandler_SetMdmCallState(EMDMCS_ActiveCall);
				}
				else{
					UbloxHandler_SetMdmCallState(EMDMCS_SendDtmf);
				}
			}
			else if(response == EAR_Timeout || response == 	EAR_Error /*|| response == EAR_Undefined*/){
				UbloxHandler_SetMdmCallState(EMDMCS_CallError);
			}
		}
		else
			UbloxHandler_SetMdmCallState(EMDMCS_CallError);
	break;
	case EMDMCS_ActiveCall:
		if(CScheme->Dtmf.Active ){
			CInfo = GetCallStatus(SMDM_OutMsgDialInfo.DialNumber , MAX_CALLES_HANDLED);
			if(CInfo != NULL && CInfo->TrafficChAssigned == true ){
				Index = 0;
				UbloxHandler_SetMdmCallState(EMDMCS_SendDtmf);
			}
		}

		if(IsAnyActiveCall() == false)
			UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
		//check slcc status here
	break;
	case EMDMCS_CallError:
		if(CScheme->Call.Active == false  && CScheme->Dtmf.Active == false)
			UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
	break;
	}

return sm;
}

//receive gprs main task
//once modem alert us there is data to read we run this state machine
EMDM_GprsReceiveState UbloxHandler_OnlineGprsReceiveTask( void )
{

	static u16 seqnum = 0;
	EATC_Response response;

	static bool TriggerReceiveEvent[elementsof(Smdm.Gprs)] = {false};

    EMDM_GprsReceiveState sm = UbloxHandler_GetMdmGprsReceiveState();

	switch(sm){
	case EMDMGRS_GprsDataIdle:

		for(Smdm.GprsReadSocketIndex = 0 ; Smdm.GprsReadSocketIndex < elementsof(Smdm.Gprs) ; Smdm.GprsReadSocketIndex++)
		{
			if( Smdm.Gprs[Smdm.GprsReadSocketIndex].RxDataToRead || TriggerReceiveEvent[Smdm.GprsReadSocketIndex] ){
				Smdm.Gprs[Smdm.GprsReadSocketIndex].RxDataToRead = 0;
                TriggerReceiveEvent[Smdm.GprsReadSocketIndex] = false;
				UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_StartReadGprsData);
				break;
			}

		}

		break;
	case EMDMGRS_StartReadGprsData:
        //avoid reading from close socket
        if(Smdm.Gprs[Smdm.GprsReadSocketIndex].SocketAssigned == EMDMDSD_Undefined)
        {
            UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_GprsDataIdle);
            break;
        }

		if( UBL_GetFreeCommandsSpace() < 3 ) {
			break;
		}

		if(UBL_SendCommand( true,
                            UBL_CMD_GetReadSocketInfo,
                            Smdm.Gprs[Smdm.GprsReadSocketIndex].SocketAssigned,
                            MIN(MDM_APP_DATA_MAX_READ , (MDM_APP_DATA_MAX_LENGTH - Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Length))) != 0 &&

                            ( seqnum = UBL_SendCommand(false, UBL_CMD_GetReadSocketData)) != 0) {
                              UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_WaitReadGprsData);
       }

	break;

	case EMDMGRS_WaitReadGprsData:
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {

			if (response == EAR_Ok){
				if(Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Length)
					UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_GprsDataAvailable);
				else{
					UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_GprsDataIdle);
				}

			}
			else if (response == EAR_Timeout || response == EAR_Error) {
				UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_GprsDataIdle);
			}
		}
		else{
			UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_GprsDataIdle);
			Smdm.Gprs[Smdm.GprsReadSocketIndex].RxDataToRead = 0;
			TriggerReceiveEvent[Smdm.GprsReadSocketIndex] = false;
		}
	break;
	case EMDMGRS_GprsDataAvailable:

		for(Smdm.GprsReadSocketIndex = 0 ; Smdm.GprsReadSocketIndex < elementsof(Smdm.Gprs) ; Smdm.GprsReadSocketIndex++)
		{
			if(Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Length)
			{
				u16 TempLength = Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Length;

				if(Smdm.GprsReadSocketIndex < MAX_TCP_DESTINATIONS) {
					TriggerReceiveEvent[Smdm.GprsReadSocketIndex] =	UbloxHandler_FindNewMessage(Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Data,
                                                                                                (u16*)&Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Length,
                                                                                                (EMDM_DestSocketDef)Smdm.GprsReadSocketIndex );

                    if(TriggerReceiveEvent[Smdm.GprsReadSocketIndex] == false){
                          //Clean GPRS buffer after handling data
                          BZERO(Smdm.Gprs[Smdm.GprsReadSocketIndex].DataReceiver.Data);
                    }
                }
				else
				{
					if(Smdm.GprsReadSocketIndex == EMDMDSD_Traffic){
						UbloxHandler_SetTrafficDownloadState(EMDMGTD_NewFrameReceived);
					}
					//new http frame tell to http sm to see what we got
					else{
						UbloxHandler_SetHttpDownloadState(EMDMGHD_NewFrameReceived);
					}
				}

				if (TempLength == MDM_APP_DATA_MAX_READ) {
					TriggerReceiveEvent[Smdm.GprsReadSocketIndex] = true;
                }
			}
		}

		UbloxHandler_SetMdmGprsReceiveState(EMDMGRS_GprsDataIdle);

	break;
	default:
		// Unhandled state.
	break;
	}

	return UbloxHandler_GetMdmGprsReceiveState();
}

// open socket request according to socket status.
EMDM_ServiceState UbloxHandler_OpenSocket(EMDM_DestSocketDef dest , u16 * seqnum)
{
	EATC_Response response;
	*seqnum = 0;
	SMDM_GprsServiceInfo *pSock = &Smdm.Gprs[dest];
	EMDM_ServiceState state = pSock->ServiceState;

	char * ip = NULL;
	char * port = NULL;


	switch(state)
	{
	case EMDMSS_SocketInactive:
				//Get Real IP ( or url) and port from relevant Parameters
				switch(dest)
				{
					case EMDMDSD_GprsDestination_1:

						ip = Ublox_GetGprsDestinationNamePtr(SParameters.MessageLayer_GPRSDestination1);
						port = Ublox_GetGprsDestinationPortPtr(SParameters.MessageLayer_GPRSDestination1);
						break;
					case EMDMDSD_GprsDestination_2:
						ip = Ublox_GetGprsDestinationNamePtr(SParameters.MessageLayer_GPRSDestination2);
						port = Ublox_GetGprsDestinationPortPtr(SParameters.MessageLayer_GPRSDestination2);
						break;
					case EMDMDSD_Traffic:
						ip = Ublox_GetGprsDestinationNamePtr(Smdm.Traffic.BinariesServer);
						port = Ublox_GetGprsDestinationPortPtr(Smdm.Traffic.BinariesServer);
						break;
					case EMDMDSD_Http:
						ip = Ublox_GetGprsDestinationNamePtr(Smdm.Ota.BinariesServer);
						port = Ublox_GetGprsDestinationPortPtr(Smdm.Ota.BinariesServer);
						break;
				}

				//ErrorOnSocketOperation goes from 0 to 177 see section A.7 of AT command manual
				Smdm.Gprs[dest].ErrorOnSocketOperation = 0xFFFF; 	//Start from an unknown Error

				if(ip != NULL && port != NULL){
					*seqnum = UBL_SendCommand(	false,
												UBL_CMD_ConnectTcpSocket,
												Smdm.Gprs[dest].SocketAssigned,
												ip,
												port);
				}
	break;
	case EMDMSS_SocketSynSnd:
    case EMDMSS_SocketSynRcev:
	break;
	case EMDMSS_SocketEstablished:
		pSock->ServiceOpenTimeOut = TmrGetTime_ms();
	break;
	case EMDMSS_SocketFinWait1:
        case EMDMSS_SocketFinWait2:
        case EMDMSS_SocketCloseWait:
        case EMDMSS_SocketClosing:
	break;
	//case EMDMSS_Down:
			//UBL_SendCommand(true, UBL_CMD_CloseTcpSocket, Smdm.SocketList[dest]); //close first then Open;
	//break;
	}

	return state;
}
//get last socket error from main struct
bool UbloxHandler_IsSocketErrorOccurred( EMDM_DestSocketDef dest )
{
	bool ret = false;
	//in case of 0x00 socket open OK so no error here , if socket error is 0xffff no error returned yet
	if(Smdm.Gprs[dest].ErrorOnSocketOperation != 0x00 && Smdm.Gprs[dest].ErrorOnSocketOperation != 0xFFFF)
	{

		if( dest == EMDMDSD_Http )
		{
			UbloxHandler_SetHttpDownloadError( (u16)Smdm.Gprs[dest].ErrorOnSocketOperation );
		}
		else if(dest == EMDMDSD_Traffic)
		{
          UbloxHandler_SetTrafficDownloadError( (u16)Smdm.Gprs[dest].ErrorOnSocketOperation );
		}

        Smdm.Gprs[dest].ErrorOnSocketOperation = 0xFFFF;
		ret = true;
	}
	return  ret;
}

//reset last socket error
void ResetSocketError( EMDM_DestSocketDef dest )
{
	Smdm.Gprs[dest].LastError = EMDMSLE_Undefined;
}

//get the Destinatin from the send request made by the outgoing
EMDM_DestSocketDef UbloxHandler_GetDestinationFormSchemRequest( void )
{

	EMDM_DestSocketDef ret = EMDMDSD_Undefined;

	if(Sreq.CurrentMsgMgrReq.Out.Scheme.Fields.GprsDestination & EMSGGD_GprsDestination_1 )
		ret = EMDMDSD_GprsDestination_1;

	else if( Sreq.CurrentMsgMgrReq.Out.Scheme.Fields.GprsDestination & EMSGGD_GprsDestination_2 )
		ret = EMDMDSD_GprsDestination_2;


	//If both destinations are the same, only use the GPRS destination 2
	if( ret != EMDMDSD_Undefined && UbloxHandler_AreGprsDestinationsSame() ) {
		ret = EMDMDSD_GprsDestination_2;
	}

	return ret;
}

EMDM_CmeError UbloxHandler_GetLastCmeError( void )
{
	EMDM_CmeError err = (EMDM_CmeError)Smdm.CmeLastError;
	Smdm.CmeLastError = (u16)EMDMCME_Undefined;
	return err;
}

EMDM_CmsError GetLastCmsError( void ){
	EMDM_CmsError err = (EMDM_CmsError)Smdm.CmsLastError;
	Smdm.CmsLastError = (u16)EMDMCMS_Undefined;
	return err;
}

/*******************************************************************************
NAME:           UbloxHandler_AreGprsDestinationsSame
PARAMETERS:	    void
RETURN VALUES:  bool
DESCRIPTION:   	Returns true when the system parameters related with the GPRS
				destinations have configured the same address
*******************************************************************************/
bool UbloxHandler_AreGprsDestinationsSame(void) {
	s16 CmpResult;
	bool RetValue;

	CmpResult = strncmp( SParameters.MessageLayer_GPRSDestination1,
						 SParameters.MessageLayer_GPRSDestination2,
						 GPRS_DESTINATION_MAX_SIZE );

	RetValue = (CmpResult == 0) ? true:false;

	return RetValue;
}

void MDM_OpenSocketError( EMDM_DestSocketDef dest )
{
	Smdm.Gprs[MIN(elementsof(Smdm.Gprs) - 1 ,dest)].ServiceOpenRetries = 0;

    if(Smdm.Gprs[dest].SocketAssigned  != EMDMDSD_Undefined){
      UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[dest].SocketAssigned);
    }
    Smdm.Gprs[dest].SocketAssigned = EMDMDSD_Undefined;

    UbloxHandler_SetMdmGprsSendState( EMDMGSS_NetGprsError );
}

// and walla this is the send gprs state machine only G>O>D know what going on here.
// basiclly we have 3 sockets that we can use for send so according to the request we open the
// relevant socket and send the data.
EMDM_GprsSendState UbloxHandler_OnlineGprsSendTask( SMDM_Scheme *GScheme )
{

	static EMDM_DestSocketDef dest;
	static u8 ErrRetryCount = 0;
	static u32 start_time = 0,timeout = 0;
	static u16 seqnum = 0;
	EMDM_ServiceState SockState;
	EATC_Response response;

	EMDM_GprsSendState sm = UbloxHandler_GetMdmGprsSendState();

	if( !IsVoiceAndDataCommAllowed() &&
		(Smdm.AcmPendingDial || UbloxHandler_GetMdmCallState() != EMDMCS_CallIdle || IsAnyActiveCall() == true) ) {
		if(GScheme->Active == true)
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);

		return sm;
	}


	switch(sm)
	{
	case EMDMGSS_GprsDataIdle:


		//we give periorty to http/ftp since we just need to create socket here
		if(Smdm.Ota.HttpConnection || Smdm.Traffic.TrafficConnection || UBLDFI_GetFtpDownload2FileSystemConnectionRequest()){
			dest = Smdm.Ota.HttpConnection? EMDMDSD_Http:Smdm.Traffic.TrafficConnection? EMDMDSD_Traffic:EMDMDSD_ModemInternal;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_CheckGprsRegStatus);
			break;
		}

		// any data to send by gprs
		if( GScheme->Active )
		{
			if((dest = UbloxHandler_GetDestinationFormSchemRequest()) != EMDMDSD_Undefined)
			{
				Smdm.Gprs[MIN((elementsof(Smdm.Gprs) - 1) ,dest)].ServiceOpenRetries = 0;
				Smdm.ConnectionActivationResult = 0;		//To trigger the GRPS connection activation
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_CheckGprsRegStatus);
			}
			else
			{
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
			}

			break;
		}


		for( u8 DestIdx = 0; DestIdx < (u8)EMDMDSD_Last; DestIdx++) {

			if ( UbloxHandler_AreGprsDestinationsSame() && (EMDM_DestSocketDef)DestIdx == EMDMDSD_GprsDestination_1 ) {
				//Skip for EMDMDSD_GprsDestination_1
				continue;
			}

			if( UbloxHandler_IsSocketErrorOccurred( (EMDM_DestSocketDef)DestIdx ) &&
				Smdm.Gprs[DestIdx].SocketAssigned != EMDMDSD_Undefined ) {

				UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[DestIdx].SocketAssigned);
				//Once the socket is closed, Set the respective variables in an undefined state
				Smdm.Gprs[DestIdx].SocketAssigned = EMDMDSD_Undefined;		//Set the SocketAssigned as EMDMDSD_Undefined (0xFF)
	        	Smdm.Gprs[DestIdx].ErrorOnSocketOperation = 0xFFFF;			//Set the ErrorOnSocketOperation as undefined (0xFFFF)
			}
		}
	break;

	case EMDMGSS_CheckGprsRegStatus:

		if( Smdm.NetStat.Gprs.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gprs.NetStat == EMDMNS_RegRoam ) {
				ErrRetryCount = 0;

				if((dest != EMDMDSD_GprsDestination_1 && dest != EMDMDSD_GprsDestination_2) || Smdm.Gprs[dest].SocketAssigned == EMDMDSD_Undefined)
				{
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckConnectionStatus);
					UbloxHandler_GetLastCmeError( ); //just to clear last error
				}
				else
				{
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
					UbloxHandler_GetLastCmeError( ); //just to clear last error
				}
		}
		else {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
		}

	break;

	case EMDMGSS_CheckGprsAttachStatus:

		if( (seqnum = UBL_SendCommand(false, UBL_CMD_GetGprsAttachedState)) != 0 ) {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitCheckGprsAttachStatus);
		}

	break;

	case EMDMGSS_WaitCheckGprsAttachStatus:

		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok) {
				if(Smdm.GprsAttachStatus == EMDMAD_GprsAttach) {
					ErrRetryCount = 0;
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckConnectionStatus);
				}
				else {
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartGprsAttach);
				}
			}
			else {
				if(response == EAR_Error || response == EAR_Timeout) {
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
				}
			}
		}
		else {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
		}

	break;

	case EMDMGSS_StartGprsAttach:

		if ((seqnum = UBL_SendCommand(false, UBL_CMD_SetGprsAttachedState, EMDMAD_GprsAttach)) != 0) {
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitGprsAttach);
		}

	break;
	case EMDMGSS_WaitGprsAttach:

		// Wait for response or timeout then exit or jump to next state
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok) {
				ErrRetryCount = 0;
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckConnectionStatus);
				break;
			}
			else
			{
				if(response == EAR_Error){
					EMDM_CmeError err = UbloxHandler_GetLastCmeError( );
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
					if(err >= EMDMCME_IllegalMs && err <= EMDMCME_InvalidMobileClass){
						UbloxHandler_SetMdmState(EMDMS_StartCheckNetworkRegistration);
					}
				}
				else if(response == EAR_Timeout) {
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
				}
			}
		}
		else {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
		}

	break;

	case EMDMGSS_StartCheckConnectionStatus:

		if( Smdm.ConnectionActivationResult != 0xFFFF || TmrIsTimeOccured_ms(Smdm.ConnectionActivationTimeout, MDM_GPRS_ACTIVATION_TIMEOUT) ) {
			if ((seqnum = UBL_SendCommand(false, UBL_CMD_GetConnectionStatus, PSD_PROFILE_IDENTIFIER)) != 0) {
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitCheckConnectionStatus);
			}
		}

	break;

	case EMDMGSS_WaitCheckConnectionStatus:

		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok){
				if(Smdm.ConnectionStatus == true) {
                    if(UBLDFI_GetFtpDownload2FileSystemConnectionRequest()){
                      //in case of modem internal service we are done here
                      UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
                      //update the download2filesystem handler that we can go to next step
                      UBLDFI_SetFtpDownload2FileSystemState(EFDW_StartFtpSslConfiguretion);
                    }
                    else{
					  ErrRetryCount = 0;
                      UbloxHandler_SetMdmGprsSendState(EMDMGSS_CreateSocket);
                    }
				}
				else {
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartConnectionActivation);
				}

				ErrRetryCount = 0;
				break;
			}
			else if (response == EAR_Error ||response == EAR_Timeout){
				ErrRetryCount++;
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckConnectionStatus);
			}
		}
		else{
			ErrRetryCount++;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckConnectionStatus);
		}

		if(ErrRetryCount >= MAX_ERROR_RETRY)
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "Check Connection Status timeout\n" );
			ErrRetryCount = 0;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
		}

	break;

	case EMDMGSS_StartConnectionActivation:

		/* If this command is sent when the GPRS activation is in process due to a previous aborted command
		 * we get a +CME ERROR 4 as response. And if it is sent after receive the +UUPSDA = 0 URC, we get
		 * the +CME ERROR 3 due to the activation procedure was ended successfully. For this reason,
		 * the logic related with the "Smdm.ConnectionActivationResult" is made in the
		 * EMDMGSS_StartCheckConnectionStatus in order to get the CONNECTION STATUS before send or not
		 * this command again
		 */
		if ((seqnum = UBL_SendCommand(false, UBL_CMD_ActivatePacketSwitchedDataConfig, PSD_PROFILE_IDENTIFIER)) != 0) {
			//we send AT to abourt this command in case of timeout
			UBL_SendCommand(true, UBL_CMD_GetAtTest);
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitConnectionActivation);
		}

	break;

	case EMDMGSS_WaitConnectionActivation:

		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok) {
				//we are going to use internal http service we are done here
				if(UBLDFI_GetFtpDownload2FileSystemConnectionRequest()){
					//in case of modem internal service we are done here
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
					//update the download2filesystem handler that we can go to next step
					UBLDFI_SetFtpDownload2FileSystemState(EFDW_StartFtpSslConfiguretion);
				}
				else
				{
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_CreateSocket);
				}
				// The command response was received in the time expected, then the GPRS activation
				// was done sucessfully. In this case the ActivationResult should be set to action
				// successfully (0)
				Smdm.ConnectionActivationResult = 0;
				ErrRetryCount = 0;
				break;
			}
			else if (response == EAR_Error ||response == EAR_Timeout){

				// In case of timeout:
				// 1. Set the PDP context activation result in an undefined value (0xFFFF) and
				//    wait for the +UUPSDA URC to get the final result, and
				// 2. Start a timeout in case of URC result will be delayed too much time
				if ( response == EAR_Timeout ) {
					Smdm.ConnectionActivationResult = 0xFFFF;
					Smdm.ConnectionActivationTimeout = TmrGetTime_ms();
				}else
				{
					//Requested service option not subscribed
					if( ErrRetryCount < MAX_ERROR_RETRY )
					{
						timeout = TmrGetTime_ms();
						ErrRetryCount++;
						UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitNetworkRelaxTime);
						break;
					}
				}

				ErrRetryCount++;
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_CheckGprsRegStatus);
			}
		}
		else{
			ErrRetryCount++;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartConnectionActivation);
		}

		if(ErrRetryCount >= MAX_ERROR_RETRY)
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "WaitConnectionActivation timeout\n" );
			ErrRetryCount = 0;
			//we are done here fail to create the service
			if(UBLDFI_GetFtpDownload2FileSystemConnectionRequest()){
				//in case of modem internal service we are done here
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
				//update the download2filesystem handler that we are in error state
				UBLDFI_SetFtpDownload2FileSystemState(EFDW_DowmloadConnectionError);
			}
			else
			{
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
			}
		}

	break;
	case EMDMGSS_CreateSocket:
		if(Smdm.Gprs[dest].SocketAssigned == EMDMDSD_Undefined){
			if((seqnum = UBL_SendCommand(false, UBL_CMD_CreateTcpSocket)) != 0){
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitCreateSocket);
			}
		}
		else
		{
			ErrRetryCount = 0;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
		}
		break;
	case EMDMGSS_WaitCreateSocket:
		if( UBL_GetCommandResponse(seqnum, &response) == ATC_Ok ) {
			if( response == EAR_Ok ) {
				Smdm.Gprs[dest].SocketAssigned = UbloxHandler_GetTcpSocket();
				if(dest == EMDMDSD_Http){
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitStartHttpSslConfiguretion);
				}
				else{
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
				}
				ErrRetryCount = 0;
				break;
			}
			else if (response == EAR_Error ||response == EAR_Timeout){
				ErrRetryCount++;
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_CreateSocket);
			}
		}
		else{
			ErrRetryCount++;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_CreateSocket);
		}

		if(ErrRetryCount >= MAX_ERROR_RETRY)
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "WaitConnectionActivation timeout\n" );
			ErrRetryCount = 0;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
		}

		break;

	case EMDMGSS_WaitStartHttpSslConfiguretion:
			if(UBLSC_Idle == UBLSSL_GetConfiguretionState()){
				UBLSSL_SetConfiguretionState(UBLSC_ReadSecurtyLayerProfile);
                UbloxHandler_SetMdmGprsSendState(EMDMGSS_HttpSslConfiguretion);
			}
	break;

	case EMDMGSS_HttpSslConfiguretion:

			if(UBLSSL_ConfiguretionHandler(EMDMDSD_Http)){
				if(UBLSSL_GetConfiguretionState() == UBLSC_Ok) {
					ErrRetryCount = 0;
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
				}else
				{
					UbloxHandler_SetHttpDownloadError(EMDMGHDE_SocketError);
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
				}
				UBLSSL_SetConfiguretionState(UBLSC_Idle);
			}

	break;

	case EMDMGSS_StartCheckSocketService:

		// Only send the UBL_CMD_SocketControlQueryForTcpSocketStatus when the socket is open or when the socket opening is in process
		if( Smdm.Gprs[dest].SocketAssigned != EMDMDSD_Undefined &&
			(Smdm.Gprs[dest].ErrorOnSocketOperation == 0x0000 || Smdm.Gprs[dest].ErrorOnSocketOperation == 0xFFFF) )
		{
			if ((seqnum = UBL_SendCommand(false, UBL_CMD_SocketControlQueryForTcpSocketStatus, Smdm.Gprs[dest].SocketAssigned)) != 0) {
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitCheckSocketService);
			}
		}
		else {
			// Smdm.Gprs[dest].SocketAssigned = EMDMDSD_Undefined when the socket is closed undexpectely
			// Smdm.Gprs[dest].ErrorOnSocketOperation != 0x0000 (and != 0xFFFF) when the socket opening process was not ended successful
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
		}

	break;

	case EMDMGSS_WaitCheckSocketService:
		// Wait for response or timeout then exit or jump to next state
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok){

				UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartOpenSocketService);
				break;
			}
			else if( response == EAR_Error )
            {
				//if we get error here update relevant hendler for error and go back to Idle if needed.
				if(dest == EMDMDSD_Traffic){
					UbloxHandler_SetTrafficDownloadError(EMDMGTDE_AtCmdFail);
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
				}
				else if(dest == EMDMDSD_Http){
					UbloxHandler_SetHttpDownloadError(EMDMGHDE_AtCmdFail);
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
				}
				else{
	                UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
				}

                 Smdm.Gprs[dest].SocketAssigned = EMDMDSD_Undefined;
            }
            else if( response == EAR_Timeout )
			{
				ErrRetryCount++;
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
			}
		}
		else {
			ErrRetryCount++;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
		}

		if(ErrRetryCount >= MAX_ERROR_RETRY)
		{
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "WaitCheckSocketService timeout\n" );
			ErrRetryCount = 0;

		}

        break;

	case EMDMGSS_StartOpenSocketService:

		SockState =  UbloxHandler_OpenSocket(dest , &seqnum) ;

		if( SockState == EMDMSS_SocketEstablished ) {
			if(dest != EMDMDSD_Http && dest != EMDMDSD_Traffic )
			{
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartSendGprsData);
            }
			else
			{
				if(dest == EMDMDSD_Http && Smdm.Ota.HttpConnection){
					Smdm.Ota.HttpConnection = false;
				}

				if(dest == EMDMDSD_Traffic && Smdm.Traffic.TrafficConnection){
					Smdm.Traffic.TrafficConnection = false;
				}
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
			}
		}
		else if(UbloxHandler_IsSocketErrorOccurred(dest) == true) {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
		}
		//else if(SockState ==  EMDMSS_SocketUndefined) {
		//	break;
		//}
		else {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitOpenSocketService);
		}

	break;

	case EMDMGSS_WaitOpenSocketService:

		//detect cases where we send the command AT+USOCO and get cme error
	   	if (seqnum && UBL_GetCommandResponse(seqnum, &response) == ATC_Ok)
		{
			if(response == EAR_Ok) {
				Smdm.Gprs[dest].ServiceOpenTimeOut = TmrGetTime_ms();
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitOpenSocketConnection);
			}
			else if( response == EAR_Error  || response == EAR_Timeout ) {
            	if( dest == EMDMDSD_Http )
				{
					UbloxHandler_SetHttpDownloadError( (u16)Smdm.Gprs[dest].ErrorOnSocketOperation );
				}
				else if(dest == EMDMDSD_Traffic)
				{
          			UbloxHandler_SetTrafficDownloadError( (u16)Smdm.Gprs[dest].ErrorOnSocketOperation );
				}
				//On error clear and close socket and jump to EMDMGSS_NetGprsError
				MDM_OpenSocketError(dest);
			}
		}
		else {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
		}

	break;

	case EMDMGSS_WaitOpenSocketConnection:

		//Socket Connection in Process
		if( Smdm.Gprs[dest].ErrorOnSocketOperation == 0xFFFF ) {
			//Test here if socket was change in case of emargency or if send was ended
			if(GScheme->Active == false && dest != EMDMDSD_Http && dest != EMDMDSD_Traffic)
			{
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
				break;
			}
			//Wait until 3 minutes to get the Socket Connection response
			if( TmrIsTimeOccured_ms(Smdm.Gprs[dest].ServiceOpenTimeOut, SOCKET_CONNECTION_TIMEOUT) ) {
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
			}
			start_time = TmrGetTime_ms();
		}
		//Socket Connection Ended
		else {
			if( TmrIsTimeOccured_ms(start_time, 1000) ) {
				//Ended with error
				if( Smdm.Gprs[dest].ErrorOnSocketOperation != 0x0000 || Smdm.Gprs[dest].SocketAssigned == EMDMDSD_Undefined ) {
					//Generally when the the socket opening ends with error, the +UUSOCL URC is also received and therefore,
					//the Smdm.Gprs[dest].SocketAssigned is set to EMDMDSD_Undefined

					Smdm.Gprs[dest].ErrorOnSocketOperation = 0xFFFF;
					if ( Smdm.Gprs[dest].ServiceOpenRetries < MAX_OPEN_SOCKET_RETRY ) {
						// Try to open the socket again
						ErrRetryCount = 0;
						UbloxHandler_SetMdmGprsSendState(EMDMGSS_CreateSocket);
					}
					else {
						Smdm.Gprs[dest].ServiceOpenRetries = 0;
						Log(LOG_SUB_MDM,LOG_LVL_WARN,"Socket open retry max reached, closing socket %d\n", dest);
						//On error clear and close socket and jump to EMDMGSS_NetGprsError
						MDM_OpenSocketError(dest);
						break;
					}
                    Smdm.Gprs[dest].ServiceOpenRetries++;
				}
				//Ended Successfully
				else {
					// Verify the Socket Service
					ErrRetryCount = 0;
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartCheckSocketService);
				}
			}
		}

	break;


	case EMDMGSS_StartSendGprsData:

		if( !GScheme->Active || !Sreq.CurrentMsgMgrReq.Out.length )
		{
			 UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendAckOk);
		}
		else if(Smdm.Gprs[dest].SocketAssigned == EMDMDSD_Undefined){
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
		}
		/*
		To send the GPRS data, we add the following commands to the Queue:
		1) UBL_CMD_SetWriteSocketInfo with DISPOSE flag = true
		2) UBL_CMD_SetWriteSocketData with DISPOSE flag = false
		*/
		else if((seqnum = UBL_SendIsData(false, Smdm.Gprs[dest].SocketAssigned, Sreq.CurrentMsgMgrReq.Out.length - LOGGER_HEADER_LENGTH)) != 0)
		{
			Smdm.Gprs[MIN((elementsof(Smdm.Gprs) - 1) ,dest)].TxWaitAck = false;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitSendGprsData);
		}

	break;
	case EMDMGSS_WaitSendGprsData:
		//Here we wait for the response of the UBL_CMD_SetWriteSocketData command
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok){
				timeout = TmrGetTime_ms();
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsOk);
				ErrRetryCount = 0;
			}
			else if (response == EAR_Error || response == EAR_Timeout) {
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
				Log(LOG_SUB_MDM,LOG_LVL_WARN, "SendGprsError\n" );
			}
            // In case that socket is closed unexpectedly
            else if ( Smdm.Gprs[dest].SocketAssigned == EMDMDSD_Undefined ) {
				/*
				If we enter here, the UBL_CMD_SetWriteSocketInfo command was disposed due to
				the +CME ERROR response from modem, and therefore there is pending in the Queue
				only the UBL_CMD_SetWriteSocketData command.
				*/
                ATC_ClosePendingCommand(&UBL_ATC);							//Close UBL_CMD_SetWriteSocketData
                UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);	//Jump to the GPRS error state
                Log(LOG_SUB_MDM,LOG_LVL_WARN, "SendGprsError - Socket closed unexpectedly\n" );
            }
		}
		else
		{
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendGprsError);
			Log(LOG_SUB_MDM,LOG_LVL_WARN, "SendGprsError\n" );
		}

	break;

	case EMDMGSS_SendGprsOk:

		if(true == Smdm.Gprs[MIN((elementsof(Smdm.Gprs) - 1) ,dest)].TxWaitAck){
			ErrRetryCount = 0;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_SendAckOk);
			break;
		}

		if(UbloxHandler_IsSocketErrorOccurred(dest) == true ||
		   Smdm.Gprs[dest].SocketAssigned == EMDMDSD_Undefined){

			ErrRetryCount = 0;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
			if(Smdm.Gprs[dest].SocketAssigned != EMDMDSD_Undefined){
				UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[dest].SocketAssigned);
			}

			break;
		}


		if(TmrIsTimeOccured_ms(timeout , 1000)){
			timeout = TmrGetTime_ms();
			if(MAX_WAIT_TCP_ACK_RETRY >= ++ErrRetryCount)
				UBL_SendUrgentCommand(true, UBL_CMD_GetTcpOutgoingUnacknowledgedData, Smdm.Gprs[dest].SocketAssigned);
			else{
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_NetGprsError);
				if( Smdm.Gprs[dest].SocketAssigned != EMDMDSD_Undefined ) {
					UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[dest].SocketAssigned);
					Smdm.Gprs[dest].SocketAssigned = EMDMDSD_Undefined;
				}
				Log(LOG_SUB_MDM,LOG_LVL_WARN, "EMDMGSS_SendGprsOk error\n" );
			}
			break;
		}

	break;
	case EMDMGSS_SendAckOk:
	case EMDMGSS_SendGprsError:
	case EMDMGSS_NetGprsError:

		if(GScheme->Active == false)
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);

		if( dest == EMDMDSD_Http && Smdm.Ota.HttpConnection )
		{
			Smdm.Ota.HttpConnection = false;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
			if( sm == EMDMGSS_SendGprsError || sm == EMDMGSS_NetGprsError )
			{
				UbloxHandler_SetHttpDownloadError( EMDMGHDE_SocketError );
			}
		}


		if( dest == EMDMDSD_Traffic && Smdm.Traffic.TrafficConnection )
		{
			Smdm.Traffic.TrafficConnection = false;
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
			if( sm == EMDMGSS_SendGprsError || sm == EMDMGSS_NetGprsError )
			{
				UbloxHandler_SetTrafficDownloadError( EMDMGTDE_SocketError );
			}
		}

        if(dest == EMDMDSD_ModemInternal && UBLDFI_GetFtpDownload2FileSystemConnectionRequest())
        {
        	//in case of modem internal service we are done here
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
        	if( sm == EMDMGSS_SendGprsError || sm == EMDMGSS_NetGprsError )
        	{
				//update the download2filesystem handler that we are in error state
				UBLDFI_SetFtpDownload2FileSystemState(EFDW_DowmloadConnectionError);
        	}
        }

	break;

	case EMDMGSS_CloseCurrentService:

		dest = UbloxHandler_GetDestinationFormSchemRequest();
		if(Smdm.Gprs[dest].SocketAssigned  != EMDMDSD_Undefined){
			UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[dest].SocketAssigned);
		}
		Smdm.Gprs[dest].SocketAssigned = EMDMDSD_Undefined;
		UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);

	break;

	case EMDMGSS_StartGprsCloseService:

		if (UBL_GetFreeCommandsSpace() < 3)
			break;

		if(Smdm.Gprs[EMDMDSD_GprsDestination_1].SocketAssigned != EMDMDSD_Undefined){
			UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[EMDMDSD_GprsDestination_1].SocketAssigned);
		}

		if(Smdm.Gprs[EMDMDSD_GprsDestination_2].SocketAssigned != EMDMDSD_Undefined){
			UBL_SendUrgentCommand(true, UBL_CMD_CloseTcpSocket, Smdm.Gprs[EMDMDSD_GprsDestination_2].SocketAssigned);
		}

		Smdm.Gprs[EMDMDSD_GprsDestination_1].SocketAssigned = EMDMDSD_Undefined;
		Smdm.Gprs[EMDMDSD_GprsDestination_2].SocketAssigned = EMDMDSD_Undefined;

		/* After close a socket, not possible to use get the service state by usign the AT command
		   (UBL_CMD_SocketControlQueryForTcpSocketStatus), then the status is forced to inactive */
		Smdm.Gprs[EMDMDSD_GprsDestination_1].ServiceState = EMDMSS_SocketInactive;
		Smdm.Gprs[EMDMDSD_GprsDestination_2].ServiceState = EMDMSS_SocketInactive;

		timeout = TmrGetTime_ms();
		UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitGprsCloseService);

	break;
	case EMDMGSS_WaitGprsCloseService:

		if(UbloxHandler_IsSocketsAreClosed() == true){
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
		}

		if(TmrIsTimeOccured_ms(timeout , 3000)){
			timeout = TmrGetTime_ms();
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
		}

	break;
	case EMDMGSS_StartGprsDetach:

		if ((seqnum = UBL_SendCommand(false, UBL_CMD_SetGprsAttachedState, EMDMAD_GprsDetach)) != 0) {
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_WaitGprsDetach);
		}

	break;
	case EMDMGSS_WaitGprsDetach:

		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok || response == EAR_Error || response == EAR_Timeout){
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
			}
		}

	break;
	case EMDMGSS_StartGprsCloseConnection:
	break;
	case EMDMGSS_WaitGprsCloseConnection:
	break;
	case EMDMGSS_WaitNetworkRelaxTime:
		if(TmrIsTimeOccured_ms(timeout , 5000)){
			UbloxHandler_SetMdmGprsSendState(EMDMGSS_CheckGprsRegStatus);
		}
		break;
	default:
	break;

	}

	return UbloxHandler_GetMdmGprsSendState();

}

EUBL_RingTone UbloxHandler_GetRingtone(void)
{
	return Smdm.Ringtone;
}
void UbloxHandler_SetRingtone(EUBL_RingTone Ringtone)
{
	Smdm.Ringtone = Ringtone;
}

void UbloxHandler_SetPendingSamuCallOnFailure (bool val){
    PendingSamuCallOnFailure = val;
}

void UbloxHanlder_SetRingToneSelectionState(EMDM_RingToneSelectionState NextState)
{
	if( RingToneSelection.sm != NextState ) {
		RingToneSelection.sm = NextState;
	}
}

bool UbloxHandler_RingToneSelection(void)
{
	bool ret;
	EATC_Response CmdResponse;

	ret = false;
	UbloxHandler_SetRingtone(EUBL_RingTone_Undefined);

	switch(RingToneSelection.sm)
	{
		case EMDMRTS_Idle:
			UbloxHanlder_SetRingToneSelectionState(EMDMRTS_StarToneSelection);
		break;

		case EMDMRTS_StarToneSelection:
			if( SParameters.SystemParameters_PNG_RingTone  <= EUBL_RingTone_EndDefaults ) {
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_SelectDefaultRingTone);
			}
			else if ( SParameters.SystemParameters_PNG_RingTone >= EUBL_RingTone_StarPersonalized && SParameters.SystemParameters_PNG_RingTone  <= EUBL_RingTone_EndPersonalized) {
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_EnablePersonalized);
			}
			else {
				Log(LOG_SUB_MDM, LOG_LVL_ERROR, "Ringtone selected is out of expected range. Default ringtone %d will be used\n", EUBL_RingTone_1 );
				ChangeParameterValue(SystemParameters_PNG_RingTone, (u32)EUBL_RingTone_1);
				//Remain in this state until the parameter will be one of the last verifications
			}
		break;

		case EMDMRTS_SelectDefaultRingTone:
			RingToneSelection.CmdId = UBL_CMD_DefaultRingToneSelection;
			RingToneSelection.CmdSeqnum = UBL_SendCommand(false, (u16)RingToneSelection.CmdId, SParameters.SystemParameters_PNG_RingTone);
			if( RingToneSelection.CmdSeqnum != 0 ) {
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_WaitSelectDefaultRingTone);
			}
		break;

		case EMDMRTS_WaitSelectDefaultRingTone:
			if( UBL_GetCommandResponse(RingToneSelection.CmdSeqnum, &CmdResponse) == ATC_Ok ) {
				if( CmdResponse == EAR_Ok ) {
					RingToneSelection.RetryCount = 0;
					UbloxHanlder_SetRingToneSelectionState(EMDMRTS_Idle);
					ret = true;
				}
				else if( CmdResponse == EAR_Timeout || CmdResponse == EAR_Error ) {
					RingToneSelection.RetryCount++;
					UbloxHanlder_SetRingToneSelectionState(EMDMRTS_SelectDefaultRingTone);
				}
			}
			else {
				RingToneSelection.RetryCount++;
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_SelectDefaultRingTone);
			}

			if(RingToneSelection.RetryCount > 3) {
				RingToneSelection.RetryCount = 0;
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_Error);
			}
		break;


		case EMDMRTS_EnablePersonalized:
			//Default ringtone should be disabled to allow the personalized ringtones
			RingToneSelection.CmdId = UBL_CMD_DefaultRingToneDisable;
			RingToneSelection.CmdSeqnum = UBL_SendCommand(false, (u16)RingToneSelection.CmdId);
			if( RingToneSelection.CmdSeqnum != 0 ) {
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_WaitEnablePersonalized);
			}
		break;

		case EMDMRTS_WaitEnablePersonalized:
			if( UBL_GetCommandResponse(RingToneSelection.CmdSeqnum, &CmdResponse) == ATC_Ok ) {
				if( CmdResponse == EAR_Ok ) {
					RingToneSelection.RetryCount = 0;
					UbloxHanlder_SetRingToneSelectionState(EMDMRTS_Idle);
					ret = true;
				}
				else if( CmdResponse == EAR_Timeout || CmdResponse == EAR_Error ) {
					RingToneSelection.RetryCount++;
					UbloxHanlder_SetRingToneSelectionState(EMDMRTS_EnablePersonalized);
				}
			}
			else {
				RingToneSelection.RetryCount++;
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_EnablePersonalized);
			}

			if(RingToneSelection.RetryCount > 3) {
				RingToneSelection.RetryCount = 0;
				UbloxHanlder_SetRingToneSelectionState(EMDMRTS_Error);
			}
		break;

		case EMDMRTS_Error:
			Log(LOG_SUB_MDM, LOG_LVL_INFO, "Ringtone selection error while sending the %d command\n", RingToneSelection.CmdId);
			UbloxHanlder_SetRingToneSelectionState(EMDMRTS_Idle);
		break;

		default:
		break;
	}

	return ret;


}


bool IsGprsCriticalSection( void )
{
	EMDM_GprsSendState Send = UbloxHandler_GetMdmGprsSendState();
	EMDM_GprsReceiveState Recv = UbloxHandler_GetMdmGprsReceiveState();
	return ((Send > EMDMGSS_CriticalSectionStart && Send < EMDMGSS_CriticalSectionEnd) || (Recv > EMDMGRS_CriticalSectionStart && Recv < EMDMGRS_CriticalSectionEnd))? true:false;
}

bool IsSmsCriticalSection( void )
{
	EMDM_SmsSendState Send = UbloxHandler_GetMdmSmsSendState();
	return ((Send > EMDMSSS_CriticalSectionStart && Send < EMDMSSS_CriticalSectionEnd))? true:false;
}

//receive sms state machina
//we use this machine only after modem power up.
//all other sms reads are handled in the low level of the modem drive
//her we just read sms untill no more sms in the modem.


void UbloxHandler_OnlineSmsReceiveTask( void )
{
	static bool bIsReadFromURC = false;
	static u16 SmsIndex = 0;
	static u16 SmsHandled = 0;
	static u32 pullTimeout = 0;
	EATC_Response response;
	static u16 seqnum = 0;
	EMDM_SmsReceiveState sm;

	sm = UbloxHandler_GetMdmSmsReceiveState();

	switch(sm)
	{
	case EMDMSRS_SmsIdle:

		if( Smdm.sms.rx.RxInfo.Index > 0 ) {
			bIsReadFromURC = true;
			SmsIndex = Smdm.sms.rx.RxInfo.Index;
			Smdm.sms.rx.RxInfo.Index = 0;
			pullTimeout = TmrGetTime_ms();
			UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_StartReadSms);
		}
		else if (Smdm.sms.rx.RxInfo.Pending > 0) {
			bIsReadFromURC = false;
			SmsIndex = 1;		//SMSes reading start from 1
			UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_StartReadSms);
		}

		if( !bIsReadFromURC ) {
			if( TmrIsTimeOccured_ms(pullTimeout, MDM_SMS_POLL_INTERVAL) &&
				!IsGprsCriticalSection() && !IsSmsCriticalSection() ){
				pullTimeout = TmrGetTime_ms();
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_pullPendingSms);
			}
		}

	break;
	case EMDMSRS_pullPendingSms:
		if((seqnum = UBL_SendCommand(false, UBL_CMD_CheckPendingSms)) != 0){
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_WaitPendingSms);
		}
	break;
	case EMDMSRS_WaitPendingSms:
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if (response == EAR_Error || response == EAR_Ok || response == EAR_Timeout) {
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
			}
		}
		else
			UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
	break;

	case EMDMSRS_StartReadSms:
		if (UBL_GetFreeCommandsSpace() < 3)
			break;

		Smdm.sms.rx.RxInfo.MT_emptySpot = false;

		if( (UBL_SendCommand(true, UBL_CMD_GetReadSmsInfo, SmsIndex)) != 0 &&
			(seqnum = UBL_SendCommand(false, UBL_CMD_GetReadSmsData)) != 0 ) {
			UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_WaitReadSms);
		}
	break;
	case EMDMSRS_WaitReadSms:
		// Wait for response or timeout then exit or jump to next state
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if (response == EAR_Ok) {
				if( !bIsReadFromURC ) {
					SmsHandled++;
				}
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_StartDeletSms);
			}
		}
		else {
			if( !bIsReadFromURC ) {
				if( SmsIndex < Smdm.sms.rx.RxInfo.MaxLocations &&
					SmsHandled < Smdm.sms.rx.RxInfo.Pending ) {
					// Continue reading the SMSes
					SmsIndex++;
					UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_StartReadSms);
				}
				else {
					/* When the SmsHandled = Smdm.sms.rx.RxInfo.Pending means that all Pending
					   SMSes were handled and thefore there is no need to read all Locations
					   in the modem memory */
					Smdm.sms.rx.RxInfo.MaxLocations = 0;
					Smdm.sms.rx.RxInfo.Pending = 0;
					SmsIndex = 0;
					SmsHandled = 0;
					UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
				}
			}
			else {
				SmsIndex = 0;
				bIsReadFromURC = false;
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
			}
		}

	break;

	case EMDMSRS_StartDeletSms:
		if( Smdm.sms.rx.RxInfo.MT_emptySpot == false){
			if((seqnum = UBL_SendUrgentCommand(false, UBL_CMD_SetDeleteSms, SmsIndex)) != 0){
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_WaitDeletSms);
			}
		}
	break;

	case EMDMSRS_WaitDeletSms:
		// Wait for response or timeout then exit or jump to next state
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if( response == EAR_Ok || response == EAR_Error || response == EAR_Timeout ) {
				if( !bIsReadFromURC ) {
					if( SmsIndex < Smdm.sms.rx.RxInfo.MaxLocations &&
						SmsHandled < Smdm.sms.rx.RxInfo.Pending ) {
						// Continue reading the SMSes
						SmsIndex++;
						UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_StartReadSms);
					}
					else {
						/* When the SmsHandled = Smdm.sms.rx.RxInfo.Pending means that all Pending
						   SMSes were handled and thefore there is no need to read all Locations
						   in the modem memory */
						Smdm.sms.rx.RxInfo.MaxLocations = 0;
						Smdm.sms.rx.RxInfo.Pending = 0;
						SmsIndex = 0;
						SmsHandled = 0;
						UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
					}
				}
				else {
					SmsIndex = 0;
					bIsReadFromURC = false;
					UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
				}
			}
		}
		else {
			if( !bIsReadFromURC ) {
				if( SmsIndex < Smdm.sms.rx.RxInfo.MaxLocations &&
					SmsHandled < Smdm.sms.rx.RxInfo.Pending ) {
					// Continue reading the SMSes
					SmsIndex++;
					UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_StartReadSms);
				}
				else {
					/* When the SmsHandled = Smdm.sms.rx.RxInfo.Pending means that all Pending
					   SMSes were handled and thefore there is no need to read all Locations
					   in the modem memory */
					Smdm.sms.rx.RxInfo.MaxLocations = 0;
					Smdm.sms.rx.RxInfo.Pending = 0;
					SmsIndex = 0;
					SmsHandled = 0;
					UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
				}
			}
			else {
				SmsIndex = 0;
				bIsReadFromURC = false;
				UbloxHandler_SetMdmSmsReceiveState(EMDMSRS_SmsIdle);
				}
		}
	break;
	default:
	break;
	}
}

//semd sms - get the next fild size
static bool ParseNextField( const char*         pMessage,
							u16* 		        ConvertedLength,
							u16* 		        OriginalLength,
							EMDM_SmsDcsFormat   DataFormat )
{
	bool bEndOfField = false;

	*ConvertedLength = 0;
	*OriginalLength = 0;

	// Check if current character is a end of data delimiter
	if ( !memcmp( pMessage, END_OF_DATA_CHARACTER, END_OF_DATA_CHARACTER_LENGTH ) )
	{
		return true;
	}

	do
	{
		// calculate converted character length
		u16 Codepoint;
		u8 CharLen;
		if ( !UTF8_GetCurrentCharacterInfo( pMessage, &Codepoint, &CharLen ) )
		{
			// The current character is not a valid UTF-8 character. This shouldn't have happened,
			// and there's nothing we can do about it. Tell the calling layer that this SMS message
			// has ended.
			return true;
		}
		*OriginalLength += CharLen;
		if ( DataFormat == EMDMDF_7bit )
		{
          *ConvertedLength += Pdu::Get7BitCharSize( (u8)Codepoint );
		}
		else
		{
			*ConvertedLength += 2;
		}
		// Check if current character is a field delimiter
		if ( !memcmp( pMessage, FIELD_SEPARATOR, FIELD_SEPARATOR_LEN ) )
		{
			bEndOfField = true;
		}
		pMessage += CharLen;
	} while ( !bEndOfField );
	return false;
}

//send sms  - get the header size
static u16 FindHeaderSize( const char* pSource, u16 SrcSize )
{
	const char* pHeaderEnd = (const char*)FindPattern( (u8*)pSource, END_OF_DID, SrcSize, END_OF_DID_LENGTH );

	if ( pHeaderEnd )
	{
		return pHeaderEnd - pSource + 1;
	}
	else
	{
		return 0;
	}
}

static u16 convert_utf8_to_sms_format( 	const char*	pInput,
										u16 		InputSizeInBytes,
										u8*			pDest,
										u16 		DestSize,
										EMDM_SmsDcsFormat DataFormat )
{
	switch ( DataFormat )
	{
		case EMDMDF_7bit:
            return Pdu::convert_utf8_to_7bit( pInput, InputSizeInBytes, pDest );
		case EMDMDF_16bit:
			return UTF8_ToUCS2BE( (u16*)pDest, DestSize / 2, pInput, InputSizeInBytes ) * 2 ;
		default:
			// unsupported encoding format.
			break;
	}
	assert( 0 );
	return 0;
}

//send sms add suffix to message (if it was split)
u16 AddOfficeSuffix( u8 *pOutputMessageBuffer, u8 MessageChecksum, EMDM_SmsDcsFormat DataFormat, u16 OutputBufferSize )
{
	char Temp[ CHECKSUM_MAX_LENGTH * 2 ];

	// Put message termination
	u16 Length = convert_utf8_to_sms_format(	END_OF_DATA_CHARACTER,
												END_OF_DATA_CHARACTER_LENGTH,
												pOutputMessageBuffer,
												OutputBufferSize,
												DataFormat );

	// Add the checksum number.
	b64ConvertInt(	MessageChecksum,
					Temp,
					CHECKSUM_MAX_LENGTH );

	Length += convert_utf8_to_sms_format(	Temp,
											CHECKSUM_MAX_LENGTH,
											pOutputMessageBuffer + Length,
											OutputBufferSize - Length,
											DataFormat );

	Length += convert_utf8_to_sms_format(	END_OF_MESSAGE,
											END_OF_MESSAGE_LENGTH,
											pOutputMessageBuffer + Length,
											OutputBufferSize - Length,
											DataFormat );

	return Length;
}

// send sms - since some time the sms size is larger then 160 bytes we have to split them
// here we build the new header
u16 BuildNewSmsHeader( 	const char* HeaderSrc,
						u16 HeaderSize,
						u8* HeaderDest,
						EMDM_SmsDcsFormat DataFormat,
						u8* MessageChecksum )
{
	u16 ConvertedSize;

	// Take the existing header as the new header of the SMS.
	ConvertedSize = convert_utf8_to_sms_format( HeaderSrc, HeaderSize, HeaderDest, SMS_MAX_SIZE, DataFormat );

	*MessageChecksum ^= MSG_Calculate_Checksum( HeaderSrc, HeaderSize );

	return ConvertedSize;
}

static u16 GetMaxSMSSizeForDataFormat( EMDM_SmsDcsFormat DataFormat )
{
	switch ( DataFormat )
	{
		case EMDMDF_7bit:
			return SMS_MAX_SIZE;
		case EMDMDF_8bit:
		case EMDMDF_16bit:
			return SMS_MAX_SIZE * 7 / 8;
	}
	return 0;
}

static u16 GetSMSBytesPerCharacter( EMDM_SmsDcsFormat DataFormat )
{
	switch ( DataFormat )
	{
		case EMDMDF_7bit:
		case EMDMDF_8bit:
			return 1;
		case EMDMDF_16bit:
			return 2;
	}
	return 0;
}

//send sms here we split the message
EMDM_SplitResult SplitOutGoingMessageToSmsMaxSize(	const char*	        src,
													u16 		        SrcSize,
													EMDM_SmsDcsFormat   DataFormat,
													u8* 		        dest,
													u16*		        DestSize,
													u16*		        Index )
{
	// Build header of new SMS
	u8 MessageChecksum = 0;
	u16 HeaderSizeInBytes = FindHeaderSize( src,SrcSize );

	if ( HeaderSizeInBytes == 0 )
	{
		// Error. We dId not find a header in the original message. Skip it...
		return EMDMSR_ErrMsgEmpty;
	}
	u16 HeaderNewSizeInBytes = BuildNewSmsHeader( 	src,
													HeaderSizeInBytes,
													dest,
													DataFormat,
													&MessageChecksum );

	u16 BytesOccupiedByHeaderAndFooter = 	HeaderNewSizeInBytes +
											OFFICE_MSG_SUFFIX_LENGTH * GetSMSBytesPerCharacter( DataFormat );

	u16 MaxSMSMessageInBytes = GetMaxSMSSizeForDataFormat( DataFormat );

	if ( BytesOccupiedByHeaderAndFooter >= MaxSMSMessageInBytes )
	{
		return EMDMSR_ErrHeadToBig;
	}
	u16 TotalBytesForData = MaxSMSMessageInBytes - BytesOccupiedByHeaderAndFooter;
	u16 BytesLeftForData = TotalBytesForData;

	u16 DestIndex = HeaderNewSizeInBytes;
	EMDM_SplitResult res = EMDMSR_SplitOnGoing;
	u16 MsgPrevIndex = *Index;

	if ( MsgPrevIndex == 0 )
	{
		// This is the first field in the original SMS that we're processing.
		MsgPrevIndex = HeaderSizeInBytes;
	}

	// Take fields from SMS until exhusting all space in output buffer
	while ( BytesLeftForData )
	{
		u16 OriginalLength;
		u16 ConvertedLengthInBytes;

		if ( ParseNextField( src + MsgPrevIndex, &ConvertedLengthInBytes, &OriginalLength, DataFormat ) )
		{
			// Reached end of the input message.
			res = EMDMSR_SplitEnd;
			break;
		}
		else if ( ConvertedLengthInBytes >= TotalBytesForData )
		{
			// This field is too long. Just skip it.
			ErrorReport( ERR_OUTGOING_FIELD_TOO_LONG_FOR_SMS );
			MsgPrevIndex += OriginalLength;
		}
		else if ( ConvertedLengthInBytes < BytesLeftForData )
		{
			MessageChecksum ^= MSG_Calculate_Checksum( src + MsgPrevIndex, OriginalLength );

			// Add current field to the dest SMS.
			convert_utf8_to_sms_format(	src + MsgPrevIndex,
										OriginalLength,
										dest + DestIndex,
										ConvertedLengthInBytes + 2,
										DataFormat );
			MsgPrevIndex += OriginalLength;
			DestIndex += ConvertedLengthInBytes;
			BytesLeftForData -= ConvertedLengthInBytes;
		}
		else
		{
            // Not enough space in destination buffer.
			break;
		}
	}
	// End the SMS with suffix.
	DestIndex += AddOfficeSuffix( dest + DestIndex, MessageChecksum, DataFormat, SMS_MAX_SIZE );
	*DestSize = DestIndex;
	*Index = MsgPrevIndex;
	return res;
}

//ok this is the main send sms state machine task
void UbloxHandler_OnlineSmsSendTask( SMDM_Scheme *SScheme )
{
	static u16 seqnum = 0;
	static u16 Index = 0;
	static u32 SmsSendTimeout = 0;
	static EMDM_SplitResult SplitRes;
	EATC_Response response;

	EMDM_SmsSendState sm = UbloxHandler_GetMdmSmsSendState();

	switch(sm)
	{
	case EMDMSSS_SmsIdle:
		if(SScheme->Active){
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_CheckGsmRegStatus);
			Index = 0;
		}
		break;
	case EMDMSSS_CheckGsmRegStatus:
		if (	( Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome ) ||
				( Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRoam ) )
		{
			memset(ConvertBuff , '\0' , sizeof(ConvertBuff));
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_BuildSms);
		}
		else
		{
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsError);
		}
		break;
	case EMDMSSS_BuildSms:
		{
			if ( !Sreq.CurrentMsgMgrReq.Out.Scheme.Fields.UserSMS )
			{
				u16 tmpSize;
				char *destPhone = UblGetSmsDestinationNumber();

			if (SScheme->Active == false)
			{
				UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsOk);
				return;
			}

				u16 UCS2CharIndex;

				// Get most optimized encoding for SMS. TODO: Can optimized the given length a little.
				EMDM_SmsDcsFormat DataFormat = Pdu::GetPreferedDataFormat(   (char*)(Sreq.CurrentMsgMgrReq.Out.data + Index),
													        				MIN( Sreq.CurrentMsgMgrReq.Out.length - LOGGER_HEADER_LENGTH - Index, SMS_MAX_SIZE ),
															        		&UCS2CharIndex );

				SplitRes = SplitOutGoingMessageToSmsMaxSize(	(char*)Sreq.CurrentMsgMgrReq.Out.data,
																(Sreq.CurrentMsgMgrReq.Out.length - LOGGER_HEADER_LENGTH),
																DataFormat,
																(u8*)ConvertBuff,
																&tmpSize,
																&Index);

				if (SplitRes == EMDMSR_ErrHeadToBig || SplitRes == EMDMSR_ErrMsgEmpty)
				{
					UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsError);
					break;
				}
				Pdu::EncodeMessage( (char*)destPhone, DataFormat, (u8*)ConvertBuff, tmpSize );
			}
			else
			{
				u16 UCS2CharIndex;
				SMDM_QUEUE_Data *Dataptr = (SMDM_QUEUE_Data*)Sreq.CurrentMsgMgrReq.Out.data;

				// Get most optimized encoding for SMS.
				EMDM_SmsDcsFormat DataFormat = Pdu::GetPreferedDataFormat(   (char*)Dataptr->MsgData,
                                                                            Dataptr->MsgDataSize,
                                                                            &UCS2CharIndex );

				// Convert SMS message to the selected encoding.
				convert_utf8_to_sms_format( (char*)Dataptr->MsgData,
											Dataptr->MsgDataSize,
											(u8*)ConvertBuff,
											sizeof(ConvertBuff),
											DataFormat );

				Pdu::EncodeMessage( 	(char*)Dataptr->SourcePhoneNum,
													DataFormat,
													(u8*)ConvertBuff,
													Dataptr->MsgDataSize);
				SplitRes = EMDMSR_SplitEnd;
			}
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_StartSendSms);
		}

	//*************************
	//NO BREAK HERE FOR REASON
	//*************************
	case EMDMSSS_StartSendSms:

		if(UBL_GetFreeCommandsSpace() < 3) {
			break;
		}

		if (UBL_SendCommand(true, UBL_CMD_SetSendSmsInfo, Smdm.sms.tx.length) != 0) {
				seqnum = UBL_SendCommand(false, UBL_CMD_SetSendSmsData);
				UbloxHandler_SetMdmSmsSendState(EMDMSSS_WaitSendSms);
				//Start with an unknow error due to the +UUCMSRES URC will provide the error
				//according the +CMS Error values: Range [1-548].
				//See section A.2 of AT command manual
				Smdm.sms.SendingError = 0xFFFF;
				SmsSendTimeout = TmrGetTime_ms();
		}

		break;
	case EMDMSSS_WaitSendSms:
			// Wait for response or timeout then exit or jump to next state
		if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok) {
			if(response == EAR_Ok){
				UbloxHandler_SetMdmSmsSendState(EMDMSSS_WaitSendIndication);
			}
			else if (response == EAR_Error || response == EAR_Timeout) {
				UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsError);
			}
		}
		else {
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsError);
		}
		break;

	case EMDMSSS_WaitSendIndication:
		if( Smdm.sms.SendingError != 0xFFFF ) {
			if( Smdm.sms.SendingError == 0 ) {
				if( SplitRes == EMDMSR_SplitEnd ) {
					UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsOk);
				}
				else {
					UbloxHandler_SetMdmSmsSendState(EMDMSSS_CheckGsmRegStatus);
				}
			}
			else {
				UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsError);
			}
			SmsSendTimeout = 0;
		}

		if( SmsSendTimeout != 0 && TmrIsTimeOccured_ms(SmsSendTimeout, SMS_SEND_TIMEOUT) ) {
			SmsSendTimeout = 0;
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_SendSmsError);
		}
	break;
	case EMDMSSS_SendSmsOk:
	case EMDMSSS_SendSmsError:
		if(SScheme->Active == false) {
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_SmsIdle);
		}
		break;
	default:
		break;
	}
}

//just delete outgoing old request to send data
void ClearNextSendDataTaskRequest( void )
{
	memset(&Sreq.NextMsgMgrReq , '\0' , sizeof(SMDM_MessageManagerRequest));
}
//just delet outgoing new request to send data
void ClearCurrentSendDataTaskRequest( void )
{
	memset(&Sreq.CurrentMsgMgrReq , '\0' , sizeof(SMDM_MessageManagerRequest));
}
//just copy
void GetSendDataTaskRequest( void )
{
	memcpy(&Sreq.CurrentMsgMgrReq , &Sreq.NextMsgMgrReq , sizeof(SMDM_MessageManagerRequest));
}
//if the current schem is the active one
bool IsCurrentSchemeActive( SMDM_Scheme *CScheme )
{

	return CScheme->Active;
}

void ActivateSchemeState( SMDM_Scheme *CScheme , bool state)
{
	CScheme->Active = state;
	if(state == true)
		CScheme->timeout = TmrGetTime_ms();
}

//
void RemoveBackUpScheme(SMDM_Requests *req ,  EMSG_OTATransmission t)
{
	switch(t)
	{
		case EMSGOT_SendAlways:
		 	if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs == EMSGOT_FirstBackUp || req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs == EMSGOT_SecBackUp)
				req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs = EMSGOT_NoSend;
			if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms == EMSGOT_FirstBackUp || req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms == EMSGOT_SecBackUp)
				req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms = EMSGOT_NoSend;
			if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf == EMSGOT_FirstBackUp || req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf == EMSGOT_SecBackUp)
					req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf = EMSGOT_NoSend;
		break;
		case EMSGOT_FirstBackUp:
			if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs == EMSGOT_SecBackUp)
				req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs = EMSGOT_NoSend;
			if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms == EMSGOT_SecBackUp)
				req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms = EMSGOT_NoSend;
			if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf == EMSGOT_SecBackUp)
					req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf = EMSGOT_NoSend;
		break;
		case EMSGOT_SecBackUp:
		break;
		default:
		break;
	}
}

// 	EACMPT_Emergency,
//	EACMPT_Assistance,
//	EACMPT_Silent,
//	EACMPT_ZilTok,

bool IsAlreadyInEmgCall( void )
{
	u16 i;
	for(i = 0 ; i < MAX_CALLES_HANDLED ; i++){
		if(Smdm.Call[i].state != EACMSTC_Inactive && Smdm.Call[i].state != EACMSTC_Ended){
			if( Smdm.Call[i].PhoneType == EACMPT_Emergency || Smdm.Call[i].PhoneType == EACMPT_Assistance ||
                Smdm.Call[i].PhoneType == EACMPT_Accident ||Smdm.Call[i].PhoneType == EACMPT_Silent )
                {
                    return true;
                }
		}
	}
	return false;
}

bool IsAlreadyInAccCall( void )
{
	u16 i;
	for(i = 0 ; i < MAX_CALLES_HANDLED ; i++){
		if(Smdm.Call[i].state != EACMSTC_Inactive && Smdm.Call[i].state != EACMSTC_Ended){
			if(Smdm.Call[i].PhoneType == EACMPT_Accident)
				return true;
		}
	}
	return false;
}

//here we check what outgoing requested and handle the sending schem according to that.
//the output at the end of this handler is "did we send the information" or not (if not what was the reason)
void ComputeMsgMgrRequest( SMDM_OutMsgScheme *Scheme , SMDM_Requests *req)
{

	EMDM_GprsSendState GStat;
	static EMDM_GprsSendState GprsErr;

	EMDM_CallState CStat;
	EMDM_SmsSendState SStat;
	static EMDM_SmsSendState SmsErr;

	static u32 GprsErgTimeOut = 0;


	if(IsGprsCriticalSection() == false && IsSmsCriticalSection() == false)
	{
		if(Smdm.AcmPendingAbort)
        {
			Smdm.AcmPendingAbort = false;
			if(Smdm.AcmPendingEmgMsg)
            {

				if(req->NextMsgMgrReq.Req == EMDMAR_SendDataHighPriority)
                {
					req->NextMsgMgrReq.Req = EMDMAR_AbortData;
					Smdm.AcmPendingEmgMsg = false;
				}
			}
			else
            {
				req->NextMsgMgrReq.Req = EMDMAR_AbortData;
			}
            if  (IsCurrentSchemeActive(&Scheme->Call)){    //if abort request arrives, and call scheme is active, disable it
				ActivateSchemeState(&Scheme->Call , false);
				SMDM_OutMsgDialInfo.ty = (EMSGS_MessageTypeWithPhone)(req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone = EMSGMT_Normal_Message);
                UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
                Log(LOG_SUB_MDM,LOG_LVL_TRACE,"Deactivate pending call scheme\n");
            }

            if (Smdm.AcmPendingAbortCall){
                Smdm.AcmPendingAbortCall = false;
                CallAbortAny();
                Log(LOG_SUB_MDM,LOG_LVL_TRACE,"Abort pending call\n");
            }
		}


		if(req->NextMsgMgrReq.Req == EMDMAR_SendDataHighPriority ||
			(req->NextMsgMgrReq.Req == EMDMAR_SendData && req->CurrentMsgMgrReq.Req == EMDMAR_None) ||
			 req->NextMsgMgrReq.Req == EMDMAR_AbortData ||
			 Smdm.AbortCurrentMsg )
		{
			bool AbortedMsgWasSent = true;

			if( req->CurrentMsgMgrReq.Req != EMDMAR_None )
			{
				Log(LOG_SUB_MDM,LOG_LVL_WARN,"Aborting current message\n");
				AbortedMsgWasSent = Scheme->SchemeEndedOk;
			}
            else{
                AbortedMsgWasSent = false;
            }

			if(req->NextMsgMgrReq.Req == EMDMAR_SendDataHighPriority)
				Smdm.AcmPendingEmgMsg = false;

			// Next message becomes the current one
			GetSendDataTaskRequest();
			ClearNextSendDataTaskRequest();

			memset(Scheme , '\0' , sizeof(SMDM_OutMsgScheme));

			// If this request need dueto emergency check if there is an active call and abort it.
			if( IsAnyActiveCall() == true )
			{
                switch(req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone)
				{

                    case EMSGMT_Accident_Alert_Message:
					case EMSGMT_Assistance_button_Press_Alert_Message:
					case EMSGMT_Emergency_button_press_Alert_Message:
					case EMSGMT_Normal_Message_WithDtmfNumber:
						if ( SParameters.ACM_EmergencyCallsThroughBT || IsAlreadyInEmgCall() == true || IsAlreadyInAccCall() == true ){
							req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone = EMSGMT_Normal_Message;
                        }
						else{
							CallAbortAny();
                        }
					break;
					case EMSGMT_Normal_Message:
					default:
					break;
				}
			}

			if( req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone == EMSGMT_Accident_Alert_Message &&
				!SParameters.AccidentAlert_PhoneCallEnable ) {
				req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone = EMSGMT_Normal_Message;
			}

			// If current messages is aborted, then set as idle
			if(Smdm.AbortCurrentMsg || req->CurrentMsgMgrReq.Req == EMDMAR_AbortData)
			{
				if( req->CurrentMsgMgrReq.Req == EMDMAR_AbortData )
				{
					req->CurrentMsgMgrReq.Req = EMDMAR_None;
                    ClearCurrentSendDataTaskRequest();
				}
				// Reset sending status for the outgoing state machine
                UbloxHandler_SetModemSendingStatus( AbortedMsgWasSent ? Modem_LastMsgSend_Ok : Modem_MessageAborted );
			}
			Smdm.AbortCurrentMsg = false;
		}
	}

	if(req->CurrentMsgMgrReq.Req == EMDMAR_None)
    {
        Smdm.AbortingCurrentCall = false;
		return;
    }


	if(Scheme->sm == EMSGOT_NoSend)
		Scheme->sm = EMSGOT_SendAlways;
	else if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs == Scheme->sm)
    {
		if(IsCurrentSchemeActive(&Scheme->Gprs) == false)
        {
            if(Smdm.AbortingCurrentCall)
            {
                if(TmrIsTimeOccured_ms(Smdm.TimeoutAbortingCurrentCall,3000) || !IsAnyActiveCall())
                {
                    Smdm.TimeoutAbortingCurrentCall = 0;
                    Smdm.AbortingCurrentCall = false;
                }
                else
                    return;
            }

            ActivateSchemeState(&Scheme->Gprs , true);
			Scheme->Gprs.retry = 0;
			GprsErgTimeOut = TmrGetTime_ms();
		}
		else
        {
			GStat = UbloxHandler_GetMdmGprsSendState();

			if((req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone != EMSGMT_Normal_Message) &&
                (TmrIsTimeOccured_ms(GprsErgTimeOut,GetSendEmgMsgTimeOut())||
                ((TmrIsTimeOccured_ms(GprsErgTimeOut,(GetSendEmgMsgTimeOut()/2))) && IsGprsCriticalSection() == false && Smdm.Gprs[ EMDMDSD_Http ].ServiceOpenTimeOut == 0 )))
            {
				req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs = EMSGOT_NoSend;
				ActivateSchemeState(&Scheme->Gprs , false);
				if(IsGprsCriticalSection() == true || GStat == EMDMGSS_SendGprsOk)
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_CloseCurrentService);
				else
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);

			}

			if(GStat == EMDMGSS_SendAckOk)
            {
				req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs = EMSGOT_NoSend;
				ActivateSchemeState(&Scheme->Gprs , false);
				RemoveBackUpScheme(req , Scheme->sm);
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
				Scheme->SchemeEndedOk = true;
				//ResetMultipleErrorCounters();

			}
			else if(GStat == EMDMGSS_SendGprsError || GStat == EMDMGSS_NetGprsError)
            {
                if ( (req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone == EMSGMT_Emergency_button_press_Alert_Message)||
                    (req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone == EMSGMT_Accident_Alert_Message)) {
                    if (TmrIsTimeOccured_ms(Scheme->Gprs.timeout , GetSendEmgMsgTimeOut())) {
                        Scheme->Gprs.timeout = TmrGetTime_ms();
                        Scheme->Gprs.retry++;
                        UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
                        GprsErr = GStat;
                        if(Scheme->Gprs.retry >= GetSendMsgEmgRetryCount()) {
                            req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs = EMSGOT_NoSend;
                            ActivateSchemeState(&Scheme->Gprs , false);
                        }
                    }
                }
                else {
                    if (TmrIsTimeOccured_ms(Scheme->Gprs.timeout , GetSendStMsgTimeOut())) {
                        Scheme->Gprs.timeout = TmrGetTime_ms();
                        Scheme->Gprs.retry++;
                        UbloxHandler_SetMdmGprsSendState(EMDMGSS_GprsDataIdle);
                        GprsErr = GStat;
                        if(Scheme->Gprs.retry >= GetSendMsgStRetryCount()) {
                            req->CurrentMsgMgrReq.Out.Scheme.Fields.Gprs = EMSGOT_NoSend;
                            ActivateSchemeState(&Scheme->Gprs , false);
                        }
                    }
				}
			}
			else
				Scheme->Gprs.timeout = TmrGetTime_ms();
		}
	}
    else if(req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone != EMSGMT_Normal_Message)
    {

		if ( SParameters.ACM_EmergencyCallsThroughBT )
        {
			SMDM_OutMsgDialInfo.ty = (EMSGS_MessageTypeWithPhone)(req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone = EMSGMT_Normal_Message);
		}
		else if(IsCurrentSchemeActive(&Scheme->Call) == false)
        {
			UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
			ActivateSchemeState(&Scheme->Call , true);
		}
		else
        {
			CStat = UbloxHandler_GetMdmCallState();

			if(CStat == EMDMCS_ActiveCall)
            {
                //if gprs only + call and gprs fail send OK to outgoing so we wont dial again.
				if(!Scheme->SchemeEndedOk && req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms ==  EMSGOT_NoSend &&
                   req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf ==  EMSGOT_NoSend)
                {
					if ( (req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone != EMSGMT_Emergency_button_press_Alert_Message)&&
                    (req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone != EMSGMT_Accident_Alert_Message)){
                        Scheme->SchemeEndedOk = true;
                    }
				}

				if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Dtmf != EMSGOT_NoSend)
                {
					ActivateSchemeState(&Scheme->Dtmf , true);
				}
				else
                {
					ActivateSchemeState(&Scheme->Call , false);
					SMDM_OutMsgDialInfo.ty = (EMSGS_MessageTypeWithPhone)(req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone = EMSGMT_Normal_Message);
				}

				UbloxHandler_ResetMultipleErrorCounters();
			}
			else if (CStat == EMDMCS_CallError)
            {
				ActivateSchemeState(&Scheme->Dtmf , false);
				ActivateSchemeState(&Scheme->Call , false);
				SMDM_OutMsgDialInfo.ty = (EMSGS_MessageTypeWithPhone)(req->CurrentMsgMgrReq.Out.Scheme.Fields.MessageTypeWithPhone = EMSGMT_Normal_Message);

				if(IsAnyActiveCall() == false)
					UbloxHandler_SetMdmCallState(EMDMCS_CallIdle);
				else
					UbloxHandler_SetMdmCallState(EMDMCS_ActiveCall);
			}
		}
	}
	else if(req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms == Scheme->sm)
    {

		if(IsCurrentSchemeActive(&Scheme->Sms) == false)
        {
			UbloxHandler_SetMdmSmsSendState(EMDMSSS_SmsIdle);
			ActivateSchemeState(&Scheme->Sms , true);
		}
		else
        {
			SStat = UbloxHandler_GetMdmSmsSendState();
			if(SStat == EMDMSSS_SendSmsOk )
            {
                req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms = EMSGOT_NoSend;
				ActivateSchemeState(&Scheme->Sms , false);
				RemoveBackUpScheme(req , Scheme->sm);
				UbloxHandler_SetMdmSmsSendState(EMDMSSS_SmsIdle);
				Scheme->SchemeEndedOk = true;
				Smdm.ErrCount.SendSmsErrorCounter = 0;
			}
			else if(SStat == EMDMSSS_SendSmsError || SStat == EMDMSSS_NetSmsError)
            {
                if(TmrIsTimeOccured_ms(Scheme->Sms.timeout , GetSendStMsgTimeOut()))
                {
					Scheme->Sms.timeout = TmrGetTime_ms();
					Scheme->Sms.retry++;
					UbloxHandler_SetMdmSmsSendState(EMDMSSS_SmsIdle);
					req->CurrentMsgMgrReq.Out.Scheme.Fields.Sms = EMSGOT_NoSend;
					ActivateSchemeState(&Scheme->Sms , false);
					SmsErr = SStat;
				}
			}
			else
            {
				Scheme->Sms.timeout = TmrGetTime_ms();
			}
		}
	}
	else
    {
        Scheme->sm = (EMSG_OTATransmission)(Scheme->sm + 1);
        if ( Scheme->sm > EMSGOT_SecBackUp)
        {
            if(Scheme->SchemeEndedOk)
            {
                UbloxHandler_SetModemSendingStatus(Modem_LastMsgSend_Ok);
                GprsErr = EMDMGSS_GprsDataIdle;
            }
            else
            {
                if(SmsErr == EMDMSSS_NetSmsError || GprsErr == EMDMGSS_NetGprsError)
                {
                    GprsErr = EMDMGSS_GprsDataIdle;
                    SmsErr = EMDMSSS_SmsIdle;
                    UbloxHandler_SetModemSendingStatus(Modem_NoCoverage);
                }
                else
                {
                    UbloxHandler_SetModemSendingStatus(Modem_LastMsgSend_Error);
                }
            }
            ClearCurrentSendDataTaskRequest();
        }
    }
}

bool IsModemInPoweredDownProc( void )
{
	EMDM_State sm = UbloxHandler_GetMdmState();
	if( sm == EMDMS_StartPowerDown ||
        sm == EMDMS_CheckGprsAttachState ||
		sm == EMDMS_StartPowerDownConfig ||
		sm == EMDMS_WaitPowerDownConfig ||
		sm == EMDMS_WaitPowerDown ||
		sm == EMDMS_FinalizePowerDown ||
	    sm == EMDMS_PowerDown )
	{
		return true;
	}

	return false;
}

bool IsModemInSleepProc( void )
{

	EMDM_State Mdmsm = UbloxHandler_GetMdmState();

	return (Mdmsm != EMDMS_StartSleep && Mdmsm != EMDMS_WaitSleep && Mdmsm != EMDMS_Sleep )? false:true;
}

bool IsMdmOnLineHandlersIdle( void )
{

	EMDM_CallState   		Call_sm = UbloxHandler_GetMdmCallState();
	EMDM_SmsSendState		SmsSend_sm = UbloxHandler_GetMdmSmsSendState();
	EMDM_GprsSendState		GprsSend_sm = UbloxHandler_GetMdmGprsSendState();
	EMDM_SmsReceiveState	SmsReceiveStat_sm = UbloxHandler_GetMdmSmsReceiveState();
	EMDM_GprsReceiveState	GprsReceiveState_sm = UbloxHandler_GetMdmGprsReceiveState();

	return (Call_sm == EMDMCS_CallIdle && SmsSend_sm == EMDMSSS_SmsIdle && GprsSend_sm == EMDMGSS_GprsDataIdle &&
		    SmsReceiveStat_sm == EMDMSRS_SmsIdle && GprsReceiveState_sm == EMDMGRS_GprsDataIdle)? true:false;
}

bool IsNoGprsReg( void )
{
	return (Smdm.NetStat.Gprs.NetStat != EMDMNS_RegHome && Smdm.NetStat.Gprs.NetStat != EMDMNS_RegRoam)? true:false;
}

bool UbloxHandler_IsSocketClosed( EMDM_ServiceState ServiceState )
{

        switch( ServiceState )
	{
		case EMDMSS_SocketInactive:
			return true;
		break;
		default:
		break;
	}
	return false;
}

bool UbloxHandler_IsSocketsAreClosed( void )
{
	bool result = UbloxHandler_IsSocketClosed(Smdm.Gprs[EMDMDSD_GprsDestination_1].ServiceState) &&
				  UbloxHandler_IsSocketClosed(Smdm.Gprs[EMDMDSD_GprsDestination_2].ServiceState);
	return result;
}

bool IsSetSleepModeConditions( void )
{
	bool ret = false;

	EMDM_State Mdmsm = UbloxHandler_GetMdmState();
	if(UbloxHandler_GetHttpDownloadState() == EMDMGHD_Idle){
		if(Mdmsm == EMDMS_MainMdmTask ){
			if(IsMdmOnLineHandlersIdle() == true && ((Mdm_MesageStatus == Modem_IdleToSend && !IsAnyActiveCall()) || (IsNoGprsReg() == true && IsNoGsmReg() == true))){
				ret = true;
			}
		}
		else if(Mdmsm == EMDMS_StartConfigAudioHw && Smdm.Info.Sim == EMDMSS_SimCardNotPresent)
			ret = true;
	}
	return ret;
}

void UbloxHandler_ComputePwrMgrRequest(SMDM_Requests *req)
{

	switch(req->CurrentPwrMgrReq.Req){
	case EMDMAR_None:
		return;
	break;
	case EMDMAR_StartOff:
		UbloxHandler_SetMdmState(EMDMS_StartPowerDown);
		req->CurrentPwrMgrReq.Req = EMDMAR_None;
	break;
	case EMDMAR_StartOn:
	case EMDMAR_StartWakeUp:
		ReInitBeforSleep = false;
		if( IsModemInPoweredDownProc() )
		{
			// Need to wait for power down before restarting
			Smdm.NeedRestart = true;
		}
		else if( IsModemInSleepProc() )
		{
			UbloxHandler_SetMdmState(EMDMS_StartWakeUp);
		}

		req->CurrentPwrMgrReq.Req = EMDMAR_None;

	break;
	case EMDMAR_StartSleep:
		if(IsSetSleepModeConditions() == true){
			if(UbloxHandler_IsSocketsAreClosed() == true){
				UbloxHandler_SetMdmState(EMDMS_StartSleep);
				req->CurrentPwrMgrReq.Req = EMDMAR_None;
			}
			else
				UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartGprsCloseService);
		}
	break;

	default:
		req->CurrentPwrMgrReq.Req = EMDMAR_None;
		break;
	}
}

bool IsReadyToChangeSrvParamConditions( void )
{
	bool ret = false;
	EMDM_State Mdmsm = UbloxHandler_GetMdmState();
	if(Mdmsm == EMDMS_MainMdmTask){
		if((Mdm_MesageStatus == Modem_IdleToSend && !IsAnyActiveCall()) || (IsNoGprsReg() == true && IsNoGsmReg() == true)){
			ret = true;
		}
	}
return ret;
}

static void MdmKeepAlivePeriodic( void )
{
	if( Smdm.KeepAliveTimer == 0 )
	{
		Smdm.KeepAliveTimer = TmrGetTime_ms();
		Smdm.KeepAliveRetry = MDM_KEEPALIVE_RETRIES;
	}

	if( TmrIsTimeOccured_ms( Smdm.KeepAliveTimer, MDM_KEEPALIVE_INTERVAL ) )
	{
		Smdm.KeepAliveTimer = TmrGetTime_ms();

		// Failed to get message from modem.
		if( Smdm.KeepAliveRetry > 0 )
		{
			Smdm.KeepAliveRetry--;
		}

		if( Smdm.KeepAliveRetry == 0 )
		{
			// Modem not responding. Reset
            Log(LOG_SUB_MDM,LOG_LVL_WARN,"Keepalive failure, resetting modem\n");
			UbloxHandler_SetMdmState(EMDMS_ConfigErrorPwrReset);
		}
	}
}

struct {
	u32 DistancePassed;
	u32 TimePassed;
}NetworkTracker = {0,0};

#define MOVING_DISTANCE_BEFORE_RADIO_REFRASH_m	2000
#define TIMEOUT_BEFORE_RADIO_REFRASH_ms			120000


bool UbloxHanler_RegistrationFailDetected( void )
{
	u32 CurrentOdometer = 0;
	bool GoToNetworkRefresh = false;

	//in case we are not connected to network
	if(IsNoGsmReg())
	{
		//first time take distance
		if(NetworkTracker.DistancePassed == 0)
		{
			//try to get Odometer calculated from bus or ppk
			if(!EVENTREP_GetOdo( &NetworkTracker.DistancePassed, false))
			{
				//try to get odometer from gps
				if(!EVENTREP_GetOdo( &NetworkTracker.DistancePassed, true))
				{
					 Log(LOG_SUB_MDM,LOG_LVL_TRACE,"UbloxHanler_RegistrationFailDetector: Can not use Odometer\n");
				}
			}
		}
		else
		{
			//take current distance
			if(!EVENTREP_GetOdo( &CurrentOdometer, false))
			{
				if(!EVENTREP_GetOdo( &CurrentOdometer, true))
				{
					 Log(LOG_SUB_MDM,LOG_LVL_TRACE,"UbloxHanler_RegistrationFailDetector: Can not use Odometer\n");
				}
			}

			if(CurrentOdometer)
			{
				if(	is_odometer_distance_passed( NetworkTracker.DistancePassed , MOVING_DISTANCE_BEFORE_RADIO_REFRASH_m , CurrentOdometer))
				{
					GoToNetworkRefresh = true;
				}
			}
		}
		//in case time is 0 take time for the first time
		if(NetworkTracker.TimePassed == 0)
		{
			NetworkTracker.TimePassed = TmrGetTime_ms();
		}

		if(TmrIsTimeOccured_ms(NetworkTracker.TimePassed , TIMEOUT_BEFORE_RADIO_REFRASH_ms))
		{
			GoToNetworkRefresh = true;
		}

		if(GoToNetworkRefresh)
		{
			BZERO(NetworkTracker);
			return true;
		}

	}
	else
	{
		BZERO(NetworkTracker);
	}
return false;
}

void UbloxHandler_OnlineCheckIPAddress( void )
{
    EATC_Response response;
    static u8 ExtPdpContext;

    switch(SModemCheckIPAddress.Sm)
    {
        case EMDMCIP_Idle:
                SModemCheckIPAddress.Timer = TmrGetTime_ms();
                SModemCheckIPAddress.Sm = EMDMCIP_WaitTimeout;
            break;

        case EMDMCIP_WaitTimeout:
            if(TmrIsTimeOccured_ms(SModemCheckIPAddress.Timer, MDM_IP_ADDRESS_POLLING_TIMEOUT)) {
                SModemCheckIPAddress.Sm = EMDMCIP_RequestInternalPdpIp;
            }
            break;

        case EMDMCIP_RequestInternalPdpIp:
            SModemCheckIPAddress.SeqNum = UBL_SendCommand(false, UBL_CMD_GetAssignedIpAddress, PSD_PROFILE_IDENTIFIER);
            BZERO(Smdm.OwnIP);
            SModemCheckIPAddress.Sm = EMDMCIP_WaitInternalPdpIp;
            break;

        case EMDMCIP_WaitInternalPdpIp:
            if(UBL_GetCommandResponse(SModemCheckIPAddress.SeqNum, &response) == ATC_Ok) {
                if( response == EAR_Ok || response == EAR_Timeout || response == EAR_Error ) {
                    SModemCheckIPAddress.Sm = EMDMCIP_RequestExternalPdpIp;
                    BZERO(Smdm.WifiIP);
                    ExtPdpContext = (u8)EMDMPDPCI_NotSupported;
                }
            }
            break;

        case EMDMCIP_RequestExternalPdpIp:
            if(SParameters.WIFI_Mode == EWF_Disabled) {
                SModemCheckIPAddress.Sm = EMDMCIP_Idle;
            }
            else {
                SModemCheckIPAddress.SeqNum = UBL_SendCommand(false, UBL_CMD_GetExternalPDPAddress, EMDMPDPCI_ExternalContext2);
                SModemCheckIPAddress.Sm = EMDMCIP_WaitExternalPdpIp;
            }
            break;

        case EMDMCIP_WaitExternalPdpIp:
            if(UBL_GetCommandResponse(SModemCheckIPAddress.SeqNum, &response) == ATC_Ok) {
                if(response == EAR_Ok || response == EAR_Timeout || response == EAR_Error )
                {
					if( !IsValidIPv4AndPortAddress(Smdm.WifiIP, false) ) {
						if( SModemCheckIPAddress.isPPPConnected ) {
#if defined (__USE_P2P)
							P2PService::GetService().SendMessage(   MESSAGE_GROUP_MARVEL,
				                                                    MESSAGE_CODE_MARVEL_CONNECTION_LOST,
				                                                    NULL,
				                                                    0);
#endif
							SModemCheckIPAddress.isPPPConnected = false;
						}
					}
					SModemCheckIPAddress.Sm = EMDMCIP_Idle;
            	}
            }
            break;

		default:
			break;
    }
}

void UbloxHandler_ResetOnlineMdmMainStateMachines( void )
{
	// Reset state machines
	Smdm.HandlersState.CallState 		= EMDMCS_CallIdle;
	Smdm.HandlersState.SmsReceiveState 	= EMDMSRS_SmsIdle;
	Smdm.HandlersState.SmsSendState 	= EMDMSSS_SmsIdle;
	Smdm.HandlersState.GprsReceiveState = EMDMGRS_GprsDataIdle;
	Smdm.HandlersState.GprsSendState 	= EMDMGSS_GprsDataIdle;
	Smdm.HandlersState.GprsHttpDownload = EMDMGHD_Idle;

	Smdm.AbortCurrentMsg = false;

	SModemAntenna.Sm = EMDMAMS_Idle;
	SModemCellStatus.Sm = EMDCS_Idle;
	SModemCheckIPAddress.Sm = EMDMCIP_Idle;
}



void UbloxHandler_OnlineMdmMainTask( void )
{
	static u32 timeout = 0;
	static SMDM_OutMsgScheme		OutScheme;

	UBLMFO_PeriodicTask();
	if(UBLMFO_GetHandlerState() <= UBLFOS_StartModemFirmwarePrograming )
	{

		MdmKeepAlivePeriodic();
	    //Modem just turnd off in main task power it on
	    if(IsMdmHwPowerStateOn() == false || Smdm.SysStartDetected )
	    {
	        Log(LOG_SUB_MDM,LOG_LVL_CRITICAL,"Modem Just turnd off on us!!!!!!!\n");
	        UbloxHandler_SetMdmState(EMDMS_ConfigError);
	        return;
	    }

		//Read Task's
		if( SYS_GetInstallationType() == SYS_INSTALLATION_GMLAN ) {
			UbloxOnlineExternalAntennaMonitor();
		}

		UbloxOnlineMDMJammingMonitor();
		UbloxOnlineCellStatus();
		UbloxHandler_OnlineGprsReceiveTask();
		UbloxHandler_OnlineSmsReceiveTask();

		ComputeMsgMgrRequest(&OutScheme, &Sreq);

		UbloxHandler_OnlineSmsSendTask(&OutScheme.Sms);
		UbloxHandler_OnlineVoiceCallTask(&OutScheme);
		UbloxHandler_OnlineGprsSendTask(&OutScheme.Gprs);


		HttpNormalDownload();
		TrafficDownloadHandler();
		ResetModemOnMultipleErrorHandler();
		////////////////////////////////////////////////////
		////////////////////////////////////////////////////
#if defined (__A1)
		if(UBLMFO_GetHandlerState() == UBLFOS_Idle)
		{
			UBLAF_AudioFileDownloadHandler();
		}
#endif
		UBLDFI_FtpDownload2FileSystem();
		////////////////////////////////////////////////////
		////////////////////////////////////////////////////

	    UbloxHandler_OnlineCheckIPAddress();

		//download sertificate
		UBLSFS_Handler();

		if ((UParamUpdate.changed.ip || UParamUpdate.changed.apn) && IsReadyToChangeSrvParamConditions() )
		{
			//lets wait 10 sec before we make the change
			if (TmrIsTimeOccured_ms(timeout, 10000))
			{
				timeout = TmrGetTime_ms();
				if (UbloxHandler_IsSocketsAreClosed() == true)
				{
					if( UParamUpdate.changed.apn ) {
						UBL_SendUrgentCommand(true, UBL_CMD_DeactivatePacketSwitchedDataConfig, PSD_PROFILE_IDENTIFIER);
						UbloxHandler_SetMdmState(EMDMS_StartConfigGprsConnection);
					}
					UParamUpdate.changed.ip = UParamUpdate.changed.apn = 0;
				}
				else
				{
					UbloxHandler_SetMdmGprsSendState(EMDMGSS_StartGprsCloseService);
				}
			}
		}
		else
		{
			timeout = TmrGetTime_ms();
		}

		if(UbloxHanler_RegistrationFailDetected() == true)
		{
			UbloxHandler_SetMdmState(EMDMS_OpSelectionDeregisterFromNetwork);
		}
	}

}

///////////////////////////////////////////////////////////////////////////////
///////////////// Command Sequence Functions //////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

static u16 ConnConfigErrCount = 0;
static EATC_Result UbloxHandler_GprsConnectionCmdResponse(u16 id, u8* data, u16 length)
{
	data[length] = '\0';

	if(strstr((char*)data,"CME ERROR") || strstr((char*)data,"CMS ERROR"))
	{
		UbloxHandler_SetMdmState(EMDMS_StartConfigGprsConnection);
		ConnConfigErrCount++;
	}
	else
	{
		if( Smdm.CmdSeq.CmdSequence[Smdm.CmdSeq.CmdSequenceIndex] != UBL_CMD_None && !Smdm.CmdSeq.SendFail )
		{
			Smdm.CmdSeq.CmdSequenceIndex++;
			Smdm.CmdSeq.pfNextCmd();
		}
	}
	return ATC_Ok;
}

static void UbloxHandler_GprsConnectionNextCmd( void )
{
	u32 index = Smdm.CmdSeq.CmdSequenceIndex;
	if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_SetPacketSwitchedDataConfig ) {
		if( UBL_SendCommandOverride(true,
									UbloxHandler_GprsConnectionCmdResponse,
									Smdm.CmdSeq.CmdSequence[index],
									PSD_PROFILE_IDENTIFIER,
									UbloxConnectionSetup[index].conParmTag,
									strlen(UbloxConnectionSetup[index].conParmValue)? UbloxConnectionSetup[index].conParmValue:"StomStom" ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_SetupDynamicIpAddressAssignment ) {
		if( UBL_SendCommandOverride(true,
									UbloxHandler_GprsConnectionCmdResponse,
									Smdm.CmdSeq.CmdSequence[index],
									PSD_PROFILE_IDENTIFIER ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] != UBL_CMD_None )
	{
		if( UBL_SendCommandOverride(true,
									UbloxHandler_GprsConnectionCmdResponse,
									Smdm.CmdSeq.CmdSequence[index],
									0) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
}

static void UbloxHandler_AudioFiltersConfigNextCmd( void )
{
	u32 index = Smdm.CmdSeq.CmdSequenceIndex;
	if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_UplinkDigitalFilterConf ) {
		if( UBL_SendCommandOverride(true,
									UbloxHandler_CmdSequenceResponseCallback,
									Smdm.CmdSeq.CmdSequence[index],
									UbloxFilterSetup[index].FilterNumber,
									UbloxFilterSetup[index].Coefficient_a0,
									UbloxFilterSetup[index].Coefficient_a1,
									UbloxFilterSetup[index].Coefficient_a2,
									UbloxFilterSetup[index].Coefficient_b1,
									UbloxFilterSetup[index].Coefficient_b2) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_DownlinkDigitalFilterConf ) {
		if( UBL_SendCommandOverride(true,
									UbloxHandler_CmdSequenceResponseCallback,
									Smdm.CmdSeq.CmdSequence[index],
									UbloxFilterSetup[index].FilterNumber,
									UbloxFilterSetup[index].Coefficient_a0,
									UbloxFilterSetup[index].Coefficient_a1,
									UbloxFilterSetup[index].Coefficient_a2,
									UbloxFilterSetup[index].Coefficient_b1,
									UbloxFilterSetup[index].Coefficient_b2) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] != UBL_CMD_None )
	{
		if ( UBL_SendCommandOverride( true,
									  UbloxHandler_CmdSequenceResponseCallback,
									  Smdm.CmdSeq.CmdSequence[index] ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
}


static void UbloxHandler_IndicationsConfigNextCmd( void )
{
	u32 index = Smdm.CmdSeq.CmdSequenceIndex;
	const EUBL_Command *Cmnds = Smdm.CmdSeq.CmdSequence;

	if( Cmnds[index] == UBL_CMD_SetNewSmsIndicator )
	{
		if (UBL_SendCommandOverride(true, UbloxHandler_CmdSequenceResponseCallback, Cmnds[index], 1, 1) == 0)
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}

	else if( Cmnds[index] == UBL_CMD_FlowControl )
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseFlowControlSetCallback,
									 Cmnds[index],
									 EMDMFC_RTSCTSHardwareFlowControl) == 0 ){
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] == UBL_CMD_CustomizePLMNScanEnableDisable)
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseCallback,
									 Cmnds[index],
									 0 ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] != UBL_CMD_None )
	{
		if ( UBL_SendCommandOverride( true, UbloxHandler_CmdSequenceResponseCallback, Cmnds[index] ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
}

static void UbloxHandler_SleepConfigNextCmd( void )
{
	u32 index = Smdm.CmdSeq.CmdSequenceIndex;
	u16 MdmCsTimer;
	const EUBL_Command *Cmnds = Smdm.CmdSeq.CmdSequence;

	if( Cmnds[index] == UBL_CMD_SetNewSmsIndicator )
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseCallback,
									 Cmnds[index],
									 1,1 ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] == UBL_CMD_FlowControl )
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseFlowControlSetCallback,
									 Cmnds[index],
									 EMDMFC_DisableFlowControl) == 0 ){
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if ( Cmnds[index] == UBL_CMD_CustomizePLMNScan )
	{
		if( IsNoGsmReg() ) {
			MdmCsTimer = SParameters.SystemParameters_A1_RadioCsTimerNotRegistered;
		}
		else {
			MdmCsTimer = SParameters.SystemParameters_A1_RadioCsTimerRegistered;
		}

		if (UBL_SendCommandOverride(true, UbloxHandler_CmdSequenceResponseCallback, Cmnds[index], 30, MdmCsTimer) == 0)
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] == UBL_CMD_CustomizePLMNScanEnableDisable)
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseCallback,
									 Cmnds[index],
									 1 ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] == UBL_CMD_CheckPendingSms )
	{
		if ( UBL_SendCommandOverride( true, UbloxHandler_CmdSequenceResponseCallback, Cmnds[index] ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] != UBL_CMD_None )
	{
		if ( UBL_SendCommandOverride( true, UbloxHandler_CmdSequenceResponseCallback, Cmnds[index] ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
}

static void UbloxHandler_PwrDownConfigNextCmd( void )
{
	u32 index = Smdm.CmdSeq.CmdSequenceIndex;
	const EUBL_Command *Cmnds = Smdm.CmdSeq.CmdSequence;

	if( Cmnds[index] == UBL_CMD_SetGeneralPDPContextAction)
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseFlowControlSetCallback,
									 Cmnds[index],
									 EMDMPDPCA_Deactivate ) == 0 ){
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Cmnds[index] != UBL_CMD_None )
	{
		if ( UBL_SendCommandOverride( true, UbloxHandler_CmdSequenceResponseCallback, Cmnds[index] ) == 0 )
		{
			UbloxHandler_CmdSeqSendFail();
		}
	}
}


static EATC_Result UbloxHandler_CmdSequenceResponseFlowControlSetCallback(u16 id, u8* data, u16 length)
{
	if(UbloxHandler_GetMdmState() == EMDMS_SleepConfig){
		  GSMUART.EnableHardwareFlowControl(false);
	}
	else{
		  GSMUART.EnableHardwareFlowControl(true);
	}

  return UbloxHandler_CmdSequenceResponseCallback(id,data,length);
}


static EATC_Result UbloxHandler_CmdSequenceResponseCallback(u16 id, u8* data, u16 length)
{
    EATC_Result res = ATC_Ok;
    switch(id)
    {
    case UBL_CMD_GetFirmwareVersion:
		res = UbloxHandler_SetMdmFwVersion(id, (char*)data , length );
	break;
	case UBL_CMD_GetProductTypeNumber:
		res = UbloxHandler_SetMdmModel(id, (char*)data , length);
	break;
    case UBL_CMD_GetIMEI:
		res = UbloxHandler_SetMdmImei(id, (char*)data , length);
    break;
    case UBL_CMD_GetIMSI:
		res = UbloxHandler_SetMdmImsi(id, (char*)data , length );
	break;
    case UBL_CMD_GetICCID:
		res = UbloxHandler_SetSimIccid(id,(char*)data , length );
    break;
    case UBL_CMD_GetOwnNumber:
		res = UbloxHandler_MdmOwnPhoneNumber(id, (char*)data, length);
	break;
	case UBL_CMD_SetPreferredSmsMessageStorage:
		res = UbloxHandler_UpdateConfigureSmsStorage(id , (char*)data , length);
	break;
	case UBL_CMD_QueryCallWaitingConfig:
		res = UbloxHandler_UpdateCallWaitingStatus(id , (char*)data , length);
	break;

	case UBL_CMD_GetNetworkStatus:
			UbloxHandler_UpdateNetworkStatus((char*)data, SMDMMTI_CommandRes, SMDMNTI_Gsm);
	break;
	case UBL_CMD_GetGprsNetworkStatus:
          	UbloxHandler_UpdateNetworkStatus((char*)data , SMDMMTI_CommandRes, SMDMNTI_Gprs);
	break;
	case UBL_CMD_GetSimUrcConfig:
		res = UbloxHandler_ReceivedSimUrcConfiguration(id, (char*)data , length);

	break;

    }

	if( res == ATC_Ok &&
        Smdm.CmdSeq.CmdSequence[Smdm.CmdSeq.CmdSequenceIndex] != UBL_CMD_None &&
        !Smdm.CmdSeq.SendFail )
	{
		Smdm.CmdSeq.CmdSequenceIndex++;
		Smdm.CmdSeq.pfNextCmd();
	}
	return res;
}

static void UbloxHandler_CmdSeqSendFail( void )
{
	Smdm.CmdSeq.SendFail = true;
	Smdm.CmdSeq.RetryTimer = TmrGetTime_ms();
}

static void UbloxHandler_CmdSequenceNextCommand( void )
{
	u32 index = Smdm.CmdSeq.CmdSequenceIndex;
	bool bSecondCallIndication = false;

    if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_FlowControl ) {
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseFlowControlSetCallback,
									 Smdm.CmdSeq.CmdSequence[index],
									 EMDMFC_RTSCTSHardwareFlowControl) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	//disable Message waiting indication (not relevant for PNG)
	else if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_MessageWaitingIndication )
	{
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseCallback,
									 Smdm.CmdSeq.CmdSequence[index],
									 0 ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_SetNewSmsIndicator ) {
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseCallback,
									 Smdm.CmdSeq.CmdSequence[index],
									 1, 1 ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_SetPreferredSmsMessageStorage) {
		if (UBL_SendCommandOverride( true,
									 UbloxHandler_CmdSequenceResponseCallback,
									 Smdm.CmdSeq.CmdSequence[index],
									 ME_MESSAGE_STORAGE,
									 ME_MESSAGE_STORAGE,
									 ME_MESSAGE_STORAGE ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else if( Smdm.CmdSeq.CmdSequence[index] != UBL_CMD_None ) {
		//check if ^SYSSTART URC was detected.... if it was move to next command and reset the indication
		if(Smdm.CmdSeq.CmdSequence[index] == UBL_CMD_SetGreetingText && Smdm.SysStartDetected)
		{
			Smdm.SysStartDetected = false;
			UbloxHandler_CmdSequenceResponseCallback(UBL_CMD_SetGreetingText,NULL,0);
			return;
		}
		if ( UBL_SendCommandOverride( true,
									  UbloxHandler_CmdSequenceResponseCallback,
									  Smdm.CmdSeq.CmdSequence[index] ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
}

static void UbloxHandler_CmdSequenceNextCommandBasicConfig( void )
{
	u8 SimUrcConfig = 0;

	if( Smdm.SimUrcEnabled ) {
		Smdm.SimUrcEnabled = false;
		SimUrcConfig = (EMDMSUBM_InitializationStatus | EMDMSUBM_PhoneBookInitializationStatus);
		if( UBL_SendCommand(true, UBL_CMD_SetSimUrcConfig, SimUrcConfig) == 0 ||
			UBL_SendCommandOverride( true, UbloxHandler_CmdSequenceResponseCallback, UBL_CMD_StoreConfigurationToRAM ) == 0 ) {
			UbloxHandler_CmdSeqSendFail();
		}
	}
	else {
		UbloxHandler_CmdSequenceNextCommand();
	}
}

static void UbloxHandler_CmdSequenceStart( EMDM_State InitialState, const EUBL_Command *CmdSequence, pfCmdSeqExecuteNextCmd pfExecuteNextCmd )
{
	Smdm.CmdSeq.CmdSequence			= CmdSequence;
	Smdm.CmdSeq.CmdSequenceIndex	= 0;
	Smdm.CmdSeq.SendFail			= false;
	Smdm.CmdSeq.TimeOut				= 0;
	Smdm.CmdSeq.StartTime			= TmrGetTime_ms();
	Smdm.CmdSeq.pfNextCmd 			= ( pfExecuteNextCmd == NULL )? UbloxHandler_CmdSequenceNextCommand : pfExecuteNextCmd;

	// Calculate sequence timeout: sum of timeouts of individual commands
	u32 index = 0;
	const SATC_Command* pCmd;
	while( Smdm.CmdSeq.CmdSequence[index] != UBL_CMD_None )
	{
		pCmd = UBL_GetCommand( Smdm.CmdSeq.CmdSequence[index] );
		Smdm.CmdSeq.TimeOut += pCmd->Timeout;
		index++;
	}

	// Start sequence
	UbloxHandler_SetMdmState( InitialState );
	Smdm.CmdSeq.pfNextCmd();
}

static bool UbloxHandler_CmdSequenceIfDoneNextState( EMDM_State NextState )
{
	bool result = false;

	if( Smdm.CmdSeq.CmdSequence[Smdm.CmdSeq.CmdSequenceIndex] == UBL_CMD_None )
	{
		// Last command is done, sequence is complete
		UbloxHandler_SetMdmState(NextState);
		result = true;
	}
	else if( TmrIsTimeOccured_ms(Smdm.CmdSeq.StartTime, Smdm.CmdSeq.TimeOut) )
	{
		// Overall timeout for sequence has expired, sequence has failed
		UbloxHandler_SetMdmState(EMDMS_ConfigError);
	}
	else if( Smdm.CmdSeq.SendFail )
	{
		// If failed to send command, retry after timeout
		if( TmrIsTimeOccured_ms(Smdm.CmdSeq.RetryTimer, CMD_SEQ_RETRY_TIME_ms) )
		{
			Smdm.CmdSeq.SendFail = false;
			Smdm.CmdSeq.pfNextCmd();
		}
	}

	return result;
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
void UbloxHandler_ClearSocketLiast( void )
{
	for(u16 i = 0 ; i < elementsof(Smdm.Gprs) ; i++)
	{
		Smdm.Gprs[i].SocketAssigned = EMDMDSD_Undefined;
	}
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////



EMDM_State UbloxHandler_MdmMainTask( void )
{

	static u32 Cmd_TimeOut = 0;
	static u32 OpTimeout;
	static u16 seqnum = 0;

	EATC_Response response;

	EMDM_State sm;

	UbloxHandler_ComputePwrMgrRequest(&Sreq);
	sm = UbloxHandler_GetMdmState();

	switch(sm)
	{
		case EMDMS_Undefined:
			//Activate ForteMedia profile for fast attendance of "TTS"
			fmm_fmmInitGsmProfile();
			UbloxHandler_SetMdmState(EMDMS_WaitInitEnd);
			OpTimeout = TmrGetTime_ms();
			Smdm.NetStat.Gsm.NetStat = EMDMNS_Undefined;
			Smdm.NetStat.Gprs.NetStat = EMDMNS_Undefined;
			Smdm.NetStat.NewReg = false;
			Smdm.Info.Sim = EMDMSS_SimCardNotPresent;
			Smdm.NeedRestart = false;
			Smdm.KeepAliveTimer = 0;
            Smdm.MuteIncomingAudioPath = true;
            Smdm.TimeoutAbortingCurrentCall = 0;
			UbloxHandler_ClearAllCalls();
			UbloxHandler_ClearSocketLiast();

			//check if need to restart certificate download
			UBLSFS_Init();

			Smdm.SysStartDetected = false;

			//if its not shark...no detection assume always connected
			if( SParameters.Input_ExternalGSMAntenna != EAntType_External_Shark)
			{
			  	DTC_CheckModemAntenna(Modem_Antenna_Connected);
			}

            PTM_ChangeHardwareDeviceStatus(EPTMHD_Modem, EPTMHS_NotReady);

		break;
		case EMDMS_WaitInitEnd:

			if( UBL_IsInitialized() == true )
			{
				ConnConfigErrCount = 0;
				UbloxHandler_SetModemSendingStatus(Modem_IdleToSend);
				UbloxHandler_SetMdmState(EMDMS_StartConfigBasic);
				if(UBLMFO_GetHandlerState() < UBLFOS_StartModemFirmwarePrograming)
				{
					UBLMFO_SetHandlerState(UBLFOS_CheckIfNeedFirmwareOTA);
				}
#if defined (__A1)
				//check if we need to update audio files on every modem reset
				UBLAF_AudioFileDownloadInit();
#endif
			}
			else if(TmrIsTimeOccured_ms(OpTimeout , WAIY_INIT_END_TIMEOUT_ms))
			{
				OpTimeout = TmrGetTime_ms();
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}

		break;
		case EMDMS_StartConfigBasic:

			Smdm.SimUrcEnabled = false;
			UbloxHandler_CmdSequenceStart( EMDMS_ConfigBasic, UbloxMdmSetGetMdmBasic, UbloxHandler_CmdSequenceNextCommandBasicConfig );

		break;
		case EMDMS_ConfigBasic:

			if( UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartConfigBasicG) ) {
				OpTimeout = TmrGetTime_ms();
			}

		break;

		case EMDMS_StartConfigBasicG:

			if( Smdm.Info.Sim == EMDMSS_SimCardNotPresent	||
				Smdm.Info.Sim == EMDMSS_SimPhoneBookReady	||
				Smdm.Info.Sim == EMDMSS_USimPhoneBookReady	||
				TmrIsTimeOccured_ms(OpTimeout , 10000) ) {
				UbloxHandler_CmdSequenceStart( EMDMS_ConfigBasicDone, UbloxMdmSetGetMdmBasicG, UbloxHandler_CmdSequenceNextCommandBasicConfig );
			}

		break;

		case EMDMS_ConfigBasicDone:

			UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartRingToneConfig);

		break;
		case EMDMS_StartRingToneConfig:
			if( UbloxHandler_RingToneSelection() == true ) {
				UbloxHandler_SetMdmState(EMDMS_ConfigAntenna);
				UbloxHandler_SetRingtone( (EUBL_RingTone)SParameters.SystemParameters_PNG_RingTone );
				OpTimeout = TmrGetTime_ms();
			}
		break;

		case EMDMS_ConfigAntenna:


			//detecting the real antenna state
			if( UbloxOnlineExternalAntennaMonitor() == true || TmrIsTimeOccured_ms(OpTimeout , 10000))
			{
				EepromRead( E2P_MDM_AUDIO_CFG_MAGIC_NUMBER_OFFSET, (u8*)&MdmAudioCfgMagic, sizeof(MdmAudioCfgMagic) );
				if( MdmAudioCfgMagic != MODEM_AUDIO_CFG_MAGIC_NUMBER ) {
					UbloxHandler_SetMdmState(EMDMS_StartConfigAudioHw);
					OpTimeout = TmrGetTime_ms();
				}
				else {
		               UbloxHandler_SetMdmState(EMDMS_StartConfigSms);
				}
			}

		break;

		case EMDMS_StartConfigAudioHw:

			UbloxHandler_CmdSequenceStart( EMDMS_ConfigAudioHw, UbloxMdmAudioHwConfiguration, NULL );

		break;
		case EMDMS_WaitSimLock:

			if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok)
			{
				// Start next sequence when set SIM PIN command succeeds or fails
				if( response == EAR_Ok || response == EAR_Timeout || response == EAR_Error )
				{
					if( UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartConfigSms) ){
						UbloxHandler_CmdSequenceStart( EMDMS_ConfigSms, UbloxMdmSmsConfiguration, NULL );
					}
				}
			}
			else
			{
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}

		break;
		case EMDMS_ConfigAudioHw:

			UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartConfigAudioFilters);

		break;

		case EMDMS_StartConfigAudioFilters:

			UbloxHandler_CmdSequenceStart( EMDMS_ConfigAudioFilters, UbloxMdmFilterConfiguration, UbloxHandler_AudioFiltersConfigNextCmd );

		break;

		case EMDMS_ConfigAudioFilters:

			if( UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartConfigSms) != 0){
				EepromRead( E2P_MDM_AUDIO_CFG_MAGIC_NUMBER_OFFSET, (u8*)&MdmAudioCfgMagic, sizeof(MdmAudioCfgMagic) );
				if( MdmAudioCfgMagic != MODEM_AUDIO_CFG_MAGIC_NUMBER) {
					MdmAudioCfgMagic = MODEM_AUDIO_CFG_MAGIC_NUMBER;
					EepromWrite( E2P_MDM_AUDIO_CFG_MAGIC_NUMBER_OFFSET, (u8*)&MdmAudioCfgMagic, sizeof(MdmAudioCfgMagic) );
				}
			}

		break;

		case EMDMS_StartConfigSms:

			if( Smdm.Info.Sim == EMDMSS_SimOperational ||
				Smdm.Info.Sim == EMDMSS_SimPhoneBookReady ||
				Smdm.Info.Sim == EMDMSS_USimPhoneBookReady) {
				//sim status goes here
				OpTimeout = TmrGetTime_ms();
				UbloxHandler_CmdSequenceStart( EMDMS_ConfigSms, UbloxMdmSmsConfiguration, NULL );
			}
			else if(Smdm.Info.Sim == EMDMSS_SimPINneeded)
			{
				if((seqnum = UBL_SendCommand(false,UBL_CMD_SetSimPin1 , UblGetSimLockPIN1())) !=0 )
				{
					UbloxHandler_SetMdmState(EMDMS_WaitSimLock);
				}
			}
			else if(TmrIsTimeOccured_ms(OpTimeout, 10000))
			{
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}

		break;
		case EMDMS_ConfigSms:

			UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartConfigGprsConnection);

		break;
		case EMDMS_StartConfigGprsConnection:

			if(ConnConfigErrCount < 3)
			{
				UbloxHandler_CmdSequenceStart( EMDMS_ConfigGprsConnection, UbloxMdmGprsConnectionConfiguration, UbloxHandler_GprsConnectionNextCmd );
			}
			else
			{
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}

		break;
		case EMDMS_ConfigGprsConnection:
			if( UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartConfigIndicationsEvents) ) {
				if( ReInitBeforSleep == true) {
					UbloxHandler_SetMdmState(EMDMS_StartCheckNetworkRegistration);
				}
			}

		break;
		case EMDMS_StartConfigIndicationsEvents:

			UbloxHandler_CmdSequenceStart( EMDMS_ConfigIndicationsEvents, UbloxMdmSetWakeUpCfg, UbloxHandler_IndicationsConfigNextCmd );

		break;
		case EMDMS_ConfigIndicationsEvents:

			UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartCheckNetworkRegistration);

		break;
		case EMDMS_StartCheckNetworkRegistration:
            PTM_ChangeHardwareDeviceStatus(EPTMHD_Modem, EPTMHS_FullPower);
			UbloxHandler_CmdSequenceStart( EMDMS_CheckNetworkRegistration, UbloxMdmRegistrationStat, NULL );

		break;
		case EMDMS_CheckNetworkRegistration:

			if( UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_StartGetCallWaitingConfiguration) )
			{
				Cmd_TimeOut = TmrGetTime_ms();
			}

		break;
		case EMDMS_StartGetCallWaitingConfiguration:


			if(Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRoam || TmrIsTimeOccured_ms(Cmd_TimeOut, ReInitBeforSleep? 60000:30000))
			{
				if( ReInitBeforSleep == true) {
					UbloxHandler_SetMdmState(EMDMS_StartSleep);
				}
				else {
					if( (seqnum = UBL_SendCommand(false, UBL_CMD_QueryCallWaitingConfig, EUBLCWUC_Disabled, EUBLCWM_QueryStatus )) !=0  ){
						UbloxHandler_SetMdmState(EMDMS_GetCallWaitingConfiguration);
					}
				}
			}

		break;

		case EMDMS_GetCallWaitingConfiguration:

			if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok)
			{
				if( response == EAR_Ok || response == EAR_Timeout || response == EAR_Error ) {
					UbloxHandler_SetMdmState(EMDMS_StartSetCallWaitingConfiguration);
				}
			}
			else
			{
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}

		break;

		case EMDMS_StartSetCallWaitingConfiguration:

			if( (Smdm.CallWaitingStatus == EUBLCWS_NotActive && SParameters.ACM_SecondCallIndicationEnable) ||
				(Smdm.CallWaitingStatus == EUBLCWS_Active && !SParameters.ACM_SecondCallIndicationEnable) ) {

				EUBL_CallWaitingMode CallWaitingMode;
				CallWaitingMode = SParameters.ACM_SecondCallIndicationEnable ? EUBLCWM_Enabled : EUBLCWM_Disabled;

				if( (seqnum = UBL_SendCommand(false, UBL_CMD_SetCallWaitingConfig, EUBLCWUC_Disabled, CallWaitingMode, EUBLCWC_Voice)) !=0 ){
					UbloxHandler_SetMdmState(EMDMS_SetCallWaitingConfiguration);
				}
			}
			else {
				UbloxHandler_SetMdmState(EMDMS_StartMainMdmTasks);
			}

			UbloxHandler_ResetMultipleErrorCounters();

		break;

		case EMDMS_SetCallWaitingConfiguration:

			if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok)
			{
				if( response == EAR_Ok || response == EAR_Timeout || response == EAR_Error ) {
					UbloxHandler_SetMdmState(EMDMS_StartMainMdmTasks);
				}
			}
			else
			{
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}

		break;

		case EMDMS_StartMainMdmTasks:

			UbloxHandler_ResetOnlineMdmMainStateMachines();
			UbloxHandler_SetMdmState(EMDMS_MainMdmTask);

		break;

        case EMDMS_MainMdmTask:

          UbloxHandler_OnlineMdmMainTask();

		break;

		case EMDMS_StartSleep:

			if(Smdm.Info.Sim != EMDMSS_SimCardNotPresent)
			{
				if(ReInitBeforSleep == false)
				{
					ReInitBeforSleep = true;
					UBL_SoftwareReset();
					UbloxHandler_SetMdmState(EMDMS_Undefined);
				}
				else
				{
					if( Smdm.sms.rx.RxInfo.Pending > 0 || Smdm.sms.rx.RxInfo.Index > 0)
					{
						// If SMS is pending, cancel sleep and contimue regular start sequence
						if ( Smdm.sms.rx.RxInfo.Index > 0 ) {
							Log(LOG_SUB_MDM,LOG_LVL_WARN,"Cannot sleep: Pending SMS - Index = %d\n", Smdm.sms.rx.RxInfo.Index);
						}
						else if	( Smdm.sms.rx.RxInfo.Pending > 0 ) {
							Log(LOG_SUB_MDM,LOG_LVL_WARN,"Cannot sleep: Pending SMSes = %d\n", Smdm.sms.rx.RxInfo.Pending);
						}
						ReInitBeforSleep = false;
						UbloxHandler_SetMdmState(EMDMS_StartConfigIndicationsEvents);
					}
					else
					{
						UbloxHandler_CmdSequenceStart( EMDMS_SleepConfig, UbloxMdmSetSleepCfg, UbloxHandler_SleepConfigNextCmd );
					}
				}
			}
			else
			{
				UbloxHandler_SetMdmState(EMDMS_StartPowerDown);
			}

		break;
		case EMDMS_SleepConfig:

			if( UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_WaitSleep) )
			{

                Smdm.Timer = TmrGetTime_ms();
                GSMUART.EnableHardwareFlowControl(false);
                UBL_SetRTS(reset);
			}

		break;
		case EMDMS_WaitSleep:

            //if( IsMdmHwSleep() )
			//{
                fmm_fmmTaskEndded();
				UbloxHandler_SetMdmState(EMDMS_Sleep);
			//}
			//else if( TmrIsTimeOccured_ms(Smdm.Timer, 60000/*MDM_WAIT_FOR_SLEEP_TIMEOUT_ms*/) )
			//{
			//	// In case modem decides not to go to sleep for some reason
			//	Log(LOG_SUB_MDM,LOG_LVL_WARN, "Timeout waiting for sleep, reentering. Last modem event was: [%s]\n", ATC_GetLastReceivedData());
			//	UbloxHandler_SetMdmState(EMDMS_StartSleep);
			//}

		break;
		case EMDMS_Sleep:

			ReInitBeforSleep = false;

		break;
		case EMDMS_StartWakeUp:

			ConnConfigErrCount = 0;
			UBL_SetRTS(set);
            UbloxHandler_SetMdmState(EMDMS_WaitWakeUp);
			Smdm.Timer = TmrGetTime_ms();
            PTM_ChangeHardwareDeviceStatus(EPTMHD_Modem, EPTMHS_NotReady);
		break;
		case EMDMS_WaitWakeUp:

			if(IsMdmHwSleep() == false)
			{
				// Activate the gsm profile of the fortemedia
				fmm_fmmInitGsmProfile();
				DTC_InitModemAntennaCounters();
				Smdm.Timer = TmrGetTime_ms();
                GSMUART.EnableHardwareFlowControl(true);
                Smdm.Timer = TmrGetTime_ms();
                UbloxHandler_SetMdmState(EMDMS_WakeUpDelay);
			}

			if (TmrIsTimeOccured_ms(Smdm.Timer, 10000L))
			{
				// Check if waked up and move to
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
				Smdm.Timer = TmrGetTime_ms();
			}

		break;
        case EMDMS_WakeUpDelay:
            if(TmrIsTimeOccured_ms(Smdm.Timer, 5000))
            {
                UbloxHandler_SetMdmState(EMDMS_StartConfigIndicationsEvents);
            }
        break;

		case EMDMS_StartPowerDown:
			UBL_SetRTS(set);
			Smdm.Timer = TmrGetTime_ms();
			UbloxHandler_SetMdmState(EMDMS_CheckGprsAttachState);
			break;

		case EMDMS_CheckGprsAttachState:
			if( TmrIsTimeOccured_ms( Smdm.Timer, 5000L ) ) {
				if( (seqnum = UBL_SendCommand(false, UBL_CMD_GetGprsAttachedState)) !=0 ) {
					UbloxHandler_SetMdmState(EMDMS_StartPowerDownConfig);
				}
    		}
		break;

		case EMDMS_StartPowerDownConfig:

			if( UBL_GetCommandResponse(seqnum, &response) == ATC_Ok ) {
				if( response == EAR_Ok ) {
					if( Smdm.GprsAttachStatus == EMDMAD_GprsAttach ) {
						//If we are attached to GPRS, deactivate all PDP context before go to sleep
						UbloxHandler_CmdSequenceStart( EMDMS_WaitPowerDownConfig, UbloxMdmSetPwrDownCfg1, UbloxHandler_PwrDownConfigNextCmd );
					}
					else {
						UbloxHandler_CmdSequenceStart( EMDMS_WaitPowerDownConfig, UbloxMdmSetPwrDownCfg2, UbloxHandler_PwrDownConfigNextCmd );
					}
				}
				else if (response == EAR_Timeout || response == EAR_Error) {
					UbloxHandler_CmdSequenceStart( EMDMS_WaitPowerDownConfig, UbloxMdmSetPwrDownCfg1, UbloxHandler_PwrDownConfigNextCmd );
				}
			}

		break;

		case EMDMS_WaitPowerDownConfig:

			UbloxHandler_CmdSequenceIfDoneNextState(EMDMS_WaitPowerDown);

		break;
		case EMDMS_WaitPowerDown:

			if( IsMdmHwPowerStateOn() == false || TmrIsTimeOccured_ms(Smdm.Timer, MDM_SHUTDOWN_TO_OFF_TIMEOUT_ms) )
			{
				UbloxHandler_SetMdmState(EMDMS_FinalizePowerDown);
			}

		break;
		case EMDMS_FinalizePowerDown:

			fmm_fmmTaskEndded();
			UbloxHandler_SetMdmState(EMDMS_PowerDown);

		break;
		case EMDMS_PowerDown:
			GSMUART.EnableHardwareFlowControl(false);
			UBL_SetRTS(reset);

			if( Smdm.NeedRestart )
			{
				UblMdmPowerUp();
				UbloxHandler_SetMdmState(EMDMS_Undefined);
			}

		break;
		case EMDMS_ConfigError:
            //if we need to dial to SAMU when modem is ON, do not restart Ublox state machine
            if (IO_Get(DIO_Modem_1_8v_On) && (PendingSamuCallOnFailure || IsAnyActiveCall())) {
                UbloxHandler_SetMdmState(EMDMS_StartMainMdmTasks);
            }
            else {
                UblMdmPowerUp();
                UbloxHandler_SetMdmState(EMDMS_Undefined);
            }

		break;
		case EMDMS_ConfigErrorPwrReset:

			UBL_PowerReset();
			UbloxHandler_SetMdmState(EMDMS_Undefined);

		break;
		case EMDMS_OpSelectionDeregisterFromNetwork:
			if((seqnum = UBL_SendCommand(false, UBL_CMD_OperatorSelection , EUBLOPS_DeregisterFromNetwork)) != 0)	{
				UbloxHandler_SetMdmState(EMDMS_OpSelectionDeregisterFromNetwork_Wait);
			}
			else{
				UbloxHandler_SetMdmState(EMDMS_ConfigError);
			}
			break;
		case EMDMS_OpSelectionDeregisterFromNetwork_Wait:

				if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok){
					if( response == EAR_Ok ){
						Log(LOG_SUB_MDM,LOG_LVL_WARN, "Deregister from Network\n" );
						UbloxHandler_SetMdmState(EMDMS_OpSelectionAutomatic);
					}
					else if( response == EAR_Timeout || response == EAR_Error )
					{
						UbloxHandler_SetMdmState(EMDMS_ConfigError);
					}
				}
				else{
					UbloxHandler_SetMdmState(EMDMS_ConfigError);
				}
			break;
		case EMDMS_OpSelectionAutomatic:
				if((seqnum = UBL_SendCommand(false, UBL_CMD_OperatorSelection , EUBLOPS_Automatic)) != 0)	{
					UBL_SendCommand(true, UBL_CMD_GetAtTest);
					UbloxHandler_SetMdmState(EMDMS_OpSelectionAutomatic_Wait);
				}
				else{
					UbloxHandler_SetMdmState(EMDMS_ConfigError);
				}
			break;
		case EMDMS_OpSelectionAutomatic_Wait:
				if (UBL_GetCommandResponse(seqnum, &response) == ATC_Ok){
					if( response == EAR_Ok ){
						Log(LOG_SUB_MDM,LOG_LVL_WARN, "Set Network Automatic Registration\n" );
						UbloxHandler_SetMdmState(EMDMS_StartMainMdmTasks);
					}
					else if( response == EAR_Timeout || response == EAR_Error ){
								UbloxHandler_SetMdmState(EMDMS_ConfigError);
					}
				}
				else
				{
					UbloxHandler_SetMdmState(EMDMS_ConfigError);
				}
			break;
        case EMDMS_AbortSelectionOperation:
            UBL_ClosePendingCommand();
            if(UBL_GetCommandResponse(seqnum, &response) == ATC_Ok)
            {
                if((seqnum = UBL_SendUrgentCommand(true, UBL_CMD_GetAtTest)) != 0)
                {
                    UbloxHandler_SetMdmState(EMDMS_StartMainMdmTasks);
                }
            }
            break;
	}
	return sm;
}

bool HandleFullFifo( void )
{
	/* Special workaround for Cinterion modem bug:
	 *
	 * The modem sometimes sends extra data, so we remove it.
	 *
	 * Conditions:
	 *
	 * 1. We are using Cinterion modem with revision: A-REVISION 01.002.02
	 * 2. The fifo contains socket data from ^SISR
	 * 3. The fifo contains more data than was supposed to be sent
	 * ==> In this case, throw away the data which is extra, and insert
	 * ==> CR/LF so that the AT processor will handle the data correctly.
	 */

/*
	bool Result = false;
	if( Smdm.CinterionGhostBytesBug )
	{
		int isock;

		// See if a socket is reading data
		for( isock = 0; isock <= EMDMDSD_Http; isock++ )
		{
			if( DataReceiver[isock].WaitingForFrame )
			{
				break;
			}
		}

		if( isock <= EMDMDSD_Http )
		{
			s32 CountBytesNeeded = DataReceiver[isock].IncomingFrameDataSize - DataReceiver[isock].LengthCurrentFrame;
			s32 CountDataToDiscard = GSMUART.GetRxQueueCount() - CountBytesNeeded;

			if( CountDataToDiscard > 0 )
			{
				// Throw away extra bytes (the latest to arrive)
				GSMUART.DiscardData( CountDataToDiscard );

				// Insert message end
				GSMUART.AddRxByte( '\r' );
				GSMUART.AddRxByte( '\n' );
				GSMUART.AddRxByte( 'O' );
				GSMUART.AddRxByte( 'K' );
				GSMUART.AddRxByte( '\r' );
				GSMUART.AddRxByte( '\n' );

				// Tell AT processor to try again
				Result = true;

				Log(LOG_SUB_MDM,LOG_LVL_WARN, "Fifo fixed for socket %d, %d bytes truncated to get frame size %d\n",
							isock,
							CountDataToDiscard,
							DataReceiver[isock].IncomingFrameDataSize );
			}
			else
			{
				Log(LOG_SUB_MDM,LOG_LVL_WARN, "Fifo fix error on socket %d: %d < %d - %d\n",
							isock,
							GSMUART.GetRxQueueCount(),
							DataReceiver[isock].IncomingFrameDataSize,
							DataReceiver[isock].LengthCurrentFrame );
			}
		}
	}
	return Result;
*/

	return true;
}


void UbloxHandler_PeriodicTask( void )
{
	// TODO: We need to HandlerFullFifo with Ublox???
	UBL_OnSendReceive( HandleFullFifo );


	if(Smdm.CallInfoChanged)
	{
		/*
		if(AreWeConnectedToOtherSide() && Smdm.MuteIncomingAudioPath)
        {
            Smdm.MuteIncomingAudioPath = false;
            CIN_SendUrgentCommand(true,CIN_CMD_SetAudioMode5);
        }
        else if(!AreWeConnectedToOtherSide() && !Smdm.MuteIncomingAudioPath)
        {
            Smdm.MuteIncomingAudioPath = true;
            CIN_SendUrgentCommand(true,CIN_CMD_SetAudioMode4);
        }
        */


        Smdm.CallInfoChanged = false;

		// Update call manager
		Mdm_AcmUpdateCallState();
	}

	UbloxHandler_MdmMainTask();
}



bool IsPwrMgrRequestPowerDownState( void )
{
	return (Sreq.CurrentPwrMgrReq.Req == EMDMAR_StartSleep || Sreq.CurrentPwrMgrReq.Req ==EMDMAR_StartOff) ? true:false;
}

void Mdm_InterruptCurrentMsg( void )
{
	Smdm.AbortCurrentMsg = true;
}

EMDM_Result Mdm_SendDataTaskRequest(SMSG_Outgoing *Ptr)
{

	if((IsModemInPoweredDownProc() || IsModemInSleepProc()) || IsPwrMgrRequestPowerDownState()){
		if(Ptr->FlashHeader.Scheme.Fields.MessageTypeWithPhone == EMSGMT_Normal_Message)
			return EMDMR_OperationNotAllowed;
		else
			Mdm_PwrMgrTaskRequest(EMDMAR_StartOn);

	}else if((UParamUpdate.changed.ip || UParamUpdate.changed.apn) && Ptr->FlashHeader.Scheme.Fields.MessageTypeWithPhone == EMSGMT_Normal_Message){
		return EMDMR_OperationNotAllowed;
	}

	Sreq.NextMsgMgrReq.Req = ((Ptr->FlashHeader.Scheme.Fields.MessageTypeWithPhone != EMSGMT_Normal_Message)&&(Ptr->FlashHeader.Scheme.Fields.MessageTypeWithPhone != EMSGMT_Normal_Message_WithDtmfNumber))? EMDMAR_SendDataHighPriority:EMDMAR_SendData;
	Sreq.NextMsgMgrReq.Out.length = Ptr->FlashHeader.length;
    memcpy( &Sreq.NextMsgMgrReq.Out.Scheme, &Ptr->FlashHeader.Scheme, sizeof( Sreq.NextMsgMgrReq.Out.Scheme ) );
	Sreq.NextMsgMgrReq.Out.PhoneDest = Mdm_GetPhoneNumber((EMSGS_MessageTypeWithPhone)Ptr->FlashHeader.Scheme.Fields.MessageTypeWithPhone);
	Sreq.NextMsgMgrReq.Out.data	= Ptr->data;
	Sreq.NextMsgMgrReq.Out.DtmfStr = (char*)Ptr->Ptr_DtmfStr;
	UbloxHandler_SetModemSendingStatus(Modem_StillSending);

	return EMDMR_MdmProcessing;
}

char* Mdm_GetPhoneNumber(EMSGS_MessageTypeWithPhone KindOfMsg)
{

	switch (KindOfMsg){

	case EMSGMT_Normal_Message_WithDtmfNumber:
		return &SParameters.SystemParameters_EmergencyButtonPhoneNumber[0];
	break;

	case EMSGMT_Assistance_button_Press_Alert_Message:
		return &SParameters.SystemParameters_AssistanceButtonPhoneNumber[0];
	break;

	case EMSGMT_Emergency_button_press_Alert_Message:
		return &SParameters.SystemParameters_EmergencyButtonPhoneNumber[0];
	break;

	case EMSGMT_Accident_Alert_Message:
		return &SParameters.SystemParameters_AccidentPhoneNumber[0];
	break;

	default:
		return (char*)NULL;
	break;

	}
}

EMDM_Result Mdm_PwrMgrTaskRequest(EMDM_ApplicationRequest req)
{

	EMDM_Result eResult = EMDMR_MdmProcessing;
	Sreq.CurrentPwrMgrReq.Req = req;
	return eResult;
}

bool IsAtCommandShouldbeAborted( void )
{
	bool ret = false;
	EMDM_GprsReceiveState		GprsReceive_sm = UbloxHandler_GetMdmGprsReceiveState();

	if(GprsReceive_sm == EMDMGRS_GprsDataIdle)
		ret = true;

return ret;
}



//ACM API
EMDM_Result Mdm_AcmRequestDial( const char *num, FATC_ResponseCallback callback)
{

	EMDM_State sm = UbloxHandler_GetMdmState();

	if(sm == EMDMS_MainMdmTask){
		if(Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRoam || PendingSamuCallOnFailure){
            //Allow to start SAMU call despite registration status
            //GSMUART_SendByte(ATC_ESC);
			Smdm.AcmPendingDial = true;
			UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_SetVoiceDial, num);
		}
		else
			return EMDMR_NoGsmCoverage;
	}
	else
		return	EMDMR_Busy;

return EMDMR_Ok;
}

EMDM_Result Mdm_AcmRequestHangUp( u8 CallIndx , FATC_ResponseCallback callback)
{
	EMDM_Result ret = EMDMR_Ok;

	if(CallIndx != 0xff)
	{
		UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_SpecificCallHoldMultiparty,1,CallIndx + 1);
	}
	else
	{
		//UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_GeneralCallHoldMultiparty,1);
		UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_SetVoiceHangup);
	}
	return ret;
}

EMDM_Result Mdm_AcmRequestReject( u8 CallIndx , FATC_ResponseCallback callback)
{
	EMDM_Result ret = EMDMR_Ok;

    if (CallIndx >= MAX_CALLES_HANDLED){
        return EMDMR_OperationNotAllowed;
    }

    if(Smdm.Call[CallIndx].state == EACMSTC_Incoming)
        UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_SetVoiceHangup);
    else
        UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_GeneralCallHoldMultiparty,0);

return ret;
}

EMDM_Result Mdm_AcmRequestAnswer( u8 CallIndx , FATC_ResponseCallback callback )
{
	EMDM_Result ret = EMDMR_Ok;

    if (CallIndx >= MAX_CALLES_HANDLED){
        return EMDMR_OperationNotAllowed;
    }

    if(Smdm.Call[CallIndx].state == EACMSTC_Incoming)
        UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_SetVoiceAnswer);//,2,CallIndx + 1);
    else
        UBL_SendUrgentCommandOverride(true, callback , UBL_CMD_GeneralCallHoldMultiparty,2);
return ret;
}

EMDM_Result Mdm_AcmRequestConference( FATC_ResponseCallback callback )
{
	EMDM_Result ret = EMDMR_Ok;
	EMDM_State sm = UbloxHandler_GetMdmState();
	if (sm == EMDMS_MainMdmTask)
	{
		UBL_SendUrgentCommand(true, UBL_CMD_GeneralCallHoldMultiparty,2);
		UBL_SendUrgentCommandOverride(true, callback, UBL_CMD_GeneralCallHoldMultiparty,3);
	}
	else
	{
		ret =	EMDMR_Busy;
	}
return ret;
}

EMDM_Result Mdm_AcmRequestSwitchCalls( u8 HoldCallIndx , FATC_ResponseCallback callback)
{
	EMDM_Result ret = EMDMR_Ok;
	EMDM_State sm = UbloxHandler_GetMdmState();

	if(sm == EMDMS_MainMdmTask){
		UBL_SendUrgentCommandOverride(true, callback, UBL_CMD_GeneralCallHoldMultiparty,2);
	}
	else
		ret =	EMDMR_Busy;

return ret;
}

EMDM_Result Mdm_AcmRequestHoldCall( FATC_ResponseCallback callback )
{
	EMDM_Result ret = EMDMR_Ok;
	EMDM_State sm = UbloxHandler_GetMdmState();

	if(sm == EMDMS_MainMdmTask){
		UBL_SendUrgentCommandOverride(true, callback, UBL_CMD_GeneralCallHoldMultiparty,2);
	}
	else
		ret =	EMDMR_Busy;
return ret;
}

EMDM_Result Mdm_AcmRequestUnHoldCall( u8 CallIndx , FATC_ResponseCallback callback)
{
	EMDM_Result ret = EMDMR_Ok;
	EMDM_State sm = UbloxHandler_GetMdmState();

	if(sm == EMDMS_MainMdmTask){
		if(CallIndx != 0xFF)
			UBL_SendUrgentCommandOverride(true, callback, UBL_CMD_SpecificCallHoldMultiparty,2,CallIndx + 1);
		else
			UBL_SendUrgentCommandOverride(true, callback, UBL_CMD_GeneralCallHoldMultiparty,2);
	}
	else
		ret =	EMDMR_Busy;
return ret;
}

EMDM_Result Mdm_AcmRequestReDialLastNumber( FATC_ResponseCallback callback )
{

	EMDM_Result ret = EMDMR_Ok;
	EMDM_State sm = UbloxHandler_GetMdmState();

	if(sm == EMDMS_MainMdmTask){

		if(Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRoam){
			UBL_SendUrgentCommandOverride(true, callback, UBL_CMD_SetVoiceRedialLastNumber);
		}
		else
			ret = EMDMR_NoGsmCoverage;
	}
	else
		ret =	EMDMR_Busy;

return ret;
}

EMDM_Result Mdm_AcmRequestDtmf( u8 *Dtmf , FATC_ResponseCallback callback)
{
	EMDM_Result ret = EMDMR_Ok;

	EMDM_State sm = UbloxHandler_GetMdmState();

	if(sm == EMDMS_MainMdmTask){
		UBL_SendUrgentCommand(true, UBL_CMD_DtmfAndToneGeneration , Dtmf);
	}
	else
		ret =	EMDMR_Busy;

	return ret;
}

EMDM_Result Mdm_AcmRequestMuteInternalRingtone( void )
{
	UBL_SendUrgentCommand(true, UBL_CMD_DefaultRingToneDisable , Dtmf);
	Log(LOG_SUB_MDM,LOG_LVL_TRACE, "Default Ringtone Disabled\n" );
	return EMDMR_Ok;
}

EMDM_Result Mdm_AcmRequestUnmuteInternalRingtone( void )
{
	UBL_SendUrgentCommand(true, UBL_CMD_DefaultRingToneEnable , Dtmf);
	Log(LOG_SUB_MDM,LOG_LVL_TRACE, "Default Ringtone Enabled\n" );
	return EMDMR_Ok;
}

u8 static SwappedNibble( u8 c )
{
                u8 LeftDigit = (c & 0x0f);
                u8 RightDigit = ((c & 0xf0) >> 4);

                return (10*LeftDigit + RightDigit);
}

u32 GetServiceCentreTimeStampToRtcDifference( void )
{
	u32 			MinutesPassedSinceTheSMSwasSent;
    URTC_DateTime   CurrTime;
    URTC_DateTime   SMS_SentTime;

    // If the time hasn't been updated return 0 minutes.
    if ( GetIsSystemStartTimeSet () == false )
    {
    	return 0;
    }

	SMS_SentTime.Year = SwappedNibble(Smdm.sms.rx.RxData.scts[0]) + 2000;
	SMS_SentTime.Month = (ERTCGEN_Month)SwappedNibble(Smdm.sms.rx.RxData.scts[1]);
	SMS_SentTime.Day = SwappedNibble(Smdm.sms.rx.RxData.scts[2]);
	SMS_SentTime.Hour = SwappedNibble(Smdm.sms.rx.RxData.scts[3]);
	SMS_SentTime.Min = SwappedNibble(Smdm.sms.rx.RxData.scts[4]);
	SMS_SentTime.Sec = SwappedNibble(Smdm.sms.rx.RxData.scts[5]);

	RTC_GetDateTime(&CurrTime);

	// MSB (After swapping nibbels mark negative TZ)
	// TZ units are 15 minutes
	s16 TZ = SwappedNibble(Smdm.sms.rx.RxData.scts[6]&0xf7) * 15;
    if(Smdm.sms.rx.RxData.scts[6] & 0x8)
    {
        TZ *= -1;
    }

	// We've seen network in Ven without time zone info in the SMS, then we take the value from the time zone parameter
	if(TZ==0)
	{
		TZ = SParameters.SystemParameters_TimeZone;
	}

	// Change the time to UTC according to TZ
	u32 SentTimeInMinutes = RTC_ConvertToMinutes(&SMS_SentTime) - TZ;
	u32 CurrTimeInMinutes = RTC_ConvertToMinutes(&CurrTime);

    MinutesPassedSinceTheSMSwasSent = (CurrTimeInMinutes > SentTimeInMinutes) ? CurrTimeInMinutes - SentTimeInMinutes : 0;

    return MinutesPassedSinceTheSMSwasSent;
}

void Mdm_AcmSetPendingEmgMsg( bool state )
{
	Smdm.AcmPendingEmgMsg = state;
}

void Mdm_Mdm_AcmSetPendingDial( bool state )
{
	Smdm.AcmPendingDial = state;
}

void Mdm_AcmSetPendingAbort( bool state)
{
	Smdm.AcmPendingAbort = state;
}

void Mdm_AcmPendingAbortCall( bool state)
{
	Smdm.AcmPendingAbortCall = state;
}


void HttpDnldGetProgress( u32* pBytesDownloaded )
{
	*pBytesDownloaded = 0;
	if( Smdm.HandlersState.GprsHttpDownload == EMDMGHD_NewFrameReceived ||
		Smdm.HandlersState.GprsHttpDownload == EMDMGHD_WaitWhileReading ||
		Smdm.HandlersState.GprsHttpDownload == EMDMGHD_GprsDownloadEnded )
	{
		*pBytesDownloaded =  Smdm.Ota.RecivedDataSize;
	}
}


EATC_Result MDM_CALLBACK_HttpRequestInfo(u16 cmdid, u8* data, u16 length)
{
	EATC_Result eResult = ATC_Ok;
	// Close send info pending command without OK and prepare to send data command
	eResult = (EATC_Result)( (u32)eResult | (u32)UBL_ClosePendingCommand() );

	if(eResult == ATC_Ok){
		eResult = ATC_InProc;

      GSMUART.Write(Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data, strlen((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data));
      BZERO(Smdm.Gprs[EMDMDSD_Http].DataReceiver);

	}

return eResult;
}

void MDM_API_sendListDownloadGetRequest(char* urlpath, char* filename, char* massege)
{
	char request[300];
	sprintf(request,
			"GET %s HTTP/1.1\r\nHost: %s%s\r\nAccept: */*;\r\nUser-Agent: PNG HttpHandler , IMEI = %s\r\n\r\n",
			filename,
			urlpath,
			massege,
			Smdm.Info.imei);
    GSMUART.Write(request, strlen(request));
}


//////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////

bool MDM_API_DowmlodFileFromURL(char*  urlPath,
								char* filename,
								u32 toAdressOffset ,
								int maxSize,
								MDM_CALLBACK_DownlaodFileResponse finshCallBack ,
								MDM_CALLBACK_DownlaodFileFrameResponse frameCallBack )
{
	if(UbloxHandler_GetHttpDownloadState() != EMDMGHD_Idle || UbloxHandler_GetMdmState() != EMDMS_MainMdmTask)
	{
		return false;
	}
	//lets start
	UbloxHandler_SetHttpDownloadState(EMDMGHD_InitHttpService);
	BZERO(Smdm.Ota.BinariesServer);
	BZERO(Smdm.Ota.Filename);

	Log(LOG_SUB_MDM,LOG_LVL_TRACE, "MDM_API_DowmlodFileFromURL %s%s \n", urlPath, filename );

	if(memcmp(urlPath, "http://", strlen("http://")) == 0) {
		Log(LOG_SUB_MDM,LOG_LVL_ERROR,"server url cannot start with http:// \n");
		return false;
	}
	if(memcmp(urlPath, "https://", strlen("https://")) == 0) {
		urlPath += strlen("https://");
	}
	//coppy address and file path
	strncpy(Smdm.Ota.BinariesServer , urlPath , sizeof(Smdm.Ota.BinariesServer) - 1);
	strncpy(Smdm.Ota.Filename , filename , sizeof(Smdm.Ota.Filename) - 1);
	//get flash start offset
	Smdm.Ota.StartFlashOffset = toAdressOffset;
    Smdm.Ota.RecivedDataSize = 0;
    Smdm.Ota.ContentLength = 0;
	//Get callback function
	Smdm.Ota.finishCallback = finshCallBack;
	Smdm.Ota.frameCallback = frameCallBack;
	Smdm.Ota.maxSize = maxSize;
	Smdm.Ota.HttpRequestType = EMDMHRT_Get;

return true;
}

bool  MDM_GetModificationDateOfFileInURL (char*  urlPath, char* filename ,MDM_CALLBACK_DownlaodFileResponse finshCallBack)
{
	if(UbloxHandler_GetHttpDownloadState() != EMDMGHD_Idle || UbloxHandler_GetMdmState() != EMDMS_MainMdmTask)
	{
		return false;
	}
	Log(LOG_SUB_MDM,LOG_LVL_TRACE, "MDM_GetModificationDateOfFileInURL %s %s \n", urlPath, filename );
	//lets start
	UbloxHandler_SetHttpDownloadState(EMDMGHD_InitHttpService);
	BZERO(Smdm.Ota.BinariesServer);
	BZERO(Smdm.Ota.Filename);

	//coppy address and file path
	strncpy(Smdm.Ota.BinariesServer , urlPath , sizeof(Smdm.Ota.BinariesServer) - 1);
	strncpy(Smdm.Ota.Filename , filename , sizeof(Smdm.Ota.Filename) - 1);
	//get flash start offset
	//Get callback Cunction
	Smdm.Ota.finishCallback = finshCallBack;
	Smdm.Ota.frameCallback = UbloxHandler_SaveHttpFrameToFlash;
	Smdm.Ota.HttpRequestType = EMDMHRT_Head;
    Smdm.Ota.RecivedDataSize = 0;
return true;
}

void UbloxHandler_SaveHttpFrameToFlash( EMDM_GprsHttpDownload state, const u8* buffer , u16 size, u32 ContentLength )
{
	bool Ret = true;
	Log(LOG_SUB_MDM,LOG_LVL_TRACE, "Flash %04d bytes => %06d\n", size, Smdm.Ota.RecivedDataSize );

	for(int i = 0 ; i < size ; i++)
	{
		Smdm.Ota.Sum += buffer[i];
		Smdm.Ota.Xor ^= buffer[i];
	}

	if(FlashWrite((Smdm.Ota.StartFlashOffset + Smdm.Ota.RecivedDataSize) , buffer , size) != Flash_Success)
	{
		Ret = false;
	}

	if ( !Ret )
	{
		UbloxHandler_SetHttpDownloadError( EMDMGHDE_FlashError );
	}

	UbloxHandler_SetHttpDownloadState(EMDMGHD_ProcessingFrameFinished);
}

void HttpNormalDownload( void )
{

	static u16 errors_count = 0;
	static u32 prev_time = 0;
	static u16 seqnum = 0;
	static u32 FrameTimer;
	EATC_Response response;
    char * HeaderEnd = NULL;
	char * ptr = NULL;
	char * end = NULL;
	u16 length = 0;
	EMDM_GprsHttpDownload sm = UbloxHandler_GetHttpDownloadState();

	switch(sm)
	{
	case EMDMGHD_Idle:break;
	case EMDMGHD_InitHttpService:
		Smdm.Ota.DownloadRetryCount = 0;
		Smdm.Ota.Sum = 0;
		Smdm.Ota.Xor = 0;
		UbloxHandler_SetHttpDownloadState(EMDMGHD_HttpCreateSocket);
	break;
	case EMDMGHD_HttpCreateSocket:
			Smdm.Gprs[ EMDMDSD_Http ].ServiceOpenTimeOut = 0;
			// Signal GPRS task to open http connection
			Smdm.Ota.HttpConnection = true;
			UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitCreateSocket);
	break;
	case EMDMGHD_WaitCreateSocket:
			if(Smdm.Gprs[EMDMDSD_Http].SocketAssigned != EMDMDSD_Undefined  && Smdm.Gprs[EMDMDSD_Http].ServiceState == EMDMSS_SocketEstablished)
			{
				UbloxHandler_SetHttpDownloadState(EMDMGHD_HttpRequestInfo);
			}
			break;
	case EMDMGHD_HttpRequestInfo:


			// Verify there is enough free space in commands queue
			if (ATC_GetFreeCommandsSpace(&UBL_ATC) < 3) {
				break;
			}

            BZERO(Smdm.Gprs[EMDMDSD_Http].DataReceiver);

			if(Smdm.Ota.HttpRequestType == EMDMHRT_Get)
			{
				sprintf((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data,
					 "GET %s HTTP/1.1\r\nHost: %s\r\nRange: bytes=%d-\r\nAccept: */*;\r\nUser-Agent: PNG HttpHandler , IMEI = %s\r\n\r\n",
					 Smdm.Ota.Filename,
					 Smdm.Ota.BinariesServer,
                     Smdm.Ota.RecivedDataSize,
					 Smdm.Info.imei);
			}
			else
			{

				sprintf((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data,
					 "HEAD %s HTTP/1.1\r\nHost: %s\r\nAccept: */*;\r\nUser-Agent: PNG HttpHandler , IMEI = %s\r\n\r\n",
					 Smdm.Ota.Filename,
					 Smdm.Ota.BinariesServer,
					 Smdm.Info.imei);

			}

			Log(LOG_SUB_MDM,LOG_LVL_DEBUG,"HTTP Requesting Data: %s\n", Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data);

            Smdm.Ota.HttpHeaderDetected = false;

			if(UBL_SendCommandOverride(	true,
													MDM_CALLBACK_HttpRequestInfo,
													UBL_CMD_SetWriteSocketInfo,
													Smdm.Gprs[EMDMDSD_Http].SocketAssigned,
													strlen((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data)) !=0){

						//send empty at command just to remove the OK when we are done
						seqnum = UBL_SendCommand(false, UBL_CMD_SetWriteSocketData);
            			UbloxHandler_SetHttpDownloadState(EMDMGHD_HttpRequestDataWait);
        	}
		break;
	case EMDMGHD_HttpRequestDataWait:
				if( UBL_GetCommandResponse(seqnum, &response) == ATC_Ok )
				{
					if(response == EAR_Ok)
					{
						UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileReading);
					}
					else if (response == EAR_Error || response == EAR_Timeout)
					{
						UbloxHandler_SetHttpDownloadError(EMDMGHDE_ConnectTimeout);
					}
				}
				else
				{
					UbloxHandler_SetHttpDownloadError( EMDMGHDE_AtCmdFail );
				}
			break;

	case EMDMGHD_WaitWhileReading:

       	if( UbloxHandler_IsSocketErrorOccurred( EMDMDSD_Http ) )
		{
			break;
		}
        else if( Smdm.Ota.RecivedDataSize  >= Smdm.Ota.ContentLength - 1 )
        {
          // Download is complete, all data arrived
          UbloxHandler_SetHttpDownloadState( EMDMGHD_GprsDownloadEnded );
		  UbloxHandler_CloseSocket(EMDMDSD_Http);

        }
		else if( TmrIsTimeOccured_ms(FrameTimer, MDM_HTTP_FRAME_TIMEOUT_ms) )
		{
			FrameTimer = TmrGetTime_ms();
			if( ++Smdm.Ota.FrameRetryCount < MDM_HTTP_FRAME_RETRY )
			{
				Log(LOG_SUB_MDM,LOG_LVL_WARN,"HTTP Requesting Frame, retry = %d\n", Smdm.Ota.FrameRetryCount);
				// Trigger data request
				Smdm.Gprs[EMDMDSD_Http].RxDataToRead = 1;
			}
			else if( ++Smdm.Ota.DownloadRetryCount < MDM_HTTP_DOWNLOAD_RETRY )
			{
				u32 BytesDownloaded;
				HttpDnldGetProgress( &BytesDownloaded );
				Log(LOG_SUB_MDM,LOG_LVL_WARN,"HTTP Frame Fail, restart download (%d bytes downloaded)\n", BytesDownloaded);
				UbloxHandler_SetHttpDownloadState(EMDMGHD_HttpCreateSocket);
				UbloxHandler_CloseSocket(EMDMDSD_Http);
			}
			else
			{
				u32 BytesDownloaded;
				HttpDnldGetProgress( &BytesDownloaded );
				Log(LOG_SUB_MDM,LOG_LVL_ERROR,"HTTP Download Fail, cancel download (%d bytes downloaded)\n", BytesDownloaded);
				UbloxHandler_SetHttpDownloadError( EMDMGHDE_DownloadRetryError );
			}
		}
		break;
	case EMDMGHD_NewFrameReceived:
		FrameTimer = TmrGetTime_ms();
		Smdm.Ota.FrameRetryCount = 0;
		Smdm.Ota.DownloadRetryCount = 0;

		//we didnt detect header yet
		if(!Smdm.Ota.HttpHeaderDetected)
		{
			//look for end of Http header
			if((HeaderEnd = strstr((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data , "\r\n\r\n")) != NULL )
			{
				//check if right header
				if(( ptr = strstr((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data,  "HTTP/1.1 ") ) != NULL )
				{
					//Check response code
					Smdm.Ota.sc = (EMDM_HttpStatusCode)atoi(&ptr[strlen("HTTP/1.1 ")]);
					//if response code PartialContent - we use range in case of retries
					if(Smdm.Ota.sc == EMDMHSC_PartialContent || Smdm.Ota.sc == EMDMHSC_OK)
					{
						//get last modified date
						if(( ptr = strstr((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data,  "Last-Modified: ") ) != NULL )
						{
                            ptr = &ptr[strlen("Last-Modified: ")];
							if(( end = strstr((char*)ptr , "\r\n") ) != NULL )
							{
								//clac the length of the Last Modified string
								length = MIN((end-ptr) , (sizeof(Smdm.Ota.LastModified) - 1));
								//copy it to the right buffer
								memcpy(Smdm.Ota.LastModified , ptr , length);
								//make sure strin ends correctlly
								Smdm.Ota.LastModified[length] = '\0';
								//if its a get command 	read also the size of the file we are downloading
							}
							else
							{
									//try to read more data from modem to see if we can find reset of header
  									UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileReading);
							}
						}

						if( ( Smdm.Ota.HttpRequestType == EMDMHRT_Get ) && ( EMDMGHD_WaitWhileReading != UbloxHandler_GetHttpDownloadState() ) )
						{
							//get the file size
							if(( ptr = strstr((char*)Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data,  "Content-Length: ") ) != NULL )
							{
								// if Smdm.Ota.ContentLength is not 0 is a retry, we do not read the length because it will be lower
								if(Smdm.Ota.ContentLength == 0)
								{
									Smdm.Ota.ContentLength = MIN(atoi(&ptr[strlen("Content-Length: ")]) , Smdm.Ota.maxSize);
								}
								//check the file is not empty
								if(Smdm.Ota.ContentLength != 0){
									//write to flash left overs
									length  = (u16)((u8*)&HeaderEnd[strlen("\r\n\r\n")] - &Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data[0]);
									if(Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length > length)
									{

										UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileProcessingFrame);
										if( nullptr != Smdm.Ota.frameCallback )
										{
											Smdm.Ota.frameCallback( sm, &Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data[length] , Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length - length - 3, Smdm.Ota.ContentLength );
											Smdm.Ota.RecivedDataSize += (Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length - length - 3);
										}

									}
								}
								else{
									UbloxHandler_SetHttpDownloadState(EMDMGHD_GprsDownloadEnded);
								}
							}
							else
							{
								//try to read more data from modem to see if we can find reset of header
								UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileReading);
							}
						}
						else
						{
							UbloxHandler_SetHttpDownloadState(EMDMGHD_GprsDownloadEnded);
						}

					}
					else
					{
                        Log(LOG_SUB_MDM,LOG_LVL_INFO, "Http download Error %d\n" , Smdm.Ota.sc);
						UbloxHandler_SetHttpDownloadError( EMDMGHDE_404NotFoundError );
					}
				}

			}
			else
			{
					//try to read more data from modem to see if we can find reset of header
						UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileReading);
			}
		}
		//all data bytes received -1 since we use amout of bytes vs offset
		else{
			//we start to copy from data[1] to skip the starting " and we copy 4 byte less because modem add to the data the following extra bytes ""\r\n

			UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileProcessingFrame);
			if( nullptr != Smdm.Ota.frameCallback )
			{
				Smdm.Ota.frameCallback( sm, &Smdm.Gprs[EMDMDSD_Http].DataReceiver.Data[1] , Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length - 4, Smdm.Ota.ContentLength );
				Smdm.Ota.RecivedDataSize += (Smdm.Gprs[EMDMDSD_Http].DataReceiver.Length - 4);
			}

      }
	break;
	case EMDMGHD_ProcessingFrameFinished:
			//Clean GPRS buffer after handling data
		BZERO(Smdm.Gprs[EMDMDSD_Http].DataReceiver);

		if(UbloxHandler_GetHttpDownloadState() != EMDMGHD_Error){
			UbloxHandler_SetHttpDownloadState(EMDMGHD_WaitWhileReading);
		}
		//keep reading
		Smdm.Ota.HttpHeaderDetected = true;
	break;
	case EMDMGHD_GprsDownloadEnded:
		// Wait for client to handle state
	break;
	case EMDMGHD_Error:
		// Wait for client to handle state
	break;
	case EMDMGHD_WaitWhileProcessingFrame:
		// wait forcall back to process frame.
		break;

	default:
		break;

	}
}
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
EMDM_GprsTrafficDownload UbloxHandler_GetTrafficDownloadState( void )
{
	return Smdm.HandlersState.GprsTrafficDownload;
}

void  UbloxHandler_SetTrafficDownloadError( u16 error )
{
	Log(LOG_SUB_MDM,LOG_LVL_WARN, "TrafficDnld Error %d\n", error );
	// Close the download service
	UbloxHandler_SetTrafficDownloadState( EMDMGTD_Error );
	UbloxHandler_CloseSocket(EMDMDSD_Traffic);
	Smdm.Traffic.error = (EMDM_GprsTrafficDownloadError)error > EMDMGTDE_InternalErrors? (EMDM_GprsTrafficDownloadError)error:EMDMGTDE_SocketError;
}


EATC_Result MDM_CALLBACK_TrafficRequestInfo(u16 cmdid, u8* data, u16 length)
{
	EATC_Result eResult = ATC_Ok;
	// Close send info pending command without OK and prepare to send data command
	eResult = (EATC_Result)( (u32)eResult | (u32)UBL_ClosePendingCommand() );

	if(eResult == ATC_Ok){
		eResult = ATC_InProc;

      GSMUART.Write(Smdm.Traffic.Request, Smdm.Traffic.RequestSize);

	}

return eResult;
}


bool MDM_API_DowmlodTrafficInformation(char*  urlPath, char* reqData , u32 reqDataSize  , MDM_CALLBACK_DownlaodTrafficResponse finshCallBack , u32 timeout)
{
	if(UbloxHandler_GetTrafficDownloadState() != EMDMGTD_Idle || UbloxHandler_GetMdmState() != EMDMS_MainMdmTask)
	{
		return false;
	}


	BZERO(Smdm.Traffic);
	BZERO(Smdm.Gprs[EMDMDSD_Traffic].DataReceiver);

	//lets start
	UbloxHandler_SetTrafficDownloadState(EMDMGTD_InitTrafficService);

	//coppy address and file path
	strncpy(Smdm.Traffic.BinariesServer , urlPath , sizeof(Smdm.Traffic.BinariesServer) - 1);
	//Get callback function
	Smdm.Traffic.callback = finshCallBack;

	memcpy(Smdm.Traffic.Request , reqData , reqDataSize);
	Smdm.Traffic.RequestSize = reqDataSize;
	Smdm.Traffic.Timeout = timeout;

return true;
}


void UbloxHandler_SetTrafficDownloadState( EMDM_GprsTrafficDownload state )
{
	Smdm.HandlersState.GprsTrafficDownload = state;


	if( state == EMDMGTD_Idle )
	{
		Smdm.Gprs[EMDMDSD_Traffic].DataReceiver.Length = 0;
		Smdm.Gprs[EMDMDSD_Traffic].RxDataToRead = 0;

	}else if( state == EMDMGTD_GprsDownloadEnded || state == EMDMGTD_Error)
    {
		if(Smdm.Traffic.callback != NULL){
			Smdm.Traffic.callback(state , (char*)&Smdm.Gprs[EMDMDSD_Traffic].DataReceiver.Data[1] , Smdm.Gprs[EMDMDSD_Traffic].DataReceiver.Length - 4);
			Smdm.Traffic.callback = NULL;
			//after calling to the callback clear the buffer
			BZERO(Smdm.Gprs[EMDMDSD_Traffic].DataReceiver);
		}


		//GO BACK TO iDLE AFTER CALL BACK
		Smdm.HandlersState.GprsTrafficDownload = EMDMGTD_Idle;
    }
}

void UbloxHandler_CancelTrafficDownload( void )
{
	if( Smdm.HandlersState.GprsTrafficDownload != EMDMGTD_Idle )
	{
		UbloxHandler_SetTrafficDownloadState( EMDMGTD_Idle );
		UbloxHandler_CloseSocket(EMDMDSD_Traffic);
	}
}

void TrafficDownloadHandler( void )
{
	static u16 seqnum = 0;
	EATC_Response response;
	EMDM_GprsTrafficDownload st = UbloxHandler_GetTrafficDownloadState();

	switch(st)
	{
	case EMDMGTD_Idle:break;
	case EMDMGTD_InitTrafficService:
		UbloxHandler_SetTrafficDownloadState(EMDMGTD_TrafficCreateSocket);
		break;
	case EMDMGTD_TrafficCreateSocket:
		Smdm.Gprs[ EMDMDSD_Traffic ].ServiceOpenTimeOut = 0;
		// Signal GPRS task to open http connection
		Smdm.Traffic.TrafficConnection = true;
		UbloxHandler_SetTrafficDownloadState(EMDMGTD_WaitCreateSocket);
		break;
	case EMDMGTD_WaitCreateSocket:
		if(Smdm.Gprs[EMDMDSD_Traffic].SocketAssigned != EMDMDSD_Undefined  && Smdm.Gprs[EMDMDSD_Traffic].ServiceState == EMDMSS_SocketEstablished)
		{
			UbloxHandler_SetTrafficDownloadState(EMDMGTD_TrafficRequestInfo);
		}

		break;
	case EMDMGTD_TrafficRequestInfo:

		if(UBL_SendCommandOverride(	true,
													MDM_CALLBACK_TrafficRequestInfo,
													UBL_CMD_SetWriteSocketInfo,
													Smdm.Gprs[EMDMDSD_Traffic].SocketAssigned,
													Smdm.Traffic.RequestSize) !=0){

						//send empty at command just to remove the OK when we are done
						seqnum = UBL_SendCommand(false, UBL_CMD_SetWriteSocketData);
            			UbloxHandler_SetTrafficDownloadState(EMDMGTD_TrafficRequestDataWait);
        }

		break;
	case EMDMGTD_TrafficRequestDataWait:
		if( UBL_GetCommandResponse(seqnum, &response) == ATC_Ok )
		{
			if(response == EAR_Ok)
			{
				UbloxHandler_SetTrafficDownloadState(EMDMGTD_WaitWhileReading);
				Smdm.Traffic.RequestTimeout = TmrGetTime_ms();
			}
			else if (response == EAR_Error || response == EAR_Timeout)
			{
				UbloxHandler_SetTrafficDownloadError(EMDMGTDE_ConnectTimeout);
			}
		}
		else
		{
			UbloxHandler_SetTrafficDownloadError( EMDMGTDE_AtCmdFail );
		}

		break;
	case EMDMGTD_WaitWhileReading:
		if( UbloxHandler_IsSocketErrorOccurred( EMDMDSD_Traffic ) )
		{
			break;
		}

		else if( TmrIsTimeOccured_ms(Smdm.Traffic.RequestTimeout, Smdm.Traffic.Timeout) )
		{
			UbloxHandler_SetTrafficDownloadError( EMDMGTDE_RequestTimeout );
		}
		break;
	case EMDMGTD_NewFrameReceived:
			UbloxHandler_SetTrafficDownloadState(EMDMGTD_GprsDownloadEnded);
			UbloxHandler_CloseSocket(EMDMDSD_Traffic);
		break;
	case EMDMGTD_GprsDownloadEnded:break;
	case EMDMGTD_Error:break;
	}
}


///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////


static const char* const SocketStates[] = {
	"EMDMSS_SocketInactive",
	"EMDMSS_SocketListen",
	"EMDMSS_SocketSynSnd",
	"EMDMSS_SocketSynRcev",
	"EMDMSS_SocketEstablished",
	"EMDMSS_SocketFinWait1",
	"EMDMSS_SocketFinWait2",
	"EMDMSS_SocketCloseWait",
	"EMDMSS_SocketClosing",
	"EMDMSS_SocketLastAck",
	"EMDMSS_SocketTimeWait",
	"EMDMSS_SocketUndefined",
};

const char* Mdm_GetSocketStatus( EMDM_DestSocketDef SocketType )
{
	return SocketStates[Mdm_GetSocketState(SocketType)];
}

EMDM_ServiceState Mdm_GetSocketState( EMDM_DestSocketDef SocketType )
{
	return Smdm.Gprs[SocketType].ServiceState;
}

#if CLI_SUPPORT_ENABLED && defined (__USE_MODEM)

void Mdm_PrintState( char* pArgs )
{
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "Modem State:\n" );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--              Main: %s (%d)\n", GetMdmStatus(), UbloxHandler_GetMdmState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--              Call: %d\n", UbloxHandler_GetMdmCallState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--          Gprs Snd: %s (%d)\n", Mdm_GetGprsSendStatus(), UbloxHandler_GetMdmGprsSendState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--          Gprs Rcv: %d\n", UbloxHandler_GetMdmGprsReceiveState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--         Http Dnld: %s (%d)\n", UbloxHandler_GetHttpDownloadStatus(), UbloxHandler_GetHttpDownloadState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--           SMS Snd: %d\n", UbloxHandler_GetMdmSmsSendState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--           SMS Rcv: %d\n", UbloxHandler_GetMdmSmsReceiveState() );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--      Gprs Network: %d\n", Smdm.NetStat.Gprs.NetStat );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--       GSM Network: %d\n", Smdm.NetStat.Gsm.NetStat );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--       Message Snd: %d\n", Mdm_MesageStatus );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--   Signal Strength: %d\n", Smdm.NetStat.Rssi );
	Log(LOG_SUB_MDM,LOG_LVL_INFO, "--  Last Modem Event: [%s]\n", ATC_GetLastReceivedData() );
    Log(LOG_SUB_MDM,LOG_LVL_INFO, "--        SIM Status: %s\n", MODEM_SIM_STATES[Smdm.Info.Sim] );
	int iCall;

	for ( iCall = 0; iCall < elementsof( Smdm.Call ); iCall++ )
	{
		Log(LOG_SUB_MDM,LOG_LVL_INFO, "--    Call-%d, State-%d\n", iCall, Smdm.Call[iCall].state );
	}
}
#endif

void UbloxHandler_ModemEvent(EUBL_Event Event)
{
	AcmModemEvent(Event);
}

#define MAX_SMS_COUNT_BEFORE_RESET	5
#define MAX_DIAL_COUNT_BEFORE_RESET	3
#define MAX_GPRS_COUNT_BEFORE_RESET	10

void UbloxHandler_MdmNoDialToneDetected( void )
{
	Smdm.ErrCount.DialErrorCounter++;
}

void MdmSmsErrorDetected( u16 ErrorCode )
{
	Smdm.CmsLastError = ErrorCode;
	switch( ErrorCode )
	{
		// Count networking problems
		case EMDMCMS_ShortMessageTransferRejected:
		case EMDMCMS_FacilityRejected:
		case EMDMCMS_NetworkOutOfOrder:
		case EMDMCMS_TemporaryFailure:
		case EMDMCMS_Congestion:
		case EMDMCMS_ScBusy:
		case EMDMCMS_MeFailure:
		case EMDMCMS_SimBusy:
		case EMDMCMS_NoNetworkService:
		case EMDMCMS_NetworkTimeout:
		//case EMDMCMS_PsBusy:
		//case EMDMCMS_SmBlNotReady:
		//case EMDMCMS_MeTemporaryNotAvailable:
			Smdm.ErrCount.SendSmsErrorCounter++;
		break;
	}
}

void UbloxHandler_ResetMultipleErrorCounters( void )
{
	Smdm.ErrCount.DialErrorCounter = Smdm.ErrCount.SendSmsErrorCounter = Smdm.ErrCount.SendGprsErrorCounter = 0;
}

void ResetModemOnMultipleErrorHandler( void )
{
	if( (!IsAnyActiveCall() && ACM_IsEmergencyModeActive() == false )&&
		( Smdm.ErrCount.DialErrorCounter >=  MAX_DIAL_COUNT_BEFORE_RESET  ||
		  Smdm.ErrCount.SendSmsErrorCounter >= MAX_SMS_COUNT_BEFORE_RESET ||
		  Smdm.ErrCount.SendGprsErrorCounter >= MAX_GPRS_COUNT_BEFORE_RESET ) )
	{
		if( UbloxHandler_GetMdmState() == EMDMS_MainMdmTask )
		{


            //not used in AGS2 insted we reset directlly
            // Airplane mode is used as a "lite reset" (Cinterion workaround)
            //UbloxHandler_SetMdmState(EMDMS_StartAirplaneMode);

            //full reset
            Log(LOG_SUB_DLS,LOG_LVL_WARN,"Modem will be reset due to multiple error on handler\n");
            Log(LOG_SUB_DLS,LOG_LVL_TRACE,"Error Counters(max): Dial %d(%d) - SmsMsg %d(%d) - GprsMsg %d(%d)\n",
                                            Smdm.ErrCount.DialErrorCounter, MAX_DIAL_COUNT_BEFORE_RESET,
                                            Smdm.ErrCount.SendSmsErrorCounter, MAX_SMS_COUNT_BEFORE_RESET,
                                            Smdm.ErrCount.SendGprsErrorCounter, MAX_GPRS_COUNT_BEFORE_RESET );
            UbloxHandler_SetMdmState(EMDMS_ConfigError);
		}
		UbloxHandler_ResetMultipleErrorCounters();
	}
}

bool Mdm_IsBusyGsmCall( void )
{
	bool is_busy = 	   Smdm.AcmPendingDial
					|| UbloxHandler_GetMdmCallState() != EMDMCS_CallIdle
					|| IsAnyActiveCall() == true;

	return is_busy;
}

bool Mdm_NeedToWake( void )
{
	// If there are pending SMSs, need to read them
	return ( Smdm.sms.rx.RxInfo.Pending > 0 || Smdm.sms.rx.RxInfo.Index > 0 );
}


EMDM_SmsDcsFormat GetReceivedSmsDcs( void )
{
	return (EMDM_SmsDcsFormat)Smdm.sms.rx.RxData.dcs;
}

EMDM_PhoneFormat GetReceivedSmsPhoneFormat( void )
{
	return (EMDM_PhoneFormat)Smdm.sms.rx.RxData.phone_number_address_type.Value;
}


/*******************************************************************************
NAME:           UpdateSIMStatus
PARAMETERS:	    Sim status, enum of EMDM_SimStatus
RETURN VALUES:  void
DESCRIPTION:   	Enable or Disable the DTC of the SIM and update the status
				in the global variable Smdm.Info.Sim
*******************************************************************************/
static void UbloxHandler_UpdateSIMStatus ( EMDM_SimStatus status )
{

	Smdm.Info.Sim = status;

	if ( status == EMDMSS_SimCardNotPresent )
	{
		GD_DTC_SetDtcEvent( DTC_B1021_3C, true );
		Set_F_IMSI_Valid(false);
		Set_F_ICCID_Valid(false);
	}
	else
	{
		GD_DTC_SetDtcEvent( DTC_B1021_3C, false );
	}

	Log(LOG_SUB_MDM,LOG_LVL_INFO, "Sim status %d\n",Smdm.Info.Sim);
}


#endif

//if enable we check here if we got a valid direct sms number
//we check for atlist known  number
bool IsDirectSmsNumber( const char *number )
{
	bool res = false;
	bool PlusInclude;
	u16 i,len,lenInc = strlen(number);
	//SMS known number
	const char *DirectSmsArray[] = 	{	SParameters.SystemParameters_Knownnumber1forSMS,
										SParameters.SystemParameters_Knownnumber2forSMS,
										SParameters.SystemParameters_Knownnumber3forSMS,
										SParameters.SystemParameters_Knownnumber4forSMS};

	// If known numbers are not required, always approve the SMS.
	if ( SParameters.DirectSMSCommands_DirectSMSKnownNumber == 0 ){
		return true;
	}
	for(i = 0 ; i < elementsof(DirectSmsArray) ; i++){
		len = (u16)strlen(DirectSmsArray[i]);
		PlusInclude = (DirectSmsArray[i][0] == '+')? true:false;
		if(lenInc < ((PlusInclude)? (len - 1): len) || len == 0 || lenInc == 0)
			continue;
		else if(!strcmp(&number[lenInc - ((PlusInclude)? (len - 1): len)] , &DirectSmsArray[i][((PlusInclude)? 1:0)])){
			res = true;
			break;
		}
	}
	return res;
}




//SLCC if incoming call is Ziltok
EMDM_CallerIDCommands IsZiltokCall( const char* num )
{
	EMDM_CallerIDCommands res = EMDMCIC_CmdLast;

	//SMS known number
	char * const ZiltokCall[] = 	{	SParameters.CallerIDcommand_KnownOfficeNumberForSocket1Opening1,
										SParameters.CallerIDcommand_KnownOfficeNumberForSocket1OpeningDRP,
										SParameters.CallerIDcommand_KnownOfficeNumberForSocket2Opening1,
										SParameters.CallerIDcommand_KnownOfficeNumberForSocket2OpeningDRP};

	for ( u16 i = 0 ; i < std::size( ZiltokCall ) ; i++ ){
		if ( PhoneNumbers_IsNumbersMatch( num, ZiltokCall[i] ) )
		{
			res = (EMDM_CallerIDCommands)i;
			break;
		}
	}
	return res;

}



const ZiltokCmd CommandCall[EMDMCIC_CmdLast] = {    &Commands_CallerIDSocket1Open,
                                                    &Commands_CallerIDSocket1Open,
                                                    &Commands_CallerIDSocket2Open,
                                                    &Commands_CallerIDSocket2Open };

ZiltokCmd CommandCallCmd (EMDM_CallerIDCommands cmd)
{
    if   (cmd < EMDMCIC_CmdLast)
    {
        return (CommandCall[cmd]);
    }
    else
    {
        return NULL;
    }
}

bool IsNoGsmReg( void )
{
	return (Smdm.NetStat.Gsm.NetStat != EMDMNS_RegHome && Smdm.NetStat.Gsm.NetStat != EMDMNS_RegRoam)? true:false;
}

bool IsGsmReg( void )
{
	return (Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRoam)? true:false;
}




void P8MDM_SetModemServiceState( EMDM_NetworkStatus state )
{
	Smdm.NetStat.Gsm.NetStat = state;
#if defined (__P8)
	if(Smdm.NetStat.Gsm.NetStat == EMDMNS_RegHome || Smdm.NetStat.Gsm.NetStat == EMDMNS_RegRom){
		PwrMgrMdmClearTimer();
	}
#endif
}

/*******************************************************************************
NAME:           OnlineExternalAntennaMonitor
PARAMETERS:	    void
RETURN VALUES:  void
DESCRIPTION:   	Drives the state machine used to read the status of the external
				antenna once the basic configuration is ended
*******************************************************************************/

EModemAntennaState ConvertUbloxAntennaStatus( s8 st )
{
    switch(st)
    {
    case -1: return Modem_Antenna_Disconnected;break;
    case 0: return Modem_Antenna_Shorted_to_gnd;break;
    }
return Modem_Antenna_Connected;
}

bool UbloxOnlineExternalAntennaMonitor(void)
{
	EATC_Response CmdResponse;
	bool ret = false;
	CurrentModemAntennaType = SParameters.Input_ExternalGSMAntenna;

	if( CurrentModemAntennaType != LastModemAntennaType ) {
		if( CurrentModemAntennaType == EAntType_External_Shark ) {
			SModemAntenna.Sm  = EMDMAMS_Idle;
		}
		else if( CurrentModemAntennaType == EAntType_External_Regular){
			UBL_SendCommand ( true , UBL_CMD_SelectExternalAntenna );
			SModemAntenna.ExternalAntennaUsed = true;
			ret = true;
		}
		else
		{
			UBL_SendCommand ( true , UBL_CMD_SelectInternalAntenna );
			SModemAntenna.ExternalAntennaUsed = false;
			ret = true;
		}
		LastModemAntennaType = CurrentModemAntennaType;
	}

	if( CurrentModemAntennaType == EAntType_External_Shark ) {

		switch(SModemAntenna.Sm)
		{
			case EMDMAMS_Idle:
				SModemAntenna.Timer = TmrGetTime_ms();
				SModemAntenna.Sm = EMDMAMS_WaitSampleTime;
			break;

			case EMDMAMS_WaitSampleTime:
				if( TmrIsTimeOccured_ms(SModemAntenna.Timer, MDM_ANTENNA_SAMPLE_TIME)  ) {
					SModemAntenna.ErrorRetryCount = 0;
					SModemAntenna.Sm = EMDMAMS_SendCmdToReadAntennaStatus;
				}
			break;

			case EMDMAMS_SendCmdToReadAntennaStatus:
				SModemAntenna.CmdId = UBL_CMD_GetAntennaStatus;
				SModemAntenna.SeqNum = UBL_SendCommand( false, SModemAntenna.CmdId );
				SModemAntenna.Sm = EMDMAMS_WaitResponseOfCmdToReadAntennaStatus;
			break;

			case EMDMAMS_WaitResponseOfCmdToReadAntennaStatus:

				if ( UBL_GetCommandResponse(SModemAntenna.SeqNum, &CmdResponse) == ATC_Ok ) {
					if ( CmdResponse == EAR_Ok ) {
						EModemAntennaState st = ConvertUbloxAntennaStatus(SModemAntenna.Data);

                        DTC_CheckModemAntenna(st);

						if( st != Modem_Antenna_Connected )
						{
							if(SModemAntenna.ExternalAntennaUsed)
							{
								UBL_SendCommand ( true , UBL_CMD_SelectInternalAntenna );
								SModemAntenna.ExternalAntennaUsed = false;
							}
						}
						else
						{
							if( !SModemAntenna.ExternalAntennaUsed )
							{
								UBL_SendCommand ( true , UBL_CMD_SelectExternalAntenna );
								SModemAntenna.ExternalAntennaUsed = true;
							}
						}

						SModemAntenna.Sm = EMDMAMS_Idle;
						ret = true;
					}
					else if ( CmdResponse == EAR_Timeout || CmdResponse == EAR_Error ) {
						SModemAntenna.ErrorRetryCount++;
						SModemAntenna.Sm = EMDMAMS_SendCmdToReadAntennaStatus;
					}
				}

				if( SModemAntenna.ErrorRetryCount >= MDM_ANTENNA_MAX_RETRY_CMDS ) {
					SModemAntenna.ErrorRetryCount = 0;
					SModemAntenna.Sm = EMDMAMS_ErrorWhileSendingCmd;
				}
			break;

			case EMDMAMS_ErrorWhileSendingCmd:
				Log(LOG_SUB_MDM,LOG_LVL_WARN, "External Antenna monitor error while sending command %d\n", SModemAntenna.CmdId );
				SModemAntenna.Sm = EMDMAMS_Idle;
			break;

			default:
			break;
		}
	}
return ret;
}

/*******************************************************************************
NAME:           UbloxOnlineMDMJammingMonitor
PARAMETERS:	     void
RETURN VALUES:  void
 DESCRIPTION:   	Drives the state machine used to read the jamming enabling
 				once the basic configuration is ended
*******************************************************************************/

bool UbloxOnlineMDMJammingMonitor(void)
{
	EATC_Response CmdResponse;
	bool ret = false;

	switch(SJammingEnaMon.Sm)
	{
		case EMDMJMS_Idle:
			if( SJammingEnaMon.LastConfig!= (bool)SParameters.Jamming_Enable_MDM_Jamming_Detection) {
				SJammingEnaMon.Sm = EMDMJMS_SendCmdToConfigureJammingMon;
			}
		break;

		case EMDMJMS_SendCmdToConfigureJammingMon:
			if( SJammingEnaMon.LastConfig == true ){
				SJammingEnaMon.SeqNum = UBL_SendCommand( false, UBL_CMD_JammingDetectionEna,
														 SParameters.Jamming_MDM_Jamming_MinNum_Of_2G_Carriers ,
														 SParameters.Jamming_MDM_Jamming_rxlev_threshold ,
														 SParameters.Jamming_MDM_Jamming_MinNum_Of_3G_Carriers ,
														 SParameters.Jamming_MDM_Jamming_RSSI_Carriers  );
			}
			else{
				SJammingEnaMon.SeqNum = UBL_SendCommand( false, UBL_CMD_JammingDetectionDis );
			}

			SJammingEnaMon.Sm = EMDMJMS_WaitResponseOfConfigureJammingMon;
		break;

		case EMDMJMS_WaitResponseOfConfigureJammingMon:
			if ( UBL_GetCommandResponse(SJammingEnaMon.SeqNum, &CmdResponse) == ATC_Ok ) {
				if ( CmdResponse == EAR_Ok || EAR_Timeout || CmdResponse == EAR_Error ) {
					SJammingEnaMon.LastConfig = SParameters.Jamming_Enable_MDM_Jamming_Detection;
					SJammingEnaMon.Sm = EMDMJMS_Idle;
				}
			}
		break;

		default:
		break;
	}
return ret;
}


/*******************************************************************************
NAME:           UbloxOnlineCellStatus
PARAMETERS:	    void
RETURN VALUES:  void
DESCRIPTION:   	Drives the state machine used to read the cell status
				according to CLI command
*******************************************************************************/

static void UbloxOnlineCellStatus(void)
{
	EATC_Response CmdResponse = EAR_Undefined;

	switch(SModemCellStatus.Sm)
	{
		case EMDCS_Idle:

			if( SModemCellStatus.FrecuencyMonitor != 0 ) {
				SModemCellStatus.ErrorRetryCount = 0;
				SModemCellStatus.Timer = TmrGetTime_ms();
				SModemCellStatus.Sm = EMDCS_WaitSampleTime;
			}

		break;

		case EMDCS_WaitSampleTime:

			if( TmrIsTimeOccured_ms( SModemCellStatus.Timer, SModemCellStatus.FrecuencyMonitor*1000) ) {
				SModemCellStatus.Sm = EMDCS_SendCmdToReadCellStatus;
			}

		break;

		case EMDCS_SendCmdToReadCellStatus:

			SModemCellStatus.SeqNum = UBL_SendCommand( false, UBL_CMD_CellStatus);
			SModemCellStatus.Sm = EMDCS_WaitResponseOfCmdToReadCellStatus;

		break;

		case EMDCS_WaitResponseOfCmdToReadCellStatus:
			if ( UBL_GetCommandResponse(SModemCellStatus.SeqNum, &CmdResponse) == ATC_Ok ) {
				if ( CmdResponse == EAR_Ok )
				{
					SModemCellStatus.Sm = EMDCS_Idle;
				}
				else if ( CmdResponse == EAR_Timeout || CmdResponse == EAR_Error )
				{
					SModemCellStatus.ErrorRetryCount++;
					SModemCellStatus.Sm = EMDCS_SendCmdToReadCellStatus;
				}
			}

			if( SModemCellStatus.ErrorRetryCount >= MDM_CELSTATUS_MAX_RETRY_CMDS ) {
				SModemCellStatus.ErrorRetryCount = 0;
				SModemCellStatus.Sm = EMDCS_ErrorWhileSendingCmd;
			}
		break;

		case EMDCS_ErrorWhileSendingCmd:
			Log(LOG_SUB_MDM,LOG_LVL_INFO,"Cell status, error while sending command \n");
			SModemCellStatus.Sm = EMDCS_Idle;
		break;

		default:
		break;
	}

}

void SetMonitorFrecuencyTime ( u8 time)
{
	SModemCellStatus.FrecuencyMonitor = time;
}


EATC_Result UbloxHandler_CellStatus(u16 cmdid, char* data, u16 length)
{

	char buffer[CELLSTATUS_RESPONSE_MAX_SIZE];
	BZERO(buffer);

    if( length <= ATC_MSG_END_LENGTH || length>= CELLSTATUS_RESPONSE_MAX_SIZE -1 ){
      return ATC_InProc;
    }

	if ( strncasecmp( data, "OK",strlen("OK") ) == 0)
	{
		return ATC_Ok;
	}
	else
	{
		strncpy(buffer,data,(length)-1);
		Log(LOG_SUB_MDM,LOG_LVL_INFO,"Cell Status: %s", buffer);
		return ATC_InProc;
	}

}

void UbloxHandler_UpdateInternalPdpIpAddress(u16 cmdid, char* data , u16 lenght)
{
	u16 ProfileId;
	u16 ParamTag;
	char IPaddress[IP_ADDRESS_MAX_SIZE];

	//   Parse: +UPSND: <profile_id>,<param_tag>,<dynamic_param_val>
	// Example: +UPSND: 2,0,"151.9.78.170"

	BZERO(IPaddress);
	sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%hd,%hd,\"%[^\"]", &ProfileId, &ParamTag, IPaddress);
	strncpy(Smdm.OwnIP, IPaddress, MIN(IP_ADDRESS_MAX_SIZE-1, strlen(IPaddress)));
}


void UbloxHandler_UpdateExternalPdpIpAddresses(u16 cmdid, char* data , u16 lenght)
{
	u16	PdpContext;
	char IPaddress[IP_ADDRESS_MAX_SIZE];

	//   Parse: +CGPADDR: <cid>,<PDP_addr>
	// Example: +CGPADDR: 1,"91.80.104.82"

	BZERO(IPaddress);
	sscanf((char*)&data[strlen(UBL_Commands[cmdid].Response)], "%hd,\"%[^\"]", &PdpContext, IPaddress);

	switch( (EMDM_PacketDataProtocolContextId)PdpContext )
	{
		case EMDMPDPCI_ExternalContext2:
			strncpy(Smdm.WifiIP, IPaddress, MIN(IP_ADDRESS_MAX_SIZE-1, strlen(IPaddress)));
		break;
		default:
		break;
	}

}

/*******************************************************************************
NAME:           IsVoiceAndDataCommAllowed
PARAMETERS:	    void
RETURN VALUES:  bool
DESCRIPTION:   	This function returns TRUE when the voice + data communication
				is allowed at the same time when the Radio Access Technology
				provided by the network is one of the following types:

				- EMDMRAT_Utrain
				- EMDMRAT_UtrainWithHsdpa1
				- EMDMRAT_UtrainWithHsdpa2
				- EMDMRAT_UtrainWithHsdpaAndHsupa
*******************************************************************************/

bool IsVoiceAndDataCommAllowed( void )
{
	bool ret;

	ret = false;
	if( Smdm.NetStat.Gprs.RadioAccessTech == EMDMRAT_Utrain ||
		Smdm.NetStat.Gprs.RadioAccessTech == EMDMRAT_UtrainWithHsdpa1 ||
		Smdm.NetStat.Gprs.RadioAccessTech == EMDMRAT_UtrainWithHsdpa2 ||
		Smdm.NetStat.Gprs.RadioAccessTech == EMDMRAT_UtrainWithHsdpaAndHsupa ) {
		ret = true;
	}

	return ret;
}

void Ublox_SetSysStartIndication( bool status )
{
    Smdm.SysStartDetected = status;
}

void UbloxHandler_StartIPPolling( bool value )
{
    SModemCheckIPAddress.isPPPConnected = value;
    if(!value) {
        SModemCheckIPAddress.Sm = EMDMCIP_Idle;
    }
}

bool UbloxHandler_IsModemFirmwareUpdateIdle( void )
{
	return ((UBLMFO_GetHandlerState() == UBLFOS_Idle) && (!UBL_IsModemFirmwareUpdate()));
}


void UbloxHandler_ResetModem( void )
{
	UblMdmPowerUp();
	UbloxHandler_SetMdmState(EMDMS_Undefined);
}


char* UbloxHandler_GetOwnIPAddress( void )
{
	return Smdm.OwnIP;
}

char* UbloxHandler_GetWifiIPAddress( void )
{
	return Smdm.WifiIP;
}
