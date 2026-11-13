/**
 * **Copie des sauvegardes dans une seconde région** — AO 26-015-DO, P-TN-4 (« les environnements
 * de reprise après sinistre offrent le même niveau de service qu'en temps normal »).
 *
 * **Désactivée par défaut, et c'est le contrat qui le veut.** L'article 17.07.04 du contrat
 * 26-015-DO (comme la réponse 11 de l'addenda du CSSDC) range les sites de relève et de sauvegarde
 * dans le « lieu d'hébergement », qui doit être au Québec ; communiquer les renseignements hors du
 * Québec exige une **autorisation écrite** de l'organisme (art. 70.1 RLRQ c. A-2.1, art. 78 RLRQ
 * c. R-22.1). AWS n'a qu'une région au Québec (ca-central-1) : la seule
 * seconde région canadienne, ca-west-1, est à Calgary. Activer cette copie sans l'autorisation
 * ferait sortir des renseignements de santé de la province — le `plan` le refuse.
 *
 * **Ce que la copie couvre**, une fois autorisée : la perte de la **région** entière de Montréal (les
 * trois zones à la fois), cas que la réplique Multi-AZ ne couvre pas. RDS réplique en continu les
 * sauvegardes automatisées **et les journaux de transactions** vers ca-west-1 ; la restauration à un
 * instant donné y reste possible sur `retention_hors_region_jours`. RPO visé : de l'ordre de quelques
 * minutes — AWS ne publie pas d'engagement de délai pour cette réplication ; le décalage réel se lit
 * dans `LatestRestorableTime` de la copie et doit être mesuré à l'exercice de reprise.
 *
 * **Ce qu'elle ne couvre pas** — il n'y a **pas de site de relève actif** :
 * - aucune application, aucun répartiteur, aucun pare-feu ni DNS ne sont déployés à Calgary. La
 *   reprise régionale consiste à appliquer cette même configuration dans ca-west-1 puis à restaurer la
 *   base depuis la copie : un RTO de **plusieurs heures**, à mesurer, et non celui d'une heure
 *   d'`urgences-sante.tfvars`, qui ne vaut que pour la perte d'une zone ;
 * - le « même niveau de service » de P-TN-4 n'est donc **pas** atteint en cas de perte de région :
 *   seule la donnée est préservée ;
 * - l'essai mensuel de restauration (essai-de-restauration.tf) éprouve la copie **locale** seulement.
 * - la paire ca-central-1 → ca-west-1 doit figurer parmi les régions sources admises par RDS :
 *   `aws rds describe-source-regions --region ca-west-1` le confirme avant activation.
 */

variable "sauvegardes_hors_region" {
  type        = bool
  default     = false
  description = "Réplique les sauvegardes automatisées RDS (et les journaux de transactions) dans region_releve. Fait sortir des renseignements du Québec : exige autorisation_hors_quebec."

  validation {
    # Garde-fou contractuel au `plan` : la copie hors Québec ne part jamais par simple bascule d'un
    # booléen, elle doit nommer l'autorisation écrite qui la permet.
    condition     = !var.sauvegardes_hors_region || length(trimspace(var.autorisation_hors_quebec)) >= 10
    error_message = "Copier les sauvegardes hors du Québec exige l'autorisation écrite de l'organisme (contrat 26-015-DO, art. 17.07.04) : renseigner autorisation_hors_quebec (référence du document, 10 caractères au moins)."
  }
}

variable "autorisation_hors_quebec" {
  type        = string
  default     = ""
  description = "Référence de l'autorisation écrite de l'organisme permettant l'hébergement de sauvegardes hors du Québec (numéro, date, signataire). Portée en étiquette sur les ressources de la seconde région."
}

variable "region_releve" {
  type        = string
  default     = "ca-west-1"
  description = "Région de la copie des sauvegardes. Seule ca-west-1 (Calgary) est admise : c'est la seule autre région canadienne d'AWS."

  validation {
    # La loi québécoise exige une protection « adéquate » hors Québec ; hors Canada, elle se démontre
    # bien plus difficilement. Le garde-fou ferme la porte plutôt que de la laisser à l'appréciation.
    condition     = var.region_releve == "ca-west-1"
    error_message = "Seule ca-west-1 (Calgary) est admise : la copie des sauvegardes reste au Canada."
  }
}

variable "retention_hors_region_jours" {
  type        = number
  default     = 7
  description = "Rétention des sauvegardes répliquées dans la seconde région (1 à 35 jours, limite d'RDS)."

  validation {
    condition     = var.retention_hors_region_jours >= 1 && var.retention_hors_region_jours <= 35
    error_message = "RDS admet une rétention répliquée de 1 à 35 jours."
  }
}

# Un fournisseur par région : la copie vit dans ca-west-1, la base source dans var.region. Les
# étiquettes disent honnêtement où sont les données, et sous quelle autorisation elles y sont.
provider "aws" {
  alias  = "releve"
  region = var.region_releve

  default_tags {
    tags = {
      Organisme              = var.organisme
      AppelOffres            = var.appel_offres
      Environment            = var.environnement
      Residence              = "Canada-hors-Quebec"
      AutorisationHorsQuebec = var.autorisation_hors_quebec == "" ? "aucune" : var.autorisation_hors_quebec
      GereePar               = "terraform"
    }
  }
}

# La base est chiffrée : sa copie doit l'être aussi, avec une clé de la région de destination — une
# clé KMS ne franchit pas les régions.
resource "aws_kms_key" "sauvegardes_releve" {
  provider = aws.releve
  count    = var.sauvegardes_hors_region ? 1 : 0

  description             = "Chiffrement des sauvegardes RDS répliquées — ${var.organisme} ${var.environnement}"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

resource "aws_db_instance_automated_backups_replication" "releve" {
  provider = aws.releve
  count    = var.sauvegardes_hors_region && var.base_postgresql ? 1 : 0

  source_db_instance_arn = aws_db_instance.principale[0].arn
  kms_key_id             = aws_kms_key.sauvegardes_releve[0].arn
  retention_period       = var.retention_hors_region_jours
}

# Le coffre de la seconde région, où la règle quotidienne d'AWS Backup copie le volume de données
# (stockage-applicatif.tf, copy_action) — sous la même autorisation écrite.
resource "aws_backup_vault" "releve" {
  provider    = aws.releve
  count       = var.sauvegardes_hors_region ? 1 : 0
  name        = "${local.prefixe}-coffre-releve"
  kms_key_arn = aws_kms_key.sauvegardes_releve[0].arn
}
