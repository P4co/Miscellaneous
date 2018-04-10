#include "trackingm.h"
#include "SysStatus.h"
#include "Fields.h"
#include "WakeupService.h"
#include "log.h"
#include "start_end_trip_alert.h"
#include "Handling_Parameters.h"
#include "GetParameterValue.h"
#include "Outgoing.h"
#include <string.h>
#include <assert.h>
#include <cmath>

static const u32 KM_TO_METER = 1000;
static const u32 MINIMUM_DELTA_HEADING = 4;
static const u16 HEADING_TRACK_DISABLE = 0;

enum class ETrackUnitsType
{
	SEC = 0,
	MIN,
	HOUR
};

enum class HeadingTrackingState
{
	IDLE = 0,
	CHECK,
	ACTIVE
};

class TrackingService
{
public:
	struct Config {

		Config(	EV2O_MsgCodes msgCode,
				EGlobal_ParametersId enableParamID,
				EGlobal_ParametersId maxTrackingTimeParamID,
				EGlobal_ParametersId distanceWhileMovingParamID,
				EGlobal_ParametersId movingDeltaTimeParamID,
				EGlobal_ParametersId notDrivingDeltaTimeParamID,
				EGlobal_ParametersId idleDeltaTimeParamID,
				EGlobal_ParametersId headingDelayParamID,
				EGlobal_ParametersId headingThresholdParamId ) :
			e_msg_code{ msgCode },
			EnableParamID{ enableParamID },
			MaxTrackingTimeParamID{ maxTrackingTimeParamID },
			DistanceWhileMovingParamID{ distanceWhileMovingParamID },
			MovingDeltaTimeParamID{ movingDeltaTimeParamID },
			NotDrivingDeltaTimeParamID{ notDrivingDeltaTimeParamID },
			IdleDeltaTimeParamID{ idleDeltaTimeParamID },
			DeleyParamID{ headingDelayParamID },
			DeltaThresholdParamID{ headingThresholdParamId }
		{}

		const EV2O_MsgCodes e_msg_code;
		const EGlobal_ParametersId EnableParamID;
		const EGlobal_ParametersId MaxTrackingTimeParamID;
		const EGlobal_ParametersId DistanceWhileMovingParamID;
		const EGlobal_ParametersId MovingDeltaTimeParamID;
		const EGlobal_ParametersId IdleDeltaTimeParamID;
		const EGlobal_ParametersId NotDrivingDeltaTimeParamID;;

		/*Pointer to the Parameter Tracking_HeadingChangeDelayXX */
		const EGlobal_ParametersId DeleyParamID;

		/*Pointer to the Parameter Tracking_HeadingChangeThresholdXX*/
		const EGlobal_ParametersId DeltaThresholdParamID;
	};

	TrackingService( const Config& config ) :
		configParams{ config }
	{}

    bool isParamEnabled() {
        s32 boolVal;
        GetParameterValueSimple( configParams.EnableParamID, &boolVal, nullptr, nullptr, nullptr );
        return boolVal != 0;
    }

	u16 getMaxTrackingTime() {
      	s32 maxTime;
        GetParameterValueSimple( configParams.MaxTrackingTimeParamID, &maxTime, nullptr, nullptr, nullptr );
        return maxTime;
	}

	u16 getDistanceWhileMoving() {
      	s32 distance;
        GetParameterValueSimple( configParams.DistanceWhileMovingParamID, &distance, nullptr, nullptr, nullptr );
        return distance;
	}

	u16 getMovingDeltaTime() {
      	s32 movingTime;
        GetParameterValueSimple( configParams.MovingDeltaTimeParamID, &movingTime, nullptr, nullptr, nullptr );
        return movingTime;
	}

	u16 getIdleDeltaTime() {
      	s32 idleTime;
        GetParameterValueSimple( configParams.IdleDeltaTimeParamID, &idleTime, nullptr, nullptr, nullptr );
        return idleTime;
	}

	u32 getNonDrivingDeltaTime() {
      	s32 nonDrivingTime;
        GetParameterValueSimple( configParams.NotDrivingDeltaTimeParamID, &nonDrivingTime, nullptr, nullptr, nullptr );
        return nonDrivingTime;
	}

	u16 getHeadingDelay() {
      	s32 delayTime;
        GetParameterValueSimple( configParams.DeleyParamID, &delayTime, nullptr, nullptr, nullptr );
        return delayTime;
	}

	u16 getHeadingDeltaThreshold() {
      	s32 threshold;
        GetParameterValueSimple( configParams.DeltaThresholdParamID, &threshold, nullptr, nullptr, nullptr );
        return threshold;
	}

	URTC_DateTime srtc_max_time_counter;		//u32 u_max_time_counter;
	URTC_DateTime srtc_moving_time_counter;		//u32 u_drive_time_counter;
	URTC_DateTime srtc_idle_time_counter;       //u16 u_idle_delta_time;
	URTC_DateTime srtc_not_drive_time_counter;	//u32 u_not_drive_time_counter;
	URTC_DateTime TimeToSendNotDriveTimeCounter;
	u32 u_distance_counter;
	bool bEnabled;
	u16 u_max_time;
	u16 u_moving_delta_time;
    u16 u_idle_delta_time;
	u32 u_not_drive_delta_time;
	u16 u_delta_distance;
	WakeupServiceIndex wsi_wakeup_index_max_time = NO_WAKE_UP_INDEX;
	WakeupServiceIndex wsi_wakeup_index_moving_time_counter = NO_WAKE_UP_INDEX;
	WakeupServiceIndex wsi_wakeup_index_idle_time_counter = NO_WAKE_UP_INDEX;
	WakeupServiceIndex wsi_wakeup_index_notdrive_time_counter = NO_WAKE_UP_INDEX;
	WakeupServiceIndex wsi_wakeup_index_Prenotdrive_time_counter = NO_WAKE_UP_INDEX;
    bool first_init = true;
    bool PrevIgnitionOn = false;
	const Config configParams;

	/*The struct for the Heading Tracking message*/
	struct HeadingTracking
	{
		/*Current Heading Value take from GPS*/
		u16 	CurrentHeading;
		/*Previous Heading Value saved if the GPS was ok */
		u16 	PrevHeading;
		/*Delta between Current and Previous Heading*/
		s16     Delta;
		/*Each Delta wil be added in order to be compare with a threshold */
		s16 	SumDelta;
		/*Interval to compare accumulated heading changes with threshold*/
		u8		Delay;
		/*Value to compare the changes of the heading*/
		u16 	DeltaThresh;

		u32		Timer;
		/*Enable/Disable the HedingTracking if the Threshold is 0 means disable*/
		bool	Exist;
		/*State of Heading*/
		HeadingTrackingState 		State;

		void PrintStatus()
		{
			Log(LOG_SUB_TRAK,LOG_LVL_TRACE, "HC %u - Heading %u-%u=%d , Sum %d\n" ,
													State,
													CurrentHeading,
													PrevHeading,
													Delta,
													SumDelta);
		}

		void RestartCalculateHeading( )
		{
			State = HeadingTrackingState::CHECK;
			SumDelta = 0;
			PrevHeading = CurrentHeading;
		}


		void SwitchToHeadingActiveState( )
		{
			SumDelta 	= 0;
			Timer 		= TmrGetTime_ms();
			State 		= HeadingTrackingState::ACTIVE;
		}
	};
	HeadingTracking HeadTrk;

};

static TrackingService s_service1_data{ TrackingService::Config{
		EV2OMC_Tracking_Message_1,
		Tracking_Enable1,
		Tracking_MaximumTrackingTimeT1,
		Tracking_DistanceWhileMoving1,
		Tracking_TimerWhileMoving1,
		Tracking_TimerWhileNotDriving1,
		Tracking_TrackDelayStanding,
		Tracking_HeadingChangeDelay1,
		Tracking_HeadingChangeThreshold1
	}
};

static TrackingService s_service2_data{ TrackingService::Config{
		EV2OMC_Tracking_Message_2,
		Tracking_Enable2,
		Tracking_MaximumTrackingTimeT2,
		Tracking_DistanceWhileMoving2,
		Tracking_TimerWhileMoving2,
		Tracking_TimerWhileNotDriving2,
		Tracking_TrackDelayStanding,
		Tracking_HeadingChangeDelay2,
		Tracking_HeadingChangeThreshold2
	}
};

static void TrackingCleanupWakeupRequests( TrackingService& pData );
static void TrackingCB( void* Param );
static void InitData_TrackingService1( void );
static void InitData_TrackingService2( void );
static void TrackingMessageHelper(EV2O_MsgCodes Msg_Code);

/*HEAD TRACKING HANDLERS*/
static void Start_HeadingTracking( TrackingService& pTrackingData );
static s16 GetHeadingDelta(u16 prev , u16 current);
static void HeadingTracking_Handler( TrackingService& pTrackingData );
void TrackingServiceHandler( TrackingService& s_data );
static void ImmediateTracking_Handler( TrackingService& pTrackingData );

/******/

void TrackingInit( void )
{
}

void Add_to_RTC_data(URTC_DateTime *s_rtc_dt, u32 u_units_amount, ETrackUnitsType e_unit_type)
{
	switch ( e_unit_type )
	{
		case ETrackUnitsType::SEC:	// add seconds
			RTC_AddSeconds(s_rtc_dt, u_units_amount);
			break;
		case ETrackUnitsType::MIN:	// add minutes
			RTC_AddMinutes(s_rtc_dt, u_units_amount);
			break;
		case ETrackUnitsType::HOUR:	// add hours
			RTC_AddHours(s_rtc_dt, u_units_amount);
			break;
	}
}

static void TrackingSetTimer( 	u32 TimeCount,
								URTC_DateTime* pRtcTime,
								WakeupServiceIndex* pWakeupIdx,
								ETrackUnitsType Units,
								void* pCBData )
{
	WakeupServiceCheckAndClearRequest( pWakeupIdx );

	if ( TimeCount != 0 )
	{
		RTC_GetDateTime( pRtcTime );
		Add_to_RTC_data( pRtcTime, TimeCount, Units);

		*pWakeupIdx = WakeupServiceAddOneTimeRequest( pRtcTime, TrackingCB, pCBData );
	}
	else
	{
		RTC_SetTimeInvalid( pRtcTime );
	}
}

void InitTime_MaxTrackingTime(TrackingService& pTrackingData)
{
	TrackingSetTimer(	pTrackingData.u_max_time,
						&pTrackingData.srtc_max_time_counter,
						&pTrackingData.wsi_wakeup_index_max_time,
						ETrackUnitsType::MIN,
						&pTrackingData );
}

void InitTime_MovingDeltaTime(TrackingService& pTrackingData)
{
	TrackingSetTimer(	pTrackingData.u_moving_delta_time,
						&pTrackingData.srtc_moving_time_counter,
						&pTrackingData.wsi_wakeup_index_moving_time_counter,
						ETrackUnitsType::SEC,
						&pTrackingData );
}

void InitTime_IdleDeltaTime(TrackingService& pTrackingData)
{
	TrackingSetTimer(	pTrackingData.u_idle_delta_time,
						&pTrackingData.srtc_idle_time_counter,
						&pTrackingData.wsi_wakeup_index_idle_time_counter,
						ETrackUnitsType::SEC,
						&pTrackingData );
}

u32 ModifyTimeNotDriveDeltaTime(TrackingService& pTrackingData)
{
    if(pTrackingData.u_not_drive_delta_time > 60)
    {
    	// Time changed from 20 to 40 in order to assure getting a new GPS fix and not send an old one
    	return pTrackingData.u_not_drive_delta_time - 40;
	}
	return  pTrackingData.u_not_drive_delta_time;
}

void InitTime_NotDriveDeltaTime(TrackingService& pTrackingData)
{
 	TrackingSetTimer(	pTrackingData.u_not_drive_delta_time,
						&pTrackingData.TimeToSendNotDriveTimeCounter,
						&pTrackingData.wsi_wakeup_index_notdrive_time_counter,
						ETrackUnitsType::SEC,
						&pTrackingData );

	TrackingSetTimer(	ModifyTimeNotDriveDeltaTime(pTrackingData),
						&pTrackingData.srtc_not_drive_time_counter,
						&pTrackingData.wsi_wakeup_index_Prenotdrive_time_counter,
						ETrackUnitsType::SEC,
						&pTrackingData );
}

bool CheckGlobalParameters_IsChanged( TrackingService& pTrackingData )
{
	bool bConfigurationChanged = false;
	bool bMaxTimeChanged = false;

	if ( pTrackingData.bEnabled != pTrackingData.isParamEnabled() )
	{
		// Tracking enable flag has changed.
		pTrackingData.bEnabled = pTrackingData.isParamEnabled();
		bMaxTimeChanged = bConfigurationChanged = true;
	}
	if ( 	( pTrackingData.u_max_time != pTrackingData.getMaxTrackingTime() ) ||
			bConfigurationChanged )
	{
		// The max tracking time has changed or the tracking enable flag.
		bConfigurationChanged = true;
		pTrackingData.u_max_time = pTrackingData.getMaxTrackingTime();
		InitTime_MaxTrackingTime(pTrackingData);
	}
	if (	( pTrackingData.u_moving_delta_time != pTrackingData.getMovingDeltaTime()) ||
			( bMaxTimeChanged ) )
	{
		pTrackingData.u_moving_delta_time = pTrackingData.getMovingDeltaTime();
		InitTime_MovingDeltaTime(pTrackingData);
		bConfigurationChanged = true;
	}
	if ( pTrackingData.u_idle_delta_time != pTrackingData.getIdleDeltaTime() ||
         bConfigurationChanged ) {
         pTrackingData.u_idle_delta_time = pTrackingData.getIdleDeltaTime();
         InitTime_IdleDeltaTime( pTrackingData );
         bConfigurationChanged = true;
    }
	if ( 	( pTrackingData.u_not_drive_delta_time != pTrackingData.getNonDrivingDeltaTime() ) ||
			bMaxTimeChanged )
	{
		// Set timer and Wakeup Service to time when vehicle not in drive.
		pTrackingData.u_not_drive_delta_time = pTrackingData.getNonDrivingDeltaTime();
		InitTime_NotDriveDeltaTime(pTrackingData);
		bConfigurationChanged = true;
	}
	if ( pTrackingData.u_delta_distance != pTrackingData.getDistanceWhileMoving() )
	{
		pTrackingData.u_delta_distance = pTrackingData.getDistanceWhileMoving();
		bConfigurationChanged = true;
	}

	if ( pTrackingData.HeadTrk.Delay != pTrackingData.getHeadingDelay() ||
		 pTrackingData.HeadTrk.DeltaThresh != pTrackingData.getHeadingDeltaThreshold() )
	{
		pTrackingData.HeadTrk.Delay = pTrackingData.getHeadingDelay();
		pTrackingData.HeadTrk.DeltaThresh = pTrackingData.getHeadingDeltaThreshold();
		Start_HeadingTracking( pTrackingData );
		bConfigurationChanged = true;

	}

	return bConfigurationChanged;
}

void TrackingPeriodicTask( void )
{
	TrackingServiceHandler(s_service1_data);
	TrackingServiceHandler(s_service2_data);
}

static void TrackingCB( void* Param )
{
	TrackingService *pData = (TrackingService*) Param;
	assert( pData != nullptr );
	Log(LOG_SUB_TRAK,LOG_LVL_WARN,"Tracking %d wakeup\n", (u16)(pData->configParams.e_msg_code-EV2OMC_Tracking_Message_1+1));
	TrackingServiceHandler( *pData );
}

void TrackingServiceHandler(TrackingService& pTrackingData )
{
	u32 CurrentOdometer = 0;
    u16 Speed = 0;
	bool bSendMessage = false;

	if ( ! (pTrackingData.isParamEnabled() ) ){
		// Tracking service is disabled. Remove all wakeup requests.
		TrackingCleanupWakeupRequests( pTrackingData );
		pTrackingData.bEnabled = false;
        pTrackingData.first_init = false; // Enable first Tracking Change Configuration message when Tracking changes from disabled to enabled
        return;
	}

	if ( CheckGlobalParameters_IsChanged( pTrackingData ) && !pTrackingData.first_init){
		pTrackingData.PrevIgnitionOn = (SYS_IGNITION_ON == SYS_GetIgnitionState())? true:false;
		Set_F_Reason( EMsgR_Tracking_ChangeConfiguration );
        TrackingMessageHelper( pTrackingData.configParams.e_msg_code );
	}

	if ( 	!RTC_IsTimeInvalid( &pTrackingData.srtc_max_time_counter ) &&
			RTC_IsTimeOccured( &pTrackingData.srtc_max_time_counter ) ){
		// Max tracking time was set and is now expired.
		WakeupServiceCheckAndClearRequest( &pTrackingData.wsi_wakeup_index_max_time );
		ChangeParameterValue( pTrackingData.configParams.EnableParamID, false );
		pTrackingData.bEnabled = false;
		return;
	}

	if ( ( !SYS_GetIsPPKInstallation() ) &&
		( SYS_GetInstallationType()== SYS_INSTALLATION_DISCRETE ) )	{
        EVENTREP_GetOdo( &CurrentOdometer, true );
        EVENTREP_GetSpeed( &Speed, true );
	} else {
        EVENTREP_GetOdo( &CurrentOdometer, false );
        EVENTREP_GetSpeed( &Speed, false );
	}

	if ( SYS_IGNITION_ON == SYS_GetIgnitionState() ){	// the vehicle in move
		if ( Speed != 0 ) {
			//if srtc_moving_time_counter is valid
			if (!RTC_IsTimeInvalid( &pTrackingData.srtc_moving_time_counter )){
				//check for srtc_moving_time_counter time pass or ignition turned ON
				if(	RTC_IsTimeOccured( &pTrackingData.srtc_moving_time_counter ) ||
					pTrackingData.PrevIgnitionOn == false ){
						//set reason and mark to send
						Set_F_Reason( EMsgR_Tracking_TimerWhileMoving );
						bSendMessage = true;
				}
			}
		} else {
            if ( !RTC_IsTimeInvalid( &pTrackingData.srtc_idle_time_counter ) ) {
                if( RTC_IsTimeOccured( &pTrackingData.srtc_idle_time_counter ) ||
                    pTrackingData.PrevIgnitionOn == false ) {
                    Set_F_Reason( EMsgR_Tracking_TimerWhileIdle );
                    bSendMessage = true;
                }
            }
		}
        //so we are not sending message
		if(bSendMessage == false){
            //check that delta distance is above 0
			if( 0 < pTrackingData.u_delta_distance ){
                //again check if ignition is just turned ON OR distance passed
				if (( pTrackingData.u_distance_counter > 0 ) &&
					( CurrentOdometer > 0 ) &&
					is_odometer_distance_passed(pTrackingData.u_distance_counter,
												pTrackingData.u_delta_distance * KM_TO_METER,
												CurrentOdometer )) // distance is over
				{
                    //set reason and mark to send
					Set_F_Reason(EMsgR_Tracking_DistanceWhileMoving);
					bSendMessage = true;
				}
				else if ( ( pTrackingData.u_distance_counter == 0 ) & ( CurrentOdometer != 0 ) ){
					// Update first odometer value.
					pTrackingData.u_distance_counter = CurrentOdometer;
				}
			}
		}

		if ( bSendMessage )
		{
			// Send message.
			TrackingMessageHelper(pTrackingData.configParams.e_msg_code);

			// Get new start distance.
			pTrackingData.u_distance_counter = CurrentOdometer;	// get new start distance

			// Reset the time passes.
			if ( Speed != 0 ) {
                InitTime_MovingDeltaTime(pTrackingData);
            } else {
                InitTime_IdleDeltaTime(pTrackingData);
            }
		}
        pTrackingData.PrevIgnitionOn = true;
	}
	else if(SYS_IGNITION_OFF == SYS_GetIgnitionState())	{			// the vehicle not in move
		if( GetEndTripMsgSent() || !IsEndTripEnabled())	{
			if( !RTC_IsTimeInvalid( &pTrackingData.TimeToSendNotDriveTimeCounter )){
				if(pTrackingData.PrevIgnitionOn == true){
					bSendMessage = true;
				}
				else if(!RTC_IsTimeInvalid( &pTrackingData.srtc_not_drive_time_counter ) &&
						 RTC_IsTimeOccured( &pTrackingData.srtc_not_drive_time_counter ) &&
						 RTC_IsTimeOccured( &pTrackingData.TimeToSendNotDriveTimeCounter)	){
							bSendMessage = true;

				}
			}

			if(bSendMessage == true){
				Set_F_Reason(EMsgR_Tracking_TimerWhileNotDriving);
				// Send message.
				TrackingMessageHelper( pTrackingData.configParams.e_msg_code );
				InitTime_NotDriveDeltaTime( pTrackingData );
				// Get new start distance.
				pTrackingData.u_distance_counter = CurrentOdometer;	// get new start distance
			}

		pTrackingData.PrevIgnitionOn = false;
		}

	}

	HeadingTracking_Handler( pTrackingData );
    ImmediateTracking_Handler( pTrackingData );

    pTrackingData.first_init=false;
}

static void TrackingCleanupWakeupRequests( TrackingService& pData )
{
	WakeupServiceCheckAndClearRequest( &pData.wsi_wakeup_index_max_time );
	WakeupServiceCheckAndClearRequest( &pData.wsi_wakeup_index_moving_time_counter );
	WakeupServiceCheckAndClearRequest( &pData.wsi_wakeup_index_idle_time_counter );
	WakeupServiceCheckAndClearRequest( &pData.wsi_wakeup_index_notdrive_time_counter );
	WakeupServiceCheckAndClearRequest( &pData.wsi_wakeup_index_Prenotdrive_time_counter );
}

static void TrackingPrintTime(const char *name, URTC_DateTime *current_dt)
{
	Log(LOG_SUB_TRAK,LOG_LVL_WARN, "%s%02d:%02d:%02d\n",
		name,
		(int)current_dt->Hour, // hour
		(int)current_dt->Min, // min
		(int)current_dt->Sec // sec
		); // ms
}


static void TrackingRportService(TrackingService& s_data)
{
	URTC_DateTime TempTime;
	Log(LOG_SUB_TRAK,LOG_LVL_WARN," Enable flag: %d\n", (int)s_data.bEnabled);
	Log(LOG_SUB_TRAK,LOG_LVL_WARN," Index of next not drive tracking message time %d \n", (int)s_data.wsi_wakeup_index_notdrive_time_counter);
	if ( WakeupServideGetNextWakeTimeOfEntry(s_data.wsi_wakeup_index_notdrive_time_counter, &TempTime))
		TrackingPrintTime(" EXPECTED Next Tracking message time ",&TempTime);
	Log(LOG_SUB_TRAK,LOG_LVL_WARN," Index of MAX time %d \n", (int)s_data.wsi_wakeup_index_max_time);
	if (WakeupServideGetNextWakeTimeOfEntry(s_data.wsi_wakeup_index_max_time , &TempTime))
		TrackingPrintTime(" MAX TIME: ",&TempTime);

	Log(LOG_SUB_TRAK,LOG_LVL_WARN," Distance %d\n", (int)s_data.u_distance_counter);
}

void TrackingRport(void)
{
	URTC_DateTime TempTime;
	RTC_GetDateTime(&TempTime);
	TrackingPrintTime("Current time ",&TempTime);
	Log(LOG_SUB_TRAK,LOG_LVL_WARN, "Tracking Service 1 Info:\n");
	TrackingRportService(s_service1_data);
	Log(LOG_SUB_TRAK,LOG_LVL_WARN, "\nTracking Service 2 Info:\n");
	TrackingRportService(s_service2_data);
}

static void TrackingTimeAddSeconds( URTC_DateTime* pTime, s32 Seconds )
{
	if ( !RTC_IsTimeInvalid( pTime ) )
	{
		RTC_AddSeconds( pTime, Seconds );
	}
}

void TrackingHandleTimeChange( s32 TimeDifference )
{
	TrackingTimeAddSeconds( &s_service1_data.srtc_max_time_counter, TimeDifference );

	if ( s_service1_data.wsi_wakeup_index_max_time != NO_WAKE_UP_INDEX )
	{
		WakeupServiceRemoveRequest( s_service1_data.wsi_wakeup_index_max_time );
		s_service1_data.wsi_wakeup_index_max_time =
			WakeupServiceAddOneTimeRequest( &s_service1_data.srtc_max_time_counter, TrackingCB, &s_service1_data );
	}
	TrackingTimeAddSeconds( &s_service2_data.srtc_max_time_counter, TimeDifference );
	if ( s_service2_data.wsi_wakeup_index_max_time != NO_WAKE_UP_INDEX )
	{
		WakeupServiceRemoveRequest( s_service2_data.wsi_wakeup_index_max_time );
		s_service2_data.wsi_wakeup_index_max_time =
			WakeupServiceAddOneTimeRequest( &s_service2_data.srtc_max_time_counter, TrackingCB, &s_service2_data );
	}
}

void TrackingInternalRun (u8 TrackingOption, u16 TotalTrackingDuration, u16 MsgTrackingTmrWhileMov, u32 MsgTrackingTmrWhileNotDriving)
{
	if( TrackingOption != 0 )
	{
		ChangeParameterValue ( TrackingOption == 1 ? Tracking_MaximumTrackingTimeT1 : Tracking_MaximumTrackingTimeT2, TotalTrackingDuration);
		ChangeParameterValue ( TrackingOption == 1 ? Tracking_TimerWhileMoving1 : Tracking_TimerWhileMoving2, MsgTrackingTmrWhileMov);
        ChangeParameterValue ( Tracking_TrackDelayStanding, MsgTrackingTmrWhileMov );
		ChangeParameterValue ( TrackingOption == 1 ? Tracking_TimerWhileNotDriving1 : Tracking_TimerWhileNotDriving2, MsgTrackingTmrWhileNotDriving);
		ChangeParameterValue ( TrackingOption == 1 ? Tracking_Enable1 : Tracking_Enable2, true);
	}
}



/***************************************************************************************/
/********************************* HEAD TRACKING HANDLERS ***********************************/
/***************************************************************************************/

static void Start_HeadingTracking( TrackingService& pTrackingData )
{
	if( pTrackingData.HeadTrk.DeltaThresh != HEADING_TRACK_DISABLE ) {
		pTrackingData.HeadTrk.State = HeadingTrackingState::CHECK;
		pTrackingData.HeadTrk.Exist = true;
		Log(LOG_SUB_TRAK, LOG_LVL_TRACE, "Start Tracking (Heading) , Deg: %03u , Delay: %d \n" , pTrackingData.HeadTrk.DeltaThresh , pTrackingData.HeadTrk.Delay);
	}
	else {
		pTrackingData.HeadTrk.State = HeadingTrackingState::IDLE;
		pTrackingData.HeadTrk.Exist = false;
		pTrackingData.HeadTrk.RestartCalculateHeading();
		Log(LOG_SUB_TRAK, LOG_LVL_TRACE, "Tracking (Heading) Disable, Delay: %d \n", pTrackingData.HeadTrk.Delay );
	}
}

static s16 GetHeadingDelta(u16 prev , u16 current)
{
	s16 delta;
	u16 abs_delta;

	delta = (s16)current - (s16)prev;
	abs_delta = (u16)abs(delta);


	if(abs_delta < 360) // for safety
	{
		if(abs_delta > 180)
		{
			if(current < prev)
				delta = 360 - abs_delta;
			else
				delta = abs_delta - 360;
		}

		//else delta as is
	}
	else
		delta = 0;


	return delta;
}

static void HeadingTracking_Handler( TrackingService& pTrackingData )
{

	u16 abs_delta;
	const GPSFix*	pGPSFix = NULL;

	pGPSFix = EVENTREP_GetLastGpsFix();


	if ( (pGPSFix == NULL ) ||
		 ( !(SYS_IGNITION_ON == SYS_GetIgnitionState()) ) ||
		 ( pTrackingData.HeadTrk.DeltaThresh == 0 ) ||
		 ( pTrackingData.HeadTrk.Exist == false ) ) {

		pTrackingData.HeadTrk.RestartCalculateHeading();
		pTrackingData.HeadTrk.State = HeadingTrackingState::IDLE;

	}
	else
	{
		pTrackingData.HeadTrk.State = HeadingTrackingState::CHECK;
	}


	switch( pTrackingData.HeadTrk.State )
	{
		case HeadingTrackingState::IDLE:
			pTrackingData.HeadTrk.SumDelta = 0;
			pTrackingData.HeadTrk.PrevHeading = pTrackingData.HeadTrk.CurrentHeading;
		break;

		case HeadingTrackingState::CHECK:

			pTrackingData.HeadTrk.CurrentHeading 	= (u16)pGPSFix->GroundCourse;
			pTrackingData.HeadTrk.Delta 	= GetHeadingDelta(pTrackingData.HeadTrk.CurrentHeading , pTrackingData.HeadTrk.PrevHeading);

			abs_delta = abs(pTrackingData.HeadTrk.Delta);

			if(abs_delta >= MINIMUM_DELTA_HEADING )
			{
		        /*** Suspect ***/
				pTrackingData.HeadTrk.PrintStatus();
				pTrackingData.HeadTrk.SwitchToHeadingActiveState();
		    }

		break;

		case HeadingTrackingState::ACTIVE:
			pTrackingData.HeadTrk.CurrentHeading 	= (u16)pGPSFix->GroundCourse;
			pTrackingData.HeadTrk.Delta 	= GetHeadingDelta(pTrackingData.HeadTrk.CurrentHeading , pTrackingData.HeadTrk.PrevHeading);
			pTrackingData.HeadTrk.SumDelta += pTrackingData.HeadTrk.Delta;

			if(TmrIsTimeOccured_ms(pTrackingData.HeadTrk.Timer , SEC2MSEC(pTrackingData.HeadTrk.Delay)))
			{
				pTrackingData.HeadTrk.PrintStatus();

				if(((u8)(abs(pTrackingData.HeadTrk.SumDelta))) >= pTrackingData.HeadTrk.DeltaThresh)
				{
					/*** Send Message ***/
                    Set_F_Reason( EMsgR_Tracking_DrivingAngleChange );
                    TrackingMessageHelper( pTrackingData.configParams.e_msg_code );
				}
				/*** Restart ***/
				pTrackingData.HeadTrk.RestartCalculateHeading();
			}

		break;

		default:
			pTrackingData.HeadTrk.State = HeadingTrackingState::IDLE;
		break;
	}
	pTrackingData.HeadTrk.PrevHeading = pTrackingData.HeadTrk.CurrentHeading;
}

void TrackingMessageHelper(EV2O_MsgCodes Msg_Code)
{
	bool sendMessage = true;
#if defined ( __F1 )
    sendMessage = MSG_Logger_CheckSpaceForMessage(Msg_Code);
#endif
	if (sendMessage)
	{
		MSG_Send_Message( Msg_Code );
	}
}

static void ImmediateTracking_Handler( TrackingService& pTrackingData )
{
    if (SParameters.Reserved_U8_29 == 1)
    {
        if (MSG_ModemSendStateConnected())
        {
            Set_F_Reason( EMsgR_ImmediateTracking );
            MSG_Send_Message( pTrackingData.configParams.e_msg_code );
            Clear_F_Reason();
        }
    }
}

/********************************************************************/
