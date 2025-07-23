package com.nokia4ever.whatsapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by hunte on 7/11/2025.
 */
public class ContactsListAdapter extends ArrayAdapter<Contact> {
    private static final String TAG = "ContactsListAdapter";
    private Context context;
    private int resource;
    private ArrayList<Contact> items;

    public ContactsListAdapter(Context context, int resource, ArrayList<Contact> items) {
        super(context, resource, items);
        this.context = context;
        this.resource = resource;
        this.items = items;
    }

    public void update(ArrayList<Contact> results){
        items = new ArrayList<>();
        items.addAll(results);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        //String id = getItem(position).getId().replace("@c.us","").replace("@g.us","");
        //String name = getItem(position).getName();

        String id = items.get(position).getId().replace("@c.us","").replace("@g.us","");
        String name = items.get(position).getName();

        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(resource, parent, false);
        TextView lblContactId = (TextView) view.findViewById(R.id.contact_id);
        TextView lblContactName = (TextView) view.findViewById(R.id.contact_name);

        lblContactId.setText(id);
        lblContactName.setText(name);

        return view;

    }
}
