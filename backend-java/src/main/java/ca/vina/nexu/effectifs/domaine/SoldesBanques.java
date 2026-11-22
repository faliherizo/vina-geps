package ca.vina.nexu.effectifs.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <strong>Les soldes des banques de vacances et de congés</strong> (26-015-DO, addenda 5 c ; M-01 ; AD-06b). BD Corpo
 * reste maître des soldes : l'organisme les <em>transmet</em> par l'API d'échange, la solution les tient tels que reçus
 * et s'en sert pour signaler une absence qui les dépasse. Un code d'absence nomme sa banque par l'attribut
 * {@value #ATTRIBUT_BANQUE}, et ses heures par jour par {@value #ATTRIBUT_HEURES_PAR_JOUR}.
 */
public final class SoldesBanques {

    public static final String ATTRIBUT_BANQUE = "banque";
    public static final String ATTRIBUT_HEURES_PAR_JOUR = "heuresParJour";
    /** Un envoi porte au plus ce nombre de soldes pour une personne : bien plus qu'il n'existe de banques. */
    public static final int SOLDES_PAR_ENVOI = 50;
    /** Un solde en heures reste dans cette borne, au-delà c'est une erreur de saisie à la source. */
    public static final BigDecimal HEURES_MAX = new BigDecimal("99999");
    private static final int DECIMALES = 2;
    /** Le code d'une banque est celui de BD Corpo : un ensemble ouvert, propre à l'organisme, d'où un format et non un enum. */
    private static final Pattern CODE_BANQUE = Pattern.compile("^[A-Z0-9_-]{1,40}$");

    private SoldesBanques() {
    }

    /** Un solde reçu : la banque, les heures (négatif admis : BD Corpo peut avancer des heures), la date à laquelle il vaut. */
    public record Solde(String ressourceId, String banqueCode, BigDecimal heures, String auJour, String recuLe) { }

    /**
     * Lit l'envoi {@code {"soldes":[{"banque","heures","au"}]}} d'une personne ; les écarts sont nommés ensemble, par leur
     * position, et une même banque ne peut figurer deux fois.
     */
    public static List<Solde> lire(String ressourceId, Object corps, String recuLe) {
        Object soldes = corps instanceof Map<?, ?> m ? m.get("soldes") : null;
        if (!(soldes instanceof List<?> liste) || liste.isEmpty() || liste.size() > SOLDES_PAR_ENVOI) {
            throw new EffectifsInvalideException("« soldes » : liste de 1 à " + SOLDES_PAR_ENVOI + " soldes attendue");
        }
        List<String> ecarts = new ArrayList<>();
        Map<String, Solde> parBanque = new LinkedHashMap<>();
        for (int i = 0; i < liste.size(); i++) {
            Optional<Solde> s = solde(ressourceId, liste.get(i), recuLe, "soldes[" + i + "]", ecarts);
            if (s.isPresent() && parBanque.putIfAbsent(s.get().banqueCode(), s.get()) != null) {
                ecarts.add("soldes[" + i + "].banque : « " + s.get().banqueCode() + " » déjà présente dans cet envoi");
            }
        }
        if (!ecarts.isEmpty()) {
            throw new EffectifsInvalideException(String.join(" ; ", ecarts));
        }
        return List.copyOf(parBanque.values());
    }

    private static Optional<Solde> solde(String ressourceId, Object brut, String recuLe, String ou, List<String> ecarts) {
        Map<?, ?> m = brut instanceof Map<?, ?> x ? x : Map.of();
        int avant = ecarts.size();
        String banque = m.get("banque") instanceof String b ? b.strip().toUpperCase(Locale.ROOT) : "";
        if (!CODE_BANQUE.matcher(banque).matches()) {
            ecarts.add(ou + ".banque : code de 1 à 40 lettres, chiffres, tirets ou soulignés");
        }
        BigDecimal heures = m.get("heures") instanceof Number n ? new BigDecimal(n.toString()).setScale(DECIMALES, RoundingMode.HALF_UP) : null;
        if (heures == null || heures.abs().compareTo(HEURES_MAX) > 0) {
            ecarts.add(ou + ".heures : nombre de -" + HEURES_MAX + " à " + HEURES_MAX);
        }
        String au = date(m.get("au"));
        if (au == null) {
            ecarts.add(ou + ".au : date AAAA-MM-JJ du solde");
        }
        return ecarts.size() == avant ? Optional.of(new Solde(ressourceId, banque, heures, au, recuLe)) : Optional.empty();
    }

    private static String date(Object brut) {
        if (!(brut instanceof String s)) {
            return null;
        }
        try {
            return LocalDate.parse(s.strip()).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Les faits du solde pour une demande d'absence — toujours fournis, car une règle qui lit un fait absent échoue :
     * sans banque nommée par le code, ou sans solde reçu, {@code connu} est faux et les heures valent zéro.
     */
    public record FaitsSolde(boolean connu, BigDecimal soldeHeures, BigDecimal heuresDemandees, BigDecimal soldeApresHeures) {

        public static FaitsSolde de(Optional<Solde> solde, int joursDemandes, Optional<BigDecimal> heuresParJour) {
            BigDecimal demandees = heuresParJour.map(h -> h.multiply(BigDecimal.valueOf(joursDemandes))).orElse(BigDecimal.ZERO)
                    .setScale(DECIMALES, RoundingMode.HALF_UP);
            BigDecimal heures = solde.map(Solde::heures).orElse(BigDecimal.ZERO).setScale(DECIMALES, RoundingMode.HALF_UP);
            return new FaitsSolde(solde.isPresent(), heures, demandees, solde.isPresent() ? heures.subtract(demandees) : BigDecimal.ZERO);
        }

        /** Le code de la banque d'un code d'absence, s'il en nomme une. */
        public static Optional<String> banqueDe(Map<String, Object> attributs) {
            return attributs.get(ATTRIBUT_BANQUE) instanceof String b && !b.isBlank() ? Optional.of(b.strip().toUpperCase(Locale.ROOT)) : Optional.empty();
        }

        /** Les heures par jour d'un code d'absence, s'il les règle. */
        public static Optional<BigDecimal> heuresParJourDe(Map<String, Object> attributs) {
            return attributs.get(ATTRIBUT_HEURES_PAR_JOUR) instanceof Number n ? Optional.of(new BigDecimal(n.toString())) : Optional.empty();
        }
    }
}
