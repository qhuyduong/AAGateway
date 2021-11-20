package com.carretrofit.aagateway;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import f1x.aasdk.proto.messages.WifiInfoRequestMessage.WifiInfoRequest;
import f1x.aasdk.proto.messages.WifiSecurityRequestMessage.WifiSecurityRequest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.UUID;

public class RfcommService extends Service {
    private static final String TAG = "AAGateWayRfcommService";
    private static final String WAA_NAME = "Wireless Android Auto";
    private static final UUID WAA_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");
    private static final UUID HSP_UUID = UUID.fromString("00001112-0000-1000-8000-00805f9b34fb");
    private static final short WIFI_INFO_REQUEST = 1;
    private static final short WIFI_INFO_RESPONSE = 2;
    private static final short WIFI_SECURITY_REQUEST = 3;
    private static final short WIFI_SECURITY_RESPONSE = 6;
    private String btName = BluetoothAdapter.getDefaultAdapter().getName();

    private boolean running = false;

    private BroadcastReceiver deviceReceiver =
            new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                        BluetoothDevice device =
                                (BluetoothDevice)
                                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Log.d(TAG, "Device connected " + device);

                        new Thread(new WAAListener(device)).start();
                    }
                }
            };

    private BroadcastReceiver adapterReceiver =
            new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
                        unregisterReceiver(adapterReceiver);
                        btName = (String) intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
                        Log.d(TAG, "Bluetooth local name changed to " + btName);
                        enableHotspot();
                    }
                }
            };

    @Override
    public void onCreate() {
        Notification notification =
                new Notification.Builder(this).setContentTitle("Rfcomm Service").build();
        startForeground(2, notification);
        IntentFilter deviceFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        registerReceiver(deviceReceiver, deviceFilter);
        IntentFilter adapterFilter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        registerReceiver(adapterReceiver, adapterFilter);
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

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        unregisterReceiver(deviceReceiver);
        try {
            unregisterReceiver(adapterReceiver);
        } catch (IllegalArgumentException e) {
        }
        stopForeground(true);
        Log.d(TAG, "Service destroyed");
    }

    private class WAAListener implements Runnable {
        private BluetoothDevice device;
        private DataInputStream inputStream;
        private OutputStream outputStream;

        public WAAListener(BluetoothDevice dev) {
            device = dev;
        }

        public void run() {
            BluetoothServerSocket serverSocket = null;
            BluetoothSocket socket = null;
            try {
                Log.d(TAG, "Listening to device " + device);
                serverSocket =
                        BluetoothAdapter.getDefaultAdapter()
                                .listenUsingRfcommWithServiceRecord(WAA_NAME, WAA_UUID);
                socket = serverSocket.accept();
                serverSocket.close();

                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = socket.getOutputStream();
                Log.d(TAG, "Connecting to WAA on device " + device);
                connectToPhone(device);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (socket != null) {
                        socket.close();
                    }
                    if (serverSocket != null) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                }
            }
        }

        private void connectToPhone(BluetoothDevice device) {
            try {
                device.createRfcommSocketToServiceRecord(HSP_UUID).connect();
                sendWifiInfoRequest();
                if (!receiveWifiInfoResponse()) {
                    return;
                }
                sendWifiSecurityRequest();
                while (!receiveWifiSecurityResponse()) {}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendWifiInfoRequest() throws IOException {
            WifiInfoRequest request =
                    WifiInfoRequest.newBuilder()
                            .setIpAddress(Constants.IP_ADDRESS)
                            .setPort(Constants.TCP_PORT)
                            .build();
            byte[] bytes = request.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
            buffer.put((byte) ((bytes.length >> 8) & 255));
            buffer.put((byte) (bytes.length & 255));
            buffer.putShort(WIFI_INFO_REQUEST);
            buffer.put(bytes);
            outputStream.write(buffer.array());
        }

        private boolean receiveWifiInfoResponse() throws IOException {
            byte[] bytes = new byte[1024];
            inputStream.read(bytes);
            short command = (short) (((bytes[2] & 255) << 8) | (bytes[3] & 255));
            if (command != WIFI_INFO_RESPONSE) {
                return false;
            }
            return true;
        }

        private void sendWifiSecurityRequest() throws IOException {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            WifiSecurityRequest response =
                    WifiSecurityRequest.newBuilder()
                            .setSsid(btName)
                            .setBssid(wifiManager.getConnectionInfo().getMacAddress())
                            .setAccessPointType(WifiSecurityRequest.AccessPointType.STATIC)
                            .setKey(Constants.WIFI_PASSWORD)
                            .setSecurityMode(WifiSecurityRequest.SecurityMode.WPA2_PERSONAL)
                            .build();
            Log.d(TAG, "Sending wifi security request to phone " + response.toString());
            byte[] bytes = response.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
            buffer.put((byte) ((bytes.length >> 8) & 255));
            buffer.put((byte) (bytes.length & 255));
            buffer.putShort(WIFI_SECURITY_REQUEST);
            buffer.put(bytes);
            outputStream.write(buffer.array());
        }

        private boolean receiveWifiSecurityResponse() throws IOException {
            byte[] bytes = new byte[1024];
            inputStream.read(bytes);
            short command = (short) (((bytes[2] & 255) << 8) | (bytes[3] & 255));
            if (command != WIFI_SECURITY_RESPONSE) {
                return false;
            }
            Log.d(TAG, "WAA started");
            return true;
        }
    }

    private void enableHotspot() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = btName;
        wifiConfig.preSharedKey = Constants.WIFI_PASSWORD;
        wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        try {
            wifiManager.setWifiEnabled(false);
            Method method =
                    wifiManager
                            .getClass()
                            .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifiManager, wifiConfig, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
