package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.OrganigrammePourEffectifsPort;
import ca.vina.nexu.organisation.application.requetes.LectureOrganisation;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * L'organigramme lu par la <strong>requête publique</strong> du contexte {@code organisation}, traduit en ce que les
 * effectifs en savent : ni ses tables, ni ses entités ne franchissent la frontière.
 */
@Repository("effectifsAdaptateurOrganigramme")
public class AdaptateurOrganigrammePourEffectifs implements OrganigrammePourEffectifsPort {

    private final LectureOrganisation organisation;

    public AdaptateurOrganigrammePourEffectifs(LectureOrganisation organisation) {
        this.organisation = organisation;
    }

    @Override
    public List<UniteDeLOrganigramme> organigramme(String tenantId, String date) {
        return organisation.organigramme(tenantId, date).stream()
                .map(v -> new UniteDeLOrganigramme(v.id(), v.code(), v.libelle(), v.parentVersionId(), v.profondeur(), v.enVigueur())).toList();
    }
}
