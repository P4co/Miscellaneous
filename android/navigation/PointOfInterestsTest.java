package com.roadtrack.navigation;

import java.util.ArrayList;

import com.roadtrack.navigation.Destination.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.roadtrack.util.RTLog;

public class PointOfInterestsTest {
    private static final String TAG               = "PointOfInterests";
    private static final int mDelay               = 30000;
    private Context                               mContext;
    private PointOfInterestReceiver               mReceiver;
    private  LocationManager                      mLocationMgr;
    private RTLog mLogger  = RTLog.getLogger(TAG, "poitest", RTLog.LogLevel.INFO );

    public PointOfInterestsTest(Context context) {
        mLogger.v( "PointOfInterestsTest constructor");
        mContext = context;
        mReceiver = new PointOfInterestReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        mContext.registerReceiver(mReceiver, filter);
        //mLocationMgr = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    public void destinationToLog(Destination destination) {
        if (destination == null) {
            mLogger.d( "POI >> null");
        } else {
            mLogger.d( "POI >> Name: " + destination.mName + " Type: " + destination.mType + " Subtype: " + Destination.SUBTYPE[destination.mSubtype] + " Country: " + Destination.COUNTRY[destination.mCountry]);
        }
    }

    public void destinationListToLog(ArrayList<Destination> destinationList) {
        if ( destinationList != null ) {
            for (Destination destination: destinationList) {
                destinationToLog(destination);
            }
        }
        else {
            mLogger.e( "Error on DB finding location");
        }
    }

    class PointOfInterestReceiver extends BroadcastReceiver {
        public void onReceive(Context contex, Intent intent) {
            if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                mLogger.d( "intent: ACTION_BOOT_COMPLETED");
                /*Location location = null;
                while (location == null)
                {
                	try {
                		Thread.sleep(mDelay);
                	} catch (InterruptedException e) {
                		mLogger.e( "Exception in thread sleep! ", e);
                	}
                	location = mLocationMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                mLogger.d( "GPS: " + "longitude: " + location.getLongitude() + " latitude: " + location.getLatitude());
                */
                PointOfInterests pointOfInterests = new PointOfInterests();
                mLogger.v( "findbyName: RESTAURANT, ChineseFood, 5, CN");
                destinationToLog(pointOfInterests.findbyName(Type.RESTAURANT, "ChineseFood", (float)34.8643666666, (float)32.2830866666, (float)5, 4));
                mLogger.v( "FindDestinationByLocation: GAS_STATION, ALL, 5");
                destinationListToLog(pointOfInterests.FindDestinationByLocation(Type.GAS_STATION, 5, (float)34.8643666666, (float)32.2830866666, (float)5));
                mLogger.v( "findbyName: BANK, Bank Leomi, 1, CN");
                destinationToLog(pointOfInterests.findbyName(Type.BANK, "Bank Leomi", (float)34.8643666666, (float)32.2830866666, (float)1, 4));
                mLogger.v( "FindDestinationByLocation: BANK, ALL, 0.0000000001");
                destinationListToLog(pointOfInterests.FindDestinationByLocation(Type.BANK, 6, (float)34.8643666666, (float)32.2830866666, (float)0.0000000001));
                mLogger.v( "FindDestinationByLocation: BANK, ALL, 4");
                destinationListToLog(pointOfInterests.FindDestinationByLocation(Type.BANK, 6, (float)34.8643666666, (float)32.2830866666, (float)4));
                mLogger.v( "findbyName: GAS_STATION, Paz station, 3, CN");
                destinationToLog(pointOfInterests.findbyName(Type.GAS_STATION, "Paz station", (float)34.8643666666, (float)32.2830866666, (float)3, 4));
                mLogger.v( "findbyName: BANK, Bank Discont, 4, US");
                destinationToLog(pointOfInterests.findbyName(Type.BANK, "Bank Discont", (float)34.8643666666, (float)32.2830866666, (float)4, 1));
                pointOfInterests.close();
            }
        }
    }
}
