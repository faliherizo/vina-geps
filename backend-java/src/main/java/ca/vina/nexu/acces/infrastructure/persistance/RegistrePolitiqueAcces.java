package ca.vina.nexu.acces.infrastructure.persistance;

import ca.vina.nexu.acces.domaine.DecisionAcces;
import ca.vina.nexu.acces.domaine.PolitiqueAcces;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge la table des droits depuis les données extraites du Node.
 *
 * <p>Lecture <strong>stricte</strong> : un champ inconnu fait échouer le démarrage. Sur une table
 * de droits, mieux vaut un service qui refuse de partir qu'un service qui part en ayant ignoré
 * une clause qu'il ne comprenait pas.
 */
@Component
public class RegistrePolitiqueAcces {

    private final PolitiqueAcces politique;
    private final DecisionAcces decision;

    public RegistrePolitiqueAcces() {
        this("/politique-acces.json");
    }

    /**
     * Le chemin de la ressource est un paramètre, et non une constante enfouie : sans cette
     * couture, l'échec au démarrage — ressource absente ou illisible — ne peut être vérifié
     * par aucun test. C'est pourtant lui qui décide si l'application s'arrête en le disant ou
     * démarre silencieusement sur un registre vide.
     */
    RegistrePolitiqueAcces(String ressource) {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream flux = getClass().getResourceAsStream(ressource)) {
            if (flux == null) {
                throw new IllegalStateException("table des droits absente du classpath : " + ressource);
            }
            Map<String, PolitiqueAcces.Regle> regles = mapper.readValue(
                    flux, new TypeReference<LinkedHashMap<String, PolitiqueAcces.Regle>>() { });
            this.politique = PolitiqueAcces.de(regles);
            this.decision = new DecisionAcces(politique);
        } catch (Exception e) {
            throw new IllegalStateException("table des droits illisible", e);
        }
    }

    public PolitiqueAcces politique() {
        return politique;
    }

    public DecisionAcces decision() {
        return decision;
    }
}
