package ca.vina.nexu.securite.infrastructure.configuration;

import ca.vina.nexu.securite.infrastructure.persistance.AnalyseurClamd;

import ca.vina.nexu.partage.demonstration.Commutateur;
import ca.vina.nexu.securite.domaine.Antivirus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** L'analyseur du processus : ClamAV dès que {@code CLAMD_HOTE} est posé, le simulateur EICAR sinon. */
@Configuration
public class ConfigurationAntivirus {

    /** L'analyseur du processus, permutable à chaud par la démonstration ; le reste du code ne voit que {@link #analyseurFichier}. */
    @Bean
    public Commutateur<Antivirus.AnalyseurFichier> commutateurAntivirus(@Value("${nexu.antivirus.clamd.hote:}") String hote, @Value("${nexu.antivirus.clamd.port:3310}") int port) {
        return new Commutateur<>(analyseur(hote, port));
    }

    @Bean
    public Antivirus.AnalyseurFichier analyseurFichier(Commutateur<Antivirus.AnalyseurFichier> commutateur) {
        return contenu -> commutateur.courant().analyser(contenu);
    }

    public static Antivirus.AnalyseurFichier analyseur(String hote, int port) {
        return hote == null || hote.isBlank() ? new Antivirus.AnalyseurSimule() : new AnalyseurClamd(hote, port, 30_000, 64 * 1024);
    }
}
