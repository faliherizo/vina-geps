package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.ResolutionHote;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * L'adaptateur de {@link HoteEtablissement} et d'{@link AdressePublique} (ADR 0033) : la
 * configuration lue une fois — et vérifiée à la construction, donc au démarrage — et le slug de
 * l'hôte résolu dans la table des établissements. Un établissement résilié n'existe plus pour l'hôte.
 */
@Repository
public class ResolveurTenantHote implements HoteEtablissement, AdressePublique {

    private static final String STATUT_RESILIE = "terminated";

    private final JdbcClient jdbc;
    private final ResolutionHote.Configuration configuration;
    private final String adresseCommune;

    public ResolveurTenantHote(JdbcClient jdbc, @Value("${nexu.tenant.resolution}") String mode, @Value("${nexu.domaine.racine}") String racine,
                               @Value("${nexu.sous-domaines.reserves}") String reserves, @Value("${nexu.url.publique}") String adresseCommune) {
        this.jdbc = jdbc;
        this.configuration = ResolutionHote.Configuration.depuis(mode, racine, reserves);
        this.adresseCommune = adresseCommune == null ? "" : adresseCommune.trim().replaceAll("/+$", "");
    }

    @Override
    public ResolutionHote.Configuration configuration() {
        return configuration;
    }

    @Override
    public Resolution de(HttpServletRequest requete) {
        return switch (ResolutionHote.genreDe(ResolutionHote.hoteDe(requete), configuration)) {
            case ResolutionHote.Genre.Selecteur s -> new Resolution.Selecteur();
            case ResolutionHote.Genre.Racine r -> new Resolution.Global();
            case ResolutionHote.Genre.Reserve r -> new Resolution.Global();
            case ResolutionHote.Genre.HorsDomaine h -> new Resolution.HorsDomaine(h.hote());
            case ResolutionHote.Genre.Etablissement e -> parSlug(e.slug()).<Resolution>map(t -> t).orElseGet(() -> new Resolution.Inconnu(e.slug()));
        };
    }

    private Optional<Resolution.Etablissement> parSlug(String slug) {
        return jdbc.sql("SELECT id, name, slug FROM tenants WHERE slug = ? AND status <> ?").params(slug, STATUT_RESILIE)
                .query((rs, i) -> new Resolution.Etablissement(rs.getString("id"), rs.getString("name"), rs.getString("slug"))).optional();
    }

    @Override
    public String pour(String tenantId) {
        if (!configuration.parSousDomaine()) {
            return adresseCommune;
        }
        String slug = tenantId == null ? null : jdbc.sql("SELECT slug FROM tenants WHERE id = ?").params(tenantId).query(String.class).optional().orElse(null);
        return configuration.urlPublique(slug, adresseCommune);
    }
}
