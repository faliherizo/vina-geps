# FX-AO-20260810 — Traitement des addenda n° 1 à 3 et du contrat

> Écrit le 19 novembre 2026 pour l'équipe de soumission. Sources, converties en Markdown :
> [03_addenda_1](../00_sources/03_addenda_1_converti.md), [04_addenda_2](../00_sources/04_addenda_2_converti.md),
> [05_addenda_3](../00_sources/05_addenda_3_converti.md), [06_contrat](../00_sources/06_contrat_addenda_1_converti.md),
> [07_bordereau et grille](../00_sources/07_bordereau_grille_addenda_3_converti.md). Conversion reproductible :
> `python3 00_sources/convertir-addenda.py "$PWD"` depuis ce dossier.

## 0. Ce qui change pour la soumission, en cinq lignes

1. **Ouverture reportée** au **7 décembre 2026, 13 h 30** (addenda 1). Le dépôt se fait sur la grille de l'**addenda 3**.
2. **Aucun renseignement de santé** ne transite par la solution (addenda 1, Q1). La certification **TGV** (clause 10.20)
   est **retirée** du contrat. Les critères de sécurité 79 à 93 restent dus.
3. **Le but** : remplacer les téléavertisseurs et tracer les appels d'urgence. Tout est attendu dès la mise en service ;
   rien n'est différé (addenda 2, Q1, Q4 et Q5). Les six sites partent ensemble.
4. **Quatre profils minimaux** : expéditeur, destinataire, gestionnaire, pilote. Le pilotage est **centralisé** (addenda 2,
   Q20 à Q31).
5. **Le contrat est nouveau pour le dossier** : jusqu'ici, seuls le devis et la grille avaient été transmis. Il fixe la
   facturation au nombre d'utilisateurs actifs avec un **minimum de 100 par mois** (3.03), un **taux d'efficacité de 95 %**
   (10.19.07) et des pénalités de 500 $ par jour (10.19.05, 10.19.07).

Le bordereau de prix est **identique** dans les trois classeurs, vérifié cellule par cellule.

---

## 1. Addenda n° 1 — 13 novembre 2026

| Q | Ce que dit l'organisme | Effet pour nous | Action |
|---|---|---|---|
| 1 | La solution n'est **pas destinée** à transmettre des renseignements de santé et de services sociaux, quel que soit le canal. La clause **10.20 (TGV) est retirée**. Les critères 79 à 93 et la protection des renseignements personnels restent dus. | Un risque majeur tombe : la TGV, à nos frais et avant tout déploiement, n'est plus exigée. En contrepartie, l'engagement « aucun RSS » doit **tenir dans le produit**, et pas seulement sur papier. | **ST-H04** : garde-fou sur le contenu (refus d'un numéro d'assurance maladie, rappel à l'écran). |
| 2 | L'hébergement, l'acheminement des SMS et des appels, et les notifications Apple et Google **ne sont pas de la sous-traitance** (Régie 1.05.23, contrat 10.09). | Twilio, AWS, APNs et FCM n'ont pas à figurer à la liste des sous-contractants (10.09.02). Les obligations de sécurité et de confidentialité demeurent. | Proposition : nommer ces services comme **services technologiques**, sans les déclarer comme sous-contrats. |
| 3 | Grille modifiée : critère **99** (rehaussement), préavis ramené de **30 à 21 jours ouvrables** ; nouveau **99.1** (correctif de sécurité ou mineur), **3 jours ouvrables** de préavis, avec la nature du changement. | Notre module Soutien impose aujourd'hui **72 h** à toute maintenance, sans distinguer sa nature. Il ne tient ni les 21 jours ni la nature à communiquer. | **ST-H02** : préavis par nature de changement, en jours ouvrables. Mettre à jour `CALENDRIER-EVOLUTION-ET-MISES-A-JOUR.md`. |
| — | Ouverture reportée du 1er au **7 décembre 2026, 13 h 30**. | Six jours ouvrables de plus. | Planificateur. |

## 2. Addenda n° 2 — 17 novembre 2026 (questions de cadrage, réponses métier)

| Q | Réponse | Ce qui en découle pour la solution |
|---|---|---|
| 1, 2 | Le but : **remplacer les téléavertisseurs** et **tracer** les appels d'urgence. Indicateur de succès : aucun enjeu d'envoi ou de réception rapporté, transactions traçables. | Le suivi par remise (envoyée, lue, répondue, relancée, horodatées) et la trace de cascade sont le cœur de la démonstration (critères 11, 19, 89). La preuve se montre, elle ne se raconte pas. |
| 3, 4, 5 | Signaler **rapidement, efficacement, de façon sécuritaire** un médecin, un professionnel ou un employé de garde, et suivre ces signalements. **Aucun** cas d'utilisation différé. | Tout ce qui est coché « Oui » doit être en service dès la mise en production. Les critères encore partiels (2, 18, 39) ne peuvent pas être renvoyés à une phase 2. |
| 7, 8 | Les **six sites** au déploiement initial ; l'ordre selon ce que la solution permet. | Le déploiement par site du bordereau (bloc 1) reste possible : nous proposons l'ordre. |
| 9 | Les équipes **extra-hospitalières** sont exclues. | Aucun effet sur le produit. Hors périmètre de la formation. |
| 10, 11, 12 | Populations : médecins, infirmières, administratif, employés, gestionnaires, **pilotes**. Environ **1 500 utilisateurs** (500, 100, 500, 200, 100, 100 par site) ; une personne peut appartenir à plusieurs établissements. **150 simultanés** en pointe. | La campagne JMeter vise 2 000 (critère 95) : la pointe de 150 simultanés est bien en dessous. Un compte peut être rattaché à plusieurs sites : nos unités datées et nos groupes transverses le permettent. |
| 13 | **24 h sur 24, 7 jours sur 7**, en tout temps. | Les maintenances se font hors des heures normales (contrat 10.19.01) et s'annoncent (ST-H02). |
| 14 | **Français** pour les interfaces, les notifications, les modèles et les réponses. | Conforme. L'anglais reste offert sur demande (contrat 10.02.02, déjà en place). |
| 15, 16, 17, 18 | Irritants : bris, pertes physiques, absence de service. Contrainte : une **panne réseau**. Le **téléphone** reste le plan de contingence pendant une panne. | La cascade se replie déjà sur le texto, puis sur l'appel vocal, qui passe sans réseau de données (ST-G04, ST-G06). La documentation de continuité (critère 115) nomme le téléphone comme plan de l'établissement. |
| 19 | Propriétaire : la **direction des services professionnels**. | Interlocuteur de l'acceptation (contrat 9.03). |
| 20 | Profils minimaux : **expéditeur, destinataire, gestionnaire, pilote**. | **ST-H03** : les quatre profils, chacun avec ses droits exacts. |
| 21 | Les expéditeurs envoient à une personne, à plusieurs ou à un groupe. | Conforme (critère 5). |
| 22, 23, 24 | **Seuls les pilotes** gèrent les groupes et les cascades. Les gestionnaires avisent les pilotes des ajouts et des retraits. | Conforme : les groupes transverses et les politiques de cascade sont réservés à l'administration de l'organisme. Le pilote est donc l'**administrateur** de l'organisme. |
| 25 | L'expéditeur désigne si un message est **prioritaire ou non**. | Conforme : la priorité est choisie à l'envoi. La priorité critique reste réservée (`alertes.critique`). |
| 26 | Tous peuvent accéder aux messages et aux réponses. | Conforme : le suivi est partagé entre les personnes autorisées (critère 12). |
| 27, 28, 29, 30 | Le pilotage est **centralisé**. Les pilotes accordent et retirent les accès, temporaires ou permanents, et valident périodiquement les droits et les groupes. | Conforme : l'administration de l'organisme tient les comptes. La validation périodique est un geste du pilote, appuyé par l'export des comptes et des groupes. |
| 31 | La **preuve de livraison** est accessible aux **expéditeurs, gestionnaires et pilotes**. Les **rapports** aux gestionnaires et aux pilotes. Les **journaux d'audit** aux pilotes seulement. | **Écart** : aujourd'hui, voir le suivi (la preuve de livraison) exige le droit d'**envoyer**. Un gestionnaire qui n'envoie pas ne verrait pas la preuve. **ST-H03** sépare « suivre » d'« envoyer ». Le journal d'audit est déjà réservé à l'administration. |
| 32 | Un même rôle pour une personne, quel que soit le site. | Conforme : un compte porte un rôle. |
| 33 | RACI : selon ce qu'offre la solution. | Proposition : RACI à joindre au plan d'implantation (critère 102). |

## 3. Addenda n° 3 — 19 novembre 2026 (grille de l'addenda 3)

| Q / critère | Ce que dit l'organisme | Effet | Statut de la grille |
|---|---|---|---|
| Q1, critère 8 | La copie par courriel **doit être incluse** au bordereau. | Inclus dans le prix d'utilisation (bloc 2). | ✅ inchangé |
| Q2 | L'utilisateur compte expéditeurs, destinataires et pilotes ; **aucun lien avec les lits**. | Notre compte des utilisateurs actifs part des tentatives de transmission : il compte les personnes visées, pas les lits. | ✅ (ST-G08, à compléter par ST-H01) |
| Q3, critère 7 | Une alerte est un message **envoyé par un utilisateur**. **Aucune intégration** d'appareils biomédicaux ni de systèmes externes. | Pas de licence MDALL à détenir. Aucune intégration externe d'alerte à chiffrer. | ✅ inchangé |
| Q3, critère 18 | L'alerte critique est une notification qui **passe outre le mode silencieux**, « Ne pas déranger » ou le mécanisme Critical Alerts d'iOS. | Notre lecture était la bonne (ADR 0080). Restent l'application mobile et le droit d'Apple (Q-ALC). | 🟡 inchangé |
| Q3, critère 38 | Office : **extraire les rapports en XLS** ; Office 2016. | Nous produisons du **XLSX**, qu'Office 2016 ouvre nativement. À déclarer comme tel. | ✅ |
| Q3, critère 39 | **PDF/A** pour les rapports d'activité ou d'analyse. | Nos PDF sont en PDF/A-2b. veraPDF n'a toujours pas été exécuté. | 🟡 inchangé |
| Q4, critère 40 | Il **suffit** que les courriels émis soient **reçus** dans Outlook 365. | Le relais SMTP suffit. Microsoft Graph reste une option, il n'est plus nécessaire. | ✅ |
| Q3, critère 44 | Pas de broadcast. Le multicast est permis avec la DRI LLL, mais le RITM ne le route pas entre sites. | Nous n'émettons ni broadcast ni multicast. | ✅ inchangé |
| Q5, critères 51, 56 à 61, 64, 69, 70 | En **SaaS exclusif**, les exigences propres à une installation chez l'organisme sont **sans objet** et n'entraînent pas de non-conformité. | Le **profil B** (installation chez l'établissement) n'est plus nécessaire pour être conforme. Il reste une option que nous pouvons offrir. Décision commerciale : **SaaS** (profil A). | Sans objet en SaaS |
| Q6, critère 81 | **Pas obligatoire**, mais souhaité : SSO avec Entra ID de l'établissement. | Déjà offert (OIDC Entra ID). | ✅ |
| Q7, critère 53 | Données, bases et **sauvegardes au Canada**. Le **transit** par la téléphonie, les SMS ou le courriel n'est pas de l'hébergement. | AWS `ca-central-1` est conforme. Twilio en transit est admis. Toute conservation hors du Canada exige une autorisation écrite. | ✅ |

## 4. Le contrat (version de l'addenda 1) — les clauses qui touchent le logiciel

Seule la clause 10.20 est barrée. Mais le texte du contrat entre pour la première fois au dossier. Voici les clauses qui
engagent le produit ou l'exploitation.

| Clause | Exigence | État dans VINA GePS | Action |
|---|---|---|---|
| 3.03 | Facturation au nombre **réel d'utilisateurs actifs** du mois, **minimum de 100**. Est actif quiconque est **ciblé par au moins une tentative de transmission**, directement ou par un groupe, une garde, une cascade. Les données doivent pouvoir valider le compte facturé. | Le bordereau des utilisateurs actifs (ST-G08) compte les personnes visées par une tentative et garde la pièce justificative. Il ignore le **minimum de 100**. | **ST-H01** : le nombre facturable, `max(minimum, actifs)`, avec le minimum en configuration. |
| 3.02 | 15 % au bon de commande, 35 % à l'acceptation provisoire, 50 % à l'acceptation finale. | Commercial. | Proposition. |
| 2.03 | Transition jusqu'à **6 mois** au prix en vigueur si un autre fournisseur est retenu. | Commercial ; nos exports complets et notre copie par organisme la rendent possible. | Proposition. |
| 10.02 | Version française sans frais ; version **anglaise** sur demande, sans frais. | Interface fr, en (et mg). | ✅ |
| 10.05, 10.18.03 vi | L'organisme est propriétaire des données ; accès libre « sous la forme exigée », format structuré et courant, sans frais. | Exports CSV, XLSX, JSON, PDF ; copie complète par organisme. | ✅ |
| 10.18.02 c, d | Politique de **télétravail et d'appareils mobiles** ; un **responsable de la sécurité** nommé dans les 10 jours de l'adjudication. | Hors code. | Planificateur (engagement). |
| 10.18.04 b | À la fin du contrat : **destruction** des renseignements personnels, attestation signée sous **60 jours** (annexes 10.18.04 A à C). | La destruction d'un organisme existe (`saas/domaine/Destruction`). | Vérifier que l'attestation produite reprend les champs de l'annexe 10.18.04 C (planificateur). |
| 10.19.01 | Préavis de chaque entretien, installation, rehaussement ; arrêt **hors des heures normales**. | Les fenêtres de maintenance s'annoncent (72 h) ; voir 99 et 99.1. | **ST-H02**. |
| 10.19.02 | Ligne téléphonique sans frais, **7 h à 18 h**, du lundi au vendredi, jours fériés compris. | Hors code (téléphonie du soutien). Le critère 101 exige en plus **24/7 pour les incidents critiques**. | Planificateur (dotation). |
| 10.19.04 | **Rapport d'intervention** : date et heure de l'appel, prise en charge, priorité, type, description, travail effectué, résolution. | Le billet de soutien porte ces jalons (ouverture, retour d'appel, résolution, niveau, catégorie, échanges). | ✅ ; vérifier l'export du billet. |
| 10.19.06 | **Rapport exécutif annuel** : interventions et taux d'efficacité. | Rapports périodiques du Soutien (critère 111) ; disponibilité mensuelle calculée. | ✅ ; l'agrégat annuel est un export. |
| 10.19.07 | **Taux d'efficacité ≥ 95 %**, hors entretien préventif ; 500 $ par jour sous le seuil (plafond 500 000 $ par an). | La politique standard vise 99,5 % et retire déjà la maintenance planifiée du calcul ; mais aucune politique ne juge un mois contre **ce** contrat. | **ST-H05** : politique « communications cliniques », objectif 95 %. |
| 10.19.05 | 500 $ par jour si les délais d'intervention ne sont pas tenus (plafond 100 000 $ par an). | La politique standard ne promet **aucun délai de réponse** : seulement une résolution (N1 1 h, N2 2 h, N3 4 h, N4 24 h). Le critère 98 exige une **réponse en 4 h ou moins** pour toute demande. | **ST-H05** : délai de réponse de 4 h à tous les niveaux, en tout temps (24/7, addenda 2, Q13). |
| 10.20 | ~~Certification TGV~~ — **retirée**. | — | Retirer du registre des risques. |

## 5. Ce qui ne change pas

- Les **115 critères**, plus 99.1, restent à cocher « Oui » ou « Non ». Les critères 51, 56 à 61, 64, 69 et 70 se cochent
  en SaaS comme **sans objet** (addenda 3, Q5) : la proposition le dit en commentaire.
- Les questions encore ouvertes du dossier (Q-TEL, Q-ALC, Q-DOS) ne sont pas tranchées par ces addenda. Q-DOS est
  **éclairée** par l'addenda 1, Q1 : sans renseignement de santé, la « note au dossier » ne peut pas être une note au
  **dossier patient**. Elle se lit comme une note sur le **destinataire**, ce que nous livrons (critère 2, 🟡 → ✅).
