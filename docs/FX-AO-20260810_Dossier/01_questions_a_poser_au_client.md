# Cahier de questions et de clarifications à poser au client
**Projet :** solution de communications cliniques – Santé Québec Laurentides  
**Version :** 2.0  
**Statut :** document de travail pour ateliers de découverte et clarification  
**Objectif :** obtenir les décisions métier, fonctionnelles, techniques, de sécurité, d'exploitation, contractuelles et de déploiement nécessaires avant la conception détaillée et la configuration de la solution.

> **Règle de lecture**
> - Les questions ci-dessous sont des questions à poser : elles ne constituent pas des exigences déjà approuvées.
> - Les éléments explicitement présents dans le devis ou le bordereau sont rappelés comme points à confirmer lorsqu'une interprétation ou une règle opérationnelle manque.
> - Les propositions de conception (ex. groupes dynamiques, moteur de règles, versionnement des configurations) sont des pistes à valider, pas des décisions client.
> - Pour chaque réponse, consigner : décision, justification, responsable, date, preuve/document, exigences impactées et statut (Ouvert / À valider / Approuvé / Hors périmètre).

## 1. Synthèse des décisions prioritaires

| Priorité | Sujet | Décision attendue | Pourquoi c'est bloquant |
|---|---|---|---|
| P0 | Portée métier | Processus remplacés, sites, populations et cas d'usage prioritaires | Détermine le périmètre et le backlog |
| P0 | Urgence clinique | Définition des priorités, délais, accusés et escalades | Impact direct sur la sécurité des communications |
| P0 | Identités et groupes | Sources maîtresses et fréquence de synchronisation | Conditionne le ciblage fiable |
| P0 | Données sensibles | Contenu permis, classification, rétention et note au dossier | Conditionne sécurité et conformité |
| P0 | Canaux | SMS, application mobile, Web, courriel, téléphonie et fournisseur | Conditionne intégrations, coûts et parcours |
| P0 | Hébergement | SaaS, nuage privé ou infrastructure institutionnelle | Conditionne architecture, réseau et responsabilités |
| P0 | Disponibilité et reprise | SLA, RTO, RPO, fenêtres de maintenance et mode dégradé | Conditionne architecture et exploitation |
| P0 | Modèle de licence | Définition de l'utilisateur actif et règles de facturation | Conditionne contrôle financier |
| P1 | Administration | Responsabilités, délégations et approbations de configuration | Conditionne gouvernance |
| P1 | Migration | Sources, qualité, mapping et stratégie de bascule | Conditionne préparation des données |
| P1 | Déploiement | Ordre des six sites, pilotes, formation et critères de sortie | Conditionne calendrier |
| P1 | Contrat | Ambiguïtés, livrables, SLA et obligations de réversibilité | Conditionne l'engagement fournisseur |

## 2. Questions métier – contexte, objectifs et périmètre

1. Quel problème métier prioritaire la solution doit-elle résoudre : remplacement des téléavertisseurs, réduction des délais, traçabilité, coordination, ou plusieurs objectifs?
2. Quels indicateurs démontreront que le projet est un succès après 3, 6 et 12 mois?
3. Quels processus actuels doivent être reproduits à l'identique, simplifiés, transformés ou abandonnés?
4. Quels processus sont critiques pour la continuité des soins et lesquels sont seulement informatifs?
5. Quels cas d'utilisation doivent être opérationnels dès la première mise en service?
6. Quels cas d'utilisation peuvent être livrés dans une phase ultérieure?
7. Quels établissements, sites, unités, services et spécialités sont dans le périmètre initial?
8. Les six sites cités dans les documents sont-ils tous inclus dans le déploiement initial?
9. Quel est l'ordre de déploiement souhaité des sites et quels critères justifient cet ordre?
10. Existe-t-il des équipes ou des unités exclues du périmètre? Lesquelles et pourquoi?
11. Quelles populations utiliseront la solution : médecins, infirmières, personnel administratif, équipes techniques, gestionnaires, intervenants externes?
12. Quels volumes d'utilisateurs sont prévus par site, unité, rôle et période?
13. Combien d'utilisateurs doivent pouvoir utiliser la solution simultanément lors des périodes de pointe?
14. Quels sont les horaires d'activité, les périodes de garde, les fins de semaine et les jours fériés à couvrir?
15. Quelles langues sont requises pour les interfaces, notifications, modèles et réponses?
16. Quels irritants du système actuel doivent absolument être éliminés?
17. Quelles contraintes opérationnelles pourraient empêcher l'utilisation d'un téléphone intelligent ou d'un navigateur?
18. Quel est le processus de secours actuel lorsqu'un téléavertisseur ou un canal de communication tombe en panne?
19. Quels processus doivent continuer à fonctionner pendant une panne de la solution?
20. Qui est le propriétaire métier de la solution et qui arbitre les conflits de priorités entre établissements?

## 3. Questions sur les acteurs, rôles et responsabilités métier

21. Quels profils d'utilisateurs doivent exister au minimum?
22. Qui peut envoyer une communication individuelle, à plusieurs personnes ou à un groupe?
23. Qui peut créer, modifier, activer, désactiver ou supprimer un groupe?
24. Qui est responsable de l'exactitude des membres d'un groupe?
25. Qui approuve la création ou la modification d'une cascade d'escalade?
26. Qui peut modifier les priorités et les délais de réponse?
27. Qui peut consulter les contenus des messages et qui ne doit voir que les métadonnées?
28. Les responsables locaux peuvent-ils administrer leurs propres unités sans accéder aux autres sites?
29. Quelles délégations sont permises pendant une absence ou un remplacement?
30. Qui autorise l'accès temporaire d'un utilisateur, d'un médecin remplaçant ou d'un intervenant externe?
31. Qui déclenche la suspension immédiate d'un compte en cas de départ ou d'incident?
32. Qui est responsable de la validation périodique des droits et des groupes?
33. Quels rôles doivent pouvoir consulter les rapports, les journaux d'audit et les preuves de livraison?
34. Un même utilisateur peut-il avoir plusieurs rôles selon le site, l'unité ou le quart de travail?
35. Quelle matrice RACI le client souhaite-t-il appliquer entre propriétaire métier, sécurité, TI, unités et fournisseur?

## 4. Questions métier et cliniques sur les communications

36. Quels types de communications doivent être pris en charge : demande de rappel, demande d'intervention, alerte clinique, coordination, information générale ou autres?
37. Quels exemples réels de messages anonymisés peuvent être fournis pour concevoir les parcours?
38. Quelles informations sont indispensables dans un message pour que le destinataire puisse agir?
39. Quelles données cliniques ou personnelles sont interdites dans le corps d'un message?
40. Les messages doivent-ils contenir un identifiant de patient, un numéro de dossier, une localisation ou aucun renseignement patient?
41. Si un identifiant patient est permis, quelles règles de minimisation et de masquage doivent s'appliquer?
42. Le message doit-il être associé à un patient, à une unité, à une équipe ou uniquement à une communication opérationnelle?
43. Que signifie précisément « note au dossier » dans le bordereau : note au dossier patient, au dossier professionnel, au dossier de communication ou autre?
44. Quel système constitue le dossier cible de cette note?
45. La note doit-elle être créée automatiquement ou validée par un utilisateur avant inscription?
46. Quelles données doivent être inscrites dans la note et sous quelle forme?
47. Faut-il conserver la preuve de l'auteur, de l'heure, du destinataire et du contenu de la note?
48. Quels niveaux de priorité sont nécessaires et quelle définition métier correspond à chaque niveau?
49. Qui peut attribuer ou modifier une priorité?
50. Une priorité peut-elle être augmentée automatiquement selon l'absence de réponse ou un événement?
51. Quelles communications exigent une réponse obligatoire?
52. Quelles communications exigent une prise en charge explicite plutôt qu'une simple lecture?
53. Quelle différence métier doit être faite entre « envoyé », « transmis », « livré », « reçu », « lu », « accepté », « pris en charge », « répondu » et « fermé »?
54. Quelle preuve est considérée suffisante pour déclarer une communication reçue?
55. Un accusé technique de l'opérateur SMS est-il suffisant ou une action du destinataire est-elle obligatoire?
56. Un destinataire peut-il répondre par texte libre, par réponse prédéfinie, par appel ou par plusieurs moyens?
57. Quelles réponses prédéfinies sont nécessaires et qui les approuve?
58. Peut-on envoyer une pièce jointe? Si oui, quels formats, tailles, règles d'analyse antivirus et restrictions?
59. Faut-il permettre l'annulation, la correction ou le rappel d'un message envoyé par erreur?
60. Comment le système doit-il traiter un mauvais destinataire, un numéro invalide ou un message envoyé à un groupe erroné?
61. Faut-il permettre à l'expéditeur de voir qui a lu ou pris en charge le message dans un groupe?
62. Une réponse individuelle doit-elle être visible par tous les membres du groupe ou uniquement par l'expéditeur?
63. Quelle durée un échange doit-il rester accessible dans l'historique utilisateur?
64. Existe-t-il des règles différentes selon le type de communication, l'unité ou le site?

## 5. Questions sur les urgences, relances et escalades

65. Quels scénarios sont considérés comme critiques et doivent faire l'objet d'une procédure d'escalade?
66. Existe-t-il une politique clinique ou institutionnelle formelle décrivant les délais de réponse attendus?
67. Pour chaque priorité, quel délai maximal est accordé au destinataire pour confirmer la réception?
68. Quel délai maximal est accordé pour accepter ou prendre en charge une demande?
69. Les relances doivent-elles être envoyées au même destinataire, à un groupe ou à une personne de relève?
70. Les cascades doivent-elles être séquentielles, parallèles ou configurables selon le scénario?
71. Quel est le nombre maximal d'étapes d'une cascade?
72. Quel événement arrête une cascade : lecture, confirmation, réponse, prise en charge, annulation ou validation humaine?
73. Que doit-il se passer si le destinataire lit le message sans le prendre en charge?
74. Que doit-il se passer si le destinataire répond qu'il n'est pas disponible?
75. Que doit-il se passer si aucun membre du groupe ne répond?
76. Qui doit être averti à la dernière étape d'une cascade?
77. Faut-il une escalade vers un superviseur, un coordonnateur, un médecin de garde ou un centre de coordination?
78. Comment traiter les doublons lorsqu'un même utilisateur appartient à plusieurs groupes ciblés?
79. Comment éviter qu'un utilisateur reçoive plusieurs fois la même alerte?
80. Les règles d'escalade doivent-elles varier selon l'heure, le jour, le site, la spécialité ou la disponibilité?
81. Qui peut créer et modifier les règles d'escalade et quelle approbation est requise?
82. Les règles doivent-elles être versionnées afin de reconstituer la règle appliquée au moment de l'envoi?
83. Quels mécanismes de secours doivent être utilisés si le SMS, l'application ou la téléphonie est indisponible?
84. Quelle procédure humaine doit être déclenchée si tous les canaux automatisés échouent?

## 6. Questions sur les groupes, horaires, gardes et disponibilités

85. Quelle est la source officielle des utilisateurs, unités, services, spécialités, groupes et affectations?
86. Quelle est la source officielle des horaires, gardes, astreintes, remplacements et disponibilités?
87. Les groupes sont-ils définis manuellement, importés d'un référentiel ou calculés à partir de règles?
88. Quels critères permettent de composer un groupe : établissement, unité, spécialité, fonction, quart, garde ou combinaison?
89. Un groupe peut-il être imbriqué dans un autre groupe?
90. Un utilisateur peut-il appartenir à plusieurs groupes et plusieurs sites?
91. Comment gérer les affectations temporaires et les remplacements de dernière minute?
92. À quelle fréquence les horaires et les groupes doivent-ils être synchronisés?
93. Une modification d'horaire doit-elle prendre effet immédiatement ou à une date/heure déterminée?
94. Qui valide les changements d'affectation et de garde?
95. Comment gérer les conflits d'horaire ou les doubles affectations?
96. Quelle règle s'applique lorsqu'un utilisateur est indisponible mais reste membre d'un groupe?
97. Le calendrier de disponibilité intégré est-il requis dès la première version ou seulement si le module est activé?
98. Les utilisateurs peuvent-ils déclarer eux-mêmes leurs indisponibilités? Avec quelles validations?
99. Quelles règles de priorité s'appliquent entre calendrier personnel, horaire officiel et règle administrative?
100. Quelle politique s'applique aux utilisateurs sans téléphone mobile ou sans accès réseau?

## 7. Questions sur les fonctionnalités et les parcours utilisateurs

101. La console Web doit-elle être la seule interface d'expédition ou une application mobile peut-elle aussi envoyer des messages?
102. L'accès mobile par navigateur est-il suffisant ou une application native iOS et Android est-elle obligatoire?
103. Quelles fonctions doivent être disponibles sur ordinateur, tablette et téléphone?
104. L'utilisateur doit-il pouvoir rechercher par nom, prénom, identifiant, unité, spécialité ou groupe?
105. Quels filtres et tris sont nécessaires pour consulter les messages et leurs statuts?
106. Faut-il des favoris, des destinataires récents ou des listes de diffusion personnelles?
107. Les messages prédéfinis doivent-ils être classés par site, unité, spécialité ou type de communication?
108. Les modèles peuvent-ils contenir des champs dynamiques? Qui définit et valide ces champs?
109. Quelles préférences personnelles sont configurables : canal, son, vibration, plages horaires, langue ou autres?
110. Quelles préférences doivent être imposées par l'organisation et ne peuvent pas être désactivées?
111. Faut-il afficher des notifications visuelles, sonores ou persistantes pour les alertes critiques?
112. Une notification doit-elle rester visible jusqu'à confirmation?
113. Faut-il permettre de transférer une communication à un autre utilisateur?
114. Faut-il permettre de réassigner une demande avec justification et trace d'audit?
115. L'historique doit-il être consultable par conversation, patient, expéditeur, destinataire, groupe, site ou période?
116. Quels exports sont nécessaires et dans quels formats?
117. Quelles fonctions d'administration doivent être disponibles sans intervention du fournisseur?
118. Faut-il un environnement de simulation ou de test des cascades avant leur activation?
119. Quelles fonctionnalités doivent être accessibles en mode dégradé ou hors connexion?
120. Quels critères d'ergonomie, d'accessibilité et de compatibilité navigateur doivent être respectés?

## 8. Questions sur les canaux et les intégrations

121. Quel fournisseur SMS est déjà contracté ou privilégié par l'établissement?
122. Le client possède-t-il un compte, une passerelle ou une entente tarifaire existante avec ce fournisseur?
123. Quels types de SMS sont permis : transactionnels, bidirectionnels, courts, longs, internationaux?
124. Les réponses SMS entrantes doivent-elles être reçues et associées automatiquement à la conversation?
125. Quels délais de livraison et taux de disponibilité le fournisseur SMS garantit-il contractuellement?
126. Quel système téléphonique est utilisé et quelles interfaces d'intégration sont disponibles?
127. Les appels automatiques doivent-ils utiliser une synthèse vocale, des touches DTMF, un retour d'appel ou une intervention humaine?
128. Quels messages vocaux peuvent être diffusés sans divulguer d'information confidentielle?
129. Microsoft 365/Outlook doit-il être intégré pour les copies courriel, les calendriers ou les notifications?
130. Quelle solution de gestion des identités (GIA/IdP) doit être utilisée?
131. Quels protocoles d'authentification et attributs d'identité sont disponibles?
132. Quelles API, interfaces, fichiers ou mécanismes d'échange sont autorisés par les systèmes sources?
133. Une intégration au dossier clinique est-elle requise? Avec quel produit, quelle version et quelle équipe responsable?
134. Une intégration avec les systèmes de gestion des horaires ou de ressources humaines est-elle requise?
135. Quels environnements d'intégration (développement, test, acceptation, production) seront fournis?
136. Qui fournit les comptes techniques, certificats, secrets, listes d'adresses IP et accès aux environnements?
137. Quels flux réseau, ports, DNS, proxys, pare-feu et certificats doivent être autorisés?
138. Le service « F » mentionné pour l'accès distant désigne-t-il un service précis? Quel est son rôle?
139. L'accès Citrix est-il dans le périmètre obligatoire ou seulement une possibilité d'accès?
140. Qui est responsable du support de chaque intégration et du diagnostic de bout en bout?

## 9. Questions sur les données, la migration et le modèle dynamique

141. Quels référentiels de données seront fournis au fournisseur pour l'import initial?
142. Quels formats sont disponibles (CSV, Excel, API, HL7, autre) et quels dictionnaires de données les accompagnent?
143. Quels volumes sont attendus pour les utilisateurs, groupes, horaires, modèles et historiques?
144. Quelle est la qualité actuelle des données et qui est responsable de leur nettoyage?
145. Quelles règles de rapprochement permettent d'identifier un utilisateur de façon unique?
146. Quel identifiant stable doit être utilisé entre la GIA, les horaires, les groupes et la solution?
147. Comment traiter les comptes en double, les comptes incomplets et les utilisateurs sans coordonnées?
148. Les groupes, priorités, modèles, réponses, cascades et délais doivent-ils être configurables par les administrateurs sans développement?
149. Confirmez-vous que les entités et règles métier doivent être pilotées par les données et les paramètres, sans références métier codées en dur?
150. Quelles données de configuration doivent être versionnées et historisées?
151. Faut-il conserver l'historique des changements de membres, d'affectations, d'horaires et de règles?
152. Quelle stratégie de synchronisation est attendue : temps réel, événementielle, périodique ou import manuel?
153. Quel système fait autorité en cas de divergence entre deux sources?
154. Quelles données doivent être migrées depuis l'ancien système et lesquelles peuvent être archivées séparément?
155. Quel volume d'historique doit être repris et dans quel format?
156. Quels contrôles de qualité et rapprochements doivent être réalisés avant et après migration?
157. Le client doit-il approuver formellement les données importées avant la bascule?
158. Quelles données doivent être supprimées à la fin du contrat et quelles preuves de suppression sont attendues?

## 10. Questions sur la sécurité, la confidentialité et la conformité

159. Quelle classification institutionnelle s'applique aux messages, métadonnées, journaux et pièces jointes?
160. Quelles catégories de renseignements personnels ou cliniques peuvent être traitées?
161. Une évaluation des facteurs relatifs à la vie privée (ÉFVP) est-elle requise et qui la réalise?
162. Quelles règles de minimisation doivent être appliquées au contenu des notifications SMS et vocales?
163. Quelles exigences s'appliquent au chiffrement en transit et au repos?
164. Qui détient et administre les clés de chiffrement?
165. Quelles exigences sont imposées pour l'authentification multifacteur?
166. L'authentification unique (SSO) est-elle obligatoire pour tous les profils?
167. Quelles politiques s'appliquent aux sessions, délais d'inactivité et appareils personnels?
168. L'utilisation d'appareils personnels (BYOD) est-elle autorisée? Avec quelles conditions?
169. Quelles politiques de verrouillage, effacement à distance et gestion des appareils mobiles doivent être respectées?
170. Quelles durées de conservation s'appliquent aux contenus, métadonnées, journaux, preuves de livraison et sauvegardes?
171. Quelles règles s'appliquent à l'archivage, à la destruction et à la preuve de suppression?
172. Quels événements doivent être journalisés et quelles informations doivent être exclues des journaux?
173. Qui peut consulter les journaux et pendant combien de temps?
174. Quel SIEM institutionnel doit recevoir les événements et sous quel format?
175. Quels délais de notification et de prise en charge sont attendus en cas d'incident de sécurité ou de confidentialité?
176. Le fournisseur et ses sous-traitants peuvent-ils accéder aux données? Selon quel mécanisme d'approbation et de traçabilité?
177. Où les données principales, sauvegardes, journaux, télémétries et accès de soutien peuvent-ils être localisés?
178. Quelles attestations, certifications, audits ou tests d'intrusion sont exigés du fournisseur?
179. À quelle fréquence les tests d'intrusion et les analyses de vulnérabilité doivent-ils être réalisés?
180. Quelles exigences s'appliquent à la gestion des vulnérabilités, correctifs et dépendances logicielles?

## 11. Questions sur l'hébergement, l'architecture et les environnements

181. Quel modèle d'hébergement est attendu : SaaS, nuage privé, infrastructure institutionnelle ou modèle hybride?
182. Le fournisseur doit-il héberger la solution ou l'établissement fournira-t-il l'infrastructure?
183. Si SaaS, quelles régions géographiques sont autorisées pour les données, sauvegardes, journaux et opérations de soutien?
184. Si installation institutionnelle, qui fournit les VM, systèmes d'exploitation, stockage, bases de données, réseau et sauvegardes?
185. Quelles normes, patrons d'architecture et technologies sont imposés par l'établissement?
186. Existe-t-il des contraintes sur les bases de données, systèmes d'exploitation, navigateurs ou appareils mobiles?
187. Quels environnements sont requis : développement, intégration, test, acceptation, formation et production?
188. Les environnements doivent-ils être isolés logiquement ou physiquement?
189. Quelles données peuvent être utilisées dans les environnements non productifs et comment doivent-elles être anonymisées?
190. Quelles exigences de haute disponibilité, redondance et tolérance aux pannes sont attendues?
191. Quels composants doivent être redondants : application, base de données, passerelle SMS, téléphonie, réseau?
192. Quels mécanismes de montée en charge sont requis pour les périodes de pointe?
193. Quels outils de supervision, métriques, traces et journaux doivent être utilisés?
194. Quelles exigences de journalisation technique et de corrélation des transactions sont nécessaires?
195. Quelles contraintes de proxy, pare-feu, filtrage Web, accès distant et segmentation réseau s'appliquent?
196. L'architecture doit-elle permettre une réversibilité vers une autre infrastructure ou un autre fournisseur?

## 12. Questions sur la performance, la disponibilité et la continuité

197. Les 1 500 utilisateurs planifiés représentent-ils le nombre de comptes, tandis que les 2 000 utilisateurs représentent la capacité minimale? Confirmez-vous cette interprétation?
198. La capacité de 2 000 utilisateurs doit-elle être simultanée ou correspond-elle au nombre total de comptes?
199. Quel est le nombre maximal de messages par minute et par heure?
200. Quels pics d'envoi sont observés lors d'une situation exceptionnelle?
201. Quel délai cible est attendu entre l'envoi et la tentative de transmission?
202. Quels délais cibles de livraison sont attendus pour SMS, application, courriel et appel?
203. Quel taux de disponibilité mensuel ou annuel est exigé?
204. La disponibilité doit-elle être mesurée globalement ou séparément pour chaque canal?
205. Quelles fenêtres de maintenance sont acceptables et quel préavis est requis?
206. Quels RTO et RPO sont attendus pour la solution et pour chacun des composants critiques?
207. Quelle fréquence de sauvegarde est exigée et combien de copies doivent être conservées?
208. Où les copies de sauvegarde doivent-elles être stockées?
209. À quelle fréquence les restaurations et exercices de reprise doivent-ils être testés?
210. Quel plan de continuité doit être appliqué en cas de panne prolongée?
211. Quels modes de fonctionnement manuel ou alternatifs doivent être documentés?
212. Quels niveaux de sévérité d'incident, délais de réponse, délais de résolution et mécanismes d'escalade doivent figurer au SLA?

## 13. Questions sur l'administration, l'audit et les rapports

213. Quels paramètres les administrateurs fonctionnels doivent-ils pouvoir modifier sans intervention du fournisseur?
214. Une approbation à deux personnes est-elle requise pour les changements sensibles?
215. Faut-il prévoir une séparation des tâches entre création, approbation et activation d'une règle?
216. Quelles modifications doivent être historisées avec ancienne valeur, nouvelle valeur, auteur, date et motif?
217. Quels rapports sont nécessaires pour les gestionnaires, responsables d'unité, TI, sécurité et finances?
218. Quels filtres sont requis : site, unité, groupe, priorité, canal, période, statut ou utilisateur?
219. Quels indicateurs doivent être calculés automatiquement?
220. À quelle fréquence les rapports doivent-ils être produits et distribués?
221. Quels formats d'export sont requis (CSV, XLSX, PDF, API ou autre)?
222. Les rapports doivent-ils masquer les contenus et renseignements personnels par défaut?
223. Quel niveau de détail doit être fourni pour auditer une communication et une escalade?
224. Combien de temps les preuves d'audit doivent-elles être disponibles?
225. Qui approuve la définition des indicateurs et les règles de calcul?

## 14. Questions sur la facturation et le modèle commercial

226. Confirmez-vous que l'utilisateur est facturable lorsqu'il est ciblé par au moins une tentative de transmission durant le mois?
227. Comment traiter un utilisateur ciblé via plusieurs groupes, gardes ou cascades dans le même mois : une seule occurrence facturable?
228. Comment traiter un utilisateur ciblé si toutes les tentatives techniques échouent?
229. Quelle règle s'applique aux comptes désactivés, comptes de test, comptes de service et utilisateurs temporaires?
230. Quel fuseau horaire et quelle période définissent le mois de facturation?
231. Quel rapport de rapprochement doit accompagner chaque facture?
232. Quelles données détaillées le fournisseur doit-il remettre pour vérifier les utilisateurs facturés?
233. Comment traiter les ajouts ou retraits d'utilisateurs en cours de mois?
234. Le minimum facturable de 100 utilisateurs s'applique-t-il à chaque établissement ou à l'ensemble du contrat?
235. Les frais SMS, téléphonie, données mobiles, stockage, licences tierces et dépassements sont-ils inclus ou facturés séparément?
236. Quelles conditions s'appliquent aux 100 heures professionnelles/techniques et aux utilisateurs additionnels estimés?
237. Quelles options de renouvellement, indexation, révision de prix et sortie de contrat doivent être précisées?

## 15. Questions sur le fournisseur, le soutien et les engagements contractuels

238. Quels éléments de preuve sont attendus pour démontrer l'expérience du fournisseur en environnement hospitalier ou critique?
239. Les références clients doivent-elles être comparables en taille, volumétrie, criticité et contexte réglementaire?
240. Le fournisseur doit-il identifier tous ses sous-traitants et leurs lieux de traitement?
241. Quelles obligations de notification et d'approbation s'appliquent en cas de changement de sous-traitant?
242. Le soutien doit-il être disponible en français 24/7 pour tous les incidents ou seulement les incidents critiques?
243. Quels canaux de soutien sont acceptés : téléphone, portail, courriel, outil de billetterie?
244. Quels délais de prise en charge et de résolution sont attendus par niveau de sévérité?
245. Quel mécanisme d'escalade vers la direction du fournisseur est exigé?
246. Quels rapports de soutien et de disponibilité doivent être remis périodiquement?
247. Le fournisseur doit-il fournir un environnement de démonstration et un environnement de formation?
248. Quel niveau de transfert de connaissances est attendu pour rendre l'établissement autonome?
249. Quelles activités de maintenance peuvent être réalisées sans approbation préalable?
250. Quel préavis doit être donné pour les mises à jour et quels tests de non-régression doivent être fournis?
251. Quelle procédure de retour arrière est attendue si une mise à jour perturbe le service?
252. Quelles obligations de réversibilité s'appliquent : export des données, formats, documentation, assistance et coûts?
253. À la fin du contrat, quelles preuves de restitution et de destruction des données sont requises?
254. Confirmez-vous le délai de 20 jours ouvrables indiqué pour corriger les non-conformités d'audit?
255. Le devis mentionne « cinq (21) jours ouvrables » pour l'accusé de réception de certains livrables : quel délai fait foi?
256. Le délai de remise du plan d'implantation de 21 jours ouvrables après adjudication est-il confirmé?

## 16. Questions sur le projet, la migration, la formation et l'acceptation

257. Qui sera le responsable de projet côté client et qui sont les décideurs disponibles pour les ateliers?
258. Quels représentants métier, cliniques, TI, sécurité, protection des renseignements personnels et approvisionnement doivent participer?
259. Quelles disponibilités les experts métier peuvent-ils consacrer aux ateliers et validations?
260. Quels livrables doivent être approuvés formellement et par qui?
261. Quel processus de gestion des demandes de changement sera appliqué?
262. Quels jalons contractuels et dates cibles doivent être respectés?
263. Quels prérequis doivent être disponibles avant le démarrage de chaque site?
264. Qui prépare, valide et approuve les données d'import?
265. Quelle stratégie de pilote est souhaitée et quels critères déterminent le passage à l'échelle?
266. Faut-il une période de coexistence entre la solution et les téléavertisseurs?
267. Quelle durée de stabilisation est attendue après chaque mise en service?
268. Quels profils doivent recevoir une formation et combien de personnes par profil/site?
269. Les deux pilotes mentionnés dans les documents sont-ils les seuls superutilisateurs ou faut-il un réseau local plus large?
270. Quels formats de formation sont requis : présentiel, virtuel, capsules, guides, aide-mémoire?
271. Quels critères permettront de déclarer une formation réussie?
272. Quels scénarios de tests métier et cliniques doivent être exécutés avant acceptation?
273. Qui fournit les scénarios, données de test et résultats attendus?
274. Quels critères bloquants empêchent la mise en production?
275. Quel taux de réussite est exigé pour les tests fonctionnels, intégration, sécurité, performance et reprise?
276. Qui signe l'acceptation de chaque site et le procès-verbal final?
277. Quel mécanisme est prévu pour les anomalies découvertes après acceptation?

## 17. Questions sur l'accessibilité, l'ergonomie et l'environnement utilisateur

278. Quelles exigences d'accessibilité institutionnelles doivent être respectées?
279. Quel niveau de conformité est attendu pour les interfaces Web et mobiles?
280. Quels navigateurs et versions doivent être supportés?
281. Quels appareils et tailles d'écran sont utilisés dans les unités?
282. L'utilisation avec lecteur d'écran, clavier seul, contraste renforcé et zoom doit-elle être testée?
283. Les alertes critiques doivent-elles être perceptibles autrement que par la couleur ou le son?
284. Quelles contraintes d'utilisation existent avec gants, mobilité, postes partagés ou interruptions fréquentes?
285. Des tests utilisateurs doivent-ils être réalisés dans les unités avant validation des maquettes?

## 18. Questions à documenter explicitement comme décisions d'architecture

286. Les règles métier (priorités, délais, cascades, modèles, réponses, groupes, canaux et politiques de disponibilité) doivent-elles être entièrement administrables et stockées en données?
287. Quelles entités métier doivent être configurables par établissement, unité ou groupe?
288. Faut-il prévoir des dates d'effet, dates de fin, versions et approbations pour les configurations?
289. Quelle règle s'applique lorsqu'une configuration change alors qu'une communication est déjà en cours?
290. Une communication doit-elle conserver une photographie de la configuration appliquée lors de sa création?
291. Les identifiants de rôles, statuts, priorités, canaux et types doivent-ils provenir de référentiels administrables plutôt que d'énumérations figées?
292. Quels référentiels sont partagés entre établissements et lesquels doivent être propres à un site?
293. Quelles extensions futures sont anticipées (nouveaux canaux, systèmes cliniques, nouveaux sites, nouvelles catégories de messages)?
294. Le client exige-t-il une API documentée pour les intégrations et l'export des données?
295. Quelles contraintes de portabilité des données et d'indépendance fournisseur doivent être respectées?

## 19. Matrice de suivi des réponses

À compléter pendant les ateliers. Ajouter une ligne par question ou regrouper uniquement lorsque les réponses et les responsables sont identiques.

| ID question | Réponse / décision | Responsable client | Preuve ou référence | Exigences liées | Statut | Échéance |
|---|---|---|---|---|---|---|
| Q-001 | À compléter | À désigner | À fournir | EF / BM / NFR | Ouvert | À définir |
| Q-002 | À compléter | À désigner | À fournir | EF / BM / NFR | Ouvert | À définir |
| Q-003 | À compléter | À désigner | À fournir | EF / BM / NFR | Ouvert | À définir |

## 20. Documents et éléments à demander au client

- Organigrammes des établissements, unités, services et spécialités.
- Référentiel des utilisateurs et identifiants institutionnels (jeu anonymisé si nécessaire).
- Exemples anonymisés de groupes, gardes, horaires, remplacements et listes de diffusion.
- Procédures actuelles de téléavertissement, appels critiques, relances et escalades.
- Politiques institutionnelles de communication clinique et de gestion des urgences.
- Politique de classification, conservation, archivage et destruction des renseignements.
- Politique BYOD et exigences de gestion des appareils mobiles.
- Architecture réseau, zones de sécurité, proxys, DNS, pare-feu et exigences d'accès distant.
- Documentation GIA/IdP, SSO, MFA et attributs d'identité.
- Documentation des API et systèmes cibles (téléphonie, SMS, horaires, dossier clinique, Microsoft 365).
- SLA et contrats existants des fournisseurs SMS et téléphonie.
- Exigences institutionnelles d'hébergement, de sécurité, de journalisation et de supervision.
- Politique de sauvegarde, continuité, reprise et exercices de restauration.
- Exigences d'accessibilité et standards UI institutionnels.
- Modèle de rapport attendu pour les utilisateurs actifs et la facturation.
- Scénarios de tests, critères d'acceptation et processus de gestion des changements.
- Plan de formation, profils cibles et contraintes de disponibilité des équipes.
- Clarification écrite des ambiguïtés contractuelles relevées dans le devis et le bordereau.

## 21. Proposition d'organisation des ateliers

| Atelier | Participants recommandés | Sections | Résultat attendu |
|---|---|---|---|
| A1 – Vision et périmètre | Sponsor, propriétaire métier, gestionnaires de sites | 2 et 3 | Périmètre, objectifs, RACI |
| A2 – Parcours cliniques | Représentants cliniques, médecins, soins, coordonnateurs | 4 et 5 | Scénarios, priorités, délais, escalades |
| A3 – Référentiels et horaires | RH, responsables d'unités, gestionnaires de garde | 6 et 9 | Sources maîtresses et règles de synchronisation |
| A4 – Fonctionnalités et UX | Utilisateurs, pilotes, accessibilité | 7 et 17 | Parcours validés et critères UX |
| A5 – Intégrations et architecture | Architecture, infrastructure, téléphonie, intégration | 8 et 11 | Contrats d'interface et modèle d'hébergement |
| A6 – Sécurité et conformité | RSSI, protection des renseignements, juridique | 10 | Classification, contrôles et rétention |
| A7 – Exploitation et SLA | Exploitation, soutien, fournisseur, continuité | 12 et 15 | SLA, supervision, RTO/RPO |
| A8 – Commercial et facturation | Approvisionnement, finances, fournisseur | 14 et 15 | Modèle de prix et règles de facturation |
| A9 – Livraison et acceptation | Projet, métier, TI, fournisseur | 16 | Roadmap, migration, tests et acceptation |
| A10 – Décisions d'architecture | Comité d'architecture et propriétaires métier | 18 | Décisions, contraintes et ADR à ouvrir |

## 22. Points à ne pas présumer

Les éléments suivants ne doivent pas être considérés comme acquis tant qu'une réponse formelle n'a pas été obtenue :
- La nature exacte et le système cible de la « note au dossier ».
- Le niveau de confidentialité permis dans les SMS, courriels et messages vocaux.
- La signification métier et technique de chaque statut de livraison.
- Le caractère obligatoire d'une application mobile native.
- Le fournisseur SMS/téléphonie et les engagements réels de leurs API.
- La source maîtresse des identités, groupes, horaires et disponibilités.
- Le modèle d'hébergement et la localisation de toutes les catégories de données.
- La disponibilité cible, les RTO/RPO et les objectifs de performance.
- Les règles exactes de facturation en cas d'échec de transmission ou de doublons.
- La portée de Citrix et du service « F ».
- Le délai contractuel visé par la formulation « cinq (21) jours ouvrables ».
- Les règles métier de priorité et d'escalade qui doivent être approuvées par les responsables compétents.

---
**Fin du cahier de questions.** Les réponses validées doivent alimenter la matrice de traçabilité, le backlog, les diagrammes BPMN, le MCD, les séquences et les ADR. Toute réponse qui modifie le périmètre, une règle métier ou une exigence non fonctionnelle doit être évaluée pour ses impacts avant approbation.
