package net.xiii.izra.gsphere;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.sphere.dji.WGS84;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;


import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.common.error.DJIError;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends FragmentActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    protected static final String TAG = "GSphereActivity";

    private GoogleMap gMap;

    private Button config, start, stop, upload;


    private double droneLocationLat = 52.247788, droneLocationLng = 104.266734;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private final double grad = Math.PI / 180.0;
    private final double minute = Math.PI / 180.0 / 60.0;
    private final double second = Math.PI / 180.0 / 60.0 / 60.0;

    private double longitude = 0.0f;
    private double latitude = 0.0f;
    private double radius = 1.0f * second;
    private int cirlcePoints = 4;
    private float altitude  = 1;
    private int stay = 20000;
    private float mSpeed = 10.0f;

    private final double FE = 1.0/298.257223563;
    private final double EE = 2.0*FE-FE*FE;
    private final double RE = 6378137.0;
    private double H0=450.0;

    private List<Waypoint> waypointList = new ArrayList<>();
    public File logfile;

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
//        File file = new File(this.getFilesDir(), "test.log");
//        Log.d("my", file.getAbsolutePath());
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        Log.e(TAG, "Toast: " + string);
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {
        config = (Button) findViewById(R.id.config);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        upload = (Button) findViewById(R.id.upload);

        config.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        upload.setOnClickListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logfile  = new File (getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "testlog.txt");


        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        // test log

//        String string = "Hello world!";




        IntentFilter filter = new IntentFilter();
        filter.addAction(DJApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        initUI();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        addListener();

    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange()
    {
        initFlightController();
        loginAccount();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        setResultToToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        BaseProduct product = DJApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation();
                }
            });
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null){
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }
        return instance;
    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for amap object
    }

    @Override
    public void onMapClick(LatLng point) {
        /*
        if (isAdd == true){
            markWaypoint(point);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }
        }else{
            setResultToToast("Cannot Add Waypoint");
        }
        */
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    public void sendGPSLocation(final double lat, final double lng)   {

        class MyThread extends Thread {
            String output = "";
            // creating connection
            public void run() {
                try {
                    String set_server_url = "http://194.176.114.21:8020/";
                    Date date = new Date();
                    long timestamp = date.getTime() / 1000;
                    String data = String.format(Locale.US, "{\"timestamp\" : %d, \"lat\" : %f, \"lng\": %f }", timestamp, lat, lng);
                    Log.d("my", data);

                    URL url = null;
                    try {
                        url = new URL(set_server_url);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setDoOutput(true); // setting POST method

                    // creating stream for writing request
                    OutputStream out = urlConnection.getOutputStream();
                    out.write(data.getBytes());

                    // reading response
                    Scanner in = new Scanner(urlConnection.getInputStream());
                    while (in.hasNext()) {
                        output += in.nextLine();
                    }

                    urlConnection.disconnect();
                }
                catch(IOException e)
                {
                    Log.d("my", e.getMessage());
                    output = e.getMessage();
                }
            }
        }
        MyThread mt = new MyThread();
        mt.start();
        while (mt.isAlive()); // do nothing
        if (mt.output.equals("")) {
            mt.output = "No output received";
        }
        Toast.makeText(this, mt.output, Toast.LENGTH_SHORT).show();


    }
    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        String info = String.format("%f, %f, %f\n", droneLocationLat, droneLocationLng, altitude+1.0);
        sendGPSLocation(droneLocationLat, droneLocationLng);
        Log.d("my", logfile.getAbsolutePath());
        try {
//            FileOutputStream outputStream = new FileOutputStream(logfile);
            FileOutputStream outputStream = new FileOutputStream(logfile,true);
            outputStream.write(info.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.upload:{
                uploadWayPointMission();
                break;
            }
            case R.id.start:{
                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            default:
                break;
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);
    }

    private void cameraUpdateToWaypoint() {
        if (waypointMissionBuilder == null) {
            return;
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++){
            builder.include(
                    new LatLng(waypointMissionBuilder.getWaypointList().get(i).coordinate.getLatitude(),
                               waypointMissionBuilder.getWaypointList().get(i).coordinate.getLongitude()));
        }
        LatLngBounds bounds = builder.build();

        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 10);
        gMap.moveCamera(cu);
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpLatitude_TV = (TextView) wayPointSettings.findViewById(R.id.latitude);
        final TextView wpLongitude_TV = (TextView) wayPointSettings.findViewById(R.id.longitude);
        final TextView wpRadius_TV = (TextView) wayPointSettings.findViewById(R.id.radius);

        final TextView wpCirlcePoints_TV = (TextView) wayPointSettings.findViewById(R.id.circlepoints);
        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        final TextView wpStay_TV = (TextView) wayPointSettings.findViewById(R.id.stay);

        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }
        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });


        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                        String latitudeStr = wpLatitude_TV.getText().toString();
                        String longitudeStr = wpLongitude_TV.getText().toString();
                        String radiusStr = wpRadius_TV.getText().toString();
                        String cirlcePointsStr = wpCirlcePoints_TV.getText().toString();
                        String altitudeStr  = wpAltitude_TV.getText().toString();
                        String stayStr = wpStay_TV.getText().toString();

                        latitude = Float.parseFloat(nulltoFloatDefalt(latitudeStr, "" + droneLocationLat));
                        longitude = Float.parseFloat(nulltoFloatDefalt(longitudeStr, "" + droneLocationLng));

//                        radius = Float.parseFloat(nulltoFloatDefalt(radiusStr, "3.0")) * second;
                        radius = Float.parseFloat(nulltoFloatDefalt(radiusStr, "3.0"));

                        cirlcePoints = Integer.parseInt(nulltoIntegerDefalt(cirlcePointsStr, "8"));
                        altitude = Float.parseFloat(nulltoFloatDefalt(altitudeStr, "10.0"));
                        stay = Integer.parseInt(nulltoFloatDefalt(stayStr, "20")) * 1000;

                        LatLng point = new LatLng(latitude, longitude);

                        StringBuilder builder = new StringBuilder();
                        builder.append("Coord: ");
                        builder.append(point.toString());
                        builder.append(System.getProperty("line.separator"));
                        builder.append("radius: ");
                        builder.append(radius/second);
                        builder.append(System.getProperty("line.separator"));
                        builder.append("cirlcePoints: ");
                        builder.append(cirlcePoints);
                        builder.append(System.getProperty("line.separator"));
                        builder.append("altitude: ");
                        builder.append(altitude);
                        builder.append(System.getProperty("line.separator"));
                        builder.append("speed: ");
                        builder.append(mSpeed);
                        builder.append(System.getProperty("line.separator"));
                        builder.append("mFinishedAction: ");
                        builder.append(mFinishedAction);
                        builder.append(System.getProperty("line.separator"));

                        setResultToToast(builder.toString());



                        Log.e(TAG,"latitude "+latitude);
                        Log.e(TAG,"longitude "+longitude);
                        Log.e(TAG,"radius "+(radius/second));
                        Log.e(TAG,"cirlcePoints "+cirlcePoints);
                        Log.e(TAG,"altitude "+altitude);

                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);



                        configWayPointMission();
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    String nulltoIntegerDefalt(String value, String defaultValue){
        if(!isFloatValue(value)) value=defaultValue;
        return value;
    }

    String nulltoFloatDefalt(String value){
        if(!isFloatValue(value)) value="0";
        return value;
    }

    String nulltoFloatDefalt(String value, String defaultValue){
        if(!isFloatValue(value)) value=defaultValue;
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    boolean isFloatValue(String val)
    {
        try {
            val=val.replace(" ","");
            Float.parseFloat(val);
        } catch (Exception e) {return false;}
        return true;
    }


    private void configWayPointMission(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gMap.clear();
            }

        });
        if(waypointMissionBuilder != null) {
            waypointList.clear();
            waypointMissionBuilder.waypointList(waypointList);
            updateDroneLocation();
        }


        waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                .headingMode(WaypointMissionHeadingMode.AUTO)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(mSpeed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        if(waypointMissionBuilder == null) {
            setResultToToast("Cannot create WaypointBuilder");
            return;
        }

        Log.e(TAG, "Test function: " + WGS84.someFunction(1.0F));

        double x,y,z,f,R_fi0;
        double fi0,lg0,dfi;
        fi0=latitude*grad;
        lg0=longitude*grad;
        f=RE/Math.sqrt(1.0-EE*Math.sin(fi0)*Math.sin(fi0));
        x=(f+H0)*Math.cos(fi0)*Math.cos(lg0);
        y=(f+H0)*Math.cos(fi0)*Math.sin(lg0);
        z=(f*(1-EE)+H0)*Math.sin(fi0);
        R_fi0=Math.sqrt(x*x+y*y+z*z);
        dfi=Math.atan(radius/(R_fi0+altitude));

        double daz= 2.0D * Math.PI/(double)cirlcePoints;
        int i=0;
        for(double az = 0.0D; az < 2.0D * Math.PI; az+=daz) {
            double lat_shift = Math.asin(Math.sin(fi0)*Math.cos(dfi)+Math.cos(fi0)*Math.sin(dfi)*Math.cos(az));
            double dlg=Math.atan2(Math.sin(az)*Math.sin(dfi),Math.cos(dfi)*Math.cos(fi0)-Math.sin(fi0)*Math.sin(dfi)*Math.cos(az));
            double lon_shift=longitude + dlg/grad;
            lat_shift/=grad;
            LatLng point = new LatLng(lat_shift, lon_shift);
            markWaypoint(point);

            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            mWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, stay));

            Log.e(TAG, "Way #" + i + ": " + point.toString());
            i++;
            waypointList.add(mWaypoint);
        }
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());

        /*
        for(int i = 0; i < cirlcePoints; ++i) {
            double lat_shift = latitude + radius * Math.cos((2.0D * Math.PI * (double) i)/((double)cirlcePoints));
            double lon_shift = longitude + radius * Math.sin((2.0D * Math.PI * (double) i)/((double)cirlcePoints));

            LatLng point = new LatLng(lat_shift, lon_shift);
            markWaypoint(point);

            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            mWaypoint.addAction(new WaypointAction(WaypointActionType.STAY, stay));

            Log.e(TAG, "Way #" + i + ": " + point.toString());

            waypointList.add(mWaypoint);

        }
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
        */

        cameraUpdateToWaypoint();

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }
    }

    private void uploadWayPointMission(){
        Log.e(TAG, "uploadWayPointMission");
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });
    }

    private void startWaypointMission(){
        String info = String.format("%f, %f, %f", latitude, longitude, altitude);

        Log.d("my", logfile.getAbsolutePath());
        try {
            FileOutputStream outputStream = new FileOutputStream(logfile);
            outputStream.write(info.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });
    }

    private void stopWaypointMission(){

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }

        LatLng irkutsk = new LatLng(52.00, 104.00);
        gMap.addMarker(new MarkerOptions().position(irkutsk).title("Marker in Irkutsk"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(irkutsk));
    }

}