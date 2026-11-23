# ADR 0092 — L'adresse réelle du client, et les durcissements IN-01 à IN-05

- **Statut** : accepté, le 22 novembre 2026.
- **Décideurs** : équipe VINA.
- **Prolonge** : [ADR 0091](0091-correctifs-tests-intrusion-2026-11-22.md) (correctifs des tests d'intrusion).
- **Concerne** :
  - `frontend/nginx.conf` et `frontend/Dockerfile` ;
  - `backend-java/Dockerfile` et `application.properties` ;
  - `securite/domaine/PareFeu` ;
  - `infra/terraform/aws` (`calcul.tf`, `reseau.tf`) ;
  - `docker-compose.yml` et `frontend/package.json`.

## Contexte

Le rapport des tests d'intrusion du 22 novembre 2026 laissait quatre observations de durcissement ouvertes :

| Observation | Constat |
|---|---|
| IN-01 | Le processus maître de nginx tourne en root. |
| IN-03 | L'outillage de l'interface porte des avis `npm audit`. |
| IN-04 | Les fichiers SQLite sont en 0644. |
| IN-05 | N'importe qui peut verrouiller un compte connu. |

L'examen d'IN-05 a révélé un défaut plus large. Derrière nginx, et derrière le répartiteur en AWS,
`HttpServletRequest.getRemoteAddr()` rendait l'adresse du **mandataire**. Trois mécanismes en souffraient :

- **Verrou de connexion** : sa clé `adresse|compte` devenait une clé par compte, et un inconnu pouvait le poser.
- **Limitation de débit du pare-feu** : elle était commune à tous les utilisateurs.
- **Pointage** : le contrôle de plage réseau jugeait l'adresse de nginx, et non celle du poste.

Ajouter seulement une confiance de Tomcat envers `X-Forwarded-For` aurait créé une usurpation. nginx relayait
`$proxy_add_x_forwarded_for`, qui **ajoute** à la chaîne reçue : un client pouvait y glisser l'adresse de son choix, et
toute adresse privée (le poste de développement, un client interne) aurait été crue.

## Options considérées (adresse réelle)

1. **nginx établit l'adresse, Java croit nginx** (retenue) :
   - nginx (module `realip`) ne croit `X-Forwarded-For` que des plages de `API_PROXIES_CONFIANCE`, soit le VPC en AWS et
     personne en local ;
   - il **réécrit** ensuite l'en-tête vers l'API (`$remote_addr`) ;
   - Tomcat (`server.forward-headers-strategy=native`) ne croit l'en-tête que d'un mandataire interne : nginx.
2. **Tomcat seul, avec la chaîne ajoutée par nginx.** La chaîne reste forgeable depuis toute adresse privée.
3. **Lire `X-Real-IP` dans le code.** Chaque lecteur d'adresse devrait y penser, et l'en-tête reste forgeable sans le
   filtrage de nginx.

## Décision

- **IN-01** :
  - l'image passe à `nginxinc/nginx-unprivileged` et écoute sur **8080** ;
  - Docker Compose publie 8080 ;
  - Terraform fait passer tous les ports de l'interface (groupe cible, service ECS, deux règles de sécurité) par
    `local.port_web = 8080`, vérifié par `tests/ports-interface.tftest.hcl`.
- **IN-03** : vite 8, vitest et `@vitest/coverage-v8` 5, Capacitor 8. `uuid` est forcé en 11.1.1 sous `exceljs`, que
  l'on ne rétrograde pas. Le script `audit:prod` (`npm audit --omit=dev --audit-level=high`) entre dans l'intégration
  continue. L'image construit l'interface avec Node 22, qu'exige Capacitor 8.
- **IN-04** :
  - `/app/data` en 700 dans l'image ;
  - `umask 077` pour tout ce que le serveur crée ;
  - `chmod -R go-rwx /app/data` au démarrage, pour un volume plus ancien.
- **IN-05** :
  - **adresse réelle** : décision 1 ci-dessus ;
  - **verrou par adresse** : `PareFeu` ajoute un seuil par adresse, tous comptes confondus (`WAF_ECHECS_CONNEXION_ADRESSE`,
    50 par défaut, 0 le désactive) contre l'aspersion de mots de passe ;
  - **verrou par compte** : il reste lié à l'adresse et ne peut donc plus être posé sur la victime par un tiers.

## Conséquences

- **Positives** :
  - Le verrou, la limitation de débit et le contrôle de plage réseau du pointage voient enfin le client.
  - Un `X-Forwarded-For` forgé n'a aucun effet : l'essai `09-durcissements` en envoie dix différents, et le verrou tombe
    quand même.
  - `npm audit` ne relève plus rien, outillage compris.
- **Négatives** :
  - Le port de l'interface change, de 80 à 8080 : un déploiement Terraform remplace le groupe cible.
  - En AWS, `API_PROXIES_CONFIANCE` vaut la plage du VPC. Un hôte interne au VPC pourrait donc forger l'en-tête vers nginx,
    ce qui suppose déjà un accès au réseau privé.
  - Plusieurs postes derrière une même adresse NAT partagent le seuil par adresse : 50 échecs, et c'est réglable.
- **Essais** :
  - `PareFeuTest.aspersionParAdresse` ;
  - `nginx-conf.test.ts` (IN-01, IN-05) ;
  - `ports-interface.tftest.hcl` ;
  - `DemarrageTest`, avec la nouvelle propriété ;
  - côté projet de tests d'intrusion, `09-durcissements.test.mjs` contre la pile reconstruite : 57/57.
