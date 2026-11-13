package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** <strong>Liaison unités ↔ organigramme</strong> (lot R12, jumeau de {@code effectifs-liaison-organisation.test.ts}) : le plan, pur. */
@DisplayName("Alignement des unités sur l'organigramme — domaine pur")
class AlignementOrganigrammeDomaineTest {

    private static OrganigrammePourEffectifsPort.UniteDeLOrganigramme v(String id, String code, String libelle, String parent, int profondeur, boolean enVigueur) {
        return new OrganigrammePourEffectifsPort.UniteDeLOrganigramme(id, code, libelle, parent, profondeur, enVigueur);
    }

    private static ValeurReferentiel u(String code, String libelle, Map<String, Object> attributs) {
        return new ValeurReferentiel("id-" + code, "unites", code, libelle, null, null, 0, attributs, "2026-01-01", null);
    }

    @Test
    @DisplayName("crée, modifie, garde l'identique, ignore avec motif, signale l'hors-organigramme")
    void plan() {
        List<OrganigrammePourEffectifsPort.UniteDeLOrganigramme> org = List.of(v("1", "US", "Urgences-santé", null, 0, true),
                v("2", "EST", "Opérations Est", "1", 1, true), v("3", "OUEST", "Opérations Ouest", "1", 1, true), v("4", "Bloc 7", "Code invalide", "1", 1, true),
                v("5", "SUD", "Sud", "4", 2, true), v("6", "FERME", "Fermée", "1", 1, false));
        AlignementOrganigramme.Plan p = AlignementOrganigramme.planAlignement(org, List.of(u("US", "Urgences-santé", Map.of()),
                u("EST", "Est", Map.of("parent", "US")), u("VIEUX", "Ancienne unité", Map.of())));
        assertThat(p.creer()).containsExactly(new AlignementOrganigramme.UniteCible("OUEST", "Opérations Ouest", "US"));
        assertThat(p.modifier()).containsExactly(new AlignementOrganigramme.Modification("EST", "Opérations Est", "US",
                new AlignementOrganigramme.Avant("Est", "US")));
        assertThat(p.identiques()).containsExactly("US");
        assertThat(p.ignorees()).containsExactly(
                new AlignementOrganigramme.Ignoree("Bloc 7", "code hors du format des référentiels (lettres, chiffres, tirets, soulignés ; 40 au plus)"),
                new AlignementOrganigramme.Ignoree("SUD", "son unité parente « Bloc 7 » n'est pas reprise"));
        assertThat(p.horsOrganigramme()).containsExactly("VIEUX");
    }

    @Test
    @DisplayName("un parent changé est une modification ; un parent inconnu de la carte vaut racine ; un parent vide aussi")
    void parents() {
        AlignementOrganigramme.Plan p = AlignementOrganigramme.planAlignement(List.of(v("2", "EST", "Est", "absent", 1, true)),
                List.of(u("EST", "Est", Map.of("parent", "US"))));
        assertThat(p.modifier()).containsExactly(new AlignementOrganigramme.Modification("EST", "Est", null, new AlignementOrganigramme.Avant("Est", "US")));
        assertThat(AlignementOrganigramme.planAlignement(List.of(v("2", "EST", "Est", null, 0, true)), List.of(u("EST", "Est", Map.of("parent", ""))))
                .identiques()).containsExactly("EST");
        assertThat(AlignementOrganigramme.planAlignement(List.of(v("1", "US", "US", null, 0, true), v("2", "EST", "Est", "1", 1, true)),
                List.of(u("EST", "Est", Map.of("parent", "US")))).creer()).extracting(AlignementOrganigramme.UniteCible::code).containsExactly("US");
        AlignementOrganigramme.Plan horsVigueur = AlignementOrganigramme.planAlignement(List.of(v("1", "US", "US", null, 0, false),
                v("2", "EST", "Est", "1", 1, true), v("9", "Bloc 9", "x", null, 0, true)), List.of(u("US", "US", Map.of()), u("Bloc 9", "x", Map.of())));
        assertThat(horsVigueur.creer()).containsExactly(new AlignementOrganigramme.UniteCible("EST", "Est", "US"));
        assertThat(horsVigueur.horsOrganigramme()).containsExactly("US");
        assertThat(AlignementOrganigramme.planAlignement(List.of(), List.of()).creer()).isEmpty();
    }
}
