/******************************************************************************
 *
 * Description: implements two Interfaces, SPP Connection and PND interface,
 * together to allow using the Bluetooth connection and make it possible
 * to interact with the opened sockets by SPP.
 ******************************************************************************/
import com.roadtrack.bluetooth.ISPPConnection;
import com.roadtrack.bluetooth.RoadTrackSPP;
import com.roadtrack.bluetooth.RoadTrackSPP.SppConnection;
import com.roadtrack.util.RTLog;
import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PNDBluetooth extends PNDInterface implements ISPPConnection {
    private static final String  TAG   = "PNDBluetooth";

    private RoadTrackSPP.SppConnection mConnectionDesc  = null;
    private PNDInterfaceMgr mPNDInterfaceMgr            = null;
    private Thread          mReaderThread               = null;
    private volatile boolean mIsConnected               = false;
    /*package*/ static volatile boolean miAPConnected              = false;

    private RTLog mLogger  = RTLog.getLogger(TAG, "pndbt", RTLog.LogLevel.INFO );

    /**
     * Constructor
     * registers PNDBluetooth the the SPP bluetooth profile.
     * And creates a new instance of PNDProtocol.
     */
    public PNDBluetooth(Context context, PNDInterfaceMgr pndInterfaceMgr) {
        RoadTrackSPP.registerClient(this);
        mLogger.d( "Registered Client");
        mPNDInterfaceMgr = pndInterfaceMgr;
    }

    /**
     * called when connection is istableshed. receives SppConnection 
     * class instance, holding connection details and runs a thread 
     * listening on the socket.
     * @param connectionDesc holding connection data (socket address)
     */
    public void connectionInitiated(SppConnection connectionDesc) {
        mConnectionDesc = connectionDesc;
        mIsConnected = true;
        if (mConnectionDesc.iAPconnected())
        	miAPConnected = true;
        else
        	miAPConnected = false;
        
        mLogger.i( "Connection Initiated. miAPConnected:" + miAPConnected);

        mReaderThread = new Thread( new Runnable() {
            @Override
            public void run() {
                pndRead();
            }
        }, "PNDThread");
        mReaderThread.start();
    }

    /**
     * called when connection is lost, indicates to PNDProtocol
     * that PND is disconnected.
     */
    public void connectionLost() {
        //Indication off
        mIsConnected = false;
        miAPConnected = false;
        mPNDInterfaceMgr.interfaceConnectionLost();
    }

    /**
     * Returns the required Client SPP connection event to be used for connectionInitiated()
     */
    public EClientSppEventMode getClientSppEventMode() {
    	return ISPPConnection.EClientSppEventMode.EventMode_iAP;
    }

    /**
     * connects to socket and reads buffer of data
     * @return null on failure and data on success
     */
    private byte[] read() {
        if (mIsConnected == true) {
            byte[] data;
        	data = mConnectionDesc.read();
        	return data;
        }
        return null;
    }

    /**
     *
     *
     */
    public void sendMessage(byte[] data) {
        if ((mIsConnected == true) && (data.length > 0)) {
        	int writeLen;
        	writeLen = mConnectionDesc.write(data, data.length);
        	if (writeLen == -1) {
            	mLogger.e("Error Write Len: " + writeLen );
        	}
        } 
    }

    /**
     * contains a loop to read on socket, when a buffer is received,
     * it send's it to devideToMessages and receives a list which is 
     * sent to the msg Handler to pass it through to the PND Protocol for parsing
     */
    private void pndRead() {
        while (mIsConnected) {
            byte[]  dataBuf;
            int length;
            dataBuf = read();

            mLogger.d(Arrays.toString(dataBuf));

            if ( (dataBuf != null) && (dataBuf.length > 0) ) {
                List<byte[]> msgList = divideToMessages(dataBuf);

                if ( msgList != null ) {
                    for(byte[] msg : msgList) {
                        mPNDInterfaceMgr.receiveMessage(msg, PNDInterfaceMgr.PND_INTERFACE_BLUETOOTH);
                    }
                }
            }
        }
    }
}
