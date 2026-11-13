package ca.vina.nexu.soutien.application.requetes;

import ca.vina.nexu.soutien.domaine.EngagementsDeService;

import java.util.Arrays;
import java.util.List;

/** Le soutien lit la politique de chaque organisme, et le catalogue des politiques qu'il peut attribuer. */
public final class LectureEngagements {

    /** Un organisme et sa politique, tels que l'écran les montre. */
    public record AffectationVue(String tenantId, String nom, String politique, String libelle, String modifieLe, String modifiePar) {
        public static AffectationVue de(EngagementsDeService.Affectation a) {
            return new AffectationVue(a.tenantId(), a.nom(), a.politique().code(), a.politique().libelle(), a.modifieLe().orElse(null), a.modifiePar().orElse(null));
        }
    }

    public record PolitiqueVue(String code, String libelle) {
    }

    public record Engagements(List<PolitiqueVue> politiques, List<AffectationVue> organismes) {
    }

    private final EngagementsDeService.Repertoire repertoire;

    public LectureEngagements(EngagementsDeService.Repertoire repertoire) {
        this.repertoire = repertoire;
    }

    public Engagements lire() {
        List<PolitiqueVue> politiques = Arrays.stream(EngagementsDeService.Politique.values()).map(p -> new PolitiqueVue(p.code(), p.libelle())).toList();
        return new Engagements(politiques, repertoire.affectations().stream().map(AffectationVue::de).toList());
    }
}
