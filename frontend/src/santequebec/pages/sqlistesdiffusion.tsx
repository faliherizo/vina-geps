/**
 * **Listes de distribution** — nommées, privées ou partagées, composées de personnes et de groupes.
 *
 * Un membre de nature « groupe » se déplie **à l'envoi** : une arrivée dans le service est jointe
 * sans que personne ait touché à la liste. C'est la différence entre une liste et une copie.
 */
import { useState } from "react";
import { page, type ContexteSection, type Section } from "../noyau";

/** Les natures d'un membre de liste : une personne, ou un groupe déplié à l'envoi. */
const NATURES = [
  { code: "personne", libelle: "Une personne" },
  { code: "groupe", libelle: "Un groupe (déplié à l'envoi)" },
] as const;

/**
 * **Créer et composer une liste** (BF-033).
 *
 * L'écran listait sans permettre d'en créer une : il renvoyait à l'écran historique. La
 * composition se fait membre par membre, et la **nature** du membre est le point du besoin : un
 * membre « groupe » se déplie à chaque envoi, de sorte qu'une arrivée dans le service soit jointe
 * sans que personne ait touché à la liste. Une copie de noms ne fait pas cela.
 */
function Composer({ ctx }: { ctx: ContexteSection }) {
  const [nom, setNom] = useState("");
  const [partagee, setPartagee] = useState(false);
  const [nature, setNature] = useState<string>("personne");
  const [cible, setCible] = useState("");
  const [membres, setMembres] = useState<Array<{ nature: string; cible: string }>>([]);

  const ajouter = () => {
    const valeur = cible.trim();
    if (!valeur) { ctx.signaler("Indiquez la personne ou le groupe à ajouter."); return; }
    if (membres.some((m) => m.nature === nature && m.cible === valeur)) {
      ctx.signaler(`${valeur} est déjà dans la composition.`);
      return;
    }
    setMembres([...membres, { nature, cible: valeur }]);
    setCible("");
  };

  return (
    <form onSubmit={async (e) => {
      e.preventDefault();
      if (membres.length === 0) { ctx.signaler("Une liste vide ne sert à rien : ajoutez un membre."); return; }
      const creee = await ctx.appeler("/api/communication/listes-distribution", {
        method: "POST",
        body: JSON.stringify({ nom, partagee, membres }),
      });
      if (creee?.id) {
        ctx.annoncer(`Liste « ${nom} » créée avec ${membres.length} membre(s)`
          + (partagee ? ", partagée en lecture." : ", privée."));
        setNom(""); setMembres([]); setPartagee(false);
        ctx.recharger();
      }
    }}>
      <div className="sq-rangee">
        <div className="sq-champ">
          <label htmlFor="sq-liste-nom">Nom de la liste <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></label>
          <input id="sq-liste-nom" required data-testid="sq-liste-nom" value={nom}
                 onChange={(e) => setNom(e.target.value)} />
        </div>
        <div className="sq-champ">
          <label htmlFor="sq-liste-nature">Nature du membre</label>
          <select id="sq-liste-nature" data-testid="sq-liste-nature" value={nature}
                  onChange={(e) => setNature(e.target.value)}>
            {NATURES.map((n) => <option key={n.code} value={n.code}>{n.libelle}</option>)}
          </select>
        </div>
        <div className="sq-champ">
          <label htmlFor="sq-liste-cible">Personne ou groupe</label>
          <input id="sq-liste-cible" aria-describedby="sq-liste-cible-aide" data-testid="sq-liste-cible"
                 value={cible} onChange={(e) => setCible(e.target.value)} />
          <p className="sq-champ-aide" id="sq-liste-cible-aide">
            Un membre « groupe » n'est pas une copie de ses membres : il se déplie à l'envoi.
          </p>
        </div>
      </div>

      <fieldset className="sq-groupe">
        <legend>Portée de la liste</legend>
        <div className="sq-champ">
          <label htmlFor="sq-liste-partagee">
            <input id="sq-liste-partagee" type="checkbox" data-testid="sq-liste-partagee"
                   checked={partagee} onChange={(e) => setPartagee(e.target.checked)} />
            {" "}Partager la liste en lecture
          </label>
          <p className="sq-champ-aide">
            Partager, c'est donner à lire : vous restez seul à la modifier, même partagée.
          </p>
        </div>
      </fieldset>

      <div className="sq-actions">
        <button type="button" data-testid="sq-liste-ajouter" onClick={ajouter}>
          Ajouter ce membre à la composition
        </button>
        <button type="submit" data-testid="sq-liste-creer">Créer la liste</button>
      </div>

      {membres.length === 0
        ? <p className="sq-vide" data-testid="sq-liste-composition-vide">
            La composition est vide : la liste n'atteindrait personne.
          </p>
        : (
          <ul className="sq-liste-nue" data-testid="sq-liste-composition">
            {membres.map((m) => (
              <li key={`${m.nature}-${m.cible}`}>
                {m.nature === "groupe" ? "Groupe" : "Personne"} <strong>{m.cible}</strong>{" "}
                <button type="button" data-testid={`sq-liste-retirer-${m.cible}`}
                        onClick={() => setMembres(membres.filter((x) => x !== m))}>
                  Retirer {m.cible} de la composition
                </button>
              </li>
            ))}
          </ul>
        )}
    </form>
  );
}

/**
 * **Les sections des listes, exportées** : elles ont deux hôtes et un seul foyer.
 *
 * Les horaires cliniques les montrent ici ; Urgences-santé les montre sous sa propre
 * « Communication » (AO 26-015-DO). Le même code, donc le même comportement — voir le même
 * raisonnement dans `sqmessages.tsx`.
 */
export const SECTIONS_LISTES: Section[] = [
    {
      titre: "Listes disponibles",
      source: () => "/api/communication/listes-distribution",
      tableau: {
        legende: "Listes de distribution visibles de mon compte",
        vide: "Aucune liste. Composez-en une ci-dessous : une liste vous appartient, même partagée.",
        colonnes: [
          { entete: "Nom", valeur: (l: any) => l.nom, identifiante: true },
          { entete: "Portée", valeur: (l: any) => (l.partagee ? "partagée" : "privée") },
          { entete: "Auteur", valeur: (l: any) => l.creeePar },
          {
            entete: "Composition",
            valeur: (l: any) => (Array.isArray(l.membres) && l.membres.length
              ? l.membres.map((m: any) => `${m.nature === "groupe" ? "groupe" : "personne"} ${m.cible}`).join(", ")
              : "vide"),
          },
        ],
        actions: [
          {
            libelle: (l: any) => `Partager ou reprendre la liste ${l.nom}`,
            testId: (l: any) => `sq-liste-partage-${l.id}`,
            executer: async (l: any, c) => {
              const fait = await c.appeler(`/api/communication/listes-distribution/${l.id}`, {
                method: "PUT",
                body: JSON.stringify({ nom: l.nom, partagee: !l.partagee, membres: l.membres }),
              });
              if (fait) {
                c.annoncer(l.partagee
                  ? `« ${l.nom} » redevient privée : elle n'est plus lisible des autres.`
                  : `« ${l.nom} » est partagée en lecture ; vous restez seul à la modifier.`);
                c.recharger();
              }
            },
          },
          {
            libelle: (l: any) => `Supprimer la liste ${l.nom}`,
            testId: (l: any) => `sq-liste-supprimer-${l.id}`,
            executer: async (l: any, c) => {
              const fait = await c.appeler(`/api/communication/listes-distribution/${l.id}`, { method: "DELETE" });
              if (fait) {
                c.annoncer(`Liste « ${l.nom} » supprimée. Les conversations qu'elle a ouvertes demeurent.`);
                c.recharger();
              }
            },
          },
        ],
      },
    },
    {
      titre: "Composer une liste",
      aide: "Des personnes, des groupes, ou les deux. Un membre « groupe » se déplie à chaque "
        + "envoi : la liste suit les arrivées et les départs du service.",
      rendu: (ctx) => <Composer ctx={ctx} />,
    },
];

export default page({
  cle: "sqlistesdiffusion",
  titre: "Listes de distribution",
  intro: "Les listes que vous pouvez employer : les vôtres, et celles que d'autres ont partagées. "
    + "Seul l'auteur modifie la sienne, même partagée.",
  sections: SECTIONS_LISTES,
});
