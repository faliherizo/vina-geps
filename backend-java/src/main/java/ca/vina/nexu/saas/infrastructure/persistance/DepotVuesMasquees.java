package ca.vina.nexu.saas.infrastructure.persistance;

import ca.vina.nexu.saas.domaine.CatalogueVues;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;

import ca.vina.nexu.partage.temps.Iso;

/**
 * <b>Les vues de menu masquées par organisme.</b>
 *
 * <p>Une <b>liste de masquage</b>, jamais d'autorisation : un organisme sans ligne voit tout ce
 * que ses modules lui ouvrent. Une liste d'autorisation aurait vidé le menu de tous les
 * organismes existants le jour du déploiement, et l'incident aurait ressemblé à une panne.
 *
 * <p>⚠️ Ce n'est pas un contrôle d'accès : les routes d'API d'une page restent ouvertes quand un
 * de ses onglets est masqué. La frontière de sécurité reste le module.
 */
@Repository
public class DepotVuesMasquees {

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotVuesMasquees(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    /** Levée quand une clé n'existe pas au catalogue — refus, jamais absorption silencieuse. */
    public static class ClesInconnues extends IllegalArgumentException {
        private final transient List<String> cles;

        public ClesInconnues(List<String> cles) {
            super("vues inconnues : " + String.join(", ", cles));
            this.cles = cles;
        }

        public List<String> cles() {
            return cles;
        }
    }

    public List<String> masquees(String tenantId) {
        return jdbc.sql("SELECT cle FROM tenant_vues_masquees WHERE tenant_id = ? ORDER BY cle")
                .params(tenantId).query(String.class).list();
    }

    /**
     * Remplace la liste d'un organisme, en un seul geste.
     *
     * <p>Remplacer plutôt qu'ajouter/retirer : l'écran envoie l'état complet qu'il affiche, et
     * deux superadmins qui règlent le même organisme ne se laissent pas une liste mi-cuite que
     * ni l'un ni l'autre n'a voulue.
     */
    public List<String> regler(String tenantId, List<String> cles) {
        List<String> demandees = cles.stream().map(String::valueOf).distinct().toList();
        List<String> inconnues = CatalogueVues.clesInconnues(demandees);
        if (!inconnues.isEmpty()) {
            throw new ClesInconnues(inconnues);
        }
        String instant = Iso.de(horloge.instant());
        jdbc.sql("DELETE FROM tenant_vues_masquees WHERE tenant_id = ?").params(tenantId).update();
        for (String cle : demandees) {
            jdbc.sql("INSERT INTO tenant_vues_masquees (tenant_id, cle, masquee_le) VALUES (?, ?, ?)")
                    .params(tenantId, cle, instant).update();
        }
        return masquees(tenantId);
    }
}
