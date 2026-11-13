/**
 * **Intégrations et webhooks sortants** (écart E5 / écran ÉC-5, règles INT-1 et WH-1…WH-6).
 *
 * Une carte par intégration (état en texte, dernière activité, erreurs récentes, écran de détail),
 * jamais un secret. Les webhooks sortants : URL https, événements cochés, secret généré par le
 * serveur et montré **une seule fois** ; journal des envois avec rejeu après confirmation (on
 * avise un tiers) ; désactiver sans modale, supprimer avec — et seulement sans envoi livré.
 *
 * Chaque carte offre, quand aucune configuration réelle ne l'interdit, de **brancher le tiers de
 * démonstration** embarqué (Entra ID, IdP SAML, Twilio, clamd, client d'API, récepteur de
 * webhooks — docs/INTEGRATIONS-MOCKS.md) et un **essai** qui fait passer un vrai message par lui ;
 * le résultat s'écrit sous la carte, en toutes lettres.
 */
import { useCallback, useEffect, useState } from "react";
import { Message, EnTeteEcran } from "./composants";
import TbodyPagine from "./TbodyPagine";
import { useAuth } from "./AuthContext";
import { Modal, useConfirm } from "./Modal";
import { Lang, useT } from "./i18n";

interface Demonstration { possible: boolean; motif: string; branchee: boolean }
interface Carte { code: string; nom: string; etat: "configure" | "simule" | "non_configure" | "demonstration"; detail: string; derniereActiviteLe: string | null; erreursRecentes: number; ecran: string; demonstration: Demonstration | null }
interface Evenement { code: string; libelle: string }
interface Webhook { id: string; url: string; description: string | null; evenements: string[]; actif: boolean; suspendu: boolean; echecsConsecutifs: number; creeLe: string; envoisEnAttente: number; dernierEnvoiLe: string | null }
interface Envoi { id: string; evenement: string; statut: "en_attente" | "livre" | "echoue"; tentatives: number; dernierCode: number | null; derniereErreur: string | null; creeLe: string; livreLe: string | null }

const CLE_ETAT: Record<Exclude<Carte["etat"], "demonstration">, string> = { configure: "integrations.configure", simule: "integrations.simule", non_configure: "integrations.nonConfigure" };
/** Les libellés de la démonstration, dans les trois langues de l'application (le catalogue commun n'est pas touché ici). */
const LIBELLES_DEMO: Record<Lang, { etat: string; brancher: string; debrancher: string; essai: string; essaiEnCours: string; reussi: string; echoue: string; impossible: string; branche: string; debranche: string }> = {
  fr: { etat: "Démonstration", brancher: "Brancher la démonstration", debrancher: "Débrancher la démonstration", essai: "Essayer", essaiEnCours: "Essai en cours…", reussi: "Essai réussi", echoue: "Essai échoué", impossible: "Démonstration impossible :", branche: "Démonstration branchée", debranche: "Démonstration débranchée" },
  en: { etat: "Demonstration", brancher: "Plug in the demonstration", debrancher: "Unplug the demonstration", essai: "Try it", essaiEnCours: "Trying…", reussi: "Trial succeeded", echoue: "Trial failed", impossible: "Demonstration unavailable:", branche: "Demonstration plugged in", debranche: "Demonstration unplugged" },
  mg: { etat: "Fampisehoana", brancher: "Ampifandraiso ny fampisehoana", debrancher: "Esory ny fampisehoana", essai: "Andramo", essaiEnCours: "Andrana…", reussi: "Nahomby ny andrana", echoue: "Tsy nahomby ny andrana", impossible: "Tsy azo atao ny fampisehoana:", branche: "Voampifandray ny fampisehoana", debranche: "Nesorina ny fampisehoana" },
};
const CLE_STATUT: Record<Envoi["statut"], string> = { en_attente: "integrations.enAttente", livre: "integrations.livre", echoue: "integrations.echoue" };
const APP_BASE = typeof window !== "undefined" && window.location.pathname.startsWith("/admin") ? "/admin" : "";
const date = (iso: string | null) => (iso ? new Date(iso).toLocaleString("fr-CA", { dateStyle: "short", timeStyle: "short" }) : "—");

/** « scolaire:reprise » → la page « scolaire » de l'application (l'onglet reste une indication). */
function lienEcran(ecran: string): { href: string; libelle: string } {
  const [page, onglet] = ecran.split(":");
  return { href: `${APP_BASE}/${page}`, libelle: onglet ? `${page} › ${onglet}` : page };
}

export default function Integrations() {
  const { apiFetch } = useAuth();
  const { t, lang } = useT();
  const L = LIBELLES_DEMO[lang];
  const etatWebhook = (w: Webhook): string => {
    if (w.suspendu) return t("integrations.suspendu", { n: String(w.echecsConsecutifs) });
    if (!w.actif) return t("integrations.desactive");
    return w.echecsConsecutifs > 0 ? t("integrations.actifEchecs", { n: String(w.echecsConsecutifs) }) : t("integrations.actif");
  };
  const { confirm, ConfirmHost } = useConfirm();
  const [cartes, setCartes] = useState<Carte[] | null>(null);
  const [evenements, setEvenements] = useState<Evenement[]>([]);
  const [webhooks, setWebhooks] = useState<Webhook[]>([]);
  const [coches, setCoches] = useState<string[]>([]);
  const [secret, setSecret] = useState<{ url: string; secret: string } | null>(null);
  const [copie, setCopie] = useState(false);
  const [ouvert, setOuvert] = useState<Webhook | null>(null);
  const [envois, setEnvois] = useState<Envoi[]>([]);
  const [erreur, setErreur] = useState("");
  const [succes, setSucces] = useState("");
  const [essais, setEssais] = useState<Record<string, { ok: boolean | null; detail: string }>>({});

  const charger = useCallback(async () => {
    const [c, w] = await Promise.all([apiFetch("/api/integrations/etat"), apiFetch("/api/integrations/webhooks")]);
    if (c.ok) setCartes(await c.json()); else { setCartes([]); setErreur(t("integrations.erreurLecture")); }
    if (w.ok) setWebhooks(await w.json());
  }, [apiFetch, t]);
  useEffect(() => { void charger(); apiFetch("/api/integrations/webhooks/evenements").then(async (r) => { if (r.ok) setEvenements(await r.json()); }).catch(() => {}); }, [charger, apiFetch]);

  const chargerEnvois = useCallback(async (w: Webhook) => {
    const r = await apiFetch(`/api/integrations/webhooks/${w.id}/envois`);
    setEnvois(r.ok ? await r.json() : []);
  }, [apiFetch]);
  useEffect(() => { if (ouvert) void chargerEnvois(ouvert); }, [ouvert, chargerEnvois]);

  async function envoyer(chemin: string, corps: unknown, quoi: string, methode = "POST"): Promise<Response | null> {
    setErreur(""); setSucces("");
    const r = await apiFetch(chemin, { method: methode, headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    if (!r.ok) { const d = await r.json().catch(() => ({})); setErreur(`${quoi} : ${(d.errors ?? [d.error ?? t("integrations.refus")]).join(" ; ")}`); return null; }
    setSucces(t("integrations.fait", { quoi }));
    await charger();
    return r;
  }

  /** Brancher ou débrancher : l'origine de l'application part avec la demande, pour que le SSO y revienne. */
  async function basculerDemo(c: Carte) {
    const action = c.demonstration?.branchee ? "debrancher" : "brancher";
    const urlApplication = `${window.location.origin}${APP_BASE}`;
    await envoyer(`/api/integrations/demonstration/${c.code}/${action}`, { urlApplication }, action === "brancher" ? L.branche : L.debranche);
  }

  async function essayerDemo(c: Carte) {
    setEssais((e) => ({ ...e, [c.code]: { ok: null, detail: L.essaiEnCours } }));
    const r = await apiFetch(`/api/integrations/demonstration/${c.code}/essai`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
    const d = await r.json().catch(() => ({}));
    setEssais((e) => ({ ...e, [c.code]: r.ok ? { ok: !!d.ok, detail: String(d.detail ?? "") } : { ok: false, detail: (d.errors ?? [t("integrations.refus")]).join(" ; ") } }));
    await charger();
  }

  async function creer(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = e.currentTarget; const d = new FormData(f);
    const url = String(d.get("url") ?? "").trim();
    const r = await envoyer("/api/integrations/webhooks", { url, evenements: coches, description: String(d.get("description") ?? "") || null }, t("integrations.creation"));
    if (!r) return;
    const w = await r.json();
    // Le secret n'est jamais renvoyé ensuite (règle WH-1) : la modale est la seule occasion de le copier.
    setSecret({ url: w.url, secret: w.secret }); setCopie(false);
    f.reset(); setCoches([]);
  }

  async function copier() {
    try { await navigator.clipboard.writeText(secret?.secret ?? ""); setCopie(true); } catch { setCopie(false); }
  }

  return (
    <div data-testid="integrations-page">
      <EnTeteEcran icone="lien" titre={<>{t("integrations.title")}</>} testId="integrations-title" description={<>{t("integrations.help")}</>} />
      {erreur && <Message variante="erreur" testId="integrations-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="integrations-succes">{succes}</Message>}

      <section className="card" aria-labelledby="cartes-titre">
        <h3 id="cartes-titre">{t("integrations.cartes")}</h3>
        {cartes === null && <p role="status">{t("common.loading")}</p>}
        {cartes !== null && (
          <ul className="kpi-grid" style={{ listStyle: "none", padding: 0, margin: 0 }} data-testid="integrations-cartes">
            {cartes.map((c) => {
              const lien = lienEcran(c.ecran);
              return (
                <li key={c.code} data-testid={`carte-${c.code}`}>
                  <strong>{c.nom}</strong>
                  <span data-testid={`carte-${c.code}-etat`}>{t("integrations.etat")} <span className={`badge ${c.etat === "configure" ? "active" : c.etat === "demonstration" ? "planned" : c.etat === "simule" ? "on_leave" : "terminated"}`}>{c.etat === "demonstration" ? L.etat : t(CLE_ETAT[c.etat])}</span></span>
                  <span className="muted">{c.detail}</span>
                  <span className="muted">{t("integrations.derniereActivite", { date: date(c.derniereActiviteLe), n: String(c.erreursRecentes) })}</span>
                  <a href={lien.href} data-testid={`carte-${c.code}-lien`}>{t("integrations.ouvrir", { ecran: lien.libelle })}</a>
                  {c.demonstration && (
                    <span style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
                      {c.demonstration.possible ? (
                        <>
                          <button type="button" className="secondary" data-testid={`carte-${c.code}-demo`} aria-pressed={c.demonstration.branchee} onClick={() => basculerDemo(c)}>
                            {c.demonstration.branchee ? L.debrancher : L.brancher}
                          </button>
                          {c.demonstration.branchee && <button type="button" className="secondary" data-testid={`carte-${c.code}-essai`} onClick={() => essayerDemo(c)}>{L.essai}</button>}
                        </>
                      ) : <span className="muted" data-testid={`carte-${c.code}-demo-impossible`}>{L.impossible} {c.demonstration.motif}</span>}
                    </span>
                  )}
                  {/* La zone vivante existe avant l'essai : un lecteur d'écran n'annonce que ce qui change dans une région déjà présente (4.1.3). */}
                  {c.demonstration?.possible && (
                    <span role="status" aria-live="polite" data-testid={`carte-${c.code}-essai-resultat`}>
                      {essais[c.code] ? `${essais[c.code].ok === null ? "" : `${essais[c.code].ok ? L.reussi : L.echoue} — `}${essais[c.code].detail}` : ""}
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="card" aria-labelledby="webhooks-titre">
        <h3 id="webhooks-titre">{t("integrations.webhooks")}</h3>
        <p className="section-help">{t("integrations.webhooksHelp")}</p>
        <form className="grid-form" data-testid="webhook-formulaire" onSubmit={creer}>
          <fieldset className="fieldset">
            <legend>{t("integrations.nouveau")}</legend>
            <label className="field field-full"><span>{t("integrations.url")} <span className="req-star" aria-hidden="true">*</span></span>
              <input name="url" type="url" required inputMode="url" autoComplete="off" data-testid="webhook-url" aria-describedby="webhook-url-aide" placeholder="https://paie.exemple.qc.ca/hooks" />
              <span className="aide" id="webhook-url-aide">{t("integrations.urlAide")}</span></label>
            <label className="field field-full"><span>{t("integrations.description")}</span><input name="description" maxLength={200} data-testid="webhook-description" placeholder={t("integrations.descriptionEx")} /></label>
            <fieldset className="field field-full" data-testid="webhook-evenements">
              <legend>{t("integrations.evenements")} <span className="req-star" aria-hidden="true">*</span></legend>
              {evenements.length === 0 && <p className="aide">{t("integrations.aucunEvenement")}</p>}
              {evenements.map((ev) => (
                <label key={ev.code} className="field field-inline">
                  <input type="checkbox" name="evenements" value={ev.code} checked={coches.includes(ev.code)} data-testid={`webhook-ev-${ev.code}`}
                    onChange={(e) => setCoches((c) => (e.target.checked ? [...c, ev.code] : c.filter((x) => x !== ev.code)))} />
                  {ev.libelle} <code>{ev.code}</code>
                </label>
              ))}
            </fieldset>
            <div className="field field-actions"><button type="submit" className="primary" data-testid="webhook-creer" disabled={coches.length === 0}>{t("integrations.creer")}</button></div>
          </fieldset>
        </form>

        <div className="table-wrap">
          <table data-testid="webhooks-table">
            <caption className="sr-only">{t("integrations.webhooks")}</caption>
            <thead><tr><th scope="col">{t("integrations.colUrl")}</th><th scope="col">{t("integrations.colEvenements")}</th><th scope="col">{t("integrations.colEtat")}</th><th scope="col">{t("integrations.colAttente")}</th><th scope="col">{t("integrations.colDernier")}</th><th scope="col">{t("integrations.colActions")}</th></tr></thead>
            <TbodyPagine items={webhooks} vide={<tr><td colSpan={6} className="muted" data-testid="webhooks-vide">{t("integrations.vide")}</td></tr>} rendre={(w) => (
                <tr key={w.id} data-testid={`webhook-${w.id}`}>
                  <th scope="row">{w.url}{w.description && <span className="muted"> — {w.description}</span>}</th>
                  <td>{w.evenements.join(", ")}</td>
                  <td data-testid={`webhook-${w.id}-etat`}>{etatWebhook(w)}</td>
                  <td>{w.envoisEnAttente}</td>
                  <td>{date(w.dernierEnvoiLe)}</td>
                  <td><div className="actions-row">
                    <button type="button" className="secondary" data-testid={`webhook-envois-${w.id}`} aria-pressed={ouvert?.id === w.id} onClick={() => setOuvert(ouvert?.id === w.id ? null : w)}>{t("integrations.envois")}</button>
                    <button type="button" className="secondary" data-testid={`webhook-basculer-${w.id}`} onClick={() => envoyer(`/api/integrations/webhooks/${w.id}/activite`, { actif: !(w.actif && !w.suspendu) }, w.actif && !w.suspendu ? t("integrations.desactivation") : t("integrations.reactivation"))}>
                      {w.actif && !w.suspendu ? t("integrations.desactiver") : t("integrations.reactiver")}
                    </button>
                    <button type="button" className="secondary" data-testid={`webhook-supprimer-${w.id}`} aria-label={t("integrations.supprimerAria", { url: w.url })} onClick={async () => {
                      if (!await confirm({ title: t("integrations.supprimerTitre"), message: t("integrations.supprimerMessage", { url: w.url }), confirmLabel: t("integrations.supprimer"), variant: "danger", testId: "webhook-supprimer-modal" })) return;
                      if (await envoyer(`/api/integrations/webhooks/${w.id}`, {}, t("integrations.suppression"), "DELETE") && ouvert?.id === w.id) setOuvert(null);
                    }}>{t("integrations.supprimer")}</button>
                  </div></td>
                </tr>
              )} />
          </table>
        </div>

        {ouvert && (
          <section aria-labelledby="envois-titre" data-testid="webhook-envois">
            <h4 id="envois-titre">{t("integrations.envoisTitre", { url: ouvert.url })}</h4>
            <div className="table-wrap">
              <table data-testid="envois-table">
                <caption className="sr-only">{t("integrations.envoisTitre", { url: ouvert.url })}</caption>
                <thead><tr><th scope="col">{t("integrations.colEvenement")}</th><th scope="col">{t("integrations.colCree")}</th><th scope="col">{t("integrations.colStatut")}</th><th scope="col">{t("integrations.colTentatives")}</th><th scope="col">{t("integrations.colCode")}</th><th scope="col">{t("integrations.colErreur")}</th><th scope="col">{t("integrations.colActions")}</th></tr></thead>
                <TbodyPagine items={envois} vide={<tr><td colSpan={7} className="muted" data-testid="envois-vide">{t("integrations.envoisVide")}</td></tr>} rendre={(e) => (
                    <tr key={e.id} data-testid={`envoi-${e.id}`}>
                      <th scope="row">{e.evenement}</th><td>{date(e.creeLe)}</td>
                      <td data-testid={`envoi-${e.id}-statut`}>{t(CLE_STATUT[e.statut])}{e.statut === "livre" && t("integrations.livreLe", { date: date(e.livreLe) })}</td>
                      <td>{e.tentatives}</td><td>{e.dernierCode ?? "—"}</td><td>{e.derniereErreur ?? "—"}</td>
                      <td>
                        <button type="button" className="secondary" data-testid={`envoi-rejouer-${e.id}`} aria-label={t("integrations.rejouerAria", { evenement: e.evenement, date: date(e.creeLe) })} onClick={async () => {
                          if (!await confirm({ title: t("integrations.rejouerTitre"), message: t("integrations.rejouerMessage", { evenement: e.evenement, url: ouvert.url }), confirmLabel: t("integrations.rejouer"), testId: "envoi-rejouer-modal" })) return;
                          if (await envoyer(`/api/integrations/webhooks/envois/${e.id}/rejouer`, {}, t("integrations.rejeu"))) await chargerEnvois(ouvert);
                        }}>{t("integrations.rejouer")}</button>
                      </td>
                    </tr>
                  )} />
              </table>
            </div>
          </section>
        )}
      </section>

      <Modal open={secret !== null} onClose={() => setSecret(null)} title={t("integrations.secretTitre")} testId="webhook-secret-modal">
        <p>{t("integrations.secretTexte", { url: secret?.url ?? "" })}</p>
        <p><code data-testid="webhook-secret">{secret?.secret}</code></p>
        <p role="status" aria-live="polite">{copie ? t("integrations.secretCopie") : ""}</p>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button type="button" className="secondary" data-testid="webhook-secret-copier" onClick={copier}>{t("integrations.copier")}</button>
          <button type="button" className="primary" data-testid="webhook-secret-fermer" onClick={() => setSecret(null)}>{t("integrations.secretFermer")}</button>
        </div>
      </Modal>
      {ConfirmHost}
    </div>
  );
}
