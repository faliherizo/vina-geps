package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Instant;
import java.util.Optional;

/**
 * Le <strong>quart pointé</strong> — jumeau de {@code presencePrealable} et {@code quartAutourDe}
 * ({@code backend/src/effectifs/presence/service.ts}). Le lot R9 (temps) l'emprunte pour la présence préalable d'une
 * nature de quart.
 */
public class LectureQuartsPresence {

    /** On pointe un quart jusqu'à deux heures avant son début (ou sa présence préalable, si plus longue) et deux heures après sa fin. */
    public static final long MARGE_POINTAGE_MS = 2 * 3_600_000L;
    private static final long JOUR_MS = 86_400_000L;
    private static final int LONGUEUR_DATE = 10;
    private static final String PRESENCE_PREALABLE = "presencePrealableMinutes";

    private final LecturePointagesEffectifsPort depot;
    private final LectureReferentiels referentiels;

    public LectureQuartsPresence(LecturePointagesEffectifsPort depot, LectureReferentiels referentiels) {
        this.depot = depot;
        this.referentiels = referentiels;
    }

    /** Les minutes de présence préalable rémunérée d'une nature de quart à une date (E-GP-9) ; 0 si absente. */
    public int presencePrealable(String tenantId, String natureCode, String date) {
        Object v = referentiels.exigerValeur(tenantId, TypeReferentiel.NATURES_QUART, natureCode, date).attributs().get(PRESENCE_PREALABLE);
        return v instanceof Number n && n.doubleValue() > 0 ? n.intValue() : 0;
    }

    /** Le quart publié de la personne dont la fenêtre de pointage contient l'instant ; le plus proche de son début s'il y en a deux. */
    public Optional<PresenceEffectifs.QuartPointe> autourDe(String tenantId, String ressourceId, String instant) {
        long t = Instant.parse(instant).toEpochMilli();
        String veille = Iso.de(Instant.ofEpochMilli(t - JOUR_MS)).substring(0, LONGUEUR_DATE);
        String lendemain = Iso.de(Instant.ofEpochMilli(t + JOUR_MS)).substring(0, LONGUEUR_DATE);
        return PresenceEffectifs.quartAutourDe(depot.quartsPublies(tenantId, ressourceId, veille, lendemain).stream()
                .map(q -> PresenceEffectifs.quartPointe(q, presencePrealable(tenantId, q.natureCode(), q.date()))).toList(), instant, MARGE_POINTAGE_MS);
    }
}
