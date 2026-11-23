# Rapport des tests d'intrusion — organisme FX-AO-20260810 — 22 novembre 2026

> Pour l'équipe de soumission et le responsable de la sécurité de l'information de l'établissement. Plan, périmètre et
> méthode : [PLAN-TESTS-INTRUSION-FX-AO.md](PLAN-TESTS-INTRUSION-FX-AO.md). Branche `feature/FX-AO-20260810`.

## Synthèse

| | |
|---|---|
| Cible | Pile éphémère (`securite/intrusion/lancer.sh`) : serveur Java et frontal nginx construits depuis la branche, secrets tirés au hasard, organisme FX-AO semé par `scripts/semer-fx-ao.cjs`, organisme témoin |
| Tests scriptés | **15/15** après correctifs |
| Balayage exhaustif des routes | `ScanAccesParRoleTest` et `ScanAccesParModuleTest` verts, dans `mvn verify` (1 506 essais, 0 échec) |
| ZAP 2.16.1 (passif) | **0 échec**, 58 contrôles passés, 9 avertissements de 4 types (triés en § 3) |
| Constats | **5 corrigés** (2 élevés, 2 moyens, 1 faible) ; **4 risques acceptés et justifiés** ; aucun critique ouvert |

## 1. Constats

| ID | Constat | Gravité | Statut |
|---|---|---|---|
| IN-FX-02 | **Clés d'API sans le module.** `/api/integrations/api-clients` n'exigeait que le rôle `admin`. L'administrateur FX-AO, dont l'organisme n'a pas le module `integrations`, pouvait émettre un client OAuth2 et son secret : un accès machine permanent aux données de l'organisme, hors de tout écran. | **Élevée** (CVSS 3.1 ≈ 7.2 : AV:N/AC:L/PR:H/UI:N/S:U/C:H/I:H/A:N) | ✅ Corrigé : toute la famille `/api/integrations` passe par la garde du module (`ControleurIntegrations.exigerAdministration`) |
| FN-22 | **Une préférence contournait l'alerte critique** (critère 22 ; et 18). Le domaine imposait tous les canaux, mais l'envoi revérifiait la préférence canal par canal : le texto fermé par une infirmière était « désactivé ». Une alerte critique pouvait donc ne joindre personne par le canal qui réveille. | **Élevée** (fonctionnelle, clinique) | ✅ Corrigé : `alerte.critique` est **obligatoire** au catalogue ; `Preferences.ouvert` l'honore |
| IN-FX-01 | **Déni de service par expression régulière** (CWE-1333). `/api/integrations/inbound-email` compilait un motif fourni par l'appelant contre un sujet fourni par lui ; tout compte d'un organisme, même employé, pouvait occuper un fil du serveur avec `(a+)+$`. | **Moyenne** | ✅ Corrigé : recherche bornée à 2 millions de lectures de caractères (borne déterministe, sans horloge), motif ≤ 256 caractères, sujet tronqué à 998 ; route réservée à l'administration d'un organisme qui a le module |
| IN-FX-05 | Utilitaires d'intégration (`export.xml`, `sso/map-role`, `inbound-email`) ouverts à tout compte, quel que soit le module | **Moyenne** (surface) | ✅ Corrigé avec IN-FX-02 |
| IN-FX-03 | **Groupes transverses** servis à un organisme qui n'a ni messagerie ni alertes : liste des groupes, recherche de personnes par nom | **Faible** | ✅ Corrigé : l'un des deux modules exigé |
| ZAP 90004 | Isolation de l'origine (Spectre) : ni `Cross-Origin-Resource-Policy` ni `Cross-Origin-Opener-Policy` | Faible | ✅ Corrigé : CORP sur toutes les réponses statiques, COOP sur les documents (essai `nginx-conf.test.ts`) |
| SAST | Réponse JSON du pare-feu écrite par concaténation | Faible (aucune saisie n'y entre aujourd'hui) | ✅ Corrigé : sérialisée |

**Comment les constats ont été trouvés.**
- IN-FX-02, IN-FX-03 et IN-FX-05 : par **`ScanAccesParModuleTest`**, écrit pour cette campagne. Il appelle, pour un
  organisme sans aucun module, chaque route que le catalogue attribue à un module.
- FN-22 : en écrivant la preuve de comportement du critère 22. La règle était déclarée, mais jamais vérifiée de bout en
  bout.
- IN-FX-01 : par lecture du code d'une route qui compile une expression fournie.

## 2. Résultats des tests scriptés

Dernière exécution : `securite/intrusion/resultats/20261122-234420/`.

| ID | Test | Résultat |
|---|---|---|
| IN-FX-01 | Motif catastrophique : réponse en moins de 5 s, route suivante essayée | ✅ ~0,3 s |
| IN-FX-01b | Routage des courriels refusé à un employé | ✅ 403 |
| IN-FX-02 | Clé d'API refusée à l'administrateur FX-AO, aucun secret rendu | ✅ 403 |
| IN-FX-03 | Groupes transverses sans module (essai Java) | ✅ 403 |
| IN-FX-04 | Paie, temps, pointage, échanges, AGPH, connecteurs, webhooks, registre Loi 25 | ✅ 403 partout |
| IN-FX-05 | Utilitaires d'intégration sans le module | ✅ 403 |
| IN-FX-06 | Alerte du témoin lue ou répondue depuis FX-AO ; `X-Tenant-Id` étranger | ✅ 404 / 403, aucun contenu |
| IN-FX-07 | Comptes du témoin depuis l'administration FX-AO | ✅ seulement ses propres comptes (en-tête ignoré, jeton fait autorité) |
| IN-FX-08 | Destinataire qui envoie une alerte, ordinaire ou critique | ✅ 403 |
| IN-FX-09 | Expéditeur de garde : envoie une urgente ; refus de critique, comptes, réponses, réglages, journal | ✅ 201 puis 403 |
| IN-FX-10 | Auto-promotion (`role`, `tenantId`, `capabilities`) | ✅ refusée, rôle inchangé |
| IN-FX-11 | Numéro réel absent de toute réponse, pour chaque profil | ✅ |
| IN-FX-12 | Note au dossier avec un numéro d'assurance maladie | ✅ 400 |
| IN-FX-13 | Formule dans le titre, export CSV | ✅ neutralisée |
| IN-FX-14 | Balise dans une alerte | ✅ `application/json`, `nosniff`, CSP `default-src 'none'` |
| IN-FX-15 | 12 échecs de connexion | ✅ verrou |

**Avant correctif**, mesuré sur la démonstration publique (version `develop` du 22 novembre, lecture seule) :
`GET /api/integrations/api-clients` répondait **200** à l'administrateur FX-AO. Le test IN-FX-01 n'a pas été joué
contre un serveur partagé : il l'aurait occupé. Sa preuve est l'essai `RoutageCourrielBorneTest` (40 « a » + « ! » :
abandon en moins de 5 s) et la lecture du code d'origine.

## 3. Balayage ZAP — avertissements triés

| Alerte | Occurrences | Décision |
|---|---|---|
| Insufficient Site Isolation Against Spectre (90004) | 5 | **Accepté** : il reste seulement `Cross-Origin-Embedder-Policy`. Il ne sert qu'à l'isolation complète (requise pour `SharedArrayBuffer`, que l'application n'emploie pas), et il bloquerait toute ressource intégrée sans CORP. CORP et COOP sont posés. |
| Private IP Disclosure (2) | 1 | **Faux positif** : `10.12.0.0/16` est la valeur d'exemple du champ « Plages réseau » dans une fiche d'aide (`fichesEcrans.ts`) |
| Modern Web Application (10109) | 4 | Informatif : application à page unique |
| ZAP is Out of Date (10116) | 1 | Informatif : version épinglée pour la reproductibilité |

## 4. Analyse du code et des composants (pipeline DevSecOps)

| Porte | Constats | Après |
|---|---|---|
| Images (Trivy) | Serveur : Tomcat 11.0.22 (**5 critiques**), Spring Web MVC 7.0.8 (**2 critiques**), Jackson 2.21.4 / 3.1.4, pilote PostgreSQL 42.7.11. Interface : Alpine 3.21.3, puis nginx 1.28.2 (**5 critiques**) | Tomcat 11.0.25, Spring 7.0.9, Jackson 2.21.7 / 3.1.7, PostgreSQL 42.7.13 ; nginx 1.29.8 sur Alpine 3.23 + `apk upgrade` → **0 élevée ou critique corrigeable** dans les deux images |
| Secrets (gitleaks) | 14 jetons de session d'essai (`e2e/.auth/admin.json`) et 4 secrets de clients d'API de campagnes locales (`perf/jmeter/resultats/`), suivis par git ; 15 fixtures d'essai | fichiers retirés du suivi, fixtures déclarées, constats historiques figés (valeurs masquées) → **0 nouveau** |
| Infrastructure (Trivy) | `frontend/Dockerfile` sans `USER` ; 3 constats Terraform AWS | `USER 101` ; 3 justifiés en ligne |
| Analyse statique (Semgrep) | 0 ERROR ; 5 avertissements | 1 corrigé, 4 justifiés (`docs/securite/DEVSECOPS.md`) |

Les versions de Tomcat, Spring et Jackson sont surchargées dans le `pom.xml`, au-dessus de celles que gère Spring
Boot 4.0.7. Ce sont des versions de correctif de la même branche, et `mvn verify` passe en entier (1 506 essais, normes,
architecture, couverture). La surcharge est à retirer au passage à un parent qui les gère.

## 5. Risques acceptés, à revoir

| Risque | Pourquoi accepté | Qui décide |
|---|---|---|
| Les routes d'**approbations** répondent à FX-AO, qui n'en a pas l'usage (vue masquée) | Module du cœur sans garde serveur par conception ; le fermer touche des flux transverses (approbation des absences) | Produit |
| Le **courriel** d'un collègue est lisible de tout compte de l'organisme (fiches du personnel) | Le critère 29 vise « les numéros ou coordonnées » : le numéro n'est jamais rendu ; l'annuaire de courriel est l'usage attendu d'un réseau de santé | Établissement (Q à ajouter aux questions au client) |
| L'historique Git conserve les 18 constats retirés du suivi | Des jetons et secrets d'**essais locaux** (serveurs éphémères), sans valeur hors de ces piles ; réécrire l'historique d'un dépôt partagé demande l'accord de son propriétaire | Propriétaire du dépôt |
| `Cross-Origin-Embedder-Policy` absent | § 3 | — |

## 6. Rejouer

```bash
ZAP=1 securite/intrusion/lancer.sh
(cd backend-java && mvn -o verify)          # ScanAccesParRoleTest, ScanAccesParModuleTest
```

Ou Actions › **Tests d'intrusion** (pile éphémère ; hebdomadaire le lundi), et Actions › **DevSecOps** pour toutes les
portes.
