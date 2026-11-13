# **Urgences-santé — environnement de tests et formation** (AO 26-015-DO : E-TN-18, P-TN-1 ;
# Contrat 2.05 a) : « un Environnement de tests et formation pour la réalisation de tous les tests
# requis pendant la durée du Contrat et pour la réalisation du Banc d'essai, ainsi que pour la
# formation du personnel ». Il vit pendant toute la durée du contrat, à côté de la production.
#
# Même code et mêmes images que la production, tailles réduites : un environnement bâti autrement
# ne validerait pas les mises à jour qu'on y éprouve avant leur activation (E-TN-3, E-TN-4).
# Les comptes et l'organisme de formation se sèment par l'API (docs/urgences-sante/COMPTES-DEMONSTRATION.md).
#
#   terraform init -backend-config="key=urgences-sante/26-015-do/essai.tfstate"
#   terraform plan -var-file=urgences-sante-essai.tfvars -var registre_images=… -var version_images=<empreinte> -var certificat_arn=…

client                     = "us"
organisme                  = "Urgences-santé"
appel_offres               = "26-015-DO"
environnement              = "essai"
taches_application         = 1
processeur_tache           = 512
memoire_tache              = 1024
classe_base                = "db.t4g.micro"
stockage_base_go           = 20
retention_sauvegarde_jours = 7
# Ouvert aux usagers d'Urgences-santé pour la formation : il passe donc par le pare-feu applicatif,
# comme la production, et non par le seul réseau privé.
pare_feu_actif = true
domaine        = "essai-urgences-sante.nexu.ca"
