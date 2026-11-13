/**
 * **Sauvegardes de l'instance** (grille scolaire, exigence 47) — réservé au superadmin.
 *
 * L'écran lit la politique (dossier, rétention, cadences) et la liste des copies, déclenche une
 * copie à la demande et (ré)amorce les deux cadences. Il ne fait rien lui-même : chaque bouton
 * appelle `/api/exploitation/sauvegardes`, qui était jusqu'ici un endpoint sans écran.
 */
import { useCallback, useEffect, useState } from "react";
import { Message, Icone } from "./composants";
import TbodyPagine from "./TbodyPagine";

type TypeSauvegarde = "incrementale" | "complete";

interface Sauvegarde { fichier: string; type: TypeSauvegarde; faiteLe: string; octets: number }
interface TestRestauration { fichier: string; testeLe: string; dureeMs: number; integrite: string; tables: number; lignes: number; ageMinutes: number; reussi: boolean; erreur: string | null }

interface EtatSauvegardes {
  dossier: string;
  retention: Record<TypeSauvegarde, number>;
  intervalleMinutes: Record<TypeSauvegarde, number>;
  sauvegardes: Sauvegarde[];
}

const LIBELLE: Record<TypeSauvegarde, string> = { incrementale: "Incrémentale", complete: "Complète" };

/** Une taille lisible : on n'affiche pas 734 003 200 octets à un exploitant. */
export function formaterOctets(octets: number): string {
  if (octets < 1024) return `${octets} o`;
  if (octets < 1024 * 1024) return `${(octets / 1024).toFixed(1)} Ko`;
  return `${(octets / (1024 * 1024)).toFixed(1)} Mo`;
}

/** Une cadence en minutes se lit mieux en heures au-delà d'une heure ronde. */
export function formaterCadence(minutes: number): string {
  return minutes % 60 === 0 && minutes >= 60 ? `toutes les ${minutes / 60} h` : `toutes les ${minutes} min`;
}

export default function SauvegardesPanel({ apiFetch }: { apiFetch: (path: string, init?: RequestInit) => Promise<Response> }) {
  const [etat, setEtat] = useState<EtatSauvegardes | null>(null);
  const [erreur, setErreur] = useState("");
  const [message, setMessage] = useState("");
  const [occupe, setOccupe] = useState(false);
  const [restaurations, setRestaurations] = useState<TestRestauration[]>([]);

  const charger = useCallback(async () => {
    setErreur("");
    const r = await apiFetch("/api/exploitation/sauvegardes");
    if (!r.ok) { setErreur("Impossible de lire l'état des sauvegardes."); return; }
    setEtat(await r.json());
    const t = await apiFetch("/api/exploitation/sauvegardes/restaurations");
    const liste = t.ok ? await t.json().catch(() => []) : [];
    setRestaurations(Array.isArray(liste) ? liste : []);
  }, [apiFetch]);

  useEffect(() => { charger().catch(() => setErreur("Impossible de lire l'état des sauvegardes.")); }, [charger]);

  async function sauvegarder(type: TypeSauvegarde) {
    setOccupe(true); setErreur(""); setMessage("");
    try {
      const r = await apiFetch("/api/exploitation/sauvegardes", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ type }) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) { setErreur((j.errors || ["La sauvegarde a échoué."]).join(", ")); return; }
      setMessage(`Sauvegarde ${LIBELLE[type].toLowerCase()} faite : ${j.fichier}`);
      await charger();
    } finally { setOccupe(false); }
  }

  async function planifier() {
    setOccupe(true); setErreur(""); setMessage("");
    try {
      const r = await apiFetch("/api/exploitation/sauvegardes/planifier", { method: "POST" });
      if (!r.ok) { setErreur("La planification a échoué."); return; }
      const j = await r.json();
      setMessage(`Cadences amorcées : incrémentale ${formaterCadence(j.intervalleMinutes.incrementale)}, complète ${formaterCadence(j.intervalleMinutes.complete)}.`);
    } finally { setOccupe(false); }
  }

  /** L'exercice de reprise (grille 35, 48) : restaurer à côté, vérifier, mesurer — la preuve que le plan est testé. */
  async function testerRestauration() {
    setOccupe(true); setErreur(""); setMessage("");
    try {
      const r = await apiFetch("/api/exploitation/sauvegardes/tester-restauration", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({}) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok) { setErreur((j.errors || ["L'exercice de restauration a échoué."]).join(", ")); return; }
      setMessage(j.reussi ? `Restauration vérifiée : ${j.tables} tables, ${j.lignes} lignes, intégrité « ${j.integrite} », en ${j.dureeMs} ms (copie vieille de ${j.ageMinutes} min).` : `Restauration échouée : ${j.erreur}`);
      await charger();
    } finally { setOccupe(false); }
  }

  return (
    <section data-testid="sa-sauvegardes" aria-labelledby="sa-sauvegardes-titre">
      <h3 id="sa-sauvegardes-titre"><Icone nom="dossier" /> Sauvegardes de l'instance</h3>
      <p className="aide">
        Copie cohérente de la base à chaud (API de sauvegarde en ligne SQLite). Une incrémentale
        toutes les 15 minutes, une complète chaque jour ; la rétention est comptée en copies.
      </p>
      {erreur && <Message variante="erreur" testId="sa-sauvegardes-erreur">{erreur}</Message>}
      {message && <Message variante="succes" testId="sa-sauvegardes-message">{message}</Message>}
      {etat && (
        <dl className="account-meta" data-testid="sa-sauvegardes-politique" style={{ marginBottom: 12 }}>
          <div><dt>Dossier</dt><dd><code>{etat.dossier}</code></dd></div>
          <div><dt>Incrémentale</dt><dd>{formaterCadence(etat.intervalleMinutes.incrementale)}, {etat.retention.incrementale} copies conservées</dd></div>
          <div><dt>Complète</dt><dd>{formaterCadence(etat.intervalleMinutes.complete)}, {etat.retention.complete} copies conservées</dd></div>
        </dl>
      )}
      <div className="row-form" style={{ gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
        <button type="button" className="primary" data-testid="sa-sauvegarde-complete" disabled={occupe} onClick={() => sauvegarder("complete")}>Sauvegarde complète maintenant</button>
        <button type="button" className="secondary" data-testid="sa-sauvegarde-incrementale" disabled={occupe} onClick={() => sauvegarder("incrementale")}>Sauvegarde incrémentale maintenant</button>
        <button type="button" className="secondary" data-testid="sa-sauvegardes-planifier" disabled={occupe} onClick={planifier}>Amorcer les cadences automatiques</button>
        <button type="button" className="secondary" data-testid="sa-sauvegardes-tester" disabled={occupe || (etat?.sauvegardes ?? []).length === 0} onClick={testerRestauration}>Tester la restauration (exercice de reprise)</button>
      </div>
      <div className="table-wrap">
        <table data-testid="sa-sauvegardes-liste">
          <caption className="sr-only">Copies de sauvegarde, de la plus récente à la plus ancienne</caption>
          <thead><tr><th scope="col">Faite le</th><th scope="col">Type</th><th scope="col">Fichier</th><th scope="col">Taille</th></tr></thead>
          <tbody>
            {(etat?.sauvegardes ?? []).length === 0 && <tr><td colSpan={4} className="muted" data-testid="sa-sauvegardes-aucune">Aucune copie pour l'instant.</td></tr>}
            {(etat?.sauvegardes ?? []).map((s) => (
              <tr key={s.fichier} data-testid={`sa-sauvegarde-${s.fichier}`}>
                <td>{new Date(s.faiteLe).toLocaleString("fr-CA")}</td>
                <td>{LIBELLE[s.type]}</td>
                <td><code>{s.fichier}</code></td>
                <td>{formaterOctets(s.octets)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h4>Exercices de reprise</h4>
      <p className="aide">Chaque exercice restaure la dernière copie à côté de la base vivante, vérifie son intégrité, compte ses tables et mesure la durée — c'est le test du plan de reprise (NIST SP 800-34), pas une promesse.</p>
      <div className="table-wrap">
        <table data-testid="sa-restaurations-liste">
          <caption className="sr-only">Exercices de restauration, du plus récent au plus ancien</caption>
          <thead><tr><th scope="col">Testé le</th><th scope="col">Copie</th><th scope="col">Résultat</th><th scope="col">Durée</th><th scope="col">Âge de la copie</th></tr></thead>
          <TbodyPagine items={restaurations.slice(0, 10)} vide={<tr><td colSpan={5} className="muted" data-testid="sa-restaurations-aucune">Aucun exercice pour l'instant.</td></tr>} rendre={(t) => (
              <tr key={`${t.testeLe}-${t.fichier}`}>
                <td>{new Date(t.testeLe).toLocaleString("fr-CA")}</td><td><code>{t.fichier}</code></td>
                <td>{t.reussi ? `réussi (${t.tables} tables, ${t.lignes} lignes)` : `échoué — ${t.erreur}`}</td><td>{t.dureeMs} ms</td><td>{t.ageMinutes} min</td>
              </tr>
            )} />
        </table>
      </div>
    </section>
  );
}
