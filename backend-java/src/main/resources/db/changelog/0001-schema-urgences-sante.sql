-- Schéma d'Urgences-santé, base vierge.
-- Engendré à partir des tables que le serveur Java lit ou écrit : celles du socle relevées sur une base
-- bâtie par les migrations du produit d'origine (commit 52087a5), celles du module relevées dans
-- schema/module-urgences-sante.sql. Une table ajoutée au code exige une migration Liquibase nouvelle,
-- jamais la modification de ce fichier.
CREATE TABLE achat_attributions (
          id            TEXT PRIMARY KEY,
          tenantId      TEXT NOT NULL,
          contratRef    TEXT NOT NULL,
          projetId      TEXT,
          fournisseurId TEXT,
          montant       REAL NOT NULL,
          mode          TEXT NOT NULL,
          modeRequis    TEXT NOT NULL,
          derogation    INTEGER NOT NULL DEFAULT 0,
          justification TEXT,
          auteurId      TEXT,
          attribueLe    TEXT NOT NULL,
          creeLe        TEXT NOT NULL
        );
CREATE INDEX idx_achat_attributions ON achat_attributions (tenantId, projetId);
CREATE TABLE annonces (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          titre     TEXT NOT NULL,
          corps     TEXT NOT NULL,
          rolesJson TEXT NOT NULL DEFAULT '[]',
          du        TEXT NOT NULL,
          au        TEXT NOT NULL,
          auteur    TEXT NOT NULL,
          creeeLe   TEXT NOT NULL
        );
CREATE INDEX idx_annonces_tenant_periode ON annonces (tenantId, du, au);
CREATE TABLE annonces_lectures (
          annonceId TEXT NOT NULL,
          userId    TEXT NOT NULL,
          luLe      TEXT NOT NULL,
          PRIMARY KEY (annonceId, userId)
        );
CREATE TABLE ap_bills (
        id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, supplierId TEXT NOT NULL, billNumber TEXT NOT NULL,
        date TEXT NOT NULL, dueDate TEXT NOT NULL, currency TEXT NOT NULL DEFAULT 'CAD',
        amount REAL NOT NULL, paidAmount REAL NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'open',
        poId TEXT, createdAt TEXT NOT NULL
      , bookedRate REAL NOT NULL DEFAULT 1, supplierInvoiceNo TEXT, baseAmount REAL, tpsAmount REAL, tvqAmount REAL, receiptDate TEXT, accountingPeriod TEXT, paymentTermDays INTEGER, paiementSepare INTEGER NOT NULL DEFAULT 0, approbationEtape TEXT NOT NULL DEFAULT 'saisie', exerciceOrigine TEXT, reporteDe TEXT);
CREATE INDEX idx_ap_bills ON ap_bills (tenantId, supplierId, status);
CREATE TABLE api_clients (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, name TEXT NOT NULL, clientId TEXT NOT NULL UNIQUE, clientSecretHash TEXT NOT NULL, scopes TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, createdAt TEXT NOT NULL, lastUsedAt TEXT);
CREATE TABLE approval_delegations (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, fromUserId TEXT NOT NULL, toUserId TEXT NOT NULL, createdAt TEXT NOT NULL, fromDate TEXT, toDate TEXT);
CREATE TABLE approval_document_types (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          code      TEXT NOT NULL,
          labelFr   TEXT NOT NULL,
          labelEn   TEXT NOT NULL,
          labelMg   TEXT NOT NULL,
          systeme   INTEGER NOT NULL DEFAULT 0,
          sortOrder INTEGER NOT NULL DEFAULT 0,
          active    INTEGER NOT NULL DEFAULT 1,
          createdAt TEXT NOT NULL
        );
CREATE UNIQUE INDEX idx_appr_doctypes_code ON approval_document_types (tenantId, code);
CREATE TABLE approval_requests (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, documentType TEXT NOT NULL, documentId TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', createdAt TEXT NOT NULL, requestedBy TEXT);
CREATE TABLE approval_rules (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, documentType TEXT NOT NULL, field TEXT NOT NULL, operator TEXT NOT NULL, value TEXT NOT NULL, level INTEGER NOT NULL, approverRole TEXT, approverId TEXT, createdAt TEXT NOT NULL);
CREATE TABLE approval_steps (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, requestId TEXT NOT NULL, level INTEGER NOT NULL, approverRole TEXT, approverId TEXT, status TEXT NOT NULL DEFAULT 'pending', decidedBy TEXT, decidedAt TEXT, additional INTEGER NOT NULL DEFAULT 0, onBehalfOf TEXT);
CREATE TABLE audit_log (id TEXT PRIMARY KEY, at TEXT NOT NULL, userId TEXT, username TEXT, action TEXT NOT NULL, target TEXT, details TEXT, empreinte TEXT, acteurReel TEXT);
CREATE TABLE audit_revues (id TEXT PRIMARY KEY, du TEXT NOT NULL, au TEXT NOT NULL, entrees INTEGER NOT NULL, empreinteFin TEXT, reviseurId TEXT NOT NULL, reviseurNom TEXT NOT NULL, constat TEXT NOT NULL, faiteLe TEXT NOT NULL);
CREATE TABLE auth_annuaires (
          id              TEXT PRIMARY KEY,
          tenantId        TEXT NOT NULL,
          nom             TEXT NOT NULL,
          locataireEntra  TEXT NOT NULL,
          clientId        TEXT NOT NULL,
          variableSecret  TEXT NOT NULL,
          domaines        TEXT NOT NULL DEFAULT '[]',
          revendication   TEXT NOT NULL DEFAULT 'groups',
          correspondances TEXT NOT NULL DEFAULT '[]',
          roleParDefaut   TEXT,
          actif           INTEGER NOT NULL DEFAULT 1,
          creeLe          TEXT NOT NULL,
          creePar         TEXT NOT NULL,
          modifieLe       TEXT
        );
CREATE UNIQUE INDEX uq_auth_annuaires_nom ON auth_annuaires (tenantId, nom);
CREATE TABLE auth_emprunts (id TEXT PRIMARY KEY, tenantId TEXT, emprunteurId TEXT NOT NULL, cibleId TEXT NOT NULL, motif TEXT NOT NULL, debuteLe TEXT NOT NULL, expireLe TEXT NOT NULL, termineLe TEXT);
CREATE INDEX idx_auth_emprunts_tenant ON auth_emprunts (tenantId, debuteLe);
CREATE TABLE auth_invitations (
          id          TEXT PRIMARY KEY,
          -- NULL pour une invitation posée par un superadmin hors établissement.
          tenantId    TEXT,
          courriel    TEXT NOT NULL,
          username    TEXT NOT NULL,
          role        TEXT NOT NULL,
          employeeId  TEXT,
          -- SHA-256 du jeton. Unique : deux invitations ne peuvent pas partager un lien.
          empreinte   TEXT NOT NULL UNIQUE,
          expireLe    TEXT NOT NULL,
          accepteeLe  TEXT,
          -- Le compte créé en acceptant : la trace de ce que l'invitation a ouvert.
          compteId    TEXT,
          revoqueeLe  TEXT,
          creeLe      TEXT NOT NULL,
          creePar     TEXT NOT NULL
        );
CREATE INDEX idx_auth_invitations_tenant
          ON auth_invitations (tenantId, accepteeLe, revoqueeLe, expireLe);
CREATE TABLE auth_photos (
          userId    TEXT PRIMARY KEY,
          typeMime  TEXT NOT NULL,
          taille    INTEGER NOT NULL,
          contenu   TEXT NOT NULL,
          modifieLe TEXT NOT NULL
        );
CREATE TABLE auth_reglages_securite (tenantId TEXT PRIMARY KEY, empruntIdentite INTEGER NOT NULL DEFAULT 0, modifieLe TEXT, modifiePar TEXT);
CREATE TABLE bank_transactions (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, bankAccountId TEXT NOT NULL, date TEXT NOT NULL, amount REAL NOT NULL, description TEXT, reconciledPaymentId TEXT, createdAt TEXT NOT NULL, category TEXT, status TEXT NOT NULL DEFAULT 'pending', txType TEXT, glEntryId TEXT, payee TEXT);
CREATE TABLE billing_invoices (id TEXT PRIMARY KEY, number TEXT NOT NULL, tenantId TEXT NOT NULL, periodStart TEXT NOT NULL, periodEnd TEXT NOT NULL, userCount INTEGER NOT NULL, unitPrice REAL NOT NULL, subtotal REAL NOT NULL, gst REAL NOT NULL, qst REAL NOT NULL, total REAL NOT NULL, status TEXT NOT NULL DEFAULT 'issued', createdAt TEXT NOT NULL, paidAt TEXT);
CREATE TABLE budgets (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, name TEXT NOT NULL, fiscalYear INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'draft', createdAt TEXT NOT NULL);
CREATE TABLE card_transactions (
        id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, cardId TEXT NOT NULL, date TEXT NOT NULL,
        amount REAL NOT NULL, currency TEXT NOT NULL DEFAULT 'CAD', merchant TEXT, matchedExpenseId TEXT, createdAt TEXT NOT NULL
      );
CREATE INDEX idx_card_tx ON card_transactions (tenantId, cardId);
CREATE TABLE client_invoices (
          id           TEXT PRIMARY KEY,
          tenantId     TEXT NOT NULL,
          invoiceNumber TEXT NOT NULL,
          date         TEXT NOT NULL,
          dueDate      TEXT,
          currency     TEXT NOT NULL,
          taxProfile   TEXT NOT NULL,
          clientName   TEXT NOT NULL,
          subtotal     REAL NOT NULL,
          totalTax     REAL NOT NULL,
          total        REAL NOT NULL,
          status       TEXT NOT NULL DEFAULT 'draft',
          input_json   TEXT NOT NULL,
          createdAt    TEXT NOT NULL,
          updatedAt    TEXT NOT NULL
        , contractId TEXT);
CREATE INDEX idx_client_invoices_contract ON client_invoices (tenantId, contractId);
CREATE INDEX idx_client_invoices_tenant ON client_invoices (tenantId, status);
CREATE TABLE courriel_message (
          id            INTEGER PRIMARY KEY AUTOINCREMENT,
          tenant_id     VARCHAR(100),
          destinataire  VARCHAR(320) NOT NULL,
          contexte      VARCHAR(100),
          statut        VARCHAR(30) NOT NULL,
          fournisseur   VARCHAR(30),
          provider_id   VARCHAR(255),
          created_at    TIMESTAMP NOT NULL,
          error_message TEXT
        );
CREATE INDEX idx_courriel_message_tenant ON courriel_message (tenant_id, created_at);
CREATE TABLE courriel_quota (
          tenant_id     VARCHAR(100) PRIMARY KEY,
          monthly_limit INTEGER NOT NULL
        );
CREATE TABLE credit_notes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, supplierId TEXT NOT NULL, poId TEXT, amount REAL NOT NULL, reason TEXT, status TEXT NOT NULL DEFAULT 'open', createdAt TEXT NOT NULL, appliedAt TEXT);
CREATE TABLE departments (
      id        TEXT PRIMARY KEY,
      name      TEXT NOT NULL,
      parentId  TEXT
    , tenantId TEXT NOT NULL DEFAULT 'default');
CREATE INDEX idx_departments_tenant ON departments (tenantId);
CREATE TABLE employees (
      id            TEXT PRIMARY KEY,
      firstName     TEXT NOT NULL,
      lastName      TEXT NOT NULL,
      sin           TEXT,
      email         TEXT,
      phone         TEXT,
      address       TEXT,
      hireDate      TEXT NOT NULL,
      terminationDate TEXT,
      status        TEXT NOT NULL DEFAULT 'active',
      annualSalary  REAL NOT NULL,
      frequency     TEXT NOT NULL,
      province      TEXT NOT NULL,
      role          TEXT NOT NULL,
      jobTitle      TEXT,
      departmentId  TEXT,
      managerId     TEXT,
      bankAccount   TEXT,
      weeklyCapacityHours REAL NOT NULL DEFAULT 40,
      ytd_json      TEXT NOT NULL
    , positionId TEXT, dateOfBirth TEXT, payType TEXT NOT NULL DEFAULT 'salary', hourlyRate REAL, workerType TEXT NOT NULL DEFAULT 'employee', isTeacher INTEGER NOT NULL DEFAULT 0, tenantId TEXT NOT NULL DEFAULT 'default');
CREATE INDEX idx_employees_tenant ON employees (tenantId);
CREATE TABLE expenses (
      id             TEXT PRIMARY KEY,
      date           TEXT NOT NULL,
      category       TEXT NOT NULL,
      description    TEXT NOT NULL,
      amountHT       REAL NOT NULL,
      gst            REAL NOT NULL,
      qst            REAL NOT NULL,
      deductiblePct  REAL NOT NULL,
      employeeId     TEXT
    , tenantId TEXT NOT NULL DEFAULT 'default', payee TEXT, paymentAccount TEXT, paymentMethod TEXT, refNumber TEXT, memo TEXT, attachments_json TEXT, lines_json TEXT, glEntryId TEXT);
CREATE INDEX idx_expenses_tenant ON expenses (tenantId);
CREATE TABLE fixed_assets (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, cost REAL NOT NULL, acquisitionDate TEXT NOT NULL, depreciationStart TEXT NOT NULL, usefulLifeMonths INTEGER NOT NULL, residualValue REAL NOT NULL DEFAULT 0, disposedDate TEXT, disposalProceeds REAL, createdAt TEXT NOT NULL, parentId TEXT, etat TEXT NOT NULL DEFAULT 'en_service', methode TEXT NOT NULL DEFAULT 'lineaire', tauxDegressif REAL, amortissementCumule REAL NOT NULL DEFAULT 0, composant INTEGER NOT NULL DEFAULT 0, scindeDeId TEXT);
CREATE TABLE gl_entries (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, date TEXT NOT NULL, memo TEXT, status TEXT NOT NULL DEFAULT 'draft', source TEXT, createdAt TEXT NOT NULL, postedAt TEXT);
CREATE TABLE gl_entry_lines (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, entryId TEXT NOT NULL, accountId TEXT NOT NULL, debit REAL NOT NULL DEFAULT 0, credit REAL NOT NULL DEFAULT 0, dimensions TEXT);
CREATE TABLE hab_objets (
          id         TEXT PRIMARY KEY,
          tenantId   TEXT NOT NULL,
          type       TEXT NOT NULL,
          code       TEXT NOT NULL,
          libelle    TEXT NOT NULL,
          parentCode TEXT,
          creeLe     TEXT NOT NULL
        );
CREATE UNIQUE INDEX uq_hab_objets ON hab_objets (tenantId, code);
CREATE TABLE hab_octrois (
          id          TEXT PRIMARY KEY,
          tenantId    TEXT NOT NULL,
          sujetType   TEXT NOT NULL,
          sujetId     TEXT NOT NULL,
          objetType   TEXT NOT NULL,
          objetCode   TEXT NOT NULL,
          permission  TEXT NOT NULL,
          effectifDu  TEXT NOT NULL,
          effectifAu  TEXT,
          motif       TEXT,
          auteurId    TEXT,
          creeLe      TEXT NOT NULL
        );
CREATE INDEX idx_hab_octrois_objet ON hab_octrois (tenantId, objetCode);
CREATE UNIQUE INDEX uq_hab_octrois
          ON hab_octrois (tenantId, sujetType, sujetId, objetCode, effectifDu);
CREATE TABLE hab_profils_utilisateurs (
          tenantId      TEXT NOT NULL,
          utilisateurId TEXT NOT NULL,
          profilId      TEXT NOT NULL,
          creeLe        TEXT NOT NULL,
          PRIMARY KEY (tenantId, utilisateurId, profilId)
        );
CREATE TABLE horaire_periodes (
          id       TEXT PRIMARY KEY,
          tenantId TEXT NOT NULL,
          groupeId TEXT NOT NULL,
          nom      TEXT NOT NULL,
          debut    TEXT NOT NULL,
          fin      TEXT NOT NULL,
          statut   TEXT NOT NULL DEFAULT 'brouillon',
          limiteSaisieAbsences TEXT,
          publieeLe   TEXT,
          publieePar  TEXT,
          closeLe     TEXT,
          createdAt   TEXT NOT NULL
        , revisionDemandeeLe TEXT, revisionDemandeePar TEXT, approuveeLe TEXT, approuveePar TEXT, commentaireRevision TEXT, limiteSaisiePreferences TEXT);
CREATE INDEX idx_horaire_periodes_groupe
          ON horaire_periodes (tenantId, groupeId, debut);
CREATE TABLE horaire_quarts (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          periodeId TEXT NOT NULL,
          tacheId   TEXT NOT NULL,
          date      TEXT NOT NULL,
          debut     INTEGER NOT NULL,
          fin       INTEGER NOT NULL,
          rang      INTEGER NOT NULL DEFAULT 1,
          personId  TEXT,
          note      TEXT,
          createdAt TEXT NOT NULL, nature TEXT NOT NULL DEFAULT 'regulier', tauxHoraireCents INTEGER, primeCents INTEGER, remplaceQuartId TEXT, installationId TEXT,
          UNIQUE (tenantId, periodeId, tacheId, date, debut, rang)
        );
CREATE INDEX idx_horaire_quarts_periode
          ON horaire_quarts (tenantId, periodeId, date);
CREATE INDEX idx_horaire_quarts_personne
          ON horaire_quarts (tenantId, personId, date);
CREATE TABLE income_tax_declarations (
      id              TEXT PRIMARY KEY,
      tenantId        TEXT NOT NULL DEFAULT 'default',
      employeeId      TEXT,                 -- déclaration « pour ce personnel » (sinon = compte connecté)
      label           TEXT,
      profile         TEXT NOT NULL,
      year            INTEGER,
      input_json      TEXT NOT NULL,
      result_json     TEXT NOT NULL,
      status          TEXT NOT NULL DEFAULT 'draft', -- draft | submitted | validated | rejected
      accountantEmail TEXT,
      accountantToken TEXT,
      accountantNote  TEXT,
      validatedAt     TEXT,
      createdBy       TEXT,
      createdAt       TEXT NOT NULL,
      updatedAt       TEXT NOT NULL
    , accountantUserId TEXT);
CREATE INDEX idx_inctax_decl_accountant
          ON income_tax_declarations (tenantId, accountantUserId);
CREATE INDEX idx_inctax_decl_tenant ON income_tax_declarations (tenantId);
CREATE INDEX idx_inctax_decl_tenant_status
          ON income_tax_declarations (tenantId, status);
CREATE INDEX idx_inctax_decl_token ON income_tax_declarations (accountantToken);
CREATE TABLE integrations_webhook_envois (
          id            TEXT PRIMARY KEY,
          tenantId      TEXT NOT NULL,
          webhookId     TEXT NOT NULL,
          evenement     TEXT NOT NULL,
          cle           TEXT,
          corps         TEXT NOT NULL,
          statut        TEXT NOT NULL DEFAULT 'en_attente',
          tentatives    INTEGER NOT NULL DEFAULT 0,
          dernierCode   INTEGER,
          derniereErreur TEXT,
          creeLe        TEXT NOT NULL,
          livreLe       TEXT
        );
CREATE INDEX idx_webhook_envois_webhook ON integrations_webhook_envois (tenantId, webhookId, creeLe);
CREATE UNIQUE INDEX uq_webhook_envois_cle ON integrations_webhook_envois (webhookId, cle) WHERE cle IS NOT NULL;
CREATE TABLE integrations_webhooks (
          id                TEXT PRIMARY KEY,
          tenantId          TEXT NOT NULL,
          url               TEXT NOT NULL,
          description       TEXT,
          evenementsJson    TEXT NOT NULL,
          secret            TEXT NOT NULL,
          actif             INTEGER NOT NULL DEFAULT 1,
          suspendu          INTEGER NOT NULL DEFAULT 0,
          echecsConsecutifs INTEGER NOT NULL DEFAULT 0,
          creeLe            TEXT NOT NULL,
          creePar           TEXT NOT NULL
        );
CREATE INDEX idx_integrations_webhooks_tenant ON integrations_webhooks (tenantId, actif);
CREATE TABLE journal_actions (
          id         TEXT PRIMARY KEY,
          instant    TEXT NOT NULL,
          tenantId   TEXT,
          userId     TEXT NOT NULL,
          acteurReel TEXT,
          methode    TEXT NOT NULL,
          chemin     TEXT NOT NULL,
          statut     INTEGER NOT NULL,
          dureeMs    INTEGER NOT NULL,
          requeteId  TEXT NOT NULL
        );
CREATE INDEX idx_journal_actions_tenant_instant ON journal_actions (tenantId, instant);
CREATE INDEX idx_journal_actions_user ON journal_actions (userId, instant);
CREATE TABLE maintenance_message (id INTEGER PRIMARY KEY CHECK (id = 1), message TEXT NOT NULL DEFAULT '', level TEXT NOT NULL DEFAULT 'info', active INTEGER NOT NULL DEFAULT 0, updatedAt TEXT);
CREATE TABLE msg_appels (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          appelantId     TEXT NOT NULL,
          appeleId       TEXT NOT NULL,
          conversationId TEXT,
          etat           TEXT NOT NULL,
          lanceLe        TEXT NOT NULL,
          decrocheLe     TEXT,
          termineLe      TEXT,
          termineParId   TEXT
        );
CREATE INDEX idx_msg_appels_appelant ON msg_appels (tenantId, appelantId, etat);
CREATE INDEX idx_msg_appels_appele ON msg_appels (tenantId, appeleId, etat);
CREATE TABLE msg_brouillons (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          conversationId TEXT NOT NULL,
          auteurId       TEXT NOT NULL,
          corps          TEXT NOT NULL,
          modifieLe      TEXT NOT NULL
        );
CREATE UNIQUE INDEX uq_msg_brouillon
          ON msg_brouillons (tenantId, conversationId, auteurId);
CREATE TABLE msg_conversations (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          sujet     TEXT NOT NULL,
          type      TEXT NOT NULL DEFAULT 'groupe',
          groupeId  TEXT,
          creePar   TEXT NOT NULL,
          createdAt TEXT NOT NULL,
          archiveeLe TEXT
        );
CREATE INDEX idx_msg_conversations_tenant
          ON msg_conversations (tenantId, archiveeLe);
CREATE TABLE msg_journal (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, acteurId TEXT, action TEXT NOT NULL, cible TEXT, avant TEXT, apres TEXT, horodatage TEXT NOT NULL);
CREATE TABLE msg_lectures (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          messageId TEXT NOT NULL,
          personId  TEXT NOT NULL,
          luLe      TEXT NOT NULL,
          UNIQUE (tenantId, messageId, personId)
        );
CREATE TABLE msg_liste_membres (
          id       TEXT PRIMARY KEY,
          tenantId TEXT NOT NULL,
          listeId  TEXT NOT NULL,
          -- « personne » ou « groupe » : un groupe se **recalcule à l'envoi**, de sorte qu'une
          -- arrivée dans le service soit jointe sans que personne ait à modifier la liste.
          nature   TEXT NOT NULL,
          cible    TEXT NOT NULL
        );
CREATE UNIQUE INDEX uq_msg_liste_membre
          ON msg_liste_membres (tenantId, listeId, nature, cible);
CREATE TABLE msg_listes (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          nom       TEXT NOT NULL,
          partagee  INTEGER NOT NULL DEFAULT 0,
          creeePar  TEXT NOT NULL,
          creeeLe   TEXT NOT NULL
        );
CREATE UNIQUE INDEX uq_msg_liste_nom
          ON msg_listes (tenantId, creeePar, nom);
CREATE TABLE msg_messages (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          conversationId TEXT NOT NULL,
          auteurId       TEXT NOT NULL,
          corps          TEXT NOT NULL,
          envoyeLe       TEXT NOT NULL,
          editeLe        TEXT
        , repondAuMessageId TEXT, priveA TEXT);
CREATE INDEX idx_msg_messages_conversation
          ON msg_messages (tenantId, conversationId, envoyeLe);
CREATE TABLE msg_participants (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          conversationId TEXT NOT NULL,
          personId       TEXT NOT NULL,
          role           TEXT NOT NULL DEFAULT 'membre',
          depuis         TEXT NOT NULL,
          jusqua         TEXT
        );
CREATE INDEX idx_msg_participants_personne
          ON msg_participants (tenantId, personId, conversationId);
CREATE TABLE msg_pieces_jointes (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          messageId TEXT NOT NULL,
          nom       TEXT NOT NULL,
          typeMime  TEXT NOT NULL,
          taille    INTEGER NOT NULL,
          cle       TEXT NOT NULL,
          televerseLe TEXT NOT NULL
        , contenu TEXT);
CREATE TABLE notification_appareils (
          id              TEXT PRIMARY KEY,
          tenantId        TEXT NOT NULL,
          userId          TEXT NOT NULL,
          jeton           TEXT NOT NULL UNIQUE,
          plateforme      TEXT NOT NULL,
          nom             TEXT,
          enregistreLe    TEXT NOT NULL,
          dernierStatut   TEXT,
          dernierEnvoiLe  TEXT
        );
CREATE INDEX idx_notification_appareils_compte ON notification_appareils (tenantId, userId);
CREATE TABLE notification_preferences (
          userId     TEXT PRIMARY KEY,
          tenantId   TEXT,
          courriel   INTEGER NOT NULL DEFAULT 1,
          texto      INTEGER NOT NULL DEFAULT 1,
          poussee    INTEGER NOT NULL DEFAULT 1,
          updatedAt  TEXT NOT NULL
        , evenements TEXT, courrielPersonnel TEXT, regroupement TEXT);
CREATE TABLE notification_regroupement_defaut (
          tenantId   TEXT PRIMARY KEY,
          -- 'un_avis_groupe' | 'un_avis_par_objet' : converti une fois à la frontière.
          mode       TEXT NOT NULL,
          modifieLe  TEXT NOT NULL,
          modifiePar TEXT NOT NULL
        );
CREATE TABLE operator_company (id TEXT PRIMARY KEY, json TEXT NOT NULL, updatedAt TEXT NOT NULL);
CREATE TABLE org_versions (
          id              TEXT PRIMARY KEY,
          tenantId        TEXT NOT NULL,
          uniteId         TEXT NOT NULL,
          code            TEXT NOT NULL,
          libelle         TEXT NOT NULL,
          parentVersionId TEXT,
          valideDu        TEXT NOT NULL,
          valideAu        TEXT,
          operation       TEXT NOT NULL DEFAULT 'creation',
          issueDe         TEXT,
          motif           TEXT,
          auteurId        TEXT,
          creeLe          TEXT NOT NULL
        );
CREATE INDEX idx_org_versions ON org_versions (tenantId, valideDu, valideAu);
CREATE INDEX idx_org_versions_unite ON org_versions (tenantId, uniteId);
CREATE TABLE pay_runs (
      id           TEXT PRIMARY KEY,
      employeeId   TEXT NOT NULL,
      periodStart  TEXT NOT NULL,
      periodEnd    TEXT NOT NULL,
      payDate      TEXT NOT NULL,
      slip_json    TEXT NOT NULL,
      createdAt    TEXT NOT NULL
    , tenantId TEXT NOT NULL DEFAULT 'default', absences_json TEXT);
CREATE INDEX idx_pay_runs_tenant ON pay_runs (tenantId);
CREATE TABLE payments (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, payee TEXT NOT NULL, amount REAL NOT NULL, date TEXT NOT NULL, method TEXT NOT NULL DEFAULT 'eft', status TEXT NOT NULL DEFAULT 'pending', reference TEXT, createdAt TEXT NOT NULL, numero TEXT, motifAnnulation TEXT, remplaceNumero TEXT, remplacePar TEXT, comptabilise INTEGER NOT NULL DEFAULT 0, glEntryId TEXT, renversementId TEXT);
CREATE TABLE pricing_plans (
          id          TEXT PRIMARY KEY,
          code        TEXT NOT NULL UNIQUE,
          name        TEXT NOT NULL,
          audience    TEXT NOT NULL DEFAULT 'b2c',     -- b2c | b2b
          kind        TEXT NOT NULL DEFAULT 'base',    -- base | module
          price       REAL NOT NULL DEFAULT 0,
          currency    TEXT NOT NULL DEFAULT 'CAD',
          interval    TEXT NOT NULL DEFAULT 'month',   -- month | year | per_prof_month | per_school_month
          description TEXT,
          includesModules TEXT NOT NULL DEFAULT '[]',  -- JSON: modules débloqués
          active      INTEGER NOT NULL DEFAULT 1,
          sortOrder   INTEGER NOT NULL DEFAULT 0,
          createdAt   TEXT NOT NULL,
          updatedAt   TEXT NOT NULL
        , originalPrice REAL, badge TEXT, highlighted INTEGER NOT NULL DEFAULT 0);
CREATE TABLE projects (
      id          TEXT PRIMARY KEY,
      code        TEXT NOT NULL,
      name        TEXT NOT NULL,
      clientName  TEXT,
      status      TEXT NOT NULL,
      startDate   TEXT NOT NULL,
      endDate     TEXT,
      budgetHours REAL,
      hourlyRate  REAL,
      createdAt   TEXT NOT NULL
    , managerId TEXT, tenantId TEXT NOT NULL DEFAULT 'default');
CREATE INDEX idx_projects_tenant ON projects (tenantId);
CREATE TABLE ptf_enveloppes (
          id          TEXT PRIMARY KEY,
          tenantId    TEXT NOT NULL,
          libelle     TEXT NOT NULL,
          exercice    TEXT NOT NULL,
          montant     REAL NOT NULL,
          etat        TEXT NOT NULL DEFAULT 'brouillon',
          voteLe      TEXT,
          votePar     TEXT,
          creeLe      TEXT NOT NULL
        );
CREATE INDEX idx_ptf_enveloppes ON ptf_enveloppes (tenantId, exercice);
CREATE TABLE ptf_protocoles (
          id           TEXT PRIMARY KEY,
          tenantId     TEXT NOT NULL,
          projetId     TEXT NOT NULL,
          bailleur     TEXT NOT NULL,
          reference    TEXT,
          tauxPourcent REAL NOT NULL,
          plafond      REAL NOT NULL,
          dejaVerse    REAL NOT NULL DEFAULT 0,
          debutLe      TEXT,
          finLe        TEXT,
          creeLe       TEXT NOT NULL
        );
CREATE INDEX idx_ptf_protocoles ON ptf_protocoles (tenantId, projetId);
CREATE TABLE ptf_ventilation (
          id          TEXT PRIMARY KEY,
          tenantId    TEXT NOT NULL,
          enveloppeId TEXT NOT NULL,
          -- « projet » ou « activite » : les deux découpes de la même enveloppe.
          axe         TEXT NOT NULL,
          reference   TEXT NOT NULL,
          libelle     TEXT NOT NULL,
          montant     REAL NOT NULL,
          creeLe      TEXT NOT NULL
        );
CREATE UNIQUE INDEX uq_ptf_ventilation
          ON ptf_ventilation (tenantId, enveloppeId, axe, reference);
CREATE TABLE push_abonnements (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          userId         TEXT NOT NULL,
          endpoint       TEXT NOT NULL UNIQUE,
          p256dh         TEXT NOT NULL,
          auth           TEXT NOT NULL,
          navigateur     TEXT,
          creeLe         TEXT NOT NULL,
          dernierStatut  TEXT,
          dernierEnvoiLe TEXT
        );
CREATE INDEX idx_push_abonnements_compte ON push_abonnements (tenantId, userId);
CREATE TABLE rap_indicateurs (
          id           TEXT PRIMARY KEY,
          tenantId     TEXT NOT NULL,
          code         TEXT NOT NULL,
          libelle      TEXT NOT NULL,
          -- La source du catalogue (horaires_quarts, effectifs_heures...).
          source       TEXT NOT NULL,
          format       TEXT NOT NULL,
          numerateur   TEXT NOT NULL,
          -- NULL permis : seul un taux exige son dénominateur.
          denominateur TEXT,
          -- Axe de découpage ; NULL = une seule valeur globale.
          axe          TEXT,
          filtresJson  TEXT NOT NULL DEFAULT '[]',
          seuil        REAL,
          -- De quel côté du seuil se trouve le bon côté : sans direction, un seuil ne décide rien.
          direction    TEXT NOT NULL DEFAULT 'au_moins',
          -- Zone de vigilance : sans elle, on bascule de « tout va bien » à « alerte » pour un
          -- dixième de point, et plus personne ne regarde les alertes.
          marge        REAL NOT NULL DEFAULT 5,
          actif        INTEGER NOT NULL DEFAULT 1,
          creeLe       TEXT NOT NULL,
          creePar      TEXT NOT NULL,
          UNIQUE (tenantId, code)
        );
CREATE INDEX idx_rap_indicateurs_actifs
          ON rap_indicateurs (tenantId, actif, source);
CREATE TABLE retenues (
          id          TEXT PRIMARY KEY,
          tenantId    TEXT NOT NULL,
          regleId     TEXT,
          fournisseurId TEXT,
          contratId   TEXT,
          factureRef  TEXT NOT NULL,
          montantFacture REAL NOT NULL,
          montant     REAL NOT NULL,
          libere      REAL NOT NULL DEFAULT 0,
          etat        TEXT NOT NULL DEFAULT 'retenue',
          tauxApplique REAL,
          echeance    TEXT,
          motifs      TEXT,
          ecritureId  TEXT,
          auteurId    TEXT,
          effectueLe  TEXT NOT NULL,
          creeLe      TEXT NOT NULL
        );
CREATE INDEX idx_retenues_contrat ON retenues (tenantId, contratId, etat);
CREATE INDEX idx_retenues_echeance ON retenues (tenantId, echeance, etat);
CREATE TABLE scol_integrations (
          id          TEXT PRIMARY KEY,
          tenantId    TEXT NOT NULL,
          source      TEXT NOT NULL,
          configJson  TEXT NOT NULL DEFAULT '{}',
          modifieLe   TEXT NOT NULL,
          modifiePar  TEXT NOT NULL
        );
CREATE UNIQUE INDEX idx_scol_integrations_source ON scol_integrations (tenantId, source);
CREATE TABLE scol_suppleants (
          id                  TEXT PRIMARY KEY,
          tenantId            TEXT NOT NULL,
          personId            TEXT NOT NULL,
          statutEngagement    TEXT NOT NULL DEFAULT 'suppleance',
          disponibilitesJson  TEXT NOT NULL DEFAULT '{}',
          telephone           TEXT,
          courriel            TEXT,
          sourceExterne       TEXT NOT NULL DEFAULT 'manuel',
          cleExterne          TEXT,
          actif               INTEGER NOT NULL DEFAULT 1,
          createdAt           TEXT NOT NULL
        , qualificationLegale TEXT NOT NULL DEFAULT 'inconnue', antecedentsVerifiesLe TEXT, attributsJson TEXT NOT NULL DEFAULT '[]');
CREATE UNIQUE INDEX idx_scol_suppleants_personne ON scol_suppleants (tenantId, personId);
CREATE TABLE scol_synchronisations (
          id         TEXT PRIMARY KEY,
          tenantId   TEXT NOT NULL,
          source     TEXT NOT NULL,
          debuteLe   TEXT NOT NULL,
          termineLe  TEXT,
          -- reussie | echouee
          statut     TEXT NOT NULL,
          lues       INTEGER NOT NULL DEFAULT 0,
          creees     INTEGER NOT NULL DEFAULT 0,
          modifiees  INTEGER NOT NULL DEFAULT 0,
          rejetees   INTEGER NOT NULL DEFAULT 0,
          rejetsJson TEXT NOT NULL DEFAULT '[]',
          curseur    TEXT,
          erreur     TEXT,
          simulee    INTEGER NOT NULL DEFAULT 0
        );
CREATE INDEX idx_scol_synchronisations_tenant ON scol_synchronisations (tenantId, source, debuteLe);
CREATE TABLE securite_incidents (
          id                 TEXT PRIMARY KEY,
          tenantId           TEXT NOT NULL,
          survenuLe          TEXT NOT NULL,
          decouvertLe        TEXT NOT NULL,
          nature             TEXT NOT NULL,
          description        TEXT NOT NULL,
          renseignementsJson TEXT NOT NULL,
          personnesTouchees  INTEGER,
          gravite            TEXT NOT NULL,
          mesures            TEXT NOT NULL,
          responsableId      TEXT,
          responsableNom     TEXT,
          notifieCai         INTEGER NOT NULL DEFAULT 0,
          notifieCaiLe       TEXT,
          notifiePersonnes   INTEGER NOT NULL DEFAULT 0,
          notifiePersonnesLe TEXT,
          statut             TEXT NOT NULL DEFAULT 'ouvert',
          closLe             TEXT,
          consigneLe         TEXT NOT NULL,
          consignePar        TEXT NOT NULL
        );
CREATE INDEX idx_securite_incidents_tenant ON securite_incidents (tenantId, statut, gravite, survenuLe);
CREATE TABLE security_question_catalog (id TEXT PRIMARY KEY, tenantId TEXT, question TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, sortOrder INTEGER NOT NULL DEFAULT 0, createdAt TEXT NOT NULL);
CREATE TABLE seq (
      name   TEXT PRIMARY KEY,
      value  INTEGER NOT NULL
    );
CREATE TABLE sms_message (
          id              INTEGER PRIMARY KEY AUTOINCREMENT,
          tenant_id       VARCHAR(100) NOT NULL,
          phone_number    VARCHAR(30) NOT NULL,
          message         TEXT NOT NULL,
          status          VARCHAR(30) NOT NULL,
          provider        VARCHAR(30),
          provider_id     VARCHAR(255),
          created_at      TIMESTAMP NOT NULL,
          sent_at         TIMESTAMP,
          error_message   TEXT,
          contexte        VARCHAR(100),
          expediteur      VARCHAR(30)
        , segments INTEGER NOT NULL DEFAULT 1);
CREATE INDEX idx_sms_message_provider ON sms_message (provider_id);
CREATE INDEX idx_sms_message_tenant ON sms_message (tenant_id, created_at);
CREATE TABLE sms_quota (
          tenant_id       VARCHAR(100) PRIMARY KEY,
          monthly_limit   INTEGER NOT NULL
        );
CREATE TABLE soutien_battements (
          minute    TEXT PRIMARY KEY,
          le        TEXT NOT NULL,
          latenceMs INTEGER NOT NULL
        );
CREATE TABLE soutien_engagements (
          tenantId   TEXT PRIMARY KEY,
          politique  TEXT NOT NULL,
          modifieLe  TEXT NOT NULL,
          modifiePar TEXT NOT NULL
        );
CREATE TABLE soutien_maintenances (
          id          TEXT PRIMARY KEY,
          debut       TEXT NOT NULL,
          fin         TEXT NOT NULL,
          description TEXT NOT NULL,
          statut      TEXT NOT NULL DEFAULT 'planifiee',
          annonceeLe  TEXT NOT NULL,
          annonceePar TEXT NOT NULL,
          annuleeLe   TEXT
        );
CREATE TABLE soutien_messages (
          id        TEXT PRIMARY KEY,
          ticketId  TEXT NOT NULL,
          auteurId  TEXT NOT NULL,
          auteurNom TEXT NOT NULL,
          texte     TEXT NOT NULL,
          interne   INTEGER NOT NULL DEFAULT 0,
          le        TEXT NOT NULL
        );
CREATE INDEX idx_soutien_messages_ticket ON soutien_messages (ticketId, le);
CREATE TABLE soutien_pieces (
          id          TEXT PRIMARY KEY,
          ticketId    TEXT NOT NULL,
          messageId   TEXT,
          nom         TEXT NOT NULL,
          typeMime    TEXT NOT NULL,
          taille      INTEGER NOT NULL,
          contenu     TEXT NOT NULL,
          ajouteLe    TEXT NOT NULL,
          ajouteParId TEXT NOT NULL,
          ajouteParNom TEXT NOT NULL
        );
CREATE INDEX idx_soutien_pieces_ticket ON soutien_pieces (ticketId, ajouteLe);
CREATE TABLE soutien_tickets (
          id              TEXT PRIMARY KEY,
          tenantId        TEXT,
          auteurId        TEXT NOT NULL,
          auteurNom       TEXT NOT NULL,
          sujet           TEXT NOT NULL,
          description     TEXT NOT NULL,
          niveau          TEXT NOT NULL,
          statut          TEXT NOT NULL DEFAULT 'nouveau',
          langue          TEXT NOT NULL DEFAULT 'fr',
          ouvertLe        TEXT NOT NULL,
          echeanceLe      TEXT NOT NULL,
          prisEnChargeLe  TEXT,
          resoluLe        TEXT,
          fermeLe         TEXT,
          traitePar       TEXT
        , numero INTEGER, categorie TEXT NOT NULL DEFAULT 'incident', impact TEXT NOT NULL DEFAULT 'moi', ecran TEXT, navigateur TEXT, satisfaction INTEGER, satisfactionCommentaire TEXT, satisfactionLe TEXT);
CREATE INDEX idx_soutien_tickets_auteur ON soutien_tickets (auteurId, ouvertLe);
CREATE INDEX idx_soutien_tickets_tenant ON soutien_tickets (tenantId, ouvertLe);
CREATE TABLE taches_differees (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          type           TEXT NOT NULL,
          cibleId        TEXT NOT NULL,
          charge         TEXT NOT NULL DEFAULT '{}',
          echeanceLe     TEXT NOT NULL,
          statut         TEXT NOT NULL DEFAULT 'en_attente',
          tentatives     INTEGER NOT NULL DEFAULT 0,
          derniereErreur TEXT,
          verrouLe       TEXT,
          verrouPar      TEXT,
          faiteLe        TEXT,
          createdAt      TEXT NOT NULL,
          creePar        TEXT NOT NULL
        );
CREATE INDEX idx_taches_differees_cible
          ON taches_differees (tenantId, type, cibleId);
CREATE INDEX idx_taches_differees_echues
          ON taches_differees (tenantId, statut, echeanceLe);
CREATE TABLE tenant_admins (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, userId TEXT NOT NULL UNIQUE, username TEXT NOT NULL, createdAt TEXT NOT NULL);
CREATE TABLE tenant_quotas (tenantId TEXT PRIMARY KEY, maxTeachers INTEGER NOT NULL, maxContractsPerYear INTEGER NOT NULL, modules TEXT NOT NULL);
CREATE TABLE tenant_role_capabilities (tenantId TEXT NOT NULL, role TEXT NOT NULL, capability TEXT NOT NULL, allowed INTEGER NOT NULL, PRIMARY KEY (tenantId, role, capability));
CREATE TABLE tenant_roles (tenantId TEXT NOT NULL, code TEXT NOT NULL, label TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, createdAt TEXT NOT NULL, PRIMARY KEY (tenantId, code));
CREATE TABLE tenant_usage (tenantId TEXT NOT NULL, month TEXT NOT NULL, teachersCount INTEGER NOT NULL DEFAULT 0, contractsCount INTEGER NOT NULL DEFAULT 0, payCalcsCount INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (tenantId, month));
CREATE TABLE tenant_vues_masquees (
          tenant_id VARCHAR(100) NOT NULL,
          cle       VARCHAR(80)  NOT NULL,
          masquee_le TIMESTAMP   NOT NULL,
          PRIMARY KEY (tenant_id, cle)
        );
CREATE INDEX idx_tenant_vues_masquees ON tenant_vues_masquees (tenant_id);
CREATE TABLE tenants (id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'public_institution', country TEXT NOT NULL DEFAULT 'CA', region TEXT, planCode TEXT, status TEXT NOT NULL DEFAULT 'trial', trialEndsAt TEXT, subscriptionEndsAt TEXT, dataResidency TEXT NOT NULL DEFAULT 'canadacentral', createdAt TEXT NOT NULL DEFAULT '2026-01-01T00:00:00.000Z', address TEXT, city TEXT, postalCode TEXT, phone TEXT, email TEXT, slug TEXT);
CREATE TABLE user_security_questions (id TEXT PRIMARY KEY, userId TEXT NOT NULL, question TEXT NOT NULL, answerHash TEXT NOT NULL, sortOrder INTEGER NOT NULL DEFAULT 0, createdAt TEXT NOT NULL);
CREATE TABLE users (id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, loginName TEXT, tenantId TEXT, passwordHash TEXT NOT NULL, role TEXT NOT NULL, employeeId TEXT, createdAt TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, mustChangePassword INTEGER NOT NULL DEFAULT 0, securitySetupRequired INTEGER NOT NULL DEFAULT 0, email TEXT, securityMethod TEXT, securityQuestion TEXT, securityAnswerHash TEXT, totpSecret TEXT, pendingTotpSecret TEXT);
CREATE TABLE varpay_notifications (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, channel TEXT NOT NULL, recipientType TEXT NOT NULL, recipientId TEXT NOT NULL, subject TEXT NOT NULL, body TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'queued', createdAt TEXT NOT NULL, sentAt TEXT, error TEXT, evenement TEXT, tentative INTEGER NOT NULL DEFAULT 1);
CREATE TABLE varpay_pay_rules (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, activityType TEXT NOT NULL, echelon INTEGER, jobTitleCode TEXT, rateMultiplier REAL NOT NULL DEFAULT 1.0, fixedRate REAL, validFrom TEXT NOT NULL, validUntil TEXT, createdAt TEXT NOT NULL);
CREATE TABLE varpay_teachers (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, sagirEmployeeId TEXT NOT NULL, firstName TEXT NOT NULL, lastName TEXT NOT NULL, email TEXT, phone TEXT, type TEXT NOT NULL, echelon INTEGER NOT NULL DEFAULT 1, hireDate TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, isGovernmentEmployee INTEGER NOT NULL DEFAULT 0, createdAt TEXT NOT NULL, establishmentId TEXT, linkedEmployeeId TEXT, UNIQUE (tenantId, sagirEmployeeId));
CREATE TABLE eff_absences (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, uniteCode TEXT NOT NULL, codeAbsence TEXT NOT NULL, du TEXT NOT NULL, au TEXT NOT NULL, motif TEXT, statut TEXT NOT NULL, actionRegles TEXT NOT NULL, motifsRegles TEXT NOT NULL DEFAULT '[]', declareeLe TEXT NOT NULL, declareePar TEXT NOT NULL, decideeLe TEXT, decideePar TEXT, motifDecision TEXT, derogationCode TEXT, derogationMotif TEXT);
CREATE INDEX idx_eff_absences_ressource ON eff_absences (tenantId, ressourceId, du);
CREATE INDEX idx_eff_absences_unite ON eff_absences (tenantId, uniteCode, du);
CREATE TABLE eff_agph_campagnes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, annee INTEGER NOT NULL, nom TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, etat TEXT NOT NULL, delaiChoixMinutes INTEGER NOT NULL, creeLe TEXT NOT NULL, ouverteLe TEXT, closeLe TEXT, budgetHeuresSemaine REAL, reviseeLe TEXT);
CREATE UNIQUE INDEX uq_eff_agph_campagne ON eff_agph_campagnes (tenantId, uniteCode, corpsEmploiCode, annee);
CREATE TABLE eff_agph_choix (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, posteId TEXT NOT NULL, mode TEXT NOT NULL, choisiLe TEXT NOT NULL, choisiPar TEXT NOT NULL, PRIMARY KEY (tenantId, campagneId, ressourceId), UNIQUE (tenantId, campagneId, posteId));
CREATE TABLE eff_agph_depots (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, conformite TEXT NOT NULL, motifs TEXT NOT NULL DEFAULT '[]', heures REAL NOT NULL, deposeLe TEXT NOT NULL, deposePar TEXT NOT NULL, PRIMARY KEY (tenantId, campagneId, ressourceId));
CREATE TABLE eff_agph_plages (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, jour INTEGER NOT NULL, debut INTEGER NOT NULL, fin INTEGER NOT NULL, type TEXT NOT NULL);
CREATE INDEX idx_eff_agph_plages ON eff_agph_plages (tenantId, campagneId, ressourceId);
CREATE TABLE eff_agph_postes (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, posteId TEXT NOT NULL, PRIMARY KEY (tenantId, campagneId, posteId));
CREATE TABLE eff_agph_rangs (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, rang INTEGER NOT NULL, anciennete REAL NOT NULL, etat TEXT NOT NULL, tourDebut TEXT, tourFin TEXT, PRIMARY KEY (tenantId, campagneId, ressourceId));
CREATE TABLE eff_comblement_avis (comblementId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, canal TEXT NOT NULL, statut TEXT NOT NULL, erreur TEXT, envoyeLe TEXT NOT NULL);
CREATE INDEX idx_eff_comblement_avis ON eff_comblement_avis (tenantId, comblementId);
CREATE TABLE eff_comblements (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, absenceId TEXT NOT NULL, placeId TEXT NOT NULL, mode TEXT NOT NULL, resultat TEXT NOT NULL, attribueeA TEXT, mecanisme TEXT NOT NULL, creeLe TEXT NOT NULL);
CREATE INDEX idx_eff_comblements_absence ON eff_comblements (tenantId, absenceId);
CREATE TABLE eff_connecteurs (tenantId TEXT NOT NULL, code TEXT NOT NULL, mode TEXT NOT NULL, url TEXT, jetonRef TEXT, actif INTEGER NOT NULL, jeuEssai TEXT NOT NULL DEFAULT '[]', curseur TEXT, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL, PRIMARY KEY (tenantId, code));
CREATE TABLE eff_depots (tenantId TEXT NOT NULL, periodeId TEXT NOT NULL, ressourceId TEXT NOT NULL, conformite TEXT NOT NULL, motifs TEXT NOT NULL DEFAULT '[]', deposeLe TEXT NOT NULL, deposePar TEXT NOT NULL, approuveLe TEXT, approuvePar TEXT, PRIMARY KEY (tenantId, periodeId, ressourceId));
CREATE TABLE eff_disponibilites (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, periodeId TEXT NOT NULL, ressourceId TEXT NOT NULL, date TEXT NOT NULL, debut INTEGER NOT NULL, fin INTEGER NOT NULL, type TEXT NOT NULL);
CREATE INDEX idx_eff_disponibilites ON eff_disponibilites (tenantId, periodeId, ressourceId);
CREATE TABLE eff_echanges (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, placeCedeeId TEXT NOT NULL, placeVoulueId TEXT NOT NULL, ressourceAId TEXT NOT NULL, ressourceBId TEXT NOT NULL, uniteA TEXT NOT NULL, uniteB TEXT NOT NULL, statut TEXT NOT NULL, actionRegles TEXT NOT NULL, motifsRegles TEXT NOT NULL DEFAULT '[]', proposeLe TEXT NOT NULL, proposePar TEXT NOT NULL, decideLe TEXT, decidePar TEXT, motifDecision TEXT);
CREATE INDEX idx_eff_echanges_a ON eff_echanges (tenantId, ressourceAId);
CREATE INDEX idx_eff_echanges_b ON eff_echanges (tenantId, ressourceBId);
CREATE TABLE eff_equipages (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, periodeId TEXT NOT NULL, uniteCode TEXT NOT NULL, date TEXT NOT NULL, debut INTEGER NOT NULL, fin INTEGER NOT NULL, natureCode TEXT NOT NULL, plageCode TEXT, milieuId TEXT NOT NULL, modeAttribution TEXT NOT NULL, minimumOperationnel INTEGER NOT NULL, note TEXT, creeLe TEXT NOT NULL);
CREATE INDEX idx_eff_equipages_periode ON eff_equipages (tenantId, periodeId, date);
CREATE TABLE eff_feuille_decisions (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, feuilleId TEXT NOT NULL, palier INTEGER NOT NULL, decision TEXT NOT NULL, motif TEXT, decideLe TEXT NOT NULL, decidePar TEXT NOT NULL);
CREATE INDEX idx_eff_feuille_decisions ON eff_feuille_decisions (tenantId, feuilleId);
CREATE TABLE eff_feuilles (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, uniteCode TEXT NOT NULL, debut TEXT NOT NULL, fin TEXT NOT NULL, statut TEXT NOT NULL, palier INTEGER NOT NULL DEFAULT 0, paliersRequis INTEGER NOT NULL, instantane TEXT, soumiseLe TEXT, soumisePar TEXT, approuveeLe TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL, UNIQUE (tenantId, ressourceId, debut));
CREATE INDEX idx_eff_feuilles_periode ON eff_feuilles (tenantId, debut, statut);
CREATE TABLE eff_horodatages_externes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, source TEXT NOT NULL, cle TEXT NOT NULL, ressourceId TEXT NOT NULL, sens TEXT NOT NULL, horodatage TEXT NOT NULL, importeLe TEXT NOT NULL, importePar TEXT NOT NULL, UNIQUE (tenantId, source, cle));
CREATE INDEX idx_eff_horodatages_ressource ON eff_horodatages_externes (tenantId, ressourceId, horodatage);
CREATE TABLE eff_journal (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, horodatage TEXT NOT NULL, acteurId TEXT NOT NULL, origine TEXT NOT NULL, action TEXT NOT NULL, objetType TEXT NOT NULL, objetId TEXT NOT NULL, uniteCode TEXT, avant TEXT, apres TEXT);
CREATE INDEX idx_eff_journal_objet ON eff_journal (tenantId, objetType, objetId);
CREATE INDEX idx_eff_journal_temps ON eff_journal (tenantId, horodatage);
CREATE TABLE eff_messages_sortants (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, connecteur TEXT NOT NULL, type TEXT NOT NULL, cle TEXT NOT NULL, charge TEXT NOT NULL, etat TEXT NOT NULL, tentatives INTEGER NOT NULL DEFAULT 0, refExterne TEXT, motif TEXT, deposeLe TEXT NOT NULL, deposePar TEXT NOT NULL, traiteLe TEXT, UNIQUE (tenantId, connecteur, cle));
CREATE INDEX idx_eff_messages_etat ON eff_messages_sortants (tenantId, etat, deposeLe);
CREATE TABLE eff_milieu_preuves (tenantId TEXT NOT NULL, milieuId TEXT NOT NULL, mecanisme TEXT NOT NULL, exige INTEGER NOT NULL DEFAULT 0, parametres TEXT NOT NULL DEFAULT '{}', secret TEXT, PRIMARY KEY (tenantId, milieuId, mecanisme));
CREATE TABLE eff_milieux (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, typeCode TEXT NOT NULL, uniteCode TEXT NOT NULL, adresse TEXT, latitude REAL, longitude REAL, rayonMetres INTEGER, actif INTEGER NOT NULL DEFAULT 1, creeLe TEXT NOT NULL);
CREATE UNIQUE INDEX uq_eff_milieux ON eff_milieux (tenantId, code);
CREATE TABLE eff_perimetres (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, utilisateurId TEXT NOT NULL, uniteCode TEXT NOT NULL, etendue TEXT NOT NULL, niveau TEXT NOT NULL, depuis TEXT NOT NULL, jusqua TEXT, creeLe TEXT NOT NULL);
CREATE INDEX idx_eff_perimetres_utilisateur ON eff_perimetres (tenantId, utilisateurId);
CREATE TABLE eff_periodes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, nom TEXT NOT NULL, debut TEXT NOT NULL, fin TEXT NOT NULL, etat TEXT NOT NULL, finCollecte TEXT, creeLe TEXT NOT NULL, publieeLe TEXT, closeLe TEXT);
CREATE INDEX idx_eff_periodes_unite ON eff_periodes (tenantId, uniteCode, debut);
CREATE TABLE eff_places (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, equipageId TEXT NOT NULL, rang INTEGER NOT NULL, corpsEmploiCode TEXT NOT NULL, posteId TEXT, ressourceId TEXT, attribueePar TEXT, justificationForcage TEXT, attribueeLe TEXT);
CREATE INDEX idx_eff_places_equipage ON eff_places (tenantId, equipageId);
CREATE INDEX idx_eff_places_ressource ON eff_places (tenantId, ressourceId);
CREATE TABLE eff_pointage_preuves (tenantId TEXT NOT NULL, pointageId TEXT NOT NULL, mecanisme TEXT NOT NULL, exigee INTEGER NOT NULL, fournie INTEGER NOT NULL, satisfaite INTEGER NOT NULL, distanceMetres INTEGER, detail TEXT, PRIMARY KEY (tenantId, pointageId, mecanisme));
CREATE TABLE eff_pointages (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, placeId TEXT, milieuId TEXT, uniteCode TEXT NOT NULL, sens TEXT NOT NULL, horodatage TEXT NOT NULL, origine TEXT NOT NULL, saisieId TEXT, resultat TEXT NOT NULL, ecart TEXT, distanceMetres INTEGER, actionRegles TEXT NOT NULL, motifsRegles TEXT NOT NULL DEFAULT '[]', statut TEXT NOT NULL, justification TEXT, saisiLe TEXT NOT NULL, saisiPar TEXT NOT NULL, decideLe TEXT, decidePar TEXT, motifDecision TEXT, naturePause TEXT, activiteCode TEXT);
CREATE INDEX idx_eff_pointages_ressource ON eff_pointages (tenantId, ressourceId, horodatage);
CREATE INDEX idx_eff_pointages_statut ON eff_pointages (tenantId, statut);
CREATE TABLE eff_postes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, statutEmploiCode TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, milieuId TEXT NOT NULL, natureCode TEXT NOT NULL, cycle TEXT NOT NULL, debutCycle TEXT NOT NULL, parametres TEXT NOT NULL DEFAULT '{}', etat TEXT NOT NULL, titulaireId TEXT, creeLe TEXT NOT NULL);
CREATE UNIQUE INDEX uq_eff_postes ON eff_postes (tenantId, code);
CREATE TABLE eff_profils_pointage (tenantId TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, mode TEXT NOT NULL, porteeType TEXT NOT NULL, porteeCode TEXT, naturesNonPayees TEXT NOT NULL, pausesManuelles INTEGER NOT NULL DEFAULT 0, presencePrealableMinutes INTEGER NOT NULL DEFAULT 15, fenetreAPosterioriJours INTEGER, actif INTEGER NOT NULL DEFAULT 1, majLe TEXT NOT NULL, majPar TEXT, PRIMARY KEY (tenantId, code));
CREATE TABLE eff_quotas (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, plageCode TEXT, natureCode TEXT, quota INTEGER NOT NULL, effectifDepuis TEXT NOT NULL, effectifJusqua TEXT, cle TEXT NOT NULL, creeLe TEXT NOT NULL);
CREATE INDEX idx_eff_quotas_cle ON eff_quotas (tenantId, cle);
CREATE TABLE eff_referentiels (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, type TEXT NOT NULL, code TEXT NOT NULL, libelleFr TEXT NOT NULL, libelleEn TEXT, comportement TEXT, ordre INTEGER NOT NULL DEFAULT 0, attributs TEXT NOT NULL DEFAULT '{}', effectifDepuis TEXT NOT NULL, effectifJusqua TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL);
CREATE INDEX idx_eff_referentiels_type ON eff_referentiels (tenantId, type);
CREATE UNIQUE INDEX uq_eff_referentiels ON eff_referentiels (tenantId, type, code, effectifDepuis);
CREATE TABLE eff_regle_evaluations (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, contexte TEXT NOT NULL, objetType TEXT NOT NULL, objetId TEXT NOT NULL, action TEXT NOT NULL, motifs TEXT NOT NULL, versions TEXT NOT NULL, faits TEXT NOT NULL, dateDuFait TEXT NOT NULL, horodatage TEXT NOT NULL, declencheur TEXT NOT NULL);
CREATE INDEX idx_eff_regle_evaluations_objet ON eff_regle_evaluations (tenantId, objetType, objetId);
CREATE TABLE eff_regle_versions (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, regleId TEXT NOT NULL, version INTEGER NOT NULL, condition TEXT NOT NULL, action TEXT NOT NULL, message TEXT NOT NULL, priorite INTEGER NOT NULL DEFAULT 0, effectifDepuis TEXT NOT NULL, effectifJusqua TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL);
CREATE UNIQUE INDEX uq_eff_regle_versions ON eff_regle_versions (tenantId, regleId, version);
CREATE TABLE eff_regles (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, contexte TEXT NOT NULL, cle TEXT NOT NULL, porteeType TEXT NOT NULL, porteeCode TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL);
CREATE UNIQUE INDEX uq_eff_regles ON eff_regles (tenantId, contexte, cle, porteeType, IFNULL(porteeCode, ''));
CREATE TABLE eff_remplacement_interets (remplacementId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, deposeLe TEXT NOT NULL, retireLe TEXT, PRIMARY KEY (tenantId, remplacementId, ressourceId));
CREATE TABLE eff_remplacements (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, posteId TEXT NOT NULL, origine TEXT NOT NULL, referenceId TEXT, debut TEXT NOT NULL, fin TEXT NOT NULL, finManifestation TEXT NOT NULL, etat TEXT NOT NULL, attribueA TEXT, placesAttribuees INTEGER NOT NULL DEFAULT 0, creeLe TEXT NOT NULL, creePar TEXT NOT NULL, decideLe TEXT, transfereLe TEXT, quartsTransferes INTEGER NOT NULL DEFAULT 0, motifNonComble TEXT);
CREATE TABLE eff_ressources (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, matricule TEXT NOT NULL, nom TEXT NOT NULL, utilisateurId TEXT, uniteCode TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, titreEmploiCode TEXT, statutEmploiCode TEXT, conventionCode TEXT, anciennete REAL NOT NULL DEFAULT 0, actif INTEGER NOT NULL DEFAULT 1, creeLe TEXT NOT NULL);
CREATE UNIQUE INDEX uq_eff_ressources ON eff_ressources (tenantId, matricule);
CREATE TABLE eff_retention (
    tenantId TEXT NOT NULL, classe TEXT NOT NULL, annees INTEGER NOT NULL, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL,
    PRIMARY KEY (tenantId, classe));
CREATE TABLE eff_segments_temps (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, feuilleId TEXT NOT NULL, placeId TEXT, date TEXT NOT NULL, debut TEXT NOT NULL, fin TEXT NOT NULL, typeHeureCode TEXT NOT NULL, source TEXT NOT NULL, version INTEGER NOT NULL, remplaceId TEXT, actif INTEGER NOT NULL, motif TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL, sourceExterne TEXT, confirmeLe TEXT, confirmePar TEXT);
CREATE INDEX idx_eff_segments_feuille ON eff_segments_temps (tenantId, feuilleId, actif);
CREATE TABLE eff_surcharges (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, cle TEXT NOT NULL, valeur INTEGER NOT NULL, depuis TEXT NOT NULL, jusqua TEXT NOT NULL, motif TEXT NOT NULL, auteur TEXT NOT NULL, creeLe TEXT NOT NULL);
CREATE INDEX idx_eff_surcharges_cle ON eff_surcharges (tenantId, cle);
CREATE TABLE eff_temps_parametres (tenantId TEXT PRIMARY KEY, seuilJourHeures INTEGER NOT NULL, seuilSemaineHeures INTEGER NOT NULL, paliersApprobation INTEGER NOT NULL, toleranceEcartMinutes INTEGER NOT NULL, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL, toleranceCorroborationMinutes INTEGER NOT NULL DEFAULT 5, actionNonCorrobore TEXT NOT NULL DEFAULT 'SIGNALER', actionSourcesDivergentes TEXT NOT NULL DEFAULT 'BLOQUER');
