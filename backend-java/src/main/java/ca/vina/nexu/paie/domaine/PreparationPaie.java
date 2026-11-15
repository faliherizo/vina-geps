package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.FeuilleAPayer;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.LigneHeures;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort.NatureHeures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Des feuilles approuvées aux bulletins : une personne, un bulletin, qui somme ses heures de toutes ses feuilles de la
 * période. Une personne sans dossier de rémunération n'est pas payée, et ses feuilles restent approuvées — elle est
 * nommée dans {@code sansDossier} pour qu'on la voie, pas oubliée en silence.
 */
public final class PreparationPaie {

    /** Ce dont la préparation a besoin, hors des feuilles : le barème, les réglages, les dossiers, les cumuls, les identifiants. */
    public record Sources(Bareme bareme, ReglagesPaie reglages, Map<String, DossierRemuneration> dossiers,
                          Function<String, Cumuls> cumuls, Supplier<String> identifiants) { }

    public record Personne(String ressourceId, String matricule, String nom) { }

    public record Resultat(List<Bulletin> bulletins, List<Personne> sansDossier) { }

    private PreparationPaie() {
    }

    public static Resultat preparer(CyclePaie cycle, List<FeuilleAPayer> feuilles, Sources s) {
        Map<String, List<FeuilleAPayer>> parPersonne = new LinkedHashMap<>();
        feuilles.forEach(f -> parPersonne.computeIfAbsent(f.ressourceId(), k -> new ArrayList<>()).add(f));
        List<Bulletin> bulletins = new ArrayList<>();
        List<Personne> sansDossier = new ArrayList<>();
        parPersonne.forEach((ressourceId, siennes) -> {
            DossierRemuneration dossier = s.dossiers().get(ressourceId);
            if (dossier == null) {
                sansDossier.add(new Personne(ressourceId, siennes.getFirst().matricule(), siennes.getFirst().nom()));
            } else {
                bulletins.add(bulletin(cycle, siennes, dossier, s));
            }
        });
        return new Resultat(bulletins, sansDossier);
    }

    static Bulletin bulletin(CyclePaie cycle, List<FeuilleAPayer> siennes, DossierRemuneration dossier, Sources s) {
        BigDecimal regulieres = heures(siennes, NatureHeures.REGULIERE);
        BigDecimal supplementaires = heures(siennes, NatureHeures.SUPPLEMENTAIRE);
        BigDecimal brut = dossier.brut(regulieres, supplementaires, s.reglages().majorationSupplementaire());
        Retenues r = CalculRetenues.calculer(s.bareme(), s.reglages(),
                new CalculRetenues.Entree(brut, dossier.frequence(), s.cumuls().apply(dossier.ressourceId()), dossier.montants()));
        FeuilleAPayer premiere = siennes.getFirst();
        return new Bulletin(s.identifiants().get(), cycle.id(), dossier.ressourceId(), premiere.matricule(), premiere.nom(),
                cycle.datePaie(), regulieres, supplementaires, r, siennes.stream().map(FeuilleAPayer::feuilleId).toList());
    }

    static BigDecimal heures(List<FeuilleAPayer> feuilles, NatureHeures nature) {
        return feuilles.stream().flatMap(f -> f.lignes().stream()).filter(l -> l.nature() == nature)
                .map(LigneHeures::heures).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
