/**
 * **Le déploiement par la CI, sans clé d'accès** — AO 26-015-DO E-TN-3 (correctifs déployés
 * rapidement après validation) et E-TN-16 (retour arrière).
 *
 * Le travail `deploy-aws` de .github/workflows/ci-cd.yml assume ce rôle par la fédération OIDC de
 * GitHub : aucune clé AWS n'est stockée dans le dépôt ni dans ses secrets. Seules les poussées
 * de la branche principale de ce dépôt, dans l'environnement « production », peuvent l'assumer.
 * Ses droits se limitent à ce qu'un déploiement fait : lire le service, inscrire une révision de
 * tâche, mettre à jour le service, et transmettre les deux rôles de la tâche.
 */

# Un fournisseur OIDC par compte AWS : un compte qui l'a déjà le déclare avec fournisseur_oidc_github_existant.
resource "aws_iam_openid_connect_provider" "github" {
  count           = var.depot_github != "" && var.fournisseur_oidc_github_existant == "" ? 1 : 0
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

locals {
  fournisseur_oidc = var.fournisseur_oidc_github_existant != "" ? var.fournisseur_oidc_github_existant : try(aws_iam_openid_connect_provider.github[0].arn, "")
}

resource "aws_iam_role" "deploiement_ci" {
  count = var.depot_github != "" ? 1 : 0
  name  = "${local.prefixe}-deploiement-ci"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.fournisseur_oidc }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        # L'environnement GitHub « production » porte les approbations : une branche de fonctionnalité ne déploie pas.
        StringLike = { "token.actions.githubusercontent.com:sub" = "repo:${var.depot_github}:environment:production" }
      }
    }]
  })
}

resource "aws_iam_role_policy" "deploiement_ci" {
  count = var.depot_github != "" ? 1 : 0
  name  = "deploiement-ecs"
  role  = aws_iam_role.deploiement_ci[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecs:DescribeServices", "ecs:UpdateService"]
        Resource = aws_ecs_service.application.id
      },
      {
        # Les révisions de tâche ne se désignent pas avant d'exister : l'action ne se borne pas par ressource.
        Effect   = "Allow"
        Action   = ["ecs:RegisterTaskDefinition", "ecs:DescribeTaskDefinition"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = [aws_iam_role.execution.arn, aws_iam_role.tache.arn]
      },
    ]
  })
}
