package com.internalpositioning.find3.find3app;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

//************************************************
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAccSensorValue;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketBase;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketEddyTLM;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketSensor;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketSystem;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvType;
import com.kkmcn.kbeaconlib2.KBeacon;
import com.kkmcn.kbeaconlib2.KBeaconsMgr;
//************************************************



/**
 * Created by zacks on 3/2/2018.
 */

public class ScanService extends Service {
    // logging
    private final String TAG = "ScanService";

    int mStartMode;       // indicates how to behave if the service is killed
    IBinder mBinder;      // interface for clients that bind
    boolean mAllowRebind; // indicates whether onRebind should be used

    boolean isScanning = false;
    private final Object lock = new Object();

    // wifi scanning
    private WifiManager wifi;

    // bluetooth scanning
    private BluetoothAdapter BTAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothBroadcastReceiver receiver = null;

    // post data request queue
    RequestQueue queue;
    private JSONObject jsonBody = new JSONObject();
    private LinkedHashMap<String, Integer> bluetoothResults = new LinkedHashMap<>();
    private JSONObject wifiResults = new JSONObject();

    private String familyName = "";
    private String locationName = "";
    private String deviceName = "";
    private String serverAddress = "";
    private boolean allowGPS = false;
    private boolean isToggleScanTypeChecked=false;

//    *****************************************************************
//    private final static String TAG = "beacon.KBeaconsMgr";
    private final static String LOG_TAG = "beacon.KBeaconsMgr";

    private static final int PERMISSION_COARSE_LOCATION = 1;
    private static final int PERMISSION_FINE_LOCATION = 1;


    private KBeaconsMgr mBeaconsMgr;
    private KBeaconsMgr.KBeaconMgrDelegate beaconMgrDeletate;
    private KBeaconsMgr.KBeaconMgrDelegate beaconMgrExample;
    int counter_n;
//    *****************************************************************

    @Override
    public void onCreate() {
        // The service is being created
        Log.d(TAG, "creating new scan service");
        queue = Volley.newRequestQueue(this);
        // setup wifi
        wifi = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled() == false) {
            wifi.setWifiEnabled(true);
        }
        // register wifi intent filter
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mWifiScanReceiver, intentFilter);

//        ******************************************************************************
//        KBeaconsMgr.KBeaconMgrDelegate beaconMgrExample = new KBeaconsMgr.KBeaconMgrDelegate()
//        Next two lines should change. They are only for sending every 6 values. Is next line really necessary?
        counter_n=0;

        beaconMgrExample = new KBeaconsMgr.KBeaconMgrDelegate()
        {
            //get advertisement packet during scanning callback
            public void onBeaconDiscovered(KBeacon[] beacons)
            {
                for (KBeacon beacon: beacons)
                {
                    //get beacon adv common info
                    Log.v(LOG_TAG, "beacon mac:" + beacon.getMac());
                    Log.v(LOG_TAG, "beacon name:" + beacon.getName());
                    Log.v(LOG_TAG,"beacon rssi:" + beacon.getRssi());
                    Log.v(LOG_TAG,"beacon Battery Percentage:" + beacon.getBatteryPercent());
                    if (beacon.getName()==null) continue;
                    String firstThree = beacon.getName().substring(0, 3);
                    if (beacon.getRssi()<-75 && !firstThree.equals("St_")){
                        continue;
                    }
//                    ********************************************************************************************* send data// new code
                    if (bluetoothResults.containsKey(beacon.getName())) {
                        // If the beacon is already in the LinkedHashMap, update the RSSI value with the average
                        int oldRssi = bluetoothResults.get(beacon.getName());
                        int newRssi = (oldRssi + beacon.getRssi()) / 2;
                        bluetoothResults.put(beacon.getName(), newRssi);
                        Log.v(LOG_TAG, "found an existing one:" + oldRssi);
                    } else {
                        // If the LinkedHashMap size is at the maximum capacity, remove the first entry
                        if (bluetoothResults.size() >= 6) {
                            Iterator<String> iterator = bluetoothResults.keySet().iterator();
                            if (iterator.hasNext()) {
                                String firstKey = iterator.next();
                                bluetoothResults.remove(firstKey);
                            }
                        }
                        // Add the new beacon
                        bluetoothResults.put(beacon.getName(), beacon.getRssi());
                        Log.v(LOG_TAG, "found new one"+ beacon.getRssi());
                    }
                    beacon.removeAdvPacket();
                }
//                ***************************************************************************
                counter_n=counter_n+1;
                Log.d(TAG, "counter n value："+ counter_n);
                Log.d(TAG, "bluetooth results:"+ bluetoothResults);
                if (isToggleScanTypeChecked==true && counter_n>6 && bluetoothResults.size()>5){
                    Log.e(TAG, "send data objects："+ bluetoothResults);
                    Log.d(TAG,"isToggleScanTypeChecked inside scanservice: "+ isToggleScanTypeChecked);
                    counter_n=0;
                    sendData();
//                    bluetoothResults.clear();
                }
                else if (isToggleScanTypeChecked==false && bluetoothResults.size()>5 && counter_n>3){
                    Log.e(TAG, "send data objects："+ bluetoothResults);
                    Log.d(TAG,"isToggleScanTypeChecked inside scanservice: "+ isToggleScanTypeChecked);
                    counter_n=0;
                    sendData();
//                    bluetoothResults.clear();
                }
//                ***************************************************************************
            }
            public void onCentralBleStateChang(int nNewState)
            {
                Log.e(TAG, "centralBleStateChang：" + nNewState);
            }

            public void onScanFailed(int errorCode)
            {
                // empty
            }
        };
        mBeaconsMgr = KBeaconsMgr.sharedBeaconManager(this);


//        ******************************************************************************
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        deviceName = intent.getStringExtra("deviceName");
        familyName = intent.getStringExtra("familyName");
        locationName = intent.getStringExtra("locationName");
        serverAddress = intent.getStringExtra("serverAddress");
        allowGPS = intent.getBooleanExtra("allowGPS", false);
        isToggleScanTypeChecked = intent.getBooleanExtra("isToggleScanTypeChecked", false);

        Log.d(TAG, "familyName: " + familyName);



//        *********************************************************************
        mBeaconsMgr.delegate = beaconMgrExample;
        int nStartScan = mBeaconsMgr.startScanning();
        if (nStartScan == 0) {
            Log.v(TAG, "start scan success");
            Toast.makeText(this, "Jahan jigare mani",
                    Toast.LENGTH_LONG).show();
        } else if (nStartScan == KBeaconsMgr.SCAN_ERROR_BLE_NOT_ENABLE) {
            Toast.makeText(this, "BLE function is not enable",
                    Toast.LENGTH_LONG).show();
//            toastShow("BLE function is not enable");
        } else if (nStartScan == KBeaconsMgr.SCAN_ERROR_NO_PERMISSION) {
            Toast.makeText(this, "BLE scanning has no location permission",
                    Toast.LENGTH_LONG).show();
//            toastShow("BLE scanning has no location permission");
        } else {
            Toast.makeText(this, "BLE scanning unknown error",
                    Toast.LENGTH_LONG).show();
//            toastShow("BLE scanning unknown error");
            System.out.println("nStartScan:");
            System.out.println(nStartScan);
        }
//        *********************************************************************
            return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // A client is binding to the service with bindService()
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        return mAllowRebind;
    }

    @Override
    public void onRebind(Intent intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        Log.v(TAG, "onDestroy");
        try {
            if (receiver != null)
                unregisterReceiver(receiver);
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
        try {
            if (mWifiScanReceiver != null)
                unregisterReceiver(mWifiScanReceiver);
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
        stopSelf();
        super.onDestroy();

    }

    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            // This condition is not necessary if you listen to only one action
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                Log.d(TAG, "timer off, trying to send data");
                List<ScanResult> wifiScanList = wifi.getScanResults();
                for (int i = 0; i < wifiScanList.size(); i++) {
                    String name = wifiScanList.get(i).BSSID.toLowerCase();
                    int rssi = wifiScanList.get(i).level;
                    Log.v(TAG, "wifi: " + name + " => " + rssi + "dBm");
                    try {
                        wifiResults.put(name, rssi);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    }
                }
                sendData();
                BTAdapter.cancelDiscovery();
                BTAdapter = BluetoothAdapter.getDefaultAdapter();
                synchronized (lock) {
                    isScanning = false;
                }
            }
        }
    };



    // bluetooth reciever
    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String name = device.getAddress().toLowerCase();
                Log.v(TAG, "bluetooth: " + name + " => " + rssi + "dBm");
                try {
                    bluetoothResults.put(name, rssi);
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
    }

    ;


    public void sendData() {
        try {
            String URL = serverAddress + "/data";
            jsonBody.put("f", familyName);
            jsonBody.put("d", deviceName);
            jsonBody.put("l", locationName);
            jsonBody.put("t", System.currentTimeMillis());
            JSONObject sensors = new JSONObject();
            sensors.put("bluetooth", new JSONObject(bluetoothResults));
//            sensors.put("bluetooth", bluetoothResults);
            sensors.put("wifi", wifiResults);
            jsonBody.put("s", sensors);
            if (allowGPS) {
                JSONObject gps = new JSONObject();
                Location loc = getLastBestLocation();
                if (loc != null) {
                    gps.put("lat",loc.getLatitude());
                    gps.put("lon",loc.getLongitude());
                    gps.put("alt",loc.getAltitude());
                    jsonBody.put("gps",gps);
                }
            }

            final String mRequestBody = jsonBody.toString();
            Log.d("tag send data: ", mRequestBody);
            Log.d(TAG, mRequestBody);

            StringRequest stringRequest = new StringRequest(Request.Method.POST, URL, new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, error.toString());
                }
            }) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                    } catch (UnsupportedEncodingException uee) {
                        VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                        return null;
                    }
                }

                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = new String(response.data);
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };

            queue.add(stringRequest);
        } catch (JSONException e) {
            System.out.println("no data sent!");
            e.printStackTrace();
        }
    }

    /**
     * @return the last know best location
     */
    private Location getLastBestLocation() {
        LocationManager mLocationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

        Location locationGPS = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location locationNet = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);


        long GPSLocationTime = 0;
        if (null != locationGPS) {
            GPSLocationTime = locationGPS.getTime();
        }

        long NetLocationTime = 0;

        if (null != locationNet) {
            NetLocationTime = locationNet.getTime();
        }

        if (0 < GPSLocationTime - NetLocationTime) {
            Log.d("GPS",locationGPS.toString());
            return locationGPS;
        } else {
            Log.d("GPS",locationNet.toString());
            return locationNet;
        }
    }
}