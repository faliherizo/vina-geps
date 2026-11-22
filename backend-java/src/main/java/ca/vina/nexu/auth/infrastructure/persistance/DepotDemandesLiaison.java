package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.DemandesLiaison;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** La table {@code auth_demandes_liaison} (changeset 0026, ADR 0093) : les demandes soumises à l'administration. */
@Repository
public class DepotDemandesLiaison implements DemandesLiaison.Registre, DemandesLiaison.Ecriture {

    private static final String COLONNES = "id, issuer, sujet, courriel, tenantId, annuaireId, compteId, statut, creeLe, decideeLe, decideePar";

    private final JdbcClient jdbc;

    public DepotDemandesLiaison(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static DemandesLiaison.Demande ligne(ResultSet rs, int i) throws SQLException {
        return new DemandesLiaison.Demande(rs.getString("id"), IdentitesExternes.Cle.de(rs.getString("issuer"), rs.getString("sujet")),
                rs.getString("courriel"), rs.getString("tenantId"), Optional.ofNullable(rs.getString("annuaireId")), rs.getString("compteId"),
                DemandesLiaison.Statut.de(rs.getString("statut")), Instant.parse(rs.getString("creeLe")),
                Optional.ofNullable(rs.getString("decideeLe")).map(Instant::parse), Optional.ofNullable(rs.getString("decideePar")));
    }

    @Override
    public Optional<DemandesLiaison.Demande> parId(String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM auth_demandes_liaison WHERE id = ?").params(id).query(DepotDemandesLiaison::ligne).optional();
    }

    @Override
    public List<DemandesLiaison.Demande> enAttente(String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM auth_demandes_liaison WHERE tenantId = ? AND statut = ? ORDER BY creeLe, id")
                .params(tenantId, DemandesLiaison.Statut.EN_ATTENTE.code()).query(DepotDemandesLiaison::ligne).list();
    }

    @Override
    public Optional<DemandesLiaison.Demande> enAttentePour(IdentitesExternes.Cle cle) {
        return jdbc.sql("SELECT " + COLONNES + " FROM auth_demandes_liaison WHERE issuer = ? AND sujet = ? AND statut = ?")
                .params(cle.emetteur().valeur(), cle.sujet().valeur(), DemandesLiaison.Statut.EN_ATTENTE.code())
                .query(DepotDemandesLiaison::ligne).optional();
    }

    @Override
    public void poser(DemandesLiaison.Demande d) {
        jdbc.sql("INSERT INTO auth_demandes_liaison (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(d.id(), d.cle().emetteur().valeur(), d.cle().sujet().valeur(), d.courriel(), d.tenantId(), d.annuaireId().orElse(null),
                        d.compteId(), d.statut().code(), Iso.de(d.creeLe()), d.decideeLe().map(Iso::de).orElse(null), d.decideePar().orElse(null))
                .update();
    }

    @Override
    public void decider(DemandesLiaison.Demande d) {
        jdbc.sql("UPDATE auth_demandes_liaison SET statut = ?, decideeLe = ?, decideePar = ? WHERE id = ?")
                .params(d.statut().code(), d.decideeLe().map(Iso::de).orElse(null), d.decideePar().orElse(null), d.id()).update();
    }
}
