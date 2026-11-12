# Installing Clock Bridge on a phone

About 5 minutes, no computer needed. Do this on every Samsung phone whose alarms Home Assistant should know about.

**You need:**
- a Samsung phone with the Samsung **Clock** app;
- phone language set to **English** and **24-hour** time (Settings → General management → Date and time);
- a **webhook URL** from whoever runs your Home Assistant (they get it from *Settings → Devices & services → Add integration → Samsung Clock Alarm*). Ask them to send it to you in a messenger so you can copy it.

## 1. Download the app

On the phone, open this link or scan the code with the camera:

**[⬇ Download Clock Bridge](https://github.com/sogoeslight/ha-samsung-clock/releases/latest/download/ClockBridge.apk)**

<img src="download-qr.png" width="180" alt="QR code: download Clock Bridge">

Chrome / Samsung Internet may warn that *this type of file can harm your device*: tap **Download anyway**.

## 2. Install it

1. Open the downloaded `ClockBridge.apk` (from the download notification or **My Files → Downloads**).
2. If the phone says *For your security, your phone is not allowed to install unknown apps from this source*: tap **Settings**, turn on **Allow from this source**, go back and tap **Install**.
3. If it says the app is **blocked by Auto Blocker**: open **Settings → Security and privacy → Auto Blocker**, turn it **off**, install, then turn it back **on**.
4. If **Play Protect** asks to scan the app, you can tap **Scan app** or **Install without scanning** — either is fine.

## 3. Paste the webhook URL

1. Open **Clock Bridge**.
2. Paste the webhook URL into **Webhook URL** and tap **Save**.

## 4. Turn on the alarm reader

1. In Clock Bridge, tap **Turn on in Accessibility**.
2. Tap **Installed apps** → **Clock Bridge: alarm reader**.
3. Because the app was installed from a file, Android shows **"Restricted setting"** the first time. That's normal. Tap **OK**, then:
   - go back to Clock Bridge and tap **Open App info**;
   - tap **⋮** in the top-right corner → **Allow restricted settings** → confirm with your PIN/fingerprint;
   - go back to Clock Bridge and tap **Turn on in Accessibility** again.
4. Turn **Clock Bridge: alarm reader** on and tap **Allow**.

The reader only receives events from the Samsung Clock app and reads nothing else.

## 5. Check it works

Back in Clock Bridge, tap **Open Clock**, then return to Clock Bridge. You should see:

- a **green dot** and **Working**;
- **Sent to Home Assistant just now**;
- your alarms listed under *Alarms in Samsung Clock*.

That's it. From now on, every time you open Clock and change an alarm, Home Assistant is updated automatically.

## Troubleshooting

| Clock Bridge shows | What to do |
|---|---|
| **Alarm reader is off** | Repeat step 4. Android may turn it off after an app update. |
| **Webhook URL is missing** | Paste the URL (step 3). |
| **Home Assistant doesn't know this webhook** | The URL is wrong or the phone was removed in Home Assistant. Ask for a new URL. |
| **Can't reach Home Assistant. Will retry when online.** | No internet, or Home Assistant isn't reachable from outside your home. It will send on its own later. |
| Alarms list is empty | Open Clock on the **Alarm** tab once. |

## Updating

Download and install again from the same link — it always points to the newest version. Afterwards, check the dot is green; if it says *Alarm reader is off*, repeat step 4.
