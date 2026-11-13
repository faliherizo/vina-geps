# Sécurité des transmissions — matrice par type de transmission

**AO 26-015-DO, exigence E-TN-8** : « La solution garantit la confidentialité, liaison et
disponibilité pour chaque type de transmission (exemples : HTTPS sur Internet, IPSec VPN, MPLS,
extension LAN). »

Relevé le 10 novembre 2026 dans le dépôt, sur la branche `features/urgences-sante`. Chaque ligne
renvoie à l'endroit qui la met en œuvre. Ce qui n'a pas été trouvé dans le dépôt est écrit
**non couvert**, et non supposé. L'infrastructure de référence est le Terraform AWS
`infra/terraform/aws/` (région ca-central-1, Montréal).

## Matrice

| # | Transmission | Protocole | Chiffrement | Authentification | Disponibilité | Mis en œuvre par |
|---|---|---|---|---|---|---|
| 1 | **Navigateur → répartiteur** (usagers, Internet) | HTTPS, port 443 uniquement ; aucun écouteur HTTP 80 | TLS 1.2 et 1.3, politique `ELBSecurityPolicy-TLS13-1-2-2021-06` ; certificat ACM ; HSTS `max-age=31536000; includeSubDomains` ; cookie de session `Secure` | Serveur : certificat ACM. Usager : session (cookie) après mot de passe, TOTP ou SSO ; jeton CSRF sur les requêtes à cookie | Répartiteur sur trois zones ; pare-feu applicatif OWASP et limitation de débit ; Shield Standard | `infra/terraform/aws/calcul.tf` (`aws_lb_listener.https`, variables `HTTPS` et `COOKIE_SECURE`) · `reseau.tf` (groupe `repartiteur`, 443 seul) · `pare-feu.tf` · `detection.tf` · `backend-java/.../securite/infrastructure/web/FiltrePareFeu.java` (HSTS) · `backend-java/.../partage/securite/FiltreCsrf.java` |
| 2 | **Répartiteur → tâches applicatives** (dans le VPC) | HTTP, port 8080 | **Aucun** : TLS terminé au répartiteur. Le trafic reste dans le VPC privé | Groupe de sécurité : seul le répartiteur joint le port 8080 | Deux tâches (Urgences-santé) réparties sur trois sous-réseaux privés ; contrôle de santé `/api/health` | `calcul.tf` (`aws_lb_target_group.application`, `protocol = "HTTP"`) · `reseau.tf` (`application_depuis_repartiteur`) |
| 3 | **Application mobile → API** | HTTPS vers le même répartiteur que (1) | TLS du répartiteur (1). L'adresse de l'API est en `https://` dans les profils `preview` et `production` | `Authorization: Bearer` + `x-tenant-id` ; jeton gardé dans le coffre du téléphone (`expo-secure-store`) | Celle de (1) | `mobile/eas.json` · `mobile/src/api/client.ts` · `mobile/src/api/session.ts` · `backend-java/.../partage/securite/FiltreSession.java`. **Non couvert** : épinglage de certificat ; l'interdiction du HTTP en clair dépend des réglages par défaut d'Android et d'iOS, aucun n'est fixé dans `mobile/app.json` |
| 4 | **API → base de données** | PostgreSQL 5432 dans le VPC (RDS) | Au repos : `storage_encrypted = true`. En transit : **non couvert** — voir l'encadré ci-dessous | Identifiants dans Secrets Manager, injectés par l'agent ECS ; groupe de sécurité : seules les tâches joignent 5432 ; `publicly_accessible = false` | Multi-AZ (`haute_disponibilite = true` pour Urgences-santé) | `base-de-donnees.tf` · `reseau.tf` (groupe `base`) · `calcul.tf` (`secrets`) |
| 5a | **API → courriel (SMTP)** | SMTP 587 avec STARTTLS, ou SMTPS 465 (`SMTP_SECURE=1`) | STARTTLS **opportuniste** : activé (`mail.smtp.starttls.enable`) mais non exigé (`starttls.required` absent) | `SMTP_USER` / `SMTP_PASS` si fournis | Un refus est journalisé, l'action métier continue | `backend-java/.../partage/courrier/infrastructure/CourrierSmtp.java`. **Non couvert** dans le Terraform : aucune variable `SMTP_*` n'est passée à la tâche — sans `SMTP_HOST`, le courrier est journalisé et non envoyé |
| 5b | **API → opérateur de textos** (Twilio, Telnyx, générique) | HTTPS | TLS ; Twilio à `https://api.twilio.com` par défaut | Identifiants du fournisseur (`SMS_JETON` en `Bearer`, `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN`) | Selon l'opérateur | `backend-java/.../notifications/infrastructure/persistance/Twilio.java`, `SmsHttp.java`, `ConfigurationFournisseurs.java`. **Non couvert** : le schéma `https` n'est pas imposé à `SMS_URL` / `TELNYX_URL` ; les variables ne sont pas passées par le Terraform |
| 5c | **API ↔ fournisseur d'identité** (Entra ID OIDC, SAML) | HTTPS | TLS ; autorité par défaut `https://login.microsoftonline.com/<locataire>` | OIDC : `id_token` RS256 vérifié ; SAML : signature XML, émetteur et conditions vérifiés | Celle de l'IdP | `backend-java/.../auth/domaine/Oidc.java`, `auth/infrastructure/persistance/EchangeurOidcHttp.java`, `auth/domaine/Saml.java` |
| 5d | **Notifications Web Push** | HTTPS vers le service de poussée du navigateur | TLS | Clés VAPID (`PUSH_VAPID_*`) | Celle du service de poussée | `backend-java/.../notifications/domaine/AbonnementsPoussee.java` (refuse un point de terminaison non `https://`) |
| 6 | **Sauvegardes** | Interne à AWS (RDS) | Au repos : stockage chiffré, instantanés compris. Copie hors région (si autorisée) : clé KMS propre à ca-west-1 | IAM d'AWS | Rétention 14 j en production (7 j essai), restauration à un instant donné ; essai mensuel de restauration ; copie optionnelle vers ca-west-1 | `base-de-donnees.tf` · `essai-de-restauration.tf` · `sauvegardes-hors-region.tf` (désactivée par défaut, art. 17.07.04). **Non couvert** : les sauvegardes applicatives (`nexu.sauvegardes.dossier`, `exploitation/infrastructure/persistance/DepotSauvegardes.java`) ne sont pas chiffrées par l'application |
| 7a | **Intégrations sortantes vers l'organisme** (connecteurs d'effectifs, paie, webhooks) | HTTPS (Internet), ou HTTPS **dans le tunnel IPsec** si le VPN est monté | TLS exigé : une adresse non `https` est refusée à la configuration (`localhost` excepté) ; webhooks signés HMAC-SHA256 | Jeton référencé (`jetonRef`, jamais en base) ; signature HMAC du corps | Celle du système de l'organisme ; VPN à deux tunnels | `backend-java/.../effectifs/domaine/IntegrationsEffectifs.java` (`SCHEMA_SUR`) · `backend-java/.../integrations/domaine/Webhooks.java` · `infra/terraform/aws/liaison-privee.tf` |
| 7b | **Liaison privée site-à-site** (IPsec VPN) | IPsec IKEv2, deux tunnels, routes statiques vers les réseaux déclarés de l'organisme | AES-256-GCM ; intégrité SHA2-384/512 ; groupes DH 19-21 et 14-16 (ITSP.40.062) | Clés pré-partagées tirées par AWS, sorties en `sensitive` | Deux tunnels dans deux zones d'AWS ; alarme si l'un tombe 15 minutes ; journaux IKE dans CloudWatch | `infra/terraform/aws/liaison-privee.tf`, sorties `vpn_tunnels` et `vpn_configuration_organisme` (`outputs.tf`). Désactivé par défaut (`vpn_site_a_site = false`) |
| 7c | **Échanges de fichiers** (CSV Scolago, ATS, imports) | Téléversement par l'écran, donc par (1) | TLS de (1) | Session de l'usager | Celle de (1) | Aucun canal dédié. **Non couvert** : SFTP ou dépôt de fichiers automatisé — aucune trace dans le dépôt |
| 7d | **MPLS, extension LAN, Direct Connect** | — | — | — | — | **Non couvert** : un circuit dédié se commande auprès d'un opérateur ; rien dans le dépôt ne le décrit |
| 7e | **Organisme → API par le tunnel** | — | — | — | — | **Non couvert** : l'API n'est exposée que par le répartiteur public ; le VPN ne sert que le sens application → organisme. Il faudrait un répartiteur interne |
| 8a | **Administration de l'application** (superadmin, administrateurs d'organisme) | HTTPS, comme (1) | TLS de (1) | Mot de passe, TOTP (`auth/domaine/Totp.java`), contrôle d'accès par rôle | Celle de (1) | `backend-java/.../auth/` |
| 8b | **Administration de l'infrastructure** (console et API d'AWS, Terraform) | HTTPS vers les API d'AWS | TLS d'AWS ; état Terraform dans un seau S3 chiffré au Québec (`encrypt = true`) | IAM d'AWS. **Non couvert** : comptes IAM, MFA et rôles d'administration ne sont pas décrits dans le dépôt | — | `infra/terraform/aws/versions.tf` (backend `s3`). Aucun bastion ni `ECS Exec` : la base n'a pas de chemin d'administration direct |
| 8c | **Déploiement** | SSH (GitHub Actions → hôte Docker) | SSH | Clé `DEPLOY_SSH_KEY` en secret GitHub ; environnement GitHub `production` (ses règles de protection se règlent hors du dépôt) | Plage d'entretien (P-TN-6) | `.github/workflows/ci-cd.yml` (travail `deploy`). **Écart** : ce travail vise un hôte Docker, pas la pile ECS du Terraform AWS |

## Encadré — la ligne 4 n'est pas ce que le Terraform laisse croire

Le Terraform crée une base PostgreSQL RDS et injecte `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD` et
`PGDATABASE` dans la tâche. **Aucun code du backend Java ne lit ces variables** : la source de données
est `spring.datasource.url=${DB_URL:jdbc:sqlite:...}` avec le pilote `org.sqlite.JDBC`
(`backend-java/src/main/resources/application.properties`), et l'image fixe `DB_FICHIER` sur un fichier
local du conteneur (`backend-java/Dockerfile`). Le pilote PostgreSQL est déclaré dans `pom.xml`, avec
la mention que le passage à PostgreSQL est « une phase terminale ».

Conséquence pour E-TN-8 : il n'y a, en l'état, **aucune transmission application → base** à protéger
sur le réseau ; mais la base RDS chiffrée, Multi-AZ et sauvegardée ne porte pas non plus les données de
l'application. Le chiffrement en transit vers RDS (`sslmode=verify-full` et le groupe de paramètres
`rds.force_ssl`) est à établir **le jour où** l'application s'y connectera.

## Ce que la matrice ne prouve pas

- Rien de ce qui précède n'a été **déployé** ni mesuré sur AWS : les lignes 1, 2, 6 et 7b sont prouvées
  par le code et `terraform validate`, pas par un balayage TLS (testssl.sh, SSL Labs) d'un environnement
  en service.
- Le Terraform publie le conteneur sur le port 8080 et sonde `http://localhost:8080/api/health`, alors
  que l'image Java écoute sur `PORT=4000` par défaut : à corriger avant tout déploiement.
- `JWT_SECRET` n'est pas passé par le Terraform : la tâche reprendrait la valeur de développement de
  `application.properties`.
