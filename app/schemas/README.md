# Schémas Room

KSP écrit le schéma réel de version 3 dans ce dossier au premier build. Aucun fichier JSON de schéma ni hash d’identité Room n’a été inventé.

Les fixtures SQL historiques sont dans `src/test/resources/legacy-v1.sql` et `legacy-v2.sql`, dérivées des déclarations d’entités des deux archives fournies. Ce ne sont pas des bases de production exportées. Les tests `RoomMigrationV4Test` et `RoomMigrationOnDeviceTest` ouvrent les fixtures avec Room et demandent une validation du schéma final généré. Ils doivent être exécutés sur une chaîne Android disponible.
