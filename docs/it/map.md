---
title: Mappa
description: "Usa la mappa in tempo reale: dettagli degli aeromobili, etichette e filtri, seguire un aeromobile, riproduzione della cronologia e vista AR."
permalink: /it/map
translation_key: map
lang: it
source_hash: 44db13a43bbb6642
---
# Mappa
{: #map }

La mappa mostra gli aeromobili che airplanes.live sta ricevendo. Con lo zoom indietro, l’app mostra solo una parte degli aeromobili inquadrati e lo segnala ("Mostrati X di Y aeromobili. Ingrandisci per vederne altri."). Ingrandisci per vederli tutti.

I pulsanti lungo il bordo della mappa:

- **Controlli mappa**: opzioni di visualizzazione e delle etichette, vedi sotto.
- **Lista aeromobili**: un elenco degli aeromobili inquadrati. Filtralo per testo, per aeromobili **Osservati**, per **Militare**, **Categoria** e **Selezionati e fissati**, e ordinalo per nominativo, tipo, altitudine o velocità. Vedi i filtri sotto.
- **La mia posizione**: sposta la mappa dove ti trovi. La prima volta l’app chiede la tua posizione approssimativa.
- **Vista AR**: vedi [Vista AR](#ar-view). Compare solo sui dispositivi con i sensori di movimento necessari.
- **Schermo intero**: dà alla mappa tutto lo schermo.

## Dettagli dell’aeromobile
{: #aircraft-details }

Tocca un aeromobile per aprirne i dettagli: rotta (da e per), immatricolazione, codice hex, cellula, operatore, paese, altitudine, velocità, squawk e altro, con una foto da Planespotters.net quando disponibile. Un banner avvisa dei codici squawk di emergenza 7500, 7600 e 7700.

<a class="guide-shot" href="/assets/images/map/aircraft-details.webp"><img src="/assets/images/map/aircraft-details.webp" width="540" height="1200" loading="lazy" alt="Dettagli di un aeromobile sulla mappa, con i dati di volo e la traccia recente"></a>

Dai dettagli puoi:

- seguire l’aeromobile con **Segui**, così la mappa lo tiene inquadrato, finché non tocchi **Smetti di seguire**.
- usare **Aggiungi a lista di osservazione**. Imposta la nuova osservazione su **Notifica: attiva** per ricevere un avviso quando l’aeromobile viene avvistato di nuovo. Vedi [Lista di osservazione e avvisi](/it/watchlist).
- usare **Mostra nella ricerca**, oppure condividere un link all’aeromobile su globe.airplanes.live.
- aprire la sua **Cronologia**, vedi sotto.

## Controlli mappa
{: #map-controls }

<a class="guide-shot" href="/assets/images/map/map-controls.webp"><img src="/assets/images/map/map-controls.webp" width="540" height="1200" loading="lazy" alt="I controlli della mappa con le opzioni di visualizzazione e delle etichette"></a>

**Visualizzazione**

- **Tutte le tracce**: mostra il percorso recente di ogni aeromobile.
- **Evidenzia militari**: contrassegna gli aeromobili militari con un badge.
- **Evidenzia osservati**: contrassegna con un badge gli aeromobili che corrispondono alle tue osservazioni e delinea le aree che osservi.

**Etichette**

- **Nominativo** (o il codice hex quando l’aeromobile non ne trasmette uno), **Tipo di aeromobile**, **Altitudine** e **Velocità** scelgono cosa mostrano le etichette. Ognuno si attiva o disattiva per conto suo; in una nuova installazione sono tutti disattivati all’inizio.
- Con tutti disattivati la mappa non mostra etichette, e un aeromobile toccato mostra comunque il suo nominativo.

**Selezione**

- **Selezione multipla**: toccando gli aeromobili li fissi invece di aprirne i dettagli. Puoi fissare fino a 100 aeromobili. Tocca il chip "fissati" per rimuoverli.

In fondo, **Apri su airplanes.live** apre la stessa vista su globe.airplanes.live nel browser, e **Reimposta mappa** rimuove gli aeromobili fissati e la selezione e riduce lo zoom al mondo intero.

**Filtri nella lista aeromobili**

- **Militare**: nasconde gli aeromobili non militari. Gli aeromobili selezionati e fissati restano visibili.
- **Categoria**: un menu con **Aerei**, **Elicotteri**, **Alianti**, **Aerostati**, **Droni**, **Veicoli di terra** e **Sconosciuti**; la mappa mostra solo i tipi scelti. **Tutte le categorie** disattiva questo filtro. Gli aeromobili selezionati e fissati restano visibili.
- **Selezionati e fissati**: nasconde tutti gli altri aeromobili. Seleziona o fissa prima degli aeromobili.

Ogni filtro attivo compare come chip sulla mappa; tocca il chip per disattivare il filtro. Quando nessun aeromobile corrisponde, **Cancella filtri** nella lista li disattiva tutti. **Militare** e **Categoria** restano impostati al riavvio dell’app; il filtro di testo e **Osservati** si azzerano.

Lo stile di base della mappa si imposta in **Impostazioni** › **Mappa** › **Stile mappa**. **Automatico (chiaro o scuro)** segue il tema dell’app. Gli altri stili fanno parte del piano Pro. Vedi [Gratuito e Pro](/it/getting-started#free-and-pro).

## Cronologia
{: #history }

**Cronologia** riproduce dove ha volato un aeromobile negli ultimi 7 giorni. Scorri i giorni con **Giorno precedente** e **Giorno successivo**, trascina il cursore **Ora del giorno**, oppure tocca **Riproduci** e scegli una velocità di riproduzione.

La cronologia fa parte del piano Pro. Vedi [Gratuito e Pro](/it/getting-started#free-and-pro).

## Osservare un’area
{: #watch-area }

Tieni premuto sulla mappa per osservare l’area intorno a quel punto. L’app apre **Osserva quest’area** con la posizione già inserita. Per modificare un’area che osservi già, attiva **Evidenzia osservati** e tieni premuto il punto al centro dell’area. Le osservazioni per posizione fanno parte del piano Pro. Vedi [Lista di osservazione e avvisi](/it/watchlist#location).

## Vista AR
{: #ar-view }

La vista AR mostra l’immagine della fotocamera con le etichette degli aeromobili in quella direzione. Ha bisogno dell’accesso alla fotocamera per mostrare il cielo e dell’accesso alla posizione per trovare gli aeromobili vicino a te. Tocca un’etichetta per aprire quell’aeromobile sulla mappa.

Usa il cursore della distanza per scegliere quanto lontano guardare, da 1 a 50 NM. Pro consente una distanza maggiore rispetto al piano gratuito.

Se le etichette sono nella direzione sbagliata, la bussola va calibrata: tocca **Aiuto** nella vista AR e muovi il telefono disegnando un 8. Vedi [Le etichette AR puntano nella direzione sbagliata](/it/faq#ar-calibration).
