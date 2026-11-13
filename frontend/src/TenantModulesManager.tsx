/**
 * **Gestion des modules par établissement** — espace superadmin.
 *
 * Chaque établissement porte un ensemble de modules activés (`tenant_quotas.modules`). Cet écran
 * permet de les régler un par un, et la désactivation **ferme la route côté serveur** — elle ne
 * masque pas seulement un menu. C'est ce qui distingue un contrôle d'accès d'une décoration.
 *
 * Trois décisions d'interface, dictées par ce que la liste est devenue :
 *
 * **Le groupement par famille n'est pas cosmétique.** Une cinquantaine de cases à plat ne se lisent plus ;
 * groupées, elles répondent à « ce client a-t-il le RH ? » sans parcourir la liste entière.
 *
 * **Une case entraîne parfois une autre, et on le dit.** Cocher « Recrutement » active le dossier
 * d'employé ; décocher le dossier retire les huit sous-modules. Le serveur fait la résolution et
 * renvoie ce qu'il a changé — l'écran l'affiche au lieu de le taire.
 *
 * **Le bouton par famille existe parce que le geste réel est « donne-lui tout le RH ».** Neuf
 * clics pour un geste courant, c'est neuf occasions d'en oublier un.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import TbodyPagine from "./TbodyPagine";

interface TenantRow { id: string; name: string; planCode?: string; modules?: string[] }
interface ModuleDef {
  code: string; label: string; famille: string;
  requiert: string | null; dependants: string[];
  /** Ce que le module débloque, en mots du terrain — lu avant de cocher, jamais deviné après. */
  debloque?: string | null;
}
interface Famille { cle: string; libelle: string }

export default function TenantModulesManager({
  tenants, apiFetch, onChanged, setError, setSuccess,
}: {
  tenants: TenantRow[];
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  onChanged: () => void;
  setError: (s: string) => void;
  setSuccess: (s: string) => void;
}) {
  const [catalog, setCatalog] = useState<ModuleDef[]>([]);
  const [familles, setFamilles] = useState<Famille[]>([]);
  const [tenantId, setTenantId] = useState<string>("");
  const [busy, setBusy] = useState(false);
  // État local **optimiste** : la case reflète le clic immédiatement, avant le retour serveur.
  const [modules, setModules] = useState<string[]>([]);
  // Trente-deux cases : un filtre par libellé ou par code répond à « où est le pilotage scolaire ? »
  // sans dérouler sept familles.
  const [filtre, setFiltre] = useState("");

  useEffect(() => {
    apiFetch("/api/saas/modules").then((r) => (r.ok ? r.json() : {}))
      .then((j: { modules?: ModuleDef[]; familles?: Famille[] }) => {
        setCatalog(Array.isArray(j.modules) ? j.modules : []);
        setFamilles(Array.isArray(j.familles) ? j.familles : []);
      })
      .catch(() => { setCatalog([]); setFamilles([]); });
  }, [apiFetch]);

  useEffect(() => { if (!tenantId && tenants.length) setTenantId(tenants[0].id); }, [tenants, tenantId]);

  /**
   * **Le filtre ignore les accents.** On tape « urgences-sante » au clavier, pas
   * « Urgences-santé » : un filtre sensible aux accents répond « aucun module » sur un catalogue
   * qui en contient dix, et l'on en conclut que le module n'existe pas. C'est le même défaut que
   * celui du nom de la famille, un cran plus bas.
   *
   * Le juge est déclaré **une fois** : il décidait auparavant à deux endroits — la liste et le
   * message « aucun module » —, donc deux listes pouvaient se contredire, l'une affichant des
   * familles et l'autre affirmant qu'il n'y a rien.
   */
  const sansAccents = (t: string) => t.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
  const critere = sansAccents(filtre.trim());
  const correspond = (m: ModuleDef) => !critere
    || sansAccents(m.code).includes(critere)
    || sansAccents(m.label).includes(critere)
    || sansAccents(m.debloque ?? "").includes(critere);

  const tenant = useMemo(() => tenants.find((t) => t.id === tenantId), [tenants, tenantId]);
  const serverModules = (tenant?.modules ?? []).join(",");
  useEffect(() => { setModules(tenant?.modules ?? []); }, [tenantId, serverModules]); // eslint-disable-line react-hooks/exhaustive-deps
  const active = useMemo(() => new Set(modules), [modules]);

  /**
   * Enregistre une sélection et **rend compte de ce que le serveur a corrigé**.
   *
   * La résolution des dépendances est faite au serveur, pas ici : un appel direct à l'API doit
   * produire le même état qu'un clic. La dupliquer côté écran créerait une seconde vérité, et
   * c'est celle qu'on ne relit pas qui finit par diverger.
   */
  const enregistrer = useCallback(async (suivants: string[], quoi: string) => {
    if (!tenant) return;
    setError(""); setSuccess(""); setBusy(true);
    const precedents = modules;
    setModules(suivants);   // optimiste

    const r = await apiFetch(`/api/saas/tenants/${tenant.id}/modules`,
      { method: "PUT", body: JSON.stringify({ modules: suivants }) });
    setBusy(false);

    if (!r.ok) {
      setModules(precedents);   // on rend la case à son état d'avant
      const j = await r.json().catch(() => ({}));
      setError(j.errors?.join(", ") || "Modification impossible.");
      return;
    }
    const j = await r.json().catch(() => ({}));
    if (Array.isArray(j.modules)) setModules(j.modules);

    const nom = (code: string) => catalog.find((m) => m.code === code)?.label ?? code;
    const consequences = [
      ...(j.ajoutes ?? []).map((c: string) => `« ${nom(c)} » activé d'office (prérequis)`),
      ...(j.retires ?? []).map((c: string) => `« ${nom(c)} » désactivé — il dépendait du module retiré`),
    ];
    setSuccess(`${quoi} pour ${tenant.name}.`
      + (consequences.length ? ` ${consequences.join(" · ")}.` : ""));
    onChanged();
  }, [tenant, modules, apiFetch, catalog, onChanged, setError, setSuccess]);

  const basculer = (code: string, on: boolean) => {
    const def = catalog.find((m) => m.code === code);
    const suivants = on
      ? [...new Set([...modules, code])]
      : modules.filter((m) => m !== code);
    return enregistrer(suivants, `Module « ${def?.label ?? code} » ${on ? "activé" : "désactivé"}`);
  };

  const basculerFamille = (famille: Famille, on: boolean) => {
    const codes = catalog.filter((m) => m.famille === famille.cle).map((m) => m.code);
    const suivants = on
      ? [...new Set([...modules, ...codes])]
      : modules.filter((m) => !codes.includes(m));
    return enregistrer(suivants, `Famille « ${famille.libelle} » ${on ? "activée" : "désactivée"}`);
  };

  return (
    <div style={{ padding: 12 }} data-testid="tenant-modules-manager">
      <p className="subtitle">
        Activez ou désactivez chaque module par organisme. La désactivation <strong>masque le
        menu et ferme la route côté serveur</strong> : la donnée n'est plus accessible, même en
        connaissant l'adresse. Les modules initiaux découlent du <strong>plan d'abonnement</strong> ;
        ils restent ajustables ici.
      </p>

      <div className="form-row" style={{ alignItems: "flex-end", gap: 12, flexWrap: "wrap" }}>
        <label className="field" style={{ maxWidth: 380 }}><span>Organisme</span>
          <select data-testid="modmgr-tenant" value={tenantId} onChange={(e) => setTenantId(e.target.value)}>
            {tenants.map((t) => <option key={t.id} value={t.id}>{t.name}{t.planCode ? ` (${t.planCode})` : ""}</option>)}
          </select>
        </label>
        <label className="field" style={{ maxWidth: 320 }}><span>Filtrer les modules</span>
          <input type="search" data-testid="modmgr-filtre" value={filtre} placeholder="libellé ou code (ex. scolaire)"
                 onChange={(e) => setFiltre(e.target.value)} />
        </label>
        {tenant && (
          <p className="muted" role="status" data-testid="modmgr-resume" style={{ margin: 0 }}>
            <strong>{catalog.filter((m) => active.has(m.code)).length}</strong> module(s) activé(s) sur {catalog.length}
            {tenant.planCode ? ` — plan ${tenant.planCode}` : ""}
          </p>
        )}
      </div>

      {tenant && familles.map((f) => {
        // Le filtre garde la famille entière dès qu'un de ses modules correspond : un enfant sans son
        // socle à l'écran ne se comprend pas, et « Tout activer » doit agir sur la famille réelle.
        const siens = catalog.filter((m) => m.famille === f.cle);
        if (siens.length === 0 || !siens.some(correspond)) return null;
        const actifs = siens.filter((m) => active.has(m.code)).length;

        /**
         * Les modules d'une famille, **rangés par leur arbre de dépendances, à toute profondeur**.
         *
         * Une liste à plat oblige à lire la colonne « Dépendance » de chaque ligne pour deviner
         * l'arborescence. Rangés, les enfants suivent leur socle et l'indentation dit ce que la
         * colonne devait expliquer.
         *
         * ⚠️ Le parcours était limité à **un** niveau : socle, puis ses enfants directs, et rien
         * d'autre. Une famille en étoile (le RH : neuf modules qui exigent tous `rh`) s'affichait
         * donc entièrement, et l'omission ne se voyait pas. Mais Urgences-santé est une **chaîne**
         * — socle → règles → planification → disponibilités… —, et huit de ses dix modules
         * n'arrivaient jamais à l'écran : ils étaient au catalogue, accordables par l'API, et
         * impossibles à cocher. Le superadmin en concluait, à raison, que le module n'était pas
         * gérable. Le parcours est donc **récursif**, et un essai compte les lignes rendues.
         */
        const descendance = (code: string, niveau: number): Array<{ def: ModuleDef; niveau: number }> =>
          siens.filter((m) => m.requiert === code)
            .flatMap((enfant) => [{ def: enfant, niveau }, ...descendance(enfant.code, niveau + 1)]);
        const ordonnes = siens
          .filter((m) => !m.requiert || !siens.some((x) => x.code === m.requiert))
          .flatMap((socle) => [{ def: socle, niveau: 0 }, ...descendance(socle.code, 1)]);
        return (
          <section className="card" key={f.cle} data-testid={`modmgr-famille-${f.cle}`}
                   aria-labelledby={`modmgr-famille-titre-${f.cle}`} style={{ marginTop: 16 }}>
            <div className="entete-repliable">
              <h3 id={`modmgr-famille-titre-${f.cle}`}>
                {f.libelle} <span className="muted">— {actifs} sur {siens.length} activé(s)</span>
              </h3>
              <div className="actions-row">
                <button type="button" className="secondary" disabled={busy}
                        data-testid={`modmgr-famille-tout-${f.cle}`}
                        onClick={() => void basculerFamille(f, true)}>
                  Tout activer
                </button>
                <button type="button" className="secondary" disabled={busy}
                        data-testid={`modmgr-famille-rien-${f.cle}`}
                        onClick={() => void basculerFamille(f, false)}>
                  Tout désactiver
                </button>
              </div>
            </div>

            <div className="table-wrap" tabIndex={0} role="region"
                 aria-label={`Modules de la famille ${f.libelle} (zone défilable au clavier)`}>
              <table data-testid={`modmgr-table-${f.cle}`}>
                <caption className="sr-only">Modules de la famille {f.libelle}</caption>
                <thead>
                  <tr>
                    <th scope="col">Module</th>
                    <th scope="col">Code</th>
                    <th scope="col">Ce qu'il débloque</th>
                    <th scope="col">Dépendance</th>
                    <th scope="col" style={{ textAlign: "center" }}>Activé</th>
                  </tr>
                </thead>
                {/*
                    **Une famille s'affiche entière**, et `taille` le dit explicitement.
                    Le défaut mesuré le 2 novembre 2026 : la famille Urgences-santé est passée à
                    onze modules, la pagination par dix en a renvoyé un à la page 2, et c'était
                    précisément celui qu'on venait chercher — invisible à qui ne remarque pas le
                    pied de tableau. Une famille compte une quinzaine de modules au plus, et
                    « Tout activer » agit déjà sur la famille réelle : paginer cache ici la seule
                    chose que l'écran sert à voir, et le fait sans rien dire.
                */}
                <TbodyPagine items={ordonnes} taille={Math.max(ordonnes.length, 1)} rendre={({ def: m, niveau }) => {
                    const actifsDependants = m.dependants.filter((d) => active.has(d));
                    return (
                      <tr key={m.code} data-testid={`modmgr-row-${m.code}`}
                          className={niveau > 0 ? "module-enfant" : undefined}>
                        {/* L'indentation croît avec la profondeur ; au-delà du premier niveau, le
                            retrait de la feuille de style ne suffit plus à distinguer les rangs. */}
                        <th scope="row" style={niveau > 1 ? { paddingLeft: 28 + (niveau - 1) * 18 } : undefined}>
                          {/* L'indentation est doublée d'un mot : elle ne s'entend pas au lecteur
                              d'écran, et une hiérarchie portée par la seule mise en page n'existe
                              pas pour qui ne voit pas l'écran. */}
                          {niveau > 0 && <span className="sr-only">Dépend du module précédent : </span>}
                          {m.label}
                        </th>
                        <td><code>{m.code}</code></td>
                        <td data-testid={`modmgr-debloque-${m.code}`}>{m.debloque ?? <span className="muted">—</span>}</td>
                        <td>
                          {/* Ce qu'une case entraîne est écrit **avant** le clic, jamais découvert après.
                              Un socle peut à la fois exiger un module (scolaire → personnel) et en porter
                              d'autres : les deux se disent, sinon l'avertissement disparaît précisément
                              pour le module qui emporte le plus. */}
                          {m.requiert && (
                            <span data-testid={`modmgr-requiert-${m.code}`}>
                              exige « {catalog.find((x) => x.code === m.requiert)?.label ?? m.requiert} »
                            </span>
                          )}
                          {m.requiert && m.dependants.length > 0 && <span aria-hidden="true"> · </span>}
                          {actifsDependants.length > 0 && (
                            <strong data-testid={`modmgr-dependants-${m.code}`}>
                              le désactiver désactivera {actifsDependants.length} module(s)
                            </strong>
                          )}
                          {actifsDependants.length === 0 && m.dependants.length > 0 && (
                            <span className="muted" data-testid={`modmgr-dependants-${m.code}`}>
                              {m.dependants.length} module(s) en dépendent
                            </span>
                          )}
                          {!m.requiert && m.dependants.length === 0 && <span className="muted">—</span>}
                        </td>
                        <td style={{ textAlign: "center" }}>
                          <input type="checkbox" data-testid={`modmgr-toggle-${m.code}`}
                                 aria-label={actifsDependants.length > 0
                                   ? `Activer le module ${m.label} — le désactiver désactivera ${actifsDependants.length} module(s) qui en dépendent`
                                   : `Activer le module ${m.label}`}
                                 disabled={busy}
                                 checked={active.has(m.code)}
                                 onChange={(e) => void basculer(m.code, e.target.checked)} />
                        </td>
                      </tr>
                    );
                  }} />
              </table>
            </div>
          </section>
        );
      })}

      {catalog.length === 0 && (
        <p className="empty-cell" data-testid="modmgr-vide">Catalogue de modules indisponible.</p>
      )}
      {catalog.length > 0 && filtre.trim() && !catalog.some(correspond) && (
        <p className="empty-cell" role="status" data-testid="modmgr-aucun">Aucun module ne correspond à « {filtre.trim()} ».</p>
      )}
    </div>
  );
}
