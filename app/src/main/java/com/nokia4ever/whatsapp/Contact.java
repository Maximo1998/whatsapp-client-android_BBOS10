package com.nokia4ever.whatsapp;

import java.io.Serializable;

/**
 * Created by saudi on 21/07/2025.
 */

public class Contact implements Serializable {
    private String id;
    private String name;

    public Contact() {
    }

    public Contact(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
