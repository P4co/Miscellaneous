package com.roadtrack.navigation;

import android.util.Log;

import com.roadtrack.util.RTLog;

/******************************************************************************
 * Copyright (C) 2013 Road-Track Telematics Development
 *
 * Description: implements the destination location
 ******************************************************************************/

class Destination {

    private static final String TAG               = "PointOfInterests";
    public final String mName;
    public final String mPhonetics;
    public final Type mType;
    public final int mSubtype;
    public final int mCountry;
    public final String mCity;
    public final String mAddress;
    public final float mLatitude;
    public final float mLongitude;
    public final String mPhoneNumber;
    private static RTLog mLogger  = RTLog.getLogger(TAG, "destination", RTLog.LogLevel.INFO );


    public Destination(String name, String phonetics, Type type, String subtype, String country, String city,
                       String address, float latitude, float longitude, String phoneNumber) {
        mName = name;
        mPhonetics = phonetics;
        mType = type;
        mCity = city;
        mAddress = address;
        mLatitude = latitude;
        mLongitude = longitude;
        mPhoneNumber = phoneNumber;
        mSubtype = subtypeToInt(subtype);
        mCountry = countryToInt(country);
    }

    public enum Type {
        RESTAURANT,
        BANK,
        GAS_STATION
    }

    public final static String SUBTYPE[]= {"CHINESE_RESTAURANT", "FAST_FOOD", "BANK_LEUMI", "BANK_HAPOALIM", "PAZ_GAS_STATION", "DELEK_GAS_STATION", "ALL"};

    public final static String COUNTRY[]= {"MX","US","BR","RU","CN"};

    public static int subtypeToInt(String subtype) {
        for (int i=0; i<SUBTYPE.length; i++) {
            if (SUBTYPE[i].equals(subtype)) {
                return i;
            }
        }
        mLogger.w("invalid string of subtype!");
        return -1;
    }

    public static int countryToInt(String country) {
        for (int i=0; i<COUNTRY.length; i++) {
            if (COUNTRY[i].equals(country)) {
                return i;
            }
        }
        mLogger.w("invalid string of country!");
        return -1;
    }
}
