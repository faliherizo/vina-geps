package ca.vina.nexu;

import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.ErreursValidation;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Un contrôleur monté seul, avec le conseil de validation, et une session posée comme le ferait
 * {@code FiltreSession} : l'attribut de requête {@link ContexteRequete#ATTRIBUT}. Les dépôts sont
 * réels, sur SQLite — on éprouve le contrat HTTP, pas une simulation de Spring.
 */
public final class ClientHttpEssai {

    private final MockMvc mvc;

    public ClientHttpEssai(Object... controleurs) {
        this.mvc = MockMvcBuilders.standaloneSetup(controleurs).setControllerAdvice(new ErreursValidation()).build();
    }

    public static ContexteRequete session(String role, String tenantId) {
        return new ContexteRequete("usr-" + role, role + "@t", role, "emp-" + role, tenantId);
    }

    public static ContexteRequete superadmin() {
        return new ContexteRequete("usr-sa", "sa", "superadmin", null, null);
    }

    /** Une requête bâtie par l'essai lui-même (en-têtes, corps brut), avec ou sans session. */
    public ResultActions envoyer(MockHttpServletRequestBuilder b, ContexteRequete session) throws Exception {
        return envoyer(b, session, null);
    }

    private ResultActions envoyer(MockHttpServletRequestBuilder b, ContexteRequete session, String corps) throws Exception {
        if (session != null) {
            b.requestAttr(ContexteRequete.ATTRIBUT, session);
        }
        if (corps != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(corps);
        }
        return mvc.perform(b);
    }

    public ResultActions get(String chemin, ContexteRequete session) throws Exception {
        return envoyer(MockMvcRequestBuilders.get(chemin), session, null);
    }

    public ResultActions post(String chemin, ContexteRequete session, String json) throws Exception {
        return envoyer(MockMvcRequestBuilders.post(chemin), session, json);
    }

    public ResultActions put(String chemin, ContexteRequete session, String json) throws Exception {
        return envoyer(MockMvcRequestBuilders.put(chemin), session, json);
    }

    public ResultActions patch(String chemin, ContexteRequete session, String json) throws Exception {
        return envoyer(MockMvcRequestBuilders.patch(chemin), session, json);
    }

    public ResultActions delete(String chemin, ContexteRequete session) throws Exception {
        return envoyer(MockMvcRequestBuilders.delete(chemin), session, null);
    }

    public ResultActions delete(String chemin, ContexteRequete session, String json) throws Exception {
        return envoyer(MockMvcRequestBuilders.delete(chemin), session, json);
    }
}
