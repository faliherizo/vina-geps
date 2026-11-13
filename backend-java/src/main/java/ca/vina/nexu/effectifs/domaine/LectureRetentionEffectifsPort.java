package ca.vina.nexu.effectifs.domaine;

import java.util.Map;

/** Port de lecture de la rétention : les durées posées, et le décompte de ce qu'une classe purgerait. */
public interface LectureRetentionEffectifsPort {

    Map<RetentionEffectifs.Classe, Integer> durees(String tenantId);

    /** Ce que la purge de la classe supprimerait, à cette date limite (AAAA-MM-JJ, strictement antérieur). */
    long compter(String tenantId, RetentionEffectifs.Classe classe, String limite);
}
