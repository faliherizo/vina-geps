# Exigences non fonctionnelles et techniques

| Domaine | Exigence issue des pièces | Référence |
|---|---|---|
| Poste client | Postes standards; Windows 11 | Grille 33-34 |
| Impression | Service Windows ou paramètres locaux; pilotes universels HP, Ricoh (orthographe source « Richo »), Xerox | Grille 35, 37 |
| Nomenclature | Respect des noms de postes, alias et groupes AD de l'établissement | Grille 36 |
| Bureautique | Compatibilité Office à jour; source mentionne Office 2016 | Grille 38 |
| Export | PDF/A à l'extraction PDF | Grille 39 |
| Courriel | Microsoft Outlook 365 | Grille 40 |
| Réseau | WAN inter-installations; Ethernet 100/1000 Mbps; TCP/IP; latence jusqu'à 10 ms | Grille 41-42 |
| Adressage | Adressage privé établissement 10.x.x.x | Grille 43 |
| Réseau | Pas de broadcast; WPA2; Wi-Fi 802.11 a/b/g/n/ac/ax si utilisé; DNS; 802.1x EAP-TLS/PEAP MSCHAPV2; proxy/filtrage Web | Grille 44-49 |
| Hébergement | SaaS, nuage privé ou infrastructure autorisée | Grille 50 |
| Installation locale | Windows Server 2019+; VMware vSphere ou équivalent; Citrix lorsque applicable | Grille 51-52 |
| Localisation | Données au Canada en cas d'infonuagique, sauf autorisation écrite et conformité démontrée | Grille 53 |
| Infrastructure | Établissement gère/acquiert serveurs et stockage et installe l'OS si modèle local/dédié | Grille 56-58 |
| Virtualisation | VMware vSphere 8+ | Grille 61 |
| Temps | NTP établissement ou heure serveurs/clients | Grille 62 |
| Correctifs | Correctifs de sécurité installables dès disponibilité | Grille 63 |
| Sauvegarde | Compatibilité agent établissement; procédure tous environnements | Grille 64-65 |
| Cycle de vie données | Archivage et destruction permanente | Grille 66-67 |
| Restauration | Tests périodiques accompagnés par fournisseur (la grille mentionne 24 mois) | Grille 68 |
| Surveillance | Microsoft EDR; Zabbix pour surveillance standard | Grille 69-70 |
| Conformité | Lois, règlements et politiques applicables, dont Loi 25 | Grille 71 |
| Sécurité | Confidentialité, intégrité, disponibilité; chiffrement transit et repos y compris sauvegardes | Grille 72-74 |
| Identité | RBAC, moindre privilège, pas de comptes génériques; révocation rapide; intégration GIA; LDAPS | Grille 77-83 |
| Authentification locale | Mot de passe ≥8 caractères, majuscule/minuscule/chiffre/spécial; historique 10 sur 6 mois | Grille 85-86 |
| Exécution | Aucun privilège administrateur requis à l'exécution | Grille 87 |
| Accès distant | Service « F » de l'établissement lorsque applicable (terme à clarifier) | Grille 88 |
| Audit | Journaux inviolables, exportables, rétention et formats documentés | Grille 89-93 |
| Capacité | Au moins 2 000 utilisateurs et extensibilité | Grille 95 |
| Multi-sites | Plusieurs unités, services, spécialités et établissements | Grille 96 |
| SLA | Disponibilité, réponse, résolution, sauvegardes, performance, escalade | Grille 97 |
| Soutien | Réponse ≤4 h; soutien critique 24/7 en français | Grille 98, 101 |
| Maintenance | Préavis 30 jours ouvrables; rollback | Grille 99-100 |
| Continuité | PCA/PRA documenté | Grille 115 |

## Exigences non quantifiées à ne pas inventer
Les documents ne donnent pas de seuil chiffré pour disponibilité globale, temps de livraison maximal, débit de messages, RTO, RPO, durée de rétention, chiffrement précis, ni taux de réussite des tests. Ces paramètres doivent être négociés et inscrits au contrat/SLA.
