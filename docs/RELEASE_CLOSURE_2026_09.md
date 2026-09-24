# Suite Release et pilote Honor — 23 septembre 2026

Complément ultérieur : [investigations ART/LiteRT](NATIVE_FOLLOWUP_2026_09.md),
avec huit nouveaux passages 40/40 sur les mêmes APK. Les deux points restent
ouverts ; le reçu et les échecs de la présente campagne sont conservés.

**La suite métier commune passe sur la vraie Release minifiée : 40/40 sur le
Honor ARM64 4 Ko et 40/40 sur l'émulateur x86_64 16 Ko, sans test ignoré.**
Les quatre scénarios UI instrumentés passent également sur le candidat final
du Honor. Les limites 16 Ko et un crash ART intermittent restent explicites.

[Reçu et empreintes](RELEASE_CLOSURE_RECEIPT.json) ·
[Reproduire la suite](RELEASE_TESTING.md) ·
[Campagne précédente et arrière-plan](RELEASE_HARDENING_2026_09.md)

## Résultats du candidat local

| Vérification | Résultat | Conditions |
|---|---|---|
| JVM Release | 70/70 | Robolectric et code réel ; aucune preuve d'appareil déduite |
| Outils Python | 79/79 | Garde-fous des reçus, exports et qualification |
| KSP / lint Release | 101 fichiers générés ; 0 erreur, 93 avertissements | 55 sources Kotlin et 46 règles, dont le nouvel adaptateur de diagnostics |
| Métier Release Honor | 40/40 en 35,316 s | ARM64, Android 16/API 36, pages 4 Ko ; vraie activité visible entre les classes sans UI, exemption temporaire de gel |
| Métier Release émulateur | 40/40 en 25,329 s, puis 40/40 en 26,082 s | x86_64/API 35, pages 16 Ko ; aucun maintien au premier plan ni exemption |
| UI instrumentée Honor | 4/4 en 15,065 s | Même candidat signé ; pilote indépendant avec service et exemption temporaires |
| Conservation après mise à jour | Deux cas antérieurs relus et valides | Annotation humaine, image, curseur et modèle original |
| Entraînement antérieur relu | 6 270/6 270, original et checkpoint intacts | Copie entraînée distincte ; 256 images et annotations humaines conservées |

Le candidat ARM64 signé pèse **101 085 494 octets**, soit **101,1 Mo**.
SHA-256 : `04b5788754cb6640db8b606b448ea0be4ad2f9c204641bd54d6ea090cebb2b03`.
Il utilise le certificat durable déjà épinglé et sauvegardé. Il a remplacé
l'installation de recette par une mise à jour, sans désinstallation ni effacement.
L'APK x86_64 de recette conserve la signature Debug Windows de son émulateur,
tout en restant une variante Release non débogable et minifiée.

Les sources compilées correspondent aux manifestes des deux builds finaux.
Les fichiers déjà publiés sous rc5 sont inchangés. Aucun commit, push, workflow
GitHub Actions, dépôt HF ou nouvelle publication n'est réalisé par cette reprise.

## Correctifs et périmètre

Le lanceur Release échouait parce que R8 supprimait ou transformait des API
appelées depuis l'APK de tests. La [frontière d'API conservée](RELEASE_TESTING.md)
est explicite et s'applique au candidat distribuable. Les tests Compose utilisent
la vraie `MainActivity`, sans activité factice dans le manifeste Release. Le test
de profil d'inférence utilise un identifiant propre à sa campagne et conserve
les profils préexistants.

La campagne a ensuite détecté un **défaut applicatif réel** : les diagnostics
d'inférence échouaient à la sérialisation Moshi après minification. Un adaptateur
KSP généré remplace cette réflexion fragile. Les parcours de propositions,
reçus et entraînement ont été rejoués après le correctif. L'augmentation de
l'APK ARM64 par rapport au candidat précédent est de 845 444 octets, environ 0,84 %.

Le pilote Honor lit maintenant la fenêtre active avec `UiAutomation`, au lieu
de parcourir toutes les fenêtres via UiAutomator. Il attend les transitions et
la persistance des préférences. Une tentative sans aide a encore dépassé cinq
minutes : **la fiabilité sans les aides consignées n'est pas démontrée**. Le
lanceur termine les processus du pilote en sortie, même après un délai dépassé.
La préférence de conseils, laissée modifiée par un essai interrompu, a été remise
à sa valeur initiale puis vérifiée après redémarrage du processus.

Les 40 tests sont le périmètre métier commun déjà utilisé en Debug : annotations,
projets, exports, sauvegarde, migration, moteurs natifs, UI, profils et entraînement.
Les campagnes opt-in HF, corpus convertis, interruptions externes et qualité
restent distinctes ; leur exclusion est listée dans chaque reçu. Aucun test n'a
été supprimé et aucune hypothèse ignorée n'est comptée comme un succès.

## Échec intermittent conservé

Une première exécution des derniers APK x86_64 a terminé 36 tests, puis le
processus a subi un **SIGSEGV dans `libart.so`, résolution de méthode**, sur un
thread WorkManager pendant le scénario d'entraînement. Les traces n'établissent
pas sa cause. Il n'est donc attribué ni à LiteRT, ni au pilote, ni à Android sans
preuve supplémentaire. Aucun conflit de noms de classes entre les deux DEX n'a
été trouvé.

Les deux reprises sur les **mêmes octets d'APK** passent 40/40. Le Honor passe
également 40/40. Cela valide les scénarios exécutés, mais ne clôture pas le risque
de stabilité révélé par ce crash isolé. Le journal, le contexte logcat et le reçu
d'échec restent référencés. Les autres échecs de mise au point sont également
conservés ; aucun reçu historique n'a été transformé en réussite.

## LiteRT et appareil ARM 16 Ko

L'audit strict est toujours **non vert** : une fin GNU_RELRO non alignée dans
LiteRT 1.4.2 par ABI. Les audits des APK finaux confirment les mêmes bibliothèques.
L'[analyse des segments](LITERT_16K_STATUS.md) ne trouve aucune donnée modifiable
hors RELRO recouverte par l'arrondi à 16 Ko. Le signalement est expliqué, pas
supprimé. Flex, DataStore et Graphics Path passent leurs contrôles stricts.

Le seul téléphone disponible est le Honor 4 Ko. La recette matérielle ARM 16 Ko
reste à faire. Une reconstruction LiteRT reproductible demande encore une
provenance source vérifiable et une nouvelle qualification Interpreter/Flex.

## Suite à donner

- Capturer et réduire le crash ART s'il réapparaît ; ne pas présenter ce candidat comme totalement stabilisé.
- Exécuter la recette sur un téléphone ARM 16 Ko et résoudre ou accepter formellement l'exception LiteRT.
- Conserver les limites antérieures : durées d'arrière-plan supérieures à douze minutes, redémarrage, politiques OEM, qualité des modèles, copie de clé hors machine et notices natives transitives.

Les douze minutes d'arrière-plan ont été mesurées sur le candidat précédent ;
elles ne sont pas réattribuées au nouvel APK. Cette reprise vérifie la conservation
du résultat et un nouvel arrêt/reprise dans la suite métier, pas un nouvel essai
prolongé. La CI distante reste non exécutée, conformément à la demande du propriétaire.

## Contrôle des sources

Gitleaks a analysé 614 fichiers de l'arbre de travail destiné à Git. Ses 34
signalements ont été relus : 18 empreintes publiques de signature et 16 SHA-256
de sources dans les manifestes historiques. Aucun secret n'a été identifié parmi
eux. Le rapport brut, la classification et les empreintes sont conservés. Cette
revue ne prétend pas analyser tous les anciens objets Git ni les fichiers privés
ignorés. La clé et les mots de passe restent hors dépôt.
