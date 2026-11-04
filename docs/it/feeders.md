---
title: Alimentatori
description: "Aggiungi il tuo alimentatore airplanes.live, segui i suoi tassi di messaggi e ricevi una notifica quando va offline."
permalink: /it/feeders
translation_key: feeders
lang: it
source_hash: ab476bfcfb3e99fb
---
# Alimentatori
{: #feeders }

Un alimentatore è un ricevitore, di solito un Raspberry Pi con un’antenna, che invia ad airplanes.live i segnali degli aeromobili che riceve. La scheda **Alimentatore** segue i tuoi alimentatori e ti avvisa quando uno va offline. Per configurarne uno, vedi la [guida di configurazione di airplanes.live](https://airplanes.live/get-started/).

Aggiungere qui un alimentatore e [collegarlo](/it/getting-started#link-feeder) per Pro sono passaggi separati. Puoi fare l’uno senza l’altro.

## Trovare l’ID del tuo alimentatore
{: #feeder-id }

Ogni alimentatore ha un ID, un UUID come `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. Su un alimentatore configurato con lo script di alimentazione di airplanes.live, mostralo con:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

Sull’immagine per alimentatori di airplanes.live, il file è `/boot/airplanes-uuid`.

## Aggiungere un alimentatore
{: #add }

Tocca il pulsante **+** nella scheda Alimentatore. Ci sono tre modi per compilare **Nuovo alimentatore**:

- **Rileva Alimentatore Locale**: trova gli alimentatori sulla stessa rete del telefono e compila i campi. Se ne trova più di uno, scegline uno.
- **Scansiona codice QR**: scansiona un codice QR di un alimentatore creato dall’app su un altro dispositivo, vedi [Condividere un alimentatore](#share).
- Digita tu stesso l’**ID alimentatore**.

Campi facoltativi:

- **Etichetta**: un nome per l’alimentatore. Senza, l’app usa il nome MLAT dell’alimentatore su airplanes.live, oppure gli ultimi cinque caratteri del suo ID.
- **Indirizzo IP**: dove gira tar1090 o graphs1090 per questo alimentatore, come `192.168.0.42`. L’app mostra poi dei pulsanti per aprirli.
- **Posizione**: latitudine e longitudine, come `50.8006, 6.0619`.

## Elenco degli alimentatori
{: #list }

L’intestazione indica se tutti gli alimentatori sono online. Ogni alimentatore mostra il suo tasso di messaggi Beast (MSG/s), la velocità dei dati (KBit/s) e le statistiche MLAT con la quota di valori anomali e il numero di peer. Ordina l’elenco per **Etichetta** o **Tasso di messaggi**.

Tocca un alimentatore per altre opzioni:

- **Mostra alimentazione sulla mappa**: apre globe.airplanes.live nel browser, mostrando cosa riceve questo alimentatore.
- **Rinomina** e **Imposta indirizzo IP**.
- **TAR1090** e **GRAPHS1090**: aprono le pagine web dell’alimentatore. Compaiono dopo aver impostato un indirizzo IP.
- **Statistiche**: grafici di 30 giorni dei tassi di messaggi e dei valori anomali MLAT.
- **Genera codice QR**, vedi sotto.
- **Notifica quando offline**, vedi sotto.
- **Rimuovi alimentatore**.

## Notifiche offline
{: #offline }

**Notifica quando offline** è attivo per i nuovi alimentatori. Un alimentatore è considerato offline quando airplanes.live non lo vede da 48 ore. L’app mostra allora "Alimentatori offline rilevati" con il nome dell’alimentatore. Attivare l’impostazione chiede il permesso per le notifiche su Android 13 e versioni successive.

Tocca **Posticipa** sulla notifica per silenziarla finché quell’alimentatore non invia di nuovo dati. La notifica scompare da sola quando l’alimentatore torna attivo.

L’app controlla i tuoi alimentatori in background. Imposta la frequenza in **Impostazioni** › **Alimentatore** › **Intervallo di controllo**, da 15 minuti a un giorno. Il valore predefinito è ogni 60 minuti.

## Condividere un alimentatore
{: #share }

**Genera codice QR** mostra un codice QR con le impostazioni dell’alimentatore. Scansionalo con **Scansiona codice QR** nell’app su un altro dispositivo per aggiungere lì lo stesso alimentatore.
