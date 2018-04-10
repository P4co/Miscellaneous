
import android.app.IpodControl;
import android.app.IpodControl.OnAuthenticationListener;
import android.app.IpodControl.OnPlugListener;
import android.app.IpodControl.OnCommunicationListener;

import android.util.Log;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import android.os.*;
import java.nio.ByteBuffer;

import com.roadtrack.util.RTLog;


/*package*/ class RTiAPCommunication {
    private static final String TAG = "RTiAPCommunicaton";
    private RTLog mLogger;
    private static RTiAPCommunication mSingleton = null;
    private final boolean mDebugEcho = false;//for echo debug with iPhone
    private IpodControl mIpodControl = null;
    private OnCommunicationListener mOnCommunicationListener;
    private OnPlugListener mOnPlugListener;
    private OnAuthenticationListener mOnAuthenticationListener;
    private Thread  miAPThread;
    private volatile boolean mWaitAck = false;
    private volatile int mSessionID = -1;
    private volatile boolean mSessionIDResult = false;
    private static final int MAX_TRANSMIT_RETRIES = 2;
	private Handler mHandler;
	private int mAckFailCounter;
	private int mMaxPayloadSize = 384;//default
    private byte[] mData;

	private LinkedBlockingQueue<byte []> queueRcv = new LinkedBlockingQueue<byte[]>();
	private Queue queueSend = new LinkedList();
    
	// local messages types
	private static final int MSG_DATA_RECEIVED = 0;
	private static final int MSG_DATA_TRANSMIT_ACK = 1;
	private static final int MSG_DATA_TRANSMIT_TIMEOUT = 2;
	private static final int MSG_DATA_TRANSMIT_QUEUE_NOT_EMPTY = 3;
	private static final int MSG_IPOD_TRANSFER_DATA_RAW = 4;
		

	// Timeouts
	public static final int MSG_DATA_TRANSMIT_DELAY = 500;
	public static final int AUTHENTICATION_TIMEOUT_DELAY = 4000;
	
	synchronized public static RTiAPCommunication getInstance()
	{
		if (mSingleton == null)
		{
			mSingleton = new RTiAPCommunication();
		}
	    return mSingleton;
	}
    
    private RTiAPCommunication()
    {
        mLogger = RTLog.getLogger(TAG, "RTiAP", RTLog.LogLevel.INFO );
        mLogger.i("constructor");
	    // setup Ipod interface
       	mIpodControl = IpodControl.getIpodControl();
    	mOnAuthenticationListener = new IapAuthenticationListener();
    	mOnCommunicationListener = new IapCommunicationListener();
    	setupIpodControl();
   	
		/**
	     * handles different messages in it's own thread. Messages were sent from local broadcast receivers
	     * 
	     */
		miAPThread = new Thread( new Runnable() {
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
		}, "iAPThread");
		miAPThread.start();
        
    }
    
	private void messageHandler( Message msg ) 
	{
		switch ( msg.what ) {
			case MSG_DATA_TRANSMIT_ACK:
				/*
				 * Ack received for data sent to iOS application
				 */
				int result = msg.arg1;
				mLogger.d("msg: MSG_DATA_TRANSMIT_ACK");
				mLogger.d("result:" + result);
				mHandler.removeMessages(MSG_DATA_TRANSMIT_TIMEOUT);
				if (result == 1)
				{
					mWaitAck = false;
					iAPSend(false);
				}
				else
				{
					if (mWaitAck)
					{
						mAckFailCounter++;
						mWaitAck = false;
						if (mAckFailCounter >= MAX_TRANSMIT_RETRIES)
						{
							mLogger.w("max transmit retries occurred. Closing iAP");
			            	RoadTrackSPP.getInstance().sppIapDisconnected(false);
						}
						else
						{
							iAPSend(true);
						}
					}
				}
				break;
			case MSG_DATA_TRANSMIT_TIMEOUT:
				/*
				 * Ack was not received for data sent to iOS application
				 */
				mLogger.w("msg: MSG_DATA_TRANSMIT_TIMEOUT");
				if (mWaitAck)
				{
					mAckFailCounter++;
					mWaitAck = false;
					if (mAckFailCounter >= MAX_TRANSMIT_RETRIES)
					{
						mLogger.w("max transmit retries reached.");
					}
					else
					{
						iAPSend(true);
					}
				}
				break;
			case MSG_DATA_RECEIVED:
				/*
				 * Data received from the iOS application
				 */
				byte[] data = (byte[]) msg.obj;
				mLogger.d("msg: MSG_DATA_RECEIVED len: " + data.length);
				if (data.length >= 0)
				{
					String stringBuf = new String(data,0,data.length);
					mLogger.d("String Buffer: " + stringBuf);
					if (!mDebugEcho)
					{
						queueRcvAdd(data);
					}
					else
					{
						//loopback to iPhone. Debug only!
						int length = (data.length > mMaxPayloadSize) ? mMaxPayloadSize : data.length;
						iAPWrite(data,length);
					}
				}
				break;
			case MSG_DATA_TRANSMIT_QUEUE_NOT_EMPTY:
				/*
				 * Send queue to iOS application has data to be sent
				 */
				iAPSend(false);
				break;
			case MSG_IPOD_TRANSFER_DATA_RAW:
				/*
				 * Raw data received from IPOd via SPP port. Send it to iAP level for pharsing. 
				 */
				byte[] dataBuf = (byte[]) msg.obj;

				mLogger.d("msg: MSG_IPOD_TRANSFER_DATA_RAW len: " + dataBuf.length);
        		try
        		{
        			int dummy = 0;
        			mIpodControl.communicationIpodTransferDataRaw(dummy, dataBuf);
        		}
        		catch( Exception e )
                {
                    mLogger.e( "failure: ", e);
                    e.printStackTrace();
                }
				break;
				
			default:
				mLogger.w(  "messageHandler:unknown msg " + msg.what);				
				break;
		}
	}

/*package */void iAPIpodTransferDataRaw(byte[] dataBuf)
	{
		mHandler.sendMessage( mHandler.obtainMessage( MSG_IPOD_TRANSFER_DATA_RAW, dataBuf ) );
	}

	private void queueRcvAdd(byte[] data)
	{
		try {
            queueRcv.put(data);
		} catch (java.lang.InterruptedException e) {
			mLogger.e("InterruptedException ", e );
		} catch (java.lang.NullPointerException e) {
			mLogger.e(".NullPointerException ", e );
		}
	}
	
	/*
	 * sets in Ipod the state of the SPP channel.
	 * when parameter connect is "true" the SPP channel is connected. 
	 */
	public void startBtRfcommConnection(boolean connect)
	{
		try {
			mIpodControl.setBtRfcommConnection(connect);
			queueRcv.clear();	
			synchronized ( queueSend ) {
				queueSend.clear();
			}
			mData = null;
		}
		catch( Exception e )
        {
            mLogger.e( "failure: ", e);
            e.printStackTrace();
        }
	}
	
	/*
	 * Signals Ipod to start iAP communication with the remote iPhone. Will start IDPS + Authentication
	 */
	public void startUpAuthentication()
	{
		try
		{
			mIpodControl.setUpAuthentication();
		}
		catch( Exception e )
        {
            mLogger.e( "failure: ", e);
            e.printStackTrace();
        }
	}
	
	private void iAPSend(boolean retransmission)
	{
		if ((mSessionID != -1) && mSessionIDResult && !mWaitAck)
		{
    		if (!retransmission)
    		{
    			synchronized ( queueSend ) {
					mData = (byte[])queueSend.poll();
				}
    		}
	        if (mData == null)
	        {
	        	mLogger.d("TX Data length:" );
	        }
	        else
	        {
        		mLogger.d("TX Data length:" + mData.length );
	        	if (mData.length > 0)
	        	{
	        		mWaitAck = true;
	        		if (!retransmission)
	        		{
	        			mAckFailCounter = 0;
	        		}
	        		mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_DATA_TRANSMIT_TIMEOUT ),MSG_DATA_TRANSMIT_DELAY);
	        		try
	        		{
	        			mIpodControl.communicationAccessoryTransferData(mSessionID, mData);
	        		}
	        		catch( Exception e )
	                {
	                    mLogger.e( "failure: ", e);
	                    e.printStackTrace();
	                }
	        		
	        	}
	        }
		}
	}
	
    /*
     * reads a recived buffer from iOS application (iPhone)
     * returns the buffer or null when empty
     */
	public byte[] iAPRead()
	{
		try {
            byte[] data;
            data = (byte[])queueRcv.take();
	    	mLogger.d("Rcv Data length:" + data.length);
        	return data;
		} catch (java.lang.InterruptedException e) {
			mLogger.e("InterruptedException ", e );
			return null;
        }
	}
	
	/*
	 * receives a buffer to send to the iOS Application (iPhone)
	 * returns the length that will be sent
	 */
	public int iAPWrite(byte[] data, int length)
	{
		int remaining = length;
		if ((data != null) && (data.length > 0) && (length <= data.length))
		{
			int idx = 0;

			do {
				int currentSize = Math.min( remaining, mMaxPayloadSize );

				byte[] tmp = new byte[currentSize];
				System.arraycopy( data, idx, tmp, 0, currentSize );
				synchronized ( queueSend ) {
					queueSend.add(tmp);
				}
				remaining -= currentSize;
				idx += currentSize;

			} while ( remaining > 0 );

			mHandler.sendMessage( mHandler.obtainMessage( MSG_DATA_TRANSMIT_QUEUE_NOT_EMPTY ) );
			return length;
		}
		else {
        	return 0;
		}
	}
	
	/*
	 * Listener for Ipod Authentication event
	 */
    public class IapAuthenticationListener implements OnAuthenticationListener
    {
    	public IapAuthenticationListener()
    	{
    	        mLogger.d( "IapAuthenticationListener()");
    	}

    	/*
    	 * my Authentication implementation
    	 */
        public void onAuthenticationFinished(boolean status, IpodControl ic)
        {
            if (status)
            {
            	mLogger.i( "onAuthenticationFinished() SUCCESSFULLY");
            	RoadTrackSPP.getInstance().sppIapConnected();
            	try
            	{
            		mIpodControl.communicationSetUpRequestApplicationLaunch("com.roadtracktelematics.Chevy");
            	}
        		catch( Exception e )
                {
                    mLogger.e( "failure: ", e);
                    e.printStackTrace();
                }
            }
            else
            {
            	mLogger.i( "onAuthenticationFinished() FAILED");
            	RoadTrackSPP.getInstance().sppIapDisconnected(true);//disconnect with retry
            }
        }
        
        /*
         * my iplementation for event Max pay load size (max data sizes) for communiaction with the iPhone.
         */
        public void onNotifyMaxPayloadSize(int maxpayloadsize, IpodControl ic)
        {
            mLogger.i( "onNotifyMaxPayloadSize(), Max payload size = " + maxpayloadsize);
            mMaxPayloadSize = maxpayloadsize;

        }
    }
    
    
    private void setupIpodControl(){
    	try
    	{
    		mLogger.d( "setupIpodControl()");
    		mIpodControl.setup();
    		mIpodControl.setBtRfcommConnection(false);
    		mIpodControl.communicationSetProtocolString((byte)1, "com.roadtracktelematics");// protocol to be used for communication between Accessory (P8) and the iOS application
    		mIpodControl.communicationSetBundleSeedIDString("7SUPEECNG2");//Chevy application BundleSeed. Apple Code of RoadTrack IOS developer of the Chevy application.
    		mIpodControl.setOnAuthenticationListener(mOnAuthenticationListener);
    		mIpodControl.setOnCommunicationListener(mOnCommunicationListener);
    	}
		catch( Exception e )
        {
            mLogger.e( "failure: ", e);
            e.printStackTrace();
        }
     }
    
    /*
     * Listener for Ipod iAP communiction events
     */
    public class IapCommunicationListener implements IpodControl.OnCommunicationListener { 
    
    	/* Notify the Accessory that the iOS application has opened a data session with the protocol specified by 'index' */
    	public void onOpenDataSession(byte index, int sessionID, IpodControl ic)
    	{
    		mLogger.i("onOpenDataSession, index:" + index + " sessionID:" + sessionID);
    		mSessionID =  sessionID;
    	}
    	
		/* Notify the Accessory the result sent to iOS application, whether the openning of the session was accepted or not by the Accessory */
    	public void onOpenDataSessionStatus(boolean result, IpodControl ic)
    	{
    		mLogger.i("onOpenDataSessionStatus, result:" + result);
    	    mSessionIDResult = result;
    	    // if queues were not cleared on close session then check if we have something to send
    	    if (result)
    	    {
    	    	mHandler.sendMessage( mHandler.obtainMessage( MSG_DATA_TRANSMIT_QUEUE_NOT_EMPTY ) );
    	    }
    	}
    	
    	/* Notify the Accessory that the iOS application has closed the data session */
    	public void onCloseDataSession(int sessionID, IpodControl ic)
    	{
    		mLogger.i("onCloseDataSession, sessionID:" + sessionID);
    		mSessionID = -1;
    		mSessionIDResult = false;
    		//TODO should I clear the queues and mData?
    		mWaitAck = false;
    	}
    
    	/* Notify the Accessory that the iOS application has transferred data to the Accessory under data session specified by 'sessionID' */
    	public void oniPodDataTransfer(int sessionID, byte[] data, IpodControl ic)
    	{
    		mLogger.i("oniPodDataTransfer, sessionID:" + sessionID + " RX data length:" + data.length);
        	mHandler.sendMessage( mHandler.obtainMessage( MSG_DATA_RECEIVED , data) );
    	}
    	
    	/* Notify the Accessory that the Accessory iAP level has transferred data to be sent to the SPP port */
    	public void onAccessoryDataTransferSPP(byte[] data, IpodControl ic)
    	{
    		mLogger.i("onAccessoryDataTransferSPP TX data length:" + data.length);
    		//write to SPP  port
    		RoadTrackSPP sppClient = RoadTrackSPP.getInstance();
    		if (sppClient != null)
    			sppClient.sppClientWrite(data);
    		else
    			mLogger.w("sppClient is NULL!");
    	}
    	
    	/* Notify the Accessory that the iOS application has posted a notification. NOTE: not implemented yet, may be not necessary */
    	public void oniPodNotification(int sessionID, IpodControl ic)
    	{
    		mLogger.w("oniPodNotification, sessionID:" + sessionID);
    	}
    	
    	/* Notify the Accessory the result responsed by the iOS application, for data transferred to the iOS appliction. 'true' means success, 'false' means a failure */
    	public void onAccessoryTransferDataResult(boolean result, IpodControl ic)
    	{
    		int res;
    		res = result ? 1: 0;
    		mLogger.i("onAccessoryTransferDataResult, result:" + result);
    		mHandler.sendMessage( mHandler.obtainMessage( MSG_DATA_TRANSMIT_ACK, res, 0) );
    	}
    
    	/* Notify the Accessory the result responsed by the iOS application, for the request to Launch (run) the iOS application on the iPhone. 
    	 * 'true' means request was received successfully, 'false' means a failure. Note that 'true' is not a guarantee that the appliction is actually running on the iPhone.
    	 */
    	public void onRequestApplicationLaunchResult(boolean result, IpodControl ic)
    	{
    		mLogger.i("onRequestApplicationLaunchResult, result:" + result);
    	}
    }
    
}
