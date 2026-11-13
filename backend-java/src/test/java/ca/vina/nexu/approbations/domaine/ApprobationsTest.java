package ca.vina.nexu.approbations.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Approbations — règles, sélection, relance, délégation, autorisation et séparation des tâches")
class ApprobationsTest {

    private static Approbations.Regle regle(String id, String champ, String op, String valeur, int niveau, String role, String approbateur) {
        return new Approbations.Regle(id, "po", champ, op, valeur, niveau, role, approbateur);
    }

    @Test
    void uneRegleSeValideAvecTousSesMotifs() {
        assertThatThrownBy(() -> Approbations.validerRegle(" ", null, "gt", 1, 0, null, " "))
                .hasMessage("documentType required; field required; invalid operator (gte|lte|eq|neq); level must be a positive integer; approverRole or approverId required");
        Approbations.Regle r = Approbations.validerRegle(" po ", " amount ", "gte", 1000, 1, " manager ", null);
        assertThat(r).isEqualTo(new Approbations.Regle(null, "po", "amount", "gte", "1000", 1, "manager", null));
        assertThat(Approbations.validerRegle("po", "amount", "eq", "x", 2, "", "u-1").approverId()).isEqualTo("u-1");
    }

    @Test
    void laVueDUneRegleOmetLesClesAbsentesCommeLeNode() {
        Approbations.Regle parRole = new Approbations.Regle("r1", "po", "amount", "gte", "1000", 1, "manager", null);
        Approbations.Regle parPersonne = new Approbations.Regle("r2", "po", "amount", "gte", "5000", 2, null, "usr-dir");
        org.assertj.core.api.Assertions.assertThat(parRole.vue()).containsEntry("approverRole", "manager").doesNotContainKey("approverId").containsEntry("level", 1);
        org.assertj.core.api.Assertions.assertThat(parPersonne.vue()).containsEntry("approverId", "usr-dir").doesNotContainKey("approverRole");
    }

    @Test
    void lesReglesCorrespondentSelonLOperateurEtLeContexte() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("amount", 1500);
        ctx.put("dept", "IT");
        ctx.put("libre", "abc");
        assertThat(Approbations.correspond(regle("r", "amount", "gte", "1000", 1, "m", null), ctx)).isTrue();
        assertThat(Approbations.correspond(regle("r", "amount", "lte", "1000", 1, "m", null), ctx)).isFalse();
        assertThat(Approbations.correspond(regle("r", "dept", "eq", "IT", 1, "m", null), ctx)).isTrue();
        assertThat(Approbations.correspond(regle("r", "dept", "neq", "IT", 1, "m", null), ctx)).isFalse();
        assertThat(Approbations.correspond(regle("r", "absent", "eq", "x", 1, "m", null), ctx)).isFalse();
        assertThat(Approbations.correspond(regle("r", "amount", "in", "1", 1, "m", null), ctx)).isFalse();
        assertThat(Approbations.correspond(regle("r", "libre", "gte", "1", 1, "m", null), ctx)).isFalse();   // NaN ≥ 1 est faux, comme en JavaScript
        assertThat(Approbations.correspond(regle("r", "amount", "gte", "1 500", 1, "m", null), ctx)).isFalse();
        assertThat(Approbations.nombre(" 12.5 ")).isEqualTo(12.5);
    }

    @Test
    void laSelectionRetientLaPremiereRegleParNiveauTrieeParNiveau() {
        List<Approbations.Regle> regles = List.of(regle("r2", "amount", "gte", "5000", 2, "director", null), regle("r1", "amount", "gte", "1000", 1, "manager", null),
                regle("r1b", "amount", "gte", "100", 1, null, "u-9"), regle("r3", "amount", "gte", "99999", 3, "ceo", null));
        List<Approbations.Etape> etapes = Approbations.selectionner(regles, Map.of("amount", 6000));
        assertThat(etapes).containsExactly(new Approbations.Etape(1, "manager", null, "r1"), new Approbations.Etape(2, "director", null, "r2"));
        assertThat(Approbations.selectionner(regles, Map.of("amount", 10))).isEmpty();
    }

    @Test
    void laRelanceSuitLaTolerance() {
        assertThat(Approbations.doitRelancer(1000, 1050, 0.1)).isFalse();
        assertThat(Approbations.doitRelancer(1000, 1200, 0.1)).isTrue();
        assertThat(Approbations.doitRelancer(0, 1, 0.1)).isTrue();
        assertThat(Approbations.doitRelancer(0, 0, 0.1)).isFalse();
        assertThat(Approbations.doitRelancer(-1000, -1150, 0.1)).isTrue();
    }

    @Test
    void laDelegationSeValideEtSeDate() {
        assertThatThrownBy(() -> Approbations.validerDelegation("", "b", null, null)).hasMessage("fromUserId and toUserId required");
        assertThatThrownBy(() -> Approbations.validerDelegation("a", null, null, null)).hasMessage("fromUserId and toUserId required");
        assertThatThrownBy(() -> Approbations.validerDelegation("a", "a", null, null)).hasMessage("cannot delegate to oneself");
        assertThatThrownBy(() -> Approbations.validerDelegation("a", "b", "hier", null)).hasMessage("fromDate must be YYYY-MM-DD");
        assertThatThrownBy(() -> Approbations.validerDelegation("a", "b", null, "demain")).hasMessage("toDate must be YYYY-MM-DD");
        assertThatThrownBy(() -> Approbations.validerDelegation("a", "b", "2026-09-10", "2026-09-01")).hasMessage("toDate must be ≥ fromDate");
        Approbations.Delegation d = Approbations.validerDelegation("a", "b", "2026-09-01", "2026-09-30");
        assertThat(Approbations.delegationActive(d, "2026-09-15")).isTrue();
        assertThat(Approbations.delegationActive(d, "2026-10-01")).isFalse();
        assertThat(Approbations.delegationActive(d, "2026-08-31")).isFalse();
        assertThat(Approbations.delegationActive(Approbations.validerDelegation("a", "b", null, null), "2030-01-01")).isTrue();
        assertThat(Approbations.texteDelegation("2026-09-01", null)).isEqualTo("Une délégation de validation vous a été confiée pour la période 2026-09-01 → …. Vous pouvez approuver les demandes au nom du délégant.");
        assertThat(Approbations.texteDelegation(null, null)).isEqualTo("Une délégation de validation vous a été confiée. Vous pouvez approuver les demandes au nom du délégant.");
    }

    @Test
    void decideQuiPeutEtLaSeparationDesTaches() {
        var nommee = new Approbations.EtapeADecider("s1", null, "u-1", "pending");
        var parRole = new Approbations.EtapeADecider("s2", "manager", null, "pending");
        assertThatThrownBy(() -> Approbations.autoriser(new Approbations.EtapeADecider("s0", null, "u-1", "approved"), "u-1", "employee", null, null, List.of()))
                .hasMessage("step already decided");
        assertThatThrownBy(() -> Approbations.autoriser(nommee, "u-2", "employee", null, null, List.of())).hasMessage("not authorized to decide this step");
        assertThat(Approbations.autoriser(nommee, "u-1", "employee", null, null, List.of()).onBehalfOf()).isNull();
        assertThat(Approbations.autoriser(nommee, "u-3", "employee", "u-3", null, List.of()).onBehalfOf()).isEqualTo("u-1");
        assertThat(Approbations.autoriser(parRole, "u-5", "manager", null, null, List.of()).onBehalfOf()).isNull();
        assertThat(Approbations.autoriser(parRole, "adm", "admin", null, null, List.of()).onBehalfOf()).isNull();
        assertThatThrownBy(() -> Approbations.autoriser(nommee, "u-1", "admin", null, "u-1", List.of()))
                .hasMessage("segregation of duties: the requester cannot approve their own request");
        assertThatThrownBy(() -> Approbations.autoriser(nommee, "u-1", "employee", null, "u-9", List.of(new Approbations.EtapeDecidee("s9", "approved", "u-1"))))
                .hasMessage("segregation of duties: an approver cannot decide more than one level of the same request");
        assertThat(Approbations.autoriser(nommee, "u-1", "employee", null, "u-9", List.of(new Approbations.EtapeDecidee("s1", "pending", null), new Approbations.EtapeDecidee("s9", "approved", "u-2"))).onBehalfOf()).isNull();
        assertThat(Approbations.statutApres("rejected", "pending", List.of("approved", "rejected"))).isEqualTo("rejected");
        assertThat(Approbations.statutApres("approved", "pending", List.of("approved", "approved"))).isEqualTo("approved");
        assertThat(Approbations.statutApres("approved", "pending", List.of("approved", "pending"))).isEqualTo("pending");
    }

    @Test
    void uneVoieDAutorisationDuModuleAppelantSAjouteSansLeverLaSeparationDesTaches() {
        var portee = new Approbations.EtapeADecider("s1", "perimetre:GESTION", null, "pending", true);
        assertThat(Approbations.autoriser(portee, "u-7", "manager", null, "u-9", List.of()).onBehalfOf()).isNull();
        assertThatThrownBy(() -> Approbations.autoriser(portee, "u-9", "manager", null, "u-9", List.of()))
                .isInstanceOfSatisfying(Approbations.NonAutoriseException.class, e -> assertThat(e.refus()).isEqualTo(Approbations.Refus.DEMANDEUR));
        assertThatThrownBy(() -> Approbations.autoriser(new Approbations.EtapeADecider("s1", "perimetre:GESTION", null, "pending"), "u-7", "manager", null, null, List.of()))
                .isInstanceOfSatisfying(Approbations.NonAutoriseException.class, e -> assertThat(e.refus()).isEqualTo(Approbations.Refus.NON_AUTORISE));
        assertThat(Approbations.Decideur.de("u-1", "manager").porte().test(new Approbations.Designation("perimetre:GESTION", null))).isFalse();
    }
}
