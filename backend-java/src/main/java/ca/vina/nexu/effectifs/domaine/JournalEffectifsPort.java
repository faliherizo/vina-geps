package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Set;

/** Port du journal : une écriture (dans la transaction de l'action consignée) et une lecture filtrée. */
public interface JournalEffectifsPort {

    void ecrire(Contexte ctx, JournalEffectifs.Consignation c);

    /** {@code unites} nul : toutes les entrées (administrateur) ; sinon, seulement celles de ces unités. */
    List<JournalEffectifs.Entree> lire(String tenantId, JournalEffectifs.Filtre filtre, Set<String> unites, int limite);
}
