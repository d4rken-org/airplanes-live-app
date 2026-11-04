---
title: Search
description: "Find aircraft by callsign, registration, hex code, type, squawk, flight number or route, and narrow results with filters."
permalink: /search
translation_key: search
lang: en
---
# Search
{: #search }

Search finds aircraft that airplanes.live is tracking right now. Type into the search field and tap **Search**.

## What you can search for
{: #terms }

| You type | It finds |
|---|---|
| `DLH400` | a callsign |
| `D-AIBL` | a registration |
| `3C6589` | a hex code (the aircraft's ICAO 24-bit address) |
| `A320` | an aircraft type |
| `7700` | a squawk (transponder) code |
| `FR5769` | the aircraft flying a passenger flight number |
| `FR EIN FAO` | flights by airline, departure and arrival airport |

Words narrow each other down: `DLH A320` finds Lufthansa A320s. Separate alternatives with commas: `7500, 7600, 7700` finds all three squawk codes.

Many airlines broadcast a different code than the flight number on your ticket. If a flight number finds nothing, try the route from your boarding pass, like `FR EIN FAO`. Flight numbers and routes work as a term of their own; combine them with other words or **Nearby** and they won't match.

## Filters
{: #filters }

- **Military**: only military aircraft.
- **Privacy**: only aircraft in the LADD and PIA privacy programs.
- **Nearby**: aircraft around a place instead of a search term. It starts at **My location**. Tap **Change** to search near a place name like "Berlin, Germany". Some devices can't look up place names; then only **My location** works.

**Military** and **Privacy** narrow the search down. **Nearby** looks around a place.

## Results
{: #results }

If you allow location access, results are sorted by their distance from you. Results marked **Cached** come from earlier searches and may be outdated. Tap a result to see its details. Long-press results to select several, then show them together on the map.

## Limits
{: #limits }

On the free tier, a search returns a capped number of results ("Showing X of Y matches") and there is a daily search allowance ("X of Y searches left today"). Pro removes the cap and raises the allowance a lot. See [Free and Pro](/getting-started#free-and-pro).
