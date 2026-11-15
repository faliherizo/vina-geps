package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.TextosEntrants;
import ca.vina.nexu.alertes.domaine.TextosEntrantsPort;
import ca.vina.nexu.partage.telephonie.Telephonie;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

/**
 * Les textos entrants en base (changeset 0014). Le numéro d'une personne est celui de sa fiche ({@code employees.phone},
 * en format libre) : il se normalise en E.164 avant d'être comparé. Les remises sans réponse se lisent de la plus
 * récente à la plus ancienne, sur une fenêtre bornée : on répond à une alerte récente, pas à une alerte du mois dernier.
 */
public class DepotTextosEntrants implements TextosEntrantsPort {

    /** Les remises ouvertes examinées, au plus : au-delà, l'alerte est trop ancienne pour qu'un « 1 » la vise. */
    static final int FENETRE = 500;

    private final JdbcClient jdbc;

    public DepotTextosEntrants(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Candidate(String tenantId, String alerteId, String personId, String telephone) { }

    @Override
    public Optional<TextosEntrants.RemiseOuverte> derniereRemiseOuverte(String numeroE164) {
        return jdbc.sql("SELECT r.tenantId, r.alerteId, r.personId, e.phone FROM alr_remises r "
                        + "JOIN alr_alertes a ON a.tenantId = r.tenantId AND a.id = r.alerteId "
                        + "JOIN employees e ON e.id = r.compteAvis "
                        + "WHERE r.reponseId IS NULL AND e.phone IS NOT NULL ORDER BY a.envoyeeLe DESC, a.rowid DESC LIMIT ?")
                .param(FENETRE)
                .query((rs, i) -> new Candidate(rs.getString("tenantId"), rs.getString("alerteId"), rs.getString("personId"), rs.getString("phone"))).list()
                .stream().filter(c -> numeroE164.equals(Telephonie.e164(c.telephone()))).findFirst()
                .map(c -> new TextosEntrants.RemiseOuverte(c.tenantId(), c.alerteId(), c.personId()));
    }

    @Override
    public boolean dejaRecu(String messageSid) {
        return jdbc.sql("SELECT COUNT(*) FROM alr_textos_entrants WHERE messageSid = ?").param(messageSid).query(Integer.class).single() > 0;
    }

    @Override
    public void consigner(TextosEntrants.Recu r) {
        jdbc.sql("INSERT INTO alr_textos_entrants (messageSid, tenantId, alerteId, personId, issue, recuLe) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (messageSid) DO NOTHING")
                .params(r.messageSid(), r.tenantId(), r.alerteId(), r.personId(), r.issue().code(), r.recuLe()).update();
    }
}
