/**
 * **La lecture du tableau de bord gestionnaire** — uniquement des routes qui existent déjà.
 *
 * Chaque lecture n'a lieu que si l'organisme a le module qui la sert : interroger une route dont
 * le module est coupé produirait un refus, et un refus n'est pas une information pour le
 * gestionnaire. Une lecture refusée ou en échec vaut `null` (« — » à l'écran), pas zéro.
 *
 * Les lectures partent en parallèle : quatre sujets indépendants, aucun n'attend l'autre.
 */
import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { useModuleAllowed } from "../moduleAccess";
import { liste } from "../formeRecue";
import type { Equipage, Periode } from "../effectifs/planification/types";
import type { Echange } from "../effectifs/echanges/types";
import type { Pointage } from "../effectifs/presence/types";
import { AUCUNE_DONNEE, periodesDuJour, type AbsenceATraiter, type DonneesDuJour } from "./indicateurs";

type Lecteur = <T>(chemin: string) => Promise<T[] | null>;

/** Lit une liste ; `null` si la route refuse ou échoue. */
function lecteurDe(apiFetch: (chemin: string) => Promise<Response>): Lecteur {
  return async <T,>(chemin: string) => {
    try {
      const r = await apiFetch(chemin);
      return r.ok ? liste<T>(await r.json()) : null;
    } catch {
      return null;
    }
  };
}

/** Les équipages du jour, toutes périodes en cours confondues. */
async function equipagesDuJour(lire: Lecteur, jour: string): Promise<Equipage[] | null> {
  const periodes = await lire<Periode>("/api/effectifs/planification/periodes");
  if (periodes === null) return null;
  const parPeriode = await Promise.all(periodesDuJour(periodes, jour)
    .map((p) => lire<Equipage>(`/api/effectifs/planification/periodes/${encodeURIComponent(p.id)}/equipages?date=${jour}`)));
  return parPeriode.some((x) => x === null) ? null : parPeriode.flatMap((x) => x ?? []);
}

/** Le nom de chaque milieu, pour écrire « Centre-ville » plutôt qu'un identifiant (mémoire « identifiants bruts »). */
async function nomsDesMilieux(lire: Lecteur): Promise<Map<string, string>> {
  const milieux = await lire<{ id: string; libelle: string }>("/api/effectifs/milieux");
  return new Map((milieux ?? []).map((m) => [m.id, m.libelle]));
}

const rien = async () => null;

export function useTableauDeBord(actif: boolean, jour: string): { donnees: DonneesDuJour; chargement: boolean } {
  const { apiFetch } = useAuth();
  const moduleAutorise = useModuleAllowed();
  const [donnees, setDonnees] = useState<DonneesDuJour>(AUCUNE_DONNEE);
  const [chargement, setChargement] = useState(actif);
  const avec = (module: string) => actif && moduleAutorise(module);
  const cles = ["effectifs_planification", "effectifs_absences", "effectifs_echanges", "effectifs_presence", "effectifs"].map(avec).join();

  useEffect(() => {
    if (!actif) return;
    let vivant = true;
    const lire = lecteurDe(apiFetch);
    void Promise.all([
      avec("effectifs_planification") ? equipagesDuJour(lire, jour) : rien(),
      avec("effectifs_absences") ? lire<AbsenceATraiter>("/api/effectifs/absences?statut=A_APPROUVER") : rien(),
      avec("effectifs_echanges") ? lire<Echange>("/api/effectifs/echanges") : rien(),
      avec("effectifs_presence") ? lire<Pointage>("/api/effectifs/presence/pointages?statut=A_APPROUVER") : rien(),
      avec("effectifs") ? nomsDesMilieux(lire) : Promise.resolve(new Map<string, string>()),
    ]).then(([equipages, absences, echanges, pointages, milieux]) => {
      if (!vivant) return;
      setDonnees({ equipages, absences, echanges, pointages, milieux });
      setChargement(false);
    });
    return () => { vivant = false; };
    // `avec` dépend des modules et du rôle : `cles` les résume en une valeur comparable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiFetch, jour, cles]);

  return { donnees, chargement };
}
