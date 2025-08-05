package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Created by hunte on 7/11/2025.
 */
public class MessagesListAdapter extends ArrayAdapter<Message> {
    private static final String TAG = "MessagesListAdapter";
    private Context context;
    private int resource;
    private WhatsAppUser whatsAppUser;
    private String serverUrl;

    public MessagesListAdapter(Context context, int resource, ArrayList<Message> objects, WhatsAppUser whatsAppUser, String serverUrl) {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.whatsAppUser = whatsAppUser;
        this.serverUrl = serverUrl;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        String _id = getItem(position).getId();
        String sender = getItem(position).getSender().replace("@c.us","") ;// to match with logged in user's mobile / sender
        String receiver = getItem(position).getReceiver();
        String message = getItem(position).getMessage();
        int status = getItem(position).getStatus();
        String senderName = getItem(position).getSenderName();
        String createdAt = getItem(position).getCreatedAt();
        String chatType = getItem(position).getChatType();



        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);


        TextView senderMessageText = (TextView) view.findViewById(R.id.sender_message_text);
        TextView receiverMessageText = (TextView) view.findViewById(R.id.receiver_message_text);
        ImageView senderMessageImage = (ImageView) view.findViewById(R.id.sender_message_image);
        ImageView receiverMessageImage = (ImageView) view.findViewById(R.id.receiver_message_image);


        senderMessageText.setVisibility(View.GONE);
        receiverMessageText.setVisibility(View.GONE);
        senderMessageImage.setVisibility(View.GONE);
        receiverMessageImage.setVisibility(View.GONE);

        // check if the current message is sent by the sender (i.e. the logged in user)
        if(chatType.equals("chat")){
            if(sender.equals(whatsAppUser.getUser()) || sender.equalsIgnoreCase("Me")){
                senderMessageText.setVisibility(View.VISIBLE);
                senderMessageText.setBackgroundResource(R.drawable.sender_messages);
                if(sender.contains("@g.us"))
                    senderMessageText.setText(whatsAppUser.getPushname() + "\n" + message + "\n" + createdAt);
                else
                    senderMessageText.setText(message + "\n" + createdAt);
            }
            else {
                receiverMessageText.setVisibility(View.VISIBLE);
                receiverMessageText.setBackgroundResource(R.drawable.receiver_messages);
                if(sender.contains("@g.us"))
                    receiverMessageText.setText(senderName + "\n" + message + "\n" + createdAt);
                else
                    receiverMessageText.setText(message + "\n" + createdAt);
            }
        }
        else if(chatType.equals("image")){
            if(sender.equals(whatsAppUser.getUser()) || sender.equalsIgnoreCase("Me")){
                senderMessageImage.setVisibility(View.VISIBLE);


                Picasso.with(context)  //Here, this is context.
                        .load(serverUrl + "/api/mediafile/" + _id + ".jpg")  //Url of the image to load.
                        .into(senderMessageImage);

            }
            else {
                receiverMessageImage.setVisibility(View.VISIBLE);
                Picasso.with(context)  //Here, this is context.
                        .load(serverUrl + "/api/mediafile/" + _id + ".jpg")  //Url of the image to load.
                        .into(receiverMessageImage);
            }
        }

        return view;

    }
}
