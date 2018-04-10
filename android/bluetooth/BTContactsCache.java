
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import java.util.*;

import android.database.Cursor;
import android.sqlcipher.database.SQLiteDatabase;
import android.sqlcipher.DatabaseUtils.InsertHelper;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import com.roadtrack.util.RTLog;

public class BTContactsCache {
    private static final String TAG = "BTContactsCache";
    
    private Context mContext;
    private SQLiteDatabase mDatabase = null;
    private BTContactsDB mDbHelper = null;
    private static BTContactsCache btContacts = null;
    private String mContactsDevAddr = "";
    private String mNotifyContactsDevAddr = "";
    private RTLog mLogger;
    private Thread  mCacheRequestThread;
    private static final int CONTACT_LEN_FOR_MATCH = 8;
    
    private class DbEntry {
       	String firstname;
    	String lastname;
    	String midname;
    	String number;
    	int type;
    }
    
    public class Phone {
    	public String number;
    	public int type;
    }
    
	public class CacheContactEntry {
        long id;
    	public String firstname;
    	public String midname;
    	public String lastname;
    	public List<Phone> phoneNumbers = new ArrayList<Phone>();
    }
	
    public static final String ACTION_GET_CONTACT_NAME =
            "com.roadtrack.bluetooth.ACTION_GET_CONTACT_NAME";
    public static final String EXTRA_PHONE_NUMBER =
            "com.roadtrack.bluetooth.EXTRA_PHONE_NUMBER";
    
    // always includes EXTRA_PHONE_NUMBER + EXTRA_CONTACT_FIRST_NAME, EXTRA_CONTACT_MID_NAME,  EXTRA_CONTACT_LAST_NAME
    public static final String ACTION_RESPONSE_CONTACT_NAME =
                "com.roadtrack.bluetooth.ACTION_RESPONSE_CONTACT_NAME";       
    public static final String EXTRA_CONTACT_FIRST_NAME =
            "com.roadtrack.bluetooth.EXTRA_CONTACT_FIRST_NAME";
    public static final String EXTRA_CONTACT_MID_NAME =
            "com.roadtrack.bluetooth.EXTRA_CONTACT_MID_NAME";
    public static final String EXTRA_CONTACT_LAST_NAME =
            "com.roadtrack.bluetooth.EXTRA_CONTACT_LAST_NAME";
 
 
    public BTContactsCache(Context context) {
        mLogger = RTLog.getLogger( TAG, "btcontactscache", RTLog.LogLevel.INFO );

        mLogger.d( "Constructor Called!" );

        mContext       = context;
        if (btContacts != null) {
        	throw new RuntimeException("You cannot create a new instance of this class");
        }
        btContacts = this;
        mDbHelper = new BTContactsDB(context);  
        mDatabase = mDbHelper.getWritableDatabase();
        
        // register for Cache requests
       IntentFilter filter = new IntentFilter();
       filter.addAction(ACTION_GET_CONTACT_NAME);
       mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    public static BTContactsCache getInstance()
    {
    	return btContacts;
    }
    
    /**
     * returns the size of entire contacts cache db
     */
    int getContactsCacheSize() {
        Cursor cursor = mDbHelper.createTableCursor();

        int count = cursor.getCount();
        cursor.close();

        return count;
    }
    
    /**
     * returns the size of the contacts cache db per MAC
     */
    int getContactsCacheSizePerDevice(String devAddr) {
   		String mac = devAddr.replaceAll(":", "");
		String whereClause = BTContactsDB.COLUMN_MAC + " = ?";
        String[] whereParams = new String[] { mac };
		Cursor cursor = mDbHelper.createTableCursor( whereClause, whereParams );
		
        int count = cursor.getCount();
        cursor.close();

        return count;
    }
    
    /**
     * saves contacts to data base contacts cache table
     */
    private void saveContactsToDB(List<CacheContactEntry> cacheContactsList, String devAddr) {
    	
        if ( devAddr.isEmpty() ) {
            return;
        }
        mLogger.d( "saveContactsToDB() - Dropping indexes" );
        mDbHelper.DropIndexes();
        InsertHelper iHelp = new InsertHelper( mDatabase, BTContactsDB.TABLE_CONTACTS_CACHE_TEMP );
        int macIndex = iHelp.getColumnIndex( BTContactsDB.COLUMN_MAC );
        int firstNameIndex = iHelp.getColumnIndex( BTContactsDB.COLUMN_FIRST_NAME );
        int lastNameIndex = iHelp.getColumnIndex( BTContactsDB.COLUMN_LAST_NAME );
        int phoneNumberIndex = iHelp.getColumnIndex( BTContactsDB.COLUMN_PHONE_NUMBER );
        int phoneTypeIndex = iHelp.getColumnIndex( BTContactsDB.COLUMN_PHONE_TYPE );
       	int midNameIndex = iHelp.getColumnIndex( BTContactsDB.COLUMN_MID_NAME );

    	try {
            int i = 0,j  = 0;
            mDatabase.beginTransaction();
     		String mac = devAddr.replaceAll(":", "");
    		for ( CacheContactEntry contact : cacheContactsList )
    		{
                i++;
    			for (Phone phone : contact.phoneNumbers)
    			{
                    j++;
                    iHelp.prepareForInsert();
    				iHelp.bind( macIndex, mac);
                    iHelp.bind( firstNameIndex, contact.firstname); 
                    iHelp.bind( lastNameIndex, contact.lastname);
    				iHelp.bind( phoneNumberIndex, phone.number);
    				iHelp.bind( phoneTypeIndex,   phone.type);
    				iHelp.bind( midNameIndex, contact.midname);
    				iHelp.execute();
    			}
    		
    		}
            mLogger.d( "saveContactsToDB() - Comitting transaction" );
    		mDatabase.setTransactionSuccessful();
    		mLogger.d( "db contacts cache saves successfully (" + cacheContactsList.size() + " contacts, saved " + i + " contacts and " +j + " phone numbers)" )  ;
    	} finally {
    		mDatabase.endTransaction();
            iHelp.close();
            
           	mDbHelper.CopyTempTable();
            
            mLogger.d( "saveContactsToDB() - Creating indexes" );
            mDbHelper.CreateIndexes();
    	}
    }
    
    /**
     * reads all contacts per MAC from the cache contacts data base and returns a complete cache contacts list per name requested
     */
    public List<CacheContactEntry> getContactsCache( 	String Firstname, 
    													String Middlename,
														String Lastname,  
														boolean useConnected ) {
    	String addr;

        if ( useConnected ) {
            addr = mContactsDevAddr;
        } else {
            addr = mNotifyContactsDevAddr;
        }
		List<CacheContactEntry> cacheContactsList = new ArrayList<CacheContactEntry>();
    	if (!mNotifyContactsDevAddr.isEmpty())
    	{
    		String whereClause;
            ArrayList<String> whereList = new ArrayList<String>();

            whereClause = BTContactsDB.COLUMN_MAC + " = ?";
            whereList.add( addr );

            if ( !Firstname.isEmpty() ) {
            	whereList.add( Firstname );
                if ( !Middlename.isEmpty() ) {           	
	            	whereClause += " AND " + BTContactsDB.COLUMN_FIRST_NAME + " = ?";	            	
	            	whereClause += " AND " + BTContactsDB.COLUMN_MID_NAME + " = ?";
	            	whereList.add( Middlename );
                } else {
	            	whereClause += " AND trim(" + BTContactsDB.COLUMN_FIRST_NAME + "||' '||" + BTContactsDB.COLUMN_MID_NAME + ") = ?";                	
                }
            }

            if ( !Lastname.isEmpty() ) {
            	whereClause += " AND " + BTContactsDB.COLUMN_LAST_NAME + " = ?";
            	whereList.add( Lastname );            	
            }

    		Cursor cursor = mDbHelper.createTableCursor( 
                whereClause, 
                whereList.toArray( new String[ whereList.size() ]) );
	
    		DbEntry dbContact;
    		String saveFirstname;
    		String saveLastname;

            CacheContactEntry lastContact = null;

            int iPhones = 0;

            // Build contact list assuming we get them by contact order.
    		while ( cursor.moveToNext() )
    		{
                dbContact = getCursorDbEntry( cursor );

                if (    ( lastContact == null ) || 
                        ( !lastContact.firstname.equals( dbContact.firstname ) ) || 
                        ( !lastContact.midname.equals( dbContact.midname ) ) ||
                        ( !lastContact.lastname.equals( dbContact.lastname ) ) ) {
                    // Not first contact but different one.
                    if ( lastContact != null ) {
                        cacheContactsList.add( lastContact );
                    }
                    lastContact = new CacheContactEntry();
                    lastContact.firstname = dbContact.firstname;
                    lastContact.lastname = dbContact.lastname;
                    lastContact.midname = dbContact.midname;
                }
                Phone phone = new Phone();
                phone.type = dbContact.type;
                phone.number = dbContact.number;
                lastContact.phoneNumbers.add( phone );
                iPhones++;
    		}
    		cursor.close();
            if ( lastContact != null ) {
                cacheContactsList.add( lastContact );
            }
            mLogger.i( "read " + iPhones + " phones numbers." );
    	}
    	else
    	{
            mLogger.i( "mNotifyContactsDevAddr is Empty" );
    	}
        mLogger.d( "Get Contacts Cache List size:" + cacheContactsList.size() );
        return cacheContactsList;
    }
    
    public List<CacheContactEntry> getContactsCache( String Firstname, String Middlename, String Lastname ) {
        return getContactsCache( Firstname, Middlename, Lastname, false );
    }

    /**
     * reads all contacts per MAC from the cache contacts data base and returns a contact entry per requested phone number
     */
    public CacheContactEntry getContactCacheName(String phoneNumber) {
    	mLogger.d( "number:" + phoneNumber);
    	String number = phoneNumber.replaceAll("-", "");
        
     	if (!mNotifyContactsDevAddr.isEmpty() && !number.isEmpty())
     	{
     		String whereClause = BTContactsDB.COLUMN_MAC + " = ?";
            String[] whereParams = new String[] { mNotifyContactsDevAddr };
            
     		Cursor cursor = mDbHelper.createTableCursor( whereClause, whereParams );
     		cursor.moveToFirst();
 
			DbEntry dbContact;
			int numberLength = number.length();  		
 			int dbContactNumberLength;

     		while (!cursor.isAfterLast())
     		{
     			do
     			{
     				dbContact = getCursorDbEntry( cursor );
     				dbContactNumberLength = dbContact.number.length();
     				
     				if ((dbContactNumberLength < CONTACT_LEN_FOR_MATCH) || (numberLength < CONTACT_LEN_FOR_MATCH))
     				{
     					if (dbContact.number.compareTo(number) != 0)
     						continue;
     				}
     				else
     				{
      					//compare least CONTACT_LEN_FOR_MATCH digits of both phone numbers
     					if (dbContact.number.substring(dbContactNumberLength - CONTACT_LEN_FOR_MATCH).compareTo(number.substring(numberLength - CONTACT_LEN_FOR_MATCH)) != 0)
     						continue;
     				}
     				
					//equal
     				CacheContactEntry lastContact = new CacheContactEntry();
     				lastContact.firstname = dbContact.firstname;
     				lastContact.lastname = dbContact.lastname;
     				lastContact.midname = dbContact.midname;
     				Phone phone = new Phone();
     				phone.type = dbContact.type;
     				phone.number = dbContact.number;
     				lastContact.phoneNumbers.add( phone );
     				cursor.close();
     				mLogger.d( "number:" + phone.number + " first name:" + lastContact.firstname + " mid name:" + lastContact.midname +" last name" + lastContact.lastname);
     				return (lastContact);
     			} while (cursor.moveToNext());
     		}
     		cursor.close();
    		mLogger.d( "Contact's name:" );
    		return (null);
     	}
     	else
     	{
             mLogger.i( "mNotifyContactsDevAddr or number is Empty" );
             return (null);
     	}
    }
    
    /**
     * reads all contacts per MAC from the cache contacts data base and returns a cache contacts name list
     */
    public List<CacheContactEntry> getContactsCacheNames() {
		List<CacheContactEntry> cacheContactsList = new ArrayList<CacheContactEntry>();
    	if (!mNotifyContactsDevAddr.isEmpty())
    	{
    		String whereClause = BTContactsDB.COLUMN_MAC + " = ?";
            String[] whereParams = new String[] { mNotifyContactsDevAddr };
    		Cursor cursor = mDbHelper.createTableCursor( whereClause, whereParams );
    		cursor.moveToFirst();
    		DbEntry dbContact;
    		String saveFirstname;
    		String saveMiddlename;
    		String saveLastname;
    		while (!cursor.isAfterLast())
    		{
    			CacheContactEntry contact = new CacheContactEntry();
    			dbContact = getCursorDbEntry(cursor);
    			contact.firstname = dbContact.firstname;
    			saveFirstname = dbContact.firstname;
    			contact.lastname = dbContact.lastname;
    			saveLastname = dbContact.lastname;
    			contact.midname = dbContact.midname;
    			saveMiddlename = dbContact.midname;

	 	   		while ( cursor.moveToNext() )
	 	   		{
	 	   			dbContact = getCursorDbEntry(cursor);
	 	   			if ( 	dbContact.firstname.equals(saveFirstname) && 
	 	   					dbContact.lastname.equals(saveLastname) && 
	 	   					dbContact.midname.equals(saveMiddlename) )
	 	   			{
	 	   				
	 	   			}
	 	   			else
	 	   			{
	 	   				break;
	 	   			}
	 	   		}
	 	   		cacheContactsList.add(contact);
    		}
    		cursor.close();
    	}
    	else
    	{
            mLogger.i( "mNotifyContactsDevAddr is Empty" );
    	}
        mLogger.d( "Get Contacts Cache List size:" + cacheContactsList.size() );
        return cacheContactsList;
    }

    /**
     * counts number of contacts per MAC from the cache contacts data base and returns the counted number.
     */
    public int countContactsCacheNames() {
    	int count = 0;
    	
    	if (!mNotifyContactsDevAddr.isEmpty())
    	{
    		String whereClause = BTContactsDB.COLUMN_MAC + " = ?";
    		String[] whereParams = new String[] { mNotifyContactsDevAddr };
    		Cursor cursor = mDbHelper.countCursor( whereClause, whereParams );
    		if (cursor != null)
    		{
    			try {
    				cursor.moveToFirst();
    				count = cursor.getInt(0);
    			}   finally {
    				cursor.close();
    			}   				
    		}
    	}
    	else
    	{
            mLogger.i( "mNotifyContactsDevAddr is Empty" );
    	}
    	mLogger.d( "size:" + count );
    	return count;
    }
    
    /**
     * reads the db entry at cursor location
     */
    private DbEntry getCursorDbEntry(Cursor cursor) {

    	DbEntry dbContact = new DbEntry();
//      mac               = cursor.getString(0);
        dbContact.firstname = cursor.getString(1);
        dbContact.midname = cursor.getString(2); 
        dbContact.lastname = cursor.getString(3);
        dbContact.number = cursor.getString(4);
        dbContact.type = cursor.getInt(5);
       

        return dbContact;
    }
    
    /**
     * deletes the entire data base
     */
    public void deleteContactsCacheDB(){
    	mDatabase.delete(BTContactsDB.TABLE_CONTACTS_CACHE_TEMP, null, null);
    	mDatabase.delete(BTContactsDB.TABLE_CONTACTS_CACHE, null, null);
    }
    
    /**
     * deletes a specific MAC from the data base
     */
    /* package*/ void deleteMacFromDB(String devAddr){
    	try {
    		String mac = devAddr.replaceAll(":", "");
    		String whereClause = BTContactsDB.COLUMN_MAC + "= ?";
            String[] whereParams = new String[] { mac };
    		mDatabase.delete(BTContactsDB.TABLE_CONTACTS_CACHE_TEMP, whereClause, whereParams);
    		mDatabase.delete(BTContactsDB.TABLE_CONTACTS_CACHE, whereClause, whereParams);
        }
        catch( Exception e )
        {
            mLogger.e( "Delete MAC failure: ", e);
        }
    }
    
    /**
     * Aborts creation of Contacts hash
     */
    void abortCreateContactsCache(){
    	mContactsDevAddr = "";
    }
    
    /**
     * Sets the MAC to be used when getting Contacts from the Contacts cache
     */
    void setNotifyContactsCacheAddr(String devAdd){
        mLogger.d( "SetNotifyContactsCacheAddr:" + devAdd);
   	    mNotifyContactsDevAddr = devAdd.replaceAll( ":", "" );
    }

     /**
     * gets the list of contact names from the android contact table. 
     */
	private List<CacheContactEntry> getContactList( ) {
		List<CacheContactEntry> cacheContactsList = new ArrayList<CacheContactEntry>();
		
		// Get all names in the contact list
        String whereName = ContactsContract.Data.MIMETYPE + " = ?"; 
        String[] whereNameParams = new String[] {   ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE }; 
        String[] columns = new String[] {   ContactsContract.RawContacts.CONTACT_ID, 
                                            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, 
                                            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, 
                                            ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
                                        };
        Cursor nameCur = mContext.getContentResolver().query(
            ContactsContract.Data.CONTENT_URI, 
        	columns, 
            whereName, 
            whereNameParams, 
            null );

        if ( nameCur == null) {
            mLogger.e( "Cursor nameCur is null" );
            return cacheContactsList;
        } 

      
        // Put them in the cacheContactsList
        int idColumnIndex = nameCur.getColumnIndex( ContactsContract.RawContacts.CONTACT_ID );
        int firstnameColumnIndex = nameCur.getColumnIndex( ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME );
        int lastnameColumnIndex = nameCur.getColumnIndex( ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME );
        int middlenameColumnIndex = nameCur.getColumnIndex( ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME );

        while ( nameCur.moveToNext() ) {

            String firstname = nameCur.getString(firstnameColumnIndex);
            String lastname = nameCur.getString(lastnameColumnIndex);
            String middlename = nameCur.getString(middlenameColumnIndex);

            if ( firstname == null ) {
                firstname = "";
            } 

            if ( lastname == null ) {
                lastname = "";
            } 
            
            if ( middlename == null ) {
                middlename = "";
            } 

            CacheContactEntry contact = new CacheContactEntry();
            contact.firstname = firstname;
            contact.lastname = lastname;
            contact.midname = middlename;
            contact.id = nameCur.getLong( idColumnIndex );
            cacheContactsList.add(contact);

        }
        nameCur.close();

        // Load phone numbers into the contacts
        String[] phoneColumns = new String[] {
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID, 
            ContactsContract.CommonDataKinds.Phone.TYPE, 
            ContactsContract.CommonDataKinds.Phone.NUMBER
        };                                       

        Cursor phoneCur = mContext.getContentResolver().query(  ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
                                                                phoneColumns, 
                                                                null, 
                                                                null, 
                                                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID );

        if ( phoneCur == null ) {
            mLogger.e( "Failed searching for contact name for id ");
            return cacheContactsList;
        }

        idColumnIndex = phoneCur.getColumnIndex( ContactsContract.CommonDataKinds.Phone.CONTACT_ID );
        int typeColumnIndex = phoneCur.getColumnIndex( ContactsContract.CommonDataKinds.Phone.TYPE );
        int numberColumnIndex = phoneCur.getColumnIndex( ContactsContract.CommonDataKinds.Phone.NUMBER );

        CacheContactEntry contact = null;
        while ( phoneCur.moveToNext() ) {

            long contactID = phoneCur.getLong( idColumnIndex );

            if ( ( contact == null ) || ( contact.id != contactID ) ) {
                contact = null;
                for ( CacheContactEntry searchContact : cacheContactsList ) {
                    if ( searchContact.id == contactID ) {
                        contact = searchContact;
                        break;
                    }
                }
            }
            if ( contact == null ) {
                mLogger.d( "Failed searching for contact name for id " + contactID );
            } else {
                Phone phone = new Phone();
                phone.number = phoneCur.getString( numberColumnIndex ).replaceAll("-", "");
                int phoneType = phoneCur.getInt( typeColumnIndex );
                switch ( phoneType ) {
                    case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                        phone.type = BTContactsDB.PHONE_CONTACTS_HOME;
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                        phone.type = BTContactsDB.PHONE_CONTACTS_MOBILE;
                        break;
                    case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                        phone.type = BTContactsDB.PHONE_CONTACTS_WORK;
                        break;
                    default:
                        phone.type = BTContactsDB.PHONE_CONTACTS_OTHER;
                        break;
                }
                contact.phoneNumbers.add( phone );

            }
        }
        phoneCur.close();

        int phoneCount = 0;

        // Now prune all contacts with no phone numbers and duplicates
        List<CacheContactEntry> cachedContactsWithNumbers = new ArrayList<CacheContactEntry>();

        for ( CacheContactEntry populatedContact : cacheContactsList ) {

            if ( populatedContact.phoneNumbers.size() > 0 ) {

                // Search for dulplicate 
                CacheContactEntry searchContact = null;

                for ( CacheContactEntry existingContact : cachedContactsWithNumbers ) {
                    if (    populatedContact.lastname.equals( existingContact.lastname ) && 
                            populatedContact.firstname.equals( existingContact.firstname ) && 
                            populatedContact.midname.equals( existingContact.midname ) ) {
                        searchContact = existingContact;
                        mLogger.d(  String.format( "contact id %d duplicates contact id %d %s %s %s", 
                                                    existingContact.id, 
                                                    populatedContact.id, 
                                                    populatedContact.lastname, 
                                                    populatedContact.midname, 
                                                    populatedContact.firstname ) );
                        break;
                    }
                }
                if ( searchContact == null ) {
                    cachedContactsWithNumbers.add( populatedContact );
                    phoneCount++;
                }           
            } else {
                mLogger.d( String.format(   "contact id %d has no phone numbers %s %s %s", 
                                            populatedContact.id,
                                            populatedContact.lastname,
                                            populatedContact.midname,
                                            populatedContact.firstname ) );
            }
        }
        mLogger.i( "total phone count " + phoneCount );
        return cachedContactsWithNumbers;
	}
	
    private boolean hasCacheChanged( List<CacheContactEntry> newContacts )  {

        List<CacheContactEntry> oldContacts = getContactsCache( "", "", "", true );

        if ( newContacts.size() != oldContacts.size() ) {
            // The number of contacts has changed. Need to update the cache. 
            mLogger.d( "number of contacts in phone book has changed (old - " + oldContacts.size() + ", new - " + newContacts.size() );
            return true;
        }

        // Need to check that every contact we have on the newList is similar to a contact list on the cache.
        for ( CacheContactEntry newContact : newContacts ) {

            CacheContactEntry cachedOldContact = null;

            for ( CacheContactEntry oldContact : oldContacts ) {

                if (    oldContact.firstname.equals( newContact.firstname ) && 
                        oldContact.lastname.equals( newContact.lastname ) &&
                        oldContact.midname.equals( newContact.midname ) ) {
                    cachedOldContact = oldContact;
                    break;
                }
            }
            if ( cachedOldContact == null ) {
                mLogger.d( "could not find " + newContact.firstname + " " + newContact.midname  + " " + newContact.lastname + " in the cache" );
                return true;               
            }


            if ( cachedOldContact.phoneNumbers.size() != newContact.phoneNumbers.size() ) {
                mLogger.d( "number of phone numbers has changed" );
                return true;
            }

            // Search for all the phone numbers from the cache in new contact.
            for ( Phone newPhone : newContact.phoneNumbers ) {

                boolean phoneNumberFound = false;

                for ( Phone oldPhone : cachedOldContact.phoneNumbers ) {
                    if ( ( oldPhone.type == newPhone.type ) && oldPhone.number.equals( newPhone.number )) {
                        phoneNumberFound = true;
                        break;
                    }
                }
                if ( !phoneNumberFound ) {
                    // Could not locate the phone number. The cache should change.
                    mLogger.d( "Phone number " + newPhone.number + " not found for new contact. Cache should be updated" );
                    return true;
                }
            }
        }
        mLogger.d( "Phone book cache is already up to date" );
        return false;
    }
	
	boolean createContactsCache(String devAddr) {
		mContactsDevAddr = devAddr.replaceAll( ":", "" );

        if ( !mContactsDevAddr.isEmpty() ) {

            mLogger.d( "createContactsCache() - retrieving list of contact names for saving" );

            // Retrieve contacts from phone book.
            List<CacheContactEntry> ContactCacheList = getContactList();

            mLogger.d( "Contact Cache List size: " +   ContactCacheList.size() );

            mLogger.d( "createContactsCache() - Checking if cache has changed" );

            if ( hasCacheChanged( ContactCacheList ) )
            {
                if ( !mContactsDevAddr.isEmpty()) {
                    mLogger.d( "createContactsCache() - Deleting old contacts" );

                    deleteMacFromDB(mContactsDevAddr);

                    mLogger.d( "createContactsCache() - Adding new contacts");
                    saveContactsToDB(ContactCacheList, mContactsDevAddr);
                    mLogger.d( "Contact Cache db size: " + getContactsCacheSize() );
                    return true;
                }
            }
        }
        return false;
	}
	
    /********************************
     * Check for Cache requests
     *********************************/	
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String number;

            if(intent.getAction().equals(ACTION_GET_CONTACT_NAME)) {
                number = intent.getStringExtra(EXTRA_PHONE_NUMBER);
                mLogger.d( "ACTION_GET_CONTACT_NAME, number: " +   number );
                if (number != null)
                {
                	// run Cache request thread
                	mCacheRequestThread = new Thread( new Runnable() {
                		@Override
                		public void run() {
                			cacheThreadMethod(number);
                		}
                	}, "CacheRequestThread");
                	mCacheRequestThread.start();
                }
            }
        }
    };
    
    private void cacheThreadMethod(String number) {
    	CacheContactEntry contactEntry = getContactCacheName(number);
    	if (contactEntry != null)
    	{
    		mLogger.d("ACTION_RESPONSE_CONTACT_NAME, number: " +   contactEntry.phoneNumbers.get(0).number + " name:" + contactEntry.firstname + " "
    				+ contactEntry.midname + " " +  contactEntry.lastname);
            Intent intent;
            intent = new Intent(ACTION_RESPONSE_CONTACT_NAME);
            intent.putExtra(EXTRA_PHONE_NUMBER, number);// return the requested phone number and not the number as found in the cache. They might differ.
            intent.putExtra(EXTRA_CONTACT_FIRST_NAME, contactEntry.firstname);
            intent.putExtra(EXTRA_CONTACT_MID_NAME, contactEntry.midname);
            intent.putExtra(EXTRA_CONTACT_LAST_NAME, contactEntry.lastname);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            mContext.sendBroadcast(intent);
    	}
    }
}