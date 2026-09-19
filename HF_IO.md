# Acquisition et publication V4

Les sources V3 restent présentes : Dataset Viewer HF, manifeste JSONL HF, manifeste local et dossier SAF. Les tailles de lot restent configurables de 1 à 1 000, sous limites de budget et métadonnées.

## Téléchargement

`.part` + checkpoint `.range`, ETag fort, hash du préfixe, Range/If-Range et bornes strictes. L’identité est le SHA de l’URL complète (sans fragment). Changement de requête ou d’ETag : recommencer plutôt que concaténer. Un serveur répondant 200 entraîne une réécriture complète. Publication du fichier final seulement après complétude.

## Push

Préparer un paquet validé ; résoudre le parent ; refuser les chemins occupés ; enregistrer l’intention en base avant LFS/commit. Le reçu contient repo, branche, prefix, parent, snapshot, manifeste, tailles/hashes. Ne jamais recalculer silencieusement le parent au retry.

États : PREPARED → PUBLISHING → PUBLISHED → VERIFIED → PURGING → PURGED ; branche divergente → CONFLICT. Les annotations sont verrouillées pendant ces états. Une réponse perdue déclenche une comparaison distante, pas un réenvoi aveugle. Une isolation confirmée utilise un nouveau namespace et peut produire un doublon, mais pas écraser l’ancien.

Les tokens restent réservés aux hôtes HF explicitement autorisés. Les headers signés des opérations LFS viennent du contrat HF ; pas d’ajout du token HF à Google ou à un hôte arbitraire. Les réponses JSON HF sont bornées. Aucune suppression distante implicite.

## Preuve de purge

Le reçu distant survit au cache et est revérifié au commit exact. La copie locale SAF est relue avec permission durable. Le moteur conserve les annotations et reçus après suppression des images de travail et des exports temporaires. En l’absence de preuve accessible : refus de purge.

## Qualification

Tests JVM avec MockWebServer exécutés : l’essai de reprise interrompue échoue,
voir `TEST_REPORT.md`. Trois téléchargements publics complets ont réussi dans
l’audit Android ; cela ne valide pas la reprise interrompue. Aucun push sur HF
réel effectué. Voir la recette V4, notamment réponse perdue après commit, LFS
et conflit inter-client. Le journal hôte de logique n’est pas une preuve de service HF.
