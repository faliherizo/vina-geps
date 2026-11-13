package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.journal.Masquage;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** La boîte d'envoi de développement : journalise et garde ce qui est parti — lisible par l'essai. */
@Component
public class CourrierJournal implements Courrier {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrierJournal.class);
    private final List<Message> envoyes = new ArrayList<>();
    private final List<Map<String, Object>> boite = new ArrayList<>();
    private final Clock horloge;

    @Autowired
    public CourrierJournal() {
        this(Clock.systemUTC());
    }

    public CourrierJournal(Clock horloge) {
        this.horloge = horloge;
    }

    @Override
    public boolean envoyer(Message message) {
        envoyes.add(message);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", EXPEDITEUR_PAR_DEFAUT);
        m.put("to", message.to());
        m.put("subject", message.subject());
        m.put("text", message.text());
        m.put("at", Iso.de(horloge.instant()));
        boite.add(m);
        // Sans transport, le courriel reste au journal d'envoi : on le dit sans son texte ni son sujet, le destinataire masqué (ADR 0041).
        JOURNAL.atDebug().addKeyValue("evenement", "courrier").addKeyValue("destinataire", Masquage.masquer(message.to()))
                .log("courriel gardé en boîte d'envoi (aucun transport SMTP)");
        return true;
    }

    public List<Message> envoyes() {
        return List.copyOf(envoyes);
    }

    /** La boîte d'envoi telle que le Node la rend ({@code GET /api/auth/_outbox}) : expéditeur, destinataire, objet, texte, date. */
    public List<Map<String, Object>> boite() {
        return List.copyOf(boite);
    }
}
