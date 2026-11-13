/**
 * **Accueil** — l'écran d'arrivée à la connexion.
 *
 * Il donne, d'un clic, les écrans du module que la personne peut réellement ouvrir : ceux que son
 * rôle admet **et** que son organisme a souscrits. La liste vient du registre du module
 * (`urgencessante/menu.ts`) : un écran ajouté au menu apparaît ici sans rien recopier, et un module
 * coupé par le superadmin disparaît des deux endroits à la fois.
 *
 * Masquer un accès n'est pas un contrôle : la garde de module, au serveur, protège.
 */
import { useAuth } from "./AuthContext";
import { useModuleAllowed } from "./moduleAccess";
import { naviguerVers } from "./navigation";
import { EnTeteEcran, EtatVide } from "./composants";
import { AnnoncesAccueil } from "./Annonces";
import { useT } from "./i18n";
import { NOTIONS_US, type EntreeUs } from "./urgencessante";

export default function Dashboard() {
  const { apiFetch, user } = useAuth();
  const moduleAllowed = useModuleAllowed();
  const { t } = useT();
  const role = user?.role ?? "employee";
  const ouvrable = (e: EntreeUs) => e.roles.includes(role) && moduleAllowed(e.module);
  const groupes = NOTIONS_US.map((n) => ({ notion: n, entrees: n.entrees.filter(ouvrable) })).filter((g) => g.entrees.length > 0);

  const aujourdhui = new Date().toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric" });

  return (
    <div data-testid="dashboard-page" className="tableau-de-bord">
      <EnTeteEcran
        titre={`Bonjour, ${user?.username ?? "vous"}`}
        testId="dashboard-greeting"
        description={`${aujourdhui.charAt(0).toUpperCase()}${aujourdhui.slice(1)} — vos écrans d'effectifs, à un clic.`}
      />

      {/* Les annonces de l'organisme : un panneau, avant tout le reste ; rien à dire, rien de rendu. */}
      <AnnoncesAccueil call={apiFetch} />

      {/*
        Aucun écran ouvrable ⇒ un état vide qui dit pourquoi, et non une région de navigation vide :
        « Accès rapides » annoncé puis rien se tabule, s'annonce, et ne mène nulle part.
      */}
      {groupes.length === 0 ? (
        <EtatVide titre="Aucun écran d'effectifs ouvert pour votre compte" testId="dashboard-vide">
          Votre organisme n'a pas encore activé le module, ou votre rôle n'y donne pas accès. Adressez-vous à l'administration de votre organisme.
        </EtatVide>
      ) : (
        <nav className="acces-rapides-groupes" data-testid="dashboard-tiles" aria-label="Accès rapides">
          {groupes.map(({ notion, entrees }) => (
            <section key={notion.cle ?? entrees[0].cle} className="card" aria-labelledby={`tdb-${notion.cle ?? entrees[0].cle}`}>
              <h2 id={`tdb-${notion.cle ?? entrees[0].cle}`}>
                <span aria-hidden="true">{notion.icone}</span> {notion.cle ? t(`concept.${notion.cle}`) : t(`nav.${entrees[0].cle}`)}
              </h2>
              <ul className="acces-rapides">
                {entrees.map((e) => (
                  <li key={e.cle}>
                    <button type="button" className="acces-rapide" data-testid={`dashboard-tile-${e.cle}`} onClick={() => naviguerVers(e.cle)}>
                      <span aria-hidden="true">{e.icone}</span> <span>{t(`nav.${e.cle}`)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </nav>
      )}
    </div>
  );
}
