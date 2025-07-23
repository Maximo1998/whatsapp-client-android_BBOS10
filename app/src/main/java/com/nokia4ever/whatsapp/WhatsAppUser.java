package com.nokia4ever.whatsapp;

import java.io.Serializable;

/**
 * Created by saudi on 18/07/2025.
 */

public class WhatsAppUser implements Serializable {
    private String pushname;
    private String user;
    private String platform;

    public String getPushname() {
        return pushname;
    }

    public void setPushname(String pushname) {
        this.pushname = pushname;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
