package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.CorroborationTemps;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.List;
import java.util.Map;

/**
 * La feuille détaillée, dans la forme plate du contrat HTTP du Node : la feuille, puis ce qui s'en calcule à la lecture
 * (segments et ce qui les confirme, totaux, heures supplémentaires, écarts, anomalies), l'instantané de la soumission, les
 * décisions, la corrélation des sources tierces (E-RF-13) et le flux d'approbation de la dernière soumission (E-GP-6).
 */
public record FeuilleDetailleeTemps(String id, String ressourceId, String uniteCode, String debut, String fin, TempsEffectifs.StatutFeuille statut,
                                    int palier, int paliersRequis, String soumiseLe, String approuveeLe, String nom, String matricule,
                                    List<SegmentDetaille> segments, Map<String, Long> totaux,
                                    TempsEffectifs.HeuresSupplementaires heuresSupplementaires, List<TempsEffectifs.EcartPlanifie> ecarts,
                                    List<TempsEffectifs.Anomalie> anomalies, List<TempsEffectifs.Anomalie> instantane,
                                    List<LectureFeuillesTempsPort.DecisionFeuille> decisions, List<CorroborationTemps.CorrelationSources> correlation,
                                    DemandesFlux.Flux flux) {

    /**
     * Un segment tel que la feuille le montre : ce qui le confirme ({@code corrobore} nul quand la question ne se pose pas), et
     * s'il attend une confirmation (E-RF-13).
     */
    public record SegmentDetaille(String id, String date, String debut, String fin, String typeHeureCode, Comportements.TypeHeure comportement,
                                  TempsEffectifs.Source source, String placeId, int version, String remplaceId, String motif, String creeLe, String creePar,
                                  String sourceExterne, String confirmeLe, String confirmePar, Boolean corrobore, List<String> sourcesCorroborantes,
                                  boolean aConfirmer, String description, java.util.Map<String, Object> attributs)
            implements CorroborationTemps.SegmentProuve, TempsEffectifs.SegmentDeTemps {

        static SegmentDetaille de(TempsEffectifs.SegmentLu s, CorroborationTemps.Corroboration c) {
            return new SegmentDetaille(s.id(), s.date(), s.debut(), s.fin(), s.typeHeureCode(), s.comportement(), s.source(), s.placeId(), s.version(),
                    s.remplaceId(), s.motif(), s.creeLe(), s.creePar(), s.sourceExterne(), s.confirmeLe(), s.confirmePar(), c == null ? null : c.corrobore(),
                    c == null ? List.of() : c.sources(), s.source() == TempsEffectifs.Source.EXTERNE && s.confirmeLe() == null, s.description(),
                    s.attributs());
        }
    }

    /** Ce qui se calcule d'une feuille. */
    public record Calculs(List<TempsEffectifs.SegmentLu> lus, List<SegmentDetaille> segments, TempsEffectifs.HeuresSupplementaires heuresSupplementaires,
                          List<TempsEffectifs.EcartPlanifie> ecarts, List<TempsEffectifs.Anomalie> anomalies,
                          List<CorroborationTemps.CorrelationSources> correlation) { }

    static FeuilleDetailleeTemps de(LectureFeuillesTempsPort.FeuilleStockee l, Calculs c, List<LectureFeuillesTempsPort.DecisionFeuille> decisions,
                                    DemandesFlux.Flux flux) {
        FeuilleTemps f = l.feuille();
        return new FeuilleDetailleeTemps(f.id(), f.ressourceId(), f.uniteCode(), f.debut(), f.fin(), f.statut(), f.palier(), f.paliersRequis(), f.soumiseLe(),
                f.approuveeLe(), f.nom(), f.matricule(), c.segments(), TempsEffectifs.totauxParType(c.lus()), c.heuresSupplementaires(), c.ecarts(),
                c.anomalies(), l.instantane(), decisions, c.correlation(), flux);
    }
}
