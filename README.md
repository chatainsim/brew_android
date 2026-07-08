# BrewHome Android

Application Android native (Kotlin + Jetpack Compose) pour se connecter au serveur
[BrewHome](../Beers/brewhome/) sur le réseau local et consulter/gérer sa brasserie
depuis le téléphone.

## Fonctionnalités

- **Cave** : liste des bières avec photo, ajustement rapide du stock (33 cl, 75 cl, fût)
  directement depuis la liste ; fiche détaillée avec notes de dégustation éditables
  (note en étoiles + apparence, arôme, saveur, impression générale).
- **Recettes** : liste et fiche détaillée (paramètres d'empâtage/ébullition/fermentation,
  ingrédients groupés par catégorie avec temps d'ajout des houblons, alpha, EBC, notes).
- **Ingrédients** : inventaire groupé par catégorie, ajustement rapide des quantités
  (pas adapté à l'unité : ±10 g, ±0,1 kg, ±1 pièce), saisie directe par appui sur la ligne,
  alerte visuelle de stock bas.
- **Brassins** : suivi en lecture (statut, date, volume, densités, ABV, notes).
- **Réglages** : URL du serveur configurable (stockée dans DataStore), affichée au premier
  lancement si non configurée.

## Connexion au serveur

L'app parle directement à l'API REST de BrewHome (port 5000 par défaut, HTTP en clair
autorisé pour le réseau local). Renseigner par ex. `http://192.168.1.50:5000` dans les
réglages.

## Build

Toolchain installé dans `~/android-toolchain/` (JDK 17 Temurin, SDK Android 34,
Gradle 8.7) — voir `local.properties` pour le chemin du SDK.

```bash
export JAVA_HOME=~/android-toolchain/jdk-17.0.11+9
export PATH=$JAVA_HOME/bin:$PATH
~/android-toolchain/gradle-8.7/bin/gradle assembleDebug
```

APK produit : `app/build/outputs/apk/debug/app-debug.apk` (copié en `BrewHome-debug.apk`
à la racine).

## Tests

`app/src/test/` contient des tests JVM qui parsent des réponses **réelles** du serveur
BrewHome 0.0.5 (fixtures dans `app/src/test/resources/`) avec les modèles de l'app :

```bash
~/android-toolchain/gradle-8.7/bin/gradle testDebugUnitTest
```

## Installation sur le téléphone

Transférer `BrewHome-debug.apk` sur le téléphone (câble, Syncthing, partage réseau…)
et l'ouvrir — autoriser l'installation de sources inconnues si demandé.
Ou par ADB : `adb install BrewHome-debug.apk`.

## Architecture

```
app/src/main/java/fr/easter/brewhome/
├── MainActivity.kt          # Entrée, edge-to-edge, thème
├── BrewViewModel.kt         # État global (bières, recettes, inventaire, brassins) + actions API
├── data/
│   ├── Models.kt            # Modèles kotlinx.serialization (champs inconnus ignorés)
│   ├── Api.kt               # Retrofit + OkHttp, base URL dynamique normalisée
│   └── Settings.kt          # DataStore (URL du serveur)
└── ui/
    ├── App.kt               # Scaffold, barre de navigation, NavHost
    ├── Theme.kt             # Palette ambrée clair/sombre
    ├── Common.kt            # Helpers (formats, catégories, étoiles)
    ├── BeersScreens.kt      # Cave : liste + détail + dialog dégustation
    ├── RecipesScreens.kt    # Recettes : liste + détail
    ├── InventoryScreen.kt   # Ingrédients + dialog quantité
    ├── BrewsScreen.kt       # Brassins (lecture)
    └── SettingsScreen.kt    # URL serveur
```
