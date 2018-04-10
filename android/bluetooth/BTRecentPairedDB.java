
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.content.ContentValues;

import android.roadtrack.pconf.PConfParameters;
import com.roadtrack.util.RTLog;

public class BTRecentPairedDB extends SQLiteOpenHelper {
    private static final String TAG = "BTRecentPairedDB";
    private Context mContext;
    private SQLiteDatabase mDatabase = null;
    private static BTRecentPairedDB btPaired = null;
    private RTLog mLogger;
    
    public static final String TABLE_RECENT_PAIRED  	= "recentpaired";
    public static final String COLUMN_MAC      			= "mac";
    public static final String COLUMN_CONNECT      		= "connect";
    public static final String COLUMN_MAP      		= "map";
 
    private static final String DATABASE_NAME       = "BTRecentPaired.db";
    private static final int    DATABASE_VERSION    = 3;

    private static final String[] RECENT_PAIRED_COLUMNS = {
        COLUMN_MAC,
        COLUMN_CONNECT,
        COLUMN_MAP
    };

    // TextMessages Database Table creation sql statement
    private static final String DATABASE_RECENT_PAIRED_CREATE = "create table if not exists "
            + TABLE_RECENT_PAIRED 
            + "(" 
            + COLUMN_MAC + " text,"
            + COLUMN_CONNECT + " integer,"
            + COLUMN_MAP + " integer"
            + ");";

    private static final String DATABASE_ORDER_BY = COLUMN_MAC;

    class BTRecentPairedDeviceInfo {
    	boolean connect;
    	boolean map;
    }
    
    public BTRecentPairedDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mLogger = RTLog.getLogger( TAG, "btrecentpaired", RTLog.LogLevel.INFO );
        mLogger.i("Constructor Called");
        
        mContext       = context;
        if (btPaired != null) {
        	throw new RuntimeException("You cannot create a new instance of this class");
        }
        btPaired = this;
        mDatabase = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        mLogger.i(" data base created!");
        database.execSQL(DATABASE_RECENT_PAIRED_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mLogger.w(
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy the current cache.");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECENT_PAIRED);
        onCreate(db);
    }

    // This function returns a new cursor that contains all the rows and columns of the table recent paired devices.
    public Cursor createTableCursor() 
    {
        return getReadableDatabase().query(    TABLE_RECENT_PAIRED, 
        										RECENT_PAIRED_COLUMNS,
                                                null, 
                                                null, 
                                                null, 
                                                null, 
                                                DATABASE_ORDER_BY );
    }

    // This function returns a new cursor that contains all the rows and columns of the table cache according to optional 
    // where clause
    public Cursor createTableCursor( String whereClause, String[] whereParams ) 
    {
        return getReadableDatabase().query(    TABLE_RECENT_PAIRED, 
        										RECENT_PAIRED_COLUMNS,
                                                whereClause, 
                                                whereParams, 
                                                null, 
                                                null, 
                                                DATABASE_ORDER_BY );
    }
    
    /**
     * returns the size of entire recent paired db
     */
    int getRecentPairedSize() {
        Cursor cursor = createTableCursor();

        int count = cursor.getCount();
        cursor.close();

        return count;
    }
    
    /**
     * returns the size of the recent paired db per MAC
     */
    int getRecentPairedSizePerDevice(String devAddr) {
   		String mac = devAddr.replaceAll(":", "");
		String whereClause = COLUMN_MAC + " = ?";
        String[] whereParams = new String[] { mac };
		Cursor cursor = createTableCursor( whereClause, whereParams );
		
        int count = cursor.getCount();
        cursor.close();

        return count;
    }
    
    /**
     * deletes the entire data base
     */
    public void deleteRecentPairedDB(){
    	mDatabase.delete(TABLE_RECENT_PAIRED, "1", null);
    }
    
    /**
     * deletes a specific MAC from the data base
     */
    /* package*/ void deleteMacFromDB(String devAddr){
    	try {
    		String mac = devAddr.replaceAll(":", "");
    		String whereClause = COLUMN_MAC + "= ?";
            String[] whereParams = new String[] { mac };
    		mDatabase.delete(TABLE_RECENT_PAIRED, whereClause, whereParams);
        }
        catch( Exception e )
        {
            mLogger.e("Delete MAC failure: ", e);
        }
    }
    
    /**
     * save data for MAC in the recent paired DB.
     */    
    void saveRecentPairedToDB(String devAddr, boolean connectFlag, boolean mapFlag) {
    	
        if ( devAddr.isEmpty() ) {
            return;
        }
        mLogger.d("saveRecentPairedToDB()");

        int connect = (connectFlag == true) ? 1 : 0;
        int map = (mapFlag == true) ? 1 : 0;

        if (getRecentPairedSizePerDevice(devAddr) > 0)
        {
        	//update
        	try {
        		ContentValues values = new ContentValues();  
        		values.put(COLUMN_CONNECT, connect);
        		values.put(COLUMN_MAP, map);
        		String mac = devAddr.replaceAll(":", "");
        		String whereClause = COLUMN_MAC + "= ?";
        		String[] whereParams = new String[] { mac };
        		mDatabase.update(TABLE_RECENT_PAIRED, values, whereClause, whereParams);
        	}
            catch( Exception e )
            {
                mLogger.e("saveRecentPairedToDB() update failure: ", e);
            }
        }
        else
        {
        	//insert
        	InsertHelper iHelp = new InsertHelper( mDatabase, TABLE_RECENT_PAIRED );
        	int macIndex = iHelp.getColumnIndex( COLUMN_MAC );
        	int connectIndex = iHelp.getColumnIndex( COLUMN_CONNECT );
        	int mapIndex = iHelp.getColumnIndex( COLUMN_MAP );

        	try {
        		mDatabase.beginTransaction();
        		String mac = devAddr.replaceAll(":", "");
        		iHelp.prepareForInsert();
        		iHelp.bind( macIndex, mac);
        		iHelp.bind( connectIndex, connect);
        		iHelp.bind( mapIndex, map);
        		iHelp.execute();
        		mLogger.d("saveRecentPairedToDB() - Comitting transaction" );
        		mDatabase.setTransactionSuccessful();
        		mLogger.d("recent paired saved successfully" );
        	} finally {
        		mDatabase.endTransaction();
        		iHelp.close();
        		mLogger.d("saveRecentPairedToDB() - close" );
        	}
        }
    }
    
    /**
     * reads data for MAC from the recent paired DB. If MAC exists return device data otherwise return null.
     */
    BTRecentPairedDeviceInfo getRecentPairedDeviceInfo(String address) {
    	if (!address.isEmpty())
        {
     		String mac = address.replaceAll(":", "");
        	String whereClause = BTContactsDB.COLUMN_MAC + " = ?";
            String[] whereParams = new String[] { mac };
        	Cursor cursor = createTableCursor( whereClause, whereParams );
        	int connect = 0;
        	int map = 0;
        	BTRecentPairedDeviceInfo deviceInfo = null;
           	mLogger.d( "getDeviceConnectedState() cursor:" + cursor.getCount());
        	if (cursor.getCount() > 0)
        	{
        		cursor.moveToFirst();
        		deviceInfo = new BTRecentPairedDeviceInfo();
        		connect = cursor.getInt(1);
        		deviceInfo.connect = ((connect == 1) ? true: false);
        		map = cursor.getInt(2);
        		deviceInfo.map = ((map == 1) ? true: false);
         	}
        	cursor.close();
        	return deviceInfo;
        }
        else
        {
        	mLogger.d( "getDeviceConnectedState() address:" + address);
        	return null;
        }
    }
}