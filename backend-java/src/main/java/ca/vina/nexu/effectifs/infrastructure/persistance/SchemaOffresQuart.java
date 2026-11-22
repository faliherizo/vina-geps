package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>offres de quart par ancienneté et à tour de rôle</strong> (changeset 0024, AD-07, ADR 0089) :
 * le réglage de l'organisme, l'offre d'une place et l'ordre de ses rangs, avec ce que chacun en a fait.
 */
@Component("effectifsSchemaOffresQuart")
public class SchemaOffresQuart {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_offres_parametres (tenantId TEXT PRIMARY KEY, mode TEXT NOT NULL, delaiReponseMinutes INTEGER NOT NULL, "
                    + "seuilUrgenceMinutes INTEGER NOT NULL, fenetreRepartitionJours INTEGER NOT NULL, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS eff_offres (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, comblementId TEXT NOT NULL, absenceId TEXT NOT NULL, "
                    + "placeId TEXT NOT NULL, urgente INTEGER NOT NULL DEFAULT 0, etat TEXT NOT NULL, creeLe TEXT NOT NULL, fermeeLe TEXT, attribueeA TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_offres_absence ON eff_offres (tenantId, absenceId)",
            "CREATE INDEX IF NOT EXISTS idx_eff_offres_etat ON eff_offres (tenantId, etat)",
            "CREATE TABLE IF NOT EXISTS eff_offre_rangs (tenantId TEXT NOT NULL, offreId TEXT NOT NULL, rang INTEGER NOT NULL, ressourceId TEXT NOT NULL, "
                    + "anciennete REAL NOT NULL, heuresComptees REAL NOT NULL, heuresOffertes REAL NOT NULL, etat TEXT NOT NULL, offertLe TEXT, echeance TEXT, "
                    + "reponduLe TEXT, motif TEXT, PRIMARY KEY (offreId, ressourceId))",
            "CREATE INDEX IF NOT EXISTS idx_eff_offre_rangs_ressource ON eff_offre_rangs (tenantId, ressourceId, etat)");

    public SchemaOffresQuart(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
