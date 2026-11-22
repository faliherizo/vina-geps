package ca.vina.nexu.auth.domaine;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * <strong>La demande de liaison soumise à l'administration</strong> (ADR 0093) : la troisième preuve. Une personne
 * que son annuaire a authentifiée, mais qui ne peut ni saisir le mot de passe du compte existant ni présenter une
 * invitation, demande qu'un administrateur de l'organisme relie son identité au compte que son courriel désigne.
 * L'administrateur approuve ou refuse ; rien n'est relié avant.
 */
public final class DemandesLiaison {

    private DemandesLiaison() {
    }

    public enum Statut {
        EN_ATTENTE("en_attente"), APPROUVEE("approuvee"), REFUSEE("refusee");

        private final String code;

        Statut(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Statut de(String code) {
            return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("statut de demande inconnu : « " + code + " »"));
        }
    }

    /** Une demande : l'identité vérifiée, le compte candidat (trouvé par le courriel, un indice), et la décision. */
    public record Demande(String id, IdentitesExternes.Cle cle, String courriel, String tenantId, Optional<String> annuaireId, String compteId,
                          Statut statut, Instant creeLe, Optional<Instant> decideeLe, Optional<String> decideePar) {

        public Demande decider(Statut decision, Instant le, String par) {
            return new Demande(id, cle, courriel, tenantId, annuaireId, compteId, decision, creeLe, Optional.of(le), Optional.of(par));
        }
    }

    /** Port de lecture. */
    public interface Registre {
        Optional<Demande> parId(String id);

        /** Les demandes en attente d'un organisme, les plus anciennes d'abord. */
        List<Demande> enAttente(String tenantId);

        /** La demande en attente de cette identité, s'il y en a une : on n'en pose pas deux. */
        Optional<Demande> enAttentePour(IdentitesExternes.Cle cle);
    }

    /** Port d'écriture. */
    public interface Ecriture {
        void poser(Demande demande);

        void decider(Demande demande);
    }
}
