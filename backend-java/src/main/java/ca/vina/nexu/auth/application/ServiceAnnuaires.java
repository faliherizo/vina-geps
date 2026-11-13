package ca.vina.nexu.auth.application;

import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.auth.domaine.RegistreAnnuaires;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * La connexion par <strong>un des annuaires de l'organisme</strong> (P-TN-2) : le flux OIDC +
 * PKCE de l'Entra ID d'instance ({@link Oidc}), avec l'autorité, le client et le secret de
 * l'annuaire choisi, puis l'admission par l'organisme ({@link Annuaires#admettre}) et le compte
 * <em>dans cet organisme</em>.
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

    /** Une connexion admise : le compte de l'organisme, son jeton, et s'il vient d'être créé. */
    public record Ouverture(Compte compte, String jeton, String courriel, boolean cree) {
    }

    /** Les ports : l'annuaire, les comptes, les sessions et l'échange du code. */
    public record Ports(RegistreAnnuaires annuaires, Annuaires.Comptes comptes, Annuaires.Sessions sessions, Annuaires.Echange echange) {
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

    private Oidc.Configuration configuration(Annuaires.Annuaire a, String origine) {
        String secret = Optional.ofNullable(env.get(a.variableSecret())).filter(s -> !s.isEmpty())
                .orElseThrow(() -> new ConfigurationIncompleteException("le secret de l'annuaire n'est pas déployé (variable " + a.variableSecret() + ")"));
        return Oidc.configuration(Map.of("AZURE_CLIENT_ID", a.clientId(), "AZURE_CLIENT_SECRET", secret, "AZURE_TENANT_ID", a.locataireEntra(),
                "AZURE_REDIRECT_URI", origine + CHEMIN_RAPPEL), Optional.empty());
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
    public Ouverture completer(String code, String state) {
        EnCours e = Optional.ofNullable(enCours.remove(state)).filter(x -> !x.demande().expiree(horloge.millis()))
                .orElseThrow(() -> new IntrouvableException("demande de connexion inconnue ou expirée"));
        Annuaires.Annuaire a = actif(e.annuaireId());
        Map<String, Object> claims = ports.echange().echanger(code, configuration(a, e.retour()), e.demande());
        String courriel = Oidc.courriel(claims);
        String role = Annuaires.admettre(a, courriel, claims);
        Optional<Compte> existant = ports.comptes().duCourriel(a.tenantId(), courriel);
        existant.filter(u -> !u.active()).ifPresent(u -> {
            throw new Annuaires.ConnexionRefuseeException("le compte « " + courriel + " » est désactivé dans l'application");
        });
        Compte u = existant.map(c -> role.equals(c.role()) ? c : ports.comptes().reclasser(c, role))
                .orElseGet(() -> ports.comptes().creer(a.tenantId(), courriel, role));
        return new Ouverture(u, ports.sessions().emettre(u), courriel, existant.isEmpty());
    }
}
