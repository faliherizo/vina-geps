package ca.vina.nexu.communication.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Codes de conversation — conversion à la frontière, inconnu refusé")
class CodesConversationTest {

    @Test
    @DisplayName("type et rôle se lisent par leur code ; un code inconnu ou absent ne donne rien")
    void codes() {
        assertThat(TypeConversation.de("groupe")).contains(TypeConversation.GROUPE);
        assertThat(TypeConversation.de("individuelle")).contains(TypeConversation.INDIVIDUELLE);
        assertThat(TypeConversation.de("salon")).isEmpty();
        assertThat(TypeConversation.de(null)).isEmpty();
        assertThat(TypeConversation.GROUPE.est("groupe")).isTrue();
        assertThat(TypeConversation.GROUPE.est(null)).isFalse();
        assertThat(RoleParticipant.de("moderateur")).contains(RoleParticipant.MODERATEUR);
        assertThat(RoleParticipant.de("membre").orElseThrow().code()).isEqualTo("membre");
        assertThat(RoleParticipant.de("invite")).isEmpty();
        assertThat(RoleParticipant.MEMBRE.est("moderateur")).isFalse();
    }
}
