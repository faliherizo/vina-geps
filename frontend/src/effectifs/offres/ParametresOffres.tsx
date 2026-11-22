/**
 * **L'offre des quarts libérés** — le réglage de l'organisme (AD-07 ; art. 15.10, 16.02, 16.03 ; ADR 0089).
 *
 * Deux manières d'offrir le quart qu'une absence libère : à tous les admissibles en même temps (le défaut), ou une
 * personne à la fois, par ancienneté et à tour de rôle, avec un délai de réponse. Chaque champ dit l'article qui en
 * fixe la valeur conventionnelle ; le serveur refuse une valeur hors borne en la nommant, et l'écran le répète.
 */
import { useEffect, useState, type FormEvent } from "react";
import { objet } from "../../formeRecue";
import { Message } from "../../composants";
import { useApiEffectifs } from "../api";
import type { ModeOffre, ParametresOffres as Parametres } from "./types";

type CleNombre = "delaiReponseMinutes" | "seuilUrgenceMinutes" | "fenetreRepartitionJours";

export const CHAMPS_OFFRE: ReadonlyArray<readonly [CleNombre, string, number, number]> = [
  ["delaiReponseMinutes", "Délai de réponse, en minutes (art. 15.10 et 16.02 : 10)", 1, 1440],
  ["seuilUrgenceMinutes", "Seuil d'urgence, en minutes avant le quart (art. 16.02 : 120) — offert à tous, sans délai", 0, 1440],
  ["fenetreRepartitionJours", "Fenêtre de répartition, en jours (art. 16.04 : 91) — heures comptées pour le tour de rôle", 1, 366],
];

export const MODES: Record<ModeOffre, string> = {
  SIMULTANEE: "À tous les admissibles en même temps",
  CASCADE: "Une personne à la fois, par ancienneté et à tour de rôle",
};

export default function ParametresOffres() {
  const { lire, envoyer, erreur } = useApiEffectifs();
  const [p, setP] = useState<Parametres | null>(null);
  const [info, setInfo] = useState("");
  useEffect(() => { void lire<Parametres>("/api/effectifs/offres/parametres").then((x) => setP(objet<Parametres>(x))); }, [lire]);

  const enregistrer = async (e: FormEvent) => {
    e.preventDefault();
    if (!p) return;
    setInfo("");
    const r = objet<Parametres>(await envoyer<Parametres>("/api/effectifs/offres/parametres", p, "PUT"));
    if (!r) return;
    setP(r);
    setInfo(r.mode === "CASCADE" ? `Les quarts libérés seront offerts une personne à la fois, ${r.delaiReponseMinutes} minutes pour répondre.`
      : "Les quarts libérés seront offerts à tous les admissibles en même temps.");
  };

  if (!p) return erreur ? <Message variante="erreur" testId="pla-abs-offres-erreur">{erreur}</Message> : <p role="status">Lecture du réglage…</p>;
  return (
    <form onSubmit={(e) => void enregistrer(e)} aria-label="Offre des quarts libérés" data-testid="pla-abs-offres">
      <fieldset>
        <legend>Comment offrir un quart libéré par une absence</legend>
        {(Object.keys(MODES) as ModeOffre[]).map((m) => (
          <p key={m}>
            <input type="radio" id={`pla-abs-offres-${m}`} name="pla-abs-offres-mode" value={m} checked={p.mode === m} data-testid={`pla-abs-offres-${m}`}
              onChange={() => setP({ ...p, mode: m })} />
            <label htmlFor={`pla-abs-offres-${m}`}>{MODES[m]}</label>
          </p>
        ))}
      </fieldset>
      {CHAMPS_OFFRE.map(([cle, libelle, min, max]) => (
        <p key={cle}>
          <label htmlFor={`pla-abs-offres-${cle}`}>{libelle}</label>
          <input id={`pla-abs-offres-${cle}`} type="number" required min={min} max={max} step={1} data-testid={`pla-abs-offres-${cle}`} value={p[cle]}
            onChange={(e) => setP({ ...p, [cle]: Number(e.target.value) })} />
        </p>
      ))}
      {erreur && <Message variante="erreur" testId="pla-abs-offres-erreur">{erreur}</Message>}
      {info && <p role="status" data-testid="pla-abs-offres-info">{info}</p>}
      <p className="eff-actions"><button type="submit" className="primary" data-testid="pla-abs-offres-enregistrer">Enregistrer le réglage</button></p>
    </form>
  );
}
