package com.roadtrack;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.content.Context;
import android.location.GpsStatus;
import android.location.GpsStatus.Listener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Looper;
import com.roadtrack.util.RTLog;

public class GPSTracker extends Thread {

    private static final String TAG = "GPSTracker";
    private static final int    MIN_LOCATION_TIME       = 1000; // 1000 ms
    // Set the meters to 0 because we want to receive the location even if we are not moving for the GPS fix validation.
    private static final float  MIN_LOCATION_DISTANCE   = 0;    // Meters 
    private static final long   MIN_TIME_BETWEEN_FIXES = 10000;

    private Context         mContext;
    private LocationManager mLocationManager = null;
	private NotifyEvent     mNotifier = null;
    private boolean         mReceiveGPSsignal;
    private boolean         mIsRunning;
    private Looper          mLooper;
    private Long            mLastLocationMillis;
	private Location        mLastLocation;
    private int             mOldLocationProviderStatus;
    private int             mOldGpsEventStatus;
    private RTLog mLogger  = RTLog.getLogger(TAG, "gpstracker", RTLog.LogLevel.INFO );
	
    public GPSTracker(Context context,NotifyEvent notifier) {
        super("GPSTracker");
        mLogger.i("GPSTracker");
        mContext = context;
        this.mNotifier=notifier;
        mIsRunning=true;
        mLastLocationMillis = 0L;
        mLastLocation = null;
        mOldLocationProviderStatus = -1;
        mOldGpsEventStatus = -1;
        mReceiveGPSsignal = false;
    }

    public boolean mReceiveGPSsignal() {
        return mReceiveGPSsignal;
    }

    private void setGPSStatus(boolean haveGPSSignal) {
    	mLogger.v("setGPSStatus - haveGPSSignal : " + haveGPSSignal );
    	if(mReceiveGPSsignal != haveGPSSignal) {
			
            mReceiveGPSsignal = haveGPSSignal;
            if(mReceiveGPSsignal) {
            	mLogger.v("sending NotifyEventType.GPS_RECEIVE_FROM_TRACKER");
                mNotifier.notifyEvent(NotifyEventType.GPS_RECEIVE_FROM_TRACKER,true);            
        	} else {
            	mLogger.v("sending NotifyEventType.GPS_RECEIVE_FROM_TRACKER");
                mNotifier.notifyEvent(NotifyEventType.GPS_RECEIVE_FROM_TRACKER,false);        		
        	}
        }
    }

    public boolean isGpsAvailable() {
    	mLogger.v("in isGpsAvailable()");
        Location location=mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(location!=null) {
        	if(SystemClock.elapsedRealtime() - mLastLocationMillis <= MIN_TIME_BETWEEN_FIXES) {
        		mLogger.v("in isGpsAvailable() - returning true");
        		return true;
            }
        }
        mReceiveGPSsignal = false;
        mLogger.v("in isGpsAvailable() - returning false");
        return false;
    }

    public void run() {
        Looper.prepare();
        mLooper = Looper.myLooper();

        while(mIsRunning) {

            // Register for GPS location manager when available
            try {
                mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
                mLocationManager.addGpsStatusListener(mGPSStatusListener);
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_LOCATION_TIME, MIN_LOCATION_DISTANCE, locationListener);
                mReceiveGPSsignal = isGpsAvailable();
            } catch(Exception e) {
                try { Thread.sleep(1000); } catch(Exception et) { }
                continue;
            }
            mLogger.i("registerListener success");

            // Message loop
            Looper.loop();
        }
    }

    public void terminate() {
        mIsRunning=false;
        mLocationManager.removeUpdates(locationListener);
        mLocationManager.removeGpsStatusListener(mGPSStatusListener);
        mLooper.quit();
        mLocationManager=null;
        mLastLocationMillis = 0L;
        mLastLocation = null;
    }


    Listener mGPSStatusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int gpsStatus) {
        	mLogger.v("GPS status changed");
        	boolean isGPSFix = false;
            if( mOldGpsEventStatus != gpsStatus ) {
                mLogger.d("GPS status = " + gpsStatus);
                mOldGpsEventStatus = gpsStatus;
            }

            switch (gpsStatus) {
            case GpsStatus.GPS_EVENT_STARTED:
            	mLogger.v("GPS_EVENT_STARTED");
                //setGPSStatus(true);
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
            	mLogger.v("GPS_EVENT_STOPPED");
                setGPSStatus(false);
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
            	mLogger.v("GPS_EVENT_SATELLITE_STATUS");
            	/*
            	 * In case the system is just after boot/hibernate there may be inconsistency in the systemCLock and 
            	 * we want to avoid the situation that last location received time is greater then system time.
            	 */
            	if((SystemClock.elapsedRealtime() - mLastLocationMillis) < 0){
            		mLastLocationMillis = 0L;
            		return;
            	}
            	if (mLastLocation != null) {
                    isGPSFix = (SystemClock.elapsedRealtime() - mLastLocationMillis) < MIN_TIME_BETWEEN_FIXES;
                }
            	// A fix has been acquired.
                if (isGPSFix) { 
                	setGPSStatus(true);
                // The fix has been lost.
                } else { 
                	setGPSStatus(false);
                }

                break;
            }
         }
    };

    LocationListener locationListener = new LocationListener() {

        private boolean coordinateIsValid(Double coord, Double limit) {
            if( coord < -limit || coord > limit ) {
                return false;
            }
            return true;
        }

        // location update from GPS
        @Override
        public void onLocationChanged(Location location) {
        	mLogger.v("in onLocationChanged");
        	if (location == null) {
            	mLogger.d("onLocationChanged - received null location");
                return;
            }

			// Store the time of the last received location update.
            mLastLocationMillis = SystemClock.elapsedRealtime();
            mLastLocation = location;

            if( coordinateIsValid(location.getLatitude(), 90.0) && coordinateIsValid(location.getLongitude(), 180.0) ) {
            	mLogger.v("in onLocationChanged - coordinates are valid");
            	// Valid fix, make sure we know that GPS is available
                setGPSStatus(isGpsAvailable());
            }
        }

        // status of the GPS provider changes
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        	mLogger.v("in onStatusChanged");
            if( mOldLocationProviderStatus != status ) {
                mLogger.i("location provider status = " + status);
                mOldLocationProviderStatus = status;
            }

            switch(status) {
            case LocationProvider.AVAILABLE:
                setGPSStatus(true);
                break;
            case LocationProvider.OUT_OF_SERVICE:
                setGPSStatus(false);
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                // commented out as we have other indications for determining absence of gps. this event sent
            	// once, immediateley after gps returns and this causes issues in navigation.
            	//setGPSStatus(false);
                break;
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            mLogger.d("onProviderEnabled");
        }

        //GPS provider is turned off
        @Override
        public void onProviderDisabled(String provider) {
            mLogger.d("onProviderDisabled");
            setGPSStatus(false);
        }
    };
}