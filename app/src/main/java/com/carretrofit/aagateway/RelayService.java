package com.carretrofit.aagateway;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class RelayService extends Service {
    private static final String TAG = "AAGateWayRelayService";
    private static final String USB_ACCESSORY = "/dev/usb_accessory";
    private static final byte[] VERSION_REQUEST = {0, 3, 0, 6, 0, 1, 0, 1, 0, 2};
    private static final byte[] VERSION_RESPONSE = {0, 3, 0, 8, 0, 2, 0, 1, 0, 4, 0, 0};
    private static final int MAX_BUFFER_LENGTH = 16384;

    private FileOutputStream usbOutputStream;
    private FileInputStream usbInputStream;

    private static OutputStream tcpOutputStream;
    private static DataInputStream tcpInputStream;

    private boolean tcpCompleted = false;
    private boolean usbCompleted = false;

    @Override
    public void onCreate() {
        Notification build =
                new Notification.Builder(this)
                        .setContentTitle("Relay Service")
                        .setPriority(1)
                        .setTicker("")
                        .build();
        startForeground(1, build);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        new Thread(new TcpThread()).start();
        new Thread(new UsbThread()).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
    }

    class TcpThread implements Runnable {
        public void run() {
            while (true) {
                ServerSocket serverSocket = null;
                Socket socket = null;
                try {
                    Log.d(TAG, "tcp - start");
                    serverSocket = new ServerSocket(Constants.TCP_PORT, 5);
                    serverSocket.setReuseAddress(true);

                    socket = serverSocket.accept();
                    serverSocket.close();

                    Log.d(TAG, "tcp - phone connected");

                    socket.setSoTimeout(5000);

                    UdcConnector.connect();

                    tcpOutputStream = socket.getOutputStream();
                    tcpInputStream = new DataInputStream(socket.getInputStream());
                    tcpOutputStream.write(VERSION_REQUEST);
                    tcpOutputStream.flush();
                    byte[] buf = new byte[MAX_BUFFER_LENGTH];
                    tcpInputStream.read(buf);
                    tcpCompleted = true;

                    // wait for usb initialization
                    while (!usbCompleted) {
                        Log.d(TAG, "tcp - waiting for usb");
                        Thread.sleep(100);
                    }

                    int messageLength;
                    int headerLength;

                    while (true) {
                        headerLength = 4;
                        tcpInputStream.readFully(buf, 0, 4);
                        messageLength = (buf[2] & 0xFF) << 8 | (buf[3] & 0xFF);

                        // Flag 9 means the header is 8 bytes long (read it in a separate byte
                        // array)
                        if ((int) buf[1] == 9) {
                            headerLength += 4;
                            tcpInputStream.readFully(buf, 4, 4);
                        }

                        tcpInputStream.readFully(buf, headerLength, messageLength);
                        usbOutputStream.write(buf, 0, messageLength + headerLength);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "tcp - error " + e.getMessage());
                } finally {
                    if (tcpInputStream != null) {
                        try {
                            tcpInputStream.close();
                        } catch (IOException e) {
                            Log.e(TAG, "tcp - error " + e.getMessage());
                        }
                    }
                    if (tcpOutputStream != null) {
                        try {
                            tcpOutputStream.close();
                        } catch (IOException e) {
                            Log.e(TAG, "tcp - error " + e.getMessage());
                        }
                    }
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "tcp - error " + e.getMessage());
                        }
                    }
                    if (serverSocket != null) {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "tcp - error " + e.getMessage());
                        }
                    }

                    UdcConnector.disconnect();

                    Log.d(TAG, "tcp - end");
                }
            }
        }
    }

    class UsbThread implements Runnable {
        public void run() {
            while (true) {
                ParcelFileDescriptor usbFileDescriptor = null;
                try {
                    Log.d(TAG, "usb - start");
                    byte buf[] = new byte[MAX_BUFFER_LENGTH];
                    File usbFile = new File(USB_ACCESSORY);
                    usbFileDescriptor =
                            ParcelFileDescriptor.open(
                                    usbFile, ParcelFileDescriptor.MODE_READ_WRITE);
                    usbInputStream = new FileInputStream(usbFileDescriptor.getFileDescriptor());
                    usbOutputStream = new FileOutputStream(usbFileDescriptor.getFileDescriptor());
                    usbInputStream.read(buf);
                    usbOutputStream.write(VERSION_RESPONSE);
                    usbCompleted = true;

                    while (!tcpCompleted) {
                        Log.d(TAG, "usb - waiting for local");
                        Thread.sleep(100);
                    }

                    int length;
                    while (true) {
                        length = usbInputStream.read(buf);
                        tcpOutputStream.write(buf, 0, length);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "usb - error " + e.getMessage());
                } finally {
                    if (usbInputStream != null) {
                        try {
                            usbInputStream.close();
                        } catch (Exception e) {
                            Log.e(TAG, "usb - error " + e.getMessage());
                        }
                    }
                    if (usbOutputStream != null) {
                        try {
                            usbOutputStream.close();
                        } catch (Exception e) {
                            Log.e(TAG, "usb - error " + e.getMessage());
                        }
                    }

                    if (usbFileDescriptor != null) {
                        try {
                            usbFileDescriptor.close();
                        } catch (IOException e) {
                            Log.e(TAG, "usb - error " + e.getMessage());
                        }
                    }

                    Log.d(TAG, "usb - end");
                }
            }
        }
    }
}
