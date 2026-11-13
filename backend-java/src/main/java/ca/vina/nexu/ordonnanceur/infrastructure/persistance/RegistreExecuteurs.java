package ca.vina.nexu.ordonnanceur.infrastructure.persistance;

import ca.vina.nexu.ordonnanceur.domaine.Executeur;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chaque module enregistre l'exécuteur de ses types de tâche. Étendre se fait par ajout, jamais
 * en modifiant un {@code switch} ici — c'est ce qui garde l'ordonnanceur ignorant du métier.
 */
@Component
public class RegistreExecuteurs {

    private final Map<String, Executeur> executeurs = new ConcurrentHashMap<>();

    public void enregistrer(String type, Executeur executeur) {
        executeurs.put(type, executeur);
    }

    public Optional<Executeur> pour(String type) {
        return Optional.ofNullable(executeurs.get(type));
    }
}
