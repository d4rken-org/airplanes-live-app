---
title: Recherche
description: "Trouvez des aéronefs par indicatif, immatriculation, code hex, type, squawk, numéro de vol ou route, et affinez les résultats avec des filtres."
permalink: /fr/search
translation_key: search
lang: fr
source_hash: 9935e0ce8f3fcb13
---
# Recherche
{: #search }

La recherche trouve les aéronefs qu’airplanes.live suit en ce moment. Tapez dans le champ de recherche et appuyez sur **Rechercher**.

## Ce que vous pouvez rechercher
{: #terms }

| Vous tapez | Résultat |
|---|---|
| `DLH400` | un indicatif |
| `D-AIBL` | une immatriculation |
| `3C6589` | un code hex (l’adresse OACI 24 bits de l’aéronef) |
| `A320` | un type d’aéronef |
| `7700` | un code squawk (transpondeur) |
| `FR5769` | l’aéronef qui assure un numéro de vol passagers |
| `FR EIN FAO` | les vols par compagnie, aéroport de départ et d’arrivée |

Les mots se restreignent mutuellement : `DLH A320` trouve les A320 de Lufthansa. Séparez les alternatives par des virgules : `7500, 7600, 7700` trouve les trois codes squawk.

Beaucoup de compagnies émettent un autre code que le numéro de vol figurant sur votre billet. Si un numéro de vol ne donne rien, essayez la route indiquée sur votre carte d’embarquement, comme `FR EIN FAO`. Les numéros de vol et les routes fonctionnent comme un terme à part entière ; combinés avec d’autres mots ou avec **À proximité**, ils ne donnent aucun résultat.

## Filtres
{: #filters }

- **Militaire** : uniquement les aéronefs militaires.
- **Confidentialité** : uniquement les aéronefs inscrits aux programmes de confidentialité LADD et PIA.
- **À proximité** : les aéronefs autour d’un lieu au lieu d’un terme de recherche. Le point de départ est **Ma position**. Appuyez sur **Modifier** pour chercher près d’un nom de lieu comme « Berlin, Allemagne ». Certains appareils ne savent pas rechercher les noms de lieux ; seule **Ma position** fonctionne alors.

**Militaire** et **Confidentialité** restreignent la recherche. **À proximité** regarde autour d’un lieu.

## Résultats
{: #results }

Si vous autorisez l’accès à la position, les résultats sont triés selon leur distance par rapport à vous. Les résultats marqués **Mis en cache** proviennent de recherches précédentes et peuvent être obsolètes. Appuyez sur un résultat pour voir ses détails. Appuyez longuement sur des résultats pour en sélectionner plusieurs, puis affichez-les ensemble sur la carte.

## Limites
{: #limits }

Avec le niveau gratuit, une recherche renvoie un nombre plafonné de résultats (« Affichage de X sur Y correspondances ») et il existe un quota quotidien de recherches (« Il reste X recherches sur Y aujourd’hui »). Pro supprime le plafond et augmente fortement le quota. Voir [Gratuit et Pro](/fr/getting-started#free-and-pro).
