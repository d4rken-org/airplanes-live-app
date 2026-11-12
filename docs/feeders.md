---
title: Feeders
description: "Add your airplanes.live feeder, follow its message rates and get notified when it goes offline."
permalink: /feeders
translation_key: feeders
lang: en
---
# Feeders
{: #feeders }

A feeder is a receiver, usually a Raspberry Pi with an antenna, that sends the aircraft signals it receives to airplanes.live. The **Feeder** tab follows your feeders and warns you when one goes offline. To set one up, see the [airplanes.live setup guide](https://airplanes.live/get-started/).

Adding a feeder here and [linking it](/getting-started#link-feeder) for Pro are separate steps. You can do either without the other.

## Find your feeder ID
{: #feeder-id }

Every feeder has an ID, a UUID like `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. On a feeder set up with the airplanes.live feed script, show it with:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

On the airplanes.live feeder image, the file is `/boot/airplanes-uuid`.

## Add a feeder
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="The screen for adding a feeder, with the feeder ID, label, IP address and position fields"></a>

Tap the **+** button on the Feeder tab. There are three ways to fill in **New feeder**:

- **Detect Local Feeder**: finds feeders on the same network as your phone and fills in the fields. If it finds several, pick one.
- **Scan QR Code**: scans a feeder QR code made by the app on another device, see [Share a feeder](#share).
- Type the **Feeder ID** yourself.

Optional fields:

- **Label**: a name for the feeder. Without one, the app uses the feeder's MLAT name from airplanes.live, or the last five characters of its ID.
- **IP address**: where tar1090 or graphs1090 runs for this feeder, like `192.168.0.42`. The app then shows buttons to open them.
- **Position**: latitude and longitude, like `50.8006, 6.0619`.

## Feeder list
{: #list }

The header shows whether all feeders are online. Each feeder shows its status, its Beast message rate (MSG/s), data rate (KBit/s) and MLAT stats with the share of outliers and the number of peers. Sort the list by **Label** or **Message rate**.

When the app's server monitors your feeders, the status comes from the server, for example "Waiting for the first check", "Offline since …" or "Possible airplanes.live outage". Problems the server found show below it, like "MLAT client missing". The free tier has fewer monitored feeders than a linked feeder. Feeders beyond that show "Not monitored, feeder limit reached" and a **Raise limit** button to [link your feeder](/getting-started#link-feeder).

Tap a feeder for more:

- **Show feed on map**: opens globe.airplanes.live in your browser, showing what this feeder receives.
- **Rename** and **Set IP address**.
- **TAR1090** and **GRAPHS1090**: open the feeder's own web pages. They appear once an IP address is set.
- **Monitoring**: the server's status and problems for this feeder, and the checks that don't have enough data yet. Only shown when the server monitors your feeders.
- **Statistics**: 30-day charts of the message rates and MLAT outliers. When the server monitors your feeders, the charts show the message rate, MLAT peers and MLAT outliers from the server's hourly history.
- **Generate QR Code**, see below.
- **Notify when offline**, see below.
- **Remove feeder**.

## Offline notifications
{: #offline }

**Notify when offline** sets how long a feeder may be offline before you get a notification: **Off**, 6, 12, 24 or 48 hours. New feeders start at 48 hours. Turning it on asks for notification permission on Android 13 and newer.

When the app's server offers it, the server monitors your feeders, also while the app is closed. A feeder counts as offline when it has had no connection to airplanes.live for the time you picked, confirmed by at least two checks. The server doesn't call feeders offline while airplanes.live itself has trouble or many feeders drop at once. You then get "Feeder offline", and "Feeder back online" when it returns.

The server also looks for problems and sends "Feeder problems" when it finds one:

- MLAT client missing
- no MLAT peers
- MLAT sync errors
- many MLAT outliers
- receiving far fewer messages than usual
- reconnecting repeatedly
- feeder ID used by several feeders

Turn these off with **Settings** › **Feeder** › **Problem alerts**.

The server's notifications come with the app's background sync, so they can take as long as the level set in **Settings** › **General** › **Background sync**. See [How often the app syncs](/watchlist#interval).

Tap **Snooze** on a notification to silence that feeder until it is back online. For problems of a feeder that is online, the snooze lasts until they clear.

Without the server, the app checks airplanes.live itself and shows "Offline feeders detected" once a feeder hasn't been seen for the time you picked. It checks every 60 minutes.

## Share a feeder
{: #share }

**Generate QR Code** shows a QR code with the feeder's settings. Scan it with **Scan QR Code** in the app on another device to add the same feeder there.
