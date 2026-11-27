/**
 * **Le stockage que l'application lit vraiment** — AO 26-015-DO E-TN-9 (disponibilité), E-TN-15
 * (copies de sécurité et leur conservation), E-TN-16 (retour arrière), P-TN-3 (essais de reprise).
 *
 * L'application tient ses données dans une base SQLite (`DB_URL`), dont le Java bâtit le schéma au
 * démarrage (Liquibase) et qu'il sert, et écrit ses propres sauvegardes à côté (toutes les 15
 * minutes, `SAUVEGARDES_DOSSIER`). Sur Fargate, le disque d'une tâche disparaît avec elle : sans
 * ce volume, chaque redémarrage repartait d'une base vide. EFS est régional — ses données sont
 * répliquées dans les zones de disponibilité de Montréal — et chiffré au repos comme en transit.
 *
 * **Un seul écrivain.** SQLite n'admet qu'un processus écrivain : le service tourne avec une tâche
 * (`taches_application = 1`) et un déploiement arrête l'ancienne avant de démarrer la nouvelle.
 * La disponibilité tient au remplacement automatique de la tâche par ECS dans une autre zone, en
 * quelques minutes, sur le même volume — pas à deux tâches actives.
 */

resource "aws_efs_file_system" "donnees" {
  creation_token   = "${local.prefixe}-donnees"
  encrypted        = true
  performance_mode = "generalPurpose"
  throughput_mode  = "elastic"

  tags = { Name = "${local.prefixe}-donnees" }
}

resource "aws_security_group" "stockage" {
  name        = "${local.prefixe}-stockage"
  description = "NFS depuis les taches applicatives seulement"
  vpc_id      = aws_vpc.principal.id

  ingress {
    description     = "NFS chiffre (TLS) depuis les taches"
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.application.id]
  }
}

resource "aws_efs_mount_target" "donnees" {
  count           = length(aws_subnet.prives)
  file_system_id  = aws_efs_file_system.donnees.id
  subnet_id       = aws_subnet.prives[count.index].id
  security_groups = [aws_security_group.stockage.id]
}

# Le point d'accès borne la tâche à un répertoire, sous une identité fixe : un conteneur ne voit
# jamais la racine du système de fichiers.
resource "aws_efs_access_point" "donnees" {
  file_system_id = aws_efs_file_system.donnees.id

  posix_user {
    uid = 0
    gid = 0
  }

  root_directory {
    path = "/nexu"
    creation_info {
      owner_uid   = 0
      owner_gid   = 0
      permissions = "0750"
    }
  }
}

# ── Copies de sécurité (E-TN-15) ────────────────────────────────────────────

variable "retention_quotidienne_jours" {
  type        = number
  default     = 35
  description = "Conservation des copies quotidiennes du volume."

  validation {
    condition     = var.retention_quotidienne_jours >= 7
    error_message = "Moins de sept jours : une corruption découverte en fin de semaine ne se restaure plus."
  }
}

variable "retention_mensuelle_jours" {
  type        = number
  default     = 2190
  description = "Conservation des copies mensuelles : six ans couvrent l'année courante et les cinq années suivantes."

  validation {
    # La copie froide n'est admise qu'au-delà de 90 jours de stockage chaud (règle d'AWS Backup).
    condition     = var.retention_mensuelle_jours >= 1825 && var.retention_mensuelle_jours > 90
    error_message = "La conservation « année courante + 5 ans » exige au moins 1 825 jours."
  }
}

resource "aws_backup_vault" "principal" {
  name = "${local.prefixe}-coffre"
}

resource "aws_backup_plan" "donnees" {
  name = "${local.prefixe}-donnees"

  rule {
    rule_name         = "quotidienne"
    target_vault_name = aws_backup_vault.principal.name
    # 3 h du matin, heure de l'Est : après les déclarations d'absence du soir.
    schedule          = "cron(0 7 * * ? *)"
    start_window      = 60
    completion_window = 180

    lifecycle {
      delete_after = var.retention_quotidienne_jours
    }

    dynamic "copy_action" {
      for_each = var.sauvegardes_hors_region ? [1] : []
      content {
        destination_vault_arn = aws_backup_vault.releve[0].arn
        lifecycle {
          delete_after = var.retention_hors_region_jours
        }
      }
    }
  }

  rule {
    rule_name         = "mensuelle"
    target_vault_name = aws_backup_vault.principal.name
    schedule          = "cron(0 8 1 * ? *)"
    start_window      = 60
    completion_window = 360

    lifecycle {
      cold_storage_after = 90
      delete_after       = var.retention_mensuelle_jours
    }
  }
}

resource "aws_iam_role" "sauvegarde" {
  name = "${local.prefixe}-sauvegarde"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "backup.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "sauvegarde" {
  for_each = toset([
    "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup",
    "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForRestores",
  ])
  role       = aws_iam_role.sauvegarde.name
  policy_arn = each.value
}

resource "aws_backup_selection" "donnees" {
  name         = "${local.prefixe}-donnees"
  plan_id      = aws_backup_plan.donnees.id
  iam_role_arn = aws_iam_role.sauvegarde.arn
  resources    = [aws_efs_file_system.donnees.arn]
}

# ── Essai de restauration mensuel (P-TN-3) ──────────────────────────────────
# AWS Backup restaure la dernière copie dans un volume jetable, attend la fenêtre de validation,
# puis le supprime ; le résultat et la durée de chaque essai restent consultables dans la console
# « Restore testing », et un échec part au sujet d'alertes.

resource "aws_backup_restore_testing_plan" "donnees" {
  count = var.environnement == "production" ? 1 : 0
  name  = replace("${local.prefixe}_essai_restauration", "-", "_")

  # Le premier dimanche du mois, à 4 h du matin, heure de l'Est.
  schedule_expression          = "cron(0 4 ? * SUN#1 *)"
  schedule_expression_timezone = "America/Toronto"
  start_window_hours           = 2

  recovery_point_selection {
    algorithm             = "LATEST_WITHIN_WINDOW"
    include_vaults        = [aws_backup_vault.principal.arn]
    recovery_point_types  = ["SNAPSHOT"]
    selection_window_days = 7
  }
}

resource "aws_backup_restore_testing_selection" "donnees" {
  count                     = var.environnement == "production" ? 1 : 0
  name                      = "volume_donnees"
  restore_testing_plan_name = aws_backup_restore_testing_plan.donnees[0].name
  protected_resource_type   = "EFS"
  iam_role_arn              = aws_iam_role.sauvegarde.arn
  protected_resource_arns   = [aws_efs_file_system.donnees.arn]
  validation_window_hours   = 1

  restore_metadata_overrides = {
    newFileSystem = "true"
    Encrypted     = "true"
  }
}

resource "aws_cloudwatch_event_rule" "essai_restauration_echoue" {
  name        = "${local.prefixe}-essai-restauration-echoue"
  description = "Une restauration (essai ou réelle) a échoué : la reprise annoncée n'est plus démontrée."
  event_pattern = jsonencode({
    source        = ["aws.backup"]
    "detail-type" = ["Restore Job State Change", "Backup Job State Change"]
    detail        = { state = ["FAILED", "ABORTED", "EXPIRED"] }
  })
}

resource "aws_cloudwatch_event_target" "essai_restauration_echoue" {
  rule = aws_cloudwatch_event_rule.essai_restauration_echoue.name
  arn  = aws_sns_topic.alertes.arn
}
