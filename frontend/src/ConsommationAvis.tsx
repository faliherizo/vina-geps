/**
 * **Consommation d'avis par organisme** — espace superadmin.
 *
 * L'écran d'où l'exploitant répond à trois questions : combien cet organisme envoie-t-il, a-t-il
 * dépassé son enveloppe, et **qu'est-ce qui a consommé**.
 *
 * Une décision d'affichage porte tout le reste : les textos sont comptés en **segments**, pas en
 * messages. C'est l'unité que le fournisseur facture — un avis de 480 caractères contenant un
 * « ê » ou un « ç » minuscule bascule en UCS-2 (70 caractères par segment) et en coûte huit. Une
 * colonne « nombre de textos » laisserait croire à une enveloppe respectée alors qu'elle est
 * consommée huit fois. Les deux chiffres sont donc montrés côte à côte, et c'est **l'écart entre
 * eux** qui informe.
 *
 * Le dépassement n'est jamais signalé par la seule couleur (WCAG 1.4.1) : il porte un mot.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import TbodyPagine from "./TbodyPagine";

interface Canal { utilises: number; messages: number; limite: number; illimite: boolean; depassement: number; echecs: number }
interface Organisme { tenantId: string; nom: string; textos: Canal; courriels: Canal }
interface Totaux { textosSegments: number; textosMessages: number; courriels: number; depassementTextos: number; depassementCourriels: number }
interface Vue { mois: string; organismes: Organisme[]; totaux: Totaux }
interface LigneContexte { contexte: string; canal: "texto" | "courriel"; messages: number; utilises: number }

/** Le mois courant en `AAAA-MM` — ce que l'API attend, et ce qu'un `<input type="month">` rend. */
function moisCourant(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

const nombre = (n: number) => n.toLocaleString("fr-CA");

/** Ce qu'une enveloppe affiche : « illimité », ou « utilisé / limite ». */
function enveloppe(c: Canal): string {
  return c.illimite ? "illimité" : `${nombre(c.utilises)} / ${nombre(c.limite)}`;
}

export default function ConsommationAvis({
  apiFetch, setError, setSuccess,
}: {
  apiFetch: (path: string, init?: RequestInit) => Promise<Response>;
  setError: (s: string) => void;
  setSuccess: (s: string) => void;
}) {
  const [mois, setMois] = useState<string>(moisCourant());
  const [vue, setVue] = useState<Vue | null>(null);
  const [chargement, setChargement] = useState(false);
  const [detail, setDetail] = useState<{ tenantId: string; nom: string; contextes: LigneContexte[] } | null>(null);
  // Les plafonds en cours d'édition, par organisme : l'écran n'enregistre qu'au clic, pour
  // qu'une frappe en cours ne parte pas au serveur à chaque caractère.
  const [saisie, setSaisie] = useState<Record<string, { textos: string; courriels: string }>>({});

  const charger = useCallback(async () => {
    setChargement(true);
    try {
      const r = await apiFetch(`/api/saas/consommation?mois=${encodeURIComponent(mois)}`);
      if (!r.ok) { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
      setVue(await r.json());
    } finally { setChargement(false); }
  }, [apiFetch, mois, setError]);

  useEffect(() => { void charger(); }, [charger]);

  const plafondDe = (o: Organisme) => saisie[o.tenantId] ?? {
    textos: String(o.textos.limite), courriels: String(o.courriels.limite),
  };

  const enregistrer = async (o: Organisme) => {
    const v = plafondDe(o);
    const r = await apiFetch(`/api/saas/consommation/${encodeURIComponent(o.tenantId)}/quotas`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ textos: Number(v.textos), courriels: Number(v.courriels) }),
    });
    if (!r.ok) { const j = await r.json().catch(() => ({})); setError(j.errors?.join(", ") || `Erreur ${r.status}`); return; }
    setSuccess(`Plafonds enregistrés pour ${o.nom}.`);
    setSaisie((s) => { const c = { ...s }; delete c[o.tenantId]; return c; });
    void charger();
  };

  const ouvrirDetail = async (o: Organisme) => {
    const r = await apiFetch(`/api/saas/consommation/${encodeURIComponent(o.tenantId)}?mois=${encodeURIComponent(mois)}`);
    if (!r.ok) { setError(`Erreur ${r.status}`); return; }
    const j = await r.json();
    setDetail({ tenantId: o.tenantId, nom: o.nom, contextes: j.contextes ?? [] });
  };

  const organismes = vue?.organismes ?? [];
  /** Les dépassements d'abord : c'est ce qu'on vient facturer. */
  const ordonnes = useMemo(
    () => [...organismes].sort((a, b) => (b.textos.depassement + b.courriels.depassement) - (a.textos.depassement + a.courriels.depassement)),
    [organismes],
  );
  const enDepassement = ordonnes.filter((o) => o.textos.depassement > 0 || o.courriels.depassement > 0).length;

  return (
    <div data-testid="sa-consommation">
      <div className="grid-form">
        <label className="field" htmlFor="sa-conso-mois"><span>Mois</span>
          <input id="sa-conso-mois" data-testid="sa-conso-mois" type="month" value={mois}
                 onChange={(e) => setMois(e.target.value || moisCourant())} />
        </label>
        <div className="field field-actions">
          <button data-testid="sa-conso-rafraichir" onClick={() => void charger()} disabled={chargement}>
            {chargement ? "Chargement…" : "Rafraîchir"}
          </button>
        </div>
      </div>

      {/* Une mise à jour asynchrone s'annonce : un lecteur d'écran ne voit pas un tableau changer. */}
      <p role="status" data-testid="sa-conso-resume">
        {vue
          ? `${nombre(vue.totaux.textosSegments)} segment(s) de texto pour ${nombre(vue.totaux.textosMessages)} message(s), `
            + `${nombre(vue.totaux.courriels)} courriel(s) en ${vue.mois}. `
            + (enDepassement > 0 ? `${enDepassement} organisme(s) en dépassement.` : "Aucun dépassement.")
          : "Consommation en cours de chargement."}
      </p>

      <p className="hint">
        Les textos sont comptés en <strong>segments</strong> — l'unité que le fournisseur facture.
        Un message de plus de 160 caractères en occupe plusieurs, et un seul caractère hors de
        l'alphabet des textos (<span lang="fr">« ê », « ô », « ç » minuscule</span>) le fait passer
        à 70 caractères par segment. L'écart entre « segments » et « messages » est donc normal ;
        c'est son ampleur qui renseigne.
      </p>

      <div className="table-wrap" tabIndex={0} role="region" aria-label="Consommation par organisme (zone défilable au clavier)">
        <table data-testid="sa-conso-table">
          <caption className="sr-only">Textos et courriels envoyés par organisme pour le mois choisi, avec les plafonds mensuels et le dépassement facturable</caption>
          <thead>
            <tr>
              <th scope="col">Organisme</th>
              <th scope="col">Textos (segments)</th>
              <th scope="col">Textos (messages)</th>
              <th scope="col">Plafond textos</th>
              <th scope="col">Courriels</th>
              <th scope="col">Plafond courriels</th>
              <th scope="col">Dépassement</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <TbodyPagine
            items={ordonnes}
            colonnes={8}
            testId="sa-conso-pager"
            tbodyTestId="sa-conso-tbody"
            vide={<tr><td colSpan={8} className="empty-cell">Aucun organisme.</td></tr>}
            rendre={(o) => {
              const v = plafondDe(o);
              const depasse = o.textos.depassement + o.courriels.depassement;
              const modifie = saisie[o.tenantId] !== undefined;
              return (
                <tr key={o.tenantId} data-testid={`sa-conso-row-${o.tenantId}`}>
                  <th scope="row">{o.nom}</th>
                  <td data-testid={`sa-conso-segments-${o.tenantId}`}>{nombre(o.textos.utilises)}</td>
                  <td>{nombre(o.textos.messages)}</td>
                  <td>
                    <label className="sr-only" htmlFor={`sa-conso-lim-sms-${o.tenantId}`}>Plafond mensuel de segments de texto pour {o.nom}</label>
                    <input id={`sa-conso-lim-sms-${o.tenantId}`} data-testid={`sa-conso-lim-sms-${o.tenantId}`}
                           type="number" min={0} step={100} value={v.textos}
                           onChange={(e) => setSaisie((s) => ({ ...s, [o.tenantId]: { ...plafondDe(o), textos: e.target.value } }))} />
                  </td>
                  <td data-testid={`sa-conso-courriels-${o.tenantId}`}>{nombre(o.courriels.utilises)}</td>
                  <td>
                    <label className="sr-only" htmlFor={`sa-conso-lim-mail-${o.tenantId}`}>Plafond mensuel de courriels pour {o.nom}</label>
                    <input id={`sa-conso-lim-mail-${o.tenantId}`} data-testid={`sa-conso-lim-mail-${o.tenantId}`}
                           type="number" min={0} step={1000} value={v.courriels}
                           onChange={(e) => setSaisie((s) => ({ ...s, [o.tenantId]: { ...plafondDe(o), courriels: e.target.value } }))} />
                  </td>
                  <td data-testid={`sa-conso-depassement-${o.tenantId}`}>
                    {/* Jamais la couleur seule (WCAG 1.4.1) : le dépassement porte un mot. */}
                    {depasse > 0
                      ? <span className="badge badge-warn">Dépassement : {nombre(depasse)}</span>
                      : <span>Dans l'enveloppe</span>}
                  </td>
                  <td><div className="actions-row">
                    <button data-testid={`sa-conso-detail-${o.tenantId}`} onClick={() => void ouvrirDetail(o)}>Détail</button>
                    <button data-testid={`sa-conso-save-${o.tenantId}`} disabled={!modifie} onClick={() => void enregistrer(o)}>
                      Enregistrer les plafonds
                    </button>
                  </div></td>
                </tr>
              );
            }}
          />
          <tfoot>
            <tr>
              <th scope="row">Total</th>
              <td data-testid="sa-conso-total-segments">{nombre(vue?.totaux.textosSegments ?? 0)}</td>
              <td>{nombre(vue?.totaux.textosMessages ?? 0)}</td>
              {/* Les colonnes de plafond et d'actions n'ont pas de total. La cellule reste
                  **présente et vide** plutôt que masquée : `aria-hidden` sur une cellule la
                  retire de l'arbre d'accessibilité et décale la lecture des colonnes suivantes. */}
              <td />
              <td data-testid="sa-conso-total-courriels">{nombre(vue?.totaux.courriels ?? 0)}</td>
              <td />
              <td>{nombre((vue?.totaux.depassementTextos ?? 0) + (vue?.totaux.depassementCourriels ?? 0))}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </div>

      {detail && (
        <section data-testid="sa-conso-detail" aria-label={`Détail de la consommation de ${detail.nom}`}>
          <h3>Ce qui a consommé — {detail.nom}, {mois}</h3>
          <div className="table-wrap" tabIndex={0} role="region" aria-label="Détail par contexte (zone défilable au clavier)">
            <table>
              <caption className="sr-only">Consommation détaillée par contexte d'envoi</caption>
              <thead><tr><th scope="col">Contexte</th><th scope="col">Canal</th><th scope="col">Messages</th><th scope="col">Unités facturées</th></tr></thead>
              <tbody>
                {detail.contextes.map((l) => (
                  <tr key={`${l.canal}-${l.contexte}`}>
                    <th scope="row">{l.contexte}</th>
                    <td>{l.canal === "texto" ? "Texto" : "Courriel"}</td>
                    <td>{nombre(l.messages)}</td>
                    <td>{nombre(l.utilises)}</td>
                  </tr>
                ))}
                {detail.contextes.length === 0 && <tr><td colSpan={4} className="empty-cell">Aucun envoi ce mois-ci.</td></tr>}
              </tbody>
            </table>
          </div>
          <button data-testid="sa-conso-detail-fermer" onClick={() => setDetail(null)}>Fermer le détail</button>
        </section>
      )}
    </div>
  );
}
