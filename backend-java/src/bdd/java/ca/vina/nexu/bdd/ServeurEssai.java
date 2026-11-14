package ca.vina.nexu.bdd;

import ca.vina.nexu.ApplicationNexuVina;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le serveur Java démarré <b>pour de vrai</b> une seule fois pour toute la suite : port libre, base
 * SQLite vierge que Liquibase bâtit, mode d'essai — les réglages de {@code scripts/campagne-e2e.sh}.
 * Les réglages passent en arguments de ligne de commande : une variable d'environnement de la machine
 * ne peut donc pas les détourner.
 */
final class ServeurEssai {

    /** Le pare-feu applicatif compte les requêtes par adresse : une suite entière vient de la même. */
    private static final String REQUETES_PAR_MINUTE = "10000000";

    private static final class Unique {
        private static final ServeurEssai INSTANCE = new ServeurEssai();
    }

    private final String base;

    private ServeurEssai() {
        Path dossier = dossierDEssai();
        ConfigurableApplicationContext contexte = new SpringApplicationBuilder(ApplicationNexuVina.class).run(
                "--PORT=0",
                "--NEXU_ESSAI=1",
                "--DB_URL=jdbc:sqlite:" + dossier.resolve("bdd.db"),
                "--SAUVEGARDES_DOSSIER=" + dossier.resolve("sauvegardes"),
                "--NEXU_DONNEES=" + dossier.resolve("donnees"),
                "--REGION_DONNEES=canadaeast",
                "--SOUTIEN_COURRIEL=soutien@nexuvina.test",
                "--WAF_REQUETES_PAR_MINUTE=" + REQUETES_PAR_MINUTE,
                "--ORDONNANCEUR_INTERVALLE_MS=3000");
        Runtime.getRuntime().addShutdownHook(new Thread(contexte::close));
        this.base = "http://localhost:" + contexte.getEnvironment().getProperty("local.server.port");
    }

    private static Path dossierDEssai() {
        try {
            return Files.createTempDirectory("nexu-bdd-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String base() {
        return Unique.INSTANCE.base;
    }
}
