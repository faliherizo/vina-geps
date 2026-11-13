# Matrice de traçabilité, cohérence et écarts

## Références de source
- **DD** : Devis descriptif, sections indiquées.
- **GC** : Grille de conformité du bordereau, critères 1 à 115.
- **BP** : Bordereau de prix, page 1.

## Cohérence et ambiguïtés à faire arbitrer
| ID | Sujet | Observation | Impact | Question / décision |
|---|---|---|---|---|
| GAP-01 | Délai accusé réception livrable | Le devis §2.3 indique « cinq (21) jours ouvrables » : incohérence entre lettres et chiffres | Réception contractuelle | Confirmer 5 ou 21 jours |
| GAP-02 | Modèle d'hébergement | SaaS, nuage privé ou infrastructure autorisée sont permis; ailleurs l'établissement gère/acquiert serveurs et OS | Architecture et responsabilités | Quel modèle est privilégié? |
| GAP-03 | Localisation | Données Canada; lieu exact, sauvegardes, support et sous-traitants non détaillés | Conformité / résidence | Provinces, régions et accès transfrontaliers permis? |
| GAP-04 | Capacité | 1 500 utilisateurs planifiés au bordereau, minimum 2 000 capacité à la grille | Dimensionnement et prix | 2 000 utilisateurs simultanés ou enregistrés? |
| GAP-05 | Disponibilité | SLA exigé mais aucun pourcentage cible donné | Exploitation | Définir disponibilité mensuelle et exclusions |
| GAP-06 | RTO/RPO | PCA/PRA requis sans objectifs chiffrés | Reprise | Fixer RTO/RPO par service |
| GAP-07 | Réception SMS | Confirmation de réception demandée; accusé opérateur dépend du canal | Sémantique d'état | Quel niveau de preuve est acceptable? |
| GAP-08 | Téléphonie | Appel automatique et retour d'appel demandés, intégration non décrite | Fournisseur télécom / coût | Plateforme, trunk, numéros, coûts, restrictions |
| GAP-09 | Note au dossier | « dossier des destinataires » sans système cible ni format | Intégration clinique | Dossier clinique, note interne ou autre? |
| GAP-10 | Alertes critiques | « lorsque techniquement disponible et permis » sans classification ni procédure clinique | Sécurité patient | Définir catégories, priorité et garde-fous |
| GAP-11 | Service F | Terme non défini pour accès distant | Exploitation | Confirmer le service attendu |
| GAP-12 | Sauvegardes | Compatibilité agent établissement + solution sauvegarde tous environnements | Responsabilité partagée | Qui opère, chiffre et teste chaque copie? |
| GAP-13 | Réseau | WPA2 et anciennes normes Wi-Fi coexistent avec exigences actuelles | Compatibilité | Confirmer normes réellement exigées |
| GAP-14 | Office 2016 / à jour | La suite est dite « à jour » puis Office 2016 est nommée | Compatibilité | Quelle version est officiellement supportée? |
| GAP-15 | Calendrier | Module disponibilité optionnel/activable, règles de priorité non décrites | Modèle de données | Source maître des horaires? |
| GAP-16 | Confidentialité contenu | Journalisation complète vs protection des renseignements | Gouvernance | Quelles métadonnées/contenus conserver et durée? |
| GAP-17 | Facturation | Définition utilisateur actif = ciblé par au moins une tentative, même via groupe/cascade | Mesure de consommation | Dédupliquer par identifiant sur mois? Fuseau et corrections? |
| GAP-18 | Renouvellement | Contrat 3 ans + 2 options annuelles | Contractuel | Modalités de préavis et prix indexés? |

## Couverture
- Besoins métier : DD §1, §3; GC 1-32.
- Exigences fournisseur : DD §2, §5-6; GC 94, 97-115.
- Fonctionnel : GC 1-32, 89-93, 110-111.
- Technique / sécurité : GC 33-93, 95-100.
- Prix : BP p.1.

La conversion source complète reste disponible dans `00_sources`. Cette matrice est une synthèse de conception; elle ne remplace pas la réponse formelle à chaque cellule de conformité.
