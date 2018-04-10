
import com.android.internal.telephony.DriverCall.State;
import android.bluetooth.BluetoothHFPCallDetails;
import android.util.Log;
import com.roadtrack.util.RTLog;

public class RTCall {
	static final String TAG = "RTCall";
	private static RTLog mLogger  = RTLog.getLogger(TAG, "rtcall", RTLog.LogLevel.INFO );

	public enum State {
		ACTIVE(0), 
		HOLDING(1), 
		DIALING(2), // MO
		ALERTING(3), // MO
		INCOMING(4), // MT
		WAITING(5); // MT
		
		State(int val)
		{
            Value = val;
		}
		
		public int Value;

	};

	public enum PhoneNumType {
		GENERAL(0),
		HOME(1), 
		OFFICE(2),
		MOBILE(3),
		OTHER(4),
		EMERGENCY(5),
		ASSISTANCE(6),
		SILENCE(7),
		ZILTOK(8),
		ACCIDENT(9);
		
		PhoneNumType(int val)
		{
            Value = val;
		}
		
		public int Value;

	};
	
	private int 			mIndex; // starting with 1
	private boolean 		mIsMT = false;
	private State 			mState;
	private boolean 		mIsMultiParty;
	private String 			mNumber;
	private boolean 		mIsVoice;
	private String 			mName;
	private PhoneNumType 	mPhoneNumType;
	
	public RTCall(){
		
	}

	static RTCall migrateFrom(BluetoothHFPCallDetails de){
		RTCall bd = new RTCall();
		try{
			
			bd.mIndex = de.getCallIndex();
			bd.mIsMT = (1 == de.getCallDir());
			bd.mState = stateFromDetail(de.getThisCallStatus());
			bd.mIsMultiParty = (1 == de.getMultipartyCall());
			bd.mNumber = de.getCallNumber();
			bd.mIsVoice = (0 == de.getCallMode());
			bd.mName = null;
			bd.mPhoneNumType = PhoneNumType.GENERAL;
		}catch(Exception e){
			mLogger.d("migrate failed: " + e.toString());
			return null;
		}
		
		return bd;
		
	}
	
	static RTCall migrateFromGsm(GsmCallDetails details){
		RTCall call = new RTCall();
		try{
			
			call.mIndex = details.getIndex();
			call.mIsMT = (1 == details.getIsMT() ? true : false);
			call.mState = stateFromDetail(details.getState());
			call.mIsMultiParty = (1 == details.getIsMptyCall() ? true : false);
			call.mNumber = details.getNumber();
			call.mIsVoice = (1 == details.getIsVoice() ? true : false);
			call.mName = details.getName();
			call.mPhoneNumType = PhoneNumType.GENERAL;
		}catch(Exception e){
			mLogger.d("migrate failed: " + e.toString());
			return null;
		}
		
		return call;
		
	}
	
	private static State stateFromDetail(int mState) throws Exception{
		switch(mState) {
        case 0: return State.ACTIVE;
        case 1: return State.HOLDING;
        case 2: return State.DIALING;
        case 3: return State.ALERTING;
        case 4: return State.INCOMING;
        case 5: return State.WAITING;
        default:
            throw new Exception("illegal call mState " + mState);
		}
	}
	
	static boolean compareCall(RTCall bc,RTCall bc1) {
		if (!(bc.isMT() || bc1.isMT()))
			return true;
		return ( (bc.isMT() == bc1.isMT()) && equalsHandlesNulls(bc.getNumber(), bc1.getNumber()) );
	}

	static boolean equalsHandlesNulls(Object a, Object b) {
		return (a == null) ? (b == null) : a.equals(b);
	}
	
	public int getIndex() {
		return mIndex;
	}

	public void setIndex(int mIndex) {
		this.mIndex = mIndex;
	}
	
	public boolean isMT() {
		return mIsMT;
	}

	public void setIsMT(boolean isMT) {
		this.mIsMT = isMT;
	}
	public State getState() {
		return mState;
	}

	public void setState(State mState) {
		this.mState = mState;
	}
		
	public boolean isMpty() {
		return mIsMultiParty;
	}

	public void setIsMpty(boolean mIsMultiParty) {
		this.mIsMultiParty = mIsMultiParty;
	}
	
	public String getNumber() {
		return mNumber;
	}
	
	public void setNumber(String mNumber) {
		this.mNumber = mNumber;
	}
	
	public boolean isVoice() {
		return mIsVoice;
	}

	public void setIsVoice(boolean mIsVoice) {
		this.mIsVoice = mIsVoice;
	}
	
	public String getName() {
		return mName;
	}
	
	public void setName(String mName) {
		this.mName = mName;
	}
	
	public PhoneNumType getPhoneNumType() {
		return mPhoneNumType;
	}
	
	public void setPhoneNumType(PhoneNumType mPhoneNumType) {
		this.mPhoneNumType = mPhoneNumType;
	}
	public String toString() {
		return "call mIndex:" + mIndex + " mNumber:" + mNumber + " mName:" + mName + 
				(mIsMultiParty ? " conf" : "") + ", " + (mIsMT ? "in" : "out") + ", "
				+ (mIsVoice ? " voice" : " non-voice");
	}

}
