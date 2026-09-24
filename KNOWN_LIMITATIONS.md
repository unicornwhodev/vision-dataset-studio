# Ce qui reste à améliorer

[Le projet](README.md) · [English](docs/en/KNOWN_LIMITATIONS.md)

Cadryl **4.2.0-rc6** reste une préversion. Voici les limites utiles à connaître avant de lui confier du travail. Les détails de chaque campagne sont dans le [rapport de tests](TEST_REPORT.md).

## Appareils et stabilité

Les deux Release signées passent sur l’émulateur Android 16 en pages de 16 Ko. L’audit strict de LiteRT, Flex, Graphics Path et DataStore est vert. **Le candidat actuel n’a pas encore sa recette complète sur téléphone**, et aucun appareil ARM 16 Ko physique n’a été testé. L’ARM64 traduit par l’émulateur ne remplace pas cette vérification.

Le Honor disponible fonctionne en 4 Ko. Les essais précédents sur ce téléphone sont conservés, mais une coupure ADB a interrompu la dernière campagne sur un runtime intermédiaire. Ils ne valident pas automatiquement rc6.

L’ancien crash ART n’a pas de cause confirmée. Le nouvel environnement n’a pas reproduit l’incident ; les lanceurs refusent une campagne qui contient un crash ART, y compris système. Les entraînements longs et le comportement après redémarrage ou restrictions constructeur demandent encore des essais. Les douze minutes déjà observées sur Honor concernent un candidat antérieur.

## Modèles et qualité

Les résultats du catalogue sont partiels et doivent être repris avec le nouveau runtime. La campagne publique Charlbi du 22 septembre compte **13 variantes réussies sur 25**, dont huit entraînables, un délai dépassé et onze non exécutées. Ce sont des essais d’exécution, pas une mesure de précision.

Les conversions entraînables fournies ajustent des têtes ou adaptations de sortie avec un encodeur figé. Le test synthétique des couches internes est une autre preuve. Il manque encore des mesures sur un corpus indépendant : précision, erreurs, oubli après entraînement, RAM et latence sur ARM.

Un bundle incomplet ne peut pas être remplacé par un simple fichier `.tflite`. Certains modèles demandent plusieurs graphes et des fichiers de prétraitement ou de tokenisation.

## Données et échanges

Les protections contre les interruptions existent et plusieurs pannes SAF, stockage, HF et purge ont été injectées lors des campagnes précédentes. Tous les fournisseurs de stockage, gros transferts multipart et cas de coupure ne sont pas couverts. Le serveur d’agent local réel et certaines intégrations cloud restent aussi à qualifier.

Le registre anti-doublons couvre les fichiers ou pixels identiques dans un même projet. Il ne garantit pas la détection de toutes les images recadrées ou recompressées. **Un export dataset n’est pas une sauvegarde complète du projet.** Les migrations Room gardent l’identité de l’app ; elles ne récupèrent pas les données d’une autre application ni d’une installation désinstallée.

## Distribution

rc6 utilise la clé durable. Les anciennes rc4/rc5 Debug portent d’autres certificats et ne peuvent pas être mises à jour directement. Garde leurs données. La clé actuelle et sa copie ont été vérifiées sur deux disques du même PC ; une sauvegarde hors machine reste à faire.

La CI distante est préparée mais n’a pas été exécutée. L’inventaire des 109 dépendances est disponible ; la revue des notices natives transitives reste ouverte. [Signature](docs/SIGNING.md) · [Licences](LICENSING_STATUS.md) · [Priorités](docs/ROADMAP.md).
