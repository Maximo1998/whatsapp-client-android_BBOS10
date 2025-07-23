package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hunte on 7/11/2025.
 */
public class ChatsListAdapter extends ArrayAdapter<Message> {
    private static final String TAG = "ChatsListAdapter";
    private Context context;
    private int resource;

    public ChatsListAdapter(Context context, int resource, ArrayList<Message> objects) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String _id = getItem(position).getId();
        String sender = getItem(position).getSender();
        String receiver = getItem(position).getReceiver();
        String message = getItem(position).getMessage();
        int status = getItem(position).getStatus();
        String senderName = getItem(position).getSenderName();
        String createdAt = getItem(position).getCreatedAt();
        String chatType = getItem(position).getChatType();



        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);
        TextView lblSenderName = (TextView) view.findViewById(R.id.user_profile_name);
        TextView lblMessage = (TextView) view.findViewById(R.id.user_message);
        TextView lblDate = (TextView) view.findViewById(R.id.message_date);

        lblSenderName.setText(senderName);
        lblMessage.setText(message);
        lblDate.setText(createdAt);

        return view;

    }
}
