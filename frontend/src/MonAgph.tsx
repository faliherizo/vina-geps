/**
 * **Mon AGPH** — la page du module `effectifs_agph` côté employé (AO 26-015-DO, lot R7 — P-CH-1,
 * P-CH-2, E-CH-8).
 *
 * À son tour de rôle, la personne voit les postes encore offerts — admissibles ou non, et pourquoi —
 * et en choisit **un** avant la fin de son délai ; sinon le choix se fera pour elle. Elle voit aussi
 * les remplacements long terme ouverts à son corps d'emploi, et s'y manifeste ou se retire.
 * Pendant le dépôt (et la révision) de la campagne, elle émet sa semaine type de disponibilités pour
 * l'année, jugée par les règles de la convention et le budget de la campagne (E-CH-1).
 */
import { useCallback, useEffect, useState } from "react";
import { Badge, BadgeStatut, EnTeteEcran, EtatVide, Message } from "./composants";
import { useApiEffectifs } from "./effectifs/api";
import { SansDossierEffectif, useMaRessource } from "./effectifs/maRessource";
import {
  depotOuvert, instant, LIBELLE_CAMPAGNE, LIBELLE_RANG, LIBELLE_REMPLACEMENT, type Campagne, type DepotCampagne, type PlageType, type PostesDisponibles, type Remplacement,
} from "./effectifs/agph/types";
import SemaineType from "./effectifs/agph/SemaineType";
import { objet } from "./formeRecue";
import "./effectifs/effectifs.css";
import PageMaquette from "./composants/PageMaquette";

export default function MonAgph() {
  const { lire, envoyer, envoyerBrut, apiFetch, erreur, setErreur } = useApiEffectifs();
  // Même question que sur la feuille de temps : sans dossier d'effectif, on ne figure dans
  // aucune campagne — et « Aucune campagne en cours pour vous » laisse attendre la prochaine.
  const moi = useMaRessource();
  const [depots, setDepots] = useState<Record<string, DepotCampagne | null>>({});
  const [erreursDepot, setErreursDepot] = useState<Record<string, string>>({});
  const [campagnes, setCampagnes] = useState<Campagne[] | null>(null);
  const [vues, setVues] = useState<Record<string, PostesDisponibles>>({});
  const [remplacements, setRemplacements] = useState<Remplacement[]>([]);
  const [info, setInfo] = useState("");

  const charger = useCallback(async () => {
    const c = (await lire<Campagne[]>("/api/effectifs/agph/campagnes")) ?? [];
    setCampagnes(c);
    const entrees = await Promise.all(c.filter((x) => x.etat !== "PREPARATION").map(async (x) => [x.id, await lire<PostesDisponibles>(`/api/effectifs/agph/campagnes/${x.id}/postes-disponibles`)] as const));
    setVues(Object.fromEntries(entrees.filter((e): e is readonly [string, PostesDisponibles] => !!e[1])));
    const ouvertes = c.filter((x) => depotOuvert(x.etat));
    setDepots(Object.fromEntries(await Promise.all(ouvertes.map(async (x) => [x.id, objet<DepotCampagne>(await lire<DepotCampagne | null>(`/api/effectifs/agph/campagnes/${x.id}/disponibilites/moi`))] as const))));
    setRemplacements((await lire<Remplacement[]>("/api/effectifs/agph/remplacements?etat=OUVERT")) ?? []);
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const choisir = async (campagneId: string, posteId: string, code: string) => {
    setInfo("");
    if (await envoyer(`/api/effectifs/agph/campagnes/${campagneId}/choix`, { posteId })) { setInfo(`Poste ${code} choisi. Votre choix est définitif pour cette campagne.`); await charger(); }
  };
  const manifester = async (r: Remplacement, retirer: boolean) => {
    setInfo(""); setErreur("");
    const rep = await apiFetch(`/api/effectifs/agph/remplacements/${r.id}/interet`, { method: retirer ? "DELETE" : "POST" });
    const j = await rep.json().catch(() => ({}));
    if (!rep.ok) { setErreur(j.errors?.join(", ") || `Erreur ${rep.status}`); return; }
    setInfo(retirer ? `Intérêt retiré pour le remplacement du poste ${r.posteCode}.` : `Intérêt enregistré pour le remplacement du poste ${r.posteCode}.`);
    await charger();
  };

  const deposer = async (c: Campagne, plages: PlageType[]): Promise<DepotCampagne | null> => {
    setInfo("");
    const r = await envoyerBrut(`/api/effectifs/agph/campagnes/${c.id}/disponibilites/moi`, { plages }, "PUT");
    // Le refus s'affiche sous l'éditeur, là où il se corrige — pas une seconde fois en tête de page.
    setErreur("");
    setErreursDepot((e) => ({ ...e, [c.id]: r.ok ? "" : r.corps.errors?.join(", ") || `Erreur ${r.statut}` }));
    if (!r.ok) return null;
    setInfo(`Disponibilités enregistrées pour la campagne « ${c.nom} ».`);
    return r.corps as DepotCampagne;
  };

  if (campagnes === null) return erreur ? <Message variante="erreur" testId="agm-erreur">{erreur}</Message> : null;

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • confection annuelle (AGPH)">
      <EnTeteEcran titre="Mon AGPH" description="Votre tour de rôle pour le choix des postes, et les remplacements long terme ouverts." testId="agm-titre" />
      {erreur && <Message variante="erreur" testId="agm-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="agm-info">{info}</Message>}

      {moi === null && <SansDossierEffectif testId="agm-sans-ressource" />}

      <h2>Campagnes</h2>
      {/* L'attente n'est pas la même chose que l'absence de dossier : promettre un avis
          « à l'ouverture de la prochaine campagne » à quelqu'un qui n'y figurera jamais lui fait
          attendre un courriel qui ne viendra pas. */}
      {moi !== null && Object.keys(vues).length === 0 && <EtatVide titre="Aucune campagne en cours pour vous" compact testId="agm-sans-campagne">Vous serez avisé à l'ouverture de la prochaine campagne.</EtatVide>}
      {campagnes.filter((c) => vues[c.id]).map((c) => {
        const v = vues[c.id];
        return (
          <section key={c.id} className="eff-groupe" aria-labelledby={`agm-c-${c.id}`} data-testid={`agm-campagne-${c.id}`}>
            <h3 id={`agm-c-${c.id}`}>{c.nom} — {c.uniteCode}</h3>
            <p>
              <BadgeStatut statut={c.etat === "CLOSE" ? "annulee" : "en_attente"}>{LIBELLE_CAMPAGNE[c.etat]}</BadgeStatut>{" "}
              {v.monRang
                ? <span data-testid={`agm-rang-${c.id}`}>Votre rang : {v.monRang.rang} ({LIBELLE_RANG[v.monRang.etat]}){v.monRang.etat === "A_SON_TOUR" ? ` — jusqu'au ${instant(v.monRang.tourFin)}` : ""}</span>
                : <span>Vous ne figurez pas dans cette campagne.</span>}
            </p>
            {v.monRang && depotOuvert(c.etat) && c.id in depots && (
              <section className="eff-groupe" aria-labelledby={`agm-dispo-${c.id}`} data-testid={`agm-dispo-${c.id}`}>
                <h4 id={`agm-dispo-${c.id}`}>Mes disponibilités pour l'année (semaine type)</h4>
                <p className="aide">Dites quels jours et à quelles heures vous pouvez travailler. Les règles de la convention collective et le budget de la campagne jugent votre dépôt.</p>
                <SemaineType prefixe={`agm-dispo-${c.id}`} depot={depots[c.id]} erreur={erreursDepot[c.id] ?? ""} libelleBouton="Enregistrer mes disponibilités"
                  onEnregistrer={(plages) => deposer(c, plages)} />
              </section>
            )}
            {v.peutChoisir && <Message variante="info" testId={`agm-tour-${c.id}`}>C'est votre tour : choisissez un seul poste avant la fin de votre délai, sinon le choix se fera pour vous selon l'ordre des postes.</Message>}
            <ul className="eff-places" aria-label={`Postes encore offerts — ${c.nom}`}>
              {v.postes.map((p) => (
                <li key={p.id} data-testid={`agm-poste-${p.code}`}>
                  <strong>{p.code}</strong> — {p.libelle} ({p.statutEmploiCode}){" "}
                  {p.admissible ? <Badge ton="succes">Admissible</Badge> : <Badge ton="neutre">Non admissible : {p.motifs.join(" ; ")}</Badge>}{" "}
                  {v.peutChoisir && p.admissible && (
                    <button type="button" data-testid={`agm-choisir-${p.code}`} onClick={() => void choisir(c.id, p.id, p.code)}>
                      Choisir<span className="sr-only"> le poste {p.code}</span>
                    </button>
                  )}
                </li>
              ))}
            </ul>
          </section>
        );
      })}

      <h2>Remplacements long terme ouverts</h2>
      {remplacements.length === 0
        ? <EtatVide titre="Aucun remplacement ouvert à votre corps d'emploi" compact testId="agm-sans-remplacement">Les remplacements longs s'affichent ici pendant leur fenêtre de manifestation.</EtatVide>
        : (
          <ul className="eff-places" aria-label="Remplacements long terme ouverts">
            {remplacements.map((r) => (
              <li key={r.id} data-testid={`agm-rlt-${r.id}`}>
                Poste <strong>{r.posteCode}</strong>, du {r.debut} au {r.fin} — {LIBELLE_REMPLACEMENT[r.etat]}, manifestations jusqu'au {instant(r.finManifestation)}{" "}
                <span className="eff-actions">
                  <button type="button" data-testid={`agm-rlt-${r.id}-manifester`} onClick={() => void manifester(r, false)}>Manifester mon intérêt<span className="sr-only"> pour le poste {r.posteCode}</span></button>
                  <button type="button" className="secondary" data-testid={`agm-rlt-${r.id}-retirer`} onClick={() => void manifester(r, true)}>Retirer mon intérêt<span className="sr-only"> pour le poste {r.posteCode}</span></button>
                </span>
              </li>
            ))}
          </ul>
        )}
    </PageMaquette>
  );
}
