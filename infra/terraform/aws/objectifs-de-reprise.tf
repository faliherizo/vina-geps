/**
 * **Les objectifs de reprise, déclarés dans le code et validés au `plan`.**
 *
 * RTO 48 h (addenda n° 2, réponse 12) · RPO 15 min (exigence 48). Voir
 * `../objectifs-de-reprise.md` pour ce que ces deux chiffres imposent à l'architecture.
 *
 * L'essai mensuel de restauration vit dans `essai-de-restauration.tf` : c'est lui qui transforme
 * ces deux nombres en engagement démontrable.
 */

variable "rto_heures" {
  type        = number
  default     = 48
  description = "Délai maximal de remise en service après sinistre majeur."

  validation {
    condition     = var.rto_heures > 0 && var.rto_heures <= 48
    error_message = "L'addenda n° 2 (réponse 12) fixe la reprise à 48 heures : un RTO supérieur nous mettrait en défaut contractuel."
  }
}

variable "rpo_minutes" {
  type        = number
  default     = 15
  description = "Perte de données maximale acceptée."

  validation {
    condition     = var.rpo_minutes > 0 && var.rpo_minutes <= 15
    error_message = "L'exigence 48 impose une sauvegarde aux 15 minutes : un RPO supérieur ne satisfait pas la grille."
  }
}

/**
 * Le garde-fou qui compte : **la configuration choisie peut-elle tenir l'objectif déclaré ?**
 *
 * Le mécanisme réel (stockage-applicatif.tf) : la base SQLite et les sauvegardes que
 * l'application en tire toutes les 15 minutes vivent sur un volume EFS régional ; AWS Backup en
 * garde des copies quotidiennes et mensuelles dans un coffre. Le RPO de 15 minutes est celui des
 * sauvegardes applicatives ; le RTO est celui du remplacement de la tâche par ECS sur le même
 * volume (minutes) ou, après une corruption, de la restauration d'une copie (moins d'une heure
 * pour une base de cette taille — à mesurer à chaque essai mensuel).
 */
check "les_objectifs_sont_atteignables" {
  assert {
    condition     = var.retention_quotidienne_jours >= 7
    error_message = "Sans au moins sept jours de copies quotidiennes, une corruption découverte le lundi ne se restaure plus."
  }

  assert {
    # Le RPO de 15 minutes repose sur les sauvegardes applicatives : leur cadence ne doit pas le dépasser.
    condition     = var.rpo_minutes >= 15
    error_message = "Les sauvegardes applicatives sont faites toutes les 15 minutes : un RPO plus court ne serait pas tenu."
  }

  assert {
    # Une tâche unique (SQLite) se remplace en quelques minutes sur le volume régional ; un RTO
    # inférieur à une heure ne se démontre pas sans essai de bascule mesuré.
    condition     = var.rto_heures >= 1
    error_message = "Un RTO de moins d'une heure n'est pas démontrable avec une tâche unique remplacée par ECS."
  }

  assert {
    condition     = var.environnement != "production" || length(aws_backup_restore_testing_plan.donnees) == 1
    error_message = "En production, l'essai mensuel de restauration est obligatoire : sans lui, aucun RTO n'est démontré."
  }
}

output "objectifs_de_reprise" {
  description = "Ce que l'annexe 7.00 et le questionnaire EFVP demandent de déclarer, produit par le code."
  value = {
    rto_heures              = var.rto_heures
    rpo_minutes             = var.rpo_minutes
    mecanisme               = "SQLite sur EFS régional (multizone) ; sauvegardes applicatives aux 15 minutes ; AWS Backup quotidien et mensuel"
    retention_quotidienne   = "${var.retention_quotidienne_jours} jours"
    retention_mensuelle     = "${var.retention_mensuelle_jours} jours (année courante + 5 ans)"
    remplacement_de_tache   = "automatique (ECS), dans une autre zone, sur le même volume"
    sauvegardes_hors_region = var.sauvegardes_hors_region
    region_des_copies       = var.sauvegardes_hors_region ? var.region_releve : "aucune"
    zones_de_disponibilite  = length(local.zones)
    essai_de_restauration   = var.environnement == "production" ? "mensuel, premier dimanche à 4 h (heure de l'Est), AWS Backup Restore Testing" : "non planifié hors production"
  }
}
