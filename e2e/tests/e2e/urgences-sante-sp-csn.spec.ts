/**
 * **AD-08 — la convention SP-CSN, paramétrée par les gestes de l'application, puis jugée sur des valeurs.**
 *
 * L'organisme neuf reçoit son paramétrage **par le script de démonstration lui-même** (`scripts/semer-sp-csn.cjs
 * --organisme`) : un double de ses appels finirait par diverger, et c'est la démonstration livrée qui serait la
 * version non éprouvée. Les scénarios (`donnees/urgences-sante/comportements-sp-csn.json`) jouent ensuite chaque
 * règle sur un cas qui la déclenche et un cas qui ne la déclenche pas.
 *
 * Deux choses se vérifient sans serveur, depuis les seuls fichiers : chaque règle du paramétrage a ses deux cas, et
 * chaque message cite l'article qui la fonde.
 */
import { execFileSync } from "node:child_process";
import path from "node:path";
import { test, expect } from "@playwright/test";
import { declarerComportements, type Etape, type JeuComportements, type Scenario } from "./helpers/comportements";
import { modulesDeLOrganisme } from "./helpers/urgences-sante";
import { TENANT_ADMIN_PASS, TENANT_ADMIN_USER } from "./helpers/organisme";
import jeuBrut from "../../donnees/urgences-sante/comportements-sp-csn.json";
import parametrage from "../../donnees/urgences-sante/convention-sp-csn.json";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;
const SCRIPT = path.join(__dirname, "..", "..", "..", "scripts", "semer-sp-csn.cjs");
const jeu = jeuBrut as unknown as JeuComportements;

const echapper = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

test.use({ storageState: { cookies: [], origins: [] } });

test.describe("AD-08 — le paramétrage SP-CSN est complet et sourcé (fichiers seuls)", () => {
  test("chaque règle du paramétrage est jouée sur un cas qui la déclenche et un cas qui ne la déclenche pas", () => {
    const preuves = jeu.scenarios.flatMap((s) => s.prouve ?? []);
    const manques = parametrage.regles.flatMap((r) => (["declenche", "ne-declenche"] as const)
      .filter((cas) => !preuves.some((p) => p.regle === r.cle && p.cas === cas)).map((cas) => `${r.cle} : ${cas}`));
    expect(manques, "règles sans leurs deux cas").toEqual([]);
    const inconnues = preuves.filter((p) => p.regle && !parametrage.regles.some((r) => r.cle === p.regle)).map((p) => p.regle);
    expect(inconnues, "un scénario cite une règle absente du paramétrage").toEqual([]);
  });

  test("chaque message de règle cite son article, et chaque valeur posée en nomme un", () => {
    const sansArticle = parametrage.regles.filter((r) => !r.article.split(", ").every((a) => r.message.includes(a))).map((r) => r.cle);
    expect(sansArticle, "message qui ne cite pas l'article de sa règle").toEqual([]);
    const valeurs = [parametrage.convention, ...parametrage.statutsEmploi, ...parametrage.naturesQuart, ...parametrage.typesHeure,
      ...parametrage.codesAbsence.codes, parametrage.seuilsHeuresSupplementaires, ...parametrage.ecarts];
    expect(valeurs.filter((v) => !v.article?.trim()).length, "valeur sans article").toBe(0);
  });
});

/** Le premier scénario se déduit du paramétrage : chaque règle est en vigueur, sous sa clé et avec son message. */
const reglesEnVigueur: Scenario = {
  id: "SP-00", titre: "Chaque règle du paramétrage est en vigueur, à portée de la convention (ou du statut), avec son message",
  exigences: ["E-GN-12"],
  etapes: parametrage.regles.map((r): Etape => {
    const portee = r.portee ?? { type: "CONVENTION", code: parametrage.convention.code };
    const entete = `"cle":"${r.cle}","porteeType":"${portee.type}","porteeCode":"${portee.code}","versionCourante":{`;
    // Le message se cherche dans la version courante de **cette** règle, sans déborder sur la suivante.
    return {
      pourquoi: `art. ${r.article}`, methode: "GET", chemin: `/api/effectifs/regles?contexte=${r.contexte}`,
      attendu: { statut: 200, texte: `${echapper(entete)}(?:(?!"versionCourante")[\\s\\S])*?${echapper(`"action":"${r.action}","message":"${r.message}"`)}` },
    };
  }),
};

declarerComportements("AD-08 — la convention SP-CSN, jugée par l'API", { ...jeu, scenarios: [reglesEnVigueur, ...jeu.scenarios] }, {
  prefixe: "US-SPCSN",
  modules: modulesDeLOrganisme,
  parametrer: (tenantId) => {
    // Le mot de passe passe par l'environnement : en argument, il s'imprimerait dans le rapport d'un échec.
    const sortie = execFileSync("node", [SCRIPT, "--api", API, "--organisme", tenantId, "--admin", TENANT_ADMIN_USER],
      { encoding: "utf-8", timeout: 120_000, env: { ...process.env, SEMIS_MDP: TENANT_ADMIN_PASS } });
    expect(sortie, "le semis doit dire combien d'appels il a posés").toMatch(/^PARAMETRAGE=\d+$/m);
  },
});
