# Diagrammes de séquence

## SQ-01 — Envoi et distribution d'une communication
```mermaid
sequenceDiagram
 actor Exp as Expéditeur
 participant UI as Portail Web
 participant API as API
 participant IAM as Service identité
 participant MSG as Communication
 participant RES as Résolveur destinataires
 participant POL as Moteur règles
 participant ORC as Orchestrateur
 participant CH as Adaptateur canal
 participant AUD as Audit
 Exp->>UI: Composer et envoyer
 UI->>API: POST /communications
 API->>IAM: Vérifier identité et permission
 IAM-->>API: Autorisé
 API->>MSG: Créer demande
 MSG->>RES: Résoudre cible/groupe
 RES-->>MSG: Destinataires + snapshot
 MSG->>POL: Évaluer règles et préférences
 POL-->>MSG: Plan de distribution
 MSG->>AUD: Événement création
 MSG->>ORC: Démarrer distribution
 ORC->>CH: Envoyer tentative
 CH-->>ORC: Résultat fournisseur
 ORC->>AUD: Tracer tentative
 ORC-->>UI: Statut initial
```

## SQ-02 — Réponse, confirmation et arrêt de cascade
```mermaid
sequenceDiagram
 actor Dest as Destinataire
 participant Canal as SMS/Push/Web
 participant API as API
 participant MSG as Communication
 participant ORC as Orchestrateur
 participant DB as Persistance
 participant AUD as Audit
 Dest->>Canal: Confirmer / répondre
 Canal->>API: Webhook ou action authentifiée
 API->>MSG: Enregistrer événement
 MSG->>DB: Persister réponse et statut
 MSG->>AUD: Ajouter événement
 MSG->>ORC: Évaluer condition d'arrêt
 ORC->>DB: Charger instance et politique versionnée
 alt Condition atteinte
  ORC->>DB: Clore instance
 else Autre étape nécessaire
  ORC->>DB: Planifier étape suivante
 end
 API-->>Canal: Accusé technique
```

## SQ-03 — Déclenchement d'une escalade
```mermaid
sequenceDiagram
 participant Timer as Scheduler durable
 participant ORC as Orchestrateur
 participant DB as Base
 participant RES as Résolveur
 participant DIST as Distribution
 participant AUD as Audit
 Timer->>ORC: Échéance arrivée
 ORC->>DB: Charger instance + version politique
 ORC->>DB: Vérifier état courant / idempotence
 ORC->>RES: Résoudre prochaine cible
 RES-->>ORC: Cibles actives
 ORC->>DIST: Demander nouvelle diffusion
 DIST-->>ORC: Résultat
 ORC->>DB: Avancer étape / prochaine échéance
 ORC->>AUD: Tracer déclenchement
```

## SQ-04 — Synchronisation annuaire
```mermaid
sequenceDiagram
 participant IAM as Annuaire/GIA
 participant AD as Adaptateur
 participant MAP as Mapping dynamique
 participant DIR as Référentiel utilisateurs
 participant AUD as Audit
 IAM-->>AD: Flux/lecture autorisée
 AD->>MAP: Résoudre correspondances
 MAP-->>AD: Mapping versionné
 AD->>DIR: Upsert identités/affectations
 DIR-->>AD: Résultat
 AD->>AUD: Tracer import, erreurs et suspensions
```

## SQ-05 — Extraction des utilisateurs actifs facturables
```mermaid
sequenceDiagram
 participant JOB as Tâche de facturation
 participant ACT as Activités de diffusion
 participant BILL as Service facturation
 participant DB as Base
 participant ORG as Organisme
 JOB->>ACT: Charger tentatives du mois
 ACT-->>JOB: Utilisateurs ciblés au moins une fois
 JOB->>BILL: Dédupliquer par utilisateur/mois
 BILL->>DB: Persister agrégat et preuves
 ORG->>BILL: Demander détail de validation
 BILL->>DB: Charger détail et règles de calcul
 BILL-->>ORG: Export détaillé
```
