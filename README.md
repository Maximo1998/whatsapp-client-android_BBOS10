# Novel Messenger — BlackBerry Q20 / BB10 WhatsApp client
[![License: MIT](https://img.shields.io/badge/License-MIT-brightgreen.svg)](LICENSE) [![Platform](https://img.shields.io/badge/Platform-BlackBerry%2010-000000?logo=android&logoColor=white)]() [![Release](https://img.shields.io/github/v/release/Maximo1998/whatsapp-bb10-client)](https://github.com/Maximo1998/whatsapp-bb10-client/releases) [![Buy Me A Coffee](https://img.shields.io/badge/Buy%20me%20a%20coffee-%23FFDD00?logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/maxlakh1m)


An Android client (packaged for **BlackBerry 10 / Q20** via the Android runtime)
that talks to a self-hosted WhatsApp bridge server so a modified BlackBerry Q20 can
send and receive WhatsApp messages.

> **This is a fork of NovelProfessor's BB10 WhatsApp client**
> (project site: [nokia4ever.com](http://nokia4ever.com),
> YouTube: [@NovelProfessor](https://youtube.com/@NovelProfessor)).
> The login screen credits it as *"Bring back BB (a fork of Novel Professor)"*.
> It pairs with the [`whatsapp-server-improved`](https://github.com/Maximo1998/whatsapp-server-improved)
> fork of the server.

## What this fork adds over upstream

- **Working contact profile pictures** (served + cached by the improved server).
- **Send media from the device:** gallery images, full-resolution **camera** photos,
  and **recorded audio** (the send button is pinned so it's always reachable on the
  Q20's short square screen).
- **Unread-message badge:** a red dot with the count on each chat; it clears when you
  open the chat (and the status-bar notification is dismissed on entry too).
- **Correct message times:** the app shows the server-provided timestamp verbatim,
  so it isn't affected by the Q20's outdated daylight-saving rules.
- **Smarter notifications:** no notification for messages you sent from another device.
- **Add to phone book** uses the real phone number resolved by the server (WhatsApp
  multi-device `@lid` ids are not phone numbers).
- **Contact Info** dialog (name, phone, about) from the chat menu.
- **Stability hardening:** single Volley `RequestQueue` per screen, handler/lifecycle
  cleanup, and null-context guards in fragment callbacks to avoid random crashes.

## How it works

The app is a thin client. It calls the bridge server's REST API over HTTPS (through
a Cloudflare tunnel) to list chats/contacts, fetch and send messages, upload media,
and load profile pictures. A foreground `ChatService` polls for new messages and
posts notifications.

- **Package:** `com.nokia4ever.whatsapp`
- **Networking:** Volley
- **Images:** Picasso (with caching)
- **Min SDK 17 / target 36**, MultiDex enabled (for the old BB10 Android runtime)

## Configuration

On the login screen, enter your **phone number** and the **server URL**
(`https://<your-domain>`). The server must already be authenticated (QR scanned) for
that number. The app then logs in via `/api/login/<number>` and stores the session.

## Building

The APK is built by **GitHub Actions** on push to the `improved` branch (no local
Android toolchain required). Version is set in `app/build.gradle`
(`versionCode` / `versionName`).

## Credits

Original BB10 client by **Novel Professor** ([nokia4ever.com](http://nokia4ever.com)).
This fork is maintained for a personal self-hosted BlackBerry Q20 setup.
