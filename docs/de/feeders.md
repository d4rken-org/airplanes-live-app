---
title: Empfänger
description: "Füge deinen Empfänger für airplanes.live hinzu, verfolge seine Nachrichtenraten und lass dich benachrichtigen, wenn er offline geht."
permalink: /de/feeders
translation_key: feeders
lang: de
source_hash: 6e4f9db68d11263d
---
# Empfänger
{: #feeders }

Ein Empfänger, meist ein Raspberry Pi mit Antenne, fängt Flugzeugsignale auf und sendet sie an airplanes.live. Der Tab **Empfänger** verfolgt deine Empfänger und warnt dich, wenn einer offline geht. Wie du einen einrichtest, steht in der [Einrichtungsanleitung von airplanes.live](https://airplanes.live/get-started/).

Einen Empfänger hier hinzuzufügen und ihn für Pro zu [verknüpfen](/de/getting-started#link-feeder) sind getrennte Schritte. Du kannst das eine ohne das andere tun.

## Deine Feeder-ID finden
{: #feeder-id }

Jeder Empfänger hat eine Feeder-ID, eine UUID wie `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. Auf einem Empfänger, der mit dem Feed-Skript von airplanes.live eingerichtet wurde, zeigst du sie so an:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

Auf dem Feeder-Image von airplanes.live ist es die Datei `/boot/airplanes-uuid`.

## Einen Empfänger hinzufügen
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="Der Bildschirm zum Hinzufügen eines Empfängers mit den Feldern für Feeder-ID, Label, IP-Adresse und Position"></a>

Tippe im Tab Empfänger auf die Schaltfläche **+**. Es gibt drei Wege, **Neuer Empfänger** auszufüllen:

- **Lokalen Empfänger erkennen**: findet Empfänger im selben Netzwerk wie dein Handy und füllt die Felder aus. Findet es mehrere, wähle einen aus.
- **QR-Code scannen**: scannt einen Empfänger-QR-Code, den die App auf einem anderen Gerät erstellt hat, siehe [Einen Empfänger teilen](#share).
- Die **Feeder-ID** selbst eintippen.

Optionale Felder:

- **Label**: ein Name für den Empfänger. Ohne Label verwendet die App den MLAT-Namen des Empfängers bei airplanes.live oder die letzten fünf Zeichen seiner ID.
- **IP-Adresse**: wo tar1090 oder graphs1090 für diesen Empfänger läuft, wie `192.168.0.42`. Die App zeigt dann Schaltflächen, um sie zu öffnen.
- **Position**: Breiten- und Längengrad, wie `50.8006, 6.0619`.

## Empfängerliste
{: #list }

Die Kopfzeile zeigt, ob alle Empfänger online sind. Jeder Empfänger zeigt seinen Status. Wenn der Server der App deine Empfänger überwacht, zeigt jeder Empfänger außerdem seine Beast-Nachrichtenrate (MSG/s), Datenrate (KBit/s) und MLAT-Statistiken mit dem Anteil der Ausreißer und der Anzahl der Peers. Ohne Überwachung durch den Server zeigt ein Empfänger „Überwachung nicht verfügbar“ und keine Raten. Sortiere die Liste nach **Bezeichnung** oder **Nachrichtenrate**.

Wenn der Server der App deine Empfänger überwacht, kommt der Status vom Server, zum Beispiel „Warte auf die erste Prüfung“, „Offline seit …“ oder „Möglicher Ausfall von airplanes.live“. Probleme, die der Server gefunden hat, stehen darunter, etwa „MLAT-Client fehlt“. In der kostenlosen Stufe werden weniger Empfänger überwacht als mit Pro. Empfänger darüber hinaus zeigen „Nicht überwacht, Empfängerlimit erreicht“ und einen Button **Limit erhöhen**, um [deinen Empfänger zu verknüpfen](/de/getting-started#link-feeder).

Tippe auf einen Empfänger für mehr:

- **Feed auf Karte anzeigen**: öffnet globe.airplanes.live in deinem Browser und zeigt, was dieser Empfänger empfängt.
- **Umbenennen** und **IP-Adresse festlegen**.
- **TAR1090** und **GRAPHS1090**: öffnen die eigenen Webseiten des Empfängers. Sie erscheinen, sobald eine IP-Adresse festgelegt ist.
- **Überwachung**: Status und Probleme dieses Empfängers laut Server und die Prüfungen, für die noch nicht genug Daten vorliegen. Ohne Überwachung durch den Server steht dort „Überwachung nicht verfügbar“.
- **Statistiken**: Diagramme von Nachrichtenrate, MLAT-Peers und MLAT-Ausreißern aus dem stündlichen Verlauf des Servers. Nur sichtbar, wenn der Server deine Empfänger überwacht.
- **QR-Code generieren**, siehe unten.
- **Benachrichtigen, wenn offline**, siehe unten.
- **Empfänger entfernen**.

## Offline-Benachrichtigungen
{: #offline }

**Benachrichtigen, wenn offline** legt fest, wie lange ein Empfänger offline sein darf, bevor du benachrichtigt wirst: **Aus**, 6, 12, 24 oder 48 Stunden. Neue Empfänger starten mit 48 Stunden. Unter Android 13 und neuer fragt die App beim Einschalten nach der Berechtigung für Benachrichtigungen.

Wenn der Server der App es anbietet, überwacht er deine Empfänger, auch wenn die App geschlossen ist. Ein Empfänger gilt als offline, wenn er für die gewählte Zeit keine Verbindung zu airplanes.live hatte, bestätigt durch mindestens zwei Prüfungen. Solange airplanes.live selbst Probleme hat oder viele Empfänger gleichzeitig wegfallen, erklärt der Server keine Empfänger für offline. Du bekommst dann „Empfänger offline“ und „Empfänger wieder online“, wenn er zurück ist.

Der Server sucht außerdem nach Problemen und schickt „Empfängerprobleme“, wenn er eines findet:

- MLAT-Client fehlt
- keine MLAT-Peers
- MLAT-Synchronisationsfehler
- viele MLAT-Ausreißer
- empfängt deutlich weniger Nachrichten als üblich
- verbindet sich ständig neu
- Feeder-ID wird von mehreren Empfängern gesendet

Diese schaltest du unter **Einstellungen** › **Empfänger** › **Problem-Benachrichtigungen** ab.

Die Benachrichtigungen des Servers kommen mit der Hintergrundsynchronisierung der App, sie können also so lange dauern, wie die unter **Einstellungen** › **Allgemein** › **Hintergrund-Synchronisierung** gewählte Stufe vorgibt. Siehe [Wie oft die App synchronisiert](/de/watchlist#interval).

Tippe in einer Benachrichtigung auf **Schlummern**, um diesen Empfänger stummzuschalten, bis er wieder online ist. Bei Problemen eines Empfängers, der online ist, hält das Schlummern an, bis sie behoben sind.

Ohne Überwachung durch den Server gibt es keine Offline-Benachrichtigungen, und deine Empfänger zeigen „Überwachung nicht verfügbar“.

## Einen Empfänger teilen
{: #share }

**QR-Code generieren** zeigt einen QR-Code mit den Einstellungen des Empfängers. Scanne ihn mit **QR-Code scannen** in der App auf einem anderen Gerät, um dort denselben Empfänger hinzuzufügen.
