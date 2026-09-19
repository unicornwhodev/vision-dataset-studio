# Build sur Chromebook

Utiliser l'environnement Linux de ChromeOS (Crostini), pas le shell ChromeOS brut.

```bash
cd ~/Downloads
unzip vision-dataset-studio-v4.2-rc2-chromebook.zip
cd vision-dataset-studio-v4.2-rc2-chromebook
bash build_on_chromebook.sh
```

Le script installe JDK 17 et les Android Command-line Tools officiels, puis `platforms;android-36` et `build-tools;36.0.0`. Il lance ensuite le build Debug avec les outils du projet.

En cas de succès l'APK est copiée dans `My Files/Downloads/VisionDatasetStudio-build/` lorsque le partage ChromeOS est disponible, sinon dans `~/Downloads/VisionDatasetStudio-build/` du conteneur Linux.

En cas d'échec, le même dossier contient `build-errors.txt`.
