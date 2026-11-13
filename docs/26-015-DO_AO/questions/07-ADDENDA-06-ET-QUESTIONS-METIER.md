# 7. Addenda n° 06 — réponses traitées, ajustements, et questions métier

> Addenda n° 06 du **10 novembre 2026** (fichier `reponses/addenda-01.md`, qui porte en fait
> l'addenda **n° 06**). Il répond à nos neuf questions (fichier 06) et à six questions d'autres
> soumissionnaires, joint une **grille des critères v2** et **reporte le dépôt au 26 novembre 2026,
> 14 h 00**.
>
> Il renvoie à des addenda antérieurs (n° 4 sur les conventions collectives, et ceux qui ont fait
> passer E-TN-19 du Québec au Canada) que le dossier ne contient pas. **Les obtenir sur SEAO avant
> toute autre décision** : ils peuvent avoir modifié d'autres clauses (lieu d'hébergement de la
> relève, art. 17.07.04, notamment).

---

## 7.1 Ce que l'addenda tranche, et son effet sur notre réponse

| Q | Sujet | Ce que dit Urgences-santé | Effet pour nous |
|---|---|---|---|
| 1, 10 | Heures de soutien (E-TN-11, 5.02.02) | **5 h à 23 h, 7 j/7, 365 j.** Les délais de 5.02 sont des **obligations de résultat**, non négociables à la baisse. | Grille v2 modifiée. Dotation (hors code) **et** module Soutien à aligner : voir 7.2-A. |
| 2 | Conventions collectives (E-GN-12, E-CH-1) | Non transmises. Les exigences portent sur la **capacité de paramétrage** : des utilisateurs autorisés d'Urgences-santé créent, modifient et maintiennent les règles **eux-mêmes, sans développement**. À démontrer. | Notre moteur de règles y répond. Préparer un **scénario de démonstration** où une planificatrice crée, simule et publie une règle (7.2-E). |
| 3 | ESB (E-TN-7) | Aucun produit imposé. Compatibles : **API REST JSON documentées (OpenAPI)**, **OAuth 2.0 / OIDC par Entra ID**, **webhooks ou notifications** quasi temps réel, **SFTP** pour les lots. | Les trois premiers existent. **SFTP absent.** Voir 7.2-C. |
| 4 | Fonction en développement | **Non.** « Oui » à un critère essentiel exige que la fonction soit **disponible et opérationnelle dans la version commercialisée à la date de dépôt**. Toute réponse « Oui » peut être vérifiée en démonstration. | **Décisif.** Chaque « Oui » doit être vrai dans la version **en production commerciale le 26 novembre**, pas seulement dans le dépôt. Voir 7.2-D. |
| 5 | Report | Dépôt **26 novembre 2026, 14 h 00**. | Nouvelle échéance. Questions : au plus tard le **18 novembre** (1.04.01, plus de 5 jours ouvrables avant). |
| 6 | Taxes nettes (1.07.22) | **5,688 %**. | Bordereau de prix. |
| 7 | Pondération partie 5 | **20 %** (annexe 1.16.03 B fait foi). | Questionnaire de qualité. |
| 8 | Renvoi 0.01.06 | Texte corrigé fourni. | Aucun effet technique. |
| 9 | Plage E-TN-9 | **24 h / 7 j / 365 j, disponibilité 99,7 %** (2.4 et 6.01). « 20 h » était une erreur. | Grille v2 modifiée. Notre module Soutien visait 99,5 % : voir 7.2-A et 7.2-B. |
| 11 | Installation chez l'Organisme (E-TN-1) | SaaS confirmé : le prestataire installe et met à jour **sur son infrastructure** ; l'Organisme **valide, active, paramètre et planifie** la mise en production. | Conforme à notre modèle (environnement d'essai, modules activables, plage d'entretien). |
| 12 | Hébergement (E-TN-19) | **« Maintenant Canada »** : la grille v2 lit « un fournisseur qui héberge ses serveurs et ses données au **CANADA** ». | AWS ca-central-1 (Montréal) **conforme**. E-TN-19 peut passer à ✅ ; la décision d'hébergeur (ADR 0043) n'est plus un obstacle de conformité. |
| 13 | Projets de référence (E-TN-28/29) | Sur **la solution proposée** (ou une version antérieure du même produit), **en production** chez le client ; les trois clients de E-TN-29 sont **ceux des trois projets** ; date qui fait foi : **28 septembre 2026**. | Inchangé : éliminatoire, hors code, à confirmer par l'entreprise. |
| 14 | Liste MCN (E-TN-31) | « Voici le lien : » — **le lien est absent du texte reçu.** | Récupérer le lien dans l'addenda original (PDF) ou sur SEAO. |
| 15 | 2 secondes (E-TN-17) | Temps **perçu par l'utilisateur**, en **utilisation usuelle** (pas en charge maximale), de l'action à l'**affichage complet** ; conforme si sous 2 s dans **la très grande majorité** des cas. Visées : consulter un horaire ou un quart, déclarer sa présence, saisir ou consulter une demande (congé, échange), enregistrer une modification, naviguer entre les écrans principaux. Exclus : lots, gros rapports, exportations, traitements différés. | Notre campagne mesure l'API ; il faut mesurer **dans le navigateur** les six gestes nommés. Voir 7.2-F. |

---

## 7.2 Ajustements de notre solution

### A. Soutien — horaire et délais de la clause 5.02 (E-TN-11)

Le module Soutien porte les valeurs d'un autre appel d'offres :

| Élément | Aujourd'hui (`soutien/domaine/Soutien.java`) | Ce qu'exige 26-015-DO |
|---|---|---|
| Délais | un seul délai par niveau, mesuré depuis l'ouverture : N1 1 h, N2 2 h, N3 4 h, N4 24 h | **deux délais** : *réponse* (30 min en heures de soutien, 60 min hors heures pour N1-N2 ; 60/120 min pour N3-N4) puis *résolution* depuis le retour d'appel (N1 **2 h**, N2 **6 h**, N3 **48 h**, N4 **7 jours**) |
| Heures de soutien | non modélisées | **5 h à 23 h, 7 j/7** ; le délai de réponse dépend de l'heure d'ouverture |
| Objectif de disponibilité | 99,5 % | **99,7 %** par mois, sur 24 h × 30 (ou 31) jours |

**Fait le 12 novembre 2026** (ADR 0071) : la politique de soutien est celle du contrat de
l'organisme. La politique « service essentiel » applique 5.02.02 (délai de réponse selon l'heure
d'envoi, puis délai de résolution depuis le retour d'appel) et 6.01 (99,7 %, manquement majeur sous
99,0 %). Le soutien l'attribue depuis la console superadmin (onglet Soutien, « Engagements de
service par organisme ») ; les autres organismes gardent la politique standard. Reste la
**dotation** de 5 h à 23 h, 7 j/7, qui n'est pas logicielle.

### B. Disponibilité 99,7 % en 24/7 (E-TN-9, 6.01)

- **La maintenance planifiée sort du temps prévu** : 6.01.02 calcule le temps prévu d'utilisation
  comme « total d'heures du mois – temps de maintenance planifiée » ; 6.01.04 c) exclut en plus de
  l'indisponibilité la force majeure et la panne d'un élément externe. *(Une première lecture de ce
  fichier disait l'inverse ; elle est corrigée.)* Notre calcul, qui retire les fenêtres planifiées,
  suit donc le contrat ; seul l'objectif (99,5 % → 99,7 %) était faux.
- Hors fenêtre planifiée, 99,7 % sur 30 jours laisse **2 h 10 min** d'indisponibilité par mois.
  Avec une seule tâche écrivaine (SQLite), un déploiement **hors fenêtre** coupe le service le
  temps du redémarrage : déployer dans une fenêtre annoncée, ou le comptabiliser.
- Sous 99,0 % dans un mois : **manquement majeur** (6.01.05 c). Trois manquements consécutifs, ou
  trois en six mois : manquement substantiel ; plan correctif en 10 jours ouvrables (6.01.06).
- Le calcul de disponibilité du module dit désormais, pour un organisme sous ce contrat, si le
  mois est un **manquement** (< 99,7 %) ou un **manquement majeur** (< 99,0 %).

### C. Intégration (E-TN-7)

| Mécanisme admis | État |
|---|---|
| API REST JSON documentées OpenAPI | ✅ OpenAPI 3 servie par le Java |
| OAuth 2.0 / OIDC par Entra ID | ✅ pour les **personnes** (connexion par les annuaires de l'organisme) ; ⛔ **pour les applications** — vérifié le 12 nov. : nos API n'acceptent que **notre** jeton de session (`FiltreSession`, signé par `JWT_SECRET`) ; un jeton Entra d'application (*client credentials*) est ignoré, l'appel reçoit 401. À bâtir : valider les jetons de l'annuaire de l'organisme (émetteur, audience, signature par JWKS) et associer chaque application à un rôle. |
| Webhooks / notifications quasi temps réel | ✅ connecteur ESB : événements du journal poussés en HTTPS, à partir d'un curseur |
| **SFTP pour les lots** | ✅ **fait le 12 nov.** (ADR 0071) : mode `SFTP` des connecteurs ; dépôt atomique des lots sortants, cueillette des lots entrants (horodatages, dossiers), clé privée nommée, empreinte d'hôte exigée ; essayé contre un vrai serveur SFTP (`IntegrationsSftpTest`) |

E-TN-7 tient par les quatre mécanismes, à une nuance près : un système de l'ESB qui veut appeler
nos API **en tant qu'application** doit aujourd'hui recevoir un compte de l'application, pas un
jeton Entra.

### D. « Oui » seulement si commercialisé au 26 novembre (Q4)

- Les fonctions bâties dans le dépôt doivent être **en production commerciale** avant le dépôt :
  version déployée, accessible, documentée, dans la plage d'entretien.
- Les critères essentiels **encore partiels** ne peuvent pas recevoir « Oui » en l'état :
  E-TN-8, E-TN-9, E-TN-17, E-TN-23, E-TN-31 (E-TN-19 se règle par la grille v2). Et les hors-code :
  E-TN-11 (dotation 5 h-23 h 7/7), E-TN-25, E-TN-28, E-TN-29, E-TN-30. **Un « Non » à un critère
  essentiel rend la soumission non conforme** : chacun doit être fermé, ou la décision de déposer
  revue, avant le 26 novembre.

### E. Démonstration du paramétrage (Q2)

Scénario à répéter : une planificatrice crée une règle de disponibilité (nombre, durée des quarts),
la simule sur une situation réelle, la publie à une date, puis montre une disponibilité refusée par
cette règle avec son motif ; un administrateur la modifie sans développement. S'appuyer sur la
trousse de formation (modules M5, M8) et le jeu de démonstration.

### F. Mesure des 2 secondes (Q15)

Mesurer **dans le navigateur** (de l'action à l'affichage complet), sur un appareil courant, avec
la charge d'une journée normale, les six gestes nommés par l'addenda ; rapporter le **95e centile**
et le pourcentage sous 2 s. La campagne JMeter actuelle mesure le serveur : elle reste utile pour
la charge, pas pour la conformité telle que définie.

---

## 7.3 Questions métier — à transmettre au plus tard le 18 novembre 2026

> Mêmes règles que le fichier 05 : une question ne porte que sur **le sens** d'une exigence de la
> grille v2 ou du devis, ne cite **que les documents d'Urgences-santé**, ne mentionne ni notre
> produit ni nos choix, et sa réponse vaut pour **tous** les soumissionnaires. Aucune ne demande une
> valeur de règle (l'addenda n° 06, Q2, renvoie ces valeurs au paramétrage).

### Milieu de travail et présence

**1. Exigences E-GN-17, E-GP-10, E-GP-11 et E-GP-13 — milieu physique d'un quart**
Pour le personnel dont le quart se déroule en partie hors d'un bâtiment, le « milieu physique lié
au quart » (E-GN-17) peut-il être un **point de service ou un véhicule** plutôt qu'une adresse
fixe ? Le cas échéant, la validation de l'emplacement de la déclaration de présence (E-GP-10,
E-GP-13) vise-t-elle le lieu de **prise de service** au début du quart, ou chaque déclaration faite
pendant le quart ?

**2. Exigences E-GP-1 et P-GP-1 — moyen de déclaration**
La déclaration des heures « en temps réel » (E-GP-1) est-elle attendue depuis un **appareil
personnel** de l'employé, depuis un **poste partagé** sur le lieu de travail, ou les deux ? La
déclaration asynchrone (P-GP-1) vise-t-elle les mêmes moyens ?

**3. Exigence E-GP-9 — présence préalable rémunérée**
La présence préalable rémunérée exigée avant un quart (E-GP-9) doit-elle apparaître comme une
**partie du quart** ou comme un **temps distinct** dans la feuille de temps et dans les données
transmises à la paie (E-RF-12) ?

### Comblement et absences

**4. Exigences E-GN-5 et E-GN-6 — autorisation manuelle d'un congé refusé**
Lorsqu'un congé refusé par l'automatisation est autorisé manuellement (E-GN-5), l'application
automatique des règles de comblement (E-GN-6) doit-elle s'exécuter **même si le quart concerné
passe sous le nombre minimal de ressources** défini à E-GN-2, ou ce cas doit-il être signalé à
l'utilisateur avant l'autorisation ?

**5. Exigence E-GR-3 — taux de comblement**
Pour évaluer l'impact d'une absence sur le **taux de comblement** (E-GR-3), le taux se mesure-t-il
par rapport au **nombre de ressources prévu** au quart, ou par rapport au **nombre minimal** qui le
rend opérationnel (E-GN-2) ?

**6. Exigence E-GN-11 — exception opérationnelle**
Le « contexte d'exception opérationnelle » qui permet d'attribuer un quart malgré les règles de
priorisation (E-GN-11) doit-il être **qualifié** au moment de l'attribution (motif choisi ou
saisi), ou la traçabilité de la décision suffit-elle ?

**7. Exigences E-CH-6 et P-CH-8 — moyen et long terme**
Une absence planifiée qui rend un poste vacant (E-CH-6) et les codes d'absence de « moyen ou long
terme » (P-CH-8) relèvent-ils du même processus de **remplacement long terme** (E-CH-7), ou une
absence de moyen terme se comble-t-elle quart par quart en gestion régulière (E-GR-4) ?

### Échanges et ajustements

**8. Exigence E-GR-7 — contiguïté**
Dans la gestion des échanges de quarts, la **validation de contiguïté** (E-GR-7) vise-t-elle
l'enchaînement de deux quarts **sans repos suffisant** entre eux, la **continuité** de quarts
consécutifs, ou les deux ?

**9. Exigence E-GR-12 — échange « restreint » entre périodes**
Lorsque les échanges entre périodes de planification sont « **restreints** » (E-GR-12), en quoi
diffèrent-ils d'« autorisés » : une **approbation** est-elle exigée, ou seuls certains types de
quarts ou d'employés peuvent-ils échanger ?

**10. Exigence E-GR-9 — sans effet rétroactif**
Un paramètre opérationnel ajusté en cours de période « sans effet rétroactif » (E-GR-9)
s'applique-t-il aux **quarts non encore commencés**, ou seulement aux **attributions faites après**
l'ajustement ?

### AGPH et confection des horaires

**11. Exigence E-CH-1 — « optimisation des budgets »**
Dans le processus AGPH (E-CH-1), l'« optimisation des budgets » est-elle attendue comme une
**limite** que la confection ne doit pas dépasser (heures ou dollars), ou comme un **calcul
d'optimisation** que la solution propose ? Le budget s'exprime-t-il en **heures**, en **dollars**,
ou les deux ?

**12. Exigence P-CH-2 — délai du tour de rôle**
Lorsque l'employé ne sélectionne pas de poste dans le délai défini de son tour de rôle (P-CH-2), la
solution doit-elle **passer au rang suivant** en laissant l'employé sans poste, ou lui **attribuer
un poste** selon une règle (P-CH-4) ?

**13. Exigences P-CH-5 à P-CH-7 — statuts d'emploi**
Le « statut temps partiel » attribué aux employés n'ayant pas obtenu de poste (P-CH-7) correspond-il
au **temps partiel occasionnel**, distinct du temps partiel régulier (P-CH-6) ? Le changement de
statut doit-il être **transmis** à un autre système de l'organisation (paie, ressources humaines) ?

**14. Exigence E-CH-10 — transfert vers la gestion régulière**
Le transfert automatique des quarts non couverts vers la gestion régulière (E-CH-10) se fait-il **à
la clôture de l'AGPH**, ou au fil de la période, dès qu'un poste reste non couvert à une date
donnée ?

### Temps et paie

**15. Exigence E-RF-5 — heures supplémentaires**
L'évaluation automatique des heures supplémentaires (E-RF-5) doit-elle couvrir des seuils
**quotidiens, hebdomadaires et par période de paie**, ou un seul de ces modes ?

**16. Exigence P-GH-4 — attribution en fin de collecte**
Lorsque plusieurs employés ont émis une disponibilité correspondant au même quart (P-GH-4),
l'attribution automatique suit-elle **les mêmes règles de priorité** que l'attribution d'un quart
disponible (P-GN-1), ou un ordre propre à la fin de période ?

---

## 7.4 Prochaines actions

1. Récupérer sur SEAO les **addenda 1 à 5**, la **grille v2** (Excel) et le **lien MCN** manquant.
2. Envoyer les questions 7.3 **au plus tard le 18 novembre 2026** (1.04.01).
3. ✅ Grille et matrice à jour (12 nov.) : E-TN-9, E-TN-11, E-TN-19 lisent le texte de l'addenda
   (`addenda-26-015-DO.json`, appliqué par `construire-couverture.py`) ; E-TN-19 passe à ✅.
   À la réception de la grille v2, y reprendre son texte mot pour mot.
4. ✅ Module Soutien ajusté (7.2-A). Fenêtres d'entretien : le calcul suivait déjà 6.01.02.
5. ✅ SFTP des lots bâti (7.2-C). Accès des applications par jeton Entra : **vérifié, absent** — à
   bâtir si l'on veut l'annoncer.
6. Mesurer les 2 secondes dans le navigateur (7.2-F).
7. Décider, critère essentiel par critère essentiel, de ce qui sera **en production commerciale**
   le 26 novembre (7.2-D).
