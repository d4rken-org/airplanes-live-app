---
title: FAQ et dépannage
description: "Réponses sur les alertes manquantes, les quotas quotidiens, l’accès à la position et à la caméra, les sauvegardes et la vue AR."
permalink: /fr/faq
translation_key: faq
lang: fr
source_hash: 53120a21276c35b2
---
# FAQ et dépannage
{: #faq }

## Je ne reçois pas d’alertes
{: #no-alerts }

Vérifiez ces points dans l’ordre :

1. **La notification est activée.** Les nouvelles surveillances démarrent avec les notifications désactivées. Ouvrez la surveillance et passez de **Notifier : désactivé** à **Notifier : activé**.
2. **Android autorise les notifications de l’application.** Ouvrez les paramètres d’Android › **Applications** › **airplanes.live** › **Notifications**, autorisez-les et vérifiez que le canal « Alertes de liste de surveillance » est activé.
3. **La surveillance est vérifiée.** Dans la liste de surveillance, une surveillance marquée « Non vérifié, au-delà de ta limite » dépasse la limite du niveau gratuit. Voir [Limites](/fr/watchlist#limits).
4. **Quelque chose de nouveau est apparu.** Une alerte arrive quand une surveillance passe de rien à quelque chose. Si un aéronef correspondait déjà lors de la première vérification de la surveillance, cette correspondance ne déclenche pas d’alerte. L’alerte suivante arrive après qu’il a disparu puis réapparu.
5. **Android laisse l’application fonctionner en arrière-plan.** Par défaut, les vérifications ont lieu toutes les 60 minutes. Les économiseurs de batterie peuvent les retarder davantage. Si les alertes arrivent en retard, réglez l’utilisation de la batterie de l’application sur **Non restreinte** dans les paramètres de l’application sur Android.

## Pourquoi la carte n’affiche-t-elle que certains aéronefs ?
{: #map-capped }

En vue dézoomée, la carte n’affiche qu’une partie des aéronefs visibles et l’indique en haut. Zoomez pour les voir tous. Si la carte indique « Quota quotidien de carte épuisé », le quota du niveau gratuit pour aujourd’hui est consommé. Voir [Gratuit et Pro](/fr/getting-started#free-and-pro).

## Que signifie « Quota quotidien de recherche épuisé » ?
{: #allowance }

Le niveau gratuit autorise un certain nombre de recherches et de consultations par jour. Le bandeau indique quand le quota est réinitialisé. Lier un alimentateur augmente fortement les quotas. Voir [Gratuit et Pro](/fr/getting-started#free-and-pro).

## Pourquoi un aéronef manque-t-il ?
{: #missing-aircraft }

airplanes.live ne voit que les aéronefs à portée d’un alimentateur qui émettent en ADS-B ou sont localisés par MLAT. La couverture est faible au-dessus des océans et dans certaines régions, et les aéronefs au sol ou volant à basse altitude sont plus difficiles à recevoir. Vous pouvez aider en [faisant tourner un alimentateur](https://airplanes.live/get-started/).

## Pourquoi l’application demande-t-elle ma position ?
{: #location }

Uniquement pour les fonctions qui en ont besoin : **Ma position** sur la carte, le tri des résultats de recherche par distance, la recherche **À proximité** et **Utiliser ma position** lors de l’ajout d’une surveillance de position utilisent votre position approximative. La vue AR a besoin de votre position précise et de la caméra. Tout le reste fonctionne sans accès à la position.

## Comment transférer mes surveillances et alimentateurs vers un nouveau téléphone ?
{: #backup }

Ouvrez **Paramètres** › **Sauvegarde et restauration** et appuyez sur **Créer une sauvegarde**. Choisissez ce qu’elle contient : les **Surveillances** avec leur historique de vérification et les **Alimentateurs** avec leurs statistiques. L’application enregistre un fichier `.apl.zip`. Sur le nouveau téléphone, installez l’application et utilisez **Restaurer une sauvegarde** avec ce fichier.

L’accès lié à l’alimentateur ne fait pas partie d’une sauvegarde. Sur le nouveau téléphone, [liez à nouveau votre alimentateur](/fr/getting-started#link-feeder) pour obtenir Pro.

La restauration n’est garantie que pour la même version de l’application, et l’application vous avertit quand les versions diffèrent. Une sauvegarde créée par une version plus récente peut nécessiter une mise à jour de l’application avant de pouvoir être restaurée.

## Les étiquettes AR pointent dans la mauvaise direction
{: #ar-calibration }

La boussole de votre téléphone doit être calibrée. Dans la vue AR, appuyez sur **Aide**, puis déplacez votre téléphone en forme de huit plusieurs fois. Tenez-vous à l’écart du métal et des aimants, comme une voiture ou une coque de téléphone aimantée. Le [guide de calibrage](https://support.google.com/maps/answer/2839911) de Google donne d’autres conseils.

## Mon alimentateur est hors ligne mais je n’ai reçu aucune notification
{: #feeder-offline }

L’application avertit quand airplanes.live n’a pas vu l’alimentateur depuis 48 heures, donc les courtes coupures ne la déclenchent pas. Vérifiez aussi que **Notifier quand hors ligne** est activé pour cet alimentateur, que la notification n’a pas été reportée et qu’Android autorise les notifications de l’application. Voir [Notifications hors ligne](/fr/feeders#offline).

## L’application collecte-t-elle des données sur moi ?
{: #privacy }

Seulement ce dont elle a besoin pour fonctionner. Pour vous montrer les avions, l’application envoie au serveur du développeur la zone de la carte que vous regardez, vos recherches et surveillances, et votre position quand une fonction en a besoin. Elle le fait sous un identifiant d’installation aléatoire, pas sous un compte. Il n’y a ni publicité ni analyse, et rien n’est vendu. Vous pouvez supprimer vos données à tout moment avec **Supprimer mes données** dans **Paramètres**. Les détails, y compris la durée de conservation des données, sont dans la [politique de confidentialité](/privacy).
