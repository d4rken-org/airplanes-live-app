---
title: Primi passi
description: "Installa l’app airplanes.live, impara a muoverti tra le sue schede e scopri cosa significano Gratuito e Pro."
permalink: /it/getting-started
translation_key: getting-started
lang: it
source_hash: bdd1c8627641c661
---
# Primi passi
{: #getting-started }

L’app ufficiale di airplanes.live mostra il traffico aereo in tempo reale di [airplanes.live](https://airplanes.live), una community di persone che ricevono i segnali degli aeromobili (ADS-B e MLAT) con le proprie antenne e li condividono. Funziona su Android 8.0 e versioni successive.

## Installazione
{: #install }

Scarica l’APK dalle [release su GitHub](https://github.com/d4rken-org/airplanes-live-app/releases/latest) oppure installala da [IzzyOnDroid](https://apt.izzysoft.de/packages/eu.darken.apl/) con un client F-Droid.

Le build di GitHub possono cercare da sole le nuove versioni. Attiva **Controllo aggiornamenti** durante la configurazione o più tardi in **Impostazioni** › **Generale**. È disattivato per impostazione predefinita.

## Primo avvio
{: #first-launch }

L’app mostra un breve benvenuto e il riepilogo sulla privacy: non raccoglie né vende dati degli utenti e ha bisogno dell’accesso a internet. Tocca **Accetta** per continuare. Puoi leggere l’[informativa sulla privacy](/privacy) completa in qualsiasi momento da **Impostazioni** › **Informativa sulla privacy**.

## Orientarsi nell’app
{: #tabs }

La barra in basso passa tra:

- **Mappa**: aeromobili in tempo reale intorno a te o in qualsiasi parte del mondo. Vedi [Mappa](/it/map).
- **Ricerca**: trova aeromobili per nominativo, immatricolazione, codice hex, tipo o squawk. Vedi [Ricerca](/it/search).
- **Lista di osservazione**: aeromobili, voli, codici squawk e aree che osservi, con avvisi facoltativi. Vedi [Lista di osservazione e avvisi](/it/watchlist).
- **Alimentatore**: i ricevitori con cui alimenti airplanes.live. Vedi [Alimentatori](/it/feeders). Se non ne gestisci uno, puoi nascondere questa scheda con **Mostra scheda Alimentatore** in **Impostazioni** › **Alimentatore**. Nasconderla non interrompe il monitoraggio degli alimentatori.
- **Impostazioni**: tema, unità, stile della mappa, intervalli di controllo, backup e supporto.

Una barra rossa con la scritta "Internet non è disponibile." significa che l’app non riesce a raggiungere la rete. Posizioni e risultati di ricerca restano allora all’ultimo stato.

## Gratuito e Pro
{: #free-and-pro }

L’app è gratuita. airplanes.live è gestito dalla community: la sua copertura viene da persone che installano un’antenna e aiutano a pagare i server, quindi l’accesso completo spetta a loro. Il chip accanto al nome dell’app sulla mappa mostra **GRATUITO** o **PRO**. Toccalo per aprire **Stato del piano**.

Nel piano gratuito valgono alcuni limiti, per esempio una quota giornaliera per le ricerche e i dati della mappa e un numero limitato di osservazioni. L’app ti avvisa quando ne raggiungi uno.

Pro sblocca:

- Risultati di ricerca senza limite
- Quote giornaliere molto più alte per ricerche e dati degli aeromobili
- Aggiornamenti più rapidi, sulla mappa e per le osservazioni
- Osservazioni illimitate
- Osservazioni di squawk e di posizione
- Un raggio più ampio per la ricerca nelle vicinanze e la ricerca AR
- Cronologia degli aeromobili
- Modalità, stile e colori del tema, anche personalizzati

### Collega il tuo alimentatore
{: #link-feeder }

Se alimenti airplanes.live, collega il tuo alimentatore e questa installazione passa a Pro:

1. Connetti il telefono alla stessa rete del tuo alimentatore, per esempio il Wi-Fi di casa.
2. Apri **Stato del piano** e tocca **Rileva** per trovare l’alimentatore su questa rete, oppure **Inserisci ID** per digitare il suo [ID alimentatore](/it/feeders#feeder-id).
3. Tocca **Collega**.

Il collegamento si fa una sola volta dalla rete del tuo alimentatore. Dopo, Pro funziona ovunque. Il collegamento richiede una connessione IPv4, perché è così che il tuo alimentatore viene riconosciuto. Se il collegamento non riesce con "Questo alimentatore non è su questa rete", controlla che il telefono non sia sui dati mobili o su una VPN.

Il collegamento vale per singola installazione. Dopo aver reinstallato l’app o essere passato a un nuovo telefono, collegalo di nuovo; un [backup](/it/faq#backup) non lo trasferisce.

Non alimenti ancora? **Come alimentare** nella schermata del piano porta alla [guida di configurazione di airplanes.live](https://airplanes.live/get-started/).

## Impostazioni
{: #settings }

- **Generale**: modalità, stile e colore del tema (Pro), unità per aeromobili e distanze, e il controllo aggiornamenti nelle build di GitHub.
- **Mappa**: **Ripristina ultima vista** e lo **Stile mappa**.
- **Lista di osservazione** e **Alimentatore**: ogni quanto l’app esegue i controlli in background.
- **Backup e ripristino**: esporta e importa le tue osservazioni e i tuoi alimentatori. Vedi [Backup](/it/faq#backup).
- **Supporto**: queste guide, il tracker dei problemi, Discord e il **Log di debug**. Vedi [Segnalare problemi](/it/bugs).
