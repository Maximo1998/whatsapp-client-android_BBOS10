package com.nokia4ever.whatsapp;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDexApplication;
import android.content.SharedPreferences;
import android.util.Log;

// Extiende MultiDexApplication para mantener soporte multidex en API < 21
public class MyApplication extends MultiDexApplication implements LifecycleObserver {
    private static final String TAG = "MyApplication";

    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate called");

        super.onCreate();
        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded() {
        Log.d(TAG, "onAppBackgrounded called");
        //App in background

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("app_in_background", true);
        editor.apply();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded() {
        Log.d(TAG, "onAppForegrounded called");
        // App in foreground

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("app_in_background", false);
        editor.apply();
    }
}