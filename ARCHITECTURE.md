# Architecture — Vision Dataset Studio UWD

Le moteur reste générique : schéma canonique, tâches composables, contrats de modèle, exporteurs et packs de configuration sans domaine imposé.

| Couche | Responsabilité V4 |
|---|---|
| `core/storage` | Écriture privée durable, checkpoint de téléchargement, contrôle de propriété des chemins, copie SAF relue |
| `core/workflow` | États de verrouillage et décision de réconciliation ; statistiques de mesure |
| `data/db` | Schéma 3, migrations additives communes aux tests et à Room |
| `data/json` | Factory Moshi unique, générateurs puis fallback |
| `data/hf` | Client réseau borné, reprise contrôlée, commit parent explicite, vérification des reçus |
| `domain/batch` | Orchestration et états persistés, preuves locales/distantes et purge reprenable |
| `domain/export` | Canonique et projections ; passation des paquets et archives |
| `domain/inference` | Adaptateurs existants, catalogue inspecté, mesures sur l’appareil |
| `ui` | Contrôles de projet/modèle, confirmations, reprise/conflit et export des mesures |

Invariant : une prédiction n’est pas une validation. Une réception HTTP n’est pas une preuve de copie. Un fichier effacé ne doit pas effacer son reçu. Un état migré ne doit pas créer une preuve absente. Une URI temporaire ne doit pas autoriser une purge irréversible.

Les politiques de cache/transfert sont testées sur hôte ; les orchestrations dépendant de Room/Android restent à qualifier. Les états persistants permettent la reprise demandée mais ne sont pas un service d’exécution en arrière-plan.

Le catalogue n’importe aucun code de post-traitement fourni par un utilisateur : les contrats restent déclaratifs avec adaptateurs intégrés et validations. Le client de modèle HTTP n’est pas un tunnel vers un serveur distant.
