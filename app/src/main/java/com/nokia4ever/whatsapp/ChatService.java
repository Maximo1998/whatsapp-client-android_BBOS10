package com.nokia4ever.whatsapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
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

public class ChatService extends Service {
    private static final String TAG = "ChatService";
    private static final String CHANNEL_ID = "novel_messenger_service";
    private static final int FOREGROUND_NOTIF_ID = 1;
    private static final int MSG_NOTIF_ID = 100;

    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private ChatsResponse chatsResponse;
    private String lastChatId = "";
    private Contact selectedContact;
    private SharedPreferences sharedPreferences;
    private volatile boolean mIsChatHandlerOn;

    class MyServiceBinder extends Binder {
        public ChatService getService() { return ChatService.this; }
    }
    private final IBinder mBinder = new MyServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onRebind(Intent intent) { super.onRebind(intent); }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsChatHandlerOn = false;
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        lastChatId   = sharedPreferences.getString("last_chat_id", "");
        serverUrl    = sharedPreferences.getString("server_url", "");
        whatsAppUser = new WhatsAppUser(
                sharedPreferences.getString("pushname", ""),
                sharedPreferences.getString("user", ""),
                sharedPreferences.getString("platform", ""));
        selectedContact = new Contact(
                sharedPreferences.getString("contact_id", ""),
                sharedPreferences.getString("contact_name", ""));

        createNotificationChannel();
        Notification foregroundNotif = buildForegroundNotification();
        // API 29+ requiere pasar el tipo; API < 29 usa la firma original
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_NOTIF_ID, foregroundNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_NOTIF_ID, foregroundNotif);
        }

        mIsChatHandlerOn = true;
        new Thread(new Runnable() {
            @Override
            public void run() { startChatHandler(); }
        }).start();

        return START_STICKY; // sistema lo reinicia si lo mata
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Novel Messenger background service");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildForegroundNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_service_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void startChatHandler() {
        while (mIsChatHandlerOn) {
            try {
                Thread.sleep(5000);
                if (mIsChatHandlerOn) retrieveChats();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                Log.e(TAG, "Handler error: " + ex.getMessage());
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) { return super.onUnbind(intent); }

    public ChatsResponse getChats() { return chatsResponse; }
    public void setSelectedContact(Contact c) { selectedContact = c; }

    private void retrieveChats() {
        try {
            String url = serverUrl + "/api/chats/" + whatsAppUser.getUser() + "@c.us";
            RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
            final Gson gson = new Gson();

            queue.add(new JsonObjectRequest(Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                chatsResponse = gson.fromJson(response.toString(), ChatsResponse.class);
                                if (chatsResponse.getChats().size() > 0) {
                                    Message chat = chatsResponse.getChats().get(0);
                                    if (!lastChatId.equals(chat.getId())) {
                                        boolean appInBackground = sharedPreferences.getBoolean("app_in_background", false);
                                        if (!lastChatId.isEmpty() && appInBackground) {
                                            showMessageNotification(chat.getSenderName(), chat.getMessage());
                                        }
                                        lastChatId = chat.getId();
                                        sharedPreferences.edit()
                                                .putString("last_chat_id", lastChatId)
                                                .apply();
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString());
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e(TAG, "retrieveChats error: " + error.getMessage());
                        }
                    }
            ));
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
    }

    private void showMessageNotification(String senderName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, MSG_NOTIF_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(senderName)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(MSG_NOTIF_ID, notif);
    }
}
