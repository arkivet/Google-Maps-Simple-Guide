package srdjancosic.studentprojekt;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Stack;


/**
 * Created by Srdjan on 2015-09-4.
 */

public class Guide extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        SensorEventListener
{

    private GoogleMap           mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient     mGoogleApiClient;
    private LocationRequest     mLocationRequest;
    private LatLng              currentPos = null;
    private Marker              selectedMarker = null;
    private Polyline            navLine;                //Navigation Line
    private TextView            distance;

    private SensorManager       mSensorManager;
    private Sensor              mSensor;

    private boolean             isSatelliteChecked = false; //For the Map type Button
    private boolean             isFirstTimeRunning = true;
    private boolean             internetConnection = true;

    private float[]             mRotationMatrix;
    private float               mDeclination;

    public static final String TAG = Guide.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    

    class DownloadMarkers extends AsyncTask<String, Void, Stack<MarkerOptions>>{

        @Override
        protected Stack<MarkerOptions> doInBackground(String... strings) {
            Stack<MarkerOptions> markers = new Stack<>();
            try {
            URL url = new URL(strings[0]);
            Scanner in = new Scanner(url.openStream());
            HashSet<LatLng> locations = new HashSet<>();
            while (in.hasNextLine()) {
                float latitude;
                float longitude;
                try {
                    latitude = Float.parseFloat(in.next());
                    longitude = Float.parseFloat(in.next());
                    LatLng pos = new LatLng(latitude,longitude);
                    String name = in.nextLine();

                    if(!locations.contains(pos) && name.length() < 30){
                        locations.add(pos);
                        markers.push(new MarkerOptions().position(pos)
                                .title(name)
                                .snippet("Click to start navigation"));
                    }else{
                        in.nextLine();
                    }
                }
                catch (NumberFormatException n){
                    in.nextLine();
                }
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
                internetConnection = false;
        }
            return markers;
        }

        @Override
        protected void onPostExecute(Stack<MarkerOptions> markers){
            if(internetConnection){
                while (!markers.empty()){
                    mMap.addMarker(markers.pop());
                }
            }else {
                AlertDialog alertDialog = new AlertDialog.Builder(Guide.this).create();
                alertDialog.setTitle("No Internet");
                alertDialog.setMessage("This app connects to a server to download markers. Please" +
                        " restart the app with an internet connection.");
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                alertDialog.setIcon(R.drawable.no_connection);
                alertDialog.show();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //So screen doesn't go to sleep mode

        setupGoogleServices();
        setUpMapIfNeeded();
        initiateVariables();


        new DownloadMarkers().execute("http://punkter.blekinge.it/1.txt");
        checkGPS();

    }

    private void initiateVariables() {
        this.mMap.getUiSettings().setMapToolbarEnabled(false);
        this.mRotationMatrix = new float[16];
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        distance = (TextView) findViewById(R.id.distance);
    }

    private void setupGoogleServices() {
        //Create the Google API Client
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1000); // 1 second, in milliseconds
        mGoogleApiClient.connect();

    }

    private void checkGPS() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ) {
            // Build the alert dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("GPS Not Enabled");
            builder.setMessage("It is recommended that GPS is enabled for all of the application's" +
                    " functions. Do you wish to turn it on now?");
            builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Show location settings when the user acknowledges the alert dialog
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //Return to the map
                }
            });
            builder.setIcon(R.drawable.no_gps);
            Dialog alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        MenuItem checkable = menu.findItem(R.id.map_type);
        checkable.setChecked(isSatelliteChecked);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {// Inflate the menu; this adds items to the action bar if it is
        // present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_settings:

                return true;
            case R.id.map_type:
                isSatelliteChecked = !item.isChecked();
                item.setChecked(isSatelliteChecked);
                if(isSatelliteChecked)
                {
                    mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    distance.setTextColor(Color.WHITE);
                }
                else
                {
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    distance.setTextColor(Color.BLACK);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        setUpMapIfNeeded();
        mGoogleApiClient.connect();
        this.mMap.setMyLocationEnabled(true);

        if (this.currentPos != null) {
            this.mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 16));
        }
        if(!this.isFirstTimeRunning)
        {
            registerSensorListener();
        }

    }

    @Override
    protected void onPause()
    {
        unRegisterSensorListener();
        if (this.mGoogleApiClient.isConnected())
        {
            LocationServices.FusedLocationApi.removeLocationUpdates(this.mGoogleApiClient, this);
            this.mGoogleApiClient.disconnect();
        }
        if (this.mMap!=null)
        {
            mMap.setMyLocationEnabled(false);
        }
        super.onPause();

    }
    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded()
    {
        // Do a null check to confirm that we have not already instantiated the map.
        if (this.mMap == null)
        {
            // Try to obtain the map from the CustomMapFragmet.
            CustomMapFragment customMapFragment = ((CustomMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
            customMapFragment.setOnDragListener(new MapWrapperLayout.OnDragListener() {
                @Override
                public void onDrag(MotionEvent motionEvent) {
                    Log.d("ON_DRAG", String.format("ME: %s", motionEvent));
                    unRegisterSensorListener();
                    // Handle motion event:
                }
            });
            this.mMap = customMapFragment.getMap();
            // Check if we were successful in obtaining the map.
            if (this.mMap != null)
            {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
         addMarkerFunctions();
        /*OnMapLoaded to make sure the map loads before the sensor starts
        reacting and updating the camera because then it will result in a crash
         */
        this.mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                registerSensorListener();
                isFirstTimeRunning = false; //now onResume will be able to register the Sensor
            }
        });
        this.mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                if (mMap.getMyLocation() != null) {
                    Location location = mMap.getMyLocation();
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(latLng)
                            .zoom(16)
                            .build();
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            registerSensorListener();
                        }
                    }, 2500);
                } else {
                    checkGPS();
                }
                return false;
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(this.TAG, "Location services connected.");
        Location location = LocationServices.FusedLocationApi.getLastLocation(this.mGoogleApiClient);
        //Always call no matter what requestLocationUpdates
        LocationServices.FusedLocationApi.requestLocationUpdates(this.mGoogleApiClient, this.mLocationRequest, this);
        if (location != null)
        {
            handleNewLocation(location);
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.i(this.TAG, "Location services suspended. Please reconnect.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult)
    {
        if (connectionResult.hasResolution())
        {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            Log.i(this.TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location)
    {
        GeomagneticField field = new GeomagneticField(
                (float)location.getLatitude(),
                (float)location.getLongitude(),
                (float)location.getAltitude(),
                System.currentTimeMillis()
        );

        // getDeclination returns degrees
        mDeclination = field.getDeclination();
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location)
    {
        Log.d(this.TAG, location.toString());
        if(selectedMarker != null){
            LatLng pUser = new LatLng(mMap.getMyLocation().getLatitude(),mMap.getMyLocation().getLongitude());
            navLine.remove();
            navLine = mMap.addPolyline(new PolylineOptions()
                    .add(pUser,selectedMarker.getPosition())
                    .color(Color.GREEN)
                    .width(10));
            setDistance();
        }
    }

    public void addMarkerFunctions()
    {
        this.mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                return false;
            }
        });
        this.mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {

                if (marker.equals(selectedMarker)) { //If same marker is selected again
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                    marker.setSnippet("Click the window to start navigation");
                    selectedMarker = null;
                    navLine.remove();
                    distance.setText("");

                } else {
                    if (mMap.getMyLocation() != null) {
                        //If a marker was selected before
                        if (selectedMarker != null) {
                            selectedMarker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
                            selectedMarker.setSnippet("Click the window to start navigation");
                            navLine.remove();
                        }
                        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        marker.setSnippet("Click the window to stop navigation");
                        selectedMarker = marker;
                        LatLng pUser = new LatLng(mMap.getMyLocation().getLatitude(), mMap.getMyLocation().getLongitude());
                        navLine = mMap.addPolyline(new PolylineOptions()
                                        .add(pUser, selectedMarker.getPosition())
                                        .width(10)
                                        .color(Color.GREEN)
                        );
                        setDistance();
                    }
                }
            }
        });
    }

    private void setDistance() {

        Location a = new Location("User");
        a.setLatitude(this.mMap.getMyLocation().getLatitude());
        a.setLongitude(this.mMap.getMyLocation().getLongitude());
        Location b = new Location("Marker");
        b.setLatitude(this.selectedMarker.getPosition().latitude);
        b.setLongitude(this.selectedMarker.getPosition().longitude);
        String meters;

        if(a.distanceTo(b) > 1000){
            meters = String.format("%.1f",a.distanceTo(b)/1000) + " km";
        }
        else {
            meters =  String.format("%.2f",a.distanceTo(b)) + " m";
        }
        this.distance.setText(meters);
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        if(this.mMap.getMyLocation()!=null)
        {
            if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                float[] orientation = new float[3];
                SensorManager.getOrientation(mRotationMatrix, orientation);
                SensorManager.getOrientation(mRotationMatrix, orientation);
                float bearing = (float)Math.toDegrees(orientation[0]) + mDeclination;
                updateCamera(bearing);
            }
        }

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void updateCamera(float bearing)
    {
        if(this.mMap.getMyLocation()!=null) {

            Location location = this.mMap.getMyLocation();
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(latLng)
                    .bearing(bearing)
                    .zoom(16)
                    .tilt(this.mMap.getCameraPosition().tilt)
                    .build();

            this.mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
        if(selectedMarker != null){
            ArrayList<LatLng> list = new ArrayList<>();
            list.add(selectedMarker.getPosition());
            LatLng pUser = new LatLng(this.mMap.getMyLocation().getLatitude(),this.mMap.getMyLocation().getLongitude());
            list.add(pUser);
            navLine.setPoints(list);

        }
    }

    public void registerSensorListener()
    {
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI * 5);
    }

    public void unRegisterSensorListener()
    {
        mSensorManager.unregisterListener(this);
    }


}
