---
title: Feeder
description: "Füge deinen airplanes.live-Feeder hinzu, verfolge seine Nachrichtenraten und lass dich benachrichtigen, wenn er offline geht."
permalink: /de/feeders
translation_key: feeders
lang: de
source_hash: 873f475d480f973e
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

Die Kopfzeile zeigt, ob alle Feeder online sind. Jeder Feeder zeigt seinen Status, seine Beast-Nachrichtenrate (MSG/s), Datenrate (KBit/s) und MLAT-Statistiken mit dem Anteil der Ausreißer und der Anzahl der Peers. Sortiere die Liste nach **Bezeichnung** oder **Nachrichtenrate**.

Wenn der Server der App deine Feeder überwacht, kommt der Status vom Server, zum Beispiel „Warte auf die erste Prüfung“, „Offline seit …“ oder „Möglicher Ausfall von airplanes.live“. Probleme, die der Server gefunden hat, stehen darunter, etwa „MLAT-Client fehlt“. In der kostenlosen Stufe werden weniger Feeder überwacht als mit einem verknüpften Feeder. Feeder darüber hinaus zeigen „Nicht überwacht, Feeder-Limit erreicht“ und einen Button **Limit erhöhen**, um [deinen Feeder zu verknüpfen](/de/getting-started#link-feeder).

Tippe auf einen Feeder für mehr:

- **Feed auf Karte anzeigen**: öffnet globe.airplanes.live in deinem Browser und zeigt, was dieser Feeder empfängt.
- **Umbenennen** und **IP-Adresse festlegen**.
- **TAR1090** und **GRAPHS1090**: öffnen die eigenen Webseiten des Feeders. Sie erscheinen, sobald eine IP-Adresse festgelegt ist.
- **Überwachung**: Status und Probleme dieses Feeders laut Server und die Prüfungen, für die noch nicht genug Daten vorliegen. Nur sichtbar, wenn der Server deine Feeder überwacht.
- **Statistiken**: 30-Tage-Diagramme der Nachrichtenraten und MLAT-Ausreißer. Wenn der Server deine Feeder überwacht, zeigen die Diagramme Nachrichtenrate, MLAT-Peers und MLAT-Ausreißer aus dem stündlichen Verlauf des Servers.
- **QR-Code generieren**, siehe unten.
- **Benachrichtigen, wenn offline**, siehe unten.
- **Feeder entfernen**.

## Offline-Benachrichtigungen
{: #offline }

**Benachrichtigen, wenn offline** legt fest, wie lange ein Feeder offline sein darf, bevor du benachrichtigt wirst: **Aus**, 6, 12, 24 oder 48 Stunden. Neue Feeder starten mit 48 Stunden. Unter Android 13 und neuer fragt die App beim Einschalten nach der Berechtigung für Benachrichtigungen.

Wenn der Server der App es anbietet, überwacht er deine Feeder, auch wenn die App geschlossen ist. Ein Feeder gilt als offline, wenn er für die gewählte Zeit keine Verbindung zu airplanes.live hatte, bestätigt durch mindestens zwei Prüfungen. Solange airplanes.live selbst Probleme hat oder viele Feeder gleichzeitig wegfallen, erklärt der Server keine Feeder für offline. Du bekommst dann „Feeder offline“ und „Feeder wieder online“, wenn er zurück ist.

Der Server sucht außerdem nach Problemen und schickt „Feeder-Probleme“, wenn er eines findet:

- MLAT-Client fehlt
- keine MLAT-Peers
- MLAT-Synchronisationsfehler
- viele MLAT-Ausreißer
- empfängt deutlich weniger Nachrichten als üblich
- verbindet sich ständig neu
- Feeder-ID wird von mehreren Feedern verwendet

Diese schaltest du unter **Einstellungen** › **Feeder** › **Problem-Benachrichtigungen** ab.

Die Benachrichtigungen des Servers kommen mit der Hintergrundprüfung deiner Beobachtungen, sie können also bis zu ein Prüfintervall der Beobachtungen dauern. Siehe [Wie oft Beobachtungen geprüft werden](/de/watchlist#interval).

Tippe in einer Benachrichtigung auf **Schlummern**, um diesen Feeder stummzuschalten, bis er wieder online ist. Bei Problemen eines Feeders, der online ist, hält das Schlummern an, bis sie behoben sind.

Ohne den Server prüft die App airplanes.live selbst und zeigt „Offline-Feeder erkannt“, sobald ein Feeder für die gewählte Zeit nicht gesehen wurde. Wie oft, legst du unter **Einstellungen** › **Feeder** › **Prüfintervall** fest, von 15 Minuten bis zu einem Tag. Standard ist alle 60 Minuten. Diese Einstellung erscheint nur, wenn der Server deine Feeder nicht überwacht.

## Einen Feeder teilen
{: #share }

**QR-Code generieren** zeigt einen QR-Code mit den Einstellungen des Feeders. Scanne ihn mit **QR-Code scannen** in der App auf einem anderen Gerät, um dort denselben Feeder hinzuzufügen.
