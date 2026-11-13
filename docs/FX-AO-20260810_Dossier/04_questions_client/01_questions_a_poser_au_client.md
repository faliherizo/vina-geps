# Questions à poser à Santé Québec Laurentides

## A. Gouvernance et portée
1. Qui est le propriétaire métier de la solution et qui approuve les règles cliniques?
2. Quels profils exacts doivent pouvoir envoyer, recevoir, répondre, administrer et auditer?
3. Les six sites sont-ils tous dans la première vague? Quel ordre et quels critères de priorisation?
4. Quels processus actuels de téléavertissement doivent être reproduits, modifiés ou supprimés?
5. Existe-t-il des périodes de coexistence avec l'ancien système?

## B. Communications cliniques
6. Quels types de messages et niveaux de priorité sont nécessaires?
7. Quelles informations cliniques sont permises dans les messages? Quelles données doivent être interdites?
8. Que signifie exactement « note au dossier »? Quel dossier clinique et quelle API?
9. Quelles sont les définitions opérationnelles de envoyé, livré, reçu, lu, pris en charge et répondu?
10. Un accusé SMS opérateur est-il suffisant ou faut-il une confirmation dans l'application?
11. Les pièces jointes sont-elles requises? Si oui, types, taille, antivirus et rétention?
12. Comment gérer les messages urgents, annulations, erreurs de destinataire et messages erronés?

## C. Groupes, gardes et escalades
13. Quelle est la source maîtresse des groupes, unités, spécialités et horaires?
14. Les groupes sont-ils maintenus manuellement, importés ou calculés par règles?
15. Comment représenter les gardes, remplacements, astreintes et doubles affectations?
16. Les cascades sont-elles séquentielles, parallèles ou mixtes?
17. Quels délais, canaux, règles d'arrêt, reprise et nombre maximal d'étapes?
18. Quelles actions sont attendues si aucune personne ne répond?
19. Qui peut créer/modifier une politique d'escalade et comment l'approuver?

## D. Canaux et intégrations
20. Quel fournisseur SMS et quel contrat sont disponibles?
21. Quelle plateforme téléphonique et quelles API sont autorisées?
22. Les appels doivent-ils utiliser synthèse vocale, DTMF ou retour d'appel humain?
23. Quelles permissions Microsoft 365/Outlook sont possibles?
24. Quelle GIA/IdP, quels protocoles et quels attributs sont disponibles?
25. Le service « F » mentionné pour l'accès distant désigne quel service précis?
26. Quels flux réseau, proxy, DNS, certificats et ports sont autorisés?

## E. Hébergement et architecture
27. Quel modèle est attendu : SaaS, nuage privé ou infrastructure de l'établissement?
28. Si SaaS, quelles régions canadiennes sont autorisées pour données, sauvegardes, télémétrie et accès support?
29. Si local, qui fournit VM, OS, stockage, réseau, base, sauvegarde et supervision?
30. Quelles normes d'architecture, technologies et versions sont imposées?
31. Quelle segmentation réseau et quelles zones de sécurité?
32. L'accès Citrix est-il effectivement dans le périmètre?

## F. Sécurité, confidentialité et conformité
33. Quelle classification des données et quelles politiques de minimisation s'appliquent?
34. Quelle durée de conservation pour contenu, métadonnées, journaux et preuves de livraison?
35. Quelles exigences de chiffrement/certificats et gestion de clés?
36. Quel processus de notification d'incident et délai maximal attendu?
37. Quel SIEM et quels formats d'export sont attendus?
38. Quels auditeurs peuvent consulter les contenus versus seulement les métadonnées?

## G. Performance, SLA et continuité
39. Les 2 000 utilisateurs correspondent-ils à des comptes, utilisateurs actifs, simultanés ou utilisateurs ciblés?
40. Quel volume maximal de messages/minute et quel pic simultané?
41. Quel pourcentage de disponibilité mensuelle est requis?
42. Quels temps de livraison cibles par canal et niveau de priorité?
43. Quels RTO/RPO par composant?
44. Quelle fréquence de sauvegarde et de test de restauration?
45. Quelles fenêtres de maintenance sont permises?
46. Quels niveaux de sévérité, réponse et résolution doivent figurer au SLA?

## H. Données, migration et facturation
47. Quels fichiers sources seront fournis pour utilisateurs, groupes, horaires et préférences?
48. Quels formats, volumes, règles de nettoyage et responsables de validation?
49. Pour la facturation, la déduplication est-elle par utilisateur unique et par mois civil?
50. Quel fuseau horaire définit le mois de facturation et les horodatages?
51. Comment traiter les utilisateurs ciblés par groupe mais dont la tentative technique échoue?
52. Quel format de rapport détaillé permet de valider la facture?

## I. Projet, formation et acceptation
53. Qui sont les deux pilotes à former et quels profils supplémentaires?
54. Quels critères d'acceptation par site et qui signe le procès-verbal?
55. Confirmer le délai d'accusé de réception des livrables : le devis indique « cinq (21) jours ouvrables ».
56. Quels livrables et formats sont attendus en plus de ceux décrits?
57. Quels supports de formation et quelles exigences d'accessibilité?
58. Quels mécanismes de soutien local pendant la transition?

## J. Décisions à obtenir en priorité
- Modèle d'hébergement et RACI.
- Fournisseurs SMS et téléphonie.
- Sémantique de confirmation et d'alerte critique.
- Source des identités, groupes et disponibilités.
- Rétention, RTO/RPO et disponibilité cible.
- Arbitrage du délai « cinq (21) jours ouvrables ».
