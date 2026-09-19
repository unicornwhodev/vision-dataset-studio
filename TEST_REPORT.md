# Tests — qualification 4.2.0-rc2 sur le pod

Exécution réelle du 19 septembre 2026, JDK 21.0.12.1+1, Gradle 9.3.1, SDK 36.
Les résultats de préparation RC1 sont conservés dans l’archive initiale et le
commit d’import `c8a8241`. Ce rapport décrit la nouvelle exécution.

| Contrôle | Résultat |
|---|---|
| Tests portables | 196 réussis après correction de l’identité RC2/licence |
| Compilation principale | Réussie après trois corrections Kotlin documentées |
| APK Debug et APK de tests | Produites ; signatures, identité et SHA-256 vérifiés |
| Schéma Room v3 | Généré réellement par KSP et conservé dans `app/schemas/` |
| Tests JVM Android | 25 réussis / 26 ; 1 échec |
| Migrations Room sous Robolectric | 2 réussies ; fixtures SQL reconstruites |
| Tests Moshi | 6 réussis avec les dépendances réelles |
| Capture Compose sous Robolectric | Test exécuté avec succès, pas une recette d’écran complète |
| Lint Debug | 0 erreur ; 68 avertissements |
| Installation et premier affichage | APK installée ; Atelier puis Modèles affichés |
| Tests instrumentés API 28 | Identité + 2 migrations réussies ; suite bloquée dans le fournisseur SAF |
| CI API 28/35 et téléphone | Non exécutés |
| HF réel, modèle ARM, lots 2+1, purge/reprise | Non qualifiés |

Échec JVM : `HfTransferV4Test.disconnectedDownloadResumesHashedPrefix`, assertion
de succès de la reprise à la ligne 38. Ce test utilise un serveur HTTP local de
simulation ; aucun dépôt Hugging Face n’a été modifié.

Tentative complète : `20260919T183132Z-8d4c24b3ba18`, statut **failed**. Les deux
APK restent des artefacts de diagnostic, pas une version qualifiée. Le packager
refuse cette tentative comme attendu. La première tentative de compilation
échouée reste conservée séparément.

Le [rapport du pod](docs/POD_VALIDATION.md) donne chemins, limites et suites à
terminer. Les preuves brutes restent sur le pod et une copie légère est dans
`test-results/pod-20260919/` sur le Chromebook (hors Git).

## Refonte de l’interface

Tentative `20260919T200729Z-bec11e4607bc` : APK principale et de tests compilées,
signées, vérifiées puis installées. Les **2 tests Compose instrumentés passent**
sur l’AVD API 28 (74,293 s de suite JUnit). Ils couvrent l’accès aux projets et au
catalogue sans téléchargement, puis Importer/Export/Qualité/Atelier à 320 dp.

Les 26 tests JVM ont été réexécutés : 25 réussissent, le même test de reprise HTTP
échoue (sur cette exécution, à la ligne 21). Lint : 0 erreur, 68 avertissements.
Les contrôles d’identité ont été relancés localement : 12/12 réussis.
Recette manuelle sur trois images synthétiques importées par SAF dans un projet
séparé : création d’une boîte, annuler/rétablir, propriétés, fermeture et reprise
avec conservation de la boîte. Le canevas mesure 480 × 515 px à 320 dp.
La suite SAF bloquée n’a pas été relancée par ces tests UI.

Le reçu global reste **failed**, malgré les tests UI réussis. La refonte ne rend
pas la version publiable. Voir [la recette visuelle](docs/UI_REDESIGN.md).

Dernière correction : `20260919T203710Z-e9cd5c4ddbbe` borne les aperçus de l’accueil pour éviter leur
débordement sur grand écran. Les deux tests Compose repassent à 960 dp
(75.807 s JUnit). L’accueil est contrôlé visuellement après installation ;
la boîte enregistrée reste présente dans l’éditeur. Les vérifications détaillées
des gestes à 320 dp ci-dessus portent sur `20260919T200729Z-bec11e4607bc` ;
le code de l’éditeur est identique entre les deux tentatives.

## Audit de complétude fonctionnelle

Sur la même APK principale (`abf2f50f…03c2d`), huit nouveaux tests instrumentés
réussissent en deux suites : 6 tests en 51,818 s JUnit, puis 2 en 15,243 s.
Ils couvrent trois téléchargements/inférences avec de vrais poids, préannotation
de lot, HTTP local, export, pack/prompt et correcteur de points persistant.
Les 67 tests numériques existants ont également été relancés avec succès.

Deux contrôles supplémentaires échouent : provenance brute des boîtes après
correction adaptative et validateur Python face au ZIP réellement produit par
Android. L’inspection indépendante de l’archive passe (CRC, SHA et projections).
Ces défauts restent ouverts, comme l’échec JVM et le blocage SAF précédents.

L’entraînement continu des poids, le moteur d’agent et les workflows automatisés
sont absents. Voir [la matrice et les preuves](docs/IMPLEMENTATION_AUDIT.md).
