---
title: Merkliste und Alarme
description: "Beobachte Flugzeuge, Flüge, Squawk-Codes und Bereiche und erhalte eine Benachrichtigung, wenn sie auftauchen."
permalink: /de/watchlist
translation_key: watchlist
lang: de
source_hash: 77102e6f7e160c91
---
# Merkliste und Alarme
{: #watchlist }

Eine Beobachtung behält etwas für dich im Blick: ein Flugzeug, einen Flug, einen Squawk-Code oder einen Bereich. Die Beobachtungsliste des Servers prüft deine Beobachtungen, und der Tab **Merkliste** zeigt jede mit dem, was die letzte Prüfung gefunden hat. Schalte die Benachrichtigung einer Beobachtung ein, und die App sagt dir Bescheid, wenn es auftaucht.

## Eine Beobachtung hinzufügen
{: #add }

<a class="guide-shot" href="/assets/images/watchlist/add-watch.webp"><img src="/assets/images/watchlist/add-watch.webp" width="540" height="1200" loading="lazy" alt="Der Dialog zum Hinzufügen einer Beobachtung mit den vier Arten"></a>

Tippe im Tab Merkliste auf die Schaltfläche **+** und wähle einen Typ:

- **Flugzeug (Hexcode)**: ein Flugzeug anhand seines Hexcodes, der 24-Bit-ICAO-Adresse wie `3C6589`. Du kannst auch in den Details eines Flugzeugs auf der Karte auf **Zur Merkliste hinzufügen** tippen.
- **Flug (Rufzeichen)**: ein Flug anhand des Rufzeichens, das er sendet, wie `DLH400`.
- **Transpondercode (SQUAWK)**: jedes Flugzeug, das einen bestimmten Squawk-Code sendet, wie `7700` für Notfälle.
- **Standort (Radius)**: Flugzeuge in der Nähe eines Ortes, siehe unten.

Jede Beobachtung kann eine Notiz haben, damit du weißt, warum du sie hinzugefügt hast.

Squawk- und Standortbeobachtungen gehören zur Pro-Stufe. Siehe [Kostenlos und Pro](/de/getting-started#free-and-pro).

### Standortbeobachtungen
{: #location }

Eine Standortbeobachtung zählt die Flugzeuge innerhalb eines Radius um einen Punkt. Gib Koordinaten oder einen Ortsnamen ein und tippe auf **Auflösen**, oder tippe auf **Meinen Standort verwenden**. Gib ihr dann eine Bezeichnung und wähle den Radius. Der kleinste Radius ist 2 km; wie groß er sein kann, hängt von deiner Stufe ab.

Um nur Flugzeuge in bestimmten Höhen zu zählen, fülle **Min. Höhe**, **Max. Höhe** oder beides aus. Ein leeres Feld setzt keine Grenze. Höhen reichen von 0 bis 60.000 ft und richten sich nach deinen **Flugzeugeinheiten**: Meter bei Metrisch, sonst Fuß.

Du kannst auch lange auf die Karte drücken, um den Bereich um diese Stelle zu beobachten. Wenn du den Bereich oder den Höhenbereich später änderst, zum Beispiel mit **Bereich bearbeiten** in den Details der Beobachtung, beginnt der Verlauf der Beobachtung von vorn.

## Benachrichtigt werden
{: #notifications }

Bei einer neuen Beobachtung sind Benachrichtigungen aus. Öffne die Beobachtung und tippe auf **Benachrichtigung: aus**, um auf **Benachrichtigung: an** umzuschalten.

Du bekommst eine Benachrichtigung, wenn es für eine Beobachtung nach einer Phase ohne Treffer wieder einen Treffer gibt: Ein Flugzeug wird entdeckt, ein Rufzeichen ist in der Luft, ein Squawk-Code wird gesendet oder Flugzeuge tauchen in deinem Bereich auf. Die Benachrichtigung wiederholt sich erst, wenn die Beobachtung zwischendurch nichts gesehen hat. Passt bei der allerersten Prüfung einer Beobachtung schon etwas, bleibt dieser erste Treffer stumm.

Wenn viele Alarme auf einmal eintreffen, zeigt die App ein paar davon und fasst den Rest als „Verpasste Beobachtungs-Alarme“ zusammen. Tippe auf eine Benachrichtigung, um ihre Beobachtung zu öffnen.

Alarme nutzen den Benachrichtigungskanal „Merkliste-Alarme“. Android muss Benachrichtigungen für die App erlauben. Siehe [Ich bekomme keine Alarme](/de/faq#no-alerts).

## Wie oft die App synchronisiert
{: #interval }

Der Server prüft deine Beobachtungen, nicht die App. Die App holt die Ergebnisse im Hintergrund ab, wenn sie eine Netzwerkverbindung hat, und dann kommen auch deine Alarme an. Wie oft, legst du unter **Einstellungen** › **Merkliste** › **Prüfintervall** fest. Standard ist alle 60 Minuten. Das kürzeste Intervall ist in der kostenlosen Stufe 60 Minuten, mit Pro 15 Minuten.

Die App synchronisiert außerdem, wenn du die Merkliste öffnest und die letzte Synchronisierung mehr als 15 Minuten zurückliegt. Ziehe die Liste nach unten, um sofort zu synchronisieren.

Eine Beobachtung, die der Server noch nicht übernommen hat, zeigt „Warte auf Server“. Ist die Beobachtungsliste des Servers nicht verfügbar, zeigt die Merkliste „Beobachtungen werden gerade nicht überwacht“. Die Überwachung geht weiter, sobald die App den Server erreicht.

## Details einer Beobachtung
{: #details }

Tippe auf eine Beobachtung, um Folgendes zu sehen:

- **Passende Flugzeuge**: was die letzte Prüfung gefunden hat. Tippe auf eines, um seine Details zu sehen.
- **Aktivität, letzte 7 Tage**: ein stündliches Raster, wann die Beobachtung etwas gesehen hat.
- **Flugzeuge bei jeder Prüfung, 30 Tage**: bei Squawk- und Standortbeobachtungen die meisten Flugzeuge, die der Server in jeder Stunde gezählt hat.

Das Menü **Mehr** enthält **Notiz bearbeiten**, **Bereich bearbeiten** für Standortbeobachtungen und **Beobachtung löschen**.

## Limits
{: #limits }

Die kostenlose Stufe prüft eine begrenzte Anzahl an Beobachtungen, angezeigt als „X von Y kostenlosen Beobachtungen genutzt“. Beobachtungen über dem Limit bleiben in deiner Liste als „Nicht geprüft, über deinem Limit“. Geprüft werden die Beobachtungen, die du zuerst hinzugefügt hast. Pro prüft alle.
