package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.QuestionsSecretes;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** La table {@code user_security_questions} : les questions en clair, les réponses hachées (bcrypt 8, comme le Node). */
@Repository
public class DepotQuestionsSecretes {

    private static final BCryptPasswordEncoder ENCODEUR = new BCryptPasswordEncoder(8);

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotQuestionsSecretes(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    /** Remplace l'ensemble des questions ; lève avec les messages du domaine si le lot est invalide. */
    @Transactional
    public void definir(String userId, List<QuestionsSecretes.Paire> paires) {
        List<QuestionsSecretes.PaireNettoyee> propres = QuestionsSecretes.nettoyer(paires);
        jdbc.sql("DELETE FROM user_security_questions WHERE userId = ?").params(userId).update();
        String maintenant = Iso.de(horloge.instant());
        for (int i = 0; i < propres.size(); i++) {
            jdbc.sql("INSERT INTO user_security_questions (id, userId, question, answerHash, sortOrder, createdAt) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(UUID.randomUUID().toString(), userId, propres.get(i).question(),
                            ENCODEUR.encode(propres.get(i).reponseNormalisee()), i, maintenant)
                    .update();
        }
    }

    public List<String> questions(String userId) {
        return jdbc.sql("SELECT question FROM user_security_questions WHERE userId = ? ORDER BY sortOrder ASC")
                .params(userId).query((rs, i) -> rs.getString("question")).list();
    }

    public int compter(String userId) {
        return jdbc.sql("SELECT COUNT(*) FROM user_security_questions WHERE userId = ?").params(userId).query(Integer.class).single();
    }

    public boolean enACompletes(String userId) {
        return compter(userId) >= QuestionsSecretes.MINIMUM;
    }

    public void effacer(String userId) {
        jdbc.sql("DELETE FROM user_security_questions WHERE userId = ?").params(userId).update();
    }

    /** Toutes les réponses, dans l'ordre des questions, doivent être justes. */
    public boolean verifier(String userId, List<String> reponses) {
        List<String> empreintes = jdbc.sql("SELECT answerHash FROM user_security_questions WHERE userId = ? ORDER BY sortOrder ASC")
                .params(userId).query((rs, i) -> rs.getString("answerHash")).list();
        if (empreintes.size() < QuestionsSecretes.MINIMUM || reponses == null || reponses.size() != empreintes.size()) {
            return false;
        }
        for (int i = 0; i < empreintes.size(); i++) {
            String r = QuestionsSecretes.normaliser(reponses.get(i));
            if (r.isEmpty() || !ENCODEUR.matches(r, empreintes.get(i))) {
                return false;
            }
        }
        return true;
    }
}
