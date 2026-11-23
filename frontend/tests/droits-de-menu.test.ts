/**
 * **Le menu ne doit pas réinventer la règle du serveur.**
 *
 * Deux défauts signalés le 5 octobre 2026 venaient tous les deux de là : une personne dont la
 * fonction est « Directeur » ou le poste « gestionnaire » ne voyait pas le menu d'approbation,
 * parce que le menu lisait une liste de rôles écrite dans le code au lieu du droit que le
 * serveur calcule déjà. Ces essais figent le mécanisme qui a remplacé la recopie.
 */
import { describe, expect, it } from "vitest";
import { droitDeVoir } from "../src/droitsDeMenu";

const encadrants = ["admin", "manager", "director"];

describe("droit de voir une entrée de menu", () => {
  describe("quand le serveur calcule le droit (approbations)", () => {
    const approbations = { roles: encadrants, droitDelegue: true };

    it("l'accorde à qui le serveur l'accorde, même si son compte est resté « employé »", () => {
      // Le cas signalé : un contremaître, un chef d'équipe, un directeur adjoint. Il n'est le
      // gestionnaire de personne au sens du rôle, et c'est pourtant lui qu'on attend.
      expect(droitDeVoir({ ...approbations, droitServeur: true }, { role: "employee" })).toBe(true);
    });

    it("le refuse à qui le serveur le refuse, même si son rôle figure dans la liste", () => {
      // L'inverse compte autant : un menu ouvert dont chaque bouton répond 403 ne trompe
      // personne longtemps, mais il coûte un appel au soutien à chaque fois.
      expect(droitDeVoir({ ...approbations, droitServeur: false }, { role: "manager" })).toBe(false);
    });

    it("retombe sur les rôles tant que le profil n'a pas répondu", () => {
      // Au chargement, `peutApprouver` est `undefined`. Masquer serait se lire comme une
      // perte d'accès pour un écran qui allait apparaître.
      expect(droitDeVoir(approbations, { role: "manager" })).toBe(true);
      expect(droitDeVoir(approbations, { role: "employee" })).toBe(false);
    });
  });

  describe("quand une capacité gouverne l'entrée", () => {
    const facturation = { roles: ["admin"], capacitesRequises: ["invoice.manage"] };

    it("une seule capacité détenue suffit", () => {
      expect(droitDeVoir(
        { roles: ["admin"], capacitesRequises: ["accounting.manage", "accounting.govern"] },
        { role: "doyen", capabilities: ["accounting.govern"] },
      )).toBe(true);
    });

    it("ouvre l'entrée à un rôle défini par l'établissement, absent du code", () => {
      expect(droitDeVoir(facturation, { role: "coordonnateur", capabilities: ["invoice.manage"] })).toBe(true);
    });

    it("la refuse à qui ne la détient pas, quel que soit son rôle", () => {
      expect(droitDeVoir(facturation, { role: "admin", capabilities: ["absence.manage"] })).toBe(false);
    });

    it("retombe sur les rôles tant que les capacités sont inconnues", () => {
      expect(droitDeVoir(facturation, { role: "admin" })).toBe(true);
      expect(droitDeVoir(facturation, { role: "employee", capabilities: [] })).toBe(false);
    });
  });

  describe("quand l'entrée ne correspond à aucun droit nommé", () => {
    it("s'en tient aux rôles — le tableau de bord et l'aide restent ouverts", () => {
      const accueil = { roles: ["admin", "employee"] };
      expect(droitDeVoir(accueil, { role: "employee", capabilities: ["invoice.manage"] })).toBe(true);
      expect(droitDeVoir(accueil, { role: "accountant", capabilities: ["invoice.manage"] })).toBe(false);
    });

    it("traite une liste de capacités vide comme une absence de capacité", () => {
      expect(droitDeVoir({ roles: ["admin"], capacitesRequises: [] }, { role: "admin" })).toBe(true);
      expect(droitDeVoir({ roles: ["admin"], capacitesRequises: [] }, { role: "employee" })).toBe(false);
    });
  });

  describe("communications cliniques : suivre sans envoyer (ADR 0085, FX-AO-20260810 addenda 2 Q31)", () => {
    const nouvelle = { roles: ["admin", "manager", "director"], capacitesRequises: ["alertes.envoyer"] };
    const suivi = { roles: ["admin", "manager", "director"], capacitesRequises: ["alertes.suivre"] };

    it("un rôle propre « expéditeur » voit les deux pages, même absent de la liste des rôles", () => {
      const expediteur = { role: "expediteur", capabilities: ["alertes.envoyer", "alertes.suivre"] };
      expect(droitDeVoir(nouvelle, expediteur)).toBe(true);
      expect(droitDeVoir(suivi, expediteur)).toBe(true);
    });

    it("un gestionnaire privé d'envoi garde le suivi, sans la page d'envoi", () => {
      const gestionnaire = { role: "manager", capabilities: ["alertes.suivre", "horaire.rapporter"] };
      expect(droitDeVoir(suivi, gestionnaire)).toBe(true);
      expect(droitDeVoir(nouvelle, gestionnaire)).toBe(false);
    });

    it("un destinataire ne voit ni l'une ni l'autre", () => {
      const destinataire = { role: "employee", capabilities: ["profile.read"] };
      expect(droitDeVoir(suivi, destinataire)).toBe(false);
      expect(droitDeVoir(nouvelle, destinataire)).toBe(false);
    });
  });
});
