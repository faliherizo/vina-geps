package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Port de lecture de l'<strong>organigramme de l'organisme</strong> (contexte {@code organisation}), vu des effectifs :
 * jamais ses tables ni ses entités, seulement ce que l'alignement des unités doit savoir (lot R12, décision D-3).
 */
public interface OrganigrammePourEffectifsPort {

    /** Une version d'unité de l'organigramme à une date. */
    record UniteDeLOrganigramme(String id, String code, String libelle, String parentVersionId, int profondeur, boolean enVigueur) { }

    List<UniteDeLOrganigramme> organigramme(String tenantId, String date);
}
