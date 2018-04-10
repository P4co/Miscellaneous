package com.roadtrack;

import java.io.FileInputStream;
import java.util.Properties;
import java.lang.Integer;
import com.roadtrack.util.RTLog;

/* MapWorkaroundManager
 * 
 * For any code workaround created to avoid some map issue ( that future to be repeired ) 
 * 1. add ID in map_workaround.conf file (by the file conventions).
 * 2. add this ID as option in folowing  MapWorkaroundID enum.
 * 3. wrap the workaround code with condition for using it calling  "isWorkaroundAllowed(MapWorkaroundID wa_id)"
 * 
 * notes:
 * Any workaround in the code that will not be added to the configuration file will auto maticlly considred as ENABLED.
 */

public class MapWorkaroundManager {

	private static MapWorkaroundManager mThisClass = null;
	private Properties mWaConfig = null;
	private final String WA_CONFIG_FILE = "/system/vendor/roadtrack/maps/map_workaround.conf";
	private final String TAG = "MapWorkaroundManager";
	
	private RTLog mLogger = RTLog.getLogger( TAG, "workaround", RTLog.LogLevel.DEBUG );
	
	// ***************  Workarounds *********** 
	
	
	public enum MapWorkaroundID
	{
		WA_ID_BR_SAOPAULO_SP_CITY("WA_ID_br_saopaulo_sp_city"),
		WA_ID_CO_BUENAVENTURA_CITY_CENTER("WA_ID_co_buenaventura_city_center"),
		WA_ID_AR_PROVINCE_CODES_CONVERSION("WA_ID_ar_province_code_conversion"),
		WA_ID_ALL_ACCENTED_LETTERS_REPLACE("WA_ID_all_accented_letters_replace"),
		WA_ID_CO_SEARCH_CITY_WITH_PROVINCE("WA_ID_co_search_city_with_province");
		
		private final String text;

		private MapWorkaroundID(final String text) {
		   this.text = text;
		}


		@Override
		public String toString() {
			return text;
		}
		
	};
	
	
	// ****************************************
	
	
	
	
	private MapWorkaroundManager ()
	{
		mThisClass = this;
		 // read workarounds
		readConfigurationFile(WA_CONFIG_FILE);
		
	}
	
	private void readConfigurationFile(String path)
	{
		mLogger.i("loading map workaround configurations.");
		FileInputStream fi = null;
        try {
            mWaConfig = new Properties();
             fi = new FileInputStream(path);
            mWaConfig.load(fi);
            
        } catch( Exception e ) {
        	mLogger.e("failed to read workaround configuration file. " , e);
        }finally {
        	if(null != fi)
        	{
        		try
        		{
        			fi.close();
        		}
        		catch(Exception e)
        		{
        			mLogger.e("failed to close fileInputStream");
        		}
        	}
		}
	}
	
	synchronized public static MapWorkaroundManager getInstance()
	{
		if ( null == mThisClass )
		{
			mThisClass = new MapWorkaroundManager();
		}
		
		return mThisClass;
	}
	
	public void reloadConfiguration()
	{
		readConfigurationFile(WA_CONFIG_FILE);
	}
	
	/**
	 * is the provided workaround is relevent in the installed map package.
	 * @param wa_id
	 * @return true if workaround should be invoked
	 */
	public boolean isWorkaroundAllowed(MapWorkaroundID wa_id)
	{

		String result = ((String)mWaConfig.getProperty(wa_id.toString(), "missing"));
		if (result.equalsIgnoreCase("true"))
		{
			mLogger.i("Map workaround - "+ wa_id + ": ENABLED.");
			return true;
		}
		else if (result.equalsIgnoreCase("missing"))
		{
			mLogger.i("Workaround configuration ID is missing , Enabling workaround !!");
			return true;
		}
		else
		{
			mLogger.i("Map workaround - "+ wa_id + ": DISABLED.");
			return false;
		}
		

	}
}
