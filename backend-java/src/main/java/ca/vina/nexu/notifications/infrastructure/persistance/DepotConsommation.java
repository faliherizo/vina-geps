package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Consommation;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La consommation d'avis par organisme — lecture seule, inter-organismes.
 *
 * <p>Tout est lu du journal ({@code sms_message}, {@code courriel_message}), jamais d'un compteur
 * entretenu à côté : un compteur qui dérive ment sans bruit, et ici il mentirait sur une facture.
 */
@Repository
public class DepotConsommation {

    private final JdbcClient jdbc;
    private final JournalTextos.Reglages reglages;
    /** Le plafond de courriels par défaut, lu de l'environnement — le Node lit la même variable. */
    private final Consommation.ReglagesCourriel reglagesCourriel;

    public DepotConsommation(JdbcClient jdbc, JournalTextos.Reglages reglages, Consommation.ReglagesCourriel reglagesCourriel) {
        this.jdbc = jdbc;
        this.reglages = reglages;
        this.reglagesCourriel = reglagesCourriel;
    }

    /**
     * La consommation de tous les organismes pour un mois.
     *
     * <p>Les organismes <b>sans aucun envoi</b> figurent à zéro : une absence de ligne se lit
     * « je n'ai pas regardé », un zéro se lit « rien n'est parti ». Les envois hors organisme —
     * et ceux d'un organisme <b>retiré</b> — figurent aussi, sans plafond : sans eux, le total de
     * l'écran ne correspondrait pas à la facture du fournisseur, et l'écart resterait introuvable.
     */
    public Consommation.Vue duMois(String mois) {
        Map<String, Map<String, Object>> textos = parLocataire(jdbc.sql(
                "SELECT tenant_id,"
                + " COALESCE(SUM(CASE WHEN provider_id IS NOT NULL THEN segments END), 0) AS segments,"
                + " COUNT(CASE WHEN provider_id IS NOT NULL THEN 1 END) AS messages,"
                + " COUNT(CASE WHEN status = 'echoue' THEN 1 END) AS echecs"
                + " FROM sms_message WHERE substr(created_at, 1, 7) = ? GROUP BY tenant_id").params(mois).query().listOfRows());

        Map<String, Map<String, Object>> courriels = parLocataire(jdbc.sql(
                "SELECT tenant_id,"
                + " COUNT(CASE WHEN statut = 'envoye' THEN 1 END) AS messages,"
                + " COUNT(CASE WHEN statut = 'echoue' THEN 1 END) AS echecs"
                + " FROM courriel_message WHERE substr(created_at, 1, 7) = ? GROUP BY tenant_id").params(mois).query().listOfRows());

        Map<String, Integer> limiteTexto = limites("sms_quota");
        Map<String, Integer> limiteCourriel = limites("courriel_quota");

        List<Consommation.Organisme> lignes = new ArrayList<>();
        Set<String> connus = new LinkedHashSet<>();
        for (Map<String, Object> o : jdbc.sql("SELECT id, name FROM tenants ORDER BY name").query().listOfRows()) {
            String id = String.valueOf(o.get("id"));
            connus.add(id);
            lignes.add(ligne(id, String.valueOf(o.get("name")), textos, courriels,
                    limiteTexto.getOrDefault(id, reglages.limiteParDefaut()), limiteCourriel.getOrDefault(id, reglagesCourriel.limiteMensuelleParDefaut())));
        }

        if (textos.containsKey(Consommation.NON_ATTRIBUE) || courriels.containsKey(Consommation.NON_ATTRIBUE)) {
            lignes.add(ligne(Consommation.NON_ATTRIBUE, Consommation.NOM_NON_ATTRIBUE, textos, courriels, 0, 0).horsQuota());
        }
        Set<String> orphelins = new LinkedHashSet<>(textos.keySet());
        orphelins.addAll(courriels.keySet());
        for (String id : orphelins) {
            if (!Consommation.NON_ATTRIBUE.equals(id) && !connus.contains(id)) {
                lignes.add(ligne(id, "Organisme retiré (" + id + ")", textos, courriels, 0, 0).horsQuota());
            }
        }
        return new Consommation.Vue(mois, lignes);
    }

    /** Le détail d'un organisme, par contexte d'envoi — de quoi expliquer une ligne de facture. */
    public List<Map<String, Object>> detailParContexte(String tenantId, String mois) {
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Map<String, Object> l : jdbc.sql(
                "SELECT COALESCE(contexte, 'sans contexte') AS contexte, COUNT(*) AS messages, COALESCE(SUM(segments), 0) AS utilises"
                + " FROM sms_message WHERE tenant_id = ? AND substr(created_at, 1, 7) = ? AND provider_id IS NOT NULL GROUP BY contexte")
                .params(tenantId, mois).query().listOfRows()) {
            lignes.add(contexte(l, "texto"));
        }
        for (Map<String, Object> l : jdbc.sql(
                "SELECT COALESCE(contexte, 'sans contexte') AS contexte, COUNT(*) AS messages, COUNT(*) AS utilises"
                + " FROM courriel_message WHERE tenant_id = ? AND substr(created_at, 1, 7) = ? AND statut = 'envoye' GROUP BY contexte")
                .params(tenantId, mois).query().listOfRows()) {
            lignes.add(contexte(l, "courriel"));
        }
        lignes.sort((a, b) -> Integer.compare((int) b.get("utilises"), (int) a.get("utilises")));
        return lignes;
    }

    /** Le plafond mensuel de courriels d'un organisme. Zéro vaut illimité. */
    public int reglerQuotaCourriel(String tenantId, Object limite) {
        int n = JournalTextos.exigerLimite(limite);
        jdbc.sql("INSERT INTO courriel_quota (tenant_id, monthly_limit) VALUES (?, ?)"
                + " ON CONFLICT(tenant_id) DO UPDATE SET monthly_limit = excluded.monthly_limit").params(tenantId, n).update();
        return n;
    }

    private Map<String, Integer> limites(String table) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Map<String, Object> l : jdbc.sql("SELECT tenant_id, monthly_limit FROM " + table).query().listOfRows()) {
            m.put(String.valueOf(l.get("tenant_id")), ((Number) l.get("monthly_limit")).intValue());
        }
        return m;
    }

    private static Map<String, Map<String, Object>> parLocataire(List<Map<String, Object>> lignes) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map<String, Object> l : lignes) {
            Object id = l.get("tenant_id");
            m.put(id == null ? Consommation.NON_ATTRIBUE : String.valueOf(id), l);
        }
        return m;
    }

    private static int entier(Map<String, Object> ligne, String colonne) {
        if (ligne == null) {
            return 0;
        }
        Object v = ligne.get(colonne);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static Consommation.Organisme ligne(String id, String nom, Map<String, Map<String, Object>> textos, Map<String, Map<String, Object>> courriels,
                                                int limiteTexto, int limiteCourriel) {
        Map<String, Object> t = textos.get(id);
        Map<String, Object> c = courriels.get(id);
        return new Consommation.Organisme(id, nom,
                Consommation.Canal.de(entier(t, "segments"), entier(t, "messages"), limiteTexto, entier(t, "echecs")),
                Consommation.Canal.de(entier(c, "messages"), entier(c, "messages"), limiteCourriel, entier(c, "echecs")));
    }

    private static Map<String, Object> contexte(Map<String, Object> ligne, String canal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contexte", String.valueOf(ligne.get("contexte")));
        m.put("canal", canal);
        m.put("messages", entier(ligne, "messages"));
        m.put("utilises", entier(ligne, "utilises"));
        return m;
    }
}
