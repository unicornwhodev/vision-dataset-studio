# Contrôles de publication rc3 · rc3 publication checks

20 septembre 2026 UTC. Sources Android : `2738b13`. [Preuves de build](../test-results/litert-rc3/final-build/status.json).

## Français

- Gitleaks 8.30.1 : fichiers candidats et historique Git analysés, sans désactiver de règle.
- Douze alertes sur les fichiers candidats : huit SHA-256 de sources (anciens et nouveau manifeste) et quatre empreintes de **clés publiques** imprimées par `apksigner`. Les sources ont été comparées aux commits et les APK vérifiées ; aucune de ces valeurs n’est un jeton ni une clé privée.
- Les fichiers de configuration locaux, `.env`, tokens, clés privées/keystores, données utilisateur, poids et identifiants des dépôts HF privés sont exclus. Les preuves publiques sont sélectionnées explicitement ; aucun répertoire du pod n’est publié en bloc.
- Les APK ont été recopiées sur le Chromebook et leurs tailles/SHA-256 recalculés. L’inventaire APK confirme l’absence de poids. La clé Debug rc3 reste hors Git.
- Les rapports détaillés du scanner sont conservés localement sous `dist/publication-review/`, exclus du dépôt. Un scan ne prouve pas une absence absolue de secrets ; cette publication combine scan et revue des fichiers sélectionnés.

## English

- Gitleaks 8.30.1 scans publication candidates and Git history without disabling rules.
- Twelve candidate-file findings: eight source SHA-256 digests across old/new manifests and four **public-key** fingerprints printed by `apksigner`. Sources were checked against their commits and APK signatures verified; none is an access token or private key.
- Local configuration, `.env`, credentials, private keys/keystores, user data, weights and private HF repository identifiers are excluded. Public evidence is explicitly selected; no pod directory is published wholesale.
- APKs were backed up to the Chromebook and their size/SHA-256 recomputed. The APK inventory contains no model weights. The rc3 Debug key remains outside Git.
- Detailed scanner reports stay under ignored `dist/publication-review/`. Scanning cannot prove an absolute absence of secrets; this publication combines scanning with review of selected files.
