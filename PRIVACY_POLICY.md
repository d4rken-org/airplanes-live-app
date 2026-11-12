---
layout: plain
permalink: /privacy
title: "Privacy Policy"
---

# Privacy policy

This is the privacy policy for the Android app "airplanes.live" (package `eu.darken.apl`), the official
airplanes.live app for Android. The app is developed and operated by Matthias Urhahn (darken), who is responsible for
the processing described here.

Send a [quick mail](mailto:support@darken.eu) if you have questions or want to use one of your rights.

## Summary

* No sign-up, no email address, no password.
* No ads, no analytics or crash-reporting SDK. Nothing is sold.
* After you accept the privacy notice on first start, the app registers with a server I run under a random
  installation ID. It sends that server what you ask for: the map area you look at, your searches, watches and feeders,
  and your location when you use a feature that needs it.
* You can delete your data at any time with **Delete my data** in the app's settings. Inactive installations are
  deleted from the server after 90 days.

## The app's server

The server is hosted in the EU. The hosting provider processes the data only on my behalf. All connections are
encrypted (HTTPS), and only I have access to the server.

The server receives:

* **An installation identity:** the public key of a key pair generated on your device, the installation ID the server
  assigns, the app version and build type, and the days the app was used. Used to keep your data apart from everyone
  else's and to prevent abuse without accounts.
* **Your IP address, device model and Android version** with every request. Used for abuse protection and rate limits,
  and to check that a feeder you link is on your network.
* **What you look at and search for:** the map area on screen, selected aircraft, search terms, aircraft whose
  history you open, and the callsigns of aircraft whose flight route the app looks up. Used to answer that request.
* **Your location, or a place you picked,** only when you use the AR view, a "Nearby" search or center the map on
  yourself. Used to find aircraft there, and not stored.
* **Your watches,** including the coordinates and radius of location watches. Used to check them and alert you, also
  while the app is closed. Labels and notes stay on your device.
* **The IDs of the feeders you add,** and for each how long it may be offline before you get a notification. Used to
  check these feeders and alert you when one goes offline or has a problem, also while the app is closed, and to show
  their status and history. Labels, IP addresses and positions stay on your device. For these feeders the server
  collects the stats airplanes.live publishes about them, which can include their MLAT position. It keeps them per
  feeder, so installations that add the same feeder share them.
* **The ID of a feeder you link.** Used to give feeders Pro access, after checking with airplanes.live that the feeder
  is active and on your network.

The legal bases are Art. 6(1)(b) GDPR for the features you use, and Art. 6(1)(f) GDPR (a secure, working service) for
abuse protection. When a request looks like an attack, its IP address, the kind of attack and the time are shared with
an IP reputation service that maintains a shared blocklist.

### How long it is kept

* Your installation and everything tied to it are deleted after 90 days without any use of the app. Background watch
  checks count as use. Installations blocked for abuse are kept while the block is needed.
* Watch alerts are kept for 7 days, hourly watch statistics for 31 days.
* Search and watch results are kept briefly, so a retried request isn't counted twice.
* Access logs with IP addresses are deleted after 7 days.
* A feeder no installation links to anymore is deleted after 2 days.
* The stats collected about a feeder are kept for 48 hours as recent samples, 31 days as hourly stats and 7 days as
  status changes. About 2 days after no installation monitors a feeder anymore, its data is deleted.
* Backups are deleted after a limited time, so deleted data can remain in a backup until then.

## Other services

Some features load data directly from these services. They are run independently of me, some outside the EU, and
their own privacy policies apply. Each receives your IP address, basic device information and the request itself.

| Service | Used for | The request contains |
|---|---|---|
| [airplanes.live](https://airplanes.live) ([privacy policy](https://airplanes.live/privacy/)) | Finding feeders on your network and a new feeder's name | Your feeder IDs |
| [planespotters.net](https://www.planespotters.net) ([privacy policy](https://www.planespotters.net/legal/privacypolicy)) | Aircraft photos | Hex code or registration |
| [OpenFreeMap](https://openfreemap.org) ([privacy policy](https://openfreemap.org/privacy/)) | Map tiles | The map area you look at |
| Your device's geocoding service (on most devices Google's) | Place search, location watch names | Place name or coordinates |
| [GitHub](https://github.com) | Update check, not in the Google Play version, only if you turn it on | Nothing beyond the request |

## Permissions

* **Internet** (`INTERNET`): required to load any data.
* **Location** (`ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`): optional. For distances to aircraft, centering the
  map on you, the AR view, "Nearby" searches and creating location watches. The app doesn't use your location in the
  background.
* **Camera** (`CAMERA`): optional. For the AR view and scanning feeder QR codes. Images stay on your device.
* **Notifications** (`POST_NOTIFICATIONS`): optional. For watch alerts, offline feeders and feeder problems.

## Debug log

You can record a debug log in the app's settings to help with troubleshooting. It stays on your device unless you
share it. It can contain your IP address, locations, searches, watches with their notes and feeder IDs, so only share
it with people you trust. Logs and emails sent to me are only used to help you and are deleted afterwards.

## Deleting your data

**Delete my data** in the app's settings deletes your data on the server right away, then all of the app's data on your device.
If you just uninstall the app, the server deletes your data 90 days after its last use. Installations blocked for abuse
can't delete themselves in the app; mail me the installation ID shown in the app instead. After a deletion, only that
ID and the time of the deletion are kept until no older backup remains.

## Your rights

Under the GDPR you can also ask for access to your data, its correction, restriction of processing and a copy, and
you can object to processing based on legitimate interests. Mail me the installation ID shown in the app's upgrade
screen, because the server doesn't know who you are. You can also complain to a data protection supervisory authority.

## Changes

This policy is updated when the app's handling of data changes. The history is in the
[repository](https://github.com/d4rken-org/airplanes-live-app/commits/main/PRIVACY_POLICY.md).
