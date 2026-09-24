# Release, arrière-plan et 16 Ko — 23 septembre 2026

> Campagne historique. La [reprise suivante](RELEASE_CLOSURE_2026_09.md) valide la suite métier Release et le pilote Honor sur de nouveaux APK. Le présent reçu conserve les résultats et limites de ses propres artefacts, notamment l'essai d'arrière-plan.

Cette campagne prolonge la [recette P1](P1_QUALIFICATION_2026_09.md).
Elle porte sur des candidats locaux postérieurs à rc5 ; les fichiers publiés
n'ont pas été remplacés. Aucun workflow GitHub Actions n'a été lancé.

## Résultats et portée

| Vérification | Résultat | Conditions et limites |
|---|---|---|
| Build Windows | 70 JVM, 99 fichiers KSP ; lint 0 erreur / 95 avertissements ; deux APK | Build `20260923T161611Z-95c3fdee3269` ; avertissements conservés |
| Outils Python | 79 tests réussis | Inclut le refus d'une preuve d'arrière-plan avec visibilité inconnue |
| Suite Debug Honor | 40/40, aucun ignoré, 365,01 s | ARM64, API 36, pages 4 Ko ; écran de recette visible et exemption de gel des seuls processus de test ; écran rétabli deux fois pendant cette tentative |
| Suite Debug 16 Ko | 40/40, aucun ignoré, 32,419 s | Émulateur x86_64, API 35 ; inclut le test natif Graphics Path corrigé |
| Release UI 16 Ko | 4/4, aucun ignoré, 25,837 s | APK minifiée, non débogable ; pilote indépendant, certificat Debug réservé à cet émulateur |
| Release UI Honor par ADB hôte | 4/4, 67,094 s | APK ARM64 minifiée, non débogable ; arbres UI relus à chaque action, sans instrumentation ni exemption de gel |
| Release UI Honor instrumentée | **Incomplète : 2 tests passent, puis délai de 300 s dépassé** | Attentes d'accessibilité du pilote ; résultat conservé séparément de la recette ADB hôte |
| Entraînement Release Honor | 12 minutes observées en arrière-plan ; arrêt à 6 202 puis reprise jusqu'à 6 270/6 270 | Vraie Release ARM64 signée ; calcul TensorFlow dans Android, corpus synthétique |
| Conservation après entraînement | Checkpoint relu, original intact, copie entraînée distincte, 256 images et annotations conservées | Sonde indépendante en lecture seule, exécutée après la mesure |
| Audit natif strict | Graphics Path, Flex et DataStore passent ; **LiteRT reste signalé** | Aucun téléphone ARM 16 Ko testé ; aucune certification globale |

Le [reçu public](RELEASE_HARDENING_RECEIPT.json) lie les résultats aux empreintes
des APK et des preuves locales. Les journaux bruts restent dans
`test-results/release-hardening-20260923/`, hors Git. Les échecs précédents sont
conservés, sans réécriture de leurs reçus.

## Ce que couvre la recette Release

Le lanceur AndroidJUnitRunner embarqué avec les tests liés à l'application
échouait avant le premier test : `NoClassDefFoundError: androidx.tracing.Trace`.
R8 avait retiré une classe supposée fournie par l'APK cible. Les résultats de
la suite Debug ne sont donc pas présentés comme une suite métier Release complète.

Le module `release-qa` installe son propre lanceur et ses dépendances dans un
processus séparé. Il contrôle la vraie APK optimisée : accueil, navigation
Modèles/Import/Export/Qualité, création d'un projet persistée après redémarrage
du processus et préférence d'affichage persistée puis restaurée. Il refuse
une cible débogable. Aucun keep global, stub ou suppression de test ne masque
le problème de lancement. Les premières tentatives du pilote ont échoué sur
le défilement et la création de projet ; elles restent documentées dans les logs.
La dernière tentative passe sur l'émulateur, mais reste incomplète sur Honor :
deux tests réussissent avant un blocage d'accessibilité et l'expiration du délai.

Le pilote utilise [UiAutomator 2.4.0](https://developer.android.com/jetpack/androidx/releases/test-uiautomator),
qui inclut des corrections du cache d'accessibilité. Il renouvelle uniquement
ce cache aux transitions sur API 34+. Sur Honor, une pile de threads a aussi
localisé une attente dans `UiAutomation.injectInputEvent` pendant un swipe.
Le défilement passe par `input swipe`, dans les limites du conteneur observé.
Le bouton de création est activé par son action d'accessibilité après contrôle
de son état actif ; les assertions de création et de persistance restent entières.
Cela ne constitue pas un audit TalkBack ou une recette exhaustive des gestes.

Sur Honor, `tools/qa/run_release_ui_host.py` exécute les mêmes quatre parcours
par ADB avec un nouvel arbre XML à chaque action. Les coordonnées viennent des
contrôles observés. Cette recette passe en 67,094 secondes et conserve ses
25 captures XML ; elle vérifie l'empreinte de l'APK installée et son caractère
non débogable. Elle ne lance ni instrumentation, ni service de recette, ni
exemption de gel. La préférence modifiée est rétablie et sa restauration est
vérifiée après redémarrage du processus. Le projet synthétique créé est conservé.

Les aides optionnelles du pilote Honor sont déclarées : service dans l'APK
`release-qa` séparée et exemption de gel des seuls processus de cette recette.
Le runner les termine en arrêtant ces processus à la fin, sans effacer de données.
Aucune de ces aides n'appartient à la Release ni à la mesure d'entraînement
en arrière-plan décrite ci-dessous.

Ces **quatre scénarios UI**, la sonde de conservation et l'entraînement réel
ne remplacent pas les **40 tests métier sous minification**. Ce dernier
périmètre reste à porter vers un harnais compatible Release.

```powershell
.\gradlew.bat :release-qa:assembleRelease
$env:VDS_ALLOW_TEST_INSTALL='1'
python -X utf8 tools/qa/run_release_ui_qualification.py --help
python -X utf8 tools/qa/run_release_ui_host.py --help
```

Le script exige le dispositif choisi, les chemins et SHA-256 des deux APK,
ainsi que la taille de page attendue. Il installe par mise à jour et ne résout
jamais un conflit de signature par désinstallation. La variante hôte exige
l'empreinte de la Release déjà installée et n'installe aucune APK.

## Entraînement prolongé réellement mesuré

Le worker utilise un service au premier plan Android, avec notification de
progression et action d'arrêt. Les checkpoints restent durables et l'activation
des poids demeure explicite. La notification ne contient ni nom de corpus ni
contenu d'annotation. Sur Android 13+, le démarrage manuel demande l'autorisation
de notification ; son refus n'autorise aucune suppression et n'empêche pas
l'arrêt depuis l'application.

La fixture contient 256 images synthétiques et 30 cycles : 209 images pour
l'apprentissage, 47 pour le contrôle, soit 6 270 étapes. Chaque signature
d'entraînement effectue 5 000 mises à jour internes TensorFlow ; aucun sommeil
artificiel ni optimiseur hôte n'allonge la mesure.

Après Home, 25 observations sur **720,266 secondes** constatent le même processus,
le service actif et l'activité applicative invisible. Aucune instrumentation,
activité de recette ou exemption de gel n'est utilisée pendant cet intervalle.
La première observation avait confondu le lanceur visible avec l'application ;
elle a échoué et reste conservée. Le parseur filtre désormais les seules tâches
de l'application et refuse une visibilité indéterminée.

Au retour, l'UI indique 6 080 étapes. Un arrêt explicite conserve le checkpoint
à 6 202 ; la reprise atteint 6 270, phase `completed`. La perte sur le contrôle
synthétique passe de **0,6893638011 à 0,0001397660**, les poids internes changent,
et la sonde vérifie les empreintes des images et du checkpoint ainsi que le
contenu des 256 annotations humaines de la fixture. L'original est intact.
Ce résultat ne mesure pas la qualité des conversions du catalogue.
La sonde a été rejouée après tous les essais UI ; elle confirme ces protections
ainsi que les deux cas antérieurs de conservation après mise à jour signée.

Les deux captures mémoire donnent **95 324 puis 99 141 Kio de PSS**, et
251 512 puis 255 960 Kio de RSS. Ce sont deux points de mesure sur la fixture,
pas un pic absolu, une preuve d'absence de fuite ni un benchmark du catalogue.

Cette preuve couvre douze minutes sur ce Honor ; elle ne couvre pas une nuit,
un redémarrage du téléphone, toutes les politiques d'économie d'énergie ou les
quotas de tâches Android. Les [limites WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
restent applicables, notamment sur Android 16.

## Correction Graphics Path et limite LiteRT

La [reconstruction native](GRAPHICS_PATH_16K.md) épingle les sources AndroidX
et garde les classes Java officielles. LOAD et GNU_RELRO passent pour ARM64
et x86_64 ; les octets intégrés aux APK sont comparés au reçu du build natif.

Le premier test fonctionnel comptait puis parcourait le même itérateur. Le
Kotlin officiel laisse alors huit segments coniques dans son convertisseur :
42 au lieu de 34. Le test utilise maintenant un curseur indépendant pour
compter et vérifie aussi la continuité et l'équation de l'ellipse. Ce changement
ne prétend pas réparer ce défaut amont ; le code applicatif n'appelle pas
`calculateSize()` directement.

`libtensorflowlite_jni.so` de LiteRT 1.4.2 est le seul signalement strict restant
dans chaque APK 64 bits auditée : la fin de GNU_RELRO n'est pas alignée à 16 Ko.
Le crash Flex est corrigé et les essais x86_64 16 Ko passent, mais cela ne suffit
pas à effacer le signalement. Le rebuild de LiteRT nécessite une provenance
source vérifiable et une nouvelle recette Interpreter/Flex ; aucun en-tête ELF
n'a été retouché pour rendre l'audit vert.

## Ce qui reste ouvert

- Couverture métier complète de la vraie Release minifiée.
- Fiabilité du pilote UI instrumenté sur Honor ; recette ADB hôte réussie séparément.
- Signalement LiteRT strict et exécution sur téléphone ARM 16 Ko.
- Durées supérieures à douze minutes, extinction/redémarrage et politiques OEM.
- Qualité sur corpus indépendant, conversions restantes et mesures ARM répétées.
- Copie de clé hors machine, notices natives transitives et future publication.
- CI distante sans nouvelle exécution ; aucun workflow GitHub Actions n’est lancé, à la demande du propriétaire.

## Contrôle des sources avant livraison

Le scan Gitleaks de l'arbre de travail a signalé 34 valeurs. La revue de chaque
ligne identifie 18 empreintes de clés publiques dans les reçus `apksigner` et
16 SHA-256 de fichiers sources dans les manifestes historiques. Aucun secret
n'est identifié parmi ces signalements. Le rapport brut en échec et la revue
sont conservés séparément ; aucune règle générale n'a été désactivée pour
obtenir un résultat vert. Ce contrôle ne remplace pas un audit de tous les
anciens objets Git ou des emplacements privés ignorés.
