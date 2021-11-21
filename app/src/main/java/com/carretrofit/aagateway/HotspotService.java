package com.carretrofit.aagateway;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class HotspotService extends Service {
    private static final String TAG = "AAGateWayHotspotService";

    private boolean running = false;
    private String ssid = BluetoothAdapter.getDefaultAdapter().getName();

    private BroadcastReceiver receiver =
            new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
                        unregisterReceiver(receiver);
                        ssid = (String) intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
                        Log.d(TAG, "Bluetooth local name changed to " + ssid);
                        enableHotspot();
                    }
                }
            };

    @Override
    public void onCreate() {
        Notification notification =
                new Notification.Builder(this).setContentTitle("Hotspot Service").build();
        startForeground(3, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            return START_STICKY;
        }
        running = true;
        Log.d(TAG, "Service started");
        if (Pattern.matches("^KIA.*$", ssid)) {
            enableHotspot();
        } else {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
            registerReceiver(receiver, filter);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            unregisterReceiver(receiver);
        } catch (Throwable ignored) {
        }
        if (isHotspotEnabled()) {
            disableHotspot();
        }
        stopForeground(true);
        Log.d(TAG, "Service destroyed");
    }

    private void enableHotspot() {
        Log.d(TAG, "Enabling hotspot");
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        try {
            wifiManager.setWifiEnabled(false);
            Method method =
                    wifiManager
                            .getClass()
                            .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifiManager, getWifiConfig(), true);
        } catch (Throwable ignored) {
        }
    }

    private void disableHotspot() {
        Log.d(TAG, "Disabling hotspot");
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        try {
            Method method =
                    wifiManager
                            .getClass()
                            .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifiManager, getWifiConfig(), false);
            wifiManager.setWifiEnabled(true);
        } catch (Throwable ignored) {
        }
    }

    private boolean isHotspotEnabled() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int actualState = 0;
        try {
            Method method = wifiManager.getClass().getDeclaredMethod("getWifiApState");
            method.setAccessible(true);
            actualState = (Integer) method.invoke(wifiManager, (Object[]) null);
        } catch (Throwable ignored) {
        }
        return actualState == 13;
    }

    private WifiConfiguration getWifiConfig() {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = ssid;
        wifiConfig.preSharedKey = Constants.WIFI_PASSWORD;
        wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        return wifiConfig;
    }
}
