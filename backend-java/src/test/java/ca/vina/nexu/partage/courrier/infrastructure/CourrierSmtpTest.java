package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Le courrier SMTP : réglé depuis l'environnement, expéditeur du déploiement, refus journalisé sans lever ; le journal à défaut. */
@DisplayName("Courrier — SMTP ou journal")
class CourrierSmtpTest {

    @Test
    void reglageDepuisLEnvironnement_starttlsParDefaut_smtpsSur465() {
        CourrierSmtp c = CourrierSmtp.depuis(Map.of("SMTP_HOST", " smtp.exemple.qc ", "SMTP_PORT", "587", "SMTP_USER", "u", "SMTP_PASS", "p", "MAIL_FROM", "soutien@css.qc"));
        assertThat(c.transport()).isEqualTo(new Courrier.Transport("smtp", "smtp.exemple.qc", "soutien@css.qc"));
        CourrierSmtp sansAuth = CourrierSmtp.depuis(Map.of("SMTP_HOST", "smtp.exemple.qc", "SMTP_PORT", "465"));
        assertThat(sansAuth.transport().expediteur()).isEqualTo(Courrier.EXPEDITEUR_PAR_DEFAUT);
        CourrierSmtp portIllisible = CourrierSmtp.depuis(Map.of("SMTP_HOST", "smtp.exemple.qc", "SMTP_PORT", "x", "SMTP_SECURE", "1"));
        assertThat(portIllisible.transport().mode()).isEqualTo("smtp");
    }

    @Test
    void envoi_remisAuServeur_etRefusSansLever() {
        List<SimpleMailMessage> remis = new ArrayList<>();
        JavaMailSender factice = new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage m) {
                if ("refus@x.qc".equals(m.getTo()[0])) {
                    throw new MailSendException("535 Authentication failed");
                }
                remis.add(m);
            }
        };
        CourrierSmtp c = new CourrierSmtp(factice, "smtp.exemple.qc", "de@x.qc");
        assertThat(c.envoyer(new Courrier.Message("a@b.qc", "S", "T"))).isTrue();
        assertThat(remis).hasSize(1);
        assertThat(remis.get(0).getFrom()).isEqualTo("de@x.qc");
        assertThat(remis.get(0).getSubject()).isEqualTo("S");
        assertThat(c.envoyer(new Courrier.Message("refus@x.qc", "S", "T"))).isFalse();
        // Un serveur injoignable, vraiment : le refus revient sans exception.
        CourrierSmtp injoignable = CourrierSmtp.depuis(Map.of("SMTP_HOST", "127.0.0.1", "SMTP_PORT", "1"));
        assertThat(injoignable.envoyer(new Courrier.Message("a@b.qc", "S", "T"))).isFalse();
    }

    @Test
    void configuration_choisitSmtpSiHoteSinonJournal() {
        CourrierJournal journal = new CourrierJournal();
        assertThat(ConfigurationCourrier.choisir(Map.of(), journal)).isSameAs(journal);
        assertThat(ConfigurationCourrier.choisir(Map.of("SMTP_HOST", " "), journal)).isSameAs(journal);
        assertThat(ConfigurationCourrier.choisir(Map.of("SMTP_HOST", "smtp.exemple.qc"), journal)).isInstanceOf(CourrierSmtp.class);
        assertThat(new ConfigurationCourrier().courrier(journal)).isNotNull();
        assertThat(journal.envoyer(new Courrier.Message("a@b.qc", "S", "T"))).isTrue();
        assertThat(journal.envoyes()).hasSize(1);
        assertThat(journal.transport().mode()).isEqualTo("journal");
    }
}
