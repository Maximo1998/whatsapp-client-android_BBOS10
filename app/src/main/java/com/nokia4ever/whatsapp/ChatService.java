package com.nokia4ever.whatsapp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONObject;


/**
 * Created by hunte on 7/30/2025.
 */

public class ChatService extends Service {
    private static final String TAG = "ChatService";

    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private ChatsResponse chatsResponse;
    private String lastChatId = "";
    private Contact selectedContact;
    private SharedPreferences sharedPreferences;
    private boolean appInBackground;

    private boolean mIsChatHandlerOn;


    class MyServiceBinder extends Binder {
        public ChatService getService(){
            return ChatService.this;
        }
    }

    private IBinder mBinder = new MyServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "In OnBind");

        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.i(TAG, "In OnReBind");
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        stopChatHandler();
        Log.i(TAG, "Service Destroyed");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "In onStartCommand, thread id: " + Thread.currentThread().getId());

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);


        lastChatId = sharedPreferences.getString("last_chat_id","");

        serverUrl = sharedPreferences.getString("server_url","");

        whatsAppUser = new WhatsAppUser(
                sharedPreferences.getString("pushname",""),
                sharedPreferences.getString("user",""),
                sharedPreferences.getString("platform","")
        );

        selectedContact = new Contact(
                sharedPreferences.getString("contact_id",""),
                sharedPreferences.getString("contact_name","")
        );

//        serverUrl = intent.getStringExtra("ServerUrl");
//        whatsAppUser = (WhatsAppUser) intent.getSerializableExtra("WhatsAppUser");

        mIsChatHandlerOn = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                startChatHandler();
            }
        }).start();

        return START_STICKY;
    }

    private void startChatHandler(){
        while(mIsChatHandlerOn){
            try {
                Thread.sleep(5000);
                if(mIsChatHandlerOn){
                    try {
                        retrieveChats();
                    } catch(Exception ex){
                        Log.e(TAG, ex.getMessage());
                    }

                    Log.i(TAG, "Thread id: " + Thread.currentThread().getId());

                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopChatHandler() {
        mIsChatHandlerOn = false;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "In OnUnbind");
        return super.onUnbind(intent);
    }

    public ChatsResponse getChats(){
        return chatsResponse;
    }

    public void setSelectedContact(Contact contect){
        selectedContact=contect;
    }

    private void retrieveChats() {
        try {

            String mobile = whatsAppUser.getUser();

            //progressDialog.show();
            String url = serverUrl + "/api/chats/" + mobile + "@c.us";
            Log.d(TAG, "Caling retrieveAndDisplayChats with Url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
            final Gson gson = new Gson();

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.GET,
                    url,
                    null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            //progressDialog.dismiss();

                            Log.i(TAG, "Response: " + response.toString());

                            try {
                                chatsResponse = gson.fromJson(response.toString(), ChatsResponse.class);
                                //Toast.makeText(getContext(), chatsResponse.getChats().get(0).getMessage(), Toast.LENGTH_SHORT).show();

                                if(chatsResponse.getChats().size()>0){
                                    Message chat = chatsResponse.getChats().get(0);
                                    if(!lastChatId.equals(chat.getId())){

//                                        if(!lastChatId.equals("") && !selectedContact.getId().equals(chat.getSender())){
//                                            showNotification(chat.getSenderName(), chat.getMessage());
//                                        }

                                        appInBackground = sharedPreferences.getBoolean("app_in_background", false);

                                        if(!lastChatId.equals("") && appInBackground){
                                            showNotification(chat.getSenderName(), chat.getMessage());
                                        }
                                        lastChatId = chat.getId();

                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.putString("last_chat_id", lastChatId);
                                        editor.apply();
                                    }
                                }

                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString(), ex);
                                //Toast.makeText(getApplicationContext(), ex.toString(), Toast.LENGTH_LONG).show();
                            }

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            //progressDialog.dismiss();

                            String errorMsg = error.getMessage();

                            if (errorMsg == null) {
                                //Toast.makeText(getApplicationContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Unable to connect to server");
                            }

                            // if HTTP status code is 401
                            else if (errorMsg.equals("java.io.IOException: No authentication challenges found")) {
                                //Toast.makeText(getApplicationContext(), "No User Session found, please login from website", Toast.LENGTH_LONG).show();
                                Log.e(TAG, "No User Session found, please login from website");
                            } else {
                                //Toast.makeText(getApplicationContext(), "Unable to fetch chats, please check server URL", Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Unable to fetch chats, please check server URL");
                            }
                        }
                    }
            );
            requestQueue.add(request);
        } catch (Exception ex){
            Log.e(TAG, ex.getMessage(), ex);
        }

    } // retrieveAndDisplayChats

    private void showNotification(String senderName, String message){

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent repeatingIntent = new Intent(this, MainActivity.class);
        repeatingIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,100,repeatingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentIntent(pendingIntent)
                .setSmallIcon(android.R.drawable.arrow_up_float)
                .setContentTitle(senderName)
                .setContentText(message)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL);


        notificationManager.notify(100, builder.build());
    }
}
