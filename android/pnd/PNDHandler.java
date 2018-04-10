
import android.bluetooth.BluetoothPhonebookClient;
import android.bluetooth.BluetoothPhonebookClient.BluetoothPhonebookClientIntent;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.CallLog;
import android.telephony.TelephonyManager;  
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.Context;
import android.location.Location;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.location.LocationManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Looper;
import android.bluetooth.BluetoothHFP;
import android.speech.tts.*;


import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;

import java.util.Properties;
import java.util.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Calendar;

import java.lang.Character;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.roadtrack.telephony.RTCallTracker;
import com.roadtrack.inrix.TrafficRoutingServices;
import com.roadtrack.util.RTLocalParamManager;

public class PNDHandler implements IMessageReciever, TextToSpeech.OnInitListener {

    public class RouteGmlanData {
        public float  routingLatitude;
        public float  routingLongitude;
        public String  routingText;

        public RouteGmlanData () {
            this.routingLatitude = 0;
            this.routingLongitude = 0;
            this.routingText = "NA";
            
        }
        public String toString() {
            return "{ lat,lon = " + routingLatitude + "," + routingLongitude +
                    ", text = [" + routingText + "]" +
                    " }";
        }
    }

    private static final String  TAG   	 = "PNDHandler";
    private static final boolean Debug 	 = true;
    private static final String 	INI_FILE = "/system/vendor/roadtrack/resources/roadtrack.conf";
    // used for indicating to the app on hand over failure
    private static final int 		HANDOVER_FAILED           = 3;
    private static final String     PND_DEFAULT_VERSION       = "1.0";
    private static final int        HANDLE_PB_REQUEST         = 0;
    private static final int        HANDLE_CALLOG_REQ         = 1;
    private static final int        HANDLE_PND_SEND_VERSION_1 = 2;
    private static final int        HANDLE_PND_SEND_VERSION   = 3;
    private static final int        HANDLE_PND_SEND_ROUTE     = 4;
    private static final int        HANDLE_PND_ROUTE_STATUS   = 5;
    private static final int        HANDLE_PND_SEND_TXTMSG    = 6;
    private static final int        HANDLE_PND_TXTMSG_STATUS  = 7;
    private static final int        HANDLE_CHUNK_WAIT_TIMEOUT = 8;
    private static final int        HANDLE_TIME_AND_POSITION_UPDATE = 9;
    private static final int        HANDLE_WAIT_FOR_PB_ACK          = 10;
    private static final int        HANDLE_WAIT_FOR_CALL_LOG_ACK    = 11;
    private static final int        HANDLE_KEEPALIVE_WAIT_TIMEOUT   = 12;

    private static final int        PND_MAX_RECORDS                 = 4;
    private static final int        MAX_PHONE_NUMBER_PER_CONTACT    = 8;

    private static final int        HANDOVER_FAILURE_REASON_TIMEOUT = 0;
    private static final int        TIMEOUT_WAITING_FOR_CHUNK 	    = 10000;
    private static final int        TIME_AND_POSITION__MSG_INTERVAL = 5000;
    private static final int        TIMEOUT_WAITING_FOR_ACK         = 5000;
    private static final int        TIMEOUT_WAITING_FOR_KEEPALIVE   = 10000;
    


    private PNDProtocol       mPNDProtocol                         = null;
    private P2PService        mP2pService                          = null;
    private Timer             mTimer                               = null;
    private Context           mContext                             = null;           
    private NavigationManager mNavigationMgr                       = null;
    private Navigation        mNavigation                          = null;
    private SettingsHandler	   mSettingsHandler						= null;
    private NavHandler 		   mNavHandler 							= null;
    private PConf             mPconf                               = null;
    private PndDB             mDbHelper                            = null;  
    private SQLiteDatabase    mDatabase                            = null;
    private TxtMsgData        mLastTxtMsgData                      = null;
    private LocationManager   mLocationManager                     = null;
    private int               mKeepAliveCount                      = 0;
    private int               mRouteIndx                           = 0;
    private long              mStartTime                           = 0;
    private boolean           mLastGpsStatus                       = false;
    private TextToSpeech      mSpeech                              = null;
    private Gen2AppDisplay    mGen2AppDisplay                      = null;
    private RTLog 			  mLogger		                       = null;
    private int               mMsgFreeSpaceGenToApp 	 	       = 980;   // for the route details and waypoints.
    private int               mMsgFreeSpaceIndash 		 	       = 400;   // for the indash usage - currently set to 220 due to transfer buffer issue.
    private int               mNumberAllowedTimesOut               = 3;     // used for defining the number of allowed time outs in the handover
                                                                            // process if not getting the msg/ack.
    private long 			  mHandoverTimeout                     = 3000;  // used for defining the allowed time for each time out in the handover
                                                                            // process while waiting for the msg/ack.
    private HFPStatus         mHFPStatus                           = null;
    private PhoneBookReader   mPhoneBookReader                     = null;
    private CallLogReader     mCallLogReader                       = null;
    private boolean           mUseCachedContacts                   = false;
    private int               mPhoneBookContactsIndex              = 0;
    private boolean 		  mInDashConfig                        = false;
    private boolean          mIndashNaviActive						= false;
    private int               mHfpSignal                           = 0;
    private int               mHfpBatteryLevel                     = 0;
    private boolean           mCallLogAvailable                    = false;
    private int               mCallLogsIndex                       = 0;
    private GoogleServices                      mGoogleServices             = null;
    private HandleTrafficRequestObject          mHandleTrafficRequestObject = null;
    private TrafficRoutingServices              mTrafficRoutingServices     = null;
    private HandleMediaRequestObject 			mHandleMediaRequestObject   = null;
    private TBTNotificationPND                  mTbtNofifPnd                = null;
    private RoadTrackAudioOutputSwitch          mRoadTrackAudioOutputSwitch = null;
    private RouteHandOverReceiveObject          mRouteHandOverReceiveObject = null;
    private RouteHandOverSendObject             mRouteHandOverSendObject    = null;
    private EventRepositoryReader               mEventRepository            = null;    
    private String             mAvrcpAddress = "";
    private boolean 		   mIsMounted 			   = false;
    private boolean  		   mIsReady		 		   = false;
    private boolean  		   mIsBrowsable 		   = false;
    private int      		   mScanProgress 		   = 0;
    private boolean            mPBWaitingForAck        = false;
    private boolean            mCallLogWaitingForAck   = false;
    private boolean            mFinalPack              = false;
    private int                mMissedKeepAliveCount   = 0;
    private RouteGmLanObject   mRouteGmLanObject       = null;
    private RouteGmlanData     mrouteGmlanData         = null;
    private boolean            mGmlanNavConfig                        = false;
    // for speaking TTS
	private TextToSpeech mTts;
	private StringReader mStrings;
	private Thread 			   mStartThread 			= null;
	private DataHandler  	   mDataHandler 			= null;


    /**
     * PNDHandler Constructor
     * @param pndProtocol - pnd protocol instance to be able to interact with 
     * @param context
     */
    public PNDHandler(PNDProtocol pndProtocol, Context context) {
        mLogger 		 = RTLog.getLogger(TAG, "pndhandle", RTLog.LogLevel.INFO );
        mLogger.i("Constructor Called!");
        init();
        mContext         = context;
        mPNDProtocol     = pndProtocol;
        mNavigationMgr   = NavigationManager.getInstance(context);
        mNavigationMgr.setPndHandler(this);
        mGen2AppDisplay  = Gen2AppDisplay.getInstance();
        mGen2AppDisplay.setPNDHandler(this);
        mSettingsHandler = SettingsHandler.getInstance();
        mSettingsHandler.setPndHandler(this);
        mNavHandler		 = NavHandler.getInstance();
        mNavHandler.setPndHandler(this);
        mNavigation      = Navigation.Instance();
        mNavigation.setPndHandler(this);

        mHFPStatus     = new HFPStatus();        
        mRoadTrackAudioOutputSwitch = RoadTrackAudioOutputSwitch.getInstance(mContext);
        mRoadTrackAudioOutputSwitch.setPndHandler(this);

        mPconf = (PConf)context.getSystemService(Context.PCONF_SYSTEM_SERVICE);
        mEventRepository = (EventRepositoryReader)context.getSystemService(Context.EVENTREP_SYSTEM_SERVICE);
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        mLocationManager.addGpsStatusListener(mGPSStatusListener);

        RoadTrackDispatcher.registerPNDHandler(this);
        BTDevicesService.registerPNDHandler(this);
        TBTNotificationPND.registerPNDHandler(this);
        mRTUpdateService = RTUpdateService.getInstance(mContext);
        mRTUpdateService.registerPndHandler(this);

        mTbtNofifPnd = TBTNotificationPND.getInstance();
        mDbHelper = new PndDB(context);  
        mDatabase = mDbHelper.getWritableDatabase();

        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(BluetoothHFP.ACTION_HFP_STATUS_CHANGED);
		intentfilter.addAction(BTDevicesService.CONTACTS_SYNC_UPDATE_INTENT);
        intentfilter.addAction(PConf.PCONF_PARAMUPDATED);
        intentfilter.addAction(EventData.ACTION_EVENT_UPDATED);
        intentfilter.addAction(BluetoothHFP.ACTION_HFP_SIGNAL_STRENGTH_CHANGED);
        intentfilter.addAction(BluetoothHFP.ACTION_HFP_BATTERY_CHANGED);
        mContext.registerReceiver(mPbapBroadcastReceiver, intentfilter);

        mP2pService = (P2PService) context.getSystemService(Context.P2P_SYSTEM_SERVICE);
        mP2pService.register(MessageGroups.MESSAGE_GROUP_PND, this);

        mSpeech = new TextToSpeech(mContext,this);

        PicoyPlaca.registerToMessage(this);

        FileInputStream ini_file = null;
        
        try {
        	ini_file = new FileInputStream(INI_FILE);
			Properties prop = new Properties();
			prop.load(ini_file);
			mMsgFreeSpaceGenToApp  = Integer.parseInt(prop.getProperty( "Handover_free_chars_per_msg", "980"));
			mMsgFreeSpaceIndash    = Integer.parseInt(prop.getProperty( "Indash_free_chars_per_msg", "400"));
			mNumberAllowedTimesOut = Integer.parseInt(prop.getProperty( "Handover_allowed_timeout", "3"));
			mHandoverTimeout       = Long.parseLong(prop.getProperty( "Handover_timeout", "3000" ));
			
		} catch ( Exception e ) {
			mLogger.e(  "configuration file:", e );
		} finally {
			if(null != ini_file)
			{
    		try {
    			ini_file.close();
    		}catch (IOException e)
    		{
    			
    		}	
			}
		}

        mInDashConfig = (mPconf.GetIntParam(PConfParameters.PND_InDashEnabled ,0) == 0) ? false : true;
        mGoogleServices = new GoogleServices(mContext);
        mTrafficRoutingServices = new TrafficRoutingServices(mContext);
        
        /********************************
         * Listen for media Player events
         *********************************/
        // register for media player events
        IntentFilter mediaFilter = new IntentFilter();
        mediaFilter.addAction(RTMediaHandler.ACTION_MEDIA_PLAYER_STATUS);
        context.registerReceiver( mMediaBroadcastReceiver, mediaFilter);
        mrouteGmlanData = null;
        mGmlanNavConfig = (mPconf.GetIntParam(PConfParameters.Navigation_GmlanRadioNavigation ,0) == 0) ? false : true;
        
    }
    private void init() {
		mStartThread = new Thread( new Runnable() {
			@Override
			public void run() 
			{	
				Looper.prepare();
				mDataHandler = new DataHandler();
				Looper.loop();
			}
		}, "PND");
		mStartThread.start();
	}

    public boolean isCurrentlyUpdatingMap()
    {
    	return mRTUpdateService.isMapCurrentlyUpdating();
    }

    private enum MEDIA_SYNC_STATUS{
    	SYNC_NOT_SUPPORTED,
    	SYNC_STARTED,
    	SYNC_ENDED_SUCCEFULLY,
    	SYNC_ENDED_WITH_CHANGE,
    	SYNC_FAILED,
    }
    
    private BroadcastReceiver mMediaBroadcastReceiver = new BroadcastReceiver() {
    	
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if(intent.getAction().equals(RTMediaHandler.ACTION_MEDIA_PLAYER_STATUS)) {

    			try {
    				// get BT address
    				RTMediaHandler.IMediaAccessProvider media = RTMediaHandler.getInstance().Media();
    				String avrcpAddress = media.getDeviceId();

    				JSONObject msg = new JSONObject(intent.getStringExtra("status"));

    				boolean isMounted    =  ( msg.getInt(RTMediaHandler.MEDIA_FIELD_MOUNT_STATUS) == 1 );
    				boolean isReady	      =  ( msg.getInt(RTMediaHandler.MEDIA_FIELD_MEDIA_READY) == 1 );
    				boolean isBrowsable  =  ( msg.getInt(RTMediaHandler.MEDIA_FIELD_CAN_BROWSE) == 1 );
    				int 	 scanProgress =  ( msg.getInt(RTMediaHandler.MEDIA_FIELD_SCAN_PROGRESS));
                    int     avrcpVersion  = ( msg.getInt(RTMediaHandler.MEDIA_FIELD_AVRCP_VERSION) );

    				mLogger.d(" received ACTION_MEDIA_PLAYER_STATUS intent: " +
    						   " isMounted: "    + isMounted + 
    						   " isReady: "      + isReady + 
    						   " isBrowsable: "  + isBrowsable +
    						   " scanProgress: " + scanProgress);

    				if ((isMounted) && (isReady)){
    					// if mounted and ready need to check if until now wasn't ready, if no notify on started sync 
    					if(!mIsReady){
                            mPNDProtocol.sendAvrcpCapabilities(avrcpVersion, isBrowsable);
    						if(isBrowsable){
       							mPNDProtocol.sendMediaConnectStatus(avrcpAddress, true, MEDIA_SYNC_STATUS.SYNC_STARTED.ordinal());
    						} else {
    							mPNDProtocol.sendMediaConnectStatus(avrcpAddress, true, MEDIA_SYNC_STATUS.SYNC_NOT_SUPPORTED.ordinal());
    						}
    					}

    					if(mScanProgress != 100){
    						if(scanProgress == 100){
    							mPNDProtocol.sendMediaConnectStatus(avrcpAddress, true, MEDIA_SYNC_STATUS.SYNC_ENDED_SUCCEFULLY.ordinal());
    						}
    					}
    				} else if((!isMounted) && (!isReady)){
    					// if not mounted and not ready need to check if until now was ready, if yes notify on disconnection
    					if((mIsMounted) && (mIsReady)){
    						mPNDProtocol.sendMediaConnectStatus(avrcpAddress, false, MEDIA_SYNC_STATUS.SYNC_FAILED.ordinal());
    					}
    				}
    				
    				mIsMounted = isMounted;
    				mIsReady   = isReady;
    				mIsBrowsable = isBrowsable;
    				mScanProgress = scanProgress;
    				mAvrcpAddress = avrcpAddress;
    			} catch (JSONException e) {
    				mLogger.e("json parse fail on ACTION_MEDIA_PLAYER_STATUS:", e);
    				return;
    			} 
    		}
    	}
    };
    
    
    /**
     * handles general media request received from the pnd.
     */
    public void requestMediaList(int opCode, int itemType, int itemID){
    	if(mScanProgress == 100){ 
    		if(mHandleMediaRequestObject == null){
    			mLogger.d("opCode=[" + opCode + " : mScanProgress=" + mScanProgress);
    			mHandleMediaRequestObject = new HandleMediaRequestObject(itemType, itemID, null);
    		} else {
    			mLogger.d("mHandleMediaRequestObject is not null aborting send out");
    		}
    	} else {
    		mLogger.d("opCode=[" + opCode + " : not synced mScanProgress=" + mScanProgress);
    		mPNDProtocol.sendGenericAckNac_V2P(opCode, PNDProtocol.GENERIC_ACK_UNSYNCED);
    	}
    }
    
    /*
     * returns true if map update via mobile is in apply state
     */
    public static boolean isMobileMapUpdateApplyState()
    {
    	if (mOtaState == PND_OTA_STATE.APPLY)
    		return true;
    	else
    		return false;
    }
    
    /**
     * handles specific media request received from the pnd.
     */
    public void requestSpecificMediaList(int opCode, int itemType, int itemID){
    	if(mScanProgress == 100){
    		RTMediaHandler.IMediaAccessProvider media = RTMediaHandler.getInstance().Media();
    		// need to do this here and not in the constructor in case the media is switching because other device was connected.
    		media.setPndHandler(this);
    		media.listSongsForObject(RTMediaHandler.getInstance().new PlayRequest(MediaObjectType.values()[itemType], itemID, ""));
    	} else {
    		mLogger.d("opCode=[" + opCode + " : not synced mScanProgress=" + mScanProgress);
    		mPNDProtocol.sendGenericAckNac_V2P(opCode, PNDProtocol.GENERIC_ACK_UNSYNCED);
    	}		
    }
    
    public void startThreadAndSendList(int itemType, int itemID, JSONArray listContainerJson ){
    	mLogger.d("creating object that sends requested lists to the pnd");
    	if(mHandleMediaRequestObject == null){
    		mHandleMediaRequestObject = new HandleMediaRequestObject(itemType, itemID, listContainerJson);
    	} else {
    		mLogger.d("mHandleMediaRequestObject is not null aborting send out");
    	}
    }
    
    /**
     * handles received ack for either sent song list or to the meta data messages
     * @param isAckForSongList
     */
    public void handleAckFromIndashOnMediaMsg(){
    	if(mHandleMediaRequestObject != null){
    		mHandleMediaRequestObject.handleAckFromIndash();
    	}
    }
    
    /**
     * stops the media lists request threads.
     */
    private void stopMediaListsThread(){
    	if(mHandleMediaRequestObject != null){
    		mHandleMediaRequestObject.mKeepRunning = false;
    		mHandleMediaRequestObject = null;
    	}
    }
    
	/********************************************************************************************************************************/
	/********************************************************************************************************************************/
	/********************************************************************************************************************************/
	/********************************************************************************************************************************/
	
	
	
    
    
    /**
     * used for defining the requested media list type 
     *
     */
    public enum MEDIA_REQUEST_TYPE{
    	ALL_SONGS_LIST,
    	SPECIFIC_SONG_LIST,
    	ALL_MEDIA_OBJECTS_LIST,
    }
    
    /**
     * 
     * this object handles the whole process that responsible for sending the media
     * lists to the pnd.
     */
    private class HandleMediaRequestObject {
    	private int 	  						  mMsgsLeftToSend 		= 0;
    	private int 	  						  mTimeOutCounter 		= 0;
    	private ArrayList<String> 				  mMsgsList				= null;
    	private Thread   						  mSendSongsThread 		= null;
    	private boolean 						  mIsSongListStage		= true;
    	public volatile boolean				  mKeepRunning          = false;
    	// used for holding the requested list details the requested type and id.
    	private int							  mItemType				= 0;
    	private int							  mItemID				= 0;
    	private JSONArray 						  mListContainerJson    = null;
    	
    	public HandleMediaRequestObject(int itemType, int itemID, JSONArray listContainerJson){
    		mItemType = itemType;
    		mItemID   = itemID;
    		mListContainerJson = listContainerJson; 
    		// this thread used for sending the song list to the pnd client.
    		mSendSongsThread = new Thread(new Runnable() {
				
				public void run() {
					RTMediaHandler.IMediaAccessProvider media = RTMediaHandler.getInstance().Media();
					
					// if no item type received prepare for sending all media lists.
					if(mItemType == 0){
						prepareMediaLists(MEDIA_REQUEST_TYPE.ALL_SONGS_LIST, media);
					} else {
						prepareMediaLists(MEDIA_REQUEST_TYPE.SPECIFIC_SONG_LIST, media);
					}
					
					while(mKeepRunning){

						// passed allowed number of attempts, announce user and exit
						if(mTimeOutCounter >= mNumberAllowedTimesOut){
							mPNDProtocol.sendMediaConnectStatus(media.getDeviceId(), true, MEDIA_SYNC_STATUS.SYNC_FAILED.ordinal());
							stopMediaListsThread();
							return;
						}


						if(mMsgsList.size() > 0){
							// check if there are more media lists message to be sent and pass counter, 0 means it is the last msg
							if(mIsSongListStage){
								sendMediaList(mMsgsLeftToSend, MEDIA_REQUEST_TYPE.ALL_SONGS_LIST);
							} else {
								// if not in the song list stage need to check mItemType because if its part of the sync process this 
								// param value will be 0.
								if(mItemType == 0){
									sendMediaList(mMsgsLeftToSend, MEDIA_REQUEST_TYPE.ALL_MEDIA_OBJECTS_LIST);
								} else {
									sendMediaList(mMsgsLeftToSend, MEDIA_REQUEST_TYPE.SPECIFIC_SONG_LIST);
								}
							}
						// no songs to send as part of the list
						} else {
							stopMediaListsThread();
							return;
						}

						try{
							mLogger.d("waiting for ack from indash");
							Thread.sleep(mHandoverTimeout);
							// woke up from sleep meaning to interrupt was received therefore no ack received - incrementing timeout counter
							mTimeOutCounter++;
						} catch (InterruptedException e){
							mLogger.d("received ack from indash");
							mMsgsLeftToSend--;
							mTimeOutCounter = 0;

							if((mMsgsList.size() <= 0) || (mMsgsLeftToSend == 0)){
								mLogger.d("finished sending, received last ack");
								mKeepRunning = false;
								//mSendMediaMetaThread.start();
								if(mIsSongListStage){
									prepareMediaLists(MEDIA_REQUEST_TYPE.ALL_MEDIA_OBJECTS_LIST, media);
								} else {
									stopMediaListsThread();
									return;
								}
							}
						}

					}					
				}
			});
    		mSendSongsThread.start();
    	}
    	
    	
    	
    	
    	
    	/**
    	 * creates the media lists for each stage - at first stage the songs list and at second stage the meta lists.
    	 * @param isSongList
    	 * @param media
    	 */
    	private void prepareMediaLists(MEDIA_REQUEST_TYPE listType, RTMediaHandler.IMediaAccessProvider media){
    		
    		JSONArray listContainerJson = new JSONArray();
    		if(listType == MEDIA_REQUEST_TYPE.ALL_SONGS_LIST){
    			mLogger.d("sending song list");
				// adding the received array to the lists container
    			listContainerJson.put(media.listSongsFull());
    		} else if(listType == MEDIA_REQUEST_TYPE.ALL_MEDIA_OBJECTS_LIST) {
    			mLogger.d("sending all media objects lists");
        		try {
        			// adding the received arrays to the lists container as all those objects should be sent in one message
        			listContainerJson.put(MediaObjectType.ARTIST.ordinal(),   media.listObjects(MediaObjectType.ARTIST));
        			listContainerJson.put(MediaObjectType.GENRE.ordinal(),    media.listObjects(MediaObjectType.GENRE));
        			listContainerJson.put(MediaObjectType.ALBUM.ordinal(),    media.listObjects(MediaObjectType.ALBUM));
        			listContainerJson.put(MediaObjectType.PLAYLIST.ordinal(), media.listObjects(MediaObjectType.PLAYLIST));
        		} catch (JSONException e) {
        			mLogger.e("failed adding one of the lists to the json array", e);
        		}
        		mIsSongListStage = false;
    		} else if(listType == MEDIA_REQUEST_TYPE.SPECIFIC_SONG_LIST){
    			mLogger.d("sending specific requested song list");
				// adding the received array to the lists container
    			listContainerJson.put(mListContainerJson);
    			mIsSongListStage = false;
    		}
    		
    		mMsgsList = createMediaMsgsArray(listContainerJson, mMsgFreeSpaceIndash, listType);
    		mMsgsLeftToSend = mMsgsList.size();
    		mKeepRunning = true;
    	}
    		
    	/**
    	 * parses all the arrays in the received container and creates a result string array out of them.
    	 * @param listsContainer
    	 * @param msgSize
    	 * @param isSongList
    	 * @return
    	 */
    	private ArrayList<String> createMediaMsgsArray(JSONArray listsContainer, int msgSize, MEDIA_REQUEST_TYPE listType){
    		ArrayList<String> msgsList = new ArrayList<String>();
    		StringBuilder msgString  = new StringBuilder(msgSize);
    		int length = 0;
    		int recordLength = 0;
    		String objectType = "";
    		String objectName = "";
    		String objectID   = "";
    		/*String artistID   = "";
    		String albumID    = "";
    		String genreID	  = "";
    		*/
    		try {
    			// we use a container of JSON arrays, which is also an array. it allows to support handling
    			// the meta data request as it consisted from several object type lists.
    			for(int index = 0; index < listsContainer.length(); index++){
    				JSONArray itemsList = listsContainer.optJSONArray(index);
    				// need to check if this list contains any members otherwise move on to the next list
    				if((itemsList == null) || (itemsList.length() <= 0)) continue;
    				for(int i = 0; i < itemsList.length(); i++){
    					objectName = itemsList.getJSONObject(i).getString(RTMediaHandler.MEDIA_FIELD_OBJECT_NAME); 
    					objectID   = itemsList.getJSONObject(i).getString(RTMediaHandler.MEDIA_FIELD_MEDIA_ID); 
    					if(listType == MEDIA_REQUEST_TYPE.ALL_MEDIA_OBJECTS_LIST){
    						
    						// the index was set earlier according to the list types and is defined as described in the protocol document
    						objectType = String.valueOf(index);

    						// we measure the length of the record consists of data separator + object type + inner separator + object name + 
    						// inner separator + object ID.
    						recordLength = 1 + objectType.length() + PNDProtocol.RECORD_INNER_SAPERATOR.length() + objectName.length() + 
    								PNDProtocol.RECORD_INNER_SAPERATOR.length() +  objectID.length();
    					} else {
    						recordLength = 1 + objectName.length() + PNDProtocol.RECORD_INNER_SAPERATOR.length() + objectID.length();
    					}
    					
    					if((length + recordLength) > msgSize){
    						msgsList.add(msgString.toString());
    						msgString.setLength(0);
    						length = 0;
    					}
    					
    					// the two messages have different structures and therefore they are created differently.
    					if(listType == MEDIA_REQUEST_TYPE.ALL_MEDIA_OBJECTS_LIST){
    						msgString.append(PNDProtocol.PND_DATA_SEPARATOR);
    						msgString.append(objectType);
    						msgString.append(PNDProtocol.RECORD_INNER_SAPERATOR);
    						msgString.append(objectName);
    						msgString.append(PNDProtocol.RECORD_INNER_SAPERATOR);
    						msgString.append(objectID);
    					} else {
    						msgString.append(PNDProtocol.PND_DATA_SEPARATOR);
    						msgString.append(objectName);
    						msgString.append(PNDProtocol.RECORD_INNER_SAPERATOR);
    						msgString.append(objectID);
    						
    					}
    					length += recordLength;
    				}
    			}
    		} catch (JSONException e) {
				mLogger.d("failed parsing song list json object", e);
			}
			
    		// adding the record that accumulated but wasn't added to the list during the for loop
    		msgsList.add(msgString.toString());
    		return msgsList;
    	}
    	/**
	     * sends the objects lists to the indash and the number of messages left to be sent.
	     * 
	     * @param msgsLeftToSend - number of messages remains to be sent not including this msg.
	     * if msgsLeftToSend = 0 it means this is the last msg.
	     * @param isSongList - according to this flag the pndprotocol sets the msg opcode
	     */
	    private void sendMediaList(int msgsLeftToSend,MEDIA_REQUEST_TYPE listType){
	    	mLogger.d("sending song list message");
	    	// sending the msgsLeftToSend decreased by one because it says how many msgs left besides this one 
	    	// the item id will be zero in case of the general lists, and will be set to the requested id in case of specific list request
	    	mPNDProtocol.sendMediaList(mMsgsList.get(mMsgsList.size() - msgsLeftToSend), msgsLeftToSend - 1, listType, mItemID);
		}

	    // according to the ack the thread should be interrupted.
    	public void handleAckFromIndash(){
    		mSendSongsThread.interrupt();
    	}
    }

    /**
     * returns pnd connection status
     */
    public boolean isConnected() {
        return mPNDProtocol.isConnected();
    }

    /**
     * returns indash connection status
     */
    public boolean isIndashConnected() {
        mLogger.v("mInDashConfig = " + mInDashConfig + " isConnected() = " + isConnected());
        if(mInDashConfig && isConnected()){
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /**
     * returns indash connection status based on configuration parameter
     */
    public boolean isIndashConfigured(){
    	return mInDashConfig;
    }
    
    /**
     * return indash navigation status according to updated received from indash 
     */
    public boolean isIndashNaviActive(){
    	synchronized (this) {
    		return mIndashNaviActive;
		}
    }
    
    /**
     * updates the member with status received from the indash
     */
    public void setIndashNaviActive(boolean isActive){
    	synchronized (this) {
    		mIndashNaviActive = isActive;
		}
    }
 
    /*
     * Send to PND that SPP is disconnecting
     */
    public void sendSppDisconnectingMessageToPND()
    {
    	mPNDProtocol.sendSppTurningOff_V2P();
    }
    
    public void sendPicoPlacaMessageToPND(String city) {
        // send to PND
    }

    /**
     * handles different messages in it's own thread, mainly phone book reading and creating messages,
     * route and text messages read from data base and sent to  PND.
     */
    private class DataHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case HANDLE_PND_SEND_VERSION_1:
                sendVersion_V2BO("",PND_DEFAULT_VERSION,"");
                break;
            case HANDLE_PND_SEND_VERSION:
                DeviceInfo deviceInfo = (DeviceInfo) msg.obj;
                sendVersion_V2BO(deviceInfo.deviceId, deviceInfo.shellVersion, deviceInfo.installedMaps);
                break;
            case HANDLE_PND_SEND_ROUTE:
                RouteData routeData = mNavigationMgr.pullApprovedQueuedRoutes();
                if( routeData != null ) {
                    if(routeData.routingTimeStamp > System.currentTimeMillis()) {
                        if( startRoute(routeData, false) ) {
                            mDataHandler.sendEmptyMessageDelayed(HANDLE_PND_ROUTE_STATUS, 4000);
                        }
                    } else {
                        if( NavigationManager.RouteDataState.APPROVED == routeData.dataState  
                            && routeData.routingTimeStamp != 0) {
                        	mNavigationMgr.removeApprovedRoute();
                            mLogger.i(" time stamp passed! deleting Route from DB.");
                        }
                    }
                } else {
                    mLogger.e("No route to send");
                }
                break;
            case HANDLE_PND_ROUTE_STATUS:
                sendRoutingStatusToBO_V2BO(NONE, PND_TIMEOUT);
                break;
            case HANDLE_PND_SEND_TXTMSG:
                mLastTxtMsgData = getQueuedTxtMessage();

                if(mLastTxtMsgData.txtMsgTimeStamp > System.currentTimeMillis()) {
                    sendTxtMessageToPND(mLastTxtMsgData);
                } else {
                    deleteTxtMsgFromDB(mLastTxtMsgData);
                    if(queuedTextMsgsCount() > 0) {
                        mDataHandler.sendEmptyMessage(HANDLE_PND_SEND_TXTMSG);
                    }
                }
                break;
            case HANDLE_PND_TXTMSG_STATUS:
                sendTxtMsgStatusToBO_V2BO(FAILURE, PND_TIMEOUT);
                break;
            case HANDLE_CHUNK_WAIT_TIMEOUT:
                mLogger.i("Timeout waiting for chunk to download from phone passed.");
                if(mOtaState == PND_OTA_STATE.IDLE) {
                    mLogger.i("Ota is disabled");
                    return;
                }
                mOutOfSyncCounter++;
                if (mOutOfSyncCounter == MAX_OUT_OF_SYNC_RETRIES)
                {
                    mLogger.e("Error Downloading File. too many retries.");
                    mDownloadComplete = false;
                    mOtaState = PND_OTA_STATE.IDLE;
                    mPconf.SetIntParam(PConfParameters.OTA_Enable, 0);
                    mRTUpdateService.notifyMobileDownloadComplete();
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.flush();
                            fileOutputStream.close();
                        } catch (java.io.IOException e) {
                            mLogger.e("IOException ", e );
                        }
                    }
                }
                else
                {
                	mPNDProtocol.getChunk_V2P(mFileId, mChunkOffset);
                	mDataHandler.sendEmptyMessageDelayed(HANDLE_CHUNK_WAIT_TIMEOUT, TIMEOUT_WAITING_FOR_CHUNK);
                }
                break;
            case HANDLE_PB_REQUEST: 
                if(mPhoneBookReader == null) {
                    mPhoneBookReader = new PhoneBookReader();
                    mPhoneBookContactsIndex = 0;
                }

                if(mPhoneBookReader.getCount() <= 0) {
                    mLogger.i(" No PhoneBook Contacts Found!!");
                    mPhoneBookReader = null;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, PNDProtocol.PHONEBOOK_SYNC_FAILED, mHFPStatus.mHFPAddress);
                    IndicationManager.setIndication(mContext,IndicationMgrParams.EIND_BT_BLINKING_BLUE_FAST_INFINITE, false);
                    return;
                }

                mPNDProtocol.createPhoneBookContactsString_V2P(mPhoneBookReader.readContacts(), mPhoneBookReader.isLast());

                if (mPhoneBookReader.isLast()) {
                    IndicationManager.setIndication(mContext,IndicationMgrParams.EIND_BT_BLINKING_BLUE_FAST_INFINITE, false);
                    mPhoneBookReader= null;
                    mPBWaitingForAck = false;
                } else {
                    mPBWaitingForAck = true;
                    mDataHandler.sendEmptyMessageDelayed(HANDLE_WAIT_FOR_PB_ACK, TIMEOUT_WAITING_FOR_ACK);
                }
                break;
            case HANDLE_CALLOG_REQ:
                if(mCallLogReader == null) {
                    mCallLogReader = new CallLogReader(msg.arg1);
                    mCallLogsIndex = 0;

					if (mCallLogReader.getCount() <= 0) {
	                    mLogger.i(" No Call Logs Found!!");
	                    mCallLogReader = null;
	                    mPNDProtocol.createCallLogString_V2P(null, true);
	                    return;
	                }
                }

                mPNDProtocol.createCallLogString_V2P(mCallLogReader.readCallLogPack(), mCallLogReader.isLast());
                 if(!mCallLogReader.isLast()) {
                    mCallLogWaitingForAck = true;
                    mDataHandler.sendEmptyMessageDelayed(HANDLE_WAIT_FOR_CALL_LOG_ACK, TIMEOUT_WAITING_FOR_ACK);
                 } else {
                    mCallLogWaitingForAck = false;
                    mCallLogReader= null;
                 }
            break;
            case HANDLE_TIME_AND_POSITION_UPDATE:
                int vehicleSpeed = mEventRepository.getSpeedEventValue();
                // Get current location
                Location location = null;
                try {
                    if(mLocationManager != null) {
                        location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                } catch(Exception e) {
                    mLogger.e("cannot get location", e);
                }

                if(location == null) {
                    mLogger.e("no current location available from manager");
                    // if couldn't get location from manager try to get last known location stored on mcu
                    if(Navigation.Instance().mLastLocationFromMcu != null) {
                        location = Navigation.Instance().mLastLocationFromMcu;
                        mLogger.v("current location available - received from mcu last know location");
                    } else {
                        mLogger.e("no current location available from mcu");
                    }
                } else {
                    mLogger.v("current location available - received from manager");
                }

                double longitude = 0;
                double latitude = 0;
                mLogger.i("location = " + location + " mLastGpsStatus = " + mLastGpsStatus);
                if (location != null) {
                    longitude = location.getLongitude();
                    latitude = location.getLatitude();
                }

                // get date and time
                Calendar calendar =  Calendar.getInstance();

                int dayOfMonth = calendar.get(Calendar.DATE);
                int month = calendar.get(Calendar.MONTH) + 1;
                int year = calendar.get(Calendar.YEAR);
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);

                mPNDProtocol.sendTimeAndPositionUpdate_V2P(longitude, latitude, mLastGpsStatus, year,
                                            vehicleSpeed,  month, dayOfMonth, hour,
                                            minute, second, mHfpSignal, mHfpBatteryLevel);
                mDataHandler.sendEmptyMessageDelayed(HANDLE_PB_REQUEST, TIME_AND_POSITION__MSG_INTERVAL);
                break;
            case HANDLE_WAIT_FOR_PB_ACK:
                mPBWaitingForAck = false;
                mDataHandler.removeMessages(HANDLE_PB_REQUEST);
                IndicationManager.setIndication(mContext,IndicationMgrParams.EIND_BT_BLINKING_BLUE_FAST_INFINITE, false);
                mLogger.w("Time out waiting for phonebook ack passed.");
                break;
            case HANDLE_WAIT_FOR_CALL_LOG_ACK:
                mCallLogWaitingForAck = false;
                mDataHandler.removeMessages(HANDLE_CALLOG_REQ);
                mLogger.w("Time out waiting for callLog ack passed.");
                break;
            case HANDLE_KEEPALIVE_WAIT_TIMEOUT:
                mLogger.e("Missed one keep alive message.");
                mMissedKeepAliveCount++;
                if(mMissedKeepAliveCount == 2) {
                    //declare Indash Disconnected.
                    mPNDProtocol.connectionLost();
                } else {
                    mDataHandler.sendEmptyMessageDelayed(HANDLE_KEEPALIVE_WAIT_TIMEOUT, TIMEOUT_WAITING_FOR_KEEPALIVE);
                }
                break;
            }
        }
    };

    /**
     * handles keep alive message when received, 
     * if three keep alive messages are received in less than 1 second,
     * this indicates a connection problem then the connection will be discarded
     * if connection succeeds, initiation method is called.
     */
    public boolean handleKeepAliveMessage() {
 //       if(mKeepAliveCount == 0) {
 //           mStartTime = SystemClock.uptimeMillis();
 //           mKeepAliveCount = 1;
 //       } else {
 //           if((SystemClock.uptimeMillis() - mStartTime) > 1000) {
 //               mStartTime = SystemClock.uptimeMillis();
 //               mKeepAliveCount = 1;
 //           } else {
 //               mKeepAliveCount++;
//                mStartTime = SystemClock.uptimeMillis();
//                if (mKeepAliveCount > 3) {
//                    mKeepAliveCount = 0;
//                    return false;
//                }
//            }
//        }
        initiate();
        return true;
    }

    public void handlePeriodicKeepAlive() {
        mDataHandler.removeMessages(HANDLE_KEEPALIVE_WAIT_TIMEOUT);
        mMissedKeepAliveCount = 0;
        mDataHandler.sendEmptyMessageDelayed(HANDLE_KEEPALIVE_WAIT_TIMEOUT, TIMEOUT_WAITING_FOR_KEEPALIVE);
        mPNDProtocol.sendPeriodicKeepAlive_V2P(1);
    }

    public void handleDTMFsend(char srcOp) {
    	RoadTrackDispatcher.getInstance().callSendDtmf(srcOp);
    }

     /**
     * class holding connected HFP status data
     */
    private class HFPStatus {
        String  mHFPAddress;
        boolean mHFPConnected;
        int     mHFPPhoneBookStatus;
    }

    /**
     * initiation function
     * - sends Keep alive message to stablize the connection
     * - sends info request to PND
     * - if there are queued route messages it sends them to PND
     * - if there are queued text messages it sends them to PND 
     */
    private void initiate() {
        mLogger.d("starting initiate");
        mPNDProtocol.sendKeepAliveMessage_V2P();
        createPndInfoRequest();

        if(mNavigationMgr.getQueuedRoutesCount() > 0) {
            mDataHandler.sendEmptyMessage(HANDLE_PND_SEND_ROUTE);
        }

        if(queuedTextMsgsCount() > 0) {
            mDataHandler.sendEmptyMessage(HANDLE_PND_SEND_TXTMSG);
        }

        if(mHFPStatus.mHFPConnected == true) {
            mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected,
                   mHFPStatus.mHFPPhoneBookStatus,
                   mHFPStatus.mHFPAddress);
        }

        if ((mIsMounted) && (mIsReady)){
            if(mScanProgress == 100) {
                mPNDProtocol.sendMediaConnectStatus(mAvrcpAddress, true, MEDIA_SYNC_STATUS.SYNC_ENDED_SUCCEFULLY.ordinal());
            }
        }

        // Send current call status.
        sendActiveCallStatus(RTCallTracker.getInstance().getGsmCallStatus() , false);
        sendActiveCallStatus(RTCallTracker.getInstance().getBtCallStatus(), true);
        
        // Send navigation current state.
        if(mNavigation != null){
            mNavigation.sendNavStateToApp();
        } else {
        	sendRouteStateToApp(Navigation.ROUTE_STATE_INACTIVE, "", "", "");
        }

        flushMobileOtaStatusMessages();

        if(mSavedAudioRequest != null) {
            sendAudioRequestToIndash(mSavedAudioRequest.mStreamType, mSavedAudioRequest.mNewOutputDevice, mSavedAudioRequest.mStartOrStopRequest);
            mSavedAudioRequest = null;
        }

        sendP8Model();
    }

    private void sendP8Model() {
        if(isIndashConfigured()) {
            return;
        }

        int btEnable = mPconf.GetIntParam(PConfParameters.BT_Enable,0);

        mPNDProtocol.sendP8Model_V2P((btEnable == 1) ? PNDProtocol.eModelPlatinum8.MODEL_P8I : PNDProtocol.eModelPlatinum8.MODEL_P8);
    }

    /********************************************************************************************************
                                            CallLog Handling   
    ********************************************************************************************************/
    /*
     * Handles call log request 
     * Ignores sources type, sends list of all phones logs.
     * number of records is the number per call type to be sent.
     */
	public void handleCallLogRequest(int source, int numberOfRecords, String hfpAddress) {
        if(!mHFPStatus.mHFPConnected) {
            mPNDProtocol.sendCallLogStatus_V2P(PNDProtocol.CALL_LOG_STATUS_HFP_NOT_CONNECTED);
            mLogger.d("HFP not connected.");
            return;
        }

        if((mHFPStatus.mHFPPhoneBookStatus != PNDProtocol.PHONEBOOK_SYNC_ENDED_SUCCESSFULLY) &&
            (mHFPStatus.mHFPPhoneBookStatus != PNDProtocol.PHONEBOOK_SYNC_ENDED_WITH_CHANGE)) {
            mPNDProtocol.sendCallLogStatus_V2P(PNDProtocol.CALL_LOG_STATUS_NOT_AVAILABLE);
            mLogger.d("CallLog still not synced");
            return;
        }

        if(!mHFPStatus.mHFPAddress.equals(hfpAddress)) {
            mPNDProtocol.sendCallLogStatus_V2P(PNDProtocol.CALL_LOG_STATUS_FAILURE);
            mLogger.d("HFP doesn't match the connected.");
            return;
        }

        mDataHandler.obtainMessage(HANDLE_CALLOG_REQ, numberOfRecords, 0).sendToTarget();
    }

    public class CallLogReader {
        private int DEVICE_NONE = 0;
        private int DEVICE_GSM  = 1;
        private int DEVICE_BT   = 2;

        public class CallLogItem {
            public String mDisplayName;
            public String mPhoneNumber;
            public long   mLogDate;
            public int    mPhoneType; // 1 - BT / 2 - GSM
            public int    mCallType;

            private static final int BT_CALL  = 1;
            private static final int GSM_CALL = 2;

            private static final int UNKNOWN_CALL_TYPE = 0;
            private static final int LAST_DIALED       = 1;
            private static final int LAST_RECEIVED     = 2;
            private static final int LAST_MISSED       = 3;

            public int getCallType() {
                switch(mCallType) {
                    case CallLog.Calls.MISSED_TYPE:
                        return LAST_MISSED;
                    case CallLog.Calls.INCOMING_TYPE:
                        return LAST_RECEIVED;
                    case CallLog.Calls.OUTGOING_TYPE:
                        return LAST_DIALED;
                }
                return UNKNOWN_CALL_TYPE;
            }
        }

        private int mPerTypeLogs = 0;
        private boolean mIsLast = false;
        private List<CallLogItem> mCallLogList = null;

        /**
         * constructor making a query on contacts provider data and saving the cursor for use
         */
        public CallLogReader(int perTypeLogs) { 
            mPerTypeLogs = perTypeLogs;
            readCallLogLists();
        }

        /**
         * returns count of call log list size
         */
        public int getCount() {
            return mCallLogList.size();
        }

        /**
         * returns true if the package of calllogs is the last in data base
         */
        public boolean isLast() {
            return mIsLast; 
        }

        private void readCallLogLists() {
            mCallLogList = readCallLog(CallLog.Calls.MISSED_TYPE);
            mCallLogList.addAll(readCallLog(CallLog.Calls.OUTGOING_TYPE));
            mCallLogList.addAll(readCallLog(CallLog.Calls.INCOMING_TYPE));
        }

        private  List<CallLogItem> readCallLog(int logType) {
            Cursor cursor = null;
            List<CallLogItem> callLogList = new ArrayList(); 
            try {
                String[] projection = new String[] {   
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.PHONE_TYPE,
                };

                String OrderBy = String.format(CallLog.Calls.DATE + " DESC");

                cursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, 
                                                                projection, 
                                                                CallLog.Calls.TYPE + "=?", 
                                                                new String[]{String.valueOf(logType)}, 
                                                                OrderBy);

                
                if (null == cursor || cursor.isAfterLast()) {
                    mLogger.d("Call Log List is Empty!");
                    return callLogList;
                }

                int indxNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int indxName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int indxDate = cursor.getColumnIndex(CallLog.Calls.DATE);
                int indxphoneType = cursor.getColumnIndex(CallLog.Calls.PHONE_TYPE);

                int count = 0;
                mLogger.d("mPerTypeLogs = " + mPerTypeLogs);
				while (cursor.moveToNext() && (count < mPerTypeLogs)) {
                    CallLogItem call = new CallLogItem();

                    call.mPhoneNumber = cursor.getString(indxNumber);
                    if((SpecialPhoneNumbers.IsZiltokCall(mContext, call.mPhoneNumber)) || 
                        (SpecialPhoneNumbers.IsOfficeNumber(mContext, call.mPhoneNumber))) {
                        continue;
                    }
                    call.mDisplayName = cursor.getString(indxName);;
                    call.mLogDate = cursor.getLong(indxDate);
                    call.mPhoneType = cursor.getInt(indxphoneType);
                    call.mCallType = logType;

                    mLogger.d( "number:" +  call.mPhoneNumber);
                    mLogger.d( "name:" +  call.mDisplayName);
                    mLogger.d( "date:" + call.mLogDate);
                    mLogger.d( "device:" + call.mPhoneType);
                    mLogger.d( "call type: " + call.mCallType );

                    callLogList.add(call);
                    count ++;
                }
            } catch (Exception e) {
                mLogger.e( "UserRequestCallLog, e = ", e);
            } finally {
                if ( cursor != null ) {
                    cursor.close();
                }
                return callLogList;
            }
        }

        /**
         * returns a list of different PND_MAX_RECORDS call logs
         */
        public List<CallLogItem> readCallLogPack() {
            int listSize = getCount();
            List<CallLogItem> callLogList = null;
            if((mCallLogsIndex + PND_MAX_RECORDS) < listSize) {
                callLogList = mCallLogList.subList(mCallLogsIndex, mCallLogsIndex + PND_MAX_RECORDS);
                mCallLogsIndex += PND_MAX_RECORDS;
            } else {
                callLogList = mCallLogList.subList(mCallLogsIndex, listSize - 1);
                mIsLast = true;
            }

            return callLogList;
        }
    }

    public void handleCallLogAckFromIndash() {
        if (mCallLogWaitingForAck == true) {
            mLogger.d("Call Log Ack waited for is received.");
            mDataHandler.removeMessages(HANDLE_WAIT_FOR_CALL_LOG_ACK);
            mDataHandler.sendEmptyMessage(HANDLE_CALLOG_REQ);  
        } else {
            mLogger.w("Received an unexpected call log ack" );
        }
    }

    /********************************************************************************************************
                                            PhoneBook Handling   
    ********************************************************************************************************/

    public void handlePhoneBookAckFromIndash() {
        if (mPBWaitingForAck == true) {
            mLogger.d("Ack waited for is received.");
            mDataHandler.removeMessages(HANDLE_WAIT_FOR_PB_ACK);
            mDataHandler.sendEmptyMessage(HANDLE_PB_REQUEST);  
        } else {
            mLogger.w( "Received an unexpected phonebook ack" );
        }
    }

    /**
     * called when a phone book request is received
     * to start the sync process of phone book with PND
     */
    public void handlePhoneBookRequest() {
        IndicationManager.setIndication(mContext,IndicationMgrParams.EIND_BT_BLINKING_BLUE_FAST_INFINITE, true);
        mDataHandler.sendEmptyMessage(HANDLE_PB_REQUEST);
    }

    /**
     * helper class used to read phone book from Contacts content provider
     */
    public class PhoneBookReader {
        /**
         * contains data on contact
         * - display name
         * - phone numbers list
         */
        public class ContactItem {
            public String   mId;
            public long   mContactId;
            public String mDisplayName;
            public List<PhoneData> mPhoneList;            

            public ContactItem() {
                mPhoneList = new ArrayList();
            }

            public int getPhoneNumCount(){
                return mPhoneList.size();
            }

            public String readPhoneNumber(int indx) { 
                return mPhoneList.get(indx).mPhone;
            }

            public int readPhoneType(int indx) {
                return mPhoneList.get(indx).mPhoneType;
            }

            public void addPhoneData(String phoneNum, int phoneType) {
                PhoneData phoneData = new PhoneData();
                phoneData.mPhone = phoneNum.replaceAll("-", "");;
                phoneData.mPhoneType = phoneType;

                mPhoneList.add(phoneData);
            }

            public class PhoneData {
                public String mPhone;
                public int mPhoneType;
            }
        }

        private boolean mIsLast = false;
        private List<BTContactsCache.CacheContactEntry> mContactsCacheList = null;
        /**
         * constructor making a query on contacts provider data and saving the cursor for use
         */
        public PhoneBookReader() { 
            mContactsCacheList = BTContactsCache.getInstance().getContactsCache("", "", "", true);
            if (mContactsCacheList.size() == 0) {
                mLogger.v("ContactsCache List is empty");
            }
        }

        public int getCount() {
            return mContactsCacheList.size();
        }

        /**
         * returns true if the package of contacts is the last in data base
         */
        public boolean isLast() {
            return mIsLast; 
        }

        /**
         * converts from phoneType in database
         * to phone type as recognized by PND
         */
        private int getPhoneTypeId(int phoneType) {
            switch(phoneType) {
            case Phone.TYPE_MAIN:
                return 0;
            case Phone.TYPE_HOME:
                return 1;
            case Phone.TYPE_WORK:
            case Phone.TYPE_WORK_MOBILE:
                return 2;
            case Phone.TYPE_MOBILE:
                return 3;
            default:
                return 4;
            }
        }

        /**
         * returns a list of different PND_MAX_RECORDS contacts on each call
         */
        public List<ContactItem> readContacts() {
            List<ContactItem> contactItemList = new ArrayList();
            ContactItem contactItem = null;
            int listDataLength = 0;
            StringBuilder workBuffer = new StringBuilder();
            for ( int indx = mPhoneBookContactsIndex; indx < mContactsCacheList.size(); indx++ ) {
                contactItem = new ContactItem();
                workBuffer.append( mContactsCacheList.get(indx).firstname );
                workBuffer.append( " " );
                workBuffer.append( mContactsCacheList.get(indx).midname );
                workBuffer.append( " " );
                workBuffer.append( mContactsCacheList.get(indx).lastname );
                contactItem.mDisplayName = workBuffer.toString();

                // the buffer is reset to zero and used again to create a string and calculate it's length.                 
                workBuffer.setLength( 0 );
                workBuffer.append( contactItem.mDisplayName );
                workBuffer.append( "," );
                workBuffer.append( contactItem.mContactId );

                int numbersCount = 1;
                for(BTContactsCache.Phone phoneNumber : mContactsCacheList.get(indx).phoneNumbers) {
                    contactItem.addPhoneData(phoneNumber.number, phoneNumber.type);

                    workBuffer.append( "," );
                    workBuffer.append( phoneNumber.number);
                    workBuffer.append( "," );
                    workBuffer.append( phoneNumber.type );
                    
                    numbersCount++;
                    if(numbersCount > MAX_PHONE_NUMBER_PER_CONTACT) { 
                        break; 
                    }
                }

                listDataLength += workBuffer.length();
                workBuffer.setLength( 0 );

                mLogger.d("mPhoneBookContactsIndex = " +mPhoneBookContactsIndex + " listDataLength = " + listDataLength);
                if(listDataLength > 400){
                    break;
                } else {
                    contactItemList.add(contactItem);
                    mPhoneBookContactsIndex++;
                }
            }

            if((mContactsCacheList.size() - mPhoneBookContactsIndex) <= 0) {
                mIsLast = true;
            }
            return contactItemList;
        }
    }

    /********************************************************************************************************
    										Route Handover messages
    *********************************************************************************************************/

    /**
     * called when updating the gen2app with current route status
     */
    public void sendRouteStateToApp(int routeState, String destName, String routingLatitude, String routingLongitude){
    	mPNDProtocol.sendRouteStateToApp_V2P(routeState, destName, routingLatitude, routingLongitude);
    }
    
    public void pauseNavigationFromApp(){
    	if(!isIndashConfigured()){
    		Log.d(TAG, "received pause navigation request from app");
    		mNavigation.stopNavigationAndAnnounce(false);
    	}
    }
    
    public void stopIndashNaviFlows(){
    	stopHandover();
    	stopIndashNavi();
    }
    
    public void stopHandover(){
    	try {
    		if(mRouteHandOverSendObject != null){
    			mRouteHandOverSendObject.mKeepRunning = false; 	
    			mRouteHandOverSendObject 	= null;
    		}
    		if(mRouteHandOverReceiveObject != null){
    			mRouteHandOverReceiveObject.mKeepOnRuning = false;
    			mRouteHandOverReceiveObject = null;
    		}
    	} catch (Exception e) {
    		mLogger.d("couldnt null thread, ",e);
    	}
    }
    
    // send pause command to pause indash navigation
    public void stopIndashNavi(){
    	if(isIndashConfigured() && isIndashNaviActive()){
    		mPNDProtocol.pauseIndashNavigation();
    	}
    }

    /**
     * this function called when a hand over is received from the gen2app
     * @param routeData
     * @param aboutToSendWayPoints
     */
    public void startRouteHandOver(RouteData routeData, int etaMinutes, boolean aboutToSendWayPoints){
    	mLogger.d("starting Route HandOver");
    	if(mRouteHandOverReceiveObject == null){
    		mRouteHandOverReceiveObject = new RouteHandOverReceiveObject(routeData, etaMinutes, aboutToSendWayPoints); 
    	}
    }
    
    /**
     * called when new update points msgs is received from app as part of the handover process
     * @param wayPoints
     * @param msgsLeft
     */
    public void handleHandoverWaypointsUpdate(ArrayList<Location> wayPoints, int msgsLeft){
    	if(mRouteHandOverReceiveObject != null){
    		mRouteHandOverReceiveObject.updateWayPoints(wayPoints, msgsLeft);
    	}
    }
    
    /**
     * this function called when a hand over request is received from the gen2app
     */
    public void requestRouteHandOver(){
    	if(mNavigation != null){
    		if(mNavigation.getState() == Navigation.NavigationState.STARTED){
    			if(mRouteHandOverSendObject == null){
    				mRouteHandOverSendObject = new RouteHandOverSendObject(null);
    			} 
    		} 
    	} 
    }

    public void ackFromAppReceived(){
    	if(mRouteHandOverSendObject != null){
    		mRouteHandOverSendObject.handleAckFromApp();
    	} else {
    		mLogger.d("received ack from app but no handover process is active at the moment"); 
    	}
    }
    
    /**
     * 
     * @return traffic from navigation in case available if not returns null
     */
    public TrafficRoutingServices.Route getTrafficFromNavigation(){
    	if(mNavigation != null){
    		return mNavigation.getTrafficFromNav();
    	} else {
    		return null;
    	}
    }
    
    /**
     * 
     * @return ETA based on the last available guidance info received from CM
     */
    public int getCmETA(){
    	int ETA = 0;
    	if(mNavigation != null){
    		GuidanceInfo lastGuidanceInfo = mNavigation.getLastGuidanceFromNav();
    		if(lastGuidanceInfo != null) {
    			ETA = ((int)lastGuidanceInfo.mTimeToDestination) / 60 ;
    		}
    	}
    	return ETA;
    }
    
    /**
     * 
     * @return elapsed time in minutes from beginning of current navigation
     */
    public int getElapsedTimeFromNaviStart(){
    	if(mNavigation != null){
    		return mNavigation.getElapsedTimeFromNaviStart();
    	} else {
    		return 0;
    	}
    }
    
    
    // sends handover process failed indication to the gen2app
    private void indicateHandoverFailed(int failureReason){
		mLogger.d("Handover failed");
		mPNDProtocol.sendHandoverFailure_V2P(failureReason);
	}
	


    /**
     *  this object is used for running the thread that handles
     *  GMLan Radio Routes, sent to the MCU.
     */
    private class RouteGmLanObject {
        private Thread                      mHandoverThread     = null;  
        private RouteData                   mRouteTarget        = null;
        private boolean						mInfotainmentAllowed = false;
        public volatile boolean             mKeepRunning        = false;

        
        /**
         * @param routeTarget
         */
        public RouteGmLanObject(final RouteData routeTarget){
            mInfotainmentAllowed =  mEventRepository.getIsInfotainmentAllowed();
            routeTarget.routingTrafficUsed = false;
            if(mInfotainmentAllowed && mEventRepository.getMirrorPowerState()) {
                routeGmlanHandover(routeTarget);
            } else {
                mHandoverThread = new Thread(new Runnable() {
                    
                    @Override
                    public void run() {
                        mLogger.d("started handover thread");
                        mKeepRunning = true;
                        // if the route was sent when Ignition was off, start thread to sent route when Ignition turns on 
                        while(mKeepRunning) {
                        	mInfotainmentAllowed =  mEventRepository.getIsInfotainmentAllowed();
                            if(mInfotainmentAllowed && mEventRepository.getMirrorPowerState()) {
                                if ( mNavigationMgr.getQueuedRoutesCount() > 0 ) {
                                    mRouteTarget = mNavigationMgr.pullApprovedQueuedRoutes();
                                    if(null != mRouteTarget)
                                    {
                                    	routeGmlanHandover(mRouteTarget);
                                    }
                                    else
                                    {
                                    	mLogger.e("RouteGmLanObject: mRouteTarget is null !");
                                    }
                                }
                                mKeepRunning = false;
                            }

                            try {
                                // Sleep one second
                                Thread.sleep(1000);
                            } catch (Exception e) {
                                mLogger.e("Exception ", e );
                            }
                        }                   
                    }
                });
                mHandoverThread.start();
            }
        }
        

        /**
         * sends the current route destination way point to the MCU
         */
        private void routeGmlanHandover(RouteData routeData){
            mrouteGmlanData = new RouteGmlanData();
            mrouteGmlanData.routingLatitude = Float.parseFloat(routeData.routingLatitude);
            mrouteGmlanData.routingLongitude = Float.parseFloat(routeData.routingLongitude);
            mrouteGmlanData.routingText = routeData.routingText;
            mLogger.d("Send Route to Gmlan Radio" + mrouteGmlanData.toString());
            sendGmlanRouteToMCU(mrouteGmlanData);
        }
        
    }


    /**
     * returns gmlan connection status based on configuration parameter
     */
    public boolean isGmlanRadioConfigured(){
        return mGmlanNavConfig;
    }

    /**
     *	this object is used for running the thread that handles
     *  all hand over process.
     *  the process includes the passing of the route to the app.   
     */
    private class RouteHandOverSendObject {
    	private boolean 						  mFirstMsg	 			= true;
    	private int 	  						  mMsgsLeftToSend 		= 0;
    	private int 	  						  mTimeOutCounter 		= 0;
    	private ArrayList<String> 				  mMsgsList				= null;
    	private Thread   						  mHandoverThread 		= null;
    	private TrafficRoutingServices.Route     mTrafficData			= null;    
    	private RouteData						  mRouteTargetIndash    = null;
    	public volatile boolean				        mKeepRunning          = false;
    	
    	/**
    	 * the passed RouteData indicates whether the target to hand over the route to is indash or not.
    	 * if the passed parameter is null it means the target is not indash and if its not null the target is indash
    	 * and the routedata that was received should be passed to it.
    	 * this is important as in the case of indash there is no traffic involved in the process.
    	 * @param routeTargetIndash
    	 */
    	public RouteHandOverSendObject(final RouteData routeTargetIndash){
			mRouteTargetIndash = routeTargetIndash;
    		mHandoverThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					mLogger.d("started handover thread");
					mKeepRunning = true;
					// if the target is indash we only need to pass the destination details and if needed the indash 
					// will request for the traffic.
					if(routeTargetIndash == null){
						mTrafficData = getTrafficFromNavigation();
						if(mTrafficData != null){
							ArrayList<Location> locationList = mNavigation.getCurrentDestinationAndWaypoints();
							if(null != locationList)
							{
								mMsgsList = createMsgsArray(locationList, mMsgFreeSpaceGenToApp, false);
								mMsgsLeftToSend = mMsgsList.size();
							}
							else
							{
								mLogger.e("RouteHandOverSendObject: locationList is null !");
							}
						}
					}
					while(mKeepRunning){
						// passed allowed number of attempts, announce user and exit
						if(mTimeOutCounter >= mNumberAllowedTimesOut){
							indicateHandoverFailed(HANDOVER_FAILURE_REASON_TIMEOUT);
							stopHandover();
							return;
						}
						
						// first message to send is the start hand over message
						if(mFirstMsg){
							routeHandover(mRouteTargetIndash);
						} else {
							if(mTrafficData != null){
								// check if this is the last way points message to be sent and pass counter, 0 is the last msg
								sendWayPoints(mMsgsLeftToSend);
								
							// no way points to be sent as there is no traffic available
							} else {
								mKeepRunning = false;
								return;
							}
						}
						
						try{
							mLogger.d("waiting for ack from app");
							Thread.sleep(mHandoverTimeout);
							// woke up from sleep meaning to interrupt was received therefore no ack received - incrementing timeout counter
							mTimeOutCounter++;
						} catch (InterruptedException e){
							mLogger.d("received ack from app");
							if(mFirstMsg){
								mFirstMsg = false;
							} else {
								mMsgsLeftToSend--;
							}
							mTimeOutCounter = 0;
							
							// if ack received is on the last message to be sent, we can stop navigation and finish hand over.
							// if traffic is null it means we had to send only the route, in this case we finished sending as well
							if((mTrafficData == null) || (mMsgsLeftToSend == 0)){
								mLogger.d("finished sending, received last ack");
								mKeepRunning = false;
								// if the target is indash the navigation is not running at all so no need stopping it.
								if(mRouteTargetIndash == null){
									// stopping with useinactive set to true since the route just moved to the app no reason keeping 
									// it for future use on the P8
									if(mNavigation != null){
										mNavigation.stopNavigation(true);
										stopHandover();
									}
								}
							}
						}
					}					
				}
			});
			mHandoverThread.start();
		}
		
		private void handleAckFromApp(){
			mHandoverThread.interrupt();
		}

	    /**
	     * sends the current route destination way point to the gen2app
	     */
	    private void routeHandover(RouteData routeTargetIndash){
	    	mLogger.d("sending route to app");
	    	boolean withWayPoints = false;
	    	int ETA = 0;
	    	RouteData routeData = null;
	    	// check if target is indash.
	    	if(routeTargetIndash == null){
	    		// send destination way point to the gen2app as part of the handover
	    		routeData = mNavigationMgr.pullApprovedQueuedRoutes();
	    		if(routeData != null){

	    			if( mTrafficData != null ) {
	    				// in case traffic is available we want to take the following ETA:
	    				// Max(ETAtraffic - ElapsedTimeFromNaviStart, ETAcm)
	    				// this is done to provide the application the most relevant ETA we can in case this is not the very beginning of
	    				// the navigation at the point the handover was requested.
	    				int ETAtraffic = (int)mTrafficData.TravelTimeMin;
	    				int ETAcm      = getCmETA();
	    				int ElapsedTimeFromNaviStart = getElapsedTimeFromNaviStart();
	    				ETA = Math.max((ETAtraffic - ElapsedTimeFromNaviStart), ETAcm);
	    				withWayPoints = true;
	    			// if there is no traffic use last guidance info
	    			} else {
	    				ETA = getCmETA();
	    			}

	    		}
	    	} else {
	    		// if the target is indash send only destination details no traffic info is needed for now.
	    		routeData = routeTargetIndash;
	    	}
	    	if(routeData != null){
	    		mPNDProtocol.sendRouteToApp_V2P(routeData.routingLatitude,
	    				routeData.routingLongitude,
	    				routeData.routingText, 
	    				String.valueOf(ETA), 
	    				withWayPoints);
	    	}
	    }

	    /**
	     * sends the way points to the pnd and the number of messages left to be sent.
	     * 
	     * @param msgsLeftToSend - number of messages remains to be sent not including this msg.
	     * if msgsLeftToSend = 0 it means this is the last msg.
	     */
	    private void sendWayPoints(int msgsLeftToSend){
	    	mLogger.d("sending waypoints message");
	    	// sending the msgsLeftToSend decreased by one because it says how many msgs left besides this one 
	    	mPNDProtocol.sendWayPointsToApp_V2P(mMsgsList.get(mMsgsList.size() - msgsLeftToSend), msgsLeftToSend - 1);
		}
		
    }
    
    /**
	 * parses all the way points and creates a string including all way points and the relevant delimiters.
	 * @return returns the way points string
	 */
	private ArrayList<String> createMsgsArray(ArrayList<Location> wayPoints, int msgSize, boolean forIndash){
		ArrayList<String> msgsList = new ArrayList<String>();
		StringBuilder msgString  = new StringBuilder(msgSize);
		int length = 0;
		int recordLength = 0;
		String latitude = "";
		String longtitude = "";
		boolean firstWayPoint = true;
		for(Location location : wayPoints){
			// we don't want to pass the first waypoint from the array because it is the destination location.
			// so the first waypoint is not added to the array.
			// this is not valid for the case of traffic calculated for indash because then the first waypoint is part of the route.
			if(!forIndash){
				if(firstWayPoint){
					firstWayPoint = false;
					continue;
				}
			}
			latitude   = String.valueOf(location.getLatitude());
			longtitude = String.valueOf(location.getLongitude());
			// we measure the length of the record consists of data separator + latitude + inner separator + longitude 
			recordLength = 1 + latitude.length() + PNDProtocol.RECORD_INNER_SAPERATOR.length() + longtitude.length();
			if((length + recordLength) > msgSize){
				msgsList.add(msgString.toString());
				msgString.setLength(0);
				length = 0;
			}
			msgString.append(PNDProtocol.PND_DATA_SEPARATOR);
			msgString.append(latitude);
			msgString.append(PNDProtocol.RECORD_INNER_SAPERATOR);
			msgString.append(longtitude);
			length += recordLength;
		}
		// adding the record that accumulated but wasn't added to the list during the for loop
		msgsList.add(msgString.toString());
		return msgsList;
	}
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    
    private class RouteHandOverReceiveObject{
    	public  volatile  boolean 			mKeepOnRuning 				= false;
    	private Thread 				 			mRouteHandOverReceiveThread = null;
    	private RouteData 			 			mRouteData 				 	= null;
    	private float			     			mTravelTimeMin				= 0f;
    	private boolean 			 			mAboutToSendWayPoints 		= false;
    	private boolean 			 			mReceivedRouteMsg 			= false; 
    	private boolean 			 			mFirstMsg 					= true;
    	private boolean			 			mFirstUpdateMsg			 	= true;
    	private boolean 						mWaitingForFirstWayPoints	= false;
    	private int 				 			mMsgsLeft	  	 			= -1;
    	private int 				 			mMsgsLeftBeforeCurrentMsg	= 0;
    	private int 				 			mTimeoutCounter 			= 0;
    	private ArrayList<Location> 			mWayPoints					= null;
    	
    	
    	public  RouteHandOverReceiveObject(final RouteData routeData, final int etaMinutes, final boolean aboutToSendWayPoints){
    		mWayPoints = new ArrayList<Location>();
    		mRouteHandOverReceiveThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					mRouteData = routeData;
					mTravelTimeMin = etaMinutes;
					mAboutToSendWayPoints = aboutToSendWayPoints;
					mKeepOnRuning = true;
					while(mKeepOnRuning){
						if(mTimeoutCounter >= mNumberAllowedTimesOut){
							indicateHandoverFailed(HANDOVER_FAILURE_REASON_TIMEOUT);
							stopHandover();
							return;
						}
						
						// if first msg it means we received the route message and need to ack it.
						if(mFirstMsg){
							mFirstMsg = false;
							mPNDProtocol.sendAckToApp_V2P(Navigation.AckTypeForApp.HANDOVER_SUCCESS);
							// if there are no way points about to be sent it means the hand over finished can calculate route
							if(!mAboutToSendWayPoints){
								startCalculating(null);
								stopHandover();
								return;
							} else {
								mWaitingForFirstWayPoints = true;
							}
						// in case waiting for way points to arrive
						} else {
							if(mReceivedRouteMsg){
								mReceivedRouteMsg = false;
								// need to make sure there is a consistency in the arriving messages order therefore there should always be
								// one message separating between the two counters.
								if((mMsgsLeftBeforeCurrentMsg - mMsgsLeft) != 1){
									// need to rewind the state of this members to get to the same position as they were before receiving 
									// last message
									if(mWaitingForFirstWayPoints){
										mMsgsLeftBeforeCurrentMsg = 0;
										mMsgsLeft = -1;
										mLogger.d("received message index doesn't fit to the expected index");
										mPNDProtocol.sendAckToApp_V2P(Navigation.AckTypeForApp.HANDOVER_SUCCESS);
									} else {
										mMsgsLeftBeforeCurrentMsg++;
										mMsgsLeft = mMsgsLeftBeforeCurrentMsg - 1; 
										mLogger.d("received message index doesn't fit to the expected index");
										mPNDProtocol.sendAckToApp_V2P(Navigation.AckTypeForApp.WAYPOINTS_FAILURE);
									}
								} else {
									mPNDProtocol.sendAckToApp_V2P(Navigation.AckTypeForApp.WAYPOINTS_SUCCESS);
								}

								// no messages left it means this is the last message
								if(mMsgsLeft == 0){
									TrafficRoutingServices 	trafficService = new TrafficRoutingServices(mContext); 				
									TrafficRoutingServices.Route traffic = trafficService.new Route();
									traffic.WayPoints = mWayPoints;
									startCalculating(traffic);
									stopHandover();
									return;
								}
							} else {
								// time out occurred - no message was received
								if(mWaitingForFirstWayPoints){
									// if haven't received a way points update yet need to notify again that we received 
									// the first route hand over message
									mPNDProtocol.sendAckToApp_V2P(Navigation.AckTypeForApp.HANDOVER_SUCCESS);
								} else {
									// if this is not a first way points message that we are waiting for
									// need to send a way points failure ack
									mPNDProtocol.sendAckToApp_V2P(Navigation.AckTypeForApp.WAYPOINTS_FAILURE);
								}
							}
						}

						try {
							// we wait twice as much time given for the sender of the route for each timeout
							Thread.sleep(mHandoverTimeout * 2);
							mTimeoutCounter++;
						} catch (InterruptedException e) {
							mReceivedRouteMsg = true;
							mTimeoutCounter = 0;
						}
						
					}
				}
			});
    		mRouteHandOverReceiveThread.start();
    	}
    	
    	/**
    	 * start the new received route calculation.
    	 * @param trafficRoute - in case way points exist pass them with the traffic param
    	 * otherwise pass null.
    	 */
    	private void startCalculating(TrafficRoutingServices.Route trafficRoute){
    		mLogger.d("sending traffic route to navigation manager for further handling");
    		if(trafficRoute != null){
    			trafficRoute.RouteName = mRouteData.routingText;
    			trafficRoute.TravelTimeMin = mTravelTimeMin;
    		}
    		mNavigationMgr.startNavigationWithRoute(mRouteData, true, trafficRoute, true);
    	}
    	
    	/**
    	 * handled the way points update by adding the new received way points to the 
    	 * list holding all the way points that were received until now for the current route.
    	 * @param wayPoints
    	 * @param msgsLeft
    	 */
    	public void updateWayPoints(ArrayList<Location> wayPoints, int msgsLeft){
    		mLogger.d("updating wayPoints - with msgs left : " + msgsLeft);
    		if(mRouteHandOverReceiveThread != null){
    			// need to verify this is not the same message with the same way points again.
    			if(mMsgsLeft != msgsLeft){
    				// if first update message, then we need to initialize the values with valid difference 
    				// because we don't know from what index it might start
    				if(mFirstUpdateMsg){
    					mMsgsLeft = msgsLeft;
    					mMsgsLeftBeforeCurrentMsg = mMsgsLeft + 1;
    					mFirstUpdateMsg = false;
    				} else {
    					mMsgsLeftBeforeCurrentMsg = mMsgsLeft;
    					mMsgsLeft = msgsLeft;
        			}
    				for(int i = 0; i < wayPoints.size(); i++){
    					mWayPoints.add(wayPoints.get(i));
    				}
    			} else {
    				// we want a failure ack to be sent during the loop so temporarily setting the values to be equal
    				mMsgsLeftBeforeCurrentMsg = mMsgsLeft;
    			}
    			// settings this to false to avoid from sending wrong ack
    			mWaitingForFirstWayPoints = false; 
    			mRouteHandOverReceiveThread.interrupt();
    		}
    	}
    	
    }

    /********************************************************************************************************
    										   audio messages Handling 
     *********************************************************************************************************/
    private static int P8_HAS_NO_AUDIO = 0;
    private static int P8_HAS_AUDIO    = 1;

    private class AudioRequest{
        byte mStreamType;
        byte mNewOutputDevice;
        int  mStartOrStopRequest;

        public AudioRequest(byte streamType, byte newOutputDevice, int startOrStopRequest) {
            mStreamType = streamType;
            mNewOutputDevice = newOutputDevice;
            mStartOrStopRequest = startOrStopRequest;
        }
    }

    private AudioRequest mSavedAudioRequest = null;
    /**
     * send audio request to indash, if this function is called, it means we already checked the connection status.
     * @param messageId
     * @param streamType
     * @param newOutputDevice
     * @param startOrStopRequest
     * @return
     */
    public boolean sendAudioRequestToIndash(byte streamType, byte newOutputDevice, int startOrStopRequest ){
    	if(mPNDProtocol != null){
            if(isConnected()) {
    		    mPNDProtocol.sendAudioRequestToIndash_V2P(streamType, newOutputDevice, startOrStopRequest);
                audioResponse(PNDProtocol.GENERIC_ACK_SUCCESS);
            }
            saveAudioRequest(streamType, newOutputDevice, startOrStopRequest);

    		return true;
    	} else {
    		return false;
    	}
    }

    private void saveAudioRequest(byte streamType, byte newOutputDevice, int startOrStopRequest ){
        if(mSavedAudioRequest != null && startOrStopRequest == P8_HAS_NO_AUDIO) {
            mSavedAudioRequest = null;
        } else {
            mSavedAudioRequest = new AudioRequest(streamType, newOutputDevice, startOrStopRequest);
        }
    }

    /**
     * received audio request response from PND protocol. 
     * @param status
     */
    public void audioResponse(int status){
    	mRoadTrackAudioOutputSwitch.handleAudioResponseFromIndash(status);
    }
    
    public void handleVolumeRequest(RoadTrackDispatcher.INDASH_VOLUME_REQUEST volumeAction){
    	if(mInDashConfig && isConnected()){
    		mPNDProtocol.sendVolumeRequest_V2P(volumeAction.ordinal());
    	}
    }
    /**********************************************************************************************************
     * 											Comm server messages
     **********************************************************************************************************/
    /**
     * handle poi search request received from the indash
     * @param cityName
     * @param POIName
     */
    public void handlePoiSearchRequest(String cityName, String POIName){
    	mLogger.d("handlePoiSearchRequest - get POI request received from indash");
		GoogleResult googleResult = null;
		try{
			// Find POI in specified destination
			mLogger.d("handlePoiSearchRequest: Get POIS by " + POIName + " and " + cityName);
			googleResult = mGoogleServices.getPOI( cityName,POIName );
			sendPoiAddrsResponse(googleResult);
			
		} catch(Exception ex){
			mLogger.d("handlePoiSearchRequest: Failed on Get POIS");
		}
    }
    
    /**
     * handle address search request received from the indash
     * @param cityName
     * @param streetNamem
     * @param houseNumber
     */
    public void handleAddressSearchRequest(String cityName, String streetNamem, String houseNumber){
    	mLogger.d("handleAddressSearchRequest - get address search request received from indash");
		GoogleResult googleResult = null;
		try{
			// Find address in specified destination
			mLogger.d("handleAddressSearchRequest: Get address by " + cityName + " and "  + streetNamem  );
			googleResult = mGoogleServices.getAddress(cityName, streetNamem, houseNumber);
			sendPoiAddrsResponse(googleResult);
			
		} catch(Exception ex){
			mLogger.d("handleAddressSearchRequest: Failed on Get address");
		}
    }
    
    private void sendPoiAddrsResponse(GoogleResult googleResult){
    	if(googleResult.resultStatus == GoogleResultStatus.STATUS_OK){
			mPNDProtocol.sendPoiAddressResponse_V2P(googleResult.address, PNDProtocol.INDASH_POI_ADDRESS_RESULT.SUCCESS);
		} else {
			mLogger.d("handleAddressSearchRequest: search failed");
			switch(googleResult.resultStatus){
			case STATUS_CONNECTIVITY_ISSUES:
				mPNDProtocol.sendPoiAddressResponse_V2P(googleResult.address, PNDProtocol.INDASH_POI_ADDRESS_RESULT.SERVER_UNAVAILABEL);
				break;
			case STATUS_NO_RESULTS:
				mPNDProtocol.sendPoiAddressResponse_V2P(googleResult.address, PNDProtocol.INDASH_POI_ADDRESS_RESULT.NO_RESULTS_FOUND);
				break;
			case STATUS_CHEVISTAR_CLOUD_ISSUES:
				mPNDProtocol.sendPoiAddressResponse_V2P(googleResult.address, PNDProtocol.INDASH_POI_ADDRESS_RESULT.SERVER_UNAVAILABEL);
				break;
			default:
				mPNDProtocol.sendPoiAddressResponse_V2P(googleResult.address, PNDProtocol.INDASH_POI_ADDRESS_RESULT.SERVER_UNAVAILABEL);
				break;
			
			}
		}
    }

    /**
     *  create new object that contains the thread that will deal with the whole sending and receiving process.
     * @param destLat
     * @param destLong
     * @param desiredRouteNumber
     */
    public void handleTrafficRequest(double[] origDestPoints, int desiredRouteNumber){
    	if(mHandleTrafficRequestObject == null){
    		mHandleTrafficRequestObject = new HandleTrafficRequestObject(origDestPoints, desiredRouteNumber);
    	}
    }
    
    /**
     * receives from pndprotocol the route selection sent from the indash
     * @param selectedRouteNumber
     */
    public void handleIndashTrafficRouteSelection(int selectedRouteNumber){
    	if(mHandleTrafficRequestObject != null){
    		mHandleTrafficRequestObject.startSendingWayPoints(selectedRouteNumber);
    	}
    }
    
    /**
     * stops the thread that handles the traffic sending to the indash
     */
    public void stopTrafficToIndash(){
    	mLogger.d("stoping traffic to indash thread");
    	if(mHandleTrafficRequestObject != null){
    		mHandleTrafficRequestObject.mKeepRunning = false;
    		mHandleTrafficRequestObject = null;
    	}
    }
    
    /**
     * handling ack received from indash as a response to a waypoints message
     */
    public void ackFromIndashReceived(){
    	if(mHandleTrafficRequestObject != null){
    		mHandleTrafficRequestObject.handleAckFromIndash();
    	} else {
    		mLogger.d("received ack from indash but no waypoints sending process is active at the moment"); 
    	}
    }
    
    private class HandleTrafficRequestObject {
    	private int 	  						  mMsgsLeftToSend 		= 0;
    	private int 	  						  mTimeOutCounter 		= 0;
    	private ArrayList<String> 				  mMsgsList				= null;
    	private Thread   						  mSendTrafficThread 	= null;
    	private Thread 							  mGetTrafficThread		= null;
    	private TrafficRoutingServices.Routes    mRoutes 				= null;   
    	private TrafficRoutingServices.Route     mTrafficData			= null; 
    	public volatile boolean				  mKeepRunning          = false;

    	public HandleTrafficRequestObject(final double[] origDestPoints,final int numberDesiredRoutes){
    		
    		// this thread is only used for getting the traffic routes and sending them back to the indash.
    		mGetTrafficThread = new Thread(new Runnable() {
				
				public void run() {
					mLogger.d("requesting traffic");
					mRoutes = mTrafficRoutingServices.getTrafficForRoute(origDestPoints, TrafficRoutingServices.MAX_NUM_ROUTES_TO_REQUEST);
					if(mRoutes == null){
						sendResponse(null, PNDProtocol.TRAFFIC_RESPONSE_RESULT.SERVER_NOT_AVAILABLE, 0, 0, 0);
						return;
					} else {
						sendResponse(mRoutes,
								PNDProtocol.TRAFFIC_RESPONSE_RESULT.SUCCESS,
								origDestPoints[2],
								origDestPoints[3],
								numberDesiredRoutes);
					}
				}
			});
    		mGetTrafficThread.start();
    	}
    	
    	private void sendTraffic(){
    		mSendTrafficThread = new Thread(new Runnable() {
				
				@Override
				public void run() {
					while(mKeepRunning){
						
						// passed allowed number of attempts, announce user and exit
						if(mTimeOutCounter >= mNumberAllowedTimesOut){
							sendResponse(null, PNDProtocol.TRAFFIC_RESPONSE_RESULT.GENERAL_ERROR, 0, 0, 0);
							stopTrafficToIndash();
							return;
						}
					
					
						if(mTrafficData != null){
							// check if this is the last way points message to be sent and pass counter, 0 is the last msg
							sendWayPoints(mMsgsLeftToSend);

							// no way points to be sent as there is no traffic available
						} else {
							stopTrafficToIndash();
							return;
						}
						
						try{
							mLogger.d("waiting for ack from app");
							Thread.sleep(mHandoverTimeout);
							// woke up from sleep meaning to interrupt was received therefore no ack received - incrementing timeout counter
							mTimeOutCounter++;
						} catch (InterruptedException e){
							mLogger.d("received ack from indash");
							mMsgsLeftToSend--;
							mTimeOutCounter = 0;
							
							if((mTrafficData == null) || (mMsgsLeftToSend == 0)){
								mLogger.d("finished sending, received last ack");
								stopTrafficToIndash();
								return;
							}
						}
						
					}					
				}
			});
			mSendTrafficThread.start();
		}
    	
    	private void sendResponse(TrafficRoutingServices.Routes routes,
    							    PNDProtocol.TRAFFIC_RESPONSE_RESULT trafficResult,
    							    double destLat,
    							    double destLong,
    							    int numberDesiredRoutes){
    		mPNDProtocol.sendTrafficResponse_V2P(routes, trafficResult, destLat, destLong, numberDesiredRoutes);
    	}
    	
    	/**
	     * sends the way points to the indash and the number of messages left to be sent.
	     * 
	     * @param msgsLeftToSend - number of messages remains to be sent not including this msg.
	     * if msgsLeftToSend = 0 it means this is the last msg.
	     */
	    private void sendWayPoints(int msgsLeftToSend){
	    	mLogger.d("sending waypoints message");
	    	// sending the msgsLeftToSend decreased by one because it says how many msgs left besides this one 
	    	mPNDProtocol.sendWayPointsToIndash_V2P(mMsgsList.get(mMsgsList.size() - msgsLeftToSend), msgsLeftToSend - 1);
		}
    	
    	// set the selected route as the traffic data and start sending the waypoints to the indash
	    public void startSendingWayPoints(int selectedRouteIndex){
	    	mLogger.d("selectedRouteIndex " + selectedRouteIndex );
	    	if((selectedRouteIndex < 0) || (selectedRouteIndex > (mRoutes.Routes.length - 1))){
    			sendResponse(null, PNDProtocol.TRAFFIC_RESPONSE_RESULT.GENERAL_ERROR, 0, 0, 0);
    			return;
    		}
    		mTrafficData = mRoutes.Routes[selectedRouteIndex];
    		// TODO - the message size for the indash maybe different then the one defined for the chevistar app
    		// (though the limitation was originally taken from the indash definitions)
    		mMsgsList = createMsgsArray(mTrafficData.WayPoints, mMsgFreeSpaceIndash, true);
    		mMsgsLeftToSend = mMsgsList.size();

    		mKeepRunning = true;
    		sendTraffic();
    	}
    	
    	
    	public void handleAckFromIndash(){
    		mSendTrafficThread.interrupt();
    	}
    }
    
    static final int SPEED_TOP_VALUE_LIMIT 	= 150;
    static final int SPEED_LOWEST_VALUE_LIMIT = 10;

    /**
     * handle speed alert configuration request.
     * the options are either disable it or activate for one alert.
     * @param activate
     * @param speedThreshold
     */
    public void handleSpeedAlertConfig(boolean activate, int speedThreshold){
    	int configResult = PNDProtocol.GENERIC_ACK_SUCCESS;

    	if(activate){
    		if(speedThreshold > 0)
    		{
    			// if threshold was part of the message its value should be bigger then 0, which means, we need to check it is within
    			// the valid limits.
    			if((speedThreshold < SPEED_LOWEST_VALUE_LIMIT) || (speedThreshold > SPEED_TOP_VALUE_LIMIT)){
    				configResult = PNDProtocol.GENERIC_ACK_FAILURE;
    			} else {
    				if(mPconf.SetIntParam(PConfParameters.SpeedControlAlert_SpeedLimit,speedThreshold) != PConf.PC_OK){
    					configResult = PNDProtocol.GENERIC_ACK_FAILURE;
    				}
    				if(mPconf.SetIntParam(PConfParameters.SpeedControlAlert_Enable,SettingsHandler.ACTIVATE_ONCE) != PConf.PC_OK){
    					configResult = PNDProtocol.GENERIC_ACK_FAILURE;
    				}
    			}
    		} else {
    			if(mPconf.SetIntParam(PConfParameters.SpeedControlAlert_Enable,SettingsHandler.ACTIVATE_ONCE) != PConf.PC_OK){
    				configResult = PNDProtocol.GENERIC_ACK_FAILURE;
    			}
    		}
    	} else {
    		if(mPconf.SetIntParam(PConfParameters.SpeedControlAlert_Enable,SettingsHandler.DISABLED) != PConf.PC_OK){
    			configResult = PNDProtocol.GENERIC_ACK_FAILURE;
    		}
    	}
    	mPNDProtocol.sendAlertConfigurationResponse(configResult);
    }
    
    /**
     * handle valet alert configuration request, the options are either disable it or activate for one alert.
     * @param activate
     */
    public void handleValetAlertConfig(boolean activate){
    	int configResult = PNDProtocol.GENERIC_ACK_SUCCESS;
    	int valetAlertEnable = 1;
    	if(!activate){
    		valetAlertEnable = 0;
    	}
    	if(mPconf.SetIntParam(PConfParameters.ADVISOR_EnableValetAlert,valetAlertEnable) != PConf.PC_OK){
    		configResult = PNDProtocol.GENERIC_ACK_FAILURE;
		}
    	mPNDProtocol.sendAlertConfigurationResponse(configResult);
    }
    
    /**
     * handle parking alert configuration request, the options are either disable it or activate for one alert.
     * @param activate
     */
    public void handleParkingAlertConfig(boolean activate){
    	int configResult = PNDProtocol.GENERIC_ACK_SUCCESS;
    	int parkingAlertEnable = SettingsHandler.ACTIVATE_ONCE;
    	if(!activate){
    		parkingAlertEnable = 0;
    	}
    	if(mPconf.SetIntParam(PConfParameters.ParkingAlert_Enable1,parkingAlertEnable) != PConf.PC_OK){
    		configResult = PNDProtocol.GENERIC_ACK_FAILURE;
    	} 
    	mPNDProtocol.sendAlertConfigurationResponse(configResult);
    }

    /********************************************************************************************************
                                               Start Route Handling 
     *********************************************************************************************************/
    // Route Status from PND
    public final int NONE             = 1;

    // Route Status to BO
    public static final int PND_SUCCESS      = 0;
    public static final int PND_TIMEOUT      = 1;
    public static final int PND_NOTCONNECTED = 2;
    
    private static final int ROUTING_TEXT           = 0;
	private static final int ROUTING_TEXT_PHONETICS = 1;
	private static final String ROUTING_TEXT_DELIMITER = "|";

    /**
     * calls start route method from pnd protocol in order to build routing message
     * if PND is disconnected it sends a notification to the back office.
     */
    public boolean startRoute(RouteData routeData, boolean sendStatus) {

        if( sendStatus ) {
            sendRoutingStatusToBO_V2BO(NONE, PND_NOTCONNECTED);
        }
        boolean OffBoardNavigation  = (mPconf.GetIntParam(PConfParameters.SystemParameters_OnBoardNavigationEnabled,0) == 1) ? false : true;
        if ( OffBoardNavigation && mGmlanNavConfig ) {
            mRouteGmLanObject = new RouteGmLanObject(routeData);
            return true;
        }
        // let the pnd handle the navigation, but in case we are in indash configuration, 
        // need to pass a true flag to the handover object
        if(isIndashConfigured()){
        	mRouteHandOverSendObject = new RouteHandOverSendObject(routeData);
        	return true;
        }
        return false;
    }



    /**
     * called when a routing status is received from PND
     * it forwards the status received to the Navigation manager
     * and removes the message that sends pnd timeout to back office
     */
    public void handleRoutingStatus(int routeStatus) {
    	// set member with the updated status received from the indash
        setIndashNaviActive(routeStatus == 1 ? true : false);
        mNavigationMgr.routeStatusReceived(routeStatus);
        mDataHandler.removeMessages(HANDLE_PND_ROUTE_STATUS);
    }

    /**
     * sends routing and pnd status to back office
     */
    public void sendRoutingStatusToBO_V2BO(int status, int pndStatus) {
        PNDHandlerMsg msg = new PNDHandlerMsg();
        msg.setRoutingStatusMsg(status,pndStatus);
        try {
            mP2pService.sendMessage(msg);
        } catch (IOException e) {
            mLogger.e("IOException ", e );
        } catch (RemoteException e) {
            mLogger.e("RemoteException ", e );
        }
    }

    private RouteData getRouteData() {
        RouteData routeData = new RouteData();

        routeData.routingLatitudeReference  = mPconf.GetTextParam(PConfParameters.PND_RoutingLatitudeReference, "");
        routeData.routingLongitudeReference = mPconf.GetTextParam(PConfParameters.PND_RoutingLongitudeReference,"");
        routeData.routingLatitude           = mPconf.GetTextParam(PConfParameters.PND_RoutingLatitude,"");
        routeData.routingLongitude          = mPconf.GetTextParam(PConfParameters.PND_RoutingLongitude,"");
        routeData.routingAction             = mPconf.GetIntParam(PConfParameters.PND_RoutingAction,0);
        routeData.routingTextOpcode         = mPconf.GetIntParam(PConfParameters.PND_RoutingTextOpcode,0);
        routeData.routingTimeStamp          = mPconf.GetTimeParam(PConfParameters.PND_TimeUntilMessageIsSavedInMemory,0);
        routeData.dataState                 = NavigationManager.RouteDataState.NOT_ACTIVE;

        int isTrafficFeatureEnabled = mPconf.GetIntParam(PConfParameters.Navigation_TrafficFeatureStatus, NavHandler.TRAFFIC_FEATURE_ENABLED_MASKING);
        if((isTrafficFeatureEnabled & NavHandler.TRAFFIC_FEATURE_ENABLED_MASKING) == NavHandler.TRAFFIC_FEATURE_ENABLED_MASKING) {
	        routeData.routingTrafficUsed    = true;			
		}
        
        // Both text strings located in the same param divided by vertical bar delimiter need to split and validate those strings
        String routingText					= mPconf.GetTextParam(PConfParameters.PND_RoutingText,"");
        String[] tokens = routingText.split(Pattern.quote(ROUTING_TEXT_DELIMITER));
        if(tokens.length == 0){
        	routeData.routingText         = "";
        	routeData.routingTextPhonetic = "";
        }
        if(tokens.length > 0){
        	routeData.routingText         = tokens[ROUTING_TEXT]           == null ? "" : tokens[ROUTING_TEXT]; 
        }
        if(tokens.length > 1){
        	routeData.routingTextPhonetic = tokens[ROUTING_TEXT_PHONETICS] == null ? "" : tokens[ROUTING_TEXT_PHONETICS];        
        }

        return routeData;
    }

    private void clearRouteData() {
        mPconf.SetTextParam(PConfParameters.PND_RoutingLatitudeReference, "");
        mPconf.SetTextParam(PConfParameters.PND_RoutingLongitudeReference,"");
        mPconf.SetTextParam(PConfParameters.PND_RoutingLatitude,"");
        mPconf.SetTextParam(PConfParameters.PND_RoutingLongitude,"");
        mPconf.SetTextParam(PConfParameters.PND_RoutingText,"");
        mPconf.SetIntParam(PConfParameters.PND_RoutingAction,0);
        mPconf.SetIntParam(PConfParameters.PND_RoutingTextOpcode,0);
        mPconf.SetTimeParam(PConfParameters.PND_TimeUntilMessageIsSavedInMemory,0);
    }

    /********************************************************************************************************
                                        OTA and system version handling  
     ********************************************************************************************************/
    public enum PND_OTA_STATE {
    	IDLE,
    	DOWNLOAD,
    	APPLY;
    }
    
    private static PND_OTA_STATE mOtaState = PND_OTA_STATE.IDLE;
    private int mFileSize = 0;
    private int mChunkOffset = 0;
    private String mFileId = "";
    private FileOutputStream fileOutputStream;
    private boolean mDownloadComplete = false;
    private RTUpdateService mRTUpdateService = null;
    private int mOutOfSyncCounter = 0;
    private static final int MAX_OUT_OF_SYNC_RETRIES = 5;
    private static final int mWindow = 2;//3 chunks

    public void handleSystemVersionsRequest() {
        mPNDProtocol.sendSystemVersionsResponse_V2P(RTVersion.getPrimaVersion(),
                                                    RTVersion.getResourcesVersion(),
                                                    RTVersion.getMapsVersion(),
                                                    RTVersion.getUpdateAgentVersion(),
                                                    "00.00.00",
                                                    RTVersion.getMcuVersion(),
                                                    "00.00.00");
    }

    public void handleStartCancelUpdateRequest(int startCancel, int fileSize, String fileID) {
        mLogger.d("Start or Cancel update Request");
        int mode = mPconf.GetIntParam(PConfParameters.OTA_Mode,0);
        int otaEnable = mPconf.GetIntParam(PConfParameters.OTA_Enable, 0);
        mOutOfSyncCounter = 0;
        if ((mode != RTUpdateService.OTA_MODE_MOBILE) && (otaEnable  == 1))
        {
        	mLogger.w("OTA " + mode + " already running." + " Ignoring Start or Cancel update Request");
        	return;
        }
        if (mOtaState == PND_OTA_STATE.APPLY)
        {
        	mLogger.w("Already in OTA mobile Apply state. Ignoring Start or Cancel update Request. mOtaState:" + mOtaState);
        	mPNDProtocol.sendAckNakOpcodeStartCancelUpdate(PNDProtocol.GENERIC_ACK_OPERATION_PENDING);
         	return;
        }
        if (startCancel == 1)
        	mOtaState = PND_OTA_STATE.DOWNLOAD;
        else
        	mOtaState = PND_OTA_STATE.IDLE;
        mFileId   = fileID;
        mFileSize = fileSize;

        if( mOtaState == PND_OTA_STATE.DOWNLOAD ) {
            mLogger.d("Start Update Request By Mobile.(Gen2App), miAPConnected:" + PNDBluetooth.miAPConnected);

            mPconf.SetTextParam(PConfParameters.OTA_Filename, mFileId);
            mPconf.SetIntParam(PConfParameters.OTA_Mode, RTUpdateService.OTA_MODE_MOBILE);//TODO should we save the original mode before changing?
            mPconf.SetTextParam(PConfParameters.OTA_BurningHoursRange, "0000");
            mPconf.SetIntParam(PConfParameters.OTA_MinutesToStartAfterIgnitionOff,1);
            mPconf.SetIntParam(PConfParameters.OTA_Enable,1);

            try {
                File p = new File(RTUpdateService.DOWNLOAD_LOCATION_PATH);
                if (!p.exists())
                {
                	p.mkdirs();
                }
                File f = new File(RTUpdateService.DOWNLOAD_LOCATION + mFileId);
                if(f.exists()) {
                	if (f.length() > fileSize) {
                        mRTUpdateService.cleanDownloadHistory();
                        mChunkOffset = 0;
                	}
                	else if (f.length() == fileSize) {
                        mLogger.e("Download Finished Successfully.");
                        mDownloadComplete = true;
                        mRTUpdateService.notifyMobileDownloadComplete();
                        mPNDProtocol.downloadComplete_V2P(mFileId);
                        return;
                	}
                	else {
                        mChunkOffset = (int)f.length();
                	}
                }
                else {
                    int pos = fileID.lastIndexOf('.');
                    String verifiedDownloadedFile = fileID.substring(0, pos) + "_verified.tbz";
                    File v = new File(RTUpdateService.DOWNLOAD_LOCATION + verifiedDownloadedFile);
                    if(v.exists() && (v.length() == fileSize)) {
                        mLogger.e("Download Finished and already Verified Successfully.");
                        mDownloadComplete = true;
                        mPNDProtocol.downloadComplete_V2P(mFileId);
                        mRTUpdateService.updateFileVerifiedState();
                        return;
                    }
                    else {
                    	//not found
                    	mRTUpdateService.cleanDownloadHistory();
                    	mChunkOffset = 0;
                    }
                }

                fileOutputStream = new FileOutputStream(f, true);
            } catch (java.io.FileNotFoundException e){
                mLogger.e("FileNotFoundException ", e );
            }

            mPNDProtocol.getChunk_V2P(mFileId, mChunkOffset);
            mDataHandler.sendEmptyMessageDelayed(HANDLE_CHUNK_WAIT_TIMEOUT, TIMEOUT_WAITING_FOR_CHUNK);
            mDownloadComplete = false;
        } else {
            mLogger.d("Cancel Update Request By Mobile.(Gen2App)");
            mDataHandler.removeMessages(TIMEOUT_WAITING_FOR_CHUNK);
            try{
                if (fileOutputStream != null) {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                }
            } catch (java.io.IOException e) {
                mLogger.e("IOException ", e );
            }
            mPconf.SetIntParam(PConfParameters.OTA_Enable,0);
        }
    }

    public void handleChunkData(String fileId, String dataOffset, String dataSize, byte[] chunkData) {
        if(mOtaState == PND_OTA_STATE.IDLE) {
            mLogger.i("Ota is disabled, chunk will be ignored.");
            return;
        }

        mDataHandler.removeMessages(HANDLE_CHUNK_WAIT_TIMEOUT);

        if(!fileId.equals(mFileId) ) {
            mLogger.e("Chunk File Id invalid!");
            return;
        }

        int chunkSize = Integer.valueOf(dataSize);
        int chunkOffset = Integer.valueOf(dataOffset);

        // TODO AMIR - data should be received as bytes and not HEX String.
        byte[] chunkBuf = removeEscapeFromSpecialChars(chunkData, chunkSize);

        File f = new File(RTUpdateService.DOWNLOAD_LOCATION + mFileId);
        if(f != null) {
            mChunkOffset = (int) f.length();
        }
        
        boolean ok = true;
        if (mChunkOffset != chunkOffset)
        {
            // Error occured while downloading File
            mLogger.e("****************************************************");
            mLogger.e("****************************************************");
            mLogger.e("Error Downloading File. Chunk offset invalid. Expected:" + mChunkOffset + " Recieved:" + chunkOffset);
            mLogger.e("****************************************************");
            mLogger.e("****************************************************");
            ok = false;
        }
        
        if (ok && (chunkBuf.length != chunkSize)) {
            mLogger.e("****************************************************");
            mLogger.e("****************************************************");
            mLogger.e("Error Downloading File.");
            mLogger.e("Calculated chunk size is different " + chunkBuf.length + " msg = " + chunkSize + " before: " + chunkData.length);
            mLogger.e("****************************************************");
            mLogger.e("****************************************************");
            ok = false;
        }
        
        if (!ok)
        {
        	//if first time just count the failure and let the timeout event handle the retries.
        	if (mOutOfSyncCounter == 0)
        	{
        		mOutOfSyncCounter++;
        		//Don't request retransmission here. Do it always from the timeout event.
        	}
            mDataHandler.sendEmptyMessageDelayed(HANDLE_CHUNK_WAIT_TIMEOUT, TIMEOUT_WAITING_FOR_CHUNK);
        	return;
        }
        
        mOutOfSyncCounter = 0;
        try{
            if (fileOutputStream != null) {
                fileOutputStream.write(chunkBuf);
            }
        } catch (Exception e) {
            mLogger.e("IOException ", e );
        }

        mLogger.e("mChunkOffsetCurrent = " + mChunkOffset);
        int mChunkOffsetCurrent = mChunkOffset;
        mChunkOffset += chunkSize;
        mLogger.e("mChunkOffsetNext = " + mChunkOffset);

        if(mChunkOffset < mFileSize) {
            mPNDProtocol.ackChunk_V2P(mFileId, mChunkOffsetCurrent,mWindow);
            mDataHandler.sendEmptyMessageDelayed(HANDLE_CHUNK_WAIT_TIMEOUT, TIMEOUT_WAITING_FOR_CHUNK);
        } else if(mChunkOffset == mFileSize) {
            File file = new File(RTUpdateService.DOWNLOAD_LOCATION + mFileId);
            mDownloadComplete = false;
            if(file != null) {
                if((int) file.length() == mFileSize) {
                    mLogger.e("Download Finished Successfully.");
                    mDownloadComplete = true;
                } 
            }
            mRTUpdateService.notifyMobileDownloadComplete();
            mPNDProtocol.downloadComplete_V2P(mFileId);
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (java.io.IOException e) {
                    mLogger.e("IOException ", e );
                }
            }
        } else {
            // Error occured while downloading File 
            mLogger.e("Error Downloading File. File size is bigger than expected.");
            mDownloadComplete = false;
            mOtaState = PND_OTA_STATE.IDLE;
            mPconf.SetIntParam(PConfParameters.OTA_Enable, 0);
            mRTUpdateService.notifyMobileDownloadComplete();
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (java.io.IOException e) {
                    mLogger.e("IOException ", e );
                }
            }
        }
    }

    private byte[] removeEscapeFromSpecialChars(byte[] chunkData, int actualChunkSize) {
        ByteArrayOutputStream newBuff = new ByteArrayOutputStream(actualChunkSize);
        int actualWrite = 0;
        int indx;
        for(indx = 0; (indx < chunkData.length) && (actualWrite < actualChunkSize); indx++) {
            if((chunkData[indx] == (byte)'\\')) {
                if(indx + 1 < chunkData.length) {
                	if (PNDBluetooth.miAPConnected && (chunkData[indx + 1] == (byte)'\\')) //should only be done for IOS
                	{
                        newBuff.write('\\');
                        actualWrite++;
                        indx++;
                        continue;
                	}
                	else if((chunkData[indx + 1] == (byte)']') || 
                        (chunkData[indx + 1] == (byte)'[') || 
                        (chunkData[indx + 1] == (byte)'|') || 
                        (chunkData[indx + 1] == (byte)'~')) {
                        continue;
                    }
                    else
                    {
                    	if (PNDBluetooth.miAPConnected && (chunkData[indx + 1] == (byte)' ')) //should only be done for IOS
                    	{
                            newBuff.write('\0');
                            actualWrite++;
                            indx++;
                            continue;
                    	}
                    }
                }
            }
            newBuff.write(chunkData[indx]);
            actualWrite++;
        }
        // Check if chunk data has extra bytes and just warn. Less bytes is checked later.
        if (indx < chunkData.length)
        {
        	mLogger.w("*** chunk data includes extra unread bytes! Chunk data size:" + chunkData.length + ", Actual read:" + indx +
        			", ActualChunkSize:" + actualChunkSize);
        }
        return newBuff.toByteArray();
    }

    private byte[] convertHexStringToBytes(String chunkStr) {
        int len = chunkStr.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(chunkStr.charAt(i), 16) << 4) + Character.digit(chunkStr.charAt(i+1), 16));
        }
        return data;
    }

    public boolean isDownloadComplete() {
        return mDownloadComplete;
    }

    //only updates the final pack flag
    public void handleApplyUpdate(String fileId, boolean finalPack) {

        if(!fileId.equals(mFileId) ) {
        	mLogger.e("****************************************************");
            mLogger.e("Chunk File Id invalid! mFileId = " + mFileId + " Requested fileId" + fileId);
            mLogger.e("****************************************************");
            mPNDProtocol.sendAckNakOpcodeApplyUpdate(PNDProtocol.GENERIC_ACK_FAILURE);
            return;
        }
        mPNDProtocol.sendAckNakOpcodeApplyUpdate(PNDProtocol.GENERIC_ACK_SUCCESS);
        mFinalPack = finalPack;
    }
    
    //initiates the actual file apply
    private void handleApply() {
    	mOtaState = PND_OTA_STATE.APPLY;
        mRTUpdateService.applyMobileUpdate(mFinalPack);   	
    }
    
    public void notifyMobileOnOTAStatus(int status) {
        if(isConnected()) {
            mPNDProtocol.sendUpdateStatusToPhone_V2P(status);
        } else {
            mDbHelper.saveStatusToDB(status);
        }

        if (status == 121)
        {
        	//MESSAGE_CODE_OTA_SOC_RETRY_AFTER_FAIL
        	mOtaState = PND_OTA_STATE.IDLE;
        }
        else if (status == 118)
        {
        	//MESSAGE_CODE_OTA_SOC_FAIL. Fail update
        	mOtaState = PND_OTA_STATE.IDLE;
            mPconf.SetTextParam(PConfParameters.OTA_Filename, "");
            mPconf.SetIntParam(PConfParameters.OTA_Mode, RTUpdateService.OTA_MODE_NORMAL);
            mPconf.SetIntParam(PConfParameters.OTA_Enable,0);//disabled
        }
        else if (status == 117)
        {
        	//MESSAGE_CODE_OTA_SOC_BURN_COMPLETE. Map update completed.
        	mOtaState = PND_OTA_STATE.IDLE;
            if(mFinalPack) {
        	   cleanupAfterMapUpdateCompleted();
            }
        }
        else if (status == 108)
        {
        	//MESSAGE_CODE_OTA_SOC_READY
        	handleApply();
        }
    }

    private void cleanupAfterMapUpdateCompleted()
    {
    	mRTUpdateService.resetMapUpdatingFlag();
        mPconf.SetTextParam(PConfParameters.OTA_Filename, "");
        mPconf.SetIntParam(PConfParameters.OTA_Mode, RTUpdateService.OTA_MODE_NORMAL);
        mPconf.SetIntParam(PConfParameters.OTA_Enable,0);//disabled
		mStrings = (StringReader)mContext.getSystemService(mContext.STRING_SYSTEM_SERVICE);
		mTts = new TextToSpeech( mContext, null );

		try {
			Thread.sleep(1000); // wait to be sure status was sent to phone
		} catch (InterruptedException e) {
			mLogger.e( "Got InterruptedException: ", e);
		}
    	String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPNAVI_PROMPTID_MAP_UPDATE_COMPLETE );
    	String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPNAVI_PROMPTID_MAP_UPDATE_COMPLETE );
    	mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
    			TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );

        PhoneHandler.setPlayTtsOtaMobileDone(); //play the TTS MAP_UPDATE_COMPLETE again on ignition On for the case the TTS was not heard.

		// if the bt param is set to 2 it means we are in a map update process and therefore we need to set the param back to disabled
		// to prevent from BT become active on next system start.
		if(mPconf.GetIntParam(PConfParameters.BT_RadioEnabled, 1) == 2){
			mPconf.SetIntParam(PConfParameters.BT_RadioEnabled, 0);            		
		}

    }
    
    private void flushMobileOtaStatusMessages() {
        List<Integer> statuses = mDbHelper.getQueuedStatuses();

        for(int status : statuses) {
            mPNDProtocol.sendUpdateStatusToPhone_V2P(status);
        }
    }

    /********************************************************************************************************
                                        MP3 operation handling  
     ********************************************************************************************************/
    private final int PLAY_PAUSE     = 1;
    private final int NEXT_TRACK     = 2;
    private final int PREVIOUS_TRACK = 3;
    private final int FAST_FORWARD   = 4;
    private final int FAST_REWIND    = 5;
    private final int SET_POSITION   = 6;

    /**
     * parses mp3 opcode to the right mp3 command
     */
    public void handleMp3Cmd(int mp3Op, int position) {
        switch(mp3Op) {
        case PLAY_PAUSE:
            RoadTrackDispatcher.playerPlayPause();
            break;
        case NEXT_TRACK:
            RoadTrackDispatcher.playerNext();
            break;
        case PREVIOUS_TRACK:
            RoadTrackDispatcher.playerPrev();
            break;
        }
    }

    public static final int PLAYINFO_PLAYING            = 1;
    public static final int PLAYINFO_PAUSED             = 2;
    public static final int PLAYINFO_STOPPED            = 3;
    public static final int PLAYINFO_NO_SUPPORT_INFO    = 4;
    public static final int PLAYINFO_STOPPED_NO_SUPPORT = 5;

    /**
     * API to RoadTrack dispatcher, called to notify on playing info changed.
     */
    public void handlePlayingInformation(int opCode, MetaData playInfo, int playerType) {
        mPNDProtocol.sendPlayingInformation_V2P(opCode,playInfo);
    }

    public void handleMediaPlayTrack(RTMediaHandler.MediaObjectType itemType, int itemId) {
        RTMediaHandler.IMediaAccessProvider media = RTMediaHandler.getInstance().Media();
        
        RTMediaHandler.PlayRequest playRequest = RTMediaHandler.getInstance().new PlayRequest(itemType, itemId, "");
        media.playObject(playRequest);
    }

    /********************************************************************************************************
                                        call control handling    
     ********************************************************************************************************/
    private final int ANSWER             = 1;
    private final int REJECT             = 2;
    private final int HANG_UP            = 3;
    private final int SWITCH_CALLS       = 4;
    private final int TOGGLE_AUDIO       = 5;
    private final int CONFERENCE_ATTACH  = 6;
    private final int MUTE_CALL          = 7;
    private final int HOLD_CALL          = 8;
    private final int UNHOLD_CALL        = 9;
    private final int UNMUTE_CALL        = 10;

    /**
     * handles call control command action code
     */
    public void handleCallControl(int actionCode, int device, int callIndex) {
        switch (actionCode) {
        case ANSWER:
            RoadTrackDispatcher.callAnswerOrSwitch();
            break;
        case REJECT:
            RoadTrackDispatcher.callHangupOrReject();
            break;
        case HANG_UP:
            RoadTrackDispatcher.callHangupOrReject();
            break;
        case SWITCH_CALLS:
            RoadTrackDispatcher.callAnswerOrSwitch();
            break;
        case TOGGLE_AUDIO:
            RoadTrackDispatcher.callToggleAudio();
            break;
        case CONFERENCE_ATTACH:
            RoadTrackDispatcher.callAttachConfernce();
            break;
        case HOLD_CALL:
            RoadTrackDispatcher.callAnswerOrSwitch();
            break;
        case UNHOLD_CALL:
            RoadTrackDispatcher.callAnswerOrSwitch();
            break;
        case MUTE_CALL:
            RoadTrackDispatcher.callToggleMuteMic();
            break;
        case UNMUTE_CALL:
            RoadTrackDispatcher.callToggleMuteMic();
            break;
        }
    }
//
    /**
     * sends messages of all the active calls in system
     */
    public void sendActiveCallStatus(List<RTCall> callList, boolean isBluetoothCallList ) { 
        int listCount = 1;
        int listSize = callList.size();

        // TODO: Amir - when indash and phone start working in parallel, this should be handled different.
        if (!isIndashConnected() && isBluetoothCallList) {
            mLogger.d("BT Call Status is sent only to Indash.");
            return;
        }

        if(callList.size() == 0) {
            mPNDProtocol.sendActiveCallStatus_V2P(1, 1, 1, (isBluetoothCallList ? 1 : 0 ), 0,
                                    0, 0, 0, "",
                                    "", 0);
        }

        for(RTCall call : callList) {
            mPNDProtocol.sendActiveCallStatus_V2P(listCount, listSize, (call.isMT() ? 1 : 0), (isBluetoothCallList ? 1 : 0 ), call.getIndex(),
                                    0, (call.isMpty() ? 1 : 0), call.getState().Value, call.getNumber(),
                                    call.getName(), call.getPhoneNumType().Value);
            listCount ++;
        }
    }
    /********************************************************************************************************
                                    Initiating a Call handling     
     ********************************************************************************************************/
    private final int CALL_TYPE_EMERGENCY_MESSAGE           = 1;
    private final int CALL_TYPE_EMERGENCY_CALL_AND_MESSAGE  = 2;
    private final int CALL_TYPE_ASSISTANCE_CALL_AND_MESSAGE = 3;
    private final int CALL_TYPE_REDIAL                      = 4;
    private final int CALL_TYPE_IVR                         = 5;
    private final int CALL_TYPE_USER_DEFINED_NUMBER         = 6;

    private final int CALL_INIT_SUCCESS = 1;
    private final int CALL_INIT_FAILURE = 2;
    /**
     * called when initiate call request message is received from PND
     * it parses the sourceOpCode of the request message and route it to the
     * right action. call request can be of types:
     * - CALL_TYPE_EMERGENCY_MESSAGE
     * - CALL_TYPE_EMERGENCY_CALL_AND_MESSAGE
     * - CALL_TYPE_ASSISTANCE_CALL_AND_MESSAGE
     * - CALL_TYPE_REDIAL
     * - CALL_TYPE_IVR
     * - CALL_TYPE_USER_DEFINED_NUMBER
     */
    public void handleInitiateCallRequest(String sourceOpcode, String phoneNumber) {
        int opCode = Integer.valueOf(sourceOpcode);
        int	value;
        PNDHandlerMsg msg;

        switch (opCode) {
        case CALL_TYPE_EMERGENCY_MESSAGE:
            RoadTrackDispatcher.InitiateEmergencyMessage();
            // TODO: this status should be received from the dispatcher on the call initiation
            mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_SUCCESS);
            break;
        case CALL_TYPE_EMERGENCY_CALL_AND_MESSAGE:
            value = mPconf.GetIntParam(PConfParameters.ACM_EmergencyEnabled, -1);
            if (value == 0)
            {
            	mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_FAILURE);
            }
            else
            {
            	RoadTrackDispatcher.InitiateEmergencyCallAndMessage();
            	mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_SUCCESS);
            }
            break;
        case CALL_TYPE_ASSISTANCE_CALL_AND_MESSAGE:
            value = mPconf.GetIntParam(PConfParameters.ACM_AssistanceEnabled, -1);
            if (value == 0)
            {
            	mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_FAILURE);
            }
            else
            {
            	RoadTrackDispatcher.InitiateAssistanceCallAndMessage();
            	mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_SUCCESS);
            }
            break;
        case CALL_TYPE_REDIAL:
        	RoadTrackDispatcher.getInstance().callRedialIntiate();
            break;
        case CALL_TYPE_IVR:
            boolean status = RoadTrackDispatcher.startSR();
            if (status)
            	mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_SUCCESS);
            else
            	mPNDProtocol.sendVehicleReplyStatusToCallInitiate_V2P(CALL_INIT_FAILURE);
            break;
        case CALL_TYPE_USER_DEFINED_NUMBER:
            RoadTrackDispatcher.DialUser(phoneNumber);
            break;
        }
    }

    /********************************************************************************************************
                                       HMI display handling     
     ********************************************************************************************************/
    /**
     *  called when sending selection message to app, the message can be menu, one button or two
     */
    public void hmiDisplaySelectionMessage(MessageType messageType, String title, String[] Options, int displayId){
    	mPNDProtocol.send2Gen2AppSelectionMsg_V2P(messageType,title,Options, displayId);
    }
    /**
     * called when sending text message to app
     */
    public void hmiDisplayTextMessage(int displayType, String text, int displayId){
    	mPNDProtocol.send2Gen2AppTextMsg_V2P(displayType, text, displayId);
    }
    /**
     * called when sending cancel message to app
     */
    public void hmiDisplayCancelMessage(int displayType){
    	mPNDProtocol.send2Gen2AppCancelMsg_V2P(displayType);
    }
    /**
     * called when selection msg received from the display
     */
    public void handleDisplaySelectedOption(int selectedOption){
    	mGen2AppDisplay.sendSelection(selectedOption);
    }
    /**
     * called when back or cancel msg received from the display
     */
    public void handleDisplayBackOrCancelOption(int selectedOption){
    	mGen2AppDisplay.sendBackOrCancel(selectedOption);
    }

    /********************************************************************************************************
                                        PND INFO handling  
     ********************************************************************************************************/
    public void handleIdRequest(){
        String simCardNumber = mPconf.GetTextParam(PConfParameters.SystemParameters_PhoneNumber, "");
        String deviceId      = mPconf.GetTextParam(PConfParameters.SystemParameters_DID, "");

        mPNDProtocol.sendIdentificationReply_V2P(simCardNumber, deviceId);
    }

    /**
     * class holding PND device Info
     */
    private class DeviceInfo{
        public String deviceId;
        public String shellVersion;
        public String installedMaps;
        public String sdkType;
    }

    /**
     * calls PndProtocol api to send a pnd info request to PND
     * and set's a delayed message if not canceled by info reply
     */
    private void createPndInfoRequest() {
        mTimer = new Timer("PndInfoTimer");

        mPNDProtocol.sendPhoneInfoRequest_V2P();

        Message versionMsg = mDataHandler.obtainMessage(HANDLE_PND_SEND_VERSION_1);
        mDataHandler.sendMessageDelayed(versionMsg, 4000);
    }

    /**
     *  called when pnd info message is received from pnd
     *  sends pnd info to back office
     *  cancel delayed message of reply timeout
     */
    public void setPhoneInfo(String pndDeviceId, String shellVersion, String installedMaps) {
        mDataHandler.removeMessages(HANDLE_PND_SEND_VERSION_1);

        DeviceInfo deviceInfo    = new DeviceInfo();
        deviceInfo.deviceId      = pndDeviceId;
        deviceInfo.shellVersion  = shellVersion;
        deviceInfo.installedMaps = installedMaps;

        mDataHandler.obtainMessage(HANDLE_PND_SEND_VERSION, deviceInfo).sendToTarget();
    }


    /**
     * sends the ond info to back office
     */
    private void sendVersion_V2BO(String pndDeviceId, String shellVersion, String installedMaps) {
        PNDHandlerMsg msg = new PNDHandlerMsg();
        msg.setPndInfoMsg(pndDeviceId, shellVersion, installedMaps);
        try {
            mLogger.i("sendVersion_V2BO ");
            mP2pService.sendMessage(msg);
        } catch (IOException e) {
            mLogger.e("IOException ", e );
        } catch (RemoteException e) {
            mLogger.e("RemoteException ", e );
        }
    }

    /**
     * reads languageId, temperature units and distance units from PConf, and sends them to pnd.
     */
    /*package*/ 
    void createPndLanguageIdentification() {
        int languageId    = mPconf.GetIntParam(PConfParameters.SystemParameters_Language, 0) + 1; // because in pnd protocol it starts from 1.
        int tempUnits     = mPconf.GetIntParam(PConfParameters.SystemParameters_TemperatureUnits, 0) + 1;
        int distanceUnits = mPconf.GetIntParam(PConfParameters.SystemParameters_DistanceUnits, 0) + 1;

        mPNDProtocol.sendPndLanguageIdentification_V2P(languageId, tempUnits, distanceUnits);
    }

    /********************************************************************************************************
                                           Text Messages Handling 
     ********************************************************************************************************/
    private final int SUCCESS  = 1; 
    private final int FAILURE = 2;

    private final int NUM_OF_MAX_QUEUED_TXT_MSGS = 5;

    private final char TXT_IN_RT         = '1';
    private final char TXT_IN_UBIKO      = '2';
    private final char TXT_IN_WEB_FLEET  = '3';
    private final char TXT_IN_SMS        = '4';
    private final char TXT_GNRC_MSG      = '6';
    private final char TXT_IN_MANTA      = 'A';
    private final char TXT_IN_MANTB      = 'B';
    private final char TXT_IN_MANTC      = 'C';
    private final char TXT_IN_MANTD      = 'D';
    private final char TXT_IN_MANTE      = 'E';
    private final char TXT_IN_MANTF      = 'F';
    private final char TXT_IN_MANTG      = 'G';
    private final char TXT_IN_MANTH      = 'H';
    private final char TXT_IN_MANTI      = 'I';
    private final char TXT_IN_MANTJ      = 'J';

    private String[] txtmsgsColumns = { PndDB.COLUMN_ID,
            PndDB.COLUMN_TXT_MSG, PndDB.COLUMN_TXT_GUI, PndDB.COLUMN_TXT_TYP, 
            PndDB.COLUMN_TSTMP };

    /**
     * class holding data on text messages from BO
     */
    private class TxtMsgData {
        public long   id;
        public String textInMessageType;
        public int    textGuiType;
        public String textMessage;
        public long   txtMsgTimeStamp;

        public boolean isEmpty() {
            return ( (this.textMessage == null) || this.textMessage.isEmpty() );
        }
    }

    /**
     * reads the txt msg data using PConf and forward to pnd
     */
    private void txtMsgReceivedFromBO(TxtMsgData txtMsgData) {
        txtMsgData.textGuiType =  mPconf.GetIntParam(PConfParameters.PND_TxtInGUIType, 0);
        txtMsgData.textInMessageType = mPconf.GetTextParam(PConfParameters.PND_TxtInMessageType, "");  ;
        txtMsgData.textMessage = mPconf.GetTextParam(PConfParameters.PND_TxtMessage, "");
        txtMsgData.txtMsgTimeStamp = mPconf.GetTimeParam(PConfParameters.PND_TimeUntilMessageIsSavedInMemory,0);
    }

    /**
     * if pnd connected txt message is sent to pnd and sets a timer waiting for status
     * if not connected, text message is saved to DB
     */
    private void sendTxtMessageToPND(TxtMsgData txtMsgData) {
        if(!isConnected()) {
            sendTxtMsgStatusToBO_V2BO(FAILURE,PND_NOTCONNECTED);
            saveTextMessageToDB(txtMsgData);
        } else {
            mPNDProtocol.sendTextMessageFromBO_V2P( txtMsgData.textInMessageType,
                    txtMsgData.textGuiType,
                    txtMsgData.textMessage,
                    ""); // TODO: if text message is from a phone number this phone number.

            mDataHandler.sendEmptyMessageDelayed(HANDLE_PND_TXTMSG_STATUS, 4000);
        }
    }

    /**
     * returns the count of queued text messages in data base txt msgs table 
     */
    public int queuedTextMsgsCount() {
        Cursor cursor = mDatabase.query(PndDB.TABLE_TXT,
                txtmsgsColumns, null, null, null, null, null);

        int count = cursor.getCount();
        cursor.close();

        return count;
    }

    /**
     * saves text message to data base txt msgs table
     */
    private void saveTextMessageToDB(TxtMsgData txtMessage) {
        ContentValues values = new ContentValues();  

        values.put(PndDB.COLUMN_TXT_TYP, txtMessage.textInMessageType);
        values.put(PndDB.COLUMN_TXT_GUI, txtMessage.textGuiType);
        values.put(PndDB.COLUMN_TXT_MSG, txtMessage.textMessage);
        values.put(PndDB.COLUMN_TSTMP,   txtMessage.txtMsgTimeStamp);

        mDatabase.insert(PndDB.TABLE_TXT, null, values);

        if(queuedTextMsgsCount() > NUM_OF_MAX_QUEUED_TXT_MSGS) {
            Cursor cursor = mDatabase.query(PndDB.TABLE_TXT,
                    txtmsgsColumns, null, null, null, null, null);

            cursor.moveToFirst();
            TxtMsgData txtMsgData = cursorToTextMsgData(cursor);
            cursor.close();

            deleteTxtMsgFromDB(txtMsgData);
        }
    }

    /**
     * reads first txt message in the data base
     */
    public TxtMsgData getQueuedTxtMessage() {
        TxtMsgData txtMsgData = new TxtMsgData();

        Cursor cursor = mDatabase.query(PndDB.TABLE_TXT,
                txtmsgsColumns, null, null, null, null, null);

        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            txtMsgData = cursorToTextMsgData(cursor);
            cursor.moveToNext();
        }
        cursor.close();
        return txtMsgData;
    }

    /**
     * converts the cursor to TxtMsgData
     */
    private TxtMsgData cursorToTextMsgData(Cursor cursor) {
        TxtMsgData txtMsgData = new TxtMsgData();

        txtMsgData.id                = cursor.getLong(0);
        txtMsgData.textMessage       = cursor.getString(1);
        txtMsgData.textGuiType       = cursor.getInt(2);
        txtMsgData.textInMessageType = cursor.getString(3);
        txtMsgData.txtMsgTimeStamp   = Long.valueOf(cursor.getString(4)).longValue();

        return txtMsgData;
    }

    /**
     * deletes a specific text message from data base by ID
     */
    public void deleteTxtMsgFromDB(TxtMsgData txtMsgData){
        mDatabase.delete(PndDB.TABLE_TXT, PndDB.COLUMN_ID
                + " = " + txtMsgData.id, null);
    }

    /**
     * called when text message status is received
     * removes last sent message from DB and if there's
     * still more queued messages in data base, call 
     * msg handler again to send them to PND
     */
    public void handleTxtMsgStatus(int status) {
        mDataHandler.removeMessages(HANDLE_PND_TXTMSG_STATUS);
        sendTxtMsgStatusToBO_V2BO(status, PND_SUCCESS);

        if(mLastTxtMsgData != null) {
            deleteTxtMsgFromDB(mLastTxtMsgData);
            mLastTxtMsgData = null;
        }

        if(queuedTextMsgsCount() > 0) {
            mDataHandler.sendEmptyMessage(HANDLE_PND_SEND_TXTMSG);
        }
    }

    /**
     * sends msg and pnd status to back office.
     */
    private void sendTxtMsgStatusToBO_V2BO(int txtStatus, int pndStatus) {
        PNDHandlerMsg msg = new PNDHandlerMsg();
        msg.setTxtMsgStatusMsg(txtStatus, pndStatus);
        try {
            mP2pService.sendMessage(msg);
        } catch (IOException e) {
            mLogger.e("IOException ", e );
        } catch (RemoteException e) {
            mLogger.e("RemoteException ", e );
        }
    }

    private void sendGmlanRouteToMCU( RouteGmlanData route) {
        PNDHandlerMsg msg = new PNDHandlerMsg();
        msg.setGmlanRoute(route);
        try {
            mP2pService.sendMessage(msg);
        } catch (IOException e) {
            mLogger.e("IOException ", e );
        } catch (RemoteException e) {
            mLogger.e("RemoteException ", e );
        }
    }

    /**
     * handles recevied text message from PND
     */
    public void handleTextMessageFromPND(char messageType, String phoneNumber, String messageText) {
        int messageTypeValue;

        switch(messageType) {
        case TXT_IN_RT:       
        case TXT_IN_UBIKO:    
        case TXT_IN_WEB_FLEET:
        case TXT_GNRC_MSG:
        case TXT_IN_MANTA:
        case TXT_IN_MANTB:
        case TXT_IN_MANTC:
        case TXT_IN_MANTD:    
        case TXT_IN_MANTE:    
        case TXT_IN_MANTF:    
        case TXT_IN_MANTG:    
        case TXT_IN_MANTH:    
        case TXT_IN_MANTI:    
        case TXT_IN_MANTJ:
            if( '0' <= messageType && messageType <= '9' ) {
                messageTypeValue = Character.valueOf(messageType) - 48;
            } else {
                messageTypeValue = Character.valueOf(messageType) - 55;
            }

            PNDHandlerMsg msg = new PNDHandlerMsg();
            msg.setTxtMsgFromPND(messageTypeValue, messageText);

            try {
                mP2pService.sendMessage(msg);
            } catch (IOException e) {
                mLogger.e(  "IOException ", e );
            } catch (RemoteException e) {
                mLogger.e(  "RemoteException ", e );
            }
            break;
        case TXT_IN_SMS:
            // Not implemented for now, and maybe forever.
            break;
        }
    }

    private void clearTxtMsgData() {
        mPconf.SetIntParam(PConfParameters.PND_TxtInGUIType, 0);
        mPconf.SetTextParam(PConfParameters.PND_TxtInMessageType, "");
        mPconf.SetTextParam(PConfParameters.PND_TxtMessage, "");
        mPconf.SetTimeParam(PConfParameters.PND_TimeUntilMessageIsSavedInMemory,0);
    }

    /***************************************************************************************************
                                           Turn By Turn Navigation
    ***************************************************************************************************/
    public void sendManeuverListPreview(String[] maneuverList) {
        mPNDProtocol.turnByTurnRoutePreviewInformation_V2P(maneuverList);
    }

    public void handleTurnByTurnRoutePreviewAck() {

    }

    public void handleTurnByTurnAction(int actionType) {
        mTbtNofifPnd.actionHandler(actionType);
    }

    public void sendTBTManeuver(boolean showUrgent, boolean newManeuver, long distance, int signtype, String currentStreetName, String nextStreetName, int dist2dest, int time2dest) {
        mPNDProtocol.turnByTurnManeuverInformation_V2P(showUrgent, newManeuver, distance, signtype, currentStreetName, nextStreetName, dist2dest, time2dest);
    }

    public void sendTbtStatus(int code) {
        mPNDProtocol.turnByTurnStatus_V2P(code);
    }

    public void sendDestination(String destinationName, int time2Dest, int distance) {
        mPNDProtocol.turnByTurnDestination_V2P(destinationName, time2Dest, distance);
    }

    public void sendTbtMuteState(boolean state) {
        mPNDProtocol.turnByTurnMuteState_V2P(state);
    }

    /***************************************************************************************************
                                            Text To Speech
    ***************************************************************************************************/
    public void onInit(int status) {
        if(status != TextToSpeech.SUCCESS) {
            mLogger.i("Text To Speech Init Failed!");
		}
    }    

    public void handleTextForTTS(String textForTTS) {
    	// This messages are arriving from the phone app, the phone app has the ability to know if we are in the middle
    	// of VDP session or in the middle of a call and therefore the responsibility to decide whether to interrupt or no is 
    	// on the app side. Hence in this case the tts is granted an open gate to interrupt
    	mSpeech.speak(textForTTS, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.INTERUPT_CALL_OR_VR,
    			TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
    }
    /***************************************************************************************************/

    public void handleInDashPowerStatus(String ignitionSignalState, String indashDisplay) {

    }

    public void handleSyncFavorites(ArrayList <FavoriteData> favList) {
        mNavigationMgr.emptyFavoritesTable();

        mNavigationMgr.createListOfFavorites(favList);
    }

    public void handleNewMapNotification(String mapFullPath, String mapVersion) {
        mLogger.i( " Report Unhandled Message, Notify New Map.");
    }

    public void handleActiveRouteNotification(int activeRoute) {
        mLogger.i( " Report Unhandled Message, Notify Active Route");
    }

    public void setSettings(String units, String tollRoads, String routeCalcAlgorithm,
                            String passRouteToPhone, String language, String tempDegrees) {
        mLogger.i( " Report Unhandler Message, set Settings");
    }

    public void handleCancelActiveRoute() { 
        mLogger.i( " Report Unhandler Message, handleCancelActiveRoute");
    }

    public void handleAckSendRouteToPhone() {
        mLogger.i( " Report Unhandler Message, handleAckSendRouteToPhone");   
    }

    public void handleGoHomeRespond() {
        mLogger.i( " Report Unhandler Message, handleGoHomeRespond");   
    }


     public static class PNDHandlerMsg implements android.roadtrack.p2p.IMessage{
        private int mCodeType;

        //PND INFO MSG
        private byte[] mPndDeviceId;
        private byte[] mShellVersion;
        private byte[] mInstalledMaps;
        private int mDataLength = 0;

        //Routing and TxtMsgs Status
        private int mStatus; 
        private int mPndStatus;

        //TextMessage From Pnd
        private int mMessageType;
        private int mMessageLength;
        private byte[] mTextMessage;

        //Gmlan Route Paramteres
        private float mroutingLatitude;
        private float mroutingLongitude;
        private byte[] mroutingText;

        public void setPndInfoMsg(String pndDeviceId, String shellVersion, String installedMaps) {
            mPndDeviceId   = pndDeviceId.getBytes();
            mShellVersion  = shellVersion.getBytes();
            mInstalledMaps = installedMaps.getBytes();

            mDataLength = mPndDeviceId.length + mShellVersion.length + mInstalledMaps.length + 3;

            mCodeType = MessageCodes.MESSAGE_CODE_PND_INFO_SET;
        }

        public void setRoutingStatusMsg(int status, int pndStatus) {
            mCodeType  = MessageCodes.MESSAGE_CODE_PND_ROUTING_STATUS;
            mStatus    = status;
            mPndStatus = pndStatus;  
        }

        public void setTxtMsgStatusMsg(int status, int pndStatus) {
            mCodeType  = MessageCodes.MESSAGE_CODE_PND_TXTMSG_STATUS;
            mStatus    = status;
            mPndStatus = pndStatus;
        }

        public void setTxtMsgFromPND(int messageType, String textMessage) {
            mCodeType = MessageCodes.MESSAGE_CODE_PND_TXTMSG_FROM_PND;
            mMessageType = messageType;
            mTextMessage = textMessage.getBytes();
            mMessageLength = mTextMessage.length;
        }

        public void setGmlanRoute(RouteGmlanData route) {
            mCodeType = MessageCodes.MESSAGE_CODE_PND_GMLAN_ROUTE;
            mroutingLatitude = route.routingLatitude;
            mroutingLongitude = route.routingLongitude;
            mroutingText = route.routingText.getBytes();     
        }


        @Override
        public void fromArray(byte[] arg0) throws IOException {}

        @Override
        public byte[] toArray() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianOutputStream stream = new LittleEndianOutputStream(baos);

            if (mCodeType == MessageCodes.MESSAGE_CODE_PND_INFO_SET) {
                stream.writeByte(mPndDeviceId.length);
                stream.writeByte(mShellVersion.length);
                stream.writeByte(mInstalledMaps.length);
                stream.write(mPndDeviceId,0,mPndDeviceId.length);
                stream.write(mShellVersion,0,mShellVersion.length);
                stream.write(mInstalledMaps,0,mInstalledMaps.length);
            }

            if (mCodeType == MessageCodes.MESSAGE_CODE_PND_ROUTING_STATUS || 
                    mCodeType == MessageCodes.MESSAGE_CODE_PND_TXTMSG_STATUS  ) 
            {
                stream.writeInt(mStatus);
                stream.writeInt(mPndStatus);
            }

            if(mCodeType == MessageCodes.MESSAGE_CODE_PND_TXTMSG_FROM_PND) {
                stream.writeInt(mMessageType);
                stream.writeInt(mMessageLength);
                stream.write(mTextMessage,0,mTextMessage.length);
            }

            if(mCodeType == MessageCodes.MESSAGE_CODE_PND_GMLAN_ROUTE) {
                stream.writeFloat(mroutingLatitude);
                stream.writeFloat(mroutingLongitude);  
                stream.write(mroutingText,0,mroutingText.length);
                stream.writeByte(0);
            }


            stream.flush();
            stream.close();
            return baos.toByteArray();
        }

        @Override
        public int getGroup() {
            return MessageGroups.MESSAGE_GROUP_PND;
        }

        @Override
        public int getCode() {
            return mCodeType;
        }

        @Override
        public int getDataLength() {
            return mDataLength;
        }
    }

    Listener mGPSStatusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int gpsStatus) {
            switch (gpsStatus) {
            case GpsStatus.GPS_EVENT_STARTED:
                mLastGpsStatus = true;    
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                mLastGpsStatus = false;
                break;
            }
        }       
    };

    
    private boolean validateRouteCoordinates(RouteData route)
    {          
           
    	mLogger.d("Testing : "+route.routingLatitude+" , "+route.routingLongitude);
    	try {

            double lat = Double.parseDouble(route.routingLatitude);
            double lon = Double.parseDouble(route.routingLongitude);
               
            return (lat >= -90 && lat <=90) && (lon >=-180 && lon <=180);
    	}catch(NumberFormatException e)
    	{
    		return false;
    	}
    	

    }
    
    
    /**
     * listens on and PConf parameter update & event repository intents.
     */
    private BroadcastReceiver mPbapBroadcastReceiver = new BroadcastReceiver() {       
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		String mDevaddr;
    		int hfStatus;
    		if(intent.getAction().equals(PConf.PCONF_PARAMUPDATED)) {
    			ArrayList<Integer> paramIDs = intent.getIntegerArrayListExtra( PConf.PARAM_ID_LIST );
    			boolean routingData = false;
    			boolean txtMsgData  = false;
    			if ( paramIDs != null ) {
    				for (int id : paramIDs) {
    					if((id > PConfParameters.PND_Parameter_First) && (id < PConfParameters.PND_RoutingTextOpcode + 1)) {
    						routingData = true;
    					}

    					// since we are clearing the this params after reading them we need to avoid reacting to their clearance value update
    					if((id >= PConfParameters.PND_TxtInMessageType) && (id <= PConfParameters.PND_TxtMessage)){
                            if((!mPconf.GetTextParam(PConfParameters.PND_TxtInMessageType, "").isEmpty()) &&
    						   (!mPconf.GetTextParam(PConfParameters.PND_TxtMessage, "").isEmpty()) 	  &&
    						   (mPconf.GetIntParam(PConfParameters.PND_TxtInGUIType, 0) != 0)){
								txtMsgData = true;
							} 
    					}
    					
    					// if language changed and indash connected need to update indash
    					if(id == PConfParameters.SystemParameters_Language){
    						if(isIndashConfigured()){
    							createPndLanguageIdentification();
    						}
    					}
    				}
                }
                else {
                    mLogger.e( "Parameter List could not obtained");
                }

                mLogger.d("PCONF_PARAMUPDATED routingData? " + routingData);
                if (routingData) {
                    RouteData routeData = getRouteData();
                    
                    int PNG_mode = mPconf.GetIntParam(PConfParameters.SystemParameters_PNGModeControl ,0);                  
                    
                    // check navigation param. in case it is not enabled don't start new navigation. 
                	int isNavigationFeatureEnabled = mPconf.GetIntParam(PConfParameters.Navigation_NavigationFeatureStatus, SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING);
            		if((isNavigationFeatureEnabled & SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING) == SettingsHandler.NAVIGATION_FEATURE_ENABLED_MASKING  || (PNG_mode == SettingsHandler.PNG_MODE_ENABLED_WITH_NAVI))
            		{
                        if( IsTestDestination(routeData) ) {
                            // Do nothing
                        } else if( !routeData.routingLatitude.isEmpty() && 
                        		!routeData.routingLongitude.isEmpty() &&
                        		validateRouteCoordinates(routeData)) {
                        	
                        	routeData.dataState = RouteDataState.NEW_ROUTE;
                        	mNavigationMgr.createRouteRecord(routeData);
							if ((IgnigtionState.IGNITION_ON == mEventRepository.getIgnitionOnOffEvent()) && 
									(mEventRepository.getIsInfotainmentAllowed() && !mEventRepository.isExternalBTCallActive()) ) {
								
								Navigation.Instance().handleRouteReceivedFromBo(mNavigationMgr.pullNewQueuedRoute(),false);
							}
                            mLogger.d( "sent new route to Navigation now clearing routing data");
                        }
                        else
                        {
                        	mLogger.w( "route coordinate invalid - Aborting !!!");
                        }
                        // Clear PND route fields if they are not already empty
                        clearRouteData();
            		} else {
            			mLogger.d("Navigation feature not enabled - sending nack to PND / BO");
            		}
                }

                if (txtMsgData) {
                    TxtMsgData txtData = new TxtMsgData();
                    txtMsgReceivedFromBO(txtData); 
                    // the msg received from BO so no phonetype is availble
                    mPNDProtocol.sendTextMessageFromBO_V2P(txtData.textInMessageType, txtData.textGuiType, txtData.textMessage, "");
                    if(!txtData.isEmpty()) {
                        mLogger.d( "clear text message data");
                        clearTxtMsgData();
                    }
                }
            } else if(intent.getAction().equals(BluetoothHFP.ACTION_HFP_STATUS_CHANGED)) {
                mLogger.d("ACTION_HFP_STATUS_CHANGED received.");
                mDevaddr = intent.getStringExtra(BluetoothHFP.EXTRA_HFP_DEVICE_ADDRESS);
                hfStatus = intent.getIntExtra(BluetoothHFP.EXTRA_HFP_STATUS, BluetoothHFP.HFP_DISCONNECTED);
                mHFPStatus.mHFPAddress = mDevaddr.replaceAll(":","");

                switch(hfStatus) {
                case BluetoothHFP.HFP_CONNECTED:
                    mLogger.d("HFP Connected.");
                    mHFPStatus.mHFPConnected = true;
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_NOT_STARTED;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, mHFPStatus.mHFPPhoneBookStatus, mHFPStatus.mHFPAddress);
                    break;
                case BluetoothHFP.HFP_DISCONNECTED:
                    mLogger.d("HFP Disonnected.");
                    mHFPStatus.mHFPConnected = false;
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_NOT_STARTED;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, mHFPStatus.mHFPPhoneBookStatus, mHFPStatus.mHFPAddress);
                    mPNDProtocol.sendPlayingInformation_V2P(PNDHandler.PLAYINFO_PAUSED,null);    
                    break;
                default:
                    break;
                }
            } else if(intent.getAction().equals(BTDevicesService.CONTACTS_SYNC_UPDATE_INTENT)) {
                int cacheStatus = intent.getIntExtra(   BTDevicesService.CONTACTS_SYNC_STATUS_EXTRA, 
                                                        BTDevicesService.CONTACTS_STATUS_FINAL_AVAIALABLE );
                if (cacheStatus == BTDevicesService.CONTACTS_STATUS_SYNCHRONIZING) {
                    mLogger.d("CONTACTS_STATUS_SYNCHRONIZING");
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_STARTED;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, mHFPStatus.mHFPPhoneBookStatus, mHFPStatus.mHFPAddress);
                }
                
                if (cacheStatus == BTDevicesService.CONTACTS_STATUS_CACHE_AVAIALABLE ) {
                    mLogger.d("CONTACTS_STATUS_CACHE_AVAIALABLE");
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_ENDED_SUCCESSFULLY;
                    mUseCachedContacts = true;
                }

                if (cacheStatus == BTDevicesService.CONTACTS_STATUS_FINAL_AVAIALABLE ) {
                    mLogger.d("CONTACTS_STATUS_FINAL_AVAIALABLE ");
                    mUseCachedContacts = true;
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_ENDED_WITH_CHANGE;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, mHFPStatus.mHFPPhoneBookStatus, mHFPStatus.mHFPAddress);
                }
                
                // We are syncing first time and it has failed. Send empty grammar.
                if (cacheStatus == BTDevicesService.CONTACTS_STATUS_FINAL_NOT_AVAIALABLE && !mUseCachedContacts) {
                    mLogger.d("CONTACTS_STATUS_FINAL_NOT_AVAIALABLE");
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_FAILED;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, mHFPStatus.mHFPPhoneBookStatus, mHFPStatus.mHFPAddress);
                }

                // We are syncing first time and it has failed. Send empty grammar.
                if (cacheStatus == BTDevicesService.CONTACTS_STATUS_FINAL_NOT_AVAIALABLE && mUseCachedContacts) {
                    mLogger.d("CONTACTS_STATUS_FINAL_NOT_AVAIALABLE");
                    mHFPStatus.mHFPPhoneBookStatus = PNDProtocol.PHONEBOOK_SYNC_ENDED_WITH_CHANGE;
                    mPNDProtocol.sendHFPConnectedAndPbStatus_V2P(mHFPStatus.mHFPConnected, mHFPStatus.mHFPPhoneBookStatus, mHFPStatus.mHFPAddress);
                }
            } else if(intent.getAction().equals(EventData.ACTION_EVENT_UPDATED)) {
            	short eventId = intent.getShortExtra(EventData.EXTRA_EVENT_ID, (short)0);
                if(eventId == EventData.EVENTREP_GMLANPWRMODE || eventId == EventData.EVENTREP_IGNITIONONOFF) {
                    Location location = null;
                    // Get current location
                    try {
                        if( mLocationManager != null ) {
                            location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        }
                    } catch( Exception e ) {
                        mLogger.e("cannot get location", e);
                    }

                    if( location == null ) {
                        mLogger.e("no current location available from manager");
                        // if couldn't get location from manager try to get last known location stored on mcu
                        if(Navigation.Instance().mLastLocationFromMcu != null){
                            location = Navigation.Instance().mLastLocationFromMcu;
                            mLogger.v("current location available - received from mcu last know location");
                        } else {
                            mLogger.e("no current location available from mcu");
                        }
                    } else {
                        mLogger.v("current location available - received from manager");
                    }

                    double longitude = 0;
                    double latitude = 0;

                    mLogger.i("location = " + location + " mLastGpsStatus = " + mLastGpsStatus);
                    if (location != null) {
                        longitude = location.getLongitude();
                        latitude = location.getLatitude();
                    }

                    GmlanPowerState gmlanPwrState = mEventRepository.getGmlanPowerMode();
                    IgnigtionState ignitionState = mEventRepository.getIgnitionOnOffEvent();
                    int vehiclePwrMode = 0;
                    if((gmlanPwrState == GmlanPowerState.POWER_MODE_OFF) && (ignitionState == IgnigtionState.IGNITION_ON)) {
                        vehiclePwrMode = 1;
                    } else {
                        switch(gmlanPwrState) {
                            case POWER_MODE_OFF:
                                vehiclePwrMode = 0;
                            break;
                            case POWER_MODE_ACCESSORY:
                                vehiclePwrMode = 2;
                            break;
                            case POWER_MODE_RUN:
                                vehiclePwrMode = 3;
                            break;
                            case POWER_MODE_CRANK:
                                vehiclePwrMode = 4;
                            break;
                        }
                    }
                    mLogger.i("vehiclePwrMode = " + vehiclePwrMode);
                    mPNDProtocol.sendIgnitionState_V2P(vehiclePwrMode,
                                                    longitude,
                                                    latitude,
                                                    mLastGpsStatus,
                                                    0); // not using driving status for now.
                } else if (eventId == EventData.EVENTREP_MIRRORSTATE) {
                    int operationalMode = mPconf.GetIntParam(PConfParameters.OperationalModes_OperationalMode,0);
                    boolean mirrorState = mEventRepository.getMirrorPowerState();
                    mPNDProtocol.sendInfotainmentStatus_V2P(mirrorState, operationalMode);
                }
            } else if (intent.getAction().equals(BluetoothHFP.ACTION_HFP_SIGNAL_STRENGTH_CHANGED)) {
                mHfpSignal = intent.getIntExtra(BluetoothHFP.EXTRA_HFP_SIGNAL_STRENGTH, 0);
            } else if (intent.getAction().equals(BluetoothHFP.ACTION_HFP_BATTERY_CHANGED)) {
                mHfpBatteryLevel = intent.getIntExtra(BluetoothHFP.EXTRA_HFP_BATTERY_LEVEL, 0);
            }
        }
    };

    /**
     * receives messages from MCU by P2P
     */
    public void onMessageRecieved(IMessage message)
    {
        mLogger.d("Message Received = " + message);
        LittleEndianInputStream is;

        if ( message.getGroup() != MessageGroups.MESSAGE_GROUP_PND ) {
            return;
        }

        switch (message.getCode()) {
        case MessageCodes.MESSAGE_CODE_PND_TXTMSG_FROM_PND:
            try
            {
                is = new LittleEndianInputStream(new ByteArrayInputStream(message.toArray()));
            
                mLogger.d( "length = " + message.getDataLength() + " ( " + is.available() + " ) ");
                
                if( is.available() > 0 )
                {
                    int status = is.readInt();
                    mPNDProtocol.sendTxtMessageFromPndStatus_V2P(status);
                }
            }
            catch(Exception e)
            {
                mLogger.e(  "Exception ", e );
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private class TestRoute {
        String                       destLat;
        String                       destLon;
        TrafficRoutingServices.Route trafficRoute;

        public TestRoute() {
            destLat = null;
            destLon = null;
            trafficRoute = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private boolean IsTestDestination( RouteData routeData ) {

        /* Testing
         * If the destination lat, lon are special, indicates a test to be run:
         *  0,0 : Run address search test
         *  0,1 : Run test routing defined by navtest file
         *  0,2 : Get current active destination and waypoints
         *  0,3 : Lookup current address
         *  0,4 : Lookup crossing and start navigation to it
         *  1,1 : Stop navigation
         */

         // Run test in separate thread so as not to delay server thread

        if( routeData.routingLatitude.equals("0") && routeData.routingLongitude.equals("0") ) {
            // Initiate address search test
            mLogger.i( "Initiating search test");
            Navigation.Instance().runSearchTest();
            return true;

        } else if( routeData.routingLatitude.equals("0") && routeData.routingLongitude.equals("1") ) {
            // Use defined destination with optional waypoints
            TestRoute testRoute = GetTestDestinationAndWaypoints();
            if( testRoute != null ) {
                mLogger.i( "using test destination + waypoints");
                if( testRoute.trafficRoute != null ) {
                    routeData.routingTrafficUsed = true;
                }
                routeData.routingLatitude = testRoute.destLat;
                routeData.routingLongitude = testRoute.destLon;
                mNavigationMgr.startNavigationWithRoute(routeData, false, testRoute.trafficRoute, true);
            } else {
                mLogger.i( "test destination not found");
            }
            return true;

        } else if( routeData.routingLatitude.equals("0") && routeData.routingLongitude.equals("2") ) {

            mLogger.i( "Get current active destination and waypoints");
            Thread t = new Thread(TAG + "_GET_ACTIVE_DEST_WAYPOINTS") {
                public void run() {
                    try {
                        ArrayList<Location> result = Navigation.Instance().getCurrentDestinationAndWaypoints();
                        if( result == null ) {
                            mLogger.i( "Current destination and waypoints is null" );
                        } else {
                            if( result.size() > 0 ) {
                                mLogger.i( "Current destination: " + result.get(0).getLatitude() + ", " + result.get(0).getLongitude() );
                            } else {
                                mLogger.i( "Current destination and waypoints is empty" );
                            }

                            for( int i = 1; i < result.size(); i++ ) {
                                mLogger.i( "Current waypoint " + i + ": " + result.get(i).getLatitude() + ", " + result.get(i).getLongitude() );
                            }
                        }
                    } catch( Exception e ) {
                        mLogger.i( "Current location lookup fail: ", e );
                    }
                }
            };
            t.start();
            return true;

        } else if( routeData.routingLatitude.equals("0") && routeData.routingLongitude.equals("3") ) {
            // lookup current location => address and time how long it takes
            mLogger.i( "Test current location lookup");
            Thread t = new Thread(TAG + "_TEST_CURRENT_ADDRESS") {
                public void run() {
                    try {
                        long start = SystemClock.uptimeMillis();
                        JSONObject currentAddress = Navigation.Instance().getCurrentAddress();
                        long delta = SystemClock.uptimeMillis() - start;
                        mLogger.i( "Current location [t=" + delta + "] " + 
                                    (currentAddress == null ? "failed" : currentAddress.toString(4)) );
                    } catch( Exception e ) {
                        mLogger.i( "Current location lookup fail: ", e );
                    }
                }
            };
            t.start();
            return true;
        } else if( routeData.routingLatitude.equals("0") && routeData.routingLongitude.equals("4") ) {
            // lookup address and start navigation
            mLogger.i( "Test navigation to address");
            Thread t = new Thread(TAG + "_TEST_NAVIGATE_TO_ADDRESS") {
                public void run() {
                    Location location = mNavigation.getLocationByAddress("EC","", "quito", "Manuel Najas", "Juan de Selis", "");
                    NavigationManager.RouteData routeData = new NavigationManager.RouteData();
                    routeData.routingLatitudeReference  = "";
                    routeData.routingLongitudeReference = "";
                    routeData.routingLatitude           = String.valueOf(location.getLatitude());
                    routeData.routingLongitude          = String.valueOf(location.getLongitude());
                    routeData.routingText               = "";
                    routeData.routingTextPhonetic       = "";
                    routeData.routingAction             = NavigationManager.RouteDataOrigin.USER.ordinal();
                    routeData.routingTextOpcode         = 0;
                    routeData.routingTimeStamp          = 0;
                    routeData.dataState                 = NavigationManager.RouteDataState.NOT_ACTIVE;
                    routeData.routingTrafficUsed        = false;
                    mNavigationMgr.startNavigationWithRoute(routeData, false, null, true);        
                }
            };
            t.start();
            return true;
        } else if( routeData.routingLatitude.equals("1") && routeData.routingLongitude.equals("1") ) {
            // stop navigation
            mLogger.i( "Stop navigation");
            Thread t = new Thread(TAG + "_TEST_STOP_NAVIGATION") {
                public void run() {
                    mNavigation.stopNavigation(true);
                }
            };
            t.start();
            return true;
        }

        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private TestRoute GetTestDestinationAndWaypoints() {

        final String DEBUG_FILE_PATH = "/data/data/roadtrack/navtest";
        final int    MAX_WAYPOINTS = 100;

        ArrayList<Location> locations = new ArrayList<Location>();
        TestRoute testroute = new TestRoute();

        // load destination
        testroute.destLat = RTLocalParamManager.GetLocalStringParam(DEBUG_FILE_PATH, "destLat", "");
        testroute.destLon = RTLocalParamManager.GetLocalStringParam(DEBUG_FILE_PATH, "destLon", "");
        int numPoints = 0;

        if( !testroute.destLat.isEmpty() && !testroute.destLon.isEmpty() ) {
            for( int i = 0; i < MAX_WAYPOINTS; i++ ) {
                String lat = RTLocalParamManager.GetLocalStringParam(DEBUG_FILE_PATH, "wp " + i + " lat", "");
                String lon = RTLocalParamManager.GetLocalStringParam(DEBUG_FILE_PATH, "wp " + i + " lon", "");
                if( lat.isEmpty() || lon.isEmpty() ) {
                    break;
                }
                Location loc = new Location("debug");
                try {
                    loc.setLatitude(Double.parseDouble(lat));
                    loc.setLongitude(Double.parseDouble(lon));
                    locations.add(loc);
                    numPoints += 1;
                    mLogger.i("test waypoint " + i + ": " + loc);
                } catch( Exception e ) {
                    mLogger.w("failed to parse coordinates: " + lat + ", " + lon, e);
                }
            }
        }

        if( numPoints > 0 ) {
            TrafficRoutingServices svc = new TrafficRoutingServices(mContext);
            testroute.trafficRoute = svc.new  Route();
            testroute.trafficRoute.TravelTimeMin = 1;
            testroute.trafficRoute.RouteName = "Test Route";
            testroute.trafficRoute.Distance = 1;
            testroute.trafficRoute.RouteQuality = TrafficRoutingServices.eRouteQuality.FreeFlow;
            testroute.trafficRoute.WayPoints = locations;
        }

        if( testroute.destLat.isEmpty() || testroute.destLon.isEmpty() ) {
            return null;
        }
        mLogger.i("Loaded destination and " + numPoints + " waypoints from " + DEBUG_FILE_PATH);
        return testroute;
    }

}
