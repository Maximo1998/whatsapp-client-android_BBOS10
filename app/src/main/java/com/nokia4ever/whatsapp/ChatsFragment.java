package com.nokia4ever.whatsapp;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import androidx.fragment.app.Fragment;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import static android.content.Context.MODE_PRIVATE;


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
    private static final int POLL_INTERVAL_MS = 5000;
    private Boolean isTimerEnabled = false;
    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private String lastChatId = "";
    private String lastSignature = ""; // detecta cambios en cualquier chat (mensaje o no-leídos)
    private Contact selectedContact;
    private SharedPreferences sharedPreferences;

    private ChatService chatService;
    private boolean isServiceBound;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private ServiceConnection serviceConnection;

    private Intent serviceIntent;

    public ChatsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        sharedPreferences = getContext().getSharedPreferences("UserPreferences", MODE_PRIVATE);
        serverUrl = sharedPreferences.getString("server_url","");

        whatsAppUser = new WhatsAppUser(
                sharedPreferences.getString("pushname",""),
                sharedPreferences.getString("user",""),
                sharedPreferences.getString("platform","")
        );



        mGroupFragmentView = inflater.inflate(R.layout.fragment_chats, container, false);

        initializeFields();


        serviceIntent = new Intent(getContext(), ChatService.class);
//        serviceIntent.putExtra("ServerUrl",serverUrl);
//        serviceIntent.putExtra("WhatsAppUser", whatsAppUser);
        getContext().startService(serviceIntent);
        bindService();

        /*
        Intent intent = new Intent(getContext(), MyBroadcastReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(getContext(),100,intent,PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(ALARM_SERVICE);

        long intervalMillis = 5 * 1000; // 5 seconds in milliseconds
        long triggerAtMillis = System.currentTimeMillis() + intervalMillis; // Start after 5 seconds

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP, // Or AlarmManager.RTC if you don't need to wake the device
                triggerAtMillis,
                intervalMillis,
                pendingIntent
        );
        */

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Message selectedChat = (Message) adapterView.getItemAtPosition(position);
                selectedContact = new Contact(selectedChat.getSender(), selectedChat.getSenderName());
                chatService.setSelectedContact(selectedContact);

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("contact_id", selectedContact.getId());
                editor.putString("contact_name", selectedContact.getName());
                editor.apply();

                Intent intent = new Intent(getContext(), ChatActivity.class);
//                intent.putExtra("Contact", selectedContact);
//                intent.putExtra("ServerUrl", serverUrl);
//                intent.putExtra("WhatsAppUser", whatsAppUser);

                startActivity(intent);
            }
        });

        return mGroupFragmentView;
    }

    @Override
    public void onStart() {
        super.onStart();
        selectedContact = new Contact("", "");
    }

    @Override
    public void onResume() {
        super.onResume();
        isTimerEnabled = true;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTimerEnabled && isAdded()) {
                    retrieveAndDisplayChats();
                    timerHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        isTimerEnabled = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacksAndMessages(null);
        if (isServiceBound) unbindService1();
    }

    private void initializeFields() {
        mListView = mGroupFragmentView.findViewById(R.id.list_view);


        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.layout_loading_dialog);
        progressDialog = builder.create();
    }

    private void showNotification(String sender, String message){
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


        NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        Intent repeatingIntent = new Intent(getContext(), MainActivity.class);
        repeatingIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(),100,repeatingIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext())
                .setContentIntent(pendingIntent)
                .setSmallIcon(android.R.drawable.arrow_up_float)
                .setContentTitle(sender)
                .setContentText(message)
                .setAutoCancel(true);

        notificationManager.notify(100, builder.build());


    }


    private void unbindService1() {
        if(isServiceBound){
            getContext().unbindService(serviceConnection);
            isServiceBound=false;
        }
    }

    private void bindService(){
        if(serviceConnection==null){
            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    ChatService.MyServiceBinder myServiceBinder=(ChatService.MyServiceBinder)iBinder;
                    chatService=myServiceBinder.getService();
                    isServiceBound=true;
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    isServiceBound=false;
                }
            };
        }

        getContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

    }

    private void retrieveAndDisplayChats() {
        try {

            if(isServiceBound){
                chatsResponse = chatService.getChats();

                if(chatsResponse != null && chatsResponse.getChats().size() > 0 && isAdded()){
                    // Reconstruir cuando CAMBIE cualquier chat (mensaje nuevo o contador
                    // de no-leídos). Antes solo se refrescaba si cambiaba el primer chat,
                    // por eso la bolita de no-leídos no desaparecía al entrar al chat
                    // (mark-read pone unread=0 pero el primer chat seguía siendo el mismo).
                    String sig = buildSignature(chatsResponse.getChats());
                    if (!sig.equals(lastSignature)) {
                        lastSignature = sig;
                        lastChatId = chatsResponse.getChats().get(0).getId();
                        adapter = new ChatsListAdapter(getContext(), R.layout.custom_chats_layout,
                                chatsResponse.getChats(), serverUrl, whatsAppUser.getUser());
                        mListView.setAdapter(adapter);
                    }
                }
            }
            else {
                //Toast.makeText(getContext(), "Service is not bound", Toast.LENGTH_LONG).show();
            }

        } catch (Exception ex){
            Log.e(TAG, ex.getMessage(), ex);
        }

    } // retrieveAndDisplayChats

    /** Firma del estado de la lista: id + no-leídos + último mensaje de cada chat.
     *  Si cambia, hay que repintar (mensaje nuevo o bolita de no-leídos actualizada). */
    private String buildSignature(java.util.ArrayList<Message> chats) {
        StringBuilder sb = new StringBuilder();
        for (Message c : chats) {
            sb.append(c.getId()).append(':')
              .append(c.getUnreadCount()).append(':')
              .append(c.getMessage()).append('|');
        }
        return sb.toString();
    }

}
