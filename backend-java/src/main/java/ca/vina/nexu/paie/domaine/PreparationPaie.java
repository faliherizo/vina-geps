package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.NatureHeures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Des feuilles approuvées aux bulletins : une personne, un bulletin, qui somme ses heures de toutes ses feuilles de la
 * période. Personne n'est payé en silence à tort ni oublié : sans dossier, sans taux, ou avec des feuilles que la paie
 * n'a pas validées alors que l'organisme l'exige, la personne ou la feuille est nommée, et ses feuilles restent
 * approuvées.
 */
public final class PreparationPaie {

    /** Ce dont la préparation a besoin, hors des feuilles. */
    public record Sources(Bareme bareme, ReglagesPaie reglages, Map<String, DossierRemuneration> dossiers,
                          Function<String, Cumuls> cumuls, Supplier<String> identifiants, Variables variables) { }

    /**
     * Ce que l'ADR 0084 ajoute : les règles de paie variable, les jours fériés, les personnes des effectifs (titre
     * d'emploi, convention) et la dernière validation de chaque feuille.
     */
    public record Variables(List<ReglePaie> regles, Set<LocalDate> feries,
                            Map<String, ca.vina.nexu.partage.paie.PersonnesPourPaiePort.Personne> personnes,
                            Map<String, ValidationTemps> validations) {

        public static final Variables AUCUNE = new Variables(List.of(), Set.of(), Map.of(), Map.of());
    }

    public record Personne(String ressourceId, String matricule, String nom) { }

    public record FeuilleNonValidee(String feuilleId, String ressourceId, String nom, String debut) { }

    public record Resultat(List<Bulletin> bulletins, List<Personne> sansDossier, List<Personne> sansTaux, List<FeuilleNonValidee> nonValidees) { }

    private PreparationPaie() {
    }

    public static Resultat preparer(CyclePaie cycle, List<FeuilleAPayer> feuilles, Sources s) {
        List<FeuilleNonValidee> nonValidees = new ArrayList<>();
        Map<String, List<FeuilleAPayer>> parPersonne = new LinkedHashMap<>();
        for (FeuilleAPayer f : feuilles) {
            if (s.reglages().validationTempsExigee() && !estValidee(f, s.variables())) {
                nonValidees.add(new FeuilleNonValidee(f.feuilleId(), f.ressourceId(), f.nom(), f.debut()));
            } else {
                parPersonne.computeIfAbsent(f.ressourceId(), k -> new ArrayList<>()).add(f);
            }
        }
        Resultat r = new Resultat(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), nonValidees);
        parPersonne.forEach((ressourceId, siennes) -> payer(cycle, siennes, s, r));
        return r;
    }

    private static boolean estValidee(FeuilleAPayer f, Variables v) {
        ValidationTemps validation = v.validations().get(f.feuilleId());
        return validation != null && validation.couvre(f);
    }

    private static void payer(CyclePaie cycle, List<FeuilleAPayer> siennes, Sources s, Resultat r) {
        FeuilleAPayer premiere = siennes.getFirst();
        Personne personne = new Personne(premiere.ressourceId(), premiere.matricule(), premiere.nom());
        DossierRemuneration dossier = s.dossiers().get(premiere.ressourceId());
        if (dossier == null) {
            r.sansDossier().add(personne);
            return;
        }
        Optional<List<Gain>> gains = CalculGains.calculer(personneDuCalcul(dossier, s.variables()), siennes, contexte(cycle, s));
        if (gains.isEmpty()) {
            r.sansTaux().add(personne);
            return;
        }
        r.bulletins().add(bulletin(cycle, siennes, dossier, gains.get(), s));
    }

    private static CalculGains.Personne personneDuCalcul(DossierRemuneration dossier, Variables v) {
        return new CalculGains.Personne(dossier, ControleTemps.profil(v.personnes().get(dossier.ressourceId()), dossier));
    }

    private static CalculGains.Contexte contexte(CyclePaie cycle, Sources s) {
        return new CalculGains.Contexte(s.variables().regles(), s.variables().feries(), s.reglages().majorationSupplementaire(),
                LocalDate.parse(cycle.datePaie()));
    }

    static Bulletin bulletin(CyclePaie cycle, List<FeuilleAPayer> siennes, DossierRemuneration dossier, List<Gain> gains, Sources s) {
        Retenues r = CalculRetenues.calculer(s.bareme(), s.reglages(),
                new CalculRetenues.Entree(CalculGains.brut(gains), dossier.frequence(), s.cumuls().apply(dossier.ressourceId()), dossier.montants()));
        FeuilleAPayer premiere = siennes.getFirst();
        return new Bulletin(s.identifiants().get(), cycle.id(), dossier.ressourceId(), premiere.matricule(), premiere.nom(),
                cycle.datePaie(), heures(siennes, NatureHeures.REGULIERE), heures(siennes, NatureHeures.SUPPLEMENTAIRE), r,
                siennes.stream().map(FeuilleAPayer::feuilleId).toList(), gains);
    }

    static BigDecimal heures(List<FeuilleAPayer> feuilles, NatureHeures nature) {
        return ControleTemps.total(feuilles.stream().flatMap(f -> f.lignes().stream()).toList(), nature);
    }
}
