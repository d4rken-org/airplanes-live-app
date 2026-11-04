---
title: Watchlist & alerts
description: "Watch aircraft, flights, squawk codes and areas, and get a notification when they show up."
permalink: /watchlist
translation_key: watchlist
lang: en
---
# Watchlist & alerts
{: #watchlist }

A watch keeps an eye on something for you: one aircraft, a flight, a squawk code or an area. The **Watchlist** tab shows each watch with what the last check found. Turn on its notification and the app tells you when it shows up.

## Add a watch
{: #add }

Tap the **+** button on the Watchlist tab and choose a type:

- **Aircraft (hex code)**: one aircraft by its hex code, the ICAO 24-bit address like `3C6589`. You can also tap **Add to watchlist** in an aircraft's details on the map.
- **Flight (callsign)**: a flight by the callsign it broadcasts, like `DLH400`.
- **Transponder code (SQUAWK)**: any aircraft transmitting a squawk code, like `7700` for emergencies.
- **Location**: aircraft near a place, see below.

Each watch can have a note, so you remember why you added it.

Squawk and location watches are part of the Pro tier. See [Free and Pro](/getting-started#free-and-pro).

### Location watches
{: #location }

A location watch counts the aircraft within a radius around a point. Enter coordinates or a place name and tap **Resolve**, or tap **Use my location**. Then give it a label and pick the radius. The smallest radius is 2 km; how large it can be depends on your plan.

You can also long-press the map to watch the area around that spot. Moving or resizing the area later starts the watch's history over.

## Get notified
{: #notifications }

Notifications are off for a new watch. Open the watch and tap **Notify: off** to switch it to **Notify: on**.

You get a notification when a watch goes from nothing to something: an aircraft is spotted, a callsign is airborne, a squawk code is being transmitted, or aircraft show up in your area. The notification repeats only after the watch has seen nothing in between. If something already matches when the watch is checked for the very first time, that first match stays quiet.

When many alerts come in at once, the app shows a few and sums up the rest as "Missed watchlist alerts". Tap a notification to open its watch.

Alerts use the "Watchlist alerts" notification channel. Android must allow notifications for the app. See [I don't get alerts](/faq#no-alerts).

## How often watches are checked
{: #interval }

The app checks your watches in the background, when it has a network connection. Set how often in **Settings** › **Watchlist** › **Check interval**. The default is every 60 minutes. Free allows 60 minutes at the shortest, Pro 15 minutes.

The app also checks when you open the Watchlist and the last check is more than 15 minutes old. Pull down on the list to check right away.

## Watch details
{: #details }

Tap a watch to see:

- **Matching aircraft**: what the last check found. Tap one to see its details.
- **Activity, last 7 days**: an hourly grid of when the watch saw something.
- **Aircraft at each check, 30 days**: the counts from the checks this app made.

The **More** menu has **Edit note**, **Edit area** for location watches, and **Delete watch**.

## Limits
{: #limits }

The free tier checks a limited number of watches, shown as "X of Y free watches used". Watches over the limit stay in your list as "Not checked, over your limit". The first watches you added are the ones that get checked. Pro checks all of them.
