package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.EtatFeuille;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleDeLaPeriode;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.LigneHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.NatureHeures;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort.Personne;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le contrôle des temps d'une période par la paie (ADR 0084) : chaque feuille avec ses heures, ses anomalies et sa
 * validation, et les personnes actives qui n'en ont aucune. Domaine pur.
 */
public final class ControleTemps {

    /** Au-delà, une semaine mérite un regard : c'est plus que la durée normale doublée de la plupart des conventions. */
    static final BigDecimal HEURES_ELEVEES = BigDecimal.valueOf(60);

    public record Donnees(List<FeuilleDeLaPeriode> feuilles, List<Personne> personnes, Map<String, DossierRemuneration> dossiers,
                          Map<String, ValidationTemps> validations, List<ReglePaie> regles) { }

    public record Ligne(FeuilleDeLaPeriode feuille, BigDecimal heuresRegulieres, BigDecimal heuresSupplementaires,
                        List<AnomalieTemps> anomalies, ValidationTemps validation) {

        public Ligne {
            anomalies = List.copyOf(anomalies);
        }

        /** Validée, et pour les heures telles qu'elles sont maintenant. */
        public boolean estValidee() {
            return validation != null && feuille.heures().map(validation::couvre).orElse(false);
        }

        /** Approuvée, calculable, pas encore payée : la paie peut la valider ou la retourner. */
        public boolean estValidable() {
            return feuille.etat() == EtatFeuille.APPROUVEE && anomalies.stream().noneMatch(AnomalieTemps::bloquante);
        }
    }

    public record Etat(List<Ligne> feuilles, List<Personne> sansFeuille) { }

    private ControleTemps() {
    }

    public static Etat controler(Donnees d) {
        Map<String, Personne> parId = d.personnes().stream().collect(Collectors.toMap(Personne::ressourceId, Function.identity(), (a, b) -> a));
        List<Ligne> lignes = d.feuilles().stream().map(f -> ligne(f, parId.get(f.ressourceId()), d)).toList();
        Set<String> avecFeuille = d.feuilles().stream().map(FeuilleDeLaPeriode::ressourceId).collect(Collectors.toSet());
        return new Etat(lignes, d.personnes().stream().filter(p -> !avecFeuille.contains(p.ressourceId())).toList());
    }

    private static Ligne ligne(FeuilleDeLaPeriode f, Personne personne, Donnees d) {
        List<LigneHeures> heures = f.heures().map(FeuilleAPayer::lignes).orElse(List.of());
        ValidationTemps validation = d.validations().get(f.feuilleId());
        return new Ligne(f, total(heures, NatureHeures.REGULIERE), total(heures, NatureHeures.SUPPLEMENTAIRE),
                anomalies(f, personne, d, validation), validation);
    }

    private static List<AnomalieTemps> anomalies(FeuilleDeLaPeriode f, Personne personne, Donnees d, ValidationTemps validation) {
        List<AnomalieTemps> a = new ArrayList<>();
        DossierRemuneration dossier = d.dossiers().get(f.ressourceId());
        ajouterSi(a, f.etat() != EtatFeuille.APPROUVEE && f.etat() != EtatFeuille.PAYEE, AnomalieTemps.NON_APPROUVEE);
        ajouterSi(a, f.heures().isEmpty(), AnomalieTemps.INCALCULABLE);
        ajouterSi(a, dossier == null, AnomalieTemps.SANS_DOSSIER);
        ajouterSi(a, dossier != null && sansTaux(f, personne, dossier, d.regles()), AnomalieTemps.SANS_TAUX);
        List<LigneHeures> heures = f.heures().map(FeuilleAPayer::lignes).orElse(List.of());
        BigDecimal tout = total(heures, NatureHeures.REGULIERE).add(total(heures, NatureHeures.SUPPLEMENTAIRE));
        ajouterSi(a, tout.compareTo(HEURES_ELEVEES) > 0, AnomalieTemps.HEURES_ELEVEES);
        boolean perimee = validation != null && validation.decision() == ValidationTemps.Decision.VALIDEE
                && f.heures().map(h -> !validation.couvre(h)).orElse(true);
        ajouterSi(a, perimee, AnomalieTemps.VALIDATION_PERIMEE);
        return a;
    }

    /** À l'heure, une seule heure sans taux suffit : la personne ne sera pas payée. */
    private static boolean sansTaux(FeuilleDeLaPeriode f, Personne personne, DossierRemuneration dossier, List<ReglePaie> regles) {
        if (dossier.mode() != ModeRemuneration.HORAIRE) {
            return false;
        }
        ProfilPaie profil = profil(personne, dossier);
        return f.heures().map(FeuilleAPayer::lignes).orElse(List.of()).stream()
                .anyMatch(l -> TauxDeBase.resoudre(dossier, profil, regles, LocalDate.parse(l.date())).isEmpty());
    }

    /** Le profil d'une personne pour les règles : titre d'emploi et convention des effectifs, échelon du dossier. */
    public static ProfilPaie profil(Personne personne, DossierRemuneration dossier) {
        return new ProfilPaie(personne == null ? null : personne.titreEmploi(), personne == null ? null : personne.convention(),
                dossier == null ? null : dossier.echelon());
    }

    private static void ajouterSi(List<AnomalieTemps> a, boolean condition, AnomalieTemps anomalie) {
        if (condition) {
            a.add(anomalie);
        }
    }

    static BigDecimal total(List<LigneHeures> lignes, NatureHeures nature) {
        return lignes.stream().filter(l -> l.nature() == nature).map(LigneHeures::heures).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
