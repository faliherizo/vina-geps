package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.EcritureAnnuaires;
import ca.vina.nexu.auth.domaine.RegistreAnnuaires;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** La table {@code auth_annuaires} (migration 0172) : listes et correspondances en JSON, le secret jamais. */
@Repository
public class DepotAnnuaires implements RegistreAnnuaires, EcritureAnnuaires {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TEXTES = new TypeReference<>() { };
    private static final TypeReference<List<Annuaires.Correspondance>> REGLES = new TypeReference<>() { };
    private static final String COLONNES = "id, tenantId, nom, locataireEntra, clientId, variableSecret, domaines, revendication, correspondances, roleParDefaut, actif, fournisseur, emetteur";

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotAnnuaires(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    private static Annuaires.Annuaire ligne(ResultSet rs, int i) throws SQLException {
        return new Annuaires.Annuaire(rs.getString("id"), rs.getString("tenantId"), rs.getString("nom"), rs.getString("locataireEntra"),
                rs.getString("clientId"), rs.getString("variableSecret"), JSON.readValue(rs.getString("domaines"), TEXTES),
                Annuaires.Revendication.de(rs.getString("revendication")), JSON.readValue(rs.getString("correspondances"), REGLES),
                Optional.ofNullable(rs.getString("roleParDefaut")), rs.getInt("actif") == 1,
                Annuaires.Fournisseur.de(rs.getString("fournisseur")), rs.getString("emetteur"));
    }

    @Override
    public List<Annuaires.Annuaire> de(String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM auth_annuaires WHERE tenantId = ? ORDER BY nom").params(tenantId).query(DepotAnnuaires::ligne).list();
    }

    @Override
    public Optional<Annuaires.Annuaire> parId(String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM auth_annuaires WHERE id = ?").params(id).query(DepotAnnuaires::ligne).optional();
    }

    @Override
    public void enregistrer(Annuaires.Annuaire a, String auteur) {
        // Vérifié avant d'écrire : le pilote SQLite ne traduit pas la contrainte d'unicité en une exception reconnaissable.
        if (jdbc.sql("SELECT COUNT(*) FROM auth_annuaires WHERE tenantId = ? AND nom = ? AND id <> ?").params(a.tenantId(), a.nom(), a.id())
                .query(Integer.class).single() > 0) {
            throw new Annuaires.AnnuaireInvalideException(List.of("un annuaire de l'organisme porte déjà le nom « " + a.nom() + " »"));
        }
        String maintenant = Iso.de(horloge.instant());
        jdbc.sql("INSERT INTO auth_annuaires (" + COLONNES + ", creeLe, creePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET nom = excluded.nom, locataireEntra = excluded.locataireEntra, clientId = excluded.clientId, "
                + "variableSecret = excluded.variableSecret, domaines = excluded.domaines, revendication = excluded.revendication, "
                + "correspondances = excluded.correspondances, roleParDefaut = excluded.roleParDefaut, actif = excluded.actif, "
                + "fournisseur = excluded.fournisseur, emetteur = excluded.emetteur, modifieLe = ? "
                // Un identifiant d'un autre organisme ne se réécrit jamais, même si l'appelant l'a oublié.
                + "WHERE auth_annuaires.tenantId = excluded.tenantId")
                .params(a.id(), a.tenantId(), a.nom(), a.locataireEntra(), a.clientId(), a.variableSecret(), JSON.writeValueAsString(a.domaines()),
                        a.revendication().code(), JSON.writeValueAsString(a.correspondances()), a.roleParDefaut().orElse(null), a.actif() ? 1 : 0,
                        a.fournisseur().code(), a.emetteur(), maintenant, auteur, maintenant)
                .update();
    }

    @Override
    public boolean retirer(String tenantId, String id) {
        return jdbc.sql("DELETE FROM auth_annuaires WHERE tenantId = ? AND id = ?").params(tenantId, id).update() > 0;
    }
}
