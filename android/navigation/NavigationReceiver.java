package com.roadtrack;

import android.util.Log;
import android.os.ResultReceiver;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.location.Location;
import java.util.ArrayList;
import com.roadtrack.util.RTLog;
import com.roadtrack.NotifyEventType;

public class NavigationReceiver extends ResultReceiver {
    private static final String TAG = "NavigationReceiver";
    private NotifyEvent notifier=null;
    private RTLog mLogger  = RTLog.getLogger(TAG, "devreceiver", RTLog.LogLevel.INFO );

    public NavigationReceiver(Handler handler,NotifyEvent notifier) {
        super(handler);
        this.notifier=notifier;
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        mLogger.i("onReceiveResult code = " + resultCode);
        if(resultCode==Navigation.ResultCode.ROUTE.ordinal()) {
            ArrayList<Location> routeLocations =resultData.getParcelableArrayList("route");
            if (null != routeLocations)
            {
	            Bundle bundle;
	            for(int ix=0 ; ix<routeLocations.size(); ++ix) {
	                bundle=routeLocations.get(ix).getExtras();
	            }		
            }else
            {
            	mLogger.e("routeLocations is null !");
            }

        } else if(resultCode==Navigation.ResultCode.ROAD.ordinal()) {
            String city=resultData.getString("city");
            String street=resultData.getString("street");
            double speedLimit=resultData.getDouble("speedLimit");
        } else if(resultCode==Navigation.ResultCode.POI.ordinal()) {
            ArrayList<Location> poiResult =resultData.getParcelableArrayList("poi");
        }
    }
}