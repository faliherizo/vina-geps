/**
 * **Tableau de comblement** (AO 26-015-DO, écran E10 — E-GN-2, E-GR-1, E-GR-8).
 *
 * Tout ce qui s'affiche ici se calcule à la lecture, côté serveur : taux, quarts sous leur minimum,
 * quotas atteints ou non, jour par jour. Les tuiles sont des sections titrées, jamais des images ;
 * chaque chiffre porte son unité en texte.
 *
 * **Chaque quart sous minimum se comble d'ici** (8 novembre 2026). Les planches s01 et s05 portent
 * une colonne « Action » par quart ; l'écran livré n'en avait aucune : il montrait les véhicules
 * qui risquaient de ne pas sortir sans permettre d'y mettre quelqu'un — il fallait retrouver le
 * même quart dans une autre page. « Combler » ouvre le tiroir d'attribution de « Quarts et
 * équipages », sur la première place vacante du quart : le même formulaire, les mêmes règles.
 */
import { useEffect, useState } from "react";
import { Badge, EtatVide, Message, Tableau, Tuile, type Colonne } from "../../composants";
import { heure, useApiEffectifs } from "../api";
import { liste } from "../../formeRecue";
import { TiroirAttribution } from "./Equipages";
import type { Comblement as DonneesComblement, Equipage, Periode, Place, Ressource } from "./types";
import { useReferentiels } from "./useReferentiels";

type LigneQuota = { cle: string; date: string; quota: number | null; origine: string | null; operationnels: number; atteint: boolean };

export default function Comblement({ periode }: { periode: Periode | null }) {
  const { lire, erreur } = useApiEffectifs();
  const { milieux } = useReferentiels([]);
  const [donnees, setDonnees] = useState<DonneesComblement | null>(null);
  const [version, setVersion] = useState(0);
  const [ressources, setRessources] = useState<Ressource[]>([]);
  const [attribution, setAttribution] = useState<{ equipage: Equipage; place: Place } | null>(null);
  const [succes, setSucces] = useState("");

  useEffect(() => {
    if (!periode) { setDonnees(null); return; }
    void lire<DonneesComblement>(`/api/effectifs/planification/periodes/${periode.id}/comblement`).then(setDonnees);
  }, [lire, periode, version]);
  useEffect(() => { void lire<Ressource[]>("/api/effectifs/planification/ressources").then((r) => setRessources(liste(r))); }, [lire]);

  /** Le quart de la ligne, relu avec ses places : le comblement ne rend que des compteurs. */
  const combler = async (s: DonneesComblement["sousMinimum"][number]) => {
    if (!periode) return;
    setSucces("");
    const equipages = liste<Equipage>(await lire<Equipage[]>(`/api/effectifs/planification/periodes/${periode.id}/equipages?date=${s.date}`));
    const equipage = equipages.find((e) => e.id === s.equipageId);
    const place = equipage?.places.find((p) => !p.ressourceId);
    if (equipage && place) setAttribution({ equipage, place });
    // Le quart a pu être comblé ailleurs entre la lecture et le clic : le dire, et relire.
    else { setSucces("Ce quart n'a plus de place vacante : le tableau est relu."); setVersion((v) => v + 1); }
  };

  if (!periode) return <EtatVide titre="Choisissez une période" compact testId="pla-comblement-sans-periode">Le comblement se calcule période par période.</EtatVide>;
  if (!donnees) return erreur ? <Message variante="erreur" testId="pla-comblement-erreur">{erreur}</Message> : null;

  const milieu = (id: string) => milieux.find((m) => m.id === id)?.libelle ?? id;
  const manques: LigneQuota[] = donnees.quotas.flatMap((q) => q.jours.filter((j) => !j.atteint).map((j) => ({ cle: q.cle, ...j })));

  const colonnesSous: ReadonlyArray<Colonne<DonneesComblement["sousMinimum"][number]>> = [
    { id: "quand", libelle: "Quart", tri: (s) => `${s.date}${s.debut}` },
    { id: "milieu", libelle: "Milieu" },
    { id: "places", libelle: "Pourvues / minimum", numerique: true },
    { id: "mode", libelle: "Attribution" },
    { id: "actions", libelle: "Actions", actions: true },
  ];
  const colonnesQuotas: ReadonlyArray<Colonne<LigneQuota>> = [
    { id: "date", libelle: "Jour", tri: (l) => l.date },
    { id: "quota", libelle: "Quota (unité | plage | nature)" },
    { id: "valeur", libelle: "Opérationnels / quota", numerique: true },
    { id: "origine", libelle: "Origine du quota" },
  ];

  return (
    <div data-testid="pla-comblement">
      {succes && <Message variante="succes" testId="pla-comblement-succes">{succes}</Message>}
      <div className="eff-tuiles">
        <Tuile libelle="Taux de comblement" valeur={`${donnees.taux.toLocaleString("fr-CA")} %`} periode={`${donnees.pourvues} places pourvues sur ${donnees.places}`} testId="pla-tuile-taux" />
        <Tuile libelle="Quarts sous le minimum" valeur={`${donnees.sousMinimum.length} quart(s)`} periode={`sur ${donnees.equipages}`}
          alerte={donnees.sousMinimum.length ? "Des véhicules risquent de ne pas sortir" : undefined} testId="pla-tuile-sous-minimum" />
        <Tuile libelle="Jours sous le quota" valeur={`${manques.length} jour(s)`} testId="pla-tuile-quotas" />
      </div>
      <h3>Quarts sous leur minimum opérationnel</h3>
      <Tableau colonnes={colonnesSous} lignes={donnees.sousMinimum} legende="Quarts sous leur minimum opérationnel" testId="pla-sous-minimum"
        vide={<EtatVide titre="Tous les quarts sont opérationnels" compact testId="pla-sous-minimum-vide">Aucun quart n'est sous son minimum.</EtatVide>}
        rendre={(s) => (
          <tr key={s.equipageId} data-testid={`pla-sous-minimum-${s.equipageId}`}>
            <td>{s.date} · {heure(s.debut)}–{heure(s.fin)}</td>
            <td>{milieu(s.milieuId)}</td>
            <td className="col-numerique">{s.pourvues} / {s.minimum}</td>
            <td>{s.mode === "AUTOMATISE" ? "Automatisée" : "Manuelle"}</td>
            <td>
              {/* L'intitulé **nomme la ligne** (WCAG 2.4.9) : vingt boutons « Combler » ne se
                  distinguent pas au lecteur d'écran. */}
              <button type="button" data-testid={`pla-sous-minimum-${s.equipageId}-combler`} onClick={() => void combler(s)}>
                Combler<span className="sr-only"> le quart du {s.date} de {heure(s.debut)} à {heure(s.fin)}, {milieu(s.milieuId)}</span>
              </button>
            </td>
          </tr>
        )} />
      <h3>Jours où un quota n'est pas atteint</h3>
      <Tableau colonnes={colonnesQuotas} lignes={manques} legende="Jours où un quota n'est pas atteint" testId="pla-quotas-manques"
        vide={<EtatVide titre="Tous les quotas sont atteints" compact testId="pla-quotas-manques-vide">Aucun jour sous son quota.</EtatVide>}
        rendre={(l) => (
          <tr key={`${l.cle}-${l.date}`}>
            <td>{l.date}</td>
            <td>{l.cle.replace(/\*/g, "toutes")}</td>
            <td className="col-numerique">{l.operationnels} / {l.quota}</td>
            <td>{l.origine === "SURCHARGE" ? <Badge ton="info">Surcharge temporaire</Badge> : "Quota en vigueur"}</td>
          </tr>
        )} />
      {attribution && (
        <TiroirAttribution attribution={attribution}
          ressources={ressources.filter((r) => r.actif && r.corpsEmploiCode === attribution.place.corpsEmploiCode)}
          onFermer={() => setAttribution(null)}
          onFait={async (message) => { setAttribution(null); setSucces(message); setVersion((v) => v + 1); }} />
      )}
    </div>
  );
}
