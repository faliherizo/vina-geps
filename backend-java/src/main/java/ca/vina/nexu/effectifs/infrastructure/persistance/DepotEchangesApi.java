package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptateur SQLite de l'API d'échange (AD-06). Les états et les types d'objets sont passés en paramètres — jamais
 * écrits dans le SQL — depuis les ensembles fermés du domaine. Les places d'une page se lisent en une requête, pas une
 * par quart : une fenêtre de deux mois en compte des milliers.
 */
@Repository
public class DepotEchangesApi implements EchangesApiPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final List<String> ETATS = EchangesApi.ETATS_EXPOSES.stream().map(Enum::name).sorted().toList();
    private static final List<String> OBJETS = EchangesApi.OBJETS_QUART.stream().sorted().toList();
    private static final List<String> STATUTS = EchangesApi.STATUTS_VALIDES.stream().map(Enum::name).sorted().toList();
    private static final int ACTIF = 1;
    /**
     * Les horodatages ({@code Iso.de}) ont une largeur fixe : {@code uuuu-MM-dd'T'HH:mm:ss.SSS'Z'}. Le curseur commence
     * donc par l'horodatage de sa ligne, et la borne {@code horodatage >= substr(curseur, 1, 24)} — exacte, puisque la
     * comparaison concaténée l'implique — laisse SQLite parcourir l'index au lieu de tout le journal de l'organisme.
     */
    private static final int LONGUEUR_HORODATAGE = 24;

    private static final String QUARTS = "SELECT e.id, e.periodeId, e.uniteCode, e.date, e.debut, e.fin, e.natureCode, e.milieuId FROM eff_equipages e "
            + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat IN (?, ?) WHERE e.tenantId = ? ";
    private static final String PLACES = "SELECT p.id, p.equipageId, p.rang, p.corpsEmploiCode, p.ressourceId, r.matricule FROM eff_places p "
            + "JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
            + "JOIN eff_periodes pr ON pr.id = e.periodeId AND pr.tenantId = e.tenantId AND pr.etat IN (?, ?) "
            + "LEFT JOIN eff_ressources r ON r.id = p.ressourceId AND r.tenantId = p.tenantId WHERE p.tenantId = ? ";

    private final JdbcClient jdbc;

    public DepotEchangesApi(JdbcClient jdbc, SchemaPlanification planification, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EchangesApi.Quart> quartsExposes(String tenantId, EchangesApi.Fenetre fenetre) {
        String du = fenetre.du().toString();
        String au = fenetre.au().toString();
        Map<String, List<EchangesApi.Place>> places = places(PLACES + "AND e.date BETWEEN ? AND ? ORDER BY p.equipageId, p.rang",
                List.of(ETATS.get(0), ETATS.get(1), tenantId, du, au));
        return jdbc.sql(QUARTS + "AND e.date BETWEEN ? AND ? ORDER BY e.date, e.debut, e.id").params(ETATS.get(0), ETATS.get(1), tenantId, du, au)
                .query((rs, i) -> quart(rs, places)).list();
    }

    @Override
    public Map<String, String> equipagesDesPlaces(String tenantId, Collection<String> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> equipages = new HashMap<>();
        jdbc.sql("SELECT id, equipageId FROM eff_places WHERE tenantId = ? AND id IN (" + marques(placeIds.size()) + ")").params(parametres(tenantId, placeIds))
                .query((rs, i) -> Map.entry(rs.getString("id"), rs.getString("equipageId"))).list().forEach(e -> equipages.put(e.getKey(), e.getValue()));
        return equipages;
    }

    @Override
    public Map<String, EchangesApi.Quart> quartsExposesParId(String tenantId, Collection<String> equipageIds) {
        if (equipageIds.isEmpty()) {
            return Map.of();
        }
        String dans = "AND e.id IN (" + marques(equipageIds.size()) + ")";
        Map<String, List<EchangesApi.Place>> places = places(PLACES + dans + " ORDER BY p.equipageId, p.rang", parametres(ETATS.get(0), ETATS.get(1), tenantId, equipageIds));
        Map<String, EchangesApi.Quart> quarts = new HashMap<>();
        jdbc.sql(QUARTS + dans).params(parametres(ETATS.get(0), ETATS.get(1), tenantId, equipageIds)).query((rs, i) -> quart(rs, places)).list()
                .forEach(q -> quarts.put(q.id(), q));
        return quarts;
    }

    /** Une marque « ? » par valeur : seules les marques sont assemblées, les valeurs restent des paramètres liés. */
    private static String marques(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }

    private static List<Object> parametres(Object a, Collection<String> valeurs) {
        List<Object> p = new ArrayList<>();
        p.add(a);
        p.addAll(valeurs);
        return p;
    }

    private static List<Object> parametres(Object a, Object b, Object c, Collection<String> valeurs) {
        List<Object> p = new ArrayList<>(List.of(a, b, c));
        p.addAll(valeurs);
        return p;
    }

    @Override
    public List<DonneesIntegrationPort.EntreeJournal> journalQuartsApres(String tenantId, String curseur, int limite) {
        return jdbc.sql("SELECT id, horodatage, action, objetType, objetId, uniteCode, apres FROM eff_journal "
                        + "WHERE tenantId = ? AND objetType IN (?, ?) AND (? IS NULL OR (horodatage >= substr(?, 1, ?) AND horodatage || id > ?)) "
                        + "ORDER BY horodatage, id LIMIT ?")
                .params(tenantId, OBJETS.get(0), OBJETS.get(1), curseur, curseur, LONGUEUR_HORODATAGE, curseur, limite)
                .query((rs, i) -> new DonneesIntegrationPort.EntreeJournal(rs.getString("id"), rs.getString("horodatage"), rs.getString("action"),
                        rs.getString("objetType"), rs.getString("objetId"), rs.getString("uniteCode"),
                        rs.getString("apres") == null ? null : JSON.readValue(rs.getString("apres"), Object.class)))
                .list();
    }

    @Override
    public List<EchangesApi.Releve> relevesApres(String tenantId, String curseur, int limite) {
        List<FeuilleValidee> feuilles = jdbc.sql("SELECT f.id, f.ressourceId, r.matricule, f.uniteCode, f.debut, f.fin, f.statut, f.approuveeLe FROM eff_feuilles f "
                        + "LEFT JOIN eff_ressources r ON r.id = f.ressourceId AND r.tenantId = f.tenantId "
                        + "WHERE f.tenantId = ? AND f.statut IN (?, ?) AND f.approuveeLe IS NOT NULL "
                        + "AND (? IS NULL OR (f.approuveeLe >= substr(?, 1, ?) AND f.approuveeLe || f.id > ?)) ORDER BY f.approuveeLe, f.id LIMIT ?")
                .params(tenantId, STATUTS.get(0), STATUTS.get(1), curseur, curseur, LONGUEUR_HORODATAGE, curseur, limite)
                .query((rs, i) -> new FeuilleValidee(rs.getString("id"), rs.getString("ressourceId"), rs.getString("matricule"), rs.getString("uniteCode"),
                        rs.getString("debut"), rs.getString("fin"), rs.getString("statut"), rs.getString("approuveeLe"))).list();
        if (feuilles.isEmpty()) {
            return List.of();
        }
        Map<String, List<EchangesApi.Segment>> segments = segments(tenantId, feuilles.stream().map(FeuilleValidee::id).toList());
        return feuilles.stream().map(f -> new EchangesApi.Releve(f.approuveeLe() + f.id(), f.id(), f.ressourceId(), f.matricule(), f.uniteCode(),
                f.debut(), f.fin(), f.statut(), f.approuveeLe(), segments.getOrDefault(f.id(), List.of()))).toList();
    }

    /** Une ligne de {@code eff_feuilles} validée, avant d'y joindre ses segments. */
    private record FeuilleValidee(String id, String ressourceId, String matricule, String uniteCode, String debut, String fin, String statut,
                                  String approuveeLe) { }

    private Map<String, List<EchangesApi.Segment>> segments(String tenantId, List<String> feuilleIds) {
        List<Object> parametres = new ArrayList<>(List.of(tenantId, ACTIF));
        parametres.addAll(feuilleIds);
        Map<String, List<EchangesApi.Segment>> parFeuille = new LinkedHashMap<>();
        jdbc.sql("SELECT feuilleId, date, debut, fin, typeHeureCode, placeId FROM eff_segments_temps WHERE tenantId = ? AND actif = ? AND feuilleId IN ("
                        + marques(feuilleIds.size()) + ") ORDER BY feuilleId, debut").params(parametres)
                .query((rs, i) -> Map.entry(rs.getString("feuilleId"), new EchangesApi.Segment(rs.getString("date"), rs.getString("debut"),
                        rs.getString("fin"), rs.getString("typeHeureCode"), rs.getString("placeId")))).list()
                .forEach(e -> parFeuille.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return parFeuille;
    }

    private Map<String, List<EchangesApi.Place>> places(String sql, List<Object> parametres) {
        Map<String, List<EchangesApi.Place>> parEquipage = new LinkedHashMap<>();
        jdbc.sql(sql).params(parametres).query((rs, i) -> Map.entry(rs.getString("equipageId"), new EchangesApi.Place(rs.getString("id"),
                        rs.getInt("rang"), rs.getString("corpsEmploiCode"), rs.getString("ressourceId"), rs.getString("matricule")))).list()
                .forEach(e -> parEquipage.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return parEquipage;
    }

    private static EchangesApi.Quart quart(ResultSet rs, Map<String, List<EchangesApi.Place>> places) throws SQLException {
        String id = rs.getString("id");
        return new EchangesApi.Quart(id, rs.getString("periodeId"), rs.getString("uniteCode"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"),
                rs.getString("natureCode"), rs.getString("milieuId"), places.getOrDefault(id, List.of()));
    }
}
