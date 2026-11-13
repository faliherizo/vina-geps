/**
 * **Détection d'intrusion et déni de service** — AO 26-015-DO E-TN-24 (« la solution fournit une
 * détection d'intrusion ») et E-TN-23 (« une protection contre le déni de service »).
 *
 * - **Intrusion** : GuardDuty analyse les journaux de flux du réseau, les requêtes DNS et les appels
 *   à l'API d'AWS (reconnaissance, identifiants dérobés, minage, exfiltration). Une découverte de
 *   gravité élevée part au sujet d'alertes.
 * - **Déni de service** : AWS Shield Standard protège d'office le répartiteur aux couches 3 et 4,
 *   sans coût ni configuration. La couche 7 l'est par la limitation de débit du pare-feu
 *   applicatif (pare-feu.tf) ; un pic de requêtes refusées déclenche ici une alerte. Shield
 *   Advanced (équipe de réponse d'AWS, protection des coûts) reste un choix : 3 000 USD par mois et
 *   un engagement d'un an, relevés sur la page de tarifs d'AWS — il ne s'active pas par défaut.
 */

# Un seul sujet pour toutes les alertes d'exploitation : un courriel déclaré les reçoit toutes.
resource "aws_sns_topic" "alertes" {
  name              = "${local.prefixe}-alertes"
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sns_topic_subscription" "alertes_courriel" {
  count     = var.courriel_alertes == "" ? 0 : 1
  topic_arn = aws_sns_topic.alertes.arn
  protocol  = "email"
  endpoint  = var.courriel_alertes
}

# EventBridge publie au sujet ; sans cette politique, la règle se déclenche et rien n'arrive.
resource "aws_sns_topic_policy" "alertes" {
  arn = aws_sns_topic.alertes.arn
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = ["events.amazonaws.com", "cloudwatch.amazonaws.com"] }
      Action    = "sns:Publish"
      Resource  = aws_sns_topic.alertes.arn
    }]
  })
}

# Un détecteur par compte et par région : la pile qui partage un compte déjà surveillé le désactive.
resource "aws_guardduty_detector" "principal" {
  count                        = var.detection_intrusion ? 1 : 0
  enable                       = true
  finding_publishing_frequency = "FIFTEEN_MINUTES"
}

resource "aws_cloudwatch_event_rule" "intrusion" {
  count       = var.detection_intrusion ? 1 : 0
  name        = "${local.prefixe}-intrusion"
  description = "Découverte GuardDuty de gravité élevée (7 et plus) : à traiter comme un Événement de Sécurité (Contrat 17.07.05)."
  event_pattern = jsonencode({
    source        = ["aws.guardduty"]
    "detail-type" = ["GuardDuty Finding"]
    detail        = { severity = [{ numeric = [">=", 7] }] }
  })
}

resource "aws_cloudwatch_event_target" "intrusion" {
  count = var.detection_intrusion ? 1 : 0
  rule  = aws_cloudwatch_event_rule.intrusion[0].name
  arn   = aws_sns_topic.alertes.arn
}

# Couche 7 : la limitation de débit refuse ; une rafale de refus dit qu'une attaque est en cours.
resource "aws_cloudwatch_metric_alarm" "deni_de_service" {
  count = var.pare_feu_actif ? 1 : 0

  alarm_name          = "${local.prefixe}-deni-de-service"
  alarm_description   = "Plus de ${var.seuil_alerte_refus} requêtes refusées par la limitation de débit en 5 minutes : déni de service probable."
  namespace           = "AWS/WAFV2"
  metric_name         = "BlockedRequests"
  dimensions          = { WebACL = local.prefixe, Region = var.region, Rule = "limite-de-debit" }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = var.seuil_alerte_refus
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alertes.arn]
}

resource "aws_shield_protection" "repartiteur" {
  count        = var.shield_avance ? 1 : 0
  name         = "${local.prefixe}-repartiteur"
  resource_arn = aws_lb.principal.arn
}
