package ca.vina.nexu.auth.domaine;

import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Emprunt d'identité</strong> (CSSMB 25 ; ADR 0038) — les règles, sans E/S ; port de
 * {@code auth/emprunt.ts}. Un administrateur agit <em>comme</em> un compte de son organisme, une heure au
 * plus, tracé sous les deux identités ; jamais un compte d'administration, jamais soi-même, jamais depuis
 * une session déjà empruntée ; le réglage de l'organisme est fermé par défaut.
 */
public final class Emprunt {

    private Emprunt() {
    }

    public static final int DUREE_MINUTES = 60;
    public static final int LONGUEUR_MOTIF_MINIMUM = 5;

    private static final List<Role> EMPRUNTEURS = List.of(Role.ADMIN, Role.SUPERADMIN);
    private static final List<Role> NON_EMPRUNTABLES = List.of(Role.ADMIN, Role.SUPERADMIN);

    /** Qui demande : son compte, son rôle, son organisme, et l'emprunt qu'il porte déjà s'il y en a un. */
    public record Emprunteur(String id, String role, String tenantId, String emprunteur) {
    }

    public record Cible(String id, String role, String tenantId, boolean active) {
    }

    /** Le refus nommé, ou vide si l'emprunt est permis ; l'ordre va du réglage au détail. */
    public static Optional<String> refus(Emprunteur e, Cible c, boolean reglageActif) {
        if (Role.de(e.role()).filter(EMPRUNTEURS::contains).isEmpty()) {
            return Optional.of("réservé à l'administration de l'organisme");
        }
        if (e.emprunteur() != null) {
            return Optional.of("une session empruntée n'emprunte pas à son tour");
        }
        if (!reglageActif) {
            return Optional.of("l'emprunt d'identité n'est pas autorisé pour cet organisme : ouvrez-le dans Administration › Comptes");
        }
        if (c == null) {
            return Optional.of("compte introuvable");
        }
        if (c.id().equals(e.id())) {
            return Optional.of("on n'emprunte pas sa propre identité");
        }
        if (Role.de(c.role()).filter(NON_EMPRUNTABLES::contains).isPresent()) {
            return Optional.of("un compte d'administration ne s'emprunte pas");
        }
        if (e.tenantId() != null && !e.tenantId().equals(c.tenantId())) {
            return Optional.of("compte hors de votre organisme");
        }
        if (!c.active()) {
            return Optional.of("compte désactivé");
        }
        return Optional.empty();
    }

    /**
     * Sous emprunt, les portes de première connexion de la cible (mot de passe à changer, facteurs à
     * régler) ne s'ouvrent pas : ce sont <em>ses</em> secrets, interdits à l'emprunteur — l'écran doit
     * entrer directement, pas s'arrêter sur un formulaire que personne ne peut remplir.
     */
    public static Map<String, Object> sansPortesDePremiereConnexion(Map<String, Object> vue) {
        Map<String, Object> v = new LinkedHashMap<>(vue);
        v.put("mustChangePassword", false);
        v.put("securitySetupRequired", false);
        return v;
    }

    public static String expiration(String debut, int dureeMinutes) {
        return Iso.de(Instant.parse(debut).plus(dureeMinutes, ChronoUnit.MINUTES));
    }

    /** Le motif est écrit au journal : il doit dire quelque chose. */
    public static Optional<String> refusDuMotif(Object motif) {
        return motif instanceof String s && s.trim().length() >= LONGUEUR_MOTIF_MINIMUM ? Optional.empty()
                : Optional.of("motif requis (cinq caractères au moins) : il est écrit au journal");
    }
}
