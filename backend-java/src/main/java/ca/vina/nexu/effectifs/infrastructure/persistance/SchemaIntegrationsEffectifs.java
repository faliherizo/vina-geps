package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>intégrations des effectifs</strong> (lot R10), identique à {@code backend/src/effectifs/integrations/db.ts} :
 * la configuration de chaque connecteur par organisme (jamais un jeton : le nom de sa variable), son curseur de lecture, et la
 * <strong>boîte d'envoi</strong> — un message par livraison, déposé dans la transaction de ce qui le produit.
 */
@Component("effectifsSchemaIntegrations")
public class SchemaIntegrationsEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_connecteurs (tenantId TEXT NOT NULL, code TEXT NOT NULL, mode TEXT NOT NULL, url TEXT, jetonRef TEXT, "
                    + "actif INTEGER NOT NULL, jeuEssai TEXT NOT NULL DEFAULT '[]', curseur TEXT, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL, "
                    + "PRIMARY KEY (tenantId, code))",
            "CREATE TABLE IF NOT EXISTS eff_messages_sortants (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, connecteur TEXT NOT NULL, type TEXT NOT NULL, "
                    + "cle TEXT NOT NULL, charge TEXT NOT NULL, etat TEXT NOT NULL, tentatives INTEGER NOT NULL DEFAULT 0, refExterne TEXT, motif TEXT, "
                    + "deposeLe TEXT NOT NULL, deposePar TEXT NOT NULL, traiteLe TEXT, UNIQUE (tenantId, connecteur, cle))",
            "CREATE INDEX IF NOT EXISTS idx_eff_messages_etat ON eff_messages_sortants (tenantId, etat, deposeLe)");

    public SchemaIntegrationsEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
