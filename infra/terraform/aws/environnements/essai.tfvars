# Environnement d'essai — **permanent**, comme l'exige la réponse 18 de l'addenda n° 2
# (« disponible pour toute la durée du contrat »), fonctionnel avant le 15 avril 2027.
#
# Même code, mêmes images, tailles réduites : un environnement bâti autrement ne validerait pas les
# changements qu'on y éprouve, et l'exigence 42 n'aurait plus d'objet.
environnement              = "essai"
taches_application         = 1
processeur_tache           = 512            # 0,5 vCPU
memoire_tache              = 1024           # 1 Gio
classe_base                = "db.t4g.micro" # 0,018 USD/h relevé le 27 sept. 2026
stockage_base_go           = 20
retention_sauvegarde_jours = 7
pare_feu_actif             = false # non exposé publiquement ; accès par le réseau privé
domaine                    = "essai-cssdc.nexu.ca"
