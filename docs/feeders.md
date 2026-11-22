---
title: Receivers
description: "Add your airplanes.live receiver, follow its message rates and get notified when it goes offline."
permalink: /feeders
translation_key: feeders
lang: en
---
# Receivers
{: #feeders }

A receiver, usually a Raspberry Pi with an antenna, picks up aircraft signals and sends them to airplanes.live. The **Receiver** tab follows your receivers and warns you when one goes offline. To set one up, see the [airplanes.live setup guide](https://airplanes.live/get-started/).

Adding a receiver here and [linking it](/getting-started#link-feeder) for Pro are separate steps. You can do either without the other.

## Find your feeder ID
{: #feeder-id }

Every receiver has a feeder ID, a UUID like `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. On a receiver set up with the airplanes.live feed script, show it with:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

On the airplanes.live feeder image, the file is `/boot/airplanes-uuid`.

## Add a receiver
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="The screen for adding a receiver, with the feeder ID, label, IP address and position fields"></a>

Tap the **+** button on the Receiver tab. There are three ways to fill in **New receiver**:

- **Detect Local Receiver**: finds receivers on the same network as your phone and fills in the fields. If it finds several, pick one.
- **Scan QR Code**: scans a receiver QR code made by the app on another device, see [Share a receiver](#share).
- Type the **Feeder ID** yourself.

Optional fields:

- **Label**: a name for the receiver. Without one, the app uses the receiver's MLAT name from airplanes.live, or the last five characters of its ID.
- **IP address**: where tar1090 or graphs1090 runs for this receiver, like `192.168.0.42`. The app then shows buttons to open them.
- **Position**: latitude and longitude, like `50.8006, 6.0619`.

## Receiver list
{: #list }

The header shows whether all receivers are online. Each receiver shows its status. When the app's server monitors your receivers, each receiver also shows its Beast message rate (MSG/s), data rate (KBit/s), the latency of its last successful check (ms) and MLAT stats with the share of outliers and the number of peers. Without server monitoring, a receiver shows "Monitoring unavailable" and no rates. Sort the list by **Label** or **Message rate**.

When the app's server monitors your receivers, the status comes from the server, for example "Waiting for the first check", "Offline since …" or "Possible airplanes.live outage". Problems the server found show below it, like "MLAT client missing". The free tier has fewer monitored receivers than Pro. Receivers beyond that show "Not monitored, receiver limit reached" and a **Raise limit** button to [link your receiver](/getting-started#link-feeder).

Tap a receiver for more:

- **Show feed on map**: opens globe.airplanes.live in your browser, showing what this receiver picks up.
- **Rename** and **Set IP address**.
- **TAR1090** and **GRAPHS1090**: open the receiver's own web pages. They appear once an IP address is set.
- **Monitoring**: the server's status and problems for this receiver, and the checks that don't have enough data yet. Without server monitoring, it says "Monitoring unavailable".
- **Statistics**: charts of the message rate, MLAT peers, MLAT outliers, uptime and latency from the server's hourly history. Only shown when the server monitors your receivers.
- **Generate QR Code**, see below.
- **Notify when offline**, see below.
- **Notify about message rate drops**, **Notify about MLAT problems** and **Notify about connection problems**, see below. Only shown when the server monitors the receiver.
- **Remove receiver**.

## Offline notifications
{: #offline }

**Notify when offline** sets how long a receiver may be offline before you get a notification: **Off**, 6, 12, 24 or 48 hours. New receivers start at 48 hours. Turning it on asks for notification permission on Android 13 and newer.

When the app's server offers it, the server monitors your receivers, also while the app is closed. A receiver counts as offline when it has had no connection to airplanes.live for the time you picked, confirmed by at least two checks. The server doesn't call receivers offline while airplanes.live itself has trouble or many receivers drop at once. You then get "Receiver offline", and "Receiver back online" when it returns.

The server also looks for problems and sends "Receiver problems" when it finds one. Three switches in the receiver's sheet pick which problems you get notified about for that receiver:

- **Notify about message rate drops**: receiving far fewer messages than usual
- **Notify about MLAT problems**: MLAT client missing, no MLAT peers, MLAT sync errors, many MLAT outliers
- **Notify about connection problems**: reconnecting repeatedly, feeder ID sent by several receivers

MLAT problems are off by default for every receiver, the other two are on. Turning a switch on asks for notification permission on Android 13 and newer. Problems outside these three, like ones this app version doesn't know yet, still notify. The Receiver tab and the receiver's sheet still list every problem.

**Settings** › **Receiver** › **Problem alerts** turns all problem notifications off and removes one that is already shown.

The server's notifications come with the app's background sync, so they can take as long as the level set in **Settings** › **General** › **Background sync**. See [How often the app syncs](/watchlist#interval).

Tap **Snooze** on a notification to silence that receiver until it is back online. For problems of a receiver that is online, the snooze lasts until no problem you get notified about is left.

Without server monitoring there are no offline notifications, and your receivers show "Monitoring unavailable".

## Share a receiver
{: #share }

**Generate QR Code** shows a QR code with the receiver's settings. Scan it with **Scan QR Code** in the app on another device to add the same receiver there.
