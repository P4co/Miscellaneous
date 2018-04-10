/*
 * Parcelable that extends Intent to be used as body/payload used as Interface to Navigation.java
 * 2Way Messenger NavMsgParcel instead of Intents to communicate between (base/...)Navigation & (tomtom\navapp...)NavigationService
 * Navigation ><  2Way Messenger. send(Message(NavMsgParcel)) >< handleMessage(Message(NavMsgParcel)) >< NavigationService
 * NaigationService > 2Way Message(NavMsgParcel) > Navigation >  if to TBT:broadcast(NavMsgParcel)  > TBTNotificationManager > TBT/GMLAN
 */

package com.roadtrack;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;


/**
 * Created by nadav.n on 1/24/2016.
 */
public class NavMsgParcel extends Intent {

	private static final String TAG                          = "NavMsgParcel";


	public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
		@Override
		public NavMsgParcel createFromParcel(Parcel in) {
			return new NavMsgParcel(in);
		}

		@Override
		public NavMsgParcel[] newArray(int size) {
			return new NavMsgParcel[size];
		}
	};

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        //dest.writeBundle(this.getExtras());
    }
    @Override
    public void readFromParcel(Parcel in) {
        super.readFromParcel(in);
        //this.putExtras(in.readBundle());
    }

    /*** Costructors ***/
    public NavMsgParcel(Parcel in) {
        readFromParcel(in);
    }
    public NavMsgParcel(Intent intent) {
        super(intent);
    }
    public NavMsgParcel(String action) {
        super(action);
    }
    public NavMsgParcel() {
        super();
    }
    public NavMsgParcel(NavMsgParcel inNavMsgParcel) {
        super(inNavMsgParcel);
    }
}
