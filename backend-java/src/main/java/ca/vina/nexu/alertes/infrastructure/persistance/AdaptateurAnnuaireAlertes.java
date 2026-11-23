package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.GroupesTransversesPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/**
 * L'annuaire des alertes : les groupes sont ceux de la communication (les unités en vigueur), et une
 * personne se nomme par son <strong>nom de connexion</strong> — jamais la clé technique
 * {@code login@@organisme} de la colonne {@code username}, que personne ne porte (voir changeset 0005).
 *
 * <p>Le compte à aviser est le dossier d'employé quand il existe, sinon le compte lui-même : c'est
 * l'identifiant que le port d'avis attend.
 */
public class AdaptateurAnnuaireAlertes implements AnnuaireAlertes {

    private static final String NOM_DE_CONNEXION = "COALESCE(u.loginName, u.username)";
    private static final String PERSONNE = "SELECT " + NOM_DE_CONNEXION + " AS personId, COALESCE(MAX(r.nom), " + NOM_DE_CONNEXION + ") AS nom, "
            + "COALESCE(u.employeeId, u.id) AS compte FROM users u "
            + "LEFT JOIN eff_ressources r ON r.utilisateurId = u.id AND r.tenantId = u.tenantId AND r.actif = 1 "
            + "WHERE u.tenantId = ? AND u.active = 1 AND " + NOM_DE_CONNEXION + " = ? GROUP BY u.id";

    private final GroupesDeTravailPort groupes;
    private final LectureListesDistributionPort listes;
    private final GroupesTransversesPort.Lecture personnes;
    private final JdbcClient jdbc;

    public AdaptateurAnnuaireAlertes(GroupesDeTravailPort groupes, LectureListesDistributionPort listes, GroupesTransversesPort.Lecture personnes,
                                     JdbcClient jdbc) {
        this.groupes = groupes;
        this.listes = listes;
        this.personnes = personnes;
        this.jdbc = jdbc;
    }

    /** La recherche par nom est celle de la communication (un seul SQL, jokers neutralisés) ; le compte à aviser se résout à l'envoi. */
    @Override
    public List<Destinataire> rechercher(String tenantId, String texte, int limite) {
        return personnes.personnes(tenantId, texte, limite).stream().map(p -> new Destinataire(p.personId(), p.nom(), null)).toList();
    }

    @Override
    public List<Groupe> groupes(String tenantId) {
        return groupes.groupes(tenantId).stream().map(g -> new Groupe(g.id(), g.nom())).toList();
    }

    @Override
    public List<Destinataire> membres(String tenantId, String groupeId) {
        return groupes.membres(tenantId, groupeId).stream().map(m -> personne(tenantId, m.personId())
                .orElse(new Destinataire(m.personId(), m.nom(), null))).toList();
    }

    @Override
    public Optional<Destinataire> personne(String tenantId, String personId) {
        return jdbc.sql(PERSONNE).params(tenantId, personId)
                .query((rs, i) -> new Destinataire(rs.getString("personId"), rs.getString("nom"), rs.getString("compte"))).optional();
    }

    @Override
    public List<String> groupesDe(String tenantId, String personId) {
        return groupes.groupesDe(tenantId, personId);
    }

    /** Les listes de la messagerie que l'acteur voit : partagées, ou les siennes (BF-033). */
    @Override
    public List<Liste> listes(String tenantId, String acteurId) {
        return listes.visibles(tenantId, acteurId).stream().map(l -> new Liste(l.id(), l.nom())).toList();
    }

    /**
     * Une liste dépliée à l'envoi, comme la messagerie la déplie : ses personnes, et les membres actifs de ses groupes.
     * Une liste privée d'un autre n'existe pas pour l'acteur.
     */
    @Override
    public Optional<List<Destinataire>> membresDeListe(String tenantId, String acteurId, String listeId) {
        return listes.lire(tenantId, listeId).filter(l -> l.partagee() || acteurId.equals(l.creeePar()))
                .map(l -> ListesDistribution.destinataires(l.membres(), g -> listes.membresActifsDuGroupe(tenantId, g)).stream()
                        .map(p -> personne(tenantId, p).orElse(new Destinataire(p, p, null))).toList());
    }
}
