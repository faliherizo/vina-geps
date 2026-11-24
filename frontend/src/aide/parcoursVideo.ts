/**
 * **Les parcours en vidéo de l'aide** — des tâches complètes, d'un écran à l'autre et d'une personne à l'autre, jouées
 * sur l'organisme de démonstration des communications cliniques (Clincom).
 *
 * Une fiche d'écran montre un écran ; un parcours montre une tâche entière : l'alerte envoyée, reçue, prise en charge
 * puis suivie ; l'absence déclarée puis vue au calendrier interne. Chaque vidéo est montée depuis l'essai e2e qui joue
 * le parcours et affirme ce qu'il montre (`e2e/tests/e2e/deploiement-fx-ao-videos.spec.ts`, `scripts/monter-parcours.mjs`).
 * Les identifiants et titres suivent `e2e/donnees/fx-ao/videos-aide.json` ; l'essai `aide-videos.test.tsx` exige le
 * fichier de chaque parcours.
 */
import type { Page } from "../App";

export interface ParcoursVideo {
  id: string;
  titre: string;
  resume: string;
  /** Les écrans montrés : le parcours ne paraît que si l'organisme les a tous. */
  ecrans: readonly Page[];
}

/** Le module de l'offre : les parcours sont joués avec ses données, ils ne servent qu'aux organismes qui l'ont. */
export const MODULE_PARCOURS = "alertes";

export const PARCOURS_VIDEO: readonly ParcoursVideo[] = [
  { id: "configurer", titre: "Configurer les communications cliniques",
    resume: "Les sites, les comptes et leurs profils, les réponses et les modèles d'alerte, les groupes de garde, les relances et les réglages de l'organisme.",
    ecrans: ["usreferentiels", "admin", "access", "alparametres"] },
  { id: "alertes", titre: "Envoyer une alerte, y répondre, la suivre",
    resume: "Le chef d'urgence vise un groupe de garde et une personne ; la médecin de garde prend en charge d'un geste ; le suivi nomme qui a pris en charge.",
    ecrans: ["alnouvelle", "mesalertes", "alsuivi"] },
  { id: "absences", titre: "Déclarer une absence et la voir au calendrier interne",
    resume: "La personne déclare son congé ; l'administration le voit au calendrier interne, qui écarte l'absent des cascades ; la personne l'annule.",
    ecrans: ["mesabsences", "usabsences"] },
  { id: "disponibilites", titre: "Déposer ses disponibilités et suivre les dépôts",
    resume: "Pendant la collecte, l'infirmière dépose ses plages de quart ; le gestionnaire suit les dépôts de la période et leur conformité.",
    ecrans: ["mesdisponibilites", "usdisponibilites"] },
  { id: "quarts", titre: "Quarts supplémentaires : la liste de temps supplémentaire",
    resume: "La personne s'inscrit à la liste de temps supplémentaire ; le gestionnaire traite les demandes et lit la liste d'un jour pour combler un quart.",
    ecrans: ["mesdemandes", "usdemandes"] },
];

export const videoDeParcours = (id: string) => ({ video: `/aide/videos/parcours-${id}.webm`, sousTitres: `/aide/videos/parcours-${id}.fr.vtt` });

export const idParcours = (id: string) => `parcours-${id}`;

/** Les parcours qu'un organisme peut suivre : le module de l'offre, et chacun de leurs écrans au menu. */
export function parcoursVisibles(ouvert: (code?: string) => boolean, ecranOuvert: (p: Page) => boolean): ParcoursVideo[] {
  if (!ouvert(MODULE_PARCOURS)) return [];
  return PARCOURS_VIDEO.filter((p) => p.ecrans.every(ecranOuvert));
}
