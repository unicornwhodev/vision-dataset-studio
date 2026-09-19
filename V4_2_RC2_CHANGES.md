# V4.2 RC2

- Catalogue UWD étendu aux 16 conversions complémentaires préparées pour le dépôt Hugging Face.
- Les huit backbones de représentation mono-entrée sont proposés comme encodeurs d'embeddings.
- Les conversions détection/document/profondeur mono-entrée restent en mode inspection tant qu'un décodeur spécialisé n'est pas qualifié.
- Grounding DINO et OWLv2 sont visibles mais volontairement non activables : leurs graphes nécessitent plusieurs entrées image/texte et le runtime Android V4.2 ne prétend pas encore les exécuter.
- Aucun modèle n'est embarqué dans l'APK ; tous les poids restent téléchargés à la demande.
- versionCode 7, versionName 4.2.0-rc2.
