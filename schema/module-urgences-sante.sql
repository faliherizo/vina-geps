-- Schéma du module Urgences-santé, extrait d'une base migrée (migrations Node 0001-0173) le 13 nov. 2026.
-- Tables propres au module (eff_*, analytique_*), puis les tables du socle qu'il lit (à fournir par le socle).
-- Point de départ de la baseline Flyway du nouveau dépôt : voir PLANIFICATION-SEPARATION.md, phase 4.

-- ===== PROPRES AU MODULE =====
CREATE TABLE analytique_jetons (
          id             TEXT PRIMARY KEY,
          tenantId       TEXT NOT NULL,
          libelle        TEXT NOT NULL,
          -- L'empreinte SHA-256 du jeton, jamais le jeton.
          empreinte      TEXT NOT NULL UNIQUE,
          -- Les codes de vue, séparés par des virgules. Vide = ne lit rien (refus par défaut).
          vues           TEXT NOT NULL,
          creeLe         TEXT NOT NULL,
          creePar        TEXT NOT NULL,
          expireLe       TEXT NOT NULL,
          revoqueLe      TEXT,
          revoquePar     TEXT,
          dernierUsageLe TEXT,
          usages         INTEGER NOT NULL DEFAULT 0
        );
CREATE INDEX idx_analytique_jetons_tenant
          ON analytique_jetons (tenantId, revoqueLe);

CREATE TABLE analytique_lectures (
          id        TEXT PRIMARY KEY,
          tenantId  TEXT NOT NULL,
          jetonId   TEXT,
          acteurId  TEXT,
          vue       TEXT NOT NULL,
          du        TEXT NOT NULL,
          au        TEXT NOT NULL,
          lignes    INTEGER NOT NULL,
          lueLe     TEXT NOT NULL
        );
CREATE INDEX idx_analytique_lectures
          ON analytique_lectures (tenantId, lueLe);

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

-- ===== SOCLE LU PAR LE MODULE =====
CREATE TABLE users (id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, loginName TEXT, tenantId TEXT, passwordHash TEXT NOT NULL, role TEXT NOT NULL, employeeId TEXT, createdAt TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1, mustChangePassword INTEGER NOT NULL DEFAULT 0, securitySetupRequired INTEGER NOT NULL DEFAULT 0, email TEXT, securityMethod TEXT, securityQuestion TEXT, securityAnswerHash TEXT, totpSecret TEXT, pendingTotpSecret TEXT);

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

CREATE TABLE tenant_quotas (tenantId TEXT PRIMARY KEY, maxTeachers INTEGER NOT NULL, maxContractsPerYear INTEGER NOT NULL, modules TEXT NOT NULL);

CREATE TABLE tenants (id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'public_institution', country TEXT NOT NULL DEFAULT 'CA', region TEXT, planCode TEXT, status TEXT NOT NULL DEFAULT 'trial', trialEndsAt TEXT, subscriptionEndsAt TEXT, dataResidency TEXT NOT NULL DEFAULT 'canadacentral', createdAt TEXT NOT NULL DEFAULT '2026-01-01T00:00:00.000Z', address TEXT, city TEXT, postalCode TEXT, phone TEXT, email TEXT, slug TEXT);
