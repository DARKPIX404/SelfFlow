package com.selfflow.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import com.selfflow.app.alarm.AlarmOverlayPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AlarmOverlayPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
