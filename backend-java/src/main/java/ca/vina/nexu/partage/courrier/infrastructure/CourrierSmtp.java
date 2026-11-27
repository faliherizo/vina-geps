package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.journal.Masquage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.Properties;

/**
 * Le courrier par SMTP ({@code SMTP_HOST}, {@code SMTP_PORT} 587 à défaut, {@code SMTP_SECURE=1}
 * pour SMTPS, {@code SMTP_USER}/{@code SMTP_PASS}, {@code MAIL_FROM}). Le chiffrement est
 * <strong>exigé</strong> (E-TN-8) : sur 587, STARTTLS obligatoire — un serveur qui ne l'offre pas
 * est refusé plutôt que de recevoir le courriel en clair — et le certificat doit nommer l'hôte. Un refus du serveur ne
 * lève jamais : il se journalise et le geste métier continue — la remise des accès l'affiche à
 * l'écran, l'avis de soutien reste dans la file.
 */
public class CourrierSmtp implements Courrier {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrierSmtp.class);
    /** TLS 1.2 et 1.3 seulement, comme le répartiteur (ITSP.40.062). */
    static final String PROTOCOLES_TLS = "TLSv1.3 TLSv1.2";
    private static final String DELAI_MS = "10000";

    private final JavaMailSender envoyeur;
    private final String hote;
    private final String expediteur;

    public CourrierSmtp(JavaMailSender envoyeur, String hote, String expediteur) {
        this.envoyeur = envoyeur;
        this.hote = hote;
        this.expediteur = expediteur;
    }

    /** L'envoyeur réglé depuis l'environnement, comme le Node avec nodemailer. */
    public static CourrierSmtp depuis(Map<String, String> env) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String hote = env.getOrDefault("SMTP_HOST", "").trim();
        int port = 587;
        try {
            port = Integer.parseInt(env.getOrDefault("SMTP_PORT", "587").trim());
        } catch (NumberFormatException e) {
            // 587 à défaut, comme le Node (`Number(env.SMTP_PORT) || 587`).
        }
        boolean securise = "1".equals(env.getOrDefault("SMTP_SECURE", "").trim()) || port == 465;
        sender.setHost(hote);
        sender.setPort(port);
        String utilisateur = env.getOrDefault("SMTP_USER", "").trim();
        if (!utilisateur.isEmpty()) {
            sender.setUsername(utilisateur);
            sender.setPassword(env.getOrDefault("SMTP_PASS", ""));
        }
        sender.setJavaMailProperties(proprietes(securise, !utilisateur.isEmpty()));
        String expediteur = env.getOrDefault("MAIL_FROM", "").trim();
        return new CourrierSmtp(sender, hote, expediteur.isEmpty() ? EXPEDITEUR_PAR_DEFAUT : expediteur);
    }

    /** Les réglages de session : SMTPS direct, ou STARTTLS exigé ; l'identité du serveur vérifiée dans les deux cas. */
    static Properties proprietes(boolean securise, boolean authentifie) {
        Properties p = new Properties();
        p.put("mail.smtp.auth", String.valueOf(authentifie));
        p.put("mail.smtp.starttls.enable", String.valueOf(!securise));
        p.put("mail.smtp.starttls.required", String.valueOf(!securise));
        p.put("mail.smtp.ssl.enable", String.valueOf(securise));
        p.put("mail.smtp.ssl.checkserveridentity", "true");
        p.put("mail.smtp.ssl.protocols", PROTOCOLES_TLS);
        p.put("mail.smtp.connectiontimeout", DELAI_MS);
        p.put("mail.smtp.timeout", DELAI_MS);
        return p;
    }

    @Override
    public boolean envoyer(Message message) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(expediteur);
        m.setTo(message.to());
        m.setSubject(message.subject());
        m.setText(message.text());
        try {
            envoyeur.send(m);
            return true;
        } catch (MailException e) {
            JOURNAL.atError().addKeyValue("evenement", "courrier").addKeyValue("destinataire", Masquage.masquer(message.to())).setCause(e).log("SMTP refuse un courriel");
            return false;
        }
    }

    @Override
    public Transport transport() {
        return new Transport("smtp", hote, expediteur);
    }
}
