/**
 * **Registre des incidents de confidentialité** (Loi 25 ; écart E10 / écran ÉC-8, règles INC-1…INC-5).
 *
 * Consigner (dates, nature, renseignements visés, gravité, mesures, responsable), notifier la CAI
 * et les personnes (une gravité élevée sans notification s'écrit « attendue », jamais bloquant),
 * clore après modale — jamais supprimer (conservation cinq ans). Filtres statut et gravité, export
 * CSV et PDF : le registre que la CAI demande. En mode « tous » (superadmin), l'établissement
 * s'affiche en colonne.
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "./composants";
import { FormulaireModal } from "./Modal";
import TbodyPagine from "./TbodyPagine";
import ChampPersonne from "./ChampPersonne";
import { useConfirm } from "./Modal";
import Pagination, { usePagination } from "./Pagination";
import { useT } from "./i18n";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
interface Code { code: string; libelle: string }
interface Vocabulaire { natures: Code[]; renseignements: Code[]; gravites: Code[] }
interface Incident {
  id: string; tenantId: string; nomEtablissement: string | null; survenuLe: string; decouvertLe: string; nature: string; description: string; renseignements: string[];
  personnesTouchees: number | null; gravite: string; mesures: string; responsableId: string | null; responsableNom: string | null;
  notifieCai: boolean; notifieCaiLe: string | null; notifiePersonnes: boolean; notifiePersonnesLe: string | null;
  statut: "ouvert" | "clos"; closLe: string | null; consigneLe: string; attendue: { cai: boolean; personnes: boolean };
}

const libelle = (liste: Code[] | undefined, code: string) => liste?.find((c) => c.code === code)?.libelle ?? code;
const court = (l: string) => l.split(" — ")[0];

export default function IncidentsConfidentialite({ apiFetch, tous = false }: { apiFetch: Appel; tous?: boolean }) {
  const { t } = useT();
  const [voc, setVoc] = useState<Vocabulaire | null>(null);
  const [incidents, setIncidents] = useState<Incident[] | null>(null);
  const [filtre, setFiltre] = useState({ statut: "", gravite: "" });
  const [enEdition, setEnEdition] = useState<Incident | null>(null);
  const [formulaireOuvert, setFormulaireOuvert] = useState(false);
  const [responsable, setResponsable] = useState("");
  const [gravite, setGravite] = useState("faible");
  const [notifieCai, setNotifieCai] = useState(false);
  const [notifiePersonnes, setNotifiePersonnes] = useState(false);
  const [erreur, setErreur] = useState("");
  const [succes, setSucces] = useState("");
  const { confirm, ConfirmHost } = useConfirm();
  const base = tous ? "/api/securite/incidents/tous" : "/api/securite/incidents";
  const q = new URLSearchParams(Object.entries(filtre).filter(([, v]) => v)).toString();

  const charger = useCallback(async () => {
    const r = await apiFetch(`${base}${q ? `?${q}` : ""}`);
    if (r.ok) { const j = await r.json().catch(() => []); setIncidents(Array.isArray(j) ? j : []); } else { setIncidents([]); setErreur(t("incidents.erreurLecture")); }
  }, [apiFetch, base, q, t]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => {
    apiFetch("/api/securite/incidents/vocabulaire").then(async (r) => { const j = r.ok ? await r.json().catch(() => null) : null; if (j && Array.isArray(j.natures)) setVoc(j); }).catch(() => {});
  }, [apiFetch]);

  const ouvrir = (i: Incident | null) => {
    setEnEdition(i); setFormulaireOuvert(true); setErreur(""); setSucces("");
    setResponsable(i?.responsableId ?? ""); setGravite(i?.gravite ?? "faible"); setNotifieCai(i?.notifieCai ?? false); setNotifiePersonnes(i?.notifiePersonnes ?? false);
  };
  const fermer = () => { setFormulaireOuvert(false); setEnEdition(null); };

  async function enregistrer(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault(); setErreur(""); setSucces("");
    const d = new FormData(e.currentTarget);
    const clos = enEdition?.statut === "clos";
    const notifications = {
      notifieCai, notifieCaiLe: notifieCai ? String(d.get("notifieCaiLe") ?? "") || null : null,
      notifiePersonnes, notifiePersonnesLe: notifiePersonnes ? String(d.get("notifiePersonnesLe") ?? "") || null : null,
    };
    const personnes = String(d.get("personnesTouchees") ?? "").trim();
    // Clos : le serveur ne prend que les notifications (règle INC-3) ; on n'envoie que cela.
    const corps = clos ? notifications : {
      survenuLe: String(d.get("survenuLe") ?? ""), decouvertLe: String(d.get("decouvertLe") ?? ""), nature: String(d.get("nature") ?? ""),
      description: String(d.get("description") ?? ""), renseignements: d.getAll("renseignements").map(String),
      personnesTouchees: personnes === "" ? null : Number(personnes), gravite, mesures: String(d.get("mesures") ?? ""), responsableId: responsable || null, ...notifications,
    };
    const r = await apiFetch(enEdition ? `/api/securite/incidents/${enEdition.id}` : "/api/securite/incidents", { method: enEdition ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setErreur(t("incidents.erreur", { erreurs: (j.errors ?? [t("integrations.refus")]).join(" ; ") })); return; }
    const i: Incident = await r.json();
    const cibles = [i.attendue.cai && t("incidents.cai"), i.attendue.personnes && t("incidents.personnesMot")].filter(Boolean).join(t("incidents.et") + " ");
    setSucces(`${enEdition ? t("incidents.modifie") : t("incidents.consigne")}${i.attendue.cai || i.attendue.personnes ? t("incidents.succesAttendue", { cibles }) : ""}`);
    fermer(); await charger();
  }

  async function clore(i: Incident) {
    if (!await confirm({ title: t("incidents.cloreTitre"), message: t("incidents.cloreMessage", { date: i.survenuLe, nature: libelle(voc?.natures, i.nature) }), confirmLabel: t("incidents.clore"), variant: "danger", testId: "incident-clore-modal" })) return;
    const r = await apiFetch(`/api/securite/incidents/${i.id}/clore`, { method: "POST" });
    if (r.ok) { setSucces(t("incidents.closSucces")); await charger(); } else setErreur(t("incidents.closEchec"));
  }

  const pager = usePagination(incidents ?? [], 10);
  const clos = enEdition?.statut === "clos";
  const attendueElevee = gravite === "elevee" && (!notifieCai || !notifiePersonnes);

  return (
    <section className="card" aria-labelledby="incidents-titre" data-testid="incidents-confidentialite">
      <h3 id="incidents-titre">{t("incidents.title")}</h3>
      <p className="section-help">{t("incidents.help")}</p>
      {erreur && <Message variante="erreur" testId="incidents-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="incidents-succes">{succes}</Message>}

      <div className="grid-form" role="group" aria-label={t("incidents.filtres")}>
        <label className="field"><span>{t("incidents.statut")}</span>
          <select value={filtre.statut} onChange={(e) => { setFiltre({ ...filtre, statut: e.target.value }); pager.setPage(1); }} data-testid="incidents-filtre-statut">
            <option value="">{t("incidents.tous")}</option><option value="ouvert">{t("incidents.ouvert")}</option><option value="clos">{t("incidents.clos")}</option>
          </select></label>
        <label className="field"><span>{t("incidents.gravite")}</span>
          <select value={filtre.gravite} onChange={(e) => { setFiltre({ ...filtre, gravite: e.target.value }); pager.setPage(1); }} data-testid="incidents-filtre-gravite">
            <option value="">{t("incidents.toutes")}</option>{voc?.gravites.map((g) => <option key={g.code} value={g.code}>{court(g.libelle)}</option>)}
          </select></label>
        <div className="field field-actions">
          <a className="btn secondary" data-testid="incidents-csv" href={`${base}.csv${q ? `?${q}` : ""}`} download>{t("incidents.csv")}</a>
          <a className="btn secondary" data-testid="incidents-pdf" href={`${base}.pdf${q ? `?${q}` : ""}`} download>{t("incidents.pdf")}</a>
          {!tous && !formulaireOuvert && <button type="button" className="primary" data-testid="incident-nouveau" onClick={() => ouvrir(null)}>{t("incidents.consigner")}</button>}
        </div>
      </div>

      {incidents === null && <p role="status">{t("common.loading")}</p>}
      {incidents !== null && incidents.length === 0 && <p data-testid="incidents-vide">{t("incidents.vide")}{q ? t("incidents.videFiltres") : ""}.</p>}
      {incidents !== null && incidents.length > 0 && (
        <>
          <div className="table-wrap">
            <table data-testid="incidents-table">
              <caption className="sr-only">{t("incidents.caption")}</caption>
              <thead><tr>{tous && <th scope="col">{t("incidents.colEtablissement")}</th>}<th scope="col">{t("incidents.colSurvenu")}</th><th scope="col">{t("incidents.colNature")}</th><th scope="col">{t("incidents.colGravite")}</th><th scope="col">{t("incidents.colPersonnes")}</th><th scope="col">{t("incidents.colCai")}</th><th scope="col">{t("incidents.colPersonnesNotifiees")}</th><th scope="col">{t("incidents.colStatut")}</th>{!tous && <th scope="col">{t("incidents.colActions")}</th>}</tr></thead>
              <TbodyPagine items={pager.pageItems} rendre={(i) => (
                <tr key={i.id} data-testid={`incident-${i.id}`}>
                  {tous && <td>{i.nomEtablissement ?? i.tenantId}</td>}
                  <th scope="row">{i.survenuLe}</th><td>{libelle(voc?.natures, i.nature)}</td><td>{court(libelle(voc?.gravites, i.gravite))}</td><td>{i.personnesTouchees ?? t("incidents.inconnu")}</td>
                  <td data-testid={`incident-${i.id}-cai`}>{i.notifieCai ? `${t("incidents.oui")}${i.notifieCaiLe ? ` (${i.notifieCaiLe})` : ""}` : i.attendue.cai ? t("incidents.attendue") : t("incidents.non")}</td>
                  <td>{i.notifiePersonnes ? `${t("incidents.oui")}${i.notifiePersonnesLe ? ` (${i.notifiePersonnesLe})` : ""}` : i.attendue.personnes ? t("incidents.attendue") : t("incidents.non")}</td>
                  <td data-testid={`incident-${i.id}-statut`}>{i.statut === "clos" ? t("incidents.closLe", { date: i.closLe ?? "" }) : t("incidents.ouvert")}</td>
                  {!tous && <td><div className="actions-row">
                    <button type="button" className="secondary" data-testid={`incident-modifier-${i.id}`} onClick={() => ouvrir(i)}>{i.statut === "clos" ? t("incidents.notifications") : t("incidents.modifier")}</button>
                    {i.statut === "ouvert" && <button type="button" className="secondary" data-testid={`incident-clore-${i.id}`} aria-label={t("incidents.cloreAria", { date: i.survenuLe })} onClick={() => void clore(i)}>{t("incidents.clore")}</button>}
                  </div></td>}
                </tr>
              )} />
            </table>
          </div>
          <Pagination page={pager.page} pageCount={pager.pageCount} total={pager.total} pageSize={pager.pageSize} onPage={pager.setPage} testId="incidents-pager" />
        </>
      )}

      {formulaireOuvert && voc && (
        <FormulaireModal libelle="Consigner un incident" titre={enEdition ? "Modifier l'incident" : "Consigner un incident"} testId="incident" ouvert={formulaireOuvert} onFermer={fermer}>{() => (
          <form className="grid-form" data-testid="incident-formulaire" key={enEdition?.id ?? "nouveau"} onSubmit={enregistrer}>
          <fieldset className="fieldset">
            <legend>{enEdition ? (clos ? t("incidents.legendeClos", { date: enEdition.survenuLe }) : t("incidents.legendeModifier", { date: enEdition.survenuLe })) : t("incidents.legendeNouveau")}</legend>
            {!clos && (
              <>
                <label className="field"><span>{t("incidents.survenu")} <span className="req-star" aria-hidden="true">*</span></span><input name="survenuLe" type="date" required defaultValue={enEdition?.survenuLe ?? ""} data-testid="incident-survenu" /></label>
                <label className="field"><span>{t("incidents.decouvert")} <span className="req-star" aria-hidden="true">*</span></span><input name="decouvertLe" type="date" required defaultValue={enEdition?.decouvertLe ?? ""} data-testid="incident-decouvert" aria-describedby="incident-decouvert-aide" />
                  <span className="aide" id="incident-decouvert-aide">{t("incidents.decouvertAide")}</span></label>
                <label className="field"><span>{t("incidents.nature")} <span className="req-star" aria-hidden="true">*</span></span>
                  <select name="nature" required defaultValue={enEdition?.nature ?? ""} data-testid="incident-nature"><option value="">{t("incidents.choisir")}</option>{voc.natures.map((n) => <option key={n.code} value={n.code}>{n.libelle}</option>)}</select></label>
                <label className="field"><span>{t("incidents.gravite")} <span className="req-star" aria-hidden="true">*</span></span>
                  <select name="gravite" value={gravite} onChange={(e) => setGravite(e.target.value)} data-testid="incident-gravite" aria-describedby="incident-gravite-aide">{voc.gravites.map((g) => <option key={g.code} value={g.code}>{court(g.libelle)}</option>)}</select>
                  <span className="aide" id="incident-gravite-aide">{t("incidents.graviteAide")}</span></label>
                <fieldset className="field field-full" data-testid="incident-renseignements"><legend>{t("incidents.renseignements")} <span className="req-star" aria-hidden="true">*</span></legend>
                  {voc.renseignements.map((r) => <label key={r.code} className="field field-inline"><input type="checkbox" name="renseignements" value={r.code} defaultChecked={enEdition?.renseignements.includes(r.code) ?? false} data-testid={`incident-rens-${r.code}`} /> {r.libelle}</label>)}
                </fieldset>
                <label className="field"><span>{t("incidents.personnes")}</span><input name="personnesTouchees" type="number" min={0} step={1} defaultValue={enEdition?.personnesTouchees ?? ""} data-testid="incident-personnes" aria-describedby="incident-personnes-aide" />
                  <span className="aide" id="incident-personnes-aide">{t("incidents.personnesAide")}</span></label>
                <div className="field"><ChampPersonne valeur={responsable} onChoisir={setResponsable} appel={apiFetch} testId="incident-responsable" libelle={t("incidents.responsable")} /></div>
                <label className="field field-full"><span>{t("incidents.description")} <span className="req-star" aria-hidden="true">*</span></span><textarea name="description" required minLength={20} maxLength={5000} rows={3} defaultValue={enEdition?.description ?? ""} data-testid="incident-description" /></label>
                <label className="field field-full"><span>{t("incidents.mesures")} <span className="req-star" aria-hidden="true">*</span></span><textarea name="mesures" required minLength={10} maxLength={5000} rows={2} defaultValue={enEdition?.mesures ?? ""} data-testid="incident-mesures" /></label>
              </>
            )}
            <label className="field field-inline"><input type="checkbox" checked={notifieCai} onChange={(e) => setNotifieCai(e.target.checked)} data-testid="incident-notifie-cai" /> {t("incidents.notifieCai")}</label>
            <label className="field"><span>{t("incidents.notifieCaiLe")}</span><input name="notifieCaiLe" type="date" disabled={!notifieCai} defaultValue={enEdition?.notifieCaiLe ?? ""} data-testid="incident-notifie-cai-le" /></label>
            <label className="field field-inline"><input type="checkbox" checked={notifiePersonnes} onChange={(e) => setNotifiePersonnes(e.target.checked)} data-testid="incident-notifie-personnes" /> {t("incidents.notifiePersonnes")}</label>
            <label className="field"><span>{t("incidents.notifiePersonnesLe")}</span><input name="notifiePersonnesLe" type="date" disabled={!notifiePersonnes} defaultValue={enEdition?.notifiePersonnesLe ?? ""} data-testid="incident-notifie-personnes-le" /></label>
            {attendueElevee && <p className="field-full" role="status" data-testid="incident-attendue">{t("incidents.attendueElevee", { cible: `${!notifieCai ? t("incidents.aLaCai") : ""}${!notifieCai && !notifiePersonnes ? t("incidents.et") : ""}${!notifiePersonnes ? t("incidents.auxPersonnes") : ""}` })}</p>}
            <div className="field field-actions">
              <button type="submit" className="primary" data-testid="incident-enregistrer">{enEdition ? t("incidents.enregistrer") : t("incidents.consignerBtn")}</button>
              <button type="button" className="secondary" data-testid="incident-annuler" onClick={fermer}>{t("incidents.annuler")}</button>
            </div>
          </fieldset>
        </form>
        )}</FormulaireModal>
      )}
      {ConfirmHost}
    </section>
  );
}
