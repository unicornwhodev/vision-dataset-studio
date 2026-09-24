# Les parcours guidés et les masques

Choisis un parcours guidé quand tu veux laisser Cadryl enchaîner les étapes. L’app s’arrête dès qu’elle a besoin de ta relecture ou d’une confirmation.


**Français** · [English](en/WORKFLOWS.md)

Le bouton **Workflow** propose trois parcours : production assistée, production manuelle et finalisation du lot. **Continuer** exécute les étapes disponibles puis s’arrête à une décision humaine. Le journal persiste par projet et par lot ; une reprise conserve l’étape et son éventuelle erreur.

| Étape | Comportement |
|---|---|
| Import | Réutilise l’index et le contrôle des doublons du projet |
| Préannotation | Exécute le modèle sélectionné sur les nouveaux cas ; conserve les corrections humaines |
| Relecture | Attend la validation ou le rejet explicite de chaque cas |
| Contrôle | Vérifie les annotations et l’unicité des images acceptées |
| Export | Prépare l’archive ou clôture un lot entièrement rejeté |
| Vérification | Attend la preuve d’une copie relue |
| Apprentissage | Facultatif, sur le lot exporté ; attend sa fin avant le nettoyage |
| Nettoyage | Attend votre confirmation dans Export |

Un lot vide en fin de source et un lot déjà nettoyé terminent le workflow sans nouvel import. Le lot suivant reste une action explicite. Relancer un template ne contourne aucune de ces conditions.

L’agent local facultatif reçoit les consignes et les nombres de cas, puis propose l’un des trois templates. Vous choisissez de l’utiliser. Il ne valide, ne publie et ne supprime rien. Son serveur doit être fourni séparément sur l’appareil ; seuls les endpoints HTTP `127.0.0.1` ou `localhost`, sans redirection, sont acceptés. L’application n’embarque pas de serveur VLM autonome. Le prompt système `dataset-agent/1` reste consultable dans le panneau de l’agent.

## Masques et SAM

Activez la tâche Segmentation. Le pinceau ajoute au masque sélectionné, la gomme retire des pixels. **Actions du cas → Nouveau masque** crée une autre instance, y compris de la même classe. Les masques restent sélectionnables dans le panneau des régions ; annuler/rétablir, changement de classe et relecture humaine sont disponibles.

Avec un bundle SAM sélectionné, **Actions du cas → Pointer pour SAM** permet de placer un repère temporaire. **Segmenter ce point** lance l’inférence. Le repère ne devient pas une annotation exportée. Une boîte ou un point humain existant peut aussi guider SAM lorsqu’aucun repère temporaire n’est choisi.

Les masques proposés doivent être corrigés et relus. Une nouvelle inférence conserve les masques humains. Le JSONL canonique conserve le masque RLE et sa résolution ; COCO projette le masque à la résolution de l’image et marque chaque instance `iscrowd=0`. L’export calcule le RLE sans allouer une image complète supplémentaire. Limites : quatre millions de pixels pour un masque canonique, cent millions pour l’image projetée, complexité RLE bornée. YOLO detection reste une projection des boîtes.
