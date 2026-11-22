/**
 * Audit **géométrique** d'accessibilité — WCAG 2.2 AA et qualité de mise en page.
 *
 * Le scanner statique (`scripts/audit-accessibilite.js`) lit le balisage : il voit un libellé
 * manquant, pas un bouton de 18 pixels ni un menu qui recouvre un champ. Or WCAG **2.2** ajoute
 * précisément des critères qui ne se vérifient qu'une fois la page rendue :
 *
 *  - **2.5.8 Taille de cible (minimum)** — toute cible de pointage fait au moins 24 × 24 px ;
 *  - **2.4.11 Focus non masqué (minimum)** — l'élément qui reçoit le focus n'est pas recouvert ;
 *  - **1.4.10 Redistribution** — aucun débordement horizontal jusqu'à 320 px de large.
 *
 * S'y ajoute un contrôle de mise en page : aucun élément interactif visible ne doit être
 * **recouvert** par un autre. C'est le même calcul que 2.4.11, appliqué à tous les contrôles et
 * non au seul élément focalisé.
 *
 * Exceptions appliquées, conformes à la norme : les liens **en ligne dans un texte** échappent à
 * 2.5.8 (leur taille suit celle du texte), de même que les cibles dont l'espacement compense la
 * taille. On ignore aussi ce qui est masqué ou de dimension nulle.
 */
import type { Page } from "@playwright/test";
import { test, expect, DEFAULT_MODULES, mockApi } from "./fixtures/app";
import { deplierJusqua, estAffiche, ouvrirNav } from "../e2e/helpers/nav";
import { expectNoA11yViolations } from "../e2e/helpers/a11y";

/**
 * Le module « horaires » n'est pas ouvert par défaut dans le harnais : on l'ouvre **ici
 * seulement**, pour que son écran entre dans l'audit sans changer la navigation que mesurent
 * les autres essais d'interface. Sa barre d'onglets est le contrôle qui compte : quinze onglets
 * sur 390 px de large, c'est exactement là qu'un débordement se produit.
 *
 * `horaires_legacy` (mig. 0169), `portefeuille` (0170) et `immobilisations` gardent leur page
 * depuis la fin d'octobre : sans eux, trois écrans de la liste n'avaient plus d'entrée au menu.
 */
const MODULES_AUDIT = {
  "GET /api/saas/my-modules": { modules: [...DEFAULT_MODULES], planCode: "enterprise" },
  // Le formulaire unique des absences (ADR 0026) se mesure dans chaque nature : les trois ouvertes, un catalogue scolaire garni, une vue d'ensemble avec ses trois sources.
  "GET /api/absences/categories": [{ categorie: "dossier", disponible: true, raison: null }, { categorie: "enseignement", disponible: true, raison: null }, { categorie: "scolaire", disponible: true, raison: null },
    // Catégorie branchée (AO 26-015-DO, lot R5) : la nature « effectifs » se mesure comme les autres.
    { categorie: "effectifs", disponible: true, raison: null }],
  "GET /api/absences": [
    { id: "abs-1", categorie: "dossier", personneId: "emp-1", teacherId: null, du: "2026-11-02", au: "2026-11-03", heures: 14, motif: "Congé", statut: "en_attente", source: null, reflet: false, suppleantId: null, remplacement: null, etablissementId: null, declareeLe: "2026-10-01T00:00:00.000Z", lien: { module: "payroll", id: "abs-1" }, pieces: 1, attestation: null, typeCode: "maladie" },
    { id: "va-1", categorie: "enseignement", personneId: "emp-1", teacherId: "t-1", du: "2026-10-21", au: "2026-10-21", heures: 3.5, motif: "Maladie", statut: "declaree", source: null, reflet: false, suppleantId: "t-9", remplacement: true, etablissementId: null, declareeLe: "2026-10-02T00:00:00.000Z", lien: { module: "varpay", id: "va-1" }, pieces: 0, attestation: null, typeCode: null },
    { id: "sa-1", categorie: "scolaire", personneId: "emp-2", teacherId: null, du: "2026-10-19", au: "2026-10-20", heures: null, motif: "MAL", statut: "declaree", source: null, reflet: false, suppleantId: null, remplacement: true, etablissementId: "e-1", declareeLe: "2026-10-03T00:00:00.000Z", lien: { module: "scolaire", id: "sa-1" }, pieces: 2, attestation: { le: "2026-10-21T12:00:00.000Z", par: "chef", commentaire: "Vu" }, typeCode: null },
  ],
  // Lots A-1 à A-3 : le tiroir des pièces et de l'attestation, le catalogue des types et leur effet.
  "GET /api/absences/enseignement/va-1/pieces": [{ id: "pc-1", nom: "certificat.pdf", typeMime: "application/pdf", taille: 2048, ajouteLe: "2026-10-02T00:00:00.000Z", ajouteParId: "usr-sa", ajouteParNom: "superadmin" }],
  "GET /api/payroll/absence-types": [
    { id: "typ-1", code: "maladie", libelle: "Maladie", effet: "paye", banqueId: null, justificatifRequis: true, actif: true, ordre: 1 },
    { id: "typ-2", code: "personnel", libelle: "Personnel", effet: "paye", banqueId: null, justificatifRequis: false, actif: true, ordre: 3 },
    { id: "typ-3", code: "mobile", libelle: "Congé mobile", effet: "banque", banqueId: "bnq-1", justificatifRequis: false, actif: false, ordre: 6 },
  ],
  "GET /api/rh/banques": [{ id: "bnq-1", code: "mobile", libelle: "Congés mobiles", plafondMinutes: null, decouvertAutorise: false, ordre: 1, actif: true }],
  // Gestion des effectifs (AO 26-015-DO, lot R1) : un descripteur à comportement, des unités, un milieu à preuves, une entrée de journal.
  "GET /api/effectifs/referentiels": [
    { type: "unites", libelle: "Unités", attributs: [{ nom: "parent", libelle: "Unité parente", type: "reference", reference: "unites" }] },
    { type: "statuts_emploi", libelle: "Statuts d'emploi", comportements: ["TEMPS_COMPLET", "TEMPS_PARTIEL_REGULIER"], attributs: [] },
    { type: "codes_absence", libelle: "Codes d'absence", comportements: ["PAYE", "NON_PAYE"], attributs: [
      { nom: "duree", libelle: "Catégorie de durée", type: "choix", valeurs: ["PONCTUELLE", "LONG_TERME"], obligatoire: true },
      { nom: "planifiable", libelle: "Peut être planifiée d'avance", type: "booleen" },
    ] },
  ],
  "GET /api/effectifs/referentiels/statuts_emploi": [
    { id: "r-1", type: "statuts_emploi", code: "TPR", libelleFr: "Temps partiel régulier", libelleEn: null, comportement: "TEMPS_PARTIEL_REGULIER", ordre: 1, attributs: {}, effectifDepuis: "2026-01-01", effectifJusqua: null },
  ],
  "GET /api/effectifs/referentiels/unites": [
    { id: "u-1", type: "unites", code: "EST", libelleFr: "Opérations Est", libelleEn: null, comportement: null, ordre: 0, attributs: {}, effectifDepuis: "2026-01-01", effectifJusqua: null },
  ],
  "GET /api/effectifs/milieux": [
    { id: "m-1", code: "C12", libelle: "Caserne 12", typeCode: "CASERNE", uniteCode: "EST", adresse: "1 rue Jarry", latitude: 45.58, longitude: -73.55, rayonMetres: 150, actif: true,
      preuves: [{ mecanisme: "GEOREPERAGE", exige: true, parametres: {} }] },
  ],
  "GET /api/effectifs/perimetres": [
    { id: "p-1", utilisateurId: "u-2", uniteCode: "EST", etendue: "SOUS_ARBRE", niveau: "PLANIFICATION", depuis: "2026-01-01", jusqua: null },
  ],
  "GET /api/effectifs/journal": [
    { id: "j-1", horodatage: "2026-09-29T10:00:00.000Z", acteurId: "usr-demo", origine: "UTILISATEUR", action: "milieu.cree", objetType: "milieu", objetId: "m-1", uniteCode: "EST", avant: null, apres: { code: "C12" } },
  ],
  // Lots R2 et R3 : un catalogue de faits, une règle, une période publiée avec un quart sous son minimum.
  "GET /api/effectifs/regles/faits": { DISPONIBILITE: [{ code: "quartsParSemaine", type: "nombre", libelle: "Quarts disponibles dans la semaine" }] },
  "GET /api/effectifs/regles": [{ id: "rg-1", contexte: "DISPONIBILITE", cle: "minimum-quarts", porteeType: "ORGANISME", porteeCode: null,
    versionCourante: { regleId: "rg-1", version: 1, cle: "minimum-quarts", contexte: "DISPONIBILITE", porteeType: "ORGANISME", porteeCode: null,
      condition: { fait: "quartsParSemaine", op: "<", valeur: 3 }, action: "BLOQUER", message: "Au moins 3 quarts", priorite: 0, effectifDepuis: "2026-01-01", effectifJusqua: null } }],
  "GET /api/effectifs/regles/evaluations": [],
  "GET /api/effectifs/planification/periodes": [{ id: "pr-1", uniteCode: "EST", nom: "Semaine 10", debut: "2030-03-04", fin: "2030-03-10", etat: "PUBLIEE", finCollecte: null }],
  "GET /api/effectifs/planification/periodes/pr-1/comblement": { equipages: 1, places: 2, pourvues: 1, taux: 50,
    sousMinimum: [{ equipageId: "eq-1", date: "2030-03-04", debut: 360, fin: 1080, milieuId: "m-1", pourvues: 1, minimum: 2, mode: "AUTOMATISE" }],
    quotas: [{ cle: "EST|*|*", plageCode: null, natureCode: null, jours: [{ date: "2030-03-04", quota: 2, origine: "SURCHARGE", operationnels: 0, atteint: false }] }] },
  "GET /api/effectifs/planification/periodes/pr-1/equipages": [{ id: "eq-1", periodeId: "pr-1", date: "2030-03-04", debut: 360, fin: 1080, natureCode: "REG", plageCode: null,
    milieuId: "m-1", modeAttribution: "AUTOMATISE", minimumOperationnel: 2, note: null, pourvues: 1, statut: "SOUS_MINIMUM", places: [
      { id: "pl-1", rang: 1, corpsEmploiCode: "PARA", posteId: null, ressourceId: "rs-1", ressourceNom: "Julie Roy", attribueePar: "UTILISATEUR", justificationForcage: null },
      { id: "pl-2", rang: 2, corpsEmploiCode: "PARA", posteId: null, ressourceId: null, ressourceNom: null, attribueePar: null, justificationForcage: null }] }],
  "GET /api/effectifs/planification/ressources": [{ id: "rs-1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA", titreEmploiCode: null,
    statutEmploiCode: null, conventionCode: null, anciennete: 3, actif: true }],
  // E-CH-3 : un poste, pour que la sélection multiple et le tiroir de modification en lot s'auditent.
  "GET /api/effectifs/planification/postes": [{ id: "po-1", uniteCode: "EST", code: "A-TC", libelle: "PSA jour", statutEmploiCode: "TC", corpsEmploiCode: "PARA",
    milieuId: "mi-1", natureCode: "REG", cycle: [["J", "J", "J", null, null, null, null]], debutCycle: "2030-03-04", parametres: {}, etat: "PUBLIE", titulaireId: null }],
  "GET /api/effectifs/planification/quotas": [],
  // Lot R4 : une période en collecte côté employé ; un dépôt en attente d'approbation côté planificateur.
  // Mon dossier d'effectif : les écrans personnels s'en servent pour savoir s'ils ont un geste à
  // offrir. Sans ce fait, l'audit mesurerait l'état vide « votre compte n'est relié à aucune
  // ressource » plutôt que l'écran dont on vérifie la géométrie.
  "GET /api/effectifs/ma-ressource": { ressource: { id: "rs-1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA", titreEmploiCode: null,
    statutEmploiCode: null, conventionCode: null, anciennete: 3, actif: true } },
  /*
    **Mon horaire** (planche s03) : sans cette doublure, l'écran rendait « votre compte n'est relié
    à aucune ressource » — et les trois boutons de semaine, ajoutés le 8 novembre, n'étaient donc
    jamais mesurés. Un écran ajouté sans son contrôle est une livraison incomplète ; un contrôle
    qui mesure l'état vide d'un écran l'est autant.
  */
  // Paie (ADR 0076) : un cycle préparé, une personne sans dossier, des remises et des relevés — chaque tableau rempli.
  "GET /api/paie/cycles/cy-1": { cycle: { id: "cy-1", debut: "2030-02-16", fin: "2030-03-01", datePaie: "2030-03-07", etat: "prepare", prepareLe: "2030-03-02T15:00:00Z", preparePar: "paie.admin" },
    bulletins: [{ id: "b-1", cycleId: "cy-1", ressourceId: "rs-1", matricule: "P1", nom: "Julie Roy", datePaie: "2030-03-07", heuresRegulieres: 70, heuresSupplementaires: 2,
    retenues: { brut: 2190, impotFederal: 150.1, impotQuebec: 190.2, rrq: 129.49, rrq2: 0, ae: 28.47, rqap: 9.42, gainsRrq: 2190, gainsAe: 2190, gainsRqap: 2190,
      employeur: { rrq: 129.49, rrq2: 0, ae: 39.86, rqap: 13.18, fss: 93.29, cnt: 1.31, cnesst: 38.98 } },
    totalRetenues: 507.68, net: 1682.32, cotisationsEmployeur: 316.11, coutEmployeur: 2506.11, feuilles: ["ft-1", "ft-2"] }] },
  "GET /api/paie/cycles": [{ id: "cy-1", debut: "2030-02-16", fin: "2030-03-01", datePaie: "2030-03-07", etat: "prepare", prepareLe: "2030-03-02T15:00:00Z", preparePar: "paie.admin" },
    { id: "cy-0", debut: "2030-02-02", fin: "2030-02-15", datePaie: "2030-02-21", etat: "traite", prepareLe: "x", preparePar: "paie.admin", traiteLe: "y", traitePar: "paie.admin" }],
  "GET /api/paie/dossiers": [
    { ressourceId: "rs-1", matricule: "P1", nom: "Julie Roy", dossier: { ressourceId: "rs-1", mode: "horaire", frequence: "aux-deux-semaines", tauxHoraire: 30, salaireAnnuel: null,
      montantPersonnelFederal: 16452, montantPersonnelQuebec: 18952 } },
    { ressourceId: "rs-2", matricule: "P2", nom: "Marc Dubé", dossier: null }],
  "GET /api/paie/remises": [{ mois: "2030-02", echeance: "2030-03-15", impotQuebec: 190.2, rrq: 258.98, rqap: 22.6, fss: 93.29, revenuQuebec: 565.07, impotFederal: 150.1, ae: 68.33, arc: 218.43, cnt: 1.31, cnesst: 38.98 }],
  "GET /api/paie/releves": [{ ressourceId: "rs-1", matricule: "P1", nom: "Julie Roy", rl1: { a: 2190, b: 129.49, rrq2: 0, c: 28.47, e: 190.2, g: 2190, h: 9.42, i: 2190 },
    t4: { c14: 2190, c17: 129.49, rrq2: 0, c18: 28.47, c22: 150.1, c24: 2190, c26: 2190, c55: 9.42, c56: 2190 } }],
  "GET /api/paie/mes-bulletins": [{ id: "b-1", cycleId: "cy-1", ressourceId: "rs-1", matricule: "P1", nom: "Julie Roy", datePaie: "2030-03-07", heuresRegulieres: 70, heuresSupplementaires: 2,
    retenues: { brut: 2190, impotFederal: 150.1, impotQuebec: 190.2, rrq: 129.49, rrq2: 0, ae: 28.47, rqap: 9.42, gainsRrq: 2190, gainsAe: 2190, gainsRqap: 2190,
      employeur: { rrq: 129.49, rrq2: 0, ae: 39.86, rqap: 13.18, fss: 93.29, cnt: 1.31, cnesst: 38.98 } },
    totalRetenues: 507.68, net: 1682.32, cotisationsEmployeur: 316.11, coutEmployeur: 2506.11, feuilles: ["ft-1", "ft-2"] }],
  "GET /api/paie/reglages": { annee: 2030, tauxFss: 0.0426, tauxCnesst: 0.0178, majorationSupplementaire: 0.5, validationTempsExigee: true },
  // ADR 0084 : règles de paie variable et validation des temps.
  "GET /api/paie/regles": [
    { id: "rg-1", code: "TAUX_INF_E3", libelle: "Infirmière, échelon 3", type: "taux-de-base", valeur: 34, titreEmploi: "INF", convention: "FIQ", echelon: 3,
      heureDebut: null, heureFin: null, jours: [], feriesSeulement: false, codesPaie: [], valideDu: "2030-01-01", valideAu: null, actif: true },
    { id: "rg-2", code: "PRIME_NUIT", libelle: "Prime de nuit", type: "prime-pourcentage", valeur: 0.12, titreEmploi: null, convention: null, echelon: null,
      heureDebut: "00:00", heureFin: "08:00", jours: [1, 2, 3, 4, 5], feriesSeulement: false, codesPaie: ["REG"], valideDu: "2030-01-01", valideAu: "2030-12-31", actif: true },
  ],
  "GET /api/paie/temps": {
    feuilles: [
      { feuilleId: "ft-1", ressourceId: "rs-1", matricule: "P1", nom: "Julie Roy", debut: "2030-02-17", etat: "approuvee", heuresRegulieres: 35, heuresSupplementaires: 2,
        anomalies: [], incalculable: null, validee: false, validable: true, validation: null },
      { feuilleId: "ft-2", ressourceId: "rs-2", matricule: "P2", nom: "Marc Dubé", debut: "2030-02-17", etat: "soumise", heuresRegulieres: 62, heuresSupplementaires: 0,
        anomalies: ["non-approuvee", "sans-taux", "heures-elevees"], incalculable: null, validee: false, validable: false, validation: null },
    ],
    sansFeuille: [{ ressourceId: "rs-3", matricule: "P3", nom: "Léa Côté", anomalies: ["aucune-feuille"] }],
  },
  "GET /api/paie/bareme": { annee: 2030, rrq: { tauxBase: 0.053, tauxSupplementaire: 0.01, exemption: 3500, mga: 74600, plafondSupplementaire: 85000, tauxRrq2: 0.04 },
    ae: { tauxEmploye: 0.013, maximumAssurable: 68900 }, rqap: { tauxEmploye: 0.0043, tauxEmployeur: 0.00602, maximumAssurable: 103000 },
    federal: { montantPersonnel: 16452 }, quebec: { montantPersonnel: 18952 } },
  // Communications cliniques (ADR 0074).
  "GET /api/alertes/reponses": [{ id: "rp-1", libelle: "J'arrive", semantique: "prise_en_charge", active: true }, { id: "rp-2", libelle: "Indisponible", semantique: "refus", active: false }],
  "GET /api/alertes/modeles/mo-1/versions": [
    { version: 2, titre: "Code bleu", corps: "Chambre à préciser", priorite: "critique", reponseIds: ["rp-1"], creeLe: "2031-01-15T14:00:00.000Z", creePar: "admin" },
    { version: 1, titre: "Code bleu (ancien)", corps: "Chambre", priorite: "critique", reponseIds: [], creeLe: "reprise", creePar: "reprise" }],
  "GET /api/alertes/modeles": [{ id: "mo-1", titre: "Code bleu", corps: "Chambre à préciser", priorite: "critique", reponseIds: ["rp-1"], groupeIds: ["gt-1"], version: 2, actif: true }],
  // R1-bis : droits de l'écran, listes de distribution, réglages d'organisme, groupes transverses (ST-G01, ST-G02, ST-G09).
  "GET /api/alertes/droits": { envoyer: true, critique: true, configurer: true },
  "GET /api/alertes/listes": [{ id: "l-garde", nom: "Garde de nuit — liste" }],
  "GET /api/alertes/reglages": { copieCourriel: true },
  // Notes sur les destinataires (ST-G12).
  "GET /api/alertes/notes": [{ personId: "julie", texte: "Joindre sur le cellulaire de garde", ecriteLe: "2030-03-04T14:00:00Z", ecritePar: "chef" }],
  // Utilisateurs actifs du mois (ST-G08).
  "GET /api/alertes/utilisateurs-actifs": { mois: "2030-03", total: 2, actifs: [
    { personId: "julie", nom: "Julie Roy", premiereTentative: "2030-03-04T14:02:00Z", alerteId: "al-1", titre: "Code bleu chambre 512" },
    { personId: "marc", nom: "Marc Dubé", premiereTentative: "2030-03-04T14:07:00Z", alerteId: "al-1", titre: "Code bleu chambre 512" }] },
  // Relances et cascades (ST-G04).
  "GET /api/alertes/politiques": [{ id: "po-1", nom: "Code bleu", arret: "prise_en_charge", version: 2, actif: true, etapes: [
    { cible: "initiaux", cibleId: null, canaux: ["poussee"], delaiMinutes: 5 }, { cible: "groupe", cibleId: "gt-1", canaux: ["texto", "courriel"], delaiMinutes: 10 }] }],
  "GET /api/alertes/envoyees/al-1/cascade": { cascade: { politique: { id: "po-1", nom: "Code bleu", arret: "prise_en_charge", version: 2, actif: true, etapes: [] },
    etape: 2, etapes: 2, echeanceLe: "2030-03-04T14:15:00Z", etat: "armee" },
    traces: [{ evenement: "armee", etape: 1, personnes: [], instant: "2030-03-04T14:02:00Z", par: "chef" },
      { evenement: "etape", etape: 1, personnes: ["julie"], instant: "2030-03-04T14:07:00Z", par: "cascade" }],
    appels: [{ personId: "julie", etape: 1, resultat: "repondu", touche: "1", dureeSecondes: 42, lanceLe: "2030-03-04T14:07:00Z", termineLe: "2030-03-04T14:08:00Z" }] },
  "GET /api/communication/groupes-transverses": [{ id: "gt-1", nom: "Garde de nuit", nature: "garde", actif: true,
    membres: [{ personId: "julie", nom: "Julie Roy", depuis: "2031-01-13", jusqua: "2031-01-19", moderateur: true }, { personId: "marc", nom: "Marc Dubé", depuis: null, jusqua: null, moderateur: false }] }],
  "GET /api/alertes/groupes/EST/membres": [{ personId: "julie", nom: "Julie Roy" }, { personId: "marc", nom: "Marc Dubé" }],
  "GET /api/alertes/groupes": [{ id: "EST", nom: "Opérations Est" }, { id: "gt-1", nom: "Garde de nuit" }],
  "GET /api/alertes/envoyees": [{ id: "al-1", titre: "Code bleu chambre 512", corps: "5e étage, répondez à la prise en charge", priorite: "critique", expediteurId: "chef", envoyeeLe: "2030-03-04T14:02:00Z", reponses: [{ id: "rp-1", libelle: "J'arrive", semantique: "prise_en_charge", active: true }], bilan: { destinataires: 2, lues: 1, repondues: 1, relances: 1, priseEnChargePar: "Julie Roy", priseEnChargeLe: "2030-03-04T14:03:00Z" },
    remises: [{ personId: "julie", nom: "Julie Roy", envoyeeLe: "2030-03-04T14:02:00Z", lueLe: "2030-03-04T14:03:00Z", reponseId: "rp-1", reponduLe: "2030-03-04T14:03:00Z" }, { personId: "marc", nom: "Marc Dubé", envoyeeLe: "2030-03-04T14:02:00Z", lueLe: null, reponseId: null, reponduLe: null }] }],
  "GET /api/alertes/recues": [{ id: "al-1", titre: "Code bleu chambre 512", corps: "5e étage, répondez à la prise en charge", priorite: "critique", expediteurId: "chef", envoyeeLe: "2030-03-04T14:02:00Z", reponses: [{ id: "rp-1", libelle: "J'arrive", semantique: "prise_en_charge", active: true }], maRemise: { personId: "julie", nom: "Julie Roy", envoyeeLe: "2030-03-04T14:02:00Z", lueLe: null, reponseId: null, reponduLe: null } }],
  // AD-07 : un quart offert à Julie à son tour (Mon horaire), et le réglage de l'offre (Absences et comblement).
  "GET /api/effectifs/offres/miennes": [{ id: "eofr-1", comblementId: "ecbl-1", absenceId: "abs-1", urgente: false, etat: "OUVERTE", creeLe: "2030-02-20T15:00:00.000Z",
    attribueeA: null, quart: { placeId: "pl-9", uniteCode: "EST", date: "2030-03-12", debut: 360, fin: 1080 },
    rangs: [{ rang: 1, ressourceId: "rs-1", nom: "Julie Roy", anciennete: 3, heuresComptees: 0, heuresOffertes: 12, etat: "OFFERT",
      offertLe: "2030-02-20T15:00:00.000Z", echeance: "2030-02-20T15:10:00.000Z", reponduLe: null, motif: null }] }],
  "GET /api/effectifs/offres/parametres": { mode: "CASCADE", delaiReponseMinutes: 10, seuilUrgenceMinutes: 120, fenetreRepartitionJours: 91 },
  "GET /api/effectifs/mon-horaire": { ressource: { id: "rs-1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA",
    titreEmploiCode: null, statutEmploiCode: null, conventionCode: null, anciennete: 3, actif: true },
    quarts: [
      { placeId: "pl-1", date: "2030-03-11", debut: 360, fin: 1080, uniteCode: "EST", corpsEmploiCode: "PARA", ressourceId: "rs-1", nom: "Julie Roy", milieuId: "mi-1", milieu: "Centre-ville" },
      // Un quart **sans milieu** : « — » plutôt qu'un site deviné, et la colonne se mesure aussi vide.
      { placeId: "pl-2", date: "2030-03-13", debut: 840, fin: 1320, uniteCode: "EST", corpsEmploiCode: "PARA", ressourceId: "rs-1", nom: "Julie Roy", milieuId: null, milieu: null }] },
  "GET /api/effectifs/disponibilites/moi": { ressource: { id: "rs-1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA", titreEmploiCode: null,
    statutEmploiCode: null, conventionCode: null, anciennete: 3, actif: true },
    periodes: [{ id: "pr-2", uniteCode: "EST", nom: "Semaine 11", debut: "2030-03-11", fin: "2030-03-17", etat: "COLLECTE", finCollecte: "2030-03-01",
      // Les règles annoncées avant le dépôt (E-GN-16) : une à l'organisme, une au statut d'emploi,
      // pour mesurer les deux formes de ligne — avec et sans la mention de portée.
      regles: [{ cle: "deux-quarts-par-semaine", action: "AVERTIR", message: "La convention attend au moins deux quarts par semaine", porteeType: "ORGANISME", porteeCode: null },
        { cle: "fins-de-semaine", action: "EXIGER_APPROBATION", message: null, porteeType: "STATUT_EMPLOI", porteeCode: "TPR" }],
      depot: { periodeId: "pr-2", ressourceId: "rs-1", conformite: "AVERTIE", motifs: [{ cle: "peu-d-heures", action: "AVERTIR", message: "Seulement 24 h offertes" }],
        deposeLe: "2030-02-20T10:00:00.000Z", deposePar: "u-1", approuveLe: null, approuvePar: null,
        plages: [{ date: "2030-03-11", debut: 360, fin: 1080, type: "PREFERENCE" }, { date: "2030-03-13", debut: 0, fin: 1440, type: "INDISPONIBLE" }] } }] },
  "GET /api/effectifs/disponibilites/periodes/pr-1": [
    { ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", depot: { conformite: "APPROBATION_REQUISE", motifs: [{ cle: "fins-de-semaine", action: "EXIGER_APPROBATION", message: "Une fin de semaine offerte demande l'accord du chef" }], plages: 1, deposeLe: "2030-02-20T10:00:00.000Z" } },
    { ressourceId: "rs-2", nom: "Marc Côté", matricule: "P2", depot: null }],
  "GET /api/effectifs/disponibilites/periodes/pr-1/ressources/rs-1": { periodeId: "pr-1", ressourceId: "rs-1", conformite: "APPROBATION_REQUISE",
    motifs: [{ cle: "fins-de-semaine", action: "EXIGER_APPROBATION", message: "Une fin de semaine offerte demande l'accord du chef" }],
    deposeLe: "2030-02-20T10:00:00.000Z", deposePar: "u-1", approuveLe: null, approuvePar: null, plages: [{ date: "2030-03-09", debut: 360, fin: 1080, type: "DISPONIBLE" }] },
  "GET /api/effectifs/disponibilites/places/pl-2/candidats": { admissibles: [{ ressourceId: "rs-1", nom: "Julie Roy", preference: true, anciennete: 3, heuresPlanifiees: 12 }],
    exclus: [{ ressourceId: "rs-2", nom: "Marc Côté", motif: "aucune disponibilité déposée pour cette période" }] },
  // Lot R5 : une absence refusée par les règles, et son impact sur le comblement.
  // Le harnais apparie sur le chemin, pas sur la chaîne de requête : la liste vaut pour tout filtre.
  "GET /api/effectifs/absences": [{ id: "ab-1", ressourceId: "rs-1", ressourceNom: "Julie Roy", uniteCode: "EST", codeAbsence: "MAL", du: "2030-03-04", au: "2030-03-05",
    motif: null, statut: "REFUSEE_AUTO", motifsRegles: [{ cle: "comblement-minimal", action: "REFUSER", message: "Le comblement tomberait à 0 %" }], decideePar: null, motifDecision: null, derogationCode: null },
    // Lot U5 : une absence accordée, dont le comblement automatique a attribué, offert (avis) et réservé.
    { id: "ab-2", ressourceId: "rs-1", ressourceNom: "Julie Roy", uniteCode: "EST", codeAbsence: "MAL", du: "2030-03-11", au: "2030-03-12",
      motif: null, statut: "ACCEPTEE", motifsRegles: [], decideePar: "u-9", motifDecision: "Hospitalisation", derogationCode: "MAL" }],
  "GET /api/effectifs/absences/ab-1/impact": { places: [{ id: "pl-1", date: "2030-03-04", debut: 360, fin: 1080 }], tauxAvant: 100, tauxApres: 50 },
  "GET /api/effectifs/absences/ab-2": { id: "ab-2", ressourceId: "rs-1", ressourceNom: "Julie Roy", uniteCode: "EST", codeAbsence: "MAL", du: "2030-03-11", au: "2030-03-12",
    motif: null, statut: "ACCEPTEE", motifsRegles: [], decideePar: "u-9", motifDecision: "Hospitalisation", derogationCode: "MAL" },
  "GET /api/effectifs/absences/ab-2/impact": { places: [], tauxAvant: 100, tauxApres: 100 },
  "GET /api/effectifs/absences/ab-2/comblement": {
    places: [
      { id: "cb-1", placeId: "pl-7", date: "2030-03-11", debut: 360, fin: 1080, mode: "AUTOMATISE", resultat: "ATTRIBUEE", attribueeA: "rs-2", attribueeANom: "Marc Côté",
        occupeeParId: "rs-2", occupeeParNom: "Marc Côté", creeLe: "2030-03-01T12:00:00.000Z",
        avis: [{ ressourceId: "rs-2", nom: "Marc Côté", canal: "courriel", statut: "sent", erreur: null, envoyeLe: "2030-03-01T12:00:00.000Z" }] },
      { id: "cb-2", placeId: "pl-8", date: "2030-03-12", debut: 360, fin: 1080, mode: "MANUEL", resultat: "OFFERTE", attribueeA: null, attribueeANom: null,
        occupeeParId: null, occupeeParNom: null, creeLe: "2030-03-01T12:00:00.000Z",
        avis: [{ ressourceId: "rs-2", nom: "Marc Côté", canal: "courriel", statut: "sent", erreur: null, envoyeLe: "2030-03-01T12:00:00.000Z" },
          { ressourceId: "rs-3", nom: "Léa Tremblay", canal: "texto", statut: "failed", erreur: "aucun numéro de téléphone connu", envoyeLe: "2030-03-01T12:00:00.000Z" }] },
    ],
    remplacement: null, remplacementPossible: true, raisonRemplacement: null, postesProposes: [{ id: "po-1", code: "EST-1", libelle: "Poste EST-1", titulaire: true }] },
  "GET /api/effectifs/referentiels/codes_absence": [{ code: "MAL", libelleFr: "Maladie", comportement: "PAYE", attributs: { duree: "PONCTUELLE" }, effectifDepuis: "2026-01-01", effectifJusqua: null }],
  // Lot R6 : Julie a un quart à échanger ; Marc lui en propose un ; un échange attend le chef.
  "GET /api/effectifs/echanges/moi": { ressource: { id: "rs-1", matricule: "P1", nom: "Julie Roy", uniteCode: "EST", corpsEmploiCode: "PARA", titreEmploiCode: null,
    statutEmploiCode: null, conventionCode: null, anciennete: 3, actif: true },
    quarts: [{ placeId: "pl-1", date: "2030-03-04", debut: 360, fin: 1080, uniteCode: "EST", corpsEmploiCode: "PARA", ressourceId: "rs-1", nom: "Julie Roy" }] },
  "GET /api/effectifs/echanges/places/pl-1/demandables": [{ placeId: "pl-9", date: "2030-03-06", debut: 360, fin: 1080, uniteCode: "EST", corpsEmploiCode: "PARA", ressourceId: "rs-2", nom: "Marc Côté" }],
  "GET /api/effectifs/echanges": [{ id: "ec-1", statut: "A_VALIDER", actionRegles: "EXIGER_APPROBATION",
    motifsRegles: [{ cle: "entre-periodes", action: "EXIGER_APPROBATION", message: "Pour Marc : échange entre deux périodes : le chef valide" }],
    placeCedeeId: "pl-9", placeVoulueId: "pl-1", ressourceAId: "rs-2", ressourceBId: "rs-1", nomA: "Marc Côté", nomB: "Julie Roy",
    quartCede: { date: "2030-03-06", debut: 360, fin: 1080 }, quartVoulu: { date: "2030-03-11", debut: 360, fin: 1080 }, uniteA: "EST", uniteB: "EST",
    proposeLe: "2030-02-20T10:00:00.000Z", motifDecision: null }],
  // Lot R7 : une campagne au tour de Julie (rang 1), et un remplacement long terme ouvert.
  "GET /api/effectifs/agph/campagnes": [{ id: "cg-1", uniteCode: "EST", annee: 2031, nom: "AGPH 2031", corpsEmploiCode: "PARA", etat: "CHOIX", delaiChoixMinutes: 30 },
    { id: "cg-2", uniteCode: "EST", annee: 2032, nom: "AGPH 2032", corpsEmploiCode: "PARA", etat: "REVISION", delaiChoixMinutes: 30, budgetHeuresSemaine: 40 },
    { id: "cg-3", uniteCode: "EST", annee: 2030, nom: "AGPH 2030", corpsEmploiCode: "PARA", etat: "CLOSE", delaiChoixMinutes: 30, budgetHeuresSemaine: null }],
  // E-CH-1 : une campagne en révision (semaine type, budget, attributions à réviser) et une close (postes transférés).
  "GET /api/effectifs/agph/campagnes/cg-2": { id: "cg-2", uniteCode: "EST", annee: 2032, nom: "AGPH 2032", corpsEmploiCode: "PARA", etat: "REVISION", delaiChoixMinutes: 30, budgetHeuresSemaine: 40,
    rangs: [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, anciennete: 12, etat: "A_CHOISI", tourDebut: null, tourFin: null },
      { ressourceId: "rs-2", nom: "Marc Côté", matricule: "P2", rang: 2, anciennete: 8, etat: "PASSE", tourDebut: null, tourFin: null }],
    choix: [{ ressourceId: "rs-1", posteId: "po-1", posteCode: "A-TC", mode: "EMPLOYE", choisiLe: "2031-01-06T13:10:00.000Z" }],
    postesNonPourvus: [{ posteId: "po-2", posteCode: "B-TC", etat: "PUBLIE" }] },
  "GET /api/effectifs/agph/campagnes/cg-2/postes-disponibles": { campagne: { id: "cg-2", uniteCode: "EST", annee: 2032, nom: "AGPH 2032", corpsEmploiCode: "PARA", etat: "REVISION", delaiChoixMinutes: 30 },
    monRang: { ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, anciennete: 12, etat: "A_CHOISI", tourDebut: null, tourFin: null }, peutChoisir: false,
    postes: [{ id: "po-2", code: "B-TC", libelle: "Poste B", statutEmploiCode: "TC", admissible: false, motifs: ["Le temps complet est réservé aux deux premiers rangs (rang 3)"] }] },
  "GET /api/effectifs/agph/campagnes/cg-2/disponibilites": { campagneId: "cg-2", etat: "REVISION", budgetHeuresSemaine: 40, heuresCampagne: 48, tauxBudget: 120, deposes: 1,
    lignes: [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, depot: { ressourceId: "rs-1", conformite: "AVERTIE", heures: 48, deposeLe: "2031-01-02T10:00:00.000Z", deposePar: "u-1",
      motifs: [{ cle: "budget", action: "AVERTIR", message: "Les disponibilités offertes dépassent le budget (120 %)" }],
      plages: [{ jour: 1, debut: 360, fin: 1080, type: "DISPONIBLE" }, { jour: 2, debut: 360, fin: 1080, type: "PREFERENCE" }, { jour: 3, debut: 0, fin: 1440, type: "DISPONIBLE" }] } },
      { ressourceId: "rs-2", nom: "Marc Côté", matricule: "P2", rang: 2, depot: null }] },
  "GET /api/effectifs/agph/campagnes/cg-2/disponibilites/moi": { ressourceId: "rs-1", conformite: "AVERTIE", heures: 48, deposeLe: "2031-01-02T10:00:00.000Z", deposePar: "u-1",
    motifs: [{ cle: "budget", action: "AVERTIR", message: "Les disponibilités offertes dépassent le budget (120 %)" }],
    plages: [{ jour: 1, debut: 360, fin: 1080, type: "DISPONIBLE" }, { jour: 2, debut: 360, fin: 1080, type: "PREFERENCE" }, { jour: 3, debut: 0, fin: 1440, type: "DISPONIBLE" }] },
  "GET /api/effectifs/agph/campagnes/cg-3": { id: "cg-3", uniteCode: "EST", annee: 2030, nom: "AGPH 2030", corpsEmploiCode: "PARA", etat: "CLOSE", delaiChoixMinutes: 30, budgetHeuresSemaine: null,
    rangs: [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, anciennete: 12, etat: "A_CHOISI", tourDebut: null, tourFin: null }],
    choix: [{ ressourceId: "rs-1", posteId: "po-1", posteCode: "A-TC", mode: "AUTOMATIQUE", choisiLe: "2030-01-06T13:30:00.000Z" }],
    postesNonPourvus: [{ posteId: "po-2", posteCode: "B-TC", etat: "VACANT" }, { posteId: "po-3", posteCode: "C-TPR", etat: "VACANT" }] },
  "GET /api/effectifs/agph/campagnes/cg-3/postes-disponibles": { campagne: { id: "cg-3", uniteCode: "EST", annee: 2030, nom: "AGPH 2030", corpsEmploiCode: "PARA", etat: "CLOSE", delaiChoixMinutes: 30 },
    monRang: { ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, anciennete: 12, etat: "A_CHOISI", tourDebut: null, tourFin: null }, peutChoisir: false, postes: [] },
  "GET /api/effectifs/agph/campagnes/cg-3/disponibilites": { campagneId: "cg-3", etat: "CLOSE", budgetHeuresSemaine: null, heuresCampagne: 0, tauxBudget: null, deposes: 0,
    lignes: [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, depot: null }] },
  "GET /api/effectifs/agph/campagnes/cg-1/disponibilites": { campagneId: "cg-1", etat: "CHOIX", budgetHeuresSemaine: null, heuresCampagne: 0, tauxBudget: null, deposes: 0,
    lignes: [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, depot: null }, { ressourceId: "rs-2", nom: "Marc Côté", matricule: "P2", rang: 2, depot: null }] },
  // P-GH-4 : l'attribution de fin de collecte, faite seule par l'ordonnanceur.
  "GET /api/effectifs/disponibilites/periodes/pr-1/attribution-automatique": { periodeId: "pr-1", finCollecte: "2030-02-28", echeanceLe: "2030-03-01T00:00:00.000Z", statut: "faite",
    derniere: { le: "2030-03-01T00:00:05.000Z", attribuees: 2, restees: 1 } },
  "GET /api/effectifs/agph/campagnes/cg-1": { id: "cg-1", uniteCode: "EST", annee: 2031, nom: "AGPH 2031", corpsEmploiCode: "PARA", etat: "CHOIX", delaiChoixMinutes: 30,
    rangs: [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, anciennete: 12, etat: "A_SON_TOUR", tourDebut: "2031-01-06T13:00:00.000Z", tourFin: "2031-01-06T13:30:00.000Z" },
      { ressourceId: "rs-2", nom: "Marc Côté", matricule: "P2", rang: 2, anciennete: 8, etat: "EN_ATTENTE", tourDebut: null, tourFin: null }], choix: [] },
  "GET /api/effectifs/agph/campagnes/cg-1/postes-disponibles": { campagne: { id: "cg-1", uniteCode: "EST", annee: 2031, nom: "AGPH 2031", corpsEmploiCode: "PARA", etat: "CHOIX", delaiChoixMinutes: 30 },
    monRang: { ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", rang: 1, anciennete: 12, etat: "A_SON_TOUR", tourDebut: "2031-01-06T13:00:00.000Z", tourFin: "2031-01-06T13:30:00.000Z" }, peutChoisir: true,
    postes: [{ id: "po-1", code: "A-TC", libelle: "Poste A", statutEmploiCode: "TC", admissible: true, motifs: [] },
      { id: "po-2", code: "B-TC", libelle: "Poste B", statutEmploiCode: "TC", admissible: false, motifs: ["Le temps complet est réservé aux deux premiers rangs (rang 3)"] }] },
  "GET /api/effectifs/agph/remplacements": [{ id: "rl-1", uniteCode: "EST", posteId: "po-3", posteCode: "C-TPR", origine: "POSTE_VACANT", debut: "2031-06-02", fin: "2031-06-30",
    finManifestation: "2031-05-20T12:00:00.000Z", etat: "OUVERT", attribueANom: null, placesAttribuees: 0, interesses: 2 },
    // E-CH-9 : un remplacement laissé non comblé, motif à l'appui ; ses quarts sont passés à la gestion régulière.
    { id: "rl-2", uniteCode: "EST", posteId: "po-2", posteCode: "B-TC", origine: "ABSENCE", debut: "2031-07-01", fin: "2031-07-31",
      finManifestation: "2031-06-20T12:00:00.000Z", etat: "NON_COMBLE", attribueANom: null, placesAttribuees: 0, interesses: 1,
      transfereLe: "2031-06-18T14:00:00.000Z", quartsTransferes: 12, motifNonComble: "Budget de l'unité atteint" }],
  "GET /api/effectifs/agph/remplacements/rl-1/interesses": [{ ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", anciennete: 12, deposeLe: "2031-05-02T10:00:00.000Z" }],
  // Lot R8 : Julie en pause sur son quart, et l'entrée de Marc sans preuve, à approuver.
  "GET /api/effectifs/presence/moi": { ressource: { id: "rs-1", nom: "Julie Roy" },
    quart: { placeId: "pl-1", milieuId: "m-1", milieu: "Caserne 12", uniteCode: "EST", natureCode: "REG", date: "2031-01-15", debut: 360, fin: 1080,
      debutPrevu: "2031-01-15T11:00:00.000Z", finPrevue: "2031-01-15T23:00:00.000Z", presencePrealableMinutes: 15, prochains: ["FIN_PAUSE"], minutesPayees: 310,
      segments: [{ debut: "2031-01-15T10:45:00.000Z", fin: "2031-01-15T11:00:00.000Z", type: "PRESENCE_PREALABLE", minutes: 15 },
        { debut: "2031-01-15T11:00:00.000Z", fin: "2031-01-15T15:55:00.000Z", type: "TRAVAIL", minutes: 295 }, { debut: "2031-01-15T15:55:00.000Z", fin: null, type: "PAUSE", minutes: 5 }],
      // La forme **que le Node rend** depuis le lot multi-site (E-GP-7, E-GP-15). La doublure ne les
      // portait pas : l'écran s'effondrait, et l'audit mesurait sa page d'erreur. Deux sites et une
      // activité, pour que les blocs de répartition et de déplacement soient eux aussi mesurés.
      parMilieu: [{ milieuId: "m-1", milieu: "Caserne 12", minutes: 200 }, { milieuId: "m-2", milieu: "Caserne Nord", minutes: 110 }],
      parActivite: [{ activiteCode: "INTERVENTION", minutes: 180 }, { activiteCode: null, minutes: 130 }],
      deplacements: [{ quand: "2031-01-15T14:20:00.000Z", de: "m-1", vers: "m-2", deLibelle: "Caserne 12", versLibelle: "Caserne Nord" }] },
    pointages: [{ id: "po-1", ressourceId: "rs-1", nom: "Julie Roy", placeId: "pl-1", milieuId: "m-1", uniteCode: "EST", sens: "ENTREE", horodatage: "2031-01-15T10:45:00.000Z",
      origine: "APPAREIL", saisieId: null, resultat: "CONFORME", ecart: null, distanceMetres: 12, actionRegles: "ACCEPTER", motifsRegles: [], statut: "ENREGISTRE", justification: null,
      saisiLe: "2031-01-15T10:45:00.000Z", saisiPar: "u-1", decideLe: null, decidePar: null, motifDecision: null, corrobore: true,
      preuves: [{ mecanisme: "GEOREPERAGE", exigee: true, fournie: true, satisfaite: true, distanceMetres: 12 }] }] },
  "GET /api/effectifs/presence/pointages": [{ id: "po-2", ressourceId: "rs-2", nom: "Marc Côté", placeId: "pl-9", milieuId: "m-1", uniteCode: "EST", sens: "ENTREE",
    horodatage: "2031-01-15T11:20:00.000Z", origine: "APPAREIL", saisieId: null, resultat: "ECART", ecart: "PREUVE_ABSENTE", distanceMetres: null, actionRegles: "EXIGER_APPROBATION",
    motifsRegles: [{ cle: "retard", action: "EXIGER_APPROBATION", message: "retard de 20 min" }], statut: "A_APPROUVER", justification: null,
    saisiLe: "2031-01-15T11:20:00.000Z", saisiPar: "u-2", decideLe: null, decidePar: null, motifDecision: null, corrobore: false,
    preuves: [{ mecanisme: "GEOREPERAGE", exigee: true, fournie: false, satisfaite: false }] }],
  "GET /api/effectifs/presence/milieux/m-1/code-qr": { code: "m-1.6989.AbCdEfGhIjKlMnOp", valideJusqua: "2031-01-15T11:25:00.000Z" },
  // Les fiches se relisent à l'ouverture : chaque route de détail a son doublon, sinon l'appariement
  // par préfixe lui rendrait la liste — et une liste rendue comme un objet fait tomber l'arbre React.
  // Relectures à l'ouverture d'une fiche : l'appariement du harnais se fait par préfixe, chaque enregistrement a donc son doublon.
  "GET /api/effectifs/absences/ab-1": { id: "ab-1", ressourceId: "rs-1", ressourceNom: "Julie Roy", uniteCode: "EST", codeAbsence: "MAL", du: "2030-03-04", au: "2030-03-05",
    motif: null, statut: "REFUSEE_AUTO", motifsRegles: [{ cle: "comblement-minimal", action: "REFUSER", message: "Le comblement tomberait à 0 %" }], decideePar: null, motifDecision: null, derogationCode: null },
  "GET /api/effectifs/echanges/ec-1": { id: "ec-1", statut: "A_VALIDER", actionRegles: "EXIGER_APPROBATION",
    motifsRegles: [{ cle: "entre-periodes", action: "EXIGER_APPROBATION", message: "Pour Marc : échange entre deux périodes : le chef valide" }],
    placeCedeeId: "pl-9", placeVoulueId: "pl-1", ressourceAId: "rs-2", ressourceBId: "rs-1", nomA: "Marc Côté", nomB: "Julie Roy",
    quartCede: { date: "2030-03-06", debut: 360, fin: 1080 }, quartVoulu: { date: "2030-03-11", debut: 360, fin: 1080 }, uniteA: "EST", uniteB: "EST",
    proposeLe: "2030-02-20T10:00:00.000Z", motifDecision: null },
  "GET /api/effectifs/agph/remplacements/rl-1": { id: "rl-1", uniteCode: "EST", posteId: "po-3", posteCode: "C-TPR", origine: "POSTE_VACANT", debut: "2031-06-02", fin: "2031-06-30",
    finManifestation: "2031-05-20T12:00:00.000Z", etat: "OUVERT", attribueANom: null, placesAttribuees: 0, interesses: 2 },
  "GET /api/effectifs/presence/pointages/po-2": { id: "po-2", ressourceId: "rs-2", nom: "Marc Côté", placeId: "pl-9", milieuId: "m-1", uniteCode: "EST", sens: "ENTREE",
    horodatage: "2031-01-15T11:20:00.000Z", origine: "APPAREIL", saisieId: null, resultat: "ECART", ecart: "PREUVE_ABSENTE", distanceMetres: null, actionRegles: "EXIGER_APPROBATION",
    motifsRegles: [{ cle: "retard", action: "EXIGER_APPROBATION", message: "retard de 20 min" }], statut: "A_APPROUVER", justification: null,
    saisiLe: "2031-01-15T11:20:00.000Z", saisiPar: "u-2", decideLe: null, decidePar: null, motifDecision: null, corrobore: false,
    preuves: [{ mecanisme: "GEOREPERAGE", exigee: true, fournie: false, satisfaite: false }] },
  "GET /api/effectifs/presence/ressources/rs-2/pointages": [],
  // Lot R12 : le plan d'alignement sur l'organigramme, les quarts d'une personne pour un échange proposé pour elle.
  "GET /api/effectifs/liaisons/organisation": { date: "2031-01-15", creer: [{ code: "OUEST", libelle: "Opérations Ouest", parent: "US" }],
    modifier: [{ code: "EST", libelle: "Opérations Est", parent: "US", avant: { libelle: "Est", parent: null } }], identiques: ["US"],
    ignorees: [{ code: "Bloc 7", motif: "code hors du format des référentiels" }], horsOrganigramme: ["VIEUX"] },
  "GET /api/effectifs/echanges/ressources/rs-1/quarts": [{ placeId: "pl-1", date: "2030-03-04", debut: 360, fin: 1080, uniteCode: "EST", corpsEmploiCode: "PARA", ressourceId: "rs-1", nom: "Julie Roy" }],
  // Lot R11 : pays restreints au Canada, une règle d'alerte, une alerte partie ; la rétention.
  "GET /api/organisme/pays": { pays: ["CA"], enteteConfiguree: true },
  "GET /api/organisme/alertes": [{ id: "ra-1", evenement: "EXPORT_TELECHARGE", destinataires: ["securite@exemple.ca"], creeLe: "2031-01-01T00:00:00.000Z", creePar: "usr-demo" }],
  "GET /api/organisme/alertes/journal": [{ id: "al-1", regleId: "ra-1", evenement: "EXPORT_TELECHARGE", quoi: "journal-effectifs.csv (GET /api/effectifs/journal)", qui: "admin", envoyees: 1, erreur: null, horodatage: "2031-01-15T10:00:00.000Z", destinataires: ["securite@exemple.ca"] }],
  "GET /api/effectifs/retention": [
    { classe: "HORAIRES", libelle: "Horaires (quarts, places, échanges)", annees: 5, limite: "2026-01-15", aPurger: 12 },
    { classe: "JOURNAUX", libelle: "Journal des décisions et évaluations des règles", annees: null, limite: null, aPurger: 0 },
  ],
  // Lot R10 : le PFT en HTTP, un message rejeté à rejouer.
  "GET /api/effectifs/integrations/connecteurs": [
    { code: "PFT", libelle: "PFT — production des feuilles de temps (lots de paie)", sens: "SORTANT", mode: "HTTP", url: "https://pft.exemple.ca/api", jetonRef: "PFT_JETON", actif: true, enAttente: 0, enEchec: 1, curseur: null, jeuEssai: [] },
    { code: "RAO", libelle: "RAO — répartition assistée par ordinateur (horodatages)", sens: "ENTRANT", mode: "SIMULATEUR", url: null, jetonRef: null, actif: false, enAttente: 0, enEchec: 0, curseur: null, jeuEssai: [] },
  ],
  "GET /api/effectifs/integrations/messages": [{ id: "em-1", connecteur: "PFT", type: "LOT_PAIE", cle: "abc", charge: {}, etat: "REJETE", tentatives: 1, refExterne: null, motif: "code de paie 999 inconnu", deposeLe: "2031-01-20T10:00:00.000Z", deposePar: "u-1", traiteLe: "2031-01-20T10:01:00.000Z" }],
  // Historique d'une valeur de référentiel et versions d'une règle (câblés avec le lot R9).
  "GET /api/effectifs/referentiels/statuts_emploi/TPR/historique": [
    { id: "r-0", type: "statuts_emploi", code: "TPR", libelleFr: "Temps partiel", libelleEn: null, comportement: "TEMPS_PARTIEL_REGULIER", ordre: 1, attributs: {}, effectifDepuis: "2025-01-01", effectifJusqua: "2026-01-01" },
    { id: "r-1", type: "statuts_emploi", code: "TPR", libelleFr: "Temps partiel régulier", libelleEn: null, comportement: "TEMPS_PARTIEL_REGULIER", ordre: 1, attributs: {}, effectifDepuis: "2026-01-01", effectifJusqua: null },
  ],
  "GET /api/effectifs/regles/rg-1": { regle: { id: "rg-1", contexte: "DISPONIBILITE", cle: "minimum-quarts", porteeType: "ORGANISME", porteeCode: null },
    versions: [{ id: "rv-1", regleId: "rg-1", version: 1, cle: "minimum-quarts", contexte: "DISPONIBILITE", porteeType: "ORGANISME", porteeCode: null,
      condition: { fait: "quartsParSemaine", op: "<", valeur: 3 }, action: "BLOQUER", message: "Au moins 3 quarts", priorite: 0, effectifDepuis: "2026-01-01", effectifJusqua: null }] },
  "GET /api/effectifs/perimetres/moi": { toutes: true, lecture: [], planification: [], gestion: [] },
  // Lot R9 : la feuille soumise de Julie, au premier de deux paliers.
  "GET /api/effectifs/referentiels/types_heure": [
    { id: "th-1", type: "types_heure", code: "REG", libelleFr: "Régulier", libelleEn: null, comportement: "TRAVAILLEE", ordre: 0, attributs: { codePaie: "100" }, effectifDepuis: "2026-01-01", effectifJusqua: null },
  ],
  "GET /api/effectifs/temps/moi": [{ ...{ id: "ef-1", ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19", statut: "SOUMISE", palier: 1, paliersRequis: 2, soumiseLe: "2031-01-20T10:00:00.000Z", approuveeLe: null }, statut: "RETOURNEE", palier: 0 }],
  "GET /api/effectifs/temps/feuilles": [{ id: "ef-1", ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19", statut: "SOUMISE", palier: 1, paliersRequis: 2, soumiseLe: "2031-01-20T10:00:00.000Z", approuveeLe: null }],
  "GET /api/effectifs/temps/feuilles/ef-1": { ...{ id: "ef-1", ressourceId: "rs-1", nom: "Julie Roy", matricule: "P1", uniteCode: "EST", debut: "2031-01-13", fin: "2031-01-19", statut: "SOUMISE", palier: 1, paliersRequis: 2, soumiseLe: "2031-01-20T10:00:00.000Z", approuveeLe: null }, statut: "RETOURNEE", palier: 0,
    segments: [{ id: "sg-1", date: "2031-01-15", debut: "2031-01-15T11:00:00.000Z", fin: "2031-01-15T23:00:00.000Z", typeHeureCode: "REG", comportement: "TRAVAILLEE", source: "POINTAGE", placeId: "pl-1", version: 2, remplaceId: "sg-0", motif: "Sortie pointée en retard",
      corrobore: true, sourcesCorroborantes: ["BORNE"], aConfirmer: false, sourceExterne: null,
      description: "Transport inter-hospitalier, retour à la base", attributs: { vehicule: "A-12", km: 42, garde: true, ancien: "valeur conservée" } },
    // E-RF-13 : un temps proposé par une source tierce seule, à confirmer (le bouton « Confirmer » est mesuré).
    { id: "sg-2", date: "2031-01-16", debut: "2031-01-16T11:00:00.000Z", fin: "2031-01-16T23:00:00.000Z", typeHeureCode: "REG", comportement: "TRAVAILLEE", source: "EXTERNE", placeId: "pl-2", version: 1, remplaceId: null, motif: null,
      corrobore: null, sourcesCorroborantes: [], aConfirmer: true, sourceExterne: "BORNE" }],
    correlation: [{ date: "2031-01-16", sources: ["BORNE", "RAO"], concordants: 1, divergents: 2 }],
    flux: { demandeId: "areq-1", statut: "REJETE", palierCourant: null, etapes: [
      { id: "as-1", palier: 1, niveau: 1, libelle: "le niveau GESTION de l'unité", statut: "REJETEE", decidePar: "u-9", decideLe: "2031-01-20T12:00:00.000Z", pourLeCompteDe: null },
      { id: "as-2", palier: 2, niveau: 2, libelle: "rh (personne désignée)", statut: "EN_ATTENTE", decidePar: null, decideLe: null, pourLeCompteDe: null }] },
    totaux: { REG: 720 }, heuresSupplementaires: { parJour: { "2031-01-15": 240 }, total: 240 },
    ecarts: [{ placeId: "pl-1", date: "2031-01-15", planifie: 720, declare: 720, ecart: 0 }],
    anomalies: [{ code: "REGLE", date: "2031-01-15", message: "travail un jour férié : majoration à vérifier", bloquante: false }],
    decisions: [{ palier: 1, decision: "RETOURNER", motif: "Ajoutez la réunion du 14", decideLe: "2031-01-20T12:00:00.000Z", decidePar: "u-9" }] },
  "GET /api/effectifs/temps/feuilles/ef-1/segments/sg-1/historique": [
    { id: "sg-1", date: "2031-01-15", debut: "2031-01-15T11:00:00.000Z", fin: "2031-01-15T23:00:00.000Z", typeHeureCode: "REG", comportement: "TRAVAILLEE", source: "POINTAGE", placeId: "pl-1", version: 2, remplaceId: "sg-0", motif: "Sortie pointée en retard" },
    { id: "sg-0", date: "2031-01-15", debut: "2031-01-15T11:00:00.000Z", fin: "2031-01-15T23:10:00.000Z", typeHeureCode: "REG", comportement: "TRAVAILLEE", source: "POINTAGE", placeId: "pl-1", version: 1, remplaceId: null, motif: null },
  ],
  // ADR 0075 : un annuaire Entra et un annuaire OpenID Connect (Keycloak) ; le second fait paraître la colonne « Fournisseur » complète.
  "GET /api/auth/annuaires": { rappel: "https://app.urgences-sante.qc.ca/api/auth/sso/annuaires/callback", annuaires: [
    { id: "ann-1", nom: "Entra du siège", fournisseur: "entra", locataireEntra: "0f8fad5b-d9cb-469f-a165-70867728950e", emetteur: null, clientId: "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      variableSecret: "ANNUAIRE_SIEGE", secretDeploye: true, domaines: ["urgences-sante.qc.ca"], revendication: "groups", correspondances: [{ groupe: "g-admin", role: "admin" }], roleParDefaut: null, actif: true },
    { id: "ann-2", nom: "Keycloak Urgences-santé", fournisseur: "oidc", locataireEntra: "", emetteur: "https://sso.urgences-sante.qc.ca/realms/vina-geps", clientId: "vina-geps",
      variableSecret: "ANNUAIRE_KEYCLOAK", secretDeploye: false, domaines: ["urgences-sante.qc.ca"], revendication: "roles",
      correspondances: [{ groupe: "vina-admin", role: "admin" }, { groupe: "vina-chef", role: "manager" }], roleParDefaut: null, actif: true }] },
  // Mon équipe (comme dans Nexu VINA) : un direct, un indirect, une personne à rattacher ; une suppléance en cours.
  "GET /api/personnel/equipe": { moiId: "e-chef", moiNom: "Marc Lavoie", membres: [
    { id: "e-julie", nom: "Julie Roy", titre: "Paramédic", courriel: "julie.roy@urgences-sante.qc.ca", superieurId: "e-chef", superieurNom: "Marc Lavoie", niveau: 1 },
    { id: "e-luc", nom: "Luc Bouchard", titre: null, courriel: null, superieurId: "e-julie", superieurNom: "Julie Roy", niveau: 2 }],
    sansSuperieur: [{ id: "e-lea", nom: "Léa Morin", titre: "Paramédic", courriel: null, superieurId: null, superieurNom: null, niveau: 0 }] },
  "GET /api/approvals/delegations": [{ id: "adel-1", toUserId: "usr-dir", toNom: "Diane Roy", fromDate: "2031-03-20", toDate: "2031-03-27", createdAt: "2031-03-01T10:00:00.000Z" }],
  // L'organigramme que lit l'approbateur « supérieur hiérarchique » : Julie relève de Marc.
  "GET /api/personnel/hierarchie": [{ id: "e-chef", nom: "Marc Lavoie", superieurId: null }, { id: "e-julie", nom: "Julie Roy", superieurId: "e-chef" }],
  // La feuille qui s'adapte à l'emploi : un champ de chaque type, et un désactivé qui garde sa valeur (colonne conservée).
  "GET /api/effectifs/temps/champs": [
    { code: "vehicule", libelle: "Véhicule", type: "LISTE", valeurs: ["A-12", "B-07"], requis: true, actif: true, ordre: 0 },
    { code: "km", libelle: "Kilomètres", type: "NOMBRE", valeurs: [], requis: false, actif: true, ordre: 1 },
    { code: "garde", libelle: "De garde", type: "BOOLEEN", valeurs: [], requis: false, actif: true, ordre: 2 },
    { code: "note", libelle: "Note", type: "TEXTE", valeurs: [], requis: false, actif: true, ordre: 3 },
    { code: "ancien", libelle: "Ancien champ", type: "TEXTE", valeurs: [], requis: false, actif: false, ordre: 4 }],
  "GET /api/effectifs/temps/parametres": { seuilJourHeures: 8, seuilSemaineHeures: 40, paliersApprobation: 2, toleranceEcartMinutes: 15,
    toleranceCorroborationMinutes: 5, actionNonCorrobore: "SIGNALER", actionSourcesDivergentes: "BLOQUER" },
  // E-GP-6 : le flux d'approbation paramétré (paliers, conditions, approbateurs) dans Paramètres des feuilles.
  "GET /api/effectifs/temps/flux": { parDefaut: { FEUILLE: false, POINTAGE: true },
    regles: [{ id: "arule-1", objet: "FEUILLE", palier: 1, champ: "toujours", operateur: "eq", valeur: 1, approbateur: { type: "PERIMETRE", valeur: "GESTION" } },
      { id: "arule-2", objet: "FEUILLE", palier: 2, champ: "heuresSupplementaires", operateur: "gte", valeur: 4, approbateur: { type: "PERSONNE", valeur: "usr-dir" } }],
    champs: { FEUILLE: [{ code: "toujours", libelle: "Toujours (1)", type: "nombre" }, { code: "heuresSupplementaires", libelle: "Heures supplémentaires", type: "nombre" }],
      POINTAGE: [{ code: "toujours", libelle: "Toujours (1)", type: "nombre" }, { code: "saisieAPosteriori", libelle: "Déclaré après coup (1) ou pointé (0)", type: "nombre" }] } },
  "GET /api/effectifs/temps/rapports": [{ groupe: "EST", feuilles: 3, planifie: 2160, declare: 2500, supplementaires: 700, anomalies: 2 }],
  // P-RF-1 : le constructeur de rapports sur une source d'effectifs (période, taux), et un relevé enregistré.
  "GET /api/rapports/sources": [{ code: "effectifs_places", libelle: "Effectifs — comblement des quarts", module: "effectifs_planification", faits: { periode: "jour" }, capacite: "effectifs.manage",
    champs: [{ code: "jour", libelle: "Jour", type: "date", groupable: true }, { code: "unite", libelle: "Unité", type: "texte", groupable: true },
      { code: "uniteNiveau1", libelle: "Unité de niveau 1 (racine)", type: "texte", groupable: true }, { code: "natureQuart", libelle: "Nature de quart", type: "texte", groupable: true },
      { code: "places", libelle: "Places", type: "nombre", groupable: false }, { code: "pourvues", libelle: "Places pourvues", type: "nombre", groupable: false },
      { code: "equipagesSousMinimum", libelle: "Équipages sous le minimum", type: "nombre", groupable: false }] }],
  "GET /api/rapports/agregats": ["somme", "moyenne", "compte", "minimum", "maximum", "taux"],
  "GET /api/rapports/operateurs": ["egal", "different", "contient", "superieur", "inferieur", "entre", "parmi"],
  "GET /api/rapports/formats": ["csv", "xlsx", "pdf", "json"],
  "GET /api/rapports/enregistres": [{ id: "rap-1", nom: "Comblement hebdomadaire", description: "Taux de places pourvues", modifieLe: "2031-01-20T10:00:00.000Z",
    definition: { source: "effectifs_places", groupes: ["unite"], colonnes: [{ champ: "unite" }, { champ: "pourvues", agregat: "taux", denominateur: "places" }],
      filtres: [{ champ: "jour", operateur: "entre", valeurs: ["2031-01-13", "2031-01-19"] }] } }],
  "POST /api/rapports/executer": { colonnes: [{ champ: "unite", libelle: "Unité", type: "texte" }, { champ: "pourvues", libelle: "Places pourvues / Places (%)", type: "nombre" }],
    lignes: [{ unite: "EST", pourvues: 66.67 }], total: 1, tronque: false },
  "POST /api/rapports/enregistres/rap-1/executer": { colonnes: [{ champ: "unite", libelle: "Unité", type: "texte" }, { champ: "pourvues", libelle: "Places pourvues / Places (%)", type: "nombre" }],
    lignes: [{ unite: "EST", pourvues: 66.67 }], total: 1, tronque: false },
  "GET /api/effectifs/temps/export-paie": { debut: "2031-01-13", feuilles: ["ef-1"], lignes: [{ matricule: "P1", date: "2031-01-15", codePaie: "100", heures: 8 }],
    empreinte: "3f1c0a9d5b7e2c4f6a8b0d1e3f5a7c9b1d3e5f7a9c1b3d5e7f9a1c3b5d7e9f1a" },
  // ADR 0029 : la matrice « Mes notifications » et la carte « Notifier une personne » se rendent avec un catalogue et des préférences.
  "GET /api/notifications/evenements": [
    { code: "feuille.decidee", libelle: "Feuille de temps approuvée ou refusée", module: "paie", moduleLibelle: "Paie", destinataire: "l'employé", defauts: { courriel: true, texto: false, poussee: true }, essentiel: false },
    { code: "remplacement.annule", libelle: "Remplacement annulé", module: "scolaire", moduleLibelle: "Remplacements scolaires", destinataire: "le suppléant attribué", defauts: { courriel: true, texto: true, poussee: true }, essentiel: true },
  ],
  "GET /api/notifications/preferences": { courriel: true, texto: false, poussee: true, evenements: {}, updatedAt: null },
  "GET /api/personnel/employes/suggestions": [{ id: "emp-1", nom: "Tremblay, Léa", titre: "Enseignante", courriel: null, statut: "active" }],
  "GET /api/scolaire/motifs": [{ code: "MAL", libelle: "Maladie", corpsEmploiCode: null, justificatifRequis: false, remplacementParDefaut: true, actif: true }],
  // Le soutien de l'administrateur : un billet résolu par le soutien, avec ses pièces — à noter, puis à fermer.
  "GET /api/soutien/tickets": [{
    id: "b-1", numero: 12, tenantId: "tnt-demo", auteurId: "usr-demo", auteurNom: "admin", sujet: "Export vide", description: "Le classeur n'a aucune ligne.", niveau: "N2", categorie: "donnees", impact: "equipe",
    ecran: "/admin/scolaire", navigateur: "Chrome 130 · macOS", statut: "resolu", langue: "fr", ouvertLe: "2026-09-14T08:00:00.000Z", echeanceLe: "2026-09-14T10:00:00.000Z", prisEnChargeLe: null, resoluLe: "2026-09-14T09:00:00.000Z", fermeLe: null, traitePar: "usr-sa",
    satisfaction: null, satisfactionCommentaire: null, satisfactionLe: null, sla: { etat: "resolu_a_temps", ecartMinutes: 60 },
    messages: [{ id: "m-1", auteurId: "usr-sa", auteurNom: "superadmin", texte: "Correctif livré.", interne: false, le: "2026-09-14T08:30:00.000Z" }],
    pieces: [{ id: "p-1", messageId: null, nom: "capture.png", typeMime: "image/png", taille: 12_000, ajouteLe: "2026-09-14T08:00:00.000Z", ajouteParNom: "admin" }, { id: "p-2", messageId: "m-1", nom: "correctif.pdf", typeMime: "application/pdf", taille: 40_000, ajouteLe: "2026-09-14T08:30:00.000Z", ajouteParNom: "superadmin" }],
  }],
  // L'écran des intégrations se mesure avec des cartes, un webhook et son formulaire : vide, il ne montrerait rien.
  "GET /api/integrations/etat": [
    { code: "grics", nom: "GRICS — Paie et GRH (Espace API)", etat: "simule", detail: "simulateur : aucun identifiant client", derniereActiviteLe: "2026-09-10T08:00:00.000Z", erreursRecentes: 1, ecran: "scolaire:reprise" },
    { code: "webhooks", nom: "Webhooks sortants (événements métier)", etat: "configure", detail: "1 webhook(s), 0 suspendu(s), 0 envoi(s) en attente", derniereActiviteLe: null, erreursRecentes: 0, ecran: "admin:integrations" },
  ],
  // La console téléphonique se mesure avec un appel en cours et ses compteurs : vide, son suivi n'apparaît pas.
  // Lot P-2 : une feuille soumise avec ses jours, pour que l'onglet Feuilles des Approbations rende l'approbation par jour (dont la correction dépliée).
  "GET /api/payroll/timesheets/pending-for-me": [{ id: "ts-1", employeeId: "emp-1", weekStart: "2026-09-14", weekEnd: "2026-09-20", status: "submitted", totalHours: 23.5, comment: null, approverId: "usr-demo", submittedAt: "2026-09-18T12:00:00.000Z", candidateApprovers: ["usr-demo"], createdBy: "lea" }],
  "GET /api/payroll/timesheets/ts-1/jours": [
    { date: "2026-09-14", heures: 8, saisies: [{ id: "te-1", projectId: "P-SOUD", hours: 8, timeType: "J", description: null }], statut: "approuve", decidePar: "usr-marc", decideLe: "2026-09-18T12:00:00.000Z", commentaire: null },
    { date: "2026-09-15", heures: 8, saisies: [{ id: "te-2", projectId: "P-SOUD", hours: 8, timeType: "J", description: null }], statut: "refuse", decidePar: "usr-marc", decideLe: "2026-09-18T12:00:00.000Z", commentaire: "heures à revoir" },
    { date: "2026-09-16", heures: 7.5, saisies: [{ id: "te-3", projectId: "P-SOUD", hours: 7.5, timeType: "J", description: null }], statut: "a_decider", decidePar: null, decideLe: null, commentaire: null },
  ],
  // Lot P-1 : un groupe et une tâche pour que l'onglet Tâches et postes rende le poste planifiable (conditions, formations requises, habilités).
  "GET /api/communication/groupes": [{ id: "grp-1", code: "SOUD", nom: "Soudure", chefNom: null, chefCourriel: null, chefTelephone: null, informationsReference: null, actif: 1, createdAt: "x" }],
  "GET /api/horaires/taches": [{ id: "tch-1", groupeId: "grp-1", code: "SOUD-1", libelle: "Soudeur, ligne 1", ordre: 1, actif: true, cases: [], permetAjustementHeures: false }],
  /**
   * **La communication du personnel** — un fil, ses bulles, un appel, une liste.
   *
   * Les stubs ne sont pas du remplissage : un état vide et un fil de deux bulles n'ont pas la
   * même géométrie, et c'est la géométrie que cet audit mesure. Sans eux, les trois écrans
   * seraient contrôlés dans l'état le moins représentatif de leur usage.
   */
  "GET /api/communication/groupes/grp-1/membres": [
    { id: "mb-1", groupeId: "grp-1", personId: "julie.roy", role: "membre", actif: 1, nom: "Julie Roy" },
    { id: "mb-2", groupeId: "grp-1", personId: "marc.dube", role: "membre", actif: 1, nom: "Marc Dubé" }],
  // La forme du serveur (`LectureMessagerie.nonLus`) : un total et les fils. Un tableau nu, ici avant le
  // 13 nov. 2026, laissait la liste des fils vide — elle n'avait jamais été mesurée.
  "GET /api/communication/conversations/non-lus": { total: 1, conversations: [{ conversationId: "cv-1", sujet: "Couverture du quart de nuit", nonLus: 1,
    dernierMessageLe: "2031-01-15T22:10:00.000Z", dernierAuteurId: "marc.dube", dernierExtrait: "Je peux prendre la nuit" }] },
  "GET /api/communication/conversations": [{ id: "cv-1", sujet: "Couverture du quart de nuit — véhicule 412", type: "groupe",
    groupeId: "grp-1", archiveeLe: null, createdAt: "2031-01-15T21:00:00.000Z",
    participants: [{ personId: "julie.roy", jusqua: null }, { personId: "marc.dube", jusqua: null }] }],
  "GET /api/communication/conversations/cv-1/messages": [
    { id: "ms-1", conversationId: "cv-1", auteurId: "julie.roy", corps: "Le 412 sort à 23 h avec un seul paramédic.",
      envoyeLe: "2031-01-15T21:05:00.000Z", priveA: null, luParMoi: true, pieces: [] },
    { id: "ms-2", conversationId: "cv-1", auteurId: "marc.dube", corps: "Je peux prendre la nuit si on décale demain.",
      envoyeLe: "2031-01-15T22:10:00.000Z", priveA: null, luParMoi: false, pieces: [] }],
  "GET /api/communication/brouillons": [],
  "GET /api/communication/acheminement/unites": {
    unites: [
      { uniteId: "EST", nom: "Secteur Est", parDefaut: false, modifieLe: "2026-11-13T10:00:00.000Z", modifiePar: "julie.roy",
        canaux: [{ canal: "sms", mode: "prioritaire", ordrePriorite: 1, parDefaut: false, libelle: "Texto (SMS) — Prioritaire — passe le mode silencieux" },
                 { canal: "appel", mode: "sonnerie", ordrePriorite: 2, parDefaut: false, libelle: "Appel téléphonique — Sonnerie" }] },
      { uniteId: "OUEST", nom: "Secteur Ouest", parDefaut: true, modifieLe: null, modifiePar: null,
        canaux: [{ canal: "courriel", mode: "sonnerie", ordrePriorite: 1, parDefaut: true, libelle: "Courriel — Sonnerie" }] },
    ],
    canaux: [{ code: "courriel", libelle: "Courriel" }, { code: "sms", libelle: "Texto (SMS)" }, { code: "appel", libelle: "Appel téléphonique" }],
    modes: [{ code: "sonnerie", libelle: "Sonnerie" }, { code: "vibration", libelle: "Vibration" }, { code: "prioritaire", libelle: "Prioritaire — passe le mode silencieux" }],
  },
  "GET /api/communication/appels/en-cours": null,
  "GET /api/communication/appels-groupe/en-cours": [],
  "GET /api/communication/appels": [{ id: "ap-1", de: "julie.roy", vers: "marc.dube", sens: "sortant", etat: "termine",
    lanceLe: "2031-01-15T21:30:00.000Z", repondeLe: "2031-01-15T21:30:20.000Z", termineLe: "2031-01-15T21:34:00.000Z", dureeSecondes: 220 }],
  "GET /api/communication/listes-distribution": [{ id: "ld-1", nom: "Secteur Est — tous", partagee: true, creeePar: "julie.roy",
    membres: [{ nature: "groupe", cible: "grp-1" }] }],
  "GET /api/horaires/equipes/grp-1/semaine": { groupeId: "grp-1", du: "2026-09-21", au: "2026-09-27", jours: ["2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24", "2026-09-25", "2026-09-26", "2026-09-27"],
    membres: [{ personId: "emp-1", role: "contremaitre", quarts: [{ id: "q-1", date: "2026-09-21", debut: 420, fin: 900, heures: 8, personId: "emp-1", tacheId: "tch-1", code: "SOUD-1", libelle: "Soudeur, ligne 1", nature: "regulier", tauxHoraireCents: 3150, primeCents: 100, remplaceQuartId: null, periodeId: "per-1" }], heures: 8 }, { personId: "emp-2", role: "membre", quarts: [], heures: 0 }],
    aPourvoir: [{ id: "q-2", date: "2026-09-22", debut: 420, fin: 900, heures: 8, personId: null, tacheId: "tch-1", code: "SOUD-1", libelle: "Soudeur, ligne 1", nature: "regulier", tauxHoraireCents: null, primeCents: null, remplaceQuartId: null, periodeId: "per-1" }],
    besoins: [{ tacheId: "tch-1", jour: "2026-09-21", debut: 420, fin: 900, requis: 3, modifiePar: "usr-demo", modifieLe: "x" }] },
  "GET /api/horaires/equipes/grp-1/journee": { groupeId: "grp-1", date: "2026-09-21", membres: [
    { personId: "emp-1", quarts: [{ id: "q-1", date: "2026-09-21", debut: 420, fin: 900, heures: 8, personId: "emp-1", tacheId: "tch-1", code: "SOUD-1", libelle: "Soudeur, ligne 1", nature: "regulier", tauxHoraireCents: 3150, primeCents: 100, remplaceQuartId: null, periodeId: "per-1" }], absences: [{ id: "abs-9", status: "pending", reason: "Grippe", typeCode: "maladie" }] },
    { personId: "emp-2", quarts: [], absences: [] },
  ], aPourvoir: [] },
  // Lot P-4 : une offre d'heures supplémentaires et ses propositions, pour l'onglet Heures supplémentaires et Mon compte › Mes heures supplémentaires.
  "GET /api/horaires/hs/offres": [{ id: "o-1", groupeId: "grp-1", tacheId: "tch-1", date: "2026-09-26", debut: 960, fin: 1200, requis: 2, motif: "Commande urgente", statut: "ouverte", creeePar: "usr-demo", creeeLe: "x" }],
  "GET /api/horaires/hs/attributions": [
    { id: "a-1", offreId: "o-1", personId: "emp-1", rang: 1, justification: "0 h supplémentaire(s) sur 12 semaines", statut: "proposee", compensation: null, quartId: null, approuveePar: null, approuveeLe: null, motifRefus: null, creeeLe: "x" },
    { id: "a-2", offreId: "o-1", personId: "emp-2", rang: 2, justification: "4 h supplémentaire(s) sur 12 semaines", statut: "acceptee", compensation: "reprise", quartId: null, approuveePar: null, approuveeLe: null, motifRefus: null, creeeLe: "x" },
  ],
  "GET /api/horaires/hs/offres/o-1/candidats": { retenus: [{ personId: "emp-1", rang: 1, justification: "0 h supplémentaire(s) sur 12 semaines" }], ecartes: [{ personId: "emp-3", raison: "non éligible au poste" }] },
  // Lot P-5 : la règle de pointage, un punch et deux propositions (retard, absence) pour l'onglet Pointage ; une demande en attente pour le tiroir « Modifier ».
  "GET /api/communication/groupes/grp-1/regle-pointage": { graceMinutes: 5, arrondiMinutes: 15, codeAbsence: "maladie", couperAutomatiquement: false, modifiePar: "usr-demo", modifieLe: "x" },
  "GET /api/horaires/pointage/punchs": [{ id: "p-1", idExterne: "m-1", personId: "emp-1", date: "2026-09-21", minute: 432, sens: "entree", source: "csv", importeLe: "x" }],
  "GET /api/horaires/pointage/propositions": [
    { id: "pr-1", groupeId: "grp-1", quartId: "q-1", personId: "emp-1", date: "2026-09-21", type: "retard", retardMinutes: 12, coupeMinutes: 15, codeAbsence: null, statut: "proposee", decidePar: null, decideLe: null, commentaire: null, absenceId: null, creeeLe: "x" },
    { id: "pr-2", groupeId: "grp-1", quartId: "q-2", personId: "emp-2", date: "2026-09-21", type: "absence", retardMinutes: null, coupeMinutes: null, codeAbsence: "maladie", statut: "acceptee", decidePar: "usr-demo", decideLe: "x", commentaire: "Injoignable", absenceId: "abs-9", creeeLe: "x" },
  ],
  "GET /api/payroll/absence-requests": [{ id: "abs-1", tenantId: "tnt-demo", employeeId: "emp-1", startDate: "2026-10-05", endDate: "2026-10-05", hours: 8, reason: "Rendez-vous", status: "pending", approverId: "usr-demo", comment: null, source: null, sourceId: null, typeCode: "personnel", submittedAt: "x", decidedAt: null, decidedBy: null }],
  // Lot P-6 : un férié avec ses sorts, une statistique, pour l'onglet Fériés et statistiques ; le travail planifié (avec férié) pour la carte de la feuille de temps.
  "GET /api/horaires/feries": [{ date: "2026-10-12", nom: "Action de grâce", sorts: [{ personId: "emp-1", ferie: "2026-10-12", sort: "travaille", compensation: "conge_compensatoire", congeLe: "2026-10-26" }] }],
  "GET /api/horaires/statistiques/absences": [{ periode: "2026-10-12", cle: "grp-1", libelle: "Soudure", absences: 1, heures: 8, personnes: 1 }],
  "GET /api/payroll/time-entries/week": { employeeId: "emp-1", weekStart: "2026-10-12", weekEnd: "2026-10-18", days: ["2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18"], rows: [], grandTotal: 0, periodLabel: "P21", period: 21, week: 1, status: null, timesheetId: null, remainingHours: 40,
    planifie: [{ quartId: "q-7", date: "2026-10-12", tacheId: "tch-1", libelle: "Soudeur, ligne 1", debut: 420, fin: 900, heures: 8, nature: "ferie_travaille", tauxHoraireCents: 3150, primeCents: 100, ferie: { sort: "travaille", compensation: "conge_compensatoire", congeLe: "2026-10-26" } }] },
  "GET /api/horaires/taches/tch-1/poste": { posteId: "pst-1", categorieSalariale: "Classe 7", poste: { code: "SOUD-A", titre: "Soudeur classe A", echelleMin: 52000, echelleMax: 68000 } },
  "GET /api/horaires/taches/tch-1/conditions": [{ nature: "taux_horaire", valeurCents: 3150, plage: "toutes", depuis: "2026-01-01", jusqua: null }, { nature: "prime_quart", valeurCents: 175, plage: "nuit", depuis: "2026-01-01", jusqua: null }],
  "GET /api/horaires/taches/tch-1/formations-requises": [{ formationId: "frm-1", bloquante: true, libelle: "Secourisme" }],
  "GET /api/horaires/taches/tch-1/habilites": [{ personId: "emp-1", source: "derivee", certifications: [{ formationId: "frm-1", libelle: "SST", statut: "valide", expireLe: "2027-01-10" }] }, { personId: "emp-2", source: "manuelle", certifications: [{ formationId: "frm-1", libelle: null, statut: "absente", expireLe: null }] }],
  // La forme complète du catalogue : la fiche d'établissement (reprise) et la page Postes le lisent aussi.
  "GET /api/rh/catalogue-postes": [{ id: "pst-1", code: "SOUD-A", titre: "Soudeur classe A", categorie: "soutien", corpsEmploiCode: null, etablissementId: null, etp: 1, echelleMin: 52000, echelleMax: 68000, exigences: null, actif: true, occupation: { pourcentage: 0, capacite: 100, statut: "vacant" }, nomEtablissement: null }],
  "GET /api/rh/catalogue-postes/categories": [{ code: "soutien", libelle: "Personnel de soutien" }],
  "GET /api/rh/formations": [
    { id: "frm-1", code: "SST-101", titre: "Secourisme", description: null, fournisseur: "CNESST", modalite: "presentiel", dureeMinutes: 480, coutCents: 45000, delivreCertification: true, validiteMois: 24, actif: true, createdAt: "x" },
    { id: "frm-2", code: "CWB-1", titre: "Soudage CWB", description: null, fournisseur: null, modalite: "presentiel", dureeMinutes: 480, coutCents: 0, delivreCertification: true, validiteMois: 36, actif: true, createdAt: "x" },
  ],
  "GET /api/horaires/console/tableau-de-bord": {
    appels: [{ id: "ap-1", agentId: "tel-1", personId: "p1", etat: "en_cours", provenance: "besoin", demarreLe: "2026-09-13T08:00:00.000Z", delaiRetourMinutes: 15 }],
    parEtat: { en_cours: 1, retenu: 0, attente_retour: 0, echec: 0 }, messages: {},
  },
  // Un besoin ouvert avec un sollicité : le détail montre le panneau « Joindre » (texto, appel, journal).
  "GET /api/scolaire/besoins": [{ id: "b-1", nature: "surnumeraire", corpsEmploiCode: "ENS", etablissementId: "etab-1", specialiteCode: null, clienteleCode: null, absenceId: null, rechercheId: "r-1", attribueA: null, attribueNom: null, consignes: null,
    plages: [{ date: "2026-10-19", partie: "journee", periodes: [] }], statut: "ouvert", filtrage: { retenus: 1, ecartes: [] },
    sollicitations: [{ personId: "p1", nom: "Léa Bouchard", emiseLe: "2026-09-13T08:00:00.000Z", reponse: null, saisiePar: null }], conversionEnVue: false, createdAt: "2026-09-13T08:00:00.000Z" }],
  "GET /api/scolaire/besoins/b-1": { id: "b-1", nature: "surnumeraire", corpsEmploiCode: "ENS", etablissementId: "etab-1", specialiteCode: null, clienteleCode: null, absenceId: null, rechercheId: "r-1", attribueA: null, attribueNom: null, consignes: null,
    plages: [{ date: "2026-10-19", partie: "journee", periodes: [] }], statut: "ouvert", filtrage: { retenus: 1, ecartes: [] },
    sollicitations: [{ personId: "p1", nom: "Léa Bouchard", emiseLe: "2026-09-13T08:00:00.000Z", reponse: null, saisiePar: null }], conversionEnVue: false, createdAt: "2026-09-13T08:00:00.000Z" },
  "GET /api/scolaire/besoins/b-1/communications": [{ id: "c-1", personId: "p1", nom: "Léa Bouchard", canal: "texto", numeroExpediteur: "+18195550100", numeroDestinataire: "••••••••0101", texte: "Bonjour", statut: "envoye", emisLe: "2026-09-13T08:05:00.000Z" }],
  "GET /api/scolaire/communications/numero-client": { numeroClient: "+18195550100" },
  // Les règles RH sur les absences se mesurent avec des lignes et leurs boutons d'action : c'est là
  // qu'une cellule sortie du flux tabulaire posait ses boutons sur la ligne suivante (14 sept. 2026).
  "GET /api/rh/regles": [
    { id: "rg-1", nom: "Justificatif après 3 jours", contexte: "absence", champ: "joursConsecutifs", operateur: ">=", valeur: "3", action: "exiger_justificatif", message: "Une pièce justificative est exigée au-delà de trois jours.", priorite: 50, actif: true, enVigueurDu: null, enVigueurAu: null, condition: "joursConsecutifs ≥ 3", declenchements: 0 },
    { id: "rg-2", nom: "Approbation en période d'examens", contexte: "absence", champ: "motifCode", operateur: "=", valeur: "CONGE", action: "exiger_approbation", message: "Approbation de la direction requise.", priorite: 10, actif: false, enVigueurDu: "2026-06-01", enVigueurAu: "2026-06-30", condition: "motifCode = CONGE", declenchements: 4 },
  ],
  "GET /api/rh/regles/vocabulaire": { contextes: ["absence"], champs: [{ code: "joursConsecutifs", libelle: "Jours consécutifs", type: "nombre", operateurs: [">=", ">", "="] }, { code: "motifCode", libelle: "Motif", type: "texte", operateurs: ["=", "!="] }, { code: "justificatifFourni", libelle: "Justificatif fourni", type: "booleen", operateurs: ["="] }],
    actions: [{ code: "avertir", libelle: "Avertir" }, { code: "exiger_justificatif", libelle: "Exiger un justificatif" }, { code: "exiger_approbation", libelle: "Exiger l'approbation" }, { code: "refuser", libelle: "Refuser" }] },
  // Lot E-2 : une absence « à approuver » que l'appelant peut décider — les cases et les boutons de décision se mesurent.
  "GET /api/scolaire/absences": [
    { id: "sa-1", personId: "emp-2", nom: "Léa Bouchard", corpsEmploiCode: "ENS", etablissementId: "etab-1", motifCode: "MAL", plages: [{ date: "2026-10-19", partie: "journee", periodes: [] }], justificatifFourni: false, justifiee: true, commentaire: null, consignes: null, courrielsAviser: [], statut: "a_approuver", declareeLe: "2026-10-03T00:00:00.000Z", declareePar: "u", besoinId: null, decideePar: null, decideeLe: null, decisionCommentaire: null, peutDecider: true },
    { id: "sa-2", personId: "emp-2", nom: "Léa Bouchard", corpsEmploiCode: "ENS", etablissementId: "etab-1", motifCode: "MAL", plages: [{ date: "2026-10-20", partie: "am", periodes: [] }], justificatifFourni: false, justifiee: true, commentaire: null, consignes: null, courrielsAviser: [], statut: "refusee", declareeLe: "2026-10-03T00:00:00.000Z", declareePar: "u", besoinId: null, decideePar: "usr-dir", decideeLe: "2026-10-04T00:00:00.000Z", decisionCommentaire: "période d'examens", peutDecider: false },
  ],
  "POST /api/scolaire/absences/decider-en-masse": { decidees: ["sa-1"], refusees: [] },
  "POST /api/payroll/absence-requests/decider-en-masse": { decidees: ["abs-1"], refusees: [] },
  // Lot E-4 : la campagne de disponibilités — une campagne ouverte, deux réponses, une silencieuse.
  "GET /api/scolaire/suppleants/campagnes/camp-1": { id: "camp-1", tenantId: "tnt-demo", lanceeLe: "2026-09-17T10:00:00.000Z", lanceePar: "usr-demo", echeance: "2026-10-01", relanceLe: null, statut: "ouverte", message: "Rentrée", total: 3,
    repondus: [{ personId: "p1", nom: "Léa Bouchard", envoyeeLe: "2026-09-17T10:00:00.000Z", repondueLe: "2026-09-18T10:00:00.000Z", decision: "confirme" }, { personId: "p2", nom: "Nadir Haddad", envoyeeLe: "2026-09-17T10:00:00.000Z", repondueLe: "2026-09-18T11:00:00.000Z", decision: "indisponible" }],
    nonRepondants: [{ personId: "p3", nom: "Rose Tremblay", envoyeeLe: "2026-09-17T10:00:00.000Z", repondueLe: null, decision: null }] },
  "GET /api/scolaire/suppleants/campagnes": [{ id: "camp-1", echeance: "2026-10-01", lanceeLe: "2026-09-17T10:00:00.000Z", relanceLe: null, statut: "ouverte", message: "Rentrée", total: 3, repondus: 2 }],
  "POST /api/scolaire/suppleants/campagnes/camp-1/desactiver": { desactives: ["p3"], refusees: [{ id: "p2", raison: "déjà inactif" }] },
  // Lot E-4 : les annonces — le bloc de l'accueil (une non lue, une lue) et la liste de l'administration.
  "GET /api/notifications/annonces/toutes": [{ id: "an-1", titre: "Rentrée", corps: "Les horaires changent lundi.", roles: ["employee"], du: "2026-08-24", au: "2099-09-04", auteur: "usr-demo", creeeLe: "x", lectures: 2 }],
  "GET /api/notifications/annonces": [
    { id: "an-1", titre: "Rentrée", corps: "Les horaires changent lundi.", roles: ["employee"], du: "2026-08-24", au: "2099-09-04", auteur: "usr-demo", creeeLe: "x", lue: false },
    { id: "an-0", titre: "Stationnement", corps: "Le stationnement nord est fermé cette semaine.", roles: [], du: "2026-08-24", au: "2099-09-04", auteur: "usr-demo", creeeLe: "x", lue: true },
  ],
  "POST /api/notifications/annonces/an-1/lue": { id: "an-1", lue: true },
  // Lot E-3 : le panneau d'emprunt d'identité (Administration › Comptes), réglage ouvert, un emprunt en cours au journal.
  "GET /api/auth/emprunt/reglage": { empruntIdentite: true, modifieLe: "2026-09-17T10:00:00.000Z", modifiePar: "usr-demo" },
  // ADR 0041 : le journal des actions des utilisateurs — une réussie, une refusée sous emprunt (acteur réel écrit).
  "GET /api/auth/journal-actions": { total: 2, items: [
    { id: "ja-1", instant: "2026-09-19T14:02:11.412Z", tenantId: "tnt-demo", userId: "u-2", utilisateur: "lea", acteurReel: null, acteurReelNom: null, methode: "PUT", chemin: "/api/notifications/preferences", statut: 200, dureeMs: 41, requeteId: "a3f9c2d1e4b7" },
    { id: "ja-2", instant: "2026-09-19T14:03:11.412Z", tenantId: "tnt-demo", userId: "u-2", utilisateur: "lea", acteurReel: "usr-demo", acteurReelNom: "admin", methode: "POST", chemin: "/api/scolaire/absences/{id}/approuver", statut: 403, dureeMs: 12, requeteId: "b4e8d1c2f3a9" },
  ] },
  "GET /api/auth/emprunt/journal": [{ id: "e-1", emprunteurId: "usr-demo", emprunteur: "admin", cibleId: "u-2", cible: "lea", motif: "Reproduire le problème de saisie", debuteLe: "2026-09-17T10:00:00.000Z", expireLe: "2026-09-17T11:00:00.000Z", termineLe: null, actif: true }],
  "GET /api/scolaire/corps-emploi": [{ code: "ENS", libelle: "Enseignant", categorie: "enseignant", seuilJoursConversion: null, actif: true }],
  // Le sas de transmission à la paie GRICS (ADR 0036) : une file à trois statuts, dont une bloquée, et son détail avec l'horaire.
  "GET /api/scolaire/paie/resume": { aValider: 2, bloquees: 1, rejetees: 1, transmises: 3 },
  "GET /api/scolaire/paie/valideurs": { userIds: ["usr-sa"] },
  "GET /api/auth/users": [{ id: "usr-sa", username: "paie", role: "rh" }, { id: "usr-dir", username: "direction", role: "director" }],
  "GET /api/scolaire/paie/transactions": [
    { id: "tp-1", type: "absence", sourceId: "sa-1", personId: "emp-2", nom: "Léa Bouchard", etablissementId: "etab-1", etablissementCode: "ECO-001", charge: { codePaie: "MAL", plages: [{ date: "2026-10-19", partie: "journee", periodes: [] }], heures: null, commentaire: null }, statut: "a_valider", anomalies: [], bloquee: false, tentatives: 0, valideeLe: null, transmiseLe: null, refExterne: null, rejetMotif: null, creeeLe: "2026-10-03T00:00:00.000Z" },
    { id: "tp-2", type: "paiement", sourceId: "b-1", personId: "p1", nom: "Nadir Haddad", etablissementId: "etab-1", etablissementCode: "ECO-001", charge: { codePaie: null, plages: [{ date: "2026-10-19", partie: "periodes", periodes: [1, 2] }], heures: 1.67, commentaire: null }, statut: "a_valider", anomalies: [{ code: "code_paie_manquant", gravite: "bloquante", message: "le corps d'emploi n'a pas de code de paie" }], bloquee: true, tentatives: 0, valideeLe: null, transmiseLe: null, refExterne: null, rejetMotif: null, creeeLe: "2026-10-03T00:00:00.000Z" },
    { id: "tp-3", type: "absence", sourceId: "sa-2", personId: "emp-2", nom: "Léa Bouchard", etablissementId: "etab-1", etablissementCode: "ECO-001", charge: { codePaie: "MAL", plages: [{ date: "2026-10-20", partie: "am", periodes: [] }], heures: null, commentaire: null }, statut: "rejetee", anomalies: [{ code: "hors_horaire", gravite: "avertissement", message: "la personne n'a pas d'horaire le 2026-10-20 : déclaration hors du début d'horaire" }], bloquee: false, tentatives: 1, valideeLe: "2026-10-04T00:00:00.000Z", transmiseLe: null, refExterne: null, rejetMotif: "code de paie inconnu", creeeLe: "2026-10-03T00:00:00.000Z" },
  ],
  "POST /api/scolaire/paie/transactions/valider-en-masse": { validees: ["tp-1"], refusees: [{ id: "tp-3", raison: "transaction bloquée" }] },
  "GET /api/scolaire/paie/transactions/tp-3": { id: "tp-3", type: "absence", sourceId: "sa-2", personId: "emp-2", nom: "Léa Bouchard", etablissementId: "etab-1", etablissementCode: "ECO-001", charge: { codePaie: "MAL", plages: [{ date: "2026-10-20", partie: "am", periodes: [] }], heures: null, commentaire: null }, statut: "rejetee", anomalies: [{ code: "hors_horaire", gravite: "avertissement", message: "la personne n'a pas d'horaire le 2026-10-20 : déclaration hors du début d'horaire" }], bloquee: false, tentatives: 1, valideeLe: "2026-10-04T00:00:00.000Z", transmiseLe: null, refExterne: null, rejetMotif: "code de paie inconnu", creeeLe: "2026-10-03T00:00:00.000Z",
    horaire: { "1": { parties: ["am", "pm"], periodes: [] }, "2": { parties: [], periodes: [] }, "3": { parties: [], periodes: [1, 2, 3] } },
    journal: [{ action: "creee", acteur: "systeme", acteurReel: null, details: null, le: "2026-10-03T00:00:00.000Z" }, { action: "validee", acteur: "paie", acteurReel: null, details: null, le: "2026-10-04T00:00:00.000Z" }, { action: "rejetee", acteur: "systeme", acteurReel: null, details: "code de paie inconnu", le: "2026-10-05T00:00:00.000Z" }] },
  "GET /api/scolaire/etablissements": [{ id: "etab-1", code: "ECO-001", nom: "École Saint-Charles", ordre: "primaire", actif: true, secteur: "nord", adresse: "12, rue des Sources", ville: "Montréal", telephone: "514 555-0142" }],
  // Lot E-5 : le catalogue d'attributs (un inactif), des suppléants qui en portent, une recherche qui en exige.
  "GET /api/scolaire/attributs-profil": [{ code: "voiture", libelle: "Possède une voiture", actif: true }, { code: "bilingue", libelle: "Bilingue", actif: true }, { code: "plongee", libelle: "Plongée", actif: false }],
  "GET /api/scolaire/suppleants": [
    { personId: "p1", nom: "Léa Bouchard", statutEngagement: "suppleance", actif: true, telephone: "418 555-0100", courriel: null, sourceExterne: "manuel", cleExterne: null, disponibilites: {}, qualificationLegale: "brevet", antecedentsVerifiesLe: "2026-08-01", attributs: ["voiture", "plongee"] },
    { personId: "p2", nom: "Nadir Haddad", statutEngagement: "contrat", actif: false, telephone: null, courriel: null, sourceExterne: "manuel", cleExterne: null, disponibilites: {}, qualificationLegale: "inconnue", antecedentsVerifiesLe: null, attributs: [] },
  ],
  // Les calendriers de tous les suppléants (grille 13) : un mois entier, les quatre états présents pour que la grille montre chaque lettre.
  "GET /api/scolaire/suppleants/calendriers": ["p1", "p2"].map((personId, i) => ({ personId, nom: i === 0 ? "Léa Bouchard" : "Nadir Haddad", mois: "2026-10",
    jours: Array.from({ length: 31 }, (_, j) => { const d = `2026-10-${String(j + 1).padStart(2, "0")}`; const etats = ["affecte", "disponible", "indisponible", "hors_disponibilites"];
      return { date: d, am: etats[(j + i) % 4], pm: etats[(j + i + 1) % 4], besoinId: (j + i) % 4 === 0 ? "b-1" : null }; }) })),
  "GET /api/scolaire/suppleants/disponibles": { criteres: { date: "2026-10-19", partie: "journee", corpsEmploiCode: "ENS", etablissementId: "etab-1", attributs: ["voiture"] },
    retenus: [{ personId: "p1", nom: "Léa Bouchard", telephone: "418 555-0100", priorite: 1, favori: "personnel" }],
    ecartes: [{ personId: "p3", nom: "Paul Proscrit", raison: "proscrit", libelleRaison: "proscrit à cet établissement" }, { personId: "p2", nom: "Nadir Haddad", raison: "sans_attribut", libelleRaison: "sans l'attribut de profil exigé : voiture" }],
    besoinsOuverts: [{ id: "b1", nature: "debut_annee", plages: [] }] },
  "GET /api/recherche": { total: 3, parType: { employe: 2, contrat: 1 }, types: [{ code: "employe", libelle: "Employés" }, { code: "contrat", libelle: "Contrats" }],
    resultats: [{ type: "employe", id: "e1", libelle: "Marie Tremblay", extrait: "marie.tremblay@exemple.qc.ca", cible: { page: "personnel", id: "e1" } }, { type: "employe", id: "e2", libelle: "Paul Tremblay", extrait: "Paul Tremblay", cible: { page: "personnel", id: "e2" } },
      { type: "contrat", id: "c1", libelle: "Contrat d'enseignement Tremblay (CT-1)", extrait: "Contrat d'enseignement Tremblay", cible: { page: "contracts", id: "c1" } }] },
  "GET /api/securite/incidents/vocabulaire": {
    natures: [{ code: "acces_non_autorise", libelle: "Accès non autorisé" }, { code: "perte", libelle: "Perte" }],
    renseignements: [{ code: "identite", libelle: "Identité" }, { code: "emploi", libelle: "Emploi" }],
    gravites: [{ code: "faible", libelle: "Faible" }, { code: "moderee", libelle: "Modérée" }, { code: "elevee", libelle: "Élevée — risque de préjudice sérieux" }],
  },
  "GET /api/securite/incidents": [{ id: "inc-1", tenantId: "tnt-demo", nomEtablissement: "Démo", survenuLe: "2026-09-01", decouvertLe: "2026-09-03", nature: "perte", description: "Une clé USB perdue dans un autobus.", renseignements: ["identite"],
    personnesTouchees: null, gravite: "elevee", mesures: "Clé révoquée, personnes avisées.", responsableId: null, responsableNom: null, notifieCai: false, notifieCaiLe: null, notifiePersonnes: true, notifiePersonnesLe: "2026-09-04",
    statut: "ouvert", closLe: null, consigneLe: "2026-09-03T10:00:00.000Z", attendue: { cai: true, personnes: false } }],
  "POST /api/scolaire/importations/apercu": ({ body }: { body: unknown }) => {
    const corr = (body as { correspondance?: Record<string, string | null> | null }).correspondance ?? { code: "code_ecole", nom: null, ordre: "niveau", statut: null };
    const manquants = ["code", "nom"].filter((r) => !corr[r]);
    return { type: "etablissements", entetesFichier: ["code_ecole", "nom_de_l_ecole", "niveau"], attendus: { requis: ["code", "nom"], facultatifs: ["ordre", "statut"] },
      correspondance: corr, erreursCorrespondance: manquants.length ? [`en-têtes obligatoires sans colonne : ${manquants.join(", ")}`] : [],
      lignes: 2, importes: manquants.length ? 0 : 1, ignores: 0, rejets: manquants.length ? [] : [{ ligne: 3, motif: "code et nom requis" }],
      apercu: manquants.length ? [] : [{ code: "ECO-1", nom: "École", ordre: "primaire" }] };
  },
  "GET /api/integrations/webhooks/evenements": [{ code: "absence.declaree", libelle: "Absence déclarée" }, { code: "employe.cree", libelle: "Employé créé" }],
  "GET /api/integrations/webhooks": [{ id: "wh-1", url: "https://paie.exemple.qc.ca/hooks", description: "Paie", evenements: ["absence.declaree"], actif: true, suspendu: false, echecsConsecutifs: 0, creeLe: "2026-09-01T00:00:00.000Z", envoisEnAttente: 0, dernierEnvoiLe: "2026-09-10T08:00:00.000Z" }],
};

const PAGES = [
  { nom: "Tableau de bord", nav: "nav-dashboard",
    onglets: ["tdb-onglet-couverture", "tdb-onglet-absences", "tdb-onglet-demandes", "tdb-onglet-anomalies"],
    // Lot E-4 : le bloc des annonces, avant et après « Marquer comme lue ».
    etats: [{ nom: "annonce marquée lue", agir: async (page) => {
      await page.getByTestId("annonce-lue-an-1").click();
      await page.getByTestId("annonce-an-1").waitFor({ state: "visible" });
    } },
    // Planche s01 : chaque sujet du jour, ouvert par sa tuile ou par son onglet, est un état à part.
    { nom: "onglet « Absences à traiter » ouvert par sa tuile", agir: async (page) => {
      await page.getByTestId("tdb-tuile-absences").click();
      await page.getByTestId("tdb-panneau-absences").waitFor({ state: "visible" });
    } },
    { nom: "onglet « Demandes en attente »", agir: async (page) => {
      await page.getByTestId("tdb-onglet-demandes").click();
      await page.getByTestId("tdb-panneau-demandes").waitFor({ state: "visible" });
    } },
    { nom: "onglet « Anomalies de présence »", agir: async (page) => {
      await page.getByTestId("tdb-onglet-anomalies").click();
      await page.getByTestId("tdb-panneau-anomalies").waitFor({ state: "visible" });
    } }] },
  { nom: "Constructeur de rapports", nav: "nav-rapports",
    // Les colonnes, les filtres et le tableau de résultat ne se rendent qu'une source choisie.
    etats: [{ nom: "rapport bâti", agir: async (page) => {
      await page.getByTestId("rap-colonnes").waitFor({ state: "visible" });
      await page.getByTestId("rap-filtre-ajouter").click();
      await page.getByTestId("rap-filtre-0").waitFor({ state: "visible" });
      await page.getByTestId("rap-executer").click();
      await page.getByTestId("rap-resume").waitFor({ state: "visible" });
    } },
    // P-RF-1 : un relevé d'effectifs — sa période, un taux et son dénominateur, puis un relevé enregistré chargé et produit.
    { nom: "relevé d'effectifs : période, taux, enregistré produit", agir: async (page) => {
      await page.getByTestId("rap-periode").waitFor({ state: "visible" });
      await page.getByTestId("rap-col-pourvues").check();
      await page.getByTestId("rap-agr-pourvues").selectOption("taux");
      await page.getByTestId("rap-den-pourvues").waitFor({ state: "visible" });
      await page.getByTestId("rap-enr-produire-0").click();
      await page.getByTestId("rap-enr-maj").waitFor({ state: "visible" });
      await page.getByTestId("rap-resume").waitFor({ state: "visible" });
    } }] },
  { nom: "Administration", nav: "nav-admin",
    onglets: ["admin-tab-comptes", "admin-tab-roles", "admin-tab-config", "admin-tab-audit", "admin-tab-confidentialite", "admin-tab-taches", "admin-tab-organisme"],
    // Le registre des incidents (ÉC-8) : le formulaire de consignation est un état à part entière.
    etats: [{ nom: "confidentialité › consigner", agir: async (page) => {
      await page.getByTestId("admin-tab-confidentialite").click();
      await page.getByTestId("incident-nouveau").click();
      await page.getByTestId("incident-formulaire").waitFor({ state: "visible" });
      await page.getByTestId("incident-gravite").selectOption("elevee");
    // Le formulaire est un dialogue : refermé avant de quitter la page, sinon son voile intercepte la navigation.
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("incident-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "organisme › déclarer un annuaire OpenID Connect (Keycloak)", agir: async (page) => {
      await page.getByTestId("admin-tab-organisme").click();
      await page.getByTestId("annuaire-ann-2").waitFor({ state: "visible" });
      await page.getByTestId("annuaire-nouveau-ouvrir").click();
      await page.getByTestId("annuaire-nouveau-fournisseur").selectOption("oidc");
      await page.getByTestId("annuaire-nouveau-emetteur").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("annuaire-nouveau-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  { nom: "Guide et aide", nav: "nav-help" },
  { nom: "Soutien", nav: "nav-soutien", onglets: ["soutien-tab-niveaux", "soutien-tab-billets"], etats: [
    { nom: "ouvrir un billet, une pièce retenue et une refusée", agir: async (page) => {
      await page.getByTestId("soutien-billet-ouvrir").click();
      await page.getByTestId("soutien-pieces-fichiers").setInputFiles([
        { name: "capture.png", mimeType: "image/png", buffer: Buffer.from("png") }, { name: "virus.exe", mimeType: "application/x-msdownload", buffer: Buffer.from("MZ") },
      ]);
      await page.getByTestId("soutien-pieces-refus").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("soutien-billet-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "billet résolu › satisfaction et réponse avec pièce", agir: async (page) => {
      await page.getByTestId("soutien-filtre-tous").click();
      await page.getByTestId("soutien-reponse-b-1").fill("Merci");
      await page.getByTestId("soutien-reponse-pieces-b-1").waitFor({ state: "visible" });
      await page.getByTestId("satisfaction-b-1-5").check();
    } },
  ] },
  { nom: "Mon compte", nav: "nav-account",
    // « Dossier » (account-tab-file) n'existe que pour un compte rattaché à un employé : le compte audité ne l'est pas.
    onglets: ["account-tab-identity", "account-tab-profile", "account-tab-password", "account-tab-mfa", "account-tab-passkeys", "account-tab-notifications"],
    etats: [
    // La matrice « Mes notifications » avec un refus affiché : un avis essentiel privé de ses deux canaux écrits.
    { nom: "mes notifications › refus nommé", agir: async (page) => {
      await page.getByTestId("account-tab-notifications").click();
      await page.getByTestId("account-avis-remplacement.annule-courriel").uncheck();
      await page.getByTestId("account-canaux-save").click();
      await page.getByTestId("account-canaux-refus").waitFor({ state: "visible" });
    } },
  ] },
  { nom: "Approbations", nav: "nav-approvals", onglets: ["appr-tab-demandes", "appr-tab-regles"],
    // La délégation s'ouvre en tiroir depuis l'en-tête : un état que rien d'autre n'ouvre.
    etats: [{ nom: "délégation (tiroir)", agir: async (page) => {
      await page.getByTestId("appr-delegation-ouvrir").click();
      await page.getByTestId("appr-delegation").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("appr-delegation-tiroir").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  { nom: "Accès", nav: "nav-access",
    onglets: ["access-tab-objets", "access-tab-octrois", "access-tab-qui"] },
  { nom: "Notifications", nav: "nav-notifications", etats: [
    // Notifier une personne : la liste de suggestions ouverte, une personne retenue (jeton), le refus à l'écran.
    { nom: "notifier une personne › suggestions, jeton, refus", agir: async (page) => {
      await page.getByTestId("notif-personne-cible").fill("Tre");
      await page.getByTestId("notif-personne-cible-option-emp-1").waitFor({ state: "visible" });
      await page.getByTestId("notif-personne-cible-option-emp-1").click();
      await page.getByTestId("notif-personne-cible-retenu-emp-1").waitFor({ state: "visible" });
      await page.getByTestId("notif-personne-canal-courriel").uncheck();
      await page.getByTestId("notif-personne-canal-texto").uncheck();
      await page.getByTestId("notif-personne-canal-poussee").uncheck();
      await page.getByTestId("notif-personne-sujet").fill("Réunion");
      await page.getByTestId("notif-personne-corps").fill("Jeudi 9 h");
      await page.getByTestId("notif-personne-envoyer").click();
      await page.getByTestId("notif-personne-refus").waitFor({ state: "visible" });
    } },
  ] },
  { nom: "Mon AGPH", nav: "nav-monagph" },
  { nom: "Mes absences", nav: "nav-mesabsences",
    // La déclaration vit désormais dans un tiroir, comme sur « Absences » : l'état se mesure
    // tiroir ouvert, et on attend son contenu — attendre le bouton qui l'ouvre ne prouvait rien.
    etats: [{ nom: "formulaire de déclaration ouvert", agir: async (page) => {
      await page.getByTestId("mab-declarer").click();
      await page.getByTestId("mab-formulaire").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Comblement", nav: "nav-uscomblement" },
  { nom: "Quarts et équipages", nav: "nav-usequipages" },
  { nom: "Disponibilités de la période", nav: "nav-usdisponibilites" },
  { nom: "Absences et comblement", nav: "nav-usabsences",
    // AD-07 : le réglage de l'offre des quarts libérés vit dans un tiroir ; il se mesure ouvert, formulaire chargé.
    etats: [{ nom: "offre des quarts libérés", agir: async (page) => {
      await page.getByTestId("pla-abs-offres-regler").click();
      await page.getByTestId("pla-abs-offres-enregistrer").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Demandes et échanges", nav: "nav-usechanges" },
  { nom: "Confection annuelle (AGPH)", nav: "nav-usagph" },
  { nom: "Pointage et présence (console)", nav: "nav-uspresence" },
  { nom: "Feuilles de temps (console)", nav: "nav-ustemps",
    etats: [{ nom: "fiche ouverte : ressource, tâches et champs de l'organisme", agir: async (page) => {
      await page.getByTestId("pla-tps-filtre").selectOption("");
      await page.getByTestId("pla-tps-ef-1-ouvrir").click();
      await page.getByTestId("pla-tps-fiche-segments").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }, { nom: "flux : approbateur « supérieur hiérarchique »", agir: async (page) => {
      await page.getByTestId("feuilles-approbations-ouvrir").click();
      await page.getByTestId("pla-tps-flux-type").selectOption("SUPERIEUR");
      await page.getByTestId("pla-tps-flux-superieur").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }, { nom: "supérieurs hiérarchiques : une personne choisie", agir: async (page) => {
      await page.getByTestId("feuilles-superieurs-ouvrir").click();
      await expect(page.getByTestId("pla-tps-sup-personne").locator("option", { hasText: "Julie Roy" })).toHaveCount(1);
      await page.getByTestId("pla-tps-sup-personne").selectOption("e-julie");
      await page.getByTestId("pla-tps-sup-table").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }, { nom: "champs de la feuille : définir une liste", agir: async (page) => {
      await page.getByTestId("feuilles-champs-ouvrir").click();
      await page.getByTestId("pla-tps-champ-type").selectOption("liste");
      await page.getByTestId("pla-tps-champ-valeurs").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Postes", nav: "nav-uspostes" },
  { nom: "Périodes", nav: "nav-usperiodes" },
  { nom: "Ressources", nav: "nav-usressources" },
  { nom: "Quotas", nav: "nav-usquotas" },
  { nom: "Référentiels", nav: "nav-usreferentiels" },
  { nom: "Codes d'absence", nav: "nav-uscodesabsence" },
  { nom: "Règles des effectifs", nav: "nav-usregles", onglets: ["rgl-vue-regles", "rgl-vue-simulateur", "rgl-vue-decisions"] },
  { nom: "Milieux physiques", nav: "nav-usmilieux" },
  { nom: "Périmètres", nav: "nav-usperimetres" },
  { nom: "Intégrations des effectifs", nav: "nav-usintegrations" },
  { nom: "Rétention des effectifs", nav: "nav-usretention" },
  { nom: "Journal des décisions", nav: "nav-usjournal" },
  { nom: "Messagerie du personnel", nav: "nav-usmessages",
    // La refonte (REFONTE-MESSAGERIE.md) : réactions, message privé et appel de groupe entrant — mesurés
    // dans leur état chargé, sans quoi la bulle, les boutons de réaction et le bandeau ne seraient jamais vus.
    stubs: {
      "GET /api/communication/appels-groupe/en-cours": [{ id: "ag-1", conversationId: "cv-1", lanceParId: "marc.dube", etat: "sonne",
        lanceLe: "2031-01-15T22:12:00.000Z", debuteLe: null, presents: 1, monEtat: "invite", entrant: true }],
      "GET /api/communication/conversations/cv-1/messages": [
        { id: "ms-1", conversationId: "cv-1", auteurId: "julie.roy", corps: "Le 412 sort à 23 h avec un seul paramédic.",
          envoyeLe: "2031-01-15T21:05:00.000Z", priveA: null, luParMoi: true, piecesJointes: [],
          reactions: [{ reaction: "pouce", mots: "bien reçu", nombre: 2, parMoi: true }, { reaction: "regarde", mots: "je regarde", nombre: 1, parMoi: false }] },
        { id: "ms-2", conversationId: "cv-1", auteurId: "marc.dube", corps: "Je peux prendre la nuit si on décale demain.",
          envoyeLe: "2031-01-16T06:10:00.000Z", priveA: "julie.roy", luParMoi: false, piecesJointes: [], reactions: [] },
        // Le mien (le compte simulé est « admin ») : modifié, avec son acheminement ; puis un message supprimé.
        { id: "ms-3", conversationId: "cv-1", auteurId: "admin", corps: "Je préviens la répartition pour le 412.",
          envoyeLe: "2031-01-16T06:20:00.000Z", editeLe: "2031-01-16T06:25:00.000Z", modifie: true, priveA: null, luParMoi: true,
          piecesJointes: [], reactions: [], statut: { destinataires: 2, recus: 2, lus: 1, libelle: "Lu par 1 sur 2" } },
        { id: "ms-4", conversationId: "cv-1", auteurId: "marc.dube", corps: null, envoyeLe: "2031-01-16T06:30:00.000Z", priveA: null,
          luParMoi: true, piecesJointes: [], reactions: [], supprime: true, supprimeLe: "2031-01-16T06:31:00.000Z", supprimePar: "marc.dube" }],
      "GET /api/communication/messages/ms-3/historique": [
        { version: 1, corps: "Je préviens la répartition.", ecritLe: "2031-01-16T06:20:00.000Z", remplaceLe: "2031-01-16T06:25:00.000Z", courante: false },
        { version: 2, corps: "Je préviens la répartition pour le 412.", ecritLe: "2031-01-16T06:25:00.000Z", remplaceLe: null, courante: true }],
    },
    etats: [
      { nom: "fil ouvert : jours, réactions, message privé, appel de groupe entrant", agir: async (page) => {
        await page.getByTestId("sq-msg-fil-cv-1").click();
        await page.getByTestId("sq-msg-reactions-ms-1").waitFor({ state: "visible" });
        await page.getByTestId("sq-appel-groupe-ag-1").waitFor({ state: "visible" });
      } },
      { nom: "mon message : historique déplié", agir: async (page) => {
        await page.getByTestId("sq-msg-historique-ms-3").click();
        await page.getByTestId("sq-msg-versions-ms-3").waitFor({ state: "visible" });
      } },
      { nom: "mon message : modification en cours", agir: async (page) => {
        await page.getByTestId("sq-msg-modifier-ms-3").click();
        await page.getByTestId("sq-msg-edition-ms-3").waitFor({ state: "visible" });
      }, apres: async (page) => { await page.getByTestId("sq-msg-edition-annuler-ms-3").click(); } },
      { nom: "mon message : suppression à confirmer", agir: async (page) => {
        await page.getByTestId("sq-msg-supprimer-ms-3").click();
        await page.getByTestId("sq-msg-supprimer-confirmer-ms-3").waitFor({ state: "visible" });
      }, apres: async (page) => { await page.getByTestId("sq-msg-supprimer-annuler-ms-3").click(); } },
      { nom: "réponse privée préparée", agir: async (page) => {
        await page.getByTestId("sq-msg-repondre-ms-2").click();
        await page.getByTestId("sq-msg-annuler-prive").waitFor({ state: "visible" });
      } },
      { nom: "filtre « Non lus »", agir: async (page) => {
        await page.getByTestId("sq-msg-filtre-non-lus").click();
        await page.getByTestId("sq-msg-filtre-non-lus").and(page.locator("[aria-pressed=true]")).waitFor({ state: "visible" });
      } },
      // Choisir une unité reprend son réglage dans le formulaire : les trois étapes se remplissent.
      { nom: "acheminement : unité choisie", agir: async (page) => { await page.getByTestId("us-ach-unite").selectOption("EST"); } },
    ] },
  { nom: "Appeler un collègue", nav: "nav-usappels" },
  { nom: "Listes de diffusion du personnel", nav: "nav-usdiffusion" },
  // Communications cliniques (ADR 0074) : chaque écran, et ses états — dialogues ouverts, détail, alerte dépliée.
  { nom: "Nouvelle alerte", nav: "nav-alnouvelle",
    etats: [{ nom: "modèle appliqué, personnes d'un groupe proposées, priorité critique annoncée", agir: async (page) => {
      await page.getByTestId("aln-modele").selectOption("mo-1");
      await page.getByTestId("aln-critique").waitFor({ state: "visible" });
      await page.getByTestId("aln-groupe-personnes").selectOption("EST");
      await page.getByTestId("aln-pers-julie").waitFor({ state: "visible" });
      await page.getByTestId("aln-l-l-garde").waitFor({ state: "visible" });
    } }] },
  { nom: "Suivi des alertes", nav: "nav-alsuivi",
    etats: [{ nom: "détail des destinataires", agir: async (page) => {
      await page.getByTestId("als-alerte-al-1-detail").click();
      await page.getByTestId("als-fiche").waitFor({ state: "visible" });
      await page.getByTestId("als-cascade-trace").waitFor({ state: "visible" });
    } },
    { nom: "note d'un destinataire en édition", agir: async (page) => {
      if (!(await page.getByTestId("als-fiche").isVisible())) await page.getByTestId("als-alerte-al-1-detail").click();
      await page.locator('[data-testid^="als-note-"][data-testid$="-modifier"]').first().click();
      await page.getByTestId("als-note-texte").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("als-note-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  { nom: "Mes alertes", nav: "nav-mesalertes",
    etats: [{ nom: "alerte dépliée, réponses offertes", agir: async (page) => {
      // L'audit a peut-être déjà déplié les replis de la page : cliquer le résumé replierait l'alerte.
      const repli = page.getByTestId("mal-alerte-al-1").locator("details");
      if (!(await repli.evaluate((d) => (d as HTMLDetailsElement).open))) await page.getByTestId("mal-alerte-al-1-resume").click();
      await page.getByTestId("mal-alerte-al-1-reponse-rp-1").waitFor({ state: "visible" });
    } }] },
  { nom: "Paramètres des alertes", nav: "nav-alparametres",
    etats: [{ nom: "dialogue « Nouvelle réponse »", agir: async (page) => {
      await page.getByTestId("alp-reponse-ouvrir").click();
      await page.getByTestId("alp-r-libelle").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } },
    { nom: "dialogue « Nouvelle politique de relance », deux étapes", agir: async (page) => {
      await page.getByTestId("alp-politique-ouvrir").click();
      await page.getByTestId("alp-pol-ajouter-etape").click();
      await page.getByTestId("alp-pol-e1-cible").selectOption("prochain_disponible");
      await page.getByTestId("alp-pol-e1-groupe").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("alp-politique-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "dialogue « Nouveau modèle »", agir: async (page) => {
      await page.getByTestId("alp-modele-ouvrir").click();
      await page.getByTestId("alp-m-titre").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } },
    { nom: "modifier un modèle : nouvelle version, périmètre coché", agir: async (page) => {
      await page.getByTestId("alp-modele-mo-1-modifier").click();
      await page.getByTestId("alp-m-g-gt-1").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("alp-modification-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "versions d'un modèle", agir: async (page) => {
      await page.getByTestId("alp-modele-mo-1-versions").click();
      await page.getByTestId("alp-versions-table").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("alp-versions-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "groupe transverse : membres et inscription", agir: async (page) => {
      await page.getByTestId("alp-gt-gt-1-gerer").click();
      await page.getByTestId("alp-gt-membres").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("alp-gt-gestion-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  // Paie (ADR 0076) : chaque écran, et ses dialogues ouverts.
  { nom: "Cycles de paie", nav: "nav-paiecycles",
    etats: [{ nom: "dialogue « Préparer un cycle »", agir: async (page) => {
      await page.getByTestId("pcy-preparer-ouvrir").click();
      await page.getByTestId("pcy-debut").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("pcy-preparer-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "cycle ouvert, gestes offerts", agir: async (page) => {
      await page.getByTestId("pcy-cycle-cy-1-voir").click();
      await page.getByTestId("pcy-traiter").waitFor({ state: "visible" });
    } },
    { nom: "détail d'un bulletin", agir: async (page) => {
      if (!(await page.getByTestId("pcy-ouvert").isVisible())) await page.getByTestId("pcy-cycle-cy-1-voir").click();
      await page.getByTestId("pcy-bulletin-rs-1-voir").click();
      await page.getByTestId("pcy-b-retenues").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("pcy-bulletin-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "confirmation du traitement", agir: async (page) => {
      if (!(await page.getByTestId("pcy-ouvert").isVisible())) await page.getByTestId("pcy-cycle-cy-1-voir").click();
      await page.getByTestId("pcy-traiter").click();
      await page.getByTestId("pcy-confirmer-traiter-confirm").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Dossiers de rémunération", nav: "nav-paiedossiers",
    etats: [{ nom: "dialogue du dossier, au salaire", agir: async (page) => {
      await page.getByTestId("pdo-rs-1-modifier").click();
      await page.getByTestId("pdo-mode").selectOption("salaire");
      await page.getByTestId("pdo-salaire").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("pdo-dossier-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  { nom: "Remises et relevés", nav: "nav-paiedeclarations" },
  { nom: "Mes bulletins de paie", nav: "nav-mesbulletins",
    etats: [{ nom: "détail d'un bulletin", agir: async (page) => {
      await page.getByTestId("mbu-b-1-voir").click();
      await page.getByTestId("mbu-b-employeur").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Paramètres de la paie", nav: "nav-paieparametres" },
  { nom: "Règles de paie variable", nav: "nav-paieregles",
    etats: [{ nom: "dialogue d'une prime", agir: async (page) => {
      await page.getByTestId("prg-rg-2-modifier").click();
      await page.getByTestId("prg-heures").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("prg-regle-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  { nom: "Validation des temps", nav: "nav-paietemps",
    etats: [{ nom: "dialogue de retour", agir: async (page) => {
      await page.getByTestId("pvt-ft-1-retourner").click();
      await page.getByTestId("pvt-motif").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("pvt-retour-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } }] },
  { nom: "Mon horaire", nav: "nav-monhoraire",
    // Le calendrier du mois est la vue par défaut ; la liste de la semaine est l'autre lecture. Le quart offert
    // (AD-07) s'affiche au-dessus, dans les deux lectures.
    etats: [{ nom: "liste de la semaine", agir: async (page) => {
      await page.getByTestId("mho-offre-accepter-2030-03-12").waitFor({ state: "visible" });
      await page.getByTestId("mho-vue-liste").click();
      await page.getByTestId("mho-table").or(page.getByTestId("mho-vide")).first().waitFor({ state: "visible" });
    } }] },
  { nom: "Mon équipe", nav: "nav-monequipe",
    etats: [{ nom: "changer le supérieur d'un membre", agir: async (page) => {
      await page.getByTestId("meq-e-luc-changer").click();
      await page.getByTestId("meq-nouveau").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); await page.getByTestId("meq-changement-modal").waitFor({ state: "hidden", timeout: 3000 }).catch(() => {}); } },
    { nom: "détacher un subordonné direct, à confirmer", agir: async (page) => {
      await page.getByTestId("meq-e-julie-detacher").click();
      await page.getByTestId("meq-detacher-confirm").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.getByTestId("meq-detacher-cancel").click(); } },
    { nom: "mettre fin à une suppléance, à confirmer", agir: async (page) => {
      await page.getByTestId("meq-deleg-adel-1-terminer").click();
      await page.getByTestId("meq-fin-confirm").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.getByTestId("meq-fin-cancel").click(); } }] },
  { nom: "Ma feuille de temps", nav: "nav-matemps",
    etats: [{ nom: "correction d'un segment et ses versions", agir: async (page) => {
      await page.getByTestId("mft-s-sg-1-modifier").click();
      await page.getByTestId("mft-e-historique").click();
      await page.getByTestId("mft-e-versions").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }, { nom: "ajout d'un segment, dialogue ouvert et rempli", agir: async (page) => {
      // L'ajout est un dialogue (8 novembre 2026). Son ouverture seule est déjà mesurée par le
      // relevé des `…-ouvrir` ; cet état le mesure **rempli**, ce que l'ouverture ne montre pas.
      if (!(await page.getByTestId("mft-ajouter-modal").isVisible().catch(() => false))) await page.getByTestId("mft-ajouter-ouvrir").click();
      await page.getByTestId("mft-a-date").fill("2031-01-14");
      await page.getByTestId("mft-a-description").fill("Formation donnée à l'équipe de nuit");
      await page.getByTestId("mft-a-champ-vehicule").selectOption("B-07");
      await page.getByTestId("mft-a-date").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Mon pointage", nav: "nav-monpointage",
    etats: [{ nom: "déclaration d'un quart non pointé", agir: async (page) => {
      // La déclaration est un dialogue (8 novembre 2026) : mesurée ici **remplie**, pause comprise —
      // c'est la pause qui fait paraître le champ « nature », absent du dialogue vide.
      if (!(await page.getByTestId("mpo-declarer-modal").isVisible().catch(() => false))) await page.getByTestId("mpo-declarer-ouvrir").click();
      await page.getByTestId("mpo-s-date").fill("2031-01-14");
      await page.getByTestId("mpo-s-pause-debut").fill("12:00");
      await page.getByTestId("mpo-s-pause-fin").fill("12:30");
      await page.getByTestId("mpo-s-nature").waitFor({ state: "visible" });
    }, apres: async (page) => { await page.keyboard.press("Escape"); } }] },
  { nom: "Mes échanges", nav: "nav-mesechanges",
    etats: [{ nom: "échange en préparation", agir: async (page) => {
      await page.getByTestId("mec-cede").selectOption("pl-1");
      await page.getByTestId("mec-voulu").selectOption("pl-9");
    } }] },
  { nom: "Mes disponibilités", nav: "nav-mesdisponibilites",
    etats: [{ nom: "plage ajoutée au dépôt", agir: async (page) => {
      await page.getByTestId("mdi-ajouter").click();
      await page.getByTestId("mdi-plage-2").waitFor({ state: "visible" });
    } }, { nom: "règles applicables déployées", agir: async (page) => {
      // Un `<details>` replié cache sa géométrie : mesurer l'écran fermé ne dit rien du panneau.
      await page.getByTestId("mdi-regles").evaluate((el) => { (el as HTMLDetailsElement).open = true; });
      await page.getByTestId("mdi-regles-deux-quarts-par-semaine").waitFor({ state: "visible" });
    } }] },
  { nom: "API", nav: "nav-apidocs" },
  { nom: "Intégrations", nav: "nav-integrations" },
] as Array<{ nom: string; nav: string; onglets?: string[]; etats?: Array<{ nom: string; agir: (page: Page) => Promise<void>; apres?: (page: Page) => Promise<void> }> }>;

const ECRANS = [
  { nom: "mobile", width: 390, height: 844 },
  { nom: "tablette", width: 768, height: 1024 },
  { nom: "portable", width: 1280, height: 800 },
  { nom: "grand écran", width: 1920, height: 1080 },
];

/** Relevé exécuté dans la page : cibles trop petites, éléments recouverts, débordement. */
const RELEVE = `() => {
  const SELECTEUR = 'a[href], button, input, select, textarea, [role="button"], [role="tab"], [tabindex]:not([tabindex="-1"])';
  const visible = (el) => {
    const s = getComputedStyle(el);
    if (s.display === "none" || s.visibility === "hidden" || Number(s.opacity) === 0) return false;
    // Le contenu d'un <details> replié n'est ni rendu ni atteignable, mais Chromium lui rapporte
    // une géométrie non nulle : sans cette garde, chaque champ d'un panneau replié serait déclaré
    // « recouvert » par le summary qui le remplace à l'écran.
    for (let d = el.closest("details:not([open])"); d; d = d.parentElement && d.parentElement.closest("details:not([open])")) {
      const summary = d.querySelector(":scope > summary");
      if (!summary || !summary.contains(el)) return false;
    }
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  };
  const dansLeViewport = (r) =>
    r.top >= 0 && r.left >= 0 && r.bottom <= innerHeight && r.right <= innerWidth;

  const petites = [];
  const recouverts = [];

  for (const el of document.querySelectorAll(SELECTEUR)) {
    if (!visible(el)) continue;
    const r = el.getBoundingClientRect();
    const decrire = () =>
      el.getAttribute("data-testid") ||
      (el.getAttribute("aria-label") || el.textContent || "").trim().slice(0, 40) ||
      el.tagName.toLowerCase();

    // 2.5.8 — exception « en ligne » : lien inséré dans un bloc de texte.
    const enLigne = el.tagName === "A" && el.closest("p, li, figcaption, small, .muted, caption");
    // Arrondi au pixel : une case de 24 px CSS peut se mesurer 23,99 px selon la mise en page (sous-pixel).
    if (!enLigne && (Math.round(r.width) < 24 || Math.round(r.height) < 24)) {
      petites.push({ quoi: decrire(), largeur: Math.round(r.width), hauteur: Math.round(r.height) });
    }

    // Recouvrement : au centre de l'élément, c'est bien lui (ou un descendant) qui répond.
    //
    // Les **superpositions flottantes** (position fixed : bouton d'assistant, bandeaux) sont
    // exclues. Un élément fixe passe au-dessus de tout ce qui défile sous lui : exiger qu'il ne
    // recouvre jamais rien reviendrait à interdire ce motif d'interface, ce qu'aucune norme ne
    // demande. Ce que WCAG 2.2 exige — que l'élément **focalisé** ne soit pas masqué (2.4.11) —
    // fait l'objet du test dédié plus bas, appliqué sans exception.
    if (dansLeViewport(r)) {
      const cible = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      // Un menu déroulant ouvert (\`role="menu"\`) est, comme un élément fixe, une couche voulue au-dessus
      // du contenu : il se referme d'un Échap, et ce qu'il recouvre redevient atteignable. Ce que
      // WCAG 2.2 exige de lui — que l'élément focalisé ne soit pas masqué (2.4.11) — reste mesuré plus bas.
      const flottant = cible && (() => {
        for (let n = cible; n; n = n.parentElement) {
          const pos = getComputedStyle(n).position;
          /*
            **Collant comme fixe.** Le critère ne connaissait que \`fixed\`, parce que la barre
            d'en-tête, bien que \`sticky\`, ne collait jamais : \`overflow-x: hidden\` sur
            \`html, body\` faisait défiler \`body\` et non la fenêtre. Depuis que le collage
            fonctionne (8 novembre 2026, \`overflow-x: clip\`), le contenu défile **sous** la barre —
            exactement le motif que ce critère tolère pour une couche fixe, et pour la même raison.
            La condition reste exacte : un élément collant qui ne colle pas est dans le flux et
            n'occupe que sa propre place ; il ne recouvre quelque chose **que** lorsqu'il colle. Le
            focus, lui, reste mesuré sans exception par l'essai 2.4.11.
          */
          if (pos === "fixed" || pos === "sticky" || n.getAttribute("role") === "menu") return true;
          /*
            **Un panneau de divulgation ouvert, s'il est positionné, est une couche voulue.**

            Le critère ne connaissait que \`fixed\`, par accident de ce qu'il avait rencontré
            d'abord. Or la **géométrie** est la même quand le panneau est \`absolute\` et ancré à son
            déclencheur : une surface au-dessus du contenu, qu'on referme d'un Échap ou d'un clic
            au-dehors. Le panneau d'avis en est un : relevé sous 480 px, il avait été déclaré
            \`fixed\` pour satisfaire ce critère — et le même recouvrement a reparu à 768 px et à
            1280 px, là où il reste \`absolute\`. Déclarer \`fixed\` partout l'a fait **intercepter le
            bouton « Menu »** : la correction était au mauvais endroit.

            La condition reste étroite : il faut un \`<details open>\` **et** un positionnement. Un
            \`<details>\` dans le flux repousse le contenu au lieu de le couvrir, et ne déclenche donc
            jamais ce relevé ; un élément positionné hors d'un panneau ouvert n'est pas excusé.
          */
          if ((pos === "absolute" || pos === "sticky") && n.parentElement instanceof HTMLDetailsElement
              && n.parentElement.open) return true;
        }
        return false;
      })();
      if (cible && !flottant && cible !== el && !el.contains(cible) && !cible.contains(el)) {
        recouverts.push({
          quoi: decrire(),
          par: cible.getAttribute("data-testid") || cible.className || cible.tagName.toLowerCase(),
        });
      }
    }
  }

  // Une cellule (ou un bouton qu'elle contient) qui dépasse sa rangée : c'est ce que produit un
  // \`<td>\` mis en \`display: flex\` — sorti du rendu tabulaire, il ne compte plus dans la hauteur
  // de la ligne, et ses boutons se posent sur la ligne suivante. Rien n'est « recouvert » au sens
  // du relevé précédent (la ligne du dessous n'est pas interactive), et pourtant l'écran est cassé.
  const debordants = [];
  for (const tr of document.querySelectorAll("tr")) {
    if (!visible(tr)) continue;
    const ligne = tr.getBoundingClientRect();
    for (const cellule of tr.querySelectorAll(":scope > td, :scope > th")) {
      const c = cellule.getBoundingClientRect();
      if (c.height === 0) continue;
      if (c.bottom > ligne.bottom + 1 || c.top < ligne.top - 1) {
        const nom = cellule.querySelector("[data-testid]")?.getAttribute("data-testid") || (cellule.textContent || "").trim().slice(0, 40) || "cellule";
        debordants.push({ quoi: nom, de: Math.round(c.bottom - ligne.bottom) });
      }
    }
  }

  /*
    **Aucun élément interactif collé à un autre** (demande du 14 novembre 2026 : « Ajouter l'accès »
    collé dans Périmètres, « Valider la définition » contre « Exécuter le rapport » dans Rapports).
    Deux commandes voisines doivent garder au moins 4 px entre elles : collées, elles se lisent
    comme une seule, et un doigt touche l'une pour l'autre. On mesure dans <main> seulement — le
    menu latéral empile ses entrées pleine largeur, voulu — et l'on excuse les onglets d'une même
    barre, qui se touchent par dessin.
  */
  const colles = [];
  const nom = (el) => el.getAttribute("data-testid") || (el.getAttribute("aria-label") || el.textContent || "").trim().slice(0, 30) || el.tagName.toLowerCase();
  // Un dialogue modal ouvert est une couche à part : on mesure **ses** commandes entre elles, pas
  // leur superposition à la page qu'il couvre (ce n'est pas un collage, c'est une couche).
  const modaux = [...document.querySelectorAll('[aria-modal="true"]')].filter(visible);
  const portee = modaux.length ? modaux[modaux.length - 1] : document.querySelector("main");
  const commandes = [...(portee ? portee.querySelectorAll(SELECTEUR) : [])]
    .filter((el) => visible(el) && !(el.tagName === "A" && el.closest("p, li, figcaption, small, .muted, caption, td")))
    .map((el) => ({ el, r: el.getBoundingClientRect() }));
  const ECART_MIN = 4;
  for (let i = 0; i < commandes.length; i++) {
    for (let j = i + 1; j < commandes.length; j++) {
      const a = commandes[i], b = commandes[j];
      if (a.el.contains(b.el) || b.el.contains(a.el)) continue;
      if (a.el.getAttribute("role") === "tab" && b.el.getAttribute("role") === "tab") continue;
      const recouvreV = Math.min(a.r.bottom, b.r.bottom) - Math.max(a.r.top, b.r.top);
      const recouvreH = Math.min(a.r.right, b.r.right) - Math.max(a.r.left, b.r.left);
      const ecartH = Math.max(b.r.left - a.r.right, a.r.left - b.r.right);
      const ecartV = Math.max(b.r.top - a.r.bottom, a.r.top - b.r.bottom);
      const coteACote = recouvreV > 4 && ecartH > -2 && ecartH < ECART_MIN;
      const dessusDessous = recouvreH > 4 && ecartV > -2 && ecartV < ECART_MIN;
      if (coteACote || dessusDessous) colles.push({ quoi: nom(a.el), avec: nom(b.el), ecart: Math.round(coteACote ? ecartH : ecartV) });
    }
  }

  return {
    petites,
    recouverts,
    debordants,
    colles,
    debordement: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  };
}`;

/**
 * Sous 768 px la barre latérale est un tiroir : hors écran au repos, ramené par le bouton
 * « Menu ». Avant de cliquer un lien de navigation, il faut donc que le tiroir soit ouvert.
 *
 * L'ouverture se vérifie sur la **géométrie du lien**, pas sur la classe `.sidebar.open` :
 * cette classe reste posée pendant les 200 ms de la transition de fermeture, si bien qu'un
 * test qui s'y fie peut conclure « ouvert » d'un tiroir en train de sortir de l'écran. Le lien
 * a alors des dimensions valides — Playwright le juge « stable » — tout en étant hors cadre,
 * et le clic échoue sur une cause invisible. La position, elle, ne ment pas.
 */
/** Sort le tiroir de navigation s'il existe à cette largeur et qu'il est rentré. */
async function sortirLeTiroir(page: import("@playwright/test").Page): Promise<void> {
  const bascule = page.getByTestId("menu-toggle");
  if (!(await bascule.isVisible().catch(() => false))) return;
  const tiroir = await page.getByTestId("sidebar").boundingBox();
  if (tiroir && tiroir.x >= 0) return;
  await bascule.click({ timeout: 2000 }).catch(() => {});
  await page.waitForTimeout(250);
}

async function amenerLeLienDansLEcran(page: import("@playwright/test").Page, navTestId: string): Promise<void> {
  const dansLEcran = async () => {
    const boite = await page.getByTestId(navTestId).boundingBox();
    return !!boite && boite.x >= 0 && boite.y >= 0;
  };
  if (await dansLEcran()) return;

  const bascule = page.getByTestId("menu-toggle");
  if (!(await bascule.isVisible().catch(() => false))) return;   // pas de tiroir à cette largeur

  for (let essai = 0; essai < 3; essai++) {
    /**
     * Un tiroir **déjà sorti** recouvre le bouton ☰ qui l'a ouvert : le recliquer, c'est cliquer
     * à travers lui, et Playwright refuse — après cinq secondes d'attente. Le lien est alors
     * simplement plus bas dans une liste de quarante entrées : il faut le faire défiler, pas
     * rouvrir ce qui est ouvert.
     *
     * L'état se lit sur la **position du tiroir**, jamais sur sa classe : `.sidebar.open`
     * subsiste pendant les 200 ms de la fermeture, pour la même raison qui vaut au lien.
     */
    const tiroir = await page.getByTestId("sidebar").boundingBox();
    if (tiroir && tiroir.x >= 0) {
      await page.getByTestId(navTestId).scrollIntoViewIfNeeded({ timeout: 2000 }).catch(() => {});
    } else {
      await bascule.click();
    }
    try {
      await page.waitForFunction(
        (id) => {
          const el = document.querySelector(`[data-testid="${id}"]`);
          if (!el) return false;
          const r = el.getBoundingClientRect();
          // La largeur seule ment pour un lien d'un <details> replié (voir `estAffiche`).
          return r.x >= 0 && r.y >= 0 && r.width > 0 && el.checkVisibility();
        },
        navTestId,
        { timeout: 2000 },
      );
      return;
    } catch { /* transition manquée : on retente l'ouverture */ }
  }
}

/**
 * **Écrans où aucun formulaire d'écriture ne vit hors d'un dialogue** — le module Urgences-santé.
 *
 * La règle date du 13 septembre 2026 (« un ajout ou une modification vit dans une modale ; un
 * formulaire de requête reste en ligne ») et n'était vérifiée nulle part : mesuré le 8 novembre,
 * **onze** formulaires d'écriture du module vivaient encore en ligne ou repliés dans un `<details>`
 * — dont la déclaration d'absence, en tiroir sur « Absences » mais en ligne sur « Mes absences ».
 *
 * Le contrôle se fait **à l'exécution**, pas sur le code source : un formulaire rendu par un
 * composant enfant à l'intérieur du dialogue de son parent échappait à toute analyse fichier par
 * fichier. Ici, la question est simple : un `<form>` visible alors qu'aucun dialogue n'est ouvert
 * est en ligne, par définition — et il doit le **déclarer** (`data-formulaire="requete"` pour un
 * filtre ou un contexte, `"en-place"` pour une grille éditée là où elle se lit).
 *
 * La liste est **nommée**, écran par écran : l'appliquer à toute l'application ferait rougir les
 * modules dont la conversion est une dette connue (TODO C6 du 13 septembre), ce qui est une autre
 * décision. Les trois écrans de communication en sont absents à dessein : ce sont les pages de
 * Santé Québec, gouvernées par son propre essai des verbes, et le champ de rédaction d'un fil de
 * messages appartient à la conversation qu'il prolonge — le mettre dans une modale cacherait le
 * fil auquel on répond.
 */
const SANS_FORMULAIRE_EN_LIGNE = new Set([
  "nav-monhoraire", "nav-mesdisponibilites", "nav-mesabsences", "nav-monagph", "nav-monpointage", "nav-matemps", "nav-mesechanges", "nav-monequipe",
  "nav-uscomblement", "nav-usequipages", "nav-usdisponibilites", "nav-usabsences", "nav-usechanges", "nav-usagph", "nav-uspresence",
  "nav-ustemps", "nav-uspostes", "nav-usperiodes", "nav-usressources", "nav-usquotas", "nav-usreferentiels", "nav-uscodesabsence", "nav-usregles",
  "nav-usmilieux", "nav-usperimetres", "nav-usintegrations", "nav-usretention", "nav-usjournal", "nav-alparametres",
  "nav-paiecycles", "nav-paiedossiers", "nav-mesbulletins",
]);
const NATURES_EN_LIGNE = ["requete", "en-place"];

test.describe("Audit WCAG 2.2 — géométrie et mise en page", () => {
  for (const ecran of ECRANS) {
    test(`${ecran.nom} (${ecran.width} px) : cibles ≥ 24 px, rien de recouvert, aucun débordement`, async ({ page, mock }) => {
      // Douze écrans rendus et mesurés, à cette largeur : le budget par défaut de 30 s tient tant
      // que la campagne est seule, et lâche dès qu'elle partage la machine avec les autres.
      // Soixante-dix écrans, leurs onglets et leurs états : une minute par largeur quand la machine
      // est seule. Un écran qui fait tomber l'arbre React se voit ici comme une avalanche
      // d'« injoignables », pas comme une lenteur : le budget reste serré à dessein.
      // Cent cinquante états par largeur depuis que chaque onglet rendu est audité (F.3) : dix
      // minutes, quatre largeurs en parallèle sur une machine qui fait autre chose en même temps.
      test.setTimeout(600_000);
      // Chaque action (un onglet à cliquer, un champ à remplir) garde un budget court, mais deux
      // fois celui des autres essais d'interface : l'audit tourne en parallèle d'eux et, sur une
      // machine chargée, un état déclaré « injoignable » à 5 s n'était qu'un rendu lent — la
      // refonte a doublé le nombre d'états mesurés (quatre-vingt-dix vues et onglets).
      page.setDefaultTimeout(10_000);
      await mock(MODULES_AUDIT);
      await page.setViewportSize({ width: ecran.width, height: ecran.height });
      // Le premier chargement a son propre budget : l'essai qui part le premier trouve un Vite froid, qui compile ses
      // dépendances à cette requête (plus de 10 s sur une machine chargée). Le budget court vaut pour les gestes.
      await page.goto("/", { timeout: 60_000 });
      // La navigation n'existe qu'une fois la session et les modules chargés. Sans cette attente,
      // le tout premier écran est déclaré injoignable pour la seule raison qu'on l'a demandé trop
      // tôt — et l'audit accuse l'application d'un défaut qui est le sien.
      await page.getByTestId("sidebar").waitFor({ state: "attached", timeout: 15_000 }).catch(() => {});

      const manquements: string[] = [];
      /**
       * Écrans que la navigation n'a pas offerts.
       *
       * Les sauter en silence viderait l'audit de son sens : ajouter une ligne à `PAGES` suffirait
       * à *déclarer* un écran couvert sans qu'il soit jamais rendu. Comme cet essai ouvre lui-même
       * tous les modules qu'il liste, un écran injoignable est un défaut, pas une configuration.
       */
      const injoignables: string[] = [];
      /**
       * Onglets **rendus** par l'écran mais absents de sa liste `onglets` : un onglet qu'on ne
       * clique pas est une vue jamais mesurée. L'écran les déclare lui-même (`role="tab"` avec
       * un `data-testid`) ; la liste de cet essai doit les nommer tous — un écran sauté en silence
       * est un audit décoratif (F.3 de la refonte).
       */
      const nonAudites: string[] = [];

      // `AUDIT_PAGES=<regex>` restreint la campagne aux écrans nommés — pour vérifier un écran qu'on
      // vient de toucher sans attendre les dix minutes ; la campagne complète reste la règle avant
      // de livrer, et un filtre posé se lit dans le journal, jamais en silence.
      const filtre = process.env.AUDIT_PAGES ? new RegExp(process.env.AUDIT_PAGES) : null;
      const audites = filtre ? PAGES.filter((p) => filtre.test(p.nom)) : PAGES;
      if (filtre) console.log(`[audit ${ecran.width}px] filtre AUDIT_PAGES=${process.env.AUDIT_PAGES} : ${audites.length}/${PAGES.length} écrans`);
      expect(audites.length, "le filtre AUDIT_PAGES ne retient aucun écran").toBeGreaterThan(0);

      for (const p of audites) {
        // Le dernier état mesuré d'un écran peut être un dialogue ouvert (un tiroir, une fiche) :
        // son voile intercepterait le clic sur l'écran suivant, déclaré alors « jamais rendu ».
        for (let n = 0; n < 3 && (await page.locator('[role="dialog"]').last().isVisible().catch(() => false)); n++) {
          await page.keyboard.press("Escape");
          await page.waitForTimeout(250);
        }
        const lien = page.getByTestId(p.nav);
        // La navigation est faite de sections **repliées** : sans les déplier, aucun lien n'est
        // visible et l'audit ne mesure jamais que l'écran d'accueil. C'est ce qu'il a fait
        // jusqu'ici — il passait à vide.
        // Les sections de la navigation sont **repliées** : sans les déplier, aucun lien n'est
        // visible et l'audit ne mesure jamais que l'écran d'accueil — ce qu'il a fait jusqu'ici.
        // Le dépliage est animé, et la section elle-même peut n'être pas encore montée : d'où
        // trois tentatives, chacune suivie d'une attente, plutôt qu'un clic et un espoir.
        // Sur téléphone et tablette, la navigation est un tiroir : une section repliée ne se
        // déplie qu'une fois le tiroir sorti, sinon le clic sur son titre vise hors écran, attend
        // deux secondes, échoue — trois fois par écran, et chaque écran suivant est déclaré
        // « jamais rendu » alors qu'il n'a simplement pas été atteint.
        for (let essai = 0; essai < 3 && !(await lien.isVisible().catch(() => false)); essai++) {
          await sortirLeTiroir(page);
          await deplierJusqua(page, p.nav);
          await lien.waitFor({ state: "visible", timeout: 3000 }).catch(() => {});
        }
        if (!(await lien.isVisible().catch(() => false))) { injoignables.push(p.nom); continue; }
        await amenerLeLienDansLEcran(page, p.nav);
        await lien.click();
        await page.waitForTimeout(350);

        const mesurer = async (etat: string) => {
          // `AUDIT_TRACE=1` date chaque relevé : c'est ce qui dit où le budget de temps part.
          if (process.env.AUDIT_TRACE) console.log(`[audit ${ecran.width}px] ${new Date().toISOString().slice(11, 19)} ${etat}`);
          /*
            **Un écran effondré n'est pas un écran mesuré.** La frontière d'erreur remplace l'écran
            par un message — parfaitement accessible, sans rien qui déborde ni se recouvre : la
            géométrie passait donc sur la page d'erreur. « Mon pointage » s'effondrait ainsi depuis
            le lot multi-site (`quart.parMilieu` absent de la doublure, et du backend Java), et
            l'audit ne relevait que l'état suivant, « injoignable », sans dire pourquoi.
          */
          const motif = await page.getByTestId("frontiere-erreur-motif").textContent({ timeout: 300 }).catch(() => null);
          if (motif !== null) { manquements.push(`${etat} — l'écran s'est effondré : ${motif}`); return; }
          // La chaîne doit être **invoquée** : évaluée telle quelle, elle ne produirait qu'une fonction.
          const r = await page.evaluate(`(${RELEVE})()`) as {
            petites: Array<{ quoi: string; largeur: number; hauteur: number }>;
            recouverts: Array<{ quoi: string; par: string }>;
            debordants: Array<{ quoi: string; de: number }>;
            colles: Array<{ quoi: string; avec: string; ecart: number }>;
            debordement: number;
          };
          if (r.debordement > 2) manquements.push(`${etat} — débordement horizontal de ${r.debordement} px`);
          for (const c of r.colles) manquements.push(`${etat} — « ${c.quoi} » collé à « ${c.avec} » (${c.ecart} px)`);
          for (const c of r.debordants) {
            manquements.push(`${etat} — la cellule « ${c.quoi} » déborde de sa rangée de ${c.de} px (cellule sortie du flux tabulaire)`);
          }
          for (const c of r.petites) {
            manquements.push(`${etat} — cible trop petite « ${c.quoi} » : ${c.largeur}×${c.hauteur} px`);
          }
          for (const c of r.recouverts) {
            manquements.push(`${etat} — « ${c.quoi} » est recouvert par « ${c.par} »`);
          }
        };
        await mesurer(p.nom);
        const rendus = await page.locator('[role="tab"][data-testid]:visible').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.testid!));
        for (const id of rendus) if (!(p.onglets ?? []).includes(id)) nonAudites.push(`${p.nom} › ${id}`);
        /**
         * Les formulaires d'ajout et de modification vivent dans des **dialogues** (13 sept. 2026) :
         * un écran mesuré sans ses dialogues est un écran à moitié mesuré. Chaque bouton
         * `…-ouvrir` visible est cliqué, le dialogue relevé (cibles, recouvrement, débordement —
         * le voile et la boîte compris), puis fermé par Échap : c'est aussi l'essai du piège de focus.
         */
        const dialogues = async (etat: string) => {
          // Un bouton `…-ouvrir` de type `submit` ouvre un cycle, une période — pas un dialogue : il envoie un formulaire.
          const ouvreurs = await page.locator('[data-testid$="-ouvrir"]:visible').evaluateAll((els) => els.filter((e) => (e as HTMLButtonElement).type !== "submit").map((e) => (e as HTMLElement).dataset.testid!));
          for (const id of ouvreurs) {
            const bouton = page.getByTestId(id);
            if (!(await bouton.isEnabled().catch(() => false))) continue;
            await bouton.click({ timeout: 2000 }).catch(() => {});
            const boite = page.locator('[role="dialog"]').last();
            if (!(await boite.isVisible().catch(() => false))) { injoignables.push(`${etat} › dialogue ${id}`); continue; }
            await page.waitForTimeout(120);
            await mesurer(`${etat} › dialogue ${id}`);
            await page.keyboard.press("Escape");
            await boite.waitFor({ state: "hidden", timeout: 2000 }).catch(() => injoignables.push(`${etat} › dialogue ${id} ne se ferme pas par Échap`));
          }
        };
        await dialogues(p.nom);
        // Les formulaires vivent souvent dans des panneaux repliés : un écran mesuré replié est
        // un écran à moitié mesuré. Chaque page est donc relevée une seconde fois, tout déployé.
        const deployer = async () => {
          const n = await page.evaluate(() => {
            // Le contenu de l'écran seulement : les menus déroulants de la barre du haut (« Mon compte »,
            // avis) sont aussi des <details>, et les ouvrir de force posait leur liste par-dessus les
            // boutons de l'en-tête — tous les dialogues suivants devenaient « injoignables ».
            const fermes = [...document.querySelectorAll("main details:not([open])")];
            fermes.forEach((d) => { (d as HTMLDetailsElement).open = true; });
            return fermes.length;
          });
          if (n > 0) await page.waitForTimeout(150);
          return n;
        };
        if (await deployer() > 0) await mesurer(`${p.nom} › panneaux déployés`);
        // **Aucun formulaire d'écriture hors dialogue** — vérifié panneaux déployés, sans quoi un
        // formulaire replié dans un `<details>` échapperait au contrôle qui le vise.
        if (SANS_FORMULAIRE_EN_LIGNE.has(p.nav)) {
          const enLigne = await page.evaluate((natures) => [...document.querySelectorAll("main form")]
            .filter((f) => !f.closest('[role="dialog"]') && !natures.includes(f.getAttribute("data-formulaire") ?? ""))
            .map((f) => f.getAttribute("aria-label") || f.querySelector('[type="submit"]')?.textContent?.trim() || "formulaire sans nom"), NATURES_EN_LIGNE);
          for (const nom of enLigne) {
            manquements.push(`${p.nom} — formulaire d'écriture hors dialogue « ${nom} » : un ajout ou une modification vit dans une modale ; `
              + `un filtre ou un contexte se déclare data-formulaire="requete", une grille éditée en place "en-place"`);
          }
        }

        for (const onglet of p.onglets ?? []) {
          const bouton = page.getByTestId(onglet);
          if (!(await bouton.isVisible().catch(() => false))) { injoignables.push(`${p.nom} › ${onglet}`); continue; }
          await bouton.click();
          await page.waitForTimeout(250);
          await mesurer(`${p.nom} › ${onglet}`);
          await dialogues(`${p.nom} › ${onglet}`);
          if (await deployer() > 0) { await mesurer(`${p.nom} › ${onglet} › panneaux déployés`); await dialogues(`${p.nom} › ${onglet} › panneaux déployés`); }
        }
        // Les états qu'un onglet n'ouvre pas (un assistant, une étape) : une action, puis la même mesure.
        // Une action qui échoue est un état injoignable, jamais un état sauté.
        // Les états d'un écran à doublures partent de sa vue par défaut, pour laquelle ils sont écrits :
        // on recharge son adresse — recliquer l'entrée du menu ne suffit pas, l'écran garde l'onglet
        // courant quand la navigation n'en nomme aucun (relevé le 12 nov. 2026).
        // Un écran qui porte ses propres doublures (une création qui doit rendre un objet) les reçoit
        // ici, avant d'être rechargé : ses lectures initiales en profitent aussi.
        const doublures = (p as { stubs?: Record<string, unknown> }).stubs;
        if (doublures) {
          await page.unroute("**/api/**");
          await mockApi(page, { ...MODULES_AUDIT, ...doublures } as Parameters<typeof mockApi>[1]);
        }
        // Seuls les écrans à doublures sont rechargés : les autres états (Remplacements scolaires,
        // Administration…) s'appuient sur la notion et l'onglet où la navigation les a laissés.
        if ((p.etats ?? []).length > 0 && doublures) {
          await page.goto(`/${p.nav.replace(/^nav-/, "")}`);
          if (p.onglets?.length) await page.getByTestId(p.onglets[0]).waitFor({ state: "visible" }).catch(() => {});
          else await page.waitForTimeout(500);
        }
        for (const etat of p.etats ?? []) {
          try { await etat.agir(page); } catch (e) {
            // Le **motif** voyage avec l'état : « injoignable » seul envoyait relancer l'audit en mode trace pour savoir quoi corriger.
            injoignables.push(`${p.nom} › ${etat.nom} (${(e as Error).message.split("\n")[0].slice(0, 160)})`); continue;
          }
          await page.waitForTimeout(250);
          await mesurer(`${p.nom} › ${etat.nom}`);
          // Un état qui recouvre la navigation (liste de résultats, modale) se referme avant l'écran suivant.
          if (etat.apres) await etat.apres(page).catch(() => {});
        }
        if (doublures) {
          await page.unroute("**/api/**");
          await mockApi(page, MODULES_AUDIT as Parameters<typeof mockApi>[1]);
        }
      }

      // **Une seule assertion pour les trois listes** : en trois, la première qui échouait masquait les
      // deux autres — un écran effondré se lisait alors « état injoignable », la conséquence à la
      // place de la cause.
      expect({ injoignables, nonAudites, manquements }, [...injoignables.map((x) => `injoignable : ${x}`), ...nonAudites.map((x) => `non audité : ${x}`), ...manquements].join("\n"))
        .toEqual({ injoignables: [], nonAudites: [], manquements: [] });
    });
  }

  // L'espace superadmin n'apparaît pas dans la navigation d'un administrateur d'établissement :
  // il a son propre parcours, avec son propre compte, et chacun de ses onglets est un état.
  const SUPERADMIN_AUDIT = {
    "GET /api/auth/me": { id: "usr-sa", username: "superadmin", role: "superadmin", tenantId: null, employeeId: null, active: true, mustChangePassword: false, securitySetupRequired: false, email: "superadmin@exemple.qc.ca" },
    "GET /api/superadmin/tenants": [],
    "GET /api/vitrine/content": { hero: {}, sections: [] },
    "GET /api/saas/maintenance": { active: false, message: "" },
    // La consommation d'avis : un organisme en dépassement et un dans son enveloppe, pour que la
    // mesure porte sur un tableau rempli — un tableau vide ne fait déborder ni recouvrir personne,
    // et l'audit passerait à vide sans rien prouver.
    "GET /api/saas/consommation": {
      mois: "2026-09",
      organismes: [
        { tenantId: "chene", nom: "Centre de services scolaire des Chênes", echecs: 0,
          textos: { utilises: 3100, messages: 1200, limite: 2000, illimite: false, depassement: 1100, echecs: 4 },
          courriels: { utilises: 912, messages: 912, limite: 0, illimite: true, depassement: 0, echecs: 1 } },
        { tenantId: "calme", nom: "École Calme", echecs: 0,
          textos: { utilises: 40, messages: 40, limite: 1000, illimite: false, depassement: 0, echecs: 0 },
          courriels: { utilises: 12, messages: 12, limite: 0, illimite: true, depassement: 0, echecs: 0 } },
      ],
      totaux: { textosSegments: 3140, textosMessages: 1240, courriels: 924, depassementTextos: 1100, depassementCourriels: 0 },
    },
    "GET /api/countries": [{ code: "CA", name: "Canada", currency: "CAD" }],
    "GET /api/varpay/requirements": { reference: "AIN20260525 §3.4.3", summary: { total: 1, implemented: 1, pivot: 0, infra: 0 }, categories: ["Générales"],
      requirements: [{ id: 1, category: "Générales", status: "implemented", text: "SaaS.", response: "Multi-tenant." }] },
    "GET /api/exploitation/sauvegardes": { dossier: "/srv/nexu/sauvegardes", retention: { incrementale: 96, complete: 30 }, intervalleMinutes: { incrementale: 15, complete: 1440 },
      sauvegardes: [{ fichier: "2026-09-12T08-00-00-000Z-complete.sqlite", type: "complete", faiteLe: "2026-09-12T08:00:00.000Z", octets: 2_400_000 }] },
    // L'onglet Soutien se mesure avec un billet complet (pièces, satisfaction), le dispositif d'avis en mode journal et un badge.
    "GET /api/soutien/tickets/resume": { ouverts: 2, nouveaux: 1, enRetard: 1, dernierOuvertLe: "2026-09-14T08:00:00.000Z" },
    "GET /api/soutien/notification": { destinataires: ["soutien@exemple.qc.ca"], courrielDuCompte: "superadmin@exemple.qc.ca", boitePartagee: "soutien@exemple.qc.ca", transport: { mode: "journal", hote: null, expediteur: "ne-pas-repondre@exemple.qc.ca" } },
    "GET /api/soutien/tickets/par-etablissement": [{ tenantId: "t-1", nom: "CSS des Bois", total: 1, ouverts: 1, enRetard: 1, tickets: [{
      id: "b-1", numero: 12, tenantId: "t-1", auteurId: "u-2", auteurNom: "Ana Roy", sujet: "Export vide", description: "Le classeur n'a aucune ligne.", niveau: "N2", categorie: "donnees", impact: "equipe",
      ecran: "/admin/scolaire", navigateur: "Chrome 130 · macOS", statut: "resolu", langue: "fr", ouvertLe: "2026-09-14T08:00:00.000Z", echeanceLe: "2026-09-14T10:00:00.000Z", prisEnChargeLe: null, resoluLe: "2026-09-14T11:00:00.000Z", fermeLe: null, traitePar: "usr-sa",
      satisfaction: 4, satisfactionCommentaire: "Réglé vite.", satisfactionLe: "2026-09-14T12:00:00.000Z", sla: { etat: "resolu_en_retard", ecartMinutes: -60 },
      messages: [{ id: "m-1", auteurId: "usr-sa", auteurNom: "superadmin", texte: "Correctif livré.", interne: false, le: "2026-09-14T10:30:00.000Z" }],
      pieces: [{ id: "p-1", messageId: null, nom: "capture.png", typeMime: "image/png", taille: 12_000, ajouteLe: "2026-09-14T08:00:00.000Z", ajouteParNom: "Ana Roy" }, { id: "p-2", messageId: "m-1", nom: "correctif.pdf", typeMime: "application/pdf", taille: 40_000, ajouteLe: "2026-09-14T10:30:00.000Z", ajouteParNom: "superadmin" }],
    }] }],
    "GET /api/soutien/maintenances": [],
    "GET /api/soutien/disponibilite": { mois: "2026-09", observeDepuis: "2026-09-01T00:00:00.000Z", observeJusqua: "2026-09-14T08:00:00.000Z", minutesObservees: 19_000, minutesEnMaintenance: 0, minutesIndisponibles: 10, pourcentage: 99.95, objectifPourcent: 99.5, objectifAtteint: true },
  };
  const ONGLETS_SUPERADMIN = ["tenants", "modules", "consommation", "compliance", "security", "soutien", "exploitation"];

  for (const ecran of ECRANS) {
    test(`${ecran.nom} (${ecran.width} px) : l'espace superadmin, onglet par onglet`, async ({ page, mock }) => {
      // Soixante-dix écrans, leurs onglets et leurs états : une minute par largeur quand la machine
      // est seule. Un écran qui fait tomber l'arbre React se voit ici comme une avalanche
      // d'« injoignables », pas comme une lenteur : le budget reste serré à dessein.
      test.setTimeout(180_000);
      await mock(SUPERADMIN_AUDIT);
      await page.setViewportSize({ width: ecran.width, height: ecran.height });
      await page.goto("/");
      const lien = page.getByTestId("nav-superadmin");
      await lien.waitFor({ state: "attached" });
      for (let essai = 0; essai < 3 && !(await estAffiche(page, "nav-superadmin")); essai++) {
        await deplierJusqua(page, "nav-superadmin");
        await lien.waitFor({ state: "visible", timeout: 3000 }).catch(() => {});
      }
      await amenerLeLienDansLEcran(page, "nav-superadmin");
      await lien.click();
      await expect(page.getByTestId("sa-tabs")).toBeVisible();

      // **La liste ci-dessus se périme en silence.** Un onglet ajouté au produit et oublié ici ne
      // serait jamais mesuré, et l'audit passerait au vert en ne regardant rien — le défaut le
      // moins visible d'un audit. On relit donc les onglets réellement rendus et on compare.
      const rendus = await page.$$eval('[data-testid^="sa-tab-"]',
        (els) => els.map((e) => (e.getAttribute("data-testid") ?? "").replace("sa-tab-", "")).filter(Boolean));
      const oublies = rendus.filter((o) => !ONGLETS_SUPERADMIN.includes(o));
      expect(oublies, `onglets superadmin rendus mais absents de la liste auditée : ${oublies.join(", ")}`).toEqual([]);

      const manquements: string[] = [];
      const injoignables: string[] = [];
      for (const onglet of ONGLETS_SUPERADMIN) {
        const bouton = page.getByTestId(`sa-tab-${onglet}`);
        if (!(await bouton.isVisible().catch(() => false))) { injoignables.push(onglet); continue; }
        await bouton.click();
        await page.waitForTimeout(250);
        await page.evaluate(() => { document.querySelectorAll("main details:not([open])").forEach((d) => { (d as HTMLDetailsElement).open = true; }); });
        await page.waitForTimeout(150);
        const r = await page.evaluate(`(${RELEVE})()`) as {
          petites: Array<{ quoi: string; largeur: number; hauteur: number }>;
          recouverts: Array<{ quoi: string; par: string }>;
          debordants: Array<{ quoi: string; de: number }>;
          debordement: number;
        };
        if (r.debordement > 2) manquements.push(`${onglet} — débordement horizontal de ${r.debordement} px`);
        for (const c of r.debordants) manquements.push(`${onglet} — la cellule « ${c.quoi} » déborde de sa rangée de ${c.de} px`);
        for (const c of r.petites) manquements.push(`${onglet} — cible trop petite « ${c.quoi} » : ${c.largeur}×${c.hauteur} px`);
        for (const c of r.recouverts) manquements.push(`${onglet} — « ${c.quoi} » est recouvert par « ${c.par} »`);
      }
      expect(injoignables, `onglets jamais rendus : ${injoignables.join(", ")}`).toEqual([]);
      expect(manquements, manquements.join("\n")).toEqual([]);
    });
  }

  test("2.1.1 — chaque barre d'onglets se parcourt au clavier (flèches, Début, Fin)", async ({ page, mock }) => {
    // Un tabindex tournant sans flèches interdit d'ouvrir un autre onglet au clavier : treize écrans
    // étaient dans ce cas. Un seul délégué les couvre tous ; cet essai le prouve sur le plus fourni.
    // Les modules de l'audit : depuis le 26 octobre, la page scolaire exige le code `scolaire`,
    // que les modules par défaut n'accordent pas — l'entrée visée n'existait plus au menu.
    await mock(MODULES_AUDIT);
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto("/");
    // L'administration : la barre d'onglets la plus fournie du produit (comptes, rôles, clés, journal…).
    await ouvrirNav(page, "nav-admin");
    await expect(page.getByTestId("admin-tab-comptes")).toHaveAttribute("aria-selected", "true");
    const onglets = page.locator('[role="tablist"][aria-label="Sections de l\'administration"] [role="tab"]');
    expect(await onglets.count()).toBeGreaterThanOrEqual(4);
    const premier = onglets.first();
    await premier.click();
    await expect(premier).toHaveAttribute("aria-selected", "true");
    await premier.focus();
    await page.keyboard.press("ArrowRight");
    await expect(onglets.nth(1)).toHaveAttribute("aria-selected", "true");
    await expect(onglets.nth(1)).toBeFocused();
    await page.keyboard.press("End");
    await expect(onglets.last()).toHaveAttribute("aria-selected", "true");
    await page.keyboard.press("ArrowRight");   // boucle : du dernier au premier
    await expect(premier).toHaveAttribute("aria-selected", "true");
    await page.keyboard.press("ArrowLeft");
    await expect(onglets.last()).toBeFocused();
    await page.keyboard.press("Home");
    await expect(premier).toBeFocused();
  });


  // Lot E-3 (ADR 0038) : le bandeau d'une session empruntée pousse la page — mesuré à chaque largeur, géométrie et axe-core.
  for (const ecran of ECRANS) {
    test(`session empruntée (${ecran.width} px) : le bandeau ne recouvre rien, contraste et cibles conformes`, async ({ page, mock }) => {
      await mock({ "GET /api/auth/me": { id: "u-2", username: "lea", role: "employee", tenantId: "tnt-demo", employeeId: "emp-2", active: true, mustChangePassword: false, securitySetupRequired: false,
        emprunt: { id: "e-1", emprunteur: { id: "usr-demo", username: "admin" }, expireLe: "2026-09-17T11:00:00.000Z" } } });
      await page.setViewportSize({ width: ecran.width, height: ecran.height });
      await page.goto("/");
      await page.getByTestId("emprunt-bandeau").waitFor({ state: "visible", timeout: 15_000 });
      await expect(page.getByTestId("emprunt-bandeau")).toContainText("admin, vous agissez comme lea (employee)");
      const r = await page.evaluate(`(${RELEVE})()`) as { petites: Array<{ quoi: string }>; recouverts: Array<{ quoi: string; par: string }>; debordement: number };
      expect(r.debordement, "débordement horizontal").toBeLessThanOrEqual(2);
      expect(r.recouverts.map((c) => `${c.quoi} par ${c.par}`), "éléments recouverts").toEqual([]);
      expect(r.petites.map((c) => c.quoi), "cibles trop petites").toEqual([]);
      await expectNoA11yViolations(page);
    });
  }

  test("2.4.11 — l'élément focalisé n'est jamais masqué par un élément collant", async ({ page, mock }) => {
    // Une barre latérale ou un en-tête collant peut recouvrir le contrôle qui vient de recevoir
    // le focus : l'utilisateur au clavier perd alors sa position sans rien voir.
    await mock();
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto("/");

    const masques = await page.evaluate(async () => {
      const resultats: string[] = [];
      for (let i = 0; i < 40; i++) {
        // eslint-disable-next-line no-await-in-loop
        await new Promise((r) => setTimeout(r, 10));
        const el = document.activeElement as HTMLElement | null;
        if (!el || el === document.body) break;
        const r = el.getBoundingClientRect();
        if (r.width && r.height && r.top >= 0 && r.bottom <= innerHeight) {
          const dessus = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
          if (dessus && dessus !== el && !el.contains(dessus) && !dessus.contains(el)) {
            resultats.push(el.getAttribute("data-testid") || el.tagName.toLowerCase());
          }
        }
        (document.querySelector("body") as HTMLElement).dispatchEvent(new Event("noop"));
        const focusables = [...document.querySelectorAll<HTMLElement>(
          'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])',
        )].filter((e) => e.offsetParent !== null);
        const suivant = focusables[focusables.indexOf(el) + 1];
        if (!suivant) break;
        suivant.focus();
      }
      return resultats;
    });

    expect(masques, `éléments focalisés masqués : ${masques.join(", ")}`).toEqual([]);
  });
});
