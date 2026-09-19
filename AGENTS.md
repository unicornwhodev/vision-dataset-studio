# Vision Dataset Studio UWD — cadre de qualification

Lire README.md, TEST_REPORT.md, KNOWN_LIMITATIONS.md, LICENSING_STATUS.md et docs/ANDROID_QUALIFICATION.md. Le code métier existant reste la base. Le propriétaire a demandé le 19 septembre 2026 une refonte complète de l’UI : studio sombre cyan/violet, textes courts, interactions animées. Conserver les protections des données et les confirmations de publication/suppression.

L’identité stable est `com.unicornwhodev.visiondatasetstudio`. Ne pas réintroduire de namespace de template, de preset métier imposé, de références à des dépôts privés ni d’archives documentaires externes au produit. Conserver les attributions requises : le branding ne prouve pas l’origine des droits. Ne pas choisir une licence sans décision explicite du titulaire.

P0 : compiler réellement avec les dépendances Compose/Room/Moshi/LiteRT, produire les schémas KSP et les deux APK, puis exécuter les tests préparés. Les tests portables ne sont pas une compilation Android. Ne pas supprimer de tests, ajouter de stubs Android ou inventer un identityHash Room pour afficher un succès.

Le build écrit un reçu par tentative, et le script d’installation vérifie les empreintes. Un échec peut conserver une véritable APK déjà construite dans la même tentative, mais ne devient pas une qualification. Ne jamais fabriquer un APK de substitution.

P1 : base réellement utilisée et sauvegardée ; source locale avec lots 2+1 ; tous les cas rejetés ; HF autorisé privé de test ; panne réseau, conflit, réponse perdue et purge interrompue ; permissions SAF révoquées et quota/volume perdu ; corpus autorisé représentatif ; mesure RAM/latence sur appareil. Les émulateurs ne remplacent pas un téléphone réel ou un benchmark ARM.

Aucun dépôt HF existant ne sert de poubelle de test. Pas de token, clé privée, poids privé ou corpus utilisateur dans les sources ou journaux. Écritures externes et création d’infrastructure seulement avec autorisation explicite.

Toujours protéger les annotations humaines. Ne pas déplacer silencieusement le parent HF. Ne pas purger sans preuve de copie relue. Le reçu doit survivre à la purge. Les migrations Room ne transfèrent pas les données d’une autre application Android.
