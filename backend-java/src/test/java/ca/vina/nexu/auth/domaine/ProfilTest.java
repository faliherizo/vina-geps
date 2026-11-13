package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Profil — champs modifiables, dossier en lecture seule, hiérarchie, approbateurs")
class ProfilTest {

    private static final Profil.Employe E = new Profil.Employe("e1", "Ana", "Roy", "ana@x.ca", "514", "1 rue", "1990-01-01", "Analyste", "employee", "d1", "m1", "2024-01-01", "active",
            35.0, "employee", "salary", 50_000.0, null, "123 456 789", true);

    private static Compte compte(String id, String role, String employeeId, String nom) {
        return new Compte(id, nom + "@@T1", nom, "T1", "h", role, employeeId, "2026-01-01", true, false, false, null, null, null, null);
    }

    @Test
    void blocs() {
        assertThat(Profil.modifiable(E)).containsExactly(Map.entry("employeeId", "e1"), Map.entry("firstName", "Ana"), Map.entry("lastName", "Roy"), Map.entry("email", "ana@x.ca"),
                Map.entry("phone", "514"), Map.entry("address", "1 rue"), Map.entry("dateOfBirth", "1990-01-01"), Map.entry("jobTitle", "Analyste"));
        Map<String, Object> ro = Profil.lectureSeule(E, "Finance", "Marc Ng");
        assertThat(ro).containsEntry("function", "employee").containsEntry("department", "Finance").containsEntry("managerName", "Marc Ng").containsEntry("sinMasked", "***-***-789")
                .containsEntry("isTeacher", true).containsEntry("hourlyRate", null).containsEntry("annualSalary", 50_000.0);
        assertThat(Profil.masquerNas("12")).isNull();
        assertThat(Profil.masquerNas(null)).isNull();
        Profil.Employe vide = new Profil.Employe("e2", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false);
        assertThat(Profil.modifiable(vide)).containsEntry("firstName", "").containsEntry("jobTitle", "");
    }

    @Test
    void modification() {
        Map<String, Object> mauvais = new HashMap<>();
        mauvais.put("sin", "x");
        mauvais.put("firstName", " ");
        mauvais.put("lastName", "");
        mauvais.put("email", "pas-un-courriel");
        assertThat(Profil.validerModification(mauvais)).containsExactlyInAnyOrder("field 'sin' is not editable", "firstName required", "lastName required", "invalid email");
        Map<String, Object> bon = new HashMap<>();
        bon.put("firstName", " Anne ");
        bon.put("email", "");
        bon.put("dateOfBirth", "");
        bon.put("phone", null);
        assertThat(Profil.validerModification(bon)).isEmpty();
        Profil.Employe m = Profil.appliquer(E, bon);
        assertThat(m.firstName()).isEqualTo("Anne");
        assertThat(m.lastName()).isEqualTo("Roy");
        assertThat(m.email()).isEmpty();
        assertThat(m.phone()).isNull();
        assertThat(m.dateOfBirth()).isNull();
        assertThat(m.address()).isEqualTo("1 rue");
        Profil.Employe m2 = Profil.appliquer(E, Map.of("lastName", " Ng ", "address", "2 rue", "dateOfBirth", "1991-02-02", "email", "n@x.ca"));
        assertThat(m2.lastName()).isEqualTo("Ng");
        assertThat(m2.address()).isEqualTo("2 rue");
        assertThat(m2.dateOfBirth()).isEqualTo("1991-02-02");
        assertThat(Profil.validerModification(Map.of("email", "n@x.ca"))).isEmpty();
    }

    @Test
    void hierarchie() {
        List<Profil.Lien> liens = List.of(new Profil.Lien("m1", null), new Profil.Lien("e1", "m1"), new Profil.Lien("e2", "m1"), new Profil.Lien("e3", "e1"), new Profil.Lien("boucle", "boucle"));
        assertThat(Profil.subordonnes("m1", liens)).isEqualTo(3);
        assertThat(Profil.subordonnes("e1", liens)).isEqualTo(1);
        assertThat(Profil.subordonnes("e3", liens)).isZero();
        assertThat(Profil.subordonnes(null, liens)).isZero();
        assertThat(Profil.subordonnes("boucle", liens)).isEqualTo(1);
        Map<String, String> g = new HashMap<>(Map.of("m1", "", "e1", "m1", "e3", "e1", "a", "b", "b", "a", "orphelin", "inconnu"));
        assertThat(Profil.chaineHierarchique("e3", g)).containsExactly("e1", "m1");
        assertThat(Profil.chaineHierarchique("m1", g)).isEmpty();
        assertThat(Profil.chaineHierarchique("a", g)).containsExactly("b");
        assertThat(Profil.chaineHierarchique("orphelin", g)).isEmpty();
        assertThat(Profil.chaineHierarchique("absent", g)).isEmpty();
    }

    @Test
    void approbateurs() {
        Compte admin = compte("u-admin", "admin", null, "zadmin");
        Compte chef = compte("u-chef", "employee", "m1", "chef");   // encadrant resté « employee » : la fiche fait foi
        Compte dir = compte("u-dir", "director", "d1", "dir");
        Compte manager = compte("u-man", "manager", "m9", "manager");
        Compte inactif = new Compte("u-in", "in@@T1", "in", "T1", "h", "manager", "m1", "2026-01-01", false, false, false, null, null, null, null);
        Compte superadmin = compte("u-sa", "superadmin", null, "sa");
        List<Compte> perimetre = List.of(admin, chef, dir, manager, inactif, superadmin);
        // Annuaire général : gestionnaires et administrateurs, triés.
        assertThat(Profil.approbateurs(perimetre, null, List.of())).extracting(m -> m.get("username")).containsExactly("dir", "manager", "zadmin");
        // Ligne hiérarchique complète, dans l'ordre : le chef (compte « employee ») puis la direction.
        assertThat(Profil.approbateurs(perimetre, "e1", List.of("m1", "d1"))).extracting(m -> m.get("id")).containsExactly("u-chef", "u-dir");
        // Une chaîne sans compte : les administrateurs seulement.
        assertThat(Profil.approbateurs(perimetre, "e1", List.of("m7"))).extracting(m -> m.get("id")).containsExactly("u-admin");
        assertThat(Profil.approbateurs(perimetre, "e1", List.of())).extracting(m -> m.get("id")).containsExactly("u-admin");
        // Sans employé mais avec un gestionnaire préféré : il passe en tête de l'annuaire.
        assertThat(Profil.approbateurs(perimetre, "", List.of("m9"))).extracting(m -> m.get("username")).containsExactly("manager", "dir", "zadmin");
        assertThat(Profil.approbateurs(perimetre, "e1", List.of("m1", "d1")).get(0)).containsEntry("role", "employee").containsEntry("employeeId", "m1");
    }
}
