# Bacs à sable — alertes critiques (critère 18) et PDF/A (critère 39)

FX-AO-20260810 laissait deux critères ouverts pour une raison extérieure au code :

- **Critère 18** : sur iOS, une alerte critique ne contourne le mode silencieux et « Ne pas déranger » qu'avec le droit
  *Critical Alerts*, qu'Apple accorde au cas par cas (Q-ALC, sans réponse). L'application mobile n'est pas dans ce dépôt.
- **Critère 39** : les rapports sont produits en PDF/A-2b, mais n'avaient jamais été passés au validateur de référence.

Les bacs à sable rendent ces deux chaînes jouables de bout en bout, **avec le comportement des services réels**, sur un
poste ou sur un serveur. Le serveur Java s'y branche par ses variables ordinaires : rien dans son code ne sait qu'il
parle à un bac à sable. Décision et options écartées : [ADR 0086](../docs/adr/0086-bacs-a-sable-poussee-critique-et-verapdf.md).

| Service | Port | Ce qu'il est | Ce qu'il prouve |
|---|---|---|---|
| `verapdf` | 18492 | **veraPDF officiel** (`verapdf/rest`) | Chaque PDF exporté est conforme PDF/A-2b, règle par règle |
| `poussee` | 18499 | Contrat de l'**API Expo** (`/--/api/v2/push/send`, `getReceipts`), traduction **APNs/FCM**, **téléphones simulés** | Ce que l'alerte critique devient sur un iPhone avec ou sans le droit d'Apple, sur un Android avec ou sans le canal `critique` |

## Fidélité du service de poussée

| Comportement réel | Reproduit |
|---|---|
| Un message seul rend un billet seul, un tableau rend un tableau | ✅ |
| Au plus 100 messages par requête ; champ mal formé → 400 `VALIDATION_ERROR` qui nomme le champ | ✅ |
| Jeton inconnu ou application désinstallée → billet `DeviceNotRegistered` | ✅ (le serveur Java périme alors l'appareil) |
| Charge > 4 096 octets → `MessageTooBig` | ✅ |
| Sécurité renforcée : jeton d'accès exigé → 401 `UNAUTHORIZED` | ✅ (`EXPO_JETON_ACCES`) |
| Accusés : `ok` par billet, identifiants inconnus omis | ✅ |
| iOS : `interruption-level: critical` + son critique honorés **seulement** avec le droit d'Apple **et** l'autorisation de l'utilisateur ; sinon ramené au niveau `active` | ✅ |
| Android : canal nommé par `channelId` ; canal absent → canal de repli ; contournement de « Ne pas déranger » si l'accès est accordé ; son « alarme » malgré le silencieux | ✅ |

Ce qui ne l'est pas : la livraison réseau effective vers un téléphone (APNs et FCM ne sont pas joints), les délais et
le plafond de débit d'Expo (600 notifications par seconde).

## Démarrer

```bash
docker compose -f bac-a-sable/docker-compose.yml up -d --build
curl -s http://127.0.0.1:18499/sante          # {"statut":"ok"}
curl -s http://127.0.0.1:18492/api/info       # version de veraPDF
```

Sans Docker, le service de poussée tourne aussi seul : `cd bac-a-sable/poussee && npm start` (Node 22, aucune dépendance).

## Brancher le serveur Java

```bash
EXPO_PUSH_URL=http://127.0.0.1:18499/--/api/v2/push/send java -jar backend-java/target/nexu-vina-backend-*.jar
```

C'est la variable du branchement réel (`ConfigurationFournisseurs.porteurNatif`) ; en production, elle est absente et le
serveur vise `https://exp.host`.

## Installer l'application simulée sur un téléphone

L'application mobile réelle s'inscrit auprès du serveur (`POST /api/notifications/appareils`) avec son jeton Expo.
Le bac à sable doit savoir quel téléphone porte ce jeton :

```bash
curl -X PUT http://127.0.0.1:18499/bac-a-sable/appareils/ExponentPushToken%5Biphone-julie%5D \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $BAC_JETON_CONTROLE" \
  -d '{"plateforme":"ios","application":{"droitAlertesCritiques":true},
       "utilisateur":{"autoriseAlertesCritiques":true,"modeSilencieux":true,"nePasDeranger":true}}'
```

| Champ | Sens |
|---|---|
| `application.droitAlertesCritiques` | iOS : le profil de l'application porte le droit `critical-alerts` accordé par Apple |
| `application.canaux[]` | Android : `{ id, importance (min…max), contourneNePasDeranger, usageAudio: "alarme" \| "notification" }` |
| `utilisateur.*` | `autoriseNotifications`, `autoriseAlertesCritiques`, `accesNePasDeranger`, `modeSilencieux`, `nePasDeranger` |

Ce que chaque téléphone a reçu, et comment il l'a présenté : `GET /bac-a-sable/notifications?jeton=…&titre=…`, ou la
console `http://127.0.0.1:18499/`.

## Valider les PDF

```bash
VERAPDF_URL=http://127.0.0.1:18492 scripts/verifier-pdfa.sh backend-java/target/pdfa
```

## Essais

| Niveau | Commande |
|---|---|
| Unitaires et HTTP du service de poussée (couverture ≥ 96 % exigée) | `cd bac-a-sable/poussee && npm test` |
| Bout en bout, contre le Java et les deux bacs à sable, données dans `e2e/donnees/fx-ao/bac-a-sable.json` | `bac-a-sable/campagne.sh` |

## Déployer sur un serveur

1. Copier `bac-a-sable/` ; `BAC_JETON_CONTROLE` et, si voulu, `EXPO_JETON_ACCES` dans un `.env` à côté du compose
   (jamais versionné).
2. `docker compose up -d --build`. Les deux services redémarrent seuls et tournent en lecture seule, sans privilège.
3. Publier 18492 et 18499 derrière le mandataire TLS de l'environnement d'essai ; l'environnement de formation de
   l'organisme règle `EXPO_PUSH_URL` vers le bac à sable.
4. Ne jamais pointer un environnement de production vers le bac à sable : aucun téléphone réel n'y serait joint.
