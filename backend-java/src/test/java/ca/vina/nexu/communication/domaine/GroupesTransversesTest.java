package ca.vina.nexu.communication.domaine;

import ca.vina.nexu.communication.application.requetes.GroupesDeCommunication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** <strong>Groupes transverses</strong> (ST-G09) : validation, appartenance datée, et leur réunion aux unités. */
@DisplayName("GroupesTransverses et GroupesDeCommunication — garde, comité, spécialité, aux côtés des unités")
class GroupesTransversesTest {

    @Test
    @DisplayName("un groupe a un nom borné et une nature fermée ; une appartenance a des dates AAAA-MM-JJ, la fin jamais avant le début")
    void validation() {
        GroupesTransverses.Groupe g = GroupesTransverses.nouveau("1", "  Garde de nuit  ", "garde");
        assertThat(g).isEqualTo(new GroupesTransverses.Groupe("gt-1", "Garde de nuit", GroupesTransverses.Nature.GARDE, true));
        assertThat(g.avec("Garde de fin de semaine", false)).isEqualTo(new GroupesTransverses.Groupe("gt-1", "Garde de fin de semaine", GroupesTransverses.Nature.GARDE, false));
        assertThatThrownBy(() -> GroupesTransverses.nouveau("2", " ", "garde")).hasMessage("« nom » : 1 à 80 caractères");
        assertThatThrownBy(() -> GroupesTransverses.nouveau("2", "x".repeat(81), "garde")).hasMessage("« nom » : 1 à 80 caractères");
        assertThatThrownBy(() -> GroupesTransverses.nouveau("2", "X", "club")).hasMessage("« nature » : specialite, garde ou comite");
        assertThatThrownBy(() -> GroupesTransverses.nouveau("2", "X", null)).isInstanceOf(RefusCommunication.class);
        assertThat(GroupesTransverses.appartenance("lea", "Léa", " 2031-03-01 ", "", true))
                .isEqualTo(new GroupesTransverses.Appartenance("lea", "Léa", "2031-03-01", null, true));
        assertThatThrownBy(() -> GroupesTransverses.appartenance("lea", "Léa", "2031-03-10", "2031-03-01", false)).hasMessage("« jusqua » : la fin ne précède pas le début");
        assertThatThrownBy(() -> GroupesTransverses.appartenance("lea", "Léa", "01/03/2031", null, false)).hasMessage("« depuis » : une date AAAA-MM-JJ");
        GroupesTransverses.Appartenance a = GroupesTransverses.appartenance("lea", "Léa", "2031-03-01", "2031-03-07", false);
        assertThat(List.of(a.active("2031-02-28"), a.active("2031-03-01"), a.active("2031-03-07"), a.active("2031-03-08"))).containsExactly(false, true, true, false);
        assertThat(GroupesTransverses.appartenance("x", "X", null, null, false).active("1999-01-01")).isTrue();
        assertThat(List.of(GroupesTransverses.estTransverse("gt-1"), GroupesTransverses.estTransverse("EST"), GroupesTransverses.estTransverse(null)))
                .containsExactly(true, false, false);
        assertThat(GroupesTransverses.Nature.de("comite")).contains(GroupesTransverses.Nature.COMITE);
        assertThat(GroupesTransverses.Nature.SPECIALITE.code()).isEqualTo("specialite");
    }

    @Test
    @DisplayName("la communication voit les unités et les groupes transverses actifs ; un membre l'est entre ses dates, au jour de Montréal")
    void reunionAuxUnites() {
        GroupesDeTravailPort unites = new GroupesDeTravailPort() {
            @Override
            public List<Groupe> groupes(String tenantId) {
                return List.of(new Groupe("EST", "EST", "Opérations Est"));
            }

            @Override
            public List<Membre> membres(String tenantId, String groupeId) {
                return "EST".equals(groupeId) ? List.of(new Membre("julie", "Julie")) : List.of();
            }

            @Override
            public List<String> groupesDe(String tenantId, String personId) {
                return "julie".equals(personId) ? List.of("EST") : List.of();
            }
        };
        Map<String, GroupesTransverses.Groupe> groupes = Map.of("gt-g", new GroupesTransverses.Groupe("gt-g", "Garde", GroupesTransverses.Nature.GARDE, true),
                "gt-x", new GroupesTransverses.Groupe("gt-x", "Ancien comité", GroupesTransverses.Nature.COMITE, false));
        GroupesTransversesPort.Lecture lecture = new GroupesTransversesPort.Lecture() {
            @Override
            public List<GroupesTransverses.Groupe> groupes(String tenantId) {
                return List.of(groupes.get("gt-g"), groupes.get("gt-x"));
            }

            @Override
            public Optional<GroupesTransverses.Groupe> groupe(String tenantId, String id) {
                return Optional.ofNullable(groupes.get(id));
            }

            @Override
            public List<GroupesTransverses.Appartenance> appartenances(String tenantId, String groupeId) {
                return List.of(new GroupesTransverses.Appartenance("julie", "Julie", "2031-03-01", "2031-03-07", false),
                        new GroupesTransverses.Appartenance("marc", "Marc", "2031-04-01", null, true));
            }

            @Override
            public Optional<String> nomDe(String tenantId, String personId) {
                return Optional.empty();
            }

            @Override
            public List<GroupesTransversesPort.Personne> personnes(String tenantId, String texte, int limite) {
                return List.of();
            }
        };
        // 2031-03-08 à 03 h UTC : encore le 7 mars à Montréal — Julie est de garde.
        GroupesDeCommunication c = new GroupesDeCommunication(unites, lecture, Clock.fixed(Instant.parse("2031-03-08T03:00:00Z"), ZoneOffset.UTC));
        assertThat(c.groupes("T").stream().map(GroupesDeTravailPort.Groupe::nom)).containsExactly("Opérations Est", "Garde");
        assertThat(c.membres("T", "gt-g")).extracting(GroupesDeTravailPort.Membre::personId).containsExactly("julie");
        assertThat(c.membres("T", "gt-x")).isEmpty();
        assertThat(c.membres("T", "gt-inconnu")).isEmpty();
        assertThat(c.membres("T", "EST")).extracting(GroupesDeTravailPort.Membre::personId).containsExactly("julie");
        assertThat(c.groupesDe("T", "julie")).containsExactly("EST", "gt-g");
        assertThat(c.groupesDe("T", "marc")).isEmpty();
        GroupesDeCommunication apres = new GroupesDeCommunication(unites, lecture, Clock.fixed(Instant.parse("2031-03-08T12:00:00Z"), ZoneOffset.UTC));
        assertThat(apres.membres("T", "gt-g")).isEmpty();
    }
}
