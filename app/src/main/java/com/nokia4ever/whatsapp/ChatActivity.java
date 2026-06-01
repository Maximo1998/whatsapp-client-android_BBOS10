package com.nokia4ever.whatsapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.os.Bundle;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;
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
    private ArrayList<Message> currentMessages = new ArrayList<>();
    private AlertDialog progressDialog;
    private static final int POLL_INTERVAL_MS = 5000;
    private Boolean isTimerEnabled;
    private String serverUrl;
    private RequestQueue mQueue;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private WhatsAppUser whatsAppUser;
    private String lastMessageId = "";
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        sharedPreferences = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        serverUrl = sharedPreferences.getString("server_url", "");
        mQueue = Volley.newRequestQueue(this);

        whatsAppUser = new WhatsAppUser(
                sharedPreferences.getString("pushname", ""),
                sharedPreferences.getString("user", ""),
                sharedPreferences.getString("platform", "")
        );

        selectedContact = new Contact(
                sharedPreferences.getString("contact_id", ""),
                sharedPreferences.getString("contact_name", "")
        );

        initializeFields();

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Message selectedMessage = (Message) adapterView.getItemAtPosition(position);
                if (selectedMessage == null) return;
                String ct = selectedMessage.getChatType();
                if (ct == null) return;

                Intent intent = null;
                if (ct.equals("image"))
                    intent = new Intent(ChatActivity.this, ImageViewActivity.class);
                else if (ct.equals("audio") || ct.equals("ptt"))
                    intent = new Intent(ChatActivity.this, PlayAudioActivity.class);

                if (intent != null) {
                    intent.putExtra("Message", selectedMessage);
                    intent.putExtra("ServerUrl", serverUrl);
                    intent.putExtra("WhatsAppUser", whatsAppUser);
                    startActivity(intent);
                }
            }
        });

        btnSendMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (txtMessage.getText().toString().trim().isEmpty()) {
                    Toast.makeText(ChatActivity.this, "Please enter the text to send", Toast.LENGTH_SHORT).show();
                    return;
                }
                sendMessage();
            }
        });

        btnSendFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CharSequence[] options = new CharSequence[]{"Gallery", "Camera", "Record Audio"};
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
                builder.setTitle("Make your selection");
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_GET_CONTENT);
                                intent.setType("image/*");
                                startActivityForResult(Intent.createChooser(intent, "Select Image"), GALLERY_REQUEST);
                                break;
                            case 1:
                                Intent intent2 = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                startActivityForResult(intent2, CAMERA_REQUEST);
                                break;
                            case 2:
                                Intent intent3 = new Intent(ChatActivity.this, RecordAudioActivity.class);
                                intent3.putExtra("ServerUrl", serverUrl);
                                intent3.putExtra("WhatsAppUser", whatsAppUser);
                                intent3.putExtra("Contact", selectedContact);
                                startActivity(intent3);
                                break;
                        }
                    }
                });
                builder.show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isTimerEnabled = true;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTimerEnabled && !isFinishing()) {
                    retrieveAndDisplayMessages();
                    timerHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isTimerEnabled = false;
        timerHandler.removeCallbacks(timerRunnable);
        sharedPreferences.edit()
                .putString("contact_id", "")
                .putString("contact_name", "")
                .apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacksAndMessages(null);
        if (mQueue != null) mQueue.cancelAll(TAG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == GALLERY_REQUEST || requestCode == CAMERA_REQUEST) && resultCode == RESULT_OK) {
            try {
                Bitmap photo;
                if (requestCode == GALLERY_REQUEST) {
                    final Uri imageUri = data.getData();
                    final InputStream imageStream = getContentResolver().openInputStream(imageUri);
                    photo = BitmapFactory.decodeStream(imageStream);
                } else {
                    photo = (Bitmap) data.getExtras().get("data");
                }

                progressDialog.show();
                RequestQueue requestQueue = mQueue;
                Map<String, String> headers = new HashMap<>();
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
                request.addPart(new MultipartRequest.FormPart("sender", whatsAppUser.getUser()));
                request.addPart(new MultipartRequest.FormPart("receiver", selectedContact.getId()));
                request.addPart(new MultipartRequest.FilePart("media", "image/jpeg", "image.jpg", getByteImage(photo)));
                requestQueue.add(request);

            } catch (FileNotFoundException e) {
                progressDialog.dismiss();
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private byte[] getByteImage(Bitmap bm) {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, ba);
        return ba.toByteArray();
    }

    private void initializeFields() {
        mToolbar = findViewById(R.id.chat_toolbar);
        setSupportActionBar(mToolbar);

        // Toolbar personalizada: foto de perfil + nombre del contacto (toca para ver perfil)
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayShowCustomEnabled(true);

        View toolbarContent = getLayoutInflater().inflate(R.layout.chat_toolbar_content, null);
        ((TextView) toolbarContent.findViewById(R.id.toolbar_contact_name)).setText(selectedContact.getName());

        CircleImageView toolbarPic = toolbarContent.findViewById(R.id.toolbar_profile_pic);
        Picasso.with(this)
                .load(serverUrl + "/api/profilepic/" + whatsAppUser.getUser() + "@c.us/" + selectedContact.getId())
                .placeholder(R.drawable.profile_image)
                .error(R.drawable.profile_image)
                .into(toolbarPic);

        toolbarContent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showContactProfileDialog();
            }
        });

        getSupportActionBar().setCustomView(toolbarContent);

        btnSendMessage = findViewById(R.id.send_message_btn);
        btnSendFiles   = findViewById(R.id.send_files_btn);
        txtMessage     = findViewById(R.id.input_message);

        mListView = findViewById(R.id.messages_list_view);
        mListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        mListView.setStackFromBottom(true);

        // Crear adapter una sola vez con la lista compartida
        adapter = new MessagesListAdapter(getApplicationContext(),
                R.layout.custom_messages_layout, currentMessages, whatsAppUser, serverUrl);
        mListView.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            getSharedPreferences("UserPreferences", MODE_PRIVATE).edit()
                    .remove("user").remove("pushname").remove("platform")
                    .remove("last_chat_id").apply();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showContactProfileDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_contact_profile, null);
        CircleImageView profilePic = dialogView.findViewById(R.id.dialog_profile_pic);
        TextView contactIdView     = dialogView.findViewById(R.id.dialog_contact_id);

        // Mostrar número limpio o etiqueta amigable en lugar del ID interno @lid
        String rawId = selectedContact.getId();
        String displayId;
        if (rawId.contains("@c.us")) {
            displayId = "+" + rawId.replace("@c.us", "");
        } else if (rawId.contains("@lid")) {
            displayId = "WhatsApp Contact";
        } else {
            displayId = rawId;
        }
        contactIdView.setText(displayId);

        Picasso.with(this)
                .load(serverUrl + "/api/profilepic/" + whatsAppUser.getUser() + "@c.us/" + selectedContact.getId())
                .placeholder(R.drawable.profile_image)
                .error(R.drawable.profile_image)
                .into(profilePic);

        new AlertDialog.Builder(this)
                .setTitle(selectedContact.getName())
                .setView(dialogView)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void sendMessage() {
        final String messageText = txtMessage.getText().toString().trim();
        txtMessage.setText("");

        // Optimistic UI: añadir el mensaje al chat inmediatamente
        Message optimistic = new Message(
                "pending_" + System.currentTimeMillis(),
                whatsAppUser.getUser() + "@c.us",
                selectedContact.getId(),
                messageText,
                0,
                whatsAppUser.getPushname(),
                getCurrentTimestamp(),
                getCurrentTimestamp(),
                "chat"
        );
        currentMessages.add(optimistic);
        adapter.notifyDataSetChanged();
        mListView.post(new Runnable() {
            @Override
            public void run() {
                mListView.setSelection(adapter.getCount() - 1);
            }
        });

        // Enviar al servidor
        try {
            String url = serverUrl + "/api/messages/";
            RequestQueue requestQueue = mQueue;

            JSONObject jsonRequest = new JSONObject();
            try {
                jsonRequest.put("sender", whatsAppUser.getUser());
                jsonRequest.put("receiver", selectedContact.getId());
                jsonRequest.put("message", messageText);
            } catch (JSONException ex) {
                Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, url, jsonRequest,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            Log.i(TAG, "sendMessage OK: " + response.toString());
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            String errorMsg = error.getMessage();
                            if (errorMsg == null)
                                Toast.makeText(getApplicationContext(), "Unable to connect to server", Toast.LENGTH_LONG).show();
                            else
                                Toast.makeText(getApplicationContext(), "Unable to send message", Toast.LENGTH_LONG).show();
                        }
                    }
            );
            requestQueue.add(request);

        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
            Toast.makeText(getApplicationContext(), "Error sending message", Toast.LENGTH_LONG).show();
        }
    }

    private void retrieveAndDisplayMessages() {
        try {
            String url = serverUrl + "/api/messages/" + whatsAppUser.getUser() + "@c.us/" + selectedContact.getId();
            Log.d(TAG, "retrieveAndDisplayMessages: " + url);

            RequestQueue requestQueue = mQueue;
            final Gson gson = new Gson();

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.GET, url, null,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                MessageResponse resp = gson.fromJson(response.toString(), MessageResponse.class);
                                ArrayList<Message> messagesList = resp.getMessages();

                                if (messagesList != null && messagesList.size() > 0) {
                                    // El servidor devuelve DESC (más nuevo primero)
                                    Message newest = messagesList.get(0);
                                    if (!lastMessageId.equals(newest.getId())) {
                                        lastMessageId = newest.getId();
                                        Collections.reverse(messagesList);
                                        adapter.update(messagesList);
                                        mListView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                mListView.setSelection(adapter.getCount() - 1);
                                            }
                                        });
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString(), ex);
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.e(TAG, "retrieveAndDisplayMessages error: " + error.toString());
                        }
                    }
            );
            requestQueue.add(request);

        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
