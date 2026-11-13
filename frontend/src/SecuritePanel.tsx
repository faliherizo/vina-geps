/**
 * **Sécurité et capacité** de l'instance (grille 32), lues du serveur : version déployée, pare-feu
 * applicatif et ses blocages, verrous de connexion, dernier balayage des dépendances, mémoire,
 * charge, débit et latence p95. Des chiffres, jamais un feu vert.
 */
import { useEffect, useState } from "react";
import { Message, Icone } from "./composants";

interface Etat {
  version: { application: string; git: string | null; node: string; migrations: string | null };
  residence: { region: string | null; hebergeur: string | null; auQuebec: boolean | null; auCanada: boolean | null; ia: string };
  pareFeu: { mode: string; reglages: { requetesParMinute: number; echecsConnexionAvantVerrou: number; verrouMinutes: number }; blocages24h: Record<string, number>; verrousActifs: number };
  enTetes: string[];
  balayage: { faitLe: string; outil: string; vulnerabilites: { critique: number; elevee: number; moderee: number; faible: number; total: number } } | null;
  capacite: { demarreLe: string; dureeSecondes: number; cpus: number; chargeMoyenne1Min: number; memoireRssMo: number; memoireTasMo: number; baseOctets: number | null;
    requetesDerniereMinute: number; latenceP95Ms: number | null; latenceP50Ms: number | null; enVol: number };
}

const LIBELLE_REGLE: Record<string, string> = {
  traversee: "traversée de répertoire", octet_nul: "octet nul", injection_sql: "injection SQL", script: "script inséré", commande: "injection de commande",
  longueur: "requête démesurée", debit: "débit dépassé", verrou: "connexion sur compte verrouillé", verrou_pose: "verrous posés",
};
const LIBELLE_MODE: Record<string, string> = { bloquer: "actif (bloque)", observer: "observation seulement", inactif: "inactif" };

export function formaterDuree(secondes: number): string {
  const j = Math.floor(secondes / 86_400), h = Math.floor((secondes % 86_400) / 3600), m = Math.floor((secondes % 3600) / 60);
  return j > 0 ? `${j} j ${h} h` : h > 0 ? `${h} h ${m} min` : `${m} min`;
}

export default function SecuritePanel({ apiFetch }: { apiFetch: (path: string, init?: RequestInit) => Promise<Response> }) {
  const [etat, setEtat] = useState<Etat | null>(null);
  const [erreur, setErreur] = useState("");
  useEffect(() => {
    apiFetch("/api/exploitation/securite").then(async (r) => {
      const j = r.ok ? await r.json() : null;
      if (j && j.version && j.pareFeu && j.capacite) setEtat(j); else setErreur("Impossible de lire l'état de sécurité.");
    })
      .catch(() => setErreur("Impossible de lire l'état de sécurité."));
  }, [apiFetch]);

  const blocages = Object.entries(etat?.pareFeu.blocages24h ?? {});
  return (
    <section className="card" aria-labelledby="sa-securite-titre" data-testid="sa-securite" style={{ marginTop: 16 }}>
      <h3 id="sa-securite-titre"><Icone nom="bouclier" /> Sécurité et capacité</h3>
      {erreur && <Message variante="erreur">{erreur}</Message>}
      {etat && (
        <dl className="account-meta" data-testid="sa-securite-etat">
          <div><dt>Version</dt><dd>{etat.version.application}{etat.version.git ? ` (${etat.version.git.slice(0, 12)})` : ""} · Node {etat.version.node} · migrations jusqu'à {etat.version.migrations ?? "—"}</dd></div>
          <div><dt>Résidence des données</dt><dd data-testid="sa-securite-residence">{etat.residence.region === null
            ? <strong>non déclarée (REGION_DONNEES) — à poser avant toute mise en service</strong>
            : <>{etat.residence.hebergeur ?? etat.residence.region} · {etat.residence.auQuebec ? "au Québec" : etat.residence.auCanada ? "au Canada, hors Québec (acceptation écrite requise)" : <strong>hors Canada — non conforme</strong>}</>}
            {" · IA : "}{etat.residence.ia}</dd></div>
          <div><dt>Pare-feu applicatif</dt><dd data-testid="sa-securite-waf">{LIBELLE_MODE[etat.pareFeu.mode] ?? etat.pareFeu.mode} · {etat.pareFeu.reglages.requetesParMinute || "∞"} requêtes/min/adresse · verrou après {etat.pareFeu.reglages.echecsConnexionAvantVerrou} échecs pendant {etat.pareFeu.reglages.verrouMinutes} min · {etat.pareFeu.verrousActifs} verrou(s) actif(s)</dd></div>
          <div><dt>Blocages (24 h)</dt><dd data-testid="sa-securite-blocages">{blocages.length === 0 ? "aucun" : blocages.map(([r, n]) => `${LIBELLE_REGLE[r] ?? r} : ${n}`).join(" · ")}</dd></div>
          <div><dt>En-têtes de durcissement</dt><dd>{etat.enTetes.join(", ")}</dd></div>
          <div><dt>Balayage des dépendances</dt><dd data-testid="sa-securite-balayage">{etat.balayage
            ? `${etat.balayage.outil}, le ${new Date(etat.balayage.faitLe).toLocaleString("fr-CA")} : ${etat.balayage.vulnerabilites.total} vulnérabilité(s) — critique ${etat.balayage.vulnerabilites.critique}, élevée ${etat.balayage.vulnerabilites.elevee}, modérée ${etat.balayage.vulnerabilites.moderee}, faible ${etat.balayage.vulnerabilites.faible}`
            : <strong>jamais balayé sur cette instance</strong>}</dd></div>
          <div><dt>Capacité</dt><dd data-testid="sa-securite-capacite">en service depuis {formaterDuree(etat.capacite.dureeSecondes)} · {etat.capacite.cpus} cœurs, charge {etat.capacite.chargeMoyenne1Min} · mémoire {etat.capacite.memoireRssMo} Mo (tas {etat.capacite.memoireTasMo} Mo) · base {etat.capacite.baseOctets === null ? "en mémoire" : `${(etat.capacite.baseOctets / 1_048_576).toFixed(1)} Mo`}</dd></div>
          <div><dt>Débit et latence</dt><dd data-testid="sa-securite-latence">{etat.capacite.requetesDerniereMinute} requêtes la dernière minute · p50 {etat.capacite.latenceP50Ms ?? "—"} ms · p95 {etat.capacite.latenceP95Ms ?? "—"} ms · {etat.capacite.enVol} en vol</dd></div>
        </dl>
      )}
      <p className="aide">Balayage dynamique OWASP ZAP et audit des dépendances à chaque construction et chaque semaine (CI) ; le test d'intrusion par un tiers indépendant est un engagement contractuel, hors de cet écran.</p>
    </section>
  );
}
