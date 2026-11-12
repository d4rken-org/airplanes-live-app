---
title: Alimentateurs
description: "Ajoutez votre alimentateur airplanes.live, suivez ses taux de messages et soyez averti quand il passe hors ligne."
permalink: /fr/feeders
translation_key: feeders
lang: fr
source_hash: dd44f6750bbfb180
---
# Alimentateurs
{: #feeders }

Un alimentateur est un récepteur, généralement un Raspberry Pi avec une antenne, qui envoie à airplanes.live les signaux d’aéronefs qu’il reçoit. L’onglet **Alimentateur** suit vos alimentateurs et vous avertit quand l’un d’eux passe hors ligne. Pour en installer un, consultez le [guide d’installation d’airplanes.live](https://airplanes.live/get-started/).

Ajouter un alimentateur ici et [le lier](/fr/getting-started#link-feeder) pour Pro sont deux étapes distinctes. Vous pouvez faire l’une sans l’autre.

## Trouver l’ID de votre alimentateur
{: #feeder-id }

Chaque alimentateur a un ID, un UUID comme `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. Sur un alimentateur installé avec le script d’alimentation d’airplanes.live, affichez-le avec :

```
cat /usr/local/share/airplanes/airplanes-uuid
```

Sur l’image d’alimentateur airplanes.live, le fichier est `/boot/airplanes-uuid`.

## Ajouter un alimentateur
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="L’écran d’ajout d’un alimentateur, avec les champs ID, étiquette, adresse IP et position"></a>

Appuyez sur le bouton **+** de l’onglet Alimentateur. Il y a trois façons de remplir **Nouvel alimentateur** :

- **Détecter l'Alimentateur Local** : trouve les alimentateurs sur le même réseau que votre téléphone et remplit les champs. S’il en trouve plusieurs, choisissez-en un.
- **Scanner le code QR** : scanne un code QR d’alimentateur créé par l’application sur un autre appareil, voir [Partager un alimentateur](#share).
- Saisissez vous-même l’**ID de l'alimentateur**.

Champs facultatifs :

- **Étiquette** : un nom pour l’alimentateur. Sans étiquette, l’application utilise le nom MLAT de l’alimentateur sur airplanes.live, ou les cinq derniers caractères de son ID.
- **Adresse IP** : l’adresse où tar1090 ou graphs1090 fonctionne pour cet alimentateur, comme `192.168.0.42`. L’application affiche alors des boutons pour les ouvrir.
- **Position** : latitude et longitude, comme `50.8006, 6.0619`.

## Liste des alimentateurs
{: #list }

L’en-tête indique si tous les alimentateurs sont en ligne. Chaque alimentateur affiche son état, son taux de messages Beast (MSG/s), son débit de données (KBit/s) et ses statistiques MLAT avec la part de valeurs aberrantes et le nombre de pairs. Triez la liste par **Étiquette** ou par **Taux de messages**.

Quand le serveur de l’application surveille vos alimentateurs, l’état vient du serveur, par exemple « En attente de la première vérification », « Hors ligne depuis … » ou « Panne possible d'airplanes.live ». Les problèmes trouvés par le serveur s’affichent en dessous, comme « client MLAT absent ». Le niveau gratuit surveille moins d’alimentateurs qu’avec un alimentateur lié. Les suivants affichent « Non surveillé, limite de feeders atteinte » et un bouton **Augmenter la limite** pour [lier votre alimentateur](/fr/getting-started#link-feeder).

Appuyez sur un alimentateur pour plus d’options :

- **Afficher l'alimentation sur la carte** : ouvre globe.airplanes.live dans votre navigateur et montre ce que cet alimentateur reçoit.
- **Renommer** et **Définir l'adresse IP**.
- **TAR1090** et **GRAPHS1090** : ouvrent les pages web de l’alimentateur lui-même. Ils apparaissent une fois qu’une adresse IP est définie.
- **Surveillance** : l’état et les problèmes de cet alimentateur selon le serveur, et les vérifications qui n’ont pas encore assez de données. Affiché seulement quand le serveur surveille vos alimentateurs.
- **Statistiques** : graphiques sur 30 jours des taux de messages et des valeurs aberrantes MLAT. Quand le serveur surveille vos alimentateurs, les graphiques montrent le taux de messages, les pairs MLAT et les valeurs aberrantes MLAT de l’historique horaire du serveur.
- **Générer un code QR**, voir plus bas.
- **Notifier quand hors ligne**, voir plus bas.
- **Supprimer l'alimentateur**.

## Notifications hors ligne
{: #offline }

**Notifier quand hors ligne** définit combien de temps un alimentateur peut rester hors ligne avant que vous receviez une notification : **Désactivé**, 6, 12, 24 ou 48 heures. Les nouveaux alimentateurs commencent à 48 heures. L’activer demande l’autorisation d’envoyer des notifications sur Android 13 et versions ultérieures.

Quand le serveur de l’application le propose, il surveille vos alimentateurs, même quand l’application est fermée. Un alimentateur est considéré comme hors ligne quand il n’a eu aucune connexion à airplanes.live pendant la durée choisie, confirmé par au moins deux vérifications. Le serveur ne déclare aucun alimentateur hors ligne tant qu’airplanes.live a lui-même des problèmes ou que de nombreux alimentateurs décrochent en même temps. Vous recevez alors « Alimentateur hors ligne », puis « Alimentateur de nouveau en ligne » à son retour.

Le serveur cherche aussi des problèmes et envoie « Problèmes d'alimentateur » quand il en trouve un :

- client MLAT absent
- aucun pair MLAT
- erreurs de synchronisation MLAT
- nombreuses valeurs aberrantes MLAT
- reçoit beaucoup moins de messages que d'habitude
- se reconnecte sans cesse
- ID d'alimentateur utilisé par plusieurs alimentateurs

Désactivez-les dans **Paramètres** › **Alimentateur** › **Alertes de problèmes**.

Les notifications du serveur arrivent avec la synchronisation en arrière-plan de l’application, elles peuvent donc prendre aussi longtemps que le prévoit le rythme choisi dans **Paramètres** › **Général** › **Synchronisation en arrière-plan**. Voir [Fréquence de synchronisation](/fr/watchlist#interval).

Appuyez sur **Reporter** dans une notification pour faire taire cet alimentateur jusqu’à ce qu’il soit de nouveau en ligne. Pour les problèmes d’un alimentateur en ligne, le report dure jusqu’à ce qu’ils disparaissent.

Sans le serveur, l’application interroge elle-même airplanes.live et affiche « Alimentateurs hors ligne détectés » quand un alimentateur n’a pas été vu pendant la durée choisie. Elle vérifie toutes les 60 minutes.

## Partager un alimentateur
{: #share }

**Générer un code QR** affiche un code QR contenant les réglages de l’alimentateur. Scannez-le avec **Scanner le code QR** dans l’application sur un autre appareil pour y ajouter le même alimentateur.
