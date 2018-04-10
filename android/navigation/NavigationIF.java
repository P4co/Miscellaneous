/* 
 * Interface to Navigation.java
 * 2Way Messenger NavMsgParcel instead of Intents to communicate between (base/...)Navigation & (tomtom\navapp...)NavigationService
 * Navigation >< 2Way Messenger. send(Message(NavMsgParcel)) >< handleMessage(Message(NavMsgParcel)) >< NavigationService
 * NaigationService > 2Way Message(NavMsgParcel) > Navigation >  if to TBT:broadcast(NavMsgParcel)  > TBTNotificationManager > TBT/GMLAN
 * Message to specific class instead of Intent broadcasting
 */

package com.roadtrack;

import java.util.Arrays;
import java.util.ArrayList;

import com.roadtrack.NavMsgParcel;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.Context;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.HandlerThread;

import com.roadtrack.tbt.TBTNotificationMgr;
import com.roadtrack.util.RTLog;

public class NavigationIF {
	
    private static final String TAG                          = "NavigationIF";
    
    private TBTNotificationMgr				mTBTNotificationMgr;
    public Navigation mNavigationRef;
    private Context mContext;
    
    private static RTLog                        mLogger;
    
    public static final String NAVIGATION_PACKAGE_NAME = "com.navigation.roadtrack";
    public static final String NAVIGATION_CLASS_NAME = "com.navigation.roadtrack.NavigationService";
    	
	/*   From NavigationService   */
    public static final String ROUTE_FOUND = "Nav.S2C.ROUTE_FOUND";  
    public static final String ROUTE_EMPTY = "Nav.S2C.ROUTE_EMPTY"; 
    public static final String ROUTE_FINISHED = "Nav.S2C.ROUTE_FINISHED"; 
    public static final String ROUTE_MESSAGES = "Nav.S2C.ROUTE_MESSAGES"; 
    public static final String ADDRESS_MESSAGE = "Nav.S2C.ADDRESS_MESSAGE"; 
    public static final String PROVINCE_MESSAGE = "Nav.S2C.PROVINCE_MESSAGE"; 
    public static final String ROUTE_PREVIEW =          "Nav.S2C.ROUTE_PREVIEW"; 
    public static final String ROUTE_PREVIEW_ERROR =    "Nav.S2C.ROUTE_PREVIEW_ERROR"; 
    public static final String PREVIEW_MANEUVER_LIST =    "Nav.S2C.PREVIEW_MANEUVER_LIST";
    public static final String NAVIGATION_TTS_INTENT =   "Nav.S2C.NAVIGATION_TTS_INTENT";  
    public static final String NAVIGATION_RECALCULATING_ROUTE =   "Nav.S2C.NAVIGATION_RECALCULATING_ROUTE";  
    public static final String ROUTE_CALC_RESTARTED = "Nav.S2C.ROUTE_CALC_RESTARTED";
	public static final String NAVIGATION_GUIDANCE_INFO = "Nav.S2C.GUIDANCE_INFO";
	public static final String NAVIGATION_DESTINATION_ARRIVED = "Nav.S2C.DESTINATION_ARRIVED";

    
    /*  To NavigationService  */
	public static final String ACTION_CREATE_NAV_ENGINE = "Nav.S2C.CREATE_NAV_ENGINE";  
	
    public static final String ACTION_NAVIGATION_START = "Nav.C2S.START";
    public static final String ACTION_NAVIGATION_STOP = "Nav.C2S.STOP";

    public static final String ACTION_GET_ADDRESS = "Nav.C2S.GET_ADDRESS";
    public static final String ACTION_GET_ROUTE_PREVIEW = "Nav.C2S.ROUTE_PREVIEW";
    public static final String ACTION_GET_MANEUVER_LIST = "Nav.C2S.GET_MANEUVER_LIST";
	 
    public static final String ACTION_GET_LOCATION = "Nav.C2S.GET_LOCATION";
    public static final String ACTION_SEARCH_TEST = "Nav.C2S.SEARCH_TEST"; // TBD
    public static final String ACTION_GET_ACTIVE_DEST_AND_WAYPOINTS = "Nav.C2S.GET_ACTIVE_DEST_AND_WAYPOINTS";  // TBD
    public static final String ACTION_GET_ROAD_INFO = "Nav.C2S.ROAD_INFO"; // TBD
    public static final String ACTION_SEARCH_POI = "Nav.C2S.SEARCH_POI";  // TBD
    public static final String ACTION_SEARCH_CROSSING_STREETS = "Nav.C2S.SEARCH_CROSSING_STREETS"; 
    public static final String ACTION_READ_DIRECTIONS = "Nav.C2S.READ_DIRECTIONS";  // TBD
    public static final String ACTION_GET_DISTANCE = "Nav.C2S.GET_DISTANCE";  // TBD
    public static final String ACTION_GET_PROVINCE = "Nav.C2S.GET_PROVINCE";

    
    /* To Navigation service and TBTNotificationManager  */
    /*  Not From NavigationService  */    
    public static final String NAVIGATION_DBNC_GPS_STATUS_INTENT =   "Nav.C2TBT.NAVIGATION_DBNC_GPS_STATUS_INTENT";
    public static final String NAVIGATION_DBNC_ONROAD_STATUS_INTENT =   "Nav.C2TBT.NAVIGATION_DBNC_ONROAD_STATUS_INTENT"; 
    public static final String LOCATION_MESSAGE = "Nav.S2C.LOCATION_MESSAGE"; 
    public static final String CROSSING_STREETS_MESSAGE = "Nav.S2C.CROSSING_STREETS_MESSAGE"; 
    public static final String CALCULATING_ROUTE =      "Nav.C2TBT.CALCULATING_ROUTE";   
    public static final String NAVIGATION_CURRENT_DESTINATION_AND_WAYPOINTS = "Nav.S2C.NAVIGATION_CURRENT_DESTINATION_AND_WAYPOINTS"; 
    
    public static final String ROUTE_SHOW_MANEUVER =    "com.roadtrack.Navigation.SHOW_MANEUVER"; //TtsService.java:
    public static final String NAVIGATION_STATE_CHANGE = "com.roadtrack.Navigation.STATE_CHANGE"; //vdp/NavHandler.java
    
    /*Message.What values: C-lient==Navigation  S-ervice==NavigationService TBT-NotificationMng  A-ll */
    public static final int NAV_MSG_WHAT_S2C = 501;
    public static final int NAV_MSG_WHAT_C2S = 502;  
    public static final int NAV_MSG_WHAT_C2TBT = 503;  
    public static final int NAV_MSG_WHAT_S2A = 504;  
    public static final int NAV_MSG_WHAT_C2A = 505;
    /* Command to the service to register a client, receiving callbacks  from the service. 
     * The Message's replyTo field must be a Messenger of the client where callbacks should be sent.*/
    public static final int C2S_REGISTER_CLIENT = 601;
	/* replyTo field must be a Messenger of the client */
    public static final int C2S_UNREGISTER_CLIENT = 602;
	/** set a new value */
    public static final int S2C_FROM_SERVICE_INTENT = 603;
    
    private NavMsgParcel mSavedNavMsg = null;;
    
    
    public enum MCURouteInstructions
    {
    	NONE(-1),
        DEPART(0),
        FOLLOW(1),
        TURN_LEFT(2),
        TURN_RIGHT(3),
        TURN_SHARP_LEFT(4),
        TURN_SHARP_RIGHT(5),
        KEEP_LEFT(8),
        KEEP_RIGHT(9),
        BEAR_LEFT(6),
        BEAR_RIGHT(7),
        EXIT_LEFT(10),
        EXIT_RIGHT(11),
        MAKE_UTURN(12),
        ARRIVE(15),
        ROUNDABOUT_UNKNOWN_EXIT(16),
        ROUNDABOUT_1ST_EXIT(17),
        ROUNDABOUT_SCND_EXIT(18),
        ROUNDABOUT_3RD_EXIT(19),
        ROUNDABOUT_4TH_EXIT(20),
        ROUNDABOUT_5TH_EXIT(21),
        ROUNDABOUT_6TH_EXIT(22),
        ROUNDABOUT_7TH_EXIT(23),
        ROUNDABOUT_8TH_EXIT(25);
        
        public int numVal;
    	
    	private MCURouteInstructions(int val)
    	{
    		this.numVal = val;
    	}     
        
    }
    
    public enum TTJunctionType
    {
    	BIFURCATION(0),
    	REGULAR(1),
    	ROUNDABOUT(2);
    	
        public int numVal;
    	
    	private TTJunctionType(int val)
    	{
    		this.numVal = val;
    	}   
    }
    
    
    public NavigationIF(Navigation mNavigationRef, Context context) {
        mLogger = RTLog.getLogger(TAG, "navigation", RTLog.LogLevel.INFO );
        this.mNavigationRef = mNavigationRef;     
        mContext = context;
        mHandlerThread = new HandlerThread( "NavigationIF" );
        mHandlerThread.start();
        mIncomingHandler = new IncomingHandler( mHandlerThread );
        mIncomingMessanger = new Messenger( mIncomingHandler );
    }
	
	/***Strat  2Way Messenger ,,,***/
	
    /**** Receiving messages part (Messenger)  */
    /* Handler of incoming messages from service. //S2C */ 
    class IncomingHandler extends Handler {

        public IncomingHandler( HandlerThread thr ) {
            super( thr.getLooper() );
        }

    	@Override
    	public void handleMessage(Message msg) {
    		switch (msg.what) {
    		case NAV_MSG_WHAT_S2A:
    		case NAV_MSG_WHAT_S2C: 
    			if(msg.obj != null){
    				final NavMsgParcel navParcel = (NavMsgParcel) msg.obj; 
    				mNavigationRef.onMsgReceive(mContext,  navParcel); 
        			//mLogger.d("Received from service: " + navParcel.toString());
    			} else {
    				mLogger.w("Received from service:  navParcel == null");
    			}
    			break;
    		default:
    			mLogger.w("Received unknown msg from service: msg.what==" + msg.what);
    			break;
    		}
    	}
    }
    
    
    /* Target we publish for  I/O messages (incoming goes to IncomingHandler.) */

    private HandlerThread mHandlerThread;
    private IncomingHandler mIncomingHandler;
    private Messenger mIncomingMessanger;

    /******************* Sending messages part (Messenger)  */

    /** Messenger that belongs to the service (received). */
    Messenger mMsgService = null;

    /** Flag indicating whether we have called bind on the service. */

    /* Class for interacting with the main interface of the service. */ 
    private ServiceConnection mConnection = new ServiceConnection() {
    	public void onServiceConnected(ComponentName className, IBinder service) {
    		mLogger.d("onServiceConnected");
    		// This is called when the connection with the service has been established, giving us the object we can use to
    		// interact with the service.  We are communicating with the service using a Messenger, so here we get a client-side
    		// representation of that from the raw IBinder object.
    		synchronized (this) {
        		mMsgService = new Messenger(service);    		
                try {
                    Message msg = Message.obtain(null,
                            C2S_REGISTER_CLIENT);
                    msg.replyTo = mIncomingMessanger;
                    mMsgService.send(msg);
                    if(null != mSavedNavMsg)
                    {
                   	 sendMsgToNavService(mSavedNavMsg);
                   	 mSavedNavMsg = null;
                    }
                } catch (RemoteException e) {
               	 mLogger.e("",e);
                    // In this case the service has crashed before we could even do anything with it; we can count on soon being
                    // disconnected (and then reconnected if it can be restarted) so there is no need to do anything here.
                }
			}

    	}

    	public void onServiceDisconnected(ComponentName className) {
    		mLogger.d("2Way onServiceDisconnected");
    		// This is called when the connection with the service has been unexpectedly disconnected -- that is, its process crashed.
    		mMsgService = null;
    	}
    };

    public void sendMsgToNavService(NavMsgParcel navParcel) {
    	String outStr= ", Empty navParcel";
    	if(navParcel!=null){
    		outStr=navParcel.toString();
        }
    	//mLogger.d("2Way sendMsgToNavService, " + outStr);
    	
    	if (mMsgService==null){
    		mLogger.e("mMsgService==null");
    		return;
    	}
    	// Create and send a message to the service, using a supported 'what' value
    	Message msg = Message.obtain(null, NAV_MSG_WHAT_C2A , navParcel); //what=1,(Object)navParcel
    	try {
    		mMsgService.send(msg);
    	} catch (RemoteException e) {

    		mLogger.e("",e);
    	}
    }

    private boolean isConnected() {
        return ( mMsgService != null );
    }

    //    @Override
    protected void bindIfNot() { //first
        final int SLEEP_MAX_TRIES = 12;
        final int SLEEP_MULT = 10;
    	if(!isConnected()){
    		mLogger.d("2Way startBind");
    		// Bind to the service
    		NavMsgParcel navParcel = new NavMsgParcel();
    		navParcel.setClassName(NAVIGATION_PACKAGE_NAME, NAVIGATION_CLASS_NAME); //navParcel.setAction/putExtra 
    		mContext.bindService(navParcel, mConnection,
    				mContext.BIND_AUTO_CREATE);
            for(int counter=2; (counter<SLEEP_MAX_TRIES) && (!isConnected()) ; counter++){
                try{
                    Thread.sleep(SLEEP_MULT*counter*counter);
                    if(counter%3==2){
                        mLogger.d("Wait for bind " + (SLEEP_MULT*counter*counter)+ " ms");
                    }
                } catch (java.lang.InterruptedException e) {
                    mLogger.e("Thread.sleep thrown: ",e);
                }
            }
            if(!isConnected()){
                mLogger.e("bind failed, mMsgService==null");
            } else {
                mLogger.d("bind of NavigationService Messsenger is successful");
            }
    	}
    }

    //    @Override
    protected void stopBind() {
    	mLogger.d(" 2Way ");
    	// Unbind from the service
    	if (isConnected()) {
    		mContext.unbindService(mConnection);
    		mMsgService=null;
    	}
    }
    
	/***End  2Way Messenger ,,,***/
    
	/***Strat  Methodes ,,,***/
    

	public void start(ArrayList<Location>  navigationRoute){
		NavMsgParcel navParcel = initiateNavServMessage(ACTION_NAVIGATION_START);
        navParcel.putParcelableArrayListExtra("navigation_route", navigationRoute);
		msgSend(navParcel); 	
	}
	public void createService()
	{
		NavMsgParcel navParcel = initiateNavServMessage(ACTION_CREATE_NAV_ENGINE);
		msgSend(navParcel);
	}
	
	public void stop(){

        if ( isConnected() ) {
	       NavMsgParcel navParcel = initiateNavServMessage(ACTION_NAVIGATION_STOP);

    	   msgSend(navParcel);
        }
	}
	
	public void getRoutPreview( int offset ){
		NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_ROUTE_PREVIEW);
        navParcel.putExtra("LocationIdx",offset);

		msgSend(navParcel);
	}
	
	public void getManuverList(int requestedAction){
		NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_MANEUVER_LIST);
		navParcel.putExtra("requestedAction", requestedAction);
		
		msgSend(navParcel); 
	}
	
 /** TBD */
    
    public void getRoadInfo(NavigationReceiver mReceiver) {
    	NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_ROAD_INFO );
        navParcel.putExtra("mReceiver",mReceiver);

        msgSend(navParcel);
    }
    
    public void getCrossingStreetLocation(String country, String city, String street1, String street2){
    	NavMsgParcel navParcel = initiateNavServMessage(ACTION_SEARCH_CROSSING_STREETS);
    	 Bundle bundle = add2streetsBundle( country,  city,  street1,  street2);
    	 navParcel.putExtras(bundle);
         msgSend(navParcel); 
    }
    
    public void getLocationByAddress(String country,String province,String city, String street1, String street2, String houseNumber, boolean needResult) {
    	if(!((null == street2) || (street2.isEmpty()) || (street2.equalsIgnoreCase("")) 
    			|| (null == street1)|| (street1.isEmpty()) || (street1.equalsIgnoreCase(""))))
    	{
    		getCrossingStreetLocation(country, city, street1, street2);
    	}
    	else
    	{
    		NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_LOCATION);
            	
    		Bundle bundle = add2streetsBundle( country,  city,  street1,  street2);
    		bundle.putString("province",province);
    		bundle.putString("houseNumber",houseNumber);
    		if( !needResult ) {
    			bundle.putInt("sendResult",0);
    		}
        	navParcel.putExtras(bundle);
    		msgSend(navParcel);
    	}
    }
    
    private Bundle add2streetsBundle(String country, String city, String street1, String street2){
    	Bundle bundle = new Bundle();
    	bundle.putString("country",country);
    	bundle.putString("city",city);
    	bundle.putString("street1",street1);
    	bundle.putString("street2",street2);
    	return bundle;
    }
    
    public void getPOIsByLocation(Location currentLocation,String category,int radius, NavigationReceiver mReceiver) {
    	NavMsgParcel navParcel = initiateNavServMessage(ACTION_SEARCH_POI);
        navParcel.putExtra("currentLocation",currentLocation);
        navParcel.putExtra("category",category);
        navParcel.putExtra("radius",radius);
        navParcel.putExtra("mReceiver",mReceiver);

        msgSend(navParcel); 
    }
    
    
    public void getAddress(double latitude, double longitude) {
    	NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_ADDRESS);

    	Bundle bundle = new Bundle();
    	bundle.putDouble("latitude",latitude);
    	bundle.putDouble("longitude",longitude);
        navParcel.putExtras(bundle); 
    	msgSend(navParcel); 
    }
    
    public void getDistanceToTarget(NavigationReceiver mReceiver) {
    	NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_DISTANCE);

        navParcel.putExtra("mReceiver",mReceiver);
        msgSend(navParcel); 
    }
	
    
    public void getFullProvinceName(String province,String country)
    {
    	NavMsgParcel navParcel = initiateNavServMessage(ACTION_GET_PROVINCE);
    	
    	navParcel.putExtra("province",province);
    	navParcel.putExtra("country",country);
    	msgSend(navParcel);
    	
    }
    
	/****** Generic  ,,, *****/
	
	public void sendMessage(String action, Bundle extras){  
		NavMsgParcel navParcel = initiateNavServMessage(action);
        navParcel.putExtras(extras);

		msgSend(navParcel); 
	}
	public void sendMessage(String action){  
		NavMsgParcel navParcel = initiateNavServMessage(action);

		msgSend(navParcel); 
	}
	public void sendMessage(NavMsgParcel navParcel){  
		navParcel.setClassName(NAVIGATION_PACKAGE_NAME, NAVIGATION_CLASS_NAME);
		msgSend(navParcel); 
	}
	public void sendMessageNotToNavSer(NavMsgParcel navParcel){  

		msgSend(navParcel); 
	}
	
    private void msgSend(NavMsgParcel navParcel){
    	bindIfNot();
    	// will not send messege if the service is down
    	// when creation messege arrive it will be saved for the moment the service will be up again
    	synchronized (this) { // synchronizing with the navigation thread
        	if(isConnected())
        	{
        		mLogger.i(navParcel.toString());
        		sendMsgToNavService(navParcel);
        	}
        	else if (navParcel.getAction().equals(ACTION_CREATE_NAV_ENGINE))
        	{
        		mLogger.i("saving msg- Wait for Nav service to connect : "+navParcel.toString());
        		mSavedNavMsg = navParcel;
        	}
		}

    }
    private NavMsgParcel initiateNavServMessage(String actionStr){
    	NavMsgParcel navParcel = new NavMsgParcel(actionStr);
    	navParcel.setClassName(NAVIGATION_PACKAGE_NAME, NAVIGATION_CLASS_NAME);
        return navParcel;
	}
     
	/***End  Methodes ***/ 
}

