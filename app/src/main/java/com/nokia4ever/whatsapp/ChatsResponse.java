package com.nokia4ever.whatsapp;

import java.util.ArrayList;

/**
 * Created by hunte on 7/11/2025.
 */
public class ChatsResponse {
    private ArrayList<Message> chats;

    public ArrayList<Message> getChats() {
        return chats;
    }

    public void setChats(ArrayList<Message> chats) {
        this.chats = chats;
    }
}
