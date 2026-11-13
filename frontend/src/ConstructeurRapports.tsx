/**
 * **Constructeur de rapports** — STO DS2026-120-03, FIN191.
 *
 * L'exigence demande de *« personnaliser aisément de nouveaux rapports … et d'exporter les données
 * dans différents formats »*. Trois décisions d'interface en découlent :
 *
 * - **L'écran ne connaît que des sources déclarées**, et jamais un nom de table. Il ne peut donc
 *   pas en demander une qui n'ait pas été ouverte — c'est la même règle côté serveur, et c'est ce
 *   qui empêche un constructeur de rapports de devenir un lecteur universel de la base.
 * - **La définition se valide pendant la saisie**, sans être exécutée. Un refus qui n'arriverait
 *   qu'à l'exécution ferait recommencer, et sur une grosse table il ferait attendre pour rien.
 * - **Le résultat dit s'il est tronqué.** Une liste coupée en silence se lit comme une liste
 *   complète, et c'est ainsi qu'on rapporte un total faux.
 *
 * **Relevés statistiques d'effectifs** (AO 26-015-DO, P-RF-1) : l'organisation définit ses
 * propres relevés sur les sources d'effectifs — regroupements, filtres, indicateurs (dont le
 * **taux**, une somme rapportée à une autre) —, les **enregistre**, les reproduit et les exporte.
 * Une source de faits calculés exige sa période : l'écran la demande en deux dates plutôt que de
 * laisser l'usager deviner le filtre « entre » que le serveur attend.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { EnTeteEcran, EtatVide, Message } from "./composants";
import { useAuth } from "./AuthContext";
import { liste, objet } from "./formeRecue";

interface Champ { code: string; libelle: string; type: string; groupable?: boolean }
interface Source { code: string; libelle: string; module: string; champs: Champ[]; faits?: { periode: string } }
interface ColonneChoisie { champ: string; agregat?: string; denominateur?: string }
interface Definition {
  source: string; colonnes: ColonneChoisie[]; groupes?: string[];
  filtres?: Array<{ champ: string; operateur: string; valeurs: Array<string | number | boolean> }>;
  tri?: { champ: string; descendant?: boolean }; limite?: number;
}
interface Enregistre { id: string; nom: string; description: string | null; definition: Definition; modifieLe: string }
interface FiltreChoisi { champ: string; operateur: string; valeurs: string[] }
interface Resultat {
  colonnes: Array<{ champ: string; libelle: string; type: string }>;
  lignes: Array<Record<string, unknown>>;
  total: number;
  tronque: boolean;
}

/** Les libellés des ensembles fermés : le serveur envoie un code, l'écran affiche un mot. */
const AGREGATS: Record<string, string> = {
  somme: "Somme", moyenne: "Moyenne", compte: "Nombre", minimum: "Minimum", maximum: "Maximum", taux: "Taux (%)",
};
const OPERATEURS: Record<string, string> = {
  egal: "égal à", different: "différent de", contient: "contient", superieur: "supérieur à",
  inferieur: "inférieur à", entre: "compris entre", parmi: "parmi",
};

/** Le nombre de valeurs qu'attend chaque opérateur : l'écran n'offre pas ce que le serveur refuse. */
const ARITE: Record<string, number> = {
  egal: 1, different: 1, contient: 1, superieur: 1, inferieur: 1, entre: 2, parmi: 3,
};

export default function ConstructeurRapports() {
  const { apiFetch } = useAuth();
  const [erreur, setErreur] = useState("");
  const [sources, setSources] = useState<Source[]>([]);
  const [source, setSource] = useState("");
  const [colonnes, setColonnes] = useState<ColonneChoisie[]>([]);
  const [groupes, setGroupes] = useState<string[]>([]);
  const [filtres, setFiltres] = useState<FiltreChoisi[]>([]);
  const [triChamp, setTriChamp] = useState("");
  const [triDescendant, setTriDescendant] = useState(false);
  const [limite, setLimite] = useState("1000");
  // Les agrégats et les opérateurs viennent du serveur : les lister ici ferait proposer une
  // option que le domaine refuserait, ou taire une option qu'il accepte.
  const [agregats, setAgregats] = useState<string[]>([]);
  const [operateurs, setOperateurs] = useState<string[]>([]);
  const [formats, setFormats] = useState<string[]>([]);
  const [format, setFormat] = useState("csv");
  const [verdict, setVerdict] = useState<string>("");
  const [resultat, setResultat] = useState<Resultat | null>(null);
  // La période d'une source de faits : deux dates, traduites en filtre « entre » sur son champ de période.
  const [periodeDu, setPeriodeDu] = useState("");
  const [periodeAu, setPeriodeAu] = useState("");
  const [enregistres, setEnregistres] = useState<Enregistre[]>([]);
  const [courant, setCourant] = useState<Enregistre | null>(null);
  const [nom, setNom] = useState("");
  const [description, setDescription] = useState("");
  const [annonce, setAnnonce] = useState("");

  const choisie = useMemo(() => sources.find((s) => s.code === source) ?? null, [sources, source]);

  const lire = useCallback(async <T,>(chemin: string): Promise<T | null> => {
    const r = await apiFetch(chemin);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setErreur(j.errors?.join(", ") || `Erreur ${r.status}`);
      return null;
    }
    return r.json();
  }, [apiFetch]);

  const relireEnregistres = useCallback(async () => {
    setEnregistres(liste(await lire<Enregistre[]>("/api/rapports/enregistres")));
  }, [lire]);

  useEffect(() => {
    void lire<Source[]>("/api/rapports/sources").then((s) => {
      if (s) { setSources(liste(s)); setSource((a) => a || liste<Source>(s)[0]?.code || ""); }
    });
    void relireEnregistres();
    void lire<string[]>("/api/rapports/agregats").then((v) => setAgregats(liste(v)));
    void lire<string[]>("/api/rapports/operateurs").then((v) => setOperateurs(liste(v)));
    void lire<string[]>("/api/rapports/formats").then((f) => {
      if (f) { setFormats(f); setFormat((a) => (f.includes(a) ? a : f[0] ?? "")); }
    });
  }, [lire, relireEnregistres]);

  /** Changer de source remet tout à zéro : une colonne d'une autre source n'existe pas ici. */
  const changerSource = (code: string) => {
    setSource(code);
    setColonnes([]); setGroupes([]); setFiltres([]); setTriChamp(""); setVerdict(""); setResultat(null); setCourant(null);
  };

  const definition = useCallback((): Definition => ({
    source,
    colonnes: colonnes.map((c) => (c.agregat === "taux"
      ? { champ: c.champ, agregat: c.agregat, denominateur: c.denominateur ?? "" }
      : c.agregat ? { champ: c.champ, agregat: c.agregat } : { champ: c.champ })),
    groupes,
    filtres: [
      ...(choisie?.faits ? [{ champ: choisie.faits.periode, operateur: "entre", valeurs: [periodeDu, periodeAu] }] : []),
      ...filtres.map((f) => ({
        champ: f.champ, operateur: f.operateur,
        // `parmi` accepte autant de valeurs que l'usager en a saisies, séparées par une virgule.
        valeurs: f.operateur === "parmi"
          ? f.valeurs[0].split(",").map((v) => v.trim()).filter(Boolean)
          : f.valeurs.slice(0, ARITE[f.operateur]),
      })),
    ],
    tri: triChamp ? { champ: triChamp, descendant: triDescendant } : undefined,
    limite: Number(limite),
  }), [source, choisie, colonnes, groupes, filtres, periodeDu, periodeAu, triChamp, triDescendant, limite]);

  /** Charger un rapport enregistré : sa définition remplit l'écran, la période revient dans ses deux dates. */
  const charger = async (choisi: Enregistre) => {
    // La version enregistrée fait foi : un collègue a pu la modifier depuis l'affichage de la liste.
    const r = objet<Enregistre>(await lire(`/api/rapports/enregistres/${choisi.id}`)) ?? choisi;
    const d = r.definition;
    const s = sources.find((x) => x.code === d.source);
    const estPeriode = (f: { champ: string; operateur: string }) => Boolean(s?.faits) && f.champ === s?.faits?.periode && f.operateur === "entre";
    const periode = (d.filtres ?? []).find(estPeriode);
    setSource(d.source);
    setColonnes(d.colonnes.map((c) => ({ ...c })));
    setGroupes(d.groupes ?? []);
    setFiltres((d.filtres ?? []).filter((f) => !estPeriode(f)).map((f) => ({
      champ: f.champ, operateur: f.operateur,
      valeurs: f.operateur === "parmi" ? [f.valeurs.join(", ")] : f.valeurs.map(String),
    })));
    setPeriodeDu(periode ? String(periode.valeurs[0]) : "");
    setPeriodeAu(periode ? String(periode.valeurs[1]) : "");
    setTriChamp(d.tri?.champ ?? "");
    setTriDescendant(d.tri?.descendant === true);
    setLimite(String(d.limite ?? 1000));
    setCourant(r); setNom(r.nom); setDescription(r.description ?? "");
    setVerdict(""); setResultat(null);
    setAnnonce(`Rapport « ${r.nom} » chargé.`);
  };

  const appeler = useCallback(async (chemin: string, corps: unknown, methode: "POST" | "PUT" | "DELETE" = "POST") => {
    setErreur("");
    const r = await apiFetch(chemin, {
      method: methode, headers: { "Content-Type": "application/json" }, body: methode === "DELETE" ? undefined : JSON.stringify(corps),
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setErreur(j.errors?.join(", ") || `Erreur ${r.status}`); return null; }
    return j;
  }, [apiFetch]);

  const basculerColonne = (champ: string) =>
    setColonnes((c) => (c.some((x) => x.champ === champ)
      ? c.filter((x) => x.champ !== champ)
      : [...c, { champ }]));

  const basculerGroupe = (champ: string) =>
    setGroupes((g) => (g.includes(champ) ? g.filter((x) => x !== champ) : [...g, champ]));

  const enregistrerCourant = async (nouveau: boolean) => {
    setAnnonce("");
    const corps = { nom, description, definition: definition() };
    const r = nouveau || !courant
      ? await appeler("/api/rapports/enregistres", corps)
      : await appeler(`/api/rapports/enregistres/${courant.id}`, corps, "PUT");
    if (!r) return;
    setCourant(objet<Enregistre>(r));
    await relireEnregistres();
    setAnnonce(`Rapport « ${r.nom} » ${nouveau || !courant ? "enregistré" : "mis à jour"}.`);
  };

  const retirer = async (r: Enregistre) => {
    setAnnonce("");
    if (!(await appeler(`/api/rapports/enregistres/${r.id}`, null, "DELETE"))) return;
    if (courant?.id === r.id) setCourant(null);
    await relireEnregistres();
    setAnnonce(`Rapport « ${r.nom} » retiré.`);
  };

  /** Télécharger un fichier rendu par le serveur, par un lien créé puis révoqué. */
  const exporterFichier = async (chemin: string, corps: unknown, nomFichier: string) => {
    setErreur("");
    const r = await apiFetch(chemin, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps),
    });
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setErreur(j.errors?.join(", ") || `Erreur ${r.status}`);
      return;
    }
    // Sans révocation, l'objet resterait en mémoire du navigateur tant que l'onglet est ouvert.
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const lien = document.createElement("a");
    lien.href = url;
    lien.download = nomFichier;
    lien.click();
    URL.revokeObjectURL(url);
    setAnnonce(`Fichier ${nomFichier} produit.`);
  };

  return (
    <section>
      <EnTeteEcran
        titre="Constructeur de rapports"
        description="Bâtir un rapport sur une source déclarée, le valider pendant la saisie, l'exécuter et l'exporter."
        testId="rap-titre"
        icone="graphique"
      />

      {erreur && <Message variante="erreur" testId="rap-erreur">{erreur}</Message>}
      <p role="status" data-testid="rap-annonce">{annonce}</p>

      <ListeEnregistres enregistres={enregistres} courant={courant} onCharger={(r) => void charger(r)} onRetirer={(r) => void retirer(r)}
        onProduire={async (r) => { await charger(r); setResultat(await appeler(`/api/rapports/enregistres/${r.id}/executer`, {})); }}
        onExporter={(r) => void exporterFichier(`/api/rapports/enregistres/${r.id}/exporter`, { format }, `rapport-${r.definition.source}.${format}`)} />

      <div className="champ">
        <label htmlFor="rap-source">Source de données</label>
        <select id="rap-source" data-testid="rap-source" value={source} onChange={(e) => changerSource(e.target.value)}>
          <option value="">— Choisir une source —</option>
          {sources.map((s) => <option key={s.code} value={s.code}>{s.libelle}</option>)}
        </select>
      </div>

      {!choisie && (
        <EtatVide titre="Aucune source choisie">
          Les sources offertes dépendent des modules activés pour votre organisme.
        </EtatVide>
      )}

      {choisie && (
        <>
          <fieldset data-testid="rap-colonnes">
            <legend>Colonnes du rapport</legend>
            {choisie.champs.map((c) => {
              const retenue = colonnes.find((x) => x.champ === c.code);
              const numerique = c.type === "nombre" || c.type === "montant";
              return (
                <div key={c.code} className="champ">
                  <input type="checkbox" id={`rap-col-${c.code}`} data-testid={`rap-col-${c.code}`}
                    checked={Boolean(retenue)} onChange={() => basculerColonne(c.code)} />
                  <label htmlFor={`rap-col-${c.code}`}>{c.libelle}</label>
                  {retenue && (
                    <>
                      <label htmlFor={`rap-agr-${c.code}`} className="sr-only">
                        Agrégat appliqué à {c.libelle}
                      </label>
                      <select id={`rap-agr-${c.code}`} data-testid={`rap-agr-${c.code}`}
                        value={retenue.agregat ?? ""}
                        onChange={(e) => setColonnes((liste) => liste.map((x) =>
                          x.champ === c.code ? { champ: c.code, agregat: e.target.value || undefined } : x))}>
                        <option value="">Aucun agrégat</option>
                        {agregats
                          // Additionner des dates rendrait un résultat qu'on ne peut ni lire ni
                          // contester : seul « Nombre » s'applique à tout.
                          .filter((code) => code === "compte" || numerique)
                          .map((code) => <option key={code} value={code}>{AGREGATS[code] ?? code}</option>)}
                      </select>
                      {retenue.agregat === "taux" && (
                        <>
                          <label htmlFor={`rap-den-${c.code}`}>Rapporté à</label>
                          <select id={`rap-den-${c.code}`} data-testid={`rap-den-${c.code}`} value={retenue.denominateur ?? ""}
                            onChange={(e) => setColonnes((l) => l.map((x) => (x.champ === c.code ? { ...x, denominateur: e.target.value } : x)))}>
                            <option value="">— Choisir le dénominateur —</option>
                            {choisie.champs.filter((d) => d.code !== c.code && (d.type === "nombre" || d.type === "montant"))
                              .map((d) => <option key={d.code} value={d.code}>{d.libelle}</option>)}
                          </select>
                        </>
                      )}
                    </>
                  )}
                </div>
              );
            })}
          </fieldset>

          <fieldset data-testid="rap-groupes">
            <legend>Regroupements</legend>
            {choisie.champs.filter((c) => c.groupable !== false).map((c) => (
              <div key={c.code} className="champ">
                <input type="checkbox" id={`rap-grp-${c.code}`} data-testid={`rap-grp-${c.code}`}
                  checked={groupes.includes(c.code)} onChange={() => basculerGroupe(c.code)} />
                <label htmlFor={`rap-grp-${c.code}`}>{c.libelle}</label>
              </div>
            ))}
          </fieldset>

          {choisie.faits && (
            <fieldset data-testid="rap-periode">
              <legend>Période du relevé (obligatoire, une année au plus)</legend>
              <div className="champ">
                <label htmlFor="rap-periode-du">Du</label>
                <input id="rap-periode-du" data-testid="rap-periode-du" type="date" value={periodeDu} onChange={(e) => setPeriodeDu(e.target.value)} />
              </div>
              <div className="champ">
                <label htmlFor="rap-periode-au">Au</label>
                <input id="rap-periode-au" data-testid="rap-periode-au" type="date" value={periodeAu} onChange={(e) => setPeriodeAu(e.target.value)} />
              </div>
            </fieldset>
          )}

          <FiltresChoisis champs={choisie.champs} operateurs={operateurs} filtres={filtres} onChanger={setFiltres} />

          <fieldset>
            <legend>Tri et limite</legend>
            <div className="champ">
              <label htmlFor="rap-tri">Trier par</label>
              <select id="rap-tri" data-testid="rap-tri" value={triChamp} onChange={(e) => setTriChamp(e.target.value)}>
                <option value="">Aucun tri</option>
                {choisie.champs.map((c) => <option key={c.code} value={c.code}>{c.libelle}</option>)}
              </select>
            </div>
            <div className="champ">
              <input type="checkbox" id="rap-tri-desc" data-testid="rap-tri-desc"
                checked={triDescendant} onChange={(e) => setTriDescendant(e.target.checked)} />
              <label htmlFor="rap-tri-desc">Ordre décroissant</label>
            </div>
            <div className="champ">
              <label htmlFor="rap-limite">Nombre maximal de lignes</label>
              <input id="rap-limite" data-testid="rap-limite" type="number" min="1" max="10000"
                aria-describedby="rap-limite-aide" value={limite} onChange={(e) => setLimite(e.target.value)} />
              <span id="rap-limite-aide">Au plus 10 000 : au-delà, un rapport d'écran devient un export.</span>
            </div>
          </fieldset>

          <p>
            <button type="button" data-testid="rap-valider" onClick={async () => {
              const r = await appeler("/api/rapports/valider", definition());
              setVerdict(r ? `Définition valide : ${r.colonnes.length} colonne(s), limite ${r.limite}.` : "");
            }}>
              Valider la définition
            </button>
            <button type="button" data-testid="rap-executer" onClick={async () => {
              setResultat(await appeler("/api/rapports/executer", definition()));
            }}>
              Exécuter le rapport
            </button>
          </p>

          {verdict && <Message variante="succes" testId="rap-verdict">{verdict}</Message>}

          <fieldset>
            <legend>Exporter</legend>
            <div className="champ">
              <label htmlFor="rap-format">Format</label>
              <select id="rap-format" data-testid="rap-format" value={format} onChange={(e) => setFormat(e.target.value)}>
                {formats.map((f) => <option key={f} value={f}>{f.toUpperCase()}</option>)}
              </select>
            </div>
            <button type="button" data-testid="rap-exporter"
              onClick={() => void exporterFichier("/api/rapports/exporter", { definition: definition(), format }, `rapport-${source}.${format}`)}>
              Exporter le rapport
            </button>
          </fieldset>

          <fieldset data-testid="rap-enregistrer">
            <legend>Enregistrer ce rapport pour l'organisme</legend>
            <div className="champ">
              <label htmlFor="rap-enr-nom">Nom du rapport</label>
              <input id="rap-enr-nom" data-testid="rap-enr-nom" maxLength={120} value={nom} onChange={(e) => setNom(e.target.value)} />
            </div>
            <div className="champ">
              <label htmlFor="rap-enr-description">Description (facultative)</label>
              <textarea id="rap-enr-description" data-testid="rap-enr-description" maxLength={1000} rows={2}
                value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            <button type="button" data-testid="rap-enr-creer" onClick={() => void enregistrerCourant(true)}>
              Enregistrer comme nouveau rapport
            </button>
            {courant && (
              <button type="button" data-testid="rap-enr-maj" onClick={() => void enregistrerCourant(false)}>
                Mettre à jour « {courant.nom} »
              </button>
            )}
          </fieldset>

          {resultat && (
            <>
              {/* **Dire que la liste est coupée** : une liste tronquée en silence se lit comme une
                  liste complète, et c'est ainsi qu'on rapporte un total faux. */}
              <p role="status" data-testid="rap-resume">
                {resultat.total} ligne(s){resultat.tronque
                  ? " — résultat tronqué à la limite demandée : augmentez la limite ou affinez les filtres."
                  : "."}
              </p>
              <table data-testid="rap-resultat">
                <caption className="sr-only">Résultat du rapport bâti sur la source {choisie.libelle}</caption>
                <thead>
                  <tr>{resultat.colonnes.map((c) => <th key={c.champ} scope="col">{c.libelle}</th>)}</tr>
                </thead>
                <tbody>
                  {resultat.lignes.length === 0 && (
                    <tr><td colSpan={resultat.colonnes.length}>Aucune ligne ne répond à ces filtres.</td></tr>
                  )}
                  {resultat.lignes.map((l, i) => (
                    <tr key={i} data-testid={`rap-ligne-${i}`}>
                      {resultat.colonnes.map((c) => <td key={c.champ}>{l[c.champ] === null || l[c.champ] === undefined ? "—" : String(l[c.champ])}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </>
      )}
    </section>
  );
}

/** Les rapports que l'organisme a enregistrés : chacun se charge, se produit, s'exporte ou se retire. */
function ListeEnregistres({ enregistres, courant, onCharger, onProduire, onExporter, onRetirer }: {
  enregistres: Enregistre[]; courant: Enregistre | null;
  onCharger: (r: Enregistre) => void; onProduire: (r: Enregistre) => void; onExporter: (r: Enregistre) => void; onRetirer: (r: Enregistre) => void;
}) {
  return (
    <section aria-labelledby="rap-enr-titre" data-testid="rap-enregistres">
      <h2 id="rap-enr-titre">Rapports enregistrés de l'organisme</h2>
      {enregistres.length === 0
        ? <p data-testid="rap-enr-vide">Aucun rapport enregistré : bâtissez-en un ci-dessous, puis enregistrez-le.</p>
        : (
          <table data-testid="rap-enr-liste">
            <caption className="sr-only">Rapports enregistrés</caption>
            <thead><tr><th scope="col">Nom</th><th scope="col">Description</th><th scope="col">Modifié le</th><th scope="col">Actions</th></tr></thead>
            <tbody>
              {enregistres.map((r, i) => (
                <tr key={r.id} data-testid={`rap-enr-${i}`} aria-current={courant?.id === r.id ? "true" : undefined}>
                  <th scope="row">{r.nom}</th>
                  <td>{r.description ?? "—"}</td>
                  <td>{r.modifieLe.slice(0, 10)}</td>
                  <td>
                    <button type="button" data-testid={`rap-enr-charger-${i}`} onClick={() => onCharger(r)}>Charger « {r.nom} »</button>
                    <button type="button" data-testid={`rap-enr-produire-${i}`} onClick={() => onProduire(r)}>Produire « {r.nom} »</button>
                    <button type="button" data-testid={`rap-enr-exporter-${i}`} onClick={() => onExporter(r)}>Exporter « {r.nom} »</button>
                    <button type="button" data-testid={`rap-enr-retirer-${i}`} onClick={() => onRetirer(r)}>Retirer « {r.nom} »</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
    </section>
  );
}

/** Les filtres, ajoutés un par un : chacun porte son champ, son opérateur et ses valeurs. */
function FiltresChoisis({ champs, operateurs, filtres, onChanger }: {
  champs: Champ[]; operateurs: string[]; filtres: FiltreChoisi[]; onChanger: (f: FiltreChoisi[]) => void;
}) {
  const modifier = (i: number, partiel: Partial<FiltreChoisi>) =>
    onChanger(filtres.map((f, j) => (j === i ? { ...f, ...partiel } : f)));

  return (
    <fieldset data-testid="rap-filtres">
      <legend>Filtres</legend>
      {filtres.map((f, i) => (
        <div key={i} className="champ" data-testid={`rap-filtre-${i}`}>
          <label htmlFor={`rap-filtre-champ-${i}`} className="sr-only">Champ du filtre {i + 1}</label>
          <select id={`rap-filtre-champ-${i}`} data-testid={`rap-filtre-champ-${i}`}
            value={f.champ} onChange={(e) => modifier(i, { champ: e.target.value })}>
            {champs.map((c) => <option key={c.code} value={c.code}>{c.libelle}</option>)}
          </select>

          <label htmlFor={`rap-filtre-op-${i}`} className="sr-only">Opérateur du filtre {i + 1}</label>
          <select id={`rap-filtre-op-${i}`} data-testid={`rap-filtre-op-${i}`}
            value={f.operateur} onChange={(e) => modifier(i, { operateur: e.target.value })}>
            {operateurs
              // « contient » ne s'applique qu'à un champ texte : l'offrir ailleurs ferait
              // construire une définition que le serveur refuserait.
              .filter((code) => code !== "contient"
                || champs.find((c) => c.code === f.champ)?.type === "texte")
              .map((code) => <option key={code} value={code}>{OPERATEURS[code] ?? code}</option>)}
          </select>

          <label htmlFor={`rap-filtre-val-${i}`} className="sr-only">
            {f.operateur === "parmi" ? `Valeurs du filtre ${i + 1}, séparées par des virgules` : `Valeur du filtre ${i + 1}`}
          </label>
          <input id={`rap-filtre-val-${i}`} data-testid={`rap-filtre-val-${i}`}
            value={f.valeurs[0] ?? ""} onChange={(e) => modifier(i, { valeurs: [e.target.value, f.valeurs[1] ?? ""] })} />

          {f.operateur === "entre" && (
            <>
              <label htmlFor={`rap-filtre-val2-${i}`} className="sr-only">Seconde borne du filtre {i + 1}</label>
              <input id={`rap-filtre-val2-${i}`} data-testid={`rap-filtre-val2-${i}`}
                value={f.valeurs[1] ?? ""} onChange={(e) => modifier(i, { valeurs: [f.valeurs[0] ?? "", e.target.value] })} />
            </>
          )}

          <button type="button" data-testid={`rap-filtre-retirer-${i}`}
            onClick={() => onChanger(filtres.filter((_, j) => j !== i))}>
            Retirer le filtre {i + 1}
          </button>
        </div>
      ))}
      <button type="button" data-testid="rap-filtre-ajouter"
        onClick={() => onChanger([...filtres, { champ: champs[0].code, operateur: "egal", valeurs: [""] }])}>
        Ajouter un filtre
      </button>
    </fieldset>
  );
}
