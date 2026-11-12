# Samsung Clock Alarm for Home Assistant

**Samsung Clock** alarms in Home Assistant, unaffected by Samsung **Modes & Routines**.

The Companion app's `next_alarm` sensor reports a single "next alarm clock" event from *any* app. Samsung Modes & Routines schedule such events too, so the sensor often shows a routine instead of your alarm. This project reads the alarm list from Samsung Clock itself and gives Home Assistant **all** your alarms.

It has two parts:

| Part | What it does |
|---|---|
| **Clock Bridge** (Android, `android/`) | When you open Clock, reads the alarm list from the screen through an accessibility service and sends it to Home Assistant. No persistent notification, no polling, no root, no ADB. |
| **Integration** (`custom_components/samsung_clock_alarm`) | Gives each phone its own webhook, stores the alarm list and computes the next alarm from the weekday schedule, even if Clock hasn't been opened since. |

Plus the **Before the first morning alarm** blueprint: run actions N minutes before the earliest morning alarm across several phones, e.g. turn on the heating.

## Installation

### 1. Integration (HACS)

1. HACS → ⋮ → **Custom repositories** → this repository's URL, type **Integration**.
2. Install **Samsung Clock Alarm** and restart Home Assistant.
3. **Settings → Devices & services → Add integration → Samsung Clock Alarm**. Enter a name for the phone and copy the webhook URL shown.
   Repeat for every phone.

> For phones to report while away from home, Home Assistant needs an external URL (Nabu Casa or your own domain). Without one, updates are delivered once the phone is back on the home network: the app retries on its own.

### 2. Clock Bridge (on every phone)

1. Install `ClockBridge-x.y.z.apk` from [Releases](https://github.com/sogoeslight/ha-samsung-clock/releases).
2. Android 13+ blocks accessibility for apps installed from a file. Open **Settings → Apps → Clock Bridge → ⋮ → Allow restricted settings**.
3. In the app, paste the webhook URL and tap **Save**.
4. **Open Accessibility settings** → **Installed apps → Clock Bridge: alarm reader** → turn on.
5. Tap **Open Clock**. The app status should show `Sent N alarms`.

The service receives events **only** from Samsung Clock (`com.sec.android.app.clockpackage`) and reads nothing in other apps.

### 3. Automation

Import the blueprint:

```
https://github.com/sogoeslight/ha-samsung-clock/blob/main/blueprints/automation/samsung_clock_alarm/before_first_alarm.yaml
```

Pick the sensors of all phones and how many minutes in advance to run. The "morning" window (04:00–11:00 by default) filters out daytime reminder alarms. The automation runs at most once a day.

## Sensor

`sensor.<phone>_next_alarm` (`timestamp`): the next enabled alarm, or `unknown` when no alarm is enabled.

| Attribute | |
|---|---|
| `alarm_name` | label of the next alarm |
| `upcoming` | all occurrences in the next 7 days `[{time, name}]` |
| `alarms` | the list as shown in Clock: `name`, `time`, `enabled`, `schedule` |
| `clock_next` | what Clock itself shows in its header, e.g. "Fri 13 Nov, 04:31" |
| `read_at` / `received_at` | when the list was read on the phone / received by Home Assistant |
| `unparsed` | alarms whose schedule could not be understood |

## Limitations

- **Updates when Clock is opened.** If an alarm is changed another way (voice, Galaxy Watch, a widget), Home Assistant learns about it the next time Clock is opened. Alarms that have rung are rolled over by Home Assistant on its own.
- **English phone UI.** Schedules ("Every Monday", "Friday, 13 November") are parsed in English. Anything unrecognised shows up in `unparsed`.
- **24-hour time format.**
- **Long lists.** If the alarms don't fit on one screen, the app sends only the visible ones and Home Assistant merges them with what it already knows. An alarm deleted at the bottom of a long list may linger until you scroll down to it.
- Tested on Galaxy S23, One UI with Android 16, Samsung Clock 12.6. A Samsung Clock update may change its screen layout.

## Development

```
pytest                   # tests: schedule parsing, the integration inside Home Assistant, blueprint template
android/build.sh         # build the APK (JDK 17 + Android SDK build-tools 37, platform 36)
```
