package com.roadtrack;

import  com.roadtrack.NavigationIF;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import android.os.SystemClock;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import android.os.Handler;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.os.Messenger;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.SystemService;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;

import android.speech.tts.*;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.roadtrack.p2p.IMessage;
import android.roadtrack.p2p.MessageCodes;
import android.roadtrack.p2p.MessageGroups;
import android.roadtrack.p2p.P2PService;
import android.roadtrack.p2p.P2PService.IMessageReciever;

import android.roadtrack.dispatch.RoadTrackDispatcher;
import android.roadtrack.eventrepository.EventRepositoryReader;
import android.roadtrack.eventrepository.EventRepositoryReader.IgnigtionState;
import android.roadtrack.pconf.PConf;
import android.roadtrack.pconf.PConfParameters;
import android.roadtrack.stringservice.*;

import com.roadtrack.eventrepository.EventData;
import com.roadtrack.hmi.HmiDisplayService;
import com.roadtrack.hmi.HmiDisplayService.HmiIconId;
import com.roadtrack.LastBearingPosition;
import com.roadtrack.NotifyEvent;
import com.roadtrack.NotifyEventType;
import com.roadtrack.RTPwrSoCMgr;
import com.roadtrack.WaitingSoundPlayer;
import com.roadtrack.pnd.NavigationManager;
import com.roadtrack.pnd.PNDHandler;
import com.roadtrack.pnd.NavigationManager.RouteData;
import com.roadtrack.pnd.NavigationManager.RouteDataState;
import com.roadtrack.tbt.TBTNotificationMgr;
import com.roadtrack.traffic.TrafficIntents;
import com.roadtrack.util.LittleEndianInputStream;
import com.roadtrack.util.RTLog;
import com.roadtrack.util.RTStringMap;
import com.roadtrack.vdp.NavHandler;
import com.roadtrack.vdp.SettingsHandler;
import com.roadtrack.vdp.VdpCallHandler;
import com.roadtrack.vdp.VdpGatewayService;
import com.roadtrack.vdp.VdpGatewayService.ExtcallStatus;

import com.roadtrack.BoMsgs.SpecialPhoneNumbers;

import com.roadtrack.inrix.*;
import com.roadtrack.inrix.TrafficRoutingServices.Route;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;
import org.json.JSONException;

public class Navigation implements NotifyEvent, OnInitListener, IMessageReciever {
	/* Not From NavigationService */
    public static final String NAVIGATION_STATE_CHANGE = NavigationIF.NAVIGATION_STATE_CHANGE;
    public static final String ROUTE_SHOW_MANEUVER =    NavigationIF.ROUTE_SHOW_MANEUVER; //to TtsService.java:
    
	/*
	 * Command to the service to register a client, receiving callbacks from the
	 * service. The Message's replyTo field must be a Messenger of the client
	 * where callbacks should be sent.
	 */
    
    public static final String NAVCALL_NAME = "name";
    public static final String NAVCALL_NAME_PHONETIC = "namephonetic";
    public static final String NAVCALL_ROUTE_DEST_TTS = "route_dest_tts";
    public static final String NAVCALL_ROUTE_DEST_DISPLAY = "route_dest_display";
    public static final String NAVCALL_NAVIGATION_STATUS  =   "navigation_status";
    public static final String PHONETIC_START_CPP  = "/ls";
    public static final String PHONETIC_END_CPP    = "/le"; 
    public static final String PHONETIC_START_JAVA = "\u001B\\toi=lhp\\";
    public static final String PHONETIC_END_JAVA   = "\u001B\\toi=orth\\";
    public static final String PAUSE_CHAR          = "/p";
    public static final String SPACE_SEPERATOR 	  = " ";
    public static final String RECALC_WAV			  =	"recalculating.wav";
    
    // Copy of routing status codes in com.osa.map.geomap.feature.navigation.RoutingStatus
    public static final int ROUTE_STATUS_EMPTY_SUCCEDED = -1;
    public static final int ROUTE_STATUS_RUNNING = 0;
    public static final int ROUTE_STATUS_ABORTED = 1;
    public static final int ROUTE_STATUS_NO_CONFIG_FOUND = 2;
    public static final int ROUTE_STATUS_STOPPED = 3;
    public static final int ROUTE_STATUS_NO_START_FOUND = 4;
    public static final int ROUTE_STATUS_NO_DESTINATION_FOUND = 5;
    public static final int ROUTE_STATUS_NO_CONNECTION_FOUND = 6;
    public static final int ROUTE_STATUS_FINISHED_WITH_RESULT = 7;
    public static final int ROUTE_STATUS_EQUAL_START_DESTINATION = 8;
    public static final int ROUTE_STATUS_ROUTE_NOT_FOUND = 9;
    
    public static final int ACTION_READ_DIRECTIONS  = 0;
    public static final int ACTION_GET_MANEUVER_LIST_PND = 1;
    
    public static final int ROUTE_STATE_INACTIVE = 0;
    public static final int ROUTE_STATE_ACTIVE = 1;
    public static final int ON_ROAD_FEATURE_ENABLED = 1;
    public static final int TIME_PERIOD_FOR_RECALCULATION_MESSAGE = 4;
    public static final String INTENT_EXTRA_TTS                 = "tts";
    public static final String INTENT_EXTRA_SPEAK_INFO          = "speakInfo";
    
    public static final int ROUTE_FINISHED_DELAY = 3000;

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // Class used to indicate current guidance maneuver
    public static class GuidanceInfo {
        // Upcoming maneuver
    	public int 	  mNextManuever;    // next Manuever Index
        public double mDistance;        // [meters]
        public String mMessage;         // guidance prompt for maneuver
        public String mMessagePhon;     // guidance prompt for maneuver with phonetic street names
        public NavigationIF.MCURouteInstructions	  mSignType;        // sign type - what kind of maneuver
        public String mStreetName;      // name of street for next maneuver
        public boolean mNewManeuver ;   //if diffrent point" its a new Maneuver "
        public String mCurrentStreetName;  // name of street for the current maneuver
        public double mAngle;          // if its a roundabout exit angle is saved here else Double.NaN    
        // Total trip
        public double mDistanceToDestination; // [meters]
        public double mTimeToDestination;     // [sec]
        public boolean mIsRecalculation;
        public NavigationIF.TTJunctionType mJunctionType; 	//
        
        public GuidanceInfo() {
            this(0,0.0, "", "", NavigationIF.MCURouteInstructions.NONE, "", -1.0, 0.0, "", false , -1.0,NavigationIF.TTJunctionType.REGULAR);
        }

        public GuidanceInfo(int nextManueverIndex, double distance, String message, String messagePhon, NavigationIF.MCURouteInstructions signType, String streeName, double distToDest, double timeToDest, String currentStreetName, boolean NewManeuver , double angle,NavigationIF.TTJunctionType junctiontype) {
            mDistance              = distance;
            mMessage               = message;
            mMessagePhon           = messagePhon;
            mSignType              = signType;
            mStreetName            = streeName;
            mDistanceToDestination = distToDest;
            mTimeToDestination     = timeToDest;
            mNewManeuver           = NewManeuver;
            mAngle                 = angle;  
            mCurrentStreetName     = currentStreetName;
            mIsRecalculation       = false;
            mNextManuever		   = nextManueverIndex;
            mJunctionType		   = junctiontype;
        }

        public GuidanceInfo(GuidanceInfo copyFrom) {
            this(copyFrom.mNextManuever,copyFrom.mDistance, copyFrom.mMessage, copyFrom.mMessagePhon, copyFrom.mSignType, copyFrom.mStreetName, copyFrom.mDistanceToDestination, copyFrom.mTimeToDestination, copyFrom.mCurrentStreetName, copyFrom.mNewManeuver, copyFrom.mAngle,copyFrom.mJunctionType);
        }

        public boolean equals(GuidanceInfo that) {
            if( 
                ( distEquals(this.mDistance, that.mDistance) ) &&
                ( distEquals(this.mDistanceToDestination, that.mDistanceToDestination) ) &&
                ( this.mMessage.equals(that.mMessage) ) &&
                ( this.mSignType.equals(that.mSignType) ) &&
                ( this.mStreetName.equals(that.mStreetName) ) &&
                ( distEquals(this.mTimeToDestination, that.mTimeToDestination) ) &&
                ( this.mNewManeuver == that.mNewManeuver) &&
                ( this.mCurrentStreetName.equals(that.mCurrentStreetName) &&
                ( this.mJunctionType.equals(that.mJunctionType))&&
                ( this.mIsRecalculation == that.mIsRecalculation)) 
               ) 
               {
                return true;
            }
            return false;
        }

        private boolean distEquals( double d1, double d2 ) {
            // Ignore differences in distance less than one meter
            if( (int) d1 == (int) d2 ) {
                return true;
            }
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // Class used to encapsulate an address
    public static class NavAddressInfo {
        public String mIsoCountry;
        public String mCity;
        public String mStreet1;
        public String mStreet2;

        public NavAddressInfo(String isoCountry, String city, String street1, String street2) {
            mIsoCountry = isoCountry;
            mCity       = city;
            mStreet1    = street1;
            mStreet2    = street2;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // Navigation states
    public enum NavigationState { 
        NONE,               // no state, idle
        IGN_DELAY,          // ignition turned on, wait 10 secs before checking for active route
        PENDING_GPS,        // need to start route, waiting for GPS to become available
        PENDING_TRAFFIC,    // GPS available, witing for traffic update to request route
        PENDING_ROUTE,      // route requested, waiting for it to be prepared
        STARTED,            // route created successfully, guidance has started
        FINISHED            // destination has been reached
    };
    
    // enum for holding the type of the last accured system shut down 
    public enum ShutDownType {
        UNKNOWN,                  // set as the shutdown status in constructor
        NORMAL_IGNITION_OFF,      // set on normal ignition off
    }
    
    // this enum helps define which type of silent start are we having
    public enum StartSilently{
    	NOT_SILENT,               // all prompts are played
    	SILENT_NO_PROMPTS,  	  // no prompts are played
    	SILENT_NO_CALC_PROMPTS    // only calc raute prompts are not played
    }
    
    // enum for defining requesting command type
    public enum RequestType{ UNKNOWN, LAST_DIRECTION, ALL_DIRECTIONS, ROUTE_INFO};
    public enum ResultCode { ROUTE, ROAD, POI };
    public enum WaitingNavigationActivity { NONE, CONTINUE_AFTER_IGNITION, CONTINUE_AFTER_RECEIVED_ROUTE_FROM_BO,
                                            CONTINUE_AFTER_GPS_RECEIVED, CONTINUE_AFTER_CM_GPS_STATUS , CONTINUE_BO_REQUEST_AFTER_CHANGE_FROM_PENDING_ROUTE};
    
    public enum SPEAK_INFO { MANEUVER_FIRST_PRIOR, MANEUVER_PRIOR, MANEUVER_FAR,MANEUVER_SOON,MANEUVER_NEAR, GPS_LOST , SPEED_RESTRICTION, NOT_MANEUVER, DESTINATION_REACHED};
    public enum AckTypeForApp { HANDOVER_SUCCESS, HANDOVER_FAILURE, WAYPOINTS_SUCCESS, WAYPOINTS_FAILURE};
    public Location mLastLocationFromMcu = null;
    
    private enum RerouteState { NONE, INPROGRESS, NOTIFIED };
    private enum CallOrigin { CALLED_FROM_START_NAVI, CALLED_FROM_NOTIFY_EVENT};
    private enum NavigationOrigin {GPS_TRACKER, NOT_GPS_TRACKER};
    
    private static final String TAG                          = "Navigation";
    private static final String RT_CONF_FILE                 = "/system/vendor/roadtrack/resources/roadtrack.conf";
    private static final String SEPERATOR_BETWEEN_DIRECTIONS = "  ";
    private static final int    GPS_OK                       = 1;
    private static final int    GPS_LOST                     = 2;
    private static final int    RECHECK_HIBERNATION_TIMEHOUT = 5 * 1000;	// Timeout for testing hibernation back.
    private static final int    TIMEOUT                      = 20 * 1000;     // Lock timeout
    private static final int    IGNITION_ON_MIN_TIMEOUT      = 10 * 1000;     // How long to wait after ignition on before starting
    private static final int    IGNITION_ON_MAX_TIMEOUT      = 60 * 1000;     // Maximum value to wait after ignition on before starting
    private static final int    IGNITION_OFF_TIMEOUT         = 2 * 60 * 1000; // How long to wait for ignition off after arrival
    private static final int    CHECK_FOR_DRIVING_TIMEOUT    = 20 * 1000;     // How long to wait to check if driving after nav start
    private static final int    CHECK_FOR_DRIVING_IMMEDIATE_TIMEOUT_FIRST = 500;        // How long to wait before check if driving after nav start - short wait to allow guidance if available
    private static final int    CHECK_FOR_DRIVING_IMMEDIATE_TIMEOUT_SECOND = 700;        // How long to wait before check if driving after nav start - short wait to allow guidance if available
    private static final int    EXTRA_TIME_SAFETY_NET			= 5 * 1000;		 // How long to wait beyond the check for driving timeout
    private static final int    SPEED_LIMIT_START_DRIVE_PROMPT = 6;           // the min speed above which the prompt wont be sounded
    private static final int    CHECK_FOR_DRIVING_DISTANCE   = 50;            // [m] Give prompt if not moving this far after start
    private static final int    APPNAVI_STATEID_CNC_MAIN_FROM_IGN = 214;      // State to initiate after ignition on
    private static final int    APPNAVI_STATEID_CNC_MAIN_FROM_BO  = 219;      // State to initiate after received route from BO
    private static final int    DEFAULT_PROMPT_DISTANCE      = 200;           // [m] No periodic prompts within this distance
    private static final int    DEFAULT_PROMPT_PERIOD        = 180;           // [sec] Periodic prompt interval
    private static final int    MINIMUM_PROMPT_INTERVAL      = 7;             // [sec] Prompts should be at least this time apart
    private static final int    RECALC_PROMPT_INTERVAL       = 30;            // [sec] Minimum interval between prompts "recalculating route"
    private static final int    PROMPT_TO_MOVE_TIME          = 20;            // [sec] When to tell driver to move when recalculation fails
    private static final int    RECALC_FAIL_TIMEOUT          = 180;           // [sec] How long to allow recalc to fail before stopping navigation
    private static final int    MAX_NUMBER_OF_CHARS_FOR_ADDING_PAUSE = 80;    // max limit of chars for a maneuver message, beyond that we are adding breaks. 
    private static final int    ROUTE_FOUND_OBJECT           = 1;
    private static final int    TTS_INTENT_MESSAGE_OBJECT    = 2;
    private static final int    NUMBER_ALLOWED_TIMES_OUT     = 3;
    private static final long   HANDOVER_TIMEOUT				= 7000;
    private static final long   WAIT_SOUND_DURATION          = 500;
    private static final long   SAFETY_TIMER_FOR_VDP_STARTED_INTENT = 1000;   // used for setting timer until vdp started intent is received
    private static final long   CHECK_ROUTE_PENDING_TIMER_FOR_FOR_START_USER_REQUEST = 1000;
    private static final long 	CHECK_NO_NON_CONFIRM_IN_DATABASE_TIMER_TIMEOUT = 3000;
    private static final int	DEFAULT_START_NFND_RESTART_TIMER		= 10000;
    private static final int	DEFAULT_STOP_WAITING_SOUND	= 20000;
    private static final int	DEFAULT_ABORT_ROUTING		= 180000;
    
    private static Navigation                   sInstance = null;

    private Thread                              mMainThread;
    private Handler                             mHandler;
    private Context                             mContext;
    private NavigationReceiver                  mReceiver;
    private Object                              mCrossingStreetResultSyncObject;
    private Object                              mCurrentDestAndWaySyncObject;
    private Object                              mLocationResultSyncObject;
    private Object                              mAddressResultSyncObject;
    private Object								mProvinceResultSyncObject;
    private NavigationManager                   mNavigationManager = null;
    private GPSTracker                          mGpsTracker = null;
    private EventRepositoryReader               mRepositoryReader;
    private TelephonyManager                     mTelephonyManager;
    private NavigationState                     mNavigationState;
    private boolean                            mIsAvailableGPS;
    private int                                mLastAnnouncedGPSStatus;
    private TrafficRoutingServices.Route        mTrafficData = null;    
    private TextToSpeech                        mTts;
    private StringReader                        mStringReader;

    private volatile boolean                    mMuteOn;
    private boolean                             mVrActive;
    private boolean                             mUserMuteOn;
    private boolean                             mRoutePreviewMuteOn;
    // this flag is used for cases when guidance tts arrives but other tts is played.
    // so after tts completes this flag indicates whether we need to play a guidance/start driving prompt.
    private boolean                             mNeedToGiveGuidance; 
    private boolean							 mNeedToGiveStartDrivingPrompt;
    private boolean							 mStartDrivingPromptWithTimeOut;
    private RouteData                           mRouteDataFromBo;
    private static RTLog                        mLogger;
    private WaitingNavigationActivity           mWaitingNavigationActivity;
    private Location                            mLocationResult = null;
    private JSONObject                          mAddressResult = null;
    private String								mProvinceResult = null;
    private ArrayList<RTStringMap>              mCrossingStreetResult;
    private ArrayList<Location>                 mCurrentDestAndWay;
    private RerouteState                        mRerouteState;
    private long                                mRerouteTime;
    private long                                mRecalcPromptTime;
    private Timer                               mTimerCheckForDriving = null;
    private Timer                               mTimerCheckForDrivingShort = null;
    private Timer                               mTimerCheckAfterIgnition = null;
    private Timer                               mTimerCheckAfterHibernation = null;
    private Timer                               mTimerCancelCalcScreen = null;
    private Timer                               mTimerCheckIfPendingRoute = null;
    private Timer                               mTimerCheckIfNonConfirmRouteExist = null;
    private GuidanceInfo                        mLastGuidanceInfo = new GuidanceInfo();
    // used for storing the system time on each navigation start.
    private long								 mElapsedTimeFromStart = 0;
    // this flag is used for indication whether to start navi timer for the first time for ETA purposes.
    private boolean							 mShouldStartTimer = false;
    private String                              mLastGuidancePrompt;
    private P2PService                          mP2PService = null;
    private Location                            mTarget = null;
    private HmiDisplayService                   mHmiDisplayService = null;
    private boolean                             mWaitingForDestAndTime=false;
    private boolean							 	mClearCalcFromScreen = false;
    private boolean							 	mInNewRouteConfirmationScreen = false;
    private ShutDownType                        mShutDownType;
    private RTPwrSoCMgr                         mRTPwrSocMgr;
    private HashMap< String, String >           mMixedAudioStream;  
    private HashMap< String, String >           mUnmixedAudioStream;    
    private boolean								mShouldMix = false;
    private long                                mLastGuidancePromptTime;
    private int                                 mPeriodicPromptTime = DEFAULT_PROMPT_PERIOD;
    private int                                 mPeriodicPromptMinDistance = DEFAULT_PROMPT_DISTANCE;
    private int                                 mMinPromptInterval = MINIMUM_PROMPT_INTERVAL;
    private int                                 mRecalcPromptInterval = RECALC_PROMPT_INTERVAL;
    private int                                 mRecalcFailPromptTime = PROMPT_TO_MOVE_TIME;
    private int                                 mRecalcFailTimeout = RECALC_FAIL_TIMEOUT;
    private int 								 mSpeedLimitForDrivePrompt = SPEED_LIMIT_START_DRIVE_PROMPT;
    private WaitingSoundPlayer                  mWaitingSound;
    private long                               mWaitingSoundTimer = 0;
    private Location                            mCurrentLocationBeforeTraffic = null;
    private boolean                            mArrivedAtDestinationNotified;
    private PNDHandler                          mPndHandler;
    private static StartSilently               mStartSilently = StartSilently.NOT_SILENT;
    private Location                            mStartingLocation;
    private Timer                               mVrStartPendingTimer;
    private boolean			 				 mDisplayTbt = false;
    private boolean 							 mIsFirstGuidance = false;
    private EventRepositoryReader 				 mEventReader;
    private volatile boolean 				 mPlayedStartNfnd=false;
    private PConf 								 mPconf	= null;
	
    private Timer								 mStartNfndRestartTimer=null;
    private int								 mStartNfndRestartTimeout=DEFAULT_START_NFND_RESTART_TIMER;
    
    private Timer								 mStopWaitingSoundTimer=null;
    private int								 mStopWaitingSoundTimeout=DEFAULT_STOP_WAITING_SOUND;
        
    private Timer								 mAbortRoutingTimer=null;
    private int								 mAbortRoutingTimeout=DEFAULT_ABORT_ROUTING;
	private TBTNotificationMgr mTBTNotificationMgr;
	private NavigationIF mNavigationIF;
    private boolean mOnRoad = true;
    
    private ArrayList<String> mReadDirectionMessages = null;
    private int mReadMassegePosition = 0; 
    private IntentReceiver                       mIntentReceiver;
    
    private boolean mAllowNotifyTBTNotificationMgr = true;
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public static Navigation Instance() {
        if(sInstance == null) {
            synchronized (Navigation.class) {
                if(sInstance == null) {                  
                    sInstance = new Navigation();
                }
            }
        }

        return sInstance ; 
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private Navigation() {
        mLogger = RTLog.getLogger(TAG, "navigation", RTLog.LogLevel.INFO );
        
        mIsAvailableGPS = false;
        mLastAnnouncedGPSStatus = GPS_OK;
        mReceiver = new NavigationReceiver(null,this);
        mNavigationState = NavigationState.NONE;
        mMuteOn = false;
        mUserMuteOn = false;
        mRoutePreviewMuteOn = false;
        mVrActive = false;
        mNeedToGiveGuidance = false;
        mNeedToGiveStartDrivingPrompt = false;
        mStartDrivingPromptWithTimeOut = false;
        mRouteDataFromBo = null;
        mWaitingNavigationActivity = WaitingNavigationActivity.NONE;
        mCrossingStreetResultSyncObject = new Object();
        mCurrentDestAndWaySyncObject = new Object();
        mLocationResultSyncObject = new Object();
        mAddressResultSyncObject = new Object();
        mProvinceResultSyncObject = new Object();
        mHmiDisplayService = HmiDisplayService.getInstance();
        mShutDownType = ShutDownType.UNKNOWN;
        // Use system stream when need to say a maneuver. TTS is mixed with radio.
        mMixedAudioStream = new HashMap<String, String>();
        mMixedAudioStream.put( TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.toString( AudioManager.STREAM_NOTIFICATION ) );

        // Use TTS stream when need to say anything else. TTS should mute the radio.
        mUnmixedAudioStream = new HashMap<String, String>();
        mUnmixedAudioStream.put( TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.toString( AudioManager.STREAM_TTS ) );

        // prompt intervals (convert to msec) and minium distance
        mPeriodicPromptTime = loadRtConfigInt("nav_periodic_time", DEFAULT_PROMPT_PERIOD) * 1000;
        mPeriodicPromptMinDistance = loadRtConfigInt("nav_periodic_min_distance", DEFAULT_PROMPT_DISTANCE);
        mMinPromptInterval = loadRtConfigInt("nav_min_prompt_interval", MINIMUM_PROMPT_INTERVAL) * 1000;
        mRecalcPromptInterval = loadRtConfigInt("nav_recalc_prompt_interval", RECALC_PROMPT_INTERVAL) * 1000;
        mRecalcFailPromptTime = loadRtConfigInt("nav_prompt_to_move_time", PROMPT_TO_MOVE_TIME) * 1000;
        mRecalcFailTimeout = loadRtConfigInt("nav_recalc_fail_timeout", RECALC_FAIL_TIMEOUT) * 1000;
        mSpeedLimitForDrivePrompt = loadRtConfigInt("speed_limit_start_drive_prompt", SPEED_LIMIT_START_DRIVE_PROMPT);
        mEventReader = (EventRepositoryReader)VdpGatewayService.GetInstance().thisContext.getSystemService(Context.EVENTREP_SYSTEM_SERVICE);

        mLogger.i("nav_periodic_time = " + mPeriodicPromptTime +
                  " nav_periodic_min_distance = " + mPeriodicPromptMinDistance +
                  " nav_min_prompt_interval = " + mMinPromptInterval +
                  " nav_recalc_prompt_interval = " + mRecalcPromptInterval +
                  " nav_prompt_to_move_time = " + mRecalcFailPromptTime +
                  " nav_recalc_fail_timeout = " + mRecalcFailTimeout);
        FileInputStream conf_file = null;
        try {
        	conf_file = new FileInputStream(RT_CONF_FILE);
            Properties prop = new Properties();
            prop.load(conf_file);
            
            try {
                mStartNfndRestartTimeout = Integer.parseInt(prop.getProperty("start_nfnd_restart_timeout"))*1000;
            } catch( Exception e ) {
                mLogger.e("failed to get rt configuration for : start_nfnd_restart_timeout");
            } 

            try {
                mStopWaitingSoundTimeout = Integer.parseInt(prop.getProperty("stop_waiting_sound_timeout"))*1000;
            } catch( Exception e ) {
                mLogger.e("failed to get rt configuration for : stop_waiting_sound_timeout");
            }

            try {
                mAbortRoutingTimeout = Integer.parseInt(prop.getProperty("abort_routing_timeout"))*1000;
            } catch( Exception e ) {
                mLogger.e("failed to get rt configuration for : abort_routing_timeout");
            }
            
            
        }catch ( Exception e)
        {
        	 mLogger.e("failed to load config file !");
        }finally {
        	if(null != conf_file)
        	{
        		try {
        			conf_file.close();
        		}catch (IOException e)
        		{
        			
        		}
        	}
		}
        
        

        

        
        
        mPconf = VdpGatewayService.GetInstance().mPconf;
        
        mMainThread = new Thread( new Runnable() {
            @Override
            public void run() {
                mainThreadMethod();
            }
        }, "NavigationMain");
        mMainThread.start();
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void mainThreadMethod()
    {
        mLogger.i("mMainThread - Start");

        Looper.prepare();

        mHandler = new Handler() {
            @Override
            public void handleMessage( Message msg ) {
                mLogger.i("mMainThread - msg: " + msg);
                switch(msg.what){
                case ROUTE_FOUND_OBJECT:
                    handleRouteFoundObject handleRouteObject = (handleRouteFoundObject)msg.obj;
                    handleRouteObject.run();
                    break;
                case TTS_INTENT_MESSAGE_OBJECT:
                    handleTtsMessagesObject handleTtsIntentMessagesObj = (handleTtsMessagesObject)msg.obj;
                    handleTtsIntentMessagesObj.run();
                    break;
                }
            }
        };

        Looper.loop();
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void updateState(NavigationState newState) {
        if( mNavigationState != newState ) {
        	
        	if(mNavigationState == NavigationState.PENDING_ROUTE) {
	        	if(mStopWaitingSoundTimer!=null) {
	        		mStopWaitingSoundTimer.cancel();
	        		mStopWaitingSoundTimer=null;
	        	}
	
	        	if(mAbortRoutingTimer!=null) {
	        		mAbortRoutingTimer.cancel();
	        		mAbortRoutingTimer=null;
	        	}
        	}
        	
            switch(newState) {
                // in case switched to pending, new navigation about to start - no need asking about resume
                case PENDING_GPS:
                case PENDING_ROUTE:
                    mWaitingNavigationActivity = WaitingNavigationActivity.NONE;
                    break;
                case STARTED:
                    checkForDrivingAfterStart();
                    if( mPeriodicPromptTime != 0 ) {
                        // Schedule periodic prompt
                        handlePeriodicPromptObject obj = new handlePeriodicPromptObject();
                        mHandler.postDelayed(obj, mPeriodicPromptTime);
                    }
                    break;
                case FINISHED:
                    // Make sure we didn't miss the TTS for arrival
                    if( mNeedToGiveGuidance ) {
                        mLogger.d("Playing last guidance on FINISHED");
                        checkAndPromptIfDest();
                        mNeedToGiveGuidance = false;
                    }
                    break;
                default:
                    break;
            }
            // State has changed
            mLogger.i("State updated from: " + mNavigationState + " => " + newState );
            mNavigationState = newState;
            
            sendNavStateToApp();
            // Broadcast state change
            Intent intent = new Intent(NavigationIF.NAVIGATION_STATE_CHANGE);
            intent.putExtra("state", mNavigationState.ordinal());
            mContext.sendBroadcast(intent);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void setContext(Context context, NavigationManager navManager) {
        this.mContext = context;
        initTrackers();
        
    	mNavigationIF = new NavigationIF(this, context);
        mTts = new TextToSpeech(mContext, this);
        mStringReader = (StringReader)mContext.getSystemService(mContext.STRING_SYSTEM_SERVICE);

        mNavigationManager = navManager;
        mRepositoryReader = (EventRepositoryReader)mContext.getSystemService(Context.EVENTREP_SYSTEM_SERVICE);

        // register for event intent
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(EventData.ACTION_EVENT_UPDATED);
        context.registerReceiver( mEventIoReceiver, eventFilter);

        // register for SR intent
        IntentFilter f = new IntentFilter();
        f.addAction(VdpGatewayService.VDP_INTENT_VR_STARTED);
        f.addAction(VdpGatewayService.VDP_INTENT_VR_STOPPED);
        context.registerReceiver(mSrBroadcastReceiver, f);

        // register for Phone state intent
        IntentFilter phoneStateFilter = new IntentFilter();
        phoneStateFilter.addAction(RoadTrackDispatcher.PHONES_ARE_BACK_TO_IDLE);
        context.registerReceiver(mPhoneStateReceiver, phoneStateFilter);

        getDistanceToTarget();
        
        // register for on boot complete for sending last GPS fix request to the mcu
        mContext.registerReceiver(mBootCompletedReceiver, new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
        mP2PService = (P2PService) mContext.getSystemService(Context.P2P_SYSTEM_SERVICE);
        mP2PService.register(MessageGroups.MESSAGE_GROUP_GPSFIX, this);
        mRTPwrSocMgr = RTPwrSoCMgr.getInstance(mContext);

        // Get the telephony manager
        mTelephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

        /***************************************************************************
         * Phone State Listener
         * 
         * according to current implementation of the  BT phone logic, when receiving 
         * one of the intents handled down below we cannot tell which phone the intent 
         * relates to but we know we are not in idle.
         * 
         ***************************************************************************/ 
        mTelephonyManager.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                String stateString = "N/A";
                switch (state) {
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    stateString = "Off Hook";
                    stopWtSnd();
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                	// check if incomming call is a wakeUp call number.
                	if(!SpecialPhoneNumbers.IsZiltokCall(mContext,incomingNumber))
                	{
                		stateString = "Ringing";
                		stopWtSnd();
                	}
                    break;
                default:
                    break;
                }
                mLogger.d("event Phone State:" + stateString);
            }
        },   PhoneStateListener.LISTEN_CALL_STATE);
        
        // will create Navigation service when VDP loaded int order to prevent 20 delay on first navigation VR.
        mContext.registerReceiver(new VDPUpReceiver(), new IntentFilter(VdpGatewayService.VDP_INTENT_VDP_UP));

        // reset inConfirmation state route as new route in case of system crash/reset during route confirmation.
        RouteData routeData = mNavigationManager.pullInConfirmationQueuedRoute();
        if(null != routeData)
        {
        	mNavigationManager.setRouteState((int)routeData.id,RouteDataState.NEW_ROUTE);
        }
        
        // register for ParamUpdated intent
        mIntentReceiver = new IntentReceiver();
        IntentFilter intentFilter = new IntentFilter(PConf.PCONF_PARAMUPDATED);
        mContext.registerReceiver(mIntentReceiver, intentFilter);
    }

    public class VDPUpReceiver extends BroadcastReceiver {
        public VDPUpReceiver() {
        }
        @Override
        public void onReceive(Context context, Intent intent) {
        	createNavigationService();
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void initTrackers() {
        mLogger.d("initTrackers()");

        if( mContext == null ) {
            mLogger.e("null context");
            return;
        }

        if(mGpsTracker == null) {
            mGpsTracker = new GPSTracker(mContext,this);
            mGpsTracker.start();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void stopTrackers() {
        try {
            if(mGpsTracker!=null) {
                mGpsTracker.terminate();
                mGpsTracker.join();
            }
        } catch(InterruptedException ex) {}

        mGpsTracker = null;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * 
     * @return is gps available according to the gps tracker
     */
    public boolean isGpsAvailable() {
    	if(mGpsTracker!=null) {
    		return mGpsTracker.isGpsAvailable();
    	} else {
    		mLogger.e("gps tracker is NULL!");
    		return false;
    	}
    }
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public NavigationState getState() {
        return mNavigationState;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void startNavigationSilently() {
        mLogger.d("starting navigation silently");
        startNavigation(mTrafficData, false, StartSilently.SILENT_NO_PROMPTS, NavigationOrigin.NOT_GPS_TRACKER);
    }
    
    /**
     * used for starting navigation after waiting for gps to receive valid fixes.
     * @param naviOrigin
     */
    public void startNavigationSilently(NavigationOrigin naviOrigin) {
        mLogger.d("starting navigation silently");
        startNavigation(mTrafficData, false, StartSilently.SILENT_NO_PROMPTS, naviOrigin);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void startNavigation() {
        startNavigation(mTrafficData, false, StartSilently.NOT_SILENT, NavigationOrigin.NOT_GPS_TRACKER);
    }
    
    public void startNavigation(NavigationOrigin naviOrigin){
        startNavigation(mTrafficData, false, StartSilently.NOT_SILENT, naviOrigin);
    }
    
    public void startNavigation(boolean useInactive) {
        startNavigation(mTrafficData, useInactive, StartSilently.NOT_SILENT, NavigationOrigin.NOT_GPS_TRACKER);
    }
    
    public void startNavigation(TrafficRoutingServices.Route trafficRoute,
    							  boolean useInactive, 
    							  StartSilently startSilently){
    	startNavigation(trafficRoute, useInactive, startSilently, NavigationOrigin.NOT_GPS_TRACKER);
    }

    public void startNavigation(TrafficRoutingServices.Route trafficRoute,
    							  boolean useInactive, 
    							  StartSilently startSilently, 
    							  NavigationOrigin naviOrigin) {
        startNavigationObject obj = new startNavigationObject(trafficRoute, useInactive, startSilently, naviOrigin);
        mHandler.post(obj);
    }

    private class startNavigationObject implements Runnable {

        private final TrafficRoutingServices.Route trafficRoute;
        private final boolean useInactive;
        private final NavigationOrigin naviOrigin;
        
        public startNavigationObject(TrafficRoutingServices.Route trafficRoute, 
        							  boolean useInactive, 
        							  StartSilently startSilently, 
        							  NavigationOrigin naviOrigin) {
        	this.trafficRoute 			= trafficRoute;
            this.useInactive 			= useInactive;
            this.naviOrigin 			= naviOrigin;
            mStartSilently 				= startSilently;
        }

        public void run() {
        	 mAllowNotifyTBTNotificationMgr = true;

        	// reset audio mixing to false
        	mShouldMix = false;
        	
            mLogger.i("Starting Navigation");
            mRerouteState = RerouteState.NONE;
            mRecalcPromptTime = 0;
            mArrivedAtDestinationNotified = false;
            mWaitingForDestAndTime = false;
            mClearCalcFromScreen = true;
            mInNewRouteConfirmationScreen = false;
            mNeedToGiveGuidance = false;
            mStartingLocation = null;
            mVrStartPendingTimer = null;
            mDisplayTbt = false;
            mIsFirstGuidance = true;
            mShouldStartTimer = false;
            
            cancelTimers();
            mTarget = null;
                                       
            if((NavigationOrigin.NOT_GPS_TRACKER == naviOrigin) && (NavigationState.PENDING_GPS != mNavigationState)){
            	// in case there is navigation in the background in any state and we already announced no gps announcement
            	// we need to reset the mLastAnnouncedGPSStatus variable so if on the new navigation there is no gps the user
            	// will get this announcement.
            	mLastAnnouncedGPSStatus = GPS_OK; 
            }
            else if (NavigationOrigin.GPS_TRACKER == naviOrigin)
            {
            	// in case the origin of the navigation initiation is from the GPS state change ( the radio can be in the middle of call in that point)
            	// so mixing is set to true in order to avoid the call to be transfer to the phone
            	mShouldMix = true;
            }

            if (mRepositoryReader.isExternalBTCallActive()) {
                mShouldMix = true;
            }

            // Not a result of startNavigationSilently()
            if(mStartSilently != StartSilently.SILENT_NO_PROMPTS) {
            	mPlayedStartNfnd=false;
            }
            
            // Cancel any ongoing navigation
            shutdownNavigationEngine();

        	// Always unmute for new navigation
        	//setMute(false);
            setUserMute(false);
            
            // Get current navigation destination        
            RouteData routeData = mNavigationManager.pullApprovedQueuedRoutes();
            
            if (routeData!=null && !routeData.routingText.trim().isEmpty()){
            	mNavigationManager.createRouteHistoryRecord(routeData);
            }
            
            if( (routeData == null) || 
                ((routeData.dataState == NavigationManager.RouteDataState.NOT_ACTIVE) && !useInactive ) ) {
                mLogger.e("no active destination, aborting");
                return;
            }

            // Set target
            mTarget = new Location("");
            try {
                mTarget.setLatitude(Double.parseDouble(routeData.routingLatitude));
                mTarget.setLongitude(Double.parseDouble(routeData.routingLongitude));
            } catch( Exception e ) {
                mLogger.e("Invalid coordinates: [" + routeData.routingLatitude + "] [" + routeData.routingLongitude + "] - ", e);
                return;
            }

            mLogger.i("target = " + mTarget);
            mTrafficData = trafficRoute;
            mLastGuidanceInfo = new GuidanceInfo();
            mLastGuidancePrompt = "";

            routeData.dataState = RouteDataState.ACTIVE;
            mNavigationManager.setRouteState((int)routeData.id,RouteDataState.ACTIVE);
               
            mIsAvailableGPS = mGpsTracker.isGpsAvailable(); // update real value of gps 

            if( mIsAvailableGPS ) {
                if(mNavigationState == NavigationState.PENDING_GPS){
                    // Need to notify that GPS has returned
                    mLogger.i("gps returned - finalizing debounce");
                    checkIfShouldAnnounceGPSStatus(GPS_OK, CallOrigin.CALLED_FROM_START_NAVI);
                }
            }
            else{
                // See if need to postpone navigation to wait for GPS to return
                updateState(NavigationState.PENDING_GPS);
                mLogger.i("no gps");
                checkIfShouldAnnounceGPSStatus(GPS_LOST, CallOrigin.CALLED_FROM_START_NAVI);
                mWaitingNavigationActivity = WaitingNavigationActivity.CONTINUE_AFTER_GPS_RECEIVED;
                return;
            }
            
            // we need to announce details in case the user didn't hear it yet from the traffic information.
        	// so we want to announce it as part of flow without traffic or as part of a flow with traffic but the user 
        	// didn't choose to update traffic which means he didn't hear the route details yet.
        	if( (mTrafficData == null) && 
        								((routeData.routingAction == NavigationManager.RouteDataOrigin.USER.ordinal()) ||
        								 (routeData.routingAction == NavigationManager.RouteDataOrigin.CALLCENTER.ordinal()) ||
        								 (routeData.routingAction == NavigationManager.RouteDataOrigin.WEB.ordinal())  )){
            	mWaitingForDestAndTime = true;
            }
                    	
        	mLogger.w(" starting Nav : trffic: "+routeData.routingTrafficUsed);

            // See if traffic is needed
            if( ((mTrafficData != null) || (!routeData.routingTrafficUsed)) && (NavigationManager.RouteDataOrigin.UPDATEROUTE.ordinal() == routeData.routingAction) ) {
            	
            	// Traffic not needed or is provided
                initiateNavigation(naviOrigin,true);

                if(mStartSilently != StartSilently.SILENT_NO_PROMPTS) {
                    asyncTtsAndIfNoRoutePlaySound(mShouldMix,null);
                }
            } else {
                // Need to wait for traffic
                mStartingLocation = getCurrentLocation();
                requestTraffic();
                updateState(NavigationState.PENDING_TRAFFIC);
                
                if(mStartSilently != StartSilently.SILENT_NO_PROMPTS){
                	mLogger.v("in startnavigationobject run method - say calculating route");                    // playing the prompt through blocking function to avoid the upcomig waiting sound to overlap with the prompt.
                	// sending with remain on clear in case the vr session ended clear screen will pop after we send this text to screen.
                	mHmiDisplayService.displayToScreenTextRemainOnClear(mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE), false, HmiIconId.VDP_HMI_ICON_NONE);
                    if(mStartSilently == StartSilently.NOT_SILENT){
                    	String stringCalcRoute = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE);
                    	asyncTtsAndIfNoRoutePlaySound(mShouldMix,StringService.expandString(stringCalcRoute));
                    } else if(mStartSilently == StartSilently.SILENT_NO_CALC_PROMPTS){
                    	asyncTtsAndIfNoRoutePlaySound(mShouldMix,null);
                    }
                }
            }
        }
    }

    /**
     * cancels all timer members
     */
    private void cancelTimers(){
    	if(mTimerCheckAfterIgnition != null) {
            mTimerCheckAfterIgnition.cancel();
        }
    	if(mTimerCheckForDriving != null) {
            mTimerCheckForDriving.cancel();
        }
    	if(mTimerCheckForDrivingShort != null) {
    		mTimerCheckForDrivingShort.cancel();
    	}
    	if(mTimerCancelCalcScreen != null) {
            mTimerCancelCalcScreen.cancel();
        }
    	if(mStartNfndRestartTimer != null) {
    		mStartNfndRestartTimer.cancel();
    		mStartNfndRestartTimer=null;
    	}
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void continueNavigation() {
        // Return to active navigation after reaching destination
        mLogger.i("from state " + mNavigationState);
        if( mNavigationState == NavigationState.FINISHED ) {
            mHandler.post( new Runnable() {
                public void run() {
                    updateState(NavigationState.STARTED);
                }
            } );
        }
    }


    public static StartSilently IsStartSilently(){
        return mStartSilently;
    }
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    /**
     * sends current navigation status and if connected also the route details
     */
    public void sendNavStateToApp(){
    	if(mNavigationState == NavigationState.STARTED){
            // update gen2app with current state and route details for comparison purpose
    		RouteData routeData = mNavigationManager.pullQueuedRoutes();
    		if( null == routeData)
    		{
    			 mLogger.e(" sendNavStateToApp: routeData is null ");
    			mPndHandler.sendRouteStateToApp(ROUTE_STATE_INACTIVE, "", "", "");
    			return;
    		}
            mPndHandler.sendRouteStateToApp(ROUTE_STATE_ACTIVE,
            								routeData.routingText,
            								routeData.routingLatitude,
            								routeData.routingLongitude);
    	} else if(mNavigationState == NavigationState.NONE) {
            // update gen2app with current state
            mPndHandler.sendRouteStateToApp(ROUTE_STATE_INACTIVE, "", "", "");
    	}
    }
    
    public TrafficRoutingServices.Route getTrafficFromNav(){
    	return mTrafficData;
    }
    
    public GuidanceInfo getLastGuidanceFromNav(){
    	return mLastGuidanceInfo;
    }
    
    /**
     * @return the time elapsed from beginning of current navigation in minutes
     */
    public int getElapsedTimeFromNaviStart(){
    	return (int)((SystemClock.uptimeMillis() - mElapsedTimeFromStart) / 60000);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void stopNavigation() {
        stopNavigation(false);
    }
    
    /**
     * for calls received from other places beside vdp (need to take care of announcing to user in tts and hmi)
     * @param forceInactive
     */
    public void stopNavigationAndAnnounce(boolean forceInactive) {
    	stopWtSndSync();
    	stopNavigation(forceInactive);
    	String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVSTOPPED);
        String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_NAVSTOPPED); 
        mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
        		TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
    }
    
    public void stopNavigation(boolean forceInactive) {
        stopNavigationObject obj = new stopNavigationObject(forceInactive, false);
        mHandler.post(obj);
    }
    
    public void stopNavigation(boolean forceInactive, boolean leaveActive) {
        stopNavigationObject obj = new stopNavigationObject(forceInactive, leaveActive);
        mHandler.post(obj);
    }

    private class stopNavigationObject implements Runnable {

        final boolean forceInactive;
        final boolean leaveActiveForIgnitionOn;
        
        public stopNavigationObject(boolean forceInactive, boolean leaveActiveForIgnitionOn) {
            this.forceInactive = forceInactive;
            this.leaveActiveForIgnitionOn = leaveActiveForIgnitionOn;
        }

        public void run() {

            mLogger.i("Stopping Navigation, forceInactive = " + forceInactive);
            
            mAllowNotifyTBTNotificationMgr = true;

            // Ignore guidance prompts
            mDisplayTbt = false;
            //setMute(true);
            setUserMute(true);
            cancelTimers();

            mTarget = null;
            if( mNavigationState != NavigationState.NONE ) {
                shutdownNavigationEngine();
            } else {
                mLogger.d("no active navigation, leave engine alone");
            }
            
            // Set route status
            RouteData routeData = mNavigationManager.pullApprovedQueuedRoutes();
            if(null != routeData)
            {
                NavigationManager.RouteDataState routeState = routeData.dataState;
                
                // in case this flag true, it means we are stopping an active navigation as part of ignition off.
                // so if the route state is active it will remain in this state until ignition on. that will cause the system suggest the user 
                // restart the unfinished navigation on ignition on.
                if(!leaveActiveForIgnitionOn){

                	// Handles the cases in which the user says stop after ignition on - we want to stop active route but no new route from BO
                	if(( forceInactive ) || 
                			(mNavigationState == NavigationState.IGN_DELAY) && ((routeState == NavigationManager.RouteDataState.ACTIVE) ||
                					(routeState == NavigationManager.RouteDataState.ACTIVE_PAUSED))) {
                		// force inactive
                		routeState = NavigationManager.RouteDataState.NOT_ACTIVE;

                		// Handles the cases in which the user says stop in the middle of active navigation.
                	} else if(routeState == NavigationManager.RouteDataState.ACTIVE) {

                		routeState = NavigationManager.RouteDataState.ACTIVE_PAUSED;

                	} 
                } 
                
                mNavigationManager.setRouteState((int)routeData.id,routeState);

            }

            // Forget waypoints
            mTrafficData = null;
            mPndHandler.stopIndashNaviFlows();
            updateState(NavigationState.NONE);
            mStartSilently = StartSilently.NOT_SILENT;
            mPlayedStartNfnd=false;
            stopWtSndSync();
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void TtsCompleted() {
        if( mNeedToGiveGuidance ) {
            // Try and give updated guidance in 1/2 second (in case another TTS is already queued )
            mHandler.postDelayed( new Runnable() {
                public void run() {
                    checkIfNavigationActivityWaiting();
                }
            }, 500 );
        }
    }
    
    /**
     * check if vractive or if the safety timer activated by the confirmation after BO is not null
     * @return
     */
    private boolean isVrActive(){
        if((mVrActive) || (mVrStartPendingTimer != null)){
            return true;
        } else {
            return false;
        }
    }
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void startWtSnd(boolean isMixed) {
		mLogger.d("START_WAIT_SND");
        if(!VdpGatewayService.GetInstance().isVRisActive() && RoadTrackDispatcher.ArePhonesIdle()){
        	mLogger.d("starting waiting sound ... ");
        	VdpGatewayService.StartWaitingSound(isMixed,false);
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void stopWtSnd(){
        mLogger.d("STOP_WAIT_SND");
        // if the passed time since started playing the waiting sound is less then WAIT_SOUND_DURATION we 
        // want to sleep until the passed time in total will pass WAIT_SOUND_DURATION
        long currentTime = SystemClock.uptimeMillis();
        if(currentTime - mWaitingSoundTimer < WAIT_SOUND_DURATION){
            try{
                Thread.sleep( (WAIT_SOUND_DURATION - (currentTime - mWaitingSoundTimer)) );
            } catch (java.lang.InterruptedException e) {
                mLogger.d(e.toString());
            }
        }
        mWaitingSoundTimer = 0;
        VdpGatewayService.StopWaitingSound();
        // in case mCurrentLocationBeforeTraffic wasn't set to null in the flow due to any possible reason.
        mCurrentLocationBeforeTraffic = null;
    }
    private void stopWtSndSync(){
        mLogger.d("STOP_WAIT_SND_SYNC");
        // if the passed time since started playing the waiting sound is less then WAIT_SOUND_DURATION we 
        // want to sleep until the passed time in total will pass WAIT_SOUND_DURATION
        long currentTime = SystemClock.uptimeMillis();
        if(currentTime - mWaitingSoundTimer < WAIT_SOUND_DURATION){
            try{
                Thread.sleep( (WAIT_SOUND_DURATION - (currentTime - mWaitingSoundTimer)) );
            } catch (java.lang.InterruptedException e) {
                mLogger.d(e.toString());
            }
        }
        mWaitingSoundTimer = 0;
        VdpGatewayService.StopWaitingSoundSync();
        // in case mCurrentLocationBeforeTraffic wasn't set to null in the flow due to any possible reason.
        mCurrentLocationBeforeTraffic = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void updateWaypoints(TrafficRoutingServices.Route trafficRoute) {
        // If not waiting for traffic waypoints, then error
        if( mNavigationState != NavigationState.PENDING_TRAFFIC ) {
            mLogger.e("traffic arrived in unexpected state: " + mNavigationState);
            return;
        }

        mHandler.post( new Runnable() {
            private TrafficRoutingServices.Route mRoute;
            public void run() {
                mTrafficData = mRoute;
                mCurrentLocationBeforeTraffic = null;
                // the timer will be started after we receive route_found
                mShouldStartTimer = true;
                // we want cm to start calculating route so we call initiateNavigation() first and then start the traffic prompt which is 
                // blocking, this way when the prompt finishes there may be a ready route already waiting.
                initiateNavigation(NavigationOrigin.NOT_GPS_TRACKER,false);
                
                if( mTrafficData != null ) {
                    RouteData routeData = mNavigationManager.pullApprovedQueuedRoutes();
                    if( routeData == null ) {
                        mLogger.e("ROUTE_FOUND but no route in db, stop navigation; state = " + mNavigationState);
                        stopNavigation();
                        return;
                    }
                    
                    String numpoints = ( mTrafficData.WayPoints == null ) ? "null" : Integer.toString(mTrafficData.WayPoints.size());
                    mLogger.i("starting navigation with " + numpoints + " waypoints");
                    
                    if( (mNavigationState != NavigationState.STARTED) && (mNavigationState != NavigationState.FINISHED) ) {
                        if(routeData.routingAction != NavigationManager.RouteDataOrigin.UPDATEROUTE.ordinal()) {
                            if(routeData.routingTrafficUsed && mTrafficData != null) {
                                // in case traffic data prompt was played we can base the location comparison 
                                // in checkForDrivingAfterStart with the location measured here and then save time
                                // in any other scenario need to measure and wait for the timeout
                                //mCurrentLocationBeforeTraffic = getCurrentLocation();
                            }                  
                        } else {
                            if(!getMuteStatus()){
                                if(mStartSilently != StartSilently.SILENT_NO_PROMPTS){
                                	stopWtSndSync();
                                    String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVSTARTED);
                                    String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_NAVSTARTED); 
                                    // If this tts is not played within about ~30 sec its not relevant as guidance probably will already arrive.
                                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
                                            30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                                }
                            }
                        }
                    }

                } else {
                    mLogger.i("starting navigation with no traffic data");
                }
            }
            private Runnable init(TrafficRoutingServices.Route trafficRoute) {
                mRoute = trafficRoute;
                return this;
            }
        }.init(trafficRoute) );
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /** 
    * function used for formating the announcement that will be displayed and played to the user when route received. 
    *
    */
    private void announceDetailsOfRoute(boolean shouldUseTTSSync, boolean routeSelByUser){
    	mLogger.v("in announceDetailsOfRoute - shouldUseTTSSync : " + shouldUseTTSSync + ", routeSelByUser : " + routeSelByUser);
    	String prompt_to_show = "";
        String prompt_to_say  = "";
        String distance       = "";
        String ETA            = "";
        
        int seconds_to_dest=0;
        int meters_to_dest=0;
        
        
        if( mTrafficData != null ) {
        
            meters_to_dest=(int)mTrafficData.Distance;
            seconds_to_dest=(int)mTrafficData.TravelTimeMin*60;
            
        } else if(mLastGuidanceInfo != null) {

        	meters_to_dest=(int)mLastGuidanceInfo.mDistanceToDestination;
        	seconds_to_dest=(int)mLastGuidanceInfo.mTimeToDestination;
        	
        }

        distance = metersToDistanceString(meters_to_dest);
        ETA = secondsToTimeString(seconds_to_dest);        
        if((meters_to_dest != 0) && (seconds_to_dest != 0)){
        	
        	int stringId = StringReader.Strings.APPNAVI_PROMPTID_DIST_AND_TIME_WITH_TRAFFIC;
        	if(mTrafficData == null){
        		if (mPconf.GetIntParam(PConfParameters.Navigation_TrafficFeatureStatus,0) == 2) // Traffic enabled        			
        			stringId = StringReader.Strings.APPNAVI_PROMPTID_DIST_AND_TIME_WITHOUT_TRAFFIC;
        		else
        			stringId = StringReader.Strings.APPNAVI_PROMPTID_DIST_AND_TIME;
        	}
        	prompt_to_show = String.format(mStringReader.getStringByIdForDisplay(stringId), distance, ETA);
        	prompt_to_say  = String.format(mStringReader.getExpandedStringById(stringId)  ,distance + "./p", ETA + "./p"); 


        // if only one of the estimations available we use the part strings including only the available part description(distance or ETA).
        } else if((meters_to_dest != 0) || (seconds_to_dest != 0)) {
        	
	        prompt_to_show +=
	                        ((meters_to_dest!=0)?(mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_DISTANCE) +
	                        " " +  distance + ". "):"") + 
	                        ((seconds_to_dest!=0)?(mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_NAVI_ETA) +                                  
	                        " " + ETA + ". "):"");
	        prompt_to_say += 
	        				((meters_to_dest!=0)?(mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_DISTANCE) +
	                        " " + distance + "./p"):"") +
	                        ((seconds_to_dest!=0)?(" " + mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVI_ETA) +                                  
	                        " " + ETA + "./p"):"");

        }
        
        
        if ((mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , ON_ROAD_FEATURE_ENABLED) == ON_ROAD_FEATURE_ENABLED) && !mOnRoad){
            int addStringId = StringReader.Strings.APPNAVI_PROMPTID_OUT_OF_ROAD_ADD_TO_DIST_AND_TIME;
            prompt_to_show = mStringReader.getStringByIdForDisplay(addStringId) + " " + prompt_to_show ;
            prompt_to_say = mStringReader.getExpandedStringById(addStringId) + " " + prompt_to_say;
        }
        
        if(shouldUseTTSSync) {
            // using ttssync to block until prompt is finished playing to avoid from guidance to barge in if ready before 
            // prompt finished
            mHmiDisplayService.displayToScreenText(prompt_to_show, true, HmiIconId.VDP_HMI_ICON_NONE);
            asyncTtsAndIfNoRoutePlaySound(false,StringService.expandString(prompt_to_say));
        } else {
            if(!getMuteStatus()){
                // This tts should be heard immediately and otherwise not played (in case there is active vr session at the moment and there is a change that navigation
            	// will be stopped by command).
            	// this is done mainly to prevent from route info be heard after the user stopped navigation.
            	int ttsRelevance = TextToSpeech.TTS_RELEVANCE_ETERNAL;
                if(isVrActive()){
                	ttsRelevance = TextToSpeech.TTS_RELEVANCE_IMMEDIATE_ONLY;
                } 
                stopWtSndSync();
                mTts.speak(StringService.expandString(prompt_to_say), TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL,
                		ttsRelevance, prompt_to_show, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
            }
        }
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean shouldDisplayTbt(){
    	return mDisplayTbt;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private String secondsToTimeString(int timeToDest) {
        StringReader sr = (StringReader)mContext.getSystemService(mContext.STRING_SYSTEM_SERVICE);
        
        String unit_minutes = sr.getStringById(StringReader.Strings.GENERIC_PROMPTID_MINUTES);
        String unit_hours = sr.getStringById(StringReader.Strings.GENERIC_PROMPTID_HOURS);
        
        int hours = timeToDest / 3600;
        int secs = timeToDest - (hours * 3600);
        int minutes = secs / 60; // Round down to nearest minute
        String fulltime = "";

        // do not allow zero time - round up to 1 minute
        if( (hours == 0) && (minutes == 0) ) {
            minutes = 1;
        }

        // use single units for 1
        if( hours == 1 ) {
            unit_hours = sr.getStringById(StringReader.Strings.GENERIC_PROMPTID_HOUR);
        }

        // add hours
        if( hours > 0 ) {
            fulltime = String.format("%s", hours) + " " + unit_hours;
        }
    
        if( (hours > 0) && (minutes > 0) ) {
            fulltime += ", ";
        }

        // use single units for 1
        if( minutes == 1 ) {
            unit_minutes = sr.getStringById(StringReader.Strings.GENERIC_PROMPTID_MINUTE);
        }
    
        // add minutes
        if( minutes > 0 ) {
            fulltime += String.format("%s", minutes) + " " + unit_minutes;
        }
                
        return fulltime;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public static int loadRtConfigInt(String key, int defaultValue) {
        int result = defaultValue;
        FileInputStream conf_file = null;
        try {
        	conf_file = new FileInputStream(RT_CONF_FILE);
            Properties prop = new Properties();
            prop.load(conf_file);
            try {
                result = Integer.parseInt(prop.getProperty(key));
            } catch( Exception e ) {
            	mLogger.e("failed to get rt configuration for [" + key + "]: ", e);
                result = defaultValue;
            }            
            
        }catch( Exception e )
        {
        	mLogger.e("failed to load rt configuration file !");
        }finally {
        	if(null != conf_file)
        	{
        		try {
        			conf_file.close();
        		}catch (IOException e)
        		{
        			
        		}
        		
        	}
		}

        return result;
    }  
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /*
       * Round a distance.
       * 
       * 0m.....200m  => round to nearest 10m
       * 201m.....1000m => round to nearest 50m
       * 1001m....10000m => round to nearest 100m
       * 10001m...100000m => round to nearest 1000m
       * > 100000m => round to nearest 5000m
   */
    
    public static long roundDistance(long distance) {
        int roundFactor = 10;

        if(distance < 10) return 0;        
        
        if (distance > 100000)        roundFactor = 5000;
        else if (distance > 10000)    roundFactor = 1000;
        else if (distance > 1000)     roundFactor = 100;
        else if (distance > 200)      roundFactor = 50;

        // Round distance to nearest 
        return ( ( ( distance ) + roundFactor / 2 - 1 ) / roundFactor ) * roundFactor;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private String metersToDistanceString( long distance ) {
        String dist_string = "";

        if( distance >= 0 ) {

            long roundedDistance = roundDistance( distance );
            String unit = mStringReader.getStringById(StringReader.Strings.GENERIC_PROMPTID_KILOMETERS);

            if( roundedDistance < 1000 ) { // distance in meters
                unit = mStringReader.getStringById(StringReader.Strings.GENERIC_PROMPTID_METERS);
                dist_string = String.format("%d %s", roundedDistance, unit);
            } else if( roundedDistance >= 1000 && roundedDistance < 10000 ) { // km, include one decimal point
                if( (roundedDistance % 1000) == 0 ) {
                    // exact number of km
                    roundedDistance = roundedDistance / 1000;
                    if( roundedDistance == 1 ) {
                        unit = mStringReader.getStringById(StringReader.Strings.GENERIC_PROMPTID_KILOMETER);
                    }
                    dist_string = String.format("%d %s", (int)roundedDistance, unit);
                } else {
               
                    dist_string = String.format("%1.1f %s", ((double)roundedDistance/100.0) / 10.0, unit);
                }
            } else {  // km, no fractions
                roundedDistance = roundedDistance / 1000;
                dist_string = String.format("%d %s", roundedDistance, unit);     
            }
        }       
        return dist_string;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private boolean promptIfNoNavigation(RequestType requestType) {
        boolean boolToReturn = true;
        String prompt = "";
        String promptToDisplay = "";
              
        
        if( mNavigationState == NavigationState.PENDING_GPS || (requestType == RequestType.LAST_DIRECTION && !isGpsAvailable())) {
            
            if(( requestType == RequestType.LAST_DIRECTION ) || (requestType == RequestType.ALL_DIRECTIONS)){
                prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NO_GPS_DIRECTION_NOT_AVAIL);
            } else if (requestType == RequestType.ROUTE_INFO){
                prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NO_GPS_ROUTEINFO_NOT_AVAIL);
            } else {
                prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVINOGPS);
            }
            
            promptToDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_NAVINOGPS);
            boolToReturn = false;
        } else if( mNavigationState == NavigationState.PENDING_TRAFFIC ) {
            prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_MSG_TRAFFIC_QUERY);
            promptToDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_MSG_TRAFFIC_QUERY);
            boolToReturn = false;
        } else if( mNavigationState == NavigationState.PENDING_ROUTE ) {
        	mLogger.v("in promptIfNoNavigation - say calculating route");   
            prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE);
            promptToDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE);
            boolToReturn = false;
        } else if( (mNavigationState != NavigationState.STARTED) && (mNavigationState != NavigationState.FINISHED) ) {
            prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVI_NOT_RUNNING);
            promptToDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_NAVI_NOT_RUNNING);
            boolToReturn = false;
        } else if( (mLastGuidanceInfo == null) || (mLastGuidanceInfo.mDistanceToDestination < 0.0) ) {
            prompt = mStringReader.getExpandedStringById(StringReader.Strings.MSG_INFO_NOT_AVAILABLE);
            promptToDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.MSG_INFO_NOT_AVAILABLE);
            boolToReturn = false;
        } else if( (mNavigationState == NavigationState.FINISHED) ){
            checkAndPromptIfDest();
            return false;
        }
        if(!boolToReturn){
        	stopWtSndSync();
            // This tts should be heard as soon as possible.
            if(!mHmiDisplayService.isDisplayingSelection()){
                mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                        TextToSpeech.TTS_RELEVANCE_ETERNAL, promptToDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
            } else {
                mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                        TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
            }
    
            return false;
        } else {
            return true;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * check if next maneuver is dest, if so, it means no nodes left and therefore we want the destination guidance instead. 
     * @return true if we left with the dest guidance msg only, otherwise returns false.
     */
    private boolean checkAndPromptIfDest(){
        if( !mLastGuidanceInfo.mMessagePhon.isEmpty() ) {
                // say the dest guidance
                if( mLastGuidanceInfo.mSignType == NavigationIF.MCURouteInstructions.ARRIVE ) {
                    String prompt;
                    // If we have arrived do not use prompt with distance
                    if( (mLastGuidanceInfo.mDistance < 30.0 || mNeedToGiveGuidance)  && !mLastGuidancePrompt.isEmpty()) {
                        prompt = mLastGuidancePrompt;
                    } else {
                        prompt = StringService.expandString(mLastGuidanceInfo.mMessagePhon);
                    }
                    // This tts should be played immediately.
                    playGuidanceTTS( prompt );
            
                    return true;
                }
        }
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    public class TTSData
    {
    	private String mPromptToSay = "";
    	private String mPromptToDisplay = "";
    	
    	public TTSData(String say , String display)
    	{
    		mPromptToSay = say;
    		mPromptToDisplay = display;
    	}
    	
    	public void setPromptToSay(String say)
    	{
    		mPromptToSay = say;
    	}
    	public void setPromptToDisplay(String display)
    	{
    		mPromptToDisplay = display;
    	}
    	public String getPromptToSay()
    	{
    		return mPromptToSay;
    	}
    	public String getPromptToDisplay()
    	{
    		return mPromptToDisplay;
    	}
    }

    public TTSData getCurrentRouteInfo() {

    	mLogger.d("getCurrentRouteInfo()");

    	if( !promptIfNoNavigation(RequestType.ROUTE_INFO) ) {
    		return new TTSData("","");
    	}

    	StringReader sr = (StringReader)mContext.getSystemService(mContext.STRING_SYSTEM_SERVICE);
    	String prompt = "";
    	String promptForHmi = "";

    	if ((mLastGuidanceInfo != null))
    	{
	    	// add off road disclaimer before route info
	    	if ( (mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , 1) == 1) && !mOnRoad){
	
	    		prompt += sr.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_OUT_OF_ROAD_ADD_TO_DIST_AND_TIME)+" "+ (((mLastGuidanceInfo.mCurrentStreetName != null) && (!mLastGuidanceInfo.mCurrentStreetName.trim().isEmpty()))
	    				? StringService.expandString("/streetname"+mLastGuidanceInfo.mCurrentStreetName+"/nm"): "");
	
	    		promptForHmi += sr.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_OUT_OF_ROAD_ADD_TO_DIST_AND_TIME) +" "+ (((mLastGuidanceInfo != null) && (mLastGuidanceInfo.mCurrentStreetName != null) && (!mLastGuidanceInfo.mCurrentStreetName.trim().isEmpty()))
	    				? mLastGuidanceInfo.mCurrentStreetName+", ": "");
	
	    		prompt += " ; ";
	    	}
	    	// Prepare prompt with distance to destination:
	    	//    - if we are not about to arrive
	    	//    - OR if there is no message prompt
	    	if( mLastGuidanceInfo.mSignType != NavigationIF.MCURouteInstructions.ARRIVE || mLastGuidanceInfo.mMessagePhon.isEmpty() ) {
	    		prompt       += sr.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_DISTANCE);
	    		promptForHmi += sr.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_DISTANCE);
	
	    		String distance = " " + metersToDistanceString((long)mLastGuidanceInfo.mDistanceToDestination);
	
	    		prompt       += distance ;
	    		promptForHmi += distance ;
	    	}
	    	// Add next maneuver message if exists, but not for departure point only if on the road ( when onroad feature is enabled)
	    	if( (!mLastGuidanceInfo.mMessagePhon.isEmpty() && mLastGuidanceInfo.mSignType != NavigationIF.MCURouteInstructions.DEPART) ) {
	    		if( !prompt.isEmpty() ) {
	    			prompt += " ; ";
	    		}
	    		if( !promptForHmi.isEmpty() ) {
	    			promptForHmi += ", ";
	    		}
	    		// Add prompt "next maneuver" only if it is a maneuver
	    		if( mLastGuidanceInfo.mSignType != NavigationIF.MCURouteInstructions.ARRIVE ) {
	    			prompt += sr.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_MSG_NEXT_MANEUVER) + " ";
	    			promptForHmi += sr.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_MSG_NEXT_MANEUVER) + " ";
	    		}
	    		prompt += StringService.expandString(mLastGuidanceInfo.mMessagePhon);
	    		promptForHmi += mLastGuidanceInfo.mMessage;
	    	}
    	}else
    	{
    		mLogger.e("getCurrentRouteInfo: mLastGuidanceInfo is null !");
    	}

    	stopWtSndSync();
    	
    	TTSData result = new TTSData(prompt,promptForHmi);

    	return result;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    //get information of the current position of the unit
	public void getCurrentRoadInformation() {
		mNavigationIF.getRoadInfo(mReceiver);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public ArrayList<RTStringMap> getCrossingStreetLocation(String country, String city, String street1, String street2) {
        try {
			mCrossingStreetResult = null;

			mNavigationIF.getCrossingStreetLocation(country, city, street1, street2);

            synchronized(mCrossingStreetResultSyncObject) {
            		mCrossingStreetResultSyncObject.wait(TIMEOUT);
                    if( mCrossingStreetResult == null ) {
                        mLogger.e("Timed out waiting for crossing street check");
                    }
            }
        } catch (InterruptedException e) {}
        return mCrossingStreetResult;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public ArrayList<Location> getCurrentDestinationAndWaypoints() {
        if( mNavigationState == NavigationState.NONE ) {
            return null;
        }

        try {

        	synchronized(mCurrentDestAndWaySyncObject) {
				mCurrentDestAndWay = null;
				mNavigationIF.sendMessage(NavigationIF.ACTION_GET_ACTIVE_DEST_AND_WAYPOINTS);
        		mCurrentDestAndWaySyncObject.wait(TIMEOUT);
        		if( mCurrentDestAndWay == null ) {
        			mLogger.e("Timed out waiting for current destination and waypoints");
        		}
        	}
        } catch (InterruptedException e) {}
        return mCurrentDestAndWay;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

	public void getPOIsByLocation(Location currentLocation, String category, int radius) {

		mNavigationIF.getPOIsByLocation(currentLocation, category, radius, mReceiver);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public Location getLocationByAddress(String country,String province, String city, String firstStreetName, String secondStreetName, String houseNumber) {
        return getLocationByAddress(country,province, city, firstStreetName, secondStreetName, houseNumber, true);
    }

    public Location getLocationByAddress(String country, String province, String city, String firstStreetName, String secondStreetName, String houseNumber, boolean needResult) {
    	if(houseNumber.isEmpty() && null != mLocationResult && !(null == firstStreetName || firstStreetName.isEmpty()))
    	{
    		return mLocationResult;
    	}
		try {

			mNavigationIF.getLocationByAddress(country,province, city, firstStreetName, secondStreetName, houseNumber, needResult);
            mLocationResult=null;

            if( needResult ) {
                synchronized(mLocationResultSyncObject) {
                    mLocationResultSyncObject.wait(TIMEOUT);
                    if( mLocationResult == null ) {
                        mLogger.e("Timed out waiting for location by address");
                    }
                }
            }
        } catch (InterruptedException e) {}

        return mLocationResult;
    }
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    public void createNavigationService()
    {
    	mNavigationIF.createService();
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public JSONObject getAddress(double latitude, double longitude) {

		try {
			mNavigationIF.getAddress(latitude, longitude);

            mAddressResult = null;

            synchronized(mAddressResultSyncObject) {
                mAddressResultSyncObject.wait(TIMEOUT);
                if( mAddressResult == null ) {
                    mLogger.e("Timed out waiting for address from location");
                }
            }
        } catch(InterruptedException ex) {}
		
		// workaround for unfound street from tomtom 
		try{
			if ((null != mAddressResult) && mAddressResult.getString("street").equals("(Unnamed location)"))
			{
				mAddressResult.put("street","");
			}
		}catch(JSONException ex){
		}
		
			
        return mAddressResult;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    public String getFullProvinceName(String province,String country)
    {
    	try{
    		
    		mProvinceResult = null;
    		
    		mNavigationIF.getFullProvinceName(province,country);	
    		
    		if (mProvinceResult != null)
    			return mProvinceResult;
    		
    		synchronized (mProvinceResultSyncObject) {
				mProvinceResultSyncObject.wait(TIMEOUT);
				
				if (mProvinceResult == null){
					mLogger.e("Timed out waiting for province");
				}
			}		
    	}catch (InterruptedException ex){
    	}
    	
    	return mProvinceResult;
    	
    }
    

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public Location getCurrentLocation(boolean useMcu) {

        Location location = null;
        if( mContext != null ) {
            try {
                LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch( Exception e ) {
                mLogger.e("cannot get location", e);
            }
        } else {
            mLogger.e("cannot get location, no context - should only be used in context of the SystemServer");
        }

        if( (location == null) && useMcu ) {
            // try to get last known location stored on mcu
            location = mLastLocationFromMcu;
            if( location == null ) {
                mLogger.e("no current location available from MCU");
            }
        }
        if( location != null ) {
            location = new Location(location);
        }
        return location;
    }
    
    private Location getCurrentLocation() {
        return getCurrentLocation(false);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public JSONObject getCurrentAddress() {
        Location location = getCurrentLocation(true);
        return location == null ? null : getAddress(location.getLatitude(), location.getLongitude());
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // this function is used for the address search and poi functionalities that in order to decide whether to 
    // allow or not nearby option to the user need to check if there is last know location available
    public boolean isLocationAvailable() {
        Location location = getCurrentLocation(true);
        return (location != null);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

	public void runSearchTest() {
		mNavigationIF.sendMessage(NavigationIF.ACTION_SEARCH_TEST);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void initiateNavigation(NavigationOrigin naviorigin ,boolean needToSayCalcRoute) {

        if( mTarget == null ) {
            mLogger.e("initiate with null target, navigation aborted");
            return;
        }
        
        mAllowNotifyTBTNotificationMgr = true;

        updateState(NavigationState.PENDING_ROUTE);


        
 
        // setting first location as for no traffic case
        ArrayList<Location> finalWayPoints = new ArrayList<Location>();
        finalWayPoints.add(mTarget);
        
        // set waypoints for traffic navigation
        if( (mTrafficData != null) && (mTrafficData.WayPoints != null) && (mTrafficData.WayPoints.size() > 1) ) {
        	int startWP = 0;
        	//finalWayPoints = Arrays.copyOfRange(mTrafficData.WayPoints, startWP, mTrafficData.WayPoints.length);
        	finalWayPoints = mTrafficData.WayPoints;
        }
        
        if(mStartSilently != StartSilently.SILENT_NO_PROMPTS){
        	// if the prompt already was played before the traffic request we dont want to sound it again.
        	if(needToSayCalcRoute){
        		mLogger.v("in initiateNavigation - say calculating route");
        		// sending with remain on clear in case the vr session ended clear screen will pop after we send this text to screen.
        		mHmiDisplayService.displayToScreenTextRemainOnClear(mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE), false, HmiIconId.VDP_HMI_ICON_NONE);
        		// possible that prompt was already played by vdp and therefore we only want the hmi display to show message without the prompt
        		if(mStartSilently != StartSilently.SILENT_NO_CALC_PROMPTS){
        			// playing the prompt through blocking function to avoid the upcomig waiting sound to overlap with the prompt.
        			String stringCalcRoute = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE);
        			// if route calacution triggered from reconnected GPS , the sound will be mixed with radio
        			VdpGatewayService.PlayTtsSync(StringService.expandString(stringCalcRoute),(NavigationOrigin.GPS_TRACKER == naviorigin && mLastAnnouncedGPSStatus == GPS_OK));

        		}
        	}
        }

        mNavigationIF.start(finalWayPoints);


        // Make sure that process is always started. See ActivityManagerService.java
                        
        Intent intent = new Intent();
		intent.setAction(NavigationIF.CALCULATING_ROUTE);
		notifyTBTNotificationMgr(mContext, intent);
        
        mAbortRoutingTimer = new Timer();
        mAbortRoutingTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				mHandler.post(new Runnable() {
					
					@Override
					public void run() {
		                if(mNavigationState == NavigationState.PENDING_ROUTE) {

		                	stopWtSndSync();
		                	
		                	stopNavigation();
		                	
		                    String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_EMPTY_ROUTE);                	
		                    String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_EMPTY_ROUTE);

		                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
		                    		TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
		                    
		                    mAbortRoutingTimer = null;
		                }
					}
				});
				
			}
			
		}, mAbortRoutingTimeout);
        

    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void asyncTtsAndIfNoRoutePlaySound(final boolean isMixed ,final String ttsToPlay) {

    	mLogger.d("asyncTtsAndIfNoRoutePlaySound");
        // Play TTS in separate thread so Navigation is free to respond to events
        Thread t = new Thread( new Runnable() {
            @Override
            public void run() {
                if( ttsToPlay != null ) {
                	HashMap<String,String> audioType = mUnmixedAudioStream;
                	if(isMixed)
                	{
                		mLogger.d("calculating route in mixed mode sound.");
                		audioType = mMixedAudioStream;
                	}
                	else
                	{
                		mLogger.d("calculating route in mono mode sound.");
                	}
                	if(StringService.expandString(mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_CALCROUTE)) .equalsIgnoreCase(ttsToPlay))
                	{
                		mTts.speak(ttsToPlay, TextToSpeech.QUEUE_ADD, audioType,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
                    		TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true,20, HmiDisplayService.NO_DISPLAY);
                	}
                	else
                	{
                		mTts.speak(ttsToPlay, TextToSpeech.QUEUE_ADD, audioType,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
                        		TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                	}
                	//VdpGatewayService.PlayTtsSync(ttsToPlay,isMixed);
                	mLogger.d("asyncTtsAndIfNoRoutePlaySound RUN sync return");
                }
                
                

                // When TTS is complete, start waiting sound if still waiting for traffic or route to be calculated
                // Note that this should be done in the main handler because the navigation state may be changing.
                mHandler.post( new Runnable() {
                    public void run() {
                        if( (mNavigationState == NavigationState.PENDING_ROUTE) || (mNavigationState == NavigationState.PENDING_TRAFFIC) ) {
                            mWaitingSoundTimer = SystemClock.uptimeMillis();
                            startWtSnd(isMixed);
                            // in case waiting sound started somehow after the events that supposed to stop it set mClearCalcFromScreen to true and then next 
                            // tts guidance will stop the waiting sound.
                            mClearCalcFromScreen = true;
                            
                            mStopWaitingSoundTimer = new Timer();
                            mStopWaitingSoundTimer.schedule(new TimerTask() {
            					
            					@Override
            					public void run() {
            						mHandler.post(new Runnable() {
										
										@Override
										public void run() {
		                                    if( (mNavigationState == NavigationState.PENDING_ROUTE) || (mNavigationState == NavigationState.PENDING_TRAFFIC) ) {

		                                    	stopWtSndSync();

		                                    	mClearCalcFromScreen = false;
		                                    	                                        
		                                        String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_LONG_ROUTING);                	
		                                        String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_LONG_ROUTING);

		                                        mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
		                                        		TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
		                                        
		                                        // to avoid from the tbt msg get to the screen before the "long routing" msg we give it a 1000 ms head start,
		                                        // and then we try until tts ends.
		                                        Thread sendTbtThread = new Thread(new Runnable() {
													public void run() {
														try {
															do {
																Thread.sleep(1000);
															} while (mTts.isSpeaking());
														} catch (InterruptedException e) {
															// TODO Auto-generated catch block
															e.printStackTrace();
														}
														
                                                        //make sure Navigation is still pending route/traffic before sending an mcu request to activate routing screen on radio display         
                                                        if( (mNavigationState == NavigationState.PENDING_ROUTE) || (mNavigationState == NavigationState.PENDING_TRAFFIC) ) {
														    Intent intent = new Intent();
                                                            intent.setAction(NavigationIF.NAVIGATION_RECALCULATING_ROUTE);
                                                            notifyTBTNotificationMgr(mContext, intent);
                                                        }
													}
												});
		                                        sendTbtThread.start();
		                                        mStopWaitingSoundTimer = null;
		                                    }
										}
										
									});

            					}
            					
            				}, mStopWaitingSoundTimeout);

                        }
                    }
                } );
                
            }
        }, "NavigationAsyncTts");
        t.start();
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void requestTraffic() {
        Intent intent = new Intent(TrafficIntents.GET_TRAFFIC_FOR_ACTIVE_ROUTE);
        mContext.sendBroadcast(intent);
    }


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void getDistanceToTarget() {
        mLogger.d("getDistanceToTarget");
        if(mNavigationState != NavigationState.STARTED) {
            return;
		}
		mNavigationIF.getDistanceToTarget(mReceiver);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void setUserMute(boolean muteOn) {
        mLogger.d("setUserMute" + muteOn);
        mUserMuteOn = muteOn;
        TBTNotificationMgr.setMute(mUserMuteOn);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean getUserMute(){
        return mUserMuteOn;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void setRoutePreviewMute(boolean muteOn) {
        mLogger.d("setRoutePreviewMute" + muteOn);
        mRoutePreviewMuteOn = muteOn;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean getRoutePreviewMute(){
        return mRoutePreviewMuteOn;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean getMuteStatus(){
        boolean muteState = false;
        if(mUserMuteOn || mRoutePreviewMuteOn){
            muteState = true;
        }
        return muteState;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

	public void RetriveManeuverByOffset(int offset) {
		mNavigationIF.getRoutPreview(offset); // startService
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void readLastManeuver( final boolean bIsInteractive ) {
        mLogger.d("read Last Maneuver");
        
     
        mHandler.post( new Runnable() {
            public void run() {
                if( (null == mLastGuidanceInfo) || !promptIfNoNavigation( RequestType.LAST_DIRECTION )) {
                    return;
                }
                HashMap< String, String > streamToUse = mMixedAudioStream;
                String prompt = "";
                if(mLastGuidanceInfo.mIsRecalculation == true && !bIsInteractive) {
                	// when there was recalculation the NavService will prompt the new guiedens
                    return;
                } else if ((mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , ON_ROAD_FEATURE_ENABLED) == ON_ROAD_FEATURE_ENABLED) && !mOnRoad){
                    mLogger.d("read Last Maneuver Out of road");
                    prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_WENT_OFF_MAP);
                } else if(mLastGuidanceInfo.mSignType == NavigationIF.MCURouteInstructions.DEPART) {
                    prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVSTARTED);
                    mNeedToGiveStartDrivingPrompt = false;
                } else if( mLastGuidanceInfo != null && !mLastGuidanceInfo.mMessagePhon.isEmpty()) {
                    mNeedToGiveGuidance = false; // avoid hearing twice if this is from user request
                    prompt = StringService.expandString(mLastGuidanceInfo.mMessagePhon);
                    mNeedToGiveStartDrivingPrompt = false;
                    streamToUse = mMixedAudioStream;
                } else {
                    prompt = mStringReader.getExpandedStringById(StringReader.Strings.MSG_INFO_NOT_AVAILABLE);
                }
                HashMap< String, String > stream = mMixedAudioStream;
                if ( bIsInteractive ) {
                    // Interactive call is a one that was requested by the user (for example, read last manuever). 
                    // Non-interactive is used for the automatic periodic guidance. This prompt should be mixed when possible.
                    stream = mMixedAudioStream;
                }
                stopWtSndSync();
                mTts.speak( prompt, 
                    TextToSpeech.QUEUE_ADD, 
                    stream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                    TextToSpeech.TTS_RELEVANCE_ETERNAL, 
                    "", 
                    true, 
                    0, 
                    HmiDisplayService.NO_DISPLAY, 
                    HmiDisplayService.HmiIconId.VDP_HMI_ICON_NONE, 
                    true );
            }
        } );
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void getManeuverList(int requestedAction) {
        mLogger.d("getManeuverList");

        mHandler.post( new Runnable() {
            private int mRequestedAction;
            public void run() {
                if(!promptIfNoNavigation( RequestType.ALL_DIRECTIONS )) {
                    return;
                }
                // if we have reached the dest guidance msg there is no directions left in the list.
                if(checkAndPromptIfDest()) return;

				mNavigationIF.getManuverList(mRequestedAction); // startService
            }
            private Runnable init(int requestedAction) {
                mRequestedAction = requestedAction;
                return this;
            }
        }.init(requestedAction) );
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    

    public String getManeuverList(int requestedAction, int maxDirectionsPerString ,int currentPositionIndex) {
        mLogger.d("getManeuverList");

        	mReadMassegePosition = currentPositionIndex;
           return handleRouteMessages(mReadDirectionMessages,maxDirectionsPerString);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public boolean hasRoute() {
        return mNavigationManager.pullApprovedQueuedRoutes() != null;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void onInit(int status) {
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void checkForDrivingAfterStart() {
    	
    	// first set safety net timer in case other stop sound event will not happen, we dont  want
    	// the waiting sound to hang forever.
    	mTimerCancelCalcScreen = new Timer();
    	mTimerCancelCalcScreen.schedule(new TimerTask() {
    		
    		public void run() {
    			if(mClearCalcFromScreen && !mInNewRouteConfirmationScreen){
    				mLogger.v("safety net timer is activated for stopping waiting sound and clear screen");
    				stopWtSnd();
    				// need hard cancel because the prompt calculating route is sent with "remain on clear" definition
    				// and we want to make sure that it is cleared from screen.
    				mHmiDisplayService.cancelFromSRHard();
    				mClearCalcFromScreen = false;
    			}
    		}
    	}, CHECK_FOR_DRIVING_TIMEOUT + EXTRA_TIME_SAFETY_NET);

    	// first check if drive speed is below 5km/h
    	mTimerCheckForDrivingShort = new Timer();
    	mTimerCheckForDrivingShort.schedule(new TimerTask() {
    		public void run() {
    			if(mEventReader.getSpeedEventValue() < mSpeedLimitForDrivePrompt){

    				mLogger.d("speed is below allowed limit - prompt start driving");
    				cancelTimers();
    				stopWtSndSync();
    				promptStartDriving(true);
    				return;
    			}
    		}
    	}, CHECK_FOR_DRIVING_IMMEDIATE_TIMEOUT_FIRST);

    	final Location startingLocation;
        int checkForDrivingTimeout;
        // if current location is not null it means it was retrieved before traffic prompt and therefore the 
        // testing and announcement should be done using this field and without any timer.
        // otherwise need to measure the current location and wait until timeout.
        if(mCurrentLocationBeforeTraffic  != null){
            startingLocation = mCurrentLocationBeforeTraffic ;
            mCurrentLocationBeforeTraffic = null;
            // although we want the prompt be played immediately need to give a chance for guidance msg
            // to get into tts queue so defining 700 milliseconds grace time. also need to consider the first immediate timeout and add some more time to avoid race.
            checkForDrivingTimeout = CHECK_FOR_DRIVING_IMMEDIATE_TIMEOUT_SECOND;
        } else {
            startingLocation = getCurrentLocation();
            checkForDrivingTimeout = CHECK_FOR_DRIVING_TIMEOUT;
        }

        if( startingLocation == null ) {
            mLogger.e("Cannot check for driving, no start location");
            return;
        }

        cancelTimers();
        mTimerCheckForDriving = new Timer();
        mCurrentLocationBeforeTraffic = null;

        mTimerCheckForDriving.schedule(new TimerTask() {
            public void run() {
                if( mNavigationState != NavigationState.STARTED ) {
                    return;
                }
                Location currentLoc = getCurrentLocation();
                if( currentLoc == null ) {
                    mLogger.e("Cannot check for driving, no current location");
                    return;
                }
                mLogger.v("drivecheck: moved from " + startingLocation + " => " + currentLoc + " delta = " + startingLocation.distanceTo(currentLoc));
                
                if( startingLocation.distanceTo(currentLoc) < CHECK_FOR_DRIVING_DISTANCE ) {
                	stopWtSnd();
                	if(!getMuteStatus()){
                		mLogger.v("need to prompt start driving after distance check as distance moved is less then : " + CHECK_FOR_DRIVING_DISTANCE);
                		cancelTimers();
                		promptStartDriving(true);
                    }
                }
            }
        }, checkForDrivingTimeout);
        
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     *	Prompt user to start driving
     */
    private void promptStartDriving (boolean withTimeout){
    	mLogger.d("promptStartDriving - withTimeout : " + withTimeout);
    	stopWtSndSync();
    	if(isVrActive() || !RoadTrackDispatcher.ArePhonesIdle() || mTts.isSpeaking()){
    		mLogger.d("cannot prompt now - will try later");
    		mNeedToGiveStartDrivingPrompt = true;
    		mStartDrivingPromptWithTimeOut = withTimeout;
    		if(mTts.isSpeaking()){
    			// need to set this flag to true, otherwise when tts will end it wont invoke the checkIfNavigationActivityWaiting() function
    			// and the waiting prompt wont be played.
    			mNeedToGiveGuidance = true;
    		}
    		return;
    	}
    	if(mStartSilently != StartSilently.SILENT_NO_PROMPTS){
    		String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_NAVSTARTED);
    		String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_NAVSTARTED);
    		mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
    				TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, withTimeout, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
    	} else {
    		mLogger.d("not prompting start driving - silent start");
    	}
    	mNeedToGiveStartDrivingPrompt = false;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void handleIgnitionOn() {
    	
    	if(SystemProperties.get("sys.hibernating").equals("1"))
    	{
            mTimerCheckAfterHibernation = new Timer();

            mTimerCheckAfterHibernation.schedule(new TimerTask() {
                public void run() {
                	handleIgnitionOn();
                }
            },RECHECK_HIBERNATION_TIMEHOUT);
    	}
    	else
    	{
            int ignitionOnTimeout = Math.min(Math.max(mPconf.GetIntParam(PConfParameters.Navigation_IgnitionOnToStartNavigationTimer , 30) * 1000,IGNITION_ON_MIN_TIMEOUT),IGNITION_ON_MAX_TIMEOUT);
            mLogger.i("handleIgnitionOn: IGNITION ON");
            if(NavigationState.NONE ==  getState())
            {
            updateState(NavigationState.IGN_DELAY);
            }
            // wait x seconds after ignition ON before checking for an active route
            cancelTimers();
            mTimerCheckAfterIgnition = new Timer();

            mTimerCheckAfterIgnition.schedule(new TimerTask() {
                public void run() {
                	mLogger.d("handleIgnitionOn: after ignition run");
                	// need to check navigation is enabled
                	int isNavigationFeatureEnabled = mPconf.GetIntParam(PConfParameters.Navigation_NavigationFeatureStatus, SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING);
        			if((isNavigationFeatureEnabled & SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING) == SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING)
        			{
        				checkForActiveRouteAfterIgnitionOn();
        			}
                }
            },ignitionOnTimeout);
    	}

    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void handleIgnitionOffAndOpenedDoor() {
        mShutDownType = ShutDownType.NORMAL_IGNITION_OFF;
        // Debounce: ignore ignition off if was on for less than delay time
        if( mNavigationState != NavigationState.IGN_DELAY ) {
            mHmiDisplayService.cancelFromSRHard();
            // If user heard they arrived at destination then make route inactive
        	if(mArrivedAtDestinationNotified){
        		stopNavigation(mArrivedAtDestinationNotified);
        	// If user didn't hear we want to pass a true flag to leave the route in active state 
        	// so user will be suggested to restart upon ignition on.
        	} else {
        		stopNavigation(mArrivedAtDestinationNotified, true);
            }
        }

        // Save current location and bearing in order to start next navigation in the right direction
        LastBearingPosition.saveCurrentPostionAndBearing(mContext);

        cancelTimers();
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void checkForActiveRouteAfterIgnitionOn() {
        mLogger.d("checkForActiveRouteAfterIgnitionOn");
        // Check if there is a VR session or phones are not idle
        if( isVrActive() || !RoadTrackDispatcher.ArePhonesIdle() ) {
            mLogger.i("checkForActiveRouteAfterIgnitionOn is on hold until VR finished or phone is back to IDLE state");
            mWaitingNavigationActivity = WaitingNavigationActivity.CONTINUE_AFTER_IGNITION;
            return;
        }
        
        if( mNavigationState != NavigationState.IGN_DELAY ) {
            // State has changed while waiting for delay
            return;
        }

        // if no pending routes Delay state finished
        
        if( null == mNavigationManager.pullNewQueuedRoute())
        {
        	updateState(NavigationState.NONE);
        }
        
        RouteData newRouteData = mNavigationManager.pullNewQueuedRoute();
      	RouteData lastApprovedRoute = mNavigationManager.pullApprovedQueuedRoutes();
	      
        if (lastApprovedRoute != null && newRouteData !=null)
        {
          	mRouteDataFromBo = newRouteData;
          	mRouteDataFromBo.dataState = NavigationManager.RouteDataState.IN_CONFIRMATION ;
        	mNavigationManager.setRouteState((int)mRouteDataFromBo.id,NavigationManager.RouteDataState.IN_CONFIRMATION);
            confirmWithUserNavigationFromBo();
            return;
        }
	       
        // See if there is a route waiting
        RouteData routeData = mNavigationManager.pullLastQueuedRoutes();
    	              
        NavigationManager.RouteDataState dataState = (routeData == null) ? NavigationManager.RouteDataState.NOT_ACTIVE : routeData.dataState;

        if (routeData == null)
        {
        	// if routedata is null we set routestate as not active and exit 
        	 mLogger.e("checkForActiveRouteAfterIgnitionOn: pullLastQueuedRoutes() returned NULL");
        	return;
        }
        
        // checking only active and not active_paused because we dont want to propose the user restarting a route that he ended 
        // with the stop navigation command.
        if( dataState == NavigationManager.RouteDataState.ACTIVE || 
        		(dataState == NavigationManager.RouteDataState.APPROVED && 
        			routeData.routingAction == NavigationManager.RouteDataOrigin.USER.ordinal())) {
        	// if we are after system restart that wasn't clean we don't confirm with user whether to resume 
            // to last active navigation, we start it automatically unless the route was active_paused.
            // if it was active_paused we do nothing as user stopped it before the reset
            if((mShutDownType == ShutDownType.UNKNOWN) && (!mRTPwrSocMgr.wasCleanShutDown())){
                mLogger.i("starting navigation for new route without user confirm because system after reset");
                
                if( dataState == NavigationManager.RouteDataState.ACTIVE){
                    // We don't want all the announcements of new route so starting silently.
                    startNavigationSilently();
                }
                
            } else {
                proposeUserResumeNavigation();
            }
            mShutDownType = ShutDownType.UNKNOWN;
        
        } else if( dataState == NavigationManager.RouteDataState.NEW_ROUTE ){
            mLogger.i("confirm start navigation for new route");
            mRouteDataFromBo = routeData;

            if(NavigationManager.RouteDataState.NEW_ROUTE == mRouteDataFromBo.dataState)
            {
            	mRouteDataFromBo.dataState = NavigationManager.RouteDataState.IN_CONFIRMATION ;
            }
            mNavigationManager.setRouteState((int)mRouteDataFromBo.id,NavigationManager.RouteDataState.IN_CONFIRMATION);

            confirmWithUserNavigationFromBo();
        }else if( dataState == NavigationManager.RouteDataState.IN_CONFIRMATION ) {
            mLogger.i("confirm start navigation for new route");
            mRouteDataFromBo = routeData;
            confirmWithUserNavigationFromBo();
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void proposeUserResumeNavigation(){
        // Ask user if they want to continue navigation
        mLogger.i("notify user of active route");
        try {
            RouteData routeData = mNavigationManager.pullApprovedQueuedRoutes();
            if(routeData != null) {
                JSONObject json = new JSONObject();          
                
                json.put(VdpGatewayService.EXTEVT_PTT_STARTSTATE, APPNAVI_STATEID_CNC_MAIN_FROM_IGN);
                json.put(NAVCALL_NAME, routeData.routingText);
                json.put(NAVCALL_NAME_PHONETIC, routeData.routingTextPhonetic);
                VdpGatewayService.SendEvent(VdpGatewayService.EXTEVT_ID_PTT, json);
                startVdpPendingTimer();
            }
            else {
                mLogger.e("RouteData routeData is null");
            }
        } catch (JSONException e) {
            mLogger.e("exception - " + e.getMessage());
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void confirmWithUserNavigationFromBo(){
        // Ask user if they want to continue navigation
        mLogger.d("confirm with user navigation received from BO");
        mInNewRouteConfirmationScreen = true;
        try {
            // since navigation wasn't started yet through navigation manager the route record is not stored yet in the DB
            // so we take it from the mRouteDataFromBo field
            RouteData routeData = mRouteDataFromBo;
            if(routeData == null){
                mLogger.d("route data received from BO is null");
                return;
            }
            
            RouteData lastApprovedRoute = mNavigationManager.pullApprovedQueuedRoutes();
            boolean navigationActive = (lastApprovedRoute !=null && lastApprovedRoute.dataState == RouteDataState.ACTIVE);       
            
            String name = "";
            String name_for_display = "";
            // Name associated with destination
            if(routeData.routingTextPhonetic != null && !routeData.routingTextPhonetic.isEmpty()){
                String phoneticName = routeData.routingTextPhonetic;
                name = StringService.expandString(phoneticName);
                name_for_display = routeData.routingText;
            } else {
                name = (routeData.routingText == null)? "" : ("/address" + routeData.routingText + "/nm");
                name_for_display = (routeData.routingText == null)? "" : routeData.routingText;
            }
            
            // need to add the navigation state to give the user the correct prompt.
            NavigationState navigationState = getState();
            boolean navigationStatus = false;
            if((navigationState == NavigationState.PENDING_GPS) ||
               (navigationState == NavigationState.PENDING_ROUTE) ||
               (navigationState == NavigationState.PENDING_TRAFFIC) ||
               (navigationState == NavigationState.STARTED) ||
               (navigationState == NavigationState.FINISHED)|| navigationActive ){
                navigationStatus = true;
            }
            
            JSONObject json = new JSONObject();
            json.put(VdpGatewayService.EXTEVT_PTT_STARTSTATE, APPNAVI_STATEID_CNC_MAIN_FROM_BO);
            json.put(NAVCALL_ROUTE_DEST_TTS, name);
            json.put(NAVCALL_ROUTE_DEST_DISPLAY, name_for_display);
            json.put(NAVCALL_NAVIGATION_STATUS, navigationStatus);
            
            VdpGatewayService.SendEvent(VdpGatewayService.EXTEVT_ID_PTT, json); 
            startVdpPendingTimer();
        } catch (JSONException e) {
            mLogger.e(e.toString());
        }
    }
    
    /**
     * to compensate the time from the moment we initiating vdp session
     * and until we receive an intent indicating vr started we have a
     * Precaution timer that runs for SAFETY_TIMER_FOR_VDP_STARTED_INTENT.
     */
    private void startVdpPendingTimer(){
        mVrStartPendingTimer = new Timer();
        mVrStartPendingTimer.schedule(new TimerTask() {
            public void run() {
                if(mVrStartPendingTimer != null) {
                    mVrStartPendingTimer.cancel();
                }
                mVrStartPendingTimer = null;
            }
        }, SAFETY_TIMER_FOR_VDP_STARTED_INTENT);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void denyRouteReceivedFromBo(){
        mLogger.d("cancel route from bo after confirm from user");
        mNavigationManager.removeQueuedInConfirmationRoute();
        mWaitingNavigationActivity = WaitingNavigationActivity.NONE;
        mRouteDataFromBo = null;
        if(NavigationState.STARTED != mNavigationState )
        {
        	checkForActiveRouteAfterIgnitionOn();
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void startRouteReceivedFromBo(){
        mLogger.d("starting route from bo after confirm from user");
        if(mRouteDataFromBo != null){
        	
        	 if (!validateRouteCoordinates(mRouteDataFromBo))
             {  
        		 stopWtSnd();
        		 String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_EMPTY_ROUTE);
        		 String promptDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_EMPTY_ROUTE);
        		 mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                         TextToSpeech.TTS_RELEVANCE_ETERNAL, promptDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
        		       		 
        		 mNavigationManager.removeQueuedInConfirmationRoute();
        		 
        	     mWaitingNavigationActivity = WaitingNavigationActivity.NONE;
        		 mNavigationState = NavigationState.NONE;    	   
        		 mRouteDataFromBo = null;        		 
             	 return;
             }       	
    	
            mLastAnnouncedGPSStatus = GPS_OK;
            mRouteDataFromBo.dataState = RouteDataState.APPROVED;
            mNavigationManager.setRouteState((int)mRouteDataFromBo.id,RouteDataState.APPROVED);
            
            if(mPconf.GetIntParam(PConfParameters.Navigation_GmlanRadioNavigation ,0) == 1)
            {
            	mNavigationManager.startNavigationWithRoute(mRouteDataFromBo, true, null, false);
            } else {
            	handleRouteReceivedFromBo(mRouteDataFromBo, true);
            }
            
            mPndHandler.sendRoutingStatusToBO_V2BO(mPndHandler.NONE,PNDHandler.PND_SUCCESS);
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    private boolean validateRouteCoordinates(RouteData route)
    {    	
    	 
        double lat = Double.parseDouble(route.routingLatitude);
        double lon = Double.parseDouble(route.routingLongitude);
    	
    	return (lat >= -90 && lat <=90) && (lon >=-180 && lon <=180);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void  handleRouteReceivedFromBo(RouteData routeData, boolean afterConfirmation){
    	mLogger.i("received route from bo (afterConfermation: "+afterConfirmation+")");

        // See if ignition is on
        EventRepositoryReader er = (EventRepositoryReader)mContext.getSystemService(Context.EVENTREP_SYSTEM_SERVICE);
       

        if((afterConfirmation || !er.getIsInfotainmentAllowed()) 
        		&& (RouteDataState.NEW_ROUTE != routeData.dataState )
        		 && (RouteDataState.IN_CONFIRMATION != routeData.dataState )){
            mWaitingNavigationActivity = WaitingNavigationActivity.NONE;
            mInNewRouteConfirmationScreen = false;
            mNavigationManager.startNavigationWithRoute(routeData, true, null, true);
            mRouteDataFromBo = null;
        } else if( isVrActive() || !RoadTrackDispatcher.ArePhonesIdle() ) {
            mWaitingNavigationActivity = WaitingNavigationActivity.CONTINUE_AFTER_RECEIVED_ROUTE_FROM_BO;
            mLogger.i("on hold until VR finished or phone is back to IDLE state");
        }else if( mNavigationState == NavigationState.PENDING_ROUTE){
            mWaitingNavigationActivity = WaitingNavigationActivity.CONTINUE_BO_REQUEST_AFTER_CHANGE_FROM_PENDING_ROUTE;
            startPendingRouteCheckTimer();
            mLogger.i("on hold because previous route is still in pending state");
        }else if ( er.getIsInfotainmentAllowed()) {
        	mRouteDataFromBo = routeData;
            mWaitingNavigationActivity = WaitingNavigationActivity.NONE;
            startConfirmPendingRoute();
        }
    }
    
    private void startConfirmPendingRoute()
    {
    	
		RouteData routeData = mNavigationManager.pullInConfirmationQueuedRoute();

		if ( null == routeData ) {
            mRouteDataFromBo.dataState = RouteDataState.IN_CONFIRMATION;
            mNavigationManager.setRouteState((int)mRouteDataFromBo.id,RouteDataState.IN_CONFIRMATION);

            confirmWithUserNavigationFromBo();
            return;
		}
    	
        mTimerCheckIfNonConfirmRouteExist = new Timer();
        mTimerCheckIfNonConfirmRouteExist.schedule(new TimerTask() {
			
			@Override
			public void run() {
				mTimerCheckIfNonConfirmRouteExist.cancel();
				mTimerCheckIfNonConfirmRouteExist = null;
				startConfirmPendingRoute();	          
			}
		}, CHECK_NO_NON_CONFIRM_IN_DATABASE_TIMER_TIMEOUT);
    }

    private void startPendingRouteCheckTimer(){

       
        mTimerCheckIfPendingRoute = new Timer();
        mTimerCheckIfPendingRoute.schedule(new TimerTask() {
            
            @Override
            public void run() {
                
                checkIfNavigationActivityWaiting();
                if(mWaitingNavigationActivity == WaitingNavigationActivity.NONE){
                    if(mTimerCheckIfPendingRoute != null) {
                        mTimerCheckIfPendingRoute.cancel();
                    }
                    mTimerCheckIfPendingRoute = null;
                }
            }
        }, CHECK_ROUTE_PENDING_TIMER_FOR_FOR_START_USER_REQUEST);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public GuidanceInfo GetLastGuidanceInfo(){
        return mLastGuidanceInfo;
    }
    
    

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public String replaceCppPhonetics(String phonToReplace){
        if(phonToReplace.contains(PHONETIC_START_CPP)) {
            phonToReplace = phonToReplace.replace(PHONETIC_START_CPP, PHONETIC_START_JAVA);
        }
        if(phonToReplace.contains(PHONETIC_END_CPP)) {
            phonToReplace = phonToReplace.replace(PHONETIC_END_CPP, PHONETIC_END_JAVA);
        }
        return phonToReplace;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public void notifyEvent(NotifyEventType eventType,boolean onFlag) {
    	switch(eventType) { 
        case GPS_RECEIVE_FROM_TRACKER:
            mLogger.d("GPS_RECEIVE_FROM_TRACKER: " + onFlag);
            // When GPS returns, remember to check for navigation later if in call or VR
            if( onFlag && (isVrActive() || !RoadTrackDispatcher.ArePhonesIdle()) ) {
                mLogger.i("on hold until VR finished or phone is back to IDLE state");
                if(mWaitingNavigationActivity == WaitingNavigationActivity.NONE) {
                    mWaitingNavigationActivity = WaitingNavigationActivity.CONTINUE_AFTER_GPS_RECEIVED;
                }
            } else if( onFlag && (mNavigationState == NavigationState.PENDING_GPS) ) {
                // Start navigation if waiting for GPS
                if(mStartSilently == StartSilently.SILENT_NO_PROMPTS){
                    // if mStartSilently is true it means we are waiting for gps as part of a silent start 
                    startNavigationSilently(NavigationOrigin.GPS_TRACKER );
                } else {
                    startNavigation(NavigationOrigin.GPS_TRACKER);
                }
            }
            else if (mNavigationState == NavigationState.STARTED || mNavigationState == NavigationState.PENDING_GPS)
            {
            	checkIfShouldAnnounceGPSStatus(onFlag ? GPS_OK : GPS_LOST, CallOrigin.CALLED_FROM_NOTIFY_EVENT);
            }
            //update tbt notification mgr
            Intent intent = new Intent(NavigationIF.NAVIGATION_DBNC_GPS_STATUS_INTENT);
			intent.putExtra("IsAvailableGPSNow", mIsAvailableGPS);
			notifyTBTNotificationMgr(mContext, intent);
            break;
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // check if need to prompt the gps status update and in any case stop cancel the timer because if this 
    // function was called it means the timer elapsed.
    private void checkIfShouldAnnounceGPSStatus(int gpsStatus, CallOrigin callOrigin){
        boolean shouldAnnounceGpsStatus = true;
        // need to verify the state to avoid announcing gps status when the user is not in a navigation process  
        if((mNavigationState == NavigationState.NONE) || 
           (mNavigationState == NavigationState.IGN_DELAY)){
            shouldAnnounceGpsStatus = false;
        }

        // need to change the prompt according to the calling function.
        int promptForLost = StringReader.Strings.APPNAVI_PROMPTID_MSG_GPS_LOST;
        // if mStartSilently is true it means we restarting navigation that was already
        // active before the reset, therefore the prompt shouldn't state "navigation will start ..."
        if((callOrigin == CallOrigin.CALLED_FROM_START_NAVI) && (mStartSilently != StartSilently.SILENT_NO_PROMPTS)){
            promptForLost = StringReader.Strings.APPNAVI_PROMPTID_NAVINOGPS;
        }

        switch(gpsStatus){
        case GPS_LOST:
            mLogger.i("GPS_LOST");
            if((shouldAnnounceGpsStatus) && (mLastAnnouncedGPSStatus != GPS_LOST)) {
                stopWtSndSync();
            	String prompt = mStringReader.getExpandedStringById(promptForLost);
                String promptForDisplay = mStringReader.getStringByIdForDisplay(promptForLost);
                if(!mHmiDisplayService.isDisplayingSelection()){
                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                            TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                } else {
                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                            TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                }
                mLastAnnouncedGPSStatus = GPS_LOST;
            }
            mIsAvailableGPS = false;
            break;
        case GPS_OK:
            mLogger.i("GPS_OK");
            stopWtSndSync();
            if((shouldAnnounceGpsStatus) && (mLastAnnouncedGPSStatus != GPS_OK)){

            	String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_MSG_GPS_AVAILABLE);
            	String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_MSG_GPS_AVAILABLE);
            	// This tts shouldn't be played after 30 seconds because there might be another "tts lost" announcement and this will cancel
            	// all the debounce mechanism logic.
            	mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
            			30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);

                mLastAnnouncedGPSStatus = GPS_OK;
                //whan the gps resumed and the car is out of road tts will remind the driver he is out of road
                mLogger.i("GPS Avelebel + mOnRoad:" + mOnRoad + "Mutestatus:"+ getMuteStatus() ); 
                if((mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , ON_ROAD_FEATURE_ENABLED) == ON_ROAD_FEATURE_ENABLED) && !mOnRoad && !getMuteStatus()){
                    int msgId = StringReader.Strings.APPNAVI_PROMPTID_WENT_OFF_MAP;
                    String msg = mStringReader.getExpandedStringById(msgId);
                    promptForDisplay = mStringReader.getStringByIdForDisplay(msgId);
                    //13 seconds is the time that portuguese and spanish will finish playing TTS
                    mTts.speak( msg, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
                                TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, 13, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                }
            }
            mIsAvailableGPS = true;
            break;
        default:
            break;
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void checkIfNavigationActivityWaiting(){
        switch(mWaitingNavigationActivity){
        case CONTINUE_AFTER_IGNITION:
        	// need to check navigation is enabled
        	int isNavigationFeatureEnabled = mPconf.GetIntParam(PConfParameters.Navigation_NavigationFeatureStatus, SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING);
			if((isNavigationFeatureEnabled & SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING) == SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING)
			{
				checkForActiveRouteAfterIgnitionOn();                   
			}
            break;
        case CONTINUE_AFTER_RECEIVED_ROUTE_FROM_BO:
        case CONTINUE_BO_REQUEST_AFTER_CHANGE_FROM_PENDING_ROUTE:
            //ask user if its ok to start the new navigation
        	mLogger.w(" Continu after BO");
                RouteData routedata = mNavigationManager.pullNewQueuedRoute();
            if(routedata != null){
            	mLogger.w(" Continu after BO : handle route");
                handleRouteReceivedFromBo(routedata, false);
            }
            break;
        case CONTINUE_AFTER_GPS_RECEIVED:
            // Make sure that while waiting, GPS signal didn't disappear again.
            if(mGpsTracker != null){
                if(mGpsTracker.isGpsAvailable()){
                    notifyEvent(NotifyEventType.GPS_RECEIVE_FROM_TRACKER, true);
                }
            }
            break;    
        default:
            break;
        }

        if( mNeedToGiveGuidance && !mTts.isSpeaking() ) {
            mNeedToGiveGuidance = false;
            if( (mNavigationState == NavigationState.STARTED) &&
                !getMuteStatus() &&
                (mLastGuidanceInfo != null && !mLastGuidanceInfo.mMessagePhon.isEmpty()) &&
                (mLastGuidanceInfo.mDistance > 0.0) ) {
                mLogger.i("Giving last guidance because missed tts while in VR/TTS");
                readLastManeuver( true );
            }
        }
        
        if((mNavigationState == NavigationState.STARTED) && (mNeedToGiveStartDrivingPrompt)){
        	promptStartDriving(mStartDrivingPromptWithTimeOut);          
            
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private void shutdownNavigationEngine()
    {
        mLogger.d( "Broadcast service stop request" );
		mNavigationIF.stop();

        //just because Oren asked......this will end tbt screen.
		if(NavigationState.PENDING_GPS != mNavigationState)
		{
			updateState(NavigationState.NONE);
		}

        // Remove tts intents from handlers' queue.
        mHandler.removeMessages(TTS_INTENT_MESSAGE_OBJECT);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private JSONObject UnpackIntent(Intent intent, String field, String[] keys) {
        JSONObject result = null;

        try {
            result = new JSONObject(intent.getStringExtra(field));
            mLogger.d("unpack json: " + result);

            // Insert empty values if expected field is not there
            for( String k : keys ) {
                if( !result.has(k) ) {
                    mLogger.w("Missing expected field: " + k);
                    result.put(k, "");
                }
            }
        } catch( Exception e ) {
            mLogger.e("Failed to get json from intent: ", e);
        }

        return result;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class handlePeriodicPromptObject implements Runnable {

        public void run() {
            mLogger.v("periodic deltaT = " + (SystemClock.uptimeMillis() - mLastGuidancePromptTime));
            if( mNavigationState != NavigationState.STARTED ) {
                    mLogger.d("periodic: no longer running");
                    return;
            }

            // See if we need to make a periodic prompt
            if( (mLastGuidanceInfo != null) &&
                (mPeriodicPromptTime != 0 ) &&
                (SystemClock.uptimeMillis() - mLastGuidancePromptTime >= mPeriodicPromptTime) &&
                (mLastGuidanceInfo.mDistance >= mPeriodicPromptMinDistance) ) {
    
                mLogger.d("Initiating periodic message");
                if(!getMuteStatus()) {
                    readLastManeuver( false );
                }
                mLastGuidancePromptTime = SystemClock.uptimeMillis();
            }

            // Schedule next periodic prompt
            handlePeriodicPromptObject obj = new handlePeriodicPromptObject();
            long delay = mPeriodicPromptTime - (SystemClock.uptimeMillis() - mLastGuidancePromptTime);
            if( delay <= 0 ) {
                delay = mPeriodicPromptTime;
            }
            mHandler.postDelayed(obj, delay);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class handleGuidanceInfoObject implements Runnable {

        private final Navigation.GuidanceInfo mInfo;

        public handleGuidanceInfoObject(Navigation.GuidanceInfo info) {
            this.mInfo = info;
        }

        public void run() {
            if(mNavigationState != NavigationState.STARTED ) {
                return;
            }

//
//            if( mInfo.mSignType == NavigationIF.MCURouteInstructions.ROUNDABOUT_UNKNOWN_EXIT) {
//                // We do not give guidance within roundabout, so do not remember these guidances
//                mLogger.v("discarding guidance info for exit in roundabout");
//                return;
//            }

            // Test if a new maneuver has arrived
            if(    mInfo.mSignType != mLastGuidanceInfo.mSignType
                || !mInfo.mStreetName.equals(mLastGuidanceInfo.mStreetName) ) {
                mLogger.v("detected new maneuver; flag is " + mInfo.mNewManeuver);
            }

            mLastGuidanceInfo = mInfo;

            mLogger.v("handling guidance info: distance = " + mLastGuidanceInfo.mDistance + 
                      ", signtype = " + mLastGuidanceInfo.mSignType.toString() + 
                      ", street = [" + mLastGuidanceInfo.mStreetName +
                      "], message = [" + mLastGuidanceInfo.mMessage + "]" +
                      "], messagePhon = [" + mLastGuidanceInfo.mMessagePhon + "]" +
                      ", distance to destination = " + mLastGuidanceInfo.mDistanceToDestination +
                      ", time to destination = " + mLastGuidanceInfo.mTimeToDestination + 
                      ", IsNewManeuver? = " + mLastGuidanceInfo.mNewManeuver +
                      ", Angle = " + mLastGuidanceInfo.mAngle );
            
            if(mWaitingForDestAndTime && mLastGuidanceInfo.mDistanceToDestination != -1.0) {
                cancelTimers();
                mNeedToGiveStartDrivingPrompt = false;
            	if(mStartSilently != StartSilently.SILENT_NO_PROMPTS){
                	announceDetailsOfRoute(false, true);
                }
                mWaitingForDestAndTime = false;
                mClearCalcFromScreen = false;
            } else if( mLastGuidanceInfo.mSignType != NavigationIF.MCURouteInstructions.DEPART) {
            	mLogger.v("Got maneuver sign, cancel start driving timer");
            	if(mTimerCheckForDrivingShort != null) {
            		mTimerCheckForDrivingShort.cancel();
                }
            	if(mTimerCheckForDriving != null) {
                    mTimerCheckForDriving.cancel();
                }
            }

        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class handleRouteEmptyObject implements Runnable {

        private final int status;

        public handleRouteEmptyObject(int status) {
            this.status = status;
        }

        public void run() {

            if(mHandler.hasMessages(ROUTE_FOUND_OBJECT)) {
                mLogger.i("ignoring intent for ROUTE_EMPTY because a ROUTE_FOUND is waiting in the queue");
                return;
            }

            String statustxt;
            boolean createRouteSucceeded = false;
            int textid = StringReader.Strings.APPNAVI_PROMPTID_EMPTY_ROUTE;
            switch( status ) {
                case ROUTE_STATUS_NO_CONFIG_FOUND:          statustxt = "NO_CONFIG_FOUND"; break;
                case ROUTE_STATUS_STOPPED:                  statustxt = "STOPPED"; break;
                case ROUTE_STATUS_NO_START_FOUND:  
                	mLogger.i("handling intent ROUTE_EMPTY: status = NO_START_FOUND");
                	if(!mPlayedStartNfnd) {
                		stopWtSndSync();
                        String prompt = 
                        		mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_REROUTE_MOVE_TO_ROAD);
                        String promptForDisplay = 
                        		mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_REROUTE_MOVE_TO_ROAD);
                        // This tts should be played immediately.
                        if(!mHmiDisplayService.isDisplayingSelection()){
                            mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                                    TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                        } else {
                            mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                                    TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                        }

                		mPlayedStartNfnd=true;
                	}
                	
                	mStartNfndRestartTimer = new Timer();                	
                	mStartNfndRestartTimer.schedule(new TimerTask() {
						
						@Override
						public void run() {
							if(mPlayedStartNfnd == true) {
								startNavigationSilently();
								mStartNfndRestartTimer=null;
							}
						}
					}, mStartNfndRestartTimeout);
                	
                	                	
                	return;                	
                	// statustxt = "NO_START_FOUND"; break;
                case ROUTE_STATUS_EQUAL_START_DESTINATION:  statustxt = "EQUAL_START_DESTINATION"; break;
                case ROUTE_STATUS_NO_CONNECTION_FOUND:      statustxt = "NO_CONNECTION_FOUND"; break;
                case ROUTE_STATUS_NO_DESTINATION_FOUND:
                    statustxt = "NO_DESTINATION_FOUND";
                    break;
                case ROUTE_STATUS_RUNNING:
                    statustxt = "RUNNING";
                    createRouteSucceeded = true;
                    break;
                case ROUTE_STATUS_FINISHED_WITH_RESULT:
                    statustxt = "FINISHED_WITH_RESULT";
                    createRouteSucceeded = true; // this means route was created
                    break;
                case ROUTE_STATUS_ABORTED:
                    statustxt = "ABORTED";
                    createRouteSucceeded = true; // this means previous route was aborted by a new one
                    break;
                case ROUTE_STATUS_EMPTY_SUCCEDED:
                    statustxt = "onRoutingFinished null route";
                    createRouteSucceeded = true; 
                    stopWtSnd();
                    break;
                case ROUTE_STATUS_ROUTE_NOT_FOUND:
                    statustxt = "route not found";
                    createRouteSucceeded = false; 
                    stopWtSnd();
                    HashMap<String,String> audioStream = mUnmixedAudioStream;
                    String prompt = mStringReader.getExpandedStringById(textid);
                    String promptForDisplay = mStringReader.getStringByIdForDisplay(textid);
                    // This tts should be played immediately.
                    if( mShouldMix || ( mRepositoryReader.isExternalBTCallActive() ) ) {
                    	audioStream = mMixedAudioStream;
                    }
                    if(!mHmiDisplayService.isDisplayingSelection()){
                        mTts.speak(prompt, TextToSpeech.QUEUE_ADD, audioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                                TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                    } else {
                        mTts.speak(prompt, TextToSpeech.QUEUE_ADD, audioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                                TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                    }
                	break;
                default:
                    statustxt = "unknown: " + status;
                    createRouteSucceeded = true; // don't know for sure
                    break;
            }
            mLogger.i("handling intent ROUTE_EMPTY: status = " + statustxt);
            boolean terminateNavigation = false;

            // If route creation failed before navigation started, notify user and stop navigation engine
            if( !createRouteSucceeded) {
                if( mNavigationState == NavigationState.PENDING_ROUTE ) {
                    // Initial route cannot be created, cancel navigation
                    mLogger.w("Route calculation failed at start, stop navigation");
                    terminateNavigation = true;
                } else if( mNavigationState == NavigationState.STARTED ) {
                    switch( mRerouteState ) {
                        case NONE:
                            // Reroute fail during navigation, will retry in 10 seconds
                            mLogger.w("Reroute fail during navigation");
                            mRerouteTime = SystemClock.uptimeMillis();
                            mRerouteState = RerouteState.INPROGRESS;
                        break;
                        case INPROGRESS:
                        	stopWtSndSync();
                            // If too much time goes by while recalculating, tell user to move vehicle (once)
                            if( SystemClock.uptimeMillis() - mRerouteTime >= mRecalcFailPromptTime ) {
                                mLogger.w("Reroute still failing, tell user to move");
                                
                                String prompt = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_REROUTE_MOVE_TO_ROAD);
                                String promptForDisplay = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_REROUTE_MOVE_TO_ROAD);
                                
                                // This tts should be played immediately.
                                if(!mHmiDisplayService.isDisplayingSelection()){
                                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL,
                                            TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                                } else {
                                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL,
                                            TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                                }
                                mRerouteState = RerouteState.NOTIFIED;
                            }
                        break;
                        case NOTIFIED:
                            // If too much time goes by after notification, stop navigation
                            if( (mRecalcFailTimeout > 0) && (SystemClock.uptimeMillis() - mRerouteTime >= mRecalcFailTimeout) ) {
                                mLogger.w("Reroute failing too long, stop navigation");
                                terminateNavigation = true;
                                mRerouteState = RerouteState.NONE;
                            }
                        break;
                    }
                } else {
                    mLogger.e("Empty route in unexpected state: " + mNavigationState);
                }
            } else {
                // Reset reroute state
                mRerouteState = RerouteState.NONE;
            }

            if( terminateNavigation ) {
                stopWtSndSync();
                mWaitingForDestAndTime = false;
                mClearCalcFromScreen = false;
                mIsFirstGuidance = false;
                mNavigationManager.removeApprovedRoute();
                String prompt = mStringReader.getExpandedStringById(textid);
                String promptForDisplay = mStringReader.getStringByIdForDisplay(textid);
                HashMap<String,String> audioStream = mUnmixedAudioStream;
                
                // This tts should be played immediately.
                if( mShouldMix || ( mRepositoryReader.isExternalBTCallActive() ) ) {
                	audioStream = mMixedAudioStream;
                }
                if(!mHmiDisplayService.isDisplayingSelection()){
                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, audioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                            TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
                } else {
                    mTts.speak(prompt, TextToSpeech.QUEUE_ADD, audioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
                            TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                }
                mShouldMix = false;
                shutdownNavigationEngine();
                updateState(NavigationState.NONE);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class handleRouteFoundObject implements Runnable {

        public void run() { 
            mLogger.i("handling intent ROUTE_FOUND");
            RouteData routeData = mNavigationManager.pullApprovedQueuedRoutes();
            if( routeData == null ) {
                mLogger.e("ROUTE_FOUND but no route in db, stop navigation; state = " + mNavigationState);
                stopNavigation();
                return;
            }

            if( mNavigationState != NavigationState.FINISHED ) {
                updateState(NavigationState.STARTED);
                if(mShouldStartTimer){
                	mElapsedTimeFromStart = SystemClock.uptimeMillis();
                	mShouldStartTimer = false;
                }
            }
            // reset reroute state in case was failing
            mRerouteState = RerouteState.NONE;
            
            routeData = mNavigationManager.pullNewQueuedRoute();
            if(null != routeData)
            {
            	handleRouteReceivedFromBo(routeData, false);
            }
            		
            		
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * sets pnd handler instance
     */
    public void setPndHandler(PNDHandler pndHandler) {
        mPndHandler = pndHandler;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * gets pnd handler instance
     */
    public PNDHandler getPndHandler() {
        return mPndHandler;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class handleRouteFinishedObject implements Runnable {

        public void run() {
            mLogger.i("handling intent ROUTE_FINISHED");
            mAllowNotifyTBTNotificationMgr = true;
        	updateState(NavigationState.FINISHED);
            mArrivedAtDestinationNotified = true;
 
            // Assume that driver does not want any more navigation
            stopNavigation(true);
            
            if(!mHmiDisplayService.isDisplayingSelection()){
                mHmiDisplayService.cancelFromSRHard();
            }
            
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    
  

    private void stringReplaceAll( StringBuilder builder, String src, String dest ) {
	   	int index = builder.indexOf(src);
	    while (index != -1)
	    {
	        builder.replace(index, index + src.length(), dest);
	        index += dest.length(); // Move to the end of the replacement
	        index = builder.indexOf(src, index);
	    }
    }
    
    public int getCurrentManueverIndex()
    {
    	return mLastGuidanceInfo.mNextManuever-1;
    }

    public int getDirectionMessegesListSize()
    {
    	return mReadDirectionMessages.size();
    }
    private String handleRouteMessages(ArrayList<String> messages,int maxDirectionPerString) {
            
        	 StringBuilder mReplaceBuilder = new StringBuilder();

            // if received list of messages are empty it means there are no route nodes so we are in the middle of
            // recalculation process.
        	 if(null == messages)
        	 {
        		 mLogger.e(" route instructions are null !" );
        		 return null;
        	 }
            if( messages.isEmpty()){
                promptUserOfRecalculation();
                return null;
            }

            // Concatenating all the instructions into one string so it will be possible to interrupt the tts with one button press.
            StringBuilder concatOfAllInstructions = new StringBuilder();
            boolean passedFirstIn = false;
            String instructionIn             = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_INSTRUCTION_IN);
            String instructionThenAfter      = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_INSTRUCTION_THEN_AFTER);
            String instructionAndThen        = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_INSTRUCTION_AND_THEN);
            String instructionA              = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_INSTRUCTION_A);
            String instructionWillReachDest  = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_INSTRUCTION_WILL_REACH_DEST);
            String instructionAndThenIn      = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_INSTRUCTION_ANDTHENIN);

            int msgIndex = 0;
            int firstMassegeIndex = mReadMassegePosition;
                      
            // update the current massege in the instructions list.
            if( firstMassegeIndex == mLastGuidanceInfo.mNextManuever-1 )
            {          	
            	messages.remove(firstMassegeIndex);           	
            	messages.add(firstMassegeIndex,mLastGuidanceInfo.mMessagePhon);
            }
            
            
            // If the on road feature is on.
            if ( (mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , 1) == 1)  &&
            		(firstMassegeIndex == mLastGuidanceInfo.mNextManuever-1)	){
                if (!mOnRoad){
                    mLogger.d( "readDirections out of road");
                    concatOfAllInstructions.append(SEPERATOR_BETWEEN_DIRECTIONS);
                    //play drive to road
                    concatOfAllInstructions.append(mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_OFF_MAP_FIRST_READ_DIRECTION) + ((mLastGuidanceInfo != null && mLastGuidanceInfo.mCurrentStreetName != null) ? StringService.expandString("/streetname"+ mLastGuidanceInfo.mCurrentStreetName+"/nm"): ""));
                    concatOfAllInstructions.append(SEPERATOR_BETWEEN_DIRECTIONS);
                    concatOfAllInstructions.append(StringService.expandString(PAUSE_CHAR));
                    concatOfAllInstructions.append(SEPERATOR_BETWEEN_DIRECTIONS);
                }
            }
            for(  ; (mReadMassegePosition < (firstMassegeIndex + maxDirectionPerString )) && (mReadMassegePosition < messages.size()) ; mReadMassegePosition++) {

            	String msg = messages.get(mReadMassegePosition);
                String[] msgParts = msg.split("\\|");
                msgParts[0] = replaceCppPhonetics(msgParts[0]);
                // reached last message - this is the destination maneuver
                if(msgIndex == (messages.size() - 1)){
                    String[] lastMsgParts = msgParts[0].split(" ");
                    String distancePart = "";
                    double distanceDouble = 0;
                    // looking for the distance phrase "XXm"
                    for(String lastMsgPart : lastMsgParts ){
                        if(containsDigit(lastMsgPart)){
                            distancePart = lastMsgPart;
                            try {
                                distanceDouble = Double.parseDouble(distancePart);
                            } catch (NumberFormatException e) {
                                mLogger.e("distanceDouble is not Double.", e);
                            }
                            break;
                        }
                    }
                    // may be a msg including distance and maybe not
                    if(!distancePart.isEmpty() && roundDistance((long)distanceDouble) >0 ){
                        msgParts[0] = instructionAndThenIn + SPACE_SEPERATOR + metersToDistanceString((long)distanceDouble) + SPACE_SEPERATOR + instructionWillReachDest;
                    } else {
                        msgParts[0] = instructionAndThen + SPACE_SEPERATOR + instructionWillReachDest;
                    }

                } else {
                    // replace all "In " with "Then after" except for the first occurrence
                    String[] instructionsBrokenToWords = msgParts[0].split(SPACE_SEPERATOR);
                    msgParts[0] = "";
                    int msgWordIndex = 0;
                    for(String word : instructionsBrokenToWords){
                        if((word.contains(instructionIn)) && 
                           (word.length() == instructionIn.length())){
                            if(!passedFirstIn){
                                passedFirstIn = true;
                                msgParts[0] = msgParts[0] + word + SPACE_SEPERATOR;
                                continue;
                            } 
                        // verify also that the word A is the first word in the msg
                        } else if((word.contains(instructionA)) && 
                                   (word.length() == instructionA.length()) &&
                                   (msgWordIndex == 0)){
                            word = word.replace(instructionA, instructionThenAfter);
                        } 
                        msgParts[0] = msgParts[0] + word + SPACE_SEPERATOR; 
                        msgWordIndex++;
                    }
                }
                
                concatOfAllInstructions.append(msgParts[0]);
                concatOfAllInstructions.append(SEPERATOR_BETWEEN_DIRECTIONS);
                concatOfAllInstructions.append(StringService.expandString(PAUSE_CHAR));
                concatOfAllInstructions.append(SEPERATOR_BETWEEN_DIRECTIONS);
                msgIndex++;
            }
            
            mLogger.i("ROUTE_MESSAGES after replacements : "+mReadMassegePosition+" :" + concatOfAllInstructions.toString());
          return  concatOfAllInstructions.toString();
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    public boolean containsDigit(String s){  
        boolean containsDigit = false;

        if(s != null && !s.isEmpty()){
            for(char c : s.toCharArray()){
                if(containsDigit = Character.isDigit(c)){
                    break;
                }
            }
        }

        return containsDigit;
    }

    private void promptUserOfRecalculation(){
        String announceUser = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_RECALCULATING_ROUTE) +
                SPACE_SEPERATOR + mStringReader.getExpandedStringById(StringReader.Strings.TTS_PLEASE_WAIT); 
        String displayToUser = mStringReader.getStringByIdForDisplay(StringReader.Strings.APPNAVI_PROMPTID_RECALCULATING_ROUTE) + 
        		SPACE_SEPERATOR + mStringReader.getStringByIdForDisplay(StringReader.Strings.TTS_PLEASE_WAIT); 
        notifyUserOfRecalculation(announceUser, displayToUser);
    }

    private void notifyUserOfRecalculation(String stringForTts, String stringForDisplay){
    	stopWtSndSync();
       	mTts.speak(stringForTts, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL,
        			TextToSpeech.TTS_RELEVANCE_ETERNAL, stringForDisplay, true, TIME_PERIOD_FOR_RECALCULATION_MESSAGE, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
    }

    private void notifyUserOfRecalculation(){
      	// play recalc sound instead of the tts.
       	if(!isVrActive() && RoadTrackDispatcher.ArePhonesIdle()) {
       		VdpGatewayService.PlayWavAsync(VdpGatewayService.WAV_PATH + RECALC_WAV, AudioManager.STREAM_NOTIFICATION );
       	}
    }
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class handleTtsMessagesObject implements Runnable {
        private Intent intent;
        public handleTtsMessagesObject(Intent intent) {
            this.intent = intent;
        }

        public void run() {
            stopWtSndSync();

            mLogger.d("handling intent TTS INTENT MESSAGE");

            Bundle bundle = intent.getExtras();
            // need to see maybe need to stop GPS debounce because if we received the intent
            // there was a GPS fix received.
            // for all other possible speak types they dont rely on gps but come from last guidance
            // and other. so in other cases then mentioned down here we dont want to stop debounce
            if ( bundle == null ) {
                mLogger.e("Bundle extras could not be obtained");
                return;
            }

            SPEAK_INFO speakInfo = SPEAK_INFO.values()[bundle.getInt(INTENT_EXTRA_SPEAK_INFO)];
            boolean isManeuver = isManeuverPrompt(speakInfo);

            String msg = bundle.getString(INTENT_EXTRA_TTS);
            
            if ( null == msg )
            {
            	mLogger.e("abort prompt - msg is null");
            	return;
            }

            switch(speakInfo) {
                case MANEUVER_FAR:
                case MANEUVER_SOON:
                case MANEUVER_FIRST_PRIOR:
                case MANEUVER_PRIOR:
                    // Do not allow soon and far prompts to repeat too often
                    if( SystemClock.uptimeMillis() - mLastGuidancePromptTime < mMinPromptInterval ) {
                        mLogger.i("abort prompt");
                        return;
                    }
                    msg = StringService.expandString( msg );
                    break;
                case MANEUVER_NEAR:
                	// adding an act soon to notify the user there is an action he will have to make soon.
                	msg = StringService.expandString("/act  " + msg); 
                	break;
                case DESTINATION_REACHED:
  
                	break;
            }

            // need to extract all the rest of the intent extras for the tts. 
            mLogger.v("tts text from navigation service " + speakInfo + " = [" + msg + "]" + " isManeuver = " + isManeuver);

            if( isManeuver ) {
            	mDisplayTbt = true;

                if(getMuteStatus()) {
                   mLogger.i(" MUTE IS ON - NO TTS MESSAGES EXPECTED");
                    return;
                }

                if( isVrActive() || (mTts.isSpeaking()) && ((speakInfo != SPEAK_INFO.MANEUVER_NEAR) && (speakInfo != SPEAK_INFO.MANEUVER_FIRST_PRIOR))) {
                    mLogger.d("tts canceled because in VR/TTS");
                    mLastGuidancePrompt = msg;
                    mNeedToGiveGuidance = true;
                    return;
                } else if(!getMuteStatus()) {
                	// need to enlarge the time the message stays on the screen so adding breaks (/p) in case the message is too short.
                	// this is done to prevent the first tbt message look as flickering before the route info message comes.
                	if(mClearCalcFromScreen && !mInNewRouteConfirmationScreen){
                		cancelTimers();
                		mNeedToGiveStartDrivingPrompt = false;
                		mLogger.v("received first guidance tts - stopping waiting sound and clearing screen.");

        				// need hard cancel because the prompt calculating route is sent with "remain on clear" definition
        				// and we want to make sure that it is cleared from screen.
        				mHmiDisplayService.cancelFromSRHard();
        				mClearCalcFromScreen = false;
                    }

                	if(mIsFirstGuidance){
                		mIsFirstGuidance = false;
                		if(msg.length() < MAX_NUMBER_OF_CHARS_FOR_ADDING_PAUSE){
                			msg += StringService.expandString(PAUSE_CHAR + PAUSE_CHAR);
                		}
                	}

                    // if a near maneuver to be said, cut any previous maneuvers in progress to  make sure the user understand 
                    // he needs to make the turn right away.
                    if(speakInfo == SPEAK_INFO.MANEUVER_NEAR) { 
                        mTts.stop();
                    }
                    playGuidanceTTS( msg );
                    TBTNotificationMgr.sendTbtIcon(MessageCodes.MESSAGE_CODE_GMLAN_TBT_MANEUVER_PROMPT);
                }

                mLastGuidancePromptTime = SystemClock.uptimeMillis();
  
                // Reset recalc prompt timer, we have spoken a maneuver
                mRecalcPromptTime = 0;

            } else {
                // TODO check what this tts is??? (Keshet)
                if(!getMuteStatus() || (SPEAK_INFO.DESTINATION_REACHED == speakInfo)){
                	TextToSpeech.TtsInterruptSetting interrupt = TextToSpeech.TtsInterruptSetting.DONT_INTERUPT;

                	if(SPEAK_INFO.DESTINATION_REACHED == speakInfo)
                	{
                		interrupt = TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR;
                	}

                    mTts.speak(msg, TextToSpeech.QUEUE_ADD, mMixedAudioStream,interrupt,
                            TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                }
            }
            
        }
    }

    //
    // This function plays guidance instruction as TTS.
    // the audio is mixed to the radio (in supported configuraitons).
    //
    private void playGuidanceTTS( String textForSpeach ) {
    	stopWtSndSync();
        mTts.speak( textForSpeach, 
                    TextToSpeech.QUEUE_ADD,                                         // Play only after pending TTS.
                    mMixedAudioStream,
                    TextToSpeech.TtsInterruptSetting.INTERUPT_CALL,                 // Allow the TTS to interrupt an ongoing call.
                    TextToSpeech.TTS_RELEVANCE_ETERNAL,                             // Play the TTS as sooin as the VR ends.
                    "",                                                             // No need to display any text on the screen.
                    true,                                                       
                    HmiDisplayService.NO_DISPLAY );
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
	private void notifyTBTNotificationMgr(Context context, Intent intent){
		if(intent == null)
		{
			mLogger.e("notifyTBTNotificationMgr failed - bad intent.");
			return;
		}
		
		if(!mAllowNotifyTBTNotificationMgr)
		{
			return;
		}
		if(intent.getAction().equals(NavigationIF.NAVIGATION_DESTINATION_ARRIVED))
		{
			mAllowNotifyTBTNotificationMgr = false;
		}
		if (mTBTNotificationMgr == null) {
			mTBTNotificationMgr = TBTNotificationMgr.getInstance();
			if (mTBTNotificationMgr == null) {
				mLogger.e("TBTNotificationMgr.getInstance() failed!");
				return;
			}
		}
		String outStr =  intent.toString(); 

		mTBTNotificationMgr.navToTBTCommand(mContext, intent);
	}

	void onMsgReceive(Context mContext, NavMsgParcel navServiceMsg) {
		onMsgNavMsgParcelReceive(mContext, navServiceMsg);
	}

	public void onMsgNavMsgParcelReceive(Context mContext, NavMsgParcel navServiceMsg) {
        mLogger.v("Got navServiceMsg: " + navServiceMsg + " state = " + mNavigationState);

        String action = navServiceMsg.getAction();
        if (action.equals(NavigationIF.NAVIGATION_GUIDANCE_INFO) ){
            // If the on road feature is on.
            if ((mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , ON_ROAD_FEATURE_ENABLED) != ON_ROAD_FEATURE_ENABLED || mOnRoad) && 
                mIsAvailableGPS) {//and if the gps is Available 
                // Send gdence info if the driver is on road and the gps is avelebele 
                notifyTBTNotificationMgr(mContext, navServiceMsg);
            }
        }
        //
		if (action.equals(NavigationIF.ROUTE_EMPTY) || 
            action.equals(NavigationIF.ROUTE_PREVIEW) || 
            action.equals(NavigationIF.ROUTE_PREVIEW_ERROR) || 
            action.equals(NavigationIF.PREVIEW_MANEUVER_LIST) || 
            action.equals(NavigationIF.NAVIGATION_RECALCULATING_ROUTE) ||  
            action.equals(NavigationIF.NAVIGATION_STATE_CHANGE) || 
            action.equals(NavigationIF.CALCULATING_ROUTE) || 
            action.equals(NavigationIF.NAVIGATION_DBNC_ONROAD_STATUS_INTENT) ) {
            // notifyTBTNotificationMgr
            notifyTBTNotificationMgr(mContext, navServiceMsg);
		}

		if (action.equals(NavigationIF.NAVIGATION_TTS_INTENT)) { 
            //Send the NAVIGATION_TTS only if the car on road
            if ((SPEAK_INFO.DESTINATION_REACHED.ordinal() == navServiceMsg.getExtras().getInt(INTENT_EXTRA_SPEAK_INFO,0)	||
            		mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , ON_ROAD_FEATURE_ENABLED) != ON_ROAD_FEATURE_ENABLED || mOnRoad) && 
                mIsAvailableGPS) {//and if the gps is Available 
		        Message msg = new Message();
			    msg.what = TTS_INTENT_MESSAGE_OBJECT;
			    msg.obj = new handleTtsMessagesObject(navServiceMsg);
                mHandler.sendMessage(msg);
            }
			return;
		}
        
        if (action.equals(NavigationIF.NAVIGATION_DBNC_ONROAD_STATUS_INTENT)) { 
            mOnRoad = navServiceMsg.getBooleanExtra("IsOnRoadNow",true);

            if((mPconf.GetIntParam(PConfParameters.Navigation_OutOfRoadFeatureStatus , ON_ROAD_FEATURE_ENABLED) == ON_ROAD_FEATURE_ENABLED) && !mOnRoad && !mMuteOn && mIsAvailableGPS){
            	stopWtSndSync();
                int msgId = StringReader.Strings.APPNAVI_PROMPTID_WENT_OFF_MAP;
                String msg = mStringReader.getExpandedStringById(msgId);
                String promptForDisplay = mStringReader.getStringByIdForDisplay(msgId);
                //13 seconds is the time that portuguese and spanish will finish playing TTS
                mTts.speak( msg, TextToSpeech.QUEUE_ADD, mMixedAudioStream,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
                            TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true,13, HmiDisplayService.DISPLAY_TEXT_MESSAGE);
            }

        }
		if (action.equals(NavigationIF.NAVIGATION_GUIDANCE_INFO)) {
                
                if(mNavigationState != NavigationState.STARTED ) {
                    return;
                }

                NavigationIF.TTJunctionType junctionType = (NavigationIF.TTJunctionType)navServiceMsg.getSerializableExtra("junctiontype");
                NavigationIF.MCURouteInstructions  signType = (NavigationIF.MCURouteInstructions)navServiceMsg.getSerializableExtra("signtype");

                
                
                int    nextManuever  = navServiceMsg.getIntExtra("nextManuever",0);
                double distance      = navServiceMsg.getDoubleExtra("distance", -1.0);
                String message       = navServiceMsg.getStringExtra("message");
                String messagePhon   = navServiceMsg.getStringExtra("messageWithPhonetics");
                String streetName    = navServiceMsg.getStringExtra("streetname");
                double distToDest    = navServiceMsg.getDoubleExtra("disttodest", -1.0);
                double timeToDest    = navServiceMsg.getDoubleExtra("timetodest", -1.0);
                boolean newmaneuver  = navServiceMsg.getBooleanExtra("newmaneuver",false); 
                double angle         = navServiceMsg.getDoubleExtra("angle", -1.0);
                String currentStreet = navServiceMsg.getStringExtra("currentStreet");
                
                // if first manuever of navigation clear screen and stop waiting sound to prevent waitsound during navigation.
                // unless new BO route is in confirmation screen (in that case the confirmation screen will stop the waiting sound)
                if ((1 == nextManuever) && (newmaneuver) && !mInNewRouteConfirmationScreen && !(VdpGatewayService.IsTtsPlaying() && !mOnRoad))
                {
                	mLogger.d("first and new manuaver - clearing screen for navigation TBT.");
                	HmiDisplayService.getInstance().cancelFromSRHard();
                	stopWtSnd();
                }
                if( null == mReadDirectionMessages)
                {
        			// calling for directions list for reading.
        			mNavigationIF.getManuverList(Navigation.ACTION_READ_DIRECTIONS);
                }

                Navigation.GuidanceInfo newInfo = new GuidanceInfo( nextManuever,
                													distance,
                                                                    message,
                                                                    messagePhon,
                                                                    signType,
                                                                    streetName,
                                                                    distToDest,
                                                                    timeToDest,
                                                                    currentStreet,
                                                                    newmaneuver,
                                                                    angle,
                                                                    junctionType);

                handleGuidanceInfoObject obj = new handleGuidanceInfoObject(newInfo);
                mHandler.post(obj);
		} else if (action.equals(NavigationIF.ROUTE_EMPTY)) {

                int status = navServiceMsg.getIntExtra("status", -2);
                handleRouteEmptyObject obj = new handleRouteEmptyObject(status);
                mHandler.post(obj);

		} else if (action.equals(NavigationIF.ROUTE_FOUND)) {
                // adding the object as part of the msg instead of posting runnable to be able determining later
                // on whether this kind of message is already in the handler queue.
				mReadDirectionMessages = null;
                Message msg = new Message();
                msg.what = ROUTE_FOUND_OBJECT;
                msg.obj = new handleRouteFoundObject();
                mHandler.removeMessages(ROUTE_FOUND_OBJECT);
                mHandler.sendMessage(msg);
		} else if (action.equals(NavigationIF.ROUTE_FINISHED)) {
                handleRouteFinishedObject obj = new handleRouteFinishedObject();
                
                RouteData routeData = mNavigationManager.pullApprovedQueuedRoutes();
                // send Destination arrived to MCU
              	NavMsgParcel navDestMsg = new NavMsgParcel(NavigationIF.NAVIGATION_DESTINATION_ARRIVED);
              	navDestMsg.putExtra("street_name",mLastGuidanceInfo.mStreetName);
              	navDestMsg.putExtra("destination_name",routeData.routingText);
            	mLogger.i("route destination: "+routeData.routingText + " | streetname: "+mLastGuidanceInfo.mStreetName);
            	
            	notifyTBTNotificationMgr(mContext, navDestMsg);
                mHandler.postDelayed(obj, ROUTE_FINISHED_DELAY);
		} else if (action.equals(NavigationIF.ROUTE_MESSAGES)) {
			if(null == mReadDirectionMessages)
			{
				mReadDirectionMessages = navServiceMsg.getExtras().getStringArrayList("messages");
			}
		} else if (action.equals(NavigationIF.ADDRESS_MESSAGE)) {
                synchronized(mAddressResultSyncObject) {
                    mAddressResult = UnpackIntent(navServiceMsg, "address", new String[] {
                                                           "isocountry",
                                                           "country",
                                                           "state",
                                                           "district",
                                                           "city",
                                                           "province",
                                                           "cityphon",
                                                           "street",
                                                           "streetphon",
                                                           "housenumber"
                                                      } );
                mAddressResultSyncObject.notifyAll();
            }

        } else if(action.equals(NavigationIF.LOCATION_MESSAGE)) {

            synchronized(mLocationResultSyncObject) {
                if(navServiceMsg.getParcelableExtra("location") != null) {
                    mLocationResult = navServiceMsg.getParcelableExtra("location");
                }
                mLocationResultSyncObject.notifyAll();
            }

        } else if(action.equals(NavigationIF.CROSSING_STREETS_MESSAGE)) {

            synchronized(mCrossingStreetResultSyncObject) {
            	Bundle result = navServiceMsg.getBundleExtra("crossing");
            	if(null != result)
            	{
            		// saving crossing coordinate result for use in case no need house number
                	mLocationResult = new Location("tomtom");
                	mLocationResult.setLatitude(result.getDouble("lat"));
                	mLocationResult.setLongitude(result.getDouble("lon"));
                    
                	mCrossingStreetResult = new ArrayList<RTStringMap>();
                	
                	Map data = new HashMap<String,String>();
                	data.put("streetname", result.getCharSequence("street2"));
                	data.put("streetnamePhon", result.getCharSequence("street2"));
                	// saving crossing results data
                	mCrossingStreetResult.add(new RTStringMap(data));
            	}
                mCrossingStreetResultSyncObject.notifyAll();
            }

        } else if (action.equals(NavigationIF.NAVIGATION_RECALCULATING_ROUTE)) {
        	mReadDirectionMessages=null;
            mLastGuidanceInfo.mMessage = mStringReader.getExpandedStringById(StringReader.Strings.APPNAVI_PROMPTID_RECALCULATING_ROUTE) +
                    SPACE_SEPERATOR + mStringReader.getExpandedStringById(StringReader.Strings.TTS_PLEASE_WAIT); 
			mLastGuidanceInfo.mMessagePhon = mLastGuidanceInfo.mMessage;
            mLastGuidanceInfo.mIsRecalculation = true;
            if( SystemClock.uptimeMillis() - mRecalcPromptTime >= mRecalcPromptInterval ) {
                if(!getMuteStatus()){
                    notifyUserOfRecalculation();
                }
                mRecalcPromptTime = SystemClock.uptimeMillis(); 
            }
            
            // in case of recreate traffic route command from navkit
            if(navServiceMsg.getBooleanExtra("track_nav",false) && (NavigationState.STARTED == mNavigationState))
            {
            	updateState(NavigationState.PENDING_TRAFFIC);
            	mStartSilently = StartSilently.SILENT_NO_PROMPTS;
            	requestTraffic();
            }

        } else if(action.equals(NavigationIF.NAVIGATION_CURRENT_DESTINATION_AND_WAYPOINTS)) {
                synchronized(mCurrentDestAndWaySyncObject) {
                	mCurrentDestAndWay = navServiceMsg.getParcelableArrayListExtra("currentdestandwaypoints");
                	mCurrentDestAndWaySyncObject.notifyAll();
                }

		} else if (action.equals(NavigationIF.ROUTE_CALC_RESTARTED)) {
                // TODO: Notify user to turn around, could not calculate route in current position
        }
		else if (action.equals(NavigationIF.PROVINCE_MESSAGE))
		{			
			synchronized (mProvinceResultSyncObject) {				
				mProvinceResult = navServiceMsg.getStringExtra("province");
				mProvinceResultSyncObject.notifyAll();
			}		
		}
    }


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // Speech recognition events
    private BroadcastReceiver mSrBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context mContext, Intent intent) {
            String str = intent.getAction();
            mLogger.d("received intent : " + str);

            if (str.equals(VdpGatewayService.VDP_INTENT_VR_STARTED)) {
                mVrActive = true;
				if(mVrStartPendingTimer != null){
                    mVrStartPendingTimer.cancel(); 
                }
                mVrStartPendingTimer = null;
                stopWtSndSync();
            } else if (str.equals(VdpGatewayService.VDP_INTENT_VR_STOPPED)) {
                mVrActive = false;
                if(mVrStartPendingTimer != null){
                    mVrStartPendingTimer.cancel(); 
                }
                mVrStartPendingTimer = null;
                
                mHandler.post( new Runnable() {
                    public void run() {
                        checkIfNavigationActivityWaiting();
                    }
                } );
            }
			
        }
    };

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    // Phone state changes to idle mReceiver.
    private BroadcastReceiver mPhoneStateReceiver = new BroadcastReceiver() {
        
        @Override
        public void onReceive(Context mContext, Intent intent) {
            String str = intent.getAction();
            mLogger.d("received intent : " + str);

            if(str.equals(RoadTrackDispatcher.PHONES_ARE_BACK_TO_IDLE)) {

                mHandler.post( new Runnable() {
                    public void run() {
                    	checkIfNavigationActivityWaiting();
                    }
                } );
            }
            
        }
    };

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // Ignition & Door events
    private BroadcastReceiver mEventIoReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context mContext, Intent intent) {
            if(intent.getAction().equals(EventData.ACTION_EVENT_UPDATED)) {
                short eventId = intent.getShortExtra(EventData.EXTRA_EVENT_ID,(short)255);
                switch(eventId) {
                case EventData.EVENTREP_IGNITIONONOFF:
                case EventData.EVENTREP_INFOTAINMENT_ALLOWED:

                    mHandler.post( new Runnable() {
                        public void run() {
                        	mLogger.i("ignition state: " + mEventReader.getIgnitionOnOffEvent());
                            if (mRepositoryReader.getIgnitionOnOffEvent() == IgnigtionState.IGNITION_ON) 
                            {
                                if(mRepositoryReader.getIsInfotainmentAllowed() && !mRepositoryReader.isExternalBTCallActive())
                                {        
                                    handleIgnitionOn();
                                }       

                            }
                            else if(mTimerCheckAfterIgnition != null )
                            {
                                mTimerCheckAfterIgnition.cancel();
                            }
                        }
                    } );

                    break;
                case EventData.EVENTREP_MIRRORSTATE :
                	mLogger.i("mirror state: " + mEventReader.getMirrorPowerState());
                	if(!mEventReader.getMirrorPowerState() && (IgnigtionState.IGNITION_OFF == mRepositoryReader.getIgnitionOnOffEvent()))
                	{
                		handleIgnitionOffAndOpenedDoor();
                	}	
                    break;
                case EventData.EVENTREP_EXTERNAL_BT_CALL_STATUS:
                	if( (mRepositoryReader.getIgnitionOnOffEvent() == IgnigtionState.IGNITION_ON) && 
                			mRepositoryReader.getIsInfotainmentAllowed() && !mRepositoryReader.isExternalBTCallActive() )
                	{
	                    RouteData route = mNavigationManager.pullNewQueuedRoute();
	                    if(null != route)
	                    {
	                        mLogger.i("confirm start navigation for new route");
	                        mRouteDataFromBo = route;
	
	                        if(NavigationManager.RouteDataState.NEW_ROUTE == mRouteDataFromBo.dataState)
	                        {
	                        	mRouteDataFromBo.dataState = NavigationManager.RouteDataState.IN_CONFIRMATION ;
	                        }
	                        mNavigationManager.setRouteState((int)mRouteDataFromBo.id,NavigationManager.RouteDataState.IN_CONFIRMATION);
	
	                        confirmWithUserNavigationFromBo();
	                    }
                	}
                    break;
                }
            }
        }
    };

   
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private boolean isManeuverPrompt(SPEAK_INFO speakInfo) {
        switch(speakInfo) {
            case MANEUVER_FAR:
            case MANEUVER_SOON:
            case MANEUVER_NEAR:
            case MANEUVER_FIRST_PRIOR:	
            case MANEUVER_PRIOR:
                return true;
        }
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    // tts intent received
	/*  private final BroadcastReceiver mTtsIntentReceiver = new */
    ///////////////////////////////////////////////////////////////////////////

    // Boot finished intent 
    private final BroadcastReceiver mBootCompletedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            try{
                mLogger.v("sending last fix request to mcu");
                StringBuilder msgString = new StringBuilder();
                msgString.append("");
                byte[] data = msgString.toString().getBytes(Charset.forName("UTF-8"));
                P2PService p2pService = (P2PService) mContext.getSystemService(Context.P2P_SYSTEM_SERVICE);
                p2pService.sendMessage(MessageGroups.MESSAGE_GROUP_GPSFIX, MessageCodes.MESSAGE_CODE_GPSFIX_GET_LAST_FIX, data);

            } catch (Exception e) {
                mLogger.e("failed: ",e);
            }
        }
    };

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private static String PROVIDER_NAME = "0x0102";

    @Override
    public void onMessageRecieved(IMessage message) {
        mLogger.d("Message Received = " + message);
        LittleEndianInputStream is;
        JSONObject response = new JSONObject();
        ExtcallStatus call_status = ExtcallStatus.EXTCALL_STATUS_FAILED;
        
        switch (message.getCode()) {
        case MessageCodes.MESSAGE_CODE_GPSFIX_LAST_FIX:
            try
            {
                is = new LittleEndianInputStream(new ByteArrayInputStream(message.toArray()));
                mLogger.v("received message with gps fix from mcu : length = " + message.getDataLength() + " ( " + is.available() + " ) ");
                
                if( is.available() > 0 )
                {
                    //create location and save it for future use in case no new GPS fixes will be available.
                    Location loc = new Location(PROVIDER_NAME);
                    loc.setLatitude(is.readFloat());
                    loc.setLongitude(is.readFloat());
                    // The value received from the mcu is in sec we need to convert it to milliseconds.
                    long timeInMillis = (long)is.readInt() * 1000;
                    loc.setTime(timeInMillis);
                    loc.setSpeed(is.readFloat());
                    loc.setBearing(is.readFloat());
                    loc.setAccuracy(is.readFloat());
                    // if the cordinates are zeros set the location as null as those values imply that no valid gps fix 
                    // was stored in the mcu
                    if((loc.getLatitude() == 0) && (loc.getLongitude() == 0)){
                        mLastLocationFromMcu = null;
                    }else{
                        mLastLocationFromMcu = loc;
                    }
                }
            }
            catch(Exception e)
            {
                mLogger.e("Exception ", e );
            }
            break;
        default:
            break;
        }
    }

    private class IntentReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if(intent.getAction().equals(PConf.PCONF_PARAMUPDATED)) {
                ArrayList<Integer> paramIDs = intent.getIntegerArrayListExtra(PConf.PARAM_ID_LIST);
                if(paramIDs != null) {
                    for(int id : paramIDs) {
                        if(id == PConfParameters.Navigation_NavigationFeatureStatus) {
                            int isNavigationFeatureEnabled = mPconf.GetIntParam(PConfParameters.Navigation_NavigationFeatureStatus, SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING);
                            if((isNavigationFeatureEnabled & SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING) == 0) {
                                if(mNavigationState != NavigationState.NONE) {
                                    mLogger.i("Navigation feature disabled, stop active navigation");
                                    stopNavigation();
                                }
                            }
                        }
                    }
                }
                else {
                    mLogger.e("Parameter List could not obtained");
                }
            }
        }
    }
}
