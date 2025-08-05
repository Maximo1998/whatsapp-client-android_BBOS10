package com.nokia4ever.whatsapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONObject;

import static android.content.Context.MODE_PRIVATE;

/**
 * Created by hunte on 7/29/2025.
 */

public class MyBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "MyBroadcastReceiver";
    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private ChatsResponse chatsResponse;
    private String lastChatId = "";
    private Contact selectedContact;
    private Context context;
    private SharedPreferences sharedPreferences;

    @Override
    public void onReceive(Context context, Intent intent) {

        sharedPreferences = context.getSharedPreferences("UserPreferences", MODE_PRIVATE);
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

        this.context = context;

        //createNotificationChannel();
        retrieveChats();
        //showNotification("Imran","test");

    } // onReceive

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "channel name";
            String description = "channel desc";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("11", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void retrieveChats() {
        try {
            Log.d(TAG, "Caling retrieveChats");

            String mobile = whatsAppUser.getUser();

            Log.d(TAG, "mobile: " + whatsAppUser.getUser());
            String url = serverUrl + "/api/chats/" + mobile + "@c.us?page_size=1&page=1";

            Log.d(TAG, "url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(context);
            Log.d(TAG, "request queue object created");

            final Gson gson = new Gson();

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.GET,
                    url,
                    null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            //progressDialog.dismiss();

                            Log.d(TAG, "Response: " + response.toString());

                            try {
                                chatsResponse = gson.fromJson(response.toString(), ChatsResponse.class);

                                if(chatsResponse.getChats().size()>0){
                                    Log.d(TAG, "first message: " + chatsResponse.getChats().get(0).getMessage());

                                    Message chat = chatsResponse.getChats().get(0);
                                    Log.d(TAG, "lastChatId: [" + lastChatId + "], chatId: [" + chat.getId() + "]");

                                    if(!lastChatId.equals(chat.getId())){

                                        if(!lastChatId.equals("") && !selectedContact.getId().equals(chat.getSender())){
                                            Log.d(TAG, "condition met to show notification");
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

    } // retrieveChats

    private void showNotification(String senderName, String message){

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent repeatingIntent = new Intent(context, MainActivity.class);
        repeatingIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,100,repeatingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentIntent(pendingIntent)
                .setSmallIcon(android.R.drawable.arrow_up_float)
                .setContentTitle(senderName)
                .setContentText(message)
                .setAutoCancel(true);

        notificationManager.notify(100, builder.build());
    }
}
