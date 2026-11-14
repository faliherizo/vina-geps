/**
 * Page **Superadmin** — administration de tout le système multi-tenant.
 *
 * Un **tenant = un établissement** (nom, adresse, ville…). Le superadmin crée/modifie les
 * établissements et crée les **admins par tenant**. Réservée au rôle `superadmin`.
 */
import { useCallback, useEffect, useState } from "react";
import { Message, EnTeteEcran, Onglets, Icone } from "./composants";
import type { NomIcone } from "./composants/Icone";
import DestructionDonnees from "./DestructionDonnees";
import { useAuth } from "./AuthContext";
import { Modal, useConfirm } from "./Modal";
import TenantModulesManager from "./TenantModulesManager";
import SousMenusManager from "./SousMenusManager";
import ConsommationAvis from "./ConsommationAvis";
import SecurityQuestionsManager from "./SecurityQuestionsManager";
import SauvegardesPanel from "./SauvegardesPanel";
import SoutienExploitation from "./SoutienExploitation";
import SecuritePanel from "./SecuritePanel";
import IncidentsConfidentialite from "./IncidentsConfidentialite";
import AuditIntegrite from "./AuditIntegrite";
import Pagination, { usePagination } from "./Pagination";
import { useT } from "./i18n";

interface TenantAdmin { id: string; userId: string; username: string; active?: boolean; }
interface CountryOption { code: string; name: string; flag: string; currency: string; salesTaxLabel: string; }
interface TenantRow {
  id: string; name: string; type: string; country: string; region: string | null;
  planCode: string; status: string;
  address: string | null; city: string | null; postalCode: string | null; phone: string | null; email: string | null;
  slug: string | null; modules: string[]; admins: TenantAdmin[];
}

async function handle(r: Response, onError: (s: string) => void): Promise<any | null> {
  if (r.ok) return r.status === 204 ? null : r.json().catch(() => null);
  const j = await r.json().catch(() => ({}));
  onError(j.errors?.join(", ") || `Erreur ${r.status}`);
  return null;
}

type SuperadminTab = "tenants" | "modules" | "consommation" | "compliance" | "security" | "soutien" | "exploitation";

interface ResumeSoutien { ouverts: number; nouveaux: number; enRetard: number }

/**
 * Les familles d'onglets de l'espace superadmin — trois métiers, et non neuf sujets.
 *
 * L'ordre est celui du travail : on part d'un client, on regarde ce qu'il coûte, puis on
 * s'occupe de la plateforme. Un onglet ajouté sans famille n'apparaîtrait nulle part ; l'audit
 * géométrique compare la liste rendue à celle qu'il mesure, et le dirait.
 */
const FAMILLES_SUPERADMIN: Array<{
  cle: string; libelle: string;
  onglets: (resume: ResumeSoutien | null) => Array<{ id: SuperadminTab; libelle: string; testId: string; icone: NomIcone; compteur?: number; compteurTestId?: string; compteurLibelle?: string }>;
}> = [
  { cle: "clients", libelle: "Clients", onglets: () => [
    { id: "tenants", libelle: "Organismes", testId: "sa-tab-tenants", icone: "ecole" },
    { id: "modules", libelle: "Modules et accès", testId: "sa-tab-modules", icone: "reglages" },
  ] },
  { cle: "offre", libelle: "Consommation", onglets: () => [
    { id: "consommation", libelle: "Consommation d'avis", testId: "sa-tab-consommation", icone: "envoyer" },
  ] },
  { cle: "plateforme", libelle: "Plateforme", onglets: (resume) => [
    { id: "compliance", libelle: "Conformité", testId: "sa-tab-compliance", icone: "graphique" },
    { id: "security", libelle: "Questions de sécurité", testId: "sa-tab-security", icone: "cadenas" },
    { id: "soutien", libelle: "Soutien", testId: "sa-tab-soutien", icone: "aide", compteur: resume?.ouverts ?? 0, compteurTestId: "sa-soutien-badge",
      compteurLibelle: resume ? `${resume.ouverts} billet(s) ouvert(s), dont ${resume.nouveaux} nouveau(x) et ${resume.enRetard} en retard` : undefined },
    { id: "exploitation", libelle: "Exploitation", testId: "sa-tab-exploitation", icone: "dossier" },
  ] },
];

export default function Superadmin() {
  const { apiFetch } = useAuth();
  const { t } = useT();
  // Confirmations en modal : une fenêtre native n'est ni stylable, ni testable.
  const { confirm, ConfirmHost } = useConfirm();
  const [tenants, setTenants] = useState<TenantRow[]>([]);
  const [countries, setCountries] = useState<CountryOption[]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  // Regroupement des fonctionnalités superadmin en menus.
  const [tab, setTab] = useState<SuperadminTab>("tenants");
  // Le résumé du soutien — billets ouverts, nouveaux, en retard — alimente le badge de l'onglet et
  // se rafraîchit toutes les minutes : un billet urgent ne doit pas attendre qu'on ouvre l'onglet.
  const [resume, setResume] = useState<{ ouverts: number; nouveaux: number; enRetard: number } | null>(null);
  useEffect(() => {
    let annule = false;
    const lire = () => apiFetch("/api/soutien/tickets/resume").then((r) => (r.ok ? r.json() : null)).then((j) => { if (!annule && j && typeof j.ouverts === "number") setResume(j); }).catch(() => undefined);
    void lire();
    const minuterie = setInterval(() => void lire(), 60_000);
    return () => { annule = true; clearInterval(minuterie); };
  }, [apiFetch]);
  // Message de maintenance (Global-19).
  const [maint, setMaint] = useState({ message: "", level: "info", active: false });
  const [plans, setPlans] = useState<Array<{ code: string; maxTeachers: number; maxContractsPerYear: number; modules: string[] }>>([]);
  useEffect(() => { apiFetch("/api/saas/plans").then((r) => (r.ok ? r.json() : null)).then((d) => { if (d && Array.isArray(d.plans)) setPlans(d.plans); }).catch(() => {}); }, [apiFetch]);
  useEffect(() => { apiFetch("/api/saas/maintenance").then((r) => (r.ok ? r.json() : null)).then((d) => { if (d) setMaint({ message: d.message, level: d.level, active: d.active }); }).catch(() => {}); }, [apiFetch]);
  async function saveMaintenance() {
    const r = await apiFetch("/api/saas/maintenance", { method: "PUT", body: JSON.stringify(maint) });
    if (r.ok) setSuccess("Message de maintenance enregistré.");
    else { const j = await r.json().catch(() => ({})); setError((j.errors || ["Erreur"]).join(", ")); }
  }
  // Portée du catalogue de questions secrètes géré par le superadmin : "global" ou un tenantId.
  const [secScope, setSecScope] = useState<string>("global");
  const [form, setForm] = useState({
    name: "", type: "public_institution", country: "CA", region: "QC", planCode: "professional",
    address: "", city: "", postalCode: "", phone: "", email: "", slug: "", adminUsername: "", adminPassword: "",
  });
  const [editId, setEditId] = useState<string | null>(null);
  const [edit, setEdit] = useState({ name: "", address: "", city: "", postalCode: "", phone: "", email: "", slug: "" });
  const [newAdmin, setNewAdmin] = useState<{ tenantId: string; username: string; password: string }>({ tenantId: "", username: "", password: "" });
  /**
   * Réinitialisation de mot de passe en cours.
   *
   * Un seul état porte les deux temps : la demande de confirmation, puis le mot de passe rendu.
   * `temporaryPassword` absent = on attend encore la confirmation.
   */
  const [resetCible, setResetCible] = useState<{ tenant: TenantRow; admin: TenantAdmin; temporaryPassword?: string } | null>(null);
  const tenantsPager = usePagination(tenants, 10);

  const load = useCallback(async () => {
    const r = await apiFetch("/api/superadmin/tenants");
    if (r.ok) setTenants(await r.json());
    // Catalogue des pays hôtes pris en charge (pays + monnaie du nouvel établissement).
    const co = await apiFetch("/api/countries");
    if (co.ok) { const j = await co.json(); setCountries(j.countries || []); }
  }, [apiFetch]);
  useEffect(() => { load(); }, [load]);

  // Un succès s'efface seul ; **une erreur reste** jusqu'au prochain geste — un message qui
  // disparaît en quatre secondes pendant qu'on relit le formulaire est un message qu'on n'a pas lu.
  useEffect(() => {
    if (!success) return;
    const h = setTimeout(() => setSuccess(""), 6000);
    return () => clearTimeout(h);
  }, [success]);

  // Erreurs du formulaire de création : chaque motif sur sa ligne, **à côté du formulaire**.
  const [creationErreurs, setCreationErreurs] = useState<string[]>([]);
  const [creationEnCours, setCreationEnCours] = useState(false);

  /** Ce que l'écran peut vérifier avant d'appeler le serveur — le serveur revérifie tout. */
  function validerCreation(f: typeof form): string[] {
    const erreurs: string[] = [];
    if (!f.name.trim()) erreurs.push("Le nom de l'organisme est requis.");
    if (f.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) erreurs.push(`Courriel invalide : « ${f.email.trim()} ».`);
    if (f.slug.trim() && !/^[a-z0-9]+(-[a-z0-9]+)*$/.test(f.slug.trim().toLowerCase())) erreurs.push("Le slug d'URL n'admet que des lettres, des chiffres et des tirets (ex. : ecole-a).");
    if (f.adminPassword && f.adminPassword.length < 12) erreurs.push("Le mot de passe de l'administrateur doit compter au moins 12 caractères (ou laissez vide pour un mot de passe provisoire).");
    return erreurs;
  }

  async function createTenant() {
    setError(""); setSuccess("");
    const locales = validerCreation(form);
    if (locales.length) { setCreationErreurs(locales); return; }
    setCreationErreurs([]);
    setCreationEnCours(true);   // un double clic ne crée pas deux organismes
    try {
      const r = await apiFetch("/api/superadmin/tenants", { method: "POST", body: JSON.stringify(form) });
      const j = await r.json().catch(() => ({} as any));
      if (!r.ok) {
        const motifs: string[] = Array.isArray(j.errors) && j.errors.length ? j.errors : [`Création impossible (erreur ${r.status}).`];
        setCreationErreurs(motifs.map((m) => (r.status === 409 ? `Conflit : ${m}` : m)));
        return;
      }
      // Si un mot de passe provisoire a été généré, on l'affiche (à communiquer à l'admin) — il
      // devra le changer + configurer MFA + questions secrètes au premier accès.
      setSuccess(j.temporaryPassword
        ? `Organisme créé. Mot de passe provisoire de « ${j.admin?.username} » : ${j.temporaryPassword} (à communiquer ; changement + MFA imposés au 1er accès).`
        : "Organisme créé.");
      setForm({ ...form, name: "", address: "", city: "", postalCode: "", phone: "", email: "", slug: "", adminUsername: "", adminPassword: "" });
      load();
    } catch (e) {
      setCreationErreurs([`Serveur injoignable (${e instanceof Error ? e.message : "erreur réseau"}). Rien n'a été créé.`]);
    } finally { setCreationEnCours(false); }
  }

  function startEdit(t: TenantRow) {
    setEditId(t.id);
    setEdit({ name: t.name, address: t.address ?? "", city: t.city ?? "", postalCode: t.postalCode ?? "", phone: t.phone ?? "", email: t.email ?? "", slug: t.slug ?? "" });
  }
  async function saveEdit() {
    if (!editId) return;
    const r = await apiFetch(`/api/superadmin/tenants/${editId}`, { method: "PATCH", body: JSON.stringify(edit) });
    if (await handle(r, setError)) { setSuccess("Organisme mis à jour."); setEditId(null); load(); }
  }
  // Correction du pays/devise — possible uniquement AVANT toute transaction (garde-fou serveur).
  async function correctCountry(tn: TenantRow) {
    // On demande au serveur si le verrou est posé **avant** d'ouvrir la saisie. Sans cela, on
    // fait remplir un formulaire dont on sait déjà qu'il sera refusé — et l'utilisateur croit
    // à une panne plutôt qu'à une règle.
    const verrou = await apiFetch(`/api/saas/tenants/${tn.id}/country-lock`)
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null);
    if (verrou?.locked) {
      setError(
        `Le pays de « ${tn.name} » est figé à ${verrou.country} : des transactions financières existent déjà. ` +
        `Le changer déplacerait la juridiction et la devise fonctionnelle d'écritures déjà comptabilisées.`,
      );
      return;
    }
    const code = prompt(
      `Corriger le pays de « ${tn.name} » — code ISO2 (CA, FR, MG, US).\n` +
      `⚠️ Possible uniquement tant qu'aucune transaction financière n'existe : le pays fixe la juridiction et la devise fonctionnelle.`,
      tn.country,
    );
    if (!code) return;
    const r = await apiFetch(`/api/saas/tenants/${tn.id}/country`, { method: "PATCH", body: JSON.stringify({ country: code.toUpperCase() }) });
    if (await handle(r, setError)) { setSuccess("Pays/devise corrigé."); load(); }
  }
  async function lifecycle(id: string, op: "activate" | "suspend" | "terminate") {
    // Résilier est définitif : l'établissement ne se rouvre pas, ses données restent exportables.
    if (op === "terminate" && !await confirm({
      title: "Résilier l'organisme",
      message: <>Résilier <strong>{tenants.find((x) => x.id === id)?.name ?? id}</strong> ? Ses comptes ne pourront plus se connecter ;
        ses données sont conservées pour l'export intégral. Cette action ne se défait pas.</>,
      confirmLabel: "Résilier", variant: "danger", testId: "sa-terminate-modal",
    })) return;
    const r = await apiFetch(`/api/superadmin/tenants/${id}/${op}`, { method: "POST", body: JSON.stringify({}) });
    if (await handle(r, setError)) { setSuccess(`Action ${op}.`); load(); }
  }
  /** L'usage du mois (professeurs, contrats, calculs de paie) contre le quota du forfait : lu à la demande, jamais deviné. */
  const [usage, setUsage] = useState<Record<string, { teachersCount: number; contractsCount: number; payCalcsCount: number }>>({});
  async function voirUsage(id: string) {
    const r = await apiFetch(`/api/saas/tenants/${id}/usage`);
    const u = await handle(r, setError);
    if (u) setUsage((prev) => ({ ...prev, [id]: u }));
  }
  // Récupération de compte : réinitialise le 2ᵉ facteur d'un admin d'établissement qui a oublié
  // ses questions secrètes / TOTP. Il devra le reconfigurer au prochain accès (comme au 1er login).
  async function resetAdminSecurity(tn: TenantRow, admin: TenantAdmin) {
    const uid = admin.userId || admin.id;
    if (!await confirm({
      title: "Réinitialiser le second facteur",
      message: <>Réinitialiser la sécurité (2FA / questions secrètes) de <strong>{admin.username}</strong> ({tn.name}) ?
        Il pourra se reconnecter avec son mot de passe et devra reconfigurer sa sécurité.</>,
      confirmLabel: "Réinitialiser", testId: "sa-reset-security-modal",
    })) return;
    const r = await apiFetch(`/api/auth/users/${uid}/reset-security`, { method: "POST", body: JSON.stringify({}) });
    if (await handle(r, setError)) { setSuccess(`Sécurité réinitialisée pour « ${admin.username} » — reconfiguration imposée au prochain accès.`); load(); }
  }

  /**
   * Réinitialise le mot de passe d'un administrateur d'établissement.
   *
   * Sans ce recours, un administrateur ayant perdu son mot de passe bloque tout son
   * établissement : lui seul y administre les comptes. Le mot de passe rendu est provisoire —
   * il devra en choisir un au prochain accès.
   */
  /**
   * Confirme puis exécute la réinitialisation, en deux temps dans le même modal.
   *
   * Les fenêtres natives du navigateur ne conviennent pas ici : le mot de passe provisoire doit
   * pouvoir être **sélectionné et copié**, ce qu'un `alert()` interdit, et il ne sera plus
   * jamais affiché — il n'est stocké nulle part en clair.
   */
  async function confirmerResetMotDePasse() {
    if (!resetCible) return;
    const { tenant, admin } = resetCible;
    const uid = admin.userId || admin.id;
    const r = await apiFetch(`/api/superadmin/tenants/${tenant.id}/admins/${uid}/reset-password`, {
      method: "POST", body: JSON.stringify({}),
    });
    const out = await handle(r, setError);
    if (out) {
      setResetCible({ ...resetCible, temporaryPassword: out.temporaryPassword });
      setSuccess(`Mot de passe réinitialisé pour « ${out.username} ».`);
      load();
    } else {
      setResetCible(null);
    }
  }

  /** Désactive ou réactive un administrateur. Le dernier actif est protégé côté serveur. */
  async function toggleAdminActive(tn: TenantRow, admin: TenantAdmin) {
    const uid = admin.userId || admin.id;
    const desactive = admin.active === false;
    const op = desactive ? "activate" : "deactivate";
    if (!desactive && !await confirm({
      title: "Désactiver l'administrateur",
      message: <>Désactiver <strong>{admin.username}</strong> ({tn.name}) ? Il ne pourra plus se connecter ;
        ses traces — approbations, écritures, paies — sont conservées.</>,
      confirmLabel: "Désactiver", variant: "danger", testId: "sa-deactivate-modal",
    })) return;
    const r = await apiFetch(`/api/superadmin/tenants/${tn.id}/admins/${uid}/${op}`, {
      method: "POST", body: JSON.stringify({}),
    });
    if (await handle(r, setError)) {
      setSuccess(desactive ? `« ${admin.username} » réactivé.` : `« ${admin.username} » désactivé.`);
      load();
    }
  }

  async function addAdmin() {
    if (!newAdmin.tenantId) { setError("Choisissez un organisme."); return; }
    const r = await apiFetch(`/api/superadmin/tenants/${newAdmin.tenantId}/admins`, {
      method: "POST", body: JSON.stringify({ username: newAdmin.username, password: newAdmin.password }),
    });
    if (await handle(r, setError)) { setSuccess("Admin créé."); setNewAdmin({ tenantId: "", username: "", password: "" }); load(); }
  }

  return (
    <div className="page" data-testid="superadmin-page">
      <EnTeteEcran icone="bouclier" titre={<>{t("nav.superadmin")}</>} description={<>Administration globale : un <strong>tenant = un organisme</strong> (nom, adresse, ville…). Créez les organismes et leurs administrateurs.</>} />

      {/* Accès à la documentation API (OpenAPI 3.0) + banc d'essai Swagger — utile au superadmin
          pour explorer/tester l'API ouverte (AO-5338 NF-03) sans changer de rôle. */}
      <div className="actions-row" data-testid="sa-api-links" style={{ marginBottom: 12 }}>
        <a className="btn secondary" data-testid="sa-swagger" href="/api/integrations/swagger" target="_blank" rel="noreferrer" style={{ textDecoration: "none" }}>Swagger UI (test interactif de l'API)</a>
        <a className="btn secondary" data-testid="sa-openapi" href="/api/integrations/openapi.json" target="_blank" rel="noreferrer" style={{ textDecoration: "none" }}>openapi.json</a>
      </div>

      {error && <Message variante="erreur" testId="sa-error">{error}</Message>}
      {success && <Message variante="succes" testId="sa-success">{success}</Message>}

      {/*
        **Neuf onglets à plat ne se lisent plus** — et surtout ne se reconnaissent plus : ils
        mêlent trois métiers (les clients, l'argent, l'exploitation) sur une seule ligne, qui se
        replie en quatre rangées sur un téléphone. Le produit a déjà tranché cette question une
        fois, pour le dossier d'employé : des barres d'onglets **groupées par famille**, chacune
        annoncée à son lecteur d'écran.
        On reprend ce patron plutôt qu'un menu latéral : le gain de lecture est le même, et
        **aucun `data-testid` ne bouge** — huit d'entre eux sont gelés à l'inventaire de la
        refonte visuelle, et douze fichiers d'essai en dépendent. Un menu aurait coûté cette
        réécriture sans rien apporter de plus à qui lit l'écran.
      */}
      <div className="dossier-familles" data-testid="sa-tabs">
        {FAMILLES_SUPERADMIN.map((f) => (
          <div key={f.cle} className="dossier-famille" data-testid={`sa-famille-${f.cle}`}>
            <span className="dossier-famille-libelle" aria-hidden="true">{f.libelle}</span>
            {/* Sans `prefixeIds` : les panneaux, rendus plus bas, ne portent pas d'identifiant de panneau ;
                promettre `sa-<famille>-tabpanel-<onglet>` faisait viser à `aria-controls` un id absent (axe
                aria-valid-attr-value, relevé le 12 nov. 2026 sur l'onglet Soutien). */}
            <Onglets secondaires libelle={`${f.libelle} — espace superadmin`} actif={tab} onChange={setTab}
                     onglets={f.onglets(resume)} />
          </div>
        ))}
      </div>

      {tab === "tenants" && (<>
      <details className="collapsible" open>
        <summary><Icone nom="plus" /> Nouvel organisme (tenant)</summary>
        {creationErreurs.length > 0 && (
          <div className="banner banner-error" role="alert" data-testid="sa-create-errors">
            <strong>L'organisme n'a pas été créé :</strong>
            <ul style={{ margin: "4px 0 0 18px" }}>{creationErreurs.map((m, i) => <li key={i}>{m}</li>)}</ul>
          </div>
        )}
        <div className="grid-form">
          <fieldset className="fieldset">
          <legend>Identité</legend>
          <label className="field"><span>Nom <span className="req-star" aria-hidden="true">*</span></span><input data-testid="sa-name" required aria-required="true" aria-invalid={creationErreurs.some((m) => /nom/i.test(m)) || undefined} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
          <label className="field"><span>Type</span>
            <select data-testid="sa-type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              {["government", "public_institution", "private_institution", "corporate", "country"].map((x) => <option key={x} value={x}>{x}</option>)}
            </select></label>
          <label className="field"><span>Pays & devise (définitif) <span className="req-star" aria-hidden="true">*</span></span>
            <select data-testid="sa-country" required aria-required="true" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })}>
              {countries.length === 0 && <option value={form.country}>{form.country}</option>}
              {countries.map((c) => <option key={c.code} value={c.code}>{c.flag} {c.name} — {c.currency}</option>)}
            </select>
            {(() => { const c = countries.find((x) => x.code === form.country); return c ? (
              <small className="muted" data-testid="sa-country-info" style={{ fontSize: 11, display: "block" }}>
                Monnaie : <strong>{c.currency}</strong> · Taxes : {c.salesTaxLabel} — ⚠️ <strong>définitif</strong> dès la 1ʳᵉ transaction (juridiction &amp; devise fonctionnelle).
              </small>) : null; })()}
          </label>
          <label className="field"><span>Région</span><input data-testid="sa-region" value={form.region} onChange={(e) => setForm({ ...form, region: e.target.value })} /></label>
          <label className="field"><span>Plan</span>
            <select data-testid="sa-plan" value={form.planCode} onChange={(e) => setForm({ ...form, planCode: e.target.value })} aria-describedby="sa-plan-aide">
              {(plans.length ? plans.map((p) => p.code) : ["starter", "professional", "enterprise", "sovereign"]).map((x) => <option key={x} value={x}>{x}</option>)}
            </select>
            {/* Ce que le forfait entraîne : quotas et modules, lus du serveur — pas une seconde liste tenue à la main. */}
            <small id="sa-plan-aide" className="muted" data-testid="sa-plan-aide" style={{ display: "block", fontSize: 11 }}>
              {(() => { const p = plans.find((x) => x.code === form.planCode); return p
                ? `${p.maxTeachers < 0 ? "professeurs illimités" : `${p.maxTeachers} professeurs`}, ${p.maxContractsPerYear < 0 ? "contrats illimités" : `${p.maxContractsPerYear} contrats/an`}, ${p.modules.length} modules : ${p.modules.join(", ")}`
                : "Quotas et modules du forfait (chargement)…"; })()}
            </small></label>
          </fieldset>
          <fieldset className="fieldset">
          <legend>Coordonnées</legend>
          <label className="field"><span>Adresse</span><input data-testid="sa-address" autoComplete="street-address" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
          <label className="field"><span>Ville</span><input data-testid="sa-city" autoComplete="address-level2" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></label>
          <label className="field"><span>Code postal</span><input data-testid="sa-postal" autoComplete="postal-code" value={form.postalCode} onChange={(e) => setForm({ ...form, postalCode: e.target.value })} /></label>
          <label className="field"><span>Téléphone</span><input data-testid="sa-phone" type="tel" autoComplete="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
          <label className="field"><span>Courriel</span><input data-testid="sa-email" type="email" autoComplete="email" aria-invalid={creationErreurs.some((m) => /courriel/i.test(m)) || undefined} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
          <label className="field"><span>Slug d'URL (sous-domaine)</span><input data-testid="sa-slug" value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} aria-describedby="sa-slug-aide" />
            <span className="aide" id="sa-slug-aide">Lettres minuscules, chiffres et tirets — ex. « ecole-a ». Laissez vide pour le déduire du nom.</span></label>
          </fieldset>
          <fieldset className="fieldset">
          <legend>Compte de l'administrateur</legend>
          <label className="field"><span>Identifiant de connexion</span><input data-testid="sa-admin-user" autoComplete="off" value={form.adminUsername} onChange={(e) => setForm({ ...form, adminUsername: e.target.value })} /></label>
          <label className="field"><span>Mot de passe initial</span><input data-testid="sa-admin-pass" type="password" autoComplete="new-password" value={form.adminPassword} onChange={(e) => setForm({ ...form, adminPassword: e.target.value })} aria-describedby="sa-admin-pass-aide" />
            <span className="aide" id="sa-admin-pass-aide">Douze caractères au moins, avec majuscules, chiffres et symboles ; il devra être changé à la première connexion.</span></label>
          </fieldset>
          <div className="field field-actions">
            <button className="primary" data-testid="sa-create" type="button" onClick={createTenant} disabled={creationEnCours} aria-busy={creationEnCours}>
              {creationEnCours ? "Création…" : "Créer l'organisme"}
            </button>
          </div>
        </div>
      </details>

      <div className="table-wrap" tabIndex={0} role="region" aria-label="Tableau (zone défilable au clavier)">
        <table data-testid="sa-table">
          <caption className="sr-only">Tableau de données</caption>
          <thead><tr><th scope="col">Organisme</th><th scope="col">Ville</th><th scope="col">Pays &amp; monnaie</th><th scope="col">Plan</th><th scope="col">Statut</th><th scope="col">Admins</th><th scope="col">Actions</th></tr></thead>
          <tbody>
            {tenantsPager.pageItems.map((tn) => (
              <tr key={tn.id} data-testid={`sa-row-${tn.id}`}>
                <td><strong>{tn.name}</strong><div style={{ fontSize: 11, opacity: 0.7 }}>{tn.address || "—"}</div></td>
                <td>{tn.city || "—"}</td>
                <td data-testid={`sa-country-${tn.id}`}>
                  {(() => { const c = countries.find((x) => x.code === tn.country); return c ? `${c.flag} ${c.code} · ${c.currency}` : tn.country; })()}
                  {" "}<button className="link-btn" title="Corriger le pays/devise (avant toute transaction)" data-testid={`sa-country-edit-${tn.id}`} onClick={() => correctCountry(tn)} style={{ background: "none", border: "none", cursor: "pointer", padding: 0 }}>✏️</button>
                </td>
                <td>{tn.planCode}</td>
                <td><span className={`badge badge-${tn.status}`}>{tn.status}</span></td>
                <td data-testid={`sa-admins-${tn.id}`}>
                  {tn.admins.length === 0 ? "—" : tn.admins.map((a) => (
                    // Boutons **libellés** plutôt qu'émojis seuls : trois pictogrammes côte à
                    // côte dans une cellule ne se distinguent pas, et l'action la plus
                    // recherchée — réinitialiser un mot de passe — passait inaperçue.
                    <div key={a.id} style={{ marginBottom: 6 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        <strong style={{ textDecoration: a.active === false ? "line-through" : undefined }}>{a.username}</strong>
                        {a.active === false && <span className="badge badge-muted">désactivé</span>}
                      </div>
                      <div className="actions-row" style={{ marginTop: 2 }}>
                        <button className="secondary" style={{ fontSize: 11, padding: "2px 8px" }}
                          title="Un mot de passe provisoire est généré ; il devra en choisir un au prochain accès"
                          data-testid={`sa-reset-password-${(a as any).userId || a.id}`}
                          onClick={() => setResetCible({ tenant: tn, admin: a as any })}>
                          🔒 Mot de passe
                        </button>
                        <button className="secondary" style={{ fontSize: 11, padding: "2px 8px" }}
                          title="Réinitialiser le 2ᵉ facteur (questions secrètes / TOTP oubliés) — reconfiguration imposée au prochain accès"
                          data-testid={`sa-reset-security-${(a as any).userId || a.id}`}
                          onClick={() => resetAdminSecurity(tn, a as any)}>
                          🔑 2ᵉ facteur
                        </button>
                        <button className={a.active === false ? "secondary" : "danger"}
                          style={{ fontSize: 11, padding: "2px 8px" }}
                          title={a.active === false ? "Réactiver ce compte" : "Désactiver ce compte — ses traces sont conservées"}
                          data-testid={`sa-toggle-admin-${(a as any).userId || a.id}`}
                          onClick={() => toggleAdminActive(tn, a as any)}>
                          {a.active === false ? "▶️ Réactiver" : "⏸️ Désactiver"}
                        </button>
                      </div>
                    </div>
                  ))}
                </td>
                <td>
                  <div className="actions-row">
                    <button data-testid={`sa-edit-${tn.id}`} onClick={() => startEdit(tn)}>Modifier</button>
                    {tn.status !== "active" && tn.status !== "terminated" && <button data-testid={`sa-activate-${tn.id}`} onClick={() => lifecycle(tn.id, "activate")}>Activer</button>}
                    {tn.status === "active" && <button data-testid={`sa-suspend-${tn.id}`} onClick={() => lifecycle(tn.id, "suspend")}>Suspendre</button>}
                    {tn.status !== "terminated" && <button className="danger" data-testid={`sa-terminate-${tn.id}`} onClick={() => lifecycle(tn.id, "terminate")}>Résilier</button>}
                    <button data-testid={`sa-usage-${tn.id}`} onClick={() => voirUsage(tn.id)} aria-expanded={!!usage[tn.id]}>Usage du mois</button>
                    {/* Contrat 9.25.06 : un établissement résilié peut voir ses données détruites, avec attestation. */}
                    <DestructionDonnees tenant={tn} apiFetch={apiFetch} signaler={(m, e) => (e ? setError(m) : setSuccess(m))} />
                  </div>
                  {usage[tn.id] && (
                    <p className="muted" role="status" data-testid={`sa-usage-valeurs-${tn.id}`} style={{ margin: "6px 0 0" }}>
                      {usage[tn.id].teachersCount} professeur(s) · {usage[tn.id].contractsCount} contrat(s) · {usage[tn.id].payCalcsCount} calcul(s) de paie ce mois-ci
                    </p>
                  )}
                </td>
              </tr>
            ))}
            {tenants.length === 0 && <tr><td colSpan={7} className="empty-cell">Aucun organisme.</td></tr>}
          </tbody>
        </table>
        <Pagination page={tenantsPager.page} pageCount={tenantsPager.pageCount} total={tenantsPager.total} pageSize={tenantsPager.pageSize} onPage={tenantsPager.setPage} testId="sa-tenants-pager" />
      </div>

      {editId && (
        <div className="card" data-testid="sa-edit-card" style={{ marginTop: 12 }}>
          <h4>Modifier l'organisme</h4>
          <div className="grid-form">
            <label className="field"><span>Nom</span><input data-testid="sa-edit-name" value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} /></label>
            <label className="field"><span>Adresse</span><input data-testid="sa-edit-address" value={edit.address} onChange={(e) => setEdit({ ...edit, address: e.target.value })} /></label>
            <label className="field"><span>Ville</span><input data-testid="sa-edit-city" value={edit.city} onChange={(e) => setEdit({ ...edit, city: e.target.value })} /></label>
            <label className="field"><span>Téléphone</span><input data-testid="sa-edit-phone" value={edit.phone} onChange={(e) => setEdit({ ...edit, phone: e.target.value })} /></label>
            <label className="field"><span>Slug d'URL</span><input data-testid="sa-edit-slug" value={edit.slug} onChange={(e) => setEdit({ ...edit, slug: e.target.value })} placeholder="ecole-a" /></label>
            <div className="field field-actions">
              <button className="primary" data-testid="sa-edit-save" onClick={saveEdit}>Enregistrer</button>
              <button className="secondary" onClick={() => setEditId(null)}>Annuler</button>
            </div>
          </div>
        </div>
      )}

      <details className="collapsible">
        <summary><Icone nom="plus" /> Ajouter un admin à un organisme</summary>
        <div className="grid-form">
          <label className="field"><span>Organisme <span className="req-star" aria-hidden="true">*</span></span>
            <select data-testid="sa-admin-tenant" required aria-required="true" value={newAdmin.tenantId} onChange={(e) => setNewAdmin({ ...newAdmin, tenantId: e.target.value })}>
              <option value="">— Choisir —</option>
              {tenants.map((tn) => <option key={tn.id} value={tn.id}>{tn.name}</option>)}
            </select></label>
          <label className="field"><span>Login <span className="req-star" aria-hidden="true">*</span></span><input data-testid="sa-admin2-user" required aria-required="true" value={newAdmin.username} onChange={(e) => setNewAdmin({ ...newAdmin, username: e.target.value })} /></label>
          <label className="field"><span>Mot de passe <span className="req-star" aria-hidden="true">*</span></span><input data-testid="sa-admin2-pass" required aria-required="true" type="password" value={newAdmin.password} onChange={(e) => setNewAdmin({ ...newAdmin, password: e.target.value })} /></label>
          <div className="field field-actions"><button className="primary" data-testid="sa-admin2-add" onClick={addAdmin}>Créer l'admin</button></div>
        </div>
      </details>
      </>)}

      {tab === "modules" && (<>
        <TenantModulesManager tenants={tenants} apiFetch={apiFetch} onChanged={load} setError={setError} setSuccess={setSuccess} />
        {/*
          Les deux réglages vivent sous le même onglet parce qu'ils répondent à la même question —
          « que voit cet établissement ? » — mais ils ne se valent pas : le module **ferme** un
          accès au serveur, le sous-menu **dégarnit** un menu. Les séparer en deux onglets aurait
          laissé croire à deux fonctions indépendantes ; les empiler sans le dire aurait laissé
          croire à deux permissions. D'où le titre, et l'avertissement que porte l'écran.
        */}
        <details className="collapsible" data-testid="sa-sous-menus" open>
          <summary><Icone nom="reglages" /> Sous-menus visibles (dégarnissage du menu — pas une permission)</summary>
          <SousMenusManager tenants={tenants} apiFetch={apiFetch} setError={setError} setSuccess={setSuccess} />
        </details>
      </>)}

      {tab === "consommation" && (
        <ConsommationAvis apiFetch={apiFetch} setError={setError} setSuccess={setSuccess} />
      )}


      {tab === "compliance" && (<>
      {/* Message de maintenance système (Global-19) — bannière affichée à tous les utilisateurs. */}
      <div className="card" data-testid="sa-maintenance">
        <h3><Icone nom="reglages" /> Message de maintenance</h3>
        <p className="subtitle">Affiche une bannière système à tous les utilisateurs (fenêtres de maintenance, avis).</p>
        <div className="row-form" style={{ alignItems: "end" }}>
          <div style={{ flex: 2, minWidth: 280 }}><label htmlFor="sa-maint-msg">Message</label><input id="sa-maint-msg" data-testid="sa-maint-msg" value={maint.message} onChange={(e) => setMaint({ ...maint, message: e.target.value })} /></div>
          <div><label htmlFor="sa-maint-level">Niveau</label>
            <select id="sa-maint-level" data-testid="sa-maint-level" value={maint.level} onChange={(e) => setMaint({ ...maint, level: e.target.value })}>
              <option value="info">Info</option><option value="warning">Avertissement</option><option value="critical">Critique</option>
            </select>
          </div>
          <label className="chip"><input type="checkbox" data-testid="sa-maint-active" checked={maint.active} onChange={(e) => setMaint({ ...maint, active: e.target.checked })} /> Actif</label>
          <div><button className="primary" data-testid="sa-maint-save" onClick={saveMaintenance}>Enregistrer</button></div>
        </div>
      </div>
      </>)}

      {tab === "security" && (
        <div data-testid="sa-security">
          <div className="row-form" style={{ marginBottom: 12 }}>
            <div style={{ minWidth: 280 }}>
              <label htmlFor="sa-sec-scope">Portée du catalogue</label>
              <select id="sa-sec-scope" data-testid="sa-sec-scope" value={secScope} onChange={(e) => setSecScope(e.target.value)}>
                <option value="global">🌐 Catalogue global (défaut, tous les organismes)</option>
                {tenants.map((tn) => <option key={tn.id} value={tn.id}>🏢 {tn.name}</option>)}
              </select>
            </div>
          </div>
          <SecurityQuestionsManager
            key={secScope}
            apiFetch={apiFetch}
            scopeParam={secScope}
            title={secScope === "global" ? "Catalogue global de questions de sécurité" : "Questions de sécurité — organisme sélectionné"}
          />
        </div>
      )}

      {tab === "soutien" && (
        <div data-testid="sa-soutien-onglet">
          <SoutienExploitation apiFetch={apiFetch} />
        </div>
      )}

      {tab === "exploitation" && (
        <div data-testid="sa-exploitation">
          <SauvegardesPanel apiFetch={apiFetch} />
          <SecuritePanel apiFetch={apiFetch} />
          <IncidentsConfidentialite apiFetch={apiFetch} tous />
          <AuditIntegrite apiFetch={apiFetch} peutConsigner />
          <section className="card" aria-labelledby="sa-sso-titre" style={{ marginTop: 16 }}>
            <h3 id="sa-sso-titre"><Icone nom="banque" /> Fédération d'identité</h3>
            <p className="aide">
              L'administrateur de l'IdP (Entra, ADFS…) importe les métadonnées du fournisseur de service pour
              déclarer VINA GEPS ; le bouton SAML n'apparaît au login qu'une fois l'IdP configuré côté serveur.
            </p>
            <a className="btn secondary" data-testid="sa-saml-metadata" href="/api/auth/sso/saml/metadata" target="_blank" rel="noopener">
              Métadonnées SP SAML 2.0 (XML)
            </a>
          </section>
        </div>
      )}

      {ConfirmHost}

      {/* Réinitialisation d'un mot de passe d'administrateur — deux temps dans un même modal.
          Une fenêtre native ne conviendrait pas : le mot de passe provisoire doit pouvoir être
          sélectionné et copié, et il ne sera plus jamais affiché. */}
      <Modal open={!!resetCible} onClose={() => setResetCible(null)}
        title="Réinitialiser le mot de passe" testId="sa-reset-pwd-modal" size="sm">
        {resetCible && !resetCible.temporaryPassword && (
          <>
            <p>
              Réinitialiser le mot de passe de <strong>{resetCible.admin.username}</strong>
              {" "}({resetCible.tenant.name}) ?
            </p>
            <p className="muted">
              Un mot de passe provisoire sera généré. L'intéressé devra en choisir un au
              prochain accès : celui que vous lui transmettrez ne restera pas en service.
            </p>
            <div className="modal-actions">
              <button className="secondary" onClick={() => setResetCible(null)}>Annuler</button>
              <button className="primary" data-testid="sa-reset-pwd-confirm" onClick={confirmerResetMotDePasse}>
                Réinitialiser
              </button>
            </div>
          </>
        )}
        {resetCible?.temporaryPassword && (
          <>
            <p>
              Mot de passe provisoire de <strong>{resetCible.admin.username}</strong> :
            </p>
            <p>
              <code data-testid="sa-temp-password" style={{ fontSize: 18, userSelect: "all" }}>
                {resetCible.temporaryPassword}
              </code>
            </p>
            <p className="muted">
              Notez-le maintenant — il n'est stocké nulle part en clair et ne sera plus affiché.
            </p>
            <div className="modal-actions">
              <button className="primary" data-testid="sa-reset-pwd-close" onClick={() => setResetCible(null)}>
                J'ai noté
              </button>
            </div>
          </>
        )}
      </Modal>

    </div>
  );
}
