<h5>[English] · <a href="./README.tr.md">[Türkçe]</a></h5>

# NFC Share

Hold your phone against another phone to send a **link**, a **WiFi network** (with its password) or a **contact** over NFC. No app is needed on the receiving phone.

NFC Share makes your phone act like an NFC tag. The other phone reads it the same way it would read a sticker or a card: a link opens in its app (YouTube links open in YouTube, Maps links in Maps), a WiFi network shows a "connect" prompt, and a contact opens the "add contact" screen.

## Download

**[⬇ Download the latest version](https://github.com/hubble658/nfc-share/releases/latest)**

1. On the release page, download the `NFC-Share-x.y.z.apk` file under **Assets**.
2. Open the downloaded file. If Android asks, allow installing apps from this source (usually your browser or file manager).
3. Tap **Install**.

Requires Android 5.0 or newer and a phone with NFC.

## Features

- **Links and text.** Paste anything and the type is detected automatically: YouTube, Instagram, Google Maps or any other URL. A Maps link is sent as a real location.
- **WiFi.** Pick the network you're connected to and share it with its password. The other phone joins the network in one tap, like scanning a WiFi QR code.
- **Contacts.** Pick someone from your contacts; their name, phone number and email are sent as a contact card.
- **Everything you share is saved.** Tap a saved item to share it again, tap its trash icon to delete it, or hold it to edit or copy it.
- **Time limit.** Sharing stops by itself after 20 s, 1 min or 5 min, or you can choose no limit. A countdown and a read counter show what's happening.
- **Home-screen widgets.** One shares a chosen saved item with one tap; the other shares whatever you last copied.
- **Share sheet.** Choose NFC Share from any app's "Share" menu (YouTube, Chrome, Instagram…) and it starts sharing right away.
- **Receiving text.** Android shows plain text from NFC as read-only. If the receiving phone also has NFC Share, the text opens with a "Copy Text" button.
- **English and Turkish.**

## Privacy

**NFC Share does not collect any data.** The app has no internet permission, so it can't send anything anywhere. Everything you save (links, WiFi passwords, contacts) stays on your phone. Data only leaves the phone over NFC, when you hold it against another device.

### Why each permission is requested

| Permission | Why |
|---|---|
| Location | Android only reveals the name of the connected WiFi network to apps with location permission. NFC Share never reads or stores your location. |
| Contacts | Only used when you tap "Pick From Contacts", to fill in that contact's name, number and email. |
| NFC | To send what you picked to the phone you hold yours against. |
| Shizuku (optional) | See below. |

## Importing all saved WiFi passwords (optional)

Android doesn't let normal apps read saved WiFi passwords. The first time you share a network, you type its password once and NFC Share remembers it.

To import every saved network at once, you can use [Shizuku](https://shizuku.rikka.app/). Shizuku runs with the same rights as ADB (USB debugging) and needs no root. Start it once over wireless debugging, then go to **Settings → Bulk import with Shizuku** in NFC Share. After you have imported once, you can remove Shizuku. Requires Android 11 or newer.

## Good to know

- **Both phones must be unlocked.** For security, Android doesn't read NFC tags while the phone is locked.
- **NFC must be on.** The app warns you and links to the NFC settings when it's off.
- **iPhones** can read links. WiFi networks and contacts only work when the receiving phone is an Android phone. (Not tested on iPhone yet.)
- **"Which app?" prompt on some Samsung phones.** Some recent Samsung models have a built-in service that answers NFC the same way. If Android asks which app to use, pick NFC Share. While NFC Share is open on screen, it is chosen automatically.

## Building

You need JDK 17 and the Android SDK (platform 35). Android Studio is not required.

```bash
./gradlew :app:assemblePhoneDebug      # app/build/outputs/apk/phone/debug/
./gradlew :app:assemblePhoneRelease    # needs signing/signing.properties (see below)
```

Release builds are signed using `signing/signing.properties`. This file is not in the repository:

```properties
app.keystore.file=signing/your-key.jks
app.keystore.password=...
app.key.alias=...
app.key.password=...
```

## Credits and license

NFC tag emulation is done by [ndef-emulator](https://github.com/LuigiVampa92/ndef-emulator) by **LuigiVampa92**. The modified copy of that library is in [`ndefemulation/`](./ndefemulation), together with its original documentation. See [NOTICE](./NOTICE) for what was changed.

Licensed under the [Apache License 2.0](./LICENSE.md).
