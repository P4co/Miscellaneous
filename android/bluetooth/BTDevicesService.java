package com.roadtrack.bluetooth;

/******************************************************************************
* Copyright (C) 2012 Road-Track Telematics Development
*
* Description: Auto-connection of Bluetooth devices
*			   Initiate Phone book download
*			   Connect MAP service
*			   Set Bluetooth parameters: BT Name, Max pairing etc.
*           
******************************************************************************/

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHFP;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothPhonebookClient;
import android.bluetooth.BluetoothPhonebookClient.BluetoothPhonebookClientIntent;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPanu;
import com.csr.bluetooth.BluetoothIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.internal.app.ShutdownThread;

import android.util.Log;
import android.util.Slog;
import java.util.concurrent.Semaphore;

import android.os.*; 

import android.content.pm.IPackageDataObserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.Object;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import android.roadtrack.pconf.PConf;
import android.roadtrack.pconf.PConfParameters;
import android.roadtrack.indicationmgr.IndicationManager;
import android.roadtrack.indicationmgr.IndicationMgrParams;
import android.speech.tts.*;
import android.roadtrack.stringservice.StringReader;
import android.roadtrack.dispatch.RoadTrackDispatcher;
import android.roadtrack.eventrepository.EventRepositoryReader;
import android.roadtrack.eventrepository.EventRepositoryReader.IgnigtionState;
import android.roadtrack.eventrepository.EventRepositoryReader.DoorState;
import com.roadtrack.eventrepository.EventData;
import com.roadtrack.util.RTLog;
import com.roadtrack.util.LittleEndianInputStream;
import com.roadtrack.util.LittleEndianOutputStream;
import com.roadtrack.vdp.RTMediaHandler;
import com.roadtrack.vdp.RTMediaAvrcp;
import com.roadtrack.vdp.PhoneHandler;
import com.roadtrack.gmlan.GmlanNotificationInterface;
import com.roadtrack.hmi.HmiDisplayService;
import com.roadtrack.hmi.HmiDisplayService.HmiIconId;
import com.roadtrack.pnd.PNDHandler;
import com.roadtrack.RTUpdateService;
import com.roadtrack.pnd.PNDHandler;

import android.roadtrack.p2p.MessageGroups;
import android.roadtrack.p2p.MessageCodes;
import android.roadtrack.p2p.IMessage;
import android.roadtrack.p2p.UnknownMessage;
import android.roadtrack.p2p.P2PService;
import android.roadtrack.p2p.P2PService.IMessageReciever;

import android.os.Power;
import android.os.PowerManager;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetooth;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import android.os.RemoteException;

public class BTDevicesService extends Binder implements ISPPConnection {
		
	private static boolean mDebug = true;

    private static final String DATA_DIR = "/data";
    private static final String SUB_DATA_DIR = "system";
    private static final String FILE_NAME_BT_DEV = "BTDevices.txt"; // recent connected device list.
    private static final String FILE_NAME_PAN = "BTDevicesPan.txt"; // PAN enabled device list.

    private static final String TAG = "RoadTrackBTDevicesService";
	
	private static final int TYPE_MOBILE = 1;
	private static final int TYPE_HEADSET = 2;
	private static final int TYPE_HEADPHONE = 3;
	private static final int TYPE_COMPUTER = 4;
	
	private static final int BLUETOOTH_ACTIVITY_UNPAIRED = 0;
	private static final int BLUETOOTH_ACTIVITY_PAIRED = 1;
	private static final int BLUETOOTH_ACTIVITY_WAIT_CONNECT = 2;
	private static final int BLUETOOTH_ACTIVITY_IDLE = 3;
	
    private static final int MIN_AVRCP_VERSION              = 0x104;   // avrcp less than this cannot browse
		
	// local messages types
	private static final int MSG_BLUETOOTH_ON = 0;
	private static final int MSG_BLUETOOTH_OFF = 1;
	private static final int MSG_BOOT_COMPLETE = 2;
	private static final int MSG_IGNITION_ON = 3;
	private static final int MSG_IGNITION_OFF = 4;
	private static final int MSG_HFP_CONNECTED = 5;
	private static final int MSG_HFP_DISCONNECTED = 6;
	private static final int MSG_PBAP_DISCONNECTED = 7;
	private static final int MSG_PBAP_CONNECTED = 8;
	private static final int MSG_PBAP_PULL_PB_CFM = 9;
	private static final int MSG_PBAP_DISCONNECT_IND = 10;
	private static final int MSG_BT_TOGGLE_CONNECT = 11;
	private static final int MSG_ALL_DEVICES_DISCONNECTED = 12;
	private static final int MSG_SERVICE_TIMER_ELAPSED = 13;
	private static final int MSG_A2DP_CONNECTED = 14;
	private static final int MSG_BLUETOOTH_READY = 15;
	private static final int MSG_DATA_PLAN_UPDATE = 16;
	private static final int MSG_DELETE_PAIRING_LIST = 17;
	private static final int MSG_BT_DEVICE_DISCONNECTED = 18;
	private static final int MSG_PANU_CONNECTED = 19;
	private static final int MSG_PANU_DISCONNECTED = 20;
	private static final int MSG_MAP_CONNECTED = 21;
	private static final int MSG_MAP_DISCONNECTED = 22;
	private static final int MSG_AVRCP_CONNECTED = 23;
	private static final int MSG_AVRCP_DISCONNECTED = 24;
	private static final int MSG_A2DP_DISCONNECTED = 25;
	private static final int MSG_AVRCP_TIMEOUT = 26;
	private static final int MSG_DELETE_PAIRING_LIST_TIMEOUT = 27;
	private static final int MSG_REMOVE_CONTACTS_CACHE = 28;
	private static final int MSG_A2DP_TIMEOUT = 29;
	private static final int MSG_MAP_TIMEOUT = 30;
	private static final int MSG_PBAP_TIMEOUT = 31;
	private static final int MSG_POWER_DOWN_BT = 32;
	private static final int MSG_CONTACTS_COMPILED = 33;
	private static final int MSG_AVRCP_SYNC_COMPLETED = 34;
	private static final int MSG_BT_TOGGLE_CONNECT_VERBOSE = 35;
	private static final int MSG_GET_PAIRED_PHONES_INFO = 36;
	private static final int MSG_GET_CONNECTED_HFP_PHONE_INFO = 37;
	private static final int MSG_BONDED = 38;
	private static final int MSG_BOND_NONE = 39;
	private static final int MSG_IS_ANY_BT_SERVICE_CONNECTED = 40;
	private static final int MSG_GET_BT_SERVICES_CONNECTED = 41;
	private static final int MSG_SPP_CONNECTED = 42;
	private static final int MSG_SPP_DISCONNECTED = 43;
	private static final int MSG_BT_TRY_CONNECT_VERBOSE = 44;
	private static final int MSG_MAP_UUID = 45;
	private static final int MSG_HIBERNATION_TIMEOUT = 46;
	private static final int MSG_SYNERGY_RECOVERY = 47;
	private static final int MSG_POWERDOWN_TIMEOUT = 48;
	private static final int MSG_PBAP_CHECK_TIMEOUT = 49;
	private static final int MSG_PBAP_ERROR_MSG = 50;
	private static final int MSG_WAIT_SPP_BEFORE_DISCONNECT = 51;
	private static final int MSG_MAP_CHECK_TIMEOUT = 52;
	private static final int MSG_BT_USER_DISCONNECT_NO_RECONNECT = 53;
	private static final int MSG_DELAYED_PAIRING_RESPONSE = 54;
	private static final int MSG_GET_PAIRED_DEVICES = 55;
	private static final int MSG_PASSKEY_CONFIRM = 56;
	private static final int MSG_PASSKEY_CONFIRM_TIMEOUT = 57;
	
	//Message timeouts
	private static final int MSG_BLUETOOTH_READY_DELAY = 30000;
	private static final int MSG_BLUETOOTH_AVRCP_DELAY = 9000;
	private static final int MSG_BLUETOOTH_A2DP_DELAY = 7000;
	private static final int MSG_BLUETOOTH_MAP_DELAY = 15000;
	private static final int MSG_BLUETOOTH_MAP_CHECK_DELAY = 4000;
	private static final int MSG_BLUETOOTH_PBAP_DELAY = 10000;
	private static final int MSG_DELETE_PAIRING_LIST_DELAY = 60000;
	private static final int MSG_HIBERNATION_TIMEOUT_DELAY = 90000;
	private static final int MSG_SYNERGY_RECOVERY_DELAY = 60000;
	private static final int MSG_POWERDOWN_TIMEOUT_DELAY = 60000;
	private static final int MSG_BLUETOOTH_PBAP_CHECK_TIMEOUT_DELAY = 4000;
	private static final int MSG_WAIT_SPP_BEFORE_DISCONNECT_DELAY = 1000;
	private static final int MSG_PASSKEY_CONFIRM_TIMEOUT_DELAY = 25000;
	
	private static final int BLUETOOTH_ADDR_SIZE = 17; // AA:BB:CC:DD:EE:FF
	private static BluetoothAdapter mBTAdapter;		/* Bluetooth Adapter Object */
	private static int ActivityState = 0;
	private static int mPairedDevices = 0;		/* Number of Paired devices */
	private static int mConnectedDevice = -1;	/* index of connected device */
	public  static int mTryConnectDevice = -1;	/* index of trying to connect device */
	private boolean mConnectVerbose = false; // User requested connect
	private RTLog mLogger  = RTLog.getLogger(TAG, "btdevservice", RTLog.LogLevel.INFO );
	public static final int PHONEBOOK_MAX_CONTACT_NUMBER = 10000;
	public static final int PHONEBOOK_MANY_CONTACTS = 2500;//if BT phone has more contacts than this then announce TTS "Many contacts"

	// Connect Verbose levels
    public enum ERecentPairedVerbose {
    	RecentPairedVerbose_None,
    	RecentPairedVerbose_Auto,
    	RecentPairedVerbose_User,
    	RecentPairedVerbose_Pair;
	}
    private static final ParcelUuid MAP_UUID =
            ParcelUuid.fromString("00001134-0000-1000-8000-00805F9B34FB"); //MAP
	private ERecentPairedVerbose mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
	// SMS feature related constants
	private static final int 	SMS_FEATURE_ENABLED_MASK		 	  	= 1;
	private static final int 	SMS_SEND_FEATURE_ENABLED_MASK	 	  	= 2;
	private static final int 	SMS_DICTATION_FEATURE_ENABLED_MASK   	= 4;
	private static final int 	SMS_RECEIVE_AND_SEND_DICTATION_ENABLED  = 7;
	
	private static final int MAX_PAIRED_DEVICES = 10;
	private static final int MAX_AllOWED_PAIRED_DEVICES = 5;
	private BluetoothDevice[] BTDevicesArray = new BluetoothDevice[MAX_PAIRED_DEVICES]; 
	private String [] DevicesName = new String[MAX_PAIRED_DEVICES];
	private String [] DevicesAddr = new String [MAX_PAIRED_DEVICES];
	private Integer [] DevicesType = new Integer [MAX_PAIRED_DEVICES];
	private List<DeviceServices> mDevicesConnected = new ArrayList<DeviceServices>();
	
	private volatile List<String> DevicesTryConnectedAddr = new ArrayList<String>();
	private volatile List<String> DevicesRecentConnectedAddr = new ArrayList<String>();
	private volatile List<String> DevicesPanEnabled = new ArrayList<String>();
	private List<BTDeviceInfo> mPairedPhonesInfo = new ArrayList<BTDeviceInfo>();
	private BTDeviceInfo mConnectedHfpPhoneInfo = new BTDeviceInfo();
	
	private BTContactsCache mBtContactsCache = null;
	private BTRecentPairedDB mBtRecentPaired = null;
	private BluetoothHFP btHFP = new BluetoothHFP();
	private BluetoothA2dpSink btA2dp;
	private volatile String mA2dpBTAddress = "";
	private volatile String mAvrcpBTAddress = "";
	private volatile int mAvrcpVersion = 0;
	private volatile boolean mAvrcpSyncComplete = false;
	private BluetoothPhonebookClient  mBluetoothPhonebookClient = new BluetoothPhonebookClient();
	public static BluetoothMapClient mBluetoothMapClient = new BluetoothMapClient();
    private static Boolean BluetoothPhonebookConnected = false;
    private Boolean BluetoothPhonebookSynchronizing = false;
	private BluetoothPanu btPanu = new BluetoothPanu();
    private RTMediaHandler mMediaHandler = null;
    private RTMediaAvrcp mAvrcpMedia = null;
    private EventRepositoryReader RepositoryReader;
    private Boolean mDeletePairingList = false;
    private Boolean mDeleteAllDevices = false;
    private Boolean mPowerDownBT = false;
    private int hfpService = 0;
    private int hfpSignal = 0;
    private int mContactsCompiled = 0;
    private boolean mMapTimeout = false;
    private String mSppAddress = "";
  
    private String DisconnectAddr = ""; 
	private Thread  mBTThread;
	private Handler mHandler;
    private File file;
    private Context mContext;
    private static boolean bootCompleted = false;
    private IgnigtionState ignitionState = IgnigtionState.IGNITION_OFF;
  
    private int connectRetries;
    private static final int BLUETOOTH_CONNECT_RETRIES = 1;
    
    // for speaking TTS
	private TextToSpeech mTts;
	private StringReader mStrings;
	
    private PConf mPconf;
    private String myBTName;
    private static final String BT_NAME_NOT_FOUND = "???";
    private String myBTPairingCode;
    private static final String BT_DEFAULT_PAIRING_CODE = "1234";
    private static final String BT_PAIRING_CODE_NOT_FOUND = "-1";
    public static final int BT_PAIRING_TIMEOUT_SECONDS	=	120;
    private int maxPairing;
    private boolean PBSyncEnabled = true;
    private boolean BTRadioEnabled = false;
    private static final Object mPairedPhonesAvailable = new Object();
    private static final Object mConnectedHfpPhoneAvailable = new Object();
    private static final Object mIsConnectedServicesAvailable = new Object();
    private boolean mIsAnyServiceConnected = false;
    private HmiDisplayService mHmiDisplayService = null;

    private boolean  mBluetoothRecoveryInProgress = false;

    private RoadTrackSPP.SppConnection mConnectionDesc  = null;
    
    private static boolean mBtReady = false;
    private boolean mIsReadyToEnableBT = true;
    
    private static BTDevicesService btDevices = null;
    
    private static PNDHandler mPNDHandler = null;
    
    private static P2PService      mP2P;
    private P2PReceiver     mP2PReceiver;
    
    private volatile int mUnsuccessfulPairingAttempts = 0;
    
    private final int SRV_NONE = 0;
    private final int SRV_HFP = 0x1;
    private final int SRV_PBAP = 0x2;
    private final int SRV_A2DP = 0x4;
    private final int SRV_AVRCP = 0x8;
    private final int SRV_MAP = 0x10;
    private final int SRV_PAN = 0x20;
    private final int SRV_SPP = 0x40;

    private volatile BluetoothDevice mPasskeyDevice = null;
    
    //Disable BT services
    public static final int DISABLE_SRV_NONE = 0;
    public static final int DISABLE_SRV_HFP = 0x1;
    public static final int DISABLE_SRV_A2DP_RCV = 0x2;
    public static final int DISABLE_SRV_A2DP_SEND = 0x4;
    public static final int DISABLE_SRV_AVRCP = 0x8;
    public static final int DISABLE_SRV_AG = 0x10;
    public static final int DISABLE_SRV_SPP = 0x20;
    public static final int DISABLE_SRV_PAN = 0x40;
    public static final int DISABLE_SRV_MAP = 0x80;

    
    // contacts sync intent 
    public  static final String CONTACTS_SYNC_UPDATE_INTENT = "com.roadtrack.bluetooth.CONTACTS_SYNC_UPDATE";
    public  static final String CONTACTS_SYNC_STATUS_EXTRA = "com.roadtrack.bluetooth.CONTACTS_SYNC_STATUS";
    public  static final String CONTACTS_SYNC_ADDRESS_EXTRA = "com.roadtrack.bluetooth.CONTACTS_SYNC_ADDRESS";
    //This will always be included for CONTACTS_STATUS_FINAL_AVAIALABLE. 1 - contacts changed. 0 - no change in contacts. 
    public  static final String CONTACTS_SYNC_CHANGE_EXTRA = "com.roadtrack.bluetooth.CONTACTS_SYNC_CHANGE";
    
    // contacts sync status
    public static final int CONTACTS_STATUS_SYNCHRONIZING = 0;
    public static final int CONTACTS_STATUS_CACHE_AVAIALABLE = 1;
    public static final int CONTACTS_STATUS_FINAL_AVAIALABLE = 2;
    public static final int CONTACTS_STATUS_FINAL_NOT_AVAIALABLE = 3;
    
    // Bluetooth status intent 
    public  static final String BLUETOOTH_STATUS_INTENT = "com.roadtrack.bluetooth.BLUETOOTH_STATUS_INTENT";
    public  static final String BLUETOOTH_STATUS_EXTRA = "com.roadtrack.bluetooth.BLUETOOTH_STATUS";
    
    // Bluetooth status
    public static final int BLUETOOTH_STATUS_UNREADY = 0;
    public static final int BLUETOOTH_STATUS_READY = 1;

    /*
     * called by PND to set the PND Handle
     */
    public static void registerPNDHandler( PNDHandler pndHandler )
    {
        mPNDHandler = pndHandler;
    }
    
    /**
     * called when SPP connection is istableshed. receives SppConnection 
     * class instance, holding connection details.
     */
    public void connectionInitiated(RoadTrackSPP.SppConnection connectionDesc) {
    	mConnectionDesc = connectionDesc;
		mSppAddress = mConnectionDesc.address;
    	mLogger.i( "SPP connected, Address: " + mSppAddress);
    	mHandler.sendMessage( mHandler.obtainMessage( MSG_SPP_CONNECTED ));
    }
    
    /**
     * called when SPP connection is lost
     */
    public void connectionLost() {
    	mLogger.i( "SPP disconnected, Address: " +  mSppAddress);
    	mHandler.sendMessage( mHandler.obtainMessage( MSG_SPP_DISCONNECTED, mSppAddress));
    	mSppAddress = "";
    }

    /*
     * Returns the required Client SPP connection event to be used for connectionInitiated()
     */
    public EClientSppEventMode getClientSppEventMode() {
    	return ISPPConnection.EClientSppEventMode.EventMode_Spp;
    }
    
    class DeviceServices {
    	String mDevaddr;
    	int services;
    }

    public class BTDeviceInfo {
    	public String name;
    	public String addr;
    	public boolean isConnected;
    	public boolean isInRange;
    }
    
    public static BTDevicesService getInstance()
    {
        boolean FVTMode = SystemProperties.get("ro.bootmode").equals("factorytest") ;
        if (FVTMode) {
					Log.d(TAG,"BTDevicesService.getInstance returning instance BTDevicesFVTService");
      	return BTDevicesFVTService.getInstance();
        }
        else{
    		return btDevices;
    	}
    }
    
	public BTDevicesService(Context context) {
        super();
        boolean FVTMode = SystemProperties.get("ro.bootmode").equals("factorytest") ;
        if (FVTMode)
        {
        	mLogger.i( "FVT mode. BT Service exited");
        }
        else
        {
        	mLogger.i( "Server BT Service Created");
        	mContext = context;
        	mBtContactsCache = new BTContactsCache(mContext);
        	mBtRecentPaired = new BTRecentPairedDB(mContext);

        	if (btDevices != null) {throw new RuntimeException("You cannot create a new instance of this class");}
        	btDevices = this;
			IndicationManager.setIndication(context, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);
			BluetoothPhonebookSynchronizing = false;

	 		ActivityState = BLUETOOTH_ACTIVITY_UNPAIRED;
	 		//A2DP
	 		btA2dp = new BluetoothA2dpSink();
	 		//AVRCP
	 		mMediaHandler = RTMediaHandler.getInstance();
	 		if (mMediaHandler != null)
	 		{
	 			mAvrcpMedia = mMediaHandler.getAvrcpMedia();
	 			if (mAvrcpMedia != null)
	 				print("received mAvrcpMedia");
	 			else
	 				print("failed to receive mAvrcpMedia");
	 		}
	 	


			/**
	     	 * handles different messages in it's own thread. Messages were sent from local broadcast receivers. Messages are received for boot complete, ignition status , Bluetooth services etc.
	     	 * 
	     	 */
			mBTThread = new Thread( new Runnable() {
				@Override
				public void run() {
					mLogger.i(  "thread started" );
					Looper.prepare();
	            	mHandler = new Handler() {
	            		@Override
	            		public void handleMessage( Message msg ) {
	            			messageHandler( msg );
	            		}
	            	};

	            	Looper.loop();
				}
			}, "BTThread");
	
			mBTThread.start();
		
	    	InitRegistrations();
	 		ignitionState = RepositoryReader.getIgnitionOnOffEvent();
	 	
	 		// get parameters
	    	mPconf = (PConf)context.getSystemService(context.PCONF_SYSTEM_SERVICE);
	    	myBTName = mPconf.GetTextParam(PConfParameters.BT_Name, BT_NAME_NOT_FOUND);
	    	getMaxPairing();
	    	PBSyncEnabled = getPBSyncEnabled();
	    	BTRadioEnabled = getBTRadioEnabled();
	    	readBTPairingCode();
	    	mHmiDisplayService = HmiDisplayService.getInstance();
	    
	    	RoadTrackSPP.registerClient(this);
		
			// Update A2DP configuration according to profile setting
	    	updateMusicStreamingChannel();
		}
	}

	/**
	 * returns true if passkey pairing confirmation is waiting for Chevy key to be pressed.
	 */
	public boolean isPasskeyPairingActive()
	{
		return (mPasskeyDevice != null);
	}

	/**
     * returns a list of recently connected BT devices sorted from most recently used to least recently used.
     * 
     */
	public List< BTDeviceInfo > getPairedPhones()
	{
		synchronized (mPairedPhonesAvailable)
   		{
   			try {
   				mHandler.sendMessage( mHandler.obtainMessage( MSG_GET_PAIRED_PHONES_INFO ));
   				mPairedPhonesAvailable.wait();
    			return mPairedPhonesInfo;
       		} catch(Exception e){
       			mLogger.e( "getPairedPhones() ", e);
       			return null;
       		}
   		}
	}
	
	/**
     * returns the connected HFP phone BT info or null if not connected.
     * 
     */
	public BTDeviceInfo getConnectedHfpPhone()
	{
    	mHandler.sendMessage( mHandler.obtainMessage( MSG_GET_CONNECTED_HFP_PHONE_INFO ));
   		synchronized (mConnectedHfpPhoneAvailable)
   		{
   			try {
    			mConnectedHfpPhoneAvailable.wait();
    			if (mConnectedHfpPhoneInfo.isConnected)
    				return mConnectedHfpPhoneInfo;
    			else
    				return null;
       		} catch(Exception e){
       			mLogger.e( "getConnectedHfpPhone() ", e);
       			return null;
       		}
   		}
	}
	
	public int getBTPairingTimeout()
	{
		int BTTimeout = mPconf.GetIntParam(PConfParameters.BT_DiscoverabilityTime, 0);
		if (BTTimeout == 0)
		{
			BTTimeout = BT_PAIRING_TIMEOUT_SECONDS;
		}
		else
		{
			BTTimeout *= 15;//15 secs steps
		}
		return BTTimeout;
	}
	
	/**
     * Tries to connects a BT phone if Ignition is on and Auto-connect is idle.
     * If device address is not empty will try to connect the specified device. 
     *  - If a device is already connected and it differs from requested device, it will be disconnected before doing the requested connect.
     * If device address is empty Auto-connect algorithm is performed.  
     * 
     */
	public void connectBtDevice(String deviceAddress)
	{
		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_TOGGLE_CONNECT_VERBOSE, deviceAddress));
	}
	
	/**
	 * Request to disconnect the current connected device without reconnecting another device
	 * Call isOkayToConnect() to verfiy disconnect has terminated.
	 * If calling setBTDiscoverable() verify the returned result
	 */
	public void disconnectPhone()
	{
		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_USER_DISCONNECT_NO_RECONNECT ) );
	}
	
	public void avrcpSyncCompleted(int status) {
		if (!mAvrcpSyncComplete)
			mHandler.sendMessage( mHandler.obtainMessage( MSG_AVRCP_SYNC_COMPLETED , status));
	}
	
	public void deletePairingList() {
			mHandler.sendMessage( mHandler.obtainMessage( MSG_DELETE_PAIRING_LIST ));
	}
	
	public void BluetoothPowerDown() {
		if (mBTAdapter != null) {
			if (!mHandler.hasMessages(MSG_POWER_DOWN_BT))
			{
				mHandler.sendMessage( mHandler.obtainMessage( MSG_POWER_DOWN_BT ));
				mLogger.i( "power down Bluetooth request");
			}
		}
	}

	public Boolean IsSynchronizingContacts()
	{
		return BluetoothPhonebookSynchronizing;
	}
	
	public Boolean IsPanConnected()
	{
		if (mConnectedDevice > -1)
		{
			if ((btPanu.getConnectionState(DevicesAddr[mConnectedDevice])) ==
					BluetoothPanu.BLUETOOTH_PANU_CONNECTED)
				return true;
			else
				return false;
		}
		else
			return false;
	}
	
	public Boolean IsDataplanApproved()
	{
		if (mConnectedDevice > -1)
		{
			if (DevicesPanEnabled.contains(DevicesAddr[mConnectedDevice]))
				return true;
			else
				return false;
		}else
			return false;		
	}

	public void SetContactsCompiled()
	{
		mHandler.sendMessage( mHandler.obtainMessage( MSG_CONTACTS_COMPILED ));
		print("Contacts compiled");
	}
	
	public Boolean IsDataplanSupported()
	{
		int disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
		mLogger.i("Disabled services: " + disabledServices);
		if ((disabledServices & DISABLE_SRV_PAN) == 0)
		{
			if (mConnectedDevice > -1)
			{
				if (btPanu.isPanNapSupport(DevicesAddr[mConnectedDevice]) == 1)
					return true;
				else
					return false;
			}
			else
				return false;
		}
		else
		{
			return false;
		}
	}

	//to be used to connect/disconnect PAN and approve using PAN data plan for a mobile already connected HFP	
	public void connectDataPlan(boolean useDataPlan)
	{
		if (mConnectedDevice > -1)
		{
			setDataPlanUsage(DevicesAddr[mConnectedDevice], useDataPlan);
		}
		else
		{
			mLogger.i("mobile not connected. Can't enable/disable PAN");
		}
	}

	// to be used to approve using PAN data plan during pairing phase and if possible (deviceAddress is already connected HFP) connect PAN
	public void setDataPlanUsage(String deviceAddress, boolean useDataPlan)
	{
		if (useDataPlan){
			mHandler.sendMessage( mHandler.obtainMessage( MSG_DATA_PLAN_UPDATE, 1, 0, deviceAddress));
		}else{
			mHandler.sendMessage( mHandler.obtainMessage( MSG_DATA_PLAN_UPDATE, 0, 0, deviceAddress));
		}
	}
    public Boolean BtHasCallService() {
    	if ((hfpService == 1) && (hfpSignal > 0))
    		return true;
    	else
    		return false;
    }
	
	public static int getNumberPairedDevices() {
		if (isBluetoothReady()) {
			return mPairedDevices; 
		}
		else
			return -1;
	}
	
	public static boolean isBluetoothReady() {
		if ((mBTAdapter != null) && mBtReady && (ActivityState != BLUETOOTH_ACTIVITY_UNPAIRED))
			return true;
		else
			return false;
	}
	
	/**
	 * Sets Bluetooth discoverable.
	 * Returns "false" if not ready to set discoverable.
	 */
	public boolean setBTDiscoverable() {
		if ((mBTAdapter != null) && mBTAdapter.isEnabled() && bootCompleted && isOkayToConnect()) {
			mUnsuccessfulPairingAttempts = 0;
			if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,getBTPairingTimeout()) == true)
				mLogger.i( "setBTDiscoverable(): set SCAN_MODE_CONNECTABLE_DISCOVERABLE");
			else
				mLogger.w( "setBTDiscoverable(): failed setting SCAN_MODE_CONNECTABLE_DISCOVERABLE");
			return true; 
		}
		else
			return false;
	}

    public boolean isBtDeviceConnected() {
 		synchronized (mIsConnectedServicesAvailable)
   		{
   			try {
   				mHandler.sendMessage( mHandler.obtainMessage( MSG_IS_ANY_BT_SERVICE_CONNECTED ));
   				mIsConnectedServicesAvailable.wait();
    			return mIsAnyServiceConnected;
       		} catch(Exception e){
       			mLogger.e( "isBtDeviceConnected() ", e);
       			return false;
       		}
   		}
    }
    
    public boolean isOkayToConnectAvrcp() {
		if ( (!mPowerDownBT) && (!mDeletePairingList) && (!mA2dpBTAddress.isEmpty()) && (!mDeleteAllDevices) && DisconnectAddr.isEmpty() )
			return true;
		else
			return false;
    }
    
    /**
     * returns "true" if okay to connect or pair a new device.
     */
    public boolean isOkayToConnect() {
 		if ( (!mPowerDownBT) && (!mDeletePairingList) && (!mDeleteAllDevices) && DisconnectAddr.isEmpty() && (-1 == mConnectedDevice))
 			return true;
 		else
 			return false;
     }
    
    private synchronized void getIsBtDeviceConnected() {
    	mIsAnyServiceConnected = false;
        for ( DeviceServices device : mDevicesConnected ) {
            if (device.services != SRV_NONE) {
            	mIsAnyServiceConnected = true;
            	break;
            }
        }
  		synchronized (mIsConnectedServicesAvailable) {
  			mIsConnectedServicesAvailable.notifyAll();
   		}
    }
    
	private void print(String s)
	{
		mLogger.d(s);
	}

	private void sendBtErrStatus(boolean error) {
		byte[] rawData = {0};
		if (error) {
			rawData[0] = 1;
		}
       	try{
       		mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_BT, MessageCodes.MESSAGE_CODE_GENERAL_BT_NOT_OK, rawData );
       	}
       	catch ( Exception e ) {
            mLogger.e(  "sendBtErrStatus()", e );
        }
	}
	
	private void InitRegistrations() {
		 
		print("Bluetooth init");	
		
		mTts = new TextToSpeech( mContext, null );
		mStrings = (StringReader)mContext.getSystemService(mContext.STRING_SYSTEM_SERVICE);
		mBTAdapter = BluetoothAdapter.getDefaultAdapter();
	 	if (mBTAdapter == null)
	 	{
   	 		mLogger.e( "Bluetooth not supported. Aborting init!!");
   	 		return;
	 	}

	    // register for boot complete
        IntentFilter bootFilter = new IntentFilter();
	    bootFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
	    mContext.registerReceiver( mBootBroadcastReceiver, bootFilter);
	    
        // Get intent when hibernation starts/ends
        IntentFilter hibernateFilter = new IntentFilter();
        hibernateFilter.addAction(Intent.ACTION_HIBERNATION);
        hibernateFilter.addAction(Intent.ACTION_HIBERNATION_BACK);
        mContext.registerReceiver(mHibernateReceiver, hibernateFilter);

	    // Register filter to receive Bluetooth ON/OFF intents
	 	IntentFilter bluetoothStatusFilter = new IntentFilter();
	 	bluetoothStatusFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
	 	bluetoothStatusFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
	 	mContext.registerReceiver(mBluetoothStatusReceiver, bluetoothStatusFilter);
	 	
		// register for HFP connection state change event
        IntentFilter filter = new IntentFilter();
	    filter.addAction(BluetoothHFP.ACTION_HFP_STATUS_CHANGED);
        filter.addAction(BluetoothHFP.ACTION_HFP_SERVICE_CHANGED);
        filter.addAction(BluetoothHFP.ACTION_HFP_SIGNAL_STRENGTH_CHANGED);
        filter.addAction(BluetoothHFP.ACTION_BLUETOOTH_ABORT);
        filter.addAction(BluetoothHFP.ACTION_BLUETOOTH_CHIP_COMM_FAIL);
        filter.addAction(BluetoothIntent.ACTION_SYNERGY_STACK_READY);
		mContext.registerReceiver(mBroadcastReceiver, filter); 
	    
		// register for PBAP connection state change event
        IntentFilter pbapfilter = new IntentFilter();
        pbapfilter.addAction(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_CONNECTION_STATUS_CHANGED);
        pbapfilter.addAction(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_MSG_RECEIVED);
        mContext.registerReceiver(mPbapBroadcastReceiver, pbapfilter);

		// register for  Pairing state change event
        IntentFilter bondFilter = new IntentFilter();
        bondFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
      	bondFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        mContext.registerReceiver(mBondBroadcastReceiver, bondFilter);
        
        // register for A2DP connection state change event
        IntentFilter a2dpAvrcpFilter = new IntentFilter();
        a2dpAvrcpFilter.addAction(BluetoothA2dpSink.BluetoothA2dpSinkIntent.BLUETOOTH_A2DP_SINK_CONNECTION_STATUS_CHANGED);
        a2dpAvrcpFilter.addAction(BluetoothIntent.BLUETOOTH_AVRCP_CON_IND_ACTION);
        a2dpAvrcpFilter.addAction(BluetoothIntent.BLUETOOTH_AVRCP_DIS_CON_IND_ACTION);
        mContext.registerReceiver(mBluetoothA2dpAvrcpReceiver, a2dpAvrcpFilter);

        // register for Parameters change event
        IntentFilter parameterFilter = new IntentFilter(PConf.PCONF_PARAMUPDATED);
        mContext.registerReceiver(mParameterBroadcastReceiver, parameterFilter);
        
		// register for PANU connection state change event
        IntentFilter panuFilter = new IntentFilter();
	    panuFilter.addAction(BluetoothPanu.BluetoothPanuIntent.BLUETOOTH_PANU_CONNECTION_STATUS_CHANGED);
		mContext.registerReceiver(panuBroadcastReceiver, panuFilter);
		
	    // register for Keys press event
        IntentFilter keyFilter = new IntentFilter(RoadTrackDispatcher.BT_KEY_UPDATED);
        mContext.registerReceiver(mKeyBroadcastReceiver, keyFilter);
        
        // register for Ignition events
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(EventData.ACTION_EVENT_UPDATED);
        mContext.registerReceiver( mEventBroadcastReceiver, eventFilter);
        
        // register for Bluetooth connection events
        IntentFilter connectionFilter = new IntentFilter();
        connectionFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        connectionFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mContext.registerReceiver(mBluetoothConnectionReceiver, connectionFilter);

       	// register for MAP events
        IntentFilter mapFilter = new IntentFilter();      
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE);
        mContext.registerReceiver(mMapBroadcastReceiver, mapFilter);
        
      	// register for UUID events
        IntentFilter uuidIntentFilter = new IntentFilter(BluetoothDevice.ACTION_UUID);
        mContext.registerReceiver(mMapUuidBroadcastReceiver, uuidIntentFilter);
        
        mP2PReceiver = new P2PReceiver();
        mP2P = (P2PService)mContext.getSystemService(Context.P2P_SYSTEM_SERVICE);
        // Register for BT messages from MCU
        mP2P.register(MessageGroups.MESSAGE_GROUP_BT, mP2PReceiver);
        
        RepositoryReader = (EventRepositoryReader)mContext.getSystemService(Context.EVENTREP_SYSTEM_SERVICE);
        
 
	}

	private void messageHandler( Message msg ) 
	{
		String mDevaddr;

		int DeviceId;
		int services;
		int disabledServices;
		int result;
    	String connectedDeviceAddr = null;
//		mLogger.d(  "messageHandler: " + msg.what);

		switch ( msg.what ) {
			case MSG_BLUETOOTH_OFF:
				print("msg: MSG_BLUETOOTH_OFF");
				// Clear state to Idle
				mDevicesConnected.clear();
       			DisconnectAddr = "";
				mDeletePairingList = false;
				mDeleteAllDevices = false;
				mPowerDownBT = false;
				mA2dpBTAddress = "";
				mAvrcpBTAddress = "";
				mSppAddress = "";
				ActivityState = BLUETOOTH_ACTIVITY_UNPAIRED;
				if ( (BTRadioEnabled == true) && (ignitionState == IgnigtionState.IGNITION_ON) && bootCompleted && (mBluetoothRecoveryInProgress == false))
				{
					BluetoothEnableWithStackReadyCheck();
				} else {
			        int mode = mPconf.GetIntParam(PConfParameters.OTA_Mode,0);
			        int otaEnable = mPconf.GetIntParam(PConfParameters.OTA_Enable, 0);
			        // if Mobile OTA is enabled and not in Apply state, disable the OTA
					if ( (mode == RTUpdateService.OTA_MODE_MOBILE) && (otaEnable == 1) && !PNDHandler.isMobileMapUpdateApplyState() )
					{
						PhoneHandler.setPlayTtsOtaMobileInterrupted();
						mLogger.i( "Disable mobile OTA!");
				        mPconf.SetIntParam(PConfParameters.OTA_Enable,0);
					}
					// if the bt param is set to 2 it means we are in a map update process and therefore we need to set the param back to disabled
					// to prevent from BT become active on next system start.
					if(mPconf.GetIntParam(PConfParameters.BT_RadioEnabled, 1) == 2){
						mPconf.SetIntParam(PConfParameters.BT_RadioEnabled, 0);

					}
				}
				break;
			case MSG_HIBERNATION_TIMEOUT:
				/*
				 * Timeout after Hibernation ended. But intent BluetoothIntent.ACTION_SYNERGY_STACK_READY was not seen yet. Try to bring up BT stack anyway
				 */
				print("msg: MSG_HIBERNATION_TIMEOUT");
				mLogger.w( "Hibernation ended but intent BluetoothIntent.ACTION_SYNERGY_STACK_READY was not seen!");
	          	bootCompleted = true;
            	mHandler.sendMessage( mHandler.obtainMessage( MSG_BOOT_COMPLETE ));
				break;
			case MSG_SYNERGY_RECOVERY:
				/*
				 * after synergy failed to start and recovery didn't start either, then satrt recovery from here. 
				 */
				print("MSG_SYNERGY_RECOVERY");
				if ( (BTRadioEnabled == true) && (ignitionState == IgnigtionState.IGNITION_ON) && bootCompleted && (mBluetoothRecoveryInProgress == false))
				{
					new recoveryBluetooth().start();
				}
				break;
			case MSG_BLUETOOTH_READY:
			case MSG_BLUETOOTH_ON:
			case MSG_BOOT_COMPLETE:
				if (msg.what == MSG_BOOT_COMPLETE)
				{
					print("msg: MSG_BOOT_COMPLETE");
					if (mBTAdapter.isEnabled())
					{
						//Bluetooth is on. Should not be on before boot complete.
						if (mBTAdapter.disable() == true)
						{
							mIsReadyToEnableBT = false;
							mLogger.i( "MSG_BOOT_COMPLETE:disable adapter and wait BT ready timeout");
            				mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_BLUETOOTH_READY ),MSG_BLUETOOTH_READY_DELAY);
							break;
						}
						else
							mLogger.w( "MSG_BOOT_COMPLETE:disable adapter failed");
					}			
				}
				else
				{
					if (msg.what == MSG_BLUETOOTH_ON)
					{
						print("msg: MSG_BLUETOOTH_ON");
						mBtReady = true;
						mUnsuccessfulPairingAttempts = 0;
						mHandler.removeMessages(MSG_SYNERGY_RECOVERY);
						disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
	      				if ((disabledServices & DISABLE_SRV_A2DP_RCV) == DISABLE_SRV_A2DP_RCV)
	       				{
	      					btA2dp.deactivate();
	       					mLogger.i("Disabled A2DP. Disabled services: " + disabledServices);
	       				}
	      				if ( ((disabledServices & DISABLE_SRV_A2DP_RCV) == DISABLE_SRV_A2DP_RCV) ||
	       						((disabledServices & DISABLE_SRV_AVRCP) == DISABLE_SRV_AVRCP) )
	       				{
	      					mAvrcpMedia.deactivateAvrcp();
	       					mLogger.i("Disabled AVRCP. Disabled services: " + disabledServices);
	       				}
					}
					else
					{
						print("msg: MSG_BLUETOOTH_READY");
						mIsReadyToEnableBT = true;
					}
				}
				if (!mBTAdapter.isEnabled())
				{
					// BT off. Wait for Bluetooth on
       				if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
    				{
        				mLogger.i( "Adapter turning off! " + msg.what);
        				break;
    				}
    				else if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON)
    				{
        				print( "Adapter turning on! " + msg.what);
    					break;
    				}
    				else
    				{
    					mLogger.i( "Adapter off! " + msg.what);
    					if ( (BTRadioEnabled == true) && (ignitionState == IgnigtionState.IGNITION_ON) )
    					{
    						if (!BluetoothEnableWithStackReadyCheck())
    							mHandler.sendMessage( mHandler.obtainMessage( MSG_BLUETOOTH_OFF ));
    					}
    				}
				}
				else
				{
					// BT on
					
					// Check name again just to be sure. Set BT adapter name
				 	String value = mBTAdapter.getName();
				    mLogger.i("local Bluetooth name: " + value);
				 	myBTName = mPconf.GetTextParam(PConfParameters.BT_Name, BT_NAME_NOT_FOUND);
				    if (!myBTName.equals(BT_NAME_NOT_FOUND))
				    {
				    	if (value != null)
				    	{
				    		if 	(!value.equals(myBTName))
				    		{
				    			mLogger.i("setting local BT Name: " + myBTName);
				    			mBTAdapter.setName(myBTName);
				    		}
				    	}
				    	else
				    	{
				    		mLogger.i("setting local BT Name: " + myBTName);
				    		mBTAdapter.setName(myBTName);	
				    	}
				    }
				    mLogger.i( "local Bluetooth address: " + mBTAdapter.getAddress());
				    
					if (BTRadioEnabled == false)
					{
						if (mBTAdapter.disable() == true)
							mLogger.i( "MSG_BOOT_COMPLETE:disable adapter");
						else
							mLogger.w( "MSG_BOOT_COMPLETE:disable adapter failed");
						break;
					}		
				 	if (!mBtReady)
				 	{
						mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_BLUETOOTH_READY),MSG_BLUETOOTH_READY_DELAY);
						break;
				 	}
					if (msg.what == MSG_BLUETOOTH_ON)
					{
						if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE) == true)
							mLogger.i( "set SCAN_MODE_CONNECTABLE");
					}
				 	// Scan mode TODO set for Tester mode
//	  			 	if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,0) == true) //always
//	  			 		mLogger.i( "MSG_BOOT_COMPLETE:set SCAN_MODE_CONNECTABLE_DISCOVERABLE");
//	  			 	else
//	  			 		mLogger.w( "MSG_BOOT_COMPLETE:failed set SCAN_MODE_CONNECTABLE_DISCOVERABLE");
            		mBtContactsCache.setNotifyContactsCacheAddr("");          		
					if (ActivityState == BLUETOOTH_ACTIVITY_UNPAIRED) {
						// Restore - recent connected devices.
						restoreRecentConnected();
						removeUnpairedDevices();
						// Restore PAN enabled devces.
						restorePanEnabled();
					
						// save recent connected devices list
						saveRecentConnectedDevicesList();
						
						ActivityState = BLUETOOTH_ACTIVITY_PAIRED;
					}
					if ( (msg.what == MSG_BLUETOOTH_ON) || (msg.what == MSG_BLUETOOTH_READY) )
					{
						NotifyClients(BLUETOOTH_STATUS_READY);
					}
					if ((ActivityState == BLUETOOTH_ACTIVITY_PAIRED) && bootCompleted) {
						if (ignitionState == IgnigtionState.IGNITION_ON)
						{
							startBluetoothConnect();
						}
						else
						{
							ActivityState = BLUETOOTH_ACTIVITY_IDLE;
						}
					}
				}
				break;
			case MSG_IGNITION_ON:
				print("msg: MSG_IGNITION_ON");
				print("DisconnectAddr:" + DisconnectAddr);
				if (bootCompleted)
				{
					if (!mBTAdapter.isEnabled())
					{
						// Wait for Bluetooth on
           				if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
        				{
            				mLogger.i( "MSG_IGNITION_ON: Adapter turning off!");
            				break;
        				}
          				else if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON)
        				{
            				mLogger.i( "MSG_IGNITION_ON: Adapter turning on! ");
        					break;
        				}
        				else
        				{
        					mLogger.i( "MSG_IGNITION_ON:Adapter off!");
        					if (BTRadioEnabled == true)
        					{
        						if (!BluetoothEnableWithStackReadyCheck())
        							mHandler.sendMessage( mHandler.obtainMessage( MSG_BLUETOOTH_OFF ));
        							
        					}
        				}
						break;
					}
					if ((!mBtReady) || !DisconnectAddr.isEmpty())
						break;
					if (ActivityState == BLUETOOTH_ACTIVITY_PAIRED)
					{
						startBluetoothConnect();
					}
					else
					{
						if (ActivityState == BLUETOOTH_ACTIVITY_IDLE)
						{
							if (mConnectedDevice == -1)
							{
								tryHFPConnect(false);
							}
						}
					}
				}
				break;
			case MSG_IGNITION_OFF:
				// Currently should never get here
				print("msg: MSG_IGNITION_OFF ");
				break;
			case MSG_HFP_CONNECTED:
				mDevaddr = (String) msg.obj;
				addDeviceService(mDevaddr, SRV_HFP);
				print("msg: MSG_HFP_CONNECTED " + mDevaddr);
				print("DisconnectAddr:" + DisconnectAddr);
   				if (ignitionState == IgnigtionState.IGNITION_OFF)
   					print("HFP connected while ignition is off");
				if ((ActivityState == BLUETOOTH_ACTIVITY_UNPAIRED) || (getPairedDeviceId(mDevaddr) == -1))
				{
					print("HFP connected. Updating pairing list");
					getPairedDevices();
				}
       			ActivityState = BLUETOOTH_ACTIVITY_IDLE;

       			mConnectedDevice = getPairedDeviceId(mDevaddr);
       			if (mConnectedDevice > -1) {
       				// Paired and connected
			        GmlanNotificationInterface.Instance().handlePhoneConnection(	DevicesAddr[mConnectedDevice], 
			        																DevicesName[mConnectedDevice] );
         			disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);

    				mMapTimeout = false;
       				mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_Auto;
       				if (mConnectVerbose)
       				{
       					mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_User;
       				}
   					BTRecentPairedDB.BTRecentPairedDeviceInfo deviceInfo = mBtRecentPaired.getRecentPairedDeviceInfo(mDevaddr);
   					if (deviceInfo != null)
   					{
     					if (deviceInfo.connect == false)
     					{
           					mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_Pair;//just paired
           					mBtRecentPaired.saveRecentPairedToDB(mDevaddr, true, deviceInfo.map);
     					}
   					}
   					else
   					{
       					mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_Pair;//just paired
       					mBtRecentPaired.saveRecentPairedToDB(mDevaddr, true, false);
       				}
       				mContactsCompiled = 0;
         			insertConnectedDevice(mDevaddr);

         			if (mDeleteAllDevices || !DisconnectAddr.isEmpty() ||
         					((disabledServices & DISABLE_SRV_HFP) == DISABLE_SRV_HFP))
         			{
         				int CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[mConnectedDevice]);
         				if (( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) && ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTING))
         				{
         					btHFP.disconnect(DevicesAddr[mConnectedDevice]);
         					print("Do HFP Disconnect");
         				}
         				return;
         			}
         			else
         			{
             			if (((disabledServices & DISABLE_SRV_SPP) == 0))
             			{
             				mLogger.i( "iAP SPP connect: " + DevicesAddr[mConnectedDevice]);
         					RoadTrackSPP.getInstance().connectAsClientToSPP(DevicesAddr[mConnectedDevice]); 
             			}
         			}
         			if (mBtContactsCache.getContactsCacheSizePerDevice(mDevaddr) > 0)
         			{
         				//enable giving cache contacts to clients
         				mBtContactsCache.setNotifyContactsCacheAddr(mDevaddr);
         				NotifyClients(CONTACTS_STATUS_CACHE_AVAIALABLE, mDevaddr,false);
         			}
         			else
         			{
         				//disable giving cache contacts to clients
         				mBtContactsCache.setNotifyContactsCacheAddr("");
         			}
         			if (DisconnectAddr.isEmpty())
         			{
         				boolean disconnectA2dpAvrcp = false;
         				if ((mSppAddress != "") && (!DevicesAddr[mConnectedDevice].equals(mSppAddress)))
         				{
         					mLogger.i("Disconnect SPP " + mSppAddress);
         					disconnectSpp();
         				}
                		if ( (mAvrcpMedia.getConnectState() == RTMediaAvrcp.ConnectState.CONNECTED) && 
                				(!DevicesAddr[mConnectedDevice].equals(mAvrcpBTAddress)) )
                		{
                			//Disconnect AVRCP
                			disconnectA2dpAvrcp = true;
                			mAvrcpMedia.disconnectAvrcp();
                     		mLogger.i("HFP and AVRCP on different devices. Disconnecting AVRCP: " + mAvrcpBTAddress + ", HFP: " + DevicesAddr[mConnectedDevice]); 
                		}
         				if ((mA2dpBTAddress != "") && (!mA2dpBTAddress.equals(DevicesAddr[mConnectedDevice])))
         				{
                    		//Disconnect A2DP
         					disconnectA2dpAvrcp = true;
                     		btA2dp.disconnect(mA2dpBTAddress);
                     		mLogger.i("HFP and A2DP on different devices. Disconnecting A2DP: " + mA2dpBTAddress + ", HFP: " + DevicesAddr[mConnectedDevice]);         					
         				}

         				// Check if AVRCP+A2DP already connected and we don't need to disconnect. 
         				// Make sure we saw the A2DP+AVRCP connect message before HFP and no disconnect was assigned there.
         				if (!disconnectA2dpAvrcp)
         				{
         					int service = getDeviceService(DevicesAddr[mConnectedDevice]);
         					if ( ((service & SRV_A2DP) == SRV_A2DP) && (btA2dp.getCurrentConnectionStatus(mDevaddr) == BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_CONNECTED) )
         					{
         						//A2DP is really connected
         						if ( ((service & SRV_AVRCP) == SRV_AVRCP) && (mAvrcpMedia.getConnectState() == RTMediaAvrcp.ConnectState.CONNECTED) )
         						{
         							//A2DP+AVRCP are really connected. Continue with MAP without delay
         							mHandler.sendMessage( mHandler.obtainMessage( MSG_MAP_TIMEOUT ) );
         							break;
         						}
         						else
         						{
         							//Only A2DP is really connected. ChecK later AVRCP+MAP
         							if (!mHandler.hasMessages(MSG_AVRCP_TIMEOUT))
         							{
         								mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_AVRCP_TIMEOUT ),MSG_BLUETOOTH_AVRCP_DELAY );
         							}
         							mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_MAP_TIMEOUT )
         	        					,MSG_BLUETOOTH_MAP_DELAY);
         							break;
         						}
         					}
         				}

         				// Need to check A2DP+AVRCP+MAP later
         				mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_A2DP_TIMEOUT )
        						,MSG_BLUETOOTH_A2DP_DELAY);
         				mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_MAP_TIMEOUT )
        						,MSG_BLUETOOTH_MAP_DELAY);
          			}
                }
       			else
       			{
       				mLogger.i("Device connected but seems not paired! Disconnecting DevicesAddr: " + mDevaddr);
					int CurrentStatus = btHFP.getCurrentConnectionStatus(mDevaddr);
					if (( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) && ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTING))
					{
						btHFP.disconnect(mDevaddr);
						mLogger.i( "HFP Disconnect");
					}
       			}
				break;
			case MSG_A2DP_CONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_A2DP_CONNECTED " + mDevaddr);
				addDeviceService(mDevaddr, SRV_A2DP);
				mAvrcpSyncComplete = false;
				mHandler.removeMessages(MSG_A2DP_TIMEOUT);
       			if ( (!mDeleteAllDevices) && DisconnectAddr.isEmpty() )
       			{
       				boolean disconnectA2dp = false;
					disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
       				if ((disabledServices & DISABLE_SRV_A2DP_RCV) == DISABLE_SRV_A2DP_RCV)
       				{
       					disconnectA2dp = true;
       					mLogger.i("Disabled services: " + disabledServices);
       				}
       				else
       				{
       					if (mConnectedDevice > -1) {
       						if (!mDevaddr.equals(DevicesAddr[mConnectedDevice]) )
       						{
       							disconnectA2dp = true;
       	                     	mLogger.i("HFP and A2DP are on different devices. A2DP: " + mA2dpBTAddress + ", HFP is:" + DevicesAddr[mConnectedDevice]);
       						}
       					}
       				}
       				if (disconnectA2dp == true)
       				{
       					if (btA2dp.getCurrentConnectionStatus(mDevaddr) != BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_DISCONNECTED)
						{
       						//Disconnect A2DP
       						btA2dp.disconnect(mA2dpBTAddress);
           					mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_A2DP_TIMEOUT ),MSG_BLUETOOTH_A2DP_DELAY);
       						mLogger.i("Disconnecting A2DP: " + mA2dpBTAddress);
						}
       				}
       				else
       				{
       					RTMediaHandler.getInstance().muteMusicForConnectMedia(true);//mute music until AVRCP concets or fails.
       					mHandler.removeMessages(MSG_AVRCP_TIMEOUT);
       					mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_AVRCP_TIMEOUT ),MSG_BLUETOOTH_AVRCP_DELAY);
       				}
       			}
				break;
			case MSG_A2DP_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_A2DP_DISCONNECTED " + mDevaddr);
				services = removeDeviceService(mDevaddr, SRV_A2DP);
				if (services == SRV_NONE)
				{
					if (mDeleteAllDevices || (DisconnectAddr.equals(mDevaddr)))
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
                	}
				}
  				break;
			case MSG_A2DP_TIMEOUT:
				print("msg: MSG_A2DP_TIMEOUT");
       			if ( (mConnectedDevice > -1) && (!mDeleteAllDevices) && DisconnectAddr.isEmpty() )
       			{
        			// HFP connected
    				mDevaddr = DevicesAddr[mConnectedDevice];
 					if (mA2dpBTAddress == "")
 					{
 						disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
 						mLogger.i("Disabled services: " + disabledServices);
 						if ((disabledServices & DISABLE_SRV_A2DP_RCV) == 0)
 						{
 							//Connect A2DP to HFP address
 							addDeviceService(mDevaddr, SRV_A2DP);
 							btA2dp.connect(mDevaddr);
 							print("mA2dpBTAddress " + mA2dpBTAddress);
 							mLogger.i("connect A2DP " + mDevaddr);
 						}
 					}
       			}
				break;
			case MSG_PANU_CONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_PANU_CONNECTED " + mDevaddr);
				addDeviceService(mDevaddr, SRV_PAN);
				break;
			case MSG_PANU_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_PANU_DISCONNECTED " + mDevaddr);
				services = removeDeviceService(mDevaddr, SRV_PAN);
				if (services == SRV_NONE)
				{
					if (mDeleteAllDevices || (DisconnectAddr.equals(mDevaddr)))
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
                	}
				}
				break;
			case MSG_MAP_CONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_MAP_CONNECTED " + mDevaddr);
				addDeviceService(mDevaddr, SRV_MAP);
				break;
			case MSG_MAP_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_MAP_DISCONNECTED " + mDevaddr);
				services = removeDeviceService(mDevaddr, SRV_MAP);
				if (services == SRV_NONE)
				{
					if (mDeleteAllDevices || (DisconnectAddr.equals(mDevaddr)))
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
                	}
				}
				break;
			case MSG_MAP_TIMEOUT:
				/* when this timeout occurrs HFP, A2DP, AVRCP have been handled and presumably are connected. Now do the following:
				 * - If first connect after pair and MAP is supported play TTS "Trying to connect to your short messages"
				 * - If not first connect after pair and we have a contacts cache, play TTS "Phone connected and ready to use".
				 * - If PAN is enabled connect PAN.
				 * - If MAP is enabled connect MAP.
				 * - Send delayed looper message for PBAP timeout.
				 */
				print("msg: MSG_MAP_TIMEOUT");
				mMapTimeout = true;
      			if ( (mConnectedDevice > -1) && (!mDeleteAllDevices) &&	DisconnectAddr.isEmpty() )
       			{
       				String device = DevicesAddr[mConnectedDevice];
      				disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
      				if	(mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair)
    				{
      					if ( ((mPconf.GetIntParam(PConfParameters.ADVISOR_EnableBtSMSSupport, SMS_RECEIVE_AND_SEND_DICTATION_ENABLED) & SMS_FEATURE_ENABLED_MASK) == SMS_FEATURE_ENABLED_MASK)
    	       					&& ((disabledServices & DISABLE_SRV_MAP) == 0) )
      	       			{
      	   					BTRecentPairedDB.BTRecentPairedDeviceInfo deviceInfo = mBtRecentPaired.getRecentPairedDeviceInfo(device);
      	   					if (deviceInfo != null)
      	   					{
      	       					if (deviceInfo.map == true)
      	       					{
      	       						String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_PHONE_CONNECTED_CONNECTING_MSGS );
      	       						String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_PHONE_CONNECTED_CONNECTING_MSGS );
      	       						// relevant for ~30 seconds then the connection process already about to finish
      	       						mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
      	       									30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
      	       						mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_MAP_CHECK_TIMEOUT ),MSG_BLUETOOTH_MAP_CHECK_DELAY); 
      	       					}
      	   					}
      	       			}
    				}
    				else
    				{
    					if ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User) && (mContactsCompiled == 1))
    					{
    						String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_PHONE_CONNECTED_AND_READY );
    						String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_PHONE_CONNECTED_AND_READY );
    						mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.INTERUPT_VR,
      	      						TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
    					}
    				}
    				if (DevicesPanEnabled.contains(device))
     				{
						if ((btPanu.getConnectionState(device) ==
								BluetoothPanu.BLUETOOTH_PANU_DISCONNECTED))
						{
							addDeviceService(device, SRV_PAN);
							btPanu.connect(device, BluetoothPanu.NAP_ROLE) ; //Connect PANU
	     					mLogger.i("connect PANU " + device);
						}
     				}
         			RoadTrackBTMAP.clearSupportMessageInstance();
	       			if ( ((mPconf.GetIntParam(PConfParameters.ADVISOR_EnableBtSMSSupport, SMS_RECEIVE_AND_SEND_DICTATION_ENABLED) & SMS_FEATURE_ENABLED_MASK) == SMS_FEATURE_ENABLED_MASK)
	       					&& ((disabledServices & DISABLE_SRV_MAP) == 0) )
	       			{
	       				mBluetoothMapClient.connect(device); // connect MAP
	       				mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_PBAP_TIMEOUT ),MSG_BLUETOOTH_PBAP_DELAY);
	       				mLogger.i("connect MAP " + device);
	       			}
	       			else
	       			{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_PBAP_TIMEOUT ));
	       			}
       			}
				break;
			case MSG_MAP_CHECK_TIMEOUT:
				/*
				 * MAP should be connected. if MAP is not connected announce "Check if MAP is enabled"
				 */
				print("msg: MSG_MAP_CHECK_TIMEOUT");
				if ((mConnectedDevice > -1) && (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair))
  				{
 					int service = getDeviceService(DevicesAddr[mConnectedDevice]);
 					if ((service & SRV_MAP) != SRV_MAP)
 					{
 						String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_CHECK_SHORT_MSGS_ACCESS );
 						String promptForDisplay =  mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_CHECK_SHORT_MSGS_ACCESS );
 						// relevant for ~30 seconds then the connection process already passed the messages stage
 						mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
 								30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
 					}
  				}
				break;
			case MSG_PBAP_TIMEOUT:
					/* 
					 * Now handle connecting the Phone book.
					 */
					print("msg: MSG_PBAP_TIMEOUT");
  					if ( (mConnectedDevice > -1) && (!mDeleteAllDevices) && DisconnectAddr.isEmpty() )
  					{
  						mDevaddr = DevicesAddr[mConnectedDevice];
  	            		if (PBSyncEnabled == true) {
  	            			int PbapStatus;
  	            			PbapStatus = mBluetoothPhonebookClient.getConnectionStatus(mDevaddr);
  	       					if 	( PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_DISCONNECTED ){
  	       						BluetoothPhonebookConnected = false;
  	       						//ClearUserData();
  	       						mBluetoothPhonebookClient.clearPhonebook(BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_DATABASE_ID | BluetoothPhonebookClient.CSR_ANDROID_CALLLOGS_DATABASE_ID);
  	       						if (mBluetoothPhonebookClient.connect(mDevaddr,false,null,null))
  	       						{
  	       							addDeviceService(mDevaddr, SRV_PBAP);
  	       							mLogger.i("starting PBAP connection " + mDevaddr);
  	       							mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_PBAP_CHECK_TIMEOUT ),MSG_BLUETOOTH_PBAP_CHECK_TIMEOUT_DELAY);
  	        	    				if ( (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) || ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User) && (mContactsCompiled == 0)) )
  	        	    				{
  	        	    					// for RecentPairedVerbose_User) if at this point we still don't have a cache ready  beahave as pair level.
  	        	    					String promptForDisplay;
  	        	    					if (mAvrcpVersion >= MIN_AVRCP_VERSION)
  	        	    					{
  	        	    						String prompt = mStrings.getExpandedStringById( StringReader.Strings.TTS_PHONE_CONNECTED_SYNCING_CONTACTS_AND_MEDIA );
  	        	    						promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_PHONE_CONNECTED_SYNCING_CONTACTS_AND_MEDIA );
  	        	    						// the contacts sync announcement is relevant for ~30 from this moment
  	        	    						if (!mAvrcpMedia.mediaIsPlaying() || mAvrcpMedia.mediaIsPaused())
  	        	    						{
  	        	    							//music not playing can speak.
  	        	    							mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
  	        	      	      							30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
  	        	    						}
  	        	    					}
  	        	    					else
  	        	    					{
  	        	    						String prompt = mStrings.getExpandedStringById( StringReader.Strings.TTS_PHONE_CONNECTED_SYNCING_CONTACTS );
  	        	    						promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_PHONE_CONNECTED_SYNCING_CONTACTS );
  	        	    						// the contacts sync announcement is relevant for ~30 from this moment
 	        	    						if (!mAvrcpMedia.mediaIsPlaying() || mAvrcpMedia.mediaIsPaused())
  	        	    						{
 	        	    							mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
  	        	      	      							30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
  	        	    						}
  	        	    					}
  	        	    					if (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User)
  	        	    					{
  	        	    						mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_Pair;// at this point we still don't have a cache ready so continue as pair level.
  	        	    					}
  	        	    					if(!mHmiDisplayService.isDisplayingSelection()){
  	        	    						mHmiDisplayService.displayToScreenText(promptForDisplay, true, HmiIconId.VDP_HMI_ICON_NONE);
  	        	    					}
  	        	     				}
  	       						}
  	       						else
  	       						{
  	       							mLogger.i("failed to start PBAP connection" + mDevaddr);
  	       							NotifyClients(CONTACTS_STATUS_FINAL_NOT_AVAIALABLE, mDevaddr,false);
  	       							if ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) || (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User))
  	       							{
  	       								String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
  	       								String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
  	       								// this is relevant for until the tts can be heard as user need to know that contacts sync failed.
  	       								mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
  	       											TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
  	       							}
  	       						}
  	       					}
  	            		}
  	            		else
  	            		{
  	          				NotifyClients(CONTACTS_STATUS_FINAL_NOT_AVAIALABLE, mDevaddr,false);
  							if ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) || (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User))
     						{
  								if (mAvrcpVersion >= MIN_AVRCP_VERSION)
  								{
  									String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_SYNCHRONIZATION_MEDIA );
  									String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_SYNCHRONIZATION_MEDIA );
  									// the media sync announcement is relevant for ~30 from this moment 
  									mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
      	      	      						30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
  								}
     						}
  	            		}

  					}
					break;
			case MSG_PBAP_CHECK_TIMEOUT:
				/*
				 * At this point if it is first connect after pair and PBAP didn't connect, play TTS "check access to your contacts"
				 */
				print("msg: MSG_PBAP_CHECK_TIMEOUT");
				if (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair)
  				{
  					String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_CHECK_CONTACTS_ACCESS );
  					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_CHECK_CONTACTS_ACCESS );
 					// the "check access to contacts" is relevant for ~30 from this moment 
 					mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
 							30, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
  				}
				break;
			case MSG_AVRCP_CONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_AVRCP_CONNECTED " + mDevaddr);
				addDeviceService(mDevaddr, SRV_AVRCP);
				mHandler.removeMessages(MSG_AVRCP_TIMEOUT);
  				boolean disconnectAvrcp = false;
				disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
   				if ( ((disabledServices & DISABLE_SRV_A2DP_RCV) == DISABLE_SRV_A2DP_RCV) ||
   						((disabledServices & DISABLE_SRV_AVRCP) == DISABLE_SRV_AVRCP) || mA2dpBTAddress.isEmpty() )
   				{
   					disconnectAvrcp = true;
   					mLogger.i("Disabled services: " + disabledServices);
   				}
   				else
   				{
   					if (mConnectedDevice > -1) {
   						if (!mAvrcpBTAddress.equals(DevicesAddr[mConnectedDevice]))
   						{
   							//Disconnect AVRCP
   							disconnectAvrcp = true;
   							mLogger.i("HFP and AVRCP on different devices. AVRCP: " + mAvrcpBTAddress + ", HFP: " + DevicesAddr[mConnectedDevice]); 
   						}
   					}
    			}
   				if (disconnectAvrcp == true)
   				{
					//Disconnect AVRCP
					mAvrcpMedia.disconnectAvrcp();
   					mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_AVRCP_TIMEOUT ),MSG_BLUETOOTH_AVRCP_DELAY);
					mLogger.i("Disconnecting AVRCP: " + mAvrcpBTAddress); 
   				}
   				else
   				{
   					RTMediaHandler.getInstance().muteMusicForConnectMedia(false);//unmute music
   					if (mHandler.hasMessages(MSG_MAP_TIMEOUT))
   					{
   						//A2DP+AVRCP+HFP pressumably are connected and MAP has not been handled yet. Continue without waiting for DELAYED MAP timeout
   						mHandler.removeMessages(MSG_MAP_TIMEOUT);
  						DeviceId = getPairedDeviceId(mDevaddr);
   						if ( (DeviceId > -1 ) && (DevicesType[DeviceId] == TYPE_MOBILE) )
   						{
   							mHandler.sendMessage( mHandler.obtainMessage( MSG_MAP_TIMEOUT ));
   						}
   						else
   						{
   							//not phone
   							NotifyClients(CONTACTS_STATUS_FINAL_NOT_AVAIALABLE, mDevaddr,false);
   						}
   					}
   					else
   					{
   						DeviceId = getPairedDeviceId(mDevaddr);
  						if ( (DeviceId > -1 ) && (DevicesType[DeviceId] != TYPE_MOBILE) )
   						{
   							//not phone;
  							NotifyClients(CONTACTS_STATUS_FINAL_NOT_AVAIALABLE, mDevaddr,false);
   						}
   					}
   				}
				break;
			case MSG_AVRCP_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_AVRCP_DISCONNECTED " + mDevaddr);
				int AVRCPver = msg.arg1;
				//stop blinking LED if device is still connected but AVRCP stopped prematurelly, terminating the AVRCP browsing.
				if ((mContactsCompiled > 0) && (mConnectedDevice > -1) && (AVRCPver >= MIN_AVRCP_VERSION) && !mAvrcpSyncComplete) {
					IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);
				}
				services = removeDeviceService(mDevaddr, SRV_AVRCP);
				if (services == SRV_NONE)
				{
					if (mDeleteAllDevices || (DisconnectAddr.equals(mDevaddr)))
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
                	}
				}
				break;
			case MSG_AVRCP_TIMEOUT:
				print("msg: MSG_AVRCP_TIMEOUT");
       			if ( (!mA2dpBTAddress.isEmpty()) && (!mDeleteAllDevices) && DisconnectAddr.isEmpty() )
       			{
       				RTMediaHandler.getInstance().muteMusicForConnectMedia(false);//unmute music independetly if AVRCP concets or fails.
       				// No need to check HFP. For case device doesn't support HFP and also for case A2DP+AVRCP connect before HFP.
					disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
					mLogger.i("Disabled services: " + disabledServices);
       				if ( (mAvrcpBTAddress == "") && (mA2dpBTAddress != "") && ((disabledServices & DISABLE_SRV_A2DP_RCV) == 0) &&
       						((disabledServices & DISABLE_SRV_AVRCP) == 0) )
       				{
       					mAvrcpBTAddress = mA2dpBTAddress;
       					addDeviceService(mA2dpBTAddress, SRV_AVRCP);
       					mAvrcpMedia.connectAvrcp(mA2dpBTAddress);
       					mLogger.i("connect AVRCP " + mA2dpBTAddress);
       				}
  				}
       			
				break;
			case MSG_HFP_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_HFP_DISCONNECTED " + mDevaddr);
				if (removeDeviceService(mDevaddr, SRV_HFP) == SRV_NONE)
				{
					if (mDeleteAllDevices)
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
                	}
				}
				mHandler.removeMessages(MSG_A2DP_TIMEOUT);
				mHandler.removeMessages(MSG_AVRCP_TIMEOUT);
				mHandler.removeMessages(MSG_MAP_TIMEOUT);
				mHandler.removeMessages(MSG_PBAP_TIMEOUT);
           		if (ActivityState == BLUETOOTH_ACTIVITY_WAIT_CONNECT)
        		{
           			// on going connect process
           			ActivityState = BLUETOOTH_ACTIVITY_IDLE;
           			if (mDeleteAllDevices)
           			{
            			return;
           			}
           			if ( (mTryConnectDevice >= 0) && mDevaddr.equals(DevicesTryConnectedAddr.get(mTryConnectDevice)) ) {
           				// failed to connect retry or try new device
           				mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
           				if (mTryConnectDevice < DevicesTryConnectedAddr.size())
           				{
           					if (connectRetries  > 0)
           						connectRetries--;
           					else {
           						connectRetries = BLUETOOTH_CONNECT_RETRIES;
           						mTryConnectDevice++;
           					}
           					disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
           					for (; mTryConnectDevice < DevicesTryConnectedAddr.size(); mTryConnectDevice++) {
           						DeviceId = getPairedDeviceId(DevicesTryConnectedAddr.get(mTryConnectDevice));
           						if ( (DeviceId > -1 ) && (DevicesType[DeviceId] == TYPE_MOBILE) ) {
           							if (((disabledServices & DISABLE_SRV_HFP) == 0))
           							{
           								mLogger.i( "Start HFP connect: " + DevicesAddr[DeviceId] + "," + DevicesName[DeviceId]);
           								addDeviceService(DevicesAddr[DeviceId], SRV_HFP);
           								btHFP.connect(DevicesAddr[DeviceId]);
           								ActivityState = BLUETOOTH_ACTIVITY_WAIT_CONNECT;
           								break;
           							}
           						}
           					}
           					if (mTryConnectDevice >= DevicesTryConnectedAddr.size())
           					{
           						if (mConnectVerbose)
           						{
           							mConnectVerbose = false;
           							String prompt = mStrings.getExpandedStringById( StringReader.Strings.PROMPTID_BT_FAILED_TO_CONNECT );
           							String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.PROMPTID_BT_FAILED_TO_CONNECT );
           							// This tts should be heard anyway to notify the user about the failure 
  									mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
      	      	      						TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
           						}
           						sendBluetoothConnectionFail();
           					}
           				}
           			}
         		}
           		else
           		{
           			// not in on going auto connect process
           			DisconnectAddr = "";
        			if (ActivityState == BLUETOOTH_ACTIVITY_IDLE) {
                      	if ( mConnectedDevice >= 0 )  {     
                      		if (mDevaddr.equals(DevicesAddr[mConnectedDevice]) ) {
								GmlanNotificationInterface.Instance().handlePhoneDisconnection( DevicesAddr[mConnectedDevice] );
                     			mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
//                             	if (BluetoothPhonebookConnected == true) {
                      				int PbapStatus;
                      				PbapStatus = mBluetoothPhonebookClient.getConnectionStatus(DevicesAddr[mConnectedDevice]);
                       				if 	( (PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_CONNECTED) ||
                       						(PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_COMMUNICATING) ||
                       						(PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_CONNECTING)){
                        				mLogger.i( "Disconnect PBAP: " + DevicesAddr[mConnectedDevice]);
                       					mBluetoothPhonebookClient.disconnect(DevicesAddr[mConnectedDevice]);
                       					DisconnectAddr = mDevaddr;
                       				}
//                               	}
                     			if (mSppAddress.equals(mDevaddr))
                     			{
            						disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
            	      				if ( ((disabledServices & DISABLE_SRV_SPP) == 0) && ((disabledServices & DISABLE_SRV_HFP) == DISABLE_SRV_HFP) &&
            	      						((disabledServices & DISABLE_SRV_AVRCP) == DISABLE_SRV_AVRCP) && !mDeleteAllDevices && DisconnectAddr.isEmpty() )
            	      				{
            	      					//SPP only don't remove SPP
            	      					mLogger.i("ignore Disconnect SPP " + mDevaddr + ", disabledServices:" + disabledServices);
            	      				}
            	      				else
            	      				{
            	      					mLogger.i("Disconnect SPP " + mDevaddr);
            	      					DisconnectAddr = mDevaddr;
            	      					disconnectSpp();
            	      				}
                     			}
                        		if (mAvrcpMedia.getConnectState() != RTMediaAvrcp.ConnectState.DISCONNECTED)
                        		{
                        			//Disconnect AVRCP
                        			if (mAvrcpBTAddress.equals(mDevaddr))
                        			{
                        				DisconnectAddr = mDevaddr;
                        				mAvrcpMedia.disconnectAvrcp();
                        				mLogger.i("Disconnect Active AVRCP " + mAvrcpBTAddress);
                        			}
                        		}
                             	if ((mA2dpBTAddress != "") && mA2dpBTAddress.equals(mDevaddr))
//                             	if (btA2dp.getCurrentConnectionStatus(mDevaddr) != BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_DISCONNECTED)
                             	{
                             		//Disconnect A2DP
                             		DisconnectAddr = mDevaddr;
                             		btA2dp.disconnect(mA2dpBTAddress);
                             		mLogger.i("Disconnect Active A2DP " + mA2dpBTAddress);
                             	}
                               	if (mBluetoothMapClient.getConnectionStatus(mDevaddr) != BluetoothMapClient.BLUETOOTH_MAPC_DISCONNECTED) {
                               		// disconnect MAP
                               		DisconnectAddr = mDevaddr;
                               		mLogger.i("Disconnect MAP " + mDevaddr);
                               		mBluetoothMapClient.disconnect(RoadTrackBTMAP.InstanceId);   
                              	}
                             	if ( (btPanu.getConnectionState(mDevaddr) == BluetoothPanu.BLUETOOTH_PANU_CONNECTED) ||
                             			(btPanu.getConnectionState(mDevaddr) == BluetoothPanu.BLUETOOTH_PANU_CONNECTING) ){
                               		// disconnect PAN
                             		DisconnectAddr = mDevaddr;
                               		mLogger.i("Disconnect PANu " + mDevaddr);
                               		btPanu.disconnect(mDevaddr);   
                              	}
                                mConnectedDevice = -1;
                        		if (mDeleteAllDevices)
                         		{
                        			break;
                         		}
                         		else
                         		{
                         			if (DisconnectAddr.isEmpty())
                         			{
                            			connectRetries = BLUETOOTH_CONNECT_RETRIES;
                             			mTryConnectDevice++;
                             			disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
                             			if (((disabledServices & DISABLE_SRV_HFP) == 0))
                             			{
                             				for (; mTryConnectDevice < DevicesTryConnectedAddr.size(); mTryConnectDevice++) {
                             					DeviceId = getPairedDeviceId(DevicesTryConnectedAddr.get(mTryConnectDevice));
                             					if ( (DeviceId > -1 ) && (DevicesType[DeviceId] == TYPE_MOBILE) ) {
                             						mLogger.i( "Start HFP connect: " + DevicesAddr[DeviceId] + "," + DevicesName[DeviceId]);
                             						addDeviceService(DevicesAddr[DeviceId], SRV_HFP);
                             						btHFP.connect(DevicesAddr[DeviceId]);
                             						ActivityState = BLUETOOTH_ACTIVITY_WAIT_CONNECT;
                             						break;
                             					}
                             				}
                             			}
                             			else
                             			{
                             				if (mSppAddress.isEmpty() && ((disabledServices & DISABLE_SRV_SPP) == 0))
                             				{
                             					//SPP only and nothing connected.
                             					//always try to connect to the last device that just disconnected.
                             					if (isDevicePaired(mDevaddr))
                             					{
                             						mLogger.i( "iAP SPP reconnect: " + mDevaddr);
                            						RoadTrackSPP.getInstance().connectAsClientToSPP(mDevaddr);
                             					}
                             				}
                             			}
                                	}
                         			else
                         			{
                           				if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_NONE) == true)
                        					mLogger.i( "set SCAN_MODE_NONE");
                         			}
                         		}
                       		}  
        				}
         			}
           		}
				break;
			case MSG_PBAP_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_PBAP_DISCONNECTED " + mDevaddr);
				services = removeDeviceService(mDevaddr, SRV_PBAP);
				if (services == SRV_NONE)
				{
					if (mDeleteAllDevices || (DisconnectAddr.equals(mDevaddr)))
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
                	}
				}
				if ((mConnectedDevice > -1) && (mDevaddr.equals(DevicesAddr[mConnectedDevice])))
				{
					NotifyClients(CONTACTS_STATUS_FINAL_NOT_AVAIALABLE, mDevaddr,false);
					if ( ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) || (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User))
							&& (ignitionState == IgnigtionState.IGNITION_ON))
   					{
						if (!mDeletePairingList && !DisconnectAddr.equals(mDevaddr) && mContactsCompiled == 0)
						{
							String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
							String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
							// This tts should be heard anyway to notify the user about the failure 
							mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
									TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
						}
						if (!DisconnectAddr.equals(mDevaddr))
						{
							mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
							mConnectVerbose = false;
							mContactsCompiled = 1;
						}
   					}
				}
				if ( (BluetoothPhonebookConnected == true)  ) {
					print("DisconnectAddr: " + DisconnectAddr + " mDevaddr: " + mDevaddr);
					//ClearUserData();
					mBluetoothPhonebookClient.clearPhonebook(BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_DATABASE_ID | BluetoothPhonebookClient.CSR_ANDROID_CALLLOGS_DATABASE_ID);
   					BluetoothPhonebookConnected = false;
       				IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);
       				BluetoothPhonebookSynchronizing = false;
				}
				break;
			case MSG_PBAP_CONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_PBAP_CONNECTED " + mDevaddr);
				addDeviceService(mDevaddr, SRV_PBAP);
               	if ( (mConnectedDevice > -1) &&  (mDevaddr.equalsIgnoreCase(DevicesAddr[mConnectedDevice])) ) {
               		if (BluetoothPhonebookConnected == false) {
               			mHandler.removeMessages (MSG_PBAP_CHECK_TIMEOUT);
               			BluetoothPhonebookConnected = true;
							// request only the mandatory fields (we only need them for our use)/
							mBluetoothPhonebookClient.setOption(BluetoothPhonebookClient.FILTER_ENABLE_VERSION |BluetoothPhonebookClient.FILTER_ENABLE_N | BluetoothPhonebookClient.FILTER_ENABLE_TEL,
																BluetoothPhonebookClient.VCARD_21, DevicesAddr[mConnectedDevice]);
           				if (mBluetoothPhonebookClient.pullPhonebookReq(	DevicesAddr[mConnectedDevice], 
           																BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_PHONE_PB |
           																BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_PHONE_ICH |
									    								BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_PHONE_OCH |
									    								BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_PHONE_MCH, 
           																0, PHONEBOOK_MAX_CONTACT_NUMBER ) ) {
             				mLogger.i("Download contacts started " + DevicesAddr[mConnectedDevice]);
             				IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, true);
             				BluetoothPhonebookSynchronizing = true;
 
                 		}
               			else
               			{
              				mLogger.w("Download contacts failed" + DevicesAddr[mConnectedDevice]);
              				if ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) || (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User))
              				{
       							String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
       							String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
       							// This tts should be heard anyway to notify the user about the failure 
       							mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
       									TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );

								mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
								mConnectVerbose = false;
       							mContactsCompiled = 1;
              				}
               			}
               		}
               	}
               	else
               	{
                     mBluetoothPhonebookClient.clearPhonebook(BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_DATABASE_ID | BluetoothPhonebookClient.CSR_ANDROID_CALLLOGS_DATABASE_ID);
                     mBluetoothPhonebookClient.clearSearchResult(mDevaddr);
                     mLogger.i( "disconnect PBAP: " + mDevaddr);
                     mBluetoothPhonebookClient.disconnect(mDevaddr);
                     DisconnectAddr = mDevaddr;
                     BluetoothPhonebookConnected = true;
                }
				break;
			case MSG_PBAP_PULL_PB_CFM:
				mDevaddr = (String) msg.obj;
				result = msg.arg1;
				print("msg: MSG_PBAP_PULL_PB_CFM: " + mDevaddr + " result:" + result);
        		if (mConnectedDevice > -1) {
        			if  (DevicesAddr[mConnectedDevice].equalsIgnoreCase(mDevaddr)) {
        				mLogger.i("CSR_ANDROID_PHONEBOOK_PULL_PB_CFM " + mDevaddr + " result:" + result);
        				BluetoothPhonebookSynchronizing = false;
        				boolean change = mBtContactsCache.createContactsCache(mDevaddr);
        				if ( change && ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Auto) || (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User)) ) {

        					// TODO: may need to wait for media sync as well.
        					String prompt = mStrings.getExpandedStringById( StringReader.Strings.TTS_CONTACT_LIST_UPDATED );
     						String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_CONTACT_LIST_UPDATED );
     	  					if (mBtContactsCache.countContactsCacheNames() >= PHONEBOOK_MANY_CONTACTS)
     	  					{
     	  						prompt += " " + mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_MANY_CONTACTS_ACCESS_SLOW );
     	  						promptForDisplay += " " + mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_MANY_CONTACTS_ACCESS_SLOW );
     	  					}
     						// This tts is relevant for about ~30 seconds from this moment afterwards there already will be heard "phone synchronized..."
     						mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
     								    TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );

     						mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
							mConnectVerbose = false;
        				}
         				//enable giving cache contacts to clients
         				mBtContactsCache.setNotifyContactsCacheAddr(mDevaddr);
         				NotifyClients(CONTACTS_STATUS_FINAL_AVAIALABLE, mDevaddr, change);
//                		mBtContactsCache.setNotifyContactsCacheAddr(""); //to disable using the cache.
         			}
        		}
				break;
			case MSG_PBAP_ERROR_MSG:
				mDevaddr = (String) msg.obj;
				result = msg.arg1;
				print("msg: MSG_PBAP_ERROR_MSG: " + mDevaddr + " result:" + result);
        		if (mConnectedDevice > -1) {
        			if  (DevicesAddr[mConnectedDevice].equalsIgnoreCase(mDevaddr)) {
        				mLogger.w("CSR_ANDROID_PHONEBOOK_ERROR_MSG " + mDevaddr + " result:" + result);
        				IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);
        				BluetoothPhonebookSynchronizing = false;
        				NotifyClients(CONTACTS_STATUS_FINAL_NOT_AVAIALABLE, mDevaddr,false);
    					if ( ((mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) || (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User)) && (ignitionState == IgnigtionState.IGNITION_ON) )
       					{
       						String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
       						String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_FAILED_CONTACTS_SYNCHRONIZATION );
       						// This tts should be heard anyway to notify the user about the failure 
       						mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
       								TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
       						mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
       						mConnectVerbose = false;
        					mContactsCompiled = 1;
       					}
        			}
        		}
				break;
			case MSG_CONTACTS_COMPILED:
				/*
				 * Contact table has beeen setup in VR and is ready to use.
				 */
				print("msg: MSG_CONTACTS_COMPILED");
				mContactsCompiled++;
				if ( mMapTimeout )
				{
					if ((mAvrcpVersion < MIN_AVRCP_VERSION) || ((mAvrcpVersion >= MIN_AVRCP_VERSION) && mAvrcpSyncComplete))
					{
						IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);						
					}
					if ( mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair)
					{
						if ( ((mAvrcpVersion < MIN_AVRCP_VERSION) || ((mAvrcpVersion >= MIN_AVRCP_VERSION) && mAvrcpSyncComplete))
								&&
								(mPconf.GetIntParam(PConfParameters.PND_InDashEnabled ,0) == 0) ) //!InDash 
						{
							String prompt = mStrings.getExpandedStringById( StringReader.Strings.TTS_PHONE_SYNCHRONIZIED_AND_READY );
							String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_PHONE_SYNCHRONIZIED_AND_READY );
							if (mConnectedDevice > -1)
		  					{
		  						if (mBtContactsCache.countContactsCacheNames() >= PHONEBOOK_MANY_CONTACTS)
		  						{
		  							prompt +=  " " + mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_MANY_CONTACTS_ACCESS_SLOW );
		  							promptForDisplay += " " + 
										mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_MANY_CONTACTS_ACCESS_SLOW );
		  						}
		  					}
							// This tts should be heard anyway to notify the user about the success
							if (!mAvrcpMedia.mediaIsPlaying() || mAvrcpMedia.mediaIsPaused())
							{
								//music not playing can speak.
								mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
										TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
							}
							mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
							mConnectVerbose = false;
						}
					}
					else
					{
						if ( (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_User) && (mContactsCompiled == 1) )
						{
							String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_PHONE_CONNECTED_AND_READY );
							String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_PHONE_CONNECTED_AND_READY );
							// This tts should be heard anyway to notify the user about the success 
							mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.INTERUPT_VR,
									TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
						}
					}
				}
				break;
			case MSG_PBAP_DISCONNECT_IND:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_PBAP_DISCONNECT_IND: " + mDevaddr);
               	if (BluetoothPhonebookConnected == true) {
                 	mLogger.i( "CSR_ANDROID_PHONEBOOK_DISCONNECT_IND: " + DisconnectAddr);
                 	IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);
                 	BluetoothPhonebookSynchronizing = false;
               		mBluetoothPhonebookClient.clearPhonebook(BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_DATABASE_ID | BluetoothPhonebookClient.CSR_ANDROID_CALLLOGS_DATABASE_ID);
               		mBluetoothPhonebookClient.clearSearchResult(mDevaddr);
                }
				break;
			case MSG_AVRCP_SYNC_COMPLETED:
				print("msg: MSG_AVRCP_SYNC_COMPLETED");
				mAvrcpSyncComplete =true;
				if ((mAvrcpVersion >= MIN_AVRCP_VERSION) && (mContactsCompiled > 0))
				{
					IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_BT_BLINKING_BLUE_SLOW_INFINITE, false);
					if ( (mConnectVerboseLevel == ERecentPairedVerbose.RecentPairedVerbose_Pair) &&
							(mPconf.GetIntParam(PConfParameters.PND_InDashEnabled ,0) == 0) ) //!InDash)
					{
						String prompt = mStrings.getExpandedStringById( StringReader.Strings.TTS_PHONE_SYNCHRONIZIED_AND_READY );
						String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_PHONE_SYNCHRONIZIED_AND_READY );
						if (mConnectedDevice > -1)
						{
							if (mBtContactsCache.countContactsCacheNames() >= PHONEBOOK_MANY_CONTACTS)
							{
								prompt += " " + mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_MANY_CONTACTS_ACCESS_SLOW );
								promptForDisplay += " " + mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_MANY_CONTACTS_ACCESS_SLOW );
							}
						}
						// This tts should be heard anyway to notify the user about the sync process finish
						if (!mAvrcpMedia.mediaIsPlaying() || mAvrcpMedia.mediaIsPaused())
						{
							//music not playing can speak.
							mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
								TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
						}
						mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;
						mConnectVerbose = false;
					}
				}
				break;
			case MSG_SPP_CONNECTED:
				print("msg: MSG_SPP_CONNECTED: " + mSppAddress);
				if (!mSppAddress.isEmpty())
				{
					addDeviceService(mSppAddress, SRV_SPP);
					if (mConnectedDevice > -1)
					{
						if (!DevicesAddr[mConnectedDevice].equals(mSppAddress))
						{
							mLogger.i("Disconnect SPP " + mSppAddress);
							disconnectSpp();
						}
					}
 				}
				break;
			case MSG_WAIT_SPP_BEFORE_DISCONNECT:
				print("msg: MSG_WAIT_SPP_BEFORE_DISCONNECT: " + mSppAddress);
				if (!mSppAddress.isEmpty())
				{
					mConnectionDesc.Disconnect();
				}
				break;
			case MSG_SPP_DISCONNECTED:
				mDevaddr = (String) msg.obj;
				print("msg: MSG_SPP_DISCONNECTED: " + mDevaddr);
				mHandler.removeMessages(MSG_WAIT_SPP_BEFORE_DISCONNECT);
				services = removeDeviceService(mDevaddr, SRV_SPP);
				if (services == SRV_NONE)
				{
					if (mDeleteAllDevices || (DisconnectAddr.equals(mDevaddr)))
					{
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED, mDevaddr));
                	}
				}
				mSppAddress = "";
				break;
			case MSG_BT_TOGGLE_CONNECT:
				print("msg: MSG_BT_TOGGLE_CONNECT");
				if ( (mConnectedDevice == -1) && (ActivityState == BLUETOOTH_ACTIVITY_IDLE) && (ignitionState == IgnigtionState.IGNITION_ON) )
				{
					DevicesTryConnectedAddr.clear();
					DevicesTryConnectedAddr.addAll(DevicesRecentConnectedAddr);
					tryHFPConnect(false);
				}
				break;
			case MSG_BT_TRY_CONNECT_VERBOSE:
				/*
				 * User pressed Chevy Medium to connect BT phone
				 */
				print("msg: MSG_BT_TRY_CONNECT_VERBOSE");
				if ( (mConnectedDevice != -1) || (ActivityState != BLUETOOTH_ACTIVITY_IDLE) || (ignitionState != IgnigtionState.IGNITION_ON) || mDeleteAllDevices)
				{
					break;
				}
				if (mPairedDevices <= 0)
				{
					String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPPHONE_PROMPTID_NO_PAIRING_LIST_CALL );
					String promptForDisp = mStrings.getStringByIdForDisplay( StringReader.Strings.APPPHONE_PROMPTID_NO_PAIRING_LIST_CALL );
					// This tts is relevant until it can be heard but then it should be the first in the queue.
					mTts.speak(prompt, TextToSpeech.QUEUE_FLUSH, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
							TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisp, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
					break;
				}
				else
				{
					String prompt = mStrings.getExpandedStringById( StringReader.Strings.APPSETTINGS_PROMPTID_BT_TRYING_CONNECT );
					String promptForDisp = mStrings.getStringByIdForDisplay( StringReader.Strings.APPSETTINGS_PROMPTID_BT_TRYING_CONNECT );
					// This tts is relevant for about ~30, after that the result messages will be already waiting anyway.
					mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
	      						30, promptForDisp, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
					
				}
				//continue to following case
			case MSG_BT_TOGGLE_CONNECT_VERBOSE:
				/*
				 * User said "connect phone"
				 */
				print("msg: MSG_BT_TOGGLE_CONNECT_VERBOSE");
				mDevaddr = (String) msg.obj;
				if ( (ActivityState == BLUETOOTH_ACTIVITY_IDLE) && (ignitionState == IgnigtionState.IGNITION_ON) && !mDeleteAllDevices)
				{
					if ( (mDevaddr == null) || (mDevaddr.isEmpty()) )
					{
						if (mConnectedDevice == -1)
						{
							DevicesTryConnectedAddr.clear();
							DevicesTryConnectedAddr.addAll(DevicesRecentConnectedAddr);
							tryHFPConnect(true);
						}
					}
					else
					{
						//Connect specific phone
						mLogger.i(" Connect specific phone: " + mDevaddr);
						if (mConnectedDevice > -1)
						{
							//HFP is connected
							if (!mDevaddr.equals(DevicesAddr[mConnectedDevice]))
							{
								//differnet device. First need disconnect
								DevicesTryConnectedAddr.clear();
								DevicesTryConnectedAddr.add(mDevaddr);
								mTryConnectDevice = -1;
								mConnectVerbose = true;
								int CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[mConnectedDevice]);
								if (( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) && ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTING))
								{
									mLogger.i( "HFP Disconnect: " +  DevicesAddr[mConnectedDevice]);
									btHFP.disconnect(DevicesAddr[mConnectedDevice]);
									DisconnectAddr = DevicesAddr[mConnectedDevice];
								}
							}
						}
						else
						{
							DevicesTryConnectedAddr.clear();
							DevicesTryConnectedAddr.add(mDevaddr);
							tryHFPConnect(true);
						}
					}
				}
				break;
			case MSG_BT_USER_DISCONNECT_NO_RECONNECT:
				/*
				 * User said "disconnect" and no reconnect
				 */
				print("msg: MSG_BT_USER_DISCONNECT_NO_RECONNECT");
				if ( (ActivityState == BLUETOOTH_ACTIVITY_IDLE) && (ignitionState == IgnigtionState.IGNITION_ON) && !mDeleteAllDevices)
				{
					if (mConnectedDevice > -1)
					{
						//HFP is connected, disconnect it and disable reconnect
						DevicesTryConnectedAddr.clear();
						mTryConnectDevice = DevicesTryConnectedAddr.size();
						int CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[mConnectedDevice]);
						if (( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) && ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTING))
						{
							mLogger.i( "HFP Disconnect without reconnect: " +  DevicesAddr[mConnectedDevice]);
							btHFP.disconnect(DevicesAddr[mConnectedDevice]);
							DisconnectAddr = DevicesAddr[mConnectedDevice];
						}
					}
				}
				break;
			case MSG_DATA_PLAN_UPDATE:
				print("msg: MSG_DATA_PLAN_UPDATE");
				mDevaddr = (String) msg.obj;
				int useDataPlan = msg.arg1;
				if (useDataPlan == 1)
					updateDataplanUsage(mDevaddr, true);
				else
					updateDataplanUsage(mDevaddr, false);
				break;
			case MSG_MAP_UUID:
				/*
				 * MAP UUID fetched from device. MAP supported
				 */
				print("msg: MSG_MAP_UUID");
				mDevaddr = (String) msg.obj;
				BTRecentPairedDB.BTRecentPairedDeviceInfo deviceInfo = mBtRecentPaired.getRecentPairedDeviceInfo(mDevaddr);
				if (deviceInfo != null)
				{
					mBtRecentPaired.saveRecentPairedToDB(mDevaddr, deviceInfo.connect, true);
				}
				break;
			case MSG_DELETE_PAIRING_LIST:
				print("msg: MSG_DELETE_PAIRING_LIST");
				if (!mDeletePairingList)
				{
					if (mPowerDownBT || (!mBTAdapter.isEnabled()))
					{
						mLogger.w( "Can't delete pairing while BT power down");
	     				String ttsPairCleared = mStrings.getExpandedStringById( StringReader.Strings.TTS_CLEAR_PAIR_LIST_FAILED );
	     				if ( ( ttsPairCleared != null ) && !ttsPairCleared.isEmpty() )
	     				{
	     					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_CLEAR_PAIR_LIST_FAILED );
	     					// This tts is relevant until it can be heard to notify about the failure.
	     					mTts.speak(ttsPairCleared, TextToSpeech.QUEUE_FLUSH, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
	     							TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
	     				}
	     				break;
					}
					if (mPairedDevices > 0)
					{
						mDeletePairingList = true;
						mDeleteAllDevices = true;
           				if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_NONE) == true)
        					mLogger.i( "set SCAN_MODE_NONE");
						mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_DELETE_PAIRING_LIST_TIMEOUT ),MSG_DELETE_PAIRING_LIST_DELAY);
						if (mConnectedDevice > -1)
						{
							int CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[mConnectedDevice]);
							if (( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) && ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTING))
							{
									btHFP.disconnect(DevicesAddr[mConnectedDevice]);
									mLogger.i( "HFP Disconnect");
							}
						}
						else
						{
							if (!mDevicesConnected.isEmpty())
							{
								DeviceServices deleteDevice = new DeviceServices();
								deleteDevice = mDevicesConnected.get(0);
								disconnectServices(deleteDevice.mDevaddr);
							}
							else
							{
								unpairOneDevice();
							}
						}
					}
					else
					{
						//pairing list already empty
						mBtContactsCache.deleteContactsCacheDB();
						mBtRecentPaired.deleteRecentPairedDB();
	     				String ttsPairCleared = mStrings.getExpandedStringById( StringReader.Strings.TTS_CLEAR_PAIR_LIST_SUCCEEDED );
	     				if ( ( ttsPairCleared != null ) && !ttsPairCleared.isEmpty() )
	     				{
	     					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_CLEAR_PAIR_LIST_SUCCEEDED );
	     					// This tts is relevant until it can be heard to notify about the process success.
	     					mTts.speak(ttsPairCleared, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT, 
	     							TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
	     				}
					}
				}
				break;
			case MSG_POWER_DOWN_BT:
				print("msg: MSG_POWER_DOWN_BT");
				if (!mPowerDownBT)
				{
					mPowerDownBT = true;
					if (!mDeletePairingList)
					{
						mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_POWERDOWN_TIMEOUT ),MSG_POWERDOWN_TIMEOUT_DELAY);
           				if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_NONE) == true)
        					mLogger.i( "set SCAN_MODE_NONE");
						mDeleteAllDevices = true;
						if (mConnectedDevice > -1)
						{
							int CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[mConnectedDevice]);
							if (( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) && ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTING))
							{
								btHFP.disconnect(DevicesAddr[mConnectedDevice]);
								mLogger.i( "HFP Disconnect");
							}
						}
						else
						{
							if (!mDevicesConnected.isEmpty())
							{
								DeviceServices deleteDevice = new DeviceServices();
								deleteDevice = mDevicesConnected.get(0);
								disconnectServices(deleteDevice.mDevaddr);
							}
							else
							{
								mPowerDownBT = false;
								DisconnectAddr = "";
								mDeleteAllDevices = false;
								mHandler.removeMessages (MSG_POWERDOWN_TIMEOUT);
								if ((mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) || (mBTAdapter.getState() == BluetoothAdapter.STATE_ON))
								{
									if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON)
									{
										mLogger.i( "Adapter turning on! Delay power down.");
										if (!mHandler.hasMessages(MSG_POWER_DOWN_BT))
											mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_POWER_DOWN_BT ), 1000);
										break;
									}
									if (mBTAdapter.disable() == true)
										mLogger.i( "power down Bluetooth done");
									else
									{
										mLogger.w( "power down Bluetooth failed!");
										if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE) == true)
											mLogger.i( "set SCAN_MODE_CONNECTABLE");
									}
								}
							}
						}
					}
					else
					{
						mPowerDownBT = false;
     					if (!mHandler.hasMessages(MSG_POWER_DOWN_BT))
     						mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_POWER_DOWN_BT ), 1000);
					}
				}
				break;
			case MSG_BT_DEVICE_DISCONNECTED:
				print("msg: MSG_BT_DEVICE_DISCONNECTED");
				mDevaddr = (String) msg.obj;
				print("mDevaddr:" + mDevaddr + " DisconnectAddr:" + DisconnectAddr);
				print("ActivityState:" + ActivityState + " mTryConnectDevice:" + mTryConnectDevice);
				if (mDeletePairingList == true)
				{
					if (mPairedDevices > 0) {
						if (mDevicesConnected.isEmpty())
						{
				       		BTDevicesArray[0].removeBond();
						}
						else
						{
							DeviceServices removeDevice = new DeviceServices();
							removeDevice = mDevicesConnected.get(0);
		   					disconnectServices(removeDevice.mDevaddr);
						}
					}
					else
					{
						mDeletePairingList = false;
						mDeleteAllDevices = false;
						DisconnectAddr = "";
						mBtRecentPaired.deleteRecentPairedDB();
						DevicesRecentConnectedAddr.clear();
						DevicesTryConnectedAddr.clear();
						PhoneHandler.setIsPromptPairing();
        				if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE) == true)
        					mLogger.i( "set SCAN_MODE_CONNECTABLE");
						mHandler.removeMessages (MSG_DELETE_PAIRING_LIST_TIMEOUT);
	     				String ttsPairCleared = mStrings.getExpandedStringById( StringReader.Strings.TTS_CLEAR_PAIR_LIST_SUCCEEDED );
	     				if ( ( ttsPairCleared != null ) && !ttsPairCleared.isEmpty() )
	     				{
	     					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_CLEAR_PAIR_LIST_SUCCEEDED );
	     					// This tts is relevant until it can be heard to notify about the process success.
	     					mTts.speak(ttsPairCleared, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
	     							TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
	     				}
						ActivityState = BLUETOOTH_ACTIVITY_IDLE;
					}
				}
				else if (mPowerDownBT)
				{
					print("mPowerDownBT:"+mPowerDownBT);
					if (mDevicesConnected.isEmpty())
					{
						mPowerDownBT = false;
						mDeleteAllDevices = false;
						DisconnectAddr = "";
						mHandler.removeMessages (MSG_POWERDOWN_TIMEOUT);
						if ((mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) || (mBTAdapter.getState() == BluetoothAdapter.STATE_ON))
						{
							if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON)
							{
								mLogger.i( "Adapter turning on! Delay power down");
								if (!mHandler.hasMessages(MSG_POWER_DOWN_BT))
									mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_POWER_DOWN_BT ), 1000);
								break;
							}
							if (mBTAdapter.disable() == true)
								mLogger.i( "power down Bluetooth done");
							else
							{
								mLogger.w( "power down Bluetooth failed!");
								if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE) == true)
									mLogger.i( "set SCAN_MODE_CONNECTABLE");
							}
						}
					}
					else
					{
						DeviceServices removeDevice = new DeviceServices();
						removeDevice = mDevicesConnected.get(0);
	   					disconnectServices(removeDevice.mDevaddr);
					}
				}
				else
				{
					if (DisconnectAddr.equals(mDevaddr))
					{
						DisconnectAddr = "";
        				if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE) == true)
        					mLogger.i( "set SCAN_MODE_CONNECTABLE");
        				if (ActivityState == BLUETOOTH_ACTIVITY_PAIRED)
        				{
        					ActivityState = BLUETOOTH_ACTIVITY_IDLE;
        				}
	               		if ((ActivityState == BLUETOOTH_ACTIVITY_IDLE) && (ignitionState == IgnigtionState.IGNITION_ON)) {
	               			if (mConnectedDevice == -1 )
	               			{
	               				if (mTryConnectDevice < DevicesTryConnectedAddr.size()) {
	               					connectRetries = BLUETOOTH_CONNECT_RETRIES;
	               					mTryConnectDevice++;
	               					for (; mTryConnectDevice < DevicesTryConnectedAddr.size(); mTryConnectDevice++) {
	               						DeviceId = getPairedDeviceId(DevicesTryConnectedAddr.get(mTryConnectDevice));
	               						if ( (DeviceId > -1 ) && (DevicesType[DeviceId] == TYPE_MOBILE) ) {
	               							mLogger.i( "MSG_BT_DEVICE_DISCONNECTED: Start HFP connect: " + DevicesAddr[DeviceId] + "," + DevicesName[DeviceId]);
	               							addDeviceService(DevicesAddr[DeviceId], SRV_HFP);
	               							btHFP.connect(DevicesAddr[DeviceId]);
	               							ActivityState = BLUETOOTH_ACTIVITY_WAIT_CONNECT;
	               							break;
	               						}
	               					}
	               				}
	               			}
	               			else 
	               			{
	               				mLogger.w("Should not get here!!!"); //TODO remove following
	               			}
	              		}
					}
				}
				break;
			case MSG_DELETE_PAIRING_LIST_TIMEOUT:
				print("msg: MSG_DELETE_PAIRING_LIST_TIMEOUT");
				if (mDeletePairingList)
				{
    				mLogger.w(  "clear pairing list failed!");
    				if (!mDevicesConnected.isEmpty())
    				{
    					//connected devices mismatch between us and Synergy. Assume no devices connected and try again
    					mDevicesConnected.clear();
    					mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  null));
    					mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_DELETE_PAIRING_LIST_TIMEOUT ),MSG_DELETE_PAIRING_LIST_DELAY);
    				}
    				else
    				{
    					//still failing do Recovery
    					mDeletePairingList = false;
						mDeleteAllDevices = false;
						ActivityState = BLUETOOTH_ACTIVITY_IDLE;
						String ttsPairCleared = mStrings.getExpandedStringById( StringReader.Strings.TTS_CLEAR_PAIR_LIST_FAILED );
	     				if ( ( ttsPairCleared != null ) && !ttsPairCleared.isEmpty() )
	     				{
	     					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_CLEAR_PAIR_LIST_FAILED );
	     					// This tts is relevant until it can be heard to notify about the process failure.
	     					mTts.speak(ttsPairCleared, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
	     							TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
	     				}
						new recoveryBluetooth().start();
    				}
				}
				break;
			case MSG_POWERDOWN_TIMEOUT:
				print("msg: MSG_POWERDOWN_TIMEOUT");
				if (mPowerDownBT)
				{
    				mLogger.w(  "power down failed!");
    				if (!mDevicesConnected.isEmpty())
    				{
    					//connected devices mismatch between us and Synergy. Assume no devices connected and try again
    					mDevicesConnected.clear();
    					mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  null));
    					mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_POWERDOWN_TIMEOUT ),MSG_POWERDOWN_TIMEOUT_DELAY);
    				}
    				else
    				{
    					//still failing do Recovery
    					mPowerDownBT = false;
						mDeleteAllDevices = false;
						DisconnectAddr = "";
						ActivityState = BLUETOOTH_ACTIVITY_IDLE;
						new recoveryBluetooth().start();
    				}
				}
				break;
			case MSG_REMOVE_CONTACTS_CACHE:
				print("msg: MSG_REMOVE_CONTACTS_CACHE");
				mDevaddr = (String) msg.obj;
				mBtContactsCache.deleteMacFromDB(mDevaddr);
				break;
			case MSG_GET_PAIRED_PHONES_INFO:
				print("msg: MSG_GET_PAIRED_PHONES_INFO");
				mHandler.removeMessages(MSG_GET_PAIRED_PHONES_INFO);
				getPairedPhonesInfo();
				break;
			case MSG_GET_CONNECTED_HFP_PHONE_INFO:
				print("msg: MSG_GET_CONNECTED_HFP_PHONE_INFO");
				mHandler.removeMessages(MSG_GET_CONNECTED_HFP_PHONE_INFO);
				getConnectedHfpPhoneInfo();
				break;
			case MSG_BOND_NONE:
				print("msg: MSG_BOND_NONE");
				mDevaddr = (String) msg.obj;
				mBtRecentPaired.deleteMacFromDB(mDevaddr);
    	   		if (DevicesPanEnabled.contains(mDevaddr))
    	   		{
    	   			DevicesPanEnabled.remove(mDevaddr);
    	   			savePanEnabledDeviceList();
    	   		}
                mHandler.sendMessage( mHandler.obtainMessage( MSG_REMOVE_CONTACTS_CACHE, mDevaddr));
 				if (mConnectedDevice > -1)
 					connectedDeviceAddr = DevicesAddr[mConnectedDevice];
 				else
 				{
 					disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
 					if (mSppAddress.equals(mDevaddr) && ((disabledServices & DISABLE_SRV_SPP) == 0) &&
 							((disabledServices & DISABLE_SRV_HFP) == DISABLE_SRV_HFP) )
 					{
 						//SPP only device is connected. Disconnect it.
 						mLogger.i("Disconnect SPP " + mSppAddress);
 						disconnectSpp();
 					}
 				}
 				getPairedDevices();
 				if (connectedDeviceAddr != null)
 					mConnectedDevice = getPairedDeviceId(connectedDeviceAddr);
       	   		if (mPairedDevices == 0)
       	   		{
       	   			if (mDeletePairingList)
       	   			{
       	   				mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_DEVICE_DISCONNECTED,  mDevaddr));
       	   			}
       	   		}
       	   		else
       	   		{
       	   			if ( ((mPairedDevices > maxPairing) && !mDeletePairingList) || (mDevicesConnected.isEmpty() && mDeletePairingList) ) {
       	   				unpairOneDevice();
       	   			}
       	   		}
				break;
			case MSG_BONDED:
				print("msg: MSG_BONDED");
				mDevaddr = (String) msg.obj;

   				IndicationManager.setIndication(mContext, IndicationMgrParams.EIND_PAIRED_SUCCESS, true);
 				String ttsPaired = mStrings.getExpandedStringById( StringReader.Strings.TTS_PAIRED_SUCCESSFULLY );
 				if ( ( ttsPaired != null ) && !ttsPaired.isEmpty() )
 				{
 					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_PAIRED_SUCCESSFULLY );
 					// This tts is relevant for about ~20 seconds to notify about the process success, after then the rest of the 
 					// notification msgs will be arriving.
 					mTts.speak(ttsPaired, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
 							20, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
 				}
 				mBtRecentPaired.saveRecentPairedToDB(mDevaddr, false, false);
 				insertConnectedDevice( mDevaddr );
 				if (mConnectedDevice > -1)
 					connectedDeviceAddr = DevicesAddr[mConnectedDevice];
 				getPairedDevices();
 				if (connectedDeviceAddr != null)
 					mConnectedDevice = getPairedDeviceId(connectedDeviceAddr);
     	   		if ((mPairedDevices > maxPairing) && !mDeletePairingList) {
              	   	// Allowed Paired devices list full. Unpair least recent connected device
     	   			unpairOneDevice();
       	   		}
       	   		else
       	   		{
       	   			// recheck paired devices incase device changed name since previous connection
       	   			mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_GET_PAIRED_DEVICES), 1000);
       	   		}
				break;
			case MSG_GET_PAIRED_DEVICES:
				print("msg: MSG_GET_PAIRED_DEVICES");
				getPairedDevices();
				break;
				/*
				 * user confirmed to complete passkey pairing
				 */
			case MSG_PASSKEY_CONFIRM:
				print("msg: MSG_PASSKEY_CONFIRM");
				mHandler.removeMessages(MSG_PASSKEY_CONFIRM_TIMEOUT);
				if (mPasskeyDevice != null) 
				{
					boolean allowPair = false;
					if (isDiscoverable())
					{
						allowPair = true;
					}
					else
					{
						String deviceAddress = mPasskeyDevice.getAddress();
	        	        if (isDevicePaired(deviceAddress))
	        	        {
	        	            allowPair = true;
	        	        }
	        	    }
	        	    if (allowPair)
	        	    {
						mPasskeyDevice.setPairingConfirmation(true);
					}
					mPasskeyDevice = null;
				}
				break;
				/*
				 * Confirm passkey timeout reached and no confirm was received. cancel confirm.
				 */
			case MSG_PASSKEY_CONFIRM_TIMEOUT:
				print("msg: MSG_PASSKEY_CONFIRM_TIMEOUT");
				mPasskeyDevice = null;
				break;
			case MSG_IS_ANY_BT_SERVICE_CONNECTED:
				print("msg: MSG_IS_ANY_BT_SERVICE_CONNECTED");
				getIsBtDeviceConnected();
				break;
			case MSG_GET_BT_SERVICES_CONNECTED:
				print("msg: MSG_GET_BT_SERVICES_CONNECTED");
				sendBluetoothInfo();
				break;
			case MSG_DELAYED_PAIRING_RESPONSE:
				print("msg: MSG_DELAYED_PAIRING_RESPONSE");
				BluetoothDevice device = (BluetoothDevice)msg.obj;
				byte[] pinBytes = BluetoothDevice.convertPinToBytes("?");//invalid value to reject pairing
				device.setPin(pinBytes);
				break;
				
			default:
				mLogger.w(  "messageHandler:unknown msg " + msg.what);				
				break;
		}
	}

	 
	 private void startBluetoothConnect() {
		int i;
		
	    mTryConnectDevice = -1;
	    mConnectedDevice = -1;
	    mConnectVerbose = false;
	    mConnectVerboseLevel = ERecentPairedVerbose.RecentPairedVerbose_None;

		 if ((ActivityState == BLUETOOTH_ACTIVITY_PAIRED) && (bootCompleted == true))
		 {
			 // get paired
			 ActivityState = BLUETOOTH_ACTIVITY_IDLE;
			 if (removeUnpairedDevices() != 0)
			 {				 

				 print("Boot completed:" + bootCompleted + ", Ignition state:" + ignitionState);
				 if ((bootCompleted == true) && (ignitionState == IgnigtionState.IGNITION_ON))
				 {
		             int HFPConnectionId = btHFP.getConnectedHandsfreeDevice();
		             if (HFPConnectionId == BluetoothHFP.HFP_INVALID_CONNECTION_ID)
		             {
	            		 // all disconnected.
	            		 tryHFPConnect(false);
		             }
		             else
		             {
		            	 i = CheckAllDevicesDisconnected();
		            	 if (i == -1)
		            	 {
		            		 // all disconnected.
		            		 tryHFPConnect(false);
		            	 }
		            	 else
		            	 {
		            		 mLogger.e( "not all disconnected.!!!! abort");
		            	 }
					 }
				 }
			 }
		 }   
	}

/*********************************
* Listen for Boot complete event
*********************************/
	 private BroadcastReceiver mBootBroadcastReceiver = new BroadcastReceiver() {

		 @Override
		 public void onReceive(Context context, Intent intent) {
			 mLogger.i( "ACTION_BOOT_COMPLETED");
			 bootCompleted = true;
			 mHandler.sendMessage( mHandler.obtainMessage( MSG_BOOT_COMPLETE ));
		 }
	 };

/*********************************
* Listen for Hibernation events
*********************************/
	 private BroadcastReceiver mHibernateReceiver = new BroadcastReceiver() {

		 @Override
		 public void onReceive(Context context, Intent intent)
		 {
			 String action = intent.getAction();
			 if (action.equals(Intent.ACTION_HIBERNATION))
			 {
				 mLogger.i( "ACTION_HIBERNATION_STARTED");
				 bootCompleted = false;
				 mHandler.removeMessages(MSG_HIBERNATION_TIMEOUT);
			 }
			 else
			 {
				 mLogger.i( "ACTION_HIBERNATION_ENDED");
				 // Just wait for intent Synergy stack ready
				 mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_HIBERNATION_TIMEOUT ),MSG_HIBERNATION_TIMEOUT_DELAY );
			 }
		 }
	 };

/*******************************	 
* Check Bluetooth On/Off state
********************************/
    private BroadcastReceiver mBluetoothStatusReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR); 
                print("Bluetooth ON/OFF state changed");
                switch (bluetoothState) {
                    case BluetoothAdapter.STATE_ON:
        	    		mLogger.i( "Bluetooth ON");
       	    			mHandler.sendMessage( mHandler.obtainMessage( MSG_BLUETOOTH_ON ));
       	    			sendBtErrStatus(false);
                     break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        print("Bluetooth turning OFF");     	    		
                     break;
                    case BluetoothAdapter.STATE_OFF:
         	    		mLogger.i( "Bluetooth OFF");
         	    		mBtReady = false;
         	    		mPasskeyDevice = null;
         	    		NotifyClients(BLUETOOTH_STATUS_UNREADY);
       	    			mHandler.sendMessage( mHandler.obtainMessage( MSG_BLUETOOTH_OFF ));
                     break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                    	print("Bluetooth turning on");
       	    		break;
                    
                    default:
                    break;
                }
            }
            else
            {
            	if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
            		print("ACTION_SCAN_MODE_CHANGED");
            		if (mBTAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
	  			 		mLogger.i( "SCAN Mode is: SCAN_MODE_CONNECTABLE.");
// TODO for tester mode   	  			 	if (mBTAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,0) == true) //always
//    	  			 		mLogger.i( "SCAN Mode is:SCAN_MODE_CONNECTABLE. Setting SCAN_MODE_CONNECTABLE_DISCOVERABLE");
//    	  			 	else
//    	  			 		mLogger.w( "SCAN Mode is:SCAN_MODE_CONNECTABLE. failed Setting SCAN_MODE_CONNECTABLE_DISCOVERABLE");
            		}
            		else if (mBTAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            			mLogger.i( "SCAN Mode is: SCAN_MODE_CONNECTABLE_DISCOVERABLE.");
            		}
            		else if (mBTAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_NONE) {
            			mLogger.i( "SCAN Mode is: SCAN_MODE_NONE");

            		}
            	}
            }
        }
    };

public boolean isDiscoverable()
{
	return (mBTAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
}

public boolean isPANSupported(String address)
{
	int remoteDevSupprotNap = btPanu.isPanNapSupport(address);
	return remoteDevSupprotNap == BluetoothPanu.PAN_REMOTE_NAP_SUPPORT;
}

/*******************************************
* Check for Bluetooth device connected state
********************************************/
    private BroadcastReceiver mBluetoothConnectionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            mLogger.i( "Intent received: " + action);
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                if (device != null) {
                	String deviceName = device.getName();
                	String deviceAddress = device.getAddress();
                	mLogger.i( "Bluetooth Device Connected name = " + deviceName + " address = " + deviceAddress);
                }
                else {
                	mLogger.e( "Bluetooth Device could not be obtained");
                }
            }
            else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                if (device != null) {
                	String deviceAddress = device.getAddress();
                	mLogger.i( "Bluetooth Device Disconnected. Address:" + deviceAddress);
          		}
                else {
                	mLogger.e( "Bluetooth Device could not be obtained");
                }
            }
        }
    };

/********************************
* Check for HFP connection state
*********************************/	
	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {        
		 
        @Override
        public void onReceive(Context context, Intent intent) {
            int hfStatus;
            String mDevaddr;         

            if(intent.getAction().equals(BluetoothHFP.ACTION_HFP_STATUS_CHANGED)) {
            	print("BLUETOOTH_HFP_CONNECTION_STATUS_CHANGED");
                mDevaddr = intent.getStringExtra(BluetoothHFP.EXTRA_HFP_DEVICE_ADDRESS);
                hfStatus = intent.getIntExtra(BluetoothHFP.EXTRA_HFP_STATUS, BluetoothHFP.HFP_DISCONNECTED);
                
                switch(hfStatus) {
               		case BluetoothHFP.HFP_CONNECTED:
               			mLogger.i( "HFP_CONNECTED " + mDevaddr);
               			mHmiDisplayService.cancelFromSRHard();
               			mHandler.sendMessage( mHandler.obtainMessage( MSG_HFP_CONNECTED, mDevaddr));
            	 		break;
                	case BluetoothHFP.HFP_DISCONNECTED:
                	 	mLogger.i( "HFP_DISCONNECTED " + mDevaddr);
                        hfpService = 0;
                        hfpSignal = 0;
                        sendBluetoothService(false);
                        sendBluetoothSignalStrength(hfpSignal);
                		mBtContactsCache.abortCreateContactsCache();
                		mBtContactsCache.setNotifyContactsCacheAddr("");
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_HFP_DISCONNECTED, mDevaddr ));
                		break;
                    	case BluetoothHFP.HFP_CONNECTING:
                    		mLogger.i("BLUETOOTH_HFP_CONNECTION_STATUS_CONNECTING");
                    	break;
                 }
            }
            else if (intent.getAction().equals(BluetoothHFP.ACTION_HFP_SERVICE_CHANGED)) {
            	hfpService = intent.getIntExtra(BluetoothHFP.EXTRA_HFP_SERVICE, 0);
            	boolean service = (hfpService == 1 ? true: false) ;
            	sendBluetoothService(service);
            }
            else if (intent.getAction().equals(BluetoothHFP.ACTION_HFP_SIGNAL_STRENGTH_CHANGED)) {
            	hfpSignal = intent.getIntExtra(BluetoothHFP.EXTRA_HFP_SIGNAL_STRENGTH, 0);
            	sendBluetoothSignalStrength(hfpSignal);
            }
            else if(intent.getAction().equals(BluetoothHFP.ACTION_BLUETOOTH_ABORT)){
                mLogger.w( "ACTION_BLUETOOTH_ABORT");
                new recoveryBluetooth().start();
            }
            else if(intent.getAction().equals(BluetoothHFP.ACTION_BLUETOOTH_CHIP_COMM_FAIL)){
                int errorCode = intent.getIntExtra(BluetoothHFP.EXTRA_CHIP_COMM_FAIL_ERROR, BluetoothHFP.HF_RESULT_CODE_BLUECORE_ERROR_BCSP_INIT_FAIL);
                mLogger.e( "ACTION_BLUETOOTH_CHIP_COMM_FAIL: " + errorCode );
                sendBtErrStatus(true);
                // Normally, HF_RESULT_CODE_BLUECORE_ERROR_BCSP_INIT_FAIL error cannot be recovered.
                //new recoveryBluetooth().start();
				//remove the MSG_SYNERGY_RECOVERY message after error intent comes.
				mHandler.removeMessages(MSG_SYNERGY_RECOVERY);
            }
            else if(intent.getAction().equals(BluetoothIntent.ACTION_SYNERGY_STACK_READY)){
            	int stackReady = intent.getIntExtra(BluetoothIntent.EXTRA_SYNERGY_STACK, 1);
            	if (stackReady == 1)
            	{
            		mLogger.i("ACTION_SYNERGY_STACK_READY: Bluetooth stack ready!");
                	bootCompleted = true;
    				mHandler.removeMessages(MSG_HIBERNATION_TIMEOUT);
                	// always go to boot complete. Recovery if needed will be done from there
                	mHandler.sendMessage( mHandler.obtainMessage( MSG_BOOT_COMPLETE ));
            	}
            	else
            	{
            		mLogger.e("ACTION_SYNERGY_STACK_READY: Bluetooth stack failed to start!");
            		if (mHandler.hasMessages(MSG_HIBERNATION_TIMEOUT))
            		{
            			//Hibernation timed out after Hibernation ended and stack failed to start so try recovery
                       	bootCompleted = true;
        				mHandler.removeMessages(MSG_HIBERNATION_TIMEOUT);
                    	// always go to boot complete. Recovery if needed will be done from there
                    	mHandler.sendMessage( mHandler.obtainMessage( MSG_BOOT_COMPLETE ));
                    	// send this in case auto recovery fails.
                    	mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_SYNERGY_RECOVERY ),MSG_SYNERGY_RECOVERY_DELAY);
            		}
            	}
            }
        }
	};
	
/**********************************************
* check for PBAP connection status events
**********************************************/
    private BroadcastReceiver mPbapBroadcastReceiver = new BroadcastReceiver() {        

        @Override
        public void onReceive(Context context, Intent intent) {
           String mDevaddr;
            int pbapStatus;
            int result;
            if(intent.getAction().equals(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_CONNECTION_STATUS_CHANGED)) {
            	mDevaddr = intent.getStringExtra(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_CONNECTION_ADDRESS);
            	pbapStatus = intent.getIntExtra(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_CONNECTION_STATUS,BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_DISCONNECTED);
                switch(pbapStatus) {
            		case BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_DISCONNECTED:
                     	mLogger.i("BLUETOOTH_PHONEBOOK_CONNECTION_STATUS_DISCONNECTED " + mDevaddr);
               			mHandler.sendMessage( mHandler.obtainMessage( MSG_PBAP_DISCONNECTED, mDevaddr));
            			break;
            		case BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_CONNECTING:
                		break;
            		case BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_CONNECTED :
                       	mLogger.i("BLUETOOTH_PHONEBOOK_CONNECTION_STATUS_CONNECTED " + mDevaddr);
               			mHandler.sendMessage( mHandler.obtainMessage( MSG_PBAP_CONNECTED, mDevaddr));
                		break;
    
                	default:
                		break;
                 		
                }
            }
            else
            {
            	if(intent.getAction().equals(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_MSG_RECEIVED)) {
            		print("BLUETOOTH_PHONEBOOK_MSG_RECEIVED");
                   	mDevaddr = intent.getStringExtra(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_CONNECTION_ADDRESS);
                   	pbapStatus = intent.getIntExtra(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_MSG,BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_UNKOWN_MSG);
                    switch(pbapStatus) {
                    	case BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_ERROR_MSG:
                    		result = intent.getIntExtra(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_MSG_RESULT,BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_RESULT_CODE_FAILURE);
                    		mLogger.i("CSR_ANDROID_PHONEBOOK_ERROR_MSG: " + mDevaddr + " result:" + result);
                    		mHandler.sendMessage( mHandler.obtainMessage( MSG_PBAP_ERROR_MSG, result, 0, mDevaddr));
                    		break;
                    	case BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_PULL_PB_CFM:
                    		result = intent.getIntExtra(BluetoothPhonebookClientIntent.BLUETOOTH_PHONEBOOK_MSG_RESULT,BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_RESULT_CODE_FAILURE);
                    		mLogger.i("PBAP_PULL_PB_CFM: " + mDevaddr + " result:" + result);
                   			mHandler.sendMessage( mHandler.obtainMessage( MSG_PBAP_PULL_PB_CFM, result, 0, mDevaddr));
             				break;
                    	case BluetoothPhonebookClient.CSR_ANDROID_PHONEBOOK_DISCONNECT_IND:
                    		mLogger.i("PBAP_DISCONNECT_IND: " + mDevaddr + " DisconnectAddr:" + DisconnectAddr);
                    		mHandler.sendMessage( mHandler.obtainMessage( MSG_PBAP_DISCONNECT_IND, mDevaddr));
                    		break;
                      	default:
                    		break;
                     		
                    }
            	}
            }
        }
    };
	
/****************************************
* check for MAP connection status events
*****************************************/
    private BroadcastReceiver mMapBroadcastReceiver = new BroadcastReceiver() 
    {        
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	String devAddr;
        	int instanceId;
            int status;
                 
            if (intent.getAction().equals(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE)) 
            {
            	print("BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE");
            	devAddr = intent.getStringExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_ADDRESS);
            	instanceId = intent.getIntExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE,-1);
            	status = intent.getIntExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS,BluetoothMapClient.BLUETOOTH_MAPC_DISCONNECTED);
                switch(status) {
            		case BluetoothMapClient.BLUETOOTH_MAPC_CONNECTED:
                    	print("BLUETOOTH_MAPC_CONNECTED " + devAddr + " InstanceId:" + instanceId);
                        mHandler.sendMessage( mHandler.obtainMessage( MSG_MAP_CONNECTED, devAddr));
                    	break;
               		case BluetoothMapClient.BLUETOOTH_MAPC_DISCONNECTED:
                    	print("BLUETOOTH_MAPC_DISCONNECTED " + devAddr);
                        mHandler.sendMessage( mHandler.obtainMessage( MSG_MAP_DISCONNECTED, devAddr));
                 		break;
                	default:
                	break;
                }
            }
        }
    };
    
/**************************************    
* Check for pairing (bonding) events
***************************************/
    private BroadcastReceiver mBondBroadcastReceiver = new BroadcastReceiver() {
    	public void onReceive(Context context, Intent intent) {

    	   print("mBondBroadcastReceiver " + intent.getAction());
    	   mPasskeyDevice = null;
           String action = intent.getAction();
           BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
           if ( device != null ) {
	           if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
	        	   int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
	        	   switch(bondState) {
	        	   	case BluetoothDevice.BOND_BONDED:
	        	   		mLogger.i("ACTION_BOND_STATE_CHANGED:BOND_BONDED " + device.getAddress());
	                    mHandler.sendMessage( mHandler.obtainMessage( MSG_BONDED, device.getAddress())); 
	  				break;
	          	   	case BluetoothDevice.BOND_NONE:
	        	   		mLogger.i("ACTION_BOND_STATE_CHANGED:BOND_NONE " + device);
	                    mHandler.sendMessage( mHandler.obtainMessage( MSG_BOND_NONE, device.getAddress()));
	          	   	break;
	        	   	default:
	        	   	break;      		
	        	   }
	           }
	           else
	           {
	        	   if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
	        		   int mType;
	        		   mType = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
	           		   mLogger.i("ACTION_PAIRING_REQUEST " + device + " Type:" + Integer.toString(mType));
	        		   if (mType == BluetoothDevice.PAIRING_VARIANT_PIN) {
	   	                   mLogger.i( "PAIRING_VARIANT_PIN");
	   	                   byte[] pinBytes;
	        			   if ((mBTAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) && !mDeleteAllDevices)
	        			   {
	            			   pinBytes = BluetoothDevice.convertPinToBytes(myBTPairingCode);
		    				   device.setPin(pinBytes);
	        			   }
	        			   else
	        			   {
	        	               String deviceAddress = device.getAddress();
	        	               if (isDevicePaired(deviceAddress))
	        	               {
	        	            	   pinBytes = BluetoothDevice.convertPinToBytes(myBTPairingCode);
	    	    				   device.setPin(pinBytes);
	        	               }
	        	               else
	        	               {
	        	            	   if (mUnsuccessfulPairingAttempts < 6) 
	        	            		   mUnsuccessfulPairingAttempts++;
	        	            	   if (mUnsuccessfulPairingAttempts >= 3)
	        	            	   {
	        	            		   int responseDelay;
	        	            		   if (mUnsuccessfulPairingAttempts >= 6) 
	        	            		   {
	        	            			   mUnsuccessfulPairingAttempts = 6;
	        	            			   responseDelay = 10000;
	        	            		   }
	        	            		   else if (mUnsuccessfulPairingAttempts == 5)
	        	            		   {
	        	            			   responseDelay = 2000;
	        	            		   }
	        	            		   else if (mUnsuccessfulPairingAttempts == 4)
	        	            		   {
	        	            			   responseDelay = 1000;
	        	            		   }
	        	            		   else
	        	            		   {
	        	            			   responseDelay = 500;
	        	            		   }
	        	            		   mLogger.d("pairing delay respone: " + responseDelay + "ms");
	        	            		   mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_DELAYED_PAIRING_RESPONSE, device), responseDelay);
	        	            	   }
	        	            	   else
	        	            	   {
	        	            		   pinBytes = BluetoothDevice.convertPinToBytes("?");//invalid value to reject pairing
	        	    				   device.setPin(pinBytes);
	        	            	   }
	        	               }
	        			   }
	        		   } else if (mType == BluetoothDevice.PAIRING_VARIANT_PASSKEY) {
	         			   mLogger.i( "PAIRING_VARIANT_PASSKEY");
	        		   } else if (mType == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) {
	        		   		int passkey = intent.getIntExtra(BluetoothDevice.EXTRA_PASSKEY, BluetoothDevice.ERROR);
	        		   		if (passkey == BluetoothDevice.ERROR) {
	    	                   	mLogger.i( "Invalid Confirmation Passkey received, not showing any dialog");                  
	      	               	}
	      	               	else {
	      	               		mLogger.i( "PAIRING_VARIANT_PASSKEY_CONFIRMATION Passkey: " + String.format("%06d", passkey));
	      	               		// Add spaces between the passkey digits
	      	               		char[] chars = ("" + passkey).toCharArray();
								String passkeySpace = "";
								for ( char digit : chars ) {
									passkeySpace += digit + " ";
								}
	      	               		boolean allowPair = false;
	        		   			if ((mBTAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) && !mDeleteAllDevices) {
	        		   				allowPair = true;
	        		   			}
	        		   			else  {
	        		   				String deviceAddress = device.getAddress();
	        	               		if (isDevicePaired(deviceAddress))
	        	               		{
	        	               			allowPair = true;
	        	               		}
	        	               	}
	        	               	if (allowPair) {

	    	                   		String ttsConfirmPasskey = mStrings.getExpandedStringById( StringReader.Strings.TTS_CONFIRM_PASSKEY );
	    	                   		String ttsConfirmPasskeyForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.TTS_CONFIRM_PASSKEY );
	    	                   		if ( ( ttsConfirmPasskey != null ) && !ttsConfirmPasskey.isEmpty() )
	    	                   		{
	    	                	   		String ttsPasskey = ttsConfirmPasskey.replace("%s", passkeySpace);
	    	                	   		String ttsPasskeyForDisplay = ttsConfirmPasskeyForDisplay.replace("%s", String.format("%06d", passkey));
	    	                	   		mTts.speak(ttsPasskey, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
	    	    								TextToSpeech.TTS_RELEVANCE_ETERNAL, ttsPasskeyForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
	    	                   		}
	    	                   		//device.setPairingConfirmation(true);
	    	                   		mPasskeyDevice = device;
	    	                   		mHandler.removeMessages(MSG_PASSKEY_CONFIRM_TIMEOUT);
	    	                   		mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_PASSKEY_CONFIRM_TIMEOUT), MSG_PASSKEY_CONFIRM_TIMEOUT_DELAY);
	    	               		}
	    	               }
	        		   } else if (mType ==  BluetoothDevice.PAIRING_VARIANT_CONSENT) {
	        			   device.setPairingConfirmation(true);
	        		   } else if (mType == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY) {
	        	           int passkey = intent.getIntExtra(BluetoothDevice.EXTRA_PASSKEY, BluetoothDevice.ERROR);
	        	               if (passkey == BluetoothDevice.ERROR) {
	        	                   mLogger.e( "Invalid Confirmation Passkey received, not showing any dialog");
	          	               }
	        	               else {
	        	                   mLogger.i( "PAIRING_VARIANT_DISPLAY_PASSKEY Passkey: " + String.format("%06d", passkey));      	            	   
	        	               }
	        		   } else if (mType == BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT) {
	        			   device.setRemoteOutOfBandData();
	        		   } else {
	        			   mLogger.i( "Incorrect pairing type received");
	        		   }  
	        	   }
	           }
	      	}
	      	else {
	      		mLogger.e( "BluetoothDevice device could not be obtained");
	      	}
    	}
    };

/***********************************
* Listen for A2DP + AVRCP events
***********************************/
    private BroadcastReceiver mBluetoothA2dpAvrcpReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            print("A2DP-AVRCP Intent received: " + action);
            if (action.equals(BluetoothA2dpSink.BluetoothA2dpSinkIntent.BLUETOOTH_A2DP_SINK_CONNECTION_STATUS_CHANGED)) {
            	int status  = intent.getIntExtra(BluetoothA2dpSink.BluetoothA2dpSinkIntent.BLUETOOTH_A2DP_SINK_CONNECTION_STATUS, BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_DISCONNECTED);
                if (status == BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_CONNECTED) {
                    mA2dpBTAddress = intent.getStringExtra(BluetoothA2dpSink.BluetoothA2dpSinkIntent.BLUETOOTH_A2DP_SINK_CONNECTION_ADDRESS);
                    mLogger.i( "A2DP connected. Address:" + mA2dpBTAddress);
                    mHandler.sendMessage( mHandler.obtainMessage( MSG_A2DP_CONNECTED, mA2dpBTAddress));
                }
                else
                {
                	if (status == BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_DISCONNECTED) {
                		String disconnectAddress = intent.getStringExtra(BluetoothA2dpSink.BluetoothA2dpSinkIntent.BLUETOOTH_A2DP_SINK_CONNECTION_ADDRESS);
                		if ( disconnectAddress != null ) {
	                        mHandler.sendMessage( mHandler.obtainMessage( MSG_A2DP_DISCONNECTED,  disconnectAddress));
	                		if (disconnectAddress.equals(mA2dpBTAddress)) {
	                			mLogger.i( " Active A2DP disconnected. Address:" + disconnectAddress);
	                        	mA2dpBTAddress = "";
	                		}
	                		else {
	                			mLogger.i( " Non active A2DP disconnected. Address:" + disconnectAddress);
	                		}
	                	}
	                	else {
	                		mLogger.e( "DdisconnectAddress  could not be obtained");
	                	}
                	}
                }
            }
            else
            {
                if (action.equals(BluetoothIntent.BLUETOOTH_AVRCP_CON_IND_ACTION)) {
                	if (mBTAdapter.getState() != BluetoothAdapter.STATE_OFF)
                	{
                		mAvrcpBTAddress = intent.getStringExtra(BluetoothIntent.ADDRESS);
                		mAvrcpVersion = intent.getIntExtra(BluetoothIntent.AVRCP_VERSION, 0);
                		mLogger.i( "AVRCP connected. Address:" + mAvrcpBTAddress);
                		mLogger.i( "AVRCP_VERSION: " + intent.getIntExtra(BluetoothIntent.AVRCP_VERSION, -1));
                		mHandler.sendMessage( mHandler.obtainMessage( MSG_AVRCP_CONNECTED, mAvrcpBTAddress));
						
						// After A2DP is connected, it is a good time to update the audio path.
                		updateMusicStreamingChannel();
                	}
                	else
                	{
                		print("ignore AVRCP connect while BT off");
                	}
                }
                else
                {
                	 if (action.equals(BluetoothIntent.BLUETOOTH_AVRCP_DIS_CON_IND_ACTION)) {
                         mLogger.i( "AVRCP disconnected. Address:" + mAvrcpBTAddress);
                         mHandler.sendMessage( mHandler.obtainMessage( MSG_AVRCP_DISCONNECTED, mAvrcpVersion, 0, mAvrcpBTAddress ));
                	 }
                	 mAvrcpBTAddress = "";
                	 mAvrcpVersion = 0;
                }
            }
        }
    };

/***********************************
* Listen for Ignition events
***********************************/
    private BroadcastReceiver mEventBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(EventData.ACTION_EVENT_UPDATED)) {
                short eventId = intent.getShortExtra(EventData.EXTRA_EVENT_ID,(short)255);
                switch(eventId) {
                case EventData.EVENTREP_IGNITIONONOFF:
                	IgnigtionState prevIgnitionState = ignitionState;
                	ignitionState = RepositoryReader.getIgnitionOnOffEvent();
                	if (ignitionState == IgnigtionState.IGNITION_ON)
                	{
                		mLogger.i("Ignition on");
                		if (prevIgnitionState != ignitionState)
                		{
                			mHandler.sendMessage( mHandler.obtainMessage( MSG_IGNITION_ON ));
                		}
                	}
                	else
                	{
                		mLogger.i("Ignition off");
                    }
                    break;
                case EventData.EVENTREP_DRIVERDOOR:
                   	if (RepositoryReader.getDriverDoorEvent() == DoorState.DOOR_OPEN)
                	{
                    	mLogger.i("Driver door opened");
                    	if ( (RepositoryReader.getIgnitionOnOffEvent() == IgnigtionState.IGNITION_ON) )
                    	{
                			mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_TOGGLE_CONNECT ));
                    	}
                	}
                	else
                	{
                    	mLogger.i("Driver door closed");
                	} 
                    break;
                }
            }
        }
    };

/*************************************
* Check for parameter change events
*************************************/
    private BroadcastReceiver mParameterBroadcastReceiver = new BroadcastReceiver() {        
		 
        @Override
        public void onReceive(Context context, Intent intent) {        

            if(intent.getAction().equals(PConf.PCONF_PARAMUPDATED)) {
          		ArrayList<Integer> paramIDs = intent.getIntegerArrayListExtra( PConf.PARAM_ID_LIST );
          		if ( paramIDs == null ) {
                    mLogger.e( "Parameter List could not obtained");  
                    return;
                }
                for ( int paramId : paramIDs ) {
            		switch(paramId) {
	               		case PConfParameters.BT_Name:
	               		    String value = mBTAdapter.getName();
	        			    print("local BT name: " + value);
	        			    setBTName();
	               			break;
	            		case PConfParameters.BT_MaxPairing:
	            			getMaxPairing();
               		    	if (mPairedDevices > maxPairing)
               		    	{
	                    	   	// Allowed Paired devices list full. Unpair least recent connected device
                  		    	deletePairingList();            		    	
	            		    }
	               			break;
	               		case PConfParameters.BT_PhonebookSynchronizationEnabled:
	               			PBSyncEnabled = getPBSyncEnabled();
	                 		break;
	            		case PConfParameters.BT_RadioEnabled:
	            			BTRadioEnabled = getBTRadioEnabled();
	                		if (BTRadioEnabled == true) {
	                			if ((!mBTAdapter.isEnabled()) && (ignitionState == IgnigtionState.IGNITION_ON) && bootCompleted)
	                			{
	                				BluetoothEnableWithStackReadyCheck();
	                			}
	                		}
	                		else {
	                			if (mBTAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON)
	                			{
	    							mLogger.i( "Adapter turning on! Delay power down");
	    	     					if (!mHandler.hasMessages(MSG_POWER_DOWN_BT))
	    	     						mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_POWER_DOWN_BT ), 1000);
	    							break;
	                			}
	                			
	                			if (!mHandler.hasMessages(MSG_POWER_DOWN_BT))
	                				mHandler.sendMessage( mHandler.obtainMessage( MSG_POWER_DOWN_BT ));
	                		}
	                 		break;
	               		case PConfParameters.BT_PairingCode:
	               			readBTPairingCode();
	                 		break;
	                 	case PConfParameters.ACM_AudioPath_Mp3:
	                 		updateMusicStreamingChannel();
	                 		break;
	                 	case PConfParameters.BT_DisabledServices:
	                 		mHandler.sendMessage( mHandler.obtainMessage( MSG_POWER_DOWN_BT ));
	                 		break;
	            	   	default:
	                	   	break;
	                }
            	}
            }
        }
    };

    /****************************************	 
    * Check Bluetooth PANU connection events
    ****************************************/
    private BroadcastReceiver panuBroadcastReceiver = new BroadcastReceiver() {
    	public void onReceive(Context context, Intent intent) {
            String devAddr;
    		String action = intent.getAction();
    		if (action.equals(BluetoothPanu.BluetoothPanuIntent.BLUETOOTH_PANU_CONNECTION_STATUS_CHANGED)) {
            	devAddr = intent.getStringExtra(BluetoothPanu.BluetoothPanuIntent.BLUETOOTH_PANU_CONNECTION_ADDRESS);
    			int prevState = intent.getIntExtra(BluetoothPanu.BluetoothPanuIntent.BLUETOOTH_PANU_PREVIOUS_STATE, BluetoothPanu.BLUETOOTH_PANU_DISCONNECTED); 
    			int state = intent.getIntExtra(BluetoothPanu.BluetoothPanuIntent.BLUETOOTH_PANU_STATE, BluetoothPanu.BLUETOOTH_PANU_DISCONNECTED); 
    			int localRule = intent.getIntExtra(BluetoothPanu.BluetoothPanuIntent.BLUETOOTH_PANU_LOCAL_ROLE, 0); 
    	    	mLogger.d( "BLUETOOTH_PANU_CONNECTION_STATUS_CHANGED");
    	    	switch (state) {
    	    		case BluetoothPanu.BLUETOOTH_PANU_CONNECTED:
                        mHandler.sendMessage( mHandler.obtainMessage( MSG_PANU_CONNECTED, devAddr));
    	    			mLogger.i( "Device: " + devAddr + " State: PANU_CONNECTED " + "Previous state: " + prevState + " Local rule: " + localRule);  	    		 	
    	    			break;                    
    	    		case BluetoothPanu.BLUETOOTH_PANU_DISCONNECTED:
                        mHandler.sendMessage( mHandler.obtainMessage( MSG_PANU_DISCONNECTED, devAddr));
    	    			mLogger.i( "Device: " + devAddr +" State: PANU_DISCONNECTED");
    	    			break;

    	    		default:
    	    			break;
    	    	}
    		}
    	}
    };

    /*****************************************
     * Check for key press notification events
     *****************************************/
    private BroadcastReceiver mKeyBroadcastReceiver = new BroadcastReceiver() {
    	@Override
    	public void onReceive(Context context, Intent intent) {        
    		if(intent.getAction().equals(RoadTrackDispatcher.BT_KEY_UPDATED)) {
    			int keyAction  = intent.getIntExtra(RoadTrackDispatcher.KEY_ACTION, -1);
    			print("Key action " + keyAction);              
        		if (keyAction == RoadTrackDispatcher.KEY_ACTION_BT_TOGGLE_CONNECT) {
        			mHandler.sendMessage( mHandler.obtainMessage( MSG_BT_TRY_CONNECT_VERBOSE , ""));
        		}
        		else if (keyAction == RoadTrackDispatcher.KEY_ACTION_BT_PASSKEY_CONFIRMATION) {
        			if (mPasskeyDevice != null)
        				mHandler.sendMessage( mHandler.obtainMessage( MSG_PASSKEY_CONFIRM ));
        		}
    		}
    	}
    };

    /*****************************************
     * Check for UUID for MAP support
     *****************************************/
    private final BroadcastReceiver mMapUuidBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_UUID)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Parcelable[] uuid = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                String address = device.getAddress();
                print( "UUID fetched for remote device:" + address );
                if (uuid != null) {
                    ParcelUuid[] uuids = new ParcelUuid[uuid.length];
                    for (int i = 0; i < uuid.length; i++) {
                        uuids[i] = (ParcelUuid)uuid[i];
                    }
                    for (ParcelUuid uuidtemp: uuids) {
                        if (isMapProfile(uuidtemp)) {
                            print("Remote device:" + address + " MAP supported");
                			mHandler.sendMessage( mHandler.obtainMessage( MSG_MAP_UUID, address));
                            break;
                        }
                    }
                }
                else{
                    print("Remote device:" + address + " fetch uuid with sdp failed");
                }
            }
        }
    };
    
    /*************************************
    * P2P BT messages receiver
    *************************************/
    private class P2PReceiver implements IMessageReciever
    {
        public void onMessageRecieved(IMessage message)
        {
            try
            {
                switch( message.getCode() )
                {
                	case MessageCodes.MESSAGE_CODE_BT_CLI_INFO:
           				mHandler.sendMessage( mHandler.obtainMessage( MSG_GET_BT_SERVICES_CONNECTED));
                    break;

                default:
                    mLogger.w( "Unknown code!");
                    break;
                }
            }
            catch( Exception e )
            {
                mLogger.e( "P2P receive failure: ", e);
                e.printStackTrace();
            }
        }
    }
    
// Various Utils
    
    public static boolean isMapProfile(ParcelUuid uuid) {
        return uuid.equals(MAP_UUID);
    }
    
    private void sendBluetoothInfo()
    {
    	try {
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		LittleEndianOutputStream stream = new LittleEndianOutputStream(baos);
   			String BtMacAddress = null;
			BtMacAddress = BluetoothAdapter.getDefaultAdapter().getAddress();
			if (BtMacAddress == null) {
    			BtMacAddress = "not set";
    		}
    		if (mPairedDevices > 0)
    		{
    			stream.writeByte(mPairedDevices); // number of devices
        		// Devices names
    			for (int i = 0; i < mPairedDevices; i++)
    			{
    				stream.writeBytes(DevicesName[i]);
    				stream.writeByte(',');
    				int service = getDeviceService(DevicesAddr[i]);
    				if (service > 0)
    					stream.writeInt(service);
    				else
    					stream.writeInt(0);
    				stream.writeByte(',');
    			}
    		}
    		else
    		{
    			stream.writeByte(0);
    		}
    		stream.writeBytes(myBTName);	// Local BT name
    		stream.writeByte(',');
    		stream.writeBytes(myBTPairingCode);	// Local BT password.
    		stream.writeByte(',');
			stream.writeBytes(BtMacAddress);	// Local MAC Address.
			stream.writeByte(',');
    		stream.writeByte(0);
    		stream.flush();
    		stream.close();
            mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_BT, MessageCodes.MESSAGE_CODE_BT_CLI_INFO, baos.toByteArray() );
    		print( "BT_CLI_INFO ");
    	}
        catch ( Exception e ) {
            mLogger.e(  "sendBluetoothInfo()", e );
        }
     }
    
    private void sendBluetoothService(boolean service)
    {
    	try {
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		LittleEndianOutputStream stream = new LittleEndianOutputStream(baos);
    		if (service)
    			stream.writeByte(1);
    		else
    			stream.writeByte(0);
    		stream.flush();
    		stream.close();
            mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_GENERAL, MessageCodes.MESSAGE_CODE_GENERAL_BLUETOOTH_NET_SERVICE, baos.toByteArray() );
            mLogger.d( "service:" + service);
    	}
        catch ( Exception e ) {
            mLogger.e(  "exception:", e );
        }
    }

    private void sendBluetoothSignalStrength(int hfpSignal)
    {
  		if ((hfpSignal >= 0 ) && (hfpSignal < 6 ))
  		{
  			try {
  				ByteArrayOutputStream baos = new ByteArrayOutputStream();
  				LittleEndianOutputStream stream = new LittleEndianOutputStream(baos);
  				stream.writeByte(hfpSignal);
  				stream.flush();
  				stream.close();
  				mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_GENERAL, MessageCodes.MESSAGE_CODE_GENERAL_BLUETOOTH_RSSI, baos.toByteArray() );
  				mLogger.v( "hfpSignal:" + hfpSignal);
  			}
  			catch ( Exception e ) {
  				mLogger.e(  "exception:", e );
  			}
  		}
    }
    
    private void sendBluetoothConnectionFail()
    {
    	try {
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		LittleEndianOutputStream stream = new LittleEndianOutputStream(baos);
    		stream.writeByte(0);
    		stream.flush();
    		stream.close();
            mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_GENERAL, MessageCodes.MESSAGE_CODE_GENERAL_BLUETOOTH_CONNECTION_FAIL, baos.toByteArray() );
            mLogger.d( "Connection failed");
    	}
        catch ( Exception e ) {
            mLogger.e(  "exception:", e );
        }
    }
    
	private synchronized void getPairedPhonesInfo()
	{
		mPairedPhonesInfo.clear();
		int deviceId;
		print("getPairedPhones(). size:" + DevicesRecentConnectedAddr.size());
		for (String addr : DevicesRecentConnectedAddr) {
			deviceId = getPairedDeviceId(addr);
			print("getPairedPhones(). deviceId:" + deviceId);
			if ( (deviceId > -1 ) && (DevicesType[deviceId] == TYPE_MOBILE) )
			{
				BTDeviceInfo device = new BTDeviceInfo();
				device.addr = addr;
				device.name = DevicesName[deviceId];
				device.isConnected =  (mConnectedDevice == deviceId) ? true: false;
				device.isInRange = true; //dummy
				mPairedPhonesInfo.add(device);
				print("getPairedPhones(). Addr:" + addr + " Connected:" + device.isConnected); 
			}
		}
   		synchronized (mPairedPhonesAvailable) {
   			mPairedPhonesAvailable.notifyAll();
   		}
	}
	
	private synchronized void getConnectedHfpPhoneInfo()
	{

		if (mConnectedDevice > -1)
		{
			mConnectedHfpPhoneInfo.addr = DevicesAddr[mConnectedDevice];
			mConnectedHfpPhoneInfo.name = DevicesName[mConnectedDevice];
			mConnectedHfpPhoneInfo.isConnected = true;
			mConnectedHfpPhoneInfo.isInRange = true;

		}
		else
		{
			mConnectedHfpPhoneInfo.addr = "";
			mConnectedHfpPhoneInfo.name = "";
			mConnectedHfpPhoneInfo.isConnected = false;
			mConnectedHfpPhoneInfo.isInRange = true;
		}
		print("getConnectedHfpPhoneInfo(). Addr:" + mConnectedHfpPhoneInfo.addr + " Name:" + 
				mConnectedHfpPhoneInfo.name + " Connected:" + mConnectedHfpPhoneInfo.isConnected); 
   		synchronized (mConnectedHfpPhoneAvailable) {
   			mConnectedHfpPhoneAvailable.notifyAll();
   		}
	}
	
    private boolean BluetoothEnableWithStackReadyCheck() {
    	boolean bRet = false;
    	boolean bSynergyReady = false;
    	String synergyStackReady;
    	int count, max_retry_count;
    	
    	setBTName();
    	
    	//enable bluetooth
    	if ((mBTAdapter != null) && mIsReadyToEnableBT) {
    		//waiting synergy stack ready
    		count = 0;
    		max_retry_count = 16;
    		do {
    			synergyStackReady = SystemProperties.get("CsrSynergy.stack.ready");
    			count ++;
    			print("CsrSynergy.stack.ready is " + synergyStackReady + "count=" + count);
    			if(synergyStackReady.equals("yes")){
    				bSynergyReady = true;
    				print("BT stack was ready");
    				break;
    			}
    			SystemClock.sleep(1000);
    		} while (count < max_retry_count);
    		if(bSynergyReady == true){
    			bRet = mBTAdapter.enable();
    			if (bRet)
    				mLogger.i( "Adapter enabled");
    			else
					mLogger.w( "Adapter enable failed");
    		}
    		else{
				// call enable BT to intialize the jni layer to receive the error code
    			bRet = mBTAdapter.enable();
    			if (bRet)
    				mLogger.i( "Adapter enabled");
    			else
					mLogger.w( "Adapter enable failed");
    			mLogger.e( "BT stack was not ready!");
        		// send this in case the error intent never come when CsrSynergy.stack.ready is no.
               	mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_SYNERGY_RECOVERY ),MSG_SYNERGY_RECOVERY_DELAY);
    		}
    	}
    	return bRet;
    }

    
    private void addDeviceService(String devAddr, int service)
    {
		int idx = 0;
		for (DeviceServices dev : mDevicesConnected ) {
		    if (dev.mDevaddr.equals(devAddr))
		    {
		    	dev.services |= service;
		    	mDevicesConnected.set(idx, dev);
		    	print("add Device:" + dev.mDevaddr + " Services:" + dev.services + " idx:" + idx);
		    	break;
		    }
		    idx++;
		}
		if (idx == mDevicesConnected.size())
		{
			// not found
			idx = -1;
	    	DeviceServices device = new DeviceServices();
			device.mDevaddr = devAddr;
			device.services = service;
			mDevicesConnected.add(device);
			print("add Device:" + device.mDevaddr + " Services:" + device.services + " idx:" + idx);
		}
    }

    // if device is found returns updated services value. else -1.
    private int removeDeviceService(String devAddr, int service)
    {
		int idx = 0;
		for ( DeviceServices device : mDevicesConnected ) {
		    if (device.mDevaddr.equals(devAddr))
		    {
		    	device.services &= ~service;
				if (device.services == SRV_NONE)
				{
					mDevicesConnected.remove(idx);
				}
				else
				{
					mDevicesConnected.set(idx, device);
				}
				print("remove Device:" + device.mDevaddr + " Services:" + device.services + " idx:" + idx);
				return device.services;
		    }
		    idx++;
		}
		return -1;
    }
    
    // if device is found returns services value. else -1.
    private int getDeviceService(String devAddr)
    {
		for ( DeviceServices device : mDevicesConnected ) {
		    if (device.mDevaddr.equals(devAddr))
		    {
				return device.services;
		    }
		}
		return -1;
    }
    
    private void disconnectSpp()
    {
    	if (!mSppAddress.isEmpty())
    	{
    		if (mPNDHandler != null)
    		{
    			mPNDHandler.sendSppDisconnectingMessageToPND();
    			mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_WAIT_SPP_BEFORE_DISCONNECT ), MSG_WAIT_SPP_BEFORE_DISCONNECT_DELAY);//wait for disconnect message to get phone
    		}
    		else
    		{
    			mConnectionDesc.Disconnect();
    		}
    	}
    }
    
	private void disconnectServices(String mDevaddr)
	{
		int PbapStatus;
		PbapStatus = mBluetoothPhonebookClient.getConnectionStatus(mDevaddr);
		if 	( (PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_CONNECTED) ||
				(PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_COMMUNICATING) ||
				(PbapStatus == BluetoothPhonebookClient.BLUETOOTH_PHONEBOOK_CONNECTING))
		{
			mLogger.i( "Disconnect PBAP: " + mDevaddr);
			mBluetoothPhonebookClient.disconnect(mDevaddr);
		}
		if (mDevaddr.equals(mSppAddress))
		{
			mLogger.i("Disconnect SPP " + mSppAddress);
			disconnectSpp();
		}
		if (mAvrcpMedia.getConnectState() != RTMediaAvrcp.ConnectState.DISCONNECTED)
		{
			//Disconnect AVRCP
			if (mDevaddr.equals(mAvrcpBTAddress))
			{
				mAvrcpMedia.disconnectAvrcp();
				mLogger.i("Disconnect Active AVRCP " + mAvrcpBTAddress);
	
			}
		}
		if (btA2dp.getCurrentConnectionStatus(mDevaddr) != BluetoothA2dpSink.BLUETOOTH_A2DP_SINK_DISCONNECTED)
		{
			//Disconnect A2DP
			btA2dp.disconnect(mA2dpBTAddress);
			mLogger.i("Disconnect Active A2DP " + mA2dpBTAddress);
		}
		if (mBluetoothMapClient.getConnectionStatus(mDevaddr) != BluetoothMapClient.BLUETOOTH_MAPC_DISCONNECTED)
		{
			// disconnect MAP  
			mLogger.i("Disconnect MAP " + mDevaddr);
			mBluetoothMapClient.disconnect(RoadTrackBTMAP.InstanceId);   
		}
		if ( (btPanu.getConnectionState(mDevaddr) == BluetoothPanu.BLUETOOTH_PANU_CONNECTED) ||
 			(btPanu.getConnectionState(mDevaddr) == BluetoothPanu.BLUETOOTH_PANU_CONNECTING) )
		{
			// disconnect PAN  
			mLogger.i("Disconnect PANu " + mDevaddr);
			btPanu.disconnect(mDevaddr);   
		}
	}
    
	private void updateDataplanUsage(String deviceAddress, Boolean useDataPlan)
	{
		if (useDataPlan)
		{
			//Enable
			if (isDevicePaired(deviceAddress))
			{
				if (!DevicesPanEnabled.contains(deviceAddress))
				{
					DevicesPanEnabled.add(deviceAddress);
					savePanEnabledDeviceList();
				}
				if ( (mConnectedDevice > -1) && (deviceAddress.equals(DevicesAddr[mConnectedDevice])) &&
						((btPanu.getConnectionState(deviceAddress) == BluetoothPanu.BLUETOOTH_PANU_DISCONNECTED)) )
				{
        			mLogger.i("connect PANU " + deviceAddress);
        			addDeviceService(deviceAddress, SRV_PAN);
         			btPanu.connect(deviceAddress, BluetoothPanu.NAP_ROLE) ; //Connect PANU
				}
				mLogger.i("Enabled for PAN:" + deviceAddress);
			}
			else
			{
				mLogger.w("Device not paired! Can't enable for PAN:" + deviceAddress);
			}
		}
		else
		{
			//Disable
			if (DevicesPanEnabled.contains(deviceAddress))
			{
				DevicesPanEnabled.remove(deviceAddress);
				savePanEnabledDeviceList();
			}
			if ( (mConnectedDevice > -1) && (deviceAddress.equals(DevicesAddr[mConnectedDevice])) &&
					((btPanu.getConnectionState(deviceAddress) == BluetoothPanu.BLUETOOTH_PANU_CONNECTED)) )
			{
    			mLogger.i("disconnect PANU " + deviceAddress);
     			btPanu.disconnect(deviceAddress) ; //Disconnect PANU
			}
			mLogger.i("Disabled for PAN:" + deviceAddress);
		}
	}

	private void restorePanEnabled() {
	    // Restore - PAN enabled devices.
	    int i;
		String connections = readFile(FILE_NAME_PAN);
		DevicesPanEnabled.clear();
		int len = connections.length();
		for (i = 0; (i < MAX_PAIRED_DEVICES) && (len >= BLUETOOTH_ADDR_SIZE); i++) {
			DevicesPanEnabled.add(i,connections.substring(i*BLUETOOTH_ADDR_SIZE,(i+1)*BLUETOOTH_ADDR_SIZE));
		    print("DevicesPanEnabled: " + DevicesPanEnabled.get(i));
		    len -= BLUETOOTH_ADDR_SIZE;
		}
	    print("size of DevicesPanEnabled: " + DevicesPanEnabled.size());
	}
	
	private void restoreRecentConnected() {
	    // Restore - recent connected devices.
	    mTryConnectDevice = -1;
	    mConnectedDevice = -1;
	    int i;
		String connections = readFile(FILE_NAME_BT_DEV);
		DevicesRecentConnectedAddr.clear();
		int len = connections.length();
		for (i = 0; (i < MAX_PAIRED_DEVICES) && (len >= BLUETOOTH_ADDR_SIZE); i++) {
		    DevicesRecentConnectedAddr.add(i,connections.substring(i*BLUETOOTH_ADDR_SIZE,(i+1)*BLUETOOTH_ADDR_SIZE));
		    print("DevicesRecentConnectedAddr: " + DevicesRecentConnectedAddr.get(i));
		    len -= BLUETOOTH_ADDR_SIZE;
		}
	    print("size of DevicesRecentConnectedAddr: " + DevicesRecentConnectedAddr.size());
	}
	
	private int removeUnpairedDevices() {
		if (getPairedDevices() != 0)
		{				 
			// remove all unpaired devices.
			ArrayList<String> tmpDevicesAddr = new ArrayList<String>(DevicesRecentConnectedAddr);
		    print("size of DevicesRecentConnectedAddr: " + DevicesRecentConnectedAddr.size());
		    print("size of tmpDevicesAddr: " + tmpDevicesAddr.size());
			DevicesRecentConnectedAddr.clear();
			DevicesTryConnectedAddr.clear();
			for (String addr : tmpDevicesAddr) {
				if (isDevicePaired(addr)) {
					if (!DevicesRecentConnectedAddr.contains(addr)) {
						DevicesRecentConnectedAddr.add(addr);
						DevicesTryConnectedAddr.add(addr);
						mLogger.i( "DevicesRecentConnectedAddr: " + addr);
					}
					else
					{
						mLogger.e( "Already included in DevicesRecentConnectedAddr: " + addr);
					}
				}
			}
		}
		else
		{
			DevicesRecentConnectedAddr.clear();
			DevicesTryConnectedAddr.clear();
		}
		return DevicesRecentConnectedAddr.size();
	}
	
	private void tryHFPConnect(boolean verbose) {
		int DeviceId;
		int disabledServices = mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0);
		if ( (mConnectedDevice == -1) && (ActivityState == BLUETOOTH_ACTIVITY_IDLE) && (ignitionState == IgnigtionState.IGNITION_ON) && !mDeleteAllDevices &&
				(((disabledServices & DISABLE_SRV_HFP) == 0) || ((disabledServices & DISABLE_SRV_SPP) == 0)) )
		{
			mConnectVerbose = false;
			for (mTryConnectDevice = 0; mTryConnectDevice < DevicesTryConnectedAddr.size(); mTryConnectDevice++)
			{
				DeviceId = getPairedDeviceId(DevicesTryConnectedAddr.get(mTryConnectDevice));
				if ( (DeviceId > -1 )  && (DevicesType[DeviceId] == TYPE_MOBILE) )
				{
					if ((disabledServices & DISABLE_SRV_HFP) == 0)
					{
						mLogger.i( "Start HFP connect: " + DevicesAddr[DeviceId] + "," + DevicesName[DeviceId]);
						if (verbose) mConnectVerbose = true;
						connectRetries = BLUETOOTH_CONNECT_RETRIES;
						addDeviceService(DevicesAddr[DeviceId], SRV_HFP);
						btHFP.connect(DevicesAddr[DeviceId]);
						ActivityState = BLUETOOTH_ACTIVITY_WAIT_CONNECT;
						return;
					}
					else
					{
						mLogger.i( "Start iAP SPP connect: " + DevicesAddr[DeviceId] + "," + DevicesName[DeviceId]);
						if (verbose) mConnectVerbose = true;
	     				RoadTrackSPP.getInstance().connectAsClientToSPP(DevicesAddr[DeviceId]);
	     				return;
					}
					
				}
			}
			if (ActivityState == BLUETOOTH_ACTIVITY_IDLE)
			{
				sendBluetoothConnectionFail();
				if (verbose)
				{
					String prompt = mStrings.getExpandedStringById( StringReader.Strings.PROMPTID_BT_FAILED_TO_CONNECT );
					String promptForDisplay = mStrings.getStringByIdForDisplay( StringReader.Strings.PROMPTID_BT_FAILED_TO_CONNECT );
					// This tts is relevant until it can be heard to notify about the process failure.
					mTts.speak(prompt, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
							TextToSpeech.TTS_RELEVANCE_ETERNAL, promptForDisplay, true, HmiDisplayService.DISPLAY_TEXT_MESSAGE );
				}
			}
		}
	}

    private void setBTName() {
	    myBTName = mPconf.GetTextParam(PConfParameters.BT_Name, BT_NAME_NOT_FOUND);
	    print("BTName: " + myBTName);
	    if (!myBTName.equals(BT_NAME_NOT_FOUND))
	    {
	    	print("setting local BT Name: " + myBTName);
	    	mBTAdapter.setName(myBTName);
	    }
    }
    
    private void getMaxPairing() {
    	maxPairing = mPconf.GetIntParam(PConfParameters.BT_MaxPairing, -1);
    	if ( (maxPairing < 0) || (maxPairing > MAX_PAIRED_DEVICES) ) {
    		maxPairing = MAX_AllOWED_PAIRED_DEVICES;
    	}
    }

    private boolean getPBSyncEnabled() {
    	int	value = mPconf.GetIntParam(PConfParameters.BT_PhonebookSynchronizationEnabled, -1);
    	if (value == 0)
    		return false;
    	else
    		return true;
    }
    
    private boolean getBTRadioEnabled() {
    	int	value = mPconf.GetIntParam(PConfParameters.BT_RadioEnabled, -1);
    	if (value != 0)
    		return true;
    	else
    		return false;
    }
    
    private void readBTPairingCode() {
	    myBTPairingCode = mPconf.GetTextParam(PConfParameters.BT_PairingCode, BT_PAIRING_CODE_NOT_FOUND);
	    if (myBTPairingCode.equals(BT_PAIRING_CODE_NOT_FOUND)) {
	    	print("using default pairing code");
	    	myBTPairingCode = BT_DEFAULT_PAIRING_CODE;
	    }
    }

    public String getBTPairingCode() {
    	return myBTPairingCode;
    }
    
    public String getBTPairingName() {
    	return myBTName;
    }

    private void unpairOneDevice() {
  		int Id;
    	while (DevicesRecentConnectedAddr.size() > 0) { 
    		Id = getPairedDeviceId(DevicesRecentConnectedAddr.get(DevicesRecentConnectedAddr.size()-1));
			if (Id > -1){
				BTDevicesArray[Id].removeBond();
			 	DevicesRecentConnectedAddr.remove(DevicesRecentConnectedAddr.size()-1);
			 	return;
			}
			else
			{
     	   		mLogger.i("Too many paired devices and least recent connected is not paired" + DevicesRecentConnectedAddr.get(DevicesRecentConnectedAddr.size()-1));
			 	DevicesRecentConnectedAddr.remove(DevicesRecentConnectedAddr.size()-1);
		 	}
		}
  		// no recent connected devices. just remove the first device paired
    	if (mPairedDevices > 0) {
    		BTDevicesArray[0].removeBond();
	 	}
    }  
 
	private int getDeviceType(BluetoothDevice device)
	{
		BluetoothClass btClass = device.getBluetoothClass();
		if(btClass != null)
		{
			switch(btClass.getMajorDeviceClass())
			{
			case BluetoothClass.Device.Major.PHONE:
				return TYPE_MOBILE;
			case BluetoothClass.Device.Major.COMPUTER:
				return TYPE_COMPUTER;
			}
			
			 if(btClass.doesClassMatch(android.bluetooth.BluetoothClass.PROFILE_A2DP))
				return TYPE_HEADPHONE;
			else
			if(btClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET))
				return TYPE_HEADSET;	
		}
		return 0;
		
	}
	
	private int getPairedDevices()
	{
		int i = 0;;	
		Set<BluetoothDevice> pairedDevices = mBTAdapter.getBondedDevices();  
		if ( pairedDevices != null && pairedDevices.size() > 0 ) {
	    		mLogger.i( "Paired devices:");		       
		        for (BluetoothDevice device : pairedDevices) {
		        	DevicesName[i] = device.getName();
		        	DevicesAddr[i] = device.getAddress();
		        	DevicesType[i] = getDeviceType(device);
		        	mLogger.i( DevicesName[i] + " " + DevicesAddr[i]);
		        	i++;
		        }
		        pairedDevices.toArray(BTDevicesArray);
		        mPairedDevices = i;
		        return mPairedDevices;
		}
	    else
	    {
	    	mLogger.i( "There are no paired devices!");
	    	mPairedDevices = 0;
	    	return 0;
	    }
	}
	
	private int CheckAllDevicesDisconnected()
	{
       	int i;
       	int CurrentStatus;
       	
    	for (i = 0; i < mPairedDevices; i++) {
            CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[i]);
    		if ( CurrentStatus != BluetoothHFP.HFP_DISCONNECTED) {
    			print("Address:" + DevicesAddr[i] + " Status:" + CurrentStatus);
    			return i;
    		}
    	}
    	return -1;
	    
	}
	
	private int isDeviceConnected()
	{
       	int i;
       	int CurrentStatus;
       	
    	for (i = 0; i < mPairedDevices; i++) {
            CurrentStatus = btHFP.getCurrentConnectionStatus(DevicesAddr[i]);
    		if ( CurrentStatus == BluetoothHFP.HFP_CONNECTED) {
    			return i;
    		}
    	}
    	return -1;
	    
	}
	
	private int getPairedDeviceId(String mDevaddr)
	{
       	int i;
       	if (mPairedDevices == 0)
       		return -1;
    	for (i = 0; i < mPairedDevices; i++) {
    		if (mDevaddr != null) {
    			if ( mDevaddr.equals(DevicesAddr[i]) ) {
    				return i;
    			}
    		}
    	}
    	return -1;   
	}
	
	private void insertConnectedDevice(String mDevaddr) {
        if (!DevicesRecentConnectedAddr.isEmpty() && DevicesRecentConnectedAddr.get( 0 ).equals( mDevaddr ) ) {
            return;// nothing to do already at head
        }
        DevicesRecentConnectedAddr.remove( mDevaddr );
        DevicesRecentConnectedAddr.add( 0, mDevaddr );
        for ( String addr : DevicesRecentConnectedAddr ) {
        	mLogger.i("DevicesRecentConnectedAddr: " + addr );
        }
        saveRecentConnectedDevicesList();
	}

	private void saveRecentConnectedDevicesList()
	{
       	String saveString = "";
		for (String addr :  DevicesRecentConnectedAddr) {
			saveString += addr;
		}
		writeFile(saveString,FILE_NAME_BT_DEV);
//		readFile(FILE_NAME_BT_DEV);      
	}
	
	private void savePanEnabledDeviceList()
	{
       	String saveString = "";
		for (String addr : DevicesPanEnabled) {
			saveString += addr;
		}
		writeFile(saveString,FILE_NAME_PAN);
		readFile(FILE_NAME_PAN);      
	}
	
	private boolean isDevicePaired(String mDevaddr)
	{
		if (getPairedDeviceId(mDevaddr) > -1 )
			return true;
		else
			return false;
	}

	private ClearUserDataObserver mClearDataObserver;	

	class ClearUserDataObserver extends IPackageDataObserver.Stub {
		public void onRemoveCompleted(final String packageName, final boolean succeeded) {
		}
	}
	
	public  void ClearUserData() {
		       
		// Invoke uninstall or clear user data based on sysPackage
	        
		String packageName = "com.android.providers.contacts";
		mLogger.i( "Clearing user data for package : " + packageName);
		if (mClearDataObserver == null) {
			mClearDataObserver = new ClearUserDataObserver();
		}
		ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
		boolean res = am.clearApplicationUserData(packageName, mClearDataObserver);
		if (!res) {
			// Clearing data failed for some obscure reason. Just log error for now
			mLogger.w( "failed clear application user data for package:"+packageName);
		}
		else {
			mLogger.d( "successful clear application user data for package:"+packageName);
		}
	}
	
    private void writeFile(String string,String fileName) {
       File dir = new File(DATA_DIR+ "/" + SUB_DATA_DIR);
       if (!dir.isDirectory()) {
    	   if (!dir.mkdir()) {
    		   mLogger.e( "directory make failed");
    		   return;
    	   }
       }
       File file = new File(Environment.getDataDirectory(), SUB_DATA_DIR + "/" + fileName/*FILE_NAME_BT_DEV*/);
       FileOutputStream fos = null;  
       try {
    	   fos = new FileOutputStream(file);
           fos.write(string.getBytes());
       } catch (IOException e) {
    	   mLogger.e( "File write: " + fileName + e.toString());
       } finally {
    	   try {
    		   if (fos != null) fos.close();
    	   } catch (IOException e) {
    		   mLogger.e( "File close: " + fileName + e.toString());
    	   }
       }
    }

    private String readFile(String fileName) {
    	char [] tmp = new char[(MAX_PAIRED_DEVICES*BLUETOOTH_ADDR_SIZE) + 1];
    	int i = 0;
    	tmp[0] = '\0';
    	File file = new File (DATA_DIR + "/" + SUB_DATA_DIR + "/" + fileName);
    	FileInputStream fis = null;
    	if (file.exists())
    	{
    		try {
    			fis = new FileInputStream(file);
    			int content;
    			while ( i < (MAX_PAIRED_DEVICES*BLUETOOTH_ADDR_SIZE)) {
    				content = fis.read();
    				if (((char)content != ':') && (((char)content > 'F') || ((char)content < 'A')) 
    						&& (((char)content > '9') || ((char)content < '0')))
    				break;
    				tmp[i] = (char) content;
    				i++;
    			}
    			tmp[i] = '\0';
    			char [] addr = new char[i];
    		} catch (IOException e) {
    			mLogger.i( "File read failed: " + e.toString());
    		}
    		finally {
    			try{
    				if(fis != null) {
    					fis.close();	
    				}
    			}
    			catch (IOException e) {
    				mLogger.e( "Flush not closed on exit");
    			}
    		}
    	}
    	String string = new String(tmp,0,i);
    	print("File read: " + string );
    	return string;
    }
    
    private void NotifyClients( int contactStatus, String address, boolean change)
    {
        Intent intent;
        intent = new Intent(CONTACTS_SYNC_UPDATE_INTENT);
        intent.putExtra(CONTACTS_SYNC_STATUS_EXTRA, contactStatus);
        intent.putExtra(CONTACTS_SYNC_ADDRESS_EXTRA, address);
        if (contactStatus == CONTACTS_STATUS_FINAL_AVAIALABLE)
        {
        	if (change)
        	{
        		intent.putExtra(CONTACTS_SYNC_CHANGE_EXTRA, 1);
        	}
        	else
        	{
        		intent.putExtra(CONTACTS_SYNC_CHANGE_EXTRA, 0);
        	}
        }
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcast(intent);
        if (contactStatus == CONTACTS_STATUS_FINAL_AVAIALABLE)
        	mLogger.i( "CONTACTS_SYNC_UPDATE: " + contactStatus + "," + change);
        else
           	mLogger.i( "CONTACTS_SYNC_UPDATE: " + contactStatus );
    }
   
    private void NotifyClients( int bluetoothStatus)
    {
        Intent intent;
        intent = new Intent(BLUETOOTH_STATUS_INTENT);
        intent.putExtra(BLUETOOTH_STATUS_EXTRA, bluetoothStatus);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcast(intent);
        mLogger.i( "BLUETOOTH_STATUS: " + bluetoothStatus );
    }

	// This function sets the A2DP audio channel according to the profile. If the MP3 should go through 
	// FM, then A2DP is configured to send audio directly to the FM instead of the audio flinger.
    private void updateMusicStreamingChannel() {

		// 4 is for FM.
        if ( mPconf.GetIntParam( PConfParameters.ACM_AudioPath_Mp3, 0 ) == 4 ) {
            // stream a2dp directly through FM modulator
            mLogger.i(  "A2DP audio will be sent directly to FM transmitter" );
            btA2dp.setA2dpAudioPath(1);
        } else {
            // Stream a2dp through the audio flinger
            mLogger.i(  "A2DP audio will be sent through Audio Flinger" );
            btA2dp.setA2dpAudioPath(0);
        }
    }


private class recoveryBluetooth extends Thread {

    public static final String BT_ON_PATH = "/sys/devices/platform/bt-codec/power/bluetooth_on";
    private static final int MAX_NUMBER_POLLING = 64;

    private recoveryBluetooth(){
    }

    @Override
    public void run() {
			mLogger.w( "recoveryBluetooth running");

			int count = 0;
			int bluetoothStateValue = BluetoothAdapter.STATE_OFF;

			final IBluetooth bluetooth = IBluetooth.Stub.asInterface(ServiceManager.checkService(
                               BluetoothAdapter.BLUETOOTH_SERVICE));

			mBluetoothRecoveryInProgress = true;
			while ( bootCompleted ){
				/*try to turn off bluetooth*/
				try{
				bluetoothStateValue = bluetooth.getBluetoothState();
				} catch (RemoteException ex) {
				mLogger.e( "RemoteException during getBluetoothState", ex);
				}

               	if(bluetoothStateValue == BluetoothAdapter.STATE_ON){
					mLogger.w( "[recoveryBluetooth] Disabling Bluetooth...");
					try{
						bluetooth.disable(false);  // disable but don't persist new state
					} catch (RemoteException ex) {
						mLogger.e( "RemoteException during bluetooth disable", ex);
					}
                }

				mLogger.w( "[recoveryBluetooth] Wait MAX 32 seconds for bluetooth off");
				// Wait a max of 32 seconds for clean shutdown, 500ms * 64 = 32 seconds
				count = 0;
				for (count = 0; count < MAX_NUMBER_POLLING; count++) {
					try{
						bluetoothStateValue = bluetooth.getBluetoothState();
					} catch (RemoteException ex) {
						mLogger.e( "RemoteException during getBluetoothState", ex);
					}
					if (bluetoothStateValue == BluetoothAdapter.STATE_OFF) {
						mLogger.i( "Bluetooth shutdown complete.count = " + count);
						//if the Bluetooth off take less  than 1 seconds. Sleep one more second
						if(count < 2){
							//sleep 1 seconds to let bluetooth uplayer handle state off intent.
							SystemClock.sleep(1000);
						}
						break;
					}
					SystemClock.sleep(500);
               	}

               	if (!bootCompleted){
               		mLogger.i( "[recoveryBluetooth] stop due to boot not completed.");
               		return;
               	}
				mLogger.w( "[recoveryBluetooth] begin to wait synergy stack stop");
				SystemProperties.set("ctl.start", "stop_synergy");
				String synergyStackReady;
				count = 0;
               //waiting for CsrSynergy.stack.ready turn to 'no' in 2 seconds
               	do {
					synergyStackReady = SystemProperties.get("CsrSynergy.stack.ready");
					SystemClock.sleep(500);
					count ++;
               	} while ( bootCompleted && (!synergyStackReady.equals("no") && (count < (4))));
				//put synergy stack ready  to 'no' no matter it was done correct or not.
				SystemProperties.set("CsrSynergy.stack.ready", "no");
				synergyStackReady = SystemProperties.get("CsrSynergy.stack.ready");
				mLogger.w( "[recoveryBluetooth] synergy stack has stopped, count =" + count + " synergyStackReady = " + synergyStackReady);

              	if (!bootCompleted){
               		mLogger.i( "[recoveryBluetooth] stop due to boot not completed.");
               		return;
               	}
				try {
					FileWriter fw = new FileWriter(BT_ON_PATH);
					try {
						fw.write("0\n");
						SystemClock.sleep(500);
						fw.write("1\n");
					} catch (IOException e) {
						mLogger.e( "Error writing bt_on to 0");
					} finally {
						if (fw != null) {
							try {
								fw.close();
							} catch (Exception e) {}
						}
					}
				} catch (IOException e) {
					mLogger.e( "Error create BT_ON_PATH");
				}

				SystemProperties.set("ctl.start", "synergy");

				mLogger.w( "[recoveryBluetooth] begin wait CsrSynergy.stack.ready to yes");
				//waiting for synergy stack ready
				count = 0;
				do {
					SystemClock.sleep(500);
					synergyStackReady = SystemProperties.get("CsrSynergy.stack.ready");
					count ++;
					mLogger.i( "CsrSynergy.stack.ready is " + synergyStackReady + " count=" + count);
				} while ( bootCompleted && (!synergyStackReady.equals("yes") && (count < MAX_NUMBER_POLLING)));
              	if (!bootCompleted){
               		mLogger.i( "[recoveryBluetooth] stop due to boot not completed.");
               		return;
               	}
				synergyStackReady = SystemProperties.get("CsrSynergy.stack.ready");
				mLogger.w( "[recoveryBluetooth] count =" + count + " CsrSynergy.stack.ready = " + synergyStackReady);

				mLogger.w( "[recoveryBluetooth] enable bluetooth");
				try{
					bluetooth.enable();
				} catch (RemoteException ex) {
						mLogger.e( "RemoteException during bluetooth disable", ex);
				}

                // Wait a max of 32 seconds for Bluetooth recovery, 500ms * 64 = 32 seconds
                count = 0;
                bluetoothStateValue = BluetoothAdapter.STATE_OFF;
                for (count = 0; count < MAX_NUMBER_POLLING && bootCompleted; count++) {
					try{
						bluetoothStateValue = bluetooth.getBluetoothState();
					} catch (RemoteException ex) {
						mLogger.e( "RemoteException during getBluetoothState", ex);
					}
					if (bluetoothStateValue == BluetoothAdapter.STATE_ON) {
						mLogger.i( "Bluetooth on complete.count = " + count);
						break;
					}
					SystemClock.sleep(500);
	            }
              	if (!bootCompleted){
               		mLogger.i( "[recoveryBluetooth] stop due to boot not completed.");
               		return;
               	}
                if(bluetoothStateValue == BluetoothAdapter.STATE_ON){
                    break;
                }else{
                    mLogger.w("Bluetooth recovery failed. Retry");
                }
            }
            mBluetoothRecoveryInProgress = false;
      	}
    }

}
