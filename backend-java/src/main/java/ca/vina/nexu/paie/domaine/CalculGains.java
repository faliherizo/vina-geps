package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.LigneHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.NatureHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.Plage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les gains d'une personne pour un cycle (ADR 0084) : ses heures à leur taux de base du jour, les heures supplémentaires
 * majorées, ou sa part de salaire ; puis les primes et les forfaits des règles de paie variable qui la visent. Domaine
 * pur : ni horloge, ni base.
 */
public final class CalculGains {

    /** Ce que le cycle sait pour tout le monde : les règles, les jours fériés, la majoration et la date de paie. */
    public record Contexte(List<ReglePaie> regles, Set<LocalDate> feries, BigDecimal majoration, LocalDate datePaie) {

        public Contexte {
            regles = List.copyOf(regles);
            feries = Set.copyOf(feries);
        }
    }

    public record Personne(DossierRemuneration dossier, ProfilPaie profil) { }

    private static final BigDecimal MINUTES_HEURE = BigDecimal.valueOf(60);
    private static final int ECHELLE_INTERMEDIAIRE = 10;
    private static final int DECIMALES_HEURES = 2;
    private static final String LIBELLE_REGULIER = "Heures régulières";
    private static final String LIBELLE_SUPPLEMENTAIRE = "Heures supplémentaires";
    private static final String LIBELLE_SALAIRE = "Salaire";

    private CalculGains() {
    }

    /** Vide quand une heure à payer n'a pas de taux : la personne est nommée « taux introuvable », pas payée à 0 $. */
    public static Optional<List<Gain>> calculer(Personne p, List<FeuilleAPayer> feuilles, Contexte c) {
        Optional<List<Gain>> base = switch (p.dossier().mode()) {
            case HORAIRE -> heures(p, feuilles, c);
            case SALAIRE -> Optional.of(salaire(p, feuilles, c));
        };
        return base.map(gains -> {
            List<Gain> tous = new ArrayList<>(gains);
            tous.addAll(primes(p, feuilles, c));
            tous.addAll(forfaits(p, feuilles, c));
            return List.copyOf(tous);
        });
    }

    public static BigDecimal brut(List<Gain> gains) {
        return gains.stream().map(Gain::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Heures et salaire ───────────────────────────────────────────────────

    private static Optional<List<Gain>> heures(Personne p, List<FeuilleAPayer> feuilles, Contexte c) {
        Map<List<Object>, BigDecimal> parTaux = new LinkedHashMap<>();
        for (LigneHeures l : lignes(feuilles)) {
            Optional<BigDecimal> taux = TauxDeBase.resoudre(p.dossier(), p.profil(), c.regles(), LocalDate.parse(l.date()));
            if (taux.isEmpty()) {
                return Optional.empty();
            }
            parTaux.merge(List.of(l.nature(), taux.get()), l.heures(), BigDecimal::add);
        }
        return Optional.of(parTaux.entrySet().stream()
                .map(e -> gainHoraire((NatureHeures) e.getKey().get(0), (BigDecimal) e.getKey().get(1), e.getValue(), c.majoration())).toList());
    }

    /** Au salaire, la part de l'année ; les heures supplémentaires ne se paient que si un taux est connu. */
    private static List<Gain> salaire(Personne p, List<FeuilleAPayer> feuilles, Contexte c) {
        DossierRemuneration d = p.dossier();
        BigDecimal part = CalculRetenues.cent(d.salaireAnnuel().divide(BigDecimal.valueOf(d.frequence().paiesParAn()), ECHELLE_INTERMEDIAIRE, RoundingMode.HALF_UP));
        List<Gain> gains = new ArrayList<>(List.of(new Gain(Gain.CODE_SALAIRE, LIBELLE_SALAIRE, Gain.Nature.SALAIRE, BigDecimal.ONE, part, part)));
        Map<BigDecimal, BigDecimal> parTaux = new LinkedHashMap<>();
        lignes(feuilles).stream().filter(l -> l.nature() == NatureHeures.SUPPLEMENTAIRE).forEach(l ->
                TauxDeBase.resoudre(d, p.profil(), c.regles(), LocalDate.parse(l.date())).ifPresent(t -> parTaux.merge(t, l.heures(), BigDecimal::add)));
        parTaux.forEach((taux, h) -> gains.add(gainHoraire(NatureHeures.SUPPLEMENTAIRE, taux, h, c.majoration())));
        return gains;
    }

    private static Gain gainHoraire(NatureHeures nature, BigDecimal taux, BigDecimal heures, BigDecimal majoration) {
        return switch (nature) {
            case REGULIERE -> new Gain(Gain.CODE_REGULIER, LIBELLE_REGULIER, Gain.Nature.HEURES_REGULIERES, heures, taux,
                    CalculRetenues.cent(heures.multiply(taux)));
            case SUPPLEMENTAIRE -> {
                BigDecimal majore = taux.multiply(BigDecimal.ONE.add(majoration));
                yield new Gain(Gain.CODE_SUPPLEMENTAIRE, LIBELLE_SUPPLEMENTAIRE, Gain.Nature.HEURES_SUPPLEMENTAIRES, heures, majore,
                        CalculRetenues.cent(heures.multiply(majore)));
            }
        };
    }

    private static List<LigneHeures> lignes(List<FeuilleAPayer> feuilles) {
        return feuilles.stream().flatMap(f -> f.lignes().stream()).toList();
    }

    // ── Primes et forfaits ──────────────────────────────────────────────────

    private static List<Gain> primes(Personne p, List<FeuilleAPayer> feuilles, Contexte c) {
        List<Plage> plages = feuilles.stream().flatMap(f -> f.plages().stream()).toList();
        return c.regles().stream().filter(r -> r.type().estUnePrime() && r.vise(p.profil()))
                .map(r -> prime(r, p, plages, c)).flatMap(Optional::stream).toList();
    }

    private static Optional<Gain> prime(ReglePaie r, Personne p, List<Plage> plages, Contexte c) {
        BigDecimal minutes = BigDecimal.ZERO;
        BigDecimal montant = BigDecimal.ZERO;
        for (Plage plage : plages.stream().filter(pl -> r.conditions().admettentLeCode(pl.codePaie())).toList()) {
            for (Map.Entry<LocalDate, Long> jour : HeuresAdmissibles.minutes(plage, r.conditions().plage(), j -> r.admetLeJour(j, c.feries())).entrySet()) {
                BigDecimal heures = BigDecimal.valueOf(jour.getValue()).divide(MINUTES_HEURE, ECHELLE_INTERMEDIAIRE, RoundingMode.HALF_UP);
                minutes = minutes.add(BigDecimal.valueOf(jour.getValue()));
                montant = montant.add(heures.multiply(parHeure(r, p, jour.getKey(), c)));
            }
        }
        if (minutes.signum() == 0) {
            return Optional.empty();
        }
        BigDecimal heures = minutes.divide(MINUTES_HEURE, DECIMALES_HEURES, RoundingMode.HALF_UP);
        Gain.Nature nature = r.type() == TypeRegle.PRIME_HORAIRE ? Gain.Nature.PRIME_HORAIRE : Gain.Nature.PRIME_POURCENTAGE;
        return Optional.of(new Gain(r.code(), r.libelle(), nature, heures, r.valeur(), CalculRetenues.cent(montant)));
    }

    /** Ce que rapporte une heure admissible ce jour-là : la valeur, ou sa fraction du taux de base du jour. */
    private static BigDecimal parHeure(ReglePaie r, Personne p, LocalDate jour, Contexte c) {
        if (r.type() == TypeRegle.PRIME_HORAIRE) {
            return r.valeur();
        }
        return TauxDeBase.resoudre(p.dossier(), p.profil(), c.regles(), jour).orElse(BigDecimal.ZERO).multiply(r.valeur());
    }

    /** Un forfait se paie une fois par bulletin, à qui a des heures payées ou un salaire, s'il est en vigueur à la date de paie. */
    private static List<Gain> forfaits(Personne p, List<FeuilleAPayer> feuilles, Contexte c) {
        boolean paye = p.dossier().mode() == ModeRemuneration.SALAIRE || !lignes(feuilles).isEmpty();
        return c.regles().stream().filter(r -> paye && r.type() == TypeRegle.FORFAIT && r.enVigueurLe(c.datePaie()) && r.vise(p.profil()))
                .map(r -> new Gain(r.code(), r.libelle(), Gain.Nature.FORFAIT, BigDecimal.ONE, r.valeur(), CalculRetenues.cent(r.valeur()))).toList();
    }
}
