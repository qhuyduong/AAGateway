package com.carretrofit.aagateway;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import com.carretrofit.aagateway.proto.WifiInfoRequestMessage;
import com.carretrofit.aagateway.proto.WifiSecurityResponseMessage;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "AAGateWayBluetoothService";
    private static final UUID A2DP_UUID = UUID.fromString("00001112-0000-1000-8000-00805F9B34FB");
    private static final UUID HFP_UUID = UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb");
    private static final UUID MY_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");

    private boolean running = false;

    private BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;
    private BluetoothDevice device;

    private DataInputStream inputDataStream;
    private OutputStream outputStream;

    private BroadcastReceiver bluetoothReceiver =
            new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    Log.d(TAG, "action: " + action);
                    if ("android.bluetooth.device.action.ACL_CONNECTED".equals(action)) {
                        device =
                                (BluetoothDevice)
                                        intent.getParcelableExtra(
                                                "android.bluetooth.device.extra.DEVICE");

                        aaListenerThread.start();
                    } else if ("android.bluetooth.device.action.UUID".equals(action)) {
                    } else if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                        int intExtra =
                                intent.getIntExtra(
                                        "android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                        Log.d(TAG, "intExtra = " + intExtra);
                    }
                }
            };

    private Thread aaListenerThread =
            new Thread() {
                public void run() {
                    try {
                        serverSocket = adapter.listenUsingRfcommWithServiceRecord(TAG, MY_UUID);
                        adapter.listenUsingRfcommWithServiceRecord("HFP", HFP_UUID);
                        Log.d(TAG, "Listenning for AA profile: " + serverSocket);
                        socket = serverSocket.accept();
                        Log.d(TAG, "Bluetooth device connected " + socket);
                        inputDataStream = new DataInputStream(socket.getInputStream());
                        outputStream = socket.getOutputStream();
                        connectToPhone(device);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        intentFilter.addAction("android.bluetooth.device.action.UUID");
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");

        registerReceiver(bluetoothReceiver, intentFilter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            Log.d(TAG, "Service already running");
            return START_STICKY;
        }

        Log.d(TAG, "Service started");
        running = true;

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        unregisterReceiver(bluetoothReceiver);
        Log.d(TAG, "Service destroyed");
    }

    private void connectToPhone(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice != null) {
            Log.d(TAG, "device = " + bluetoothDevice);

            try {
                bluetoothDevice.createRfcommSocketToServiceRecord(A2DP_UUID).connect();
                innerconnectPhone();
                Intent i = new Intent(this, MyService.class);
                startService(i);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void innerconnectPhone() {
        try {
            WifiInfoRequestMessage.WifiInfoRequest.Builder newBuilder =
                    WifiInfoRequestMessage.WifiInfoRequest.newBuilder();
            String ip = getLocalIpAddress();
            Log.d(TAG, "ip = " + ip);
            newBuilder.setIpAddress(ip);
            newBuilder.setPort(5288);
            Log.d(TAG, "sending initial wifi info: " + newBuilder);
            send(newBuilder.build().toByteArray(), (short) 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        return "192.168.43.1";
    }

    private void send(byte[] bArr, short s) throws IOException {
        ByteBuffer allocate = ByteBuffer.allocate(bArr.length + 4);
        Log.d(TAG, "Data length: " + bArr.length);
        allocate.put((byte) ((bArr.length >> 8) & 255));
        allocate.put((byte) (bArr.length & 255));
        allocate.putShort(s);
        allocate.put(bArr);
        outputStream.write(allocate.array());
        Log.d(TAG, "Data sent");
        read();
    }

    private void read() throws IOException {
        byte[] bArr = new byte[1024];
        inputDataStream.read(bArr);
        Log.d(TAG, "Got data from phone: ");
        short s = (short) (((bArr[2] & 255) << 8) | (bArr[3] & 255));
        if (s == 2) {
            Log.d(TAG, "s == 2");
            WifiSecurityResponseMessage.WifiSecurityReponse.Builder newBuilder =
                    WifiSecurityResponseMessage.WifiSecurityReponse.newBuilder();
            newBuilder.setBssid("28:ee:52:16:29:12");
            newBuilder.setAccessPointType(
                    WifiSecurityResponseMessage.WifiSecurityReponse.AccessPointType.STATIC);
            newBuilder.setKey("CarRetrofit");
            newBuilder.setSecurityMode(
                    WifiSecurityResponseMessage.WifiSecurityReponse.SecurityMode.WPA2_PERSONAL);
            newBuilder.setSsid("KIA Cerato");
            send(newBuilder.build().toByteArray(), (short) 3);
        }
    }
}
