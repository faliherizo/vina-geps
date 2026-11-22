/**
 * **Mon horaire** — les quarts publiés qui m'attendent (maquette s03 de l'AO 26-015-DO).
 *
 * L'écran manquait, et c'est la seule des douze planches qui n'avait pas d'hôte : la donnée
 * existait — l'horaire publié d'une personne sert déjà à proposer un échange — mais personne ne
 * pouvait le **lire comme un horaire**. Un employé devait ouvrir « Mes échanges » et dérouler un
 * sélecteur pour savoir quand il travaille.
 *
 * ## Deux décisions sur ce qui est montré
 *
 * **Le site, jamais deviné.** Un quart sans milieu rattaché affiche « — ». Se présenter au mauvais
 * garage n'est pas une erreur qu'on rattrape dans la journée, et un site supposé est pire qu'un
 * site absent.
 *
 * **Le statut dit ce que la donnée sait.** La planche propose « Confirmé » et « En attente » ;
 * ici, un quart est confirmé **sauf** si un échange ouvert le concerne — ce qui est précisément
 * ce qui rend un horaire incertain. Les échanges sont un module à part : sans lui, la colonne dit
 * « Confirmé » pour tout le monde, et c'est vrai.
 *
 * **La semaine affichée se choisit** (« Cette semaine » de la planche). L'écran ne montrait que
 * l'à-venir, parce que la lecture servait d'abord à proposer un échange — on ne cède pas un quart
 * déjà travaillé. Mais c'est au moment où sa feuille de temps lui demande de vérifier le
 * *planifié* de la semaine écoulée que l'employée a besoin de cet horaire, et il avait disparu.
 *
 * **Le mois en calendrier, la semaine en liste** (13 novembre 2026). Personne ne lit son horaire
 * comme une table : la planche s03 le montre en calendrier, et c'est désormais la vue par défaut.
 * La liste hebdomadaire reste à un clic — elle se lit mieux au lecteur d'écran et s'imprime.
 *
 * **Une seule porte vers l'échange.** Le geste appartient à « Mes échanges » (ADR 0034) : la
 * planche demande une action principale, pas un second formulaire qui divergerait du premier.
 */
import { useCallback, useEffect, useMemo, useState } from "react";
import { BadgeStatut, Calendrier, EnTeteEcran, EtatVide, Message, Tableau, type Colonne, type JourCalendrier } from "./composants";
import { heure, useApiEffectifs } from "./effectifs/api";
import { lienVersPage } from "./navigation";
import { useModuleAllowed } from "./moduleAccess";
import type { Echange, QuartEchangeable } from "./effectifs/echanges/types";
import type { Ressource } from "./effectifs/planification/types";
import "./effectifs/effectifs.css";
import PageMaquette from "./composants/PageMaquette";
import QuartsOfferts from "./effectifs/offres/QuartsOfferts";

/** Les statuts d'échange qui rendent un quart incertain : il peut encore changer de main. */
const ECHANGES_OUVERTS = new Set(["PROPOSE", "A_VALIDER"]);

const JOURS = ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"];

/** « lundi 3 novembre » — le jour de la semaine d'abord, comme la planche l'écrit. */
export function jourEnMots(date: string): string {
  const d = new Date(`${date}T12:00:00`);
  if (Number.isNaN(d.getTime())) return date;
  return `${JOURS[d.getDay()]} ${d.getDate()} ${d.toLocaleDateString("fr-CA", { month: "long" })}`;
}

/** Une fenêtre fermée : deux bornes, parce qu'une borne seule ne dit pas ce qu'on veut lire. */
export interface Fenetre { du: string; au: string }

const JOUR_MS = 86_400_000;

const iso = (d: Date) => d.toISOString().slice(0, 10);

/**
 * La semaine **du lundi au dimanche** qui contient `aujourdhui`, décalée de `semaines`.
 *
 * Midi plutôt que minuit : au changement d'heure, un minuit local recule d'un jour une fois sur
 * deux — et l'horaire sauterait alors d'une semaine sans que personne comprenne pourquoi.
 */
export function semaineDe(aujourdhui: string, semaines = 0): Fenetre {
  const d = new Date(`${aujourdhui}T12:00:00Z`);
  const lundi = new Date(d.getTime() - ((d.getUTCDay() + 6) % 7) * JOUR_MS + semaines * 7 * JOUR_MS);
  return { du: iso(lundi), au: iso(new Date(lundi.getTime() + 6 * JOUR_MS)) };
}

/** Le mois qui contient `date`, du premier au dernier jour : le calendrier le lit en un seul appel. */
export function moisDe(premier: Date): Fenetre {
  const fin = new Date(premier.getFullYear(), premier.getMonth() + 1, 0);
  const deux = (n: number) => String(n).padStart(2, "0");
  const jour = (d: Date) => `${d.getFullYear()}-${deux(d.getMonth() + 1)}-${deux(d.getDate())}`;
  return { du: jour(new Date(premier.getFullYear(), premier.getMonth(), 1)), au: jour(fin) };
}

/** Les deux lectures de l'horaire : le mois en calendrier (par défaut), la semaine en liste. */
type Vue = "calendrier" | "liste";

const COLONNES: ReadonlyArray<Colonne<QuartEchangeable>> = [
  { id: "jour", libelle: "Jour", tri: (q) => q.date },
  { id: "quart", libelle: "Quart" },
  { id: "site", libelle: "Site" },
  { id: "poste", libelle: "Poste" },
  { id: "statut", libelle: "Statut" },
];

export default function MonHoraire() {
  const { lire, erreur } = useApiEffectifs();
  const moduleAutorise = useModuleAllowed();
  const avecEchanges = moduleAutorise("effectifs_echanges");
  const [moi, setMoi] = useState<Ressource | null | undefined>(undefined);
  const [quarts, setQuarts] = useState<QuartEchangeable[]>([]);
  const [enEchange, setEnEchange] = useState<Set<string>>(new Set());
  const [semaines, setSemaines] = useState(0);
  const [vue, setVue] = useState<Vue>("calendrier");
  const [mois, setMois] = useState(() => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1); });
  const fenetre = useMemo(() => (vue === "calendrier" ? moisDe(mois) : semaineDe(iso(new Date()), semaines)), [vue, mois, semaines]);

  const charger = useCallback(async () => {
    const m = await lire<{ ressource: Ressource | null; quarts: QuartEchangeable[] }>(
      `/api/effectifs/mon-horaire?du=${fenetre.du}&au=${fenetre.au}`);
    setMoi(m?.ressource ?? null);
    setQuarts(m?.quarts ?? []);
    // Sans le module des échanges, la question ne se pose pas : aucun appel, et aucun « en
    // attente » qui ne correspondrait à rien.
    if (!avecEchanges) { setEnEchange(new Set()); return; }
    const x = (await lire<Echange[]>("/api/effectifs/echanges")) ?? [];
    setEnEchange(new Set(x.filter((e) => ECHANGES_OUVERTS.has(e.statut))
      .flatMap((e) => [e.placeCedeeId, e.placeVoulueId])));
  }, [lire, avecEchanges, fenetre.du, fenetre.au]);
  useEffect(() => { void charger(); }, [charger]);

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • mon horaire">
      <EnTeteEcran
        titre="Mon horaire"
        description="Vos quarts publiés, semaine par semaine : quand, où, et à quel titre."
        testId="mho-titre"
      />
      {erreur && <Message variante="erreur" testId="mho-erreur">{erreur}</Message>}
      {moi === null && (
        <EtatVide titre="Votre compte n'est relié à aucune ressource planifiable" testId="mho-sans-ressource">
          Demandez à votre planificateur de relier votre compte à votre dossier d'effectif.
        </EtatVide>
      )}
      {moi && (
        <>
          {/* AD-07 : un quart offert à mon tour se lit et se prend ici, là où l'horaire qu'il rejoint se lit. */}
          <QuartsOfferts apresReponse={() => void charger()} />
          <BasculeVue vue={vue} changer={setVue} />
          {vue === "calendrier" ? (
            <>
              <p role="status" data-testid="mho-fenetre">{resumeDuMois(quarts.length, mois)}</p>
              <div className="mho-calendrier">
                <Calendrier mois={mois} onMois={setMois} libelle="Mon horaire" testId="mho-cal"
                            rendreJour={(jour) => <JourDHoraire jour={jour} quarts={quarts} enEchange={enEchange} />} />
              </div>
            </>
          ) : (
            <ListeDeLaSemaine quarts={quarts} enEchange={enEchange} fenetre={fenetre} semaines={semaines} changerSemaines={setSemaines} />
          )}
          {/* La porte, pas une seconde maison : proposer un échange appartient à « Mes échanges »
              (ADR 0034). Un lien, parce qu'il mène ailleurs — un bouton promettrait un geste ici. */}
          {avecEchanges && quarts.length > 0 && (
            <p className="eff-actions">
              <a href={lienVersPage("mesechanges")} data-testid="mho-proposer-echange">Proposer un échange</a>
            </p>
          )}
        </>
      )}
    </PageMaquette>
  );
}

/** La bascule entre les deux lectures : deux boutons à bascule, groupés et nommés. */
function BasculeVue({ vue, changer }: { vue: Vue; changer: (v: Vue) => void }) {
  return (
    <div role="group" aria-label="Affichage de l'horaire" className="eff-actions mho-bascule">
      <button type="button" className={vue === "calendrier" ? "primary" : "secondary"} aria-pressed={vue === "calendrier"}
              data-testid="mho-vue-calendrier" onClick={() => changer("calendrier")}>Calendrier du mois</button>
      <button type="button" className={vue === "liste" ? "primary" : "secondary"} aria-pressed={vue === "liste"}
              data-testid="mho-vue-liste" onClick={() => changer("liste")}>Liste de la semaine</button>
    </div>
  );
}

/** « 12 quarts en novembre 2026 » — le mois change sans rechargement, le lecteur d'écran l'entend. */
export function resumeDuMois(nombre: number, mois: Date): string {
  const nom = mois.toLocaleDateString("fr-CA", { month: "long", year: "numeric" });
  return nombre === 0 ? `Aucun quart publié en ${nom}` : `${nombre} quart${nombre > 1 ? "s" : ""} en ${nom}`;
}

/** Une case du calendrier : son numéro, puis ses quarts — l'heure, le site (jamais deviné) et l'incertitude en mots. */
function JourDHoraire({ jour, quarts, enEchange }: { jour: JourCalendrier; quarts: QuartEchangeable[]; enEchange: Set<string> }) {
  const duJour = quarts.filter((q) => q.date === jour.date);
  return (
    <>
      {/* Le numéro sur grand écran ; la date en mots quand le calendrier devient un agenda (téléphone). */}
      <span className="cal-daynum mho-cal-num">{Number(jour.date.slice(8))}</span>
      <span className="cal-daynum mho-cal-date">{jourEnMots(jour.date)}</span>
      {duJour.length > 0 && (
        <ul className="mho-cal-quarts">
          {duJour.map((q) => (
            <li key={q.placeId} data-testid={`mho-cal-${q.placeId}`} className={enEchange.has(q.placeId) ? "mho-cal-incertain" : undefined}>
              <strong>{heure(q.debut)}–{heure(q.fin)}</strong> {q.milieu ?? "—"} · {q.corpsEmploiCode}
              {enEchange.has(q.placeId) && <span className="mho-cal-mention"> — Échange en cours</span>}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

interface PropsListe {
  quarts: QuartEchangeable[];
  enEchange: Set<string>;
  fenetre: Fenetre;
  semaines: number;
  changerSemaines: (n: number | ((n: number) => number)) => void;
}

/** La semaine en liste : la lecture d'avant le calendrier, gardée pour le lecteur d'écran et l'impression. */
function ListeDeLaSemaine({ quarts, enEchange, fenetre, semaines, changerSemaines: setSemaines }: PropsListe) {
  return (
    <>
      <div className="eff-barre">
        {/* Un groupe nommé : trois boutons côte à côte sans libellé commun se lisent au clavier
            comme trois commandes sans rapport. */}
        <div role="group" aria-label="Semaine affichée" className="eff-actions">
          <button type="button" className="secondary" data-testid="mho-semaine-precedente" onClick={() => setSemaines((n) => n - 1)}>
            Semaine précédente
          </button>
          <button type="button" className="secondary" data-testid="mho-semaine-courante" disabled={semaines === 0} onClick={() => setSemaines(0)}>
            Cette semaine
          </button>
          <button type="button" className="secondary" data-testid="mho-semaine-suivante" onClick={() => setSemaines((n) => n + 1)}>
            Semaine suivante
          </button>
        </div>
        {/* `role="status"` : la semaine change sans rechargement, et un lecteur d'écran doit
            l'entendre — sans que le focus quitte le bouton qu'on vient d'actionner. */}
        <p role="status" data-testid="mho-fenetre">
          {quarts.length === 0 ? "Aucun quart" : `${quarts.length} quart${quarts.length > 1 ? "s" : ""}`} du {jourEnMots(fenetre.du)} au {jourEnMots(fenetre.au)}
        </p>
      </div>
      <Tableau
        colonnes={COLONNES}
        lignes={quarts}
        legende="Mes quarts publiés de la semaine affichée"
        testId="mho-table"
        vide={<EtatVide titre="Aucun quart publié cette semaine-là" compact testId="mho-vide">
          Changez de semaine, ou attendez la publication de la période.
        </EtatVide>}
        rendre={(q) => (
          <tr key={q.placeId} data-testid={`mho-${q.placeId}`}>
            <td>{jourEnMots(q.date)}</td>
            <td>{heure(q.debut)}–{heure(q.fin)}</td>
            {/* Jamais de site deviné : « — » dit l'absence, un nom supposé enverrait au mauvais garage. */}
            <td>{q.milieu ?? "—"}</td>
            <td>{q.corpsEmploiCode}</td>
            <td>
              {/* Le mot, pas la seule couleur (WCAG 1.4.1) : un badge se lit aussi dans un export. */}
              {enEchange.has(q.placeId)
                ? <BadgeStatut statut="en_attente">Échange en cours</BadgeStatut>
                : <BadgeStatut statut="approuvee">Confirmé</BadgeStatut>}
            </td>
          </tr>
        )}
      />
    </>
  );
}
