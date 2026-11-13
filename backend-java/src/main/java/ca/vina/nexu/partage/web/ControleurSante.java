package ca.vina.nexu.partage.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /api/health} — la sonde de vie, sans session : la sonde du conteneur, l'équilibreur de
 * charge et la campagne e2e attendent le serveur par elle. Elle ne lit rien : un serveur qui répond
 * est un serveur vivant, et une sonde qui interrogerait la base ferait redémarrer le conteneur à la
 * première lenteur de celle-ci.
 */
@RestController
public class ControleurSante {

    private static final Map<String, String> VIVANT = Map.of("status", "ok");

    @GetMapping("/api/health")
    public Map<String, String> sante() {
        return VIVANT;
    }
}
