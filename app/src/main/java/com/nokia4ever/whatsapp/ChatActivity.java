package com.nokia4ever.whatsapp;

import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    private Toolbar mToolbar;
    private ImageButton btnSendMessage, btnSendFiles;
    private EditText txtMessage;

    private Contact selectedContact;

    private ListView mListView;
    private MessagesListAdapter adapter;
    private MessageResponse messagesResponse;
    private AlertDialog progressDialog;
    private int seconds = 5;
    private Boolean isTimerEnabled=true;
    private String serverUrl;
    private WhatsAppUser whatsAppUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        serverUrl = getIntent().getStringExtra("ServerUrl");
        whatsAppUser = (WhatsAppUser) getIntent().getSerializableExtra("WhatsAppUser");

        selectedContact = (Contact)getIntent().getSerializableExtra("Contact");

        initializeFields();

        btnSendMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(txtMessage.getText().toString().isEmpty()){
                    Toast.makeText(ChatActivity.this, "Please enter the text to send", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendMessage();
            }
        });

        btnSendFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ChatActivity.this, "Feature not implemented yet.", Toast.LENGTH_SHORT).show();
            }
        });

        timer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        progressDialog.show();
    }

    private void initializeFields() {
        mToolbar = (Toolbar) findViewById(R.id.chat_toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setTitle("Chat with " + selectedContact.getName());

        btnSendMessage = (ImageButton) findViewById(R.id.send_message_btn);
        btnSendFiles = (ImageButton) findViewById(R.id.send_files_btn);

        txtMessage = (EditText) findViewById(R.id.input_message);

        mListView = (ListView) findViewById(R.id.messages_list_view);
        mListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        mListView.setStackFromBottom(true);


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    private void timer(){
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                retrieveAndDisplayMessages();
                if(isTimerEnabled)
                    handler.postDelayed(this, seconds * 1000);
            }
        });
    }

    private void sendMessage() {

        try {
            String mobile = whatsAppUser.getUser();

            //progressDialog.show();
            String url = serverUrl + "/api/messages/";
            Log.d(TAG, "Caling sendMessage with Url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(this);
            final Gson gson = new Gson();

            JSONObject jsonRequest = new JSONObject();
            try {
                jsonRequest.put("sender", whatsAppUser.getUser());
                jsonRequest.put("receiver", selectedContact.getId());
                jsonRequest.put("message", txtMessage.getText().toString());
            } catch (JSONException ex) {
                Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            txtMessage.setText("");

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    jsonRequest,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            progressDialog.dismiss();

                            Log.i("Response", response.toString());


                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            progressDialog.dismiss();

                            String errorMsg = error.getMessage();

                            if (errorMsg == null) {
                                Toast.makeText(getApplicationContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                            }

                            // if HTTP status code is 401
                            else if (errorMsg.equals("java.io.IOException: No authentication challenges found")) {
                                Toast.makeText(getApplicationContext(), "No User Session found, please login from website", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Unable to send message, please check server URL", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );
            requestQueue.add(request);
        } catch(Exception ex){
            Log.e(TAG, ex.getMessage(), ex);
            Toast.makeText(getApplicationContext(), "Unknown error occurred while sending message", Toast.LENGTH_LONG).show();
        }
    }

    private void retrieveAndDisplayMessages() {
        try {

            String mobile = whatsAppUser.getUser();

            //progressDialog.show();
            String url = serverUrl + "/api/messages/" + mobile + "@c.us/" + selectedContact.getId();
            Log.d(TAG, "Caling retrieveAndDisplayMessages with Url: " + url);

            RequestQueue requestQueue = Volley.newRequestQueue(this);
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
                                messagesResponse = gson.fromJson(response.toString(), MessageResponse.class);
                                //Toast.makeText(getContext(), chatsResponse.getChats().get(0).getMessage(), Toast.LENGTH_SHORT).show();
                                ArrayList<Message> messagesList = messagesResponse.getMessages();
                                Collections.reverse(messagesList);
                                adapter =
                                        new MessagesListAdapter(getApplicationContext(), R.layout.custom_messages_layout, messagesList, whatsAppUser);

                                mListView.setAdapter(adapter);

                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString(), ex);
                                Toast.makeText(getApplicationContext(), ex.toString(), Toast.LENGTH_LONG).show();
                            }

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            progressDialog.dismiss();

                            String errorMsg = error.getMessage();

                            if (errorMsg == null) {
                                Toast.makeText(getApplicationContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                            }

                            // if HTTP status code is 401
                            else if (errorMsg.equals("java.io.IOException: No authentication challenges found")) {
                                Toast.makeText(getApplicationContext(), "No User Session found, please login from website", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), "Unable to fetch messages, please check server URL", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
            );
            requestQueue.add(request);

        } catch(Exception ex){
            Log.e(TAG, ex.getMessage(), ex);
        }
    }

}
