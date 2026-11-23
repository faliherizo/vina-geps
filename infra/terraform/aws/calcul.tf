/**
 * Calcul : **nos images Docker, telles quelles**.
 *
 * Fargate exécute les images du `docker-compose` du dépôt sans modification. Le choix d'ARM64
 * (Graviton) tient à un prix relevé : 0,035650 USD par vCPU-heure contre 0,044560 en x86, soit
 * 20 % de moins pour un code Java et Node qui s'y compile sans y penser.
 *
 * Ce que Fargate achète pour 1 076 $ par année : aucun système d'exploitation à corriger. Sous une
 * résolution d'incident en une heure en heures de calendrier (réponse 17), c'est du temps
 * d'astreinte qu'on n'a pas à provisionner.
 */
resource "aws_ecs_cluster" "principal" {
  name = local.prefixe

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/ecs/${local.prefixe}"
  retention_in_days = 90
}

resource "aws_iam_role" "execution" {
  name = "${local.prefixe}-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# **La clé qui signe les jetons de session.** Sans elle, l'application reprend la valeur de
# développement d'application.properties — publique dans le dépôt : n'importe qui pourrait forger
# une session d'administrateur. 64 caractères tirés à la création, jamais affichés.
resource "random_password" "session" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "session" {
  name        = "${local.prefixe}/jwt-session"
  description = "Clé de signature des sessions — ${var.organisme} ${var.environnement}"
}

resource "aws_secretsmanager_secret_version" "session" {
  secret_id     = aws_secretsmanager_secret.session.id
  secret_string = random_password.session.result
}

# Les secrets sont lus par l'agent d'exécution, jamais inscrits dans la définition de tâche.
resource "aws_iam_role_policy" "lecture_secret" {
  name = "lecture-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = compact([aws_secretsmanager_secret.session.arn, var.secret_registre_arn])
    }]
  })
}

resource "aws_iam_role" "tache" {
  name = "${local.prefixe}-tache"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Le montage du volume se fait sous l'identité de la tâche (authorization_config iam = ENABLED).
resource "aws_iam_role_policy" "montage_donnees" {
  name = "montage-volume-donnees"
  role = aws_iam_role.tache.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = ["elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite"]
      Resource  = aws_efs_file_system.donnees.arn
      Condition = { StringEquals = { "elasticfilesystem:AccessPointArn" = aws_efs_access_point.donnees.arn } }
    }]
  })
}

resource "aws_ecs_task_definition" "application" {
  family                   = local.prefixe
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.processeur_tache
  memory                   = var.memoire_tache
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.tache.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64" # 20 % moins cher, relevé le 27 septembre 2026
  }

  # Le volume partagé par les trois conteneurs : la base SQLite, les données du Java et ses
  # sauvegardes de 15 minutes (stockage-applicatif.tf). Chiffré en transit, monté par IAM.
  volume {
    name = "donnees"
    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.donnees.id
      transit_encryption = "ENABLED"
      authorization_config {
        access_point_id = aws_efs_access_point.donnees.id
        iam             = "ENABLED"
      }
    }
  }

  container_definitions = jsonencode([
    # 1. Le schéma : les migrations versionnées du Node (backend/src/db/migrations), puis arrêt.
    merge(local.conteneur_commun, {
      name             = "migrations"
      image            = "${var.registre_images}/migrations:${var.version_images}"
      essential        = false
      command          = ["node", "dist/db/migrateCli.js", "migrate"]
      environment      = [{ name = "NODE_ENV", value = "production" }, { name = "PAYROLL_DB", value = local.fichier_base }]
      logConfiguration = merge(local.journal, { options = merge(local.journal.options, { "awslogs-stream-prefix" = "migrations" }) })
    }),

    # 2. L'API : le Java, une fois le schéma à jour.
    merge(local.conteneur_commun, {
      name         = "api"
      image        = "${var.registre_images}/backend:${var.version_images}"
      essential    = true
      dependsOn    = [{ containerName = "migrations", condition = "SUCCESS" }]
      portMappings = [{ containerPort = local.port_api, protocol = "tcp" }]
      environment = [
        { name = "PORT", value = tostring(local.port_api) },
        { name = "DB_FICHIER", value = local.fichier_base },
        { name = "NEXU_DONNEES", value = "${local.racine_donnees}/java" },
        { name = "SAUVEGARDES_DOSSIER", value = "${local.racine_donnees}/sauvegardes" },
        # Les pièces jointes de la messagerie : dans la base, ou en fichiers sur ce même volume EFS.
        { name = "PIECES_JOINTES_STOCKAGE", value = var.pieces_jointes_stockage },
        { name = "TENANT_RESOLUTION", value = "sous_domaine" },
        # E-TN-19 : la région réelle des données. La console de sécurité la classe (Québec ou non) et un
        # organisme créé sans résidence déclarée la reçoit — sans elle, la création est refusée.
        { name = "REGION_DONNEES", value = var.region },
        { name = "HEBERGEUR", value = "AWS ${var.region}" },
        { name = "LOG_NIVEAU", value = var.environnement == "production" ? "info" : "debug" },
        # E-TN-8 : le navigateur ne parle qu'en HTTPS au répartiteur, mais c'est l'application qui pose
        # l'en-tête HSTS (FiltrePareFeu) et l'attribut Secure du cookie de session. Les deux sont
        # désactivés par défaut pour le développement local en HTTP : sans ces variables, le cookie de
        # session pouvait repartir en clair sur une simple requête http://.
        { name = "HTTPS", value = "true" },
        { name = "COOKIE_SECURE", value = "true" },
      ]
      secrets = [{ name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.session.arn }]
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:${local.port_api}/api/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 90
      }
      logConfiguration = merge(local.journal, { options = merge(local.journal.options, { "awslogs-stream-prefix" = "api" }) })
    }),

    # 3. L'interface : nginx sert les pages et relaie /api au conteneur voisin (même tâche, même hôte).
    merge(local.conteneur_commun, {
      name         = "web"
      image        = "${var.registre_images}/frontend:${var.version_images}"
      essential    = true
      dependsOn    = [{ containerName = "api", condition = "HEALTHY" }]
      portMappings = [{ containerPort = local.port_web, protocol = "tcp" }]
      mountPoints  = []
      # Une adresse plutôt qu'un nom : la configuration de nginx résout l'amont à chaque requête, et
      # il n'y a pas de DNS de conteneurs en mode awsvpc. 127.0.0.1 est le conteneur api.
      # API_PROXIES_CONFIANCE : nginx ne croit X-Forwarded-For que du répartiteur, dans le VPC (IN-05).
      environment = [{ name = "API_UPSTREAM", value = "127.0.0.1" }, { name = "API_PORT", value = tostring(local.port_api) },
      { name = "API_PROXIES_CONFIANCE", value = aws_vpc.principal.cidr_block }]
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:${local.port_web}/ || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
      logConfiguration = merge(local.journal, { options = merge(local.journal.options, { "awslogs-stream-prefix" = "web" }) })
    }),
  ])
}

locals {
  racine_donnees = "/donnees"
  fichier_base   = "/donnees/payroll.db"
  port_api       = 4000
  port_web       = 8080 # nginx sans privilège (IN-01)

  journal = {
    logDriver = "awslogs"
    options = {
      "awslogs-group"  = aws_cloudwatch_log_group.application.name
      "awslogs-region" = var.region
    }
  }

  # Ce que les trois conteneurs partagent : le volume, et les identifiants du registre privé s'il y en a.
  conteneur_commun = merge(
    { mountPoints = [{ sourceVolume = "donnees", containerPath = "/donnees", readOnly = false }] },
    var.secret_registre_arn == "" ? {} : { repositoryCredentials = { credentialsParameter = var.secret_registre_arn } },
  )
}

# Exposé à Internet par conception : c'est l'entrée publique de l'application, derrière TLS (critère 84) et le WAF.
#trivy:ignore:AVD-AWS-0053
resource "aws_lb" "principal" {
  name               = local.prefixe
  load_balancer_type = "application"
  subnets            = aws_subnet.publics[*].id
  security_groups    = [aws_security_group.repartiteur.id]

  drop_invalid_header_fields = true
  enable_deletion_protection = var.environnement == "production"
}

resource "aws_lb_target_group" "application" {
  name        = local.prefixe
  port        = local.port_web
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.principal.id

  health_check {
    path                = "/api/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
  }

  deregistration_delay = 30
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.principal.arn
  port              = 443
  protocol          = "HTTPS"
  # TLS 1.2 au minimum ; la politique 1.3 refuserait des navigateurs encore soutenus.
  ssl_policy      = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn = aws_acm_certificate_validation.principal.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.application.arn
  }
}

resource "aws_ecs_service" "application" {
  name            = local.prefixe
  cluster         = aws_ecs_cluster.principal.id
  task_definition = aws_ecs_task_definition.application.arn
  desired_count   = var.taches_application
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.prives[*].id
    security_groups  = [aws_security_group.application.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.application.arn
    container_name   = "web"
    container_port   = local.port_web
  }

  # Un déploiement qui échoue revient en arrière tout seul : à 3 h du matin, personne ne le fera.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # SQLite n'admet qu'un écrivain : l'ancienne tâche s'arrête avant que la nouvelle démarre. Deux
  # tâches ouvertes sur le même fichier, même quelques secondes, corrompraient la base.
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  depends_on = [aws_lb_listener.https, aws_efs_mount_target.donnees]

  lifecycle {
    precondition {
      condition     = var.taches_application == 1
      error_message = "La base SQLite n'admet qu'une tâche écrivaine : taches_application doit valoir 1. La disponibilité vient du remplacement automatique de la tâche sur le volume régional."
    }
  }
}
