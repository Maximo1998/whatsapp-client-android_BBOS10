package com.nokia4ever.whatsapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
    private String cameraPhotoPath;
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
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private String serverUrl;
    private RequestQueue mQueue;
    private WhatsAppUser whatsAppUser;
    private String lastMessageId = "";
    private String lastReactionSignature = "";
    private SharedPreferences sharedPreferences;

    // Estado de "responder" (reply/cita): mensaje al que se está respondiendo.
    private Message replyingTo;
    private View replyBar;
    private TextView replyPreviewText;

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

        // Mantener pulsado un mensaje → menú: Responder / Reaccionar.
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                Message selectedMessage = (Message) adapterView.getItemAtPosition(position);
                if (selectedMessage == null) return false;
                showMessageActions(selectedMessage);
                return true;
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
                CharSequence[] options = new CharSequence[]{"Emoji", "Gallery", "Camera", "Record Audio"};
                AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
                builder.setTitle("Make your selection");
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                showEmojiPicker();
                                break;
                            case 1:
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_GET_CONTENT);
                                intent.setType("image/*");
                                startActivityForResult(Intent.createChooser(intent, "Select Image"), GALLERY_REQUEST);
                                break;
                            case 2:
                                Intent intent2 = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                // Save photo to file instead of getting thumbnail
                                File photoFile = createImageFile();
                                if (photoFile != null) {
                                    cameraPhotoPath = photoFile.getAbsolutePath();
                                    intent2.putExtra(MediaStore.EXTRA_OUTPUT, android.net.Uri.fromFile(photoFile));
                                }
                                startActivityForResult(intent2, CAMERA_REQUEST);
                                break;
                            case 3:
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
        lastMessageId = "";

        // Al abrir un chat, borrar la notificación de la barra de estado (id 100,
        // compartido por ChatService.MSG_NOTIF_ID).
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(100);
        } catch (Exception ignored) {}

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

    private File createImageFile() {
        try {
            File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile("camera_", ".jpg", storageDir);
            return image;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == GALLERY_REQUEST || requestCode == CAMERA_REQUEST) && resultCode == RESULT_OK) {
            try {
                // Decodificar con muestreo (inSampleSize) para no cargar la foto a
                // resolución completa: el Q20 tiene poco heap y un JPEG de cámara de
                // varios MP en ARGB_8888 disparaba OutOfMemoryError (cierre de la app).
                Bitmap photo;
                if (requestCode == GALLERY_REQUEST) {
                    photo = decodeSampledFromUri(data.getData(), 1600);
                } else {
                    if (cameraPhotoPath != null) {
                        photo = decodeSampledFromFile(cameraPhotoPath, 1600);
                    } else {
                        Toast.makeText(this, "Camera photo save failed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                if (photo == null) {
                    Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show();
                    return;
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
        // Calidad 85: imperceptible vs 100 pero mucho menos memoria/red.
        bm.compress(Bitmap.CompressFormat.JPEG, 85, ba);
        return ba.toByteArray();
    }

    /** Calcula inSampleSize (potencia de 2) para que la imagen no supere maxDim px. */
    private int calculateInSampleSize(BitmapFactory.Options options, int maxDim) {
        int h = options.outHeight, w = options.outWidth;
        int sample = 1;
        while ((h / sample) > maxDim || (w / sample) > maxDim) sample *= 2;
        return sample;
    }

    /** Decodifica una imagen de un Uri con muestreo, sin cargarla a resolución completa. */
    private Bitmap decodeSampledFromUri(Uri uri, int maxDim) throws FileNotFoundException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream s1 = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(s1, null, bounds);
        try { if (s1 != null) s1.close(); } catch (Exception ignored) {}

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds, maxDim);
        InputStream s2 = getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(s2, null, opts);
        try { if (s2 != null) s2.close(); } catch (Exception ignored) {}
        return bmp;
    }

    /** Decodifica una imagen de un archivo con muestreo, sin cargarla a resolución completa. */
    private Bitmap decodeSampledFromFile(String path, int maxDim) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds, maxDim);
        return BitmapFactory.decodeFile(path, opts);
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

        // Listener para abrir imágenes fullscreen
        adapter.setImageClickListener(imageUrl -> showFullscreenImage(imageUrl));

        // Listener para reproducir vídeos con el player nativo
        adapter.setVideoClickListener(videoUrl -> playVideo(videoUrl));

        // Barra de "respondiendo a…": oculta hasta que se elige responder a un mensaje.
        replyBar         = findViewById(R.id.reply_bar);
        replyPreviewText = findViewById(R.id.reply_preview_text);
        findViewById(R.id.reply_cancel_btn).setOnClickListener(v -> clearReply());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_contact_info) {
            showContactInfo();
            return true;
        } else if (item.getItemId() == R.id.action_add_contact) {
            addContactToPhoneBook();
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
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

    private void addContactToPhoneBook() {
        final String id = selectedContact.getId();
        final String contactName = selectedContact.getName();

        Log.d("AddContact", "Raw ID: " + id + ", Name: " + contactName);

        // IMPORTANT: For @lid contacts the digits in the ID are NOT the phone number
        // (e.g. 30296699318376@lid is an opaque identifier). The server resolves the
        // real phone via WhatsApp's API, so always ask the server first.
        String userId = whatsAppUser.getUser() + "@c.us";
        String url = serverUrl + "/api/contact/" + userId + "/" + id;

        Toast.makeText(this, "Resolving phone number…", Toast.LENGTH_SHORT).show();

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    String phone = response.optString("phone", "");
                    Log.d("AddContact", "Server resolved phone: " + phone);
                    launchAddContactIntent(phone, contactName, id);
                },
                error -> {
                    Log.e("AddContact", "Server contact lookup failed: " + error);
                    // Fallback: only usable if the id is a real @c.us number.
                    launchAddContactIntent("", contactName, id);
                }
        );
        request.setTag(TAG);
        mQueue.add(request);
    }

    /** Lanza el intent de "Añadir contacto". Usa el teléfono resuelto por el servidor;
     *  si está vacío, intenta extraerlo del id solo cuando es @c.us (número real). */
    private void launchAddContactIntent(String serverPhone, String contactName, String id) {
        String phoneNumber = serverPhone;

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            // Solo @c.us contiene el número real; @lid NO.
            if (id.contains("@c.us")) {
                phoneNumber = "+" + id.replace("@c.us", "").replaceAll("[^0-9]", "");
            }
        }

        if (phoneNumber == null || phoneNumber.isEmpty() || phoneNumber.equals("+")) {
            Toast.makeText(this, "Could not resolve a phone number for this contact", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d("AddContact", "Final phone for intent: " + phoneNumber);

        Intent intent = new Intent(android.content.Intent.ACTION_INSERT);
        intent.setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);
        intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, phoneNumber);
        intent.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, contactName);
        intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE_TYPE,
                android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);

        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Contacts app not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void showContactProfileDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_contact_profile, null);
        CircleImageView profilePic    = dialogView.findViewById(R.id.dialog_profile_pic);
        TextView phoneView            = dialogView.findViewById(R.id.dialog_contact_phone);
        TextView aboutView            = dialogView.findViewById(R.id.dialog_contact_about);

        // Mostrar número preliminar mientras carga
        String rawId = selectedContact.getId();
        if (rawId.contains("@c.us")) phoneView.setText("+" + rawId.replace("@c.us", ""));
        else phoneView.setText(rawId.replaceAll("@.*", ""));

        Picasso.with(this)
                .load(serverUrl + "/api/profilepic/" + whatsAppUser.getUser() + "@c.us/" + rawId)
                .placeholder(R.drawable.profile_image)
                .error(R.drawable.profile_image)
                .into(profilePic);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(selectedContact.getName())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .show();

        // Cargar info real del contacto (teléfono + estado) en segundo plano
        String url = serverUrl + "/api/contact/" + whatsAppUser.getUser() + "@c.us/" + rawId;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    if (!dialog.isShowing() || isFinishing()) return;
                    String phone = response.optString("phone", "");
                    String about = response.optString("about", "");
                    if (!phone.isEmpty()) phoneView.setText(phone);
                    if (!about.isEmpty()) {
                        aboutView.setText(about);
                        aboutView.setVisibility(View.VISIBLE);
                    }
                },
                error -> { /* silencioso — ya tenemos el número preliminar */ }
        );
        req.setTag(TAG);
        mQueue.add(req);
    }

    private void sendMessage() {
        final String messageText = txtMessage.getText().toString().trim();
        txtMessage.setText("");

        // Capturar (y limpiar) el estado de respuesta antes de enviar.
        final Message reply = replyingTo;
        final String quotedMessageId = (reply != null) ? reply.getWaId() : null;
        clearReply();

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
        if (reply != null) {
            optimistic.setQuotedMessage(reply.getMessage());
            optimistic.setQuotedAuthor(reply.getSenderName());
        }
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
                if (quotedMessageId != null && !quotedMessageId.isEmpty()) {
                    jsonRequest.put("quotedMessageId", quotedMessageId);
                }
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

    /** Menú al mantener pulsado un mensaje: Responder, Reaccionar o Copiar. */
    private void showMessageActions(final Message message) {
        CharSequence[] options = new CharSequence[]{"Reply", "React", "Copy"};
        new AlertDialog.Builder(this)
                .setItems(options, (dialog, which) -> {
                    if (which == 0)      startReply(message);
                    else if (which == 1) showReactionPicker(message);
                    else if (which == 2) copyMessageText(message);
                })
                .show();
    }

    private void copyMessageText(Message message) {
        String text = message.getMessage();
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    /** Activa el modo respuesta: muestra la barra de cita sobre el campo de texto. */
    private void startReply(Message message) {
        replyingTo = message;
        String author = message.getSenderName();
        boolean mine = message.getSender() != null
                && message.getSender().replace("@c.us", "").equals(whatsAppUser.getUser());
        if (mine || author == null || author.isEmpty()) author = mine ? "You" : "";
        String preview = message.getMessage() != null ? message.getMessage() : "";
        String label = author.isEmpty() ? preview : author + ": " + preview;
        replyPreviewText.setText("↪ " + label);
        replyBar.setVisibility(View.VISIBLE);
        txtMessage.requestFocus();
    }

    /** Cancela el modo respuesta y oculta la barra. */
    private void clearReply() {
        replyingTo = null;
        if (replyBar != null) replyBar.setVisibility(View.GONE);
    }

    /** Picker compacto de reacciones; al elegir, envía la reacción al servidor. */
    private void showReactionPicker(final Message message) {
        if (message.getWaId() == null || message.getWaId().isEmpty()) {
            Toast.makeText(this, "Can't react to this message yet", Toast.LENGTH_SHORT).show();
            return;
        }
        // Sin U+FE0F (ver nota en showEmojiPicker) para que se vean en BB10.
        // 😲 (U+1F632, 6.0) en vez de 😮 (U+1F62E, 6.1, que salía invisible en BB10).
        final String[] reactions = {"👍", "❤", "😂", "😲", "😢", "🙏", "🔥", "👏"};

        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(4);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        grid.setPadding(pad, pad, pad, pad);
        grid.setAdapter(new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, reactions) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextSize(28);
                tv.setPadding(0, pad, 0, pad);
                return tv;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("React")
                .setView(grid)
                .setNegativeButton("Close", null)
                .create();

        grid.setOnItemClickListener((parent, v, position, id) -> {
            sendReaction(message, reactions[position]);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Envía una reacción (emoji) a un mensaje vía POST /api/react. */
    private void sendReaction(final Message message, final String emoji) {
        // Reflejar de inmediato en la UI (optimista).
        message.setReaction(emoji);
        adapter.notifyDataSetChanged();

        try {
            String url = serverUrl + "/api/react";
            JSONObject body = new JSONObject();
            body.put("sender", whatsAppUser.getUser());
            body.put("waId", message.getWaId());
            body.put("emoji", emoji);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, url, body,
                    response -> Log.i(TAG, "react OK"),
                    error -> Toast.makeText(ChatActivity.this, "Could not send reaction", Toast.LENGTH_SHORT).show()
            );
            request.setTag(TAG);
            mQueue.add(request);
        } catch (JSONException ex) {
            Log.e(TAG, "react json error: " + ex.getMessage());
        }
    }

    private void showFullscreenImage(String imageUrl) {
        // El diálogo descarga la imagen él mismo, escalada al tamaño de pantalla
        // (evita OOM en el Q20 y no pasa un Bitmap grande por el Bundle del fragment).
        ImageViewerDialogFragment dialog = ImageViewerDialogFragment.newInstance(imageUrl);
        dialog.show(getSupportFragmentManager(), "image_viewer");
    }

    private void playVideo(String videoUrl) {
        String ext = "mp4";
        int dot = videoUrl.lastIndexOf('.');
        if (dot >= 0) ext = videoUrl.substring(dot + 1).toLowerCase();
        final String mimeType = ext.equals("3gp") ? "video/3gpp"
                : ext.equals("mkv") ? "video/x-matroska" : "video/mp4";
        final String finalExt = ext;

        android.app.ProgressDialog dlg = new android.app.ProgressDialog(this);
        dlg.setMessage("Loading video…");
        dlg.setCancelable(true);
        dlg.show();

        new Thread(() -> {
            java.io.File cacheFile = null;
            String downloadError = null;
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(videoUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.connect();
                int code = conn.getResponseCode();
                if (code != java.net.HttpURLConnection.HTTP_OK) {
                    downloadError = "HTTP " + code;
                } else {
                    cacheFile = new java.io.File(getFilesDir(), "video_cache." + finalExt);
                    try (java.io.InputStream in = conn.getInputStream();
                         java.io.FileOutputStream out = new java.io.FileOutputStream(cacheFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                    }
                }
            } catch (Exception e) {
                downloadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }

            final java.io.File finalFile = cacheFile;
            final String finalError = downloadError;
            runOnUiThread(() -> {
                dlg.dismiss();
                if (finalError != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Video error")
                            .setMessage(finalError + "\n\nTrying to open URL directly…")
                            .setPositiveButton("Open in browser", (d, w) -> {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        android.net.Uri.parse(videoUrl)));
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(android.net.Uri.fromFile(finalFile), mimeType);
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException e) {
                    new AlertDialog.Builder(this)
                            .setTitle("No video player")
                            .setMessage("No app found for " + mimeType + ". Open in browser instead?")
                            .setPositiveButton("Open", (d, w) -> startActivity(
                                    new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }).start();
    }

    /** Picker de emojis: rejilla de emojis comunes; al tocar uno se inserta en el
     *  campo de texto y se envía como texto normal (WhatsApp los trata como texto). */
    private void showEmojiPicker() {
        // Set curado para el runtime viejo de BB10 (≈Android 4.3): solo emoji del
        // bloque clásico (Unicode 6.0) que la fuente del sistema sí dibuja, y
        // SIN el selector de variación U+FE0F (que en Android viejo provoca "tofu"
        // en símbolos como ❤ ✌ ✈ ☀ ⏰ ⭐ ✅ ❌; con el codepoint base sí se ven).
        // OJO: se omiten los emoji de Unicode 6.1 (😀 U+1F600 y 😴 U+1F634), porque
        // la fuente de BB10 (≈Android 4.3, base Unicode 6.0) NO los trae y salían
        // invisibles/tofu. Aquí todos son ≤ 6.0, que la fuente sí dibuja.
        final String[] emojis = {
                "😁","😂","😃","😄","😅","😆","😉","😊","😋","😍","😘","😚",
                "😜","😝","😎","😏","😌","😔","😪","😷","😢","😭","😡","😱",
                "👍","👎","👌","✌","☝","👏","🙌","🙏","💪","👋","✊","✋",
                "❤","💛","💚","💙","💜","💔","💕","💖","💗","💘","💝","💋",
                "✨","⭐","🔥","⚡","☀","🌙","☕","🍺","🍷","🍕","🍔","🍫",
                "⚽","🏀","🚗","✈","🏠","📞","💬","🎉","🎂","🎁","💰","✅"
        };

        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(6);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        grid.setPadding(pad, pad, pad, pad);
        grid.setAdapter(new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, emojis) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextSize(26);
                tv.setPadding(0, pad, 0, pad);
                return tv;
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Emoji")
                .setView(grid)
                .setNegativeButton("Close", null)
                .create();

        grid.setOnItemClickListener((parent, v, position, id) -> {
            if (txtMessage != null) {
                txtMessage.append(emojis[position]);
            }
        });

        dialog.show();
    }

    private void showContactInfo() {
        String contactId = selectedContact.getId();
        String userId = whatsAppUser.getUser() + "@c.us";
        String url = serverUrl + "/api/contact/" + userId + "/" + contactId;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        String name = response.optString("name", selectedContact.getName());
                        String phone = response.optString("phone", contactId);
                        String about = response.optString("about", "No status");

                        AlertDialog.Builder builder = new AlertDialog.Builder(ChatActivity.this);
                        builder.setTitle(name)
                                .setMessage("Phone: " + phone + "\nStatus: " + about)
                                .setPositiveButton("Close", null)
                                .show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing contact info: " + e.getMessage());
                        Toast.makeText(ChatActivity.this, "Error loading contact info", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e(TAG, "Error fetching contact info: " + error.getMessage());
                    Toast.makeText(ChatActivity.this, "Could not load contact info", Toast.LENGTH_SHORT).show();
                }
        );
        request.setTag(TAG);
        mQueue.add(request);
    }

    private void retrieveAndDisplayMessages() {
        // Reset unread count when viewing chat
        String markReadUrl = serverUrl + "/api/mark-read/" + whatsAppUser.getUser() + "@c.us/" + selectedContact.getId();
        mQueue.add(new com.android.volley.toolbox.JsonObjectRequest(
                com.android.volley.Request.Method.POST, markReadUrl, null,
                response -> { /* silent */ },
                error -> { /* silent */ }
        ));

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
                                    // Las reacciones no cambian el id del mensaje más nuevo,
                                    // así que detectamos su cambio con una firma aparte.
                                    String reactionSig = buildReactionSignature(messagesList);
                                    boolean newMessage      = !lastMessageId.equals(newest.getId());
                                    boolean reactionsChanged = !lastReactionSignature.equals(reactionSig);

                                    if (newMessage || reactionsChanged) {
                                        lastMessageId = newest.getId();
                                        lastReactionSignature = reactionSig;
                                        Collections.reverse(messagesList);
                                        adapter.update(messagesList);
                                        // Solo auto-scroll al fondo cuando llega un mensaje nuevo
                                        // (no al cambiar una reacción, para no dar saltos).
                                        if (newMessage) {
                                            mListView.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mListView.setSelection(adapter.getCount() - 1);
                                                }
                                            });
                                        }
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

    /** Firma ligera del estado de reacciones (id+emoji por mensaje) para detectar
     *  cambios de reacción aunque no llegue ningún mensaje nuevo. */
    private String buildReactionSignature(ArrayList<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String r = m.getReaction();
            if (r != null && !r.isEmpty()) {
                sb.append(m.getId()).append('=').append(r).append(';');
            }
        }
        return sb.toString();
    }
}
