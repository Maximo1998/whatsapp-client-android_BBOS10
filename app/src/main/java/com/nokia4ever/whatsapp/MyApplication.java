package com.nokia4ever.whatsapp;

import android.app.Application;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.SharedPreferences;
import android.util.Log;

import static java.security.AccessController.getContext;

public class MyApplication extends Application implements LifecycleObserver {
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