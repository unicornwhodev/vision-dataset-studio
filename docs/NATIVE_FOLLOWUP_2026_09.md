# ART et LiteRT — investigations du 23 septembre 2026

**Les deux points restent ouverts.** Huit nouvelles exécutions de la suite métier
Release passent **40/40 chacune**, soit 320 exécutions de tests, sur les mêmes
APK x86_64. Le crash ART n'est pas reproduit. L'audit strict LiteRT échoue encore
sur ARM64 et x86_64. Aucun correctif du code applicatif ou des binaires natifs
n'est revendiqué par cette campagne.

[Reçu et empreintes](NATIVE_FOLLOWUP_RECEIPT.json) ·
[Qualification précédente](RELEASE_CLOSURE_2026_09.md) ·
[Analyse LiteRT](LITERT_16K_STATUS.md)

## Ce qui a réellement été exécuté

| Contrôle | Résultat | Portée |
|---|---|---|
| Suite métier Release répétée huit fois | 8 × 40/40, zéro échec, zéro ignoré | Émulateur x86_64/API 35, pages 16 Ko ; mêmes octets d'APK |
| Conservation du périmètre | 40 tests distincts, exclusions opt-in inchangées | Ces répétitions n'ajoutent pas 320 scénarios différents |
| Concordance sources/builds précédents | Vérifiée pour ARM64 et x86_64 | Aucun nouveau build nécessaire : entrées compilées inchangées |
| Audit natif ARM64 | Échec, code 2 | Seul `libtensorflowlite_jni.so` est signalé parmi quatre bibliothèques |
| Audit natif x86_64 | Échec, code 2 | Même bibliothèque signalée ; contrôles LOAD et ZIP réussis |
| Téléphone ARM 16 Ko | Non exécuté | Le Honor disponible reste un appareil 4 Ko |

La paire d'APK a été réinstallée par mise à jour sur l'émulateur avant les huit
passages, sans effacement des données. Les passages n'utilisent aucun pilote
au premier plan ni exemption de gel du processus. Ils durent entre 22,641 et
27,110 secondes par commande d'instrumentation. Le Honor n'a pas été modifié
pendant cette investigation.

## ART : appel identifié, cause non établie

Le tombstone du crash de 20:19:49 UTC a été récupéré. Le thread `WM.task-2`
subit un `SIGSEGV/SEGV_MAPERR` à l'adresse `0x40`, dans
`ClassLinker::ResolveMethod +1057` de `libart.so`. Son build ID est
`e4a3c6c284a8b17d09fd84d661cf181b`.

L'instruction DEX à `ne.invoke +348` a été lue dans **l'APK exacte**, puis
rapprochée de son mapping R8 :

```text
WorkForegroundUpdater.lambda$setForegroundAsync$0 — ligne 96
  -> WorkSpecKt.generationalId(WorkSpec)
  -> résolution de méthode dans ART
```

Le nom obfusqué `ne` regroupe plusieurs lambdas après optimisation ; son nom de
classe d'origine ne suffit donc pas à attribuer le crash à l'éditeur
d'annotations. Le désassemblage ART confirme une lecture de `64(%rbx)` avec
`rbx = 0`. La structure `ArtMethod` référencée par `r15` est remplie de zéros dans
la mémoire capturée. **L'origine de cet état n'est pas démontrée.** La présence
d'ART dans la pile ne suffit pas à exclure une corruption provoquée en amont.

Deux tombstones antérieurs concernent d'autres processus du même émulateur et
le même binaire ART :

| Heure UTC | Processus | Point de crash |
|---|---|---|
| 18:50:27 | `system_server` | `NterpGetShorty +2`, adresse `0x40` |
| 18:50:52 | `com.google.android.gms` | `ClassTable::Lookup +472`, puis chaîne de résolution de méthode |
| 20:19:49 | Vision Dataset Studio | `ClassLinker::ResolveMethod +1057` |

Ces incidents rendent **plausible une instabilité plus large de l'environnement
de recette**. Ils ne prouvent pas une cause commune aux trois crashes.
L'image est `AE3A.240806.043/12960925`, révision 5 ; cette révision reste la plus
récente de l'image Google APIs API 35 x86_64 16 Ko dans le
[catalogue officiel consulté](https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-3.xml).

Les huit nouveaux passages réussis s'ajoutent aux deux reprises déjà consignées
dans le rapport précédent. Ils ne remplacent pas le reçu d'échec à 36 tests et
ne permettent pas de déclarer le crash corrigé. La suite utile est un
reproducteur réduit et une comparaison sur une autre image Android stable en
16 Ko, en conservant les mêmes APK et les preuves d'échec.

## LiteRT : obstacle précis à la reconstruction

L'AAR officiel `com.google.ai.edge.litert:litert:1.4.2` conserve les mêmes
bibliothèques que les APK qualifiées. Les deux binaires contiennent la chaîne
de version `2.22.0-dev0+selfbuilt`. **Cette chaîne n'identifie pas un commit** et
ne permet pas de présenter un snapshot TensorFlow arbitraire comme ses sources.

Le POM renvoie à la branche TensorFlow `master` sans révision. Le petit JAR de
sources ne fournit pas l'implémentation native. Les références publiques du dépôt
LiteRT consultées comportent les tags `v1.4.0` et `v1.4.1`, ainsi qu'une branche
`1.4.1`, mais aucune référence `1.4.2`. La provenance exacte du binaire n'a donc
pas été établie. Un snapshot situé près de la date de publication serait un
**nouveau runtime à qualifier**, pas une reconstruction démontrée de cet AAR.

La règle RELRO employée par l'audit figure dans la
[documentation Android](https://developer.android.com/guide/practices/page-sizes).
L'absence de données modifiables recouvertes par l'arrondi explique la disposition
observée ; elle ne supprime pas le signalement strict. Ni les en-têtes ELF, ni
les règles de contrôle, ni la dépendance distribuée n'ont été modifiés.

Pour fermer ce point, il faut une reconstruction native liée à des sources
vérifiables ou une autre version compatible, suivie des contrôles stricts et de
la recette Interpreter/Flex : inférence, entraînement, Save/Restore, arrêt/reprise
et original conservé. Le passage aveugle à LiteRT 2.x ne remplit pas ces critères :
l'essai antérieur avait échoué sur le contrat Delegate/FlexSave.

## Preuves et confidentialité

Le reçu public contient les empreintes des APK, des huit journaux, du mapping,
du désassemblage et des audits. Les chemins de preuves ignorées sont relatifs
au poste de qualification ; ils ne supposent pas leur présence sur GitHub.
Le tombstone complet, qui comporte de la mémoire du processus, et les journaux
bruts restent dans `test-results/`, hors Git. Aucune clé, aucun token, aucun poids
et aucun corpus utilisateur ne sont ajoutés à la documentation.

Aucun commit, push, workflow GitHub Actions, publication HF ou nouvelle release
n'a été effectué. Les limites de signature rc4/rc5, de durée d'arrière-plan,
de qualité des modèles et de matériel décrites dans la campagne précédente
restent distinctes.
