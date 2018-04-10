import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.io.IOException;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCard;
import java.io.ByteArrayOutputStream;
import com.roadtrack.util.LittleEndianOutputStream;



public class ModemSupervisor {

    private RTLog mLogger;
    private static final String  TAG    = "ModemSupervisor";
    private static P2PService   mP2P;
    private Context             mContext;
    private TelephonyManager telephonyManager;
    EMDM_PRIMASimStatus newState = EMDM_PRIMASimStatus.EMDMSS_ICC_UNKNOWN;

    private static String mImsi;
    private static String mImei;
    private static String mCcid;
    private static String mPhone;

    public ModemSupervisor(Context context) {
        mContext = context;
        IntentFilter filter = new IntentFilter();
        mLogger          = RTLog.getLogger(TAG, "modemsupervisor", RTLog.LogLevel.INFO );
        mLogger.d("Constructor");

        // Register for Intent broadcasts for...
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter);
        mP2P = (P2PService)mContext.getSystemService(Context.P2P_SYSTEM_SERVICE);

        // Get the telephony manager
        telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);


    }

        /*
      Enumeration to send SIM status to MCU
     */
    public enum EMDM_PRIMASimStatus {
        EMDMSS_ICC_UNKNOWN,
        EMDMSS_ICC_ABSENT,
        EMDMSS_ICC_NOT_READY,    
        EMDMSS_ICC_LOCKED,
        EMDMSS_ICC_READY,
        EMDMSS_ICC_IMSI,
        EMDMSS_ICC_LOADED
    }

     /*
      Broadcast receiver to handle ACTION_SIM_STATE_CHANGED intent
     */
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                updateSimState(intent);
            }
        }
    };

    // This function is called to send the SIM state to the MCU to update SIM status.
    private void NotifyMcu(EMDM_PRIMASimStatus state) {
        if ( state == null ) {
              mLogger.e("NotifyMcu State null : " + state);
          return;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianOutputStream stream = new LittleEndianOutputStream(baos); 
            stream.writeInt(state.ordinal());
            stream.flush();
            stream.close();  
            mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_GENERAL, MessageCodes.MESSAGE_CODE_GENERAL_SIM_STATUS, baos.toByteArray() );
            mLogger.e("NotifyMcu State  " + state);
        }
        catch ( Exception e ) {
            mLogger.e( "NotifyMcu(EMDM_PRIMASimStatus state) :cannot send message to MCU )", e );
        }
    }

     // This function is called to send the SIM state to the MCU to update SIM status.
    private void NotifyMcu(String data , int code) {
        if ( data == null ) {
              mLogger.e("NotifyMcu data null , code:" + code );
          return;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            LittleEndianOutputStream stream = new LittleEndianOutputStream(baos);     
            stream.writeBytes(data);
            stream.writeByte(0);
            stream.flush();
            stream.close();

            mP2P.sendMessage( MessageGroups.MESSAGE_GROUP_GENERAL, code , baos.toByteArray() );
          
        }
        catch ( Exception e ) {
            mLogger.e( "NotifyMcu(String data , int code) : cannot send message to MCU )", e );
        }
    }

    // This function is called to set SIM info when a broadcast event is received.
    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);

        if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            newState = EMDM_PRIMASimStatus.EMDMSS_ICC_ABSENT;
            mLogger.e("Sim State " + stateExtra);
        }
        else if (IccCard.INTENT_VALUE_ICC_NOT_READY.equals(stateExtra)) {
            newState = EMDM_PRIMASimStatus.EMDMSS_ICC_NOT_READY;
        }
        else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                newState = EMDM_PRIMASimStatus.EMDMSS_ICC_LOCKED;
        }
        else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            newState = EMDM_PRIMASimStatus.EMDMSS_ICC_READY;
        }
        else if (IccCard.INTENT_VALUE_ICC_IMSI.equals(stateExtra)) {
            newState = EMDM_PRIMASimStatus.EMDMSS_ICC_IMSI;
        }
        else if (IccCard.INTENT_VALUE_ICC_LOADED.equals(stateExtra)) {
            newState = EMDM_PRIMASimStatus.EMDMSS_ICC_LOADED;
        }
        else {
            newState = EMDM_PRIMASimStatus.EMDMSS_ICC_UNKNOWN;
            mLogger.e("Sim State unknown " + stateExtra);
        }
        onSimStateChanged(newState);
        NotifyMcu(newState);
    }


    
    private String CompareModemInformation( String newInfo , String oldInfo)
    {



        if(newInfo == null )
        {
            return null;        
        }

        if((oldInfo == null) || (oldInfo.equals(newInfo) == false))
        {
            return newInfo;
        }

    return null;     
    }

    private void onSimStateChanged (EMDM_PRIMASimStatus newState )
    {    
        
        try{    
            //check if we received the IMEI or if changed     
            String tmp = CompareModemInformation(telephonyManager.getDeviceId() , mImei);
            if(tmp != null)
            {
                mImei = tmp;
                NotifyMcu(mImei , MessageCodes.MESSAGE_CODE_GENERAL_MODEM_IMEI);
            }

            //if no sim or sim state is unknown     
            if( newState == EMDM_PRIMASimStatus.EMDMSS_ICC_UNKNOWN || newState == EMDM_PRIMASimStatus.EMDMSS_ICC_ABSENT )
            {
                return;
            }
            else
            {
                //check if we recived the IMSI from sim    
                tmp = CompareModemInformation(telephonyManager.getSubscriberId() , mImsi);
                if(tmp != null)
                {
                    mImsi = tmp;
                    NotifyMcu(mImsi , MessageCodes.MESSAGE_CODE_GENERAL_MODEM_IMSI);
                }
                                
                                
                //check if we recived the CCID from sim               
                tmp = CompareModemInformation(telephonyManager.getSimSerialNumber() , mCcid);
                if(tmp != null)
                {
                    mCcid = tmp;
                    NotifyMcu(mCcid , MessageCodes.MESSAGE_CODE_GENERAL_MODEM_CCID);
                }
            
                //check if we received the phone number
                tmp = CompareModemInformation(telephonyManager.getLine1Number() , mPhone);
                if(tmp != null)
                {
                    mPhone = tmp;
                    NotifyMcu(mPhone , MessageCodes.MESSAGE_CODE_GENERAL_MODEM_OWN_NUMBER);
                }    
            }
        }
        catch ( Exception e ) 
        {
            mLogger.e(  "Modem info transmit failure", e );
        }                
    }
}