package ca.vina.nexu.soutien.infrastructure.persistance;

import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.soutien.domaine.AvisSoutien;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * L'adaptateur d'avis : chaque courriel composé part par le {@link Courrier} du déploiement
 * (SMTP ou journal) ; ce qui a été composé reste lisible pour l'essai, et le compte rendu ne
 * compte que ce qui est parti.
 */
@Component
public class AvisSoutienJournal implements AvisSoutien {

    private final Courrier courrier;
    private final List<Courriel> envoyes = new ArrayList<>();

    /** Le courrier configuré (SMTP dès {@code SMTP_HOST}, journal sinon) : sans l'annotation, Spring prenait le constructeur vide et les avis n'atteignaient jamais le transport réel. */
    @Autowired
    public AvisSoutienJournal(Courrier courrier) {
        this.courrier = courrier;
    }

    /** Sans passerelle : un journal, comme la boîte d'envoi de développement du Node. */
    public AvisSoutienJournal() {
        this(new CourrierJournal());
    }

    @Override
    public int aviser(List<Courriel> courriels) {
        int partis = 0;
        for (Courriel c : courriels) {
            envoyes.add(c);
            if (courrier.envoyer(new Courrier.Message(c.to(), c.subject(), c.text()))) {
                partis++;
            }
        }
        return partis;
    }

    @Override
    public Courrier.Transport transport() {
        return courrier.transport();
    }

    public List<Courriel> envoyes() {
        return List.copyOf(envoyes);
    }
}
