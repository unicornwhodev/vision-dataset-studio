# Tester la vraie Release

La variante `release` conserve R8, la réduction des ressources et un manifeste
non débogable. L'APK de tests doit être compilée avec **le mapping du même
build**, puis signée avec le même certificat que l'application. Une APK de tests
Debug n'est pas interchangeable avec elle.

## Construire, signer, exécuter

Préparer les [outils Android et les runtimes natifs](ANDROID_QUALIFICATION.md),
puis utiliser JDK 21 et le SDK 36. Aucun secret n'est passé dans les arguments.

```powershell
python -X utf8 tools/qa/build_release_test_apks.py --abi arm64-v8a
# Remplacer les chemins ci-dessous par le reçu affiché et le coffre hors Git.
./tools/sign_with_local_keystore.ps1 -Kind release-tests `
  -ArtifactDirectory '<recu-build>' -KeyDirectory '<coffre-prive>'
```

Le build produit une tentative unique sous `dist/release-tests/runs/`, les deux
APK, leurs empreintes, les mappings R8 et le manifeste des sources. La signature
crée un sous-dossier distinct ; aucun reçu antérieur n'est réécrit.

Sur un appareil de recette autorisé contenant les fixtures synthétiques préparées
par la [qualification Android](ANDROID_QUALIFICATION.md), sélectionner les deux
fichiers signés et leurs SHA-256 du reçu :

```powershell
$env:VDS_ALLOW_TEST_INSTALL='1'
python -X utf8 tools/qa/run_release_business_qualification.py `
  --serial '<adb-serial>' --apk '<app.apk>' --sha256 '<sha256-app>' `
  --test-apk '<tests.apk>' --test-sha256 '<sha256-tests>' `
  --expected-page-size 4096 --output 'test-results/release-core-nouvelle-tentative'
```

Pour l'émulateur x86_64 16 Ko, construire avec `--abi x86_64` et vérifier
`--expected-page-size 16384`. La signature doit correspondre à son installation
existante. Ne pas désinstaller pour contourner un conflit de certificat.

Le lanceur exige **40 succès, aucun échec ni test ignoré**. Il conserve les sorties
partielles et un reçu d'échec si le processus plante. Le périmètre est la suite
métier commune : les tests opt-in de publication HF, modèles convertis, pannes
externes et conservation après migration gardent leurs campagnes distinctes.
Ce compte n'est donc pas une qualification de tous les modèles ni de toutes
les interruptions possibles.

Sur Honor, `--keep-app-visible --prevent-test-process-freezing` documentent les
conditions nécessaires aux essais sans UI. Elles n'établissent aucune preuve
d'entraînement en arrière-plan. Le pilote indépendant `release-qa` utilise ses
propres options, `--qa-foreground-service --prevent-test-process-freezing`.

## Environnement ART

Les trois lanceurs (Debug, Release métier et UI) enregistrent le fingerprint et
le tampon `logcat -b crash` avant/après. Un crash dont la trame fautive `#00` est
dans `libart.so` bloque la qualification, même si le processus est un service
système ou si les assertions applicatives passent. Ne pas effacer les journaux
pour contourner ce contrôle : conserver l'incident et utiliser un environnement
de recette sain. Le garde ne détecte pas tous les défauts natifs possibles.

La campagne du 23–24 septembre utilise un nouvel AVD API 36/16 Ko, image officielle
révision 7, sans chargement de snapshot. L'ancien AVD API 35 et ses incidents sont
conservés. [Résultats et limites](NATIVE_FIX_2026_09.md). L'ARM exécuté par le pont
`libndk_translation` reste une preuve sur émulateur, distincte du Honor physique.

Le pilote UI attend la stabilité de la position et le focus réel du champ avant
la saisie. Cette attente conserve les scénarios et évite une course de défilement.

## Pourquoi conserver des API précises pour R8

L'APK instrumentée et l'APK de tests sont deux programmes compilés séparément.
Les références des tests ne rendent pas automatiquement les méthodes vivantes
dans l'application. Le premier lanceur Release échouait avant JUnit parce que
`androidx.tracing.Trace` avait été éliminé ; d'autres signatures Kotlin étaient
réécrites alors que les sous-classes de tests devaient encore les surcharger.

`app/instrumentation-api.pro` contient 584 déclarations explicites issues des
appels de tests, sans joker de classe ou méthode. Les noms et signatures de cette
frontière restent stables ; `allowaccessmodification` permet à R8 de rendre une
classe accessible si une sous-classe est déplacée dans un autre package.
`BaseContinuationImpl` possède en plus une règle pour les méthodes virtuelles
surchargées implicitement par les lambdas de tests.

**Ces règles s'appliquent aussi à l'APK distribuable.** Il n'existe pas de variante
de qualification avec R8 désactivé. Les règles sont des entrées explicites de
`proguardFiles` : une modification doit relancer la minification. Une inclusion
textuelle seule avait laissé R8 « up-to-date » dans une tentative intermédiaire ;
cette tentative n'a pas été qualifiée.

Après une évolution des tests ou des dépendances, produire un nouveau candidat :

```powershell
python -X utf8 tools/qa/trace_instrumentation_api.py `
  --output 'dist/release-api-review-nouvelle-tentative'
```

Le script utilise les entrées effectives des tâches R8 de l'AGP épinglé et
`TraceReferences` du SDK. Il n'installe aucune règle automatiquement. Examiner le
diff, les diagnostics et les appels implicites, puis reconstruire et tester les
deux ABI. La génération initiale signalait des références optionnelles ou non
publiques (`java.beans`, modèle du compilateur Java, API cachées d'Instrumentation,
`SuspendToFutureAdapter`). Le résultat brut incomplet a été conservé : seul le
passage effectif des scénarios permet de conclure sur leur couverture.

Les tests Compose hébergent leurs composants dans la vraie `MainActivity`.
Aucune activité factice n'est ajoutée au manifeste Release. Les profils créés
par les essais ont un nom unique ; une ancienne campagne interrompue ne doit
pas autoriser la suppression de profils préexistants.

## Défaut détecté par la campagne

`InferenceDiagnostics` n'avait pas d'adaptateur Moshi généré. Après minification,
la création d'un reçu d'inférence échouait lors de sa sérialisation Kotlin.
`@JsonClass(generateAdapter = true)` ajoute désormais l'adaptateur KSP, testé
par les parcours réels de propositions et reçus de la suite Release.
