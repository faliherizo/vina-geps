package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.effectifs.domaine.DepotReferentielsPort;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * La communication voit les **unités** d'effectifs comme ses groupes de travail : les unités en
 * vigueur aujourd'hui, et pour chacune les ressources actives qui ont un compte — une ressource sans
 * compte ne peut ni lire ni recevoir un message.
 */
@Component
public class AdaptateurGroupesDeTravail implements GroupesDeTravailPort {

    private final DepotReferentielsPort referentiels;
    private final JdbcClient jdbc;
    private final Clock horloge;

    public AdaptateurGroupesDeTravail(DepotReferentielsPort referentiels, JdbcClient jdbc, Clock horloge) {
        this.referentiels = referentiels;
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    @Override
    public List<Groupe> groupes(String tenantId) {
        String aujourdhui = LocalDate.now(horloge).toString();
        return referentiels.enVigueur(tenantId, TypeReferentiel.UNITES, aujourdhui).stream()
                .map(u -> new Groupe(u.code(), u.code(), u.libelleFr())).toList();
    }

    @Override
    public List<Membre> membres(String tenantId, String groupeId) {
        return jdbc.sql("SELECT u.username, r.nom FROM eff_ressources r JOIN users u ON u.id = r.utilisateurId "
                        + "WHERE r.tenantId = ? AND r.uniteCode = ? AND r.actif = 1 AND u.username IS NOT NULL ORDER BY r.nom")
                .params(tenantId, groupeId).query((rs, i) -> new Membre(rs.getString("username"), rs.getString("nom"))).list();
    }

    /** L'unité de la personne, d'une seule lecture : une ressource active n'en a qu'une, et elle doit être en vigueur. */
    @Override
    public List<String> groupesDe(String tenantId, String personId) {
        List<String> enVigueur = groupes(tenantId).stream().map(Groupe::id).toList();
        return jdbc.sql("SELECT DISTINCT r.uniteCode FROM eff_ressources r JOIN users u ON u.id = r.utilisateurId "
                        + "WHERE r.tenantId = ? AND u.username = ? AND r.actif = 1 AND r.uniteCode IS NOT NULL")
                .params(tenantId, personId).query(String.class).list().stream().filter(enVigueur::contains).toList();
    }
}
