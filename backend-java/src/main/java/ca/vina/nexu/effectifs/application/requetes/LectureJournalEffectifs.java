package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.List;
import java.util.Set;

/**
 * La lecture du journal (E-GN-10). Un administrateur lit tout ; les autres ne lisent que les entrées des
 * unités de leur périmètre — une entrée sans unité (paramétrage de l'établissement) leur reste fermée.
 */
public class LectureJournalEffectifs {

    private static final int LIMITE_DEFAUT = 200;
    private static final int LIMITE_MAX = 1000;

    private final JournalEffectifsPort journal;
    private final LecturePerimetres perimetres;

    public LectureJournalEffectifs(JournalEffectifsPort journal, LecturePerimetres perimetres) {
        this.journal = journal;
        this.perimetres = perimetres;
    }

    public List<JournalEffectifs.Entree> lire(Contexte ctx, JournalEffectifs.Filtre filtre, String limite) {
        int n = limite(limite);
        Set<String> unites = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        if (unites != null && unites.isEmpty()) {
            return List.of();
        }
        return journal.lire(ctx.tenantId(), filtre, unites, n);
    }

    private static int limite(String brut) {
        if (brut == null || brut.isEmpty()) {
            return LIMITE_DEFAUT;
        }
        try {
            int n = Integer.parseInt(brut.trim());
            if (n >= 1 && n <= LIMITE_MAX) {
                return n;
            }
        } catch (NumberFormatException e) {
            // repli sur le refus nommé
        }
        throw new EffectifsInvalideException("« limite » : entier entre 1 et " + LIMITE_MAX);
    }
}
