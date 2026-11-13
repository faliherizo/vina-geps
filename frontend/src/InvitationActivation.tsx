/**
 * Page **publique** d'activation d'un compte par invitation (AO 2027-005, BF-101).
 *
 * Elle s'ouvre **sans aucun compte** : c'est précisément ce que la personne vient chercher. Le
 * jeton reçu par courriel est l'autorisation — à usage unique, daté, et rangé côté serveur sous
 * son empreinte.
 *
 * ## Ce que la page montre, et ce qu'elle ne montre pas
 *
 * Le nom d'utilisateur proposé, le courriel invité, la date limite. **Ni** le nom de
 * l'établissement, **ni** le rôle accordé : un lien peut se retrouver dans la mauvaise boîte, et
 * cette page est lisible sans identification. Le serveur ne les rend d'ailleurs pas.
 *
 * ## Le mot de passe se choisit ici, et nulle part ailleurs
 *
 * Personne — pas même l'administration qui a posé l'invitation — ne le connaîtra. C'est tout
 * l'objet de l'invitation : elle remplace le mot de passe temporaire qui circulait au téléphone.
 * La politique élevée s'applique sans exception, puisque la personne choisit elle-même : il n'y a
 * aucune raison d'accepter un mot de passe faible qu'elle devrait changer ensuite. Le champ
 * **accepte le collage** (WCAG 3.3.8) — un gestionnaire de mots de passe doit pouvoir servir.
 */
import { useEffect, useState } from "react";
import { EnTeteEcran, Message } from "./composants";

interface Offre { username: string; courriel: string; expireLe: string }

const LONGUEUR_MIN = 12;

export default function InvitationActivation({ jeton }: { jeton: string }) {
  const [offre, setOffre] = useState<Offre | null>(null);
  const [refus, setRefus] = useState<string | null>(null);
  const [username, setUsername] = useState("");
  const [motDePasse, setMotDePasse] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [erreur, setErreur] = useState<string | null>(null);
  const [fait, setFait] = useState(false);

  useEffect(() => {
    let annule = false;
    void (async () => {
      const r = await fetch(`/api/auth/invitations/jeton/${encodeURIComponent(jeton)}`);
      if (annule) return;
      const corps = await r.json().catch(() => ({}));
      if (r.ok) { setOffre(corps); setUsername(corps.username ?? ""); }
      else setRefus(Array.isArray(corps?.errors) ? corps.errors.join(" · ") : "Lien invalide.");
    })();
    return () => { annule = true; };
  }, [jeton]);

  if (refus) {
    return (
      <main className="page-publique">
        <EnTeteEcran titre="Activation impossible" testId="invit-titre"
          description="Ce lien ne peut pas servir." />
        <Message variante="erreur" titre="Lien inutilisable" testId="invit-refus">{refus}</Message>
        <p>
          Les liens d'invitation sont à <strong>usage unique</strong> et expirent. Demandez-en un
          nouveau à l'administration de votre établissement.
        </p>
      </main>
    );
  }

  if (fait) {
    return (
      <main className="page-publique">
        <EnTeteEcran titre="Compte activé" testId="invit-titre"
          description="Vous pouvez maintenant vous connecter." />
        <Message variante="succes" titre="C'est fait" testId="invit-succes">
          Votre compte <strong>{username}</strong> est actif. À la première connexion, la double
          authentification et trois questions de sécurité vous seront demandées : c'est la règle
          pour tous les comptes.
        </Message>
        <p><a href="/admin" data-testid="invit-vers-connexion">Aller à la page de connexion</a></p>
      </main>
    );
  }

  if (!offre) {
    return (
      <main className="page-publique">
        <EnTeteEcran titre="Activation de votre accès" testId="invit-titre" />
        <p role="status" data-testid="invit-chargement">Vérification du lien…</p>
      </main>
    );
  }

  return (
    <main className="page-publique">
      <EnTeteEcran titre="Activation de votre accès" testId="invit-titre"
        description="Choisissez votre mot de passe. Personne d'autre ne le connaîtra." />

      <Message variante="info" titre="Invitation valide" testId="invit-offre">
        Invitation adressée à <strong>{offre.courriel}</strong>, valable jusqu'au{" "}
        {offre.expireLe.slice(0, 10)}.
      </Message>

      {erreur && <Message variante="erreur" titre="Activation refusée" testId="invit-erreur">{erreur}</Message>}

      <form onSubmit={async (e) => {
        e.preventDefault();
        setErreur(null);
        if (motDePasse !== confirmation) {
          setErreur("Les deux saisies du mot de passe diffèrent.");
          return;
        }
        const r = await fetch(`/api/auth/invitations/jeton/${encodeURIComponent(jeton)}/accepter`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password: motDePasse }),
        });
        const corps = await r.json().catch(() => ({}));
        if (r.ok) setFait(true);
        else setErreur(Array.isArray(corps?.errors) ? corps.errors.join(" · ") : `Erreur ${r.status}`);
      }}>
        <div className="champ">
          <label htmlFor="invit-username">
            Nom d'utilisateur <span className="req-star" aria-hidden="true">*</span>
            <span className="sr-only"> (obligatoire)</span>
          </label>
          <input id="invit-username" required minLength={3} maxLength={64}
                 aria-describedby="invit-username-aide" data-testid="invit-username"
                 value={username} onChange={(e) => setUsername(e.target.value)} />
          <p className="champ-aide" id="invit-username-aide">
            Proposé par votre administration ; vous pouvez le changer maintenant, plus après.
          </p>
        </div>

        <div className="champ">
          <label htmlFor="invit-mdp">
            Mot de passe <span className="req-star" aria-hidden="true">*</span>
            <span className="sr-only"> (obligatoire)</span>
          </label>
          <input id="invit-mdp" required type="password" minLength={LONGUEUR_MIN}
                 autoComplete="new-password" aria-describedby="invit-mdp-aide"
                 data-testid="invit-mdp" value={motDePasse}
                 onChange={(e) => setMotDePasse(e.target.value)} />
          <p className="champ-aide" id="invit-mdp-aide">
            Au moins {LONGUEUR_MIN} caractères, avec majuscules, minuscules, chiffres et un
            caractère de ponctuation. Le collage depuis un gestionnaire de mots de passe est
            accepté.
          </p>
        </div>

        <div className="champ">
          <label htmlFor="invit-mdp2">
            Répéter le mot de passe <span className="req-star" aria-hidden="true">*</span>
            <span className="sr-only"> (obligatoire)</span>
          </label>
          <input id="invit-mdp2" required type="password" minLength={LONGUEUR_MIN}
                 autoComplete="new-password" data-testid="invit-mdp2"
                 value={confirmation} onChange={(e) => setConfirmation(e.target.value)} />
        </div>

        <div className="actions">
          <button type="submit" data-testid="invit-activer">Activer mon compte</button>
        </div>
      </form>
    </main>
  );
}
