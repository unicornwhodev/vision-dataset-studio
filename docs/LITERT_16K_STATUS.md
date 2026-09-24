# LiteRT : runtime sourcé et alignement 16 Ko

Le projet utilise la variante locale
`com.unicornwhodev.thirdparty:litert-interpreter:2.2.0-vds16k2`.
Le code Java **Interpreter, Delegate et JNI** provient du même
[commit public LiteRT](https://github.com/google-ai-edge/LiteRT/tree/145c7523ff08d5e57ab5c582141775eea47da9c7),
référencé par le tag `v2.2.0`. Cette variante construit l’API classique présente
dans les sources, que l’AAR 2.x essayé auparavant ne fournissait pas complètement.

Les résultats, les APK exactes et les limites matérielles figurent dans
[la qualification native](NATIVE_FIX_2026_09.md).
Le [rapport précédent](NATIVE_FOLLOWUP_2026_09.md) conserve la preuve historique
du défaut de l’AAR 1.4.2 : ses sources exactes n’ont pas été identifiées.
La nouvelle variante ne prétend pas reconstruire cet ancien binaire.

## Construction et garde-fous

[Le manifeste](../config/litert-source.json) fixe les commits, archives et SHA-256
de LiteRT, CPUinfo, Bazel 7.7.0, NDK r26d, Temurin 17 et checker-qual.
Les quatre ABI sont construites : ARM64, x86_64, ARMv7 et x86.
Les deux ABI 64 bits doivent satisfaire **LOAD et fin GNU_RELRO alignés à 16 Ko**.
Les options du linker sont `max-page-size=16384` et `common-page-size=16384`.
Aucun en-tête ELF n’est réécrit après compilation.

La [recette](../tools/build_litert_runtime.py) conserve un reçu par tentative
et ne remplace le dépôt Maven local qu’après réussite. Java utilise le bytecode
Java 8 ; la bibliothèque déclare API 28, cohérente avec le NDK et l’application.
Les outils téléchargés restent dans le répertoire de travail du builder.

Sous Linux x86_64, préparer `build-essential`, `python3-dev`, `python3-numpy`,
`unzip`, `patch`, `git`, `curl` et `ca-certificates`. Utiliser une licence SDK
**déjà acceptée**, puis lancer :

```bash
python3 tools/build_litert_runtime.py --work-dir /tmp/vds-litert --android-sdk-licenses "$ANDROID_HOME/licenses" --jobs 4
```

Exemple pour le checkout Windows, dans la distribution WSL de build existante :

```powershell
wsl -d VDS-Flex-Build -- python3 /mnt/d/Dev/project/vision-dataset-studio/tools/build_litert_runtime.py --work-dir /opt/vds-litert --android-sdk-licenses /mnt/d/Programs/Android/SDK/licenses --jobs 6
python -X utf8 tools/qa/check_litert_runtime.py
```

Le dépôt local est sous `dist/native-litert/maven/`, exclu de Git. Gradle utilise
une résolution exclusive de cette variante. Les builds Debug, Release et Release
instrumentée vérifient son reçu ; l’APK conserve les octets JNI contrôlés.
Vérifier ensuite toutes les bibliothèques et leur placement ZIP :

```powershell
python tools/qa/check_apk_page_sizes.py --apk '<apk>' --output '<audit.json>'
```

## Correctif CPUinfo

La première reconstruction alignée a révélé un SIGSEGV dans
`cpuinfo_arm_linux_init`, en exécutant ARM64 via `libndk_translation` sur Android
16/16 Ko. Le comptage des caches L2 ignorait une valeur sysfs que la passe suivante
utilisait pour remplir les caches. Avec une microarchitecture inconnue, la table
L2 pouvait rester non allouée puis être déréférencée.

Le [patch conservé dans le dépôt](../third_party/patches/cpuinfo-l2-count.patch)
applique la même taille sysfs au comptage et à la construction. Il vérifie aussi
la capacité avant les écritures si la topologie change entre les deux passes.
XNNPACK et les opérateurs sont conservés. Le builder vérifie les empreintes du
source avant/après patch et utilise un remplacement explicite du dépôt Bazel
CPUinfo. La licence BSD de CPUinfo et la notice de modification accompagnent l’AAR.

## Limites de la preuve

La compilation, l’audit statique et l’émulateur ne remplacent pas un appareil
**ARM en pages de 16 Ko**. Le Honor disponible utilise 4 Ko.
La recette doit couvrir inférence, apprentissage, Save/Restore, reprise de la copie
entraînée, original conservé et mise à jour signée sans perte de données.
La qualification du catalogue de modèles et la revue des notices natives
transitives restent des travaux distincts.
