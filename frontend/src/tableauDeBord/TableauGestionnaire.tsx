/**
 * **Tableau de bord gestionnaire** (planche s01) : onglets, tuiles chiffrées, table du sujet
 * choisi, action principale.
 *
 * Les tuiles et les onglets désignent les mêmes quatre sujets : cliquer une tuile ouvre son
 * onglet. Chaque ligne porte une action qui **mène** à l'écran où l'on agit (comblement,
 * absences, échanges, présence) : le tableau de bord montre et oriente, il ne recopie pas les
 * formulaires de ces écrans (ADR 0034, une fonction a un foyer).
 */
import { useState, type ReactNode } from "react";
import { BadgeStatut, EtatVide, Onglets, Tableau, Tuile, type Colonne, type Onglet } from "../composants";
import { heure } from "../effectifs/api";
import { useModuleAllowed } from "../moduleAccess";
import { naviguerVers } from "../navigation";
import type { Equipage } from "../effectifs/planification/types";
import type { Echange } from "../effectifs/echanges/types";
import type { Pointage } from "../effectifs/presence/types";
import { chiffre, compteurs, estACombler, FUSEAU_CLIENT, placesACombler, trierParUrgence, type AbsenceATraiter, type Compteurs, type DonneesDuJour, type VueTableau } from "./indicateurs";
import { useTableauDeBord } from "./useTableauDeBord";

const PREFIXE = "tdb";

/** Chaque sujet : son onglet, sa tuile, et le chiffre qui les nourrit. */
const SUJETS: ReadonlyArray<{ id: VueTableau; onglet: string; tuile: string; valeur: (c: Compteurs) => number | null }> = [
  { id: "couverture", onglet: "Couverture des quarts", tuile: "Quarts aujourd'hui", valeur: (c) => c.quarts },
  { id: "absences", onglet: "Absences à traiter", tuile: "Absences à traiter", valeur: (c) => c.absences },
  { id: "demandes", onglet: "Demandes en attente", tuile: "Demandes en attente", valeur: (c) => c.demandes },
  { id: "anomalies", onglet: "Anomalies de présence", tuile: "Pointages à approuver", valeur: (c) => c.anomalies },
];

export default function TableauGestionnaire({ jour }: { jour: string }) {
  const { donnees, chargement } = useTableauDeBord(true, jour);
  const [vue, setVue] = useState<VueTableau>("couverture");
  const c = compteurs(donnees);
  const onglets: Onglet<VueTableau>[] = SUJETS.map((s) => ({ id: s.id, libelle: s.onglet, testId: `tdb-onglet-${s.id}` }));
  return (
    <div className="tdb" aria-busy={chargement}>
      <Onglets onglets={onglets} actif={vue} onChange={setVue} libelle="Sujets du jour" testId="tdb-onglets" prefixeIds={PREFIXE} />
      <ul className="tuiles tdb-tuiles" aria-label="Indicateurs du jour">
        {SUJETS.map((s) => (
          <li key={s.id}>
            <Tuile valeur={chiffre(s.valeur(c))} libelle={s.tuile} onOuvrir={() => setVue(s.id)} libelleOuvrir={`Voir : ${s.onglet}`}
                   periode={s.id === "couverture" && c.aCombler !== null ? `${c.aCombler} place(s) à combler` : undefined}
                   testId={`tdb-tuile-${s.id}`} valeurTestId={`tdb-valeur-${s.id}`} />
          </li>
        ))}
      </ul>
      <section className="card tdb-panneau" role="tabpanel" id={`${PREFIXE}-tabpanel-${vue}`} aria-labelledby={`${PREFIXE}-tabbtn-${vue}`} data-testid={`tdb-panneau-${vue}`}>
        <Panneau vue={vue} donnees={donnees} chargement={chargement} />
      </section>
      <ActionPrincipale />
    </div>
  );
}

function Panneau({ vue, donnees, chargement }: { vue: VueTableau; donnees: DonneesDuJour; chargement: boolean }) {
  if (chargement) return <p role="status">Lecture des indicateurs du jour…</p>;
  switch (vue) {
    case "couverture": return <Couverture equipages={donnees.equipages} milieux={donnees.milieux} />;
    case "absences": return <Absences absences={donnees.absences} />;
    case "demandes": return <Demandes echanges={donnees.echanges} />;
    case "anomalies": return <Anomalies pointages={donnees.pointages} milieux={donnees.milieux} />;
  }
}

/** Un sujet que l'organisme n'a pas souscrit, ou que ce rôle ne lit pas : on le dit, on ne montre pas un faux zéro. */
function NonDisponible({ sujet }: { sujet: string }) {
  return (
    <EtatVide titre={`${sujet} : non disponible`} compact testId="tdb-non-disponible">
      Ce sujet ne fait pas partie des modules de votre organisme, ou votre rôle ne le consulte pas.
    </EtatVide>
  );
}

/** Le bouton d'une ligne : un intitulé court à l'écran, complet pour le lecteur d'écran (2.4.4, 2.5.3). */
function Aller({ libelle, precision, page, principal, testId }: { libelle: string; precision: string; page: string; principal?: boolean; testId: string }) {
  return (
    <button type="button" className={principal ? "primary tdb-action" : "secondary tdb-action"} aria-label={`${libelle} — ${precision}`}
            data-testid={testId} onClick={() => naviguerVers(page)}>{libelle}</button>
  );
}

const COL_COUVERTURE: ReadonlyArray<Colonne<Equipage>> = [
  { id: "heure", libelle: "Heure", tri: (e) => e.debut }, { id: "site", libelle: "Site" },
  { id: "requis", libelle: "Places requises", numerique: true }, { id: "pourvues", libelle: "Pourvues", numerique: true },
  { id: "etat", libelle: "État" }, { id: "action", libelle: "Action", actions: true },
];

function Couverture({ equipages, milieux }: { equipages: Equipage[] | null; milieux: Map<string, string> }) {
  if (equipages === null) return <NonDisponible sujet="Couverture des quarts" />;
  return (
    <Tableau colonnes={COL_COUVERTURE} lignes={trierParUrgence(equipages)} legende="Quarts à couvrir aujourd'hui" legendeVisible testId="tdb-table-couverture"
             vide={<EtatVide titre="Aucun quart planifié aujourd'hui" compact testId="tdb-vide-couverture">Aucune période en cours ne porte d'équipage pour ce jour.</EtatVide>}
             rendre={(e) => <LigneCouverture key={e.id} e={e} site={milieux.get(e.milieuId) ?? "—"} />} />
  );
}

function LigneCouverture({ e, site }: { e: Equipage; site: string }) {
  const quart = `${heure(e.debut)}–${heure(e.fin)}`;
  const aCombler = estACombler(e);
  return (
    <tr data-testid={`tdb-quart-${e.id}`}>
      <td>{quart}</td>
      <td>{site}</td>
      <td className="num">{e.places.length}</td>
      <td className="num">{e.pourvues} / {e.places.length}</td>
      <td>{aCombler
        ? <BadgeStatut statut="en_attente">{e.statut === "SOUS_MINIMUM" ? "Sous le minimum" : `${placesACombler(e)} à combler`}</BadgeStatut>
        : <BadgeStatut statut="active">Opérationnel</BadgeStatut>}</td>
      <td>{aCombler
        ? <Aller libelle="Combler" precision={`quart ${quart}, ${site}`} page="uscomblement" principal testId={`tdb-combler-${e.id}`} />
        : <Aller libelle="Détail" precision={`quart ${quart}, ${site}`} page="usequipages" testId={`tdb-detail-${e.id}`} />}</td>
    </tr>
  );
}

const COL_ABSENCES: ReadonlyArray<Colonne<AbsenceATraiter>> = [
  { id: "personne", libelle: "Personne", tri: (a) => a.ressourceNom }, { id: "code", libelle: "Motif" },
  { id: "periode", libelle: "Période", tri: (a) => a.du }, { id: "action", libelle: "Action", actions: true },
];

function Absences({ absences }: { absences: AbsenceATraiter[] | null }) {
  if (absences === null) return <NonDisponible sujet="Absences à traiter" />;
  return (
    <Tableau colonnes={COL_ABSENCES} lignes={absences} legende="Absences qui attendent une décision" legendeVisible testId="tdb-table-absences"
             vide={<Rien>Aucune absence n'attend de décision.</Rien>}
             rendre={(a) => (
               <tr key={a.id} data-testid={`tdb-absence-${a.id}`}>
                 <td>{a.ressourceNom}</td><td>{a.codeAbsence}</td><td>{a.du === a.au ? a.du : `${a.du} au ${a.au}`}</td>
                 <td><Aller libelle="Décider" precision={`absence de ${a.ressourceNom}`} page="usabsences" testId={`tdb-decider-${a.id}`} /></td>
               </tr>
             )} />
  );
}

const COL_DEMANDES: ReadonlyArray<Colonne<Echange>> = [
  { id: "personnes", libelle: "Échange entre" }, { id: "quart", libelle: "Quart cédé", tri: (e) => e.quartCede.date },
  { id: "propose", libelle: "Proposé le", tri: (e) => e.proposeLe }, { id: "action", libelle: "Action", actions: true },
];

function Demandes({ echanges }: { echanges: Echange[] | null }) {
  if (echanges === null) return <NonDisponible sujet="Demandes en attente" />;
  return (
    <Tableau colonnes={COL_DEMANDES} lignes={echanges.filter((e) => e.statut === "A_VALIDER")} legende="Échanges de quarts à valider" legendeVisible testId="tdb-table-demandes"
             vide={<Rien>Aucun échange n'attend de validation.</Rien>}
             rendre={(e) => (
               <tr key={e.id} data-testid={`tdb-demande-${e.id}`}>
                 <td>{e.nomA} et {e.nomB}</td>
                 <td>{e.quartCede.date}, {heure(e.quartCede.debut)}–{heure(e.quartCede.fin)}</td>
                 <td>{e.proposeLe.slice(0, 10)}</td>
                 <td><Aller libelle="Valider" precision={`échange entre ${e.nomA} et ${e.nomB}`} page="usechanges" testId={`tdb-valider-${e.id}`} /></td>
               </tr>
             )} />
  );
}

/** Ce qu'un pointage a de particulier, en mots. */
const ECARTS: Record<NonNullable<Pointage["ecart"]>, string> = {
  HORS_MILIEU: "Hors du milieu", MAUVAIS_MILIEU: "Mauvais milieu", PREUVE_ABSENTE: "Preuve absente",
};

const COL_ANOMALIES: ReadonlyArray<Colonne<Pointage>> = [
  { id: "personne", libelle: "Personne", tri: (p) => p.nom }, { id: "quand", libelle: "Horodatage", tri: (p) => p.horodatage },
  { id: "site", libelle: "Site" }, { id: "ecart", libelle: "Écart" }, { id: "action", libelle: "Action", actions: true },
];

function Anomalies({ pointages, milieux }: { pointages: Pointage[] | null; milieux: Map<string, string> }) {
  if (pointages === null) return <NonDisponible sujet="Anomalies de présence" />;
  const quand = (h: string) => new Date(h).toLocaleString("fr-CA", { timeZone: FUSEAU_CLIENT, dateStyle: "short", timeStyle: "short" });
  return (
    <Tableau colonnes={COL_ANOMALIES} lignes={pointages} legende="Pointages qui attendent une approbation" legendeVisible testId="tdb-table-anomalies"
             vide={<Rien>Aucun pointage n'attend d'approbation.</Rien>}
             rendre={(p) => (
               <tr key={p.id} data-testid={`tdb-pointage-${p.id}`}>
                 <td>{p.nom}</td><td>{quand(p.horodatage)}</td><td>{(p.milieuId && milieux.get(p.milieuId)) ?? "—"}</td>
                 <td>{p.ecart ? ECARTS[p.ecart] : "À approuver"}</td>
                 <td><Aller libelle="Examiner" precision={`pointage de ${p.nom}`} page="uspresence" testId={`tdb-examiner-${p.id}`} /></td>
               </tr>
             )} />
  );
}

function Rien({ children }: { children: ReactNode }) {
  return <EtatVide titre="Rien à traiter" compact>{children}</EtatVide>;
}

/** « Ouvrir le comblement » : le geste le plus fréquent de la journée, en bas à droite comme sur la planche. */
function ActionPrincipale() {
  const moduleAutorise = useModuleAllowed();
  if (!moduleAutorise("effectifs_planification")) return null;
  return (
    <p className="tdb-action-principale">
      <button type="button" className="primary" data-testid="tdb-action-principale" onClick={() => naviguerVers("uscomblement")}>Ouvrir le comblement</button>
    </p>
  );
}
