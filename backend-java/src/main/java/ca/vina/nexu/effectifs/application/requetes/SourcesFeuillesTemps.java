package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.CorroborationTemps;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.time.LocalDate;
import java.util.List;

/**
 * Ce que la feuille lit de l'horaire et de la présence, sur sa période : les quarts publiés de la personne (le planifié,
 * en instants UTC) et ses pointages non rejetés — la veille et le surlendemain compris, pour un quart qui passe minuit.
 */
public class SourcesFeuillesTemps {

    /** Les pointages se lisent dès la veille du premier jour… */
    private static final int AVANT_MINUTES = -24 * 60;
    /** … et jusqu'au surlendemain minuit du dernier : un quart de nuit se pointe le lendemain. */
    private static final int APRES_MINUTES = 48 * 60;

    private final SourcesTempsEffectifsPort depot;
    private final LectureQuartsPresence quarts;

    public SourcesFeuillesTemps(SourcesTempsEffectifsPort depot, LectureQuartsPresence quarts) {
        this.depot = depot;
        this.quarts = quarts;
    }

    /** Les quarts publiés ou clos de la personne sur la période : le planifié. */
    public List<TempsEffectifs.QuartPlanifie> quartsPlanifies(String tenantId, String ressourceId, String debut, String fin) {
        return depot.quartsPlanifies(tenantId, ressourceId, debut, fin).stream().map(q -> new TempsEffectifs.QuartPlanifie(q.placeId(), q.date(), q.natureCode(),
                PresenceEffectifs.instantLocal(q.date(), q.debut()), PresenceEffectifs.instantLocal(q.date(), q.debut() + Planification.dureeMinutes(q.debut(), q.fin()))))
                .toList();
    }

    public List<SourcesTempsEffectifsPort.PointageTemps> pointages(String tenantId, String ressourceId, String debut, String fin) {
        return depot.pointages(tenantId, ressourceId, PresenceEffectifs.instantLocal(debut, AVANT_MINUTES), PresenceEffectifs.instantLocal(fin, APRES_MINUTES));
    }

    /**
     * Les horodatages tiers de la personne sur la période (E-RF-13), avec leur source, du premier jour au lendemain du dernier
     * exclu. Ils servent deux fois : à remplacer le temps déduit de l'horaire quand ils couvrent un quart non pointé, et à
     * corroborer le reste.
     */
    public List<CorroborationTemps.HorodatageExterne> externes(String tenantId, String ressourceId, String debut, String fin) {
        return depot.horodatagesExternes(tenantId, ressourceId, debut, LocalDate.parse(fin).plusDays(1).toString());
    }

    /** Les minutes de présence préalable rémunérée d'un quart (E-GP-9), empruntées à la présence (R8). */
    public int presencePrealable(String tenantId, TempsEffectifs.QuartPlanifie q) {
        return quarts.presencePrealable(tenantId, q.natureCode(), q.date());
    }
}
