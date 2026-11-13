/**
 * **Frontière d'erreur** — un écran qui plante n'emporte plus l'application.
 *
 * Trois pages vidaient la fenêtre entière, `<main>` compris, sur une seule lecture fautive
 * (`undefined.toLocaleString`). Rien ne s'affichait : ni message, ni menu, ni moyen de repartir.
 * L'utilisateur voyait du blanc, et un relevé automatique concluait « page jamais rendue » —
 * c'est-à-dire rien du tout.
 *
 * Ce composant **ne masque pas** l'erreur : il l'affiche, nommée, dans une région d'alerte, la
 * consigne dans la console pour le journal du navigateur, et laisse la navigation en place pour
 * aller ailleurs. Avaler l'erreur en silence serait pire que la page blanche : le défaut vivrait
 * sans témoin.
 */
import { Component, type ErrorInfo, type ReactNode } from "react";

interface Props {
  /** La clé de l'écran : change de valeur ⇒ la frontière se réarme (on quitte l'écran fautif). */
  cle: string;
  children: ReactNode;
}

interface State {
  erreur: Error | null;
  /** L'écran sur lequel l'erreur s'est produite, pour ne pas la montrer sur le suivant. */
  cleFautive: string | null;
}

export default class FrontiereErreur extends Component<Props, State> {
  state: State = { erreur: null, cleFautive: null };

  static getDerivedStateFromError(erreur: Error): Partial<State> {
    return { erreur };
  }

  componentDidCatch(erreur: Error, info: ErrorInfo) {
    // La trace reste disponible : un défaut sans trace ne se corrige pas.
    console.error(`[écran ${this.props.cle}] rendu interrompu :`, erreur, info.componentStack);
    this.setState({ cleFautive: this.props.cle });
  }

  componentDidUpdate(precedentes: Props) {
    // Changer d'écran réarme la frontière : l'erreur appartenait à l'écran quitté.
    if (this.state.erreur && precedentes.cle !== this.props.cle) {
      this.setState({ erreur: null, cleFautive: null });
    }
  }

  render() {
    const { erreur, cleFautive } = this.state;
    if (!erreur || (cleFautive && cleFautive !== this.props.cle)) return this.props.children;

    return (
      <section className="panel" role="alert" data-testid="frontiere-erreur"
               aria-labelledby="frontiere-erreur-titre">
        <h1 id="frontiere-erreur-titre">Cet écran n'a pas pu s'afficher</h1>
        <p>
          Le reste de l'application reste utilisable : le menu fonctionne, et les autres écrans
          aussi. Cette page-ci s'est interrompue pendant son rendu.
        </p>
        <p>
          <strong>Motif technique :</strong>{" "}
          <span data-testid="frontiere-erreur-motif">{erreur.message}</span>
        </p>
        <p className="hint">
          Si le problème persiste, signalez-le au soutien avec le nom de l'écran
          (« {this.props.cle} ») et ce motif : ils suffisent à le retrouver.
        </p>
        <div className="actions">
          <button type="button" data-testid="frontiere-erreur-reessayer"
                  onClick={() => this.setState({ erreur: null, cleFautive: null })}>
            Réessayer d'afficher cet écran
          </button>
        </div>
      </section>
    );
  }
}
