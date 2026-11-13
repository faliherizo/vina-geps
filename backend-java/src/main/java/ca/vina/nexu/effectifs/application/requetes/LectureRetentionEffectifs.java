package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.LectureRetentionEffectifsPort;
import ca.vina.nexu.effectifs.domaine.RetentionEffectifs;
import ca.vina.nexu.effectifs.domaine.RetentionEffectifs.Classe;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** L'aperçu de la rétention (lot R11) : la politique et, par classe, ce qu'elle purgerait aujourd'hui. */
public class LectureRetentionEffectifs {

    private final LectureRetentionEffectifsPort depot;

    public LectureRetentionEffectifs(LectureRetentionEffectifsPort depot) {
        this.depot = depot;
    }

    public List<RetentionEffectifs.Ligne> apercu(Contexte ctx) {
        Map<Classe, Integer> durees = depot.durees(ctx.tenantId());
        return Arrays.stream(Classe.values()).map(c -> ligne(ctx, c, durees.get(c))).toList();
    }

    private RetentionEffectifs.Ligne ligne(Contexte ctx, Classe classe, Integer annees) {
        if (annees == null) {
            return new RetentionEffectifs.Ligne(classe, classe.libelle(), null, null, 0);
        }
        String limite = RetentionEffectifs.limiteDe(ctx.aujourdhui(), annees);
        return new RetentionEffectifs.Ligne(classe, classe.libelle(), annees, limite, depot.compter(ctx.tenantId(), classe, limite));
    }
}
