package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.communication.domaine.EcritureMessageriePort;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.infrastructure.configuration.ConfigurationCommunication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pièces jointes — le contenu rangé en base ou sur le volume de l'hébergement")
class CoffresPiecesJointesTest {

    private static final byte[] PDF = "%PDF-1.4 consigne".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    @DisplayName("le mode se règle par l'hébergement ; vide, c'est la base ; inconnu, le démarrage s'arrête en nommant les modes")
    void modes(@TempDir Path dossier) {
        assertThat(ModeStockagePieces.de(null)).isEqualTo(ModeStockagePieces.BASE);
        assertThat(ModeStockagePieces.de(" ")).isEqualTo(ModeStockagePieces.BASE);
        assertThat(ModeStockagePieces.de(" volume ")).isEqualTo(ModeStockagePieces.VOLUME);
        assertThatThrownBy(() -> ModeStockagePieces.de("s3")).hasMessage("PIECES_JOINTES_STOCKAGE « s3 » inconnu : base, volume");
        ConfigurationCommunication config = new ConfigurationCommunication();
        assertThat(config.coffrePiecesJointes("base", dossier.toString())).isInstanceOf(CoffreEnBase.class);
        assertThat(config.coffrePiecesJointes("volume", dossier.toString())).isInstanceOf(CoffreSurVolume.class);
    }

    @Test
    @DisplayName("en base : le contenu voyage dans la ligne, en base64 ; rien n'est rangé ailleurs")
    void enBase() {
        CoffreEnBase coffre = new CoffreEnBase();
        String colonne = coffre.enLigne(PDF).orElseThrow();
        coffre.deposer("T1", "cle", PDF);
        coffre.retirer("T1", "cle");
        assertThat(coffre.lire("T1", "cle", colonne)).hasValue(PDF);
        assertThat(coffre.lire("T1", "cle", null)).isEmpty();
    }

    @Test
    @DisplayName("sur le volume : un fichier par organisme et par clé, écrit sans rester à moitié, relu, retiré ; la ligne ne porte rien")
    void surVolume(@TempDir Path dossier) throws Exception {
        CoffreSurVolume coffre = new CoffreSurVolume(dossier);
        assertThat(coffre.enLigne(PDF)).isEmpty();
        coffre.deposer("T1", "cle-1", PDF);
        assertThat(dossier.resolve("T1").resolve("cle-1")).hasBinaryContent(PDF);
        try (var fichiers = Files.list(dossier.resolve("T1"))) {
            assertThat(fichiers.map(p -> p.getFileName().toString()).toList()).containsExactly("cle-1");
        }
        assertThat(coffre.lire("T1", "cle-1", null)).hasValue(PDF);
        assertThat(coffre.lire("T2", "cle-1", null)).isEmpty();
        coffre.retirer("T1", "cle-1");
        assertThat(coffre.lire("T1", "cle-1", null)).isEmpty();
        // Un identifiant qui sortirait du dossier n'est jamais un chemin.
        assertThatThrownBy(() -> coffre.deposer("../T2", "cle", PDF)).hasMessage("identifiant de pièce jointe impropre à un chemin de fichier");
        assertThatThrownBy(() -> coffre.lire("T1", "a/b", null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("une panne de disque se nomme, à l'écriture, à la lecture et au retrait")
    void pannes(@TempDir Path dossier) throws Exception {
        // Le dossier de l'organisme est un fichier : rien ne peut s'y écrire.
        Files.writeString(dossier.resolve("T1"), "pas un dossier");
        CoffreSurVolume coffre = new CoffreSurVolume(dossier);
        assertThatThrownBy(() -> coffre.deposer("T1", "cle", PDF)).isInstanceOf(UncheckedIOException.class)
                .hasMessage("pièce jointe : écriture sur le volume impossible");
        // La clé désigne un dossier non vide : ni lu comme un fichier, ni retiré.
        Files.createDirectories(dossier.resolve("T2").resolve("cle").resolve("dedans"));
        assertThatThrownBy(() -> coffre.lire("T2", "cle", null)).hasMessage("pièce jointe : lecture sur le volume impossible");
        assertThatThrownBy(() -> coffre.retirer("T2", "cle")).hasMessage("pièce jointe : retrait sur le volume impossible");
    }

    @Test
    @DisplayName("le dépôt range la pièce sur le volume et la relit ; une ligne refusée par la base défait le fichier")
    void depotSurVolume(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        Path volume = dossier.resolve("pieces-jointes");
        DepotMessagerie depot = new DepotMessagerie(jdbc, new CoffreSurVolume(volume));
        depot.envoyer("T1", new EcritureMessageriePort.NouveauMessage("m1", "c1", "julie", "Voir la pièce", "2026-09-16T12:00:00.000Z",
                List.of(new EcritureMessageriePort.NouvellePiece("p1", "consigne.pdf", "application/pdf", PDF.length, "cle-1", PDF)), Messagerie.Adresse.PUBLIQUE));
        assertThat(volume.resolve("T1").resolve("cle-1")).hasBinaryContent(PDF);
        assertThat(jdbc.sql("SELECT contenu FROM msg_pieces_jointes WHERE id = 'p1'").query(String.class).optional()).isEmpty();
        assertThat(depot.contenuPiece("T1", "p1")).hasValue(PDF);
        assertThat(depot.contenuPiece("T1", "inconnue")).isEmpty();
        // Même identifiant de pièce : la base refuse la ligne, et le fichier déjà posé est retiré.
        assertThatThrownBy(() -> depot.envoyer("T1", new EcritureMessageriePort.NouveauMessage("m2", "c1", "julie", "Encore", "2026-09-16T12:01:00.000Z",
                List.of(new EcritureMessageriePort.NouvellePiece("p1", "autre.pdf", "application/pdf", PDF.length, "cle-2", PDF)), Messagerie.Adresse.PUBLIQUE)))
                .isInstanceOf(RuntimeException.class);
        assertThat(volume.resolve("T1").resolve("cle-2")).doesNotExist();
    }
}
