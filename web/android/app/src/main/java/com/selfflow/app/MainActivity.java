package com.selfflow.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import com.selfflow.app.alarm.AlarmOverlayPlugin;
import com.selfflow.app.UpdaterPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AlarmOverlayPlugin.class);
        registerPlugin(UpdaterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
