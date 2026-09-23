# Qualification P1 — interruptions, signature et appareil

Campagne du **23 septembre 2026**, après rc5. Les résultats ci-dessous concernent
les sources et APK de cette campagne. **La release rc5 déjà publiée ne contient
pas le correctif HF décrit ici.** Le candidat signé reste une livraison locale.

[Reçu public expurgé](P1_QUALIFICATION_RECEIPT.json) · [Signature et récupération](SIGNING.md)

Build Windows : **70 tests JVM, 75 tests Python**, 99 fichiers KSP et zéro
erreur lint. Ces résultats hôte sont distincts des essais Android ci-dessous.

## Ce que les pannes réelles ont montré

| Essai | Panne ou action réellement appliquée | Résultat |
|---|---|---|
| Stockage plein | Écriture de 8 Mio dans un tmpfs de 1 Mio, sous SELinux Enforcing | Véritable `ENOSPC` ; ancien export de 64 Kio intact, fichier temporaire retiré |
| Permission révoquée | Document créé par DocumentsUI, copie relue, droit persistant révoqué, processus arrêté | Nouvelle lecture refusée ; purge bloquée ; annotation, image, original et ZIP privé intacts |
| Volume retiré | Nouvelle carte SD virtuelle dédiée, export SAF relu, volume démonté | Purge refusée ; données locales intactes ; volume remonté après l’essai |
| Téléchargement HF coupé | Paquets IPv4/IPv6 de l’UID de recette bloqués après le début du transfert | Ancien fichier conservé ; checkpoint de 7 352 621 octets ; reprise réelle en **HTTP 206**, fichier final de 24 117 576 octets conforme au SHA publié |
| Publication privée HF | Nouveau dépôt privé autorisé, image synthétique et exports réels | Deux travailleurs, une réservation gagnante ; publication et relecture Android ; relance sans nouveau commit ; purge avec historique d’identité conservé |
| Réponse de commit perdue | Arrêt forcé du processus après la réponse HTTP 2xx du vrai serveur, avant son traitement par le moteur | Intention `PUBLISHING` retrouvée ; commit distant reconnu et relu ; aucun doublon ; annotations et images conservées |
| Conflit HF | Deux commits utilisant le même parent, après acceptation du premier | Second commit refusé ; aucun déplacement silencieux du parent ; fichier candidat local conservé |
| Purge interrompue | Arrêt réel après la première suppression sur 256 images | Reprise depuis `PURGING` ; nettoyage terminé ; deux annotations humaines, copie vérifiée et reçu conservés |
| Sauvegarde/restauration | Arrêt de l’app, sauvegarde privée relue par SQLite, restauration d’une copie séparée de la base et de l’image | Empreintes de transfert vérifiées ; Room rouvre la copie et retrouve annotation/cursor ; installation `-r` conserve également les données actives |

Les pannes réseau et de stockage sont injectées sur **l’émulateur dédié**. Aucun
volume du téléphone ou disque utilisateur n’est rempli, démonté ou effacé.
Les règles réseau ne visent que l’UID de recette et sont retirées après l’essai.
Le dépôt HF est privé et contient des fixtures synthétiques. Aucun dépôt de
modèles existant ne reçoit d’écritures de QA.

Les phases volontairement tuées n’ont pas un résultat JUnit réussi : leurs
reçus enregistrent l’arrêt attendu, puis les assertions réussies à la reprise.

## Défaut de publication trouvé et corrigé

Le vrai point de terminaison LFS de vérification renvoie **HTTP 200,
`Content-Type: text/plain`, corps `OK`**. Le client essayait de décoder ce corps
comme un objet JSON et arrêtait la publication après l’envoi du binaire.

Le client accepte maintenant l’accusé HTTP des actions LFS de vérification et
de finalisation. Les réponses des API qui fournissent des objets restent
décodées strictement. Un refus HTTP bloque toujours le commit. La relecture
par taille et SHA-256 reste obligatoire avant purge. Trois régressions JVM
couvrent `OK`, un corps vide et un refus 403 ; la publication privée Android
confirme le correctif contre le serveur réel.

Référence : [contrat de vérification Git LFS](https://github.com/git-lfs/git-lfs/blob/main/docs/api/basic-transfers.md#verification).

## Signature et distribution

La clé durable RSA 4096 est fixée et conservée hors dépôt. Le certificat public
est épinglé dans [la configuration](../config/release-signing.json). La sauvegarde
sur D:\coffre a été vérifiée par empreinte, relue avec son secret portable et
utilisée pour signer l’APK ARM64. Voir [la procédure](SIGNING.md).

Le candidat Release optimisé pèse **100 219 514 octets** et ne contient aucun
poids. Son SHA-256 est
`b30a60fbca96d40a90fa4c8ee23d38782fc0e51735458202051a98b7f6a71e94`.
L’installation de cette vraie APK non débogable sur le Honor, en remplacement
d’une APK de recette portant la même clé, conserve les deux jeux de données
préparés : annotation humaine, image, curseur et modèle original. Une sonde
d’instrumentation utilisant uniquement les API Android lit ces données ;
l’interface Release démarre ensuite normalement. Cela ne transforme pas la
suite Debug en suite complète Release.

Les installations rc4 et rc5 Debug utilisent d’autres certificats. Cette
continuité ne peut pas être rétablie sans leurs clés d’origine. Elles doivent
être conservées si elles contiennent des données. La clé nouvelle est destinée
à maintenir la continuité des prochaines versions, sous l’identité Android
existante.

## Téléphone et 16 Ko

L’Honor **ELI-NX9** est bien joignable en ADB sans fil : ARM64, Android API 36,
pages réelles de **4 096 octets**. Ce n’est pas un appareil 16 Ko.
La suite de base Debug y passe **39/39 en 42,483 s**, aucun test ignoré.
La recette 16 Ko utilise l’émulateur API 35 x86_64 : **39/39**, aucun test ignoré.
La sauvegarde/restauration d’une copie Room et le remplacement de l’APK sont
également vérifiés sur les deux appareils.

Les premières passes longues de l’instrumentation Honor se figent quand leurs
activités se ferment. Une reprise qui rouvrait l’activité métier a terminé
38/39 tests, avec un échec de nettoyage de fixture ; elle reste un échec dans
les reçus. La passe complète réussie utilise un écran Debug de recette séparé,
sans second ViewModel métier, pendant les classes sans UI. Les exceptions de gel
restent limitées aux processus de test et prennent fin avec eux.
Ces conditions ne qualifient pas l’exécution prolongée en arrière-plan.

Le reçu global de cette passe Honor conserve un échec de reconnaissance de
l’accueil **après les 39 tests réussis** : panneau de notifications, puis libellé
« Studio » absent de la navigation compacte. La reprise vérifie séparément les
62 nœuds de l’interface de l’app. Le script reconnaît maintenant cette navigation
sans accepter une fenêtre système ou l’écran de recette à sa place. Les essais
antérieurs ne sont pas réécrits en succès.

Le lancement à froid de la Release a donné **248 ms** lors d’une observation.
Une observation Debug indique **194 385 Kio de PSS**. Ce sont des instantanés,
pas des mesures répétées de latence modèle, de charge prolongée ou de thermique.

### Les signalements natifs

DataStore passe de **1.1.7 à 1.2.1**. Son `libdatastore_shared_counter.so`
satisfait désormais les contrôles LOAD/RELRO pour les deux ABI 64 bits.

Deux bibliothèques gardent un avertissement strict de fin RELRO, chacune pour
ARM64 et x86_64 : `libandroidx.graphics.path.so` et `libtensorflowlite_jni.so`.
L’audit strict reste inchangé et conserve ses **quatre signalements**.

L’examen des segments montre que l’arrondi de protection RELRO à 16 Ko ne
recouvre aucun octet d’un segment LOAD inscriptible situé hors de RELRO :
les segments suivants commencent au-delà de la zone arrondie. C’est cohérent
avec l’absence de crash pendant la recette 16 Ko, sans prouver l’absence de
tout défaut natif.
Cette observation de structure ne certifie pas tous les appareils, ni une
acceptation par un magasin d’applications. Aucun en-tête ELF n’a été réécrit.

```powershell
python tools/qa/check_apk_page_sizes.py --apk CHEMIN_APK --output dist/native-strict.json
python tools/qa/explain_relro_layout.py --apk CHEMIN_APK --output dist/native-explanation.json
```

Références : [exigences Android 16 Ko](https://developer.android.com/guide/practices/page-sizes),
[protection RELRO du linker Android](https://android.googlesource.com/platform/bionic/+/android16-qpr2-release/linker/linker_phdr.cpp).

## Reproduire les essais contrôlés

Construire et installer les APK dont les empreintes sont vérifiées par
`tools/qa/resolve_apks.py`. Les outils refusent une construction incomplète.

```powershell
python -X utf8 tools/build_android.py
$env:VDS_ALLOW_TEST_INSTALL='1'
python tools/qa/run_device_qualification.py --serial EMULATEUR_DEDIE --expected-page-size 16384 --training-fixture dist/fixtures/training-fixture --tokenizer-fixture dist/fixtures/hf-runtime-fixture
python tools/qa/qualify_installed_data.py --serial EMULATEUR_DEDIE
python tools/qa/run_real_fault.py --serial EMULATEUR_ROOT_DEDIE --scenario enospc
python tools/qa/run_real_fault.py --serial EMULATEUR_ROOT_DEDIE --scenario download
python tools/qa/run_real_fault.py --serial EMULATEUR_ROOT_DEDIE --scenario purge
```

Sur le Honor dédié, la passe de base utilise `--expected-page-size 4096`,
`--signed-apks RECU_DE_SIGNATURE_QA`, `--qa-foreground-activity` et
`--prevent-test-process-freezing`. Elle installe uniquement des APK vérifiées
avec la même clé. Pour relire les données déjà préparées dans une vraie Release :

```powershell
./tools/qa/run_release_continuity.ps1 -Serial APPAREIL_DEDIE -PreservationCases CAS_PREPARE -KeyDirectory CHEMIN_PRIVE_CLE -OutputDirectory NOUVEAU_DOSSIER_PREUVES
```

Ce dernier outil compile et signe une sonde Android séparée ; il ne remplace
pas l’APK principale et ne modifie pas sa base. Le runner AndroidX standard
de Debug ne peut pas initialiser ces tests contre l’app Release minifiée ;
cette tentative échouée reste dans les preuves, distincte de la sonde réussie.

Les scénarios `hf-live`, `hf-lost-response`, `hf-conflict` exigent
`--allow-hf-writes --repo DEPOT_PRIVE_QA_AUTORISE`. La création demande une
autorisation distincte. Une reprise dans ce même dépôt de QA utilise
`--reuse-qa-repo` ; ne jamais appliquer cette option à un dépôt de modèles ou
de données utilisateur. `hf-conflict` reprend le `--case` du scénario de
réponse perdue. Le jeton provient du stockage local HF, passe par stdin dans un
fichier Android privé et est consommé puis supprimé par l’instrumentation.

Les trois étapes SAF sont les méthodes de `ExternalFaultQualificationTest` :
préparation avec choix réel dans DocumentsUI, copie/vérification puis révocation
ou démontage, et contrôle du refus de purge dans un nouveau processus. Attendre
la fin complète de chaque instrumentation avant de commencer la suivante.

## Portée et limites conservées

- La restauration testée rouvre une **copie indépendante de la base et de son
  image** ; elle ne restaure pas une sauvegarde Android système, des clés du
  Keystore matériel ou les permissions SAF d’une autre installation.
- Les migrations 1/2/3 vers 4 utilisent les fixtures documentées. Aucune ancienne
  base v1/v2 provenant d’un utilisateur n’a été fournie.
- Les résultats synthétiques ne mesurent ni qualité métier, ni autonomie, ni
  comportement thermique prolongé. Le catalogue complet garde sa qualification
  propre. Un téléphone ARM avec pages 16 Ko n’est pas disponible dans cette campagne.
- La copie de signature hors machine, la CI bloquée par la facturation et la
  revue des notices transitives natives restent ouvertes. Les 110 artefacts
  runtime résolus ont une licence déclarée ; cela n’est pas une validation juridique.

Les sauvegardes privées, identifiants de connexion, jetons et journaux bruts
restent hors Git. Les reçus publics ne doivent conserver que les résultats et
empreintes nécessaires à la reproduction.

Gitleaks a contrôlé les fichiers suivis et nouveaux ainsi que les 26 commits
existants avant cette livraison. Ses 34 alertes dans chaque scan ont été relues :
18 empreintes de clés publiques et 16 SHA-256 de fichiers source dans les reçus.
Aucun secret n’a été identifié. Les alertes brutes restent expurgées et locales ;
la synthèse figure dans le reçu public. Les clés privées et secrets de récupération
restent hors du dépôt.
