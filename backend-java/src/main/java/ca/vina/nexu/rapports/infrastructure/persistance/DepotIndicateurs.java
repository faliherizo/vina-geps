package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.Indicateurs;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Les indicateurs de l'établissement</b> — la table {@code rap_indicateurs} (migration Node), jumeau de
 * {@code serviceIndicateurs.ts}. Une lecture rend la forme du contrat HTTP : les filtres redeviennent
 * des objets, les ensembles fermés leurs codes.
 */
@Repository
public class DepotIndicateurs {

    private static final String CHAMPS = "id, code, libelle, source, format, numerateur, denominateur, axe, filtresJson, seuil, direction, marge, actif";

    /** Un indicateur rangé : son identifiant, sa définition validée, et s'il est encore proposé. */
    public record Indicateur(String id, Indicateurs.Definition definition, boolean actif) {

        public Map<String, Object> vue() {
            Indicateurs.Definition d = definition;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("code", d.code());
            m.put("libelle", d.libelle());
            m.put("source", d.source());
            m.put("format", d.format().code());
            m.put("numerateur", d.numerateur());
            m.put("denominateur", d.denominateur());
            m.put("axe", d.axe());
            m.put("filtres", filtres(d.filtres()));
            m.put("seuil", d.seuil());
            m.put("direction", d.direction().code());
            m.put("marge", d.marge());
            m.put("actif", actif);
            return m;
        }
    }

    private static List<Map<String, Object>> filtres(List<CatalogueRapports.Filtre> filtres) {
        return filtres.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("champ", f.champ());
            m.put("operateur", f.operateur().code());
            m.put("valeurs", f.valeurs());
            return m;
        }).toList();
    }

    private final JdbcClient jdbc;

    public DepotIndicateurs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * La ligne relue <b>sans</b> revalidation, comme le Node : un indicateur rangé sur une source que ce
     * serveur ne sert pas encore doit rester listable — c'est sa mesure qui le dira, nommément.
     */
    private static Indicateur indicateur(ResultSet rs, int i) throws SQLException {
        Object seuil = rs.getObject("seuil");
        Indicateurs.Definition d = new Indicateurs.Definition(rs.getString("code"), rs.getString("libelle"), rs.getString("source"),
                Indicateurs.Format.de(rs.getString("format")), rs.getString("numerateur"), rs.getString("denominateur"),
                filtresRanges(rs.getString("filtresJson")), rs.getString("axe"), seuil instanceof Number n ? n.doubleValue() : null,
                Indicateurs.Direction.de(rs.getString("direction")), rs.getDouble("marge"));
        return new Indicateur(rs.getString("id"), d, rs.getInt("actif") == 1);
    }

    private static List<CatalogueRapports.Filtre> filtresRanges(String json) {
        return Json.liste(json).stream().map(o -> o instanceof Map<?, ?> m ? m : Map.of())
                .map(m -> new CatalogueRapports.Filtre(String.valueOf(m.get("champ")), CatalogueRapports.Operateur.de(String.valueOf(m.get("operateur"))),
                        m.get("valeurs") instanceof List<?> l ? List.copyOf(l) : List.of()))
                .toList();
    }

    public List<Indicateur> lister(String tenantId, boolean inclureRetires) {
        String sql = "SELECT " + CHAMPS + " FROM rap_indicateurs WHERE tenantId = ?" + (inclureRetires ? "" : " AND actif = 1") + " ORDER BY libelle";
        return jdbc.sql(sql).param(tenantId).query(DepotIndicateurs::indicateur).list();
    }

    public Indicateur lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + CHAMPS + " FROM rap_indicateurs WHERE tenantId = ? AND id = ?").params(tenantId, id)
                .query(DepotIndicateurs::indicateur).optional()
                .orElseThrow(() -> new Indicateurs.IndicateurInvalideException("indicateur introuvable"));
    }

    /** L'auteur d'une création : l'organisme, l'acteur, l'instant et l'identifiant, injectés à la frontière. */
    public record Creation(String tenantId, String acteurId, String maintenant, String id) {
    }

    public Indicateur creer(Creation c, Map<String, Object> saisie) {
        Indicateurs.Definition v = Indicateurs.valider(saisie);
        boolean existe = jdbc.sql("SELECT 1 FROM rap_indicateurs WHERE tenantId = ? AND code = ?").params(c.tenantId(), v.code())
                .query(Integer.class).optional().isPresent();
        if (existe) {
            throw new Indicateurs.IndicateurInvalideException("le code « " + v.code() + " » est déjà utilisé");
        }
        jdbc.sql("INSERT INTO rap_indicateurs (id, tenantId, code, libelle, source, format, numerateur, denominateur, axe, filtresJson, seuil, "
                        + "direction, marge, actif, creeLe, creePar) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?)")
                .params(c.id(), c.tenantId(), v.code(), v.libelle(), v.source(), v.format().code(), v.numerateur(), v.denominateur(), v.axe(),
                        Json.texte(filtres(v.filtres())), v.seuil(), v.direction().code(), v.marge(), c.maintenant(), c.acteurId())
                .update();
        return new Indicateur(c.id(), v, true);
    }

    /** Le <b>code</b> ne change pas : il nomme l'indicateur dans les tableaux de bord et les exports. */
    public Indicateur modifier(String tenantId, String id, Map<String, Object> saisie) {
        Indicateur avant = lire(tenantId, id);
        Map<String, Object> fusion = new LinkedHashMap<>(avant.vue());
        fusion.putAll(saisie);
        fusion.put("code", avant.definition().code());
        Indicateurs.Definition v = Indicateurs.valider(fusion);
        jdbc.sql("UPDATE rap_indicateurs SET libelle = ?, source = ?, format = ?, numerateur = ?, denominateur = ?, axe = ?, filtresJson = ?, "
                        + "seuil = ?, direction = ?, marge = ? WHERE tenantId = ? AND id = ?")
                .params(v.libelle(), v.source(), v.format().code(), v.numerateur(), v.denominateur(), v.axe(), Json.texte(filtres(v.filtres())),
                        v.seuil(), v.direction().code(), v.marge(), tenantId, id)
                .update();
        return new Indicateur(id, v, avant.actif());
    }

    /** Retirer désactive : l'indicateur ne se propose plus et reste lisible dans l'historique. */
    public Indicateur retirer(String tenantId, String id) {
        Indicateur avant = lire(tenantId, id);
        jdbc.sql("UPDATE rap_indicateurs SET actif = 0 WHERE tenantId = ? AND id = ?").params(tenantId, id).update();
        return new Indicateur(id, avant.definition(), false);
    }
}
