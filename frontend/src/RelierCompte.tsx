/**
 * Page **publique** « Relier mon compte » (ADR 0093).
 *
 * L'annuaire de l'organisme (Entra ID, Keycloak, LDAPS…) vient d'authentifier la personne, mais son identité
 * — le couple émetteur + sujet du jeton — n'est reliée à aucun compte de l'application. Le serveur n'a **rien
 * créé** : le courriel n'est qu'un indice, et l'on ne prend pas un compte sur la foi d'un courriel. La personne
 * prouve qu'elle possède son compte, d'une de trois façons :
 *
 * 1. le **mot de passe** de son compte existant ;
 * 2. le **lien d'invitation** reçu par courriel, s'il en attend une ;
 * 3. une **demande à l'administration** de son organisme, qui l'approuve ou la refuse.
 *
 * La liaison en cours est portée par un témoin `HttpOnly` que la page ne lit jamais ; elle expire en dix
 * minutes et n'admet que cinq essais. Le champ du mot de passe accepte le collage (WCAG 3.3.8).
 */
import { useEffect, useState, type FormEvent } from "react";
import { EnTeteEcran, Message } from "./composants";

interface Etat { indice: string; candidat: boolean; demandeEnAttente: boolean; essaisRestants: number }
type Reponse = { ok: boolean; status: number; corps: Record<string, unknown> };

const CHEMIN = "/api/auth/sso/liaison";

async function appeler(chemin: string, corps?: Record<string, string>): Promise<Reponse> {
  const r = await fetch(chemin, corps === undefined
    ? { method: "POST", credentials: "same-origin" }
    : { method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps) });
  return { ok: r.ok, status: r.status, corps: await r.json().catch(() => ({})) };
}

function motifs(corps: Record<string, unknown>, defaut: string): string {
  return Array.isArray(corps.errors) ? corps.errors.join(" · ") : defaut;
}

export default function RelierCompte() {
  const [etat, setEtat] = useState<Etat | null>(null);
  const [expiree, setExpiree] = useState<string | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [succes, setSucces] = useState<string | null>(null);
  const [demande, setDemande] = useState<string | null>(null);
  const [occupe, setOccupe] = useState(false);
  const [identifiant, setIdentifiant] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [invitation, setInvitation] = useState("");

  async function charger() {
    const r = await fetch(CHEMIN, { credentials: "same-origin" });
    const corps = await r.json().catch(() => ({}));
    if (r.ok) setEtat(corps as Etat);
    else setExpiree(motifs(corps, "Aucune liaison en cours."));
  }

  useEffect(() => { void charger(); }, []);

  async function prouver(chemin: string, corps: Record<string, string>) {
    setErreur(null);
    setOccupe(true);
    const r = await appeler(chemin, corps);
    setOccupe(false);
    if (r.ok) {
      setSucces(String(r.corps.retour ?? "/admin"));
      window.location.assign(String(r.corps.retour ?? "/admin"));
      return;
    }
    setErreur(motifs(r.corps, `Erreur ${r.status}`));
    if (r.status === 404 || r.status === 429) setExpiree(motifs(r.corps, "La liaison a expiré."));
    else await charger();
  }

  function parMotDePasse(e: FormEvent) {
    e.preventDefault();
    void prouver(`${CHEMIN}/mot-de-passe`, { identifiant, motDePasse });
  }

  function parInvitation(e: FormEvent) {
    e.preventDefault();
    void prouver(`${CHEMIN}/invitation`, { invitation });
  }

  async function demander() {
    setErreur(null);
    const r = await appeler(`${CHEMIN}/demande`);
    if (r.ok) setDemande(String(r.corps.message ?? "Demande transmise."));
    else setErreur(motifs(r.corps, `Erreur ${r.status}`));
  }

  if (expiree) {
    return (
      <main className="page-publique">
        <EnTeteEcran titre="Relier mon compte" testId="relier-titre" />
        <Message variante="avertissement" titre="Aucune liaison en cours" testId="relier-expiree">{expiree}</Message>
        <p><a href="/admin" data-testid="relier-vers-connexion">Revenir à la page de connexion</a></p>
      </main>
    );
  }

  if (succes) {
    return (
      <main className="page-publique">
        <EnTeteEcran titre="Compte relié" testId="relier-titre" />
        <Message variante="succes" titre="C'est fait" testId="relier-succes">
          Votre identité est reliée à votre compte : les prochaines connexions par votre annuaire entreront directement.{" "}
          <a href={succes} data-testid="relier-continuer">Continuer</a>
        </Message>
      </main>
    );
  }

  if (!etat) {
    return (
      <main className="page-publique">
        <EnTeteEcran titre="Relier mon compte" testId="relier-titre" />
        <p role="status" data-testid="relier-chargement">Vérification de votre connexion…</p>
      </main>
    );
  }

  return (
    <main className="page-publique">
      <EnTeteEcran titre="Relier mon compte" testId="relier-titre"
        description="Votre annuaire vous a authentifié ; prouvez maintenant que ce compte de l'application est le vôtre." />

      <Message variante="info" titre="Première connexion par votre annuaire" testId="relier-indice">
        Votre annuaire a vérifié l'adresse <strong>{etat.indice}</strong>. Aucun compte n'a été créé : pour votre sécurité, un
        compte existant n'est jamais repris sur la seule foi d'un courriel. Il vous reste {etat.essaisRestants} essai(s).
      </Message>

      {!etat.candidat && (
        <Message variante="avertissement" titre="Aucun compte à ce courriel" testId="relier-sans-compte">
          Aucun compte de votre organisme ne porte ce courriel, et aucun n'a été créé. Si vous avez un compte sous un autre
          identifiant, reliez-le avec son mot de passe ; sinon, demandez une invitation à l'administration de votre organisme.
        </Message>
      )}

      {erreur && <Message variante="erreur" titre="Liaison refusée" testId="relier-erreur">{erreur}</Message>}

      <section aria-labelledby="relier-mdp-titre">
        <h2 id="relier-mdp-titre">Avec le mot de passe de votre compte</h2>
        <form onSubmit={parMotDePasse}>
          <div className="champ">
            <label htmlFor="relier-identifiant">Nom d'utilisateur ou courriel du compte</label>
            <input id="relier-identifiant" autoComplete="username" aria-describedby="relier-identifiant-aide" data-testid="relier-identifiant"
              value={identifiant} onChange={(e) => setIdentifiant(e.target.value)} />
            <p className="champ-aide" id="relier-identifiant-aide">Laissez vide si c'est le courriel vérifié par votre annuaire.</p>
          </div>
          <div className="champ">
            <label htmlFor="relier-mdp">
              Mot de passe <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span>
            </label>
            <input id="relier-mdp" type="password" required autoComplete="current-password" data-testid="relier-mdp"
              value={motDePasse} onChange={(e) => setMotDePasse(e.target.value)} />
          </div>
          <div className="actions">
            <button type="submit" className="primary" disabled={occupe} data-testid="relier-mdp-envoyer">Relier avec mon mot de passe</button>
          </div>
        </form>
      </section>

      <section aria-labelledby="relier-invitation-titre">
        <h2 id="relier-invitation-titre">Avec une invitation</h2>
        <form onSubmit={parInvitation}>
          <div className="champ">
            <label htmlFor="relier-invitation">
              Lien d'invitation reçu par courriel <span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span>
            </label>
            <input id="relier-invitation" required data-testid="relier-invitation" value={invitation} onChange={(e) => setInvitation(e.target.value)} />
          </div>
          <div className="actions">
            <button type="submit" className="secondary" disabled={occupe} data-testid="relier-invitation-envoyer">Relier avec mon invitation</button>
          </div>
        </form>
      </section>

      <section aria-labelledby="relier-demande-titre">
        <h2 id="relier-demande-titre">Demander à l'administration</h2>
        {demande || etat.demandeEnAttente ? (
          <Message variante="succes" titre="Demande transmise" testId="relier-demande-envoyee">
            {demande ?? "Votre demande attend la décision de l'administration de votre organisme. Reconnectez-vous une fois qu'elle l'aura approuvée."}
          </Message>
        ) : (
          <>
            <p id="relier-demande-aide">
              {etat.candidat
                ? "Un administrateur de votre organisme vérifiera votre demande et reliera votre identité au compte qui porte ce courriel."
                : "Possible seulement si un compte de votre organisme porte ce courriel."}
            </p>
            <div className="actions">
              <button type="button" className="secondary" disabled={!etat.candidat} aria-describedby="relier-demande-aide" data-testid="relier-demande"
                onClick={() => void demander()}>Demander l'approbation</button>
            </div>
          </>
        )}
      </section>
    </main>
  );
}
