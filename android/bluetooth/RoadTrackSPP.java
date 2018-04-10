//SPP client
import android.bluetooth.BluetoothSpp;
import com.roadtrack.bluetooth.BTDevicesService;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.Parcel;
import android.os.Binder;
import android.util.Log;
import android.os.SystemClock;
import android.os.*;

import com.csr.bluetooth.BluetoothIntent;

import android.roadtrack.indicationmgr.IndicationManager;
import android.roadtrack.indicationmgr.IndicationMgrParams;
import android.roadtrack.pconf.PConf;
import android.roadtrack.pconf.PConfParameters;
import com.roadtrack.bluetooth.ISPPConnection;
import com.roadtrack.util.RTLog;

import java.util.UUID;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.*;
import java.nio.ByteBuffer;

import com.roadtrack.pnd.PNDBluetooth;

public class RoadTrackSPP extends Binder {
	private static final String TAG = "RoadTrackSPP";
	private RTLog mLogger;
	private Context mContext;
	private Thread  mSPPThread = null;
	private Thread mSPPClientThread;
	private Thread mIapConnectThread;
	private Handler mHandler;
	private static  RoadTrackSPP sppClient = null;
	private static final String spp_uuid_st = "00001101-0000-1000-8000-00805f9b34fb";
	public static final UUID Spp = UUID.fromString(spp_uuid_st);
	private BluetoothAdapter mBTAdapter;
	private boolean mBluetoothOn = false;
	private static final boolean SPP_LOOBACK_TEST = false;
	private static List<ISPPConnection> mClient = new ArrayList<ISPPConnection>();
	private int exceptionRetries = 0;
	private BluetoothDevice mDevice = null;
	private long mExepctionTime = 0;
	private PConf mPconf;
	private int mDisabledServices;

	private boolean miAP_connected = false;
	private volatile boolean mEnableServer = false;
	private class DevConnection {
		BluetoothServerSocket serverSocket = null;
		BluetoothSocket devSocket = null;
		InputStream devInputStream = null;
		OutputStream devOutputStream = null;
		boolean inUse = false;
		String address = null;
	};

	// Client SPP
	private int mSppPortId = 0;
	private String miAP_RetryAddress = "";
	
    private BluetoothServerSocket miAPserverSocket = null;
    private BluetoothSocket miAPdevSocket = null;
    private InputStream miAPdevInputStream = null;
    private OutputStream miAPdevOutputStream = null;
	
	/*package*/
	static volatile boolean mSppConnected = false;
	private BluetoothSpp mBluetoothSpp = new BluetoothSpp();

	private String miAPaddress = "";

	private static final int WORK_BUFFER_SIZE = 5000;         	// The size of the temp work buffer used for reading data
																	// from the bluetooth connection.
	private byte[] mWorkBuffer = new byte[ WORK_BUFFER_SIZE ];   	// This is a temporary buffer used for holding data to read
																	// from bluetooth before being copied into final buffer.

	private static final int IAP_WORK_BUFFER_SIZE = 5000;        // The size of the temp work buffer used for reading RAW SPP data
																	// from the bluetooth connection to send to external/iAP low level
	private byte[] miAPWorkBuffer = new byte[ IAP_WORK_BUFFER_SIZE ];	// This is a temporary buffer used for reading the RAW SPP data
	
	// local messages types
	private static final int MSG_IAP_CONNECTED = 0;
	private static final int MSG_CONNECT_CLIENT_SPP = 1;
	private static final int MSG_CONNECT_CLIENT_SPP_TIMEOUT = 2;
	private static final int MSG_DISCONNECT_CLIENT_SPP = 3;
	private static final int MSG_DISCONNECT_CLIENT_SPP_TIMEOUT = 4;
	private static final int MSG_AUTHENTICATION_TIMEOUT = 5;
	private static final int MSG_IAP_CONNECT_RETRY = 6;

	//Timeouts
	private static final int MSG_CONNECT_CLIENT_SPP_DELAY = 2000;
	private static final int MSG_DISCONNECT_CLIENT_SPP_DELAY = 2000;

	private DevConnection devConnections = new DevConnection();
	public SppConnection mSppConnection = new SppConnection(this);

	public static void registerClient(ISPPConnection client) {
		mClient.add(client);
	}

	/*package*/ static RoadTrackSPP getInstance() {
		return sppClient;
	}

	/*
	 * called by RT iAP communication when iAP is connected
	 */
	void sppIapConnected() {
		mHandler.sendMessage( mHandler.obtainMessage( MSG_IAP_CONNECTED ));
	}

	/*
	 * called by RT iAP communication when iAP needs to be disconnected
	 * if retry = TRUE, try to reconnect to last device after some delay.
	 */
	void sppIapDisconnected(boolean retry) {
		if (retry && !miAPaddress.isEmpty())
		{
			//save last conneted device
			miAP_RetryAddress = miAPaddress;
		}
		mHandler.sendMessage( mHandler.obtainMessage( MSG_DISCONNECT_CLIENT_SPP ));
	}



	/*
	 * called by BTDevices to connect client SPP for iAP
	 */
	void connectAsClientToSPP(final String addr) {
		mHandler.sendMessage( mHandler.obtainMessage( MSG_CONNECT_CLIENT_SPP, addr ));
	}

	/*
	 * Request to send iAP messages to client SPP port
	 */
	public int sppClientWrite(byte [] data) {
		int length = data.length;
		mLogger.d("Client SPP. mSppConnected:" + mSppConnected + " length:" + length );
		if (mSppConnected && (length > 0) )
		{
			int writeLen;
			try {
				mLogger.d( "Write Data to SPP ");
				miAPdevOutputStream.write(data, 0, length);
				return (length);
			} catch (NullPointerException e) {
				mLogger.e( "Write Null stream ", e);
				disconnectClientSPP(miAPaddress);
				return -1;
			} catch (IOException e) {
				mLogger.e( "Write Exception ", e);
				disconnectClientSPP(miAPaddress);
				return -1;
			}
		}
		else
		{
			mLogger.d("Client SPP send:" + length);
			return 0;
		}
	}
	
	private void disconnectClientSPP(final String addr) {
		mHandler.sendMessage( mHandler.obtainMessage( MSG_DISCONNECT_CLIENT_SPP, addr ));
	}

	private void notifyIapClientsSppConnected() {
		if (!devConnections.inUse) {
			devConnections.address = miAPaddress;
			mSppConnection.setSppData();
			if(mClient != null) {
				mLogger.i("notify iAP clients on client SPP connection");
				for (ISPPConnection client : mClient) {
					if (client.getClientSppEventMode() == ISPPConnection.EClientSppEventMode.EventMode_Spp)
						client.connectionInitiated( mSppConnection );
				}
			}
			mLogger.i("connected client SPP");
		}		
	}

	private void notifyIapClientsSppDisconnected() {
		if (!devConnections.inUse) {
			if(mClient != null) {
				mLogger.i("notify iAP clients on client SPP disconnection");
				for (ISPPConnection client : mClient) {
					if (client.getClientSppEventMode() == ISPPConnection.EClientSppEventMode.EventMode_Spp)
						client.connectionLost();
				}
			}
		}		
	}
	
	private void disconnectIapClients() {
		if (miAP_connected) {
			miAP_connected = false;
			if(mClient != null) {
				for (ISPPConnection client : mClient) {
					if (client.getClientSppEventMode() == ISPPConnection.EClientSppEventMode.EventMode_iAP)
						client.connectionLost();
				}
				IndicationManager.setIndication(mContext,IndicationMgrParams.EIND_BT_BLINKING_BLUE_FAST_INFINITE, false);
			}
			mLogger.i("disconnected iAP - SPP");
		}
	}

	private void terminateIapSpp() {
		mSppConnected = false;
		closeClientSppSocket();
		mSppPortId = 0;
		miAPaddress = "";
		if (!isSPPconnected() && !miAP_RetryAddress.isEmpty()) {
			mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_IAP_CONNECT_RETRY ), RTiAPCommunication.MSG_DATA_TRANSMIT_DELAY);
		}
	}

	public class SppConnection {

		private RoadTrackSPP mSppManager;
		public  String address;

		private void setSppData() {
			address = mSppManager.devConnections.address;
		}

		public SppConnection(RoadTrackSPP manager) {
			mSppManager = manager;
			setSppData();
		}

		public void Disconnect() {
			if (devConnections.inUse) {
				mSppManager.disconnectSPP();				
			}
			if ((miAPaddress != null) && (!miAPaddress.isEmpty())) {
				disconnectClientSPP(miAPaddress);
			}
		}

		public boolean isConnected() {
			return mSppManager.isSPPconnected();
		}
		
		public boolean iAPconnected() {
			return mSppManager.is_iAPconnected();
		}


		public byte[] read() {
			return mSppManager.sppRead();
		}

		public int write(byte [] data, int length) {
			return mSppManager.sppWrite(data, length);
		}
	}

	public RoadTrackSPP(Context context) {
		mLogger = RTLog.getLogger(TAG, "rtspp", RTLog.LogLevel.INFO );
		mLogger.d( "SPP Service Created");
		mContext = context;
		if (sppClient != null) {
			throw new RuntimeException("You cannot create a new instance of this class");
		}
		sppClient = this;

		mPconf = (PConf)context.getSystemService(context.PCONF_SYSTEM_SERVICE);
		mDisabledServices = (mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0) & BTDevicesService.DISABLE_SRV_SPP);

		// iAP Connect/Disconnect thread
		mIapConnectThread = new Thread( new Runnable() {
			@Override
			public void run() {
				mLogger.i(  "iAP Connect/Disconnect thread started" );
				Looper.prepare();
				mHandler = new Handler() {
					@Override
					public void handleMessage( Message msg ) {
						messageHandler( msg );
					}
				};
				Looper.loop();
			}
		}, "IapConnectThread");
		mIapConnectThread.start();
		RTiAPCommunication.getInstance() ; // just create the instance

		// Register filter to receive Bluetooth ON/OFF intents
		IntentFilter bluetoothStatusFilter = new IntentFilter();
		bluetoothStatusFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
//        bluetoothStatusFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		bluetoothStatusFilter.addAction(BluetoothIntent.ACTION_SPP_DISCONNECT_IND);
		mContext.registerReceiver(mBluetoothStatusReceiver, bluetoothStatusFilter);

	}

	/**
	 * If SPP server connects to socket and reads buffer of data
	 * If SPP client (iAP with iPhone) reads data from iAP recieve link.
	 * receives byte array as param and fills it with the read data
	 * @return -1 on failure and >= 0 on success
	 */
	byte[] sppRead() {
		if (isSPPconnected() == true) {
			byte[] data;
			if (miAP_connected) {
				// SPP Client. iAP channel - iPhone
				data = RTiAPCommunication.getInstance().iAPRead();
				if (data != null) {
					mLogger.d("length:" + data.length);
					return (data);
				} else {
					mLogger.d("length:" );
					return null;
				}
			} else {
				// SPP Server
				try {
					int readLen = devConnections.devInputStream.read( mWorkBuffer );
					if (readLen > 0) {
						mLogger.d("Read Len: " + readLen );
						byte[] actual = new byte[ readLen ];
						System.arraycopy( mWorkBuffer, 0, actual, 0, readLen );
						return actual;
					} else {
						mLogger.d("Read Len: null" );
						return null;
					}
				} catch (IOException e) {
					mLogger.e( "Read Exception ", e);
					disconnectSPP();
					return null;
				}
			}
		}
		return null;
	}

	private int sppWrite(byte [] data, int length) {
		int writeLen;
		if ((isSPPconnected() == true) && (length > 0) && (length <= data.length)) {
			if (miAP_connected) {
				//SPP Client. iAP channel -iPhone
				writeLen = RTiAPCommunication.getInstance().iAPWrite(data, length);
				return (writeLen);
			} else {
				//SPP server
				try {
					writeLen = length;
					mLogger.d( "Write Data to SPP ");
					devConnections.devOutputStream.write(data, 0, length);
					return (writeLen);
				} catch (NullPointerException e) {
					mLogger.e( "Write Null stream ", e);
					disconnectSPP();
					return -1;
				} catch (IOException e) {
					mLogger.e( "Write Exception ", e);
					disconnectSPP();
					return -1;
				}
			}
		} else {
			return -1;
		}
	}

	// return true if iAP is connected
	private boolean is_iAPconnected()
	{
		if (miAP_connected)
			return true;
		else
			return false;
	}
	
	// returns true if SSP connected
	private boolean isSPPconnected() {
		if (devConnections.inUse || miAP_connected)
			return true;
		else
			return false;
	}

	private void disconnectSPP() {
		closeSockets();
	}

	private void closeSockets() {
		mLogger.d("server");
		try {
			if (devConnections.devSocket != null) {
				devConnections.devSocket.close();
			}
			if (devConnections.serverSocket != null) {
				devConnections.serverSocket.close();
			}
		} catch (IOException e) {
			mLogger.e("Close Exception ", e);
		}
		devConnections.serverSocket = null;
		devConnections.devSocket = null;
		devConnections.devOutputStream = null;
		devConnections.devInputStream = null;
		devConnections.inUse = false;
		devConnections.address = null;
	}

	private void sleep() {
		try {
			Thread.sleep(100); //ms
		} catch (Exception e) {
			mLogger.e("failed to sleep: ", e);
		}
	}

	/*******************************
	 * Run IAP Looper thread
	 ********************************/
	private void messageHandler( Message msg ) {
		String address;

		switch ( msg.what ) {
		case MSG_IAP_CONNECTED:
			/*
			 * iAP connected Authenticated and ready. Tell registerd clients (PND)
			 */
			mLogger.d("msg: MSG_IAP_CONNECTED ");
			mHandler.removeMessages(MSG_AUTHENTICATION_TIMEOUT);
			if (!devConnections.inUse) {
				miAP_connected = true;
				devConnections.address = miAPaddress;
				mSppConnection.setSppData();
				if(mClient != null) {
					mLogger.i("notify clients on iAP SPP connection");
					for (ISPPConnection client : mClient) {
						client.connectionInitiated( mSppConnection );
					}
				}
				mLogger.i("connected iAP - SPP");
			}
			else
			{
				mLogger.w("Server spp already connected:" + devConnections.address + " Disconnected iAP - SPP:" + miAPaddress);
				terminateIapSpp();
			}
			break;
		case MSG_CONNECT_CLIENT_SPP:
			/*
			 * request to connect client SPP for iAP
			 */
			address = (String) msg.obj;
			mLogger.d("msg: MSG_CONNECT_CLIENT_SPP " + address);
			mHandler.removeMessages(MSG_IAP_CONNECT_RETRY);
			miAP_RetryAddress = "";
			mLogger.d("Bluetooth:" + mBluetoothOn);
			if (!mBluetoothOn) {
				break;
			}
			if (devConnections.inUse || mSppConnected)
			{
				mLogger.w("SPP already connected. devConnections.inUse:" + devConnections.inUse + " mSppConnected:" + mSppConnected);
				break;
			}
			// stop server SPP thread;
			mEnableServer = false;
			disconnectSPP();
			
			mSppConnected = connectClientSppSocket(address);
			if (mSppConnected) {

				mLogger.i("SPP iAP connected successfully! address:" + address);
				miAPaddress = address;
//				dataBuf = new byte[1500];
				// run SSP thread
				mSPPClientThread = new Thread( new Runnable() {
					@Override
					public void run() {
						SPPClientRcvThreadMethod();
					}
				}, "SPPClientRcvThread");
				mSPPClientThread.start();
				notifyIapClientsSppConnected();
				mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_CONNECT_CLIENT_SPP_TIMEOUT ),MSG_CONNECT_CLIENT_SPP_DELAY);

			} else {
				mLogger.e("SPP iAP connect failed!");
				mEnableServer = true;
				if (mSPPThread == null)
				{
					// now run server SPP thread
					mSPPThread = new Thread( new Runnable() {
						@Override
						public void run() {
							SPPThreadMethod();
						}
					}, "SPPThread");
					mSPPThread.start();
				}
			}
			break;
		case MSG_CONNECT_CLIENT_SPP_TIMEOUT:
			/*
			 * Timeout after connect client SPP (iAP)
			 * Start iAP connect to remote iPhone. Now can start IDPS process and Authentication
			 */
			mLogger.d("msg: MSG_CONNECT_CLIENT_SPP_TIMEOUT");
			if (mBluetoothOn && mSppConnected) {
				mLogger.i("SPP iAP connected, start IDPS + authentication with remote");
				RTiAPCommunication.getInstance().startBtRfcommConnection(true);
				RTiAPCommunication.getInstance().startUpAuthentication();
				mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_AUTHENTICATION_TIMEOUT ),RTiAPCommunication.AUTHENTICATION_TIMEOUT_DELAY);
			}
			break;
		case MSG_DISCONNECT_CLIENT_SPP:
			/*
			 * request to disconnect iAP and client SPP used by iAP
			 */
			address = (String) msg.obj;
			mLogger.d("msg: MSG_DISCONNECT_CLIENT_SPP " + address + " mSppConnected:" + mSppConnected);
			mHandler.removeMessages(MSG_AUTHENTICATION_TIMEOUT);
			mHandler.removeMessages(MSG_IAP_CONNECT_RETRY);
			disconnectIapClients();
			RTiAPCommunication.getInstance().startBtRfcommConnection(false);
			if (mSppConnected) {
				if (mSppPortId != 0) {
					mBluetoothSpp.disconnect(mSppPortId);
					mLogger.d("MSG_DISCONNECT_CLIENT_SPP_DELAY:" + MSG_DISCONNECT_CLIENT_SPP_DELAY);
					mHandler.sendMessageDelayed( mHandler.obtainMessage( MSG_DISCONNECT_CLIENT_SPP_TIMEOUT ),MSG_DISCONNECT_CLIENT_SPP_DELAY);
				} else {
					mLogger.i( "Client SPP disconnected successfully!");
					notifyIapClientsSppDisconnected();
					terminateIapSpp();
				}
			} else {
				mLogger.i( "Client SPP disconnected successfully!");
				mHandler.removeMessages(MSG_DISCONNECT_CLIENT_SPP_TIMEOUT);
				notifyIapClientsSppDisconnected();
				terminateIapSpp();
			}
			break;
		case MSG_DISCONNECT_CLIENT_SPP_TIMEOUT:
			/*
			 * timeout after disconnecting client SPP (iAP)
			 */
			mLogger.d("msg: MSG_DISCONNECT_CLIENT_SPP_TIMEOUT");
			if (mSppConnected) {
				if (mSppPortId != 0) {
					if (mBluetoothSpp.getConnectionState(mSppPortId) == BluetoothSpp.CSR_BT_SPP_CONNECTED) {
						// failed to disconnect behave as disconnected.
						mLogger.e( "SPP iAP failed to disconnect!");
					} else {
						mLogger.i( "SPP iAP disconnected successfully!");
					}
				}
			}
			notifyIapClientsSppDisconnected();
			terminateIapSpp();
			break;
		case MSG_AUTHENTICATION_TIMEOUT:
			/*
			 * Autentication failed to start
			 */
			mLogger.d("msg: MSG_AUTHENTICATION_TIMEOUT");
			mLogger.i("iAP Authentication timed out");
			sppIapDisconnected(false);
			break;
		case MSG_IAP_CONNECT_RETRY:
			/*
			 * iAP connect retry timeout. Try to reconnect iAP SPP if SPP is not already connected.
			 */
			mLogger.d("msg: MSG_IAP_CONNECT_RETRY");
			mLogger.i("iAP retrying connect...");
			if (!miAP_RetryAddress.isEmpty() && BTDevicesService.getInstance().isOkayToConnect())
			{
				if (!isSPPconnected() && miAPaddress.isEmpty()) {
					miAPaddress = miAP_RetryAddress;
					mHandler.sendMessage( mHandler.obtainMessage( MSG_CONNECT_CLIENT_SPP, miAPaddress ));
				}
			}
			miAP_RetryAddress = "";
			break;
			
		default:
			mLogger.w(  "messageHandler:unknown msg " + msg.what);
			break;
		}

	}
	
	/*************************************************
	 * Client SPP Rcv thread to receive iAP protocol data
	 ************************************************/
	private void SPPClientRcvThreadMethod() {
		mLogger.i("Started Client SPP Rcv Thread");
		int readLen;
		while (mSppConnected )
		{
			try {
				readLen = miAPdevInputStream.read( miAPWorkBuffer );
				if (readLen > 0) {
					mLogger.d("Read Len: " + readLen );
					byte[] actual = new byte[ readLen ];
					System.arraycopy( miAPWorkBuffer, 0, actual, 0, readLen );
					RTiAPCommunication.getInstance().iAPIpodTransferDataRaw(actual);
				} else {
					mLogger.d("Read Len: null" );
				}
			} catch (IOException e) {
				mLogger.e( "Read Exception ", e);
//				disconnectClientSPP(miAPaddress);;
				break;
			}
			
		}
	}
	
private boolean connectClientSppSocket(String address)
{
	miAPdevSocket = null;
	miAPdevInputStream = null;
	miAPdevOutputStream = null;
	BluetoothAdapter btAdapter;
	
	btAdapter = BluetoothAdapter.getDefaultAdapter();
 	if (btAdapter == null)
 	{
	 		mLogger.e( "Bluetooth not supported. Aborting init!!");
	 		//closeiAPSockets();
	 		return false;
 	}

	BluetoothDevice btDevice = null;
	Set<BluetoothDevice> bondedDevices = btAdapter.getBondedDevices();
	for (BluetoothDevice dev : bondedDevices) {
	    if (dev.getAddress().equals(address)) {
	        btDevice = dev;
	    }
	}
	
	if (btDevice == null) {
	    mLogger.e("Target Bluetooth device is not found.");
	    return false;
	}
	
	try {
		miAPdevSocket = btDevice.createRfcommSocketToServiceRecord(Spp);
	} catch (IOException ex) {
	    mLogger.i("Failed to create RfComm socket: " + ex.toString());
	    return false;
	}
	mLogger.i("Created a Client bluetooth socket.");

	for (int i = 0; ; i++) {
	    try {
	    	miAPdevSocket.connect();
	    } catch (IOException ex) {
	        if (i < 5) {
	            mLogger.i("Failed to connect. Retrying: " + ex.toString());
	            continue;
	        }

	        mLogger.i("Failed to connect: " + ex.toString());
	        closeClientSppSocket();
	        return false;
	    }
	    break;
	}

	mLogger.i("Connected as Client to the bluetooth socket.");
	try {
		miAPdevOutputStream = miAPdevSocket.getOutputStream();
	} catch (IOException ex) {
		mLogger.i("Failed to get out stream: " + ex.toString());
		closeClientSppSocket();
		return false ;
	}
	
	try {
		miAPdevInputStream = miAPdevSocket.getInputStream();
	} catch (IOException ex) {
		mLogger.i("Failed to get in stream: " + ex.toString());
		closeClientSppSocket();
		return false ;
	}
	
//	int mChannel[] = new int[BluetoothSpp.BLUETOOTH_SPP_MAX_INSTANCE_NUMBER];
//	mChannel = mBluetoothSpp.getAllInstances(address, spp_uuid_st);
//	if (mChannel[0] != 0) {
//		mSppPortId = mChannel[0];
//		boolean connected = mBluetoothSpp.getConnectionState(mSppPortId) == BluetoothSpp.CSR_BT_SPP_CONNECTED ? true : false;
//		if (connected)
//		{
//			mSppPortId = mChannel[0];
//			mLogger.d("client mSppPortId:" + mSppPortId);
//		}
//		else
//		{
//			mSppPortId = 0;
//		}
//	}
	return true;
}
	private void closeClientSppSocket()
	{
		mLogger.d("client close socket");
		try {
			if (miAPdevSocket != null) {
				miAPdevSocket.close();
			}
		} catch (IOException e) {
			mLogger.e("Close Exception ", e);
		}
		miAPdevSocket = null;
		miAPdevOutputStream = null;
		miAPdevInputStream = null;	
	}
	
	/*******************************
	 * Run SPP 1
	 ********************************/
	private void SPPThreadMethod() {

		mLogger.i("Started SPP1 Server Thread");

		devConnections.inUse = false;
		devConnections.serverSocket = null;
		devConnections.devSocket = null;
		devConnections.devOutputStream = null;
		devConnections.devInputStream = null;
		devConnections.address = null;

		mBTAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBTAdapter == null) {
			mLogger.e("Bluetooth not supported. Aborting");
			return;
		}

		while (mEnableServer) {
			exceptionRetries++;
			while (exceptionRetries >= 4) {
				if (exceptionRetries == 4) {
					exceptionRetries++;
					mExepctionTime = SystemClock.uptimeMillis();
					mLogger.e("Too many execptions. Aborting");
				}
				sleep();
				if ((SystemClock.uptimeMillis() - mExepctionTime) > 30000) {
					exceptionRetries = 0;//try to reconnect
				}
			}

			if (!mBTAdapter.isEnabled()) {
				mBluetoothOn = false;
				mLogger.d("Adapter is off! wait..");
			} else {
				mBluetoothOn = true;
			}

			// Wait for Bluetooth on event and SPP service is enabled
			mDisabledServices = (mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0) & BTDevicesService.DISABLE_SRV_SPP);
			while ((mBluetoothOn == false) || (mDisabledServices == BTDevicesService.DISABLE_SRV_SPP)) {
				sleep();
				mDisabledServices = (mPconf.GetIntParam(PConfParameters.BT_DisabledServices, 0) & BTDevicesService.DISABLE_SRV_SPP);
				continue;
			}
			mLogger.d("Adapter is on");

			if (devConnections.serverSocket == null) {
				try {
					mLogger.d("Creating Listening socket");
					devConnections.serverSocket = mBTAdapter.listenUsingRfcommWithServiceRecord("RoadTrack SPP1", Spp);
				} catch (IOException e) {
					mLogger.e("Failed to create Listening socket. Aborting: ", e);
					sleep();
					continue;
				}
			}

			mLogger.i("Waiting for incoming connection");

			try {
				mLogger.d("Creating Client socket");
				devConnections.devSocket = devConnections.serverSocket.accept(-1);
			} catch (IOException e) {
				mLogger.e("Exception ", e);
			}

			if (devConnections.devSocket == null) {
				mLogger.e("Accept connection failed");
				devConnections.serverSocket = null;
				closeSockets();
				sleep();
				continue;
			} else {
				mLogger.i("Accept connection succeeded");
			}

			try {
				mLogger.d("Getting output stream");
				devConnections.devOutputStream = devConnections.devSocket.getOutputStream();
			} catch (IOException e) {
				mLogger.e("Exception ", e);
				closeSockets();
				sleep();
				continue;
			}

			try {
				mLogger.d("Getting Input stream");
				devConnections.devInputStream = devConnections.devSocket.getInputStream();
			} catch (IOException e) {
				mLogger.e("Exception ", e);
				closeSockets();
				sleep();
				continue;
			}

			if (devConnections.devSocket != null) {
				mDevice  = devConnections.devSocket.getRemoteDevice();
			}
			if (mDevice != null) {
				devConnections.address = mDevice.getAddress();
			}
			mLogger.i("remote address: " + devConnections.address);

			if ((miAPaddress != null) && !miAPaddress.isEmpty())
			{
				mLogger.w("Server SPP waiting.. iAP already connected:"  + miAPaddress);
				closeSockets();
				sleep();
				continue;
			}
			
			devConnections.inUse = true;
			mSppConnection.setSppData();
			mLogger.i("connected SPP1");

			if(mClient != null) {
				mLogger.i("notify clients on SPP connection");
				for (ISPPConnection client : mClient) {
					client.connectionInitiated( mSppConnection );
				}
			}

			// Wait for disconnect
			while (devConnections.inUse == true) {
				sleep();
				continue;
			}
			exceptionRetries = 0;
			if(mClient != null) {
				for (ISPPConnection client : mClient) {
					client.connectionLost();
				}
				IndicationManager.setIndication(mContext,IndicationMgrParams.EIND_BT_BLINKING_BLUE_FAST_INFINITE, false);
			}
		}
		mSPPThread = null;
		mLogger.i("terminated Server SPP1 thread");
	}

	/*******************************
	 * Check Bluetooth On/Off state
	 ********************************/
	private BroadcastReceiver mBluetoothStatusReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				mLogger.v("Bluetooth ON/OFF state changed");
				switch (bluetoothState) {
				case BluetoothAdapter.STATE_ON:
					mLogger.d("Bluetooth ON");
					// Start connect
					exceptionRetries = 0;
					mBluetoothOn = true;
					break;
				case BluetoothAdapter.STATE_TURNING_OFF:
					mBluetoothOn = false;
					mLogger.d("Bluetooth turning OFF");
					break;
				case BluetoothAdapter.STATE_OFF:
					mBluetoothOn = false;
					mLogger.d("Bluetooth OFF");
					break;
				default:
					break;
				}
			} else if (action.equals(BluetoothIntent.ACTION_SPP_DISCONNECT_IND)) {
				int channelId = intent.getIntExtra(BluetoothIntent.SPP_CHANNEL_ID, BluetoothSpp.ERROR);
				int connectDir = intent.getIntExtra(BluetoothIntent.SPP_CON_DIR_ID, BluetoothSpp.ERROR);
				mLogger.d("ACTION_SPP_DISCONNECT_IND" + " channel Id:" + channelId + " direction:" + connectDir);
				if (channelId > 0) {
					if (connectDir == 1) {
						//OUT - Client iAP SPP
						mSppConnected = false;
						disconnectClientSPP(miAPaddress);
					} else {
						//Server
						disconnectSPP();
					}
				}
			}
		}
	};
}