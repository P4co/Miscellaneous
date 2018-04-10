package com.roadtrack.navigation;
/******************************************************************************
 * Copyright (C) 2013 Road-Track Telematics Development
 *
 * Description: implements the destination point of interest class.
 ******************************************************************************/

import java.io.File;
import java.util.ArrayList;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import com.roadtrack.navigation.Destination.*;
import com.roadtrack.util.RTLog;
import jsqlite.Database;
import jsqlite.Exception;
import jsqlite.Stmt;

public class PointOfInterests {
    private static final String DATABASE_PATH     = "/system/vendor/roadtrack/maps/";
    private static final String DATABASE_NAME     = "PointOfInterests.db3";
    private static final String TAG               = "PointOfInterests";
    private static final boolean DEBUG            = true;
    private Database mDb;
    private RTLog mLogger  = RTLog.getLogger(TAG, "poi", RTLog.LogLevel.INFO );

    public PointOfInterests() {
        mLogger.v( "PointOfInterests construcrot ");
        mDb = new jsqlite.Database();
        try {
            mDb.open(new File(DATABASE_PATH, DATABASE_NAME).getAbsolutePath(), jsqlite.Constants.SQLITE_OPEN_READONLY);
        } catch (Exception e) {
            mLogger.e( "Exception in open database! ", e);
        }
    }

    public void close() {
        mLogger.v( "close function ");
        try {
            mDb.close();
        } catch (Exception e) {
            mLogger.e( "Exception in close database! ", e);
        }
    }


    public ArrayList<Destination> FindDestinationByLocation(Type type, int subtype, float longitude, float latitude, float max_distance) {
        String query;
        Stmt stmt;
        ArrayList<Destination> destinationList = new ArrayList();
        try {
            if (Destination.SUBTYPE[subtype].equals("ALL")) {
                query = "SELECT Name, Phonetics, Type, Subtype, Country, City, Address, Y(Location), X(Location), PhoneNumber FROM PointsOfInterests WHERE Type=? AND Distance(Location, GeomFromText(?))<?;";
                stmt = mDb.prepare(query);
                stmt.bind(1, type.toString());
                stmt.bind(2, "POINT(" + longitude + " " +  latitude + ")");
                stmt.bind(3, max_distance);
            } else {
                query = "SELECT Name, Phonetics, Type, Subtype, Country, City, Address, Y(Location), X(Location), PhoneNumber FROM PointsOfInterests WHERE Type=? AND Subtype=? AND Distance(Location, GeomFromText(?))<?;";
                stmt = mDb.prepare(query);
                stmt.bind(1, type.toString());
                stmt.bind(2, Destination.SUBTYPE[subtype]);
                stmt.bind(3, "POINT(" + longitude + " " +  latitude + ")");
                stmt.bind(4, max_distance);
            }
            while( stmt.step() ) {
                Destination destination = new Destination(stmt.column_string(0),stmt.column_string(1), Type.valueOf(stmt.column_string(2)),stmt.column_string(3), stmt.column_string(4),stmt.column_string(5),stmt.column_string(6), (float)stmt.column_double(7),(float)stmt.column_double(8), stmt.column_string(9));
                destinationList.add(destination);
            }
            return destinationList;
        } catch (Exception e) {
            mLogger.e( "error on DB" );
            return null;
        }
    }

    public Destination findbyName(Type type, String name, float longitude, float latitude, float max_distance, int country) {
        String query = "SELECT Name, Phonetics, Type, Subtype, Country, City, Address, Y(Location), X(Location), PhoneNumber FROM PointsOfInterests WHERE Type=? AND Name=? AND Country=? AND Distance(Location, GeomFromText(?))<?;";
        Stmt stmt;
        try {
            stmt = mDb.prepare(query);
            stmt.bind(1, type.toString());
            stmt.bind(2, name);
            stmt.bind(3, Destination.COUNTRY[country]);
            stmt.bind(4, "POINT(" + longitude + " " +  latitude + ")");
            stmt.bind(5, max_distance);
            while( stmt.step() ) {
                Destination destination = new Destination(stmt.column_string(0), stmt.column_string(1), Type.valueOf(stmt.column_string(2)),
                        stmt.column_string(3), stmt.column_string(4), stmt.column_string(5), stmt.column_string(6),
                        (float)stmt.column_double(7), (float)stmt.column_double(8), stmt.column_string(9));
                return destination;
            }
            return null;
        } catch (Exception e) {
            mLogger.e( "error on DB" );
            return null;
        }
    }

}
