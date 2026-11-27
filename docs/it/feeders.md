---
title: Ricevitori
description: "Aggiungi il tuo ricevitore per airplanes.live, segui i suoi tassi di messaggi e ricevi una notifica quando va offline."
permalink: /it/feeders
translation_key: feeders
lang: it
source_hash: 6e4f9db68d11263d
---
# Ricevitori
{: #feeders }

Un ricevitore, di solito un Raspberry Pi con un’antenna, capta i segnali degli aeromobili e li invia ad airplanes.live. La scheda **Ricevitore** segue i tuoi ricevitori e ti avvisa quando uno va offline. Per configurarne uno, vedi la [guida di configurazione di airplanes.live](https://airplanes.live/get-started/).

Aggiungere qui un ricevitore e [collegarlo](/it/getting-started#link-feeder) per Pro sono passaggi separati. Puoi fare l’uno senza l’altro.

## Trovare l’ID del tuo alimentatore
{: #feeder-id }

Ogni ricevitore ha un ID alimentatore, un UUID come `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. Su un ricevitore configurato con lo script di alimentazione di airplanes.live, mostralo con:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

Sull’immagine per alimentatori di airplanes.live, il file è `/boot/airplanes-uuid`.

## Aggiungere un ricevitore
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="La schermata per aggiungere un ricevitore, con i campi ID, etichetta, indirizzo IP e posizione"></a>

Tocca il pulsante **+** nella scheda Ricevitore. Ci sono tre modi per compilare **Nuovo ricevitore**:

- **Rileva Ricevitore Locale**: trova i ricevitori sulla stessa rete del telefono e compila i campi. Se ne trova più di uno, scegline uno.
- **Scansiona codice QR**: scansiona un codice QR di un ricevitore creato dall’app su un altro dispositivo, vedi [Condividere un ricevitore](#share).
- Digita tu stesso l’**ID alimentatore**.

Campi facoltativi:

- **Etichetta**: un nome per il ricevitore. Senza, l’app usa il nome MLAT del ricevitore su airplanes.live, oppure gli ultimi cinque caratteri del suo ID.
- **Indirizzo IP**: dove gira tar1090 o graphs1090 per questo ricevitore, come `192.168.0.42`. L’app mostra poi dei pulsanti per aprirli.
- **Posizione**: latitudine e longitudine, come `50.8006, 6.0619`.

## Elenco dei ricevitori
{: #list }

L’intestazione indica se tutti i ricevitori sono online. Ogni ricevitore mostra il suo stato. Quando il server dell’app monitora i tuoi ricevitori, ogni ricevitore mostra anche il suo tasso di messaggi Beast (MSG/s), la velocità dei dati (KBit/s) e le statistiche MLAT con la quota di valori anomali e il numero di peer. Senza il monitoraggio del server, un ricevitore mostra "Monitoraggio non disponibile" e nessun tasso. Ordina l’elenco per **Etichetta** o **Tasso di messaggi**.

Quando il server dell’app monitora i tuoi ricevitori, lo stato arriva dal server, per esempio "In attesa del primo controllo", "Offline da …" o "Possibile interruzione di airplanes.live". I problemi trovati dal server compaiono sotto, come "client MLAT mancante". Il piano gratuito monitora meno ricevitori rispetto a Pro. Quelli oltre il limite mostrano "Non monitorato, limite di ricevitori raggiunto" e un pulsante **Aumenta limite** per [collegare il tuo ricevitore](/it/getting-started#link-feeder).

Tocca un ricevitore per altre opzioni:

- **Mostra alimentazione sulla mappa**: apre globe.airplanes.live nel browser, mostrando cosa riceve questo ricevitore.
- **Rinomina** e **Imposta indirizzo IP**.
- **TAR1090** e **GRAPHS1090**: aprono le pagine web del ricevitore. Compaiono dopo aver impostato un indirizzo IP.
- **Monitoraggio**: lo stato e i problemi di questo ricevitore secondo il server, e i controlli che non hanno ancora dati sufficienti. Senza il monitoraggio del server, indica "Monitoraggio non disponibile".
- **Statistiche**: grafici del tasso di messaggi, dei peer MLAT e dei valori anomali MLAT dalla cronologia oraria del server. Compaiono solo quando il server monitora i tuoi ricevitori.
- **Genera codice QR**, vedi sotto.
- **Notifica quando offline**, vedi sotto.
- **Rimuovi ricevitore**.

## Notifiche offline
{: #offline }

**Notifica quando offline** stabilisce per quanto tempo un ricevitore può restare offline prima che tu riceva una notifica: **Disattivato**, 6, 12, 24 o 48 ore. I nuovi ricevitori partono da 48 ore. Attivarla chiede il permesso per le notifiche su Android 13 e versioni successive.

Quando il server dell’app lo offre, monitora i tuoi ricevitori, anche con l’app chiusa. Un ricevitore è considerato offline quando non ha avuto connessione con airplanes.live per il tempo scelto, confermato da almeno due controlli. Il server non considera offline nessun ricevitore mentre airplanes.live stesso ha problemi o molti ricevitori cadono insieme. Ricevi allora "Ricevitore offline" e, quando torna, "Ricevitore di nuovo online".

Il server cerca anche problemi e invia "Problemi del ricevitore" quando ne trova uno:

- client MLAT mancante
- nessun peer MLAT
- errori di sincronizzazione MLAT
- molti valori anomali MLAT
- riceve molti meno messaggi del solito
- si riconnette di continuo
- ID alimentatore inviato da più ricevitori

Disattivali in **Impostazioni** › **Ricevitore** › **Avvisi di problemi**.

Le notifiche del server arrivano con la sincronizzazione in background dell’app, quindi possono richiedere il tempo previsto dal livello scelto in **Impostazioni** › **Generale** › **Sincronizzazione in background**. Vedi [Ogni quanto si sincronizza l’app](/it/watchlist#interval).

Tocca **Posticipa** su una notifica per silenziare quel ricevitore finché non torna online. Per i problemi di un ricevitore online, la posticipazione dura finché non si risolvono.

Senza il monitoraggio del server non ci sono notifiche offline, e i tuoi ricevitori mostrano "Monitoraggio non disponibile".

## Condividere un ricevitore
{: #share }

**Genera codice QR** mostra un codice QR con le impostazioni del ricevitore. Scansionalo con **Scansiona codice QR** nell’app su un altro dispositivo per aggiungere lì lo stesso ricevitore.
