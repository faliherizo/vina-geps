package ca.vina.nexu.organisme.infrastructure.configuration;

import ca.vina.nexu.organisme.infrastructure.web.AlerteTelechargement;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Pose l'alerte au téléchargement sur toute l'API, comme {@code app.use("/api", alerterSurTelechargement)} :
 * c'est la réponse qui dit si c'est un export, pas la route — une route d'export neuve n'a rien à déclarer.
 */
@Configuration
public class ConfigurationWebOrganisme implements WebMvcConfigurer {

    private final AlerteTelechargement alerte;

    public ConfigurationWebOrganisme(AlerteTelechargement alerte) {
        this.alerte = alerte;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        registre.addInterceptor(alerte).addPathPatterns("/api/**");
    }
}
