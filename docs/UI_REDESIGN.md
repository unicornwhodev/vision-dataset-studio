# Interface — atelier de données

Direction choisie par le propriétaire : studio sombre, accents cyan/violet,
interface animée et textes courts. Implémentation native Jetpack Compose.

## Composition

L’atelier commence par le projet actif et le lot. Les aperçus proviennent des
images réellement acquises. Un lot vide indique comment connecter une source,
sans compteur artificiel ni illustration de démonstration.

Le fond graphite, les filets de séparation et les surfaces à faible contraste
structurent l’espace. Le cyan identifie l’action et la sélection ; le violet
identifie l’assistance et le stockage. Les titres utilisent une échelle de
14/16/20/24 sp ; les descriptions secondaires restent courtes. Les aides longues
s’ouvrent à la demande. Les confirmations de publication et suppression restent
complètes.

Navigation à cinq destinations, onglets soulignés, cibles tactiles de 48 dp
minimum. Sur grand écran, la navigation passe en rail latéral. Les vues défilent
pour rester utilisables avec une police agrandie. Le mode Clair/Système déjà
choisi est conservé ; le sombre est la valeur par défaut des nouveaux réglages.

## Écrans

| Écran | Organisation |
|---|---|
| Atelier | Projet actif, lot et aperçus réels, progression, source, assistance |
| Lot | Deux colonnes à 320 dp, reprise dans l’en-tête, barre d’actions uniquement sur sélection |
| Modèles | Explorer/Installés/Importer, fiches compactes, licence et compatibilité |
| Export | Formats en liste, archive locale, publication HF dépliable |
| Qualité | État vide explicite ; progression réelle, stockage et journal |
| Configuration | Source/Outils/Sortie ; accès direct au sélecteur de dossier Android |
| Projets | Projets/Sources/Transferts/Modèles ; commandes existantes conservées |
| Réglages | Apparence en premier, puis gestes et outils |
| Éditeur | Image sur toute la zone centrale, icônes de 20 dp, propriétés fermées par défaut |

Dans l’éditeur, les outils occupent une seule barre de 48 dp : leur pictogramme
mesure 20 dp, et le fond de sélection 28 dp. La cible tactile reste de 48 dp.
L’en-tête fait 48 dp ; aucune aide ni carte de propriétés ne recouvre l’image.
Les propriétés s’ouvrent à la demande dans un panneau modal sur téléphone et
une colonne latérale sur grand écran. Leur fermeture restitue tout le canevas.
Une sélection de région n’ouvre pas automatiquement le panneau. Les champs
conservent leur état par image et par onglet. Zoom +/−/ajustement sont accessibles
dans le menu, et directement dans la barre sur grand écran.

Les transitions d’entrée durent 240 ms. Les sélections, progressions et panneaux
s’animent sans boucle décorative. Une seule arborescence de contenu reste montée
pendant un changement de route pour conserver les brouillons. Les réglages
d’animation Android s’appliquent aux animations Compose.

## Limites et preuves

La refonte n’ajoute pas de données de démonstration au produit. Les images
synthétiques de recette sont séparées et identifiées « QA UI ». Aucun poids de
modèle n’est téléchargé implicitement.

Les conditions métier d’annotation, publication, copie vérifiée et purge sont
conservées. L’accès direct au dossier utilise le même import SAF que les
contrôles avancés, avec verrouillage lorsque le projet possède déjà des lots.

La compilation s’effectue avec `bash tools/build_android.sh` sur le pod. Chaque
tentative conserve ses APK, signatures, empreintes et résultat global. Le test
Compose vérifie le catalogue sans téléchargement de poids ainsi que la
navigation Importer/Export/Qualité/Atelier.

Révision exécutée : `20260919T200729Z-bec11e4607bc` ; APK Debug installée
par mise à jour, sans effacer les données. Les deux tests instrumentés Compose
passent sur API 28 (74,293 s JUnit). La recette manuelle utilise trois images
synthétiques importées avec le sélecteur SAF dans le projet distinct `Recette-UI`.

| Contrôle exécuté | Résultat |
|---|---|
| Grille à 320 dp | Deux colonnes visibles |
| Dessiner une boîte | Une région créée et enregistrée |
| Annuler / rétablir | Zéro puis une boîte, vérifiés dans la hiérarchie UI |
| Ouvrir / fermer les propriétés | Panneau modal ; retour au même canevas |
| Reprendre après redémarrage de l’activité | La boîte enregistrée est conservée |
| Zoom avant / ajustement | 125 % puis 100 %, annotation conservée |
| Police à 130 % | Commandes accessibles ; canevas 480 × 509 px |

À écran identique (480 × 800 px, densité 240), le canevas est passé de
480 × 164 à **480 × 515 px**. Pour la même source 640 × 480, l’image affichée
passe de 219 × 164 à **480 × 360 px**, sans déformation. Les pictogrammes
mesurent 20 dp et leur cible tactile 48 dp.

Captures de la recette à 320 dp (`20260919T200729Z-bec11e4607bc`) : [éditeur](ui/editor.png),
[annotation créée](ui/editor-annotated.png), [propriétés](ui/editor-properties.png),
[lot](ui/batch.png). Les fichiers `home-empty.png`, `model-import.png`,
`quality-empty.png`, `settings.png` et `setup.png` sont des captures exploratoires
antérieures ; ils ne constituent pas une preuve de cette APK.

Preuves brutes, hiérarchies et métadonnées :
`/workspace/qa/vision-dataset-studio/redesign/canvas-*` ; reçu et tests :
`/workspace/qa/vision-dataset-studio/canvas-first/`. Les captures incluent
l’empreinte de l’APK installée, la résolution, la densité et la taille de police.
Aucun test sur téléphone physique ni benchmark de fluidité n’est revendiqué.
Les échecs HTTP/SAF décrits dans [POD_VALIDATION.md](POD_VALIDATION.md) restent
séparés de la recette visuelle et ne sont pas réputés corrigés par cette refonte.


## Poste de travail grand écran

L’émulateur utilise maintenant une résolution physique de 960 × 720 px à 160 dpi,
avec une fenêtre correspondante sur le bureau distant. Le canevas mesure
960 × 531 px ; la source 640 × 480 est affichée en **708 × 531 px**.
Le panneau latéral de 300 dp s’ouvre et se ferme sans perdre l’annotation.
Une boîte est toujours présente après redémarrage complet de l’émulateur.

La dernière tentative `20260919T203710Z-e9cd5c4ddbbe` corrige aussi les aperçus de l’accueil :
leur hauteur est bornée dans leur conteneur, sans recouvrir le titre du lot
ni sa progression. Les deux tests Compose passent à 960 dp (75.807 s JUnit).
Le dessin/annuler/rétablir à 320 dp a été exécuté sur la tentative précédente ;
le code de l’éditeur est identique. La conservation de la boîte a été recontrôlée
après la dernière mise à jour de l’APK.

Captures finales : [accueil](ui/home-workstation.png),
[éditeur grand écran](ui/editor-workstation.png).
Le [panneau latéral](ui/editor-workstation-properties.png) est capturé avant
la dernière correction de l’accueil. Les empreintes et réglages par image sont
consignés dans [ui/evidence.json](ui/evidence.json).

Un crash natif de `system_server` a interrompu le premier démarrage grand écran.
La session a été rétablie ; le détail est conservé dans le guide du poste.
Cette émulation logicielle reste une limite de la recette.
