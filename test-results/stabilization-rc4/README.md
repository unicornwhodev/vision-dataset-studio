# Qualification 4.2.0-rc4 · 22 September 2026 UTC

## Français

Build `20260922T183415Z-818e4cf57d14`, sources Android `d9b22399f5121d05d31ab4aaa67fdc7e9742c3e2` : **54 tests JVM**, **24 tests Android de base**, **2 tests de conversions HF** et **52 tests Python** réussis. Aucun échec ni test ignoré dans ces suites sélectionnées. Lint : **0 erreur, 89 avertissements**. Les deux classes de modèles nécessitant des fixtures/options ne sont pas incluses dans la suite de base ; EdgeNeXt et RepViT sont lancés séparément. Cela ne qualifie pas les autres modèles.

La suite Android (219.285 s) couvre les lots/doublons, Room, SAF injecté, maintenance, annotations/masques, navigation FR/EN et apprentissage optionnel avant nettoyage. La fixture synthétique modifie ses poids visuels internes ; le workflow apprend sur les 88 images acceptées du lot exporté, exclut les rejets et le lot voisin, reprend après interruption et protège le nettoyage.

EdgeNeXt `edgenext_xx_small_learning` passe inférence, deux étapes d’optimisation Android, sauvegarde, restauration et reprise. Cette conversion entraîne sa tête de classification avec encodeur figé. RepViT `repvit_m1` passe l’inférence (embedding de 384 valeurs) ; il n’expose pas de signature d’apprentissage. Les révisions, empreintes et différences numériques sont conservées dans `conversions.json`. Ces régressions ne changent pas le bilan global : 5 conversions réussies, 1 défaut RTMDet, 25 à tester.

Les 143 fichiers du manifeste de compilation correspondent au commit et au poste. Le reçu `final-device/build-receipt.json` identifie les APK installées. APK utilisateur : **366404830 octets**, SHA-256 `7621ccd2737f2542156e66dc24cb0526b72a18219778a8b10512917e73cd7104`. Aucun poids embarqué ; clé de signature hors Git. La clé Debug existante a permis la mise à jour sans désinstallation ni effacement des données.

Les contrôles portables 44/72/16 et les captures FR/EN proviennent de la [validation de stabilisation précédente](../../docs/validation-20260922/README.md), conservée avec son propre identifiant. Environnement : émulateur AOSP API 28 x86_64 sans KVM. Ce sont des tests fonctionnels, pas des mesures téléphone ARM ni de précision métier. La CI rc4 n’a pas été exécutée. **Prérelease Debug, qualification partielle, pas une release stable.**

## English

Build `20260922T183415Z-818e4cf57d14`, Android sources `d9b22399f5121d05d31ab4aaa67fdc7e9742c3e2`: **54 JVM**, **24 core Android**, **2 HF conversion** and **52 Python tests** passed. No failures or skips in the selected suites. Lint: **0 errors, 89 warnings**. The core suite excludes the two classes requiring explicit model fixtures/options; EdgeNeXt and RepViT run separately. Other models are not implicitly qualified.

The Android suite (219.285 s) covers batches/deduplication, Room, injected SAF failures, maintenance, annotation/masks, FR/EN navigation and optional learning before cleanup. The synthetic fixture updates internal visual weights. The workflow trains on 88 accepted images from the exported batch, excludes rejected/neighboring data, resumes after interruption and gates cleanup.

EdgeNeXt `edgenext_xx_small_learning` passes inference, two Android optimizer steps, save/restore/resume; its visual encoder remains frozen. RepViT `repvit_m1` passes inference with a 384-value embedding and has no training signature. `conversions.json` retains revisions, hashes and numerical results. These are regressions of previously qualified models: global coverage remains 5 passed, 1 RTMDet defect and 25 pending.

All 143 compiled-source entries match the commit and workstation. The device receipt identifies the installed APKs. User APK: **366404830 bytes**, SHA-256 `7621ccd2737f2542156e66dc24cb0526b72a18219778a8b10512917e73cd7104`; no bundled weights. The signing key stays outside Git. Updating with the existing Debug key required no uninstall or data wipe.

Portable 44/72/16 checks and FR/EN screenshots belong to the [preceding stabilization validation](../../docs/validation-20260922/README.md), with its original build ID. Tests use an AOSP API 28 x86_64 software emulator without KVM, not a physical ARM device or accuracy benchmark. No rc4 CI run was executed. **Debug prerelease with partial qualification, not a stable release.**
