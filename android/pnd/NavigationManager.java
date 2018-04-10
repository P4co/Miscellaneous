
import android.database.Cursor;
import android.content.Context;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;

import android.roadtrack.eventrepository.EventRepositoryReader;

import com.roadtrack.inrix.TrafficRoutingServices;
import com.roadtrack.pnd.NavigationManager.RouteDataState;
import com.roadtrack.Navigation;
import com.roadtrack.util.RTLog;
import java.util.List;
import java.util.ArrayList;
import android.os.SystemClock;
import com.roadtrack.inrix.*;

public class NavigationManager {
    private static final String TAG = "NavigationManager";
    
    private static final String EMPTY_NAME = "???";

    private PNDHandler     mPndHandler    = null;
    private PndDB          mDbHelper      = null;  
    private SQLiteDatabase mDatabase      = null;
    private Context        mContext       = null;
    // class instance
    private static NavigationManager mNavigationManager = null;
    
    private EventRepositoryReader        mRepositoryReader;
    
    private String[] routeColumns = {  PndDB.COLUMN_ID, PndDB.COLUMN_LATREF, PndDB.COLUMN_LONREF, 
            PndDB.COLUMN_LAT, PndDB.COLUMN_LON, PndDB.COLUMN_TEXT, PndDB.COLUMN_ACTION, 
            PndDB.COLUMN_TXTOP, PndDB.COLUMN_TSTMP, PndDB.COLUMN_ACTIVE, PndDB.COLUMN_TRAFFIC, 
            PndDB.COLUMN_TEXTPHON};

    private String[] favoriteColumns = { PndDB.COLUMN_ID, PndDB.COLUMN_LATREF, PndDB.COLUMN_LONREF, 
            PndDB.COLUMN_FAVORITE_NAME };


    public enum RouteDataState {
    	NOT_ACTIVE, 	// Finished navigation - will be removed.
    	ACTIVE,			// Currently running navigation.
    	ACTIVE_PAUSED, 	// Running navigation that has been stoped.
    	APPROVED, 		// New approved route to be calculated.
    	NEW_ROUTE,		// New route that wait for approval by the user.
    	IN_CONFIRMATION // Route that in a confirmation process.  
    }

    public enum RouteDataOrigin {CALLCENTER, WEB, APP, USER, UPDATEROUTE}

    private RTLog mLogger  = RTLog.getLogger(TAG, "navigation", RTLog.LogLevel.INFO );
    
    private final static String pullApprovedWhere = PndDB.COLUMN_ACTIVE+" = ? OR "+PndDB.COLUMN_ACTIVE+" = ? OR "+PndDB.COLUMN_ACTIVE+" = ? OR "+PndDB.COLUMN_ACTIVE+" = ?";
    private final static String[] pullApprovedWhereArgs = new String[] {
    		Integer.toString(RouteDataState.ACTIVE.ordinal()),
    		Integer.toString(RouteDataState.ACTIVE_PAUSED.ordinal()),
    		Integer.toString(RouteDataState.APPROVED.ordinal()),
    		Integer.toString(RouteDataState.NOT_ACTIVE.ordinal())};

    /**
     * Route Data class holds the routing data received from the back office
     */
    public static class RouteData {
        public long    id;
        public String  routingLatitudeReference;
        public String  routingLongitudeReference;
        public String  routingLatitude;
        public String  routingLongitude;
        public String  routingText;
        public String  routingTextPhonetic;
        public int     routingAction;
        public boolean routingTrafficUsed;
        public int     routingTextOpcode;
        public long    routingTimeStamp;
        public RouteDataState dataState;

        public String toString() {
            return "{ id = " + id + 
                    ", state = " + dataState +
                    ", action = " + routingAction +
                    ", lat,lon = " + routingLatitude + "," + routingLongitude +
                    ", text = [" + routingText + "]" +
                    ", textPhonetic = " + routingTextPhonetic + 
                    ", opcode = " + routingTextOpcode + 
                    ", timestamp = " + routingTimeStamp + 
                    ", traffic = " + routingTrafficUsed +
                    ", latref,lonref = " + routingLatitudeReference + "," + routingLongitudeReference +
                    " }";
        }
    }

    public static class FavoriteData {
        public long   id;
        public String latitude;
        public String longitude;
        public String poiName;

		public FavoriteData() {
            this.id = 0;
            this.latitude = "";
            this.longitude = "";
            this.poiName = "";
        }

        public FavoriteData(long id, String latitude, String longitude, String poiName) {
            this.id = id;
            this.latitude = latitude;
            this.longitude = longitude;
            this.poiName = poiName;
        }

        @Override
        public String toString() {
            return "{id: " + id + " name: " + poiName + " lat: " + latitude + " lon: " + longitude + "}";
        }
    }

    /**
     * Navigation Manager Constructor, 
     * initiates the PND Database class
     * constructor is private and class can be instantiaded once
     */
    private NavigationManager(Context context) {
        mLogger.i(" Constructor Called!!");
        mContext  = context;
        mDbHelper = new PndDB(context);  
        mDatabase = mDbHelper.getWritableDatabase();
        mRepositoryReader = (EventRepositoryReader)mContext.getSystemService(Context.EVENTREP_SYSTEM_SERVICE);
        Navigation.Instance().setContext(mContext, this);
    }

    /**
     * sets pnd handler instance
     */
    public void setPndHandler(PNDHandler pndHandler) {
        mPndHandler = pndHandler;
    }

    /**
     * gets pnd handler instance
     */
    public PNDHandler getPndHandler() {
        return mPndHandler;
    }
    /**
     * returns the single instance of Navigation Manager class.
     */
    public static NavigationManager getInstance(Context context) {
        if(mNavigationManager == null) {
            mNavigationManager = new NavigationManager(context);
        }

        return mNavigationManager;
    }

    

    /**
     * returns a count of Route data items in the Route Database Table
     */
    public int getQueuedRoutesCount() {
    	Cursor cursor = null;
    	int count = -1;
    	try {
            	cursor = mDatabase.query(PndDB.TABLE_ROUTES,routeColumns, null, null, null, null, null);
            
            	count = cursor.getCount();
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
		}
       
        return count;
    }

    /**
     * return the route state of the approved row in the Route Database Table
     * @return
     */
    public RouteDataState getApprovedRouteDataState()
    {
        RouteData routeData = pullApprovedQueuedRoutes();
        if( routeData != null ) {
            return routeData.dataState;
        }
        return RouteDataState.NOT_ACTIVE;
    }


    /**
     * reads routing data from the data base and return the oldest in RouteData class
     */
    public RouteData pullQueuedRoutes() {
        RouteData routeData = null;
        Cursor cursor = null;

    	try {
             cursor = mDatabase.query(PndDB.TABLE_ROUTES,
                    routeColumns, null, null, null, null, PndDB.COLUMN_ID +" ASC");

            if (cursor.moveToFirst()) {
                routeData = cursorToRouteData(cursor);
            }
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}
        
        return routeData;
    }
    
    

    /**
     * reads routing data from the data base and return the newest in RouteData class
     */
    public RouteData pullLastQueuedRoutes() {
        RouteData routeData = null;
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_ROUTES,
                    routeColumns, null, null, null, null, PndDB.COLUMN_ID +" DESC");

            if (cursor.moveToFirst()) {
                routeData = cursorToRouteData(cursor);
            }
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return routeData;
    }
    
    /**
     * reads approved routing data from the data base and return it in RouteData class
     */
    public RouteData pullApprovedQueuedRoutes() {
        RouteData routeData = null;
        Cursor cursor = null;

    	try {
    			cursor = mDatabase.query(PndDB.TABLE_ROUTES, routeColumns,pullApprovedWhere,pullApprovedWhereArgs,null, null, PndDB.COLUMN_ID +" ASC");

            if (cursor.moveToFirst()) {
                routeData = cursorToRouteData(cursor);
            }
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return routeData;
    }
    
    /**
     * reads routing data from the data base that are pending to be approved by user and return it in RouteData class
     */
    public RouteData pullNewQueuedRoute() {
        RouteData routeData = null;
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_ROUTES,
    	                routeColumns, PndDB.COLUMN_ACTIVE+" = ?", new String[] { Integer.toString(RouteDataState.NEW_ROUTE.ordinal())}, null, null, PndDB.COLUMN_ID +" ASC");

            if (cursor.moveToFirst()) {
                routeData = cursorToRouteData(cursor);
            }
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return routeData;
    }
    
    
    /**
     * reads routing data from the data base that are not approved by user and return it in RouteData class
     */
    public RouteData pullInConfirmationQueuedRoute() {
        RouteData routeData = null;
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_ROUTES,
    	                routeColumns, PndDB.COLUMN_ACTIVE+" = ?", new String[] { Integer.toString(RouteDataState.IN_CONFIRMATION.ordinal())}, null, null, PndDB.COLUMN_ID +" ASC");

            if (cursor.moveToFirst()) {
                routeData = cursorToRouteData(cursor);
            }
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return routeData;
    }
    /**
     * pull specific route by his ID , return null if not found. 
     * @param routeID
     * @return
     */
    		
    private RouteData pullRouteById(int routeID)
    {
        RouteData routeData = null;
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_ROUTES,routeColumns, PndDB.COLUMN_ID+" = ?", new String[] { Integer.toString(routeID)}, null, null, null);

            if (cursor.moveToFirst()) {
                routeData = cursorToRouteData(cursor);
            }
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return routeData;
    }
   /**
    * set route with new data state
    * @param state
    */
    public void setRouteState(RouteDataState state)
    {
    	setRouteState((int)pullApprovedQueuedRoutes().id,state);
    }
    
    /**
     * set a route new data state.
     * @param routeID
     * @param state
     */
    public void setRouteState(int routeID,RouteDataState state)
    {
    	
    	RouteData routeData = null;
    	RouteData tempData = null;
    	routeData = pullRouteById(routeID);
    	if(null == routeData)
    	{
    		mLogger.e("route id: "+routeID+" not exist !");
    		return;
    	}
    	
    	
    	// remove current state place to by change to
    	switch(state)
    	{
    	case IN_CONFIRMATION :
    		long timer = SystemClock.uptimeMillis();
    		tempData = pullInConfirmationQueuedRoute();
    		if (tempData != null )
    		{
    			removeQueuedInConfirmationRoute();		
    		}		
    		break;
    	case ACTIVE:
    	case ACTIVE_PAUSED:
    	case NOT_ACTIVE:
    	case APPROVED:
    		tempData = pullApprovedQueuedRoutes();
    		if((null != tempData) && (tempData.id != routeID))
    		{ 			
    			removeRoute((int)tempData.id);
    		}
    		break;
    		
    	}

    	updateRouteState(routeID,state);
    }
    
    private void updateRouteState(int routeID,RouteDataState state)
    {
        ContentValues values = new ContentValues();  
        values.put(PndDB.COLUMN_ACTIVE,  state.ordinal());

    	mDatabase.update(PndDB.TABLE_ROUTES, values, PndDB.COLUMN_ID+" = ?", new String[]{Integer.toString(routeID)});
    }

    private void removeRoute(int routeID)
    {
    	mDatabase.delete(PndDB.TABLE_ROUTES ,PndDB.COLUMN_ID+" = ?",new String[]{Integer.toString(routeID)});
    }
    
    /**
     * remove the route from "Approved" row in data base
     */
    public void removeApprovedRoute()
    {
    	mDatabase.delete(PndDB.TABLE_ROUTES ,pullApprovedWhere, pullApprovedWhereArgs);
    }
    
    /**
     * remove the route from "Pending" row in data base
     */
    public void removeQueuedNewRoute()
    {
    	RouteData routeData = null;
    	
    	routeData = pullNewQueuedRoute();
    	
    	if(null != routeData)
    	{
    		removeNewRoute((int)routeData.id);
    	}
    }
    private void removeNewRoute(int routeID)
    {
    	mDatabase.delete(PndDB.TABLE_ROUTES, PndDB.COLUMN_ACTIVE+" = ?" + " AND _id = ?", new String[]{Integer.toString(RouteDataState.NEW_ROUTE.ordinal()),Integer.toString(routeID)});
    }
    
    /**
     * remove the route from "Not Confirm" row in data base
     */
    public void removeQueuedInConfirmationRoute()
    {
    	RouteData routeData = null;
    	
    	routeData = pullInConfirmationQueuedRoute();
    	
    	if(null != routeData && RouteDataState.IN_CONFIRMATION == routeData.dataState)
    	{
    		removeRoute((int)routeData.id);
    	}
    }
    public FavoriteData pullSingleFavorite(int favoriteId) {

        FavoriteData result = null;
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_FAVORITES,
    	                favoriteColumns, PndDB.COLUMN_ID+"="+favoriteId, null, null, null, null);

    	        if (cursor.moveToFirst()) {
    	            result = cursorToFavoriteData(cursor);
    	        }
    	        
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return result;
    }

    public ArrayList<FavoriteData> pullAllFavorites() {
        ArrayList<FavoriteData> favoriteDataList = new ArrayList<FavoriteData>();
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_FAVORITES,
    	                favoriteColumns, null, null, null, null, null);

    	        cursor.moveToFirst();
    	        while(!cursor.isAfterLast()) {
    	            favoriteDataList.add(cursorToFavoriteData(cursor));
    	            cursor.moveToNext();
    	        }
    	        
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return favoriteDataList;
    }
    
    public ArrayList<RouteData> pullHistoryRoutes() {
        ArrayList<RouteData> routesDataList = new ArrayList<RouteData>();
        Cursor cursor = null;
    	try {
    			cursor = mDatabase.query(PndDB.TABLE_HISTORY_ROUTES,
    	        		routeColumns, null, null, null, null, PndDB.COLUMN_ID + " DESC");

    	        cursor.moveToFirst();
    	        while(!cursor.isAfterLast()) {
    	        	routesDataList.add(cursorToRouteData(cursor));
    	            cursor.moveToNext();
    	        }
    	        
    	}catch (Exception e)
    	{
    		mLogger.e(" failed to read DataBase",e);
    	}finally {
    		if(null != cursor)
    		{
    			cursor.close();
    		}
    	}

        return routesDataList;
    }
    
    /**
     * * clear history routes table *
     */
    public void clearHistoryRoutes(){
    	mDatabase.delete(PndDB.TABLE_HISTORY_ROUTES, null, null);	
    }

    /**
     * receives Cursor and read data into RouteData class
     */
    private RouteData cursorToRouteData(Cursor cursor) {
        RouteData routeData = new RouteData();

        try {
            routeData.id                        = cursor.getLong(cursor.getColumnIndex(PndDB.COLUMN_ID));
            routeData.routingLatitudeReference  = cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_LATREF));
            routeData.routingLongitudeReference = cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_LONREF));
            routeData.routingLatitude           = cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_LAT));
            routeData.routingLongitude          = cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_LON));
            routeData.routingText               = cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_TEXT));
            routeData.routingAction             = Integer.parseInt(cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_ACTION)));
            routeData.routingTextOpcode         = Integer.parseInt(cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_TXTOP)));
            routeData.routingTimeStamp          = Long.valueOf(cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_TSTMP))).longValue();
            routeData.dataState                 = RouteDataState.values()[cursor.getInt(cursor.getColumnIndex(PndDB.COLUMN_ACTIVE))];
            routeData.routingTrafficUsed        = Integer.parseInt(cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_TRAFFIC))) == 0 ? false : true;
            routeData.routingTextPhonetic       = cursor.getString(cursor.getColumnIndex(PndDB.COLUMN_TEXTPHON));
        } catch( Exception e ) {
            mLogger.e(  "Bad route data: ", e);
        }

        return routeData;
    }

    private FavoriteData cursorToFavoriteData(Cursor cursor) {
        return new FavoriteData(cursor.getLong(0),
								cursor.getString(1),
                                cursor.getString(2),
								cursor.getString(3));
    }

    /**
     * receives Cursor and read data into RouteData class
     */
    public static RouteData favoriteToRouteData(FavoriteData favorite) {
        RouteData routeData = new RouteData();

        routeData.id                        = favorite.id;
        routeData.routingLatitudeReference  = "";
        routeData.routingLongitudeReference = "";
        routeData.routingLatitude           = favorite.latitude;
        routeData.routingLongitude          = favorite.longitude;
        routeData.routingText               = favorite.poiName;
        routeData.routingTextPhonetic       = "";
        routeData.routingAction             = NavigationManager.RouteDataOrigin.USER.ordinal();
        routeData.routingTextOpcode         = 0;
        routeData.routingTimeStamp          = 0;
        routeData.dataState                 = RouteDataState.NOT_ACTIVE;
        routeData.routingTrafficUsed        = false;

        return routeData;
    }

    /**
     * creates a row of routing data from data provided in parameter.
     * deletes previous saved data
     */
    public long createRouteRecord(RouteData routeData){  

    	RouteData tempData = null;
    	switch(routeData.dataState)
    	{
    	case NEW_ROUTE:
    		tempData = pullNewQueuedRoute();
    		if(null != tempData)
    		{	
    			removeNewRoute((int)tempData.id);
    		}
    		break;
    	case IN_CONFIRMATION:
    		tempData = pullInConfirmationQueuedRoute();
    		if(null != tempData)
    		{	
    			removeQueuedInConfirmationRoute();
    		}
    		break;
    	case ACTIVE:
    	case APPROVED:
    	case ACTIVE_PAUSED:
    	case NOT_ACTIVE:
    		tempData = pullApprovedQueuedRoutes();
    		if(null != tempData)
    		{	
    			removeApprovedRoute();
    		}
    		break;
    	}

        ContentValues values = new ContentValues();  

        if(routeData.routingText == null) {
			mLogger.e("Asked to create route with null name. Lat: " + routeData.routingLatitude + 
					" Lon: " + routeData.routingLongitude + "Name will be " + EMPTY_NAME);
        	
        	routeData.routingText=EMPTY_NAME;
        }
        
        if (routeData.routingTextPhonetic == null)
        {
        	routeData.routingTextPhonetic = "";
        }
        
        values.put(PndDB.COLUMN_LATREF,  routeData.routingLatitudeReference);
        values.put(PndDB.COLUMN_LONREF,  routeData.routingLongitudeReference);
        values.put(PndDB.COLUMN_LAT,     routeData.routingLatitude);
        values.put(PndDB.COLUMN_LON,     routeData.routingLongitude);
        values.put(PndDB.COLUMN_TEXT,    routeData.routingText);
        values.put(PndDB.COLUMN_ACTION,  routeData.routingAction);
        values.put(PndDB.COLUMN_TXTOP,   routeData.routingTextOpcode);
        values.put(PndDB.COLUMN_TSTMP,   routeData.routingTimeStamp);
        values.put(PndDB.COLUMN_ACTIVE,  routeData.dataState.ordinal());
        values.put(PndDB.COLUMN_TRAFFIC, routeData.routingTrafficUsed ? 1 : 0);
        values.put(PndDB.COLUMN_TEXTPHON,routeData.routingTextPhonetic);
        
        return mDatabase.insert(PndDB.TABLE_ROUTES, null, values);
    }
    
    /**
     * creates a row of routing data from data provided in parameter.
	 * inserts in to the routes history table while maintaining unique entries only
	 * and at most 3 records.
     */
    public long createRouteHistoryRecord(RouteData routeData){  
    	ArrayList<RouteData> storedRoutes = pullHistoryRoutes();
    	
    	if(routeData.routingText == null) return 0;
    	if(routeData.equals(EMPTY_NAME)) return 0;
    	
    	if((storedRoutes != null) && (routeData != null)){
    		//make sure the record is unique
    		for(RouteData storedRoute : storedRoutes){
    			if(storedRoute.routingText==null) {
    				mLogger.e("Route with null name. Lat: " + routeData.routingLatitude + 
    						" Lon: " + routeData.routingLongitude);
    				continue;
    			}
    			if(storedRoute.routingText.equals(routeData.routingText)){
    				return 0;
    			}
    		}
    	}else { 
    		mLogger.e("createRouteHistoryRecord : storedRoutes or routeData is null !");
    		return 0; 
    		}

    	// make sure we store no more then 3 records at a time
    	if(storedRoutes.size() >= PndDB.NUMBER_OF_STORED_ROUTES_IN_HISTORY){
    		String ALTER_TBL ="delete from " + PndDB.TABLE_HISTORY_ROUTES +
    			     " where rowid in (select rowid from "+ PndDB.TABLE_HISTORY_ROUTES+" order by _id LIMIT 1);";
    		mDatabase.execSQL(ALTER_TBL);
    	}
    	
    	ContentValues values = new ContentValues();  

        values.put(PndDB.COLUMN_LATREF,  routeData.routingLatitudeReference);
        values.put(PndDB.COLUMN_LONREF,  routeData.routingLongitudeReference);
        values.put(PndDB.COLUMN_LAT,     routeData.routingLatitude);
        values.put(PndDB.COLUMN_LON,     routeData.routingLongitude);
        values.put(PndDB.COLUMN_TEXT,    routeData.routingText);
        values.put(PndDB.COLUMN_ACTION,  routeData.routingAction);
        values.put(PndDB.COLUMN_TXTOP,   routeData.routingTextOpcode);
        values.put(PndDB.COLUMN_TSTMP,   routeData.routingTimeStamp);
        values.put(PndDB.COLUMN_ACTIVE,  routeData.dataState.ordinal());
        values.put(PndDB.COLUMN_TRAFFIC, routeData.routingTrafficUsed ? 1 : 0);
        values.put(PndDB.COLUMN_TEXTPHON,routeData.routingTextPhonetic);
        
        return mDatabase.insert(PndDB.TABLE_HISTORY_ROUTES, null, values);
    }
    
    public void createListOfFavorites(ArrayList<FavoriteData> favoriteList) {
        for(FavoriteData favData : favoriteList) {
            createFavoriteRecord(favData);            
        }
    }

    public long createFavoriteRecord(FavoriteData favoriteData) {
        ContentValues values = new ContentValues();  

        values.put(PndDB.COLUMN_LATREF,        favoriteData.latitude);
        values.put(PndDB.COLUMN_LONREF,        favoriteData.longitude);
        values.put(PndDB.COLUMN_FAVORITE_NAME, favoriteData.poiName);

        return mDatabase.insert(PndDB.TABLE_FAVORITES, null, values);   
    }

    /**
     * when status on routing is received, table is cleaned, and status of success is sent to BO
     */
    public void routeStatusReceived(int status) {
        emptyRouteTable();
        if(mPndHandler == null) {
            throw new NullPointerException("Null PND Handler instance!");
        }
        mPndHandler.sendRoutingStatusToBO_V2BO(status, PNDHandler.PND_SUCCESS);
    }

    /**
     * deletes all data in Route Table
     */
    public void emptyRouteTable() { 
        mDatabase.delete(PndDB.TABLE_ROUTES, null, null);
    }

    /**
     * remove route received from BO
     */
    public void removeBoNewRoute()
    {
    	mDatabase.delete(PndDB.TABLE_ROUTES, PndDB.COLUMN_ACTIVE +" = "+RouteDataState.APPROVED.ordinal(), null);
    }
    
    /**
     * deletes all data in Favorites Table
     */
    public void emptyFavoritesTable() {
        mDatabase.delete(PndDB.TABLE_FAVORITES, null, null);   
    }

    /**
     * Initiate navigation with the given route.
     * Route is stored in DB and then navigation is started.
     */
    public void startNavigationWithRoute(RouteData destinationRoute, boolean sendStatus, TrafficRoutingServices.Route trafficRoute, boolean sayCalc) {
    	boolean navigationHandledByPnd = false;
        mLogger.i("startNavigationToRoute: " + destinationRoute);

        // Update PND
        if(mPndHandler != null && mPndHandler.isGmlanRadioConfigured()) {
            navigationHandledByPnd = mPndHandler.startRoute(destinationRoute, sendStatus);
            removeApprovedRoute();
            mLogger.d("new route sent to Radio and removed from list");
        }

        // only if route not handled by pnd it remains as our responsibility and therefore should be stored in DB and handled by us.
        if(!navigationHandledByPnd){
        	// Start navigation if infotainment allowed 
        	if(mRepositoryReader.getIsInfotainmentAllowed())
        	{
	        	if(sayCalc){
	        		Navigation.Instance().startNavigation(trafficRoute, false, Navigation.StartSilently.NOT_SILENT);
	        	} else {
	        		Navigation.Instance().startNavigation(trafficRoute, false, Navigation.StartSilently.SILENT_NO_CALC_PROMPTS);
	        	}
        	}
        	
        }
    }

    /**
     * Update the Navigation engine with waypoints from the traffic application.
     * If the engine is waiting for traffic, it will start routing and guidance.
     */
    public void trafficUpdate(TrafficRoutingServices.Route trafficRoute) {
        Navigation.Instance().updateWaypoints(trafficRoute);
    }
}
