package com.nokia4ever.whatsapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;

/**
 * Created by saudi on 21/07/2025.
 */

public class ContactsAdapter extends BaseAdapter implements Filterable {

    Context context;
    ArrayList<Contact> contacts;
    LayoutInflater inflator;
    SearchFilter filter;
    ArrayList<Contact> filteredList;

    WhatsAppUser whatsAppUser;
    String serverUrl;


    public ContactsAdapter(Context context, ArrayList<Contact> contacts, WhatsAppUser whatsAppUser, String serverUrl) {
        this.context = context;
        this.contacts = contacts;
        this.filteredList = contacts;

        this.whatsAppUser = whatsAppUser;
        this.serverUrl = serverUrl;
    }

    @Override
    public int getCount() {
        return contacts.size();
    }

    @Override
    public Object getItem(int i) {
        return contacts.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(final int i, View view, ViewGroup viewGroup) {
        if(inflator == null){
            inflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }
        if(view == null){
            view = inflator.inflate(R.layout.custom_contacts_layout, null);
        }

        ContactsViewHolder viewHolder = new ContactsViewHolder(view);
        viewHolder.lblContactId.setText(contacts.get(i).getId().replace("@c.us",""));
        viewHolder.lblContactName.setText(contacts.get(i).getName());
        viewHolder.SetItemClickListener(new ItemClickListener() {
            @Override
            public void OnItemClicked(View view) {
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("Contact", contacts.get(i));
                intent.putExtra("ServerUrl", serverUrl);
                intent.putExtra("WhatsAppUser", whatsAppUser);

                context.startActivity(intent);
            }
        });

        return view;
    }

    @Override
    public Filter getFilter() {

        if(filter == null){
            filter = new SearchFilter(filteredList, this);
        }

        return filter;
    }
}
