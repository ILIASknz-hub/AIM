# Aïn Souk — version 2

Prototype réalisé dans le cadre du mémoire de fin d'études (Institut Royal de l'Administration Territoriale) : contrôle sanitaire, prix et protection du consommateur dans les souks hebdomadaires de la province de Sidi Kacem.

Le système comprend deux parties, qui fonctionnent **sans Internet** :

| Partie | Utilisateur | Rôle |
|---|---|---|
| Application Android (`app/`) | Auxiliaire d'autorité | **Saisir et transmettre** : prix, abattage clandestin, sécurité des produits, réclamations. Aucune consultation des données. |
| Poste de l'annexe (`poste_annexe/`) | Agent d'autorité, administrateur | Recevoir, analyser, décider : tableau de bord, carte SIG, alertes de prix, traitement des signalements, gestion des souks et des comptes. |

## Installer

- **Téléphone** : onglet **Releases** du dépôt → télécharger `AinSouk.apk` sur le téléphone → l'ouvrir → autoriser l'installation.
- **Poste de l'annexe** : copier le dossier `poste_annexe/` sur l'ordinateur (les deux fichiers `poste_annexe.html` et `config_annexe.js` doivent rester ensemble), puis ouvrir `poste_annexe.html` avec Chrome ou Edge.

## Comptes de démonstration

| Identifiant | Mot de passe | Rôle | Où |
|---|---|---|---|
| `admin` | `Annexe@2026` | Administrateur | Poste de l'annexe |
| `agent01` | `Caid@2026` | Agent d'autorité | Poste de l'annexe |
| `aux01` | `Souk@2026` | Auxiliaire (Sidi Kacem, Mechra Bel Ksiri) | Téléphone |
| `aux02` | `Souk@2026` | Auxiliaire (Had Kourt, Jorf El Melha, Aïn Dfali) | Téléphone |
| `aux03` | `Souk@2026` | Auxiliaire (Dar Gueddari, Nouirate, Khnichet) | Téléphone |

## Créer des comptes

**Méthode 1 — depuis le poste de l'annexe (recommandée).** Se connecter en `admin` → **Comptes et accès** → **Créer un compte** (identifiant, nom, fonction, rôle, souks affectés, mot de passe). Puis **Exporter le paquet de configuration** et l'envoyer aux téléphones (Bluetooth ou câble) ; sur le téléphone : écran de connexion → « تحديث الإعدادات من ملف الملحقة ». Le paquet est signé : un téléphone refuse toute configuration qui ne vient pas du poste.

**Méthode 2 — dans le code source.** Modifier la liste `COMPTES` (et si besoin `SOUKS`) dans `outils/generer_config.mjs`, puis lancer :

```
node outils/generer_config.mjs
```

Le script régénère `app/src/main/assets/www/config.js` (téléphones) et `poste_annexe/config_annexe.js` (poste). Pousser sur GitHub reconstruit automatiquement l'APK. Les mots de passe ne sont jamais enregistrés en clair (empreinte PBKDF2-SHA-256, 100 000 itérations).

## Souks hebdomadaires

Les souks sont **préétablis** : l'auxiliaire choisit uniquement parmi les souks actifs qui lui sont affectés ; il ne saisit jamais de nom. La gestion se fait dans le poste, menu **Souks hebdomadaires** : calendrier de la semaine, ajout, modification (noms, jour de tenue, coordonnées GPS, observations), suspension, souks sans auxiliaire, souks sans fiche récente.

## Transmettre sans Internet

| Voie | Téléphone | Poste |
|---|---|---|
| Bluetooth | « البلوتوث » : envoi direct à l'ordinateur | Windows : Bluetooth → « Recevoir un fichier », puis **Réception des fiches** |
| Partage à proximité | « المشاركة القريبة » : Quick Share / Wi-Fi Direct | Accepter, puis **Réception des fiches** |
| Code QR | « رمز QR » : les codes défilent à l'écran | **Réception des fiches** → « Activer la caméra » (webcam) |
| Câble USB | « الكابل » : fichier dans `Download/AinSouk` | Copier le fichier, puis **Réception des fiches** |

Les fichiers (`.ainsouk`) sont **chiffrés** sur le téléphone (AES-256-GCM, clé protégée par RSA-OAEP 2048) avec la clé publique du poste : seul le poste de l'annexe peut les ouvrir. Après confirmation de la remise, les fiches sont effacées du téléphone. Les photos ne passent pas par le code QR : elles restent sur le téléphone jusqu'à un envoi par Bluetooth ou câble, et le poste les rattache automatiquement aux fiches déjà reçues.

## Sécurité — à lire avant un usage réel

Les clés fournies dans ce dépôt public sont des **clés de démonstration**. Avant un usage réel :

```
node outils/generer_config.mjs --cles
```

puis reconstruire l'APK à partir d'un dépôt **privé**, et ne jamais publier `poste_annexe/config_annexe.js` (il contient les clés privées).

## Structure

```
app/src/main/assets/www/index.html   interface du téléphone (arabe)
app/src/main/assets/www/config.js    comptes des auxiliaires, souks, clés publiques
app/src/main/assets/www/fonts/       polices Noto Naskh Arabic et Noto Kufi Arabic (licence OFL)
app/src/main/assets/www/lib/         qrcode-generator (licence MIT)
app/src/main/java/.../MainActivity.kt   partage Bluetooth / à proximité, câble, caméra
poste_annexe/poste_annexe.html       poste de l'annexe (tableau de bord, souks, comptes, réception)
poste_annexe/config_annexe.js        comptes, souks, clés privées du poste
outils/generer_config.mjs            création des comptes et des clés
```

L'APK est construit automatiquement par GitHub Actions à chaque envoi et publié dans **Releases**.
