/**
 * **Sous-menus visibles par établissement** — espace superadmin, onglet « Modules et accès ».
 *
 * Le catalogue des modules dit ce qu'un établissement **a** ; cet écran dit ce qu'il **montre**.
 * Un centre de services qui n'emploie pas la reprise de données n'a pas à lire son entrée tous
 * les jours, et ses utilisateurs n'ont pas à se demander à quoi elle sert.
 *
 * ⚠️ **Ce n'est pas un contrôle d'accès, et l'écran le dit.** Les routes d'API d'une page restent
 * ouvertes quand un de ses onglets est masqué — le plus souvent, l'onglet voisin les appelle de
 * toute façon. La frontière de sécurité reste le **module**, réglé juste au-dessus. Présenter ceci
 * comme une permission serait une promesse fausse, et c'est ainsi qu'on finit par s'y fier.
 *
 * Deux décisions d'interface, dictées par ce que la liste est :
 *
 * **Une liste de masquage, pas d'autorisation.** Une case cochée = l'entrée est visible, ce qui
 * est l'état par défaut de tout le monde. L'inverse aurait demandé de cocher soixante-neuf cases
 * pour retrouver ce qu'on avait déjà.
 *
 * **Groupée par section et par notion**, comme le menu lui-même : soixante-neuf cases à plat ne
 * se lisent pas, et surtout ne se **reconnaissent** pas — le superadmin cherche « la reprise de
 * données », qu'il a vue sous « Établissement scolaire », pas la clé `scolaire:reprise`.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { useT } from "./i18n";

interface TenantRow { id: string; name: string; modules?: string[] }
interface SectionNav { cle: string; libelle: string; module: string | null }
interface DefinitionVue {
  cle: string; section: string; notion: string | null;
  page: string; onglet: string | null; i18nKey: string | null; module: string | null;
}

export default function SousMenusManager({
  tenants, apiFetch, setError, setSuccess,
}: {
  tenants: TenantRow[];
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  setError: (s: string) => void;
  setSuccess: (s: string) => void;
}) {
  const { t } = useT();
  const [sections, setSections] = useState<SectionNav[]>([]);
  const [vues, setVues] = useState<DefinitionVue[]>([]);
  const [tenantId, setTenantId] = useState("");
  const [masquees, setMasquees] = useState<Set<string>>(new Set());
  const [serveur, setServeur] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  // Soixante-neuf entrées : un filtre répond à « où est la reprise ? » sans dérouler sept sections.
  const [filtre, setFiltre] = useState("");

  useEffect(() => {
    apiFetch("/api/saas/vues").then((r) => (r.ok ? r.json() : null))
      .then((j: { sections?: SectionNav[]; vues?: DefinitionVue[] } | null) => {
        setSections(Array.isArray(j?.sections) ? j!.sections : []);
        setVues(Array.isArray(j?.vues) ? j!.vues : []);
      })
      .catch(() => { setSections([]); setVues([]); });
  }, [apiFetch]);

  useEffect(() => { if (!tenantId && tenants.length) setTenantId(tenants[0].id); }, [tenants, tenantId]);

  const charger = useCallback(async (id: string) => {
    if (!id) return;
    const r = await apiFetch(`/api/saas/tenants/${encodeURIComponent(id)}/vues`);
    if (!r.ok) { setError(`Erreur ${r.status}`); return; }
    const j = await r.json();
    const lu = new Set<string>(Array.isArray(j.masquees) ? j.masquees : []);
    setMasquees(lu); setServeur(lu);
  }, [apiFetch, setError]);
  useEffect(() => { void charger(tenantId); }, [charger, tenantId]);

  const tenant = useMemo(() => tenants.find((x) => x.id === tenantId), [tenants, tenantId]);
  const modulesDuTenant = useMemo(() => new Set(tenant?.modules ?? []), [tenant]);

  /** Le libellé d'une vue : sa traduction, ou sa page à défaut — jamais la clé brute. */
  const libelle = (v: DefinitionVue) => {
    const traduit = v.i18nKey ? t(v.i18nKey) : t(`nav.${v.page}`);
    return traduit && !traduit.startsWith("nav.") && !traduit.startsWith("vue.") ? traduit : v.page;
  };

  const retenues = useMemo(() => {
    const q = filtre.trim().toLowerCase();
    if (!q) return vues;
    return vues.filter((v) => `${v.cle} ${libelle(v)} ${v.notion ?? ""}`.toLowerCase().includes(q));
  }, [vues, filtre]); // eslint-disable-line react-hooks/exhaustive-deps

  const modifie = useMemo(() =>
    masquees.size !== serveur.size || [...masquees].some((c) => !serveur.has(c)), [masquees, serveur]);

  const basculer = (cle: string, visible: boolean) => setMasquees((s) => {
    const c = new Set(s);
    if (visible) c.delete(cle); else c.add(cle);
    return c;
  });

  const enregistrer = async () => {
    setBusy(true);
    try {
      const r = await apiFetch(`/api/saas/tenants/${encodeURIComponent(tenantId)}/vues`, {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ masquees: [...masquees] }),
      });
      if (!r.ok) { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
      const j = await r.json();
      const lu = new Set<string>(j.masquees ?? []);
      setMasquees(lu); setServeur(lu);
      setSuccess(lu.size === 0
        ? `Tous les sous-menus sont visibles pour ${tenant?.name ?? "l'établissement"}.`
        : `${lu.size} sous-menu(s) masqué(s) pour ${tenant?.name ?? "l'établissement"}.`);
    } finally { setBusy(false); }
  };

  const toutAfficher = () => setMasquees(new Set());

  return (
    <div data-testid="sous-menus-manager">
      <p className="hint">
        Ce réglage <strong>dégarnit le menu</strong> d'un établissement sous les modules qu'il
        possède : l'entrée disparaît, et l'URL directe ne l'ouvre plus. Ce n'est pas une
        permission — les données d'une page restent servies quand un de ses onglets est masqué.
        Pour <strong>fermer</strong> un accès, coupez le module ci-dessus.
      </p>

      <div className="grid-form">
        <label className="field" htmlFor="sousmenus-tenant"><span>Établissement</span>
          <select id="sousmenus-tenant" data-testid="sousmenus-tenant" value={tenantId} onChange={(e) => setTenantId(e.target.value)}>
            {tenants.map((tn) => <option key={tn.id} value={tn.id}>{tn.name}</option>)}
          </select>
        </label>
        <label className="field" htmlFor="sousmenus-filtre"><span>Filtrer les entrées</span>
          <input id="sousmenus-filtre" data-testid="sousmenus-filtre" value={filtre}
                 onChange={(e) => setFiltre(e.target.value)} />
        </label>
        <div className="field field-actions">
          <button data-testid="sousmenus-tout" onClick={toutAfficher} disabled={masquees.size === 0}>
            Tout afficher
          </button>
          <button className="primary" data-testid="sousmenus-save" onClick={() => void enregistrer()} disabled={!modifie || busy}>
            {busy ? "Enregistrement…" : "Enregistrer les sous-menus"}
          </button>
        </div>
      </div>

      {/* Une mise à jour asynchrone s'annonce : un lecteur d'écran ne voit pas un décompte changer. */}
      <p role="status" data-testid="sousmenus-resume">
        {vues.length === 0
          ? "Catalogue des sous-menus en cours de chargement."
          : `${vues.length - masquees.size} entrée(s) visible(s) sur ${vues.length}${masquees.size ? `, ${masquees.size} masquée(s)` : ""}.`}
      </p>

      {sections.map((s) => {
        const deLaSection = retenues.filter((v) => v.section === s.cle);
        if (deLaSection.length === 0) return null;
        const notions = [...new Set(deLaSection.map((v) => v.notion))];
        return (
          <details key={s.cle} className="collapsible" data-testid={`sousmenus-section-${s.cle}`} open={!!filtre}>
            <summary>
              {s.libelle}
              {/* Jamais la couleur seule (WCAG 1.4.1) : l'état porte un mot. */}
              {s.module && !modulesDuTenant.has(s.module) && <span className="badge badge-warn"> module « {s.module} » désactivé</span>}
            </summary>
            {notions.map((n) => (
              <fieldset key={n ?? "-"} className="fieldset">
                <legend>{n ? t(`concept.${n}`) : "Entrées directes"}</legend>
                {deLaSection.filter((v) => v.notion === n).map((v) => {
                  const visible = !masquees.has(v.cle);
                  const coupe = v.module && !modulesDuTenant.has(v.module);
                  return (
                    <label className="field field-inline" key={v.cle} htmlFor={`sousmenus-${v.cle}`}>
                      <input type="checkbox" id={`sousmenus-${v.cle}`} data-testid={`sousmenus-vue-${v.cle}`}
                             checked={visible} onChange={(e) => basculer(v.cle, e.target.checked)} />
                      <span>
                        {libelle(v)}
                        {v.onglet && <> — onglet de « {v.page} »</>}
                        {coupe && <> · <span className="badge badge-warn">module « {v.module} » désactivé</span></>}
                      </span>
                    </label>
                  );
                })}
              </fieldset>
            ))}
          </details>
        );
      })}

      {vues.length > 0 && retenues.length === 0 && (
        <p className="empty-cell" data-testid="sousmenus-vide">Aucune entrée ne correspond au filtre.</p>
      )}
    </div>
  );
}
