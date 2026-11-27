/**
 * **Liaison privée avec l'organisme : VPN site-à-site IPsec** — AO 26-015-DO, E-TN-8 (« la solution
 * garantit la confidentialité, liaison et disponibilité pour chaque type de transmission (exemples :
 * HTTPS sur Internet, IPSec VPN, MPLS, extension LAN) »).
 *
 * Les usagers passent par HTTPS sur Internet (calcul.tf, écouteur TLS 1.2 et plus) : c'est le cas
 * général et il ne demande rien à l'organisme. Le VPN sert un autre cas, celui où l'organisme veut
 * que les échanges entre nos tâches et **ses** systèmes (bus d'intégration, paie, dépôt de
 * fichiers) ne traversent pas l'Internet public. Il ne s'active que si l'organisme le demande et
 * fournit l'adresse de son équipement : sans pair en face, deux tunnels coûteraient ≈ 0,05 USD de
 * l'heure chacun pour ne transporter rien.
 *
 * Ce que ce fichier **ne fait pas**, et qu'il faut dire plutôt que laisser supposer :
 * - **le sens organisme → application** : l'application n'est exposée que par le répartiteur public.
 *   Joindre l'API par le tunnel exigerait un répartiteur interne, absent ici ;
 * - **MPLS / Direct Connect** : une liaison dédiée se commande auprès d'un opérateur et d'AWS, elle ne
 *   se décrit pas seulement en Terraform. Le VPN est le moyen privé que le code sait livrer seul.
 */

variable "vpn_site_a_site" {
  type        = bool
  default     = false
  description = "Monte un VPN site-à-site IPsec (deux tunnels) entre le VPC et le réseau de l'organisme (E-TN-8). Faux par défaut : il faut l'adresse publique de l'équipement de l'organisme, et les tunnels se paient à l'heure."

  validation {
    # Cohérence vérifiée au `plan` : un VPN demandé sans pair ni réseau à joindre échouerait à
    # l'application, après la création de la passerelle.
    condition     = !var.vpn_site_a_site || (var.vpn_ip_publique_client != "" && length(var.vpn_cidr_client) > 0)
    error_message = "Un VPN site-à-site exige vpn_ip_publique_client (l'équipement de l'organisme) et au moins un réseau dans vpn_cidr_client."
  }
}

variable "vpn_ip_publique_client" {
  type        = string
  default     = ""
  description = "Adresse IPv4 publique et fixe de l'équipement VPN de l'organisme (pare-feu, routeur)."

  validation {
    condition     = var.vpn_ip_publique_client == "" || can(cidrhost("${var.vpn_ip_publique_client}/32", 0))
    error_message = "vpn_ip_publique_client doit être une adresse IPv4 (par exemple 203.0.113.10)."
  }

  validation {
    # AWS refuse une passerelle client sur une adresse privée, mais seulement à l'application : le
    # refus se fait ici au `plan`.
    condition = var.vpn_ip_publique_client == "" || !anytrue([
      for prive in ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "100.64.0.0/10", "127.0.0.0/8"] :
      cidrhost("${var.vpn_ip_publique_client}/${split("/", prive)[1]}", 0) == cidrhost(prive, 0)
    ])
    error_message = "vpn_ip_publique_client est une adresse privée ou réservée : il faut l'adresse publique de l'équipement de l'organisme."
  }
}

variable "vpn_cidr_client" {
  type        = list(string)
  default     = []
  description = "Réseaux de l'organisme joignables par le tunnel (routes statiques), par exemple [\"172.20.0.0/16\"]."

  validation {
    condition     = alltrue([for c in var.vpn_cidr_client : can(cidrnetmask(c))])
    error_message = "Chaque élément de vpn_cidr_client doit être un bloc IPv4 en notation CIDR (par exemple 172.20.0.0/16)."
  }

  validation {
    # Un réseau de l'organisme qui chevauche le VPC (10.42.0.0/16, reseau.tf) rendrait le routage
    # ambigu : les paquets destinés à l'organisme resteraient dans le VPC. Deux blocs se chevauchent
    # si leurs adresses coïncident une fois tronquées au plus court de leurs deux préfixes.
    condition = alltrue([
      for c in var.vpn_cidr_client :
      cidrhost("${split("/", c)[0]}/${min(16, tonumber(split("/", c)[1]))}", 0) != cidrhost("10.42.0.0/${min(16, tonumber(split("/", c)[1]))}", 0)
    ])
    error_message = "Un réseau de vpn_cidr_client chevauche celui du VPC (10.42.0.0/16) : le routage vers l'organisme serait ambigu."
  }
}

resource "aws_vpn_gateway" "organisme" {
  count  = var.vpn_site_a_site ? 1 : 0
  vpc_id = aws_vpc.principal.id
  tags   = { Name = "${local.prefixe}-vpn" }
}

resource "aws_customer_gateway" "organisme" {
  count      = var.vpn_site_a_site ? 1 : 0
  ip_address = var.vpn_ip_publique_client
  type       = "ipsec.1"
  # Routage statique : le numéro de système autonome est exigé par l'API mais n'est pas utilisé.
  # 65000 est réservé à l'usage privé (RFC 6996).
  bgp_asn = 65000
  tags    = { Name = "${local.prefixe}-organisme" }
}

# Les journaux des tunnels disent quand et pourquoi une négociation IKE a échoué : sans eux, une
# coupure côté organisme se diagnostique au téléphone.
resource "aws_cloudwatch_log_group" "vpn" {
  count             = var.vpn_site_a_site ? 1 : 0
  name              = "/vpn/${local.prefixe}"
  retention_in_days = 90
}

/**
 * Les deux tunnels aboutissent dans deux zones distinctes d'AWS : la perte de l'une ne coupe pas la
 * liaison, pourvu que l'équipement de l'organisme monte les deux (la « disponibilité » d'E-TN-8).
 *
 * Les algorithmes sont restreints à ceux que le Centre canadien pour la cybersécurité recommande
 * (ITSP.40.062) : IKEv2 seulement, AES-256 GCM, SHA-2, groupes Diffie-Hellman 19 à 21 (courbes
 * elliptiques) et 14 à 16 (MODP ≥ 2048 bits). Laisser la liste d'AWS par défaut admettrait IKEv1,
 * SHA-1 et le groupe 2.
 *
 * Les clés pré-partagées sont tirées par AWS. Elles sont dans l'état Terraform (seau chiffré au
 * Québec, versions.tf) et ne sortent que par une sortie marquée sensible.
 */
resource "aws_vpn_connection" "organisme" {
  count               = var.vpn_site_a_site ? 1 : 0
  vpn_gateway_id      = aws_vpn_gateway.organisme[0].id
  customer_gateway_id = aws_customer_gateway.organisme[0].id
  type                = "ipsec.1"
  static_routes_only  = true

  tunnel1_ike_versions                 = ["ikev2"]
  tunnel1_phase1_encryption_algorithms = ["AES256-GCM-16"]
  tunnel1_phase1_integrity_algorithms  = ["SHA2-384", "SHA2-512"]
  tunnel1_phase1_dh_group_numbers      = [19, 20, 21, 14, 15, 16]
  tunnel1_phase2_encryption_algorithms = ["AES256-GCM-16"]
  tunnel1_phase2_integrity_algorithms  = ["SHA2-384", "SHA2-512"]
  tunnel1_phase2_dh_group_numbers      = [19, 20, 21, 14, 15, 16]
  tunnel1_log_options {
    cloudwatch_log_options {
      log_enabled       = true
      log_group_arn     = aws_cloudwatch_log_group.vpn[0].arn
      log_output_format = "json"
    }
  }

  tunnel2_ike_versions                 = ["ikev2"]
  tunnel2_phase1_encryption_algorithms = ["AES256-GCM-16"]
  tunnel2_phase1_integrity_algorithms  = ["SHA2-384", "SHA2-512"]
  tunnel2_phase1_dh_group_numbers      = [19, 20, 21, 14, 15, 16]
  tunnel2_phase2_encryption_algorithms = ["AES256-GCM-16"]
  tunnel2_phase2_integrity_algorithms  = ["SHA2-384", "SHA2-512"]
  tunnel2_phase2_dh_group_numbers      = [19, 20, 21, 14, 15, 16]
  tunnel2_log_options {
    cloudwatch_log_options {
      log_enabled       = true
      log_group_arn     = aws_cloudwatch_log_group.vpn[0].arn
      log_output_format = "json"
    }
  }

  tags = { Name = "${local.prefixe}-organisme" }
}

resource "aws_vpn_connection_route" "organisme" {
  for_each               = var.vpn_site_a_site ? toset(var.vpn_cidr_client) : toset([])
  destination_cidr_block = each.value
  vpn_connection_id      = aws_vpn_connection.organisme[0].id
}

# Seules les tables privées (une par zone) apprennent les routes de l'organisme : ce sont les tâches, dans les
# sous-réseaux privés, qui lui parlent. La table publique (répartiteur, NAT) n'a rien à y faire.
resource "aws_vpn_gateway_route_propagation" "privee" {
  count          = var.vpn_site_a_site ? 3 : 0
  vpn_gateway_id = aws_vpn_gateway.organisme[0].id
  route_table_id = aws_route_table.privee[count.index].id
}

# La redondance des deux tunnels n'a de valeur que si sa perte se voit : un tunnel tombé laisse la
# liaison debout, et personne ne s'en aperçoit avant la chute du second.
resource "aws_cloudwatch_metric_alarm" "vpn_redondance_perdue" {
  count = var.vpn_site_a_site ? 1 : 0

  alarm_name          = "${local.prefixe}-vpn-redondance-perdue"
  alarm_description   = "Au moins un des deux tunnels IPsec vers l'organisme est tombé depuis 15 minutes : la liaison privée n'a plus de secours (E-TN-8)."
  namespace           = "AWS/VPN"
  metric_name         = "TunnelState"
  dimensions          = { VpnId = aws_vpn_connection.organisme[0].id }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alertes.arn]
  ok_actions          = [aws_sns_topic.alertes.arn]
}
