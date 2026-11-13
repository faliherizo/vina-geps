/**
 * **Mes disponibilités** — la page du module `effectifs_disponibilites` côté employé (AO 26-015-DO,
 * lot R4 — E-GN-12, E-GN-15, E-GN-16, E-GR-11).
 *
 * L'employé choisit une période de son unité et y dépose ses plages pendant la collecte. Le
 * serveur confronte le dépôt aux règles de l'organisme : un refus s'affiche **avec ses motifs**,
 * rendus avec les valeurs du dépôt (« Au moins 2 quarts par semaine (1 offert) ») ; un dépôt
 * accepté peut porter un avertissement, ou attendre l'approbation d'un gestionnaire. Hors
 * collecte, le dépôt se relit sans se modifier.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { EnTeteEcran, EtatVide, Message } from "./composants";
import { useApiEffectifs } from "./effectifs/api";
import EditeurPlages, { joursDe, lignesDe, plagesDe, type LignePlage } from "./effectifs/disponibilites/EditeurPlages";
import Motifs, { BadgeConformite } from "./effectifs/disponibilites/Motifs";
import ReglesApplicables from "./effectifs/disponibilites/ReglesApplicables";
import type { Depot, MesDisponibilites as Donnees } from "./effectifs/disponibilites/types";
import type { Motif } from "./effectifs/planification/types";
import "./effectifs/effectifs.css";
import PageMaquette from "./composants/PageMaquette";

const ETATS: Record<string, string> = { COLLECTE: "Collecte ouverte", CONFECTION: "En confection", PUBLIEE: "Horaire publié" };

export default function MesDisponibilites() {
  const { lire, envoyerBrut, erreur } = useApiEffectifs();
  const [donnees, setDonnees] = useState<Donnees | null>(null);
  const [periodeId, setPeriodeId] = useState("");
  const [lignes, setLignes] = useState<LignePlage[]>([]);
  const [refus, setRefus] = useState<Motif[]>([]);
  const [confirme, setConfirme] = useState("");
  const [envoi, setEnvoi] = useState(false);

  const charger = useCallback(async () => {
    const d = await lire<Donnees>("/api/effectifs/disponibilites/moi");
    if (!d) return;
    setDonnees(d);
    // La forme reçue est vérifiée avant usage : une réponse `[]` là où un objet est attendu
    // faisait planter `.some()` et **vidait toute l'application**, sans message.
    const periodes = Array.isArray(d?.periodes) ? d.periodes : [];
    setPeriodeId((id) => (id && periodes.some((p) => p.id === id) ? id : periodes.find((p) => p.etat === "COLLECTE")?.id ?? periodes[0]?.id ?? ""));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const periode = (Array.isArray(donnees?.periodes) ? donnees!.periodes : []).find((p) => p.id === periodeId) ?? null;
  const jours = useMemo(() => (periode ? joursDe(periode.debut, periode.fin) : []), [periode]);
  // Changer de période efface les messages ; recharger après un dépôt ne doit pas effacer la confirmation.
  useEffect(() => { setRefus([]); setConfirme(""); }, [periodeId]);
  // Les lignes repartent du dépôt enregistré quand la période ou le dépôt change — pas à chaque rendu.
  const plagesDeposees = periode?.depot?.plages;
  const versionDepot = `${periodeId}|${periode?.depot?.deposeLe ?? ""}`;
  const [versionChargee, setVersionChargee] = useState("");
  if (versionChargee !== versionDepot) {
    setVersionChargee(versionDepot);
    setLignes(lignesDe(plagesDeposees ?? []));
  }

  const deposer = async () => {
    if (!periode) return;
    setEnvoi(true);
    setConfirme("");
    const r = await envoyerBrut(`/api/effectifs/disponibilites/moi/${periode.id}`, { plages: plagesDe(lignes) }, "PUT");
    setEnvoi(false);
    if (r.statut === 409) { setRefus(r.corps.verdict?.motifs ?? []); return; }
    setRefus([]);
    if (!r.ok) return;
    const depot = r.corps as Depot;
    setConfirme(depot.conformite === "APPROBATION_REQUISE"
      ? "Dépôt enregistré. Il attend l'approbation de votre gestionnaire avant d'entrer dans le bassin d'attribution."
      : "Dépôt enregistré.");
    await charger();
  };

  if (!donnees) return erreur ? <Message variante="erreur" testId="mdi-erreur">{erreur}</Message> : null;

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • disponibilités">
      <EnTeteEcran titre="Mes disponibilités" description="Déposez vos disponibilités pour chaque période, pendant sa collecte — et sachez d\u2019avance ce qui les juge." testId="mdi-titre" />
      {!donnees.ressource && (
        <EtatVide titre="Votre compte n'est relié à aucune ressource planifiable" testId="mdi-sans-ressource">
          Demandez à votre planificateur de relier votre compte à votre dossier d'effectif.
        </EtatVide>
      )}
      {donnees.ressource && donnees.periodes.length === 0 && (
        <EtatVide titre="Aucune période ouverte pour votre unité" testId="mdi-sans-periode">Vous serez avisé à l'ouverture de la prochaine collecte.</EtatVide>
      )}
      {periode && (
        <>
          <div className="eff-barre">
            <p>
              <label htmlFor="mdi-periode">Période</label>
              <select id="mdi-periode" data-testid="mdi-periode" value={periodeId} onChange={(e) => setPeriodeId(e.target.value)}>
                {donnees.periodes.map((p) => <option key={p.id} value={p.id}>{p.nom} ({p.debut} au {p.fin})</option>)}
              </select>
            </p>
            <p data-testid="mdi-etat">{ETATS[periode.etat] ?? periode.etat}{periode.etat === "COLLECTE" && periode.finCollecte ? ` jusqu'au ${periode.finCollecte} inclus` : ""}</p>
            <p><BadgeConformite conformite={periode.depot?.conformite ?? null} testId="mdi-conformite" /></p>
          </div>
          {/* Les règles **avant** le dépôt : elles ne se lisaient qu'au refus, et l'employée
              découvrait la convention en se faisant refuser. */}
          <ReglesApplicables regles={periode.regles ?? []} testId="mdi-regles" />
          {periode.depot && refus.length === 0 && (
            <Motifs motifs={periode.depot.motifs} titre="À surveiller dans votre dépôt" testId="mdi-avertissements" />
          )}
          <Motifs motifs={refus} refus titre="Dépôt refusé : corrigez ce qui suit" testId="mdi-refus" />
          {erreur && refus.length === 0 && <Message variante="erreur" testId="mdi-erreur">{erreur}</Message>}
          {confirme && <Message variante="succes" testId="mdi-confirme">{confirme}</Message>}
          <EditeurPlages prefixe="mdi" jours={jours} lignes={lignes} onChange={setLignes} desactive={periode.etat !== "COLLECTE"} />
          {periode.etat === "COLLECTE" ? (
            <p className="eff-actions">
              <button type="button" data-testid="mdi-deposer" disabled={envoi} onClick={() => void deposer()}>Déposer mes disponibilités</button>
            </p>
          ) : (
            <p data-testid="mdi-lecture-seule">La collecte de cette période est fermée : votre dépôt se consulte sans se modifier.</p>
          )}
        </>
      )}
    </PageMaquette>
  );
}
