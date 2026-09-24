# Cadryl rc6 — les preuves de cette livraison

24 septembre 2026. Les APK Release ARM64 et x86_64 sont minifiées et signées avec la clé durable. Chacune passe 40/40 tests métier et 4/4 scénarios UI sur l’émulateur API 36/16 Ko. L’ARM utilise `libndk_translation` sur un hôte x86_64 : ce n’est pas une recette sur téléphone.

`summary.json` relie les résultats aux empreintes des APK. Le packaging compare les sources compilées aux blobs Git et écrit leur commit dans `PACKAGE.json`. Les reçus finaux ont été reconstruits après correction du test JVM qui attendait l’ancien nom : les APK signées restent identiques aux octets déjà testés sur Android.

Les premières courses de clavier (39/40 métier x86_64 et 3/4 UI ARM64) précèdent l’intégration Cadryl. Elles restent dans les deux dossiers `initial-*`, avec les APK et pilotes concernés. Le premier passage JVM de la nouvelle identité a également conservé son échec attendu sur l’ancien nom. Les nouveaux passages complets sont ceux des dossiers `arm`, `x86` et `jvm`.

Les captures et l’enregistrement du lancement sont réels et non retouchés. `package.txt` est un extrait explicitement identifié du dump brut. Les identifiants synthétiques sans utilité pour la filiation sont omis du reçu public de continuité. Aucun poids, secret ou corpus utilisateur n’est inclus.

Les essais Honor, HF, interruptions externes, modèles publics et arrière-plan prolongé gardent leurs campagnes distinctes. Ces résultats ne rendent pas rc6 stable à eux seuls.
