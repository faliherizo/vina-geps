package ca.vina.nexu.ordonnanceur.infrastructure.persistance;

import ca.vina.nexu.ordonnanceur.domaine.BilanTic;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.partage.temps.Iso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * La <strong>boucle</strong> de l'ordonnanceur — le seul endroit où l'horloge réelle entre (ADR 0014),
 * comme {@code ordonnanceur/boucle.ts} : un tic à la cadence de {@code ORDONNANCEUR_INTERVALLE_MS}
 * (60 s par défaut, 3 s en campagne e2e), le premier dès le démarrage. {@code fixedDelay} garantit
 * qu'un tic long ne se superpose pas au suivant. Un tic qui échoue entièrement est journalisé et la
 * boucle continue : un ordonnanceur qui s'arrête au premier incident se découvre le lendemain.
 */
@Component
@EnableScheduling
public class BoucleOrdonnanceur {

    private static final Logger JOURNAL = LoggerFactory.getLogger(BoucleOrdonnanceur.class);

    private final DepotTaches taches;
    private final Clock horloge;
    private final String identite = "boucle-" + ProcessHandle.current().pid();

    public BoucleOrdonnanceur(DepotTaches taches, Clock horloge) {
        this.taches = taches;
        this.horloge = horloge;
    }

    @Scheduled(fixedDelayString = "${nexu.ordonnanceur.intervalle-ms}", initialDelay = 0)
    public void tic() {
        try {
            BilanTic bilan = taches.tic(Iso.de(horloge.instant()), PolitiqueRetentative.PAR_DEFAUT, identite);
            if (bilan.prises() > 0) {
                JOURNAL.atInfo().addKeyValue("evenement", "tache").addKeyValue("prises", bilan.prises()).log("ordonnanceur : {} tâche(s) prise(s)", bilan.prises());
            }
        } catch (RuntimeException e) {
            JOURNAL.atError().addKeyValue("evenement", "tache").setCause(e).log("ordonnanceur : tic en échec");
        }
    }
}
