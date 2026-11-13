/**
 * La carte « Recevoir les notifications sur cet appareil » (devis 2.6) : un interrupteur, l'état
 * écrit en toutes lettres, et la raison quand ce n'est pas possible — jamais un bouton qui ne
 * fait rien. Le texto et le courriel restent : la poussée s'ajoute, elle ne remplace pas.
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "./composants";
import { EnvironnementPoussee, EtatPoussee, activer, desactiver, environnementNavigateur, etatActuel } from "./poussee";

const LIBELLE: Record<EtatPoussee, string> = {
  non_supporte: "Ce navigateur ne prend pas en charge les notifications poussées : vous restez avisé par courriel, texto et dans cette page.",
  refuse: "Permission refusée dans le navigateur : autorisez les notifications pour ce site, puis réessayez.",
  inactif: "Les notifications ne sont pas encore poussées vers cet appareil.",
  actif: "Cet appareil reçoit les notifications, écran fermé compris.",
};

interface Props { call: (chemin: string, init?: RequestInit) => Promise<Response>; env?: EnvironnementPoussee }

export default function NotificationsPoussees({ call, env = environnementNavigateur() }: Props) {
  const [etat, setEtat] = useState<EtatPoussee | "chargement">("chargement");
  const [erreur, setErreur] = useState("");
  const [occupe, setOccupe] = useState(false);

  useEffect(() => { let vivant = true; void etatActuel(env).then((e) => { if (vivant) setEtat(e); }); return () => { vivant = false; }; }, [env]);

  const basculer = useCallback(async () => {
    setOccupe(true); setErreur("");
    try {
      if (etat === "actif") setEtat(await desactiver(env, { call }));
      else { const r = await activer(env, { call }); setEtat(r.etat); if (r.erreur) setErreur(r.erreur); }
    } catch (e: any) { setErreur(`Abonnement impossible : ${e?.message ?? "erreur"}`); }
    finally { setOccupe(false); }
  }, [env, call, etat]);

  const actif = etat === "actif";
  return (
    <section className="card" aria-labelledby="poussee-titre" data-testid="poussee-carte">
      <h3 id="poussee-titre">Notifications sur cet appareil</h3>
      <p className="section-help">
        Sur un téléphone, ajoutez l'application à l'écran d'accueil (« Installer ») : les sollicitations, annulations et
        attributions arrivent alors comme des notifications, sans rien télécharger.
      </p>
      <div className="actions-row">
        <button type="button" className={actif ? "secondary" : "primary"} data-testid="poussee-basculer" role="switch" aria-checked={actif}
                disabled={occupe || etat === "chargement" || etat === "non_supporte"} onClick={() => void basculer()}>
          {actif ? "Ne plus recevoir sur cet appareil" : "Recevoir les notifications sur cet appareil"}
        </button>
        <span data-testid="poussee-etat" role="status">{etat === "chargement" ? "Vérification…" : LIBELLE[etat]}</span>
      </div>
      {erreur && <Message variante="erreur" testId="poussee-erreur">{erreur}</Message>}
    </section>
  );
}
