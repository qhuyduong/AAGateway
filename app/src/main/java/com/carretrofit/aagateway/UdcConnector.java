package com.carretrofit.aagateway;

import java.io.FileOutputStream;
import java.io.IOException;

public class UdcConnector {
    private static final String TAG = "AAGateWayUdcConnector";
    private static final String UDC = "/sys/kernel/config/usb_gadget/g1/UDC";
    private static final String DUMMY_UDC = "dummy_udc.0";
    private static final String EMPTY_UDC = "\n";

    public static void connect() {
        try {
            FileOutputStream udcOutputStream = new FileOutputStream(UDC);
            udcOutputStream.write(DUMMY_UDC.getBytes());
            udcOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void disconnect() {
        try {
            FileOutputStream udcOutputStream = new FileOutputStream(UDC);
            udcOutputStream.write(EMPTY_UDC.getBytes());
            udcOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
