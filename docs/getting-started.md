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

The app opens with three short pages: what airplanes.live is, how Free and Pro work, and a privacy summary. Once you accept, the app registers this installation with the developer's server under a random ID and sends what you ask for, such as the map area you look at, your searches, watches and receivers, and your location when a feature needs it. It needs internet access. Tap **Accept** to continue. You can read the full [privacy policy](/privacy) at any time from **Settings** › **Privacy policy**.

## Find your way around
{: #tabs }

The bar at the bottom switches between:

- **Map**: live aircraft around you or anywhere in the world. See [Map](/map).
- **Search**: find aircraft by callsign, registration, hex code, type or squawk. See [Search](/search).
- **Watchlist**: aircraft, flights, squawk codes and areas you watch, with optional alerts. See [Watchlist & alerts](/watchlist).
- **Receiver**: the receivers you feed airplanes.live with. See [Receivers](/feeders). If you don't run one, you can hide this tab with **Show Receiver tab** in **Settings** › **Receiver**. Hiding it does not stop receiver monitoring.

Tap the gear in the Map tab's top bar to open **Settings**: theme, units, map style, background sync, backups and support. See [Settings](#settings).

A red bar saying "Internet is not available." means the app can't reach the network. Positions and search results then stay at their last state.

## Free and Pro
{: #free-and-pro }

The app is free. airplanes.live is community run: its coverage comes from people who put up an antenna and help pay for the servers, so full access is theirs. In the Google Play version, you can also get Pro with a [subscription](#subscribe). The chip next to the app name on the map shows **FREE** or **PRO**. Tap it to open **Upgrade status**.

On the free tier some limits apply, for example daily limits for searches and map data and a capped number of watches. The app tells you when you reach one.

Pro unlocks:

- Uncapped search results, and far more searches and lookups each day
- Faster refreshes and updates, on the map and for watches
- Many more watches, including squawk and location watches
- A larger radius for nearby and AR search
- Aircraft history
- Theme mode, style and colors, including your own
- Additional map overlays

### Link your receiver
{: #link-feeder }

If you feed airplanes.live, link your receiver and this installation goes Pro:

1. Connect your phone to the same network as your receiver, for example your home Wi-Fi.
2. Open **Upgrade status** and tap **Detect** to find the receiver on this network, or **Enter ID** to type its [feeder ID](/feeders#feeder-id).
3. Tap **Link**.

You only link once from your receiver's network. After that, Pro works anywhere. Linking needs an IPv4 connection, because that is how your receiver is recognized. If linking fails with "That receiver is not on this network", check that your phone isn't on mobile data or a VPN.

Linking is per installation. After reinstalling the app or moving to a new phone, link again; a [backup](/faq#backup) does not carry it over.

Don't feed yet? **How to feed** on the upgrade screen leads to the [airplanes.live setup guide](https://airplanes.live/get-started/).

### Subscribe
{: #subscribe }

In the Google Play version, you can also get Pro with a monthly subscription. It helps cover the server costs.

1. Open **Upgrade status**. The **Subscription** card shows the price, and a free trial when Google Play offers you one.
2. Tap **Subscribe** and confirm the purchase in Google Play.

On another device or after reinstalling the app, tap **Restore** on the same card, with the same Google account in Google Play. **Manage subscription** opens Google Play, where you can change or cancel the subscription.

A linked receiver and a subscription can both be active. With a linked receiver, this installation keeps Pro without the subscription.

## Settings
{: #settings }

- **General**: theme mode, style and color (Pro), aircraft and distance units, **Background sync**, and the update check on GitHub builds.
- **Map**: **Restore last view** and the **Map style**.
- **Receiver**: when the app's server monitors your receivers, **Problem alerts** turns the server's problem notifications on or off. **Show Receiver tab** shows or hides the Receiver tab.
- **Backup & Restore**: export and import your watches and receivers. See [Backups](/faq#backup).
- **Support**: these guides, the issue tracker, Discord and the **Debug log**. See [Reporting problems](/bugs).
