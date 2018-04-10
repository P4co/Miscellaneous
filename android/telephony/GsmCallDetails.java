
import android.os.Parcel;
import android.os.Parcelable;

/**
 *
 * @hide
 */
public final class GsmCallDetails implements Parcelable {
    
	private int index; // starting with 1
	//MT = incomimg
	private int isMT; //boolean
	/*state = 0 (active) 1(held) 2 (dailing for outgoing call only) 3 (Alerting for outgoing call only)
    4 (incoming) 5 (waiting)*/
	private int state;
	//multiparty
	private int isMpty; //boolean
	private String number;
	private int isVoice; //boolean
	private String name = null;
    

    public static final Parcelable.Creator<GsmCallDetails> CREATOR = new Parcelable.Creator<GsmCallDetails>() {
        public GsmCallDetails createFromParcel(Parcel in) {
            return new GsmCallDetails(in);
        }

        public GsmCallDetails[] newArray(int size) {
            return new GsmCallDetails[size];
        }
    };

    public GsmCallDetails() { }

    private GsmCallDetails(Parcel in) {
        index = in.readInt();
        isMT = in.readInt();
        state = in.readInt();
        isVoice = in.readInt();
        isMpty = in.readInt();
        number = in.readString();
        name = in.readString();
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(index);
        dest.writeInt(isMT);
        dest.writeInt(state);
        dest.writeInt(isVoice);
        dest.writeInt(isMpty);
        dest.writeString(number);
        dest.writeString(name);

    }
    
    public int describeContents() {
        return 0;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public void setIsMT(int isMT) {
        this.isMT = isMT;
    }

    public int getIsMT() {
        return isMT;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getState() {
        return state;
    }

    public void setIsVoice(int isVoice) {
        this.isVoice = isVoice;
    }

    public int getIsVoice() {
        return isVoice;
    }

    public void setIsMpty(int isMpty) {
        this.isMpty = isMpty;
    }

    public int getIsMptyCall() {
        return isMpty;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    public String toString() {
        return "call index: " + index + " isMT: " + isMT +
               " state:" + state + " isVoice:" + isVoice +
               " isMpty:" + isMpty + " number:" + number + " name:" + name;
    }
}

