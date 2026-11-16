/**
 * **Paramètres de la paie** (ADR 0076) — ce qui varie d'un organisme à l'autre (taux du FSS selon sa masse salariale,
 * taux de la CNESST de son unité, majoration des heures supplémentaires), et, en lecture, le barème de l'année.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { EnTeteEcran, Message } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { argent, pourcent, type Reglages } from "./types";

interface BaremeLu {
  annee: number;
  rrq: { tauxBase: number; tauxSupplementaire: number; exemption: number; mga: number; plafondSupplementaire: number; tauxRrq2: number };
  ae: { tauxEmploye: number; maximumAssurable: number };
  rqap: { tauxEmploye: number; tauxEmployeur: number; maximumAssurable: number };
  federal: { montantPersonnel: number };
  quebec: { montantPersonnel: number };
}

/** Un taux s'écrit en pour cent à l'écran, et voyage en fraction. */
const enPourcent = (v: number) => String(Math.round(v * 100000) / 1000);
const enFraction = (v: string) => String(Number(v) / 100);

export default function ParametresPaie() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [reglages, setReglages] = useState<Reglages | null>(null);
  const [bareme, setBareme] = useState<BaremeLu | null>(null);
  const [fss, setFss] = useState("");
  const [cnesst, setCnesst] = useState("");
  const [majoration, setMajoration] = useState("");
  const [validation, setValidation] = useState(false);
  const charger = useCallback(async () => {
    const r = await lire<Reglages>("/api/paie/reglages");
    if (r) { setReglages(r); setFss(enPourcent(r.tauxFss)); setCnesst(enPourcent(r.tauxCnesst)); setMajoration(enPourcent(r.majorationSupplementaire)); setValidation(r.validationTempsExigee === true); }
    setBareme(await lire<BaremeLu>("/api/paie/bareme"));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);
  const soumettre = async (e: FormEvent) => {
    e.preventDefault();
    const r = await envoyer<Reglages>("/api/paie/reglages", { tauxFss: enFraction(fss), tauxCnesst: enFraction(cnesst), majorationSupplementaire: enFraction(majoration),
      validationTempsExigee: validation }, "PUT");
    if (r) { setReglages(r); setSucces("Réglages de la paie enregistrés : ils s'appliquent aux prochains calculs."); }
  };

  return (
    <div data-testid="ppa-ecran">
      <EnTeteEcran titre="Paramètres de la paie" testId="ppa-titre"
        description="Les taux propres à l'organisme. Le barème fiscal de l'année est publié avec le produit : il se lit ici et ne se modifie pas." />
      {erreur && <Message variante="erreur" testId="ppa-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="ppa-succes">{succes}</Message>}
      <section className="card" aria-labelledby="ppa-reglages-h">
        <h2 id="ppa-reglages-h">Réglages de l'organisme{reglages ? ` (${reglages.annee})` : ""}</h2>
        <form onSubmit={(e) => void soumettre(e)} aria-label="Réglages de la paie">
          <p><label htmlFor="ppa-fss">Taux du Fonds des services de santé (%)</label>
            <input id="ppa-fss" data-testid="ppa-fss" type="number" min="0" max="20" step="0.001" required value={fss} onChange={(e) => setFss(e.target.value)} /></p>
          <p><label htmlFor="ppa-cnesst">Taux de la CNESST (%, selon l'avis de cotisation)</label>
            <input id="ppa-cnesst" data-testid="ppa-cnesst" type="number" min="0" max="20" step="0.001" required value={cnesst} onChange={(e) => setCnesst(e.target.value)} /></p>
          <p><label htmlFor="ppa-majoration">Majoration des heures supplémentaires (%, au moins 50)</label>
            <input id="ppa-majoration" data-testid="ppa-majoration" type="number" min="50" max="100" step="1" required value={majoration}
              onChange={(e) => setMajoration(e.target.value)} /></p>
          <p className="checkbox-line"><input id="ppa-validation" data-testid="ppa-validation" type="checkbox" checked={validation}
            aria-describedby="ppa-validation-aide" onChange={(e) => setValidation(e.target.checked)} />
            <label htmlFor="ppa-validation">Exiger la validation des temps par la paie avant de payer</label></p>
          <p id="ppa-validation-aide" className="muted">Cochée, une feuille approuvée n'entre dans un cycle qu'une fois validée dans « Validation des temps ».</p>
          <p><button type="submit" className="primary" data-testid="ppa-enregistrer">Enregistrer les réglages</button></p>
        </form>
      </section>
      {bareme && (
        <section className="card" aria-labelledby="ppa-bareme-h" data-testid="ppa-bareme">
          <h2 id="ppa-bareme-h">Barème {bareme.annee}</h2>
          <table className="eff-table-compacte">
            <caption>Paramètres fiscaux appliqués par la paie</caption>
            <thead><tr><th scope="col">Paramètre</th><th scope="col">Valeur</th></tr></thead>
            <tbody>
              <tr><th scope="row">RRQ (base + supplémentaire), personne et employeur</th><td>{pourcent(bareme.rrq.tauxBase + bareme.rrq.tauxSupplementaire)}</td></tr>
              <tr><th scope="row">Maximum des gains admissibles (MGA)</th><td>{argent(bareme.rrq.mga)}</td></tr>
              <tr><th scope="row">RRQ2, jusqu'à</th><td>{pourcent(bareme.rrq.tauxRrq2)} jusqu'à {argent(bareme.rrq.plafondSupplementaire)}</td></tr>
              <tr><th scope="row">Assurance-emploi (taux du Québec)</th><td>{pourcent(bareme.ae.tauxEmploye)} jusqu'à {argent(bareme.ae.maximumAssurable)}</td></tr>
              <tr><th scope="row">RQAP, personne · employeur</th><td>{pourcent(bareme.rqap.tauxEmploye)} · {pourcent(bareme.rqap.tauxEmployeur)} jusqu'à {argent(bareme.rqap.maximumAssurable)}</td></tr>
              <tr><th scope="row">Montant personnel de base, Québec · fédéral</th><td>{argent(bareme.quebec.montantPersonnel)} · {argent(bareme.federal.montantPersonnel)}</td></tr>
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
