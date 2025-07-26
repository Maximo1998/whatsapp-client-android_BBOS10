package com.nokia4ever.whatsapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
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

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import uk.me.hardill.volley.multipart.MultipartRequest;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    private static final int CAMERA_REQUEST = 1888;
    private static final int GALLERY_REQUEST = 1889;

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
    private String lastMessageId = "";

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
                /*
                Intent intent = new Intent(ChatActivity.this, ImageUploadActivity.class);
                intent.putExtra("ServerUrl", serverUrl);
                intent.putExtra("WhatsAppUser", whatsAppUser);
                intent.putExtra("Contact", selectedContact);
                startActivity(intent);
                */
                CharSequence options[] = new CharSequence[]{
                        "Gallery",
                        "Camera"
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
                builder.setTitle("Make your selection");

                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i){
                            case 0:
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_GET_CONTENT);
                                intent.setType("image/*");
                                startActivityForResult(Intent.createChooser(intent,"Select Image"),GALLERY_REQUEST);
                                break;

                            case 1:
                                Intent intent2 = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                startActivityForResult(intent2, CAMERA_REQUEST);
                                break;

                        };
                    };
                });
                builder.show();
            }
        });

        timer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        progressDialog.show();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == GALLERY_REQUEST || requestCode == CAMERA_REQUEST) {
            if (resultCode == RESULT_OK) {
                try {
                    Bitmap photo = null;

                    if(requestCode == GALLERY_REQUEST){
                        final Uri imageUri = data.getData();
                        final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                        photo = BitmapFactory.decodeStream(imageStream);
                    }
                    else if(requestCode == CAMERA_REQUEST){
                        photo = (Bitmap) data.getExtras().get("data");
                    }



                    progressDialog.show();
                    RequestQueue requestQueue = Volley.newRequestQueue(ChatActivity.this);
                    Map<String, String> headers = new HashMap<String, String>();
                    String url = serverUrl + "/api/upload";
                    MultipartRequest request = new MultipartRequest(url, headers,
                            new Response.Listener<NetworkResponse>() {
                                @Override
                                public void onResponse(NetworkResponse response) {
                                    progressDialog.dismiss();
                                    Toast.makeText(ChatActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    progressDialog.dismiss();
                                    Toast.makeText(ChatActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                                }
                            });

                    request.addPart(new MultipartRequest.FormPart("sender",whatsAppUser.getUser()));
                    request.addPart(new MultipartRequest.FormPart("receiver",selectedContact.getId()));
                    request.addPart(new MultipartRequest.FilePart("media", "image/jpeg", "image.jpg", getByteImage(photo)));

                    requestQueue.add(request);

                } catch (FileNotFoundException e) {
                    progressDialog.dismiss();
                    e.printStackTrace();
                    Toast.makeText(ChatActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(ChatActivity.this, "You haven't picked an image", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private byte[] getByteImage(Bitmap bm) {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, ba);
        byte[] imageData = ba.toByteArray();
        return imageData;
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


                                if(messagesList.size()>0){
                                    Message message = messagesList.get(0);
                                    if(!lastMessageId.equals(message.getId())){

                                        if(!lastMessageId.equals("")){
                                            //playSound();    // new message received
                                            //chatsList.setTitle("New msg received");
                                        }

                                        lastMessageId = message.getId();

                                        Collections.reverse(messagesList);
                                        adapter =
                                                new MessagesListAdapter(getApplicationContext(), R.layout.custom_messages_layout, messagesList, whatsAppUser, serverUrl);

                                        mListView.setAdapter(adapter);
                                    }
                                }



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
