package ca.vina.nexu.paie.infrastructure.persistance;

import ca.vina.nexu.paie.domaine.ConditionsRegle;
import ca.vina.nexu.paie.domaine.EcritureReglesPaiePort;
import ca.vina.nexu.paie.domaine.LectureReglesPaiePort;
import ca.vina.nexu.paie.domaine.PlageHoraire;
import ca.vina.nexu.paie.domaine.ReglePaie;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.paie.domaine.TypeRegle;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les règles de paie variable en base (changeset 0018, ADR 0084). Les jours et les codes de paie sont des listes
 * séparées par des virgules ; les montants, des TEXT décimaux comme le reste de la paie.
 */
public class DepotReglesPaie implements LectureReglesPaiePort, EcritureReglesPaiePort {

    private static final String SEPARATEUR = ",";
    private static final String COLONNES = "SELECT id, code, libelle, type, valeur, titreEmploi, convention, echelon, heureDebut, heureFin, jours, "
            + "feriesSeulement, codesPaie, valideDu, valideAu, actif FROM paie_regles WHERE tenantId = ?";

    private final JdbcClient jdbc;

    public DepotReglesPaie(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ReglePaie> regles(String tenantId) {
        return jdbc.sql(COLONNES + " ORDER BY type, code, valideDu").params(tenantId).query((rs, i) -> regle(rs)).list();
    }

    @Override
    public Optional<ReglePaie> regle(String tenantId, String id) {
        return jdbc.sql(COLONNES + " AND id = ?").params(tenantId, id).query((rs, i) -> regle(rs)).optional();
    }

    @Override
    public void enregistrer(Trace t, ReglePaie r) {
        ConditionsRegle c = r.conditions();
        jdbc.sql("INSERT INTO paie_regles (id, tenantId, code, libelle, type, valeur, titreEmploi, convention, echelon, heureDebut, heureFin, jours, "
                        + "feriesSeulement, codesPaie, valideDu, valideAu, actif, modifieLe, modifiePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET code = excluded.code, libelle = excluded.libelle, type = excluded.type, valeur = excluded.valeur, "
                        + "titreEmploi = excluded.titreEmploi, convention = excluded.convention, echelon = excluded.echelon, heureDebut = excluded.heureDebut, "
                        + "heureFin = excluded.heureFin, jours = excluded.jours, feriesSeulement = excluded.feriesSeulement, codesPaie = excluded.codesPaie, "
                        + "valideDu = excluded.valideDu, valideAu = excluded.valideAu, actif = excluded.actif, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(r.id(), t.tenantId(), r.code(), r.libelle(), r.type().code(), r.valeur().toPlainString(), c.titreEmploi(), c.convention(), c.echelon(),
                        c.plage() == null ? null : c.plage().debut().toString(), c.plage() == null ? null : c.plage().fin().toString(),
                        liste(c.jours().stream().sorted().map(DayOfWeek::name).toList()), c.feriesSeulement() ? 1 : 0, liste(c.codesPaie().stream().sorted().toList()),
                        r.valideDu().toString(), r.valideAu() == null ? null : r.valideAu().toString(), r.actif() ? 1 : 0, t.instant(), t.acteur())
                .update();
    }

    private static ReglePaie regle(ResultSet rs) throws SQLException {
        String debut = rs.getString("heureDebut");
        PlageHoraire plage = debut == null ? null : new PlageHoraire(LocalTime.parse(debut), LocalTime.parse(rs.getString("heureFin")));
        Set<DayOfWeek> jours = elements(rs.getString("jours")).stream().map(DayOfWeek::valueOf).collect(Collectors.toSet());
        int lu = rs.getInt("echelon");
        Integer echelon = rs.wasNull() ? null : lu;
        ConditionsRegle c = new ConditionsRegle(rs.getString("titreEmploi"), rs.getString("convention"), echelon, plage, jours,
                rs.getInt("feriesSeulement") == 1, Set.copyOf(elements(rs.getString("codesPaie"))));
        String au = rs.getString("valideAu");
        return new ReglePaie(rs.getString("id"), rs.getString("code"), rs.getString("libelle"), TypeRegle.de(rs.getString("type")),
                new BigDecimal(rs.getString("valeur")), c, LocalDate.parse(rs.getString("valideDu")), au == null ? null : LocalDate.parse(au),
                rs.getInt("actif") == 1);
    }

    private static String liste(Collection<String> elements) {
        return String.join(SEPARATEUR, elements);
    }

    private static List<String> elements(String texte) {
        return texte == null || texte.isEmpty() ? List.of() : Arrays.asList(texte.split(SEPARATEUR));
    }
}
