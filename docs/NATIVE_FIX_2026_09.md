# Correctifs LiteRT et qualification ART — 23–24 septembre 2026

**LiteRT passe désormais l’audit strict 16 Ko sur ARM64 et x86_64.** Le runtime
est construit à partir de sources publiques épinglées, avec son API Java complète.
Un crash supplémentaire dans CPUinfo, reproduit sur ARM traduit, est corrigé à
la source. La Release minifiée passe ses 40 tests métier communs et ses quatre
scénarios UI sur le nouvel émulateur Android 16/16 Ko.

**ART : environnement de recette remplacé et contrôle bloquant ajouté.** Aucun
crash ART n’est observé dans les nouveaux passages. La cause du crash historique
reste non confirmée ; ce résultat ne constitue pas un correctif du code d’ART.

[Reçu vérifiable](NATIVE_FIX_RECEIPT.json) · [Reconstruction LiteRT](LITERT_16K_STATUS.md)
· [Procédure Release](RELEASE_TESTING.md) · [Limites générales](../KNOWN_LIMITATIONS.md)

## Ce qui a changé

L’AAR 1.4.2 était opaque : sa révision source exacte n’a pas été retrouvée.
La variante `com.unicornwhodev.thirdparty:litert-interpreter:2.2.0-vds16k2`
construit l’API classique et les quatre JNI à partir du
[commit LiteRT `145c7523`](https://github.com/google-ai-edge/LiteRT/tree/145c7523ff08d5e57ab5c582141775eea47da9c7),
tag `v2.2.0`. `Interpreter`, `Delegate`, le wrapper et JNI viennent du même commit.
Les anciennes classes ou bibliothèques 1.4.2 ne sont pas réutilisées.
Flex `2.16.1-vds16k1`, Graphics Path `1.0.1-vds16k1` et DataStore restent en place.

Le [manifeste](../config/litert-source.json) fixe sources, outils et empreintes.
Le build utilise NDK r26d et les deux options de page du linker à 16 384 octets.
Les gardes vérifient le reçu, les quatre ABI du runtime et les octets réellement
embarqués dans chaque APK. Aucun en-tête ELF ni critère d’audit n’a été modifié
pour obtenir un résultat vert.

### Crash CPUinfo reproduit et corrigé

La première variante reconstruite passait l’audit statique et les tests x86_64,
mais plantait au septième test sur ARM64 via `libndk_translation` : inférence SSD,
initialisation XNNPACK, SIGSEGV à l’adresse `0x8`. Le PC invité `0x428b68`, le
mapping du linker et le désassemblage identifient `cpuinfo_arm_linux_init`.

CPUinfo comptait les caches L2 d’après la microarchitecture décodée, puis utilisait
une taille sysfs dans la passe de remplissage. Pour une microarchitecture inconnue,
cela pouvait remplir une table L2 jamais allouée. Le
[patch source](../third_party/patches/cpuinfo-l2-count.patch) applique la même taille
sysfs au comptage et ajoute des contrôles de capacité si la topologie change entre
les passes. XNNPACK reste actif. La licence BSD et la notice de modification sont
conservées dans le dépôt et l’AAR.

La même suite ARM traduite passe ensuite **40/40**, dont l’inférence qui plantait,
l’apprentissage Flex et Save/Restore. Le journal de l’échec initial est conservé.

### Environnement ART et pilote UI

Un nouvel AVD dédié utilise l’image officielle
`system-images;android-36;google_apis_ps16k;x86_64`, révision 7, démarrée sans
chargement ni sauvegarde de snapshot. L’ancien AVD API 35 est conservé.
Les lanceurs Debug, Release métier et UI archivent le fingerprint et le tampon
de crash avant/après. Une trame fautive `#00` dans `libart.so`, y compris dans un
processus système, bloque la qualification.

Les trois tombstones ART historiques sont détectés. Un essai sur l’ancien AVD
est effectivement refusé **avant installation et avant les tests**. Ce garde-fou
ne remplace pas un diagnostic de tous les types de crash ; il n’efface aucun log.
Le nouveau tampon conserve notamment le crash CPUinfo intermédiaire, distinct d’ART.

Le pilote UI rencontrait aussi une course après défilement : il saisissait avant
la stabilisation et la prise de focus du champ. Il attend désormais sa position
stable puis son focus observé. Les quatre scénarios sont conservés et passent.

## Résultats du candidat final

| Contrôle | Résultat | Portée |
|---|---|---|
| Compilation Windows et KSP | Deux paires APK Release/application-tests construites | ARM64 et x86_64, R8 et réduction des ressources actifs |
| Tests JVM et lint | **70/70**, aucun ignoré ; **0 erreur, 94 avertissements** | Sources finales du candidat |
| Outils Python | **82/82** | Dont les trois tests du garde ART ; distinct de l’exécution Android |
| Audit ELF et ZIP 16 Ko | **4/4 bibliothèques 64 bits par APK**, aucun signalement strict | Flex, LiteRT, Graphics Path et DataStore ; ARM64 et x86_64 |
| Release x86_64 | **3 passages de 40/40**, aucun ignoré | Mêmes octets d’APK sur le nouvel AVD API 36, pages 16 384 |
| Release ARM64 traduite | **40/40**, aucun ignoré | ABI du paquet `arm64-v8a`, hôte x86_64 et `libndk_translation` ; ce n’est pas un téléphone ARM |
| UI Release indépendante | **4/4 x86_64 et 4/4 ARM64 traduite** | Pilote corrigé, même AVD ; aucun service/exemption de test nécessaire |
| Original et copie entraînée | Empreinte originale inchangée ; fin du lot 1 = départ du lot 2 ; nouveaux poids après lot 2 | Données synthétiques, checkpoint absent refusé, poursuite avant activation vérifiée |
| Honor physique, 4 Ko | **14 succès, puis interruption ADB** sur la variante intermédiaire | Suite incomplète ; aucun résultat complet du candidat final sur Honor |

La suite commune conserve ses 40 tests. Les campagnes opt-in HF, corpus de modèles,
interruptions externes, conservation après mise à jour et arrière-plan restent
distinctes. Aucune précision métier ou performance ARM physique n’est déduite
de ces compteurs. Les essais s’exécutent dans l’application non débogable ; l’accès
root de l’AVD a seulement servi à déposer/relire les fixtures synthétiques, puis
ADB a été rétabli en UID 2000.

Les sources compilées sont identifiées par leurs empreintes dans les manifestes
de build, sur la base Git `bedd1a262063e345e3c741827d5765c9b2936f78` avec modifications
locales. Ce commit seul ne représente pas le candidat. La reconstruction x86_64
avec le manifeste final produit des APK non signées **identiques octet pour octet**
à celles ayant passé les trois séries ; le correctif CPUinfo ne change que les JNI ARM.
Le reçu lie explicitement ces builds et leurs APK signées.

| Artefact final | Taille | SHA-256 |
|---|---|---|
| ARM64, certificat durable, prêt pour Honor | 101 527 862 octets | `2bd7fd82436f56074a0127d3bbc9f96f1765d602e43a332f9e5cdb91eaf98cc5` |
| ARM64, certificat de l’émulateur, testé | 101 523 766 octets | `fdaa85f12601d831b933d6d0e380f73a31e906480aed09d6b531444e04ff5d32` |
| x86_64, certificat de l’émulateur, testé | 117 858 602 octets | `7ac583038da370fd7c22b0645f8c0589dd1c5b0005803147d7ba424e8d6d1516` |
| AAR LiteRT `2.2.0-vds16k2` | Voir le reçu | `c1644503243c3c78092f949e2fb3cd47eafaf252bb87e18535eb29da916810ae` |

## Ce qui reste à qualifier

Le Honor était connecté et les données précédentes ont été relues avant la mise
à jour intermédiaire. Sa liaison Wi-Fi s’est coupée après 14 succès. Le dernier
candidat ARM64 corrigé n’y est **pas installé ni qualifié**. Reprendre les 40 tests,
les quatre scénarios UI et les sondes de conservation dès son retour, avec le même
certificat durable et sans désinstallation. Les anciens 40/40 et l’essai de douze
minutes sur Honor concernent des candidats précédents.

Aucun appareil **ARM physique en pages de 16 Ko** n’est disponible. La cause ART
historique reste ouverte. Les longues durées sur le nouveau runtime, la matrice
des conversions et leur qualité sur corpus indépendant restent à vérifier.

Le workflow CI prépare les trois runtimes et l’émulateur API 36/16 Ko ; aucune
exécution distante n’a été déclenchée. L’inventaire final contient **109 dépendances
runtime**, sans métadonnée Maven de licence manquante, et la licence CPUinfo est
embarquée. La revue des autres notices natives transitives reste ouverte.

Les rapports bruts, y compris les tentatives échouées, restent sous
`test-results/native-fix-20260923/` et les builds sous `dist/`, exclus de Git.
Le reçu public ne contient ni adresse ADB, secret, chemin de coffre ni corpus.
Les assets rc5 publiés n’ont pas été remplacés ; aucun commit, push ou release
n’a été effectué pour cette correction.
