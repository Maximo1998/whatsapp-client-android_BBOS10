package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.hdodenhof.circleimageview.CircleImageView;

public class MessagesListAdapter extends ArrayAdapter<Message> {
    private static final String TAG = "MessagesListAdapter";
    private Context context;
    private int resource;
    private WhatsAppUser whatsAppUser;
    private String serverUrl;
    private OnImageClickListener imageClickListener;

    public interface OnImageClickListener {
        void onImageClick(String imageUrl);
    }

    public void setImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }

    public MessagesListAdapter(Context context, int resource, ArrayList<Message> objects,
                               WhatsAppUser whatsAppUser, String serverUrl) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.whatsAppUser = whatsAppUser;
        this.serverUrl = serverUrl;
    }

    /** El servidor ya envía la hora en su zona horaria (fuente de verdad).
     *  Mostramos "HH:mm" tal cual, sin reconvertir zonas (el reloj del
     *  dispositivo BB10 aplica mal el horario de verano). */
    private String formatTimestamp(String serverTime) {
        if (serverTime == null || serverTime.isEmpty()) return "";
        int sp = serverTime.indexOf(' ');
        if (sp >= 0 && serverTime.length() >= sp + 6) {
            return serverTime.substring(sp + 1, sp + 6);
        }
        return serverTime;
    }

    /** Reemplaza el contenido del adapter sin recrearlo (evita perder referencia en la Activity). */
    public void update(ArrayList<Message> messages) {
        clear();
        addAll(messages);
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message msg     = getItem(position);
        String sender   = msg.getSender().replace("@c.us", "");
        String message  = msg.getMessage();
        String senderName = msg.getSenderName();
        String createdAt  = formatTimestamp(msg.getCreatedAt());
        String chatType   = msg.getChatType() != null ? msg.getChatType() : "chat";
        String _id        = msg.getId();
        String mediaFilename = msg.getMediaFilename();

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);

        TextView senderMessageText   = view.findViewById(R.id.sender_message_text);
        TextView receiverMessageText = view.findViewById(R.id.receiver_message_text);
        ImageView senderMessageImage   = view.findViewById(R.id.sender_message_image);
        ImageView receiverMessageImage = view.findViewById(R.id.receiver_message_image);

        senderMessageText.setVisibility(View.GONE);
        receiverMessageText.setVisibility(View.GONE);
        senderMessageImage.setVisibility(View.GONE);
        receiverMessageImage.setVisibility(View.GONE);

        boolean isMine = sender.equals(whatsAppUser.getUser())
                || sender.equalsIgnoreCase("Me")
                || sender.equalsIgnoreCase("me");

        if (chatType.equals("chat") || chatType.equals("audio") || chatType.equals("ptt")) {
            if (isMine) {
                senderMessageText.setVisibility(View.VISIBLE);
                senderMessageText.setBackgroundResource(R.drawable.sender_messages);
                senderMessageText.setText(message + "\n" + createdAt);
            } else {
                receiverMessageText.setVisibility(View.VISIBLE);
                receiverMessageText.setBackgroundResource(R.drawable.receiver_messages);
                String label = sender.contains("@g.us") ? senderName + "\n" : "";
                receiverMessageText.setText(label + message + "\n" + createdAt);
            }
        } else if (chatType.equals("image")) {
            String filename = (mediaFilename != null && !mediaFilename.isEmpty())
                    ? mediaFilename : _id + ".jpg";
            String imageUrl = serverUrl + "/api/mediafile/" + filename;

            if (isMine) {
                senderMessageImage.setVisibility(View.VISIBLE);
                Picasso.with(context).load(imageUrl)
                        .placeholder(R.drawable.profile_image).into(senderMessageImage);
                senderMessageImage.setOnClickListener(v -> {
                    if (imageClickListener != null) {
                        imageClickListener.onImageClick(imageUrl);
                    }
                });
            } else {
                receiverMessageImage.setVisibility(View.VISIBLE);
                Picasso.with(context).load(imageUrl)
                        .placeholder(R.drawable.profile_image).into(receiverMessageImage);
                receiverMessageImage.setOnClickListener(v -> {
                    if (imageClickListener != null) {
                        imageClickListener.onImageClick(imageUrl);
                    }
                });
            }
        }

        return view;
    }
}
