package com.nokia4ever.whatsapp;

import android.widget.Filter;

import java.util.ArrayList;

/**
 * Created by saudi on 22/07/2025.
 */

public class SearchFilter extends Filter {

    ArrayList<Contact> contacts;
    ContactsAdapter adapter;

    public SearchFilter(ArrayList<Contact> contacts, ContactsAdapter adapter) {
        this.contacts = contacts;
        this.adapter = adapter;
    }

    @Override
    protected FilterResults performFiltering(CharSequence charSequence) {

        FilterResults filterResults = new FilterResults();
        if(charSequence != null && charSequence.length()>0){
            charSequence = charSequence.toString().toUpperCase();
            ArrayList<Contact> filteredData = new ArrayList<>();

            for(int i=0; i < contacts.size(); i++){
                if(contacts.get(i).getName().toUpperCase().contains(charSequence)){
                    filteredData.add(contacts.get(i));

                }
            }

            filterResults.count = filteredData.size();
            filterResults.values = filteredData;

        }
        else {
            filterResults.count = contacts.size();
            filterResults.values = contacts;
        }

        return filterResults;
    }

    @Override
    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
        adapter.contacts = (ArrayList<Contact>) filterResults.values;
        adapter.notifyDataSetChanged();
    }
}
