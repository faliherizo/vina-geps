package ca.vina.nexu.saas.infrastructure.persistance;

import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import ca.vina.nexu.pays.domaine.PaysPack;
import ca.vina.nexu.saas.domaine.CatalogueModules;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.saas.domaine.Locataires;
import ca.vina.nexu.saas.domaine.Plans;
import ca.vina.nexu.partage.temps.Iso;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La table {@code tenants} — création, cycle de vie, verrou du pays, usage : port de la partie
 * stockage de {@code backend/src/saas/tenants.ts}.
 *
 * <p>Une création écrit aussi le quota du plan (dépendances résolues, comme à la mise à jour). Elle
 * ne sème plus de règles de paie : la paie n'appartient pas à ce produit, et sa table n'y est pas.
 */
@Repository
public class DepotLocataires {


    private static final String COLONNES = "id, name, type, country, region, planCode, status, trialEndsAt, subscriptionEndsAt, dataResidency, "
            + "createdAt, address, city, postalCode, phone, email, slug";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final RegistrePays pays;
    /** La région où la base est hébergée — la même variable que lit l'état de sécurité de la console. */
    private final String regionHebergement;

    public DepotLocataires(JdbcClient jdbc, Clock horloge, RegistrePays pays, @Value("${REGION_DONNEES:}") String regionHebergement) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.pays = pays;
        this.regionHebergement = regionHebergement;
    }

    /** Les colonnes dans l'ordre de la table, comme {@code SELECT *} les rend au Node. */
    private static Map<String, Object> lire(ResultSet rs, int i) throws SQLException {
        Map<String, Object> t = new LinkedHashMap<>();
        for (String c : COLONNES.split(", ")) {
            t.put(c, rs.getString(c));
        }
        return t;
    }

    public Optional<Map<String, Object>> lire(String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM tenants WHERE id = ?").params(id).query(DepotLocataires::lire).optional();
    }

    private Map<String, Object> exiger(String id) {
        return lire(id).orElseThrow(() -> new IntrouvableException("tenant not found"));
    }

    public List<Map<String, Object>> lister() {
        return jdbc.sql("SELECT " + COLONNES + " FROM tenants ORDER BY createdAt DESC").query(DepotLocataires::lire).list();
    }

    @Transactional
    public Map<String, Object> creer(Locataires.Demande demande) {
        Locataires.Creation c = Locataires.valider(demande.avecResidenceParDefaut(regionHebergement), horloge.instant(), pays::existe, pays.tous().stream().map(PaysPack::code).toList());
        if (c.slug() != null && jdbc.sql("SELECT COUNT(*) FROM tenants WHERE slug = ?").params(c.slug()).query(Integer.class).single() > 0) {
            throw new Locataires.ConflitException("le slug d'URL « " + c.slug() + " » est déjà utilisé par un autre établissement");
        }
        String id = UUID.randomUUID().toString();
        String maintenant = Iso.de(horloge.instant());
        jdbc.sql("INSERT INTO tenants (id, name, type, country, region, planCode, status, trialEndsAt, subscriptionEndsAt, dataResidency, createdAt, "
                        + "address, city, postalCode, phone, email, slug) VALUES (?, ?, ?, ?, ?, ?, 'trial', ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, c.name(), c.type(), c.country(), c.region(), c.planCode(), c.trialEndsAt(), c.dataResidency(), maintenant,
                        c.address(), c.city(), c.postalCode(), c.phone(), c.email(), c.slug())
                .update();
        Plans.Quota defauts = Plans.defauts(c.planCode());
        List<String> modules = CatalogueModules.resoudreDependances(defauts.modules(), null).modules();
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, ?, ?, ?)")
                .params(id, defauts.maxTeachers(), defauts.maxContractsPerYear(), JSON.writeValueAsString(modules)).update();
        return exiger(id);
    }

    /** Ce que le superadmin modifie ({@code updateTenant}) : {@code null} = inchangé, une valeur vide = effacée. */
    public record Modification(String name, String region, String address, String city, String postalCode, String phone, String email, String slug) {
    }

    public Map<String, Object> modifier(String id, Modification m) {
        exiger(id);
        List<String> colonnes = new java.util.ArrayList<>();
        List<Object> valeurs = new java.util.ArrayList<>();
        if (m.name() != null) {
            if (m.name().trim().isEmpty()) {
                throw new Locataires.ValidationException(List.of("le nom de l'établissement ne peut pas être vide"));
            }
            colonnes.add("name");
            valeurs.add(m.name().trim());
        }
        for (var champ : List.of(Map.entry("region", Optional.ofNullable(m.region())), Map.entry("address", Optional.ofNullable(m.address())),
                Map.entry("city", Optional.ofNullable(m.city())), Map.entry("postalCode", Optional.ofNullable(m.postalCode())), Map.entry("phone", Optional.ofNullable(m.phone())))) {
            if (champ.getValue().isPresent()) {
                colonnes.add(champ.getKey());
                valeurs.add(champ.getValue().get().trim());
            }
        }
        if (m.email() != null) {
            String courriel = m.email().trim();
            if (!courriel.isEmpty() && !Locataires.courrielValide(courriel)) {
                throw new Locataires.ValidationException(List.of("courriel invalide « " + courriel + " »"));
            }
            colonnes.add("email");
            valeurs.add(courriel);
        }
        if (m.slug() != null && !m.slug().isEmpty()) {
            String slug = Locataires.normaliserSlug(m.slug());
            Optional<String> autre = jdbc.sql("SELECT id FROM tenants WHERE slug = ?").params(slug).query(String.class).optional();
            if (autre.isPresent() && !autre.get().equals(id)) {
                throw new Locataires.ConflitException("le slug d'URL « " + slug + " » est déjà utilisé par un autre établissement");
            }
            colonnes.add("slug");
            valeurs.add(slug);
        }
        if (colonnes.isEmpty()) {
            return exiger(id);
        }
        valeurs.add(id);
        jdbc.sql("UPDATE tenants SET " + String.join(", ", colonnes.stream().map(c -> c + " = ?").toList()) + " WHERE id = ?").params(valeurs).update();
        return exiger(id);
    }

    /** L'établissement a-t-il au moins une transaction financière ? Une table absente compte pour non. */
    public boolean aDesTransactions(String id) {
        for (String table : Locataires.TABLES_FINANCIERES) {
            try {
                if (jdbc.sql("SELECT 1 FROM " + table + " WHERE tenantId = ? LIMIT 1").params(id).query(Integer.class).optional().isPresent()) {
                    return true;
                }
            } catch (DataAccessException e) {
                // table ou colonne absente selon l'environnement : ignorée, comme côté Node
            }
        }
        return false;
    }

    public Map<String, Object> changerPays(String id, String pays) {
        exiger(id);
        String c = Locataires.paysValide(pays);
        if (aDesTransactions(id)) {
            throw new IllegalArgumentException("le pays/la devise ne peut plus être modifié : des transactions financières existent déjà pour cet établissement");
        }
        jdbc.sql("UPDATE tenants SET country = ? WHERE id = ?").params(c, id).update();
        return exiger(id);
    }

    public Map<String, Object> activer(String id, int mois) {
        exiger(id);
        jdbc.sql("UPDATE tenants SET status = 'active', subscriptionEndsAt = ? WHERE id = ?").params(Locataires.finAbonnement(horloge.instant(), mois), id).update();
        return exiger(id);
    }

    public Map<String, Object> changerStatut(String id, String statut) {
        exiger(id);
        jdbc.sql("UPDATE tenants SET status = ? WHERE id = ?").params(statut, id).update();
        return exiger(id);
    }

    /** L'usage d'un mois ({@code AAAA-MM}), zéros s'il n'y a rien. */
    public Map<String, Object> usage(String id, String mois) {
        String m = mois != null ? mois : Iso.de(horloge.instant()).substring(0, 7);
        return jdbc.sql("SELECT teachersCount, contractsCount, payCalcsCount FROM tenant_usage WHERE tenantId = ? AND month = ?").params(id, m)
                .query((rs, i) -> {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("teachersCount", rs.getInt("teachersCount"));
                    u.put("contractsCount", rs.getInt("contractsCount"));
                    u.put("payCalcsCount", rs.getInt("payCalcsCount"));
                    return u;
                }).optional().orElseGet(() -> {
                    Map<String, Object> u = new LinkedHashMap<>();
                    u.put("teachersCount", 0);
                    u.put("contractsCount", 0);
                    u.put("payCalcsCount", 0);
                    return u;
                });
    }
}
