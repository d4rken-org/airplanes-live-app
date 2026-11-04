---
title: Liste de surveillance et alertes
description: "Surveillez des aéronefs, des vols, des codes squawk et des zones, et recevez une notification quand ils apparaissent."
permalink: /fr/watchlist
translation_key: watchlist
lang: fr
source_hash: 5420d75e7f3d158e
---
# Liste de surveillance et alertes
{: #watchlist }

Une surveillance garde un œil sur quelque chose pour vous : un aéronef, un vol, un code squawk ou une zone. L’onglet **Liste de surveillance** affiche chaque surveillance avec ce que la dernière vérification a trouvé. Activez sa notification et l’application vous prévient quand cela apparaît.

## Ajouter une surveillance
{: #add }

Appuyez sur le bouton **+** de l’onglet Liste de surveillance et choisissez un type :

- **Aéronef (code hex)** : un aéronef par son code hex, l’adresse OACI 24 bits comme `3C6589`. Vous pouvez aussi appuyer sur **Ajouter à la liste de surveillance** dans les détails d’un aéronef sur la carte.
- **Vol (indicatif)** : un vol par l’indicatif qu’il émet, comme `DLH400`.
- **Code transpondeur (SQUAWK)** : tout aéronef qui émet un code squawk, comme `7700` pour les urgences.
- **Position (rayon)** : les aéronefs près d’un lieu, voir plus bas.

Chaque surveillance peut avoir une note, pour vous rappeler pourquoi vous l’avez ajoutée.

Les surveillances par squawk et par position font partie du niveau Pro. Voir [Gratuit et Pro](/fr/getting-started#free-and-pro).

### Surveillances de position
{: #location }

Une surveillance de position compte les aéronefs dans un rayon autour d’un point. Saisissez des coordonnées ou un nom de lieu et appuyez sur **Résoudre**, ou appuyez sur **Utiliser ma position**. Donnez-lui ensuite une étiquette et choisissez le rayon. Le plus petit rayon est de 2 km ; sa taille maximale dépend de votre forfait.

Vous pouvez aussi appuyer longuement sur la carte pour surveiller la zone autour de ce point. Déplacer ou redimensionner la zone par la suite remet à zéro l’historique de la surveillance.

## Recevoir des notifications
{: #notifications }

Les notifications sont désactivées pour une nouvelle surveillance. Ouvrez la surveillance et appuyez sur **Notifier : désactivé** pour passer à **Notifier : activé**.

Vous recevez une notification quand une surveillance passe de rien à quelque chose : un aéronef est repéré, un indicatif est en vol, un code squawk est émis ou des aéronefs apparaissent dans votre zone. La notification ne se répète que si la surveillance n’a rien vu entre-temps. Si quelque chose correspond déjà lors de la toute première vérification de la surveillance, cette première correspondance ne déclenche pas de notification.

Quand de nombreuses alertes arrivent en même temps, l’application en affiche quelques-unes et résume les autres sous « Alertes de liste de surveillance manquées ». Appuyez sur une notification pour ouvrir sa surveillance.

Les alertes utilisent le canal de notification « Alertes de liste de surveillance ». Android doit autoriser les notifications de l’application. Voir [Je ne reçois pas d’alertes](/fr/faq#no-alerts).

## Fréquence des vérifications
{: #interval }

L’application vérifie vos surveillances en arrière-plan, quand elle dispose d’une connexion réseau. Réglez la fréquence dans **Paramètres** › **Liste de surveillance** › **Intervalle de vérification**. Par défaut, c’est toutes les 60 minutes. Le niveau gratuit permet au minimum 60 minutes, Pro 15 minutes.

L’application vérifie aussi quand vous ouvrez la liste de surveillance et que la dernière vérification date de plus de 15 minutes. Tirez la liste vers le bas pour vérifier immédiatement.

## Détails d’une surveillance
{: #details }

Appuyez sur une surveillance pour voir :

- **Aéronefs correspondants** : ce que la dernière vérification a trouvé. Appuyez sur l’un d’eux pour voir ses détails.
- **Activité, 7 derniers jours** : une grille horaire des moments où la surveillance a vu quelque chose.
- **Aéronefs à chaque vérification, 30 jours** : les nombres relevés lors des vérifications faites par cette application.

Le menu **Plus** contient **Modifier la note**, **Modifier la zone** pour les surveillances de position, et **Supprimer la surveillance**.

## Limites
{: #limits }

Le niveau gratuit vérifie un nombre limité de surveillances, indiqué par « X surveillances gratuites sur Y utilisées ». Les surveillances au-delà de la limite restent dans votre liste avec la mention « Non vérifié, au-delà de ta limite ». Ce sont les premières surveillances ajoutées qui sont vérifiées. Pro les vérifie toutes.
