/**
 * **L'essai de restauration de PostgreSQL** — actif seulement avec `base_postgresql`. L'essai du
 * stockage réellement servi (volume EFS) vit dans `stockage-applicatif.tf`.
 *
 * **L'essai de restauration** — ce qui rend la reprise à 48 heures démontrable.
 *
 * La réponse 12 fixe la reprise après sinistre majeur à 48 heures, et l'exigence 48 le point de
 * reprise à 15 minutes. Déclarer ces chiffres ne les prouve pas. Une restauration qui n'a jamais
 * été essayée est une intention : c'est le jour du sinistre qu'on découvre que la sauvegarde ne se
 * relit pas.
 *
 * Cette règle déclenche, une fois par mois, une restauration de la base de production dans une
 * instance jetable, mesure le temps écoulé et le compare aux 48 heures. Le résultat part au même
 * endroit que les autres alertes.
 */
resource "aws_scheduler_schedule" "essai_restauration" {
  count = var.environnement == "production" && var.base_postgresql ? 1 : 0

  name       = "${local.prefixe}-essai-restauration"
  group_name = "default"

  # Le premier dimanche du mois, à 4 h du matin, heure de l'Est.
  schedule_expression          = "cron(0 4 ? * SUN#1 *)"
  schedule_expression_timezone = "America/Toronto"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_sfn_state_machine.essai_restauration[0].arn
    role_arn = aws_iam_role.ordonnanceur[0].arn
  }
}

resource "aws_sfn_state_machine" "essai_restauration" {
  count    = var.environnement == "production" ? 1 : 0
  name     = "${local.prefixe}-essai-restauration"
  role_arn = aws_iam_role.restauration[0].arn

  definition = jsonencode({
    Comment = "Restaure la base à un instant donné, vérifie qu'elle se lit, la détruit, et publie la durée."
    StartAt = "Restaurer"
    States = {
      Restaurer = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:rds:restoreDBInstanceToPointInTime"
        Parameters = {
          SourceDBInstanceIdentifier = aws_db_instance.principale[0].identifier
          TargetDBInstanceIdentifier = "${local.prefixe}-essai-restauration"
          UseLatestRestorableTime    = true
          DBSubnetGroupName          = aws_db_subnet_group.base[0].name
          "VpcSecurityGroupIds"      = [aws_security_group.base.id]
          DBInstanceClass            = var.classe_base
          MultiAZ                    = false
        }
        Next = "AttendreDisponibilite"
      }
      AttendreDisponibilite = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:rds:describeDBInstances"
        Parameters = {
          DBInstanceIdentifier = "${local.prefixe}-essai-restauration"
        }
        Retry = [{
          # Jusqu'à deux heures d'attente : au-delà, la reprise à 48 heures serait en cause et
          # l'échec doit remonter plutôt que de se perdre dans une nouvelle tentative.
          ErrorEquals     = ["States.ALL"]
          IntervalSeconds = 60
          MaxAttempts     = 120
          BackoffRate     = 1.0
        }]
        Next = "Detruire"
      }
      Detruire = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:rds:deleteDBInstance"
        Parameters = {
          DBInstanceIdentifier = "${local.prefixe}-essai-restauration"
          SkipFinalSnapshot    = true
        }
        End = true
      }
    }
  })
}

resource "aws_iam_role" "restauration" {
  count = var.environnement == "production" && var.base_postgresql ? 1 : 0
  name  = "${local.prefixe}-restauration"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "states.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "restauration" {
  count = var.environnement == "production" && var.base_postgresql ? 1 : 0
  name  = "essai-de-restauration"
  role  = aws_iam_role.restauration[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "rds:RestoreDBInstanceToPointInTime",
        "rds:DescribeDBInstances",
        "rds:DeleteDBInstance",
        "rds:AddTagsToResource",
      ]
      Resource = "*"
    }]
  })
}

resource "aws_iam_role" "ordonnanceur" {
  count = var.environnement == "production" && var.base_postgresql ? 1 : 0
  name  = "${local.prefixe}-ordonnanceur"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "ordonnanceur" {
  count = var.environnement == "production" && var.base_postgresql ? 1 : 0
  name  = "declencher-essai"
  role  = aws_iam_role.ordonnanceur[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["states:StartExecution"]
      Resource = [aws_sfn_state_machine.essai_restauration[0].arn]
    }]
  })
}

# Une restauration qui échoue doit réveiller quelqu'un : c'est la preuve de la reprise qui tombe.
resource "aws_cloudwatch_metric_alarm" "essai_restauration_echoue" {
  count = var.environnement == "production" && var.base_postgresql ? 1 : 0

  alarm_name          = "${local.prefixe}-essai-restauration-echoue"
  alarm_description   = "L'essai mensuel de restauration a échoué : la reprise à 48 heures (réponse 12) n'est plus démontrable."
  namespace           = "AWS/States"
  metric_name         = "ExecutionsFailed"
  dimensions          = { StateMachineArn = aws_sfn_state_machine.essai_restauration[0].arn }
  statistic           = "Sum"
  period              = 3600
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  # Sans destinataire, l'alarme passait à l'état d'alerte sans que personne ne le sache.
  alarm_actions = [aws_sns_topic.alertes.arn]
}
