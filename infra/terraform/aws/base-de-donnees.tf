/**
 * PostgreSQL managé — **optionnel, désactivé par défaut** (`base_postgresql`).
 *
 * L'application sert aujourd'hui ses données depuis SQLite, sur le volume de
 * `stockage-applicatif.tf` : aucun de ses conteneurs ne lit cette base. La provisionner
 * coûterait et protégerait une base vide — ce que la revue du 10 novembre 2026 a relevé.
 * Elle reste décrite ici pour le jour où l'application la lira ; on l'active alors par
 * `base_postgresql = true`, et les sections ci-dessous redeviennent exactes.
 *
 * **Une seule zone, et c'est voulu.** La réponse 12 de l'addenda fixe la reprise après sinistre
 * majeur à 48 heures. Une grappe multizone coûterait le double (0,282 $/h contre 0,141 $/h relevés
 * le 27 septembre) pour un délai que personne n'exige. La restauration à un instant donné suffit,
 * et elle est éprouvée (voir `essai-de-restauration.tf`).
 *
 * **Le point de reprise de 15 minutes** (exigence 48) est tenu par la restauration à un instant
 * donné, dont la granularité est de cinq minutes sur la fenêtre de rétention.
 */
resource "aws_db_subnet_group" "base" {
  count = var.base_postgresql ? 1 : 0

  name       = "${local.prefixe}-base"
  subnet_ids = aws_subnet.prives[*].id
}

resource "random_password" "base" {
  count = var.base_postgresql ? 1 : 0

  length  = 32
  special = true
  # Les caractères qu'une chaîne de connexion URL interprète sont écartés : un mot de passe qui
  # casse la chaîne de connexion se découvre au premier démarrage, pas au `plan`.
  override_special = "!#$%&*()-_=+[]{}<>?"
}

resource "aws_secretsmanager_secret" "base" {
  count = var.base_postgresql ? 1 : 0

  name        = "${local.prefixe}/postgresql"
  description = "Identifiants PostgreSQL — ${var.organisme} ${var.environnement}"
}

resource "aws_secretsmanager_secret_version" "base" {
  count = var.base_postgresql ? 1 : 0

  secret_id = aws_secretsmanager_secret.base[0].id
  secret_string = jsonencode({
    username = aws_db_instance.principale[0].username
    password = random_password.base[0].result
    host     = aws_db_instance.principale[0].address
    port     = aws_db_instance.principale[0].port
    dbname   = aws_db_instance.principale[0].db_name
  })
}

resource "aws_db_instance" "principale" {
  count = var.base_postgresql ? 1 : 0

  identifier     = "${local.prefixe}-pg"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.classe_base

  db_name  = "nexu"
  username = "nexu_admin"
  password = random_password.base[0].result

  allocated_storage     = var.stockage_base_go
  max_allocated_storage = var.stockage_base_go * 4
  storage_type          = "gp3"
  storage_encrypted     = true

  # Haute disponibilité : un paramètre, pas une constante. CSSDC (RTO 48 h, réponse 12) s'en passe ;
  # Urgences-santé (AO 26-015-DO, E-TN-9 : exploitation 20 h/7 j/365 j) la demande — voir
  # `urgences-sante.tfvars`. Le garde-fou de cohérence vit dans `objectifs-de-reprise.tf`.
  multi_az = var.haute_disponibilite

  db_subnet_group_name   = aws_db_subnet_group.base[0].name
  vpc_security_group_ids = [aws_security_group.base.id]
  publicly_accessible    = false

  backup_retention_period = var.retention_sauvegarde_jours
  backup_window           = "07:00-08:00" # 03 h 00 à 04 h 00, heure de l'Est — hors déclarations d'absence
  maintenance_window      = "sun:08:00-sun:09:00"
  copy_tags_to_snapshot   = true

  # **Aucune copie hors région par défaut.** Une réplique ou un instantané répliqué sortirait du
  # Québec, ce que la réponse 11 (CSSDC) et l'article 17.07.04 (Urgences-santé) interdisent sans
  # autorisation écrite. La seule réplication inter-régions admise vit dans
  # `sauvegardes-hors-region.tf`, désactivée par défaut et refusée au `plan` sans cette autorisation.

  performance_insights_enabled          = true
  performance_insights_retention_period = 7
  enabled_cloudwatch_logs_exports       = ["postgresql", "upgrade"]

  auto_minor_version_upgrade = true
  deletion_protection        = var.environnement == "production"
  skip_final_snapshot        = var.environnement != "production"
  final_snapshot_identifier  = var.environnement == "production" ? "${local.prefixe}-final" : null

  lifecycle {
    precondition {
      # Un garde-fou au `plan` : la conformité ne se vérifie pas à la première panne.
      condition     = var.retention_sauvegarde_jours >= 7
      error_message = "Rétention insuffisante pour tenir le point de reprise de 15 minutes de l'exigence 48."
    }
  }
}

variable "base_postgresql" {
  type        = bool
  default     = false
  description = "Provisionne PostgreSQL (RDS). Faux tant que l'application tient ses données dans SQLite sur le volume EFS."
}
