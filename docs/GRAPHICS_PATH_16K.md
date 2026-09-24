# AndroidX Graphics Path : reconstruction 16 Ko

Le binaire officiel `androidx.graphics:graphics-path:1.0.1` aligne les segments
LOAD à 16 Ko, mais pas la fin de GNU_RELRO. La version officielle 1.1.0 examinée
pendant cette campagne conserve ce signalement. Le contrôle strict reste inchangé.

La variante locale `1.0.1-vds16k1` reconstruit les JNI ARM64 et x86_64 avec les
deux options `-Wl,-z,max-page-size=16384` et `-Wl,-z,common-page-size=16384`.
L'API Java, les ressources et les binaires 32 bits viennent de l'AAR officiel
1.0.1. Aucun en-tête ELF n'est modifié après compilation.

Le [manifeste des sources](../config/graphics-path-source.json) épingle les
fichiers C++ AndroidX au commit `794e3806700833665f48f56f7dd3581642a6057f`,
ainsi que les empreintes de l'AAR et du POM officiels. Ce commit est le dernier
changement du dossier natif avant la publication de 1.0.1 le 1er mai 2024. Les symboles JNI exportés sont
comparés aux binaires officiels, puis LOAD et RELRO sont vérifiés pour les deux
architectures. Cela ne remplace pas les essais Android de la bibliothèque.

Prérequis : Linux ou WSL, Python 3, et **NDK r25b déjà installé**. Sur le poste
de qualification :

```powershell
wsl -d VDS-Flex-Build -- python3 /mnt/d/Dev/project/vision-dataset-studio/tools/build_graphics_path.py --ndk /opt/vds-flex/android-ndk-r25b
python -X utf8 tools/build_android.py
```

Adapter les chemins sur un autre poste. Les sources téléchargées, commandes,
journaux, bibliothèques et reçus restent sous `dist/native-graphics/runs/`.
Seule une tentative réussie installe l'AAR dans le dépôt Maven local.
Le build Android vérifie le reçu, le script de reconstruction, les entrées
épinglées et les octets JNI effectivement intégrés à l'APK.

La dépendance s'applique également aux tests JVM et instrumentés. Les poids,
les données utilisateur et les clés de signature ne sont pas concernés.

Le premier test Android a révélé un défaut distinct dans le Kotlin officiel
de Graphics Path 1.0.1, également présent dans les sources 1.1.0 : sur API 34+,
`calculateSize()` utilise le convertisseur de l'itérateur et y laisse les
quadratiques de la dernière conique. Compter puis parcourir **le même** itérateur
ajoute ici huit segments (42 au lieu de 34). Les classes Kotlin de notre AAR
sont identiques à celles de l'AAR officiel ; le rebuild JNI ne corrige pas ce
défaut amont. Le test de qualification utilise deux curseurs indépendants,
conserve l'égalité du nombre de segments et vérifie aussi la continuité et
l'équation de l'ellipse obtenue. L'application n'appelle pas `calculateSize()`
directement. Les journaux de la première exécution en échec sont conservés.

Le test corrigé passe le 23 septembre 2026 dans les suites Debug **40/40**
sur Honor ARM64/API 36/pages 4 Ko et sur émulateur x86_64/API 35/pages 16 Ko.
Voir la [campagne Release et arrière-plan](RELEASE_HARDENING_2026_09.md).

Cette correction porte sur Graphics Path. La reconstruction de
`libtensorflowlite_jni.so` est décrite dans [la note LiteRT](LITERT_16K_STATUS.md) ; aucune certification
globale 16 Ko ni recette sur téléphone ARM 16 Ko n'en est déduite.
