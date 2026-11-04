---
title: Feeder
description: "Füge deinen airplanes.live-Feeder hinzu, verfolge seine Nachrichtenraten und lass dich benachrichtigen, wenn er offline geht."
permalink: /de/feeders
translation_key: feeders
lang: de
source_hash: 8b2de0304b5170b6
---
# Feeder
{: #feeders }

Ein Feeder ist ein Empfänger, meist ein Raspberry Pi mit Antenne, der die empfangenen Flugzeugsignale an airplanes.live sendet. Der Tab **Feeder** verfolgt deine Feeder und warnt dich, wenn einer offline geht. Wie du einen einrichtest, steht in der [Einrichtungsanleitung von airplanes.live](https://airplanes.live/get-started/).

Einen Feeder hier hinzuzufügen und ihn für Pro zu [verknüpfen](/de/getting-started#link-feeder) sind getrennte Schritte. Du kannst das eine ohne das andere tun.

## Deine Feeder-ID finden
{: #feeder-id }

Jeder Feeder hat eine ID, eine UUID wie `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. Auf einem Feeder, der mit dem Feed-Skript von airplanes.live eingerichtet wurde, zeigst du sie so an:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

Auf dem Feeder-Image von airplanes.live ist es die Datei `/boot/airplanes-uuid`.

## Einen Feeder hinzufügen
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="Der Bildschirm zum Hinzufügen eines Feeders mit den Feldern für Feeder-ID, Label, IP-Adresse und Position"></a>

Tippe im Tab Feeder auf die Schaltfläche **+**. Es gibt drei Wege, **Neuer Feeder** auszufüllen:

- **Lokalen Feeder erkennen**: findet Feeder im selben Netzwerk wie dein Handy und füllt die Felder aus. Findet es mehrere, wähle einen aus.
- **QR-Code scannen**: scannt einen Feeder-QR-Code, den die App auf einem anderen Gerät erstellt hat, siehe [Einen Feeder teilen](#share).
- Die **Feeder-ID** selbst eintippen.

Optionale Felder:

- **Label**: ein Name für den Feeder. Ohne Label verwendet die App den MLAT-Namen des Feeders bei airplanes.live oder die letzten fünf Zeichen seiner ID.
- **IP-Adresse**: wo tar1090 oder graphs1090 für diesen Feeder läuft, wie `192.168.0.42`. Die App zeigt dann Schaltflächen, um sie zu öffnen.
- **Position**: Breiten- und Längengrad, wie `50.8006, 6.0619`.

## Feeder-Liste
{: #list }

Die Kopfzeile zeigt, ob alle Feeder online sind. Jeder Feeder zeigt seine Beast-Nachrichtenrate (MSG/s), Datenrate (KBit/s) und MLAT-Statistiken mit dem Anteil der Ausreißer und der Anzahl der Peers. Sortiere die Liste nach **Bezeichnung** oder **Nachrichtenrate**.

Tippe auf einen Feeder für mehr:

- **Feed auf Karte anzeigen**: öffnet globe.airplanes.live in deinem Browser und zeigt, was dieser Feeder empfängt.
- **Umbenennen** und **IP-Adresse festlegen**.
- **TAR1090** und **GRAPHS1090**: öffnen die eigenen Webseiten des Feeders. Sie erscheinen, sobald eine IP-Adresse festgelegt ist.
- **Statistiken**: 30-Tage-Diagramme der Nachrichtenraten und MLAT-Ausreißer.
- **QR-Code generieren**, siehe unten.
- **Benachrichtigen, wenn offline**, siehe unten.
- **Feeder entfernen**.

## Offline-Benachrichtigungen
{: #offline }

**Benachrichtigen, wenn offline** ist bei neuen Feedern an. Ein Feeder gilt als offline, wenn airplanes.live ihn 48 Stunden lang nicht gesehen hat. Die App zeigt dann „Offline-Feeder erkannt“ mit dem Namen des Feeders. Unter Android 13 und neuer fragt die App beim Einschalten nach der Berechtigung für Benachrichtigungen.

Tippe in der Benachrichtigung auf **Schlummern**, um sie stummzuschalten, bis dieser Feeder wieder Daten sendet. Die Benachrichtigung verschwindet von selbst, sobald der Feeder zurück ist.

Die App prüft deine Feeder im Hintergrund. Wie oft, legst du unter **Einstellungen** › **Feeder** › **Prüfintervall** fest, von 15 Minuten bis zu einem Tag. Standard ist alle 60 Minuten.

## Einen Feeder teilen
{: #share }

**QR-Code generieren** zeigt einen QR-Code mit den Einstellungen des Feeders. Scanne ihn mit **QR-Code scannen** in der App auf einem anderen Gerät, um dort denselben Feeder hinzuzufügen.
