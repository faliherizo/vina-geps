# 26-015-DO — Addenda n° 07 (19 novembre 2026)

> Conversion automatique (pdfplumber) de `26-015-DO_addenda 7.pdf`, 6 pages, par `questions/convertir-reponses.py`. Le texte barré dans l'original est rendu ~~ainsi~~ (0 caractères barrés détectés). Les pieds de page répétés sont retirés.
> Source de vérité : le PDF.

<!-- page 1 -->

19 NOVEMBRE 2026
APPEL D’OFFRES 26-015-DO
Système de gestion des effectifs

## Questions/réponses

### Question 1 : Exigences E-GN-17, E-GP-10, E-GP-11 et E-GP-13 — milieu physique d'un quart Pour le
personnel dont le quart se déroule en partie hors d'un bâtiment, le « milieu physique lié au quart »
(E-GN-17) peut-il être un point de service ou un véhicule plutôt qu'une adresse fixe ? Le cas échéant,
la validation de l'emplacement de la déclaration de présence (E-GP-10, E-GP-13) vise-t-elle le lieu
de prise de service au début du quart, ou chaque déclaration faite pendant le quart ?

**Réponse** : Non, le milieu physique lié au quart doit être un point de service, c’est-à-dire un bâtiment.
La solution doit permettre de configurer ce milieu selon le type de quart et le groupe d'employés.
La validation de l'emplacement vise la déclaration faite au début et à la fin du quart.

### Question 2. Exigences E-GP-1 et P-GP-1 — moyen de déclaration La déclaration des heures « en
temps réel » (E-GP-1) est-elle attendue depuis un appareil personnel de l'employé, depuis un poste
partagé sur le lieu de travail, ou les deux ? La déclaration asynchrone (P-GP-1) vise-t-elle les mêmes
moyens ?

**Réponse** : Les deux moyens doivent être supportés. La déclaration en temps réel doit pouvoir se
faire à partir d'un appareil cellulaire géolocalisé dans le bâtiment ou d'un poste sur le lieu de travail.
La déclaration asynchrone vise les mêmes moyens.

### Question 3. Exigence E-GP-9 — présence préalable rémunérée La présence préalable rémunérée
exigée avant un quart (E-GP-9) doit-elle apparaître comme une partie du quart ou comme un temps
distinct dans la feuille de temps et dans les données transmises à la paie (E-RF-12) ?

**Réponse** : La présence préalable rémunérée doit être rattachée au quart concerné, afin que les
saisies d'un même quart se cumulent de façon cohérente. À titre d'exemple, pour un quart de
paramédic débutant à 15 h, la déclaration de présence est requise dès 14 h 45.

## Comblement et absences

### Question 4. Exigences E-GN-5 et E-GN-6 — autorisation manuelle d'un congé refusé Lorsqu'un
congé refusé par l'automatisation est autorisé manuellement (E-GN-5), l'application automatique
des règles de comblement (E-GN-6) doit-elle s'exécuter même si le quart concerné passe sous le
nombre minimal de ressources défini à E-GN-2, ou ce cas doit-il être signalé à l'utilisateur avant
l'autorisation ?

**Réponse** : Oui. L'autorisation manuelle d'un congé refusé doit demeurer possible pour un
utilisateur autorisé.
<!-- page 2 -->

### Question 5. Exigence E-GR-3 — taux de comblement Pour évaluer l'impact d'une absence sur le
taux de comblement (E-GR-3), le taux se mesure-t-il par rapport au nombre de ressources prévu
au quart, ou par rapport au nombre minimal qui le rend opérationnel (E-GN-2) ?

**Réponse** : Le taux de comblement se mesure par rapport au niveau de comblement cible (quota)
configuré pour la période, la plage horaire ou le type de quart, c'est-à-dire le nombre de ressources
prévu.

### Question 6. Exigence E-GN-11 — exception opérationnelle Le « contexte d'exception
opérationnelle » qui permet d'attribuer un quart malgré les règles de priorisation (E-GN-11) doit-il
être qualifié au moment de l'attribution (motif choisi ou saisi), ou la traçabilité de la décision suffit-
elle ?

**Réponse** : La traçabilité complète via une journalisation des systèmes de la décision (utilisateur,
date et heure, quart, ressource attribuée) est obligatoire.

### Question 7. Exigences E-CH-6 et P-CH-8 — moyen et long terme Une absence planifiée qui rend
un poste vacant (E-CH-6) et les codes d'absence de « moyen ou long terme » (P-CH-8) relèvent-ils
du même processus de remplacement long terme (E-CH-7), ou une absence de moyen terme se
comble-t-elle quart par quart en gestion régulière (E-GR-4) ?

**Réponse** : Les deux situations doivent pouvoir être traitées distinctement selon la durée de
l'absence. Les absences de moyen ou long terme, identifiées par leurs codes d'absence et planifiées
qui rendent un poste vacant relèvent du processus de remplacement long terme à part si la période
horaire est en cours. Les absences ponctuelles ou de courte durée se comblent quart par quart en
gestion régulière. La solution doit permettre de configurer, par code d'absence, le seuil qui
détermine le processus applicable.

## Échanges et ajustements

### Question 8. Exigence E-GR-7 — contiguïté Dans la gestion des échanges de quarts, la validation de
contiguïté (E-GR-7) vise-t-elle l'enchaînement de deux quarts sans repos suffisant entre eux, la
continuité de quarts consécutifs, ou les deux ?

**Réponse** : La validation de contiguïté vise deux aspects : le chevauchement et le nombre d’heures
maximum par jour de travail (période de 24h sans tenir compte du 15 minutes avant quart). Les
règles applicables doivent être paramétrables par groupe, par statut d'emploi et selon les règles
conventionnées.

### Question 9. Exigence E-GR-12 — échange « restreint » entre périodes Lorsque les échanges entre
périodes de planification sont « restreints » (E-GR-12), en quoi diffèrent-ils d'« autorisés » : une
approbation est-elle exigée, ou seuls certains types de quarts ou d'employés peuvent-ils échanger
?

**Réponse** : Les échanges dans la période en cours et qui respectent les règles d’échange peuvent
être autorisé. Pour ceux qui sortent de la période en cours celles-ci exigent une approbation par un
utilisateur autorisé.
<!-- page 3 -->

### Question 10. Exigence E-GR-9 — sans effet rétroactif Un paramètre opérationnel ajusté en cours
de période « sans effet rétroactif » (E-GR-9) s'applique-t-il aux quarts non encore commencés, ou
seulement aux attributions faites après l'ajustement ?

**Réponse** : « Sans effet rétroactif » signifie que l'ajustement s'applique aux attributions et aux
comblements effectués après son entrée en vigueur. Tous les quarts attribués ou non doivent être
modifiés après la date d’entrée en vigueur.

## AGPH et confection des horaires

### Question 11. Exigence E-CH-1 — « optimisation des budgets » Dans le processus AGPH (E-CH-1),
l'« optimisation des budgets » est-elle attendue comme une limite que la confection ne doit pas
dépasser (heures ou dollars), ou comme un calcul d'optimisation que la solution propose ? Le
budget s'exprime-t-il en heures, en dollars, ou les deux ?

**Réponse** : L'« optimisation des budgets » est attendue comme un calcul d'optimisation proposé
par la solution. Le budget doit pouvoir s'exprimer en heures.

### Question 12. Exigence P-CH-2 — délai du tour de rôle Lorsque l'employé ne sélectionne pas de
poste dans le délai défini de son tour de rôle (P-CH-2), la solution doit-elle passer au rang suivant
en laissant l'employé sans poste, ou lui attribuer un poste selon une règle (P-CH-4) ?

**Réponse** : Lorsque l'employé ne sélectionne pas de poste dans le délai défini (P-CH-2), le traitement
doit être configurable : attribution d'un poste selon une règle prédéfinie. Si l’employé décide de ne
pas prendre de poste on attribue le statut temps partiel.

### Question 13. Exigences P-CH-5 à P-CH-7 — statuts d'emploi Le « statut temps partiel » attribué
aux employés n'ayant pas obtenu de poste (P-CH-7) correspond-il au temps partiel occasionnel,
distinct du temps partiel régulier (P-CH-6) ? Le changement de statut doit-il être transmis à un autre
système de l'organisation (paie, ressources humaines) ?

**Réponse** : Oui, Le statut temps partiel attribué aux employés n'ayant pas obtenu de poste est un
statut distinct du temps partiel régulier, il s’agit du temps partiel occasionnel. Le changement de
statut doit pouvoir être transmis aux autres systèmes de l'organisation (paie et ressources
humaines) par interface ou par exportation de données.

### Question 14. Exigence E-CH-10 — transfert vers la gestion régulière Le transfert automatique des
quarts non couverts vers la gestion régulière (E-CH-10) se fait-il à la clôture de l'AGPH, ou au fil de
la période, dès qu'un poste reste non couvert à une date donnée ?

**Réponse** : L’AGPH est un processus annuel d’attribution de postes. Le transfert automatique des
quarts non couverts pour chaque période vers la gestion régulière se fait à la confection horaire
périodique à une date donnée, une fois par mois.

## Temps et paie

### Question 15. Exigence E-RF-5 — heures supplémentaires L'évaluation automatique des heures
supplémentaires (E-RF-5) doit-elle couvrir des seuils quotidiens, hebdomadaires et par période de
paie, ou un seul de ces modes ?
<!-- page 4 -->

**Réponse** : L'évaluation automatique des heures supplémentaires doit couvrir les seuils quotidiens,
hebdomadaires et par période de paie. Les seuils applicables doivent être paramétrables selon le
groupe d'employés et les règles conventionnées.

### Question 16. Exigence P-GH-4 — attribution en fin de collecte Lorsque plusieurs employés ont
émis une disponibilité correspondant au même quart (P-GH-4), l'attribution automatique suit-elle
les mêmes règles de priorité que l'attribution d'un quart disponible (P-GN-1), ou un ordre propre à
la fin de période ?

**Réponse** : Oui. L'attribution automatique à la fin de la période de collecte (confection) doit suivre
les mêmes règles d'admissibilité et de priorisation que l'attribution d'un quart disponible.

## PARTIE A : NOTRE COMPRÉHENSION
A1. Chaque quart de travail est associé à un milieu de travail identifié (E-GN-17).

**Réponse** : VRAI
A2. L'employé déclare sa présence en temps réel (E-GP-1), au début et à la fin de son quart, à partir
d'un appareil mobile ou d'un poste situé dans un milieu de travail.

**Réponse** : VRAI
A3. Au moment de la déclaration, la Solution obtient la localisation de l'employé par un ou plusieurs
mécanismes paramétrables, par exemple la géolocalisation de l'appareil mobile comparativement
à une zone autorisée autour du site, ou l'identification d'un poste fixe rattaché à un site.

**Réponse** : VRAI
Le choix du ou des mécanismes appartient au soumissionnaire, pourvu qu'il permette de valider la
présence physique au milieu de travail du quart.

**Réponse** : Besoin de clarifications
A4. La Solution compare cette localisation au milieu de travail du quart. En cas d'écart, elle
déclenche une action paramétrable par Urgences-santé, par exemple le refus de la déclaration, son
acceptation avec une anomalie, une demande de justification ou un avis au gestionnaire.

**Réponse** : VRAI
A5. L'écart est signalé à l'employé au moment de la saisie et apparaît comme une anomalie dans la
feuille de temps soumise à l'approbation.

**Réponse** : Besoin de clarifications
A6. La localisation associée à chaque déclaration est conservée et consultable par les profils
autorisés aux fins d'audit (E-GP-14). Elle n'a pas à être affichée en continu dans les écrans courants.

**Réponse** : VRAI
A7. La validation de la localisation s'applique aux déclarations faites en temps réel. Les saisies a
posteriori (E-GP-15 et P-GP-1) ne sont pas soumises à cette validation, mais sont tracées avec
l'identification de l'utilisateur qui les effectue (E-GN-9).
<!-- page 5 -->

**Réponse** : VRAI mais la déclaration est soumise à un profil autorisé.
A8. Pour l'exigence E-RF-13, les horodatages provenant de logiciels tiers, comme la RAO ou le SAAV,
sont transmis à la Solution par Urgences-santé au moyen de l'API de la Solution, conformément à la
réponse à la question 1 de l'addenda 5. La Solution les compare ensuite à la déclaration de présence
pour en valider la cohérence.

**Réponse** : Besoin de clarifications car les communications émanent principalement du système
des horaires vers le SAAV ou autres système internes.

## PARTIE B : NOS QUESTIONS
B1. Pouvez-vous confirmer que la compréhension exprimée aux points A1 à A8 est exacte ? Le cas
échéant, pouvez-vous préciser les points à corriger ?
B2. En lien avec le point A3, pouvez-vous confirmer que tout mécanisme permettant de valider la
présence physique au milieu de travail du quart sera jugé conforme aux exigences E-GP-10 et E-GP-
11, sans qu'un mécanisme particulier soit imposé ?
B3. En lien avec le point A2, les appareils mobiles utilisés pour la déclaration de présence sont-ils
fournis par Urgences-santé ou s’agit-il d'appareils personnels des employés ?
B4. En lien avec le point A8, quels logiciels tiers fourniront les horodatages à corréler avec la
déclaration de présence ?

## Correction de l’addenda 6

### Question 1 : La définition des Conditions contractuelles (article 0.01.06) renvoie à une clause
introuvable (« Erreur ! Source du renvoi introuvable »). Quelle clause faut-il lire ?
L’article 0.01.06 aurait dû se lire :
désigne le document faisant partie du Formulaire de Soumission prescrit par l’ORGANISME PUBLIC
et utilisé par un SOUMISSIONNAIRE pour proposer son prix;
Toutefois, en cas de condition qui entre en contradiction avec les Documents d’Appel d’Offres, et
sous réserve du mécanisme qui est prévu à la clause Erreur ! Source du renvoi introuvable., la
condition contradictoire est réputée non écrite et n’est donc pas considérée comme une Condition
Contractuelle.
L'ORGANISME PUBLIC accepte de se conformer à l’ensemble des Conditions Contractuelles.
<!-- page 6 -->
Prenez note que la date de dépôt des soumissions a été reportée au :
26 novembre 2026 à 14 :00
Veuillez utiliser la grille des critèresv2.xlsx qui était jointe à l’addenda 6
pour répondre
En ce qui concerne le présent addenda, s’il modifie les documents d’appel d’offres pendant la période débutant DEUX (2)
jours avant la date limite de réception des plaintes, soyez avisés qu’il vous est possible de formuler une plainte directement
auprès de l’Autorité des marchés publics conformément à l’article 40 de la Loi sur l’Autorité des marchés publics [RLRQ c.
A-33.2.1].
Le cas échéant, une telle plainte doit être déposée auprès de l’Autorité des marchés publics au plus tard DEUX (2) jours
avant la date limite de réception des soumissions qui est le 26 novembre 2026.
Nom du soumissionnaire:
Nom de l'officier autorisé :
Titre de l'officier autorisé :
Signature de l'officier autorisé :
Date de signature :
Tous les autres termes et conditions demeurent inchangés.
