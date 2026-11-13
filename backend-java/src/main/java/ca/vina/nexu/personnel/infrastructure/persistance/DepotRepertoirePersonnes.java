package ca.vina.nexu.personnel.infrastructure.persistance;

import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import ca.vina.nexu.personnel.domaine.RepertoirePersonnes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Le répertoire dans {@code employees}, l'établissement toujours en filtre, la saisie toujours en
 * paramètre. La table vient du schéma d'origine, qui portait la paie : ses colonnes de paie
 * obligatoires reçoivent des valeurs neutres, que ce produit ne lit jamais.
 */
@Repository
public class DepotRepertoirePersonnes implements RepertoirePersonnes, FichesPersonnes.Ecriture {

    private static final String COLONNES = "SELECT id, firstName, lastName, jobTitle, email, status FROM employees WHERE tenantId = ?";
    private static final String ORDRE = " ORDER BY lastName, firstName";

    private final JdbcClient jdbc;

    public DepotRepertoirePersonnes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Personne personne(ResultSet rs, int i) throws SQLException {
        return new Personne(rs.getString("id"), rs.getString("firstName"), rs.getString("lastName"), rs.getString("jobTitle"),
                rs.getString("email"), rs.getString("status"));
    }

    @Override
    public List<Personne> parTexte(String tenantId, String texte, int limite) {
        String motif = "%" + texte.toLowerCase() + "%";
        return jdbc.sql(COLONNES + " AND (LOWER(firstName || ' ' || lastName) LIKE ? OR LOWER(lastName || ' ' || firstName) LIKE ? OR LOWER(COALESCE(email, '')) LIKE ?)"
                        + ORDRE + " LIMIT ?")
                .params(tenantId, motif, motif, motif, limite).query(DepotRepertoirePersonnes::personne).list();
    }

    @Override
    public Optional<Personne> lire(String tenantId, String id) {
        return jdbc.sql(COLONNES + " AND id = ?").params(tenantId, id).query(DepotRepertoirePersonnes::personne).optional();
    }

    @Override
    public List<Personne> tous(String tenantId) {
        return jdbc.sql(COLONNES + ORDRE).param(tenantId).query(DepotRepertoirePersonnes::personne).list();
    }

    /** Colonnes de paie obligatoires du schéma d'origine : neutres, jamais lues ici. */
    private static final double SALAIRE_NEUTRE = 0;
    private static final String FREQUENCE_NEUTRE = "biweekly";
    private static final String PROVINCE_NEUTRE = "QC";
    private static final String ROLE_FICHE = "employee";
    private static final String CUMULS_VIDES = "{}";

    @Override
    public void creer(String tenantId, FichesPersonnes.Fiche f) {
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, email, phone, jobTitle, hireDate, annualSalary, frequency, province, role, ytd_json, tenantId) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .params(f.id(), f.prenom(), f.nom(), f.courriel(), f.telephone(), f.titre(), f.embaucheLe(),
                        SALAIRE_NEUTRE, FREQUENCE_NEUTRE, PROVINCE_NEUTRE, ROLE_FICHE, CUMULS_VIDES, tenantId).update();
    }

    @Override
    public boolean modifier(String tenantId, FichesPersonnes.Fiche f) {
        return jdbc.sql("UPDATE employees SET firstName = ?, lastName = ?, email = ?, phone = ?, jobTitle = ?, hireDate = ? WHERE tenantId = ? AND id = ?")
                .params(f.prenom(), f.nom(), f.courriel(), f.telephone(), f.titre(), f.embaucheLe(), tenantId, f.id()).update() > 0;
    }
}
