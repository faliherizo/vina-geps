package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureRetentionEffectifs;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureRetentionEffectifsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.RetentionEffectifs;
import ca.vina.nexu.effectifs.domaine.RetentionEffectifs.Classe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les gestes de la rétention (lot R11) : poser la politique, et purger ce qu'elle désigne — dans la transaction
 * de l'appel, journalisé avec ses décomptes et jamais avec le contenu purgé. La purge est un mécanisme : son
 * entrée de journal est attribuée au système, pas à l'administrateur qui l'a lancée.
 */
public class GestionnaireRetentionEffectifs {

    public static final String MECANISME = "retention-effectifs";
    private static final String OBJET = "retention";

    private final LectureRetentionEffectifs lecture;
    private final EcritureRetentionEffectifsPort depot;
    private final JournalEffectifsPort journal;

    public GestionnaireRetentionEffectifs(LectureRetentionEffectifs lecture, EcritureRetentionEffectifsPort depot, JournalEffectifsPort journal) {
        this.lecture = lecture;
        this.depot = depot;
        this.journal = journal;
    }

    public List<RetentionEffectifs.Ligne> definir(Contexte ctx, Object brut) {
        Map<Classe, Integer> politique = RetentionEffectifs.politiqueDe(brut);
        Map<String, Object> apres = new LinkedHashMap<>();
        politique.forEach((classe, annees) -> {
            if (annees == null) {
                depot.retirer(ctx.tenantId(), classe);
            } else {
                depot.poser(ctx, classe, annees);
            }
            apres.put(classe.name(), annees);
        });
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("retention.politique", OBJET, ctx.tenantId(), null, null, apres));
        return lecture.apercu(ctx);
    }

    public Map<String, Object> appliquer(Contexte ctx) {
        Map<String, Object> purges = new LinkedHashMap<>();
        Map<String, Object> limites = new LinkedHashMap<>();
        for (RetentionEffectifs.Ligne l : lecture.apercu(ctx)) {
            if (l.limite() != null) {
                purges.put(l.classe().name(), depot.purger(ctx.tenantId(), l.classe(), l.limite()));
                limites.put(l.classe().name(), l.limite());
            }
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("purges", purges);
        trace.put("limites", limites);
        journal.ecrire(ctx, new JournalEffectifs.Consignation("retention.appliquee", OBJET, ctx.tenantId(), null, null, trace, MECANISME));
        return Map.of("purges", purges);
    }
}
