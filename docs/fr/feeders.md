---
title: Récepteurs
description: "Ajoutez votre récepteur pour airplanes.live, suivez ses taux de messages et soyez averti quand il passe hors ligne."
permalink: /fr/feeders
translation_key: feeders
lang: fr
source_hash: 6e4f9db68d11263d
---
# Récepteurs
{: #feeders }

Un récepteur, généralement un Raspberry Pi avec une antenne, capte les signaux d’aéronefs et les envoie à airplanes.live. L’onglet **Récepteur** suit vos récepteurs et vous avertit quand l’un d’eux passe hors ligne. Pour en installer un, consultez le [guide d’installation d’airplanes.live](https://airplanes.live/get-started/).

Ajouter un récepteur ici et [le lier](/fr/getting-started#link-feeder) pour Pro sont deux étapes distinctes. Vous pouvez faire l’une sans l’autre.

## Trouver l’ID de votre alimentateur
{: #feeder-id }

Chaque récepteur a un ID d’alimentateur, un UUID comme `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. Sur un récepteur installé avec le script d’alimentation d’airplanes.live, affichez-le avec :

```
cat /usr/local/share/airplanes/airplanes-uuid
```

Sur l’image d’alimentateur airplanes.live, le fichier est `/boot/airplanes-uuid`.

## Ajouter un récepteur
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="L’écran d’ajout d’un récepteur, avec les champs ID, étiquette, adresse IP et position"></a>

Appuyez sur le bouton **+** de l’onglet Récepteur. Il y a trois façons de remplir **Nouveau récepteur** :

- **Détecter le Récepteur Local** : trouve les récepteurs sur le même réseau que votre téléphone et remplit les champs. S’il en trouve plusieurs, choisissez-en un.
- **Scanner le code QR** : scanne un code QR de récepteur créé par l’application sur un autre appareil, voir [Partager un récepteur](#share).
- Saisissez vous-même l’**ID de l'alimentateur**.

Champs facultatifs :

- **Étiquette** : un nom pour le récepteur. Sans étiquette, l’application utilise le nom MLAT du récepteur sur airplanes.live, ou les cinq derniers caractères de son ID.
- **Adresse IP** : l’adresse où tar1090 ou graphs1090 fonctionne pour ce récepteur, comme `192.168.0.42`. L’application affiche alors des boutons pour les ouvrir.
- **Position** : latitude et longitude, comme `50.8006, 6.0619`.

## Liste des récepteurs
{: #list }

L’en-tête indique si tous les récepteurs sont en ligne. Chaque récepteur affiche son état. Quand le serveur de l’application surveille vos récepteurs, chaque récepteur affiche aussi son taux de messages Beast (MSG/s), son débit de données (KBit/s) et ses statistiques MLAT avec la part de valeurs aberrantes et le nombre de pairs. Sans surveillance par le serveur, un récepteur affiche « Surveillance indisponible » et aucun taux. Triez la liste par **Étiquette** ou par **Taux de messages**.

Quand le serveur de l’application surveille vos récepteurs, l’état vient du serveur, par exemple « En attente de la première vérification », « Hors ligne depuis … » ou « Panne possible d'airplanes.live ». Les problèmes trouvés par le serveur s’affichent en dessous, comme « client MLAT absent ». Le niveau gratuit surveille moins de récepteurs que Pro. Les suivants affichent « Non surveillé, limite de récepteurs atteinte » et un bouton **Augmenter la limite** pour [lier votre récepteur](/fr/getting-started#link-feeder).

Appuyez sur un récepteur pour plus d’options :

- **Afficher l'alimentation sur la carte** : ouvre globe.airplanes.live dans votre navigateur et montre ce que ce récepteur reçoit.
- **Renommer** et **Définir l'adresse IP**.
- **TAR1090** et **GRAPHS1090** : ouvrent les pages web du récepteur lui-même. Ils apparaissent une fois qu’une adresse IP est définie.
- **Surveillance** : l’état et les problèmes de ce récepteur selon le serveur, et les vérifications qui n’ont pas encore assez de données. Sans surveillance par le serveur, elle indique « Surveillance indisponible ».
- **Statistiques** : graphiques du taux de messages, des pairs MLAT et des valeurs aberrantes MLAT tirés de l’historique horaire du serveur. Affiché seulement quand le serveur surveille vos récepteurs.
- **Générer un code QR**, voir plus bas.
- **Notifier quand hors ligne**, voir plus bas.
- **Supprimer le récepteur**.

## Notifications hors ligne
{: #offline }

**Notifier quand hors ligne** définit combien de temps un récepteur peut rester hors ligne avant que vous receviez une notification : **Désactivé**, 6, 12, 24 ou 48 heures. Les nouveaux récepteurs commencent à 48 heures. L’activer demande l’autorisation d’envoyer des notifications sur Android 13 et versions ultérieures.

Quand le serveur de l’application le propose, il surveille vos récepteurs, même quand l’application est fermée. Un récepteur est considéré comme hors ligne quand il n’a eu aucune connexion à airplanes.live pendant la durée choisie, confirmé par au moins deux vérifications. Le serveur ne déclare aucun récepteur hors ligne tant qu’airplanes.live a lui-même des problèmes ou que de nombreux récepteurs décrochent en même temps. Vous recevez alors « Récepteur hors ligne », puis « Récepteur de nouveau en ligne » à son retour.

Le serveur cherche aussi des problèmes et envoie « Problèmes de récepteur » quand il en trouve un :

- client MLAT absent
- aucun pair MLAT
- erreurs de synchronisation MLAT
- nombreuses valeurs aberrantes MLAT
- reçoit beaucoup moins de messages que d'habitude
- se reconnecte sans cesse
- ID d'alimentateur envoyé par plusieurs récepteurs

Désactivez-les dans **Paramètres** › **Récepteur** › **Alertes de problèmes**.

Les notifications du serveur arrivent avec la synchronisation en arrière-plan de l’application, elles peuvent donc prendre aussi longtemps que le prévoit le rythme choisi dans **Paramètres** › **Général** › **Synchronisation en arrière-plan**. Voir [Fréquence de synchronisation](/fr/watchlist#interval).

Appuyez sur **Reporter** dans une notification pour faire taire ce récepteur jusqu’à ce qu’il soit de nouveau en ligne. Pour les problèmes d’un récepteur en ligne, le report dure jusqu’à ce qu’ils disparaissent.

Sans surveillance par le serveur, il n’y a pas de notifications hors ligne, et vos récepteurs affichent « Surveillance indisponible ».

## Partager un récepteur
{: #share }

**Générer un code QR** affiche un code QR contenant les réglages du récepteur. Scannez-le avec **Scanner le code QR** dans l’application sur un autre appareil pour y ajouter le même récepteur.
