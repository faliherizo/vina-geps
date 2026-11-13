package ca.vina.nexu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend Java de Nexu VINA — en cours de migration depuis le backend Node.
 *
 * <p>Pendant l'étranglement, nginx route chaque module vers l'un ou l'autre backend. Le contrat
 * HTTP est figé par le front-end, qui n'est pas modifié.
 *
 * <p>La source de données pointe sur le <strong>même fichier SQLite</strong> que le backend Node
 * pendant toute la transition : les deux processus doivent lire et écrire le même état, sans quoi
 * l'étranglement module par module serait impossible. Le fichier est en mode WAL, qui autorise
 * l'accès concurrent.
 */
@SpringBootApplication
public class ApplicationNexuVina {
    public static void main(String[] args) {
        SpringApplication.run(ApplicationNexuVina.class, args);
    }
}
