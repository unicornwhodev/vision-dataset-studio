# rc5 — preuves publiques sélectionnées

Build Windows `20260923T104142Z-befce3e3596a` : 67 JVM, 63 Python au moment du build,
70 Python avant publication avec les sept contrôles de packaging ajoutés ;
39/39 Android en 4 Ko et 39/39 en 16 Ko, sans ignorés. Les deux tests ARM64 sous
traduction Android passent ; aucun téléphone ARM n’est qualifié.

L’original et son profil restent intacts. Deux lots successifs poursuivent les
mêmes poids entraînés ; les fichiers survivent au redémarrage réel. Le crash Flex
16 Ko est corrigé ; six constats RELRO dans trois autres bibliothèques restent ouverts.

Ces fichiers sont des copies sélectionnées des reçus originaux, pas des reçus
réécrits après compilation. Le `source_commit` du build désigne la base avant le
commit des modifications locales. Le packageur compare chaque SHA de source au
commit final et conserve cette distinction dans `PACKAGE.json`.

Les fixtures et projets sont synthétiques. Aucun corpus utilisateur, poids de
modèle, sauvegarde de base privée, token, keystore ou clé privée n’est publié.
L’APK et le runtime se trouvent dans les assets de la release, pas dans Git.

**Signature :** l’APK Windows est une prérelease Debug avec un certificat
différent de rc4. Elle ne peut pas mettre à jour une installation rc4. La clé
privée rc4 a été utilisée sur l’ancienne VM et n’est pas disponible sur ce poste. Ne pas
désinstaller une installation contenant des données pour contourner ce conflit.
Le candidat Release ARM64 séparé est non signé et non installable en l’état.

[Rapport détaillé](../../docs/WINDOWS_QUALIFICATION_2026_09.md) ·
[Conservation du modèle](../../docs/MODEL_LINEAGE.md).

Provenance Git : **162 des 163 fichiers enregistrés sont identiques octet par octet**.
Le seul écart est la conversion CRLF vers LF de `tools/gradle_bootstrap.py`,
script Python exécuté sur le poste de build. Les sources Android sont identiques.
`PACKAGE.json` conserve séparément les deux empreintes de ce script ; les reçus
originaux ne sont pas réécrits.
