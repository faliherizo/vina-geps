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
 * pour SMTPS, {@code SMTP_USER}/{@code SMTP_PASS}, {@code MAIL_FROM}). Un refus du serveur ne
 * lève jamais : il se journalise et le geste métier continue — la remise des accès l'affiche à
 * l'écran, l'avis de soutien reste dans la file.
 */
public class CourrierSmtp implements Courrier {

    private static final Logger JOURNAL = LoggerFactory.getLogger(CourrierSmtp.class);

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
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(!utilisateur.isEmpty()));
        p.put("mail.smtp.starttls.enable", String.valueOf(!securise));
        p.put("mail.smtp.ssl.enable", String.valueOf(securise));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        String expediteur = env.getOrDefault("MAIL_FROM", "").trim();
        return new CourrierSmtp(sender, hote, expediteur.isEmpty() ? EXPEDITEUR_PAR_DEFAUT : expediteur);
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
