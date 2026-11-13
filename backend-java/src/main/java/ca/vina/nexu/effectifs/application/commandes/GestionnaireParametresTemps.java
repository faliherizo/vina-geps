package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.Map;

/** Les seuils de l'organisme (heures supplémentaires, paliers, tolérance) — jumeau de {@code modifierParametres}. */
public class GestionnaireParametresTemps {

    private final LectureFeuillesTemps lecture;
    private final EcritureFeuillesTempsPort depot;
    private final JournalEffectifsPort journal;

    public GestionnaireParametresTemps(LectureFeuillesTemps lecture, EcritureFeuillesTempsPort depot, JournalEffectifsPort journal) {
        this.lecture = lecture;
        this.depot = depot;
        this.journal = journal;
    }

    public TempsEffectifs.Parametres modifier(Contexte ctx, Map<String, Object> entree) {
        TempsEffectifs.Parametres avant = lecture.parametres(ctx.tenantId());
        TempsEffectifs.Parametres p = TempsEffectifs.parametresDe(entree, avant);
        depot.enregistrerParametres(ctx, p);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("temps.parametres", "parametres", ctx.tenantId(), null, avant, p));
        return p;
    }
}
