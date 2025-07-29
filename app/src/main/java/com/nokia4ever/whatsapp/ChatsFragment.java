package com.nokia4ever.whatsapp;


import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONObject;


/**
 * A simple {@link Fragment} subclass.
 */
public class ChatsFragment extends Fragment {
    private static final String TAG = "ChatsFragment";
    private final static String default_notification_channel_id = "default";

    private View mGroupFragmentView;
    private ListView mListView;
    private ChatsListAdapter adapter;
    private ChatsResponse chatsResponse;
    private AlertDialog progressDialog;
    private int seconds = 5;
    private Boolean isTimerEnabled=true;
    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private String lastChatId = "";
    private Contact selectedContact;

    public ChatsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        serverUrl = getActivity().getIntent().getStringExtra("ServerUrl");
        whatsAppUser = (WhatsAppUser) getActivity().getIntent().getSerializableExtra("WhatsAppUser");

        mGroupFragmentView = inflater.inflate(R.layout.fragment_chats, container, false);

        initializeFields();

        timer();

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Message selectedChat = (Message) adapterView.getItemAtPosition(position);
                selectedContact = new Contact(selectedChat.getSender(), selectedChat.getSenderName());

                Intent intent = new Intent(getContext(), ChatActivity.class);
                intent.putExtra("Contact", selectedContact);
                intent.putExtra("ServerUrl", serverUrl);
                intent.putExtra("WhatsAppUser", whatsAppUser);

                startActivity(intent);
            }
        });

        return mGroupFragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();
        selectedContact = new Contact("","");
    }

    private void timer(){
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                retrieveAndDisplayChats();
                if(isTimerEnabled)
                    handler.postDelayed(this, seconds * 1000);
            }
        });
    }

    private void initializeFields() {
        mListView = mGroupFragmentView.findViewById(R.id.list_view);


        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    private void playNotificationSound(){
        /*
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("WhatsApp")
                .setContentText("New message received");

        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.notify(0, builder.build());
        */


        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }



    }

    private void retrieveAndDisplayChats() {
        try {

            String mobile = whatsAppUser.getUser();

            //progressDialog.show();
            String url = serverUrl + "/api/chats/" + mobile + "@c.us";
            Log.d(TAG, "Caling retrieveAndDisplayChats with Url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(getContext());
            final Gson gson = new Gson();

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.GET,
                    url,
                    null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            progressDialog.dismiss();

                            Log.i("Response", response.toString());

                            try {
                                chatsResponse = gson.fromJson(response.toString(), ChatsResponse.class);
                                //Toast.makeText(getContext(), chatsResponse.getChats().get(0).getMessage(), Toast.LENGTH_SHORT).show();

                                if(chatsResponse.getChats().size()>0){
                                    Message chat = chatsResponse.getChats().get(0);
                                    if(!lastChatId.equals(chat.getId())){

                                        if(!lastChatId.equals("") && !selectedContact.getId().equals(chat.getSender())){
                                            playNotificationSound();    // new message received
                                            //chatsList.setTitle("New msg received");
                                        }

                                        lastChatId = chat.getId();

                                        adapter =
                                                new ChatsListAdapter(getContext(), R.layout.custom_chats_layout, chatsResponse.getChats());

                                        mListView.setAdapter(adapter);
                                    }
                                }


                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString(), ex);
                                Toast.makeText(getContext(), ex.toString(), Toast.LENGTH_LONG).show();
                            }

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            progressDialog.dismiss();

                            String errorMsg = error.getMessage();

                            if (errorMsg == null) {
                                Toast.makeText(getContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                            }

                            // if HTTP status code is 401
                            else if (errorMsg.equals("java.io.IOException: No authentication challenges found")) {
                                Toast.makeText(getContext(), "No User Session found, please login from website", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "Unable to fetch chats, please check server URL", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );
            requestQueue.add(request);
        } catch (Exception ex){
            Log.e(TAG, ex.getMessage(), ex);
        }

    }


}
