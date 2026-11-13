/**
 * **Par où l'on joint les membres d'une unité** — le réglage de l'acheminement des avis, unité par
 * unité.
 *
 * Une unité sans réglage suit le défaut de l'établissement (le courriel, en sonnerie) ; le tableau le
 * dit ligne par ligne plutôt que de laisser croire qu'un défaut est un choix. Les canaux et les modes
 * sont lus du serveur : l'écran n'en écrit aucun, de sorte qu'un canal ajouté au serveur apparaît ici
 * sans retouche. Lire est ouvert à tous ; régler demande la gestion des effectifs, et un refus
 * s'affiche avec son motif.
 */
import { useEffect, useState } from "react";
import type { ContexteSection, Section } from "../santequebec/noyau";

interface Entree { code: string; libelle: string }
interface CanalResolu { canal: string; mode: string; ordrePriorite: number; parDefaut: boolean; libelle: string }
interface UniteReglee {
  uniteId: string; nom: string; parDefaut: boolean; canaux: CanalResolu[];
  modifieLe: string | null; modifiePar: string | null;
}
interface Reglages { unites: UniteReglee[]; canaux: Entree[]; modes: Entree[] }

/** Trois étapes suffisent à l'écran : au-delà, une chaîne d'escalade ne se relit plus d'un coup d'œil. */
const ETAPES_A_L_ECRAN = 3;

function reglages(donnees: unknown): Reglages {
  const d = (donnees ?? {}) as Partial<Reglages>;
  return { unites: Array.isArray(d.unites) ? d.unites : [], canaux: Array.isArray(d.canaux) ? d.canaux : [], modes: Array.isArray(d.modes) ? d.modes : [] };
}

function Regler({ ctx }: { ctx: ContexteSection }) {
  const { unites, canaux, modes } = reglages(ctx.donnees);
  const [uniteId, setUniteId] = useState("");
  const [etapes, setEtapes] = useState(() => Array.from({ length: ETAPES_A_L_ECRAN }, () => ({ canal: "", mode: "sonnerie" })));

  // Choisir une unité reprend son réglage : on corrige une chaîne, on ne la retape pas.
  useEffect(() => {
    const u = unites.find((x) => x.uniteId === uniteId);
    if (!u) return;
    setEtapes(Array.from({ length: ETAPES_A_L_ECRAN }, (_, i) => ({
      canal: u.parDefaut ? (i === 0 ? u.canaux[0]?.canal ?? "" : "") : u.canaux[i]?.canal ?? "",
      mode: u.canaux[i]?.mode ?? "sonnerie",
    })));
  }, [uniteId]); // eslint-disable-line react-hooks/exhaustive-deps

  const changer = (i: number, champ: "canal" | "mode", valeur: string) =>
    setEtapes((avant) => avant.map((e, j) => (j === i ? { ...e, [champ]: valeur } : e)));

  return (
    <form data-testid="us-acheminement-form" onSubmit={async (e) => {
      e.preventDefault();
      const choisies = etapes.filter((x) => x.canal);
      const fait = await ctx.appeler(`/api/communication/acheminement/unites/${encodeURIComponent(uniteId)}`, {
        method: "PUT", body: JSON.stringify({ etapes: choisies }),
      });
      if (fait) {
        const nom = unites.find((u) => u.uniteId === uniteId)?.nom ?? uniteId;
        ctx.annoncer(`Acheminement de l'unité « ${nom} » enregistré : ${choisies.length} canal(aux), dans l'ordre choisi.`);
        ctx.recharger();
      }
    }}>
      <div className="sq-rangee">
        <div className="sq-champ">
          <label htmlFor="us-ach-unite">Unité <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></label>
          <select id="us-ach-unite" required data-testid="us-ach-unite" value={uniteId} onChange={(e) => setUniteId(e.target.value)}>
            <option value="">— Choisir une unité —</option>
            {unites.map((u) => <option key={u.uniteId} value={u.uniteId}>{u.nom}</option>)}
          </select>
        </div>
      </div>
      {etapes.map((etape, i) => (
        <fieldset key={i} className="sq-rangee" data-testid={`us-ach-etape-${i + 1}`}>
          <legend>{i === 0 ? "Premier canal essayé" : `Si personne ne répond : canal ${i + 1}`}</legend>
          <div className="sq-champ">
            <label htmlFor={`us-ach-canal-${i + 1}`}>Canal</label>
            <select id={`us-ach-canal-${i + 1}`} data-testid={`us-ach-canal-${i + 1}`} value={etape.canal} required={i === 0}
                    onChange={(e) => changer(i, "canal", e.target.value)}>
              <option value="">{i === 0 ? "— Choisir un canal —" : "— Aucun —"}</option>
              {canaux.map((c) => <option key={c.code} value={c.code}>{c.libelle}</option>)}
            </select>
          </div>
          <div className="sq-champ">
            <label htmlFor={`us-ach-mode-${i + 1}`}>Mode</label>
            <select id={`us-ach-mode-${i + 1}`} data-testid={`us-ach-mode-${i + 1}`} value={etape.mode} disabled={!etape.canal}
                    onChange={(e) => changer(i, "mode", e.target.value)}>
              {modes.map((m) => <option key={m.code} value={m.code}>{m.libelle}</option>)}
            </select>
          </div>
        </fieldset>
      ))}
      <div className="sq-actions">
        <button type="submit" data-testid="us-ach-enregistrer" disabled={!uniteId}>Enregistrer l'acheminement de l'unité</button>
      </div>
    </form>
  );
}

/** La section des pages de messagerie d'Urgences-santé : le tableau des unités, puis le formulaire. */
export const SECTION_ACHEMINEMENT: Section = {
  titre: "Acheminement des avis par unité",
  aide: "Par quels canaux, dans quel ordre et sur quel mode on joint les membres d'une unité quand "
    + "un message leur arrive. Une unité sans réglage suit le défaut de l'établissement : le courriel, en sonnerie.",
  source: () => "/api/communication/acheminement/unites",
  tableau: {
    legende: "Acheminement des avis, unité par unité",
    vide: "Aucune unité en vigueur : l'acheminement se règle dès qu'une unité est ouverte au référentiel.",
    lignes: (d) => reglages(d).unites,
    colonnes: [
      { entete: "Unité", valeur: (u: UniteReglee) => u.nom, identifiante: true },
      { entete: "Canaux, dans l'ordre", valeur: (u: UniteReglee) => u.canaux.map((c) => `${c.ordrePriorite}. ${c.libelle}`).join(" → ") },
      { entete: "Origine", valeur: (u: UniteReglee) => (u.parDefaut ? "Défaut de l'établissement" : `Réglée par ${u.modifiePar ?? "—"}`) },
    ],
    actions: [{
      libelle: (u: UniteReglee) => `Rétablir l'acheminement par défaut pour l'unité ${u.nom}`,
      testId: (u: UniteReglee) => `us-ach-defaut-${u.uniteId}`,
      masquer: (u: UniteReglee) => u.parDefaut,
      executer: async (u: UniteReglee, c) => {
        const fait = await c.appeler(`/api/communication/acheminement/unites/${encodeURIComponent(u.uniteId)}`, { method: "DELETE" });
        if (fait) { c.annoncer(`L'unité « ${u.nom} » suit de nouveau l'acheminement par défaut.`); c.recharger(); }
      },
    }],
  },
  rendu: (ctx) => <Regler ctx={ctx} />,
};
