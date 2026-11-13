# Diagrammes C4 — C1 à C4

Les diagrammes sont fournis en Mermaid/PlantUML source pour être versionnés et régénérés. Les noms de composants sont logiques; les technologies restent indicatives.

## C1 — Contexte système
```mermaid
flowchart LR
  Clin["Personnel clinique / médecins"] -->|Envoie, reçoit, confirme| SYS["Solution de communications cliniques"]
  Admin["Administrateurs / pilotes"] -->|Configure et administre| SYS
  Audit["Gestionnaires / auditeurs"] -->|Consulte rapports et traces| SYS
  SYS -->|SMS / appels / notifications| Telco["Fournisseurs télécom / SMS / voix"]
  SYS <-->|Identité / annuaire| IAM["GIA / annuaire institutionnel"]
  SYS <-->|Courriel| Mail["Microsoft 365 / Outlook"]
  SYS <-->|Import / synchronisation| Ref["Référentiels organisationnels"]
  Ops["Équipe infrastructure et sécurité"] <-->|Surveillance / sauvegarde / incidents| SYS
```

## C2 — Conteneurs
```mermaid
flowchart TB
  subgraph Users["Acteurs"]
    U1["Expéditeur / destinataire"]
    U2["Administrateur"]
  end
  subgraph Platform["Plateforme"]
    WEB["Portail Web responsive
React / TypeScript"]
    API["API Gateway / BFF"]
    IAM["Adaptateur identité"]
    ORG["Service organisation & annuaire"]
    MSG["Service communications"]
    POL["Service politiques & modèles"]
    RES["Résolveur de destinataires"]
    ESC["Orchestrateur relances / cascades"]
    CH["Adaptateurs de canaux"]
    AUD["Audit & rapports"]
    DB[("Base relationnelle")]
    BUS[("Transport événements")]
  end
  SMS["SMS / voix / push"]
  M365["Microsoft 365"]
  AD["GIA / LDAP(S)"]
  U1 --> WEB
  U2 --> WEB
  WEB --> API
  API --> IAM
  API --> ORG
  API --> MSG
  MSG --> POL
  MSG --> RES
  RES --> ORG
  MSG --> ESC
  MSG --> DB
  ESC --> BUS
  BUS --> CH
  CH --> SMS
  CH --> M365
  IAM <--> AD
  MSG --> AUD
  ESC --> AUD
  CH --> AUD
  AUD --> DB
```

## C3 — Composants du service de communication
```mermaid
flowchart TB
  API["CommunicationController"] --> APP["SendCommunicationUseCase"]
  APP --> AUTH["AuthorizationPolicy"]
  APP --> VALID["MessageValidator"]
  APP --> RES["RecipientResolutionPort"]
  APP --> POL["CommunicationPolicyPort"]
  APP --> REPO["CommunicationRepository"]
  APP --> OUT["DistributionRequestPort"]
  RES --> GROUP["GroupMembershipResolver"]
  RES --> AVAIL["AvailabilityResolver"]
  POL --> PREF["PreferencePolicy"]
  POL --> MAND["MandatoryRuleEvaluator"]
  OUT --> EVENT["DistributionRequested event"]
  REPO --> DB[("Communication DB")]
```

## C4 — Déploiement logique
```mermaid
flowchart TB
  subgraph Client["Postes et mobiles"]
    Browser["Navigateur Windows 11 / mobile"]
  end
  subgraph Edge["Zone d'accès"]
    WAF["WAF / Reverse proxy"]
    GW["API Gateway"]
  end
  subgraph App["Zone applicative"]
    WEB["Frontend statique"]
    API["Services applicatifs"]
    WORK["Workers distribution / escalade"]
    BUS[("File / événements")]
  end
  subgraph Data["Zone données protégée"]
    DB[("Base relationnelle chiffrée")]
    LOG[("Journaux / audit")]
    BK["Sauvegardes chiffrées"]
  end
  EXT["Services externes
GIA, SMS, voix, M365"]
  MON["Surveillance / SIEM / sauvegarde institutionnelle"]
  Browser --> WAF --> GW
  GW --> WEB
  GW --> API
  API --> DB
  API --> BUS
  BUS --> WORK
  WORK --> EXT
  API --> LOG
  WORK --> LOG
  DB --> BK
  LOG --> MON
  API --> MON
```

## Lecture et réserves
- C1 expose les acteurs et systèmes externes.
- C2 montre les conteneurs logiques et leurs échanges.
- C3 détaille le service central de communication.
- C4 représente les zones de déploiement sans imposer de fournisseur cloud.
- Les flux réels, zones réseau, certificats, protocoles et frontières de confiance doivent être validés par l'architecture institutionnelle.
