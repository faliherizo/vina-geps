/**
 * Infrastructure AWS — appel d'offres CSSDC 2026-PLAT-GA.
 *
 * Région imposée : ca-central-1 (Montréal, Québec), la seule région canadienne d'AWS située au
 * Québec et dotée de trois zones de disponibilité. La réponse 11 de l'addenda n° 2 exige que la
 * production, la redondance, la relève et les sauvegardes soient au Québec : trois zones dans la
 * province permettent d'y satisfaire sans demander l'autorisation écrite prévue pour un
 * hébergement ailleurs au Canada.
 */
terraform {
  required_version = ">= 1.9"

  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.70" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }

  # L'état vit dans un seau chiffré au Québec lui aussi : il contient des identifiants de
  # ressources et des empreintes, pas des renseignements personnels, mais le faire sortir de la
  # province obligerait à l'expliquer.
  backend "s3" {
    bucket = "nexu-tfstate-ca-central-1"
    # Clé du CSSDC par défaut ; un autre client passe la sienne à l'initialisation :
    #   terraform init -backend-config="key=urgences-sante/26-015-do/<environnement>.tfstate"
    key          = "cssdc/2026-plat-ga/terraform.tfstate"
    region       = "ca-central-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Organisme   = var.organisme
      AppelOffres = var.appel_offres
      Environment = var.environnement
      Residence   = "Quebec"
      GereePar    = "terraform"
    }
  }
}
