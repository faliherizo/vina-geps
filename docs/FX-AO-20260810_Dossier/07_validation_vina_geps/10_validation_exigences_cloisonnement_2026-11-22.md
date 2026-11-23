# FX-AO-20260810 — validation des exigences fonctionnelles et du cloisonnement (22 novembre 2026)

> Pour l'équipe de soumission. La grille (`02_conformite_115_criteres.md`) déclarait les 32 critères fonctionnels
> acquis, sauf 2 et 18. Cette revue les a **rejoués contre le serveur Java**, un à un, en exigeant pour chacun une preuve
> de **comportement** : une règle qui décide, pas seulement un écran qui s'affiche. Elle vérifie ensuite que
> l'organisme FX-AO n'atteint, **côté serveur**, que ce que l'avis demande. Branche `feature/FX-AO-20260810`.

## 1. Ce que la revue a changé

| Constat | Gravité | Correction | Preuve |
|---|---|---|---|
| **Critère 22 non tenu.** L'alerte critique imposait tous les canaux dans le domaine (`Priorite.canauxImposes`), mais l'envoi revérifiait la préférence canal par canal : le texto qu'une destinataire avait fermé était marqué « désactivé » au lieu de partir. Une préférence contournait la règle obligatoire. Effet aussi sur la poussée critique (critère 18). | Élevée (fonctionnelle, clinique) | Le catalogue des événements déclare `alerte.critique` **obligatoire** ; `Preferences.ouvert` l'honore. Un seul endroit corrige les quatre contrôles : texto, courriel, et poussée aux deux points d'envoi. | FX-14 (journal des livraisons : texto tenté, jamais « désactivé ») ; `EvenementsTest.obligatoirePasseOutreLesPreferences` |
| **Cinq critères « acquis » sans preuve de comportement** : 20, 21, 22, 26, 29 (et 23 prouvé par la seule présence de l'écran) | Moyenne (preuve) | Scénarios FX-14 à FX-17 et parcours `fx-ao-mobile.spec.ts` | ci-dessous ; la grille refuse désormais un critère fonctionnel acquis sans preuve de comportement |
| **Clés d'API servies sans le module** `integrations` : l'administrateur FX-AO pouvait émettre un secret d'accès machine à ses données | Élevée (sécurité) | Toute la famille `/api/integrations` derrière la garde du module | IN-FX-02 ; `ScanAccesParModuleTest` |
| **Groupes transverses servis sans messagerie ni alertes** | Faible | L'un des deux modules exigé | IN-FX-03 |
| **Déni de service par expression régulière** sur le routage des courriels entrants | Moyenne | Recherche bornée en lectures de caractères ; motif et sujet bornés | IN-FX-01 ; `RoutageCourrielBorneTest` |

## 2. Les 32 critères fonctionnels et leur preuve

Les scénarios **en gras** sont ajoutés par cette revue. « Scénario » : joué par l'API contre le Java, jugé sur des valeurs
(`e2e/donnees/fx-ao/comportements.json`, `fx-ao-comportements.spec.ts`). « Essai » : essai Java ou parcours à l'écran.

| # | Critère | Statut | Scénario | Essai |
|---|---|---|---|---|
| 1 | Console web d'envoi centralisée ; alerte par application mobile ou texto selon la préférence du destinataire | ✅ | FX-04 | ControleurAlertesHttpTest |
| 2 | Insertion d'une note au dossier des destinataires | 🟡 | FX-09 | ControleurNotesHttpTest |
| 3 | Recherche de destinataire par nom, prénom | ✅ | FX-04 | — |
| 4 | Messages texte bidirectionnels entre équipes, médecins, utilisateurs autorisés | ✅ | FX-05 | ControleurWebhooksAlertesHttpTest |
| 5 | Envoi à un utilisateur, plusieurs, ou un groupe configurable | ✅ | FX-04 | — |
| 6 | Messages prédéfinis pour les expéditeurs | ✅ | FX-02 | — |
| 7 | Diffusion d'alertes en temps réel | ✅ | FX-04 | — |
| 8 | Copie courriel d'un message, si configurée par l'organisme | ✅ | FX-07 | ControleurAlertesHttpTest |
| 9 | Confirmation de réception, lecture ou prise en charge, simple et automatisée | ✅ | FX-05 | — |
| 10 | Réponses prédéfinies, configurées par les administrateurs | ✅ | FX-01 | — |
| 11 | Statut d'envoi, réception, lecture, réponse et relance, vu par l'expéditeur | ✅ | FX-06 | — |
| 12 | Historique des échanges et statuts partagés entre expéditeurs | ✅ | FX-06 | — |
| 13 | Confirmer la réception d'un retour d'appel | ✅ | FX-05 | AppelsAlertesTest |
| 14 | Appel téléphonique automatique en cas de non-réponse, délai programmable | ✅ | FX-08 | AppelsAlertesTest |
| 15 | Relances automatisées configurables | ✅ | FX-08 | ControleurCascadesHttpTest |
| 16 | Cascades entre utilisateurs d'un groupe ou de groupes distincts | ✅ | FX-08 | ControleurCascadesHttpTest |
| 17 | Délais, séquences, règles d'arrêt et de reprise des cascades | ✅ | FX-08 | ControleurCascadesHttpTest |
| 18 | Alertes critiques, lorsque disponibles et permises | 🟡 | FX-04 | NotificationsEtPousseeTest |
| 19 | Traces des relances, appels, réponses, escalades | ✅ | FX-08 | ControleurCascadesHttpTest |
| 20 | Accès mobile par navigateur, sans équipement dédié | ✅ | — | **fx-ao-mobile** |
| 21 | Application mobile iOS et Android, si proposée | ✅ | **FX-16** | — |
| 22 | Préférences de communication par destinataire, sans contourner les règles obligatoires | ✅ | **FX-14** | — |
| 23 | Interface intuitive, rapide en contexte clinique | ✅ | FX-04 | **fx-ao-mobile** |
| 24 | Historique de ses messages et confirmations selon ses droits | ✅ | FX-05 | — |
| 25 | Calendrier interne de disponibilité des destinataires | ✅ | FX-08 | ControleurCascadesHttpTest |
| 26 | Console de gestion centralisée pour les administrateurs | ✅ | **FX-17** | — |
| 27 | Créer, modifier, désactiver utilisateurs et groupes (unité, service, spécialité, établissement) | ✅ | FX-13 | — |
| 28 | Cloisonner les utilisateurs dans un ou plusieurs groupes | ✅ | FX-04 | ControleurGroupesTransversesHttpTest |
| 29 | Coordonnées des destinataires confidentielles | ✅ | **FX-15** | — |
| 30 | Comptes utilisateur et administrateur, profils plus granulaires | ✅ | FX-03 | — |
| 31 | Écran de visionnement des messages envoyés et des statuts | ✅ | FX-06 | — |
| 32 | Configuration des messages et réponses prédéfinis, relances, groupes, cascades, droits, disponibilité | ✅ | FX-01, FX-02, FX-07, FX-08 | — |

Les deux partiels n'ont pas changé, et leur cause n'est pas dans le code :
- **Critère 2 (🟡)** : la note est livrée dans sa lecture « note interne » ; la réponse du client à Q-DOS est attendue.
- **Critère 18 (🟡)** : la chaîne serveur → Expo → APNs/FCM → téléphone est prouvée au bac à sable. iOS attend le droit
  Critical Alerts d'Apple (Q-ALC), et l'application mobile n'est pas dans ce dépôt.

### Ce que prouvent les ajouts

| Critère | Preuve | Ce qui est constaté |
|---|---|---|
| 20 — accès mobile par navigateur | `fx-ao-mobile.spec.ts`, 390 et 320 px | L'infirmière prend en charge une alerte depuis le navigateur d'un téléphone : aucun défilement horizontal, réponses de 44 px au moins, axe-core sans violation, et l'expéditrice le lit dans son suivi |
| 21 — applications iOS et Android | FX-16 | Un appareil iOS et un appareil Android s'abonnent ; une plateforme inconnue ou un jeton non Expo est refusé |
| 22 — préférences sans contourner l'obligatoire | FX-14 | Fermer tous les canaux écrits est refusé ; fermer le texto est permis ; une alerte critique part quand même par texto |
| 23 — rapide en contexte clinique | `fx-ao-mobile.spec.ts` | Deux gestes depuis l'écran affiché (ouvrir, toucher « J'arrive »), moins de 15 s |
| 26 — console centralisée | FX-17 | Comptes, paramètres des alertes et journal servis à l'administration ; 403 à la destinataire et à la gestionnaire |
| 29 — coordonnées confidentielles | FX-15 ; IN-FX-11 | Un vrai numéro posé sur une fiche n'est rendu ni à l'expéditrice, ni à la destinataire, ni dans la liste de l'administration |

## 3. Le cloisonnement, vérifié par le serveur

`fx-ao-menu.spec.ts` prouvait que le menu ne montre que l'avis. Ce n'est pas une permission. Deux preuves s'y ajoutent :

- **`ScanAccesParModuleTest` (Java)** : un organisme sans aucun module, son administrateur, et chaque route que le
  catalogue attribue à un module. Toutes répondent 403, sauf deux exemptions justifiées (la coquille Swagger, et la
  spécification OpenAPI, identique pour tous). C'est ce balayage qui a trouvé IN-FX-02 et IN-FX-03.
- **`fx-ao-cloisonnement.spec.ts` (e2e, `e2e/donnees/fx-ao/cloisonnement.json`)** : l'organisme réellement semé.
  - Ses **modules sont exactement** `alertes`, `effectifs_communication`, `effectifs_disponibilites`,
    `effectifs_absences`, `effectifs`, plus deux dépendances techniques (`effectifs_planification`,
    `effectifs_regles`), dont les vues restent masquées. Chacun est justifié par un critère ou une dépendance.
  - **Neuf fonctionnalités hors de l'avis répondent 403 à tous les profils**, l'administration comprise : paie, feuilles
    de temps, pointage, échanges, AGPH, connecteurs RH, clés d'API, webhooks, registre Loi 25.
  - **Chaque profil atteint ses écrans et pas ceux d'un autre** : `admin`, `coord.urgence`, `exp.garde.hsj` (rôle
    propre sans administration), `inf.roy`.
  - **Le jeton fait autorité** : un `X-Tenant-Id` étranger est refusé par les gardes de contexte. L'administration des
    comptes, elle, l'ignore et sert l'organisme du jeton : aucune donnée étrangère dans les deux cas.

### Point d'interprétation, non corrigé

Les modules du **cœur** qui n'annoncent que leur menu (`approvals`, `notifications`) servent leurs routes à tout
organisme : c'est le comportement voulu par le catalogue. Pour FX-AO :
- **notifications** : nécessaire (préférences, appareils, critères 21 et 22) ;
- **approbations** : sans usage dans l'avis, et masquées au menu ; leurs routes répondent quand même.

Les fermer touche des flux transverses, comme l'approbation des absences. C'est une décision de produit, à prendre
séparément.

## 4. Résultats

| Campagne | Résultat |
|---|---|
| Les six campagnes FX-AO contre le Java (comportements FX-01 à FX-17, cloisonnement, mobile, grille, menu, parcours) | **32/32** |
| `mvn verify` (balayages par rôle et par module, normes, architecture, couverture ≥ 96 % par classe) | 1 506 essais, 0 échec |
| Tests d'intrusion FX-AO (`securite/intrusion`) | 15/15 ; ZAP : 0 échec — [rapport](../../securite/fx-ao/RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md) |

Rejouer :

```bash
E2E_API_PORT=4031 E2E_WEB_PORT=5331 backend-java/scripts/campagne-e2e.sh tests/e2e/fx-ao-comportements.spec.ts \
  tests/e2e/fx-ao-cloisonnement.spec.ts tests/e2e/fx-ao-mobile.spec.ts tests/e2e/fx-ao-exigences.spec.ts \
  tests/e2e/fx-ao-menu.spec.ts tests/e2e/fx-ao-parcours.spec.ts
securite/intrusion/lancer.sh
```

## 5. Sur la démonstration déployée (23 novembre 2026)

L'organisme montré au client, joint par son hôte <https://clincom.geps.vinaitsolution.com> avec le compte `admin` :

| Contrôle | Résultat |
|---|---|
| Modules accordés | exactement les 7 de la grille (`alertes`, `effectifs`, `effectifs_absences`, `effectifs_communication`, `effectifs_disponibilites`, `effectifs_planification`, `effectifs_regles`) |
| Menu (réglage du superadmin) | 22 vues gardées sur 56 = exactement `vuesGardees` de `demonstration.json` ; aucune attendue masquée, aucune en trop |
| Écrans du menu | chacun s'ouvre, sans erreur, sans violation axe-core |
| Critères fonctionnels avec écran (30) | chaque écran et chaque commande de preuve présents |

Aucun accès n'a eu à être corrigé dans l'espace superadmin. Constat en cours de route : enchaîner les rechargements
d'écrans dépasse la limitation de débit du frontal (20 requêtes/s par adresse, rafale de 40) et fait répondre 503 aux
appels de l'écran suivant ; l'essai ouvre donc les écrans au rythme d'une personne.

Rejouer (aussi joué par la recette après chaque déploiement, une fois la branche fusionnée) :

```bash
cd e2e && E2E_BASE_URL=https://geps.vinaitsolution.com E2E_COMPTES_DEMO=1 \
  npx playwright test --project=e2e tests/e2e/deploiement-fx-ao.spec.ts
```
