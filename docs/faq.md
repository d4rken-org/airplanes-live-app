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
3. **The watch is checked.** On the Watchlist, a watch marked "Not checked, over your limit" is over the free tier's limit. See [Limits](/watchlist#limits).
4. **Something new showed up.** An alert comes when a watch goes from nothing to something. If an aircraft already matched the first time the watch was checked, that match stays quiet. The next alert comes after it has disappeared and shown up again.
5. **Android lets the app run in the background.** Checks run every 60 minutes by default. Battery savers can delay them further. If alerts come late, set the app's battery usage to **Unrestricted** in Android's app settings.

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

Open **Settings** › **Backup & Restore** and tap **Create Backup**. Pick what to include: **Watches** with their check history, **Feeders** with their stats, and the **Aircraft Cache**. The app saves a `.apl.zip` file. On the new phone, install the app and use **Restore Backup** with that file.

Feeder access is not part of a backup. On the new phone, [link your feeder](/getting-started#link-feeder) again to get Pro.

Restoring is only guaranteed for the same app version, and the app warns when the versions differ. A backup made by a newer version may need an app update before it can be restored.

## The AR labels point the wrong way
{: #ar-calibration }

Your phone's compass needs calibrating. In the AR view, tap **Help**, then move your phone in a figure-8 several times. Keep away from metal and magnets, like a car or a phone case with a magnet. Google's [calibration guide](https://support.google.com/maps/answer/2839911) has more tips.

## My feeder is offline but I got no notification
{: #feeder-offline }

The app warns after airplanes.live hasn't seen the feeder for 48 hours, so short outages don't trigger it. Also check that **Notify when offline** is on for that feeder, that the notification wasn't snoozed, and that Android allows the app's notifications. See [Offline notifications](/feeders#offline).

## Does the app collect data about me?
{: #privacy }

No. The app does not collect or sell user data. See the [privacy policy](/privacy).
