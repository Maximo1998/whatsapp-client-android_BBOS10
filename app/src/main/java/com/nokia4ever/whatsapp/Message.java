package com.nokia4ever.whatsapp;


import java.io.Serializable;

public class Message implements Serializable {
    private String _id;
    private String sender;
    private String receiver;
    private String message;
    private int status;
    private String senderName;
    private String updatedAt;
    private String createdAt;
    private String chatType;


    public Message() {
        super();
    }

    public Message(String _id, String sender, String receiver, String message,
                   int status, String senderName, String updatedAt, String createdAt, String chatType) {
        super();
        this._id = _id;
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
        this.status = status;
        this.senderName = senderName;
        this.setUpdatedAt(updatedAt);
        this.setCreatedAt(createdAt);
        this.setChatType(chatType);
    }

    public String getId() {
        return _id;
    }

    public void setId(String _id) {
        this._id = _id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public String getChatType() {
        return chatType;
    }





}

