/**
 * **FX-AO-20260810, critères 18 et 39 — joués contre le Java ET les bacs à sable** (`bac-a-sable/`, ADR 0086).
 *
 * L'alerte critique part du vrai serveur, traverse le service de poussée au contrat d'Expo, devient une charge APNs ou
 * FCM et arrive sur des téléphones simulés (iPhone avec ou sans le droit d'Apple, Android avec ou sans le canal
 * `critique`). Les exports PDF sont soumis à veraPDF officiel. Toute la connaissance est dans
 * `donnees/fx-ao/bac-a-sable.json`.
 *
 * Lancement : `bac-a-sable/campagne.sh` (démarre les bacs à sable, le Java branché dessus, puis cet essai).
 * Hors de cette campagne, l'essai se déclare **sauté avec son motif** — jamais vert à vide ; dans la campagne
 * (`E2E_BAC_A_SABLE=1`), un bac à sable absent le fait échouer.
 */
import { test, expect } from "@playwright/test";
import { declarerComportements, type JeuComportements, type Verificateur } from "./helpers/comportements";
import { modulesDeLOrganisme } from "./helpers/urgences-sante";
import jeu from "../../donnees/fx-ao/bac-a-sable.json";

test.use({ storageState: { cookies: [], origins: [] } });

const POUSSEE = process.env.E2E_BAC_POUSSEE_URL;
const VERAPDF = process.env.E2E_BAC_VERAPDF_URL;
const CONTROLE = process.env.E2E_BAC_JETON_CONTROLE;
const EXIGE = process.env.E2E_BAC_A_SABLE === "1";

interface RegleVeraPdf { clause: string; testNumber: number; status: string; description: string }
interface ResultatVeraPdf { compliant: boolean; details?: { failedRules?: number; ruleSummaries?: RegleVeraPdf[] } }

/** veraPDF REST : `POST /api/validate/2b`, rapport JSON ; une règle en échec est nommée par sa clause ISO 19005-2. */
function veraPdf(url: string): Verificateur {
  return async (octets) => {
    const formulaire = new FormData();
    formulaire.append("file", new Blob([new Uint8Array(octets)], { type: "application/pdf" }), "export.pdf");
    let r: Response;
    try {
      r = await fetch(`${url}/api/validate/2b`, { method: "POST", headers: { Accept: "application/json" }, body: formulaire });
    } catch (e) {
      return [`veraPDF injoignable sur ${url} (${(e as Error & { cause?: Error }).cause?.message ?? (e as Error).message}) : docker compose -f bac-a-sable/docker-compose.yml up -d verapdf`];
    }
    if (!r.ok) return [`veraPDF a répondu ${r.status} : ${(await r.text()).slice(0, 200)}`];
    const rapport = await r.json() as { report?: { jobs?: { validationResult?: ResultatVeraPdf[] }[] } };
    const v = rapport.report?.jobs?.[0]?.validationResult?.[0];
    if (!v) return [`rapport veraPDF illisible : ${JSON.stringify(rapport).slice(0, 200)}`];
    if (v.compliant) return [];
    const regles = (v.details?.ruleSummaries ?? []).filter((x) => x.status === "failed");
    return [`non conforme PDF/A-2b (${v.details?.failedRules ?? regles.length} règle(s)) :`,
      ...regles.map((x) => `  ISO 19005-2 ${x.clause} #${x.testNumber} — ${x.description}`)];
  };
}

if (POUSSEE && VERAPDF) {
  declarerComportements("FX-AO-20260810 — bacs à sable : alerte critique jusqu'au téléphone, PDF/A par veraPDF",
    jeu as unknown as JeuComportements, {
      prefixe: "FXAO-BAC", modules: [...modulesDeLOrganisme, "alertes"],
      services: { poussee: { url: POUSSEE, entetes: CONTROLE ? { Authorization: `Bearer ${CONTROLE}` } : undefined } },
      verificateurs: { "pdfa-2b": veraPdf(VERAPDF) },
    });
} else {
  test("FX-AO-20260810 — bacs à sable (critères 18, 39)", () => {
    const motif = "bacs à sable absents : E2E_BAC_POUSSEE_URL et E2E_BAC_VERAPDF_URL non réglés (bac-a-sable/campagne.sh)";
    expect(EXIGE, motif).toBe(false);
    test.skip(true, motif);
  });
}
