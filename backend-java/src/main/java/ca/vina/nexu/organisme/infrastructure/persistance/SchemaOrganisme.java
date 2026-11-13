package ca.vina.nexu.organisme.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>options de l'organisme</strong>, identique à {@code backend/src/organisme/db.ts} :
 * charte, pays autorisés, règles d'alerte et journal des alertes. Côté Node, ces tables naissent à la première
 * utilisation, sans migration ; le Java fait de même à la construction — il peut servir une base où le Node
 * n'est jamais passé, et le Node une base où le Java les a créées.
 */
@Component("organismeSchema")
public class SchemaOrganisme {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS org_charte (tenantId TEXT PRIMARY KEY, nomAffiche TEXT NOT NULL, couleurPrincipale TEXT NOT NULL, "
                    + "couleurLaterale TEXT NOT NULL, logo TEXT, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS org_pays_autorises (tenantId TEXT PRIMARY KEY, pays TEXT NOT NULL, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS org_regles_alerte (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, evenement TEXT NOT NULL, destinataires TEXT NOT NULL, "
                    + "creeLe TEXT NOT NULL, creePar TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_org_regles ON org_regles_alerte (tenantId, evenement)",
            "CREATE TABLE IF NOT EXISTS org_alertes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, regleId TEXT NOT NULL, evenement TEXT NOT NULL, "
                    + "quoi TEXT NOT NULL, qui TEXT NOT NULL, destinataires TEXT NOT NULL, envoyees INTEGER NOT NULL, erreur TEXT, horodatage TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_org_alertes ON org_alertes (tenantId, horodatage)");

    public SchemaOrganisme(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
