# Visuels et provenance / Visuals and provenance

Documentation actualisée le 21 septembre 2026. Les figures réutilisent les preuves enregistrées le 19–20 septembre ; aucun modèle n’a été exécuté pour produire ces images.

Documentation updated 21 September 2026. Figures reuse evidence recorded on 19–20 September; rendering them did not execute any models.

## Bannière / Banner

![Bannière](visuals/banner.png)

Illustration décorative créée avec l’outil intégré `image_gen`, puis copiée dans le dépôt. Le paysage et les repères d’annotation sont générés ; ce n’est ni une capture d’écran, ni un résultat d’inférence, ni une donnée d’entraînement. [Prompt exact](visuals/banner-prompt.txt). Fichier original de publication : `docs/visuals/banner.png`, 2172 × 724 pixels.

Decorative illustration created using the built-in `image_gen` tool and saved in the repository. The landscape and annotation guides are generated; this is not a screenshot, inference result or training data. [Exact prompt](visuals/banner-prompt.txt).

## Captures Android / Android screenshots

| Fichier / File | Provenance | Portée / Scope |
|---|---|---|
| [Atelier rc3](../test-results/litert-rc3/final-device/app-ready.png) | Build `20260920T215432Z-cdd13d7fbf98`, démarrage rendu après redémarrage à froid / rendered startup after cold boot | Capture actuelle rc3, projet de QA vide après recette / current rc3, empty QA project after tests |
| [Éditeur](ui-refined/refined-editor-persisted.png) | Build `20260919T213548Z-f221e25ccc2f`, [recette historique](UI_REFINEMENT.md) | Image synthétique, annotation conservée ; antérieur à rc3 / synthetic image and persisted annotation; predates rc3 |

Les captures sont intactes : aucun panneau, contrôle ou contenu n’a été inventé ou retouché. Elles viennent d’un émulateur x86_64, pas d’un téléphone ARM. Le build historique de l’éditeur avait des échecs globaux détaillés dans sa recette ; sa capture ne vaut pas validation de publication.

Screenshots are unmodified: no panels, controls or content were synthesized. They come from an x86_64 emulator, not an ARM phone. The historical editor build had failures recorded in its QA report; its screenshot does not imply release qualification.

![Atelier actuel](../test-results/litert-rc3/final-device/app-ready.png)

![Éditeur historique](ui-refined/refined-editor-persisted.png)

## Diagrammes / Diagrams

Graphviz produit les schémas FR et EN à partir des fichiers `.dot` ; les fichiers SVG sont vectoriels, les PNG permettent le partage. Ils décrivent l’architecture et les gardes du cycle, pas une nouvelle mesure d’exécution.

Graphviz renders FR/EN diagrams from `.dot` sources; SVG is vector-based and PNG is provided for sharing. They describe architecture and lifecycle guards, not newly measured execution.

```bash
for source in docs/visuals/*.dot; do
  dot -Tsvg "$source" -o "${source%.dot}.svg"
  dot -Tpng "$source" -o "${source%.dot}.png"
done
```

## Graphiques de qualification / Qualification charts

Les figures de `docs/visuals/benchmarks/` utilisent les reçus publics listés dans [android-evidence.json](visuals/android-evidence.json). Elles concernent les 25 conversions personnelles publiques : 5 réussies, 1 en échec, 19 en attente. Les identifiants des modèles d’une source privée ne sont pas inclus. Les temps sont des durées de tests complets, pas des latences d’inférence. Les pertes synthétiques et deltas de sorties ne prouvent aucune précision métier.

Figures in `docs/visuals/benchmarks/` use public receipts listed in [android-evidence.json](visuals/android-evidence.json). They cover 25 public personal conversions: 5 passed, 1 failed, 19 pending. Private-source model identifiers are excluded. Times are whole-test durations, not inference latency. Synthetic losses and output changes are not task-accuracy evidence.

```bash
python -m pip install matplotlib numpy
python tools/docs/plot_litert_evidence.py \
  docs/visuals/android-evidence.json docs/visuals/benchmarks
```

Générateurs : Matplotlib 3.6.3 / Graphviz 2.43.0 lors de cette production ; les valeurs JSON et les sources DOT font foi. Les images sont tracées directement à partir des données, sans génération d’image pour les mesures.

Renderers: Matplotlib / Graphviz; JSON values and DOT sources are authoritative. Measurement charts are deterministic data plots, not image-model generations.
