# Documentation

**Vision Dataset Studio · 4.2.0-rc5**

[Présentation française](../README.md) · [English overview](../README.en.md) · [Téléchargements / Downloads](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc5)

## Commencer / Start here

| Votre objectif / Your goal | Français | English |
|---|---|---|
| Produire un premier dataset / First dataset | [Prise en main](GETTING_STARTED.md) | [Getting started](en/GETTING_STARTED.md) |
| Utiliser les lots et exports / Batches and exports | [Production](BATCH_PRODUCTION.md) | [Production](en/BATCH_PRODUCTION.md) |
| Préserver et entraîner un modèle / Keep and train a model | [Versions et checkpoints](MODEL_LINEAGE.md) | [Versions and checkpoints](en/MODEL_LINEAGE.md) |
| Installer l’environnement / Developer setup | [Poste de travail](DEVELOPMENT_RESUME.md) | [Workstation](en/DEVELOPMENT_RESUME.md) |

## Comprendre et intégrer / Understand and integrate

| Sujet / Topic | Français | English |
|---|---|---|
| Architecture et données / Architecture and data | [Architecture](ARCHITECTURE.md) | [Architecture](en/ARCHITECTURE.md) |
| Workflows et agent optionnel / Workflows and optional agent | [Workflows](WORKFLOWS.md) | [Workflows](en/WORKFLOWS.md) |
| Contrat LiteRT entraînable / Trainable LiteRT contract | [Contrat](LITERT_TRAINING_CONTRACT.md) | [Contract](en/LITERT_TRAINING_CONTRACT.md) |
| Formats canoniques et projections / Canonical formats and projections | [Contrats de données](../DATA_SCHEMA.md) | [Data contracts](../DATA_SCHEMA.md) |
| Runtime Flex 16 Ko / 16 KB Flex runtime | [Construction et preuves](FLEX_16K.md) | [Build and evidence](FLEX_16K.md) |
| Contribution / Contributing | [Guide](../CONTRIBUTING.md) | [Guide](../CONTRIBUTING.md) |

Catalogues publics / Public catalogues : [Charlbi — modèles génériques](https://huggingface.co/Charlbi/Lite_rt_prepared_for_android_dataset_builder) · [FireViewer — feu/fumée](https://huggingface.co/fireviewer/litert-models). Leurs contrats, licences et résultats restent propres à chaque variante.

## Vérifier l’état réel / Verify the current state

| Preuve / Evidence | Accès / Read |
|---|---|
| Interruptions réelles et Honor, après rc5 / Post-rc5 real faults and Honor | [Rapport FR](P1_QUALIFICATION_2026_09.md) · [Reçu JSON](P1_QUALIFICATION_RECEIPT.json) |
| Signature durable et restauration / Durable signing and recovery | [Procédure](SIGNING.md) |
| Livraison rc5 / rc5 publication | [Plan FR](RELEASE_PLAN.md) · [Plan EN](en/RELEASE_PLAN.md) · [Reçu JSON](RC5_PUBLICATION_RECEIPT.json) |
| Build Windows et Android 4/16 Ko / Windows and 4/16 KB Android | [Rapport](WINDOWS_QUALIFICATION_2026_09.md) · [Preuves brutes](../test-results/windows-rc5-release/README.md) |
| Catalogue LiteRT / LiteRT model campaign | [Résultats FR](LITERT_QUALIFICATION.md) · [Results EN](en/LITERT_QUALIFICATION.md) |
| Tests et limites / Tests and limitations | [Tests FR](../TEST_REPORT.md) · [Validation EN](en/VALIDATION.md) · [Limites FR](../KNOWN_LIMITATIONS.md) · [Limits EN](en/KNOWN_LIMITATIONS.md) |
| Recette à poursuivre / Remaining qualification | [Critères Android](ANDROID_QUALIFICATION.md) · [Roadmap FR](ROADMAP.md) · [Roadmap EN](en/ROADMAP.md) |
| Captures et graphiques / Screenshots and figures | [Galerie et provenance](VISUALS.md) |
| Secrets, droits et notices / Secrets, rights and notices | [Publication](PUBLICATION_CHECKS.md) · [Licence](../LICENSING_STATUS.md) · [Composants tiers](../third_party/README.md) |

## Lire les résultats / Read the results

La compilation, la suite Android de base, les tests par modèle et les mesures de qualité sont des preuves distinctes. Chaque résultat conserve sa date, son build, son runtime et sa portée. Les audits historiques restent consultables ; leurs chiffres ne décrivent pas automatiquement rc5.

Builds, core Android tests, per-model execution and quality measurements are separate evidence. Each result retains its date, build, runtime and scope. Historical audits remain available and must not be read as current rc5 qualification.
