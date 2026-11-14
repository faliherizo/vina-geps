/**
 * Les deux environnements sont décrits par **le même code**, et ne diffèrent que par ces
 * variables. C'est ce que l'exigence 42 demande vraiment : un environnement d'essai qui valide des
 * changements doit être bâti comme la production, sinon il valide autre chose.
 */

variable "region" {
  type        = string
  default     = "ca-central-1"
  description = "Région AWS. Seule Montréal est admise : les données doivent rester au Québec."

  validation {
    # Un garde-fou qui échoue au `plan` plutôt qu'à la première mise en service. Une région
    # canadienne hors Québec passerait les essais et violerait la réponse 11 en production.
    condition     = var.region == "ca-central-1"
    error_message = "Seule ca-central-1 (Montréal) est admise : la réponse 11 de l'addenda n° 2 exige un hébergement au Québec, sauvegardes et relève comprises."
  }
}

/*
 * Le client servi. Les valeurs par défaut sont celles du CSSDC, premier déploiement : elles laissent
 * inchangés le préfixe et les étiquettes de ses ressources — un préfixe modifié les recréerait.
 */
variable "client" {
  type        = string
  default     = "cssdc"
  description = "Préfixe court des ressources du client (minuscules, chiffres, tirets)."

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,14}$", var.client))
    error_message = "Le client est un préfixe de 2 à 15 caractères : minuscules, chiffres, tirets."
  }
}

variable "organisme" {
  type        = string
  default     = "CSSDC"
  description = "Nom de l'organisme public, porté par l'étiquette « Organisme » de chaque ressource."
}

variable "appel_offres" {
  type        = string
  default     = "2026-PLAT-GA"
  description = "Numéro de l'appel d'offres, porté par l'étiquette « AppelOffres »."
}

variable "environnement" {
  type        = string
  description = "« production » ou « essai »."

  validation {
    condition     = contains(["production", "essai"], var.environnement)
    error_message = "L'environnement est « production » ou « essai »."
  }
}

variable "pieces_jointes_stockage" {
  type        = string
  default     = "base"
  description = <<-EOT
    Où se range le contenu des pièces jointes de la messagerie (PIECES_JOINTES_STOCKAGE).
    « base » : dans la base SQLite, donc dans chaque sauvegarde applicative, essai de restauration et copie par
    organisme (E-TN-6). « volume » : en fichiers sous NEXU_DONNEES/pieces-jointes sur l'EFS, protégés par AWS Backup
    avec le volume, mais absents des sauvegardes applicatives et des copies par organisme — à retenir quand les
    pièces alourdiraient trop la base.
  EOT

  validation {
    condition     = contains(["base", "volume"], var.pieces_jointes_stockage)
    error_message = "Le stockage des pièces jointes est « base » ou « volume »."
  }
}

variable "haute_disponibilite" {
  type        = bool
  default     = false
  description = "Base en réplique synchrone dans une seconde zone de disponibilité (RDS Multi-AZ) : bascule automatique en une à deux minutes. Double le coût de la base."
}

variable "taches_application" {
  type        = number
  description = "Nombre de tâches applicatives. Deux en production : une suffit à servir la charge, la seconde permet un déploiement sans coupure."
}

variable "processeur_tache" {
  type        = number
  description = "Unités de processeur par tâche (1024 = 1 vCPU)."
}

variable "memoire_tache" {
  type        = number
  description = "Mébioctets de mémoire par tâche."
}

variable "classe_base" {
  type        = string
  description = "Classe d'instance RDS. `db.t4g.*` : processeur Graviton, ≈ 20 % moins cher à performance égale."
}

variable "stockage_base_go" {
  type    = number
  default = 100
}

variable "retention_sauvegarde_jours" {
  type        = number
  default     = 14
  description = "Rétention des sauvegardes automatiques. La restauration à un instant donné couvre alors les 14 derniers jours avec une granularité de 5 minutes — le point de reprise de 15 minutes de l'exigence 48 est tenu avec de la marge."

  validation {
    condition     = var.retention_sauvegarde_jours >= 7
    error_message = "L'exigence 48 impose un point de reprise de 15 minutes : une rétention inférieure à sept jours ne laisse aucune marge d'analyse avant restauration."
  }
}

variable "pare_feu_actif" {
  type        = bool
  default     = true
  description = "Pare-feu applicatif OWASP (exigence 32). Toujours vrai en production ; l'environnement d'essai n'est pas exposé publiquement."
}

variable "domaine" {
  type        = string
  description = "Nom d'hôte servi, par exemple cssdc.nexu.ca ou essai-cssdc.nexu.ca."
}

variable "certificat_arn" {
  type        = string
  description = "ARN du certificat ACM pour ce domaine, dans la même région."
}

variable "registre_images" {
  type        = string
  description = "Registre et préfixe des trois images publiées par la CI (backend, migrations, frontend), p. ex. ghcr.io/organisation/depot."
}

variable "version_images" {
  type        = string
  default     = "latest"
  description = "Étiquette commune des trois images : l'empreinte du commit en production (retour arrière = version antérieure)."
}

variable "secret_registre_arn" {
  type        = string
  default     = ""
  description = "ARN du secret Secrets Manager (username, password) qui ouvre un registre privé ; vide pour ECR ou un registre public."
}

variable "courriel_alertes" {
  type        = string
  default     = ""
  description = "Adresse qui reçoit les alertes d'exploitation (intrusion, déni de service, essai de restauration). Vide : le sujet existe, personne n'y est abonné."
}

variable "detection_intrusion" {
  type        = bool
  default     = true
  description = "Active GuardDuty (E-TN-24). AWS n'admet qu'un détecteur par compte et par région : à fausser dans une seconde pile du même compte."
}

variable "seuil_alerte_refus" {
  type        = number
  default     = 500
  description = "Requêtes refusées par la limitation de débit, en 5 minutes, au-delà desquelles une alerte de déni de service part."
}

variable "shield_avance" {
  type        = bool
  default     = false
  description = "Souscrit AWS Shield Advanced sur le répartiteur (E-TN-23) : 3 000 USD par mois, engagement d'un an. Shield Standard, gratuit, est toujours actif."
}

variable "depot_github" {
  type        = string
  default     = ""
  description = "Dépôt GitHub (propriétaire/nom) autorisé à déployer par OIDC ; vide = aucun rôle de déploiement par la CI."
  validation {
    condition     = var.depot_github == "" || can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.depot_github))
    error_message = "depot_github s'écrit propriétaire/nom."
  }
}

variable "fournisseur_oidc_github_existant" {
  type        = string
  default     = ""
  description = "ARN du fournisseur OIDC GitHub déjà présent dans le compte AWS ; vide = le créer ici."
}
