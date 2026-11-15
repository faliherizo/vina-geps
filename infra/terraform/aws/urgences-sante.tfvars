# **Urgences-santé — AO 26-015-DO** (E-TN-9 : exploitation 20 h/7 j/365 j).
#
# Les données vivent sur un volume EFS régional, répliqué dans les zones de Montréal : la perte
# d'une zone ne perd rien. L'application tourne sur **une** tâche, parce que SQLite n'admet qu'un
# écrivain ; ECS la remplace d'office dans une autre zone, sur le même volume, en quelques
# minutes. Le RTO d'une heure couvre ce remplacement et, après une corruption, la restauration
# d'une copie ; le RPO est celui des sauvegardes applicatives (15 minutes). Les garde-fous de
# `objectifs-de-reprise.tf` refusent au `plan` toute combinaison qui ne tiendrait pas ces chiffres.
#
#   terraform plan -var-file=urgences-sante.tfvars -var registre_images=… -var version_images=<empreinte> -var domaine=…

client              = "us"
organisme           = "Urgences-santé"
appel_offres        = "26-015-DO"
environnement       = "production"
haute_disponibilite = true
rto_heures          = 1
rpo_minutes         = 15
taches_application  = 1
