# UWD 4.2 — recette de qualification bloquante

Cette matrice définit les critères de sortie ; elle n’est pas un journal de succès.
Des contrôles ont maintenant été exécutés sur le pod : consulter
[POD_VALIDATION.md](POD_VALIDATION.md) et [IMPLEMENTATION_AUDIT.md](IMPLEMENTATION_AUDIT.md)
pour leurs résultats et limites. Utiliser une copie des données et un appareil dédié.
Aucun push HF n’est lancé automatiquement par cette recette.

## P0 : assembler, résoudre et installer

JDK 17+, Gradle 9.3.1, SDK/API et bibliothèques déclarés dans le projet. Exécuter `bash tools/build_android.sh`, ou le workflow manuel sur un dépôt que le propriétaire choisit. Corriger les erreurs réelles sans supprimer des tests, rétrograder silencieusement les formats ou ajouter un fallback destructif.

Récupérer APK Debug, SHA, logs assemble/test/lint et APK instrumentée. Vérifier la signature avec `apksigner verify --verbose`, installer sur appareil dédié. Une APK assemblée n’est pas encore qualifiée. Ne pas désinstaller une installation porteuse de données en cas de signature différente.

```bash
adb devices
export ANDROID_SERIAL='SERIAL_DU_DISPOSITIF_DE_TEST'
export VDS_ALLOW_TEST_INSTALL=1
bash tools/qa/run_device_qualification.sh
```

Cette commande installe les deux APK déjà construites, exécute les tests instrumentés fournis et capture le démarrage. Elle n’efface pas les données utilisateur et n’utilise pas HF. Les tests sur fournisseur SAF injecté ne remplacent pas les suivants.

## Matrice P1

| Scénario | Manipulation sur appareil/service | Acceptation |
|---|---|---|
| Migration réelle | Dans une application de test avec identité et signature constantes, ouvrir une copie de base de schéma 1/2 puis migrer vers 3 ; compléter avec une base issue d’une installation réelle | Annotations, projets, curseurs et reçus préservés ; validation Room réelle ; aucune preuve inventée |
| Cycle local | Fixtures 3 images, lot 2 puis 1, revoir/corriger/exporter/copier/relire/purger | Deux lots terminés, originaux inchangés et annotations conservées |
| Reprise image | Couper réseau à mi-transfert puis relancer ; ETag inchangé puis changé | Bytes finaux identiques ; jamais concaténation de versions ; erreurs visibles |
| URL expirée | Renouveler URL source après expiration | Reprise conservatrice ou nouveau téléchargement, jamais mélange de ressources |
| HF conflit | Deux clients déplacent la branche entre préparation et commit | Conflit affiché ; aucun nouveau parent automatique ; autres chemins inchangés |
| Réponse de commit perdue | Couper après acceptation du commit avant réception ; relancer | Réconciliation depuis reçu/chemins/hashes ; aucune purge prématurée |
| Arrêt pendant LFS | Tuer processus puis relancer | Intention identique, fichier conservé ; retransmission possible mais explicite |
| Copie SAF | DocumentsUI local puis fournisseur externe ; droit persistant puis révoqué | Relecture complète ; refus de clôture/purge sans preuve durable |
| Coupure stockage | Retirer volume ou provoquer quota/ENOSPC pendant copie/export | Pas d’ancien export remplacé par un tronqué ; pas de faux reçu ; archive privée conservée |
| Purge interrompue | Tuer à plusieurs étapes, dont images supprimées mais état final non écrit | PURGING reprenable avec reçu ; puis PURGED ; pas de suppression hors cache |
| Tous rejetés | Rejeter chaque cas, confirmer clôture puis purge | Aucun cas positif fictif nécessaire ; historique des motifs conservé |
| Gros corpus | Dossier/manifeste autorisé ≥10 000 images, plusieurs lots et redémarrages | Pas de fuite d’index ni dérive de curseur, budget respecté, originaux intactes |
| Mémoire | Capturer `dumpsys meminfo`, reprendre plusieurs lots, comparer avant/après purge | Mesures appareil documentées, pas de croissance non expliquée ; aucun seuil prétendument universel |
| Modèle | Télécharger catalogue ; inspecter ; essai réel puis benchmark 10/30 répétitions | Tenseurs/labels correspondants ; retouches protégées ; mesures et artefacts JSON sauvegardés |
| HTTP loopback | Serveur local séparé, réponses invalides/redirections/timeout | Refus non-loopback, aucun token HF et aucune validation automatique |
| Compose | Portrait étroit, paysage/clavier, tablette, taille texte 200 %, TalkBack | Contrôles utilisables, absence de crash, captures runtime et logs |

## Corpus et preuves

Commencer sur corpus synthétique/autorisé, puis échantillon représentatif. Tester les tailles 1, 99, 100, 101, 500 et 1 000. Le stress hôte 200 000 lignes ne vaut pas ce test. Noter nombre de photos effectivement décodées/annotées/exportées, taille disque, modèle, paramètres, appareil, OS, chauffe et nombres d’échecs.

Les tests HF réels doivent employer un dépôt de test explicitement autorisé et un token limité. Conserver manifeste, SHA du commit, comparaison indépendante des octets et traces des pannes. Ne jamais recopier un token dans les rapports.

`tools/qa/capture_device_metrics.sh` échantillonne mémoire et framestats du seul package. Le banc de mesure dans Modèles exporte les temps et mémoires après inférence. Compléter par une trace mémoire si l’objectif est de mesurer un vrai pic ; ne pas renommer le maximum échantillonné en pic absolu.

## Critère de sortie

P0 : APK construite, installée, tests dépendances/lint réussis, schémas générés. P1 : résultats réels pour la matrice ci-dessus avec échecs restants explicités. Tant que ces étapes manquent, l’étiquette reste « qualification », pas « outil validé ».

## Identité, licence et preuves

Le nouvel applicationId est `com.unicornwhodev.visiondatasetstudio`. Une ancienne application sous un autre identifiant n’est pas mise à jour ; aucune migration automatique de sa base privée ni des permissions SAF n’est promise. Ne pas fusionner ce contrôle avec les migrations de schéma Room.

Les schémas JSON sont générés sous `app/schemas/com.unicornwhodev.visiondatasetstudio.data.db.AppDatabase/`. Ne pas inventer leurs hashes en attendant KSP.

Les émulateurs API 28/35 du workflow complètent les tests hôte, mais ne prouvent ni le comportement d’un téléphone réel, ni un test d’inférence ARM, ni un aller-retour HF. Le script refuse les APK absents, dont le reçu ne correspond pas à la dernière tentative ou dont les octets ont changé. Le premier lancement de ce workflow reste à effectuer.

Avant une distribution publique : choix de licence explicite, inventaire des droits et notices de dépendances résolues. Le titulaire a choisi Apache-2.0 ; voir `LICENSING_STATUS.md`. Les notices transitives restent à vérifier.
