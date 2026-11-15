/**
 * **Annuaires de l'organisme** — AO 26-015-DO P-TN-2 (« l'intégration à plusieurs annuaires actifs,
 * tel que : Active Directories »).
 *
 * L'administrateur déclare chaque fournisseur d'identité dont les comptes peuvent entrer — un
 * locataire Microsoft Entra ID, ou tout fournisseur OpenID Connect (Keycloak…) désigné par son
 * émetteur (ADR 0075), ou un annuaire LDAP joint en LDAPS (ADR 0083) —, les domaines de courriel admis, et quels groupes donnent quel rôle. Le secret client ne transite pas
 * par l'écran : seul le nom de la variable d'environnement qui le porte, et si elle est déployée.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { EtatVide, Message } from "../composants";
import { liste, objet } from "../formeRecue";
import { ConfirmModal, FormulaireModal } from "../Modal";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
interface Correspondance { groupe: string; role: string }
export interface Annuaire {
  id: string; nom: string; fournisseur?: Fournisseur; locataireEntra: string; emetteur?: string | null; clientId: string; variableSecret: string; secretDeploye: boolean;
  domaines: string[]; revendication: "groups" | "roles"; correspondances: Correspondance[]; roleParDefaut: string | null; actif: boolean;
}
interface Role { role: string; label: string }
type Fournisseur = "entra" | "oidc" | "ldaps";
const FOURNISSEURS: Record<Fournisseur, string> = { entra: "Microsoft Entra ID", oidc: "OpenID Connect (Keycloak, Okta…)", ldaps: "Annuaire LDAP (Active Directory), en LDAPS" };

/** « groupe = rôle », une règle par ligne : la première qui correspond l'emporte, comme au serveur. */
export function lireRegles(texte: string): Correspondance[] {
  return texte.split("\n").map((l) => l.trim()).filter(Boolean).map((l) => {
    const i = l.lastIndexOf("=");
    return i < 0 ? { groupe: l, role: "" } : { groupe: l.slice(0, i).trim(), role: l.slice(i + 1).trim() };
  });
}

export function ecrireRegles(regles: Correspondance[]): string {
  return regles.map((r) => `${r.groupe} = ${r.role}`).join("\n");
}

function corpsDe(f: HTMLFormElement): Record<string, unknown> {
  const d = new FormData(f);
  const texte = (n: string) => String(d.get(n) ?? "");
  return {
    nom: texte("nom"), fournisseur: texte("fournisseur") || "entra", locataireEntra: texte("locataireEntra"), emetteur: texte("emetteur"),
    clientId: texte("clientId"), variableSecret: texte("variableSecret"),
    domaines: texte("domaines").split(/[\s,]+/).filter(Boolean), revendication: texte("revendication"),
    correspondances: lireRegles(texte("correspondances")), roleParDefaut: texte("roleParDefaut") || null, actif: d.get("actif") === "on",
  };
}

function Formulaire({ prefixe, initial, roles, onEnvoyer }: {
  prefixe: string; initial: Annuaire | null; roles: Role[]; onEnvoyer: (corps: Record<string, unknown>) => Promise<boolean>;
}) {
  const [erreurs, setErreurs] = useState<string[]>([]);
  const [fournisseur, setFournisseur] = useState<Fournisseur>(initial?.fournisseur ?? "entra");
  const id = (n: string) => `${prefixe}-${n}`;
  async function soumettre(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErreurs([]);
    await onEnvoyer(corpsDe(e.currentTarget)).catch((x: Error) => setErreurs(x.message.split(" ; ")));
  }
  return (
    <form className="grid-form" data-testid={id("formulaire")} onSubmit={soumettre} noValidate>
      {erreurs.length > 0 && (
        <div className="field" style={{ gridColumn: "1 / -1" }}>
          <Message variante="erreur" testId={id("erreurs")}>
            <ul style={{ margin: 0 }}>{erreurs.map((m) => <li key={m}>{m}</li>)}</ul>
          </Message>
        </div>
      )}
      <label className="field" htmlFor={id("nom")}><span>Nom <span className="req-star" aria-hidden="true">*</span></span>
        <input id={id("nom")} name="nom" required maxLength={80} defaultValue={initial?.nom} data-testid={id("nom")} /></label>
      <label className="field" htmlFor={id("fournisseur")}><span>Fournisseur d'identité</span>
        <select id={id("fournisseur")} name="fournisseur" value={fournisseur} onChange={(e) => setFournisseur(e.target.value as Fournisseur)} data-testid={id("fournisseur")}>
          {(Object.keys(FOURNISSEURS) as Fournisseur[]).map((f) => <option key={f} value={f}>{FOURNISSEURS[f]}</option>)}
        </select></label>
      {fournisseur === "entra" ? (
        <>
          <label className="field" htmlFor={id("locataire")}><span>Locataire Entra (GUID ou domaine) <span className="req-star" aria-hidden="true">*</span></span>
            <input id={id("locataire")} name="locataireEntra" required defaultValue={initial?.locataireEntra} data-testid={id("locataire")}
              aria-describedby={id("locataire-aide")} /></label>
          <p id={id("locataire-aide")} className="muted" style={{ gridColumn: "1 / -1", margin: 0 }}>
            Jamais « common » ni « organizations » : ces autorités laissent entrer n'importe quel compte Microsoft.
          </p>
        </>
      ) : fournisseur === "oidc" ? (
        <>
          <label className="field" htmlFor={id("emetteur")}><span>Émetteur OIDC (issuer) <span className="req-star" aria-hidden="true">*</span></span>
            <input id={id("emetteur")} name="emetteur" type="url" required defaultValue={initial?.emetteur ?? ""} data-testid={id("emetteur")}
              aria-describedby={id("emetteur-aide")} /></label>
          <p id={id("emetteur-aide")} className="muted" style={{ gridColumn: "1 / -1", margin: 0 }}>
            L'adresse exacte de la revendication « iss », p. ex. https://sso.organisme.ca/realms/vina-geps. HTTPS obligatoire, sauf sur localhost.
          </p>
        </>
      ) : (
        <>
          <label className="field" htmlFor={id("ldaps")}><span>Adresse LDAPS et base de recherche <span className="req-star" aria-hidden="true">*</span></span>
            <input id={id("ldaps")} name="emetteur" required defaultValue={initial?.emetteur ?? "ldaps://"} data-testid={id("emetteur")}
              aria-describedby={id("ldaps-aide")} /></label>
          <p id={id("ldaps-aide")} className="muted" style={{ gridColumn: "1 / -1", margin: 0 }}>
            P. ex. ldaps://ad.etablissement.local:636/OU=Personnel,DC=etablissement,DC=local. ldap:// en clair est refusé ; le certificat
            de l'annuaire doit être reconnu par le serveur et porter ce nom d'hôte.
          </p>
        </>
      )}
      <label className="field" htmlFor={id("client")}><span>{fournisseur === "ldaps" ? "DN du compte de service" : "Identifiant du client"} <span className="req-star" aria-hidden="true">*</span></span>
        <input id={id("client")} name="clientId" required defaultValue={initial?.clientId} data-testid={id("client")} /></label>
      <label className="field" htmlFor={id("variable")}><span>Variable du secret (ANNUAIRE_…) <span className="req-star" aria-hidden="true">*</span></span>
        <input id={id("variable")} name="variableSecret" required pattern="ANNUAIRE_[A-Z0-9_]+" defaultValue={initial?.variableSecret ?? "ANNUAIRE_"}
          data-testid={id("variable")} /></label>
      <label className="field" htmlFor={id("domaines")}><span>Domaines de courriel admis <span className="req-star" aria-hidden="true">*</span></span>
        <input id={id("domaines")} name="domaines" required defaultValue={initial?.domaines.join(", ")} data-testid={id("domaines")}
          aria-describedby={id("domaines-aide")} /></label>
      <p id={id("domaines-aide")} className="muted" style={{ gridColumn: "1 / -1", margin: 0 }}>Séparés par des virgules. Un invité d'un autre domaine est refusé.</p>
      <label className="field" htmlFor={id("revendication")}><span>Appartenances lues dans</span>
        <select id={id("revendication")} name="revendication" defaultValue={initial?.revendication ?? "groups"} data-testid={id("revendication")}>
          <option value="groups">Groupes de sécurité (groups)</option>
          <option value="roles">Rôles d'application (roles)</option>
        </select></label>
      <label className="field" htmlFor={id("defaut")}><span>Rôle par défaut</span>
        <select id={id("defaut")} name="roleParDefaut" defaultValue={initial?.roleParDefaut ?? ""} data-testid={id("defaut")}>
          <option value="">Aucun — refuser sans groupe correspondant</option>
          {roles.map((r) => <option key={r.role} value={r.role}>{r.label}</option>)}
        </select></label>
      <label className="field" htmlFor={id("regles")} style={{ gridColumn: "1 / -1" }}><span>Correspondances groupe = rôle, une par ligne, par priorité</span>
        <textarea id={id("regles")} name="correspondances" rows={4} defaultValue={initial ? ecrireRegles(initial.correspondances) : ""}
          data-testid={id("regles")} aria-describedby={id("regles-aide")} /></label>
      <p id={id("regles-aide")} className="muted" style={{ gridColumn: "1 / -1", margin: 0 }}>
        Codes de rôle : {roles.map((r) => r.role).join(", ")}. La première ligne dont le groupe est reçu l'emporte.
      </p>
      <label className="field field-check" htmlFor={id("actif")}>
        <input id={id("actif")} name="actif" type="checkbox" defaultChecked={initial?.actif ?? true} data-testid={id("actif")} />
        <span>Proposé à la connexion</span></label>
      <div className="field field-actions"><button type="submit" className="primary" data-testid={id("enregistrer")}>Enregistrer</button></div>
    </form>
  );
}

export default function AnnuairesOrganisme({ apiFetch }: { apiFetch: Appel }) {
  const [annuaires, setAnnuaires] = useState<Annuaire[]>([]);
  const [rappel, setRappel] = useState("");
  const [roles, setRoles] = useState<Role[]>([]);
  const [info, setInfo] = useState("");
  const [erreur, setErreur] = useState("");
  const [enModification, setEnModification] = useState<Annuaire | null>(null);
  const [aRetirer, setARetirer] = useState<Annuaire | null>(null);

  const charger = useCallback(async () => {
    const r = await apiFetch("/api/auth/annuaires");
    const j = objet<{ annuaires: Annuaire[]; rappel: string }>(r.ok ? await r.json() : null);
    setAnnuaires(liste<Annuaire>(j?.annuaires));
    setRappel(j?.rappel ?? "");
    const rr = await apiFetch("/api/auth/assignable-roles");
    setRoles(liste<Role>(rr.ok ? await rr.json() : null));
  }, [apiFetch]);
  useEffect(() => { charger().catch(() => setErreur("Les annuaires n'ont pas pu être lus.")); }, [charger]);

  /** Rejette avec les motifs du serveur, que le formulaire affiche champ par champ. */
  async function envoyer(chemin: string, methode: string, corps: Record<string, unknown>, succes: string): Promise<boolean> {
    const r = await apiFetch(chemin, { method: methode, headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
    if (!r.ok) {
      const j = await r.json().catch(() => null);
      throw new Error((Array.isArray(j?.errors) ? j.errors : [`Enregistrement refusé (erreur ${r.status}).`]).join(" ; "));
    }
    setInfo(succes);
    await charger();
    return true;
  }

  async function retirer(a: Annuaire) {
    setARetirer(null);
    const r = await apiFetch(`/api/auth/annuaires/${encodeURIComponent(a.id)}`, { method: "DELETE" });
    if (r.ok) { setInfo(`Annuaire « ${a.nom} » retiré.`); await charger(); } else setErreur(`Le retrait a échoué (erreur ${r.status}).`);
  }

  return (
    <section className="card" aria-labelledby="annuaires-titre" data-testid="annuaires">
      <h3 id="annuaires-titre">Annuaires de l'organisme</h3>
      <p className="muted">
        Chaque annuaire déclaré ici (Microsoft Entra ID ou OpenID Connect) apparaît sur la page de connexion. Adresse de retour à inscrire dans son
        inscription d'application : <code data-testid="annuaires-rappel">{rappel || "adresse publique non déclarée"}</code>
      </p>
      {info && <Message variante="succes" testId="annuaires-info">{info}</Message>}
      {erreur && <Message variante="erreur" testId="annuaires-erreur">{erreur}</Message>}
      <FormulaireModal libelle="Déclarer un annuaire" testId="annuaire-nouveau">{(fermer) => (
        <Formulaire prefixe="annuaire-nouveau" initial={null} roles={roles}
          onEnvoyer={async (c) => { const ok = await envoyer("/api/auth/annuaires", "POST", c, `Annuaire « ${String(c.nom)} » déclaré.`); fermer(); return ok; }} />
      )}</FormulaireModal>
      {annuaires.length === 0 ? <EtatVide testId="annuaires-vide" titre="Aucun annuaire déclaré" /> : (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Annuaires (zone défilable au clavier)">
          <table data-testid="annuaires-table">
            <caption className="sr-only">Annuaires de l'organisme</caption>
            <thead><tr>
              <th scope="col">Nom</th><th scope="col">Fournisseur</th><th scope="col">Domaines</th><th scope="col">Règles</th><th scope="col">Secret</th><th scope="col">État</th><th scope="col">Actions</th>
            </tr></thead>
            <tbody>
              {annuaires.map((a) => (
                <tr key={a.id} data-testid={`annuaire-${a.id}`}>
                  <th scope="row">{a.nom}</th>
                  <td>{FOURNISSEURS[a.fournisseur ?? "entra"]}{a.fournisseur !== "entra" && a.emetteur ? ` — ${a.emetteur}` : ""}</td>
                  <td>{a.domaines.join(", ")}</td>
                  <td>{a.correspondances.length} règle(s){a.roleParDefaut ? `, défaut : ${a.roleParDefaut}` : ", sans défaut"}</td>
                  <td>{a.secretDeploye ? "✓ déployé" : `✗ ${a.variableSecret} absente`}</td>
                  <td>{a.actif ? "Actif" : "Inactif"}</td>
                  <td>
                    <button type="button" className="secondary" data-testid={`annuaire-${a.id}-modifier`} onClick={() => setEnModification(a)}
                      aria-label={`Modifier l'annuaire ${a.nom}`}>Modifier</button>{" "}
                    <button type="button" className="danger" data-testid={`annuaire-${a.id}-retirer`} onClick={() => setARetirer(a)}
                      aria-label={`Retirer l'annuaire ${a.nom}`}>Retirer</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <FormulaireModal libelle="Modifier l'annuaire" testId="annuaire-modification" ouvert={enModification !== null} onFermer={() => setEnModification(null)}>
        {enModification && (
          <Formulaire prefixe="annuaire-modification" initial={enModification} roles={roles}
            onEnvoyer={async (c) => {
              const ok = await envoyer(`/api/auth/annuaires/${encodeURIComponent(enModification.id)}`, "PUT", c, `Annuaire « ${String(c.nom)} » modifié.`);
              setEnModification(null);
              return ok;
            }} />
        )}
      </FormulaireModal>
      <ConfirmModal open={aRetirer !== null} title="Retirer l'annuaire" variant="danger" confirmLabel="Retirer" testId="annuaire-retrait"
        message={`Les comptes de « ${aRetirer?.nom ?? ""} » ne pourront plus se connecter par cet annuaire.`}
        onConfirm={() => (aRetirer ? retirer(aRetirer) : undefined)} onCancel={() => setARetirer(null)} />
    </section>
  );
}
