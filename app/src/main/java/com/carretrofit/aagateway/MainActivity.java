package com.carretrofit.aagateway;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AAGateway";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        String action = getIntent().getAction();
        if (action != null) {
            Intent i = new Intent(this, BluetoothService.class);
            if (action.equalsIgnoreCase("android.intent.action.MAIN")) {
                startService(i);
            }
        }
    }
}
