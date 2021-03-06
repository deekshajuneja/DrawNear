package com.coms309r04.drawnear.views;

import com.coms309r04.drawnear.R;
import com.coms309r04.drawnear.connection.DrawingManager;
import com.coms309r04.drawnear.connection.GPSManager;
import com.coms309r04.drawnear.connection.ILocationUpdater;
import com.coms309r04.drawnear.data.DrawingItem;
import com.coms309r04.drawnear.tools.DrawingItemAdapter;
import com.coms309r04.drawnear.tools.IntentSwitcher;
import com.coms309r04.drawnear.tools.TabListener;
import com.parse.GetDataCallback;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseGeoPoint;

import android.Manifest;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class NearbyActivity extends Activity implements ILocationUpdater, SwipeRefreshLayout.OnRefreshListener {
	ListView listView;

	private static final int MENU_REFRESH_LIST = 9002;
	private static final int ALLOW_LOCATION_SERVICES = 9999;

	// to do: store drawings and location in separate manager class (so both Map
	// and Nearby can share it)
	// HashMap<String, DrawingItem> nearbyDrawings;
	// final ArrayList<DrawingItem> drawingsList = new ArrayList<>();

	ProgressBar pb;
	TextView noContent;

	private static final double ISU_LAT = 42.025410, ISU_LNG = -93.646085;

	private SwipeRefreshLayout swipeRefreshLayout;
	DrawingItemAdapter drawingAdapter = null;

	private GPSManager gps;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_nearby);

		swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
		swipeRefreshLayout.setOnRefreshListener(this);

		// Check if Location is allowed on the device
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(
				this,
				new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
				ALLOW_LOCATION_SERVICES
			);
		}

		gps = new GPSManager(this);

		pb = (ProgressBar) findViewById(R.id.progressBar1);
		pb.setVisibility(View.VISIBLE);

		noContent = (TextView) findViewById(R.id.no_content);
		noContent.setVisibility(View.INVISIBLE);
		noContent.setText("There are no drawings nearby.\nMake one and be the first!");

		//TextView loggedInAs = (TextView) findViewById(R.id.logged_in_as_nearby);
		//loggedInAs.setText(ParseUser.getCurrentUser().getUsername());

		//Set up ActionBar Tabs
		ActionBar actionBar = getActionBar();
		if(actionBar != null) {
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

			Tab tabNearby = actionBar.newTab().setText("Nearby").setTabListener(new TabListener<NearbyActivity>(this, NearbyActivity.class));
			actionBar.addTab(tabNearby);

			Tab tabMap = actionBar.newTab().setText("Map").setTabListener(new TabListener<MapActivity>(this, MapActivity.class));
			actionBar.addTab(tabMap);

			//actionBar.setStackedBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.black)));
			actionBar.selectTab(tabNearby);

			TabListener.tabsActive = true;
		}

		//Set up ListView
		if(drawingAdapter == null) {
			listView = (ListView) findViewById(R.id.nearby_list_view);
			drawingAdapter = new DrawingItemAdapter(NearbyActivity.this, DrawingManager.getInstance().getCurrentDrawingsList());
			listView.setAdapter(drawingAdapter);
			listView.setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					DrawingItem drawing = (DrawingItem) parent.getItemAtPosition(position);
					Intent intent = new Intent(NearbyActivity.this, DisplayPostActivity.class);
					intent.putExtra("id", drawing.getId());
					startActivity(intent);
				}
			});
		}

		// If there are already drawings in ArrayList from before, get rid of
		// the progress bar
		if(DrawingManager.getInstance().getCurrentDrawingsList().size() > 0) {
			pb.setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * This function is a callback function required to handle the result
	 * from a request permission dialog (in this case, for the user
	 * allowing the application to use LocationServices.
	 *
	 * @param requestCode
	 * @param permissions
	 * @param grantResults
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case ALLOW_LOCATION_SERVICES: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					// permission was granted, yay! Do the task you need to do.
					try {
						loadAndUpdateDrawings();
					} catch(SecurityException e) {
						Log.e("Map", "Not able to set location.");
					}
				} else {
					// permission denied, boo! Disable the
					// functionality that depends on this permission.
					Log.e("Map", "Permissions not granted to use location.");

					// Exit the application
					/*Intent intent = new Intent(Intent.ACTION_MAIN);
					intent.addCategory(Intent.CATEGORY_HOME);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);*/
				}
				return;
			}

			// other 'case' lines to check for other
			// permissions this app might request
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.navigation, menu);
//		MenuItem drawingComplete = menu.add(0, MENU_REFRESH_LIST, Menu.NONE, R.string.refresh).setIcon(R.drawable.ic_refresh);
//		drawingComplete.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Temporary demo functionality to access all views with the settings menu
		Intent intent = IntentSwitcher.onOptionsNavigationSelected(item.getItemId(), this);

		if(intent != null) {
			//views that should load "on top" of view
			if(item.getItemId() != R.id.action_goto_create_post) {
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
			}

			startActivity(intent);
		}

		return super.onOptionsItemSelected(item);
	}

	private void loadImageAtListPosition(final String drawingKey, ParseFile bitmap) {
		bitmap.getDataInBackground(new GetDataCallback() {
			@Override
			public void done(byte[] data, ParseException e) {
				if(e == null) {
					//Resize drawing to thumbnail size
					// First decode with inJustDecodeBounds=true to check dimensions
					BitmapFactory.Options options=new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					BitmapFactory.decodeByteArray(data, 0, data.length, options);

					// Calculate inSampleSize
					options.inSampleSize = DrawingManager.calculateInSampleSize(options, 200, 200);

					// Decode bitmap with inSampleSize set
					options.inJustDecodeBounds = false;
					Bitmap thumbBitmap=BitmapFactory.decodeByteArray(data, 0, data.length, options);

					//Set this as the thumbnail for the appropriate drawing
					DrawingManager.getInstance().getCurrentNearbyDrawings().get(drawingKey).setThumbnail(thumbBitmap);
					drawingAdapter.notifyDataSetChanged(); // refresh list view
				} else {
					e.printStackTrace();
				}
			}
		});
	}

	private void loadAndUpdateDrawings() {
		if(gps.getLastLocation() == null) {
			Toast.makeText(this, "Can't get last location.", Toast.LENGTH_SHORT).show();
		}

		/*Toast.makeText(this, "Checking for new drawings...", Toast.LENGTH_SHORT).show();*/
		MyTask getDrawingsThread = new MyTask();
		getDrawingsThread.execute();
	}

	@Override
	public void onRefresh() {
		gps.stopGPS();
		loadAndUpdateDrawings();
		Toast.makeText(this, "", Toast.LENGTH_SHORT).show();
		swipeRefreshLayout.setRefreshing(false);
	}

	@Override
	protected void onRestart() {
		loadAndUpdateDrawings();
		super.onRestart();
	}

	@Override
	protected void onResume() {
		if(!gps.isRunning()) {
			gps.resumeGPS();
		}
		super.onResume();
	}

	@Override
	protected void onStop() {
		gps.stopGPS();
		super.onStop();
	}

	@Override
	public void locationChanged(double lat, double lng, float distanceFromLastLocation, boolean updatedLastLocation) {
		/*Toast.makeText(this, "Lat: " + lat + " Long: " + lng + "_" + distanceFromLastLocation, Toast.LENGTH_SHORT).show();*/

		if(updatedLastLocation) {
			loadAndUpdateDrawings();
			/*Toast.makeText(this, "Updating drawings.", Toast.LENGTH_SHORT).show();*/
		} else {
			/*Toast.makeText(this, "Not far enough from last location to update drawings.", Toast.LENGTH_SHORT).show();*/
		}
	}

	// this method is called when the location client has connected
	@Override
	public void locationUpdatesAvailable() {
		/*Toast.makeText(this, "Connected to location services", Toast.LENGTH_LONG).show();*/
		loadAndUpdateDrawings();
	}

	private class MyTask extends AsyncTask<String, String, String> {
		@Override
		protected void onPreExecute() {
			// has access to main thread
			Log.i("MAIN", "Starting task");

			if(drawingAdapter == null) {
				pb.setVisibility(View.VISIBLE);
			}
		}

		@Override
		protected String doInBackground(String... params) {
			ParseGeoPoint last = gps.getLastLocation();

			DrawingManager.getInstance().getNearbyDrawings(last, 100);
			// Delete drawings that aren't nearby anymore
			DrawingManager.getInstance().removeDrawingsNoLongerNearby(last);

			return "Success";
		}

		@Override
		protected void onPostExecute(String result) {
			// has access to main thread
			// update display here

			if(result == null) {
				Toast.makeText(NearbyActivity.this, "Could not connect to receive drawings", Toast.LENGTH_SHORT).show();
			}

			Log.i("MAIN", "New nearby drawings size is: " + DrawingManager.getInstance().getCurrentNearbyDrawings().size());

			drawingAdapter.notifyDataSetChanged(); // refresh list view

			// load in each picture one by one (if they have not previously been loaded)
			for(DrawingItem d : DrawingManager.getInstance().getCurrentNearbyDrawings().values()) {
				if(d.getBitmapFile() != null && d.getThumbnail() == null) {
					loadImageAtListPosition(d.getId(), d.getBitmapFile());
				}
			}

			pb.setVisibility(View.INVISIBLE);
			// IF there is still no content to display after a request, show
			// message "No content to display"
			if(DrawingManager.getInstance().getCurrentNearbyDrawings().size() == 0) {
				noContent.setVisibility(View.VISIBLE);
			} else{
				noContent.setVisibility(View.INVISIBLE);
			}
		}
	}
}
