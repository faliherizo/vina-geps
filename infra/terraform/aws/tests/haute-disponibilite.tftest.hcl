# E-TN-9 : la base est sur le volume EFS, un seul écrivain, et la sortie vers l'Internet survit à la perte d'une zone
# en production. Fournisseurs simulés : l'essai ne touche pas AWS.
mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_cloudwatch_log_group" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_kms_key" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_wafv2_web_acl" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_efs_file_system" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_efs_access_point" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_backup_vault" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_acm_certificate" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_ecs_cluster" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_db_instance" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_iam_policy" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_resource "aws_s3_bucket" {
    defaults = { arn = "arn:aws:iam::123456789012:role/essai" }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["ca-central-1a", "ca-central-1b", "ca-central-1d"] }
  }
}
mock_provider "aws" {
  alias = "releve"
}
mock_provider "random" {}

variables {
  client             = "us"
  organisme          = "Urgences-santé"
  appel_offres       = "26-015-DO"
  environnement      = "essai"
  registre_images    = "ghcr.io/exemple/depot"
  depot_github       = "exemple/depot"
  domaine            = "geps.exemple.ca"
  taches_application = 1
  processeur_tache   = 512
  memoire_tache      = 1024
  classe_base        = "db.t4g.micro"
}

run "la_base_est_sur_le_volume_et_le_java_seul_la_cree" {
  # apply sur fournisseurs simulés : la définition des conteneurs n'est connue qu'une fois les ressources « créées ».
  command = apply

  assert {
    condition     = strcontains(aws_ecs_task_definition.application.container_definitions, "jdbc:sqlite:/donnees/urgences-sante.db")
    error_message = "Le Java lit DB_URL : la base doit être sur le volume EFS monté en /donnees."
  }
  assert {
    condition     = !strcontains(aws_ecs_task_definition.application.container_definitions, "\"migrations\"")
    error_message = "Plus de conteneur de migrations Node : Liquibase bâtit le schéma au démarrage du Java."
  }
}

run "une_seule_sortie_en_essai" {
  command = plan

  assert {
    condition     = length(aws_nat_gateway.sortie) == 1 && length(aws_route_table.privee) == 3
    error_message = "En essai : une passerelle, trois tables privées qui la partagent."
  }
}

run "une_sortie_par_zone_en_production" {
  command = apply
  variables {
    nat_par_zone = true
  }

  assert {
    condition     = length(aws_nat_gateway.sortie) == 3 && length(distinct([for t in aws_route_table.privee : one(t.route).nat_gateway_id])) == 3
    error_message = "Chaque zone privée sort par la passerelle de sa zone."
  }
}

run "deux_ecrivains_sont_refuses" {
  command = plan
  variables {
    taches_application = 2
  }
  expect_failures = [var.taches_application]
}
