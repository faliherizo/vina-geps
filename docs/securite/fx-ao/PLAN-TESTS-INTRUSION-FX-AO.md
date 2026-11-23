# Plan des tests d'intrusion — organisme FX-AO-20260810

> Pour l'équipe de soumission et le responsable de la sécurité de l'information de l'établissement : ce qui est attaqué,
> comment, avec quelle autorisation, et ce que chaque test doit constater. Résultats :
> [RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md](RAPPORT-TESTS-INTRUSION-FX-AO-2026-11-22.md).

## 1. Objet et périmètre

L'avis FX-AO-20260810 (Santé Québec Laurentides, communications cliniques) est servi par VINA GePS à un **organisme
cloisonné**. Il reçoit les alertes, la messagerie, les disponibilités et l'administration, sans paie, sans pointage et
sans intégrations machine. Les tests vérifient trois choses :

1. **Le cloisonnement** : l'organisme n'atteint que ses fonctionnalités, **côté serveur** et pas seulement au menu.
2. **L'isolation** : il ne lit rien d'un autre organisme, et un autre organisme ne lit rien de lui.
3. **Les profils de l'addenda 2 (Q20)** : destinataire, expéditeur sans administration (rôle propre), gestionnaire,
   administration. Chacun reste dans son rôle.

S'y ajoutent les risques propres au métier :
- la confidentialité des coordonnées (critère 29) ;
- l'absence de renseignement de santé (addenda 1, Q1) ;
- l'export du suivi dans un tableur ;
- la disponibilité face à une saisie hostile.

**Hors périmètre** : l'infrastructure de l'hébergeur ; l'application mobile (absente de ce dépôt) ; le déni de service
volumétrique ; l'ingénierie sociale. Les tests de la plateforme générale (en-têtes nginx, authentification, API machine,
CORS, injection) sont ceux du rapport du 22 novembre 2026 (`docs/securite/TESTS-INTRUSION.md`, 57/57).

## 2. Autorisation et cible

| Cible | Autorisation | Quand |
|---|---|---|
| **Pile éphémère** construite dans l'exécuteur ou sur le poste (`securite/intrusion/lancer.sh`, port 8097, secrets tirés au hasard, détruite à la fin) | aucune requise : elle n'existe que pour le test | chaque demande de fusion, chaque semaine, à la main |
| Environnement de **démonstration** (`COMPTES_DEMO=1`) | le propriétaire du dépôt ; le pipeline refuse tout autre environnement | à la main |
| Recette ou production d'un client | **autorisation écrite** de son exploitant, fenêtre convenue ; jamais par le pipeline | hors de ce plan |

La suite **écrit** : organismes, comptes, alertes, fiches. Elle ne vise donc jamais une base qui porte des données réelles.

## 3. Méthode

- **Référentiels** : OWASP API Security Top 10 (2023), OWASP ASVS 4.0.3 (niveau 2), CWE.
- **Boîte grise** : le testeur a les comptes de chaque profil, comme un utilisateur malveillant de l'établissement, et
  connaît l'API. C'est le scénario réaliste d'un réseau de santé : la menace vient d'un compte légitime.
- **Trois niveaux, complémentaires** :
  1. **Balayage exhaustif par le registre de Spring** (essais Java, à chaque `mvn verify`) : `ScanAccesParRoleTest`
     appelle chaque route avec chaque rôle ; `ScanAccesParModuleTest` appelle chaque route de module avec un organisme
     qui n'a aucun module. Aucune route ne peut être oubliée d'une liste tenue à la main.
  2. **Suite scriptée** contre la pile réelle, nginx compris (`securite/intrusion/fx-ao/*.test.mjs`) : les scénarios
     d'attaque, sur l'organisme semé par `scripts/semer-fx-ao.cjs`.
  3. **Balayage dynamique passif** (OWASP ZAP 2.16.1, `zap-baseline`) : en-têtes, témoins, fuites d'information.
- **Analyse du code et des composants** (pipeline DevSecOps, `docs/securite/DEVSECOPS.md`) : secrets, Semgrep, Trivy
  (dépendances, infrastructure, images).

## 4. Catalogue des tests scriptés

| ID | Risque | Référence | Scénario | Attendu |
|---|---|---|---|---|
| IN-FX-01 | Déni de service par expression régulière | CWE-1333, API4 | L'administrateur d'un organisme qui a les intégrations route un courriel entrant avec le motif `(a+)+$` contre 64 « a » suivis de « ! » | réponse en moins de 5 s ; le motif est abandonné et la route suivante essayée |
| IN-FX-01b | Fonction réservée | API5 | Un employé appelle le routage des courriels entrants | 403 |
| IN-FX-02 | Fonction non accordée | API5 | L'administrateur FX-AO liste et crée une clé d'API (module `integrations` non accordé) | 403, aucun `clientSecret` rendu |
| IN-FX-03 | Fonction non accordée | API5 | Un organisme qui n'a ni messagerie ni alertes lit les groupes transverses et cherche des personnes par nom | 403 — prouvé par l'essai Java (`ScanAccesParModuleTest`, `ControleurGroupesTransversesHttpTest`), FX-AO ayant les alertes |
| IN-FX-04 | Fonction non accordée | API5 | L'administrateur FX-AO appelle la paie, le temps, le pointage, les échanges, l'AGPH, les connecteurs, les webhooks, le registre Loi 25 | 403 partout |
| IN-FX-05 | Fonction non accordée | API5 | Les utilitaires d'intégration (`inbound-email`, `export.xml`, `sso/map-role`) sans le module | 403 |
| IN-FX-06 | Accès à l'objet d'un autre organisme | API1 (BOLA) | Une alerte de l'organisme témoin lue par la coordonnatrice FX-AO par son identifiant ; puis avec `X-Tenant-Id` du témoin ; une infirmière FX-AO y répond | 403 ou 404, aucun contenu ; 403 sur l'en-tête discordant |
| IN-FX-07 | Fuite entre organismes | API1 | L'administrateur FX-AO liste les comptes avec `X-Tenant-Id` du témoin | 403, ou seulement ses propres comptes |
| IN-FX-08 | Élévation verticale | API5 | Une destinataire envoie une alerte, ordinaire puis critique | 403 |
| IN-FX-09 | Élévation verticale | API5 | L'expéditeur de garde (rôle propre) : envoie une urgente (permis), une critique, administre les comptes, les réponses, les réglages, lit le journal | 201 puis 403 partout ailleurs |
| IN-FX-10 | Affectation de masse | API3 | Un compte se modifie avec `role: admin`, `tenantId` d'un autre organisme, `capabilities` | refus, rôle inchangé |
| IN-FX-11 | Exposition de coordonnées | API3, critère 29 | Un numéro réel posé sur une fiche ; lu par chaque profil par la liste, la fiche et la recherche | numéro absent de toute réponse |
| IN-FX-12 | Renseignement de santé | addenda 1 Q1, Loi 25 | Une note au dossier qui contient un numéro d'assurance maladie | 400 |
| IN-FX-13 | Injection de formule | CWE-1236 | Une alerte titrée `=HYPERLINK(...)`, puis l'export CSV du suivi | aucune cellule ne commence par `=` |
| IN-FX-14 | Script intersite | CWE-79 | Une alerte qui porte `<img onerror>` et `<script>` | rendue en `application/json`, `nosniff`, CSP `default-src 'none'` |
| IN-FX-15 | Force brute | ASVS 2.2.1 | 12 mots de passe faux sur un compte FX-AO | verrou (423, 429 ou 403) |

## 5. Les jouer

```bash
securite/intrusion/lancer.sh                 # pile éphémère, suite FX-AO, démontage
ZAP=1 securite/intrusion/lancer.sh           # + ZAP passif
CIBLE=https://geps.vinaitsolution.com SUPERADMIN_PASSWORD='…' securite/intrusion/lancer.sh   # démonstration autorisée
```

Ou depuis GitHub : Actions › **Tests d'intrusion** › Run, avec la cible `ephemere` ou `environnement` ; l'orchestrateur
**DevSecOps** le joue aussi sur chaque demande de fusion. Résultats : `securite/intrusion/resultats/<horodatage>/`, ou
l'artefact `intrusion-*` (journal, JUnit, rapport ZAP).

Conditions :
- Node 22 et Docker.
- Une pause de 80 ms entre deux appels (`INTRUSION_PAUSE_MS`) : le frontal limite à 20 requêtes par seconde, et un test
  qui l'atteindrait mesurerait la limite plutôt que l'application.

## 6. Ajouter un test

Un fichier `securite/intrusion/fx-ao/NN-theme.test.mjs`, et une assertion par risque qui porte un identifiant
`IN-FX-nn`. Puis une ligne au catalogue ci-dessus et au rapport. Un test d'intrusion dit **ce qui doit être refusé**.
Il ne se contente pas d'un statut : il vérifie aussi que la donnée n'a pas fui (contenu, secret, numéro).
