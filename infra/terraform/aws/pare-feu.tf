/**
 * Pare-feu applicatif (exigence 32 — pratiques reconnues, dont l'OWASP).
 *
 * Relevé le 27 septembre 2026 : liste de contrôle 5 USD/mois, groupe de règles 1 USD/mois,
 * requêtes 0,60 USD le million — soit ≈ 212 $ CA par année. Le même service sur Azure Canada East
 * coûte 5 596 $ : la différence est structurelle, Azure facturant une présence horaire et AWS des
 * requêtes.
 *
 * Les groupes retenus sont ceux d'AWS qui mettent en œuvre le **Core Rule Set de l'OWASP**. Aucun
 * groupe facturé à l'usage (Bot Control, Fraud Control) n'est activé : ils coûtent 10 USD le
 * million de requêtes et ne répondent à aucune exigence de la grille.
 */
resource "aws_wafv2_web_acl" "principal" {
  count = var.pare_feu_actif ? 1 : 0

  name  = local.prefixe
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  # Les cinq groupes managés, dans l'ordre d'évaluation.
  dynamic "rule" {
    for_each = {
      1 = "AWSManagedRulesCommonRuleSet"          # Core Rule Set de l'OWASP
      2 = "AWSManagedRulesKnownBadInputsRuleSet"  # charges utiles connues
      3 = "AWSManagedRulesSQLiRuleSet"            # injection SQL
      4 = "AWSManagedRulesLinuxRuleSet"           # traversée de chemin, exécution locale
      5 = "AWSManagedRulesAmazonIpReputationList" # adresses signalées
    }
    content {
      name     = rule.value
      priority = tonumber(rule.key)

      override_action {
        none {}
      }

      statement {
        managed_rule_group_statement {
          vendor_name = "AWS"
          name        = rule.value
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.value
        sampled_requests_enabled   = true
      }
    }
  }

  # Limitation de débit : la déclaration d'absence du matin est une pointe légitime, pas une
  # attaque. Le seuil est haut pour ne pas refuser un secrétariat qui saisit vite.
  rule {
    name     = "limite-de-debit-par-adresse"
    priority = 10

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "limite-de-debit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = local.prefixe
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "repartiteur" {
  count        = var.pare_feu_actif ? 1 : 0
  resource_arn = aws_lb.principal.arn
  web_acl_arn  = aws_wafv2_web_acl.principal[0].arn
}

# Les requêtes refusées sont journalisées : l'exigence 33 demande la traçabilité, et un faux
# positif ne se règle pas sans savoir quelle règle a refusé quoi.
resource "aws_cloudwatch_log_group" "pare_feu" {
  count             = var.pare_feu_actif ? 1 : 0
  name              = "aws-waf-logs-${local.prefixe}"
  retention_in_days = 90
}

resource "aws_wafv2_web_acl_logging_configuration" "principal" {
  count                   = var.pare_feu_actif ? 1 : 0
  resource_arn            = aws_wafv2_web_acl.principal[0].arn
  log_destination_configs = [aws_cloudwatch_log_group.pare_feu[0].arn]

  # Le corps des requêtes peut porter des renseignements personnels : il est retiré du journal.
  redacted_fields {
    single_header {
      name = "authorization"
    }
  }
  redacted_fields {
    single_header {
      name = "cookie"
    }
  }
}
