package ca.vina.nexu.paie.infrastructure.persistance;

import ca.vina.nexu.paie.domaine.Bareme;
import ca.vina.nexu.paie.domaine.Baremes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les barèmes de paie, un fichier par année ({@code paie/bareme-AAAA.json}). Lecture <strong>stricte</strong> : un champ
 * inconnu est une erreur, car un taux mal nommé et ignoré paierait faux sans rien dire.
 */
public class BaremesFichiers implements Baremes {

    private static final String GABARIT = "/paie/bareme-%d.json";
    private final String gabarit;
    private final ObjectMapper lecteur = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final Map<Integer, Optional<Bareme>> lus = new ConcurrentHashMap<>();

    public BaremesFichiers() {
        this(GABARIT);
    }

    /** Le gabarit du chemin est une couture : sans elle, aucun essai ne prouverait qu'un barème illisible arrête la paie en nommant l'année. */
    BaremesFichiers(String gabarit) {
        this.gabarit = gabarit;
    }

    @Override
    public Optional<Bareme> pour(int annee) {
        return lus.computeIfAbsent(annee, this::lire);
    }

    private Optional<Bareme> lire(int annee) {
        try (InputStream flux = getClass().getResourceAsStream(gabarit.formatted(annee))) {
            return flux == null ? Optional.empty() : Optional.of(lecteur.readValue(flux, Bareme.class));
        } catch (IOException | JacksonException e) {
            // Jackson 3 lève des exceptions non vérifiées : sans les attraper ici, un champ mal nommé remonterait sans dire l'année.
            throw new IllegalStateException("barème de paie " + annee + " illisible : " + e.getMessage(), e);
        }
    }
}
