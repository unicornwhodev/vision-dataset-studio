# Contribuer · Contributing

## Français

Le parcours principal est la production par lots. Préserver les annotations humaines, l’historique des identités, les preuves d’export et l’ordre export → apprentissage facultatif → nettoyage. Ne pas inclure de poids ou de corpus dans l’APK ou les sources.

Lire [AGENTS.md](AGENTS.md), [les tests](TEST_REPORT.md) et [les limites](KNOWN_LIMITATIONS.md). Exécuter le build réel et les contrôles adaptés. Ne pas remplacer les dépendances Android par des stubs, inventer un hash Room, supprimer une assertion ou annoncer comme réussi un test absent. Les résultats sur émulateur ne prouvent pas les performances d’un téléphone.

Les changements de comportement doivent actualiser les guides FR/EN. Les rapports de bug doivent préciser version, appareil/API/ABI, révision/hash du modèle, étapes et résultat attendu. Retirer les tokens, images privées et données personnelles des journaux partagés.

Aucun dépôt HF existant ne sert de terrain de test sans autorisation. La publication publique, les packages et releases attendent la validation demandée par le propriétaire. Conserver les licences et attributions des tiers ; le projet utilise Apache-2.0 pour le code couvert par les droits des contributeurs.

## English

Batch dataset production is the primary workflow. Preserve human annotations, the identity ledger, export receipts and the order export → optional learning → cleanup. Do not bundle weights or user datasets in the APK or repository.

Read [AGENTS.md](AGENTS.md), [validation](docs/en/VALIDATION.md) and [the roadmap](docs/en/ROADMAP.md). Run real builds and relevant checks. Do not substitute Android stubs, invent Room identity hashes, remove assertions or label missing tests as passing. Emulator results do not establish phone performance.

Behaviour changes must update both language guides. Bug reports should include version, device/API/ABI, model revision/hash, reproduction steps and expected behaviour. Remove credentials, private images and personal data from shared logs.

Never use an existing HF repository for QA without authorization. Public publication, packages and releases await the owner’s functional-validation gate. Retain upstream licences and notices; Apache-2.0 covers project code for which contributors hold the relevant rights.
