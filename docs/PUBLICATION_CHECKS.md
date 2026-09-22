# Publication des sources rc5 · rc5 source publication

23 septembre 2026. Sources Android `a0d9737`, build `20260922T223550Z-3ee0d233110c` ; 158 fichiers compilés comparés au commit sans différence. Cette mise à jour publie les sources et la documentation de reprise ; les artefacts rc4 existants ne sont pas remplacés.

Les fichiers ajoutés/modifiés et les nouveaux commits sont analysés avec Gitleaks 8.30.1 et relus avant push. Les preuves sont sélectionnées : aucun poids, corpus utilisateur, identifiant de modèle privé, fichier d’authentification ni clé de signature n’est ajouté. Les journaux conservent le premier échec de budget Android et son retest réussi ; les résultats incomplets restent explicitement identifiés. Les empreintes de build et de clés publiques servent uniquement à la provenance et ne sont pas des secrets. Le scan des fichiers candidats signale huit empreintes SHA de clés publiques imprimées par `apksigner` : les huit lignes ont été vérifiées individuellement, aucun jeton ni clé privée.

This update publishes source and handoff documentation, without replacing the existing rc4 distribution. The 158 compiled files match `a0d9737`. Added/modified files and new commits are scanned with Gitleaks 8.30.1 and reviewed before push. Selected evidence excludes weights, user datasets, private model identifiers, authentication files and signing keys. The initial Android storage-budget failure and successful retry are both retained. Build and public-key fingerprints are provenance, not credentials. The candidate-file scan reports eight public-key SHA fingerprints printed by `apksigner`; each line was verified individually, with no access token or private key.

---

# Contrôles de publication rc4 · rc4 publication checks

22 septembre 2026 UTC. Sources Android `d9b2239`, build `20260922T183415Z-818e4cf57d14`.

- Les commits ajoutés et les preuves rc4 sélectionnées sont contrôlés avec Gitleaks 8.30.1, règles actives et sorties expurgées. Les empreintes des nouveaux manifestes utilisent des champs `path`/`sha256` explicites pour ne pas confondre un hash de source avec une clé API.
- Les 143 sources de compilation sont comparées au commit. Les deux APK rapatriées sont vérifiées en taille et SHA-256 ; la clé Debug reste hors du dépôt et l’APK ne contient aucun poids.
- Seuls les reçus, résultats et documents sélectionnés sont ajoutés. Aucun fichier d’authentification, identifiant de dépôt privé, donnée utilisateur ou clé de signature n’est inclus. L’authentification GitHub/GHCR est transmise en mémoire aux clients, sans jeton dans les commandes, les notes ou les artefacts.
- Les preuves rc4 sont distinctes des résultats rc3 et du build de stabilisation précédent. Une seule APK utilisateur est proposée ; l’APK instrumentée reste dans l’archive de qualification.

The added commits and selected rc4 evidence are checked using Gitleaks 8.30.1 with active rules and redacted output. Explicit `path`/`sha256` manifest records prevent source digests from resembling API credentials. All 143 compiled files match their commit; transferred APK sizes and hashes are verified. Signing keys and credentials stay outside Git and artifacts, with registry authentication passed in memory. The APK contains no model weights. rc4 receipts remain separate from historical results, and only one user-facing APK is offered.

---

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
