package ca.vina.nexu.auth.application;

import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.auth.domaine.RegistreAnnuaires;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * La connexion par <strong>un des annuaires de l'organisme</strong> (P-TN-2) : le flux OIDC +
 * PKCE de l'Entra ID d'instance ({@link Oidc}), avec l'autorité, le client et le secret de
 * l'annuaire choisi, puis l'admission par l'organisme ({@link Annuaires#admettre}) et le compte
 * <em>dans cet organisme</em>. Un annuaire LDAP n'a pas de redirection : l'identifiant et le mot de passe saisis
 * sont vérifiés par une liaison LDAPS ({@link Annuaires.Liaison}, ADR 0083), puis la même admission s'applique.
 *
 * <p>L'annuaire est la source de vérité des appartenances : le rôle d'un compte existant suit ses
 * groupes à chaque connexion. Un compte désactivé dans l'application reste refusé, quoi qu'en dise
 * l'annuaire — la désactivation locale est la mesure d'urgence d'un administrateur.
 */
public class ServiceAnnuaires {

    /** Annuaire inconnu, inactif, ou demande expirée : la même réponse, pour ne rien révéler. */
    public static final class IntrouvableException extends RuntimeException {
        public IntrouvableException(String message) {
            super(message);
        }
    }

    /** Un réglage d'exploitation manque (secret, adresse publique) : une faute de déploiement, pas de l'usager. */
    public static final class ConfigurationIncompleteException extends RuntimeException {
        public ConfigurationIncompleteException(String message) {
            super(message);
        }
    }

    /** L'issue d'une connexion par annuaire : une session, ou une liaison à prouver (ADR 0093). */
    public sealed interface Issue permits Ouverture, LiaisonRequise {
    }

    /** Une connexion admise : le compte de l'organisme, son jeton, et s'il vient d'être créé. */
    public record Ouverture(Compte compte, String jeton, String courriel, boolean cree) implements Issue {
    }

    /** L'annuaire a authentifié une identité qu'aucun compte ne porte encore : la personne doit prouver le sien. */
    public record LiaisonRequise(String jetonLiaison, String courriel) implements Issue {
    }

    /**
     * Les ports : l'annuaire, la résolution d'identité (qui trouve, relie ou provisionne le compte), les sessions,
     * l'échange du code, la découverte OIDC et la liaison LDAPS.
     */
    public record Ports(RegistreAnnuaires annuaires, ServiceResolutionIdentite resolution, Annuaires.Sessions sessions, Annuaires.Echange echange,
                        Annuaires.Decouverte decouverte, Annuaires.Liaison liaison) {
    }

    private record EnCours(Oidc.Demande demande, String annuaireId, String retour) {
    }

    /** Le chemin du retour, à inscrire tel quel dans l'inscription d'application de chaque annuaire. */
    public static final String CHEMIN_RAPPEL = "/api/auth/sso/annuaires/callback";


    private final Ports ports;
    private final Map<String, String> env;
    private final Clock horloge;
    private final RandomGenerator alea;
    private final Map<String, EnCours> enCours = new ConcurrentHashMap<>();

    public ServiceAnnuaires(Ports ports, Map<String, String> env, Clock horloge, RandomGenerator alea) {
        this.ports = ports;
        this.env = env;
        this.horloge = horloge;
        this.alea = alea;
    }

    /** Les annuaires actifs d'un organisme, tels que la page de connexion les propose. */
    public List<Annuaires.Annuaire> actifs(String tenantId) {
        return ports.annuaires().de(tenantId).stream().filter(Annuaires.Annuaire::actif).toList();
    }

    private Annuaires.Annuaire actif(String id) {
        return ports.annuaires().parId(id).filter(Annuaires.Annuaire::actif).orElseThrow(() -> new IntrouvableException("annuaire introuvable"));
    }

    private String secret(Annuaires.Annuaire a) {
        return Optional.ofNullable(env.get(a.variableSecret())).filter(s -> !s.isEmpty())
                .orElseThrow(() -> new ConfigurationIncompleteException("le secret de l'annuaire n'est pas déployé (variable " + a.variableSecret() + ")"));
    }

    private Oidc.Configuration configuration(Annuaires.Annuaire a, String origine) {
        String rappel = origine + CHEMIN_RAPPEL;
        return switch (a.fournisseur()) {
            case ENTRA -> Oidc.configuration(Map.of("AZURE_CLIENT_ID", a.clientId(), "AZURE_CLIENT_SECRET", secret(a), "AZURE_TENANT_ID", a.locataireEntra(),
                    "AZURE_REDIRECT_URI", rappel), Optional.empty());
            case OIDC -> configurationOidc(a, secret(a), rappel);
            // Un annuaire LDAP n'envoie l'usager nulle part : sa connexion passe par ouvrirParLiaison.
            case LDAPS -> throw new IntrouvableException("l'annuaire « " + a.nom() + " » se connecte par identifiant et mot de passe");
        };
    }

    /** Un fournisseur OIDC générique : ses adresses viennent de son document de découverte, lu à chaque connexion. */
    private Oidc.Configuration configurationOidc(Annuaires.Annuaire a, String secret, String rappel) {
        Annuaires.Decouverte.Points p;
        try {
            p = ports.decouverte().points(a.emetteur());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ConfigurationIncompleteException("l'annuaire « " + a.nom() + " » est injoignable (" + a.emetteur() + ") : " + e.getMessage());
        }
        return new Oidc.Configuration(false, false, a.emetteur(), a.clientId(), secret, rappel, a.emetteur(), p.autorisation(), p.jeton(),
                a.emetteur() + Oidc.CHEMIN_DECOUVERTE);
    }

    /**
     * Démarre la connexion : l'URL d'autorisation de l'annuaire.
     *
     * @param origine l'origine publique de l'application, où l'annuaire renverra l'usager
     */
    public String debuter(String annuaireId, String origine) {
        Annuaires.Annuaire a = actif(annuaireId);
        Oidc.Configuration cfg = configuration(a, origine);
        long maintenant = horloge.millis();
        enCours.values().removeIf(e -> e.demande().expiree(maintenant));
        Oidc.Demande d = Oidc.nouvelleDemande(alea, maintenant);
        enCours.put(d.state(), new EnCours(d, a.id(), origine));
        return Oidc.debuter(cfg, d).authUrl();
    }

    /** Termine la connexion : revendications vérifiées, admission par l'organisme, compte et session. */
    public Issue completer(String code, String state) {
        EnCours e = Optional.ofNullable(enCours.remove(state)).filter(x -> !x.demande().expiree(horloge.millis()))
                .orElseThrow(() -> new IntrouvableException("demande de connexion inconnue ou expirée"));
        Annuaires.Annuaire a = actif(e.annuaireId());
        Map<String, Object> claims = ports.echange().echanger(code, configuration(a, e.retour()), e.demande());
        return ouvrir(a, IdentitesExternes.Cle.de(claims.get("iss"), claims.get("sub")), Oidc.courriel(claims), claims);
    }

    /**
     * La connexion par un annuaire LDAP : liaison LDAPS, puis la même admission que l'OIDC — les groupes de la personne
     * tiennent lieu de revendication.
     *
     * @throws Annuaires.ConnexionRefuseeException si l'annuaire refuse l'identifiant ou le mot de passe, sans dire lequel
     */
    public Issue ouvrirParLiaison(String annuaireId, String identifiant, String motDePasse) {
        Annuaires.Annuaire a = actif(annuaireId);
        if (a.fournisseur() != Annuaires.Fournisseur.LDAPS) {
            throw new IntrouvableException("l'annuaire « " + a.nom() + " » se connecte par redirection");
        }
        Annuaires.Personne p;
        try {
            p = ports.liaison().authentifier(a, secret(a), identifiant, motDePasse)
                    .orElseThrow(() -> new Annuaires.ConnexionRefuseeException("identifiant ou mot de passe refusé par l'annuaire « " + a.nom() + " »"));
        } catch (IllegalStateException e) {
            throw new ConfigurationIncompleteException("l'annuaire « " + a.nom() + " » est injoignable : " + e.getMessage());
        }
        // L'identifiant de connexion tient lieu de sujet : l'annuaire LDAP l'a authentifié, à son adresse LDAPS.
        IdentitesExternes.Cle cle = IdentitesExternes.Cle.de(a.emetteur(), identifiant.trim().toLowerCase(Locale.ROOT));
        return ouvrir(a, cle, p.courriel(), Map.of(a.revendication().code(), p.groupes()));
    }

    /**
     * L'admission par l'organisme (domaine, courriel vérifié, rôle des groupes), puis la résolution par
     * {@code (émetteur, sujet)} : compte relié, provisionné si l'annuaire l'autorise, ou liaison à prouver.
     */
    private Issue ouvrir(Annuaires.Annuaire a, IdentitesExternes.Cle cle, String courriel, Map<String, Object> claims) {
        String role = Annuaires.admettre(a, courriel, claims);
        IdentitesExternes.IdentiteVerifiee v = new IdentitesExternes.IdentiteVerifiee(cle, courriel, IdentitesExternes.Provenance.annuaire(a.id(), a.tenantId()));
        return switch (ports.resolution().resoudre(v, new ServiceResolutionIdentite.Admission(Optional.of(role), true, a.provisionnementJat()))) {
            case ServiceResolutionIdentite.Reconnue r -> new Ouverture(r.compte(), ports.sessions().emettre(r.compte()), courriel, r.cree());
            case ServiceResolutionIdentite.LiaisonRequise l -> new LiaisonRequise(l.jeton(), courriel);
        };
    }
}
