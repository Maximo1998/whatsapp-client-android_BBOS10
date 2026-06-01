package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message item = getItem(position);
        String sender     = item.getSender();
        String message    = item.getMessage();
        String senderName = item.getSenderName();
        String createdAt  = item.getCreatedAt();

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);

        TextView lblSenderName = view.findViewById(R.id.user_profile_name);
        TextView lblMessage    = view.findViewById(R.id.user_message);
        TextView lblDate       = view.findViewById(R.id.message_date);
        CircleImageView profilePic = view.findViewById(R.id.users_profile_image);

        lblSenderName.setText(senderName);
        lblMessage.setText(message);
        lblDate.setText(createdAt);

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
