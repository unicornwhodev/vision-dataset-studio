# Contrôles avant publication · Publication checks

20 septembre / September 2026. Sources Android testées / tested Android source: `680a2b78876b1d670671421fd3ef97b8268e22c0`.

## Français

- Gitleaks 8.30.1 : archive officielle vérifiée par SHA-256, analyse de tout l’historique Git et des fichiers candidats à la publication.
- Aucun secret confirmé. Quatre alertes génériques du manifeste de sources ont été vérifiées : chacune est le SHA-256 recalculé du fichier Kotlin nommé, pas un identifiant d’accès. Aucune règle générale n’a été désactivée.
- Les fichiers `.env`, variantes locales, clés, APK, poids, caches et données de recette non sélectionnées sont exclus. Seules les preuves synthétiques relues sous `test-results/batch-production/` sont ajoutées explicitement.
- Les deux APK du build `20260919T234604Z-8a3382d3e997` ont été sauvegardées localement sous `dist/pod-handoff/final-build/`. Les SHA-256 ont été recalculés et comparés aux reçus. Ces binaires ne sont pas ajoutés à Git.
- Les scans ne constituent pas une preuve absolue d’absence de secrets. La publication conserve le statut de qualification et les limites documentées.

## English

- Gitleaks 8.30.1: official archive verified against its SHA-256 checksum; full Git history and publication candidate files scanned.
- No confirmed secret. Four generic findings in the source manifest were checked: each is the recomputed SHA-256 of its named Kotlin file, not an access credential. No general detection rule was disabled.
- `.env` files and local variants, keys, APKs, weights, caches and unselected QA data are excluded. Only reviewed synthetic evidence under `test-results/batch-production/` is explicitly included.
- Both APKs from build `20260919T234604Z-8a3382d3e997` were backed up locally under `dist/pod-handoff/final-build/`. Their SHA-256 digests were recomputed and matched to the receipts. The binaries are excluded from Git.
- Scans cannot prove the absolute absence of secrets. Publication preserves the qualification status and documented limitations.
