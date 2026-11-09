---
title: Alimentatori
description: "Aggiungi il tuo alimentatore airplanes.live, segui i suoi tassi di messaggi e ricevi una notifica quando va offline."
permalink: /it/feeders
translation_key: feeders
lang: it
source_hash: d2dc603bfa2095ce
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

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="La schermata per aggiungere un alimentatore, con i campi ID, etichetta, indirizzo IP e posizione"></a>

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

L’intestazione indica se tutti gli alimentatori sono online. Ogni alimentatore mostra il suo stato, il suo tasso di messaggi Beast (MSG/s), la velocità dei dati (KBit/s) e le statistiche MLAT con la quota di valori anomali e il numero di peer. Ordina l’elenco per **Etichetta** o **Tasso di messaggi**.

Quando il server dell’app monitora i tuoi alimentatori, lo stato arriva dal server, per esempio "In attesa del primo controllo", "Offline da …" o "Possibile interruzione di airplanes.live". I problemi trovati dal server compaiono sotto, come "client MLAT mancante". Il piano gratuito monitora meno alimentatori rispetto a un alimentatore collegato. Quelli oltre il limite mostrano "Non monitorato, limite di feeder raggiunto" e un pulsante **Aumenta limite** per [collegare il tuo alimentatore](/it/getting-started#link-feeder).

Tocca un alimentatore per altre opzioni:

- **Mostra alimentazione sulla mappa**: apre globe.airplanes.live nel browser, mostrando cosa riceve questo alimentatore.
- **Rinomina** e **Imposta indirizzo IP**.
- **TAR1090** e **GRAPHS1090**: aprono le pagine web dell’alimentatore. Compaiono dopo aver impostato un indirizzo IP.
- **Monitoraggio**: lo stato e i problemi di questo alimentatore secondo il server, e i controlli che non hanno ancora dati sufficienti. Compare solo quando il server monitora i tuoi alimentatori.
- **Statistiche**: grafici di 30 giorni dei tassi di messaggi e dei valori anomali MLAT. Quando il server monitora i tuoi alimentatori, i grafici mostrano il tasso di messaggi, i peer MLAT e i valori anomali MLAT dalla cronologia oraria del server.
- **Genera codice QR**, vedi sotto.
- **Notifica quando offline**, vedi sotto.
- **Rimuovi alimentatore**.

## Notifiche offline
{: #offline }

**Notifica quando offline** stabilisce per quanto tempo un alimentatore può restare offline prima che tu riceva una notifica: **Disattivato**, 6, 12, 24 o 48 ore. I nuovi alimentatori partono da 48 ore. Attivarla chiede il permesso per le notifiche su Android 13 e versioni successive.

Quando il server dell’app lo offre, monitora i tuoi alimentatori, anche con l’app chiusa. Un alimentatore è considerato offline quando non ha avuto connessione con airplanes.live per il tempo scelto, confermato da almeno due controlli. Il server non considera offline nessun alimentatore mentre airplanes.live stesso ha problemi o molti alimentatori cadono insieme. Ricevi allora "Alimentatore offline" e, quando torna, "Alimentatore di nuovo online".

Il server cerca anche problemi e invia "Problemi dell'alimentatore" quando ne trova uno:

- client MLAT mancante
- nessun peer MLAT
- errori di sincronizzazione MLAT
- molti valori anomali MLAT
- riceve molti meno messaggi del solito
- si riconnette di continuo
- ID alimentatore usato da più alimentatori

Disattivali in **Impostazioni** › **Alimentatore** › **Avvisi di problemi**.

Le notifiche del server arrivano con la sincronizzazione in background delle tue osservazioni, quindi possono richiedere fino a un intervallo di sincronizzazione. Vedi [Ogni quanto si sincronizza l’app](/it/watchlist#interval).

Tocca **Posticipa** su una notifica per silenziare quell’alimentatore finché non torna online. Per i problemi di un alimentatore online, la posticipazione dura finché non si risolvono.

Senza il server, l’app interroga airplanes.live da sola e mostra "Alimentatori offline rilevati" quando un alimentatore non è stato visto per il tempo scelto. Imposta la frequenza in **Impostazioni** › **Alimentatore** › **Intervallo di controllo**, da 15 minuti a un giorno. Il valore predefinito è ogni 60 minuti. Questa impostazione compare solo quando il server non monitora i tuoi alimentatori.

## Condividere un alimentatore
{: #share }

**Genera codice QR** mostra un codice QR con le impostazioni dell’alimentatore. Scansionalo con **Scansiona codice QR** nell’app su un altro dispositivo per aggiungere lì lo stesso alimentatore.
