# Poste Android sur Runpod

Le projet est dans `/workspace/projects/vision-dataset-studio`. Les sources, SDK,
JDK, Gradle, AVD et preuves sont dans le volume persistant de 50 Go.

## Connexion depuis le Chromebook

```bash
code --remote ssh-remote+runpod-workstation /workspace/projects/vision-dataset-studio
ssh runpod-workstation
```

Dans le terminal distant :

```bash
source /workspace/toolchains/android-env.sh
cd /workspace/projects/vision-dataset-studio
bash tools/workstation/run.sh build
```

Les tâches VS Code du projet permettent aussi de lancer build, tests portables,
émulateur et tests instrumentés. Le build produit un reçu par tentative sous
`dist/android/runs/` ; `dist/android/latest.json` désigne la dernière tentative.

## Émulateur et contrôle visuel

Le pod ne dispose pas de `/dev/kvm`. L'émulateur utilise donc la virtualisation
logicielle, avec rendu SwiftShader. Des blocages de l’interface système ont été
observés : le démarrage ne constitue pas une validation de stabilité. Pour une
recette fiable, prévoir aussi un appareil dédié ou les émulateurs accélérés de
la CI. Cet émulateur ne fournit pas de benchmark représentatif d’un appareil ARM.

Sur le pod :

```bash
bash tools/workstation/run.sh emulator
source /workspace/toolchains/android-env.sh
adb devices
adb -s emulator-5554 shell getprop sys.boot_completed
```

Sur le Chromebook, laisser ce tunnel ouvert :

```bash
ssh -N -L 127.0.0.1:6080:127.0.0.1:6080 runpod-workstation
```

Puis ouvrir http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale
Le bureau de l'émulateur se contrôle à la souris et au clavier. VNC et noVNC
écoutent uniquement sur le loopback du pod ; le transport passe par SSH.

Pour les tests automatisés sur cet émulateur dédié :

```bash
bash tools/workstation/run.sh device-tests
adb -s emulator-5554 exec-out uiautomator dump /dev/tty > ui.xml
adb -s emulator-5554 exec-out screencap -p > screen.png
adb -s emulator-5554 logcat -d -b crash > crash.txt
```

Le script d'installation vérifie les empreintes des APK et ne désinstalle pas
une application pour contourner un conflit de signature.

## Persistance et reprise

| Chemin | Usage |
|---|---|
| `/workspace/imports/` | Archive initiale |
| `/workspace/projects/vision-dataset-studio/` | Sources et résultats de build |
| `/workspace/toolchains/` | JDK, Kotlin, SDK, AVD, configuration Android |
| `/workspace/cache/gradle/` | Distribution Gradle et dépendances |
| `/workspace/qa/vision-dataset-studio/` | Logs de préparation et validation |

Les bibliothèques système installées par apt restent dans le conteneur temporaire.
Après remplacement/arrêt du pod, les réinstaller si nécessaire :

```bash
bash tools/workstation/install-android-native-tools.sh
source /workspace/toolchains/android-env.sh
bash tools/workstation/start-android-emulator.sh
```

Les clés de signature, fichiers `android-user`, caches et AVD ne doivent pas être
commités. La clé Debug sert aux tests ; une clé de distribution devra être gérée
séparément avant une release stable.

Pour recréer les outils persistants, `setup-android-toolchain.py` télécharge et
vérifie les archives officielles. L'installation des composants SDK nécessite
l'acceptation de leurs licences par l'opérateur :

```bash
python3 tools/workstation/setup-android-toolchain.py
cp tools/workstation/android-env.sh /workspace/toolchains/android-env.sh
source /workspace/toolchains/android-env.sh
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' \
  'emulator' 'system-images;android-28;default;x86_64'
```

Références officielles :
- https://developer.android.com/studio
- https://developer.android.com/studio/run/emulator-commandline
- https://developer.android.com/studio/run/emulator-acceleration
