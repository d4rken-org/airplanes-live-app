---
title: Häufige Fragen und Fehlerbehebung
description: "Antworten zu fehlenden Alarmen, täglichen Kontingenten, Standort- und Kamerazugriff, Backups und der AR-Ansicht."
permalink: /de/faq
translation_key: faq
lang: de
source_hash: 13a077c5ca89feb4
---
# Häufige Fragen und Fehlerbehebung
{: #faq }

## Ich bekomme keine Alarme
{: #no-alerts }

Prüfe diese Punkte der Reihe nach:

1. **Die Benachrichtigung ist an.** Neue Beobachtungen starten mit ausgeschalteten Benachrichtigungen. Öffne die Beobachtung und schalte **Benachrichtigung: aus** auf **Benachrichtigung: an** um.
2. **Android erlaubt die Benachrichtigungen der App.** Öffne die Android-Einstellungen › **Apps** › **airplanes.live** › **Benachrichtigungen**, erlaube sie und prüfe, dass der Kanal „Merkliste-Alarme“ an ist.
3. **Die Beobachtung wird geprüft.** Eine Beobachtung, die in der Merkliste als „Nicht geprüft, über deinem Limit“ markiert ist, liegt über dem Limit der kostenlosen Stufe. Siehe [Limits](/de/watchlist#limits).
4. **Etwas Neues ist aufgetaucht.** Ein Alarm kommt, wenn eine Beobachtung von nichts zu etwas wechselt. Wenn ein Flugzeug schon bei der ersten Prüfung der Beobachtung gepasst hat, bleibt dieser Treffer stumm. Der nächste Alarm kommt, nachdem es verschwunden und wieder aufgetaucht ist.
5. **Android lässt die App im Hintergrund laufen.** Prüfungen laufen standardmäßig alle 60 Minuten. Energiesparfunktionen können sie weiter verzögern. Wenn Alarme spät kommen, stelle die Akkunutzung der App in den App-Einstellungen von Android auf **Uneingeschränkt**.

## Warum zeigt die Karte nur einige Flugzeuge?
{: #map-capped }

Herausgezoomt zeigt die Karte nur einen Teil der Flugzeuge im sichtbaren Bereich und sagt das oben an. Zoome hinein, um alle zu sehen. Wenn die Karte „Tägliches Kartenkontingent aufgebraucht“ anzeigt, ist das heutige Kontingent der kostenlosen Stufe verbraucht. Siehe [Kostenlos und Pro](/de/getting-started#free-and-pro).

## Was bedeutet „Tägliches Suchkontingent aufgebraucht“?
{: #allowance }

Die kostenlose Stufe erlaubt eine bestimmte Anzahl an Suchen und Abfragen pro Tag. Das Banner sagt, wann das Kontingent zurückgesetzt wird. Wenn du einen Feeder verknüpfst, steigen die Kontingente deutlich. Siehe [Kostenlos und Pro](/de/getting-started#free-and-pro).

## Warum fehlt ein Flugzeug?
{: #missing-aircraft }

airplanes.live sieht nur Flugzeuge, die in Reichweite eines Feeders sind und ADS-B senden oder per MLAT geortet werden. Über Ozeanen und in manchen Regionen ist die Abdeckung dünn, und Flugzeuge am Boden oder in geringer Höhe sind schwerer zu empfangen. Du kannst helfen, indem du [einen Feeder betreibst](https://airplanes.live/get-started/).

## Warum fragt die App nach meinem Standort?
{: #location }

Nur für Funktionen, die ihn brauchen: **Mein Standort** auf der Karte, das Sortieren von Suchergebnissen nach Entfernung, die Suche **In der Nähe** und **Meinen Standort verwenden** beim Anlegen einer Standortbeobachtung nutzen deinen ungefähren Standort. Die AR-Ansicht braucht deinen genauen Standort und die Kamera. Alles andere funktioniert ohne Standortzugriff.

## Wie übertrage ich meine Beobachtungen und Feeder auf ein neues Handy?
{: #backup }

Öffne **Einstellungen** › **Sichern & Wiederherstellen** und tippe auf **Backup erstellen**. Wähle aus, was enthalten sein soll: **Beobachtungen** mit ihrem Prüfverlauf und **Feeder** mit ihren Statistiken. Die App speichert eine `.apl.zip`-Datei. Installiere auf dem neuen Handy die App und nutze **Backup wiederherstellen** mit dieser Datei.

Der Feeder-Zugang ist nicht Teil eines Backups. [Verknüpfe deinen Feeder](/de/getting-started#link-feeder) auf dem neuen Handy erneut, um Pro zu bekommen.

Das Wiederherstellen ist nur für dieselbe App-Version garantiert, und die App warnt, wenn die Versionen abweichen. Ein Backup aus einer neueren Version braucht unter Umständen erst ein App-Update, bevor es sich wiederherstellen lässt.

## Die AR-Beschriftungen zeigen in die falsche Richtung
{: #ar-calibration }

Der Kompass deines Handys muss kalibriert werden. Tippe in der AR-Ansicht auf **Hilfe** und bewege dein Handy dann mehrmals in Form einer Acht. Halte Abstand zu Metall und Magneten, etwa einem Auto oder einer Handyhülle mit Magnet. Die [Kalibrierungsanleitung](https://support.google.com/maps/answer/2839911) von Google hat weitere Tipps.

## Mein Feeder ist offline, aber ich habe keine Benachrichtigung bekommen
{: #feeder-offline }

Die App warnt erst, wenn airplanes.live den Feeder 48 Stunden lang nicht gesehen hat, kurze Ausfälle lösen also nichts aus. Prüfe außerdem, dass **Benachrichtigen, wenn offline** für diesen Feeder an ist, dass die Benachrichtigung nicht geschlummert wurde und dass Android die Benachrichtigungen der App erlaubt. Siehe [Offline-Benachrichtigungen](/de/feeders#offline).
