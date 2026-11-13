package ca.vina.nexu.auth.infrastructure.services;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.Totp;
import ca.vina.nexu.partage.securite.JetonDeSession;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * La décision de connexion — port de {@code login}, {@code authenticatedTenants} et de la route
 * {@code POST /login} de {@code backend/src/auth/routes.ts}, sans le HTTP.
 *
 * <p>Le mot de passe est vérifié <strong>avant</strong> de révéler quoi que ce soit : lister les
 * établissements d'un nom de connexion sans mot de passe valide permettrait de les énumérer.
 */
public class ServiceConnexion {

    /** Ce que la connexion a décidé. */
    public sealed interface Issue permits ChoixEtablissement, Orphelin, Refus, SecondFacteur, Succes {
    }

    /** Un même nom de connexion, valide dans plusieurs établissements : à l'utilisateur de choisir. */
    public record ChoixEtablissement(List<String> tenantIds) implements Issue {
    }

    /** Identifiants justes, compte rattaché à aucun établissement. */
    public record Orphelin() implements Issue {
    }

    public record Refus() implements Issue {
    }

    /** Mot de passe juste, second facteur attendu ({@code question} avec ses questions, ou {@code totp}). */
    public record SecondFacteur(Compte compte, String methode, List<String> questions) implements Issue {
    }

    public record Succes(Compte compte, String jeton) implements Issue {
    }

    /** Ce que le corps de la requête peut porter pour le second facteur. */
    public record Facteurs(List<String> reponses, String codeTotp) {
    }

    private final DepotComptes comptes;
    private final DepotQuestionsSecretes questions;
    private final JetonDeSession jetons;
    private final Clock horloge;

    public ServiceConnexion(DepotComptes comptes, DepotQuestionsSecretes questions, JetonDeSession jetons, Clock horloge) {
        this.comptes = comptes;
        this.questions = questions;
        this.jetons = jetons;
        this.horloge = horloge;
    }

    /** Établissements où <em>ces identifiants</em> sont valides — jamais sur la seule foi du nom. */
    public List<String> etablissementsAuthentifies(String loginName, String motDePasse) {
        return comptes.candidats(loginName).stream()
                .map(t -> comptes.parNom(loginName, t))
                .filter(o -> o.map(c -> c.active() && comptes.verifierMotDePasse(c, motDePasse) && !estOrphelin(c)).orElse(false))
                .map(o -> o.orElseThrow().tenantId())
                .toList();
    }

    private boolean estOrphelin(Compte c) {
        return c.estOrphelin(comptes.tenantDeLEmploye(c.employeeId()));
    }

    public Issue connecter(String loginName, String motDePasse, String tenantId, Facteurs facteurs) {
        return connecter(loginName, motDePasse, tenantId, facteurs, false);
    }

    /**
     * @param globauxSeulement l'hôte des comptes globaux ({@code admin.} en déploiement par sous-domaine,
     *                         ADR 0033) : aucun compte d'établissement ne s'y résout, même à identifiants exacts.
     */
    public Issue connecter(String loginName, String motDePasse, String tenantId, Facteurs facteurs, boolean globauxSeulement) {
        if (tenantId == null && !globauxSeulement) {
            List<String> valides = etablissementsAuthentifies(loginName, motDePasse);
            if (valides.size() > 1) {
                return new ChoixEtablissement(valides.stream().filter(t -> t != null).toList());
            }
        }
        Optional<Compte> trouve = globauxSeulement ? comptes.parNom(loginName, null) : resoudre(loginName, motDePasse, tenantId);
        if (trouve.isEmpty() || !trouve.get().active() || !comptes.verifierMotDePasse(trouve.get(), motDePasse) || (globauxSeulement && trouve.get().tenantId() != null)) {
            return new Refus();
        }
        Compte c = trouve.get();
        if (estOrphelin(c)) {
            return new Orphelin();
        }
        return secondFacteur(c, facteurs).orElseGet(() -> new Succes(c, emettre(c)));
    }

    /**
     * Résolution établissement d'abord, puis repli sur un compte global — sans danger pour le
     * cloisonnement : un compte global est stocké sous son nom brut, jamais sous celui d'un
     * autre établissement. Sans établissement fourni, ce sont les identifiants qui le désignent.
     */
    private Optional<Compte> resoudre(String loginName, String motDePasse, String tenantId) {
        Optional<Compte> u = comptes.parNom(loginName, tenantId);
        if (u.isEmpty() && tenantId != null) {
            u = comptes.parNom(loginName, null);
        }
        if (u.isEmpty() && tenantId == null) {
            List<String> valides = etablissementsAuthentifies(loginName, motDePasse);
            if (valides.size() == 1) {
                u = comptes.parNom(loginName, valides.get(0));
            }
        }
        return u;
    }

    private Optional<Issue> secondFacteur(Compte c, Facteurs f) {
        if (c.securitySetupRequired()) {
            return Optional.empty();
        }
        if ("question".equals(c.securityMethod()) && !questions.verifier(c.id(), f.reponses())) {
            return Optional.of(new SecondFacteur(c, "question", questions.questions(c.id())));
        }
        if ("totp".equals(c.securityMethod())
                && !(c.totpSecret() != null && Totp.verifier(c.totpSecret(), f.codeTotp() == null ? "" : f.codeTotp(), horloge.millis()))) {
            return Optional.of(new SecondFacteur(c, "totp", List.of()));
        }
        return Optional.empty();
    }

    /** Un jeton pour un compte déjà authentifié ({@code issueSessionToken}). */
    public String emettre(Compte c) {
        return jetons.emettre(new JetonDeSession.Revendications(c.id(), c.nomDeConnexion(), c.role(), c.employeeId(), c.tenantId()));
    }
}
