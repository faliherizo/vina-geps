package ca.vina.nexu.saas.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Les règles d'un locataire (un établissement) — port de la partie décisionnelle de
 * {@code backend/src/saas/tenants.ts} : validation d'une création, slug d'URL, fin d'essai,
 * cycle de vie, verrou du pays.
 *
 * <p>Le nom n'est <strong>pas</strong> unique — « École Saint-Joseph » existe dans des dizaines
 * de centres ; la clé d'un établissement est son slug. Le pays porte la devise : il ne se change
 * plus dès qu'une transaction financière existe.
 */
public final class Locataires {

    public static final List<String> TYPES = List.of("government", "public_institution", "private_institution", "corporate", "country");
    public static final List<String> STATUTS = List.of("trial", "active", "suspended", "terminated");
    /** Les tables dont une seule ligne verrouille le pays (donc la devise). */
    public static final List<String> TABLES_FINANCIERES = List.of(
            "client_invoices", "expenses", "pay_runs", "gl_entries", "ap_bills", "credit_notes",
            "bank_transactions", "card_transactions", "fixed_assets", "income_tax_declarations", "budgets", "payments");
    private static final Pattern COURRIEL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final int JOURS_ESSAI_PAR_DEFAUT = 30;
    private static final long JOURS_PAR_MOIS = 30;

    private Locataires() {
    }

    /** Une validation refusée porte chaque motif, en français : l'écran les affiche tels quels. */
    public static final class ValidationException extends ca.vina.nexu.partage.erreurs.domaine.RefusException {
        public ValidationException(List<String> motifs) {
            super(motifs);
        }

        public List<String> motifs() {
            return erreurs();
        }
    }

    /** Un slug déjà pris : 409, pas 400 — le client ne doit pas réessayer tel quel. */
    public static final class ConflitException extends ca.vina.nexu.partage.erreurs.domaine.VerrouException {
        public ConflitException(String message) {
            super(message);
        }
    }

    /** Ce qu'une création reçoit, brut. */
    public record Demande(String name, String type, String country, String region, String planCode, Integer trialDays,
                          String dataResidency, String address, String city, String postalCode, String phone, String email, String slug) {

        /**
         * La demande dont la résidence non déclarée devient celle de l'hébergement réel (E-TN-19).
         * L'ancien défaut, {@code canadacentral} (Azure, Toronto), inscrivait au registre un lieu où
         * les données n'étaient pas : un organisme se lisait hors Québec, ou ailleurs qu'il n'est.
         */
        public Demande avecResidenceParDefaut(String regionHebergement) {
            return rogne(dataResidency) != null ? this : new Demande(name, type, country, region, planCode, trialDays,
                    regionHebergement, address, city, postalCode, phone, email, slug);
        }
    }

    /** Ce qui s'écrit, validé et normalisé. */
    public record Creation(String name, String type, String country, String region, String planCode, String trialEndsAt,
                           String dataResidency, String address, String city, String postalCode, String phone, String email, String slug) {
    }

    private static String rogne(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /** Normalise un slug d'URL : minuscules, alphanumérique + tirets, 2 à 40 caractères. */
    public static String normaliserSlug(String brut) {
        String s = NON_SLUG.matcher(String.valueOf(brut).toLowerCase(Locale.ROOT).trim()).replaceAll("-").replaceAll("^-+|-+$", "");
        if (s.length() < 2 || s.length() > 40) {
            throw new ValidationException(List.of("le slug d'URL doit compter de 2 à 40 caractères (a-z, 0-9, tirets)"));
        }
        return s;
    }

    /**
     * Valide une demande — tous les motifs à la fois, dans l'ordre du Node — et fixe la fin
     * d'essai. {@code paysConnu} et {@code paysDisponibles} viennent du registre des pays : le
     * domaine ne le lit pas, on le lui donne.
     */
    public static Creation valider(Demande d, Instant maintenant, Predicate<String> paysConnu, List<String> paysDisponibles) {
        List<String> motifs = new ArrayList<>();
        if (rogne(d.name()) == null) {
            motifs.add("le nom de l'établissement est requis");
        }
        if (d.type() == null || !TYPES.contains(d.type())) {
            motifs.add("type invalide (attendu : " + String.join(", ", TYPES) + ")");
        }
        String pays = d.country() == null ? null : d.country().toUpperCase(Locale.ROOT);
        if (pays == null || pays.length() != 2) {
            motifs.add("le pays doit être un code ISO à 2 lettres");
        } else if (!paysConnu.test(pays)) {
            motifs.add("pays « " + pays + " » non pris en charge (disponibles : " + String.join(", ", paysDisponibles) + ")");
        }
        if (d.planCode() == null || !Plans.CODES.contains(d.planCode())) {
            motifs.add("plan invalide (attendu : " + String.join(", ", Plans.CODES) + ")");
        }
        String residence = rogne(d.dataResidency());
        if (residence == null) {
            motifs.add("la résidence des données est requise : la déclarer, ou déclarer la région d'hébergement de la plateforme (REGION_DONNEES)");
        }
        String courriel = rogne(d.email());
        if (courriel != null && !COURRIEL.matcher(courriel).matches()) {
            motifs.add("courriel invalide « " + courriel + " »");
        }
        if (!motifs.isEmpty()) {
            throw new ValidationException(motifs);
        }
        int joursEssai = d.trialDays() == null ? JOURS_ESSAI_PAR_DEFAUT : d.trialDays();
        String finEssai = iso(maintenant.plus(Duration.ofDays(joursEssai)));
        String slug = rogne(d.slug()) == null ? null : normaliserSlug(d.slug());
        return new Creation(d.name().trim(), d.type(), pays, d.region(), d.planCode(), finEssai,
                residence,
                rogne(d.address()), rogne(d.city()), rogne(d.postalCode()), rogne(d.phone()), courriel, slug);
    }

    public static boolean courrielValide(String courriel) {
        return COURRIEL.matcher(courriel).matches();
    }

    /** Un code pays de deux lettres, en majuscules — pour le correctif du pays. */
    public static String paysValide(String brut) {
        String c = brut == null ? "" : brut.toUpperCase(Locale.ROOT);
        if (c.length() != 2) {
            throw new IllegalArgumentException("country must be 2-letter ISO code");
        }
        return c;
    }

    /** La fin d'abonnement d'une activation : {@code mois × 30 jours} après maintenant, comme le Node. */
    public static String finAbonnement(Instant maintenant, int mois) {
        return iso(maintenant.plus(Duration.ofDays(JOURS_PAR_MOIS * mois)));
    }

    /** L'instant au format de {@code Date.prototype.toISOString} : millisecondes toujours écrites. */
    static String iso(Instant instant) {
        String s = instant.toString();
        return s.contains(".") ? s : s.replace("Z", ".000Z");
    }
}
