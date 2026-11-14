/**
 * **Tableau de bord** — l'écran d'arrivée à la connexion (planches s01 et s02).
 *
 * **L'encadrement** y lit sa journée, comme sur la planche s01 : quatre onglets (couverture des
 * quarts, absences à traiter, demandes en attente, anomalies de présence), quatre tuiles chiffrées
 * qui les ouvrent, la table du sujet choisi avec une action par ligne, et l'action principale
 * « Ouvrir le comblement ». Il remplaçait une grille de vingt-neuf boutons qui recopiait le menu
 * sans dire ce qui attendait le gestionnaire ce jour-là (relevé du 13 novembre 2026).
 *
 * **L'employé** y trouve ses prochains quarts et les portes de ses propres écrans — ceux que son
 * rôle admet **et** que son organisme a souscrits. La liste vient du registre du module
 * (`urgencessante/menu.ts`) : rien n'est recopié.
 *
 * Masquer un accès n'est pas un contrôle : la garde de module, au serveur, protège.
 */
import { useAuth } from "./AuthContext";
import { useModuleAllowed } from "./moduleAccess";
import { naviguerVers } from "./navigation";
import { EnTeteEcran, EtatVide } from "./composants";
import { AnnoncesAccueil } from "./Annonces";
import { useT } from "./i18n";
import { ENCADREMENT, NOTIONS_US, type EntreeUs } from "./urgencessante";
import TableauGestionnaire from "./tableauDeBord/TableauGestionnaire";
import ProchainsQuarts from "./tableauDeBord/ProchainsQuarts";
import { jourDuClient } from "./tableauDeBord/indicateurs";

/** « Vendredi 13 novembre 2026 » — la date du client, en toutes lettres. */
function dateEnMots(instant: Date): string {
  const d = instant.toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric", timeZone: "America/Toronto" });
  return d.charAt(0).toUpperCase() + d.slice(1);
}

export default function Dashboard() {
  const { apiFetch, user } = useAuth();
  const role = user?.role ?? "employee";
  const encadre = (ENCADREMENT as readonly string[]).includes(role);
  const maintenant = new Date();
  return (
    <div data-testid="dashboard-page" className="tableau-de-bord">
      <EnTeteEcran
        titre={encadre ? "Tableau de bord gestionnaire" : "Mon tableau de bord"}
        testId="dashboard-greeting"
        description={`Bonjour, ${user?.username ?? "vous"} — ${encadre ? "vue opérationnelle" : "votre semaine"} • ${dateEnMots(maintenant)}`}
      />
      {/* Les annonces de l'organisme : un panneau, avant tout le reste ; rien à dire, rien de rendu. */}
      <AnnoncesAccueil call={apiFetch} />
      {encadre ? <TableauGestionnaire jour={jourDuClient(maintenant)} /> : <ProchainsQuarts jour={jourDuClient(maintenant)} />}
      {!encadre && <AccesRapides role={role} />}
    </div>
  );
}

/**
 * Les portes des écrans de la personne. Aucun écran ouvrable ⇒ un état vide qui dit pourquoi, et
 * non une région de navigation vide : « Accès rapides » annoncé puis rien se tabule et ne mène nulle part.
 */
function AccesRapides({ role }: { role: string }) {
  const moduleAllowed = useModuleAllowed();
  const { t } = useT();
  const ouvrable = (e: EntreeUs) => e.roles.includes(role) && moduleAllowed(e.module);
  const entrees = NOTIONS_US.flatMap((n) => n.entrees).filter(ouvrable);
  if (entrees.length === 0) {
    return (
      <EtatVide titre="Aucun écran d'effectifs ouvert pour votre compte" testId="dashboard-vide">
        Votre organisme n'a pas encore activé le module, ou votre rôle n'y donne pas accès. Adressez-vous à l'administration de votre organisme.
      </EtatVide>
    );
  }
  return (
    <nav className="card acces-rapides-carte" data-testid="dashboard-tiles" aria-labelledby="tdb-acces">
      <h2 id="tdb-acces">Accès rapides</h2>
      <ul className="acces-rapides">
        {entrees.map((e) => (
          <li key={e.cle}>
            <button type="button" className="acces-rapide" data-testid={`dashboard-tile-${e.cle}`} onClick={() => naviguerVers(e.cle)}>
              <span aria-hidden="true">{e.icone}</span> <span>{t(`nav.${e.cle}`)}</span>
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}
