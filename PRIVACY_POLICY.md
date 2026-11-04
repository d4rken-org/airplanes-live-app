---
layout: plain
permalink: /privacy
title: "Privacy Policy"
---

# Privacy policy

This is the privacy policy for the Android app "airplanes.live" (package `eu.darken.apl`), the official
airplanes.live app for Android. The app is developed and operated by Matthias Urhahn (darken), who is responsible for
the processing described here.

If anything here is unclear, or you want to use one of your rights, send a [mail](mailto:support@darken.eu).

## Summary

* The app needs no sign-up, no email address and no password.
* It contains no ads and no analytics or crash-reporting SDK. Nothing is sold.
* After you accept the privacy notice on first start, the app registers itself with a server I run, under a random
  installation ID. To show you aircraft, it sends that server what you ask for: the map area you look at, your
  searches and watches, and your location when you use a feature that needs it.
* Some features load data directly from other services, listed below. They see your IP address and what the app asks
  them for.
* Data on the server is tied to the random installation ID, not to your name. It is deleted when you stop using the
  app, or earlier on request.

## The app's server

Aircraft positions, search, watches, history and feeder linking work through a server that I operate. It is hosted
in the EU. The hosting provider stores and processes this data as a processor on my behalf.

### What the server receives

| What | Why |
|---|---|
| An installation identity: the public key of a key pair generated on your device (the private key never leaves it), the installation ID the server assigns, the app version and whether it is the Google Play or the open source build | To keep your watches and access level apart from everyone else's, and to block abuse without accounts |
| The day the installation was first and last used, and on how many days it was used | To find inactive installations and delete them, and to tell real use from abuse |
| Your IP address, and your device model and Android version (sent with every request) | To protect the service from overload and abuse, and to check that a feeder you link is on your network |
| The map area you are looking at (its corner coordinates), the aircraft you select or pin, and your map filters | To send you the aircraft in that area. When you center the map on yourself, that area is around your location |
| Your search terms | To find the aircraft you searched for |
| An aircraft whose history you open, and the time range | To show where it was |
| Your location, or a place you picked, with a radius | Only when you use the AR view or a "Nearby" search, to find aircraft around that point |
| Your watches: what they look for (for example a callsign, registration, hex code or squawk), and for location watches the coordinates and radius | To check your watches and alert you, also while the app is closed. A location watch keeps the area you saved; the app doesn't track where you are for it. Labels and notes you give a watch stay on your device |
| The feeder ID of a feeder you link | To give feeders Pro access. The server checks with airplanes.live that the feeder is active, and uses your IP address and the feeder's to check that it is on your network |

The legal bases are Art. 6(1)(b) GDPR for the features you use, and Art. 6(1)(f) GDPR (my legitimate interest in a
secure, working service) for abuse protection and rate limits.

When a request to the server looks like an attack, its IP address, the kind of attack and the time are shared with an
IP reputation service that maintains a shared blocklist of attacking addresses.

All connections between the app and the server are encrypted (HTTPS). Access to the server and its database is
limited to me.

### How long it is kept

* Your installation and everything tied to it (watches, feeder link, access level) are deleted after 90 days without
  any use of the app. Background watch checks count as use. If you use the app again after that, it registers under a
  new installation ID, and a linked feeder has to be linked again. An installation I blocked for abuse is kept while
  the block is needed.
* Watch alerts and hourly watch statistics are kept for 7 and 31 days.
* Results of searches and watch checks are kept for a few minutes, so that a retried request isn't counted twice. A
  record that the request happened, without its content, is kept for about a day after that.
* Coordinates and map areas from AR, "Nearby" and map requests are used to answer the request and are not saved in the
  server's database. Saved location watches are kept as described above.
* Server access logs, which include IP addresses, are deleted after 7 days.
* A feeder that no installation links to anymore is deleted after 2 days.
* Database backups are deleted after a limited time, so deleted data can remain in a backup for a while afterwards. If
  a backup is ever restored, deletions made since are applied again before the service reopens.

## Other services the app contacts directly

These services are run by third parties, independently of me, and some of them are outside the EU. Their own privacy
policies apply. Each receives your IP address and the request itself. Requests the app makes with its own network
client also carry the app version, your device model and Android version. Map tile requests carry the app and map
library versions, your Android version and processor type instead.

| Service | Used for | What the request contains |
|---|---|---|
| [airplanes.live](https://airplanes.live) ([privacy policy](https://airplanes.live/privacy/)) | Feeder status, and finding feeders on your network | The feeder IDs you added. To find feeders on your network, only the request itself: airplanes.live matches it by your IP address |
| [planespotters.net](https://www.planespotters.net) ([privacy policy](https://www.planespotters.net/legal/privacypolicy)) | Aircraft photos | The aircraft's hex code or registration |
| [adsbdb.com](https://www.adsbdb.com) | Flight routes | The aircraft's callsign |
| [hexdb.io](https://hexdb.io) | Flight routes | The aircraft's callsign |
| [OpenFreeMap](https://openfreemap.org) ([privacy policy](https://openfreemap.org/privacy/)) | Map tiles | The map tiles for the area you are looking at |
| Your device's geocoding service | Place search, and names for location watches | The place you typed, or the coordinates to name |
| [GitHub](https://github.com) | Update check, only in the open source build, and only if you turn it on | Nothing beyond the request itself |

Which geocoding service your device uses depends on the device. On most devices with Google services it is Google's.

Links that open a website, such as the airplanes.live map or your feeder's own pages, open in your browser. The
browser's and the website's privacy policies apply there.

If you installed the app from Google Play, Google's own [privacy policy](https://policies.google.com/privacy) applies
to the Play Store.

## Data on your device

Your watches with their labels and notes, your feeders, settings, watch history and cached aircraft data are stored
on your device. Android's backup (Google backup or device-to-device transfer) can include them, except for the app's server
registration (its credentials, access level and feeder link). Backups you create in the app's backup screen are files that you save and share yourself.

## Permissions

### Internet (`INTERNET`)

Required. Without internet access the app can't load any data.

### Location (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`)

Optional. The app uses your location to show how far away aircraft are, to center the map on you, for the AR view,
for "Nearby" searches and to create location watches. Your location is only sent to the server when you use one of
these features, as described above. The app doesn't ask for background location access: background watch checks use
the areas saved in your watches, not your current location.

### Camera (`CAMERA`)

Optional. The camera is used for the AR view and to scan feeder QR codes. Camera images are processed on your device
and never uploaded.

### Notifications (`POST_NOTIFICATIONS`)

Optional. Needed to alert you when an aircraft matches one of your watches, and when a feeder goes offline.

## Debug log

The app has a debug log to help with troubleshooting. You start it yourself in the app's settings. The log is a
file on your device that contains detailed output of what the app does. That can include your IP address, locations
you used, your searches, your watches with their labels and notes, feeder IDs and scanned QR codes.

You decide whether to share the log, for example by email through Android's share dialog. Only share it with people
you trust.

## Contacting me

If you email me, for support or about this policy, I use your email address and what you send me, including any
debug log, only to answer you and to fix the problem. I delete it when it's no longer needed for that.

## Your rights

Under the GDPR you can ask for access to your data, its correction or deletion, restriction of processing, a copy of
your data, and you can object to processing based on legitimate interests. Send a [mail](mailto:support@darken.eu).
To find the data linked to your installation, include the installation ID shown in the app (Upgrade screen,
"Installation …"), because the server doesn't know who you are. When I delete your data, I keep the installation ID
and the time of the deletion until no older backup remains, so that restoring a backup can't bring the data back.

You also have the right to lodge a complaint with a data protection supervisory authority.

## Children

The app isn't directed at children under 13.

## Changes to this policy

When the app's handling of data changes, this policy is updated. The full history is in the
[repository](https://github.com/d4rken-org/airplanes-live-app/commits/main/PRIVACY_POLICY.md).
