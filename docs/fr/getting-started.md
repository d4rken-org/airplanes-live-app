---
title: Premiers pas
description: "Installez l’application airplanes.live, découvrez ses onglets et ce que signifient la version gratuite et Pro."
permalink: /fr/getting-started
translation_key: getting-started
lang: fr
source_hash: 2336dd0632d887b1
---
# Premiers pas
{: #getting-started }

L’application officielle airplanes.live affiche le trafic aérien en direct d’[airplanes.live](https://airplanes.live), une communauté de personnes qui reçoivent les signaux des aéronefs (ADS-B et MLAT) avec leurs propres antennes et les partagent. Elle fonctionne sous Android 8.0 et versions ultérieures.

## Installation
{: #install }

Téléchargez l’APK depuis les [versions GitHub](https://github.com/d4rken-org/airplanes-live-app/releases/latest) ou installez-la depuis [IzzyOnDroid](https://apt.izzysoft.de/packages/eu.darken.apl/) avec un client F-Droid.

Les versions GitHub peuvent rechercher elles-mêmes les nouvelles versions. Activez **Vérification des mises à jour** pendant la configuration ou plus tard dans **Paramètres** › **Général**. Cette option est désactivée par défaut.

## Premier lancement
{: #first-launch }

L’application affiche un court message de bienvenue et un résumé de sa politique de confidentialité : dès que vous acceptez, l’application enregistre cette installation sur le serveur du développeur sous un identifiant aléatoire et lui envoie vos demandes, comme la zone de la carte que vous regardez, vos recherches, surveillances et alimentateurs, et votre position quand une fonction en a besoin. Elle a besoin d’un accès Internet. Appuyez sur **Accepter** pour continuer. Vous pouvez lire la [politique de confidentialité](/privacy) complète à tout moment depuis **Paramètres** › **Politique de confidentialité**.

## S’y retrouver
{: #tabs }

La barre du bas permet de passer d’un onglet à l’autre :

- **Carte** : les aéronefs en direct autour de vous ou n’importe où dans le monde. Voir [Carte](/fr/map).
- **Recherche** : trouver des aéronefs par indicatif, immatriculation, code hex, type ou squawk. Voir [Recherche](/fr/search).
- **Liste de surveillance** : les aéronefs, vols, codes squawk et zones que vous surveillez, avec des alertes facultatives. Voir [Liste de surveillance et alertes](/fr/watchlist).
- **Alimentateur** : les récepteurs avec lesquels vous alimentez airplanes.live. Voir [Alimentateurs](/fr/feeders). Si vous n’en avez pas, vous pouvez masquer cet onglet avec **Afficher l’onglet Alimentateur** dans **Paramètres** › **Alimentateur**. Le masquer n’arrête pas la surveillance des alimentateurs.
- **Paramètres** : thème, unités, style de carte, intervalles de synchronisation et de vérification, sauvegardes et support.

Une barre rouge indiquant « Internet n'est pas disponible. » signifie que l’application ne parvient pas à joindre le réseau. Les positions et les résultats de recherche restent alors dans leur dernier état.

## Gratuit et Pro
{: #free-and-pro }

L’application est gratuite. airplanes.live est géré par la communauté : sa couverture vient de personnes qui installent une antenne et aident à payer les serveurs, donc l’accès complet leur revient. La puce à côté du nom de l’application sur la carte affiche **GRATUIT** ou **PRO**. Appuyez dessus pour ouvrir **État de la mise à niveau**.

Le niveau gratuit comporte certaines limites, par exemple un quota quotidien de recherches et de données de carte, et un nombre plafonné de surveillances. L’application vous prévient quand vous en atteignez une.

Pro débloque :

- Des résultats de recherche non limités
- Des quotas quotidiens beaucoup plus élevés pour les recherches et les données d’aéronefs
- Des actualisations et mises à jour plus rapides, sur la carte et pour les surveillances
- Des surveillances illimitées
- Les surveillances par squawk et par position
- Un rayon plus grand pour la recherche à proximité et en AR
- L’historique des aéronefs
- Le mode, le style et les couleurs du thème, y compris les vôtres

### Lier votre alimentateur
{: #link-feeder }

Si vous alimentez airplanes.live, liez votre alimentateur et cette installation passe en Pro :

1. Connectez votre téléphone au même réseau que votre alimentateur, par exemple le Wi-Fi de votre domicile.
2. Ouvrez **État de la mise à niveau** et appuyez sur **Détecter** pour trouver l’alimentateur sur ce réseau, ou sur **Saisir l’ID** pour taper son [ID d’alimentateur](/fr/feeders#feeder-id).
3. Appuyez sur **Lier**.

Vous ne liez qu’une seule fois, depuis le réseau de votre alimentateur. Ensuite, Pro fonctionne partout. La liaison nécessite une connexion IPv4, car c’est ainsi que votre alimentateur est reconnu. Si la liaison échoue avec « Cet alimentateur n’est pas sur ce réseau », vérifiez que votre téléphone n’utilise pas les données mobiles ou un VPN.

La liaison vaut pour une installation. Après avoir réinstallé l’application ou changé de téléphone, liez-le à nouveau ; une [sauvegarde](/fr/faq#backup) ne la transfère pas.

Vous n’alimentez pas encore ? **Comment alimenter** sur l’écran de mise à niveau mène au [guide d’installation d’airplanes.live](https://airplanes.live/get-started/).

## Paramètres
{: #settings }

- **Général** : mode, style et couleur du thème (Pro), unités des aéronefs et de distance, et la vérification des mises à jour sur les versions GitHub.
- **Carte** : **Restaurer la dernière vue** et le **Style de carte**.
- **Liste de surveillance** : la fréquence de synchronisation avec la liste de surveillance du serveur.
- **Alimentateur** : la fréquence de vérification de vos alimentateurs en arrière-plan.
- **Sauvegarde et restauration** : exporter et importer vos surveillances et alimentateurs. Voir [Sauvegardes](/fr/faq#backup).
- **Support** : ces guides, le suivi des problèmes, Discord et le **Journal de débogage**. Voir [Signaler un problème](/fr/bugs).
