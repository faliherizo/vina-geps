# Critère 84 (FX-AO-20260810) : aucun certificat générique. Les deux variables de noms d'hôte refusent
# l'astérisque au `plan`, avant toute ressource. Fournisseurs simulés : l'essai ne touche pas AWS.
mock_provider "aws" {
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

run "un_domaine_generique_est_refuse" {
  command = plan
  variables {
    domaine = "*.exemple.ca"
  }
  expect_failures = [var.domaine]
}

run "un_hote_supplementaire_generique_est_refuse" {
  command = plan
  variables {
    hotes_supplementaires = ["stj.geps.exemple.ca", "*.geps.exemple.ca"]
  }
  expect_failures = [var.hotes_supplementaires]
}

run "le_certificat_porte_les_seuls_noms_servis" {
  command = plan
  variables {
    hotes_supplementaires = ["stj.geps.exemple.ca"]
  }
  assert {
    condition     = aws_acm_certificate.principal.domain_name == "geps.exemple.ca"
    error_message = "Le certificat doit nommer le domaine servi."
  }
  assert {
    condition     = toset(aws_acm_certificate.principal.subject_alternative_names) == toset(["stj.geps.exemple.ca"])
    error_message = "Le certificat ne porte que les hôtes nommés."
  }
  assert {
    condition     = aws_acm_certificate.principal.validation_method == "DNS"
    error_message = "Validation par DNS, renouvelée par ACM."
  }
}
