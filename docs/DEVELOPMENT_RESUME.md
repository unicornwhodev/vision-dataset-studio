# Reprendre le développement

**Français** · [English](en/DEVELOPMENT_RESUME.md)

Le code est sur `main`. La version en développement est **4.2.0-rc5**, code Android **10**. La prérelease **rc5** et son package sont publiés : [release](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5), [reçu vérifié](RC5_PUBLICATION_RECEIPT.json). Sa signature Windows diffère de rc4 ; elle ne peut pas mettre à jour rc4. Les assets rc4 sont conservés. Les résultats exacts et leurs builds sont dans [TEST_REPORT.md](../TEST_REPORT.md) et [les preuves Windows](../test-results/windows-rc5-release/README.md).

## Poste local

Prévoir JDK 21, Python 3.11+, Android SDK 36, build-tools 36.0.0 et `adb`. Définir `ANDROID_HOME` vers le SDK et ajouter ses `platform-tools` au `PATH`. Les poids et corpus restent hors Git et hors APK.

Le premier build nécessite également le [runtime Flex reconstruit pour 16 Ko](FLEX_16K.md).
Le préparer une fois sous Linux/WSL selon cette procédure ; le build Android
vérifie ensuite son reçu et ses empreintes sous Windows comme sous Linux.

```bash
git clone https://github.com/unicornwhodev/vision-dataset-studio.git
cd vision-dataset-studio
git switch main
git pull --ff-only
python3 -m unittest discover -s tools/qa -p 'test_*.py'
bash tools/build_android.sh
```

Le build produit une APK utilisateur et une APK réservée aux tests, avec leurs reçus dans `dist/android/`. Pour une installation existante, conserver sa clé de signature : ne pas désinstaller ni effacer les données pour contourner un conflit de signature. La clé Debug du poste distant est conservée hors Git ; un clone n’inclut pas cette clé.

Sur un appareil ou émulateur **dédié à la recette**, choisir son identifiant avec `adb devices`, puis :

```bash
export ANDROID_SERIAL=emulator-5554  # remplacer par l’appareil de recette choisi
export VDS_ALLOW_TEST_INSTALL=1
bash tools/qa/run_device_qualification.sh
```

Les tests nécessitant des fixtures externes ou une autorisation sont distincts. Une suite avec tests ignorés ne reproduit pas les 39 tests documentés. `stage_android_fixture.py` injecte les fixtures hors APK ; `qualify_converted_models.py --help` décrit la recette par conversion. Les reçus de QA identifient les artefacts testés ; l’application n’exige aucun manifeste SHA pour importer ou utiliser un modèle.

Les fixtures de conversions occupent du stockage dans l’installation de recette et entrent dans son budget. Utiliser un appareil de QA distinct de la production et archiver les fixtures de campagnes terminées avant la suite de base. Un refus de budget ne prouve pas une incompatibilité du modèle.

## Comportements à conserver

- Production par lots : import, préannotation, revue, export vérifié, apprentissage facultatif, nettoyage, lot suivant. L’historique anti-doublons survit au nettoyage.
- Changer prompt, réglages ou modèle ne réécrit aucune annotation enregistrée. Une relance explicite est nécessaire et protège toujours les corrections humaines.
- Un modèle sans entraînement reste utilisable en inférence. L’apprentissage est désactivé par défaut et tourne dans Android, sur le lot exporté.
- L’original reste intact et accessible ; le premier entraînement crée une version séparée et les suivants poursuivent ses derniers poids validés, même avant activation pour l’inférence. Voir [la filiation des modèles](MODEL_LINEAGE.md).
- Une tentative interrompue peut être reprise ou abandonnée explicitement. L’abandon n’active aucun poids et ne supprime aucune image ; le nettoyage se confirme séparément.

## Prochaine recette

1. Exécuter les 15 conversions restantes et reprendre les trois délais dépassés sur matériel adapté. Les 13 succès existants, dont 8 avec apprentissage, ne qualifient pas tout le catalogue.
2. Tester téléphone ARM, permissions SAF réelles, mémoire, latence et corpus représentatif. Le pod utilisait un émulateur logiciel sans KVM.
3. Qualifier un serveur d’agent réel et les écritures HF sur un nouveau dépôt privé de QA explicitement autorisé. Aucun dépôt existant ne sert aux essais destructifs.
4. Préparer une signature durable et finir la qualification de distribution. rc5 est une prérelease Debug ; le candidat ARM64 optimisé est non signé. GHCR conserve sa visibilité privée.

Les conversions HF entraînables fournies figent leur encodeur ; leurs succès concernent des têtes ou adaptateurs. L’entraînement de couches internes est démontré uniquement par la fixture synthétique. Aucun gain de précision n’est annoncé.
