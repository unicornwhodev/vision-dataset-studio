# Cadryl rc6 — nouveau nom, Release signée et natifs 16 Ko

**4.2.0-rc6 · code Android 11 · 24 septembre 2026.** Cette prérelease livre une
APK Release ARM64 minifiée de **101,6 Mo**, signée avec la clé durable sauvegardée.
Aucun poids de modèle n’est embarqué.

[Release](https://github.com/unicornwhodev/vision-dataset-studio/releases/tag/v4.2.0-rc6)
· [Publication vérifiée](RC6_PUBLICATION_RECEIPT.json)
· [Preuves sélectionnées](../test-results/rc6-release/README.md)
· [Synthèse JSON](../test-results/rc6-release/summary.json)
· [Reconstruction LiteRT](LITERT_16K_STATUS.md)

## Une identité pour l’app

Vision Dataset Studio devient **Cadryl**. Le logo, l’icône adaptative et monochrome, le splash natif et les repères dans l’interface partagent le même symbole cyan/lilas. Les guides français et anglais ont été réécrits pour partir de l’usage. Le package Android, la signature durable et les projets existants gardent leur identité. [Les sources graphiques](BRAND.md).

## Ce que livre rc6

- LiteRT classique complet, Java et quatre JNI issus du même commit public,
  avec correction du comptage L2 CPUinfo ; XNNPACK et Flex conservés.
- Alignement strict ELF LOAD/GNU_RELRO et ZIP 16 Ko pour les quatre bibliothèques
  64 bits de chaque APK : LiteRT, Flex, Graphics Path et DataStore.
- Suite métier exécutée sur la véritable Release R8 ; adaptateur Moshi des reçus
  d’inférence généré, frontière des API instrumentées explicitement conservée.
- Service d’entraînement avec notification et arrêt ; original conservé et
  poursuite des poids de la copie entraînée avant activation.
- Correctifs d’acquittement HF LFS et protections des données qualifiés dans les
  campagnes précédentes, gardes de recette ART et scripts de build Windows.
- Publication liée aux sources Git, aux signatures et aux octets testés ; trois
  runtimes Maven téléchargeables pour reconstruire après clonage.

## Validation de ces APK

| Contrôle | Résultat | Limite |
|---|---|---|
| Windows, JVM, Python | **70/70 JVM, 87/87 Python** ; lint 0 erreur / 96 avertissements | Contrôles hôte, génération KSP et paires APK Release construites |
| Release ARM64 signée | **40/40 métier, 4/4 UI** | Émulateur API 36/16 Ko, ARM traduit par `libndk_translation` sur x86_64 |
| Release x86_64 signée | **40/40 métier, 4/4 UI** | Même émulateur dédié, mêmes données conservées entre les installations |
| Audit natif | **4/4 bibliothèques par APK**, aucun signalement strict | Ne remplace pas une recette physique ARM 16 Ko |
| Filiation | Original intact, Save/Restore, lot suivant poursuivant la copie entraînée | Fixtures synthétiques ; aucune précision métier démontrée |
| Signature | Certificat durable vérifié sur les APK effectivement testées | Incompatible avec les anciennes APK Debug rc4/rc5 |

Le premier passage x86_64 a fait **39/40** : l’animation du clavier déplaçait le
bouton de création après son repérage. Le test ferme désormais le clavier avant
défilement/clic. Le pilote UI indépendant a aussi conservé un essai **3/4**, puis
reçu une attente de changement de fenêtre et de position stable. Les nouvelles
suites complètes passent sans retirer de test. Ces échecs restent dans les preuves.
Ces essais précèdent la nouvelle identité. Les suites complètes ont ensuite été repassées sur les APK Cadryl. Le test JVM du nom a été actualisé ; les nouveaux reçus de build conservent exactement les mêmes APK signées que celles testées sur Android.

Les 198 fichiers de compilation sont comparés octet par octet aux blobs Git lors
du packaging. Les quatre entrées texte CRLF ont été normalisées avant cette
reconstruction ; aucune exception de normalisation n’est nécessaire pour rc6.
`PACKAGE.json` relie le commit publié aux reçus originaux, sans les réécrire.

## Télécharger et mettre à jour

Installer **`vision-dataset-studio.apk`**, Android 9+ sur ARM64, après comparaison
avec `SHA256SUMS`. L’archive `vision-dataset-studio-4.2.0-rc6-qualification.zip`
contient les APK ARM64/x86_64 et de tests, le pilote UI, les preuves et les notices.
Les APK de tests sont réservées à la recette.

Les trois archives Maven sont `vision-dataset-studio-flex-2.16.1-vds16k1.zip`,
`vision-dataset-studio-graphics-1.0.1-vds16k1.zip` et
`vision-dataset-studio-litert-2.2.0-vds16k2.zip`.
Le package `ghcr.io/unicornwhodev/vision-dataset-studio-qualification:v4.2.0-rc6`
contient les artefacts de qualification, pas une image exécutable. Sa visibilité
privée est conservée ; les fichiers de release GitHub sont publics.

Le certificat durable est
`51ef3e4abf953c62c8427deaecf4349aefffc2270c593229315722457f126895`.
Les installations locales portant ce certificat peuvent recevoir la version 11
avec `adb install -r`. **rc6 ne peut pas mettre à jour les anciennes rc4/rc5
Debug signées avec d’autres clés.** Conserver leurs données ; ne pas désinstaller
pour contourner le conflit. [Procédure de signature](SIGNING.md).

## Limites maintenues

Le Honor 4 Ko a perdu sa connexion durant une campagne antérieure. **rc6 n’est
pas qualifiée sur ce téléphone**, ni sur un appareil ARM physique en pages de
16 Ko. Les anciens résultats Honor et douze minutes d’arrière-plan ne sont pas
attribués à cette APK. La cause ART historique reste non confirmée ; aucun nouvel
incident ART n’est observé sur le nouvel environnement.

Catalogue de modèles sous le nouveau runtime, qualité sur corpus indépendant,
durées prolongées, CI distante et revue des notices natives transitives restent
ouverts. Les 40 tests communs ne remplacent pas les campagnes HF, corpus et pannes
opt-in. [Limites complètes](../KNOWN_LIMITATIONS.md).

## English

rc6 ships a **101.6 MB minified ARM64 Release APK**, signed with the backed-up
durable key. Source-built LiteRT and the CPUinfo fix pass strict ELF/ZIP 16 KB
checks. Each exact signed ARM64/x86_64 APK passes **40 core tests and four UI
scenarios** on the API 36/16 KB emulator; ARM uses translation, not physical
hardware. **70 JVM and 87 Python tests** pass; lint has no errors and 96 warnings.

Original-model preservation, continued training and Save/Restore pass synthetic
tests. Initial keyboard-related test failures and their fixes remain in the
evidence. Physical Honor/ARM 16 KB, historical ART root cause, prolonged operation,
model quality, remote CI and native transitive notice review remain open.
rc6 cannot update differently signed rc4/rc5 Debug installations; preserve their
data. The private GHCR package contains artifacts, not a runnable container.
