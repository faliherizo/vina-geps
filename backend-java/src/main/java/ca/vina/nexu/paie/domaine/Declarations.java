package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ce que l'employeur déclare et verse, tiré des bulletins des cycles traités : les remises du mois (Revenu Québec et
 * ARC) et les relevés de l'année (RL-1 et T4).
 */
public final class Declarations {

    /** Remettant mensuel régulier : la remise d'un mois est due le 15 du mois suivant. */
    static final int JOUR_ECHEANCE = 15;

    /** À Revenu Québec : impôt du Québec, RRQ et RQAP (les deux parts), FSS. À l'ARC : impôt fédéral, AE (les deux parts). */
    public record Remise(String mois, String echeance, BigDecimal impotQuebec, BigDecimal rrq, BigDecimal rqap, BigDecimal fss,
                         BigDecimal revenuQuebec, BigDecimal impotFederal, BigDecimal ae, BigDecimal arc, BigDecimal cnt, BigDecimal cnesst) { }

    /** RL-1 : A, B (RRQ ; la part RRQ2 à part), C, E, G, H, I. */
    public record Rl1(BigDecimal a, BigDecimal b, BigDecimal rrq2, BigDecimal c, BigDecimal e, BigDecimal g, BigDecimal h, BigDecimal i) { }

    /** T4 : 14, 17 (RRQ ; la part RRQ2 à part), 18, 22, 24, 26, 55, 56. */
    public record T4(BigDecimal c14, BigDecimal c17, BigDecimal rrq2, BigDecimal c18, BigDecimal c22, BigDecimal c24,
                     BigDecimal c26, BigDecimal c55, BigDecimal c56) { }

    public record Releve(String ressourceId, String matricule, String nom, Rl1 rl1, T4 t4) { }

    private Declarations() {
    }

    public static List<Remise> remises(List<Bulletin> traites) {
        Map<YearMonth, List<Retenues>> parMois = traites.stream().collect(Collectors.groupingBy(
                b -> YearMonth.from(LocalDate.parse(b.datePaie())), TreeMap::new, Collectors.mapping(Bulletin::retenues, Collectors.toList())));
        return parMois.entrySet().stream().map(e -> remise(e.getKey(), e.getValue())).toList();
    }

    static Remise remise(YearMonth mois, List<Retenues> rs) {
        BigDecimal impotQuebec = somme(rs, Retenues::impotQuebec);
        BigDecimal rrq = somme(rs, r -> r.rrq().add(r.rrq2()).add(r.employeur().rrq()).add(r.employeur().rrq2()));
        BigDecimal rqap = somme(rs, r -> r.rqap().add(r.employeur().rqap()));
        BigDecimal fss = somme(rs, r -> r.employeur().fss());
        BigDecimal impotFederal = somme(rs, Retenues::impotFederal);
        BigDecimal ae = somme(rs, r -> r.ae().add(r.employeur().ae()));
        return new Remise(mois.toString(), mois.plusMonths(1).atDay(JOUR_ECHEANCE).toString(), impotQuebec, rrq, rqap, fss,
                impotQuebec.add(rrq).add(rqap).add(fss), impotFederal, ae, impotFederal.add(ae),
                somme(rs, r -> r.employeur().cnt()), somme(rs, r -> r.employeur().cnesst()));
    }

    public static List<Releve> releves(List<Bulletin> traites) {
        Map<String, List<Bulletin>> parPersonne = traites.stream().collect(Collectors.groupingBy(Bulletin::ressourceId, TreeMap::new, Collectors.toList()));
        return parPersonne.values().stream().map(Declarations::releve).toList();
    }

    static Releve releve(List<Bulletin> siens) {
        List<Retenues> rs = siens.stream().map(Bulletin::retenues).toList();
        BigDecimal brut = somme(rs, Retenues::brut);
        BigDecimal rrq = somme(rs, Retenues::rrq);
        BigDecimal rrq2 = somme(rs, Retenues::rrq2);
        BigDecimal ae = somme(rs, Retenues::ae);
        BigDecimal rqap = somme(rs, Retenues::rqap);
        BigDecimal gainsRrq = somme(rs, Retenues::gainsRrq);
        BigDecimal gainsRqap = somme(rs, Retenues::gainsRqap);
        Bulletin dernier = siens.getLast();
        return new Releve(dernier.ressourceId(), dernier.matricule(), dernier.nom(),
                new Rl1(brut, rrq, rrq2, ae, somme(rs, Retenues::impotQuebec), gainsRrq, rqap, gainsRqap),
                new T4(brut, rrq, rrq2, ae, somme(rs, Retenues::impotFederal), somme(rs, Retenues::gainsAe), gainsRrq, rqap, gainsRqap));
    }

    static BigDecimal somme(List<Retenues> rs, Function<Retenues, BigDecimal> champ) {
        return rs.stream().map(champ).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
