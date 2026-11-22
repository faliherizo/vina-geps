# IN-01 (tests d'intrusion du 22 novembre 2026) : nginx tourne sans privilège et écoute sur 8080. Le répartiteur, son
# groupe de sécurité, celui des tâches et le service ECS doivent viser ce port, sans quoi le site ne répond plus.
# Fournisseurs simulés : l'essai ne touche pas AWS.
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

run "le_port_de_l_interface_est_le_meme_partout" {
  command = plan

  assert {
    condition     = aws_lb_target_group.application.port == 8080
    error_message = "Le groupe cible vise le port non privilégié de nginx."
  }
  assert {
    condition     = one([for c in aws_ecs_service.application.load_balancer : c.container_port]) == 8080
    error_message = "Le service ECS relie le répartiteur au port 8080 du conteneur web."
  }
  assert {
    condition     = aws_security_group_rule.application_depuis_repartiteur.from_port == 8080 && aws_security_group_rule.application_depuis_repartiteur.to_port == 8080
    error_message = "Les tâches n'acceptent du répartiteur que le port 8080."
  }
  assert {
    condition     = one([for r in aws_security_group.repartiteur.egress : r.from_port]) == 8080
    error_message = "Le répartiteur ne sort que vers le port 8080 des tâches."
  }
}
