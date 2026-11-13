package ca.vina.nexu.partage.journal.infrastructure;

import ca.vina.nexu.partage.journal.Niveau;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Le journal se règle par le yml (ADR 0041) : {@code LOG_NIVEAU}, {@code LOG_NIVEAU_RACINE} et
 * {@code LOG_FORMAT} de l'environnement du service deviennent les propriétés de journalisation de
 * Spring Boot — un seul endroit lit ces variables, et une valeur inconnue arrête le démarrage en la
 * nommant, comme au Node. Enregistré dans {@code META-INF/spring.factories}.
 */
public class ConfigurationJournal implements EnvironmentPostProcessor {

    static final String VAR_NIVEAU = "LOG_NIVEAU";
    static final String VAR_NIVEAU_RACINE = "LOG_NIVEAU_RACINE";
    static final String VAR_FORMAT = "LOG_FORMAT";
    static final String FORMAT_JSON = "json";
    static final String FORMAT_TEXTE = "texte";
    static final String PAQUET_APPLICATION = "ca.vina.nexu";
    /** En texte, le motif garde l'identifiant de requête et le compte : ce que l'œil cherche d'abord. */
    static final String MOTIF_TEXTE = "%d{HH:mm:ss} %-5level %logger{0} %msg %X{requete} %X{utilisateur} %X{organisme}%n";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environnement, SpringApplication application) {
        environnement.getPropertySources().addFirst(new MapPropertySource("journal-nexu", proprietes(
                environnement.getProperty(VAR_NIVEAU), environnement.getProperty(VAR_NIVEAU_RACINE), environnement.getProperty(VAR_FORMAT))));
    }

    /** Les propriétés Spring dérivées des trois variables ; pur, éprouvé à part. */
    static Map<String, Object> proprietes(String niveau, String niveauRacine, String format) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("logging.level." + PAQUET_APPLICATION, Niveau.lu(niveau).code().toUpperCase());
        p.put("logging.level.root", niveauRacine == null || niveauRacine.isBlank() ? Niveau.WARN.code().toUpperCase() : Niveau.lu(niveauRacine).code().toUpperCase());
        String f = format == null || format.isBlank() ? FORMAT_JSON : format.trim().toLowerCase();
        if (FORMAT_JSON.equals(f)) {
            p.put("logging.structured.format.console", FormatJsonJournal.class.getName());
        } else if (FORMAT_TEXTE.equals(f)) {
            p.put("logging.pattern.console", MOTIF_TEXTE);
        } else {
            throw new IllegalArgumentException("LOG_FORMAT « " + format + " » inconnu : " + FORMAT_JSON + ", " + FORMAT_TEXTE);
        }
        return p;
    }
}
