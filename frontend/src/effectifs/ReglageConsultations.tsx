/**
 * **Journaliser les consultations** (AO 26-015-DO, addenda 8, M-10) : toute modification est déjà journalisée ; la
 * consultation des dossiers et des horaires doit **pouvoir** l'être, de façon paramétrable. L'organisme l'active ici ;
 * chaque lecture d'un dossier ou d'un horaire entre alors au journal (action « consultation »), avec son lecteur.
 */
import { useEffect, useState } from "react";
import { Message } from "../composants";
import { useApiEffectifs } from "./api";

const API = "/api/effectifs/journal/reglages";

export default function ReglageConsultations() {
  const { lire, envoyer, erreur, succes, setSucces } = useApiEffectifs();
  const [actif, setActif] = useState<boolean | null>(null);
  useEffect(() => { void lire<{ consultations: boolean }>(API).then((r) => setActif(r ? r.consultations : null)); }, [lire]);
  // Le réglage est réservé à qui configure : un refus de lecture masque la section, sans erreur à l'écran.
  if (actif === null) return null;
  const basculer = async (valeur: boolean) => {
    const r = await envoyer<{ consultations: boolean }>(API, { consultations: valeur }, "PUT");
    if (r) {
      setActif(r.consultations);
      setSucces(r.consultations ? "Les consultations de dossiers et d'horaires sont désormais journalisées." : "Les consultations ne sont plus journalisées.");
    }
  };
  return (
    <section aria-labelledby="jrn-consultations-titre" data-testid="jrn-consultations">
      <h2 id="jrn-consultations-titre">Consultations</h2>
      <p className="eff-case">
        <input id="jrn-consultations-actif" type="checkbox" data-testid="jrn-consultations-actif" checked={actif}
          aria-describedby="jrn-consultations-aide" onChange={(e) => void basculer(e.target.checked)} />
        <label htmlFor="jrn-consultations-actif">Journaliser la consultation des dossiers et des horaires</label>
      </p>
      <p className="aide" id="jrn-consultations-aide">Chaque lecture d'un dossier de ressource ou d'un horaire de période entre au journal, avec son lecteur (action « consultation »).</p>
      {succes && <Message variante="succes" testId="jrn-consultations-succes">{succes}</Message>}
      {erreur && <Message variante="erreur" testId="jrn-consultations-erreur">{erreur}</Message>}
    </section>
  );
}
