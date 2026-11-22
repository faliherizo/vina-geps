package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** La table {@code auth_identites_externes} (changeset 0026, ADR 0093) : une ligne par couple (émetteur, sujet) relié. */
@Repository
public class DepotIdentitesExternes implements IdentitesExternes.Registre, IdentitesExternes.Ecriture {

    private static final String COLONNES = "issuer, sujet, userId, tenantId, annuaireId, lieeLe, lieePar, preuve, derniereConnexion";

    private final JdbcClient jdbc;

    public DepotIdentitesExternes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static IdentitesExternes.Identite ligne(ResultSet rs, int i) throws SQLException {
        return new IdentitesExternes.Identite(IdentitesExternes.Cle.de(rs.getString("issuer"), rs.getString("sujet")), rs.getString("userId"),
                new IdentitesExternes.Provenance(Optional.ofNullable(rs.getString("annuaireId")), Optional.ofNullable(rs.getString("tenantId"))),
                Instant.parse(rs.getString("lieeLe")), rs.getString("lieePar"), IdentitesExternes.Preuve.de(rs.getString("preuve")),
                Optional.ofNullable(rs.getString("derniereConnexion")).map(Instant::parse));
    }

    @Override
    public Optional<IdentitesExternes.Identite> trouver(IdentitesExternes.Cle cle) {
        return jdbc.sql("SELECT " + COLONNES + " FROM auth_identites_externes WHERE issuer = ? AND sujet = ?")
                .params(cle.emetteur().valeur(), cle.sujet().valeur()).query(DepotIdentitesExternes::ligne).optional();
    }

    @Override
    public void lier(IdentitesExternes.Identite i) {
        jdbc.sql("INSERT INTO auth_identites_externes (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(i.cle().emetteur().valeur(), i.cle().sujet().valeur(), i.userId(), i.provenance().tenantId().orElse(null),
                        i.provenance().annuaireId().orElse(null), Iso.de(i.lieeLe()), i.lieePar(), i.preuve().code(),
                        i.derniereConnexion().map(Iso::de).orElse(null))
                .update();
    }

    @Override
    public void noterConnexion(IdentitesExternes.Cle cle, Instant le) {
        jdbc.sql("UPDATE auth_identites_externes SET derniereConnexion = ? WHERE issuer = ? AND sujet = ?")
                .params(Iso.de(le), cle.emetteur().valeur(), cle.sujet().valeur()).update();
    }
}
