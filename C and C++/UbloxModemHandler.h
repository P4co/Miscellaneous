#ifndef __UBLOX_MODEM_HANDLER_H
#define __UBLOX_MODEM_HANDLER_H

#include "Ublgsm.h"
#include "AudioCallMgr.h"
#include "UblErrDefinition.h"
#include "UbloxAudioFile.h"
#include "OutgoingImpl.h"

#include <functional>

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_KNOWN_NUMBERS					4
#define MDM_DTMF_MAX_SIZE					29// + 1 null

#if defined (__A1)|| defined (__F1)
#define MDM_APP_DATA_MAX_LENGTH				1500
#define MDM_APP_DATA_MAX_READ				1024
#else
#define MDM_APP_DATA_MAX_LENGTH				10
#endif

#define WAIY_INIT_END_TIMEOUT_ms			60000L
#define WAIY_MDM_CALL_ACTIVITY_TIMEOUT_ms	10000L
#define MAX_OPEN_SOCKET_RETRY				3
#define MDM_HTTP_FRAME_TIMEOUT_ms			60000L
#define MDM_TRAFFIC_FRAME_TIMEOUT_ms		30000L
#define MDM_HTTP_FRAME_RETRY				3
#define MDM_HTTP_DOWNLOAD_RETRY				5
#define MDM_CONFIG_CMD_INTERVAL_ms			150
#define MDM_CONFIG_CMD_MAX_ERRORS			3
#define MAX_CALLES_HANDLED					7
#define CMD_SEQ_RETRY_TIME_ms				100
#define MDM_AIRPLANE_MODE_RESET_TIME		500
#define MDM_SHUTDOWN_TO_OFF_TIMEOUT_ms		2000
#define MDM_WAIT_FOR_SLEEP_TIMEOUT_ms		5000
#define MDM_SMS_POLL_INTERVAL				7000
#define MDM_KEEPALIVE_INTERVAL				(MDM_SMS_POLL_INTERVAL * 10)
#define MDM_KEEPALIVE_RETRIES				3

#define PHONE_MAX_SIZE						TELEPHONE_MAX_SIZE
#define SMS_MAX_SIZE						160 // Max size of 7Bit sms

#define MAX_ERROR_RETRY						3
#define MAX_WAIT_TCP_ACK_RETRY				20//20 sec max since the interval is 1000ms
#define MDM_MAX_GPRS_EMERGENCY_RETRY		1

#define MAX_TCP_DESTINATIONS				2
#define MAX_HTTP_DESTINATIONS				1
#define MAX_TCP_GENERAL_PURPOSE				1
#define CONNECTION_ID						0
#define CONNECTION_ID_STR					"0"
#define MODEM_ANTENNA_SHORT_TO_GND			0
#define MODEM_ANTENNA_OPEN_CIRCUIT			-1

#define FILE_LAST_MODIFIED_DATE_SIZE		35
#define TRAFFIC_REQUEST_MAX_SIZE			30
//protocl identifiers

#define OFFICE_MSG_END						{'\r'/*,'\n'*/}
#define OFFICE_MSG_END_LENGTH				1

#define OFFICE_MSG_SUFFIX_LENGTH			( END_OF_DATA_CHARACTER_LENGTH + CHECKSUM_MAX_LENGTH + END_OF_MESSAGE_LENGTH )

#define MDM_MSG_END_LENGTH					2


#define IMEI_MAX_SIZE_STR 19
#if (IMEI_MAX_SIZE_STR + 1) != IMEI_MAX_SIZE
#error adjust string size to max-1
#endif

#define IMSI_MAX_SIZE_STR 19
#if (IMSI_MAX_SIZE_STR + 1) != IMSI_MAX_SIZE
#error adjust string size to max-1
#endif

#define ICCID_MAX_SIZE_STR 39
#if (ICCID_MAX_SIZE_STR + 1) != ICCID_MAX_SIZE
#error adjust string size to max-1
#endif

#define FWVER_MAX_SIZE_STR 24
#if (FWVER_MAX_SIZE_STR + 1) != FWVER_MAX_SIZE
#error adjust string size to max-1
#endif

#define MDM_MODEL_MAX_SIZE_STR 29
#if (MDM_MODEL_MAX_SIZE_STR + 1) != MDM_MODEL_MAX_SIZE
#error adjust string size to max-1
#endif

#define IP_ADDRESS_MAX_SIZE_STR 15
#if (IP_ADDRESS_MAX_SIZE_STR + 1) != IP_ADDRESS_MAX_SIZE
#error adjust string size to max-1
#endif

#define ME_MESSAGE_STORAGE              "ME"
#define SM_MESSAGE_STORAGE              "SM"
#define ME_AND_SM_MESSAGE_STORAGE       "MT"
#define BROADCAST_MESSAGE_STORAGE       "BM"
#define STATUS_REPORT_STORAGE           "SR"

#define CELLSTATUS_RESPONSE_MAX_SIZE    100

#define OTA_FILENAME_SIZE 		OTAFILENAME_MAX_SIZE+10


typedef enum {
	EMDMSR_ErrHeadToBig = 1,
	EMDMSR_ErrMsgEmpty,
	EMDMSR_SplitEnd,
	EMDMSR_SplitOnGoing,
}EMDM_SplitResult;


typedef enum {
	EMDMDSD_GprsDestination_1 = 0,
	EMDMDSD_GprsDestination_2,
	EMDMDSD_Traffic,
	EMDMDSD_Http,
	EMDMDSD_ModemInternal,
	EMDMDSD_Last,
	EMDMDSD_Undefined = 0xff,
}EMDM_DestSocketDef;

typedef enum {
	EMDMR_Ok						= 0,
	EMDMR_Busy,
	EMDMR_NoGsmCoverage,
	EMDMR_NoGprsCoverage,
	EMDMR_OperationNotAllowed,
	EMDMR_SentOk,
	EMDMR_SentFailed,
	EMDMR_Dailing,
	EMDMR_ActiveVoice,
	EMDMR_MdmOff,
	EMDMR_MdmOn,
	EMDMR_MdmSleep,
	EMDMR_MdmAborting,
	EMDMR_MdmProcessing,
	EMDMR_MdmFatalError,
	EMDMR_MdmIdle,
} EMDM_Result;


typedef const char* (*GprsDestFunc)(void);

typedef struct {
	u16	        conParmTag;
 	const char* conParmValue;
}SMDM_InternetSetup;

typedef enum {
	EMDMSF_PduMode = 0,
	EMDMSF_TextMode = 0,
}EMDM_SmsFormat;

typedef enum {
	EMDMDF_7bit 	= 0x00,
	EMDMDF_8bit 	= 0x04,
	EMDMDF_16bit 	= 0x08,
}EMDM_SmsDcsFormat;

typedef enum {
	SMDMMTI_CommandRes = 0,
	SMDMMTI_UrcEvent,
}SMDM_MessageTypeIs;

typedef enum{
	SMDMNTI_Gprs = 0,
	SMDMNTI_Gsm,
}SMDM_NetworkTypeIs;

typedef enum {
	EMDMPF_Undefined			= -1,
	EMDMPF_NumberRestricted		= 0x80,
	EMDMPF_National 			= 0x81,
	EMDMPF_International 		= 0x91,
}EMDM_PhoneFormat;

typedef enum {
	EMDMAD_GprsDetach = 0,
	EMDMAD_GprsAttach = 1,
}EMDM_AttachDetach;

typedef enum {
    // WARNING: Ublox Sara-U only can have 3 External PDP contexts (From 1 to 3)
    EMDMPDPCI_NotSupported = 0,
    EMDMPDPCI_ExternalContext1,
    EMDMPDPCI_ExternalContext2,
    EMDMPDPCI_ExternalContext3,
}EMDM_PacketDataProtocolContextId;

typedef enum {
	EMDMPDPCA_Deactivate = 0,
	EMDMPDPCA_Activate = 1,
}EMDM_PacketDataProtocolContextAction;

typedef enum {
	EMDMFC_DisableFlowControl = 0,
	EMDMFC_XONXOFFSoftwareFlowControl,
	EMDMFC_RTSCTSHardwareFlowControl = 3,
}EMDM_FlowControl;

typedef enum {
	EMDMJP_JammingType = 0,
	EMDMJP_Min2GCarrierstoDetect,
	EMDMJP_Rxlev_thres_2G,
	EMDMJP_Min3GCarrierstoDetect,
	EMDMJP_Rssi_thres_3G,
}EMDM_JammingParams;

typedef enum {

	// WARNING: Update MODEM_GPRSSND_STATE in UbloxHandler.cpp when changing

	EMDMGSS_GprsDataIdle = 0,
	EMDMGSS_CheckGprsRegStatus,
	EMDMGSS_CheckGprsAttachStatus,
    EMDMGSS_WaitCheckGprsAttachStatus,
	EMDMGSS_StartGprsAttach,
	EMDMGSS_WaitGprsAttach,
	EMDMGSS_StartCheckConnectionStatus,
    EMDMGSS_WaitCheckConnectionStatus,
	EMDMGSS_StartConnectionDeactivation,
    EMDMGSS_WaitConnectionDeactivation,
	EMDMGSS_StartConnectionActivation,
	EMDMGSS_WaitConnectionActivation,
	EMDMGSS_CreateSocket,
	EMDMGSS_WaitCreateSocket,
	EMDMGSS_StartCheckSocketService,
	EMDMGSS_WaitCheckSocketService,
	EMDMGSS_WaitStartHttpSslConfiguretion,
	EMDMGSS_HttpSslConfiguretion,
	EMDMGSS_StartOpenSocketService,
	EMDMGSS_WaitOpenSocketService,
	EMDMGSS_WaitOpenSocketConnection,
	EMDMGSS_CriticalSectionStart,
	EMDMGSS_StartSendGprsData,
	EMDMGSS_WaitSendGprsData,
	EMDMGSS_CriticalSectionEnd,
	EMDMGSS_SendGprsOk,
	EMDMGSS_SendAckOk,
	EMDMGSS_SendGprsError,
	EMDMGSS_NetGprsError,
	EMDMGSS_CloseCurrentService,
	EMDMGSS_StartGprsCloseService,
	EMDMGSS_WaitGprsCloseService,
	EMDMGSS_StartGprsDetach,
	EMDMGSS_WaitGprsDetach,
	EMDMGSS_StartGprsCloseConnection,
	EMDMGSS_WaitGprsCloseConnection,
	EMDMGSS_WaitNetworkRelaxTime,

}EMDM_GprsSendState;

typedef enum{

	// WARNING: Update MODEM_HTTP_DNLD_STATE in CinterionMgr.c when changing

	EMDMGHD_Idle = 0,
	EMDMGHD_InitHttpService,
	EMDMGHD_HttpCreateSocket,
	EMDMGHD_WaitCreateSocket,
	EMDMGHD_HttpRequestInfo,
	EMDMGHD_HttpRequestDataWait,
	EMDMGHD_WaitWhileReading,
	EMDMGHD_WaitWhileProcessingFrame,
	EMDMGHD_ProcessingFrameFinished,
	EMDMGHD_NewFrameReceived,
	EMDMGHD_GprsDownloadEnded,
	EMDMGHD_Error,
}EMDM_GprsHttpDownload;

typedef enum{
	EMDMGHDE_None = 0,

	EMDMGHDE_InternalErrors = 1000,
	EMDMGHDE_UrlTooLong,
	EMDMGHDE_ConnectTimeout,
	EMDMGHDE_AtCmdFail,
	EMDMGHDE_SocketError,
	EMDMGHDE_404NotFoundError,
	EMDMGHDE_FlashError,
	EMDMGHDE_CrcError,
	EMDMGHDE_DownloadRetryError,
}EMDM_GprsHttpDownloadError;

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
typedef enum{
	EMDMGTD_Idle = 0,
	EMDMGTD_InitTrafficService,
	EMDMGTD_TrafficCreateSocket,
	EMDMGTD_WaitCreateSocket,
	EMDMGTD_TrafficRequestInfo,
	EMDMGTD_TrafficRequestDataWait,
	EMDMGTD_WaitWhileReading,
	EMDMGTD_NewFrameReceived,
	EMDMGTD_GprsDownloadEnded,
	EMDMGTD_Error,
}EMDM_GprsTrafficDownload;

typedef enum{
	EMDMGTDE_None = 0,

	EMDMGTDE_InternalErrors = 1000,
	EMDMGTDE_UrlTooLong,
	EMDMGTDE_ConnectTimeout,
	EMDMGTDE_AtCmdFail,
	EMDMGTDE_SocketError,
	EMDMGTDE_RequestTimeout,
}EMDM_GprsTrafficDownloadError;


///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////


///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////

typedef enum {
    EMDMCIP_Idle = 0,
    EMDMCIP_WaitTimeout,
    EMDMCIP_RequestInternalPdpIp,
    EMDMCIP_WaitInternalPdpIp,
    EMDMCIP_RequestExternalPdpIp,
    EMDMCIP_WaitExternalPdpIp,
}EMDM_CheckIPAddress;

typedef enum {
    Init_SIM_Monitoring = 0,
    Send_ICCID_Command,
    WaitResponceICCID,
}EMDM_CheckSIM;

typedef enum {

	// WARNING: Update MODEM_GPRSRCV_STATE in CinterionMgr.c when changing

	EMDMGRS_GprsDataIdle = 0,
	EMDMGRS_CriticalSectionStart,
	EMDMGRS_StartReadGprsData,
	EMDMGRS_WaitReadGprsData,
	EMDMGRS_CriticalSectionEnd,
	EMDMGRS_GprsDataAvailable,
}EMDM_GprsReceiveState;


typedef enum {

	// WARNING: Update MODEM_SMSSND_STATE in CinterionMgr.c when changing

	EMDMSSS_SmsIdle = 0,
	EMDMSSS_CheckGsmRegStatus,
	EMDMSSS_BuildSms,
	EMDMSSS_CriticalSectionStart,
	EMDMSSS_StartSendSms,
	EMDMSSS_WaitSendSms,
	EMDMSSS_CriticalSectionEnd,
	EMDMSSS_WaitSendIndication,
	EMDMSSS_SendSmsOk,
	EMDMSSS_SendSmsError,
	EMDMSSS_NetSmsError,
}EMDM_SmsSendState;

typedef enum {

	// WARNING: Update MODEM_SMSRCV_STATE in CinterionMgr.c when changing

	EMDMSRS_SmsIdle = 0,
	EMDMSRS_pullPendingSms,
	EMDMSRS_WaitPendingSms,
	EMDMSRS_StartReadSms,
	EMDMSRS_WaitReadSms,
	EMDMSRS_StartDeletSms,
	EMDMSRS_WaitDeletSms,
}EMDM_SmsReceiveState;



typedef struct {
	u8 MsgData[SMS_MAX_SIZE+1];
	u16 MsgDataSize;
	u8 SourcePhoneNum[TELEPHONE_MAX_SIZE+1];
} SMDM_QUEUE_Data;

typedef enum {
EMDMCIC_Open1Sock = 0,
EMDMCIC_Open1SockDrp,
EMDMCIC_Open2Sock,
EMDMCIC_Open2SockDrp,
EMDMCIC_CmdLast,
}EMDM_CallerIDCommands;

typedef enum {

	// WARNING: Update MODEM_CALL_STATE in CinterionMgr.c when changing

	EMDMCS_CallIdle = 0,
	EMDMCS_SentCallAnswer,
	EMDMCS_WaitCallAnswer,
	EMDMCS_SendCallReject,
	EMDMCS_WaitCallReject,
	EMDMCS_SendCall_HangUp,
	EMDMCS_WaitCall_HangUp,
	EMDMCS_SendCallHold,
	EMDMCS_WaitCallHold,
	EMDMCS_SendCallMerge,
	EMDMCS_WaitCallMerge,
	EMDMCS_SendAll_HangUp,
	EMDMCS_WaitAll_HangUp,
	EMDMCS_SendDial,
	EMDMCS_WaitDial,
	EMDMCS_WaitCallEstablished,
	EMDMCS_SendDtmf,
	EMDMCS_WaitDtmf,
	EMDMCS_ActiveCall,
	EMDMCS_CallError,
}EMDM_CallState;


typedef enum {

	// WARNING: Update MODEM_STATE in UbloxModemHandler.cpp when changing

	EMDMS_ActiveInitStart = 0,
	EMDMS_Undefined = EMDMS_ActiveInitStart,
	EMDMS_WaitInitEnd,
	EMDMS_StartConfigBasic,
	EMDMS_ConfigBasic,
	EMDMS_StartConfigBasicG,
	EMDMS_ConfigBasicDone,
    EMDMS_StartRingToneConfig,
    EMDMS_ConfigAntenna,
	EMDMS_StartConfigAudioHw,
	EMDMS_WaitSimLock,
	EMDMS_ConfigAudioHw,
    EMDMS_StartConfigAudioFilters,
    EMDMS_ConfigAudioFilters,
	EMDMS_StartConfigSms,
	EMDMS_ConfigSms,
	EMDMS_StartConfigGprsConnection,
	EMDMS_ConfigGprsConnection,
	EMDMS_StartConfigIndicationsEvents,
	EMDMS_ConfigIndicationsEvents,
	EMDMS_StartCheckNetworkRegistration,
	EMDMS_CheckNetworkRegistration,
	EMDMS_StartGetCallWaitingConfiguration,
	EMDMS_GetCallWaitingConfiguration,
	EMDMS_StartSetCallWaitingConfiguration,
	EMDMS_SetCallWaitingConfiguration,
	EMDMS_ConfigError,
	EMDMS_ConfigErrorPwrReset,
	EMDMS_ActiveInitEnd = EMDMS_ConfigErrorPwrReset,

	EMDMS_ActiveStart,
	EMDMS_StartMainMdmTasks = EMDMS_ActiveStart,
	EMDMS_MainMdmTask,
	EMDMS_StartAirplaneMode,
	EMDMS_StopAirplaneMode,
	EMDMS_WaitEnterAirplaneMode,
	EMDMS_WaitExitAirplaneMode,
	EMDMS_AirplaneMode,
	EMDMS_OpSelectionDeregisterFromNetwork,
	EMDMS_OpSelectionDeregisterFromNetwork_Wait,
	EMDMS_OpSelectionAutomatic,
	EMDMS_OpSelectionAutomatic_Wait,
    EMDMS_AbortSelectionOperation,
	EMDMS_ActiveEnd = EMDMS_AbortSelectionOperation,

	EMDMS_NonActiveStateStart,
	EMDMS_StartSleep = EMDMS_NonActiveStateStart,
	EMDMS_SleepConfig,
	EMDMS_WaitSleep,
	EMDMS_Sleep,
	EMDMS_StartPowerDown,
	EMDMS_CheckGprsAttachState,
    EMDMS_StartPowerDownConfig,
    EMDMS_WaitPowerDownConfig,
    EMDMS_WaitPowerDown,
	EMDMS_FinalizePowerDown,
	EMDMS_PowerDown,
	EMDMS_StartWakeUp,
	EMDMS_WaitWakeUp,
	EMDMS_WakeUpDelay,
	EMDMS_NonActiveStateEnd = EMDMS_WakeUpDelay,

}EMDM_State;

typedef enum {

	// WARNING: Update StatNames in UbloxModemHandler.c when changing

	EMDMNS_Undefined = -1,
	EMDMNS_NotRegNotSrch,
	EMDMNS_RegHome,
	EMDMNS_NotRegSrch,
	EMDMNS_RegDenied,
	EMDMNS_Unknown,
	EMDMNS_RegRoam,
	EMDMNS_RegHomeSmsOnly,
	EMDMNS_RegRoamSmsOnly,
	EMDMNS_RegHomeCsfbNotPref = 9,
	EMDMNS_RegRoamCsfbNotPref,
	EMDMNS_Count,
}EMDM_NetworkStatus;

typedef enum {

	// WARNING: Update StatNames in UbloxModemHandler.c when changing

	EMDMRAT_Undefined = -1,
	EMDMRAT_Gsm,
	EMDMRAT_GsmCompact,
	EMDMRAT_Utrain,
	EMDMRAT_GsmWithEdge,
	EMDMRAT_UtrainWithHsdpa1,
	EMDMRAT_UtrainWithHsdpa2,
	EMDMRAT_UtrainWithHsdpaAndHsupa,
	EMDMRAT_EUtran,
	EMDMRAT_Invalid = 255,
	EMDMRAT_Count = EMDMRAT_Invalid,
}EMDM_RadioAccessTech;

typedef enum {
	EMDMSUBM_InitializationStatus          = 0x0001,
	EMDMSUBM_PhoneBookInitializationStatus = 0x0002,
	EMDMSUBM_ToolkitRefresh                = 0x0004,
}EMDM_SimUrcsBitMask;


typedef _PACKED enum {
    EMDMSS_SimCardNotPresent = 0,
    EMDMSS_SimPINneeded,
    EMDMSS_SimPINblocked,
    EMDMSS_SimPUKblocked,
    EMDMSS_SimNotOperational,
    EMDMSS_SimInRestrictedUse,
    EMDMSS_SimOperational,
    EMDMSS_SimPhoneBookReady,
    EMDMSS_USimPhoneBookReady,
}EMDM_SimStatus;

typedef struct {
	EMDM_SimStatus Sim;
	char	imei[IMEI_MAX_SIZE];
	char 	imsi[IMSI_MAX_SIZE];
	char 	iccid[ICCID_MAX_SIZE];
	char	SwVer[FWVER_MAX_SIZE_STR];
	char	OwnNum[PHONE_MAX_SIZE+1];
#if defined (__A1) || defined ( __F1 )
	char	Model[MDM_MODEL_MAX_SIZE];
#else
    char    Model[10];
#endif
    char    MdmG;
}SMDM_Info;


typedef struct {
	EMDM_NetworkStatus NetStat;
	u32 Lac;
	u32 Ci;
	EMDM_RadioAccessTech RadioAccessTech;
}SMDMN_Network;

typedef struct {
	SMDMN_Network Gsm;
	SMDMN_Network Gprs;
	s8 	Rssi;
	bool NewReg;
    u8 SignalQuality;
}SMDM_NetworkStatus;

typedef struct{
	bool MT_emptySpot;
    u16 Index;
	u16 Pending;
    u16 MaxLocations;
}RX_INFO;

typedef _PACKED enum {
	SMSC_NUMBER_TYPE_UNKNOWN,
	SMSC_NUMBER_TYPE_INTERNATIONAL,
	SMSC_NUMBER_TYPE_NATIONAL,
	SMSC_NUMBER_TYPE_NETWORK_SPECIFIC,
	SMSC_NUMBER_TYPE_SUBSCRIBER,
	SMSC_NUMBER_TYPE_ALPHNUMERIC,
	SMSC_NUMBER_TYPE_ABBREVIATED,
	SMSC_NUMBER_TYPE_RESEVED,
} SMSC_TypeOfNumber;

typedef union {
	u8 Value;
	struct {
		u8 					NumberingPlanId : 4;
		SMSC_TypeOfNumber 	TypeOfNumber 	: 3;
		u8 					Reserved		: 1;
	};
} SMSC_TypeOfAddress;


typedef	struct {
	u16 Cmgr_State;
	u16 Cmgr_Len;
	u8 smsc_info_len_in_bytes;				// smsc -> Short Message Service Center
	SMSC_TypeOfAddress smsc_address_type;
	u8 smsc_number[PHONE_MAX_SIZE+1];		// temp size
	u8 first_octet;
	u8 phone_number_length;
	SMSC_TypeOfAddress phone_number_address_type;
	u8 phone_number[PHONE_MAX_SIZE];
	u8 pid; 								//Protocl Identifier
	u8 dcs;									//Data Codeing Scheme
	u8 scts[8];								//Service Center Time Stamp
	u8 udl;									//User Data Length
#if defined (__USE_MODEM)
	u8 ud[MAX_LENGTH_MSG + LOGGER_HEADER_LENGTH];					//User Data
#else
	u8 ud[SMS_MAX_SIZE];					//User Data
#endif
	u8 NullHolderForIncomongParse;			//in case we have 160 bytes to avoid killing outgoing sms by putting buff[160] = 0;
											//we could make the ud buffer bigger but it will involve more changes in other files.
}RX_DATA;

typedef struct {
	RX_INFO RxInfo;
	RX_DATA RxData;
}SMS_RX;

typedef struct {
	u8 length;
	u8 smsc_info_len;						// smsc -> Short Message Service Center
	u8 first_octet;
	u8 mr;									//Message Reference
	u8 phone_number_length;
	SMSC_TypeOfAddress phone_number_address_type;
	char phone_number[PHONE_MAX_SIZE];
	u8 pid; 								//Protocl Identifier
	u8 dcs;									//Data Codeing Scheme
	u8 vp;									//Validity Period
	u8 udl;									//User Data Length
	u8 udl_converted;						//User Data Length after conversion
#if defined (__USE_MODEM)
	u8 ud[MAX_LENGTH_MSG + LOGGER_HEADER_LENGTH];					//User Data
#else
	u8 ud[SMS_MAX_SIZE];					//User Data
#endif
}SMS_TX;

typedef enum{
	EMDMGSF_SendAlways = 0,
	EMDMGSF_GprsDisable,
	EMDMGSF_NoGprsCoverage,
	EMDMGSF_InVoiceCall,
}EMDM_SmsSendReason;

typedef struct {
	SMS_RX 	rx;
	SMS_TX	tx;
	EMDM_SmsSendReason reason;
    u16     SendingError;
}SMDM_Sms;

typedef enum {

	EMDMSS_SocketInactive = 0,
	EMDMSS_SocketListen,
	EMDMSS_SocketSynSnd,
	EMDMSS_SocketSynRcev,
	EMDMSS_SocketEstablished,
	EMDMSS_SocketFinWait1,
	EMDMSS_SocketFinWait2,
	EMDMSS_SocketCloseWait,
	EMDMSS_SocketClosing,
	EMDMSS_SocketLastAck,
	EMDMSS_SocketTimeWait,
	EMDMSS_SocketUndefined,
}EMDM_ServiceState;

typedef enum {
	EMDMSLE_Undefined = -1,
	EMDMSLE_ServiceOk = 0,
	EMDMSLE_ServiceErrorFirst,
	EMDMSLE_ServiceAborted = EMDMSLE_ServiceErrorFirst,
	EMDMSLE_NoSuchResource,
	EMDMSLE_InterruptedSystemCall,
	EMDMSLE_IOError = 5,
	EMDMSLE_BadFileDescriptor = 9,
	EMDMSLE_NoChildProcesses,
	EMDMSLE_CurrentOperationWouldBlock,
	EMDMSLE_OutOfMemory1,
    EMDMSLE_BadAddress = 14,
    EMDMSLE_InvalidArgument = 22,
    EMDMSLE_BrokenPipe = 32,
    EMDMSLE_FunctionNotImplemented = 38,
    EMDMSLE_ProtocoloNotAvailable = 92,
    EMDMSLE_AddressAlreadyInUse = 98,
    EMDMSLE_SoftwareCausedConnectionAbort = 103,
    EMDMSLE_ConnectionResetByPeer,
    EMDMSLE_NoBufferSpaceAvailable,
    EMDMSLE_TransportEndpointIsNotConnected = 107,
    EMDMSLE_CannotSendAfterTrasnportEndpointShutdown,
    EMDMSLE_ConnectionTimeout = 110,
    EMDMSLE_NoRouteHost = 113,
    EMDMSLE_OperationNowInProgress = 115,
    EMDMSLE_DNSServerReturnedAnswerwithNoData = 160,
    EMDMSLE_DNSServerClaimsQueryWasMisformatted,
    EMDMSLE_DNSServerReturnedGeneralFailure,
    EMDMSLE_DomainNameNotFound,
    EMDMSLE_DNSServerDoesNotImplementRequestedOperation,
    EMDMSLE_DNSServerRefusedQuery,
    EMDMSLE_MisformattedDNSQuery,
    EMDMSLE_MisformattedDomainName,
    EMDMSLE_UnsupportedAddressFamily,
    EMDMSLE_MisformattedDNSReply,
    EMDMSLE_CouldNotContatDNSServers,
    EMDMSLE_TimeoutWhileContactingDNSServers,
    EMDMSLE_EndOfFile,
    EMDMSLE_ErrorReadingFile,
    EMDMSLE_OutOfMemory2,
    EMDMSLE_ApplicationTerminatedLookup,
    EMDMSLE_DomainNameIsTooLong1,
    EMDMSLE_DamainNameIsTooLong2,
	EMDMSLE_ServiceErrorLast,
}EMDM_ServiceLastError;

typedef enum {
	EMDMSO_StartCreate,
    EMDMSO_WaitStartCreate,
    EMDMSO_GetDnsResolution,
    EMDMSO_WaitGetDnsResolution,
	EMDMSO_StartOpen,
	EMDMSO_WaitStartOpen,
	EMDMSO_StartCreateError,
} EMDM_SocketOpeningState;

typedef struct {
	u8 Data[MDM_APP_DATA_MAX_LENGTH];
	s16 Length;
	u16 LengthCurrentFrame;
	u16 IncomingFrameDataSize;
} SMDM_AppDataReceiveBuffer;

typedef struct {
    u8  FilterNumber;
    s16 Coefficient_a0;
    s16 Coefficient_a1;
    s16 Coefficient_a2;
    s16 Coefficient_b1;
    s16 Coefficient_b2;
} SMDM_BiquadFilter;

typedef struct  {
	u16 SocketAssigned;
	u16 ErrorOnSocketOperation;
	EMDM_ServiceState ServiceState;
	EMDM_SocketOpeningState OpeningState;
	u32 rxCount;
	u32 txCount;
	u32 ackData;
	u32 unackData;
	EMDM_ServiceLastError LastError;
	u32 RxDataToRead;
	bool TxWaitAck;
	u32 ReadTimeOut;
	u32 ServiceOpenTimeOut;
	u32 ServiceOpenNoTrafficTimeOut;
	u32 ServiceOpenRetries;
	u16 CmdSeqNum;
	SMDM_AppDataReceiveBuffer DataReceiver;
}SMDM_GprsServiceInfo;

typedef _PACKED struct{
	EMDM_State		State;
	EMDM_CallState 	CallState;
	EMDM_SmsReceiveState	SmsReceiveState;
	EMDM_SmsSendState		SmsSendState;
	EMDM_GprsReceiveState	GprsReceiveState;
	EMDM_GprsSendState		GprsSendState;
	EMDM_GprsHttpDownload	GprsHttpDownload;
	EMDM_GprsTrafficDownload GprsTrafficDownload;
}SMDM_HandlersState;

typedef struct {
    u16     CallSeqNum;
}SMDM_SeqNum;

typedef struct{
	bool    State;
	char phone[PHONE_MAX_SIZE];
}SMDM_SilentInfo;

typedef void (*pfCmdSeqExecuteNextCmd)(void);

typedef struct {
	const EUBL_Command*		CmdSequence;
	u16 					CmdSequenceIndex;
	u32 					TimeOut;
	u32						StartTime;
	u32						RetryTimer;
	bool					SendFail;
	pfCmdSeqExecuteNextCmd 	pfNextCmd;
} SMDM_CMD_SEQUENCE;

typedef enum{
	EMDMHRT_Get = 0,
	EMDMHRT_Head,
}EMDM_HttpRequestType;


typedef enum
{
EMDMHSC_Continue                         = 100,
EMDMHSC_SwitchingProtocols               = 101,
EMDMHSC_OK                               = 200,
EMDMHSC_Created                          = 201,
EMDMHSC_Accepted                         = 202,
EMDMHSC_NonAuthoritativeInformation      = 203,
EMDMHSC_NoContent                        = 204,
EMDMHSC_ResetContent                     = 205,
EMDMHSC_PartialContent                   = 206,
EMDMHSC_MultipleChoices                  = 300,
EMDMHSC_MovedPermanently                 = 301,
EMDMHSC_Found                            = 302,
EMDMHSC_SeeOther                         = 303,
EMDMHSC_NotModified                      = 304,
EMDMHSC_UseProxy                         = 305,
EMDMHSC_TemporaryRedirect                = 307,
EMDMHSC_BadRequest                       = 400,
EMDMHSC_Unauthorized                     = 401,
EMDMHSC_PaymentRequired                  = 402,
EMDMHSC_Forbidden                        = 403,
EMDMHSC_NotFound                         = 404,
EMDMHSC_MethodNotAllowed                 = 405,
EMDMHSC_NotAcceptable                    = 406,
EMDMHSC_ProxyAuthenticationRequired      = 407,
EMDMHSC_RequestTimeout                   = 408,
EMDMHSC_Conflict                        = 409,
EMDMHSC_Gone                            = 410,
EMDMHSC_LengthRequired                  = 411,
EMDMHSC_PreconditionFailed              = 412,
EMDMHSC_RequestEntityTooLarge           = 413,
EMDMHSC_RequestURITooLarge              = 414,
EMDMHSC_UnsupportedMediaType            = 415,
EMDMHSC_Requestedrangenotsatisfiable    = 416,
EMDMHSC_ExpectationFailed               = 417,
EMDMHSC_InternalServerError              = 500,
EMDMHSC_NotImplemented                   = 501,
EMDMHSC_BadGateway                       = 502,
EMDMHSC_ServiceUnavailable               = 503,
EMDMHSC_GatewayTimeout                   = 504,
EMDMHSC_HTTPVersionnotsupported          = 505,
}EMDM_HttpStatusCode;




//http download response function prototype
typedef std::function<void(EMDM_GprsHttpDownload, const u8*, u32 )> MDM_CALLBACK_DownlaodFileResponse;
typedef std::function<void(EMDM_GprsHttpDownload, const u8*, u16, u32 )> MDM_CALLBACK_DownlaodFileFrameResponse;
typedef void (*MDM_CALLBACK_DownlaodTrafficResponse)(EMDM_GprsTrafficDownload Status , char * data , u16 size);



typedef struct{
	bool 						HttpConnection;
	u32 						RecivedDataSize;
	EMDM_GprsHttpDownloadError 	Error;
	u32							FrameRetryCount;
	u32							DownloadRetryCount;
	u32 						StartFlashOffset;
	char 						BinariesServer[BINARIESSERVER_MAX_SIZE];												//String	//URL that Platinum download the OTA binaries
	char 						Filename[OTA_FILENAME_SIZE];
	bool 						HttpHeaderDetected;
	EMDM_HttpStatusCode 		sc;
	s8 							LastModified[FILE_LAST_MODIFIED_DATE_SIZE];
	u32 						ContentLength;
	EMDM_HttpRequestType		HttpRequestType;
	MDM_CALLBACK_DownlaodFileResponse finishCallback;
	MDM_CALLBACK_DownlaodFileFrameResponse frameCallback;
	u32 						maxSize;
	u8                          Sum;
	u8                          Xor;
}SMDM_OnTheFlyOTA;

typedef struct{
	bool 						TrafficConnection;
	u32 						RequestTimeout;
	u32 						Timeout;
	char 						BinariesServer[BINARIESSERVER_MAX_SIZE];												//String	//URL that Platinum download the OTA binaries
	char						Request[TRAFFIC_REQUEST_MAX_SIZE];
	u16							RequestSize;
	MDM_CALLBACK_DownlaodTrafficResponse callback;
	EMDM_GprsTrafficDownloadError error;
}SMDM_OnTheFlyTraffic;


typedef struct{
	u16 SendSmsErrorCounter;
	u16 SendGprsErrorCounter;
	u16 DialErrorCounter;
}SMDM_MULTIPLE_ERROR_COUNTERS;


typedef struct {
	SMDM_HandlersState HandlersState;
	SMDM_Info 				Info;
	SMDM_NetworkStatus 		NetStat;
	SMDM_Sms 				sms;
    EMDM_AttachDetach       GprsAttachStatus;
	SMDM_GprsServiceInfo 	Gprs[MAX_TCP_DESTINATIONS + MAX_HTTP_DESTINATIONS + MAX_TCP_GENERAL_PURPOSE];
    char                    GprsDestinationIP[IP_ADDRESS_MAX_SIZE];
    u8                      GprsSocket;
    u16                     ConnectionActivationResult;
    u32                     ConnectionActivationTimeout;
    bool                    ConnectionStatus;
    bool 					GprsReadError;
	bool					NeedRestart;
	u16 					GprsReadSocketIndex;
	u32						Timer;
	u32						KeepAliveTimer;
	u16						KeepAliveRetry;
	SMDM_OnTheFlyOTA		Ota;
	SMDM_OnTheFlyTraffic	Traffic;
	SACM_CallInfo			Call[MAX_CALLES_HANDLED];
	bool					CallInfoChanged;
	bool	        		AutoAnswer;
	bool 					AcmPendingEmgMsg;
	bool 					AcmPendingDial;
	bool					AcmPendingAbort;//ambush on not sent message
	SMDM_SilentInfo			SilentInfo;
	u16						CmeLastError;
	u16						CmsLastError;
	SMDM_CMD_SEQUENCE		CmdSeq;
	SMDM_MULTIPLE_ERROR_COUNTERS	ErrCount;
	bool					SimUrcEnabled;
	bool					AbortCurrentMsg;
    bool                    AbortingCurrentCall;
    u32                     TimeoutAbortingCurrentCall;
    bool                    MuteIncomingAudioPath;
    u16			            ProgramedLanguage;
    EUBL_RingTone           Ringtone;
	bool 					SysStartDetected;
    EUBL_CallWaitingStatus  CallWaitingStatus;
    char                    OwnIP[IP_ADDRESS_MAX_SIZE];
    char                    WifiIP[IP_ADDRESS_MAX_SIZE];
    bool                    AcmPendingAbortCall;
    SMDM_SeqNum             SeqNum;
	EMDM_JamDetectStatus		JammingStatus;
}SMDM;

typedef enum {
	EMDMAR_None = 0,
	EMDMAR_SendData,
	EMDMAR_SendDataHighPriority,
	EMDMAR_AbortData,
	EMDMAR_CallDial,
	EMDMAR_CallActive,
	EMDMAR_CallRejectIncoming,
	EMDMAR_CallConference,
	EMDMAR_CallPutOnHold,
	EMDMAR_CallReleaseHold,
	EMDMAR_CallHangUp,
	EMDMAR_CallAbortAll,
	EMDMAR_StartOff,
	EMDMAR_StartOn,
	EMDMAR_StartSleep,
	EMDMAR_StartWakeUp,
}EMDM_ApplicationRequest;


typedef struct {
	u16 retry;
	u32 timeout;
	bool Active;
}SMDM_Scheme;

typedef struct {
	SMDM_Scheme Gprs;
	SMDM_Scheme Sms;
	SMDM_Scheme Call;
	SMDM_Scheme Dtmf;
	bool InitRequest;
	bool SchemeEndedOk;
	EMSG_OTATransmission sm;
}SMDM_OutMsgScheme;


typedef struct {
	u16 length;
	UMSG_SendingScheme Scheme;
	char* PhoneDest;
	u8* data;
	char* DtmfStr;
}SMdm_Outgoing;



typedef struct {
	EMDM_ApplicationRequest Req;
	SMdm_Outgoing Out;
}SMDM_MessageManagerRequest;

typedef struct {
	EMDM_ApplicationRequest Req;
	u16 CallIndex;
	u8 Number[PHONE_MAX_SIZE];
}SMDM_AudioManagerRequest;


typedef struct {
	EMDM_ApplicationRequest Req;
}SMDM_PowerManagerRequest;


typedef struct {
	SMDM_MessageManagerRequest	CurrentMsgMgrReq;
	SMDM_MessageManagerRequest	NextMsgMgrReq;
	SMDM_PowerManagerRequest    CurrentPwrMgrReq;
	SMDM_PowerManagerRequest    NextPwrMgrReq;
}SMDM_Requests;


 typedef enum {
    EMDMAMS_Idle = 0,
    EMDMAMS_WaitSampleTime,
    EMDMAMS_SendCmdToReadAntennaStatus,
    EMDMAMS_WaitResponseOfCmdToReadAntennaStatus,
    EMDMAMS_ErrorWhileSendingCmd,
}EMDM_AntennaMonitorState;

 typedef enum {
    EMDMJMS_Idle = 0,
    EMDMJMS_SendCmdToConfigureJammingMon,
    EMDMJMS_WaitResponseOfConfigureJammingMon,
}EMDM_JammingEnaMonitorState;

typedef enum {
    EMDCS_Idle = 0,
    EMDCS_WaitSampleTime,
    EMDCS_SendCmdToReadCellStatus,
    EMDCS_WaitResponseOfCmdToReadCellStatus,
    EMDCS_ErrorWhileSendingCmd,
}EMDCS_FrecuencyCellStatus;


typedef enum{
	EMDMSCRI_QueryForSocketType = 0,
	EMDMSCRI_QueryForLastSocketError = 1,
	EMDMSCRI_GetTheTotalAmountOfBytesSentFromSocket = 2,
	EMDMSCRI_GetTheTotalAmountOfBytesReceivedFromSocket = 3,
	EMDMSCRI_QueryForRemotePeerIpAddressAndPort = 4,
	EMDMSCRI_QueryTcpScketStatus = 10,
	EMDMSCRI_QueryTcpOutgoingUnacknowledgedData = 11,
}EMDM_SocketControlRequestIdentifier;

typedef enum {
    EMDMRTS_Idle,
    EMDMRTS_StarToneSelection,
    EMDMRTS_SelectDefaultRingTone,
    EMDMRTS_WaitSelectDefaultRingTone,
    EMDMRTS_EnablePersonalized,
    EMDMRTS_WaitEnablePersonalized,
    EMDMRTS_Error,
}EMDM_RingToneSelectionState;




extern SMDM Smdm;
extern void UbloxHandler_PeriodicTask( void );
void UbloxHandler_SaveHttpFrameToFlash( EMDM_GprsHttpDownload state, const u8* buffer , u16 size, u32 ContentLength );
void UbloxHandler_MdmNoDialToneDetected( void );
void MdmSmsErrorDetected( u16 ErrorCode );
EATC_Result Mdm_GprsConnertionConfigError(u16 id, u8* data, u16 length);
bool IsDirectSmsNumber( const char *number );
void UbloxHandler_UpdateSocketReceivedDataSize(u16 evtid, char* data);
EATC_Result UbloxHandler_UpdateSocketReceivedData(u16 cmdid, char* data , u16 size);
void UbloxHandler_UpdateInternetServiceInformation(u16 cmdid, char *data);
void UbloxHandler_UpdateLastSocketError(u16 cmdid, char* data);
void UbloxHandler_SetDnsResolution(u16 cmdid, char *data);
void UbloxHandler_SetTcpSocket(u16 cmdid, char* data);
u8 UbloxHandler_GetTcpSocket(void);
void UbloxHandler_UpdateNetworkStatus(char* data , SMDM_MessageTypeIs mType , SMDM_NetworkTypeIs nType);
void UbloxHandler_UpdateGprsAttachStatus(u16 cmdid, char* data);
void UbloxHandler_UpdateConnectionStatus(u16 cmdid, char* data);
void UbloxHandler_UpdateInternalPdpIpAddress(u16 cmdid, char* data , u16 lenght);
void UbloxHandler_UpdateExternalPdpIpAddresses(u16 cmdid, char* data , u16 lenght);
void UbloxHandler_UpdateReceivedSignalStrengthIndication(u16 cmdid, char* data);
EATC_Result UbloxHandler_SetMdmFwVersion(u16 cmdid, char* data , u16 length);
char* UbloxHandler_GetMdmFwVersion( void );
EATC_Result UbloxHandler_SetMdmModel(u16 cmdid, char* data , u16 length);
EATC_Result UbloxHandler_SetMdmImei(u16 cmdid,char* data , u16 length);
char* UbloxHandler_GetMdmImei( void );
EATC_Result UbloxHandler_SetMdmImsi(u16 cmdid,char* data , u16 length);
char* UbloxHandler_GetMdmImsi( void );
EATC_Result UbloxHandler_SetSimIccid(u16 cmdid,char* data , u16 length);
char* UbloxHandler_GetSimIccid(void );

EUBL_RingTone UbloxHandler_GetRingtone(void);
void UbloxHandler_SetPendingSamuCallOnFailure (bool val);
void UbloxHandler_SetRingtone(EUBL_RingTone Ringtone);
bool UbloxHandler_RingToneSelection(void);

char* MdmGetOwnPhoneNum( void );
void UbloxHandler_UpdatePendingSms(u16 cmdid, char* data);
EATC_Result UbloxHandler_UpdateConfigureSmsStorage(u16 cmdid, char* data , u16 length);
void UbloxHandler_SendGprsData( void );
void UbloxHandler_UpdateServerAck(u16 cmdid , char* data , u16 size, SMDM_MessageTypeIs mType);
void UbloxHandler_UpdateCallState(u16 evtid, char* data, u16 lengt);
EATC_Result UbloxHandler_UpdateCallStateExtended(u16 cmdid, char* data , u16 lenght);
void UbloxHandler_UpdateCellInfo(u16 evtid, char* data, u16 lengt);
void UbloxHandler_UpdateSimState(u16 evtid, char* data, u16 lengt);
void UbloxHandler_TimeZoneManaging(u16 evtid, char* data, u16 lengt);
void UbloxHandler_MdmEquipmentEventReportingParse(u16 evtid, char* data, u16 lengt);
void UbloxHandler_MdmOnSocketCloseEventParse(u16 evtid, char* data, u16 lengt);
void UbloxHandler_MdmOnSocketConnectionEventParse(u16 evtid, char* data, u16 lengt);
void UbloxHandler_MdmOnTemperatureStatusEventParse(u16 evtid, char* data, u16 lengt);
void UbloxHandler_MdmOnSmsIndicationEventParse(u16 evtid, char* data, u16 lenght);
void UbloxHandler_MdmOnPdpContextActivationEventParse(u16 evtid, char* data, u16 lenght);
void UbloxHandler_MdmOnJammingEventParse(u16 evtid, char* data , u16 lenght);

EMDM_JamDetectStatus UbloxMDM_GetJammingStatus(void);
EResult_MsgStatus_Modem UbloxHandler_GetModemSendingStatus( bool justRead );
void UbloxHandler_MdmSimStates(u16 cmdid , char* data, u16 lengt);
void UbloxHandler_MdmAntennaStatus(u16 cmdid , char* data, u16 lengt);
void MdmEquipmentEventReportingParse(u16 evtid, char* data, u16 lengt);
void MdmPowerOffEventReceived( void );
EMDM_State UbloxHandler_GetMdmState( void );
const char* GetMdmStatus( void );
EATC_Result UbloxHandler_ReceivedSimUrcConfiguration(u16 cmdid, char* data , u16 lenght);
EATC_Result UbloxHandler_MdmOwnPhoneNumber(u16 cmd, char* data, u16 lengt);
void UbloxHandler_MdmRingEventDetected( void );
// Interrupt message currently being sent in order to send a higher priority message.
// Current message will either have been sent, or Modem_LastMsgSend_Error will be returned from GetModemSendingStatus()
void Mdm_InterruptCurrentMsg( void );
EMDM_Result Mdm_SendDataTaskRequest(SMSG_Outgoing *Ptr);
EMDM_Result Mdm_PwrMgrTaskRequest(EMDM_ApplicationRequest req);
EMDM_ServiceState Mdm_GetSocketState( EMDM_DestSocketDef SocketType );
const char* Mdm_GetSocketStatus( EMDM_DestSocketDef SocketType );
const char* UbloxHandler_GetHttpDownloadStatus( void );
void UbloxHandler_CancelHttpDownload( void );
void UbloxHandler_OnEventReceivedWhileSleep( void );
//Network Status (if not registr func return true)
bool IsNoGprsReg( void );
bool IsNoGsmReg( void );
bool IsGsmReg( void );
void DisableSilentMonitoring( void );
EATC_Result UpdateCmgrParams(s8* buff);
bool CallAbortAny( void );
char* Mdm_GetPhoneNumber(EMSGS_MessageTypeWithPhone KindOfMsg);
void HttpNormalDownload( void );
//ACM API
void Mdm_AcmSetPendingEmgMsg( bool state );
void Mdm_Mdm_AcmSetPendingDial( bool state );
void Mdm_AcmSetPendingAbort( bool state);
void Mdm_AcmPendingAbortCall( bool state);
EMDM_Result Mdm_UnMuteIncomingAudioPath(FATC_ResponseCallback callback);
EMDM_Result Mdm_MuteIncomingAudioPath(FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestDial( const char* num, FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestHangUp( u8 CallIndx , FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestReject( u8 CallIndx , FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestAnswer( u8 CallIndx , FATC_ResponseCallback callback );
EMDM_Result Mdm_AcmRequestConference( FATC_ResponseCallback callback );
EMDM_Result Mdm_AcmRequestSwitchCalls( u8 HoldCallIndx , FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestHoldCall( FATC_ResponseCallback callback );
EMDM_Result Mdm_AcmRequestUnHoldCall( u8 CallIndx , FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestReDialLastNumber( FATC_ResponseCallback callback );
EMDM_Result Mdm_AcmRequestDtmf( u8 *Dtmf , FATC_ResponseCallback callback);
EMDM_Result Mdm_AcmRequestMuteInternalRingtone( void );
EMDM_Result Mdm_AcmRequestUnmuteInternalRingtone( void );
void SetAssistanceCallback(FATC_ResponseCallback callback);
void SetEmergencyCallback(FATC_ResponseCallback callback);
bool IsAnyActiveCall( void );
bool CheckForSingleCallState( EACM_StateOfTheCall State );
void SetAutoAnswerState( bool state );
u32 GetServiceCentreTimeStampToRtcDifference( void );
void UbloxHandler_SetMdmState( EMDM_State state );
bool Mdm_IsBusyGsmCall( void );
bool Mdm_NeedToWake( void );
void Mdm_SimUrcsConfigured(u16 UrcsConfig);


typedef void (*ZiltokCmd)( void );
ZiltokCmd CommandCallCmd (EMDM_CallerIDCommands cmd);
EMDM_CallerIDCommands IsZiltokCall( const char *num );

void Mdm_PrintState( char* pArgs );

//OTA Api
EMDM_GprsHttpDownload UbloxHandler_GetHttpDownloadState( void );
void UbloxHandler_SetHttpDownloadState( EMDM_GprsHttpDownload state );
EMDM_GprsHttpDownloadError UbloxHandler_GetHttpDownloadError( void );
void HttpDnldGetProgress( u32* pBytesDownloaded );
void UbloxHandler_ModemEvent(EUBL_Event Event);

//user sms api
EMDM_SmsDcsFormat GetReceivedSmsDcs( void );
EMDM_PhoneFormat GetReceivedSmsPhoneFormat( void );
bool IsModemInPoweredDownProc( void );




/*******************************************************************************
NAME:           P8MDM_SetModemServiceState
PARAMETERS:	    bEMDM_NetworkStatus state
RETURN VALUES:  void
DESCRIPTION:   	Prima periodiclly will set the in network registration state
*******************************************************************************/

void P8MDM_SetModemServiceState( EMDM_NetworkStatus state );


/*******************************************************************************
NAME:           Set_F_MdmGprsReadError
PARAMETERS:	    bool bValue
RETURN VALUES:  void
DESCRIPTION:   	Set/Reset the Smdm.GprsReadError flag used to know if occurred an
                error while reading gprs frames during the FW download in an OTA
                process
*******************************************************************************/
void Set_F_MdmGprsReadError( bool bValue );

/*******************************************************************************
NAME:           Get_F_MdmGprsReadError
PARAMETERS:	    void
RETURN VALUES:  bool
DESCRIPTION:   	returns the current value of Smdm.GprsReadError flag used to
                know if occurred an error while reading gprs frames during the
                FW download in an OTA process
*******************************************************************************/
bool Get_F_MdmGprsReadError( void );

/*******************************************************************************
NAME:           MDM_API_DownloadFileFromURL
PARAMETERS:	    urlPath - ual or ip of the server XXX:XXX:XXX:XXX:PORT or www.blanla.com:PORT format
				filename - file name and path in server
				toAdressOffset - flash start address where to save file
				maxSize - max size to download
				finshCallBack - when we are done
				frameCallBack - when frame is ready ( defualt to  toAdressOffset)

RETURN VALUES:  bool
DESCRIPTION:   	start the http proccess to downlaod a file
*******************************************************************************/
bool MDM_API_DowmlodFileFromURL(char*  urlPath,
								char* filename,
								u32 toAdressOffset ,
								int maxSize,
								MDM_CALLBACK_DownlaodFileResponse finshCallBack ,
								MDM_CALLBACK_DownlaodFileFrameResponse frameCallBack = UbloxHandler_SaveHttpFrameToFlash);

/*******************************************************************************
NAME:           MDM_API_DownloadFileFromURL
PARAMETERS:	    urlPath - ual or ip of the server XXX:XXX:XXX:XXX:PORT or www.blanla.com:PORT format
				filename - file name and path in server
				finshCallBack - when we are done

RETURN VALUES:  bool
DESCRIPTION:   	start the http proccess to downlaod a file
*******************************************************************************/
bool MDM_GetModificationDateOfFileInURL (char*  urlPath, char* filename ,MDM_CALLBACK_DownlaodFileResponse finishCallBack);

/*******************************************************************************
NAME:           UbloxHandler_SetTrafficDownloadState
PARAMETERS:	    EMDM_GprsTrafficDownload state
RETURN VALUES:  void
DESCRIPTION:   	close traffic socket and clear resources
*******************************************************************************/
void UbloxHandler_SetTrafficDownloadState( EMDM_GprsTrafficDownload state );

/*******************************************************************************
NAME:           MDM_API_DowmlodTrafficInformation
PARAMETERS:	    urlPath - ual or ip of the server XXX:XXX:XXX:XXX:PORT or www.blanla.com:PORT format
				reqData - request data to send
				reqDataSize - reqest data size
				finshCallBack - callback function once procces is done type MDM_CALLBACK_DownlaodTrafficResponse
				timeout - time to wait for modem reply
RETURN VALUES:  bool
DESCRIPTION:   	start the http proccess to downlaod a file
*******************************************************************************/

bool MDM_API_DowmlodTrafficInformation(char*  urlPath, char* reqData , u32 reqDataSize  , MDM_CALLBACK_DownlaodTrafficResponse finshCallBack , u32 timeout);

/*******************************************************************************
NAME:           UbloxHandler_CancelTrafficDownload
PARAMETERS:	    void
RETURN VALUES:  void
DESCRIPTION:   	to cancel download  in case of time out or user request
*******************************************************************************/
void UbloxHandler_CancelTrafficDownload( void );

/*******************************************************************************
NAME:           MDM_API_sendListDownloadGetRequest
PARAMETERS:	    urlpath - list file path
				filename - list file name
				massege - extra flags
RETURN VALUES:  void
DESCRIPTION:   	send get request
*******************************************************************************/
void MDM_API_sendListDownloadGetRequest(char* urlpath, char* filename, char* massege);

/*******************************************************************************
NAME:           Ublox_GetGprsDestinationNamePtr
PARAMETERS:	    void
RETURN VALUES:  char*
DESCRIPTION:   	Extract ip number from Ip:port parameter
*******************************************************************************/
char* Ublox_GetGprsDestinationNamePtr(char * parm);

/*******************************************************************************
NAME:           Ublox_GetGprsDestinationPortPtr
PARAMETERS:	    void
RETURN VALUES:  void
DESCRIPTION:   	Extract port number from Ip:port parameter
*******************************************************************************/
char* Ublox_GetGprsDestinationPortPtr(char * parm);

/*******************************************************************************
NAME:           Ublox_SetSysStartIndication
PARAMETERS:	    boll true in case sysstart was detected
RETURN VALUES:  void
DESCRIPTION:   	when UBL driver detect SYSSTART Urec it will set this flag
*******************************************************************************/
void Ublox_SetSysStartIndication( bool status );

/*******************************************************************************
NAME:           UbloxHandler_CellStatus
PARAMETERS:	    The data of the Cell Status did by the AT command +CGED
RETURN VALUES:  ATC_Ok if all the data related to the cell will be printed ok
DESCRIPTION:   	The data of the Cell status should be printed
*******************************************************************************/
EATC_Result UbloxHandler_CellStatus(u16 cmdid , char* data, u16 length);

/*******************************************************************************
NAME:           SetMonitorFrecuencyTime
PARAMETERS:	    Time of the frecuency to send the information of the CELL
RETURN VALUES:  NA
DESCRIPTION:   	Set the time of the Frecuency Monitor
*******************************************************************************/
void SetMonitorFrecuencyTime ( u8 time);

/*******************************************************************************
NAME:           UbloxHandler_StartIPPolling
PARAMETERS:	    void
RETURN VALUES:  NA
DESCRIPTION:   	Tell the modem to start or stop polling for the assigned IP address
*******************************************************************************/
void UbloxHandler_StartIPPolling( bool value );

/*******************************************************************************
NAME:           UbloxHandler_ResetModem
PARAMETERS:	    void
RETURN VALUES:  void
DESCRIPTION:   	Reset the modem and modem handler state
*******************************************************************************/
void UbloxHandler_ResetModem( void ) ;

/*******************************************************************************
NAME:           UbloxHandler_IsModemFirmwareIdle
PARAMETERS:	    void
RETURN VALUES:  bool
DESCRIPTION:   	return if the modem update firmare handler is Idle and that the modem is not in recovery mode
*******************************************************************************/
bool UbloxHandler_IsModemFirmwareUpdateIdle( void ) ;


/*******************************************************************************
<<<<<<< HEAD
NAME:           UbloxHandler_GetOwnIPAddress
PARAMETERS:	    void
RETURN VALUES:  char*
DESCRIPTION:   	returns a pointer to the string which contains the IP assigned to
                the PNG
*******************************************************************************/
char* UbloxHandler_GetOwnIPAddress( void ) ;


/*******************************************************************************
NAME:           UbloxHandler_GetWifiIPAddress
PARAMETERS:	    void
RETURN VALUES:  char*
DESCRIPTION:   	returns a pointer to the string which contains the IP assigned to
                the Wifi
*******************************************************************************/
char* UbloxHandler_GetWifiIPAddress( void);


/*******************************************************************************
NAME:           UbloxHandler_isRegistered
PARAMETERS:	    void
RETURN VALUES:  bool
DESCRIPTION:   	return if the modem registered to network
*******************************************************************************/
bool UbloxHandler_isRegistered(void);


#ifdef __cplusplus
}
#endif

#endif
