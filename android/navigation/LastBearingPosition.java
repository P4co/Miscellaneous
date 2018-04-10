package com.roadtrack;

/******************************************************************************
 * Copyright (C) 2014 Road-Track Telematics Development
 *
 * Description: Utility to save and restore last GPS position in order to
 *              maintain bearing:
 *              1) saveCurrentPostionAndBearing() : call this to save current location and bearing
 *              2) getStartBearingLocation()      : use this to get a waypoint slightly offset from the current location
 *                                                  in the direction of the saved bearing
 ******************************************************************************/

import java.io.File;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import com.roadtrack.util.RTLocalParamManager;
import com.roadtrack.util.RTLog;

public class LastBearingPosition {
    public static final double MAX_SAVED_DRIFT_DISTANCE   = 20.0;      // How far can saved distance be from current to be considered as the same

    private static final String TAG                        = "LastBearingPosition";
    private static final String BEARING_POSITION_SAVE_DIR = "/data/data/navigation";
    private static final String BEARING_POSITION_SAVE_FILE = BEARING_POSITION_SAVE_DIR + "/last_bearing.json";
    private static final double EARTH_RADIUS               = 6371000.0; // Earth radius in meters
    private static final double BEARING_POINT_DISTANCE     = 20.0;      // How far to place bearing point from current position
    private static       RTLog  sLogger                    = RTLog.getLogger( TAG, "navigation", RTLog.LogLevel.INFO );

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private static Location calcPoint(double lat1, double lon1, double bearing, double distance) {

        // Calucate new point <distance> meters from given point <lat1,lon1> in direction <bearing>
        // units are decimal degrees, bearing - clockwise staring from north, distance in meters

        // Source: http://www.movable-type.co.uk/scripts/latlong.html

        // Convert to radians
        lat1    *=  Math.PI / 180.0;
        lon1    *=  Math.PI / 180.0;
        bearing *=  Math.PI / 180.0;

        double angularDis = distance/EARTH_RADIUS;

        double lat2 = Math.asin( Math.sin(lat1) * Math.cos(angularDis) +
                                 Math.cos(lat1) * Math.sin(angularDis) * Math.cos(bearing) );

        double lon2 = lon1 + Math.atan2( Math.sin(bearing) * Math.sin(angularDis) * Math.cos(lat1),
                                         Math.cos(angularDis) - Math.sin(lat1) * Math.sin(lat2) );

        // Convert back to degrees
        lat2 *= 180.0 /  Math.PI;
        lon2 *= 180.0 /  Math.PI;

        Location calculatedLocation = new Location("CalculatedBearingPosition");
        calculatedLocation.setLatitude(lat2);
        calculatedLocation.setLongitude(lon2);
        return calculatedLocation;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    private static Location getCurrentLocation(Context context) {

        Location location = null;
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch( Exception e ) {
            sLogger.e("cannot get location", e);
        }

        if( location != null ) {
            location = new Location(location);
        }
        return location;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public static void saveCurrentPostionAndBearing(Context context) {

        Location location = getCurrentLocation(context);
        if( location == null || !location.hasBearing() ) {
            sLogger.e("Cannot save last bearing point; no location / bearing");
            return;
        }

        RTLocalParamManager.SetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "lat", location.getLatitude());
        RTLocalParamManager.SetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "lon", location.getLongitude());
        RTLocalParamManager.SetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "bearing", location.getBearing());

        try {
            // Make file writeable for other processes
            File f = new File(BEARING_POSITION_SAVE_DIR);
            f.setWritable(true, false);
            f = new File(BEARING_POSITION_SAVE_FILE);
            f.setWritable(true, false);
        } catch( Exception e ) {
            sLogger.e("Failed to make writeable: ", e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public static Location getSavedLocation(Context context) {

        double savedLat = RTLocalParamManager.GetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "lat", 1000.0);
        double savedLon = RTLocalParamManager.GetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "lon", 1000.0);
        double bearing = RTLocalParamManager.GetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "bearing", 1000.0);

        sLogger.i("Saved position: " + savedLat + ", " + savedLon + " ," + bearing);

        // Check if exists
        if( savedLat > 900.0 || savedLon > 900.0 || bearing > 900.0 ) {
            sLogger.e("No last bearing point");
            return null;
        }

        Location savedLocation = new Location("LastSavedPosition");
        savedLocation.setLatitude(savedLat);
        savedLocation.setLongitude(savedLon);

        return savedLocation;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public static Location getStartBearingLocation(Context context) {

        Location savedLocation = getSavedLocation(context);

        if( savedLocation == null ) {
            return null;
        }

        // Get current position
        Location currentLocation = getCurrentLocation(context);
        if( currentLocation == null ) {
            sLogger.e("Current location not available");
            return null;
        }
        double curLat = currentLocation.getLatitude();
        double curLon = currentLocation.getLongitude();

        // See if current position is close to last saved
        double drift = currentLocation.distanceTo(savedLocation);
        if( drift >  MAX_SAVED_DRIFT_DISTANCE) {
            sLogger.e("Saved position is greater than " + MAX_SAVED_DRIFT_DISTANCE + " meters");
            return null;
        }

        // Return a position close to current in correct direction
        double bearing = RTLocalParamManager.GetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "bearing", 1000.0);
        return calcPoint(curLat, curLon, bearing, BEARING_POINT_DISTANCE);
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    public static void clearStartBearingLocation() {
        sLogger.i("clearing saved location/bearing");
        RTLocalParamManager.SetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "lat", 0.0);
        RTLocalParamManager.SetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "lon", 0.0);
        RTLocalParamManager.SetLocalDoubleParam(BEARING_POSITION_SAVE_FILE, "bearing", -1000.0);
    }

}
