---
title: Alimentateurs
description: "Ajoutez votre alimentateur airplanes.live, suivez ses taux de messages et soyez averti quand il passe hors ligne."
permalink: /fr/feeders
translation_key: feeders
lang: fr
source_hash: a9b4816daaa62c7b
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

Appuyez sur le bouton **+** de l’onglet Alimentateur. Il y a trois façons de remplir **Nouvel alimentateur** :

- **Détecter l'Alimentateur Local** : trouve les alimentateurs sur le même réseau que votre téléphone et remplit les champs. S’il en trouve plusieurs, choisissez-en un.
- **Scanner le code QR** : scanne un code QR d’alimentateur créé par l’application sur un autre appareil, voir [Partager un alimentateur](#share).
- Saisissez vous-même l’**ID de l'alimentateur**.

Champs facultatifs :

- **Étiquette** : un nom pour l’alimentateur. Sans étiquette, l’application utilise le nom de l’alimentateur sur airplanes.live.
- **Adresse IP** : l’adresse où tar1090 ou graphs1090 fonctionne pour cet alimentateur, comme `192.168.0.42`. L’application affiche alors des boutons pour les ouvrir.
- **Position** : latitude et longitude, comme `50.8006, 6.0619`.

## Liste des alimentateurs
{: #list }

L’en-tête indique si tous les alimentateurs sont en ligne. Chaque alimentateur affiche son taux de messages Beast (MSG/s), son débit de données (KBit/s) et ses statistiques MLAT avec la part de valeurs aberrantes et le nombre de pairs. Triez la liste par **Étiquette** ou par **Taux de messages**.

Appuyez sur un alimentateur pour plus d’options :

- **Afficher l'alimentation sur la carte** : ouvre globe.airplanes.live dans votre navigateur et montre ce que cet alimentateur reçoit.
- **Renommer** et **Définir l'adresse IP**.
- **TAR1090** et **GRAPHS1090** : ouvrent les pages web de l’alimentateur lui-même. Ils apparaissent une fois qu’une adresse IP est définie.
- **Statistiques** : graphiques sur 30 jours des taux de messages et des valeurs aberrantes MLAT.
- **Générer un code QR**, voir plus bas.
- **Notifier quand hors ligne**, voir plus bas.
- **Supprimer l'alimentateur**.

## Notifications hors ligne
{: #offline }

**Notifier quand hors ligne** est activé pour les nouveaux alimentateurs. Un alimentateur est considéré comme hors ligne quand airplanes.live ne l’a pas vu depuis 48 heures. L’application affiche alors « Alimentateurs hors ligne détectés » avec le nom de l’alimentateur. Activer ce réglage demande l’autorisation d’envoyer des notifications sur Android 13 et versions ultérieures.

Appuyez sur **Reporter** dans la notification pour la faire taire jusqu’à ce que cet alimentateur envoie à nouveau des données. La notification disparaît d’elle-même une fois l’alimentateur revenu.

L’application vérifie vos alimentateurs en arrière-plan. Réglez la fréquence dans **Paramètres** › **Alimentateur** › **Intervalle de vérification**, de 15 minutes à un jour. Par défaut, c’est toutes les 60 minutes.

## Partager un alimentateur
{: #share }

**Générer un code QR** affiche un code QR contenant les réglages de l’alimentateur. Scannez-le avec **Scanner le code QR** dans l’application sur un autre appareil pour y ajouter le même alimentateur.
