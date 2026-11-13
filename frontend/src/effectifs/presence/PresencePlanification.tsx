/**
 * **Présence** — onglet de la planification des effectifs (AO 26-015-DO, lot R8 — E-GP-4, E-GP-12,
 * E-GP-13, E-RF-13).
 *
 * L'encadrement voit les pointages de son périmètre, tranche ceux qui attendent (écart de lieu,
 * retard, quart déclaré après coup), affiche le code QR d'un milieu et importe les horodatages
 * d'une borne ou d'un système tiers, qui corroborent les pointages sans les remplacer.
 *
 * Les **profils de pointage** (E-GP-1, E-GP-2, P-GP-1) se règlent au bas de cet onglet : c'est le
 * même sujet, et un onglet de plus aurait séparé la configuration de ce qu'elle produit.
 *
 * Le **filtre par site** (E-GP-7) restreint la liste à une caserne : sans lui, l'encadrement d'un
 * site lit tous les pointages de son périmètre et ne peut pas vérifier le sien.
 *
 * L'**audit des localisations** (E-GP-14) dit où, quand, à quelle distance et avec quel verdict
 * chaque présence a été déclarée. La **position n'y est pas** : seule la distance au milieu est
 * conservée (Loi 25, minimisation), et c'est elle qui suffit à juger le lieu. L'écran le dit, pour
 * qu'un vérificateur ne cherche pas une donnée qui n'existe pas.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { liste, objet } from "../../formeRecue";
import { Badge, BadgeStatut, EtatVide, Message, Tableau, Tiroir, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { FormulaireModal } from "../../Modal";
import type { Milieu } from "../planification/useReferentiels";
import type { Audit, LigneAudit } from "./types";
import { horaire, LIBELLE_MECANISME, LIBELLE_SENS, LIBELLE_STATUT, resultatEnMots, TON_STATUT, type Pointage, type StatutPointage } from "./types";
import ProfilsPointage from "./ProfilsPointage";

const COLONNES: ReadonlyArray<Colonne<Pointage>> = [
  { id: "nom", libelle: "Personne", tri: (p) => p.nom },
  { id: "quand", libelle: "Quand", tri: (p) => p.horodatage },
  { id: "sens", libelle: "Pointage", tri: (p) => p.sens },
  { id: "resultat", libelle: "Preuve", tri: (p) => p.resultat },
  { id: "statut", libelle: "Statut", tri: (p) => p.statut },
  { id: "actions", libelle: "Actions", actions: true },
];

interface Bilan { importes: number; doublons: number; rejets: Array<{ ligne: number; motif: string }> }

/** L'audit ne montre **pas** de colonne de position : il n'y en a pas à montrer. */
const COLONNES_AUDIT: ReadonlyArray<Colonne<LigneAudit>> = [
  { id: "quand", libelle: "Quand", tri: (l) => l.horodatage },
  { id: "nom", libelle: "Personne", tri: (l) => l.nom },
  { id: "sens", libelle: "Pointage", tri: (l) => l.sens },
  { id: "milieu", libelle: "Site", tri: (l) => l.milieu },
  { id: "distance", libelle: "Distance", numerique: true, tri: (l) => l.distanceMetres ?? -1 },
  { id: "verdict", libelle: "Verdict", tri: (l) => l.ecart ?? l.resultat },
  { id: "mecanismes", libelle: "Mécanismes de preuve" },
];

/** Une ligne « clé;matricule;sens;horodatage » ; l'en-tête éventuel est ignoré. */
const lignesDe = (texte: string) => texte.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !/^cle;/i.test(l))
  .map((l) => { const [cle, matricule, sens, horodatage] = l.split(";").map((x) => x.trim()); return { cle, matricule, sens, horodatage }; });

export default function PresencePlanification() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const [filtre, setFiltre] = useState<"" | StatutPointage>("A_APPROUVER");
  const [site, setSite] = useState("");
  const [audit, setAudit] = useState<Audit | null>(null);
  const [auditEcartSeul, setAuditEcartSeul] = useState(false);
  const [pointages, setPointages] = useState<Pointage[]>([]);
  const [fiche, setFiche] = useState<Pointage | null>(null);
  const [recents, setRecents] = useState<Pointage[] | null>(null);
  const [motif, setMotif] = useState("");
  const [bilan, setBilan] = useState("");
  const [milieux, setMilieux] = useState<Milieu[]>([]);
  const [milieuId, setMilieuId] = useState("");
  const [code, setCode] = useState<{ code: string; valideJusqua: string } | null>(null);
  const [source, setSource] = useState("");
  const [csv, setCsv] = useState("");
  const [bilanImport, setBilanImport] = useState<Bilan | null>(null);

  const charger = useCallback(async () => {
    const q = new URLSearchParams();
    if (filtre) q.set("statut", filtre);
    if (site) q.set("milieuId", site);
    const suffixe = q.toString() ? `?${q}` : "";
    setPointages((await lire<Pointage[]>(`/api/effectifs/presence/pointages${suffixe}`)) ?? []);
  }, [lire, filtre, site]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => { void lire<Milieu[]>("/api/effectifs/milieux").then((m) => setMilieux(m ?? [])); }, [lire]);

  /** La fiche s'ouvre au geste et se relit : un autre membre de l'encadrement a pu trancher entre-temps. */
  const ouvrir = async (p: Pointage) => {
    setErreur(""); setMotif(""); setFiche(p); setRecents(null);
    const [frais, historique] = await Promise.all([lire<Pointage>(`/api/effectifs/presence/pointages/${p.id}`), lire<Pointage[]>(`/api/effectifs/presence/ressources/${p.ressourceId}/pointages`)]);
    const lu = objet<Pointage>(frais);
    if (lu) setFiche((f) => (f?.id === p.id ? lu : f));
    setRecents(liste<Pointage>(historique).filter((x) => x.id !== p.id).slice(0, 8));
  };

  const trancher = async (d: "approuver" | "rejeter") => {
    if (!fiche) return;
    const r = await envoyer<Pointage[]>(`/api/effectifs/presence/pointages/${fiche.id}/${d}`, { motif: motif || undefined });
    if (!r) return;
    // E-GP-6 : approuvé à un palier qui n'est pas le dernier, le pointage attend encore le suivant.
    const enAttente = d === "approuver" && r.some((x) => x.statut === "A_APPROUVER");
    setBilan(enAttente ? `${fiche.nom} : palier approuvé, le pointage passe au palier suivant.`
      : `${fiche.nom} : ${r.length > 1 ? `${r.length} pointages` : "pointage"} ${d === "approuver" ? "approuvé" : "rejeté"}${r.length > 1 ? "s" : ""}.`);
    setFiche(null);
    await charger();
  };

  const afficherCode = async () => {
    setCode(null);
    setCode(await lire<{ code: string; valideJusqua: string }>(`/api/effectifs/presence/milieux/${milieuId}/code-qr`));
  };

  /** Les paramètres de l'audit, partagés par la lecture et l'export : un seul endroit à changer. */
  const parametresAudit = () => {
    const q = new URLSearchParams();
    if (site) q.set("milieuId", site);
    if (auditEcartSeul) q.set("ecartSeul", "1");
    return q.toString() ? `?${q}` : "";
  };

  const chargerAudit = async () => {
    setAudit(objet<Audit>(await lire<Audit>(`/api/effectifs/presence/audit-localisations${parametresAudit()}`)));
  };

  const importer = async (e: FormEvent) => {
    e.preventDefault();
    setBilanImport(null);
    setBilanImport(await envoyer<Bilan>("/api/effectifs/presence/horodatages", { source, lignes: lignesDe(csv) }));
  };

  return (
    <div data-testid="pla-pre">
      <div className="eff-barre">
        <p>
          <label htmlFor="pla-pre-filtre">Statut</label>
          <select id="pla-pre-filtre" data-testid="pla-pre-filtre" value={filtre} onChange={(e) => setFiltre(e.target.value as "" | StatutPointage)}>
            <option value="">Tous</option>
            {(Object.keys(LIBELLE_STATUT) as StatutPointage[]).map((s) => <option key={s} value={s}>{LIBELLE_STATUT[s]}</option>)}
          </select>
        </p>
        {/* E-GP-7 : vérifier **une** caserne, et non tout le périmètre. */}
        <p>
          <label htmlFor="pla-pre-site">Site</label>
          <select id="pla-pre-site" data-testid="pla-pre-site" value={site} onChange={(e) => setSite(e.target.value)}>
            <option value="">Tous les sites</option>
            {milieux.map((m) => <option key={m.id} value={m.id}>{m.code} — {m.libelle}</option>)}
          </select>
        </p>
      </div>
      <p>Les preuves exigées se règlent par milieu (Paramétrage des effectifs › Milieux) ; les tolérances de retard et de distance, par les règles du contexte « Pointage ».</p>
      {erreur && !fiche && <Message variante="erreur" testId="pla-pre-erreur">{erreur}</Message>}
      {bilan && <Message variante="succes" testId="pla-pre-bilan">{bilan}</Message>}
      <Tableau colonnes={COLONNES} lignes={pointages} legende="Pointages de votre périmètre" testId="pla-pre-table"
        vide={<EtatVide titre="Aucun pointage" compact testId="pla-pre-vide">Aucun pointage ne correspond à ce statut.</EtatVide>}
        rendre={(p) => {
          const r = resultatEnMots(p);
          return (
            <tr key={p.id} data-testid={`pla-pre-${p.id}`}>
              <td>{p.nom}</td>
              <td>{horaire(p.horodatage)}</td>
              <td>{LIBELLE_SENS[p.sens]}{p.origine === "SAISIE" ? " (déclaré)" : ""}{p.corrobore ? " · corroboré" : ""}</td>
              <td><Badge ton={r.ton}>{r.texte}</Badge></td>
              <td><BadgeStatut statut={TON_STATUT[p.statut]} testId={`pla-pre-${p.id}-statut`}>{LIBELLE_STATUT[p.statut]}</BadgeStatut></td>
              <td>
                {p.statut === "A_APPROUVER" && (
                  <button type="button" className="secondary" data-testid={`pla-pre-${p.id}-trancher`} onClick={() => void ouvrir(p)}>
                    Trancher<span className="sr-only"> le pointage de {p.nom} du {horaire(p.horodatage)}</span>
                  </button>
                )}
              </td>
            </tr>
          );
        }} />

      <Tiroir ouvert={!!fiche} onFermer={() => setFiche(null)} titre={fiche ? `Pointage de ${fiche.nom}` : "Pointage"} testId="pla-pre-fiche">
        {fiche && (
          <>
            <p>{LIBELLE_SENS[fiche.sens]} le {horaire(fiche.horodatage)} — {resultatEnMots(fiche).texte}.</p>
            {fiche.justification && <p data-testid="pla-pre-fiche-justification">Justification : {fiche.justification}</p>}
            {fiche.saisieId && <p>Déclaration après coup : la décision vaut pour tous ses pointages.</p>}
            <ul aria-label="Preuves">
              {fiche.preuves.map((v) => (
                <li key={v.mecanisme}>{LIBELLE_MECANISME[v.mecanisme] ?? v.mecanisme}{v.exigee ? " (exigée)" : ""} : {v.fournie ? (v.satisfaite ? "satisfaite" : "non satisfaite") : "non fournie"}{v.distanceMetres !== undefined ? `, à ${v.distanceMetres} m` : ""}</li>
              ))}
            </ul>
            {fiche.motifsRegles.length > 0 && (
              <Message variante="avertissement" titre="Ce que les règles ont relevé" testId="pla-pre-fiche-motifs">
                <ul>{fiche.motifsRegles.map((m, i) => <li key={`${m.cle}-${i}`}>{m.message}</li>)}</ul>
              </Message>
            )}
            {fiche.flux && (
              <ol aria-label="Circuit d'approbation" data-testid="pla-pre-fiche-circuit">
                {fiche.flux.etapes.map((e) => (
                  <li key={e.id}>Palier {e.palier} : {e.libelle} — {{ EN_ATTENTE: "en attente", APPROUVEE: "approuvé", REJETEE: "rejeté" }[e.statut]}{e.palier === fiche.flux?.palierCourant ? " (palier courant)" : ""}</li>
                ))}
              </ol>
            )}
            {recents && recents.length > 0 && (
              <details data-testid="pla-pre-fiche-recents">
                <summary>Pointages récents de {fiche.nom}</summary>
                <ul>{recents.map((x) => <li key={x.id}>{horaire(x.horodatage)} — {LIBELLE_SENS[x.sens]} : {resultatEnMots(x).texte} ({LIBELLE_STATUT[x.statut].toLowerCase()})</li>)}</ul>
              </details>
            )}
            <p>
              <label htmlFor="pla-pre-motif">Motif (obligatoire pour un rejet)</label>
              <textarea id="pla-pre-motif" data-testid="pla-pre-motif" rows={2} maxLength={500} value={motif} onChange={(e) => setMotif(e.target.value)} />
            </p>
            {erreur && <Message variante="erreur" testId="pla-pre-fiche-erreur">{erreur}</Message>}
            <p className="eff-actions">
              <button type="button" data-testid="pla-pre-approuver" onClick={() => void trancher("approuver")}>{fiche.flux?.palierCourant && fiche.flux.etapes.length > 1 ? `Approuver le palier ${fiche.flux.palierCourant}` : "Approuver"}</button>
              <button type="button" className="secondary" data-testid="pla-pre-rejeter" onClick={() => void trancher("rejeter")}>Rejeter</button>
            </p>
          </>
        )}
      </Tiroir>

      <details className="eff-groupe" data-testid="pla-pre-qr">
        <summary>Code QR d'un milieu</summary>
        <div className="eff-barre">
          <p>
            <label htmlFor="pla-pre-milieu">Milieu</label>
            <select id="pla-pre-milieu" data-testid="pla-pre-milieu" value={milieuId} onChange={(e) => { setMilieuId(e.target.value); setCode(null); }}>
              <option value="">— choisir —</option>
              {milieux.map((m) => <option key={m.id} value={m.id}>{m.code} — {m.libelle}</option>)}
            </select>
          </p>
          <p><button type="button" data-testid="pla-pre-afficher-code" disabled={!milieuId} onClick={() => void afficherCode()}>Afficher le code</button></p>
        </div>
        {code && <p role="status" data-testid="pla-pre-code">Code : <code>{code.code}</code> — valide jusqu'à {horaire(code.valideJusqua)} (il change toutes les cinq minutes).</p>}
      </details>

      {/* Importer **ajoute** des pointages : un dialogue. Il reste ouvert après l'envoi, parce que
          le bilan — les lignes rejetées et leur motif — se corrige là où l'on vient de les coller. */}
      <div className="eff-actions">
        <FormulaireModal libelle="Importer des horodatages externes" titre="Importer des horodatages externes" testId="pla-pre-import" size="md" className="secondary">
            <form onSubmit={(e) => void importer(e)}>
              <p><label htmlFor="pla-pre-source">Source (borne, système)</label><input id="pla-pre-source" required maxLength={60} data-testid="pla-pre-source" value={source} onChange={(e) => setSource(e.target.value)} /></p>
              <p>
                <label htmlFor="pla-pre-csv">Lignes « clé;matricule;sens;horodatage ISO »</label>
                <textarea id="pla-pre-csv" required rows={4} data-testid="pla-pre-csv" aria-describedby="pla-pre-csv-aide" value={csv} onChange={(e) => setCsv(e.target.value)} />
                <span id="pla-pre-csv-aide">Sens : ENTREE, DEBUT_PAUSE, FIN_PAUSE ou SORTIE. Une clé déjà importée pour cette source est ignorée.</span>
              </p>
              <p><button type="submit" data-testid="pla-pre-importer">Importer</button></p>
            </form>
            {bilanImport && (
              <div role="status" data-testid="pla-pre-bilan-import">
                <p>{bilanImport.importes} importé(s), {bilanImport.doublons} déjà connu(s), {bilanImport.rejets.length} rejeté(s).</p>
                {bilanImport.rejets.length > 0 && <ul>{bilanImport.rejets.map((r) => <li key={r.ligne}>Ligne {r.ligne} : {r.motif}</li>)}</ul>}
              </div>
            )}
        </FormulaireModal>
      </div>

      {/* Audit des localisations (E-GP-14) : où, quand, à quelle distance, avec quel verdict. */}
      <details className="eff-groupe" data-testid="pla-pre-audit">
        <summary>Audit des localisations</summary>
        <p id="pla-pre-audit-aide">
          Chaque déclaration de présence est consignée avec <strong>son milieu, sa distance et le
          verdict de chaque mécanisme de preuve</strong>. La <strong>position n'est pas
          conservée</strong> : la distance suffit à juger le lieu, et garder les coordonnées
          permettrait de reconstituer des déplacements hors du travail (Loi 25, minimisation).
        </p>
        <p className="eff-case">
          <input id="pla-pre-audit-ecarts" type="checkbox" data-testid="pla-pre-audit-ecarts"
            checked={auditEcartSeul} onChange={(e) => { setAuditEcartSeul(e.target.checked); setAudit(null); }} />
          <label htmlFor="pla-pre-audit-ecarts">N'afficher que les écarts de lieu</label>
        </p>
        <p className="eff-actions">
          <button type="button" data-testid="pla-pre-audit-charger" onClick={() => void chargerAudit()}>
            Afficher l'audit{site ? " de ce site" : ""}
          </button>
          {/* L'export est un lien : le navigateur télécharge, l'écran ne recopie pas le CSV. */}
          <a data-testid="pla-pre-audit-csv" download
            href={`/api/effectifs/presence/audit-localisations.csv${parametresAudit()}`}>
            Exporter en CSV
          </a>
        </p>
        {audit && (
          <div data-testid="pla-pre-audit-resultat" role="status">
            <ul aria-label="Ce que l'audit couvre">
              {audit.motifs.map((m) => <li key={m}>{m}</li>)}
            </ul>
            <Tableau
              colonnes={COLONNES_AUDIT} lignes={audit.lignes}
              legende="Audit des localisations : quand, qui, quel pointage, quel site, quelle distance et quel verdict"
              testId="pla-pre-audit-table" tbodyTestId="pla-pre-audit-corps"
              vide={<EtatVide titre="Aucune déclaration" compact testId="pla-pre-audit-vide">
                Aucune déclaration de présence ne correspond à ces critères.
              </EtatVide>}
              rendre={(l) => (
                <tr key={l.pointageId} data-testid={`pla-pre-audit-${l.pointageId}`}>
                  <td>{horaire(l.horodatage)}</td>
                  <td>{l.nom}</td>
                  <td>{LIBELLE_SENS[l.sens]}</td>
                  <td>{l.milieu}</td>
                  <td className="numerique">
                    {l.distanceMetres == null
                      ? <span className="muted">non mesurée</span>
                      : `${l.distanceMetres} m`}
                  </td>
                  <td>
                    <Badge ton={l.ecart ? "erreur" : l.resultat === "CONFORME" ? "succes" : "neutre"}>
                      {l.ecart ?? l.resultat}
                    </Badge>
                  </td>
                  <td>
                    {l.mecanismes.length === 0
                      ? <span className="muted">aucun mécanisme exigé</span>
                      : l.mecanismes.map((m) => (
                        <span key={m.mecanisme}>
                          {LIBELLE_MECANISME[m.mecanisme] ?? m.mecanisme}
                          {" : "}
                          {m.satisfaite ? "satisfait" : m.fournie ? "hors du milieu" : "non fourni"}
                          <br />
                        </span>
                      ))}
                  </td>
                </tr>
              )} />
          </div>
        )}
      </details>

      {/* Profils de pointage (E-GP-1, E-GP-2, P-GP-1) : ce que chaque population peut déclarer. */}
      <ProfilsPointage />
    </div>
  );
}
