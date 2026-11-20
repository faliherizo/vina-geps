# ADR 0086 — Des bacs à sable fidèles et déployables pour l'alerte critique (Expo, APNs, FCM) et le PDF/A (veraPDF)

- **Statut** : accepté, le 20 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : l'ADR 0080 (alerte critique poussée en critique), l'ADR 0081 (PDF/A-2b).
- **Concerne** : `bac-a-sable/` (compose, service de poussée, campagne) ; `e2e/tests/e2e/helpers/comportements.ts`
  (services externes, étapes rejouées, vérificateurs) ; `e2e/donnees/fx-ao/bac-a-sable.json` ;
  `e2e/tests/e2e/fx-ao-bac-a-sable.spec.ts` ; `scripts/verifier-pdfa.sh` ; `.github/workflows/ci.yml` (`bacs-a-sable`).
- **Critères** : FX-AO-20260810, 18 et 39.

## Contexte

Deux critères restaient ouverts pour des raisons extérieures au code. Pour le critère 18, une alerte critique iOS exige
le droit *Critical Alerts*, accordé par Apple au cas par cas (Q-ALC, sans réponse), et l'application mobile n'est pas
dans ce dépôt. Pour le critère 39, les PDF sont produits en PDF/A-2b, mais n'avaient jamais été soumis au validateur de
référence.

On demande un bac à sable qui se comporte comme les services réels, qu'on peut déployer sur un serveur, et qui se
teste comme l'application réelle.

## Options considérées

1. **Bacs à sable de service, branchés par les variables de production** (retenue). veraPDF est le logiciel officiel,
   en conteneur. La poussée passe par un service qui reproduit le contrat public d'Expo, traduit en APNs ou en FCM, et
   remet à des téléphones simulés qui appliquent les règles documentées d'iOS et d'Android. Le Java s'y branche par
   `EXPO_PUSH_URL`, sa variable réelle.
2. **Doubles dans le code Java** (`PousseeNative.Simulee` existe déjà). Ce double prouve le branchement, pas le
   contrat ni ce que voit le téléphone. Il ne se déploie pas.
3. **Imiter veraPDF** (réponse « conforme » fabriquée). Ce serait une preuve fausse : écartée.
4. **Joindre le vrai Expo avec des téléphones réels.** Il faut un compte Expo, des appareils et le droit d'Apple :
   c'est précisément ce qui bloque. Cette voie reste la recette finale.

## Décision

- **veraPDF n'est jamais imité** : l'image `verapdf/rest` est le validateur lui-même. Les exports PDF réels du Java (le
  suivi des alertes et le justificatif des utilisateurs actifs) lui sont soumis. Une règle en échec est nommée par sa
  clause ISO 19005-2.
- **Le service de poussée reproduit le contrat d'Expo.** Forme du billet selon la requête, limite de 100 messages,
  `VALIDATION_ERROR` nommant le champ, `DeviceNotRegistered`, `MessageTooBig`, jeton d'accès (`UNAUTHORIZED`), accusés.
  Il traduit en charge APNs (`interruption-level`, son `critical: 1`, `apns-priority`) ou en message FCM (priorité,
  canal).
- **L'application mobile est simulée par un profil d'appareil.** Le profil donne la plateforme, le droit d'Apple, les
  canaux Android créés, les autorisations et l'état du téléphone (silencieux, « Ne pas déranger »). Les règles appliquées
  sont celles des systèmes : iOS ramène une alerte critique au niveau `active` sans le droit ou sans l'autorisation ;
  Android renvoie au canal de repli si le canal n'existe pas.
- **Aucun code d'essai dans le serveur.** Le Java ignore qu'il parle à un bac à sable.
- **Les données sont dans un JSON.** L'interprète commun gagne trois notions génériques : une étape vers un service
  externe, une étape rejouée jusqu'à un délai, et un vérificateur nommé sur les octets d'une réponse.
- **Déploiement.** Les services sont décrits dans un compose et tournent en lecture seule, sans privilège, avec un
  contrôle de santé. Les ports sont liés à 127.0.0.1 par défaut. Un jeton de contrôle protège la lecture des
  notifications reçues.
- **Jamais vert à vide.** Hors de la campagne, l'essai se déclare sauté avec son motif. Dans la campagne, un bac à
  sable absent le fait échouer.

## Conséquences

- **Positives.** La chaîne serveur → Expo → APNs/FCM → téléphone est jouée contre le vrai Java, dans les quatre cas
  qui comptent pour l'avis : iPhone avec et sans le droit d'Apple, Android avec et sans le canal. Elle couvre aussi le
  cas de l'appareil désinstallé, que le serveur cesse de viser. Les PDF passent au validateur de référence en CI.
- **Négatives.** La fidélité de la poussée tient à notre lecture des documentations d'Expo, d'Apple et de Google. Une
  évolution de leur contrat doit être reportée au bac à sable. Ni la livraison réseau, ni les délais, ni le plafond de
  débit d'Expo ne sont reproduits.
- **Ne lève pas** Q-ALC : le bac à sable montre ce que le droit d'Apple change, il ne l'accorde pas. La recette finale
  se fait avec un appareil réel et le vrai service.
- **Essais.**
  - `bac-a-sable/poussee` : 23 essais unitaires et HTTP, couverture ≥ 96 % exigée par `npm test`.
  - `fx-ao-bac-a-sable.spec.ts` : BAC-01 à BAC-09.
  - Travail CI `bacs-a-sable`.
