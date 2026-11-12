---
title: FAQ e risoluzione dei problemi
description: "Risposte su avvisi mancanti, quote giornaliere, accesso a posizione e fotocamera, backup e vista AR."
permalink: /it/faq
translation_key: faq
lang: it
source_hash: 719cb3c67994435a
---
# FAQ e risoluzione dei problemi
{: #faq }

## Non ricevo avvisi
{: #no-alerts }

Controlla questi punti nell’ordine:

1. **La notifica è attiva.** Le nuove osservazioni partono con le notifiche disattivate. Apri l’osservazione e passa da **Notifica: disattiva** a **Notifica: attiva**.
2. **Android consente le notifiche dell’app.** Apri le impostazioni di Android › **App** › **airplanes.live** › **Notifiche**, consentile e controlla che il canale "Avvisi lista di osservazione" sia attivo.
3. **L’osservazione viene controllata.** Nella Lista di osservazione, un’osservazione contrassegnata come "Non controllata, oltre il limite" supera il limite del piano gratuito. Vedi [Limiti](/it/watchlist#limits). Un’osservazione contrassegnata come "In attesa del server", o una lista che mostra "Le osservazioni non sono monitorate al momento", non viene controllata finché l’app non raggiunge il server.
4. **È comparso qualcosa di nuovo.** Un avviso arriva quando un’osservazione passa da niente a qualcosa. Se un aeromobile corrispondeva già la prima volta che l’osservazione è stata controllata, quella corrispondenza resta silenziosa. L’avviso successivo arriva dopo che è scomparso ed è ricomparso.
5. **Android lascia girare l’app in background.** L’app scarica gli avvisi quando si sincronizza con il server. La frequenza dipende da **Impostazioni** › **Generale** › **Sincronizzazione in background**, da circa ogni 15 minuti a circa ogni 6 ore. Le funzioni di risparmio energetico possono ritardare ulteriormente la sincronizzazione. Se gli avvisi arrivano in ritardo, imposta l’utilizzo della batteria dell’app su **Senza restrizioni** nelle impostazioni dell’app di Android.

## Perché la mappa mostra solo alcuni aeromobili?
{: #map-capped }

Con lo zoom indietro, la mappa mostra solo una parte degli aeromobili inquadrati e lo segnala in alto. Ingrandisci per vederli tutti. Se la mappa indica "Quota giornaliera della mappa esaurita", la quota del piano gratuito per oggi è finita. Vedi [Gratuito e Pro](/it/getting-started#free-and-pro).

## Cosa significa "Quota giornaliera di ricerche esaurita"?
{: #allowance }

Il piano gratuito consente un certo numero di ricerche e consultazioni al giorno. Il banner indica quando si azzera. Collegare un alimentatore aumenta di molto le quote. Vedi [Gratuito e Pro](/it/getting-started#free-and-pro).

## Perché manca un aeromobile?
{: #missing-aircraft }

airplanes.live vede solo gli aeromobili che sono nel raggio di un alimentatore e trasmettono ADS-B o vengono localizzati tramite MLAT. La copertura è scarsa sopra gli oceani e in alcune regioni, e gli aeromobili a terra o a bassa quota sono più difficili da ricevere. Puoi aiutare [gestendo un alimentatore](https://airplanes.live/get-started/).

## Perché l’app chiede la mia posizione?
{: #location }

Solo per le funzioni che ne hanno bisogno: **La mia posizione** sulla mappa, l’ordinamento dei risultati di ricerca per distanza, la ricerca **Nelle vicinanze** e **Usa la mia posizione** quando aggiungi un’osservazione per posizione usano la tua posizione approssimativa. La vista AR ha bisogno della tua posizione precisa e della fotocamera. Tutto il resto funziona senza accesso alla posizione.

## Come sposto le mie osservazioni e i miei alimentatori su un nuovo telefono?
{: #backup }

Apri **Impostazioni** › **Backup e ripristino** e tocca **Crea backup**. Scegli cosa includere: **Osservazioni** e **Alimentatori**. La cronologia delle osservazioni e le statistiche degli alimentatori non fanno parte di un backup, e il ripristino di un backup precedente salta la cronologia e le statistiche che contiene. L’app salva un file `.apl.zip`. Sul nuovo telefono, installa l’app e usa **Ripristina backup** con quel file.

L’accesso dell’alimentatore non fa parte di un backup. Sul nuovo telefono, [collega di nuovo il tuo alimentatore](/it/getting-started#link-feeder) per avere Pro.

Il ripristino è garantito solo per la stessa versione dell’app, e l’app avvisa quando le versioni sono diverse. Un backup creato da una versione più recente potrebbe richiedere un aggiornamento dell’app prima di poter essere ripristinato.

## Le etichette AR puntano nella direzione sbagliata
{: #ar-calibration }

La bussola del telefono va calibrata. Nella vista AR, tocca **Aiuto**, poi muovi il telefono disegnando un 8 più volte. Stai lontano da metalli e magneti, come un’auto o una custodia con magnete. La [guida alla calibrazione](https://support.google.com/maps/answer/2839911) di Google ha altri consigli.

## Il mio alimentatore è offline ma non ho ricevuto nessuna notifica
{: #feeder-offline }

L’app avvisa solo dopo che l’alimentatore è offline da almeno il tempo scelto in **Notifica quando offline**, quindi le interruzioni brevi non la attivano. Il server dell’app conferma un’interruzione con almeno due controlli e non considera offline nessun alimentatore mentre airplanes.live stesso ha problemi o molti alimentatori cadono insieme. Le notifiche del server arrivano con la sincronizzazione in background dell’app (**Impostazioni** › **Generale** › **Sincronizzazione in background**). Senza il monitoraggio del server, quando l’alimentatore mostra "Monitoring unavailable", non ci sono notifiche offline.

Controlla anche che **Notifica quando offline** non sia su **Disattivato** per quell’alimentatore, che la notifica non sia stata posticipata e che Android consenta le notifiche dell’app. Vedi [Notifiche offline](/it/feeders#offline).
