# BrewHome Android

Application Android native (Kotlin + Jetpack Compose) pour se connecter au serveur
[BrewHome](../Beers/brewhome/) sur le réseau local et consulter/gérer sa brasserie
depuis le téléphone.

## Fonctionnalités

- **Cave** : liste des bières avec photo, ajustement rapide du stock (33 cl, 75 cl, fût)
  directement depuis la liste ; fiche détaillée avec notes de dégustation éditables
  (note en étoiles + apparence, arôme, saveur, impression générale) ; bouton dans la
  barre du haut pour ouvrir la vitrine GitHub Pages de la cave dans le navigateur
  (repo lu dans les réglages GitHub du serveur ; à défaut, page Cave du site).
- **Recettes** : liste et fiche détaillée (paramètres d'empâtage/ébullition/fermentation,
  ingrédients groupés par catégorie avec temps d'ajout des houblons, alpha, EBC, notes) ;
  disponibilité du stock (coche verte si tout est en stock, détail par ingrédient dans
  la fiche : en stock / insuffisant / manquant / unités ≠, mêmes règles que le site) ;
  partage de la recette en texte (mail, Telegram, WhatsApp…) ; onglet **Brouillons**
  (idées de recettes : statut, ingrédients, date cible, notes, partage en texte,
  création et modification depuis le téléphone, autocomplétion des noms
  d'ingrédients depuis le catalogue et l'inventaire du serveur).
- **Ingrédients** : inventaire groupé par catégorie, ajustement rapide des quantités
  (pas adapté à l'unité : ±10 g, ±0,1 kg, ±1 pièce), saisie directe par appui sur la ligne,
  alerte visuelle de stock bas ; partage du stock complet en texte structuré
  (pour un copain ou une IA) ; onglet **Courses** : liste de courses avec coche,
  ajout d'articles, suppression, et transfert des articles cochés dans
  l'inventaire (comme le bouton « Acheté » du site).
- **Brassins** : liste (statut coloré, date, volume, densités, ABV) et fiche détaillée :
  lien vers la recette, atténuation, efficacité, coût, courbe de fermentation
  (densité + température, mesures manuelles et densimètre connecté), journal de brassage.
- **Statistiques** (depuis l'onglet Outils) : brassins terminés, litres brassés, alcool
  moyen, coût total ; production en détail (volume moyen, efficacité, fermentation,
  coût au litre, DI moyenne) ; volume par année, styles les plus brassés, cave
  actuelle, dégustations (note moyenne, top 3), stock (valeur estimée, stock bas),
  consommation mensuelle, totaux par format et bières les plus consommées.
- **Outils** : calculateurs de brassage hors-ligne, identiques à ceux du serveur —
  ABV/atténuation, correction densimètre (température), correction réfractomètre
  (Novotný), température d'empâtage, répartition en bouteilles 33/75 cl, primage
  (styles + types de sucre), starter de levure (viabilité Mr. Malty, 1 ou 2 étapes).
- **UX** : recherche dans les listes (cave, recettes, ingrédients), tirer-pour-rafraîchir,
  écran d'erreur avec « Réessayer », clavier numérique sur les champs de quantité,
  flèche de retour sur les fiches, onglets avec état préservé.
- **Réglages** : URL du serveur configurable (stockée dans DataStore), affichée au premier
  lancement si non configurée ; thème clair / sombre / automatique (suit le système) ;
  couleurs dynamiques Material You (Android 12+) ou palette ambre BrewHome
  (palette tonale Material 3 complète).

## Connexion au serveur

L'app parle directement à l'API REST de BrewHome (port 5000 par défaut, HTTP en clair
autorisé pour le réseau local). Renseigner par ex. `http://192.168.1.50:5000` dans les
réglages.

### VPN WireGuard automatique (hors du réseau local)

Dans les réglages, activer « VPN automatique si serveur injoignable » et donner le nom
du tunnel : si le serveur ne répond pas au lancement, l'app monte le tunnel via l'API
de contrôle de l'app WireGuard officielle, attend qu'il soit établi, puis recharge.
Prérequis côté WireGuard : Réglages → Avancé → « Autoriser les applications de contrôle
à distance », et accorder la permission demandée par BrewHome à l'activation de l'option.

## Build

Toolchain installé dans `~/android-toolchain/` (JDK 17 Temurin, SDK Android 34,
Gradle 8.7) — voir `local.properties` pour le chemin du SDK.

```bash
export JAVA_HOME=~/android-toolchain/jdk-17.0.11+9
export PATH=$JAVA_HOME/bin:$PATH
~/android-toolchain/gradle-8.7/bin/gradle assembleDebug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk`.

À chaque push sur `main`, la CI (`.forgejo/workflows/release.yml`, runner local
« wsl-simon ») compile l'APK, lance les tests et publie une release Gitea taguée
avec la version, APK attaché. L'APK n'est plus commité dans le dépôt.

## Tests

`app/src/test/` contient des tests JVM qui parsent des réponses **réelles** du serveur
BrewHome 0.0.5 (fixtures dans `app/src/test/resources/`) avec les modèles de l'app :

```bash
~/android-toolchain/gradle-8.7/bin/gradle testDebugUnitTest
```

## Installation sur le téléphone

Télécharger `BrewHome-{version}.apk` depuis la page **Releases** du dépôt Gitea
(directement depuis le navigateur du téléphone) et l'ouvrir — autoriser
l'installation de sources inconnues si demandé. Ou par ADB : `adb install BrewHome-{version}.apk`.

## Architecture

```
app/src/main/java/fr/easter/brewhome/
├── MainActivity.kt          # Entrée, edge-to-edge, thème
├── BrewViewModel.kt         # État global (bières, recettes, inventaire, brassins) + actions API
├── calc/
│   └── BrewCalc.kt          # Formules de brassage (mêmes calculs que la page Outils du serveur)
├── data/
│   ├── Models.kt            # Modèles kotlinx.serialization (champs inconnus ignorés)
│   ├── Api.kt               # Retrofit + OkHttp, base URL dynamique normalisée
│   └── Settings.kt          # DataStore (URL du serveur)
└── ui/
    ├── App.kt               # Scaffold, barres de navigation, NavHost
    ├── Theme.kt             # Palette ambrée clair/sombre
    ├── Common.kt            # Helpers (formats, recherche, pull-to-refresh, erreurs, étoiles)
    ├── BeersScreens.kt      # Cave : liste + détail + dialog dégustation
    ├── RecipesScreens.kt    # Recettes : liste + détail
    ├── InventoryScreen.kt   # Ingrédients + dialog quantité
    ├── BrewsScreen.kt       # Brassins : liste + fiche (fermentation, journal)
    ├── ToolsScreens.kt      # Calculateurs de brassage
    └── SettingsScreen.kt    # URL serveur
```
