package ca.vina.nexu.auth.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Vide les comptes (sauf {@code admin}) et le journal, comme {@code POST /api/auth/_reset} du Node — essais seulement. */
@Repository
public class RemiseAZeroComptes {

    private final JdbcClient jdbc;

    public RemiseAZeroComptes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void vider() {
        jdbc.sql("DELETE FROM users WHERE username != 'admin'").update();
        jdbc.sql("DELETE FROM audit_log").update();
    }
}
