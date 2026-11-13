import { describe, expect, it } from "vitest";
import { classeStatut, decrireQuota, decrireResume, libelleFournisseur, moisLisible, refusCanaux, refusNumero, refusTexte } from "../src/reglesTextos";

describe("règles des textos à l'écran", () => {
  it("accepte l'E.164 et le nord-américain, refuse le reste en le disant", () => {
    expect(refusNumero("+15145551234")).toBeNull();
    expect(refusNumero("(514) 555-1234")).toBeNull();
    expect(refusNumero("1 514 555 1234")).toBeNull();
    expect(refusNumero("+33 6 12 34 56 78")).toBeNull();
    expect(refusNumero("")).toMatch(/requis/);
    expect(refusNumero("+0123456789")).toMatch(/invalide/);
    expect(refusNumero("12345")).toMatch(/invalide/);
  });

  it("borne le texte", () => {
    expect(refusTexte("  ")).toMatch(/requis/);
    expect(refusTexte("a".repeat(480))).toBeNull();
    expect(refusTexte("a".repeat(481))).toMatch(/481 caractères sur 480/);
  });

  it("dit le quota en une phrase, selon qu'il est illimité, atteint ou non", () => {
    expect(decrireQuota({ mois: "2026-09", limite: 0, utilises: 3, restants: null, illimite: true })).toBe("3 texto(s) envoyé(s) en septembre 2026 — sans limite.");
    expect(decrireQuota({ mois: "2026-09", limite: 10, utilises: 4, restants: 6, illimite: false })).toBe("4 sur 10 texto(s) en septembre 2026 — 6 restant(s).");
    expect(decrireQuota({ mois: "2026-01", limite: 10, utilises: 10, restants: 0, illimite: false })).toMatch(/^Quota mensuel atteint : 10 sur 10 en janvier 2026/);
    expect(moisLisible("n'importe quoi")).toBe("n'importe quoi");
    expect(moisLisible("2026-13")).toBe("2026-13");
  });

  it("nomme les fournisseurs et les statuts sans se fier à la seule couleur", () => {
    expect(libelleFournisseur("twilio")).toBe("Twilio");
    expect(libelleFournisseur("inconnu")).toBe("inconnu");
    expect(libelleFournisseur(null)).toBe("—");
    expect(classeStatut("livre")).toContain("badge-active");
    expect(classeStatut("echoue")).toContain("badge-terminated");
    expect(classeStatut("en_attente")).toContain("badge-rule");
  });

  it("résume le journal en une ligne", () => {
    expect(decrireResume({ total: 0, parStatut: { en_attente: 0, envoye: 0, livre: 0, echoue: 0 }, dernierLe: null })).toBe("Aucun texto au journal.");
    expect(decrireResume({ total: 12, parStatut: { en_attente: 0, envoye: 2, livre: 9, echoue: 1 }, dernierLe: "x" })).toBe("12 textos au journal — 2 envoyés, 9 livrés, 1 échoué.");
    expect(decrireResume({ total: 1, parStatut: { en_attente: 1, envoye: 0, livre: 0, echoue: 0 }, dernierLe: "x" })).toBe("1 texto au journal — 1 en attente.");
  });

  it("garde au moins un canal écrit ouvert", () => {
    expect(refusCanaux({ courriel: false, texto: false, poussee: true })).toMatch(/au moins un canal écrit/i);
    expect(refusCanaux({ courriel: false, texto: true, poussee: false })).toBeNull();
  });
});
