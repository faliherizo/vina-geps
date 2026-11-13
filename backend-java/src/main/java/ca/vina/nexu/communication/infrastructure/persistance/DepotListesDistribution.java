package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.EcritureListesDistributionPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/** Les listes de distribution ({@code msg_listes}, {@code msg_liste_membres}) ; les membres d'un groupe viennent du port des groupes de travail. */
public final class DepotListesDistribution implements LectureListesDistributionPort, EcritureListesDistributionPort {

    private static final String LIGNES = "SELECT id, nom, partagee, creeePar, creeeLe FROM msg_listes WHERE tenantId = ?";
    private static final RowMapper<ListesDistribution.Liste> LIGNE = (rs, i) -> new ListesDistribution.Liste(rs.getString("id"), rs.getString("nom"),
            rs.getInt("partagee") == 1, rs.getString("creeePar"), rs.getString("creeeLe"), List.of());

    private final JdbcClient jdbc;
    private final Sequence sequence;
    private final GroupesDeTravailPort groupes;

    public DepotListesDistribution(JdbcClient jdbc, Sequence sequence, GroupesDeTravailPort groupes) {
        this.groupes = groupes;
        this.jdbc = jdbc;
        this.sequence = sequence;
    }

    private List<ListesDistribution.Membre> membres(String tenantId, String listeId) {
        return jdbc.sql("SELECT nature, cible FROM msg_liste_membres WHERE tenantId = ? AND listeId = ? ORDER BY nature, cible").params(tenantId, listeId)
                .query((rs, i) -> new ListesDistribution.Membre(ListesDistribution.Nature.de(rs.getString("nature")).orElseThrow(), rs.getString("cible"))).list();
    }

    @Override
    public Optional<ListesDistribution.Liste> lire(String tenantId, String listeId) {
        return jdbc.sql(LIGNES + " AND id = ?").params(tenantId, listeId).query(LIGNE).optional().map(l -> l.avecMembres(membres(tenantId, l.id())));
    }

    @Override
    public List<ListesDistribution.Liste> visibles(String tenantId, String acteurId) {
        return jdbc.sql(LIGNES + " AND (partagee = 1 OR creeePar = ?) ORDER BY nom").params(tenantId, acteurId).query(LIGNE).list()
                .stream().map(l -> l.avecMembres(membres(tenantId, l.id()))).toList();
    }

    @Override
    public boolean nomPris(String tenantId, String auteurId, String nom) {
        return jdbc.sql("SELECT id FROM msg_listes WHERE tenantId = ? AND creeePar = ? AND nom = ?").params(tenantId, auteurId, nom).query(String.class).optional().isPresent();
    }

    @Override
    public List<String> membresActifsDuGroupe(String tenantId, String groupeId) {
        return groupes.membres(tenantId, groupeId).stream().map(GroupesDeTravailPort.Membre::personId).toList();
    }

    private void ecrireMembres(String tenantId, String listeId, List<ListesDistribution.Membre> membres) {
        jdbc.sql("DELETE FROM msg_liste_membres WHERE tenantId = ? AND listeId = ?").params(tenantId, listeId).update();
        for (ListesDistribution.Membre m : membres) {
            jdbc.sql("INSERT OR IGNORE INTO msg_liste_membres (id, tenantId, listeId, nature, cible) VALUES (?,?,?,?,?)")
                    .params(sequence.uuid(), tenantId, listeId, m.nature().code(), m.cible()).update();
        }
    }

    @Override
    public void creer(String tenantId, ListesDistribution.Liste l) {
        jdbc.sql("INSERT INTO msg_listes (id, tenantId, nom, partagee, creeePar, creeeLe) VALUES (?,?,?,?,?,?)")
                .params(l.id(), tenantId, l.nom(), l.partagee() ? 1 : 0, l.creeePar(), l.creeeLe()).update();
        ecrireMembres(tenantId, l.id(), l.membres());
    }

    @Override
    public void modifier(String tenantId, ListesDistribution.Liste l, List<ListesDistribution.Membre> membres) {
        jdbc.sql("UPDATE msg_listes SET nom = ?, partagee = ? WHERE tenantId = ? AND id = ?").params(l.nom(), l.partagee() ? 1 : 0, tenantId, l.id()).update();
        if (membres != null) {
            ecrireMembres(tenantId, l.id(), membres);
        }
    }

    @Override
    public void supprimer(String tenantId, String listeId) {
        jdbc.sql("DELETE FROM msg_liste_membres WHERE tenantId = ? AND listeId = ?").params(tenantId, listeId).update();
        jdbc.sql("DELETE FROM msg_listes WHERE tenantId = ? AND id = ?").params(tenantId, listeId).update();
    }
}
