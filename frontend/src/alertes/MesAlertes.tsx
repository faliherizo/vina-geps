/**
 * **Mes alertes** — ce que reçoit le destinataire (CU-4, CU-5 ; FX-AO-20260810, critères 9, 10, 20, 24).
 *
 * Les non lues d'abord. Ouvrir une alerte vaut lecture ; une réponse proposée se donne d'un geste, une
 * fois. Le destinataire ne voit que sa propre remise : la liste des autres appartient au suivi.
 */
import { useCallback, useEffect, useState } from "react";
import { BadgeStatut, EnTeteEcran, EtatVide, Message } from "../composants";
import { useApiEffectifs as useApi } from "../effectifs/api";
import { liste } from "../formeRecue";
import { libellePriorite, quand, tonPriorite, type AlerteRecue } from "./types";

export default function MesAlertes() {
  const { lire, envoyer, erreur, succes, setSucces } = useApi();
  const [alertes, setAlertes] = useState<AlerteRecue[] | null>(null);
  const charger = useCallback(async () => setAlertes(liste<AlerteRecue>(await lire<AlerteRecue[]>("/api/alertes/recues"))), [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const marquerLue = async (a: AlerteRecue) => {
    if (a.maRemise.lueLe) return;
    if (await envoyer(`/api/alertes/recues/${a.id}/lue`, {})) await charger();
  };
  const repondre = async (a: AlerteRecue, reponseId: string, libelle: string) => {
    if (await envoyer(`/api/alertes/recues/${a.id}/reponse`, { reponseId })) { setSucces(`Réponse « ${libelle} » envoyée pour « ${a.titre} ».`); await charger(); }
  };

  return (
    <div data-testid="mal-ecran">
      <EnTeteEcran titre="Mes alertes" testId="mal-titre" description="Les alertes qui vous visent, les non lues d'abord. Répondez d'un geste : l'expéditeur le voit aussitôt." />
      {erreur && <Message variante="erreur" testId="mal-erreur">{erreur}</Message>}
      {succes && <Message variante="succes" testId="mal-succes">{succes}</Message>}
      {alertes !== null && alertes.length === 0 && (
        <EtatVide titre="Aucune alerte" testId="mal-vide">Les alertes qu'on vous envoie paraissent ici, et sur votre téléphone selon vos préférences.</EtatVide>
      )}
      <ul className="mal-liste">
        {(alertes ?? []).map((a) => (
          <li key={a.id} className="card" data-testid={`mal-alerte-${a.id}`}>
            <details onToggle={(e) => { if ((e.currentTarget as HTMLDetailsElement).open) void marquerLue(a); }}>
              <summary data-testid={`mal-alerte-${a.id}-resume`}>
                <BadgeStatut statut={tonPriorite(a.priorite)}>{libellePriorite(a.priorite)}</BadgeStatut>{" "}
                <strong>{a.titre}</strong> — {quand(a.envoyeeLe)}{" "}
                {!a.maRemise.lueLe && <BadgeStatut statut="en_attente">Non lue</BadgeStatut>}
              </summary>
              <p>{a.corps}</p>
              <Reponses a={a} repondre={repondre} />
            </details>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Reponses({ a, repondre }: { a: AlerteRecue; repondre: (a: AlerteRecue, id: string, libelle: string) => Promise<void> }) {
  if (a.maRemise.reponseId) {
    const r = a.reponses.find((x) => x.id === a.maRemise.reponseId);
    return <p role="status" data-testid={`mal-alerte-${a.id}-repondue`}>Vous avez répondu « {r?.libelle ?? a.maRemise.reponseId} » le {quand(a.maRemise.reponduLe)}.</p>;
  }
  if (a.reponses.length === 0) return <p className="muted">Aucune réponse n'est attendue : l'alerte est notée comme lue.</p>;
  return (
    <div role="group" aria-label={`Répondre à l'alerte ${a.titre}`} className="eff-actions mal-reponses">
      {a.reponses.map((r) => (
        <button key={r.id} type="button" className={r.semantique === "prise_en_charge" ? "primary" : "secondary"}
          data-testid={`mal-alerte-${a.id}-reponse-${r.id}`} onClick={() => void repondre(a, r.id, r.libelle)}>{r.libelle}</button>
      ))}
    </div>
  );
}
