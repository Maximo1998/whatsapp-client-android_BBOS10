package com.nokia4ever.whatsapp;

import java.util.ArrayList;

/**
 * Created by hunte on 7/11/2025.
 */
public class ContactsResponse {
    private ArrayList<Contact> contacts;

    public ArrayList<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(ArrayList<Contact> contacts) {
        this.contacts = contacts;
    }
}
