package ca.vina.nexu.partage.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * Alignement du contrat HTTP sur celui d'Express.
 *
 * <p>Express répond {@code application/json; charset=utf-8}. Spring omet le paramètre
 * {@code charset}, considérant — à juste titre selon la RFC 8259 — que JSON est UTF-8 par
 * définition. Les deux ont raison sur le fond ; il n'y en a qu'un qui décrit le contrat que le
 * front-end reçoit aujourd'hui, et c'est celui qu'on doit reproduire.
 *
 * <p>Ce n'est pas une subtilité gratuite : c'était la cause <strong>unique</strong> des 66 écarts
 * bloquants du premier diff du module {@code countries}. Une seule ligne d'en-tête, et tous les
 * endpoints du module échouaient la comparaison.
 */
@Configuration
public class ConfigurationWeb implements WebMvcConfigurer {

    private static final MediaType JSON_AVEC_CHARSET =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    /** L'horloge est injectée : « aujourd'hui » et « hier » sont des décisions métier, testées à date fixe. */
    @Bean
    public Clock horloge() {
        return Clock.systemUTC();
    }

    /** L'horloge en texte ISO, pour les cas d'utilisation qui datent un geste : la même que l'horloge du système. */
    @Bean
    public ca.vina.nexu.partage.temps.domaine.Horloge horlogeIso(Clock horloge) {
        return () -> ca.vina.nexu.partage.temps.Iso.de(horloge.instant());
    }


    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> convertisseurs) {
        for (HttpMessageConverter<?> c : convertisseurs) {
            if (c instanceof JacksonJsonHttpMessageConverter jackson) {
                // Le premier type pris en charge devient celui écrit dans la réponse.
                jackson.setSupportedMediaTypes(List.of(
                        JSON_AVEC_CHARSET,
                        MediaType.APPLICATION_JSON,
                        MediaType.valueOf("application/*+json")));
            }
        }
    }
}
