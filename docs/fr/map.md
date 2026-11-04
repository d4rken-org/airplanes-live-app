---
title: Carte
description: "Utilisez la carte en direct : détails des aéronefs, étiquettes et filtres, suivi d’un aéronef, relecture de l’historique et vue AR."
permalink: /fr/map
translation_key: map
lang: fr
source_hash: fcc8a284d66c8fc7
---
# Carte
{: #map }

La carte affiche les aéronefs qu’airplanes.live reçoit en ce moment. En vue dézoomée, l’application n’affiche qu’une partie des aéronefs visibles et l’indique (« Affichage de X aéronefs sur Y. Zoomer pour en voir davantage. »). Zoomez pour les voir tous.

Les boutons au bord de la carte :

- **Contrôles de la carte** : options d’affichage, étiquettes et filtres, voir plus bas.
- **Liste des aéronefs** : la liste des aéronefs visibles. Filtrez-la par texte ou sur les aéronefs **Surveillés**, et triez-la par indicatif, type, altitude ou vitesse.
- **Ma position** : déplace la carte là où vous êtes. L’application demande votre position approximative la première fois.
- **Vue AR** : voir [Vue AR](#ar-view). Ce bouton n’apparaît que sur les appareils dotés des capteurs de mouvement nécessaires.
- **Plein écran** : donne tout l’écran à la carte.

## Détails d’un aéronef
{: #aircraft-details }

Appuyez sur un aéronef pour ouvrir ses détails : route (départ et arrivée), immatriculation, code hex, cellule, opérateur, pays, altitude, vitesse, squawk et plus encore, avec une photo de Planespotters.net quand il en existe une. Un bandeau avertit des codes squawk d’urgence 7500, 7600 et 7700.

Depuis les détails, vous pouvez :

- **Suivre** l’aéronef, pour que la carte le garde en vue, jusqu’à ce que vous appuyiez sur **Ne plus suivre**.
- **Ajouter à la liste de surveillance**, pour être averti quand il vole à nouveau. Voir [Liste de surveillance et alertes](/fr/watchlist).
- **Afficher dans la recherche**, ou partager un lien vers l’aéronef sur globe.airplanes.live.
- Ouvrir son **Historique**, voir plus bas.

## Contrôles de la carte
{: #map-controls }

**Affichage**

- **Étiquettes** : afficher un texte à côté de chaque aéronef.
- **Toutes les pistes** : afficher la trajectoire récente de chaque aéronef.
- **Mettre en évidence les militaires** : marquer les aéronefs militaires d’un badge.
- **Mettre en évidence les surveillés** : marquer d’un badge les aéronefs qui correspondent à vos surveillances et entourer les zones que vous surveillez.

**Label content** choisit ce qu’affichent les étiquettes : **Callsign** (ou le code hex quand l’aéronef n’en émet pas), **Aircraft type**, **Altitude** et **Speed**. Au moins une option reste activée.

**Filtre**

- **Militaire** : masquer tous les aéronefs qui ne sont pas militaires.
- **Isoler** : masquer tous les autres aéronefs. Sélectionnez ou épinglez d’abord des aéronefs.

**Sélection**

- **Sélection multiple** : appuyer sur des aéronefs les épingle au lieu d’ouvrir leurs détails. Vous pouvez épingler jusqu’à 100 aéronefs. Appuyez sur la puce « épinglés » pour les retirer.

En bas, **Ouvrir sur airplanes.live** ouvre la même vue sur globe.airplanes.live dans votre navigateur, et **Réinitialiser la carte** efface les épingles et la sélection, puis dézoome sur le monde entier.

Le style de base de la carte se règle dans **Paramètres** › **Carte** › **Style de carte**. **Automatique** suit le thème clair ou sombre de l’application.

## Historique
{: #history }

**Historique** rejoue les trajets d’un aéronef au cours des 7 derniers jours. Passez d’un jour à l’autre avec **Jour précédent** et **Jour suivant**, faites glisser le curseur **Heure de la journée**, ou appuyez sur **Lecture** et choisissez une vitesse de lecture.

L’historique fait partie du niveau Pro. Voir [Gratuit et Pro](/fr/getting-started#free-and-pro).

## Surveiller une zone
{: #watch-area }

Appuyez longuement sur la carte pour surveiller la zone autour de ce point. L’application ouvre **Surveiller cette zone** avec la position déjà remplie. Appuyez longuement sur une zone que vous surveillez déjà pour la modifier. Les surveillances de position font partie du niveau Pro. Voir [Liste de surveillance et alertes](/fr/watchlist#location).

## Vue AR
{: #ar-view }

La vue AR affiche l’image de la caméra avec des étiquettes pour les aéronefs situés dans cette direction. Elle a besoin de l’accès à la caméra pour montrer le ciel et de l’accès à la position pour trouver les aéronefs près de vous. Appuyez sur une étiquette pour ouvrir cet aéronef sur la carte.

Utilisez le curseur de portée pour choisir jusqu’où regarder, de 1 à 50 NM. Pro permet une portée plus grande que le niveau gratuit.

Si les étiquettes sont dans la mauvaise direction, votre boussole doit être calibrée : appuyez sur **Aide** dans la vue AR et déplacez votre téléphone en forme de huit. Voir [Les étiquettes AR pointent dans la mauvaise direction](/fr/faq#ar-calibration).
