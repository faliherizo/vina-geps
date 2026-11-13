output "domaine_repartiteur" {
  value       = aws_lb.principal.dns_name
  description = "Nom d'hôte du répartiteur, à faire pointer par un CNAME depuis var.domaine."
}

output "adresse_base" {
  value       = var.base_postgresql ? aws_db_instance.principale[0].address : null
  description = "Hôte PostgreSQL — joignable uniquement depuis les sous-réseaux privés."
  sensitive   = true
}

output "secret_base_arn" {
  value       = var.base_postgresql ? aws_secretsmanager_secret.base[0].arn : null
  description = "Secret contenant les identifiants PostgreSQL. Aucun mot de passe n'est en clair dans l'état Terraform de l'application."
}

output "zones_utilisees" {
  value       = local.zones
  description = "Les trois zones de disponibilité, toutes au Québec — ce que Canada East d'Azure ne permet pas."
}

output "residence_des_donnees" {
  value = {
    region      = var.region
    emplacement = "Montréal, Québec, Canada"
    zones       = length(local.zones)
    sauvegardes = var.sauvegardes_hors_region ? (
      "coffre AWS Backup à Montréal (${var.retention_quotidienne_jours} j quotidiennes, ${var.retention_mensuelle_jours} j mensuelles) ; copie dans ${var.region_releve} (hors Québec), ${var.retention_hors_region_jours} jours, autorisation : ${var.autorisation_hors_quebec}"
    ) : "coffre AWS Backup à Montréal : ${var.retention_quotidienne_jours} jours (quotidiennes), ${var.retention_mensuelle_jours} jours (mensuelles), aucune copie hors région"
    replication_externe = var.sauvegardes_hors_region ? "copies AWS Backup vers ${var.region_releve}" : "aucune"
  }
  description = "Ce que l'annexe 7.00 « Infrastructures informatiques » demande de déclarer, produit par le code plutôt que recopié à la main."
}

# ── Liaison privée (liaison-privee.tf, E-TN-8) ──────────────────────────────

output "vpn_tunnels" {
  value = var.vpn_site_a_site ? {
    connexion = aws_vpn_connection.organisme[0].id
    tunnel_1  = aws_vpn_connection.organisme[0].tunnel1_address
    tunnel_2  = aws_vpn_connection.organisme[0].tunnel2_address
    routes    = sort(var.vpn_cidr_client)
  } : null
  description = "Adresses publiques des deux tunnels AWS à configurer sur l'équipement de l'organisme. Aucun secret."
}

# Les clés pré-partagées et la configuration complète des tunnels sont des secrets : elles se lisent
# par `terraform output -json vpn_configuration_organisme` et se remettent à l'organisme par un canal
# chiffré, jamais par courriel en clair.
output "vpn_configuration_organisme" {
  value = var.vpn_site_a_site ? {
    cle_tunnel_1  = aws_vpn_connection.organisme[0].tunnel1_preshared_key
    cle_tunnel_2  = aws_vpn_connection.organisme[0].tunnel2_preshared_key
    configuration = aws_vpn_connection.organisme[0].customer_gateway_configuration
  } : null
  description = "Clés pré-partagées et configuration XML des tunnels pour l'équipement de l'organisme."
  sensitive   = true
}

# À inscrire comme variable de dépôt AWS_ROLE_DEPLOIEMENT, avec ECS_CLUSTER et ECS_SERVICE.
output "deploiement_ci" {
  value = var.depot_github == "" ? null : {
    role    = aws_iam_role.deploiement_ci[0].arn
    cluster = aws_ecs_cluster.principal.name
    service = aws_ecs_service.application.name
  }
}
