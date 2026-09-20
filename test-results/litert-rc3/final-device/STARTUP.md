# Contrôle du démarrage / Startup check

La première commande `am start -W` a atteint son délai (10,403 s) avant affichage ; `app-start.png` est cette frame vide, pas une validation visuelle. Après redémarrage propre de l’émulateur, `app-ui.xml` confirme l’activité et les contrôles Atelier/Modèles ; `app-ready.png` montre l’écran rendu. Le démarrage logiciel de l’émulateur a pris 87,670 s. Ces durées ne décrivent pas un téléphone ARM.

The initial `am start -W` timed out (10.403 s) before rendering; `app-start.png` is that empty frame, not visual acceptance. After a clean emulator restart, `app-ui.xml` confirms the activity and Atelier/Modèles controls; `app-ready.png` shows the rendered screen. Software-emulator boot took 87.670 s. These durations do not describe an ARM phone.
