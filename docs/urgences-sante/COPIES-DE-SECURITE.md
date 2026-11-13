# Copies de sécurité et période de conservation

> AO 26-015-DO, **E-TN-15** : « le fournisseur doit fournir l'information relative aux sujets des
> copies de sécurité des données de la solution et de la période de conservation de ces copies ».
> Chaque chiffre est celui du code : `infra/terraform/aws/stockage-applicatif.tf` (infrastructure)
> et `backend-java/.../exploitation/domaine/Sauvegardes.java` (application). Le `plan` Terraform
> refuse une configuration qui ne les tiendrait pas (`objectifs-de-reprise.tf`).

## 1. Ce qui est copié

| Sujet | Contenu | Où il vit |
|---|---|---|
| Base de l'application | Toutes les données opérationnelles de tous les modules (horaires, quarts, absences, temps, paramètres, journal d'audit) | Fichier SQLite sur un volume EFS chiffré, régional (zones de Montréal) |
| Données propres au Java | Pièces jointes et fichiers produits | Même volume (`/donnees/java`) |
| Sauvegardes applicatives | Copies cohérentes de la base (`VACUUM INTO`), vérifiées par `integrity_check` à chaque copie | Même volume (`/donnees/sauvegardes`) |
| Copie par organisme | Les lignes d'un seul organisme, tirées de chaque sauvegarde complète (E-TN-6) | Même volume (`/donnees/sauvegardes/organismes/<organisme>`) |
| Journaux | Journaux applicatifs et d'accès | CloudWatch Logs, Montréal |

## 2. Quand et combien de temps

| Copie | Fréquence | Conservation | Mécanisme |
|---|---|---|---|
| Sauvegarde applicative incrémentale | Toutes les 15 minutes | 96 dernières (24 heures) | Ordonnanceur de l'application |
| Sauvegarde applicative complète | Quotidienne | 30 dernières (un mois) | Ordonnanceur de l'application |
| Copie quotidienne du volume | Chaque jour, 3 h (heure de l'Est) | 35 jours | AWS Backup, règle « quotidienne » |
| Copie mensuelle du volume | Le 1er de chaque mois | **6 ans** (année courante + 5 ans), en stockage froid après 90 jours | AWS Backup, règle « mensuelle » |
| Copie hors région (si autorisée) | Chaque jour | Selon `retention_hors_region_jours` | Copie AWS Backup vers ca-west-1 — **désactivée** sans autorisation écrite (contrat, art. 17.07.04) |

Le point de reprise (RPO) est de **15 minutes** : c'est la cadence des sauvegardes incrémentales.

## 3. Où sont les copies

Toutes les copies restent **au Québec**, dans la région AWS ca-central-1 (Montréal) : le volume,
le coffre AWS Backup et les journaux. La copie dans une seconde région (Calgary) n'existe que si
l'organisme l'autorise par écrit ; le `plan` la refuse sinon.

## 4. Protection des copies

- Chiffrement au repos (EFS et coffre AWS Backup, clés gérées par AWS KMS) et en transit (TLS
  vers le volume).
- Accès par rôle IAM seulement : la tâche applicative monte son répertoire par un point d'accès ;
  le service de sauvegarde a son propre rôle. Aucune clé d'accès stockée.
- Une copie applicative qui échoue à la vérification d'intégrité est supprimée et l'échec est
  signalé : une copie corrompue conservée vaudrait moins qu'une absence de copie.

## 5. Restauration et essais

- **Essai mensuel automatisé** (production) : le premier dimanche du mois à 4 h, AWS Backup
  restaure la dernière copie dans un volume jetable, valide, puis le supprime. Tout échec
  d'essai ou de restauration part au sujet d'alertes de l'exploitation.
- **Exercice de reprise applicatif** : l'administration du système restaure une sauvegarde à côté
  de la base vivante, jamais à sa place, et chaque exercice est consigné avec sa durée.
- **Retour arrière d'une donnée** : restauration d'une copie dans un volume distinct, puis
  extraction de ce qui doit être repris ; la base en service n'est jamais écrasée sans décision.

## 6. Fin de vie des copies

À l'échéance de conservation, AWS Backup supprime la copie. À la fin du contrat, les données de
l'organisme lui sont remises (copie par organisme), puis détruites avec une attestation
(destruction sécuritaire, P-TN-12) ; les copies qui les contiennent expirent au plus tard au
terme de leur conservation.
