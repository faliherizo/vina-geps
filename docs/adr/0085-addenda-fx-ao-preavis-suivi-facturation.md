# ADR 0085 — Addenda FX-AO 1 à 3 : préavis par nature de changement, suivre sans envoyer, minimum facturable, contenu sans renseignement de santé

- **Statut** : accepté, le 19 novembre 2026.
- **Décideurs** : équipe VINA.
- **Concerne** : `soutien/domaine/{NatureChangement, JoursOuvrablesDuQuebec, EngagementsDeService, Soutien}`,
  `soutien/infrastructure/persistance/DepotSoutien`, changeset `0019-soutien-nature-maintenance` ;
  `alertes/domaine/{UtilisateursActifs, ContenuSansRenseignementDeSante, Alertes, NotesDestinataires}`,
  `alertes/application/requetes/LectureUtilisateursActifs`, `alertes/infrastructure/web/{GardeAlertes, ControleurAlertes,
  ControleurCascades}`, `politique-acces.json` ; `frontend/src/{App.tsx, alertes/*, SoutienExploitation.tsx}` ;
  `scripts/semer-fx-ao.cjs`.
- **Stories** : ST-H01 à ST-H05 ([02_stories.md](../FX-AO-20260810_Dossier/08_addenda_1_a_3/02_stories.md)).

## Contexte

Les addenda 1 à 3 de FX-AO-20260810 et le texte du contrat, reçu avec l'addenda 1, ajoutent cinq exigences que le produit
ne tenait pas, ou tenait autrement :

1. **Contrat 3.03** : la facture compte les utilisateurs actifs, avec un **minimum de 100** par mois.
2. **Grille 99 et 99.1** : un rehaussement s'annonce **21 jours ouvrables** à l'avance ; un correctif sans changement
   fonctionnel, **3 jours ouvrables**, avec sa nature. Le produit imposait 72 h à toute maintenance.
3. **Addenda 2, Q31** : la preuve de livraison est due aux expéditeurs, aux gestionnaires **et** aux pilotes. Le produit
   liait la lecture du suivi au droit d'**envoyer**.
4. **Addenda 1, Q1** : aucun renseignement de santé ne transite. La certification TGV est retirée à cette condition.
5. **Grille 98 et contrat 10.19.07** : réponse au soutien en 4 h ou moins ; taux d'efficacité de 95 %.

## Décisions

### D1 — La nature d'un changement est un ensemble fermé, et le préavis en découle

`NatureChangement` = `entretien` (72 h), `correctif` (3 jours ouvrables), `rehaussement` (21 jours ouvrables). Une
maintenance sans nature reste un entretien. Les jours ouvrables se comptent par `JoursOuvrablesDuQuebec`, une fonction
pure : du lundi au vendredi à Montréal, hors jours fériés de la *Loi sur les normes du travail*. Le Vendredi saint **et**
le lundi de Pâques sont exclus tous deux.

*Écartées* :
- **Un préavis par organisme.** Une maintenance touche toute la plateforme. Le préavis le plus exigeant des contrats en
  vigueur s'applique donc à tous les organismes. Le faire varier par organisme n'aurait de sens qu'avec des
  déploiements séparés.
- **Le calendrier férié des référentiels `effectifs`.** Il appartient à chaque organisme et à son module d'effectifs.
  Le soutien ne peut pas dépendre d'un module optionnel. Compter large, les deux jours de Pâques compris, ne peut
  qu'allonger le préavis : l'erreur possible va du bon côté.

### D2 — « Suivre » devient une capacité distincte d'« envoyer »

`alertes.suivre` garde la lecture du suivi, de sa fiche, de sa cascade et de son export. Par défaut, elle est accordée aux
mêmes rôles qu'`alertes.envoyer` : aucun organisme ne perd d'accès. Le menu lit désormais les deux capacités
(`PAGE_CAPABILITIES`) au lieu de la seule liste de rôles. Un rôle propre à l'organisme, comme `expediteur`, voit donc ses
pages.

*Écartée* : **tester le rôle `manager` dans les routes.** Ce serait recopier, dans le code, la décision d'un organisme.
La capacité se surcharge par organisme, le rôle livré ne se modifie pas.

### D3 — Le minimum facturable est une donnée de configuration

`alertes.facturation.minimum-utilisateurs` (100 par défaut) entre dans `LectureUtilisateursActifs`. Le bilan rend
`total`, `minimumFacturable` et `facturables`. La règle `max(minimum, total)` vit dans le domaine.

### D4 — Le refus d'un renseignement de santé tient dans le domaine des alertes

`ContenuSansRenseignementDeSante.verifier(champ, texte)` refuse un numéro d'assurance maladie : quatre lettres puis
huit chiffres, espaces admis. Il s'applique au titre et au corps d'une alerte, au contenu d'un modèle et au texte d'une
note. Le refus nomme le champ, jamais le texte.

*Écartée* : **une détection large des données cliniques** (diagnostics, noms de médicaments). Elle produirait des faux
positifs au cœur d'un message urgent. Ce serait pire qu'un téléavertisseur qui ne sonne pas. Le numéro d'assurance
maladie est l'identifiant sans ambiguïté. Le reste relève de la formation et du rappel affiché à l'écran.

### D5 — Une troisième politique de soutien, `communications-cliniques`

Elle donne une réponse en 4 h à tous les niveaux, en tout temps ; les délais de résolution de la politique standard ;
un objectif de 95 % ; aucun seuil majeur. L'énumération `Politique` et son `switch` exhaustif s'étendent d'un cas, comme
pour `service-essentiel` (ADR 0071).

## Conséquences

- La base gagne une colonne : `soutien_maintenances.nature`, avec la valeur par défaut `entretien` (changeset `0019`).
  Son retour : `db/retour/0019-retour.sql`.
- Une maintenance annoncée sans nature garde son comportement : 72 h.
- L'API `/api/alertes/droits` rend un champ de plus (`suivre`). Les clients existants l'ignorent.
- Le critère 99 passe de 📄 à ✅, avec une preuve dans le code. Le 99.1 est couvert. Le 98 est tenu par la politique
  `communications-cliniques`.
