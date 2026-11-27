/**
 * Réseau : trois zones de disponibilité, toutes au Québec.
 *
 * C'est ce que ca-central-1 permet et que Canada East d'Azure ne permet pas. Les sous-réseaux
 * privés portent la base de données et les tâches ; aucun d'eux n'a de route vers l'Internet
 * entrant. La réponse 11 de l'addenda est satisfaite sans autorisation écrite à demander.
 */
data "aws_availability_zones" "quebec" {
  state = "available"
}

locals {
  # Trois zones, pas deux : la troisième ne coûte rien en soi et évite d'avoir à choisir laquelle
  # sacrifier le jour où l'une est en maintenance.
  zones   = slice(data.aws_availability_zones.quebec.names, 0, 3)
  prefixe = "${var.client}-${var.environnement}"
}

resource "aws_vpc" "principal" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = { Name = local.prefixe }
}

resource "aws_subnet" "publics" {
  count                   = 3
  vpc_id                  = aws_vpc.principal.id
  cidr_block              = cidrsubnet(aws_vpc.principal.cidr_block, 8, count.index)
  availability_zone       = local.zones[count.index]
  map_public_ip_on_launch = false
  tags                    = { Name = "${local.prefixe}-public-${count.index + 1}" }
}

resource "aws_subnet" "prives" {
  count             = 3
  vpc_id            = aws_vpc.principal.id
  cidr_block        = cidrsubnet(aws_vpc.principal.cidr_block, 8, count.index + 10)
  availability_zone = local.zones[count.index]
  tags              = { Name = "${local.prefixe}-prive-${count.index + 1}" }
}

resource "aws_internet_gateway" "entree" {
  vpc_id = aws_vpc.principal.id
  tags   = { Name = local.prefixe }
}

resource "aws_route_table" "publique" {
  vpc_id = aws_vpc.principal.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.entree.id
  }
  tags = { Name = "${local.prefixe}-publique" }
}

resource "aws_route_table_association" "publics" {
  count          = 3
  subnet_id      = aws_subnet.publics[count.index].id
  route_table_id = aws_route_table.publique.id
}

/**
 * Sortie vers l'Internet pour les tâches : une passerelle NAT par zone en production (`nat_par_zone`), une seule
 * ailleurs. Avec une seule, la perte de sa zone coupait les textos, les appels et le courriel même quand la tâche
 * repartait dans une autre zone (E-TN-9) ; en essai, l'économie l'emporte.
 */
locals {
  nats = var.nat_par_zone ? 3 : 1
}

resource "aws_eip" "nat" {
  count  = local.nats
  domain = "vpc"
  tags   = { Name = "${local.prefixe}-nat-${count.index}" }
}

resource "aws_nat_gateway" "sortie" {
  count         = local.nats
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.publics[count.index].id
  depends_on    = [aws_internet_gateway.entree]
  tags          = { Name = "${local.prefixe}-${count.index}" }
}

# Une table par zone privée : chacune sort par la passerelle de sa zone (ou par l'unique passerelle).
resource "aws_route_table" "privee" {
  count  = 3
  vpc_id = aws_vpc.principal.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.sortie[min(count.index, local.nats - 1)].id
  }
  tags = { Name = "${local.prefixe}-privee-${count.index}" }
}

resource "aws_route_table_association" "prives" {
  count          = 3
  subnet_id      = aws_subnet.prives[count.index].id
  route_table_id = aws_route_table.privee[count.index].id
}

# ── Groupes de sécurité : chaque flux est nommé ────────────────────────────

resource "aws_security_group" "repartiteur" {
  name        = "${local.prefixe}-repartiteur"
  description = "Entree HTTPS publique vers le repartiteur"
  vpc_id      = aws_vpc.principal.id

  ingress {
    description = "HTTPS depuis Internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description     = "Vers les taches applicatives (interface et relais API)"
    from_port       = local.port_web
    to_port         = local.port_web
    protocol        = "tcp"
    security_groups = [aws_security_group.application.id]
  }
}

# Sortie limitée au port 443 : les fournisseurs (GRICS, textos, courriel, Entra ID) n'ont pas d'adresses fixes publiées,
# une liste d'adresses se périmerait sans prévenir. La sortie passe par la passerelle NAT, journalisée.
#trivy:ignore:aws-vpc-no-public-egress-sgr
resource "aws_security_group" "application" {
  name        = "${local.prefixe}-application"
  description = "Taches applicatives"
  vpc_id      = aws_vpc.principal.id

  egress {
    description = "Sortie HTTPS vers GRICS, operateur de textos et service de courriel"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Le volume de donnees (stockage-applicatif.tf) : NFS chiffre, vers les points de montage du VPC.
  egress {
    description = "Vers le volume de donnees (NFS)"
    from_port   = 2049
    to_port     = 2049
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.principal.cidr_block]
  }
}

resource "aws_security_group_rule" "application_depuis_repartiteur" {
  type                     = "ingress"
  from_port                = local.port_web
  to_port                  = local.port_web
  protocol                 = "tcp"
  security_group_id        = aws_security_group.application.id
  source_security_group_id = aws_security_group.repartiteur.id
  description              = "Uniquement depuis le repartiteur"
}

resource "aws_security_group" "base" {
  name        = "${local.prefixe}-base"
  description = "PostgreSQL joignable uniquement depuis les taches"
  vpc_id      = aws_vpc.principal.id

  ingress {
    description     = "PostgreSQL depuis les taches applicatives"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.application.id]
  }
}

resource "aws_security_group_rule" "application_vers_base" {
  type                     = "egress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.application.id
  source_security_group_id = aws_security_group.base.id
  description              = "Vers PostgreSQL"
}
