/**
 * **Organisme** — onglet de l'administration (AO 26-015-DO, lot R11 — P-TN-9, P-TN-10, P-TN-11).
 *
 * Trois options de l'organisme : sa charte (nom, logo, deux couleurs — le serveur refuse une couleur
 * qui rendrait un texte illisible, et dit de combien), les pays d'où ses comptes se connectent, et
 * les règles qui préviennent par courriel d'un export ou d'une connexion refusée.
 */
import { useCallback, useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { EtatVide, Message } from "../composants";
import { liste, objet } from "../formeRecue";

type Appel = (chemin: string, init?: RequestInit) => Promise<Response>;
interface Charte { nomAffiche: string; couleurPrincipale: string; couleurLaterale: string; logo: string | null }
interface Regle { id: string; evenement: string; destinataires: string[] }
interface Alerte { id: string; evenement: string; quoi: string; qui: string; envoyees: number; erreur: string | null; horodatage: string }

const EVENEMENTS: Record<string, string> = {
  EXPORT_TELECHARGE: "Téléchargement d'un export (CSV, PDF, tableur)",
  CONNEXION_REFUSEE_PAYS: "Connexion refusée par la restriction de pays",
};
const DEFAUT: Charte = { nomAffiche: "", couleurPrincipale: "#2563eb", couleurLaterale: "#0f172a", logo: null };

/** Contraste WCAG, calculé comme le serveur — pour prévenir avant d'enregistrer, le serveur tranche. */
function contraste(a: string, b: string): number {
  const lum = (hex: string) => {
    const n = parseInt(hex.slice(1), 16);
    const c = (v: number) => { const s = v / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; };
    return 0.2126 * c((n >> 16) & 255) + 0.7152 * c((n >> 8) & 255) + 0.0722 * c(n & 255);
  };
  if (!/^#[0-9a-f]{6}$/i.test(a) || !/^#[0-9a-f]{6}$/i.test(b)) return 0;
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return Math.floor(((x + 0.05) / (y + 0.05)) * 100) / 100;
}

/** La couleur vue à travers un voile blanc — le fond réel du bloc de l'organisme dans la barre latérale. */
function sousVoile(hex: string, opacite: number): string {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) return hex;
  const n = parseInt(hex.slice(1), 16);
  const m = (c: number) => Math.round(c * (1 - opacite) + 255 * opacite).toString(16).padStart(2, "0");
  return `#${m((n >> 16) & 255)}${m((n >> 8) & 255)}${m(n & 255)}`;
}

export default function OptionsOrganisme({ apiFetch }: { apiFetch: Appel }) {
  const [charte, setCharte] = useState<Charte>(DEFAUT);
  const [pays, setPays] = useState({ texte: "", enteteConfiguree: false });
  const [regles, setRegles] = useState<Regle[]>([]);
  const [alertes, setAlertes] = useState<Alerte[]>([]);
  const [nouvelle, setNouvelle] = useState({ evenement: "EXPORT_TELECHARGE", destinataires: "" });
  const [info, setInfo] = useState("");
  const [erreur, setErreur] = useState("");

  const lire = useCallback(async (chemin: string) => { const r = await apiFetch(chemin); return r.ok ? r.json() : null; }, [apiFetch]);
  const charger = useCallback(async () => {
    const c = objet<Charte>(await lire("/api/organisme/charte"));
    setCharte(c ? { ...c, logo: c.logo ?? null } : DEFAUT);
    const p = objet<{ pays: string[]; enteteConfiguree: boolean }>(await lire("/api/organisme/pays"));
    setPays({ texte: (p?.pays ?? []).join(", "), enteteConfiguree: !!p?.enteteConfiguree });
    setRegles(liste<Regle>(await lire("/api/organisme/alertes")));
    setAlertes(liste<Alerte>(await lire("/api/organisme/alertes/journal")));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const ecrire = async (chemin: string, methode: string, corps: unknown, succes: string) => {
    setInfo(""); setErreur("");
    const r = await apiFetch(chemin, { method: methode, headers: { "Content-Type": "application/json" }, body: corps === undefined ? undefined : JSON.stringify(corps) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setErreur(j.errors?.join(", ") || `Erreur ${r.status}`); return false; }
    setInfo(succes);
    await charger();
    return true;
  };

  const choisirLogo = (e: ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    const lecteur = new FileReader();
    lecteur.onload = () => setCharte((c) => ({ ...c, logo: String(lecteur.result) }));
    lecteur.readAsDataURL(f);
  };

  const mesures = [
    { libelle: "Texte blanc sur la couleur principale", valeur: contraste(charte.couleurPrincipale, "#ffffff") },
    { libelle: "Liens sur le fond de page", valeur: contraste(charte.couleurPrincipale, "#f4f6fb") },
    { libelle: "Entrées du menu sur la barre latérale", valeur: contraste(charte.couleurLaterale, "#cbd5e1") },
    { libelle: "Pied de la barre latérale", valeur: contraste(charte.couleurLaterale, "#94a3b8") },
    { libelle: "Bloc de l'organisme (voile clair)", valeur: contraste(sousVoile(charte.couleurLaterale, 0.12), "#94a3b8") },
  ];

  return (
    <div className="card" data-testid="org-options">
      {erreur && <Message variante="erreur" testId="org-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="org-info">{info}</Message>}

      <section aria-labelledby="org-charte-titre">
        <h3 id="org-charte-titre">Charte graphique</h3>
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); void ecrire("/api/organisme/charte", "PUT", charte, "Charte enregistrée : elle s'applique à la prochaine ouverture de l'application."); }}>
          <div className="row-form">
            <div><label htmlFor="org-nom">Nom affiché</label><input id="org-nom" required maxLength={60} data-testid="org-nom" value={charte.nomAffiche} onChange={(e) => setCharte({ ...charte, nomAffiche: e.target.value })} /></div>
            <div><label htmlFor="org-principale">Couleur principale</label><input id="org-principale" type="color" data-testid="org-principale" value={charte.couleurPrincipale} onChange={(e) => setCharte({ ...charte, couleurPrincipale: e.target.value })} /></div>
            <div><label htmlFor="org-laterale">Couleur de la barre latérale</label><input id="org-laterale" type="color" data-testid="org-laterale" value={charte.couleurLaterale} onChange={(e) => setCharte({ ...charte, couleurLaterale: e.target.value })} /></div>
            <div><label htmlFor="org-logo">Logo (PNG, JPEG ou WebP, 200 Ko au plus)</label><input id="org-logo" type="file" accept="image/png,image/jpeg,image/webp" data-testid="org-logo" onChange={choisirLogo} /></div>
          </div>
          {charte.logo && <p><img src={charte.logo} alt={`Logo de ${charte.nomAffiche || "l'organisme"}`} className="org-logo-apercu" /></p>}
          <ul aria-label="Contrastes mesurés" data-testid="org-contrastes">
            {mesures.map((m) => <li key={m.libelle}>{m.libelle} : {m.valeur}:1 — {m.valeur >= 4.5 ? "conforme" : "insuffisant, il faut 4,5:1"}</li>)}
          </ul>
          <div className="actions">
            <button className="primary" type="submit" data-testid="org-charte-enregistrer">Enregistrer la charte</button>
            <button type="button" className="secondary" data-testid="org-charte-retirer" onClick={() => void ecrire("/api/organisme/charte", "DELETE", undefined, "Charte retirée : l'application reprend ses couleurs.")}>Revenir à la charte de l'application</button>
          </div>
        </form>
      </section>

      <section aria-labelledby="org-pays-titre">
        <h3 id="org-pays-titre">Pays autorisés à la connexion</h3>
        {!pays.enteteConfiguree && <Message variante="info" testId="org-pays-indisponible">Le serveur ne reçoit pas encore le pays d'origine des connexions : la restriction ne peut pas être activée tant que la passerelle ne le transmet pas.</Message>}
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); void ecrire("/api/organisme/pays", "PUT", { pays: pays.texte.split(/[\s,;]+/).filter(Boolean) }, "Pays autorisés enregistrés."); }}>
          <div className="row-form">
            <div>
              <label htmlFor="org-pays">Codes pays ISO, séparés par des virgules (vide : aucune restriction)</label>
              <input id="org-pays" data-testid="org-pays" aria-describedby="org-pays-aide" value={pays.texte} onChange={(e) => setPays({ ...pays, texte: e.target.value })} />
              <span id="org-pays-aide" className="aide">Ex. « CA, US ». Les comptes globaux (superadmin) ne sont pas concernés.</span>
            </div>
          </div>
          <div className="actions"><button type="submit" className="primary" data-testid="org-pays-enregistrer">Enregistrer les pays</button></div>
        </form>
      </section>

      <section aria-labelledby="org-alertes-titre">
        <h3 id="org-alertes-titre">Alertes par courriel</h3>
        {regles.length === 0
          ? <EtatVide titre="Aucune règle d'alerte" compact testId="org-alertes-vide">Ajoutez-en une pour être prévenu des exports ou des connexions refusées.</EtatVide>
          : (
            <ul data-testid="org-regles">
              {regles.map((r) => (
                <li key={r.id}>{EVENEMENTS[r.evenement] ?? r.evenement} → {r.destinataires.join(", ")}{" "}
                  <button type="button" className="secondary" data-testid={`org-regle-${r.id}-retirer`} onClick={() => void ecrire(`/api/organisme/alertes/${r.id}`, "DELETE", undefined, "Règle retirée.")}>
                    Retirer<span className="sr-only"> la règle {EVENEMENTS[r.evenement]}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        <form onSubmit={(e: FormEvent) => {
          e.preventDefault();
          void ecrire("/api/organisme/alertes", "POST", { evenement: nouvelle.evenement, destinataires: nouvelle.destinataires.split(/[\s,;]+/).filter(Boolean) }, "Règle d'alerte ajoutée.")
            .then((ok) => { if (ok) setNouvelle({ ...nouvelle, destinataires: "" }); });
        }}>
          <div className="row-form">
            <div>
              <label htmlFor="org-evenement">Événement</label>
              <select id="org-evenement" data-testid="org-evenement" value={nouvelle.evenement} onChange={(e) => setNouvelle({ ...nouvelle, evenement: e.target.value })}>
                {Object.entries(EVENEMENTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div><label htmlFor="org-destinataires">Destinataires (courriels séparés par des virgules)</label><input id="org-destinataires" required data-testid="org-destinataires" value={nouvelle.destinataires} onChange={(e) => setNouvelle({ ...nouvelle, destinataires: e.target.value })} /></div>
          </div>
          <div className="actions"><button type="submit" className="primary" data-testid="org-alerte-ajouter">Ajouter la règle</button></div>
        </form>
        {alertes.length > 0 && (
          <details data-testid="org-journal-alertes">
            <summary>Alertes envoyées ({alertes.length})</summary>
            <ul>{alertes.map((a) => <li key={a.id}>{new Date(a.horodatage).toLocaleString("fr-CA")} — {EVENEMENTS[a.evenement] ?? a.evenement} : {a.quoi} ({a.qui}) · {a.erreur ? `non envoyée : ${a.erreur}` : `${a.envoyees} courriel(s) parti(s)`}</li>)}</ul>
          </details>
        )}
      </section>
    </div>
  );
}
