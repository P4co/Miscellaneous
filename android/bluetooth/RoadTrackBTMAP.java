import java.util.*;
import java.util.concurrent.Semaphore;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothMapClient.BluetoothMapClientIntent;
import android.bluetooth.BluetoothHFP;
//import android.os.Binder;
import android.os.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.speech.tts.*;
import android.roadtrack.stringservice.StringReader;

import com.roadtrack.hmi.HmiDisplayService;
import com.roadtrack.util.RTLog;

public class RoadTrackBTMAP extends Binder 
{
	public interface IMapEventReceiver
	{
		public void onPhoneConnected( int unreadMessageCount );
		public void onNewMessageArrived(String sender);
		public void onPhoneDisconnected();
	}
	
	public class TextMessage {
	    // Message identification in the system
	    public String Id;

	    // Sender/recipient phone number
	    public String Number;

	    // Sender contact name (if in address book)
	    public String ContactName;
	    
	    // Subject
	    public String Subject;

	    // Message text
	    public String MessageBody;
	}
	
	
	
	private static TextMessage newUnreadMessage;
	protected static String clientMessageId;
	private static TextMessage newReadMessage;
	private boolean isJustConnected=false;
	private boolean isNewMessageArrived=false;
	private boolean pushResult=false;
	private int messagesCount=0;
	private static final int LIST_MESSAGE_COUNT = 10;
	private List<String> pushedMessages = new ArrayList<String>();
	private String mDevaddr = "";
	
	private static final Semaphore available = new Semaphore(1, true);
	private static final Semaphore sendSMS = new Semaphore(0, true);
	
    // for speaking TTS
	private TextToSpeech mTts;
	private StringReader mStrings;
	
	///////////////////////////////////
	// Public Event
	//////////////////////////////////
    private ArrayList<IMapEventReceiver> mapEventReceivers = new ArrayList<IMapEventReceiver>();
    
    public void registerEventHandler(IMapEventReceiver receiver){
    	mLogger.i( "Listener added");
    	mapEventReceivers.add(receiver);}
    
    private static Context mapContext;
	private static final String TAG = "RoadTrackBTMapService";
	public static volatile byte InstanceId = -1;
	public static String mapSMSInbox = "inbox";
	public static String mapSMSOutbox = "outbox";
   	private static boolean inboxAvailable = false;
  	private static boolean outboxAvailable = false;
	private BluetoothHFP btHFP = new BluetoothHFP();
  	private static RTLog mLogger  = RTLog.getLogger(TAG, "RTBTMAP", RTLog.LogLevel.INFO );
	private Thread  mMAPThread;
	private Handler mHandler;
  	
  	// Message type filter
  	private static final byte MAP_FILTER_NONE = 		0x0;
  	private static final byte MAP_FILTER_SMS_GSM = 	0x1;
  	private static final byte MAP_FILTER_SMS_CDMA = 	0x2;
  	private static final byte MAP_FILTER_EMAIL = 		0x4;
  	private static final byte MAP_FILTER_MMS = 		0x8;
  	
  	// Message read status filter
 	public static final byte MAP_GET_ALL = 			0x0;
  	public static final byte MAP_GET_UNREAD_ONLY =	0x1;
  	public static final byte MAP_GET_READ_ONLY = 	 	0x2;
  	
  	// Message include attachment
    public static final boolean MAP_WITHOUT_ATTACHMENT = false;
    public static final boolean MAP_WITH_ATTACHMENT = 	true;
  	
 	// Message fraction request
  	public static final byte MAP_FRACTION_FIRST = 	0x0;
  	public static final byte MAP_FRACTION_NEXT = 		0x1;
  	
	// Message keep sent (transparent)
  	public static final boolean MAP_KEEP_SENT = 		  false;
  	public static final boolean MAP_DO_NOT_KEEP_SENT = true;
  	
	// Message retry send
  	public static final boolean MAP_DO_NOT_RETRY_SEND = 	false;
  	public static final boolean MAP_RETRY_SEND = 		true;
  	
  	// bit values for message fields
  	private static final int MAP_MSG_SUBJECT = 			0x1;
 	private static final int MAP_MSG_DATETIME = 			0x2;
 	private static final int MAP_MSG_SENDER_NAME = 		0x4;
 	private static final int MAP_MSG_SENDER_ADDR = 		0x8;
 	private static final int MAP_MSG_RECIPIENT_NAME = 	0x10;
	private static final int MAP_MSG_RECIPIENT_ADDR = 	0x20;
	private static final int MAP_MSG_TYPE = 				0x40;
	private static final int MAP_MSG_SIZE = 				0x80;
	private static final int MAP_MSG_RECEPTION_STATUS = 	0x100;
	private static final int MAP_MSG_TEXT = 				0x200;
	private static final int MAP_ATTACHMENT_SIZE = 		0x400;
	private static final int MAP_MSG_PRIORITY = 			0x800;
	private static final int MAP_MSG_READ_STATUS = 		0x1000;
	private static final int MAP_MSG_SENT_STATUS = 		0x2000;
	private static final int MAP_MSG_PROTECTED =			0x4000;
	private static final int MAP_MSG_REPLYTO_ADDR =		0x8000;
	
	// Event Types of EventHappened
    public static final int EVENT_TYPE_CONNECTION  = 0;
    public static final int EVENT_TYPE_NEW_MESSAGES = 1;	

    //looper messages
	private static final int MSG_SMS_PUSHED = 1;
	private static final int MSG_MAPC_GET_MSG_CFM = 2;
	
	private static final int MSG_SMS_PUSHED_DELAY = 20000;
	
    private static RoadTrackBTMAP btMAP=null;
    
    public static RoadTrackBTMAP getInstance()
    {
    	return btMAP;
    }
    
    public static void clearSupportMessageInstance() {
    	InstanceId = -1;
    }
    
	public RoadTrackBTMAP(Context context) {
        super();
        if (btMAP!=null) {throw new RuntimeException("You cannot create a new instance of this class");}

        
        mapContext = context;
        btMAP = this;
        
	 	mLogger.i( "Server Service Created");
    	// register for MAP events
        IntentFilter mapFilter = new IntentFilter();      
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_INSTANCE_IND);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_OPERATOR_GENERIC_CFM);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_EVENT_IND);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_GET_FOLDER_LIST_CFM);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_GET_MSG_LIST_CFM);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_GET_MSG_CFM);
        mapFilter.addAction(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_PUSH_MSG_CFM);
        mapContext.registerReceiver(mMapBroadcastReceiver, mapFilter);
        
		mTts = new TextToSpeech( mapContext, null );
		mStrings = (StringReader)mapContext.getSystemService(mapContext.STRING_SYSTEM_SERVICE);
		
		/**
	     * handles received messages in it's own thread. Messages are sent localy to looper to track sending of SMS messages
	     * pushed to local mobile.
	     * 
	     */
		mMAPThread = new Thread( new Runnable() {
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
		}, "MAPThread");
	
		mMAPThread.start();
        
        // START TESTER
        //t.start(); 
	}
	
	private void messageHandler( Message msg ) 
	{
		String handle;

		switch ( msg.what ) {
			case MSG_SMS_PUSHED:
				handle = (String) msg.obj;
//				mLogger.d("msg: MSG_SMS_PUSHED" + " handle:" + handle);
    			if (pushedMessages.contains(handle))
    			{
            		pushedMessages.remove(handle);
            		String ttsSend = mStrings.getExpandedStringById( StringReader.Strings.TTS_MOBILE_DIDNT_SEND_SMS_YET );
            		// This tts might be irrelevant after some time as sms might have been sent already.
 					mTts.speak(ttsSend, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
 							TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
 				
    			}
				break;
			case MSG_MAPC_GET_MSG_CFM:
				/*
				 * SMS body received from phone
				 */
				if (newUnreadMessage != null)
					newUnreadMessage.MessageBody = checkUtfString((String) msg.obj);
				mLogger.d("msg: MSG_MAPC_GET_MSG_CFM" + " MessageBody:" + newUnreadMessage.MessageBody);

				if (newUnreadMessage != null && !newUnreadMessage.MessageBody.isEmpty())
            	{
            		 //possible need iPhone workaround. Remove "From:" from message body
            		 String fromContact = "From: ";
            		 String sCRLF = "\r\n";
            		 int indxOfCRLF = newUnreadMessage.MessageBody.indexOf(sCRLF);
            		 if ( (newUnreadMessage.MessageBody.startsWith(fromContact)) && (indxOfCRLF > fromContact.length()) )
            		 {
            			 String bodyContact = newUnreadMessage.MessageBody.substring(fromContact.length(),indxOfCRLF);
            			 bodyContact = bodyContact.replaceAll(" ", "");
            			 String contactTmp = newUnreadMessage.Number.replaceAll(" ", "");
            			 if (bodyContact.equals(contactTmp))
            			 {
            				 newUnreadMessage.MessageBody = newUnreadMessage.MessageBody.substring(indxOfCRLF+2);
            				 mLogger.i( "Updated Message Body: " + newUnreadMessage.MessageBody);
            			 }
            		 }
            	}
				if ((newUnreadMessage.ContactName != null) && (!newUnreadMessage.ContactName.isEmpty()))
				{
						Pattern num_pattern = Pattern.compile("[+\\-#0-9]+");
						Matcher matcher = num_pattern.matcher(newUnreadMessage.ContactName);
						if(matcher.matches()) {
							newUnreadMessage.ContactName = null;
						}
				}
				if ( (newUnreadMessage.Number != null) && ((newUnreadMessage.ContactName == null) || newUnreadMessage.ContactName.isEmpty()) )
        		{
        			 // try to append name from cache
        			 try {
        				 mLogger.d( "cache lookup" );
        				 BTContactsCache.CacheContactEntry contactEntry = 
        						 BTContactsCache.getInstance().getContactCacheName(newUnreadMessage.Number);
        				 if (contactEntry != null) {
        					 // caller's name information vailable
        					 mLogger.d("from cache, number: " +   contactEntry.phoneNumbers.get(0).number
        							 + " name:" + contactEntry.firstname + " " + contactEntry.midname + " " +  contactEntry.lastname);
        					 String contactName = buildContactName(contactEntry.firstname, contactEntry.midname, contactEntry.lastname );
        					 if (contactName != null) {
        						 newUnreadMessage.ContactName = contactName;
        					 }
        				 }
        			 } catch( Exception e ) {
        				 mLogger.e( "cache lookup thread failed: ", e );
        			 }
        		}
				//mLogger.d( "Trying to update message status: InstID:" + InstanceId + " MessageId:" + newUnreadMessage.Id + " Status:" + BluetoothMapClient.MAPC_MESSAGE_STATUS_READ);
            	 
            	// Update message status as unread
            	//BTDevicesService.mBluetoothMapClient.setMessageStatus(InstanceId, newUnreadMessage.Id, BluetoothMapClient.MAPC_MESSAGE_STATUS_READ);
            	 
            	//mLogger.e( "Releasing availability locker");
            	available.release();
            	 
            	if (isJustConnected)
            	{
             		//mLogger.d( "Fire event OnConnected. Message Count:" + messagesCount);
             		
             		// Fire event Connected 
                 	for (IMapEventReceiver cl : mapEventReceivers)
                 		cl.onPhoneConnected(messagesCount);
             		
            	}
            	isJustConnected=false;
             	
            	if (isNewMessageArrived)
            	{
             		//mLogger.d( "Fire event OnNewNessageArrived.");
             		
             		// Fire event Connected 
                 	for (IMapEventReceiver cl : mapEventReceivers)
                 		if (     ( newUnreadMessage.ContactName != null ) && 
                                ( !newUnreadMessage.ContactName.isEmpty() ) )
                 			cl.onNewMessageArrived(newUnreadMessage.ContactName);
                 		else
                 			cl.onNewMessageArrived(newUnreadMessage.Number);
            	}
            	isNewMessageArrived=false;
            	break;
			default:
				mLogger.w(  "messageHandler:unknown msg " + msg.what);				
				break;
		}
	}
	
	// check for MAP connection status events
    private BroadcastReceiver mMapBroadcastReceiver = new BroadcastReceiver() 
    {        
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	int mInstanceId, supportedMsg;
            int mStatus;
            String action = intent.getAction();
  
            mLogger.i( "Event Received: " + action);
            try{
            	if (action.equals(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE)) 
            		action=BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE;
            }
            catch(Exception ex)
            {
            	 mLogger.i( "Exception in BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE");
            }
            
            if (action.equals(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE)) 
            {
            	//mLogger.d("BLUETOOTH_MAPC_CONNECTION_STATUS_CHANGE");
            	mDevaddr = intent.getStringExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_ADDRESS);
            	mInstanceId = intent.getIntExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE,-1);
            	mStatus = intent.getIntExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_CONNECTION_STATUS,BluetoothMapClient.BLUETOOTH_MAPC_DISCONNECTED);
                switch(mStatus) {
            		case BluetoothMapClient.BLUETOOTH_MAPC_CONNECTED:
                    	mLogger.i("BLUETOOTH_MAPC_CONNECTED " + mDevaddr + " InstanceId:" + Integer.toString(mInstanceId));
                    	if (InstanceId == (byte)mInstanceId ) {
                    		// request folders list
               				pushedMessages.clear();
                    		BTDevicesService.mBluetoothMapClient.getFolderList(InstanceId, 0, 10);
                    		isJustConnected=true;
                    	}
            			break;
               		case BluetoothMapClient.BLUETOOTH_MAPC_DISCONNECTED:
                    	mLogger.i("BLUETOOTH_MAPC_DISCONNECTED " + mDevaddr);
                    	mDevaddr = "";
                    	for (IMapEventReceiver cl : mapEventReceivers)
                    		cl.onPhoneDisconnected();
                    	InstanceId = -1;
           				mHandler.removeMessages(MSG_SMS_PUSHED);
           				pushedMessages.clear();
           				pushResult = false;
           				sendSMS.release();
                		break;
                	default:
                	break;
                }
            }
            else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_INSTANCE_IND)) 
            {
            	
                mInstanceId = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE, -1);
                supportedMsg = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_SUPPORT_MESSAGE, 0);

                mLogger.i( "instanceId:" + Integer.toString(mInstanceId) + " " + "supportMsg:" + Integer.toString(supportedMsg));
                if ((supportedMsg & (BluetoothMapClient.BLUETOOTH_MAPC_SUPPORT_SMS_GSM_MASK | BluetoothMapClient.BLUETOOTH_MAPC_SUPPORT_SMS_CDMA_MASK)) != 0) {
                    // SMS
                	//mLogger.d("InstanceId:" + InstanceId);
                    if (InstanceId == -1)
                    {
                        int HFPConnectionId = btHFP.getConnectedHandsfreeDevice();
                        if (HFPConnectionId != BluetoothHFP.HFP_INVALID_CONNECTION_ID)
                        {
                        	InstanceId = (byte) mInstanceId;  	
                        	BTDevicesService.mBluetoothMapClient.selectInstance(InstanceId, true);
                        }
                        else
                        {
                        	byte InstanceTempId = (byte) mInstanceId;
                        	BTDevicesService.mBluetoothMapClient.selectInstance(InstanceTempId, false);
                        }
                    }
                    else
                    {
                    	byte InstanceTempId = (byte) mInstanceId;
                    	BTDevicesService.mBluetoothMapClient.selectInstance(InstanceTempId, false);
                    }
                }
                else
                {
                	byte InstanceTempId = (byte) mInstanceId;
                	BTDevicesService.mBluetoothMapClient.selectInstance(InstanceTempId, false);
                }
            }
            else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_OPERATOR_GENERIC_CFM)) 
            {
                int message = -1;
                message = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MESSAGE, -1);
                mInstanceId = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE, -1);

                if (message == BluetoothMapClient.BLUETOOTH_MAPC_REGISTRATION_CFM) {  
                    boolean connect = intent.getBooleanExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MNS_CONNECT, false);
                    if (connect == true) {
                        mLogger.i( "MAP event notification registration succeeded");
                        
                		// request messages list
                    	BTDevicesService.mBluetoothMapClient.getMessageList(InstanceId, mapSMSInbox, 0, LIST_MESSAGE_COUNT);
                    }
                    else {
                        mLogger.e( "MAP failed event notification registration");
                    }
                }
                else if (message == BluetoothMapClient.BLUETOOTH_MAPC_SET_OPTION_CFM) {
                    //mLogger.d( "BLUETOOTH_MAPC_SET_OPTION_CFM");
                    // register for SMS notification events
                    BTDevicesService.mBluetoothMapClient.registerNotification(InstanceId, true);
                }
                else if (message == BluetoothMapClient.BLUETOOTH_MAPC_SET_MSG_STATUS_CFM)
                {
                	boolean result = intent.getBooleanExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_RESULT, false);
                	//mLogger.d( "BLUETOOTH_MAPC_SET_MSG_STATUS_CFM Arrived Result: " + result);
                	if (result)
                	{
                		mLogger.i( "Send command to get messages list");
                		BTDevicesService.mBluetoothMapClient.getMessageList(InstanceId, mapSMSInbox, 0, LIST_MESSAGE_COUNT);
                	}
                }
                else if (message == BluetoothMapClient.BLUETOOTH_MAPC_SET_FOLDER_CFM) {
                	pushResult = intent.getBooleanExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_RESULT, false);
                	sendSMS.release();
                }
            }
            // Answer for GET FOLDER LIST
            else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_GET_FOLDER_LIST_CFM)) 
            {
            	// get folders
                mInstanceId = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE, -1);
                boolean result = intent.getBooleanExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_RESULT, false);
                //mLogger.d( "BLUETOOTH_MAPC_GET_FOLDER_LIST_CFM. result:" + Boolean.toString(result) + ", received folders number:" + folders.length);
                if ((result == true)) //(mInstanceId == InstanceId) && 
                {
                    String[] folders = intent.getStringArrayExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_FOLDER_LIST);
                	int i;                 	
                	for (i= 0; i < folders.length; i++) 
                	{
                		mLogger.i( "folders:" + folders[i]);
                		if (folders[i].toLowerCase().equals(mapSMSInbox))
                		{
                			inboxAvailable = true;
                			mapSMSInbox = folders[i]; // To keep case
                		}
                		else if (folders[i].toLowerCase().equals(mapSMSOutbox))
                		{
                			outboxAvailable = true;
                			mapSMSOutbox = folders[i]; // To keep case
                		}
                	}
                	if (inboxAvailable) 
                	{
                		// set options
                		BTDevicesService.mBluetoothMapClient.setOption(InstanceId, MAP_MSG_SUBJECT | MAP_MSG_SENDER_NAME | MAP_MSG_SENDER_ADDR | MAP_MSG_READ_STATUS, 
                				MAP_FILTER_NONE/*MAP_FILTER_EMAIL | MAP_FILTER_MMS*/, MAP_GET_UNREAD_ONLY, MAP_WITHOUT_ATTACHMENT,MAP_FRACTION_FIRST, MAP_KEEP_SENT,
                				MAP_DO_NOT_RETRY_SEND);
                 	}
                }  
            }
            // ANSWER FOR GET MESSAGES LIST
            else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_GET_MSG_LIST_CFM)) 
            { 
            	mInstanceId = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE, -1);

            	boolean result = intent.getBooleanExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_RESULT, false);
            	if (result)
            	{
            		String[] handlers = null;
            		String[] subjects = null;
            		String[] senderName = null;
            		String[] senderAddr = null;
            		ArrayList<Integer> readStatusList;
                    Integer readStatus[] = new Integer[1];

            		// messages list
            		try
            		{
            			//mLogger.d(  "BLUETOOTH_MAPC_GET_MSG_LIST_CFM: Trying to get messages info");
            			handlers = intent.getStringArrayExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_LIST_HANDLER);
            			subjects = intent.getStringArrayExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_LIST_SUBJECT);
            			senderName = intent.getStringArrayExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_LIST_SENDER_NAME);
            			senderAddr = intent.getStringArrayExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_LIST_SENDER_ADDR);
                        readStatusList = intent.getIntegerArrayListExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_LIST_READ_STATUS);
                        if ( readStatusList != null ) {
                            readStatus = readStatusList.toArray(readStatus);
                        }
                        else {
                            mLogger.e("readStatusList is null");
                        }
                        if ( handlers != null ) {
                            messagesCount = handlers.length;
                        }
                        else {
                            messagesCount = 0;
                            mLogger.e("handlers is null");
                        }
            		}
            		catch(Exception ex)
            		{
            			messagesCount = 0;
            		}
                
        			newUnreadMessage = new TextMessage();
        			newUnreadMessage.Id = "";
            		if (mInstanceId == InstanceId && messagesCount>0) 
            		{
            			//mLogger.d( "subjects:");
            			int length=0;
            			try
            			{
            				length = subjects.length;
            			} 
            			catch (Exception e)
            			{
            				length = 0;
            			}
                        try {
                			for (int i = 0; i < length; i++)
                			{
                				mLogger.i(  "Phone No:" + senderAddr[i] + ", Name:" + senderName[i] + ", Subject:" +  subjects[i]);
                                if (readStatus != null) {
                                	mLogger.i( "Status:" + readStatus[i].intValue());
                                	if (newUnreadMessage.Id.isEmpty() && (readStatus[i].intValue() == BluetoothMapClient.MAPC_MESSAGE_STATUS_UNREAD))
                                	{
                            			newUnreadMessage.Id = handlers[i];
                            			newUnreadMessage.ContactName=senderName[i];
                            			newUnreadMessage.Number=senderAddr[i];
                            			newUnreadMessage.Subject=subjects[i];
                                	}
                                }
                                else
                                {
                                	mLogger.i( "Status:");
                                	if (newUnreadMessage.Id.isEmpty())
                                	{
                            			newUnreadMessage.Id = handlers[i];
                            			newUnreadMessage.ContactName=senderName[i];
                            			newUnreadMessage.Number=senderAddr[i];
                            			newUnreadMessage.Subject=subjects[i];
                                	}
                                }
                				
                			}
                        }
                        catch(Exception e) {
                            mLogger.e( "Messages info not obtained",e);  
                        }

            			// Get first unread message
            			if (!newUnreadMessage.Id.isEmpty())
            			{
            				BTDevicesService.mBluetoothMapClient.getMessage(InstanceId, newUnreadMessage.Id, BluetoothMapClient.CHARSET_UTF8);
            			}
            			else
            			{
                   			if(isJustConnected)
                			{
                				//( TAG, "Fire event OnConnected. No New messages");
                				// Fire event Connected 
                				for (IMapEventReceiver cl : mapEventReceivers)
                					cl.onPhoneConnected(0);
                				isJustConnected=false;
                			}
                			available.release();
            			}
            		}
            		else
            		{
            			newUnreadMessage = new TextMessage();
            			newUnreadMessage.Id = "";
            			if(isJustConnected)
            			{
            				//( TAG, "Fire event OnConnected. No New messages");
            				// Fire event Connected 
            				for (IMapEventReceiver cl : mapEventReceivers)
            					cl.onPhoneConnected(0);
            				isJustConnected=false;
            			}
            			available.release();
            		}
            	}
            	else
            	{
            		newUnreadMessage = new TextMessage();
                	newUnreadMessage.Id = "";
                	if(isJustConnected)
                	{
                		//mLogger.d( "Fire event OnConnected. No New messages");
                		// Fire event Connected 
                		for (IMapEventReceiver cl : mapEventReceivers)
                			cl.onPhoneConnected(0);
                		isJustConnected=false;
                	}
                	available.release();
            	}
            } 
            // EVENTS: NEW MESSAGE, SEND FAILURE ETC.
            else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_EVENT_IND)) 
            {
            	// event notification
                mInstanceId = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE, -1);
                String handle = intent.getStringExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_EVENT_HANDLE);
                int mType = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_EVENT_TYPE, -1);
                mLogger.i( "event type: " + Integer.toString(mType) + " handle:" + handle);
//                if (mType == BluetoothMapClient.MAPC_EVENT_TYPE_NEW_MESSAGE)
//                {
//                String phoneNo = "+972542403796";
//                String messageBody = "Test 8888";
//                mLogger.d("push: phoneNo:" + phoneNo + " message:" + messageBody);
//          		BTDevicesService.mBluetoothMapClient.pushMessage(InstanceId, mapSMSOutbox,null, new String[]{phoneNo},
//        				messageBody, (byte)BluetoothMapClient.CHARSET_UTF8);
//                }
                if (mType == BluetoothMapClient.MAPC_EVENT_TYPE_NEW_MESSAGE) 
                {
                	// request messages list
                	BTDevicesService.mBluetoothMapClient.getMessageList(InstanceId, mapSMSInbox, 0, LIST_MESSAGE_COUNT);
                	isNewMessageArrived=true;
                }
                else if  ((mType == BluetoothMapClient.MAPC_EVENT_TYPE_SENDING_SUCCESS) || (mType == BluetoothMapClient.MAPC_EVENT_TYPE_DELIVERY_SUCCESS)
                		|| (mType == BluetoothMapClient.MAPC_EVENT_TYPE_SENDING_FAILURE) || (mType == BluetoothMapClient.MAPC_EVENT_TYPE_DELIVERY_FAILURE) )
                {
        			if (pushedMessages.contains(handle))
        			{
           				int index = pushedMessages.indexOf(handle);
                		mHandler.removeMessages(MSG_SMS_PUSHED,pushedMessages.get(index));
                		pushedMessages.remove(handle);
                		if ((mType == BluetoothMapClient.MAPC_EVENT_TYPE_SENDING_FAILURE) || (mType == BluetoothMapClient.MAPC_EVENT_TYPE_DELIVERY_FAILURE))
                		{
              				String ttsSend = mStrings.getExpandedStringById( StringReader.Strings.TTS_SMS_SENDING_FAILED );
              				// This tts might be irrelevant after some time as sms might have been sent already.
         					mTts.speak(ttsSend, TextToSpeech.QUEUE_ADD, null ,TextToSpeech.TtsInterruptSetting.DONT_INTERUPT,
         							TextToSpeech.TTS_RELEVANCE_ETERNAL, "", true, HmiDisplayService.NO_DISPLAY);
                		}
        			}
                }
              }
            // Message Returned
            else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_GET_MSG_CFM))
            {
            	 //
            	 mInstanceId = intent.getIntExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_MAS_INSTANCE, -1);
            	 String messageBody = intent.getStringExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_DATA);
            	 mLogger.i( "Message Body: " + messageBody);
        		 mHandler.sendMessage( mHandler.obtainMessage( MSG_MAPC_GET_MSG_CFM, messageBody ) );
             }
             else if (action.equals(BluetoothMapClientIntent.BLUETOOTH_MAPC_PUSH_MSG_CFM))
             {
            	 pushResult = intent.getBooleanExtra(BluetoothMapClientIntent.BLUETOOTH_MAPC_RESULT, false);
            	 String handle = intent.getStringExtra(BluetoothMapClient.BluetoothMapClientIntent.BLUETOOTH_MAPC_MSG_HANDLE);
            	 if (handle != null)
            	 {
            		 mLogger.i( "Result:" + pushResult + " Handle:" + handle);
            	 }
            	 else
            	 {
            		 mLogger.i( "Result:" + pushResult + " Handle:"); 
            	 }
            	 if ((pushResult) && (handle != null) && (!handle.isEmpty()) && (!handle.equals("-1")))
            	 {
            		 //push success
                	 String handler = new String(handle);
            		 pushedMessages.add(handler);
            		 mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_SMS_PUSHED, handler ),MSG_SMS_PUSHED_DELAY);
            	 }
//            	 else
//            	 {
//            		 //push failure
//      				String ttsSend = mStrings.getExpandedStringById( StringReader.Strings.TTS_SMS_SENDING_FAILED );
//      				mTts.speak(ttsSend, TextToSpeech.QUEUE_ADD, null );
//            	 }
            	 sendSMS.release();
             }
           }
        
        };
        
        public void UpdateMessageAsRead(String MessageId)
        {
        	BTDevicesService.mBluetoothMapClient.setMessageStatus(InstanceId, MessageId, BluetoothMapClient.MAPC_MESSAGE_STATUS_READ);
        }
        
        public boolean SendMessage(String phoneNo, String messageBody)
        {
        	phoneNo = phoneNo.replace("-", "");
//        	try  
//        	  {  
//        	    double d = Double.parseDouble(phoneNo);
//        	  }  
//        	  catch(NumberFormatException nfe)  
//        	  {
//        		mLogger.e( "Phone number is wrong");  
//        	    return;
//        	  }
        	
        	//mLogger.d( "Send Message with parameters: " + recipient + ", " + messageBody);
        	if (messageBody==null||messageBody=="")
        	{
        		mLogger.i( "Invalid receipient or message body");
        		return false;
        	}
        	try{
        		pushResult = false;
        		BTDevicesService.mBluetoothMapClient.setFolder(InstanceId,mapSMSOutbox,false);
        		mLogger.i( "change folder:" + mapSMSOutbox);
        		int drain = sendSMS.drainPermits();
//        		if (drain > 0)
//        		{
//        			mLogger.d("drained send semaphore. was:" + drain);
//        		}
        		if (mDevaddr.isEmpty())
        		{
        			return false;
        		}
        		sendSMS.acquire();//wait for set folder confirmation
        		mLogger.i( "folder set result:" + pushResult);
        		if (pushResult)
        		{
        			BTDevicesService.mBluetoothMapClient.pushMessage(InstanceId, mapSMSOutbox,null, new String[]{phoneNo},
        					messageBody, (byte)BluetoothMapClient.CHARSET_UTF8);
        			mLogger.i( "Message: " + messageBody + " Pushed to local device. Number:" + phoneNo);
        			if (mDevaddr.isEmpty())
        			{
        				return false;
        			}
        			sendSMS.acquire();
        		}
        		else
        		{
        			return pushResult;
        		}
        		boolean msgPushResult = pushResult;
           		BTDevicesService.mBluetoothMapClient.setFolder(InstanceId,"msg",false);
        		mLogger.i( "change folder:msg");
           		if (mDevaddr.isEmpty())
        		{
        			return msgPushResult;
        		}
        		sendSMS.acquire();
        		mLogger.i( "folder set result:" + pushResult);
        		return msgPushResult;
        	}
        	catch(Exception re){
        		mLogger.e( "Error during Send Message: ", re);
        		return false;
        	}

        }
        
        public static TextMessage GetMessage()
        {
        	//mLogger.e( "TextMessage: Client requested a new message");
        	
        	if (newUnreadMessage==null || newUnreadMessage.Id=="")
        	{
        		//mLogger.e( "TextMessage: No new messages to return.");
        		return null;
        	}
        	
        	try
        	{
        		//if (clientMessageId==newUnreadMessage.Id) // Messagein memory isn't changed
        		//{
        			//mLogger.e( "TextMessage: Stop thread untill message arriving");
        			available.acquire();
        			//mLogger.e( "TextMessage: Thread released");
        		//}
        	} catch (InterruptedException e) 
        	{
        		//mLogger.e( "Error during GetMessage on getMessageList: ", e);	
        		return null;
        	}
        	
        	clientMessageId=newUnreadMessage.Id;
        	
        	// Update message status to Read
        	try {
        		//mLogger.e( "Send command to set message status to read for: "+ newUnreadMessage.Id);
        		RoadTrackBTMAP.getInstance().UpdateMessageAsRead(clientMessageId);
        		//BTDevicesService.mBluetoothMapClient.setMessageStatus(InstanceId, newUnreadMessage.Id, BluetoothMapClient.MAPC_MESSAGE_STATUS_READ);
        	}catch(Exception re)
        	{
        		mLogger.e( "Error during GetMessage on setMessageStatus: ", re);
        	}
        	mLogger.i( "GetMessage: " + "Number:" + newUnreadMessage.Number + ", Name:" + newUnreadMessage.ContactName + ", Subject:" + newUnreadMessage.Subject); 
        	return newUnreadMessage;
        }

        /*
         * combine name to one string.
         */
    	private String buildContactName(String firstName, String midName, String lastName ) {
    		if (firstName.trim() != null) {
    			firstName = firstName.trim();
    		}
    		if (midName.trim() != null) {
    			midName = midName.trim();
    		}
    		if (lastName.trim() != null) {
    			lastName = lastName.trim();
    		}
    		String name = "";
    		if( (firstName!= null) && !firstName.isEmpty() ) {
    			name = firstName;
    		}
        	if( (midName!= null) && !midName.isEmpty() ) {
        		name+=" "+midName;
        	}
           	if( (lastName!=null) && !lastName.isEmpty() ) {
        		name+=" "+lastName;
        	}
           	name = name.trim();
           	if(!name.isEmpty()) {
           		return name;
           	}
    		else {
    			return null;
    		}
    	}
	
    	/*
    	 * returns valid "modified UTF-8" data (removes emiticon if exists)
    	 */
    	static String checkUtfString(String msg)
    	{
    	    int i,j;
			byte[] bytes = msg.getBytes();
			int len = bytes.length;
			byte[] bytesUTF8 = new byte[len];
			String s;
			
       	    if (len == 0) {
       	    	mLogger.d("null UTF string");
                return "";
    	    }
       	    j = 0;
    	    for (i = 0; i < len; i++) {
    	        byte utf8 = bytes[i];
    	        // Switch on the high four bits.
    	        switch (utf8 >> 4) {
    	            case 0x00:
    	            case 0x01:
    	            case 0x02:
    	            case 0x03:
    	            case 0x04:
    	            case 0x05:
    	            case 0x06:
    	            case 0x07: {
    	                // Bit pattern 0xxx. No need for any extra bytes.
    	            	bytesUTF8[j] = utf8;
    	            	j++;

    	                break;
    	            }
    	            case 0x08:
    	            case 0x09:
    	            case 0x0a:
    	            case 0x0b:
      	            	mLogger.w("illegal utf start byte. " + String.format( "%02X", utf8 ));
	                    return "";
    	            case 0x0f: {
    	                /*
    	                 * Bit pattern 1111, so there are three additional bytes.
    	                 * Note: 1111 is valid for normal UTF-8, but not the
    	                 * modified UTF-8 used here, so filter it out.
    	                 */
    	            	mLogger.w("illegal utf start byte. " + String.format( "%02X", utf8 ));
    	            	if ((i+=3) >= len)
    	            	{
    	            		mLogger.w("start byte: " + String.format( "%02X", utf8 ) + ". missing continuation byte.");
    	                    return "";
    	            	}
    	                break;
    	            }
    	            case 0x0e: {
    	                // Bit pattern 1110, so there are two additional bytes.
    	            	bytesUTF8[j] = utf8;
    	            	j++;
    	            	if (i++ >= len)
    	            	{
    	            		mLogger.w("start byte: " + String.format( "%02X", utf8 ) + ".  missing continuation byte.");
     	                    return "";
    	            	}
    	            	utf8 = bytes[i];
     	                // Fall through to take care of the finals byte.
    	            }
    	            case 0x0c:
    	            case 0x0d: {
    	                // Bit pattern 110x, so there is one additional byte.
    	            	bytesUTF8[j] = utf8;
    	            	j++;
      	            	if (i++ >= len)
    	            	{
      	            		mLogger.w("start byte: " + String.format( "%02X", utf8 ) + ".  missing continuation byte.");
    	                    return "";
    	            	}
    	            	utf8 = bytes[i];
    	            	bytesUTF8[j] = utf8;    	            	
    	            	j++;
    	                break;
    	            }
    	        }
    	    }
    	    byte[] finalBytes = Arrays.copyOfRange(bytesUTF8, 0, j);
    	    s = new String(finalBytes);
    	    mLogger.d(" return string:" + s);
    	    return s;
    	}
    	
        /// TESTER
        /*Thread t = new Thread() {
            public void run() {
            	mLogger.e( "Start checking thread");
            	MyEventListener receiver = new MyEventListener();
            	receiver.RegisterEvents();
            }           
        };
        
        private class MyEventListener implements IMapEventReceiver 
        {
        	public MyEventListener()
        	{
        		mLogger.e( "Starting handling events");
        		RoadTrackBTMAP.getInstance().registerEventHandler(this);
        	}
        	public void RegisterEvents()
        	{
        		mLogger.e( "Starting handling events 2 ");
        		//RoadTrackBTMAP.getInstance().registerEventHandler(this);
        	}
        	
    		public void onPhoneConnected( int unreadMessageCount )
    		{
    			mLogger.e( "CHECK: onPhoneConnected " + unreadMessageCount + " unread messages");
    			if (unreadMessageCount > 0)
    			{
    				//TextMessage txt = RoadTrackBTMAP.getInstance().GetMessage();
    				//mLogger.e( "CHECK: onPhoneConnected. Message Body: " + txt.MessageBody);
    			}
    			
    			Thread tr = new Thread() {
    	            public void run() {
    	            	RoadTrackBTMAP.getInstance().SendMessage("0544968110","Send test");
    	            }           
    	        };
    	        tr.start(); 
    		}
    		public void onNewMessageArrived(String sender)
    		{
    			mLogger.e( "CHECK: onNewMessageArrived Sender:" + sender);
    			TextMessage txt = RoadTrackBTMAP.getInstance().GetMessage();
    			mLogger.e( "CHECK: Text:" + txt.MessageBody);
    		}
    		public void onPhoneDisconnected()
    		{
    			mLogger.e( "CHECK: onPhoneDisconnected");
    		}
        };*/
       
        
        //public void DeleteMessage(string Id)
        //{
        	//BTDevicesService.mBluetoothMapClient.setMessageStatus(InstanceId, clientMessageId, BTDevicesService.BluetoothMapClient.MAPC_MESSAGE_STATUS_DELETED);
        	//return false;
        //}
    };


