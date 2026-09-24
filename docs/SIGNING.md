# Signature durable et mises à jour

L’identité de distribution est fixée dans [`config/release-signing.json`](../config/release-signing.json).
Son certificat public SHA-256 est :

```text
51ef3e4abf953c62c8427deaecf4349aefffc2270c593229315722457f126895
```

La clé RSA 4096 a été créée le 23 septembre 2026 pour 30 ans. Les scripts de
signature refusent un certificat différent. **Conserver cette clé pour toutes
les mises à jour ; ne pas en recréer une à chaque build.**

## Stockage privé et restauration

La clé principale est hors Git, sous le profil Windows, dans
`.android/signing/vision-dataset-studio`. Son mot de passe est protégé par DPAPI
pour le compte Windows qui l’a créée. La copie de sauvegarde est stockée hors dépôt, sur un second disque, avec accès limité au propriétaire et à SYSTEM. Elle contient la clé et son secret de récupération portable.

Les deux copies ont été comparées par SHA-256. Le mot de passe de récupération
a permis de relire le certificat **et de signer une vraie APK**. Les disques C:
et D: sont distincts, mais appartiennent au même PC : une copie hors machine
n’est pas encore attestée. Aucun de ces fichiers privés ne doit être ajouté au
dépôt, aux artefacts CI ou à une release.

La restauration exige `release.p12`, `recovery-password.txt` et `receipt.json`.
Les copier dans un emplacement privé hors dépôt, puis utiliser le script de
signature avec cet emplacement. La copie portable fonctionne sans le compte
Windows d’origine. Ne jamais coller le secret dans une commande ou un journal.

## Construire et signer

```powershell
python -X utf8 tools/build_release_candidate.py --abi arm64-v8a
# Utiliser le répertoire exact annoncé par le build vérifié.
./tools/sign_with_local_keystore.ps1 -KeyDirectory "$env:USERPROFILE/.android/signing/vision-dataset-studio" -Kind release -ArtifactDirectory "dist/distribution/runs/RECU_DU_BUILD"
```

Le script vérifie la clé, l’empreinte publique et l’APK du reçu avant signature.
Les mots de passe passent uniquement par l’environnement du sous-processus et
sont retirés ensuite. Les APK et reçus précédents restent conservés. Le reçu de
signature prouve les octets et le certificat ; la sauvegarde et la recette
Android sont des preuves distinctes.

`tools/create_release_keystore.ps1` est un outil de création initiale, pas une
étape du build. Il refuse d’écraser un répertoire existant. Il n’est **pas** à
relancer pour cette application dont l’identité est déjà fixée.

## Installations historiques

rc4 et rc5 Debug utilisent d’autres certificats. La nouvelle clé ne peut pas
transformer ces installations en mises à jour compatibles. La clé de rc4 était
sur une VM et n’est pas disponible. Conserver toute installation contenant des
données ; ne pas la désinstaller pour contourner le contrôle Android.

La continuité de mise à jour qualifiée concerne les APK portant le certificat
ci-dessus et le même identifiant `com.unicornwhodev.visiondatasetstudio`. Les
migrations Room ne déplacent pas les données depuis une autre application.

Pour la recette seulement, `-Kind qualification` signe les APK Debug et de test
vérifiées avec cette même clé. Elles restent des builds de test ; l’APK Release
optimisée ne contient pas les composants de diagnostic ajoutés à `src/debug`.
