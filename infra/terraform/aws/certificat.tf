/**
 * Certificat TLS **nominatif** (FX-AO-20260810, critère 84 : « aucun certificat de type wildcard ;
 * tout certificat délivré par une autorité reconnue »).
 *
 * Le certificat est demandé ici, à ACM (autorité reconnue), pour les seuls noms d'hôte servis :
 * `var.domaine`, plus les hôtes supplémentaires nommés un à un (un par organisme quand
 * l'établissement se résout par sous-domaine). Les deux variables refusent l'astérisque au `plan` :
 * un nom générique ne peut pas entrer, même par erreur de saisie.
 *
 * Validation par DNS : au premier `apply`, ACM publie les enregistrements CNAME à créer
 * (sortie `certificat_validation_dns`) ; l'`apply` attend qu'ils répondent avant de brancher le
 * certificat sur l'écouteur HTTPS. ACM renouvelle ensuite le certificat sans intervention.
 */
resource "aws_acm_certificate" "principal" {
  domain_name               = var.domaine
  subject_alternative_names = var.hotes_supplementaires
  validation_method         = "DNS"

  # Remplacer un certificat (un hôte ajouté) sans couper l'écouteur : le nouveau d'abord.
  lifecycle {
    create_before_destroy = true
  }

  tags = { Name = "${local.prefixe}-tls" }
}

resource "aws_acm_certificate_validation" "principal" {
  certificate_arn = aws_acm_certificate.principal.arn
}
