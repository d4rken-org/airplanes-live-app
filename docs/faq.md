---
title: FAQ & troubleshooting
description: "Answers for missing alerts, daily allowances, location and camera access, backups and the AR view."
permalink: /faq
translation_key: faq
lang: en
---
# FAQ & troubleshooting
{: #faq }

## I don't get alerts
{: #no-alerts }

Check these in order:

1. **Notify is on.** New watches start with notifications off. Open the watch and switch **Notify: off** to **Notify: on**.
2. **Android allows the app's notifications.** Open Android's settings › **Apps** › **airplanes.live** › **Notifications**, allow them, and check that the "Watchlist alerts" channel is on.
3. **The watch is checked.** On the Watchlist, a watch marked "Not checked, over your limit" is over the free tier's limit. See [Limits](/watchlist#limits). A watch marked "Waiting for server", or a list saying "Watches are not monitored right now", is not checked until the app reaches the server.
4. **Something new showed up.** An alert comes when a watch goes from nothing to something. If an aircraft already matched the first time the watch was checked, that match stays quiet. The next alert comes after it has disappeared and shown up again.
5. **Android lets the app run in the background.** The app fetches alerts when it syncs with the server. How often depends on **Settings** › **General** › **Background sync**, from about every 15 minutes to about every 6 hours. Battery savers can delay syncs further. If alerts come late, set the app's battery usage to **Unrestricted** in Android's app settings.

## Why does the map show only some aircraft?
{: #map-capped }

Zoomed out, the map shows a share of the aircraft in view and says so at the top. Zoom in to see all of them. If the map says "Daily map allowance used up", the free tier's allowance for today is spent. See [Free and Pro](/getting-started#free-and-pro).

## What does "Daily search allowance used up" mean?
{: #allowance }

The free tier allows a number of searches and lookups per day. The banner says when it resets. Linking a feeder raises the allowances a lot. See [Free and Pro](/getting-started#free-and-pro).

## Why is an aircraft missing?
{: #missing-aircraft }

airplanes.live only sees aircraft that are in range of a feeder and send ADS-B or are located by MLAT. Coverage is thin over oceans and in some regions, and aircraft on the ground or flying low are harder to receive. You can help by [running a feeder](https://airplanes.live/get-started/).

## Why does the app ask for my location?
{: #location }

Only for features that need it: **My location** on the map, sorting search results by distance, searching **Nearby** and **Use my location** when adding a location watch use your approximate location. The AR view needs your precise location and the camera. Everything else works without location access.

## How do I move my watches and feeders to a new phone?
{: #backup }

Open **Settings** › **Backup & Restore** and tap **Create Backup**. Pick what to include: **Watches** and **Feeders**. Watch history and feeder stats aren't part of a backup, and restoring an older backup skips the history and stats it contains. The app saves a `.apl.zip` file. On the new phone, install the app and use **Restore Backup** with that file.

Feeder access is not part of a backup. On the new phone, [link your feeder](/getting-started#link-feeder) again to get Pro.

Restoring is only guaranteed for the same app version, and the app warns when the versions differ. A backup made by a newer version may need an app update before it can be restored.

## The AR labels point the wrong way
{: #ar-calibration }

Your phone's compass needs calibrating. In the AR view, tap **Help**, then move your phone in a figure-8 several times. Keep away from metal and magnets, like a car or a phone case with a magnet. Google's [calibration guide](https://support.google.com/maps/answer/2839911) has more tips.

## My feeder is offline but I got no notification
{: #feeder-offline }

The app warns only after the feeder has been offline for at least the time you picked in **Notify when offline**, so short outages don't trigger it. The app's server confirms an outage with at least two checks, and it doesn't call feeders offline while airplanes.live itself has trouble or many feeders drop at once. Its notifications come with the app's background sync (**Settings** › **General** › **Background sync**). Without server monitoring, where the feeder shows "Monitoring unavailable", there are no offline notifications.

Also check that **Notify when offline** isn't **Off** for that feeder, that the notification wasn't snoozed, and that Android allows the app's notifications. See [Offline notifications](/feeders#offline).
