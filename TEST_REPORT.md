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
