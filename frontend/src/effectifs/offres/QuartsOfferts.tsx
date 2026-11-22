/**
 * **Quarts qui me sont offerts** (AD-07 ; convention SP-CSN, art. 15.10, 16.02, 16.03 ; ADR 0089) — dans « Mon
 * horaire ».
 *
 * Quand l'organisme offre ses quarts par ancienneté et à tour de rôle, le quart libéré par une absence est offert à
 * une personne à la fois, avec un délai (dix minutes par défaut). L'employé le lit ici, avec l'heure limite, et
 * répond. **Sans réponse dans le délai, le quart est réputé refusé** : l'écran le dit avant le geste, pas après. Le
 * serveur ne rend que le rang de la personne : l'ordre des collègues ne la regarde pas.
 */
import { useCallback, useEffect, useState } from "react";
import { Message } from "../../composants";
import { heure, useApiEffectifs } from "../api";
import { heureLocale, offresRecues, type OffreQuart } from "./types";

export default function QuartsOfferts({ apresReponse }: { apresReponse?: () => void }) {
  const { lire, envoyer, erreur } = useApiEffectifs();
  const [offres, setOffres] = useState<OffreQuart[]>([]);
  const [info, setInfo] = useState("");
  // Une lecture refusée (module fermé, compte sans ressource) tait la section ; un geste refusé se dit toujours.
  const [aRepondu, setARepondu] = useState(false);

  const charger = useCallback(async () => {
    setOffres(offresRecues(await lire<OffreQuart[]>("/api/effectifs/offres/miennes")));
  }, [lire]);
  useEffect(() => { void charger(); }, [charger]);

  const repondre = async (o: OffreQuart, geste: "accepter" | "refuser") => {
    setInfo("");
    setARepondu(true);
    const r = await envoyer<OffreQuart>(`/api/effectifs/offres/${o.id}/${geste}`, {});
    if (r) {
      const moi = r.rangs[0];
      setInfo(geste === "refuser" ? `Vous avez refusé le quart du ${o.quart.date} : il est offert à la personne suivante.`
        : moi?.etat === "ECARTE" ? `Le quart du ${o.quart.date} ne peut pas vous être attribué : ${moi.motif ?? "une règle d'attribution l'empêche"}.`
          : `Le quart du ${o.quart.date} vous est attribué : il paraît dans votre horaire.`);
    }
    await charger();
    apresReponse?.();
  };

  if (offres.length === 0 && !aRepondu) return null;
  return (
    <section aria-labelledby="mho-offres-titre" data-testid="mho-offres">
      <h2 id="mho-offres-titre">Quarts qui me sont offerts</h2>
      {info && <p role="status" data-testid="mho-offres-info">{info}</p>}
      {aRepondu && erreur && <Message variante="erreur" testId="mho-offres-erreur">{erreur}</Message>}
      {offres.length === 0 ? <p data-testid="mho-offres-vide">Aucun quart ne vous est offert en ce moment.</p> : (
        <ul>
          {offres.map((o) => (
            <li key={o.id} data-testid={`mho-offre-${o.quart.date}`}>
              <p>
                <strong>{o.quart.date} · {heure(o.quart.debut)}–{heure(o.quart.fin)}</strong> ({o.quart.uniteCode})
                {o.urgente ? " — quart urgent : le premier qui accepte le prend." : " — offert à votre tour, selon l'ancienneté et le tour de rôle."}
              </p>
              <p>Répondez avant le <strong>{heureLocale(o.rangs[0]?.echeance ?? null)}</strong>. Sans réponse, le quart est réputé refusé.</p>
              <p className="eff-actions">
                <button type="button" data-testid={`mho-offre-accepter-${o.quart.date}`} onClick={() => void repondre(o, "accepter")}>
                  Accepter<span className="sr-only"> le quart du {o.quart.date}</span>
                </button>
                <button type="button" data-testid={`mho-offre-refuser-${o.quart.date}`} onClick={() => void repondre(o, "refuser")}>
                  Refuser<span className="sr-only"> le quart du {o.quart.date}</span>
                </button>
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
