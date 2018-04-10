
import java.io.File;

import android.content.Context;
import android.sqlcipher.database.SQLiteDatabase;
import android.sqlcipher.database.SQLiteOpenHelper;
import android.util.Log;
import android.provider.ContactsContract;
import android.sqlcipher.Cursor;
import android.sqlcipher.DBKeyGenerator;
import android.sqlcipher.DefaultDatabaseErrorHandler;
import android.sqlcipher.database.SQLiteDatabaseHook;

import android.roadtrack.pconf.PConfParameters;

import com.roadtrack.util.RTLog;

public class BTContactsDB extends SQLiteOpenHelper {
    private static final String TAG = "BTContactsDB";
    private final String mDBKey = "null";

    public static final String TABLE_CONTACTS_CACHE  				= "contactscache";
    public static final String TABLE_CONTACTS_CACHE_TEMP 			= "contactscache_temp";
    
    public static final String COLUMN_MAC      					= "mac";
    public static final String COLUMN_FIRST_NAME 					= "firstname";
    public static final String COLUMN_LAST_NAME 					= "lastname";
    public static final String COLUMN_PHONE_NUMBER 				= "phonenumber";
    public static final String COLUMN_PHONE_TYPE 					= "phonetype";
    public static final String COLUMN_MID_NAME					= "midname";
    
    public static final String CONTACTSCACHE_MAC_INDEX 			= "contactscache_mac_index";
    public static final String CONTACTSCACHE_FIRST_NAME_INDEX 	= "contactscache_first_name_index";
    public static final String CONTACTSCACHE_LAST_NAME_INDEX		= "contactscache_last_name_index";    
    public static final String CONTACTSCACHE_MID_NAME_INDEX		= "contactscache_mid_name_index";  
    
	public static final  int 	PHONE_CONTACTS_MOBILE		= 	ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
	public static final  int 	PHONE_CONTACTS_HOME			= 	ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
	public static final  int 	PHONE_CONTACTS_WORK			= 	ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
	public static final  int 	PHONE_CONTACTS_OTHER		= 	ContactsContract.CommonDataKinds.Phone.TYPE_OTHER;
  
    private static final String DATABASE_NAME       = "BTContactsCacheE.db";
    private static final int    DATABASE_VERSION    = 5;

    private RTLog mLogger  = RTLog.getLogger(TAG, "btcontactsdb", RTLog.LogLevel.INFO );

    private Object mDbLock = new Object();    
    
    private DBKeyGenerator mDBKeyGenerator; 
    
    private static final String[] CONTACT_CACHE_COLUMNS = {
        COLUMN_MAC, 
        COLUMN_FIRST_NAME, 
        COLUMN_MID_NAME, 
        COLUMN_LAST_NAME, 
        COLUMN_PHONE_NUMBER, 
        COLUMN_PHONE_TYPE };

    private static final String DATABASE_ORDER_BY = COLUMN_LAST_NAME + "," + COLUMN_FIRST_NAME + "," + COLUMN_MID_NAME;

    private void CreateTable(String tableName, SQLiteDatabase database) 
    {
        String query = "create table if not exists "
                + tableName 
                + "(" 
                + COLUMN_MAC + " text,"
                + COLUMN_FIRST_NAME   + " text,"
                + COLUMN_LAST_NAME   + " text,"
                + COLUMN_PHONE_NUMBER   + " text,"
                + COLUMN_PHONE_TYPE + " integer, "
                + COLUMN_MID_NAME + " text"
                + ");";
    	
        database.execSQL(query);
    }
 
    public BTContactsDB(Context context) {
    	
        super(context, DATABASE_NAME, null, DATABASE_VERSION, new SQLiteDatabaseHook() {
        	 
        	             @Override
        	             public void preKey(SQLiteDatabase database) {}
        	 
        	             @Override
        	             public void postKey(SQLiteDatabase database) {
        	                 database.execSQL("PRAGMA cipher = 'aes-128-cfb'");
        	             }
        	         }, new DefaultDatabaseErrorHandler());
        
        mLogger.i("Constructor Called");
        SQLiteDatabase.loadLibs(context);
        
        mDBKeyGenerator = new DBKeyGenerator(context);
        
        if(mDBKeyGenerator.getKey(mDBKey))
        {
            // delete unmach database
        	mLogger.i("New Key created , delete unmach database ");
            File file = new File("/data/system/"+DATABASE_NAME);
            if(file.exists()){
            	file.delete();
            }
        }
        
        // delete old database
        File file = new File("/data/system/BTContactsCache.db");
        if(file.exists()){
        	file.delete();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        CreateTable(TABLE_CONTACTS_CACHE, database);
        CreateTable(TABLE_CONTACTS_CACHE_TEMP, database);
        
        mLogger.i("Data base created!");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mLogger.w(
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy the current cache.");
        DropIndexes( db );
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS_CACHE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS_CACHE_TEMP);
        onCreate(db);
    }

    // This function removes all the indexes on the table. It should be used before deleting the table, of before major update
    // of the data.
    public  void DropIndexes() {
        DropIndexes( null );
    }
        
    private void DropIndexes( SQLiteDatabase db ) 
    {
        if ( db == null ) {
            db = getWritableDatabase(mDBKey);
        }
        
        db.execSQL("DROP INDEX IF EXISTS " + CONTACTSCACHE_MAC_INDEX);
        db.execSQL("DROP INDEX IF EXISTS " + CONTACTSCACHE_FIRST_NAME_INDEX);
        db.execSQL("DROP INDEX IF EXISTS " + CONTACTSCACHE_LAST_NAME_INDEX);    
        db.execSQL("DROP INDEX IF EXISTS " + CONTACTSCACHE_LAST_NAME_INDEX);    
    }

    // This function creates the indexes on the table. To be used after completing table update.
    public void CreateIndexes()
    {
        getWritableDatabase(mDBKey).execSQL("CREATE INDEX IF NOT EXISTS " + CONTACTSCACHE_MAC_INDEX + " on " + 
        		TABLE_CONTACTS_CACHE  + "(" + COLUMN_MAC + ")");

        getWritableDatabase(mDBKey).execSQL("CREATE INDEX IF NOT EXISTS " + CONTACTSCACHE_FIRST_NAME_INDEX + " on " + 
        		TABLE_CONTACTS_CACHE  + "(" + COLUMN_FIRST_NAME + ")");

        getWritableDatabase(mDBKey).execSQL("CREATE INDEX IF NOT EXISTS " + CONTACTSCACHE_LAST_NAME_INDEX + " on " + 
        		TABLE_CONTACTS_CACHE  + "(" + COLUMN_LAST_NAME + ")");

        getWritableDatabase(mDBKey).execSQL("CREATE INDEX IF NOT EXISTS " + CONTACTSCACHE_MID_NAME_INDEX + " on " + 
        		TABLE_CONTACTS_CACHE  + "(" + COLUMN_MID_NAME + ")");
        
    }

    // This function returns a new cursor that contains all the rows and columns of the table cache.
    public Cursor createTableCursor() 
    {
    	synchronized(mDbLock) {
	        return getReadableDatabase(mDBKey).query(     TABLE_CONTACTS_CACHE, 
	                                                CONTACT_CACHE_COLUMNS,
	                                                null, 
	                                                null, 
	                                                null, 
	                                                null, 
	                                                DATABASE_ORDER_BY );
    	}
    }

    // This function returns a new cursor that contains all the rows and columns of the table cache according to optional 
    // where clause
    public Cursor createTableCursor( String whereClause, String[] whereParams ) 
    {
    	synchronized(mDbLock) {
	        return  getReadableDatabase(mDBKey).query(     TABLE_CONTACTS_CACHE, 
	                                                CONTACT_CACHE_COLUMNS,
	                                                whereClause, 
	                                                whereParams, 
	                                                null, 
	                                                null, 
	                                                DATABASE_ORDER_BY );
    	}
    }
    
    public void CopyTempTable() {
    	synchronized(mDbLock) {
	    	SQLiteDatabase db = getWritableDatabase(mDBKey);
	    	db.beginTransaction();
	    	 
	    	try{
	    		db.delete(TABLE_CONTACTS_CACHE, null, null);	    		
	    		db.execSQL("INSERT INTO " + TABLE_CONTACTS_CACHE + " SELECT * FROM " + TABLE_CONTACTS_CACHE_TEMP);
	    		mLogger.d( "CopyTempTable() - temp table copied" );
	    		db.setTransactionSuccessful();
	    	} finally{
	    		db.endTransaction();
	    	}
    	}
    }
    
    // This function returns a cursor to a count that contains all the rows with distinct contact names in the table cache according to optional 
    // where clause
    public Cursor countCursor( String whereClause, String[] whereParams ) 
    {
    	synchronized(mDbLock) {
	        return getReadableDatabase(mDBKey).rawQuery( "SELECT COUNT(*) FROM " + 
	        										"(SELECT DISTINCT " + DATABASE_ORDER_BY + " FROM " + TABLE_CONTACTS_CACHE +
	        										" WHERE " + whereClause + " )" , whereParams );
    	}
    }
    
    public SQLiteDatabase getWritableDatabase()
    {
    	return  getWritableDatabase(mDBKey);
    }
}