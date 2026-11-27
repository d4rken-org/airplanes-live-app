---
paths:
  - "app/src/main/java/eu/darken/apl/search/**"
---

# Search

- Ticket flight numbers often differ from the broadcast callsign (FR5769 vs RYR74MH). Research 2026-10
  (external): no free source mapped one to the other per day (paid: AeroDataBox, FlightAware AeroAPI,
  Cirium); adsbdb flight-number routes disagreed with VRS on most samples; VRS standing data matched live
  positions in nearly every sample and backs route search (`acks_vrs_routes_desc`). Search uses numeric
  aliases plus route search instead.
- When "flight X isn't found" comes up, check whether the callsign is coded before suspecting a bug.
