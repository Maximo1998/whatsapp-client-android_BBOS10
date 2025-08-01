package com.nokia4ever.whatsapp;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        //serverUrl = intent.getStringExtra("ServerUrl");
        //whatsAppUser = (WhatsAppUser) intent.getSerializableExtra("WhatsAppUser");

        //retrieveChats();
        showNotification("Imran","test");

    } // onReceive

    private void retrieveChats() {
        try {

            String mobile = whatsAppUser.getUser();

            //progressDialog.show();
            String url = serverUrl + "/api/chats/" + mobile + "@c.us";
            Log.d(TAG, "Caling retrieveAndDisplayChats with Url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(context);
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

                                        if(!lastChatId.equals("") && !selectedContact.getId().equals(chat.getSender())){
                                            showNotification(chat.getSenderName(), chat.getMessage());
                                        }

                                        lastChatId = chat.getId();

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
