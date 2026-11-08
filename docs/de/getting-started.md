---
title: Erste Schritte
description: "Installiere die airplanes.live-App, finde dich in ihren Tabs zurecht und erfahre, was Kostenlos und Pro bedeuten."
permalink: /de/getting-started
translation_key: getting-started
lang: de
source_hash: 8f5e9de45eae52a1
---
# Erste Schritte
{: #getting-started }

Die offizielle airplanes.live-App zeigt den Live-Flugverkehr von [airplanes.live](https://airplanes.live), einer Community von Menschen, die Flugzeugsignale (ADS-B und MLAT) mit eigenen Antennen empfangen und teilen. Sie läuft auf Android 8.0 und neuer.

## Installieren
{: #install }

Lade die APK von den [GitHub-Releases](https://github.com/d4rken-org/airplanes-live-app/releases/latest) herunter oder installiere sie mit einem F-Droid-Client von [IzzyOnDroid](https://apt.izzysoft.de/packages/eu.darken.apl/).

GitHub-Builds können selbst nach neuen Versionen suchen. Schalte die **Update-Prüfung** bei der Einrichtung oder später unter **Einstellungen** › **Allgemein** ein. Standardmäßig ist sie aus.

## Erster Start
{: #first-launch }

Die App zeigt eine kurze Begrüßung und ihre Datenschutz-Zusammenfassung. Wenn du auf **Akzeptieren** tippst, meldet die App diese Installation unter einer zufällig erzeugten Kennung beim Server des Entwicklers an. Danach schickt sie ihm deine Anfragen, etwa den Kartenausschnitt, den du ansiehst, deine Suchanfragen und Beobachtungen und deinen Standort, wenn eine Funktion ihn braucht. Die App braucht Internetzugriff. Die vollständige [Datenschutzerklärung](/privacy) kannst du jederzeit unter **Einstellungen** › **Datenschutzerklärung** lesen.

## Zurechtfinden
{: #tabs }

Die Leiste unten wechselt zwischen:

- **Karte**: Live-Flugzeuge um dich herum oder irgendwo auf der Welt. Siehe [Karte](/de/map).
- **Suche**: Flugzeuge nach Rufzeichen, Registrierung, Hexcode, Typ oder Squawk finden. Siehe [Suche](/de/search).
- **Merkliste**: Flugzeuge, Flüge, Squawk-Codes und Bereiche, die du beobachtest, mit optionalen Alarmen. Siehe [Merkliste und Alarme](/de/watchlist).
- **Feeder**: die Empfänger, mit denen du Empfangsdaten an airplanes.live sendest. Siehe [Feeder](/de/feeders). Wenn du keinen betreibst, kannst du diesen Tab mit **Feeder-Tab anzeigen** unter **Einstellungen** › **Feeder** ausblenden. Das Ausblenden stoppt die Feeder-Überwachung nicht.
- **Einstellungen**: Design, Einheiten, Kartenstil, Synchronisierungs- und Prüfintervalle, Backups und Support.

Eine rote Leiste mit „Internet ist nicht verfügbar.“ bedeutet, dass die App das Netzwerk nicht erreicht. Positionen und Suchergebnisse bleiben dann auf ihrem letzten Stand.

## Kostenlos und Pro
{: #free-and-pro }

Die App ist kostenlos. airplanes.live wird von der Community betrieben: Die Abdeckung stammt von Menschen, die eine Antenne aufstellen und helfen, die Server zu bezahlen, daher gehört ihnen der volle Zugang. Der Chip neben dem App-Namen auf der Karte zeigt **KOSTENLOS** oder **PRO**. Tippe darauf, um den **Upgrade-Status** zu öffnen.

In der kostenlosen Stufe gelten einige Limits, zum Beispiel ein tägliches Kontingent für Suchen und Kartendaten und eine begrenzte Anzahl an Beobachtungen. Die App sagt dir, wenn du eines erreichst.

Pro schaltet frei:

- Suchergebnisse ohne Begrenzung
- Deutlich höhere tägliche Kontingente für Suchen und Flugzeugdaten
- Schnellere Aktualisierungen auf der Karte und für Beobachtungen
- Unbegrenzte Beobachtungen
- Squawk- und Standortbeobachtungen
- Größerer Radius für Suche in der Nähe und AR-Suche
- Flugzeugverlauf
- Designmodus, Stil und Farben, auch eigene

### Feeder verknüpfen
{: #link-feeder }

Wenn du bereits Empfangsdaten an airplanes.live sendest, verknüpfe deinen Feeder und diese Installation wird Pro:

1. Verbinde dein Handy mit dem Netzwerk, in dem sich auch dein Feeder befindet, zum Beispiel deinem WLAN zu Hause.
2. Öffne den **Upgrade-Status** und tippe auf **Erkennen**, um den Feeder in diesem Netzwerk zu finden, oder auf **ID eingeben**, um seine [Feeder-ID](/de/feeders#feeder-id) einzutippen.
3. Tippe auf **Verknüpfen**.

Du verknüpfst nur einmal aus dem Netzwerk deines Feeders. Danach funktioniert Pro überall. Zum Verknüpfen brauchst du eine IPv4-Verbindung, denn darüber wird dein Feeder erkannt. Wenn das Verknüpfen mit „Dieser Feeder befindet sich nicht in diesem Netzwerk“ fehlschlägt, prüfe, dass dein Handy nicht über mobile Daten oder ein VPN verbunden ist.

Die Verknüpfung gilt pro Installation. Nach einer Neuinstallation der App oder dem Umzug auf ein neues Handy musst du erneut verknüpfen; ein [Backup](/de/faq#backup) überträgt sie nicht.

Du sendest noch keine Daten? **Feeder einrichten** auf dem Upgrade-Bildschirm führt zur [Einrichtungsanleitung von airplanes.live](https://airplanes.live/get-started/).

## Einstellungen
{: #settings }

- **Allgemein**: Designmodus, Stil und Farbe (Pro), Flugzeug- und Entfernungseinheiten sowie die Update-Prüfung bei GitHub-Builds.
- **Karte**: **Letzte Ansicht wiederherstellen** und der **Kartenstil**.
- **Merkliste**: wie oft die App mit der Beobachtungsliste des Servers synchronisiert.
- **Feeder**: wie oft die App deine Feeder im Hintergrund prüft.
- **Sichern & Wiederherstellen**: deine Beobachtungen und Feeder exportieren und importieren. Siehe [Backups](/de/faq#backup).
- **Support**: diese Anleitungen, der Issue-Tracker, Discord und das **Debug-Protokoll**. Siehe [Probleme melden](/de/bugs).
