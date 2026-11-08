---
title: Lista di osservazione e avvisi
description: "Osserva aeromobili, voli, codici squawk e aree, e ricevi una notifica quando compaiono."
permalink: /it/watchlist
translation_key: watchlist
lang: it
source_hash: 71579111de9e79ca
---
# Lista di osservazione e avvisi
{: #watchlist }

Un’osservazione tiene d’occhio qualcosa per te: un aeromobile, un volo, un codice squawk o un’area. La lista di osservazione del server controlla le tue osservazioni, e la scheda **Lista di osservazione** mostra ciascuna con ciò che ha trovato l’ultimo controllo. Attiva la notifica di un’osservazione e l’app ti avvisa quando compare.

## Aggiungere un’osservazione
{: #add }

<a class="guide-shot" href="/assets/images/watchlist/add-watch.webp"><img src="/assets/images/watchlist/add-watch.webp" width="540" height="1200" loading="lazy" alt="La finestra per aggiungere un’osservazione, con i quattro tipi"></a>

Tocca il pulsante **+** nella scheda Lista di osservazione e scegli un tipo:

- **Aeromobile (codice hex)**: un aeromobile tramite il suo codice hex, l’indirizzo ICAO a 24 bit come `3C6589`. Puoi anche toccare **Aggiungi a lista di osservazione** nei dettagli di un aeromobile sulla mappa.
- **Volo (nominativo)**: un volo tramite il nominativo che trasmette, come `DLH400`.
- **Codice transponder (SQUAWK)**: qualsiasi aeromobile che trasmette un codice squawk, come `7700` per le emergenze.
- **Posizione (raggio)**: aeromobili vicino a un luogo, vedi sotto.

Ogni osservazione può avere una nota, così ricordi perché l’hai aggiunta.

Le osservazioni di squawk e di posizione fanno parte del piano Pro. Vedi [Gratuito e Pro](/it/getting-started#free-and-pro).

### Osservazioni per posizione
{: #location }

Un’osservazione per posizione conta gli aeromobili entro un raggio intorno a un punto. Inserisci delle coordinate o il nome di un luogo e tocca **Risolvi**, oppure tocca **Usa la mia posizione**. Poi dalle un’etichetta e scegli il raggio. Il raggio minimo è 2 km; quanto può essere grande dipende dal tuo piano.

Puoi anche tenere premuto sulla mappa per osservare l’area intorno a quel punto. Se in seguito sposti o ridimensioni l’area, la cronologia dell’osservazione riparte da zero.

## Ricevere notifiche
{: #notifications }

Le notifiche sono disattivate per una nuova osservazione. Apri l’osservazione e tocca **Notifica: disattiva** per passare a **Notifica: attiva**.

Ricevi una notifica quando un’osservazione passa da niente a qualcosa: viene avvistato un aeromobile, un nominativo è in volo, viene trasmesso un codice squawk o compaiono aeromobili nella tua area. La notifica si ripete solo se nel frattempo l’osservazione non ha visto nulla. Se qualcosa corrisponde già al primissimo controllo dell’osservazione, quella prima corrispondenza resta silenziosa.

Quando arrivano molti avvisi insieme, l’app ne mostra alcuni e riassume gli altri come "Avvisi di osservazione persi". Tocca una notifica per aprire la sua osservazione.

Gli avvisi usano il canale di notifica "Avvisi lista di osservazione". Android deve consentire le notifiche dell’app. Vedi [Non ricevo avvisi](/it/faq#no-alerts).

## Ogni quanto si sincronizza l’app
{: #interval }

È il server a controllare le tue osservazioni, non l’app. L’app scarica i risultati in background, quando ha una connessione di rete, ed è allora che arrivano i tuoi avvisi. Imposta la frequenza in **Impostazioni** › **Lista di osservazione** › **Intervallo di controllo**. Il valore predefinito è ogni 60 minuti. Il piano gratuito consente al minimo 60 minuti, Pro 15 minuti.

L’app si sincronizza anche quando apri la Lista di osservazione e l’ultima sincronizzazione risale a più di 15 minuti prima. Trascina verso il basso l’elenco per sincronizzare subito.

Un’osservazione che il server non ha ancora preso in carico mostra "In attesa del server". Se la lista di osservazione del server non è disponibile, la Lista di osservazione mostra "Le osservazioni non sono monitorate al momento". Il monitoraggio riprende appena l’app raggiunge il server.

## Dettagli dell’osservazione
{: #details }

Tocca un’osservazione per vedere:

- **Aeromobili corrispondenti**: ciò che ha trovato l’ultimo controllo. Toccane uno per vederne i dettagli.
- **Attività, ultimi 7 giorni**: una griglia oraria di quando l’osservazione ha visto qualcosa.
- **Aeromobili a ogni controllo, 30 giorni**: per le osservazioni di squawk e di posizione, il numero massimo di aeromobili contati dal server in ogni ora.

Il menu **Altro** contiene **Modifica nota**, **Modifica area** per le osservazioni per posizione, ed **Elimina osservazione**.

## Limiti
{: #limits }

Il piano gratuito controlla un numero limitato di osservazioni, indicato come "X di Y osservazioni gratuite usate". Le osservazioni oltre il limite restano nella lista come "Non controllata, oltre il limite". Vengono controllate le prime osservazioni che hai aggiunto. Pro le controlla tutte.
