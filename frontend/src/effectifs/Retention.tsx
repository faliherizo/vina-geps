/**
 * **Rétention** — onglet du paramétrage des effectifs (AO 26-015-DO, lot R11).
 *
 * L'administration fixe, par classe de données, combien d'années la conserver ; l'écran dit ce que
 * chaque durée purgerait aujourd'hui, avant qu'on la purge. Sans durée, rien ne se purge. La purge
 * est irréversible : elle se confirme, et le journal en garde le décompte.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { EtatVide, Message } from "../composants";
import { liste, objet } from "../formeRecue";
import { useApiEffectifs } from "./api";
import { ConfirmModal, FormulaireModal } from "../Modal";

interface Ligne { classe: string; libelle: string; annees: number | null; limite: string | null; aPurger: number }

export default function Retention() {
  const { lire, envoyer, erreur } = useApiEffectifs();
  const [lignes, setLignes] = useState<Ligne[]>([]);
  const [durees, setDurees] = useState<Record<string, string>>({});
  const [confirmer, setConfirmer] = useState(false);
  const [info, setInfo] = useState("");

  const recevoir = (l: unknown) => {
    const x = liste<Ligne>(l);
    setLignes(x);
    setDurees(Object.fromEntries(x.map((y) => [y.classe, y.annees ? String(y.annees) : ""])));
  };
  const charger = useCallback(async () => recevoir(await lire("/api/effectifs/retention")), [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const enregistrer = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    setInfo("");
    const politique = Object.fromEntries(Object.entries(durees).map(([c, v]) => [c, v.trim() ? Number(v) : null]));
    const r = await envoyer("/api/effectifs/retention", { politique }, "PUT");
    if (r) { fermer(); recevoir(r); setInfo("Politique enregistrée. Rien n'est purgé tant que vous ne lancez pas la purge."); }
  };

  const purger = async () => {
    setInfo("");
    const r = objet<{ purges: Record<string, number> }>(await envoyer("/api/effectifs/retention/appliquer", {}));
    setConfirmer(false);
    if (r) {
      const total = Object.values(r.purges).reduce((t, n) => t + n, 0);
      setInfo(`Purge faite : ${total} enregistrement(s) supprimé(s). Le journal en garde le décompte.`);
      await charger();
    }
  };

  const aPurger = lignes.reduce((t, l) => t + l.aPurger, 0);

  return (
    <div data-testid="ret-ecran">
      <p>Loi 25 : ne conserver les renseignements que le temps nécessaire. Laissez une durée vide pour conserver sans limite.</p>
      {erreur && <Message variante="erreur" testId="ret-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="ret-info">{info}</Message>}
      {lignes.length === 0
        ? <EtatVide titre="Aucune classe de données" compact testId="ret-vide">La politique de rétention n'est pas disponible.</EtatVide>
        : (
          <>
            {/* La page **lit** la politique — ce qu'elle garde, ce qu'elle purgerait aujourd'hui ;
                la modifier se fait dans un dialogue. Éditée en place, la colonne « Purgé
                aujourd'hui » affichait le décompte de la politique enregistrée à côté d'une durée
                qu'on était en train de changer : deux vérités sur la même ligne. */}
            <table className="eff-table-compacte" data-testid="ret-table">
              <caption>Durée de conservation par classe de données</caption>
              <thead><tr><th scope="col">Classe</th><th scope="col">Conservation</th><th scope="col">Purgé aujourd'hui</th></tr></thead>
              <tbody>
                {lignes.map((l) => (
                  <tr key={l.classe} data-testid={`ret-ligne-${l.classe}`}>
                    <th scope="row">{l.libelle}</th>
                    <td>{l.annees ? `${l.annees} an${l.annees > 1 ? "s" : ""}` : "sans limite"}</td>
                    <td>{l.limite ? `${l.aPurger} (antérieur au ${l.limite})` : "conservé"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="eff-actions">
              <FormulaireModal libelle="Modifier la politique" titre="Politique de rétention" testId="ret-politique" icone="✎">
                {(fermer) => (
                  <form onSubmit={(e) => void enregistrer(e, fermer)}>
                    <p className="aide">Une durée vide conserve sans limite. Rien n'est purgé à l'enregistrement : la purge se lance à part, et se confirme.</p>
                    <table className="eff-table-compacte">
                      <caption>Années de conservation par classe de données</caption>
                      <thead><tr><th scope="col">Classe</th><th scope="col">Années</th></tr></thead>
                      <tbody>
                        {lignes.map((l) => (
                          <tr key={l.classe}>
                            <th scope="row"><label htmlFor={`ret-${l.classe}`}>{l.libelle}</label></th>
                            <td><input id={`ret-${l.classe}`} type="number" min={1} max={30} step={1} data-testid={`ret-${l.classe}`} value={durees[l.classe] ?? ""}
                              onChange={(e) => setDurees((d) => ({ ...d, [l.classe]: e.target.value }))} /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {erreur && <Message variante="erreur" testId="ret-p-erreur">{erreur}</Message>}
                    <p className="eff-actions"><button type="submit" data-testid="ret-enregistrer">Enregistrer la politique</button></p>
                  </form>
                )}
              </FormulaireModal>
              <button type="button" className="secondary" data-testid="ret-purger" disabled={aPurger === 0} onClick={() => setConfirmer(true)}>Purger maintenant ({aPurger})</button>
            </div>
          </>
        )}
      {/* **Une suppression définitive se confirme dans un vrai dialogue.** Elle se confirmait dans
          un `<div role="alertdialog">` posé dans la page : il ne prenait pas le focus et ne le
          retenait pas — un lecteur d'écran ne l'entendait pas s'ouvrir, et le geste irréversible
          restait à portée de Tab derrière lui. */}
      <ConfirmModal open={confirmer} title="Purger définitivement" variant="danger" testId="ret-confirmer"
        message={<><strong>{aPurger} enregistrement(s) seront supprimés définitivement.</strong> Cette opération ne se défait pas, et le journal en garde le décompte.</>}
        confirmLabel="Supprimer définitivement" onConfirm={purger} onCancel={() => setConfirmer(false)} />
    </div>
  );
}
