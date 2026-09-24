# Contribuer à Cadryl

Un bug bien décrit, une petite correction ou une idée expliquée avec un cas concret peuvent déjà aider. Pour une grosse évolution, ouvre d’abord une issue pour discuter du besoin et de sa place dans le projet.

## Signaler un problème

Indique la version de l’app, ton appareil et sa version Android, les étapes suivies, le résultat attendu et ce qui s’est passé. Pour un modèle, ajoute sa source publique, sa révision et sa variante. Un petit exemple synthétique est préférable à un dataset privé. Retire les tokens et données personnelles des logs.

## Proposer du code

Le [guide de développement](docs/DEVELOPMENT_RESUME.md) permet de préparer le build. Lis aussi [AGENTS.md](AGENTS.md) pour les règles du dépôt et [les limites actuelles](KNOWN_LIMITATIONS.md) pour éviter de refaire une investigation déjà documentée.

Garde le changement centré sur le problème. Ajoute les vérifications qui prouvent le comportement utile et explique ce qui a réellement été exécuté. Un build, un test hôte et un essai sur téléphone apportent des preuves différentes. Mets à jour les guides français et anglais si le parcours change.

Les annotations humaines, la copie vérifiée avant nettoyage et le modèle original doivent rester protégés. N’embarque pas de poids, de corpus ou de clé dans les sources ou l’APK. Utilise uniquement des dépôts de test autorisés pour les écritures HF. Les publications de packages et releases restent des opérations explicites du mainteneur.

Le code est sous [Apache-2.0](LICENSE). Conserve les attributions des composants tiers et n’ajoute que du contenu que tu peux contribuer.

## English

A clear bug report, small fix or concrete use case is welcome. Discuss large changes in an issue first. Include the app version, device, Android version, reproduction steps and expected result. For models, include the public source and exact variant/revision. Prefer synthetic samples and remove credentials or personal data from logs.

Follow the [development guide](docs/en/DEVELOPMENT_RESUME.md), [repository rules](AGENTS.md) and [known limits](docs/en/KNOWN_LIMITATIONS.md). Keep changes focused, test the behaviour that matters and describe what actually ran. Update both language guides when behaviour changes.

Protect human annotations, verified copies and original models. Do not commit weights, datasets or signing keys. External HF tests need an authorised destination; publication remains an explicit maintainer action. Retain third-party attribution and contribute only content you are entitled to share under the project’s Apache-2.0 licence.
