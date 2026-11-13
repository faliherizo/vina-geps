package ca.vina.nexu.demonstration.domaine;

import ca.vina.nexu.securite.domaine.Antivirus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le protocole d'un {@code clamd} de démonstration : commandes préfixées {@code z} (terminées par
 * {@code \0}) ou {@code n} (terminées par {@code \n}) ; {@code PING} → {@code PONG}, {@code VERSION}
 * → la version, {@code INSTREAM} → des morceaux préfixés de leur taille (4 octets gros-boutiste), un
 * morceau vide pour finir, puis {@code stream: OK} ou {@code stream: <signature> FOUND}. Une commande
 * inconnue reçoit {@code UNKNOWN COMMAND}, comme le vrai démon.
 *
 * <p>Il ne reconnaît qu'une signature — EICAR, la seule que l'on puisse manier sans virus — et c'est
 * assez pour prouver la chaîne : l'adaptateur {@code AnalyseurClamd} réel lui parle sans le savoir.
 */
public final class ClamdDemo {

    private ClamdDemo() {
    }

    public static final String VERSION = "ClamAV 1.4.1/demonstration/Nexu VINA";
    private static final Pattern COMMANDE = Pattern.compile("^[zn]([A-Z]+)[\\u0000\\n]");
    private static final byte[] EICAR = Antivirus.EICAR.getBytes(StandardCharsets.US_ASCII);

    /** Ce qu'il faut renvoyer, et le terminateur que le client attend. */
    public record Reponse(String texte, String fin) {
        public byte[] octets() {
            return (texte + fin).getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    public static String verdictFlux(byte[] contenu) {
        return contient(contenu, EICAR) ? "stream: Eicar-Test-Signature FOUND" : "stream: OK";
    }

    static boolean contient(byte[] meule, byte[] aiguille) {
        for (int i = 0; i + aiguille.length <= meule.length; i++) {
            int j = 0;
            while (j < aiguille.length && meule[i + j] == aiguille[j]) {
                j++;
            }
            if (j == aiguille.length) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lit ce qui est arrivé et décide s'il y a une réponse à écrire. {@code null} : il manque encore
     * des octets (un INSTREAM dont le morceau vide n'est pas arrivé).
     */
    public static Reponse repondre(byte[] recu) {
        String texte = new String(recu, StandardCharsets.ISO_8859_1);
        boolean z = texte.startsWith("z");
        String fin = z ? "\0" : "\n";
        Matcher m = COMMANDE.matcher(texte);
        if (!m.find()) {
            return texte.contains(fin) ? new Reponse("UNKNOWN COMMAND", fin) : null;
        }
        String commande = m.group(1);
        if ("PING".equals(commande)) {
            return new Reponse("PONG", fin);
        }
        if ("VERSION".equals(commande)) {
            return new Reponse(VERSION, fin);
        }
        if (!"INSTREAM".equals(commande)) {
            return new Reponse("UNKNOWN COMMAND", fin);
        }
        // INSTREAM : après l'en-tête, des morceaux [taille][octets] ; un morceau de taille zéro clôt.
        int position = 1 + commande.length() + 1;
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        while (true) {
            if (recu.length < position + 4) {
                return null;
            }
            int taille = ByteBuffer.wrap(recu, position, 4).getInt();
            position += 4;
            if (taille == 0) {
                break;
            }
            if (recu.length < position + taille) {
                return null;
            }
            flux.write(recu, position, taille);
            position += taille;
        }
        return new Reponse(verdictFlux(flux.toByteArray()), fin);
    }
}
