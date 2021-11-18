package com.carretrofit.aagateway;

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

public class MyService extends Service {
    private static final String TAG = "AAGateWayService";
    private static final String USB_ACCESSORY = "/dev/usb_accessory";
    private static final byte[] VERSION_REQUEST = {0, 3, 0, 6, 0, 1, 0, 1, 0, 2};
    private static final byte[] VERSION_RESPONSE = {0, 3, 0, 8, 0, 2, 0, 1, 0, 4, 0, 0};
    private static final int MAX_BUFFER_LENGTH = 16384;
    private static final int TCP_PORT = 5288;

    private FileOutputStream usbOutputStream;
    private FileInputStream usbInputStream;

    private static OutputStream tcpOutputStream;
    private static DataInputStream tcpInputStream;

    private boolean running = false;
    private boolean tcpCompleted = false;
    private boolean usbCompleted = false;

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

        new Thread(new TcpThread()).start();
        new Thread(new UsbThread()).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;

        Log.d(TAG, "Service destroyed");
    }

    class TcpThread implements Runnable {
        public void run() {
            ServerSocket serverSocket = null;
            try {
                Log.d(TAG, "tcp - start");
                serverSocket = new ServerSocket(TCP_PORT, 5);
                serverSocket.setReuseAddress(true);

                Socket socket = serverSocket.accept();

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
                while (!usbCompleted && running) {
                    Log.d(TAG, "tcp - waiting for usb");
                    Thread.sleep(100);
                }

                int messageLength;
                int headerLength;

                while (running) {
                    headerLength = 4;
                    tcpInputStream.readFully(buf, 0, 4);
                    messageLength = (buf[2] & 0xFF) << 8 | (buf[3] & 0xFF);

                    // Flag 9 means the header is 8 bytes long (read it in a separate byte array)
                    if ((int) buf[1] == 9) {
                        headerLength += 4;
                        tcpInputStream.readFully(buf, 4, 4);
                    }

                    tcpInputStream.readFully(buf, headerLength, messageLength);
                    usbOutputStream.write(buf, 0, messageLength + headerLength);
                }

                serverSocket.close();
                Log.d(TAG, "tcp - end");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (tcpInputStream != null) {
                    try {
                        tcpInputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
                if (tcpOutputStream != null) {
                    try {
                        tcpOutputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

                UdcConnector.disconnect();
                stopSelf();
            }
        }
    }

    class UsbThread implements Runnable {
        public void run() {
            ParcelFileDescriptor usbFileDescriptor = null;
            try {
                Log.d(TAG, "usb - start");
                byte buf[] = new byte[MAX_BUFFER_LENGTH];
                File usbFile = new File(USB_ACCESSORY);
                usbFileDescriptor =
                        ParcelFileDescriptor.open(usbFile, ParcelFileDescriptor.MODE_READ_WRITE);
                usbInputStream = new FileInputStream(usbFileDescriptor.getFileDescriptor());
                usbOutputStream = new FileOutputStream(usbFileDescriptor.getFileDescriptor());
                usbInputStream.read(buf);
                usbOutputStream.write(VERSION_RESPONSE);
                usbCompleted = true;

                while (!tcpCompleted && running) {
                    Log.d(TAG, "usb - waiting for local");
                    Thread.sleep(100);
                }

                int length;
                while (running) {
                    length = usbInputStream.read(buf);
                    tcpOutputStream.write(buf, 0, length);
                }

                usbFileDescriptor.close();

                Log.d(TAG, "usb - end");
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            } finally {
                if (usbInputStream != null) {
                    try {
                        usbInputStream.close();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
                if (usbOutputStream != null) {
                    try {
                        usbOutputStream.close();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }

                if (usbFileDescriptor != null) {
                    try {
                        usbFileDescriptor.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
                stopSelf();
            }
        }
    }
}
