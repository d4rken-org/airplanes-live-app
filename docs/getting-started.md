---
title: Getting started
description: "Install the airplanes.live app, find your way around its tabs and learn what Free and Pro mean."
permalink: /getting-started
translation_key: getting-started
lang: en
---
# Getting started
{: #getting-started }

The official airplanes.live app shows live air traffic from [airplanes.live](https://airplanes.live), a community of people who receive aircraft signals (ADS-B and MLAT) with their own antennas and share them. It runs on Android 8.0 and newer.

## Install
{: #install }

Download the APK from [GitHub releases](https://github.com/d4rken-org/airplanes-live-app/releases/latest) or install it from [IzzyOnDroid](https://apt.izzysoft.de/packages/eu.darken.apl/) with an F-Droid client.

GitHub builds can check for new versions themselves. Turn on **Update check** during setup or later in **Settings** › **General**. It is off by default.

## First launch
{: #first-launch }

The app shows a short welcome and its privacy summary: it does not collect or sell user data, and it needs internet access. Tap **Accept** to continue. You can read the full [privacy policy](/privacy) at any time from **Settings** › **Privacy policy**.

## Find your way around
{: #tabs }

The bar at the bottom switches between:

- **Map**: live aircraft around you or anywhere in the world. See [Map](/map).
- **Search**: find aircraft by callsign, registration, hex code, type or squawk. See [Search](/search).
- **Watchlist**: aircraft, flights, squawk codes and areas you watch, with optional alerts. See [Watchlist & alerts](/watchlist).
- **Feeder**: the receivers you feed airplanes.live with. See [Feeders](/feeders). If you don't run one, you can hide this tab with **Show Feeder tab** in **Settings** › **Feeder**. Hiding it does not stop feeder monitoring.
- **Settings**: theme, units, map style, check intervals, backups and support.

A red bar saying "Internet is not available." means the app can't reach the network. Positions and search results then stay at their last state.

## Free and Pro
{: #free-and-pro }

The app is free. airplanes.live is community run: its coverage comes from people who put up an antenna and help pay for the servers, so full access is theirs. The chip next to the app name on the map shows **FREE** or **PRO**. Tap it to open **Upgrade status**.

On the free tier some limits apply, for example a daily allowance for searches and map data and a capped number of watches. The app tells you when you reach one.

Pro unlocks:

- Search results that aren't capped
- Much higher daily allowances for searches and aircraft data
- Faster refreshes and updates, on the map and for watches
- Unlimited watches
- Squawk and location watches
- A larger radius for nearby and AR search
- Aircraft history
- Theme mode, style and colors, including your own

### Link your feeder
{: #link-feeder }

If you feed airplanes.live, link your feeder and this installation goes Pro:

1. Connect your phone to the same network as your feeder, for example your home Wi-Fi.
2. Open **Upgrade status** and tap **Detect** to find the feeder on this network, or **Enter ID** to type its [feeder ID](/feeders#feeder-id).
3. Tap **Link**.

You only link once from your feeder's network. After that, Pro works anywhere. Linking needs an IPv4 connection, because that is how your feeder is recognized. If linking fails with "That feeder is not on this network", check that your phone isn't on mobile data or a VPN.

Linking is per installation. After reinstalling the app or moving to a new phone, link again; a [backup](/faq#backup) does not carry it over.

Don't feed yet? **How to feed** on the upgrade screen leads to the [airplanes.live setup guide](https://airplanes.live/get-started/).

## Settings
{: #settings }

- **General**: theme mode, style and color (Pro), aircraft and distance units, and the update check on GitHub builds.
- **Map**: **Restore last view** and the **Map style**.
- **Watchlist** and **Feeder**: how often the app checks in the background.
- **Backup & Restore**: export and import your watches and feeders. See [Backups](/faq#backup).
- **Support**: these guides, the issue tracker, Discord and the **Debug log**. See [Reporting problems](/bugs).
