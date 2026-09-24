# Développer Cadryl

[Documentation](README.md) · [English](en/DEVELOPMENT_RESUME.md)

Le projet est sur `main`. La version actuelle est **4.2.0-rc6**, code Android **11**. Le nom affiché est Cadryl ; le package reste `com.unicornwhodev.visiondatasetstudio`. Garde cette identité pour les mises à jour et les données.

## Préparer le poste

Il faut JDK 21, Python 3.11+, Android SDK 36 et build-tools 36.0.0. Le bootstrap du dépôt utilise la version Gradle épinglée ; aucun besoin d’installer un Gradle global. Définis `JAVA_HOME` et `ANDROID_HOME` vers tes installations.

```powershell
git clone https://github.com/unicornwhodev/vision-dataset-studio.git
cd vision-dataset-studio
git switch main
git pull --ff-only
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-21'
$env:ANDROID_HOME='D:/Android/Sdk'
$env:PATH=$env:JAVA_HOME+'/bin;'+$env:ANDROID_HOME+'/platform-tools;'+$env:PATH
```

Les deux chemins sont des exemples : adapte-les à ton poste.

## Préparer les trois runtimes

Gradle utilise trois AAR locaux avec des contrôles de provenance : Flex `2.16.1-vds16k1`, Graphics Path `1.0.1-vds16k1` et LiteRT `2.2.0-vds16k2`. Ils ne sont pas stockés dans Git.

La [release rc6](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6) fournit un ZIP pour chacun. Vérifie `SHA256SUMS`, puis extrais leurs chemins `dist/native-*/maven/...` à la racine du clone. Garde l’AAR, le POM et le reçu ensemble. Les scripts Android vérifient leurs empreintes et leur recette avant compilation.

Pour les reconstruire, suis [Flex](FLEX_16K.md), [Graphics Path](GRAPHICS_PATH_16K.md) et [LiteRT](LITERT_16K_STATUS.md). Cette étape demande Linux ou WSL et les outils épinglés. Les builds Android suivants peuvent tourner directement sous Windows.

## Compiler et tester

```powershell
python -m unittest discover -s tools/qa -p 'test_*.py'
python -X utf8 tools/build_android.py
```

Le build réel résout les dépendances, génère Room/KSP, lance les contrôles prévus et produit les APK Debug et de tests. Chaque tentative écrit ses journaux, empreintes et résultats sous `dist/android/runs/`. Si une étape échoue, lis son reçu : une APK laissée par le build ne signifie pas que la recette est passée.

Pour la Release minifiée :

```powershell
python -X utf8 tools/qa/build_release_test_apks.py --abi arm64-v8a
python -X utf8 tools/qa/build_release_test_apks.py --abi x86_64
```

Exécute ces builds l’un après l’autre. La [procédure Release](RELEASE_TESTING.md) explique la signature commune app/tests, le mapping R8 et les 40 tests métier. Le pilote indépendant `release-qa` couvre quatre scénarios UI. Les clés privées restent hors du dépôt ; un clone n’en contient aucune.

Sous Linux, utilise `python3` et les variables d’environnement de ton shell. Le script `tools/build_android.sh` appelle le même build Python.

## Sur Android

Choisis explicitement un appareil de test dans `adb devices`. Prépare les fixtures décrites dans la [recette Android](ANDROID_QUALIFICATION.md), puis utilise les lanceurs qui vérifient les SHA des APK. Ils ne désinstallent pas l’app pour contourner une signature incompatible.

Les modèles de test sont injectés séparément et consomment de l’espace sur l’appareil. Les scénarios HF, pannes et conversions ont leurs propres conditions et autorisations. Un test ignoré reste un test non exécuté. Ne vide pas les logs de crash pour obtenir une campagne verte.

## Les repères dans le code

[Architecture](ARCHITECTURE.md) pour les modules, [schéma](../DATA_SCHEMA.md) pour les données, [contrat LiteRT](LITERT_TRAINING_CONTRACT.md) pour les modèles et [identité visuelle](BRAND.md) pour les ressources Android.

Avant une modification, garde ces règles en tête : les corrections humaines restent protégées ; une copie est relue avant nettoyage ; l’original d’un modèle reste intact ; les entraînements suivants poursuivent la copie validée. Une interruption ne doit pas être transformée en succès.

## La reprise actuelle

Les correctifs natifs et la suite Release sont dans rc6. Restent la recette du candidat sur téléphone, ARM 16 Ko physique, les essais longs, la qualité du catalogue, la CI distante et la revue des notices. Les résultats détaillés sont dans [TEST_REPORT.md](../TEST_REPORT.md) ; la [feuille de route](ROADMAP.md) fixe l’ordre.
