package ca.vina.nexu.securite.infrastructure.persistance;

import ca.vina.nexu.securite.domaine.Antivirus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Adaptateur {@code clamd} — protocole INSTREAM : {@code zINSTREAM\0}, puis des morceaux préfixés
 * de leur taille (4 octets, gros-boutiste), un morceau vide pour finir ; réponse {@code stream: OK}
 * ou {@code stream: <signature> FOUND}. Injoignable ou muet, il fait échouer le dépôt.
 */
public class AnalyseurClamd implements Antivirus.AnalyseurFichier {

    private final String hote;
    private final int port;
    private final int delaiMs;
    private final int morceau;

    public AnalyseurClamd(String hote, int port, int delaiMs, int morceau) {
        this.hote = hote;
        this.port = port;
        this.delaiMs = delaiMs;
        this.morceau = morceau;
    }

    @Override
    public Antivirus.Verdict analyser(byte[] contenu) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(delaiMs);
            socket.connect(new InetSocketAddress(hote, port), delaiMs);
            OutputStream sortie = socket.getOutputStream();
            sortie.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < contenu.length; i += morceau) {
                int taille = Math.min(morceau, contenu.length - i);
                sortie.write(ByteBuffer.allocate(4).putInt(taille).array());
                sortie.write(contenu, i, taille);
            }
            sortie.write(new byte[4]);
            sortie.flush();
            return Antivirus.interpreter(lireTout(socket.getInputStream()));
        } catch (IOException e) {
            throw new IllegalStateException("antivirus injoignable : " + e.getMessage(), e);
        }
    }

    private static String lireTout(InputStream entree) throws IOException {
        ByteArrayOutputStream reponse = new ByteArrayOutputStream();
        byte[] tampon = new byte[1024];
        int lu;
        while ((lu = entree.read(tampon)) != -1) {
            reponse.write(tampon, 0, lu);
        }
        return reponse.toString(StandardCharsets.UTF_8);
    }
}
