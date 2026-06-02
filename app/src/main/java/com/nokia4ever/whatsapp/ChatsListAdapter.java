package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatsListAdapter extends ArrayAdapter<Message> {
    private static final String TAG = "ChatsListAdapter";
    private Context context;
    private int resource;
    private String serverUrl;
    private String userNumber; // logged-in user number without @c.us

    public ChatsListAdapter(Context context, int resource, ArrayList<Message> objects,
                            String serverUrl, String userNumber) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.serverUrl = serverUrl;
        this.userNumber = userNumber;
    }

    private String formatTimestamp(String utc) {
        if (utc == null || utc.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(utc);
            SimpleDateFormat out = new SimpleDateFormat("HH:mm", Locale.getDefault());
            out.setTimeZone(TimeZone.getDefault());
            return out.format(d);
        } catch (Exception e) {
            return utc;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message item = getItem(position);
        String sender     = item.getSender();
        String message    = item.getMessage();
        String senderName = item.getSenderName();
        String createdAt  = formatTimestamp(item.getCreatedAt());

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);

        TextView lblSenderName = view.findViewById(R.id.user_profile_name);
        TextView lblMessage    = view.findViewById(R.id.user_message);
        TextView lblDate       = view.findViewById(R.id.message_date);
        TextView unreadBadge   = view.findViewById(R.id.unread_badge);
        CircleImageView profilePic = view.findViewById(R.id.users_profile_image);

        lblSenderName.setText(senderName);
        lblMessage.setText(message);
        lblDate.setText(createdAt);

        // Show unread badge if there are unread messages
        int unreadCount = item.getUnreadCount();
        if (unreadCount > 0) {
            unreadBadge.setVisibility(android.view.View.VISIBLE);
            unreadBadge.setText(String.valueOf(unreadCount > 99 ? "99+" : unreadCount));
        } else {
            unreadBadge.setVisibility(android.view.View.GONE);
        }

        // Cargar foto de perfil del contacto
        if (serverUrl != null && !serverUrl.isEmpty() && sender != null && !sender.isEmpty()) {
            String picUrl = serverUrl + "/api/profilepic/" + userNumber + "@c.us/" + sender;
            Picasso.with(context)
                    .load(picUrl)
                    .placeholder(R.drawable.profile_image)
                    .error(R.drawable.profile_image)
                    .into(profilePic);
        }

        return view;
    }
}
