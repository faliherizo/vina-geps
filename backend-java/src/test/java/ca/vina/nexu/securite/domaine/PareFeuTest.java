package ca.vina.nexu.securite.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pare-feu applicatif — signatures, débit, verrou de connexion, capacité")
class PareFeuTest {

    /** Une horloge à la main, en millisecondes. */
    private static final class Horloge {
        long t = 1_000_000L;
    }

    private static PareFeu pareFeu(PareFeu.Mode mode, int parMinute, int echecs, Horloge h, List<String> journal) {
        return new PareFeu(new PareFeu.Reglages(mode, parMinute, echecs, 15), () -> h.t, (a, d) -> journal.add(a + ":" + ((Map<?, ?>) d.get("details")).get("regle")));
    }

    @Test
    @DisplayName("les signatures OWASP, chacune nommée ; une requête saine passe")
    void signatures() {
        assertThat(PareFeu.inspecter("/api/x/../etc/passwd", "", null, null).regle()).isEqualTo("traversee");
        assertThat(PareFeu.inspecter("/api/x", "f=%2e%2e/", null, null).regle()).isEqualTo("traversee");
        assertThat(PareFeu.inspecter("/api/x", "q=%00", null, null).regle()).isEqualTo("octet_nul");
        assertThat(PareFeu.inspecter("/api/x", "q=1' or 1=1", null, null).regle()).isEqualTo("injection_sql");
        assertThat(PareFeu.inspecter("/api/x", "q=UNION%20SELECT%20a", null, null).regle()).isEqualTo("injection_sql");
        assertThat(PareFeu.inspecter("/api/x", "", "Mozilla <script>alert(1)</script>", null).regle()).isEqualTo("script");
        assertThat(PareFeu.inspecter("/api/x", "", null, "javascript:void(0)").libelle()).isEqualTo("script inséré");
        assertThat(PareFeu.inspecter("/api/x", "cmd=;%20cat%20/etc/shadow", null, null).regle()).isEqualTo("commande");
        assertThat(PareFeu.inspecter("/api/x", "q=" + "a".repeat(5000), null, null).regle()).isEqualTo("longueur");
        assertThat(PareFeu.inspecter("/api/x", "q=%E0%A4%A", null, null).bloquee()).isFalse();   // pourcentage illisible : décodé tel quel
        PareFeu.Verdict sain = PareFeu.inspecter("/api/scolaire/besoins", "date=2026-09-14&etablissementId=e1", "Mozilla/5.0", "http://app/");
        assertThat(sain.bloquee()).isFalse();
        assertThat(sain.regle()).isNull();
    }

    @Test
    @DisplayName("les réglages viennent de l'environnement, avec les défauts du Node")
    void reglages() {
        PareFeu.Reglages d = PareFeu.reglages(Map.of());
        assertThat(d.mode()).isEqualTo(PareFeu.Mode.BLOQUER);
        assertThat(d.requetesParMinute()).isEqualTo(20_000);
        assertThat(d.echecsConnexionAvantVerrou()).isEqualTo(10);
        assertThat(d.verrouMinutes()).isEqualTo(15);
        PareFeu.Reglages r = PareFeu.reglages(Map.of("WAF_MODE", "observer", "WAF_REQUETES_PAR_MINUTE", "5", "WAF_ECHECS_CONNEXION", "-1", "WAF_VERROU_MINUTES", "abc"));
        assertThat(r.mode()).isEqualTo(PareFeu.Mode.OBSERVER);
        assertThat(r.requetesParMinute()).isEqualTo(5);
        assertThat(r.echecsConnexionAvantVerrou()).isEqualTo(10);
        assertThat(r.verrouMinutes()).isEqualTo(15);
        assertThat(PareFeu.reglages(Map.of("WAF_MODE", "inactif", "WAF_VERROU_MINUTES", " ")).mode()).isEqualTo(PareFeu.Mode.INACTIF);
        assertThat(r.enCarte()).containsEntry("mode", "observer").containsEntry("requetesParMinute", 5);
    }

    @Test
    @DisplayName("bloquer : une signature refuse en 403 et se journalise ; le débit refuse en 429 ; observer laisse passer mais journalise ; inactif ne regarde rien")
    void modes() {
        Horloge h = new Horloge();
        List<String> journal = new ArrayList<>();
        PareFeu p = pareFeu(PareFeu.Mode.BLOQUER, 2, 10, h, journal);
        Optional<PareFeu.Refus> refus = p.controler("/api/x/../y", "", "GET", "1.2.3.4", null, null);
        assertThat(refus).isPresent();
        assertThat(refus.get().code()).isEqualTo(403);
        assertThat(refus.get().message()).isEqualTo("requête refusée par le pare-feu applicatif (traversée de répertoire)");
        assertThat(journal).containsExactly("securite.waf.bloque:traversee");
        assertThat(p.controler("/api/a", "", "GET", "1.2.3.4", null, null)).isEmpty();
        assertThat(p.controler("/api/a", "", "GET", "1.2.3.4", null, null)).isEmpty();
        PareFeu.Refus debit = p.controler("/api/a", "", "GET", "1.2.3.4", null, null).orElseThrow();
        assertThat(debit.code()).isEqualTo(429);
        assertThat(p.controler("/api/a", "", "GET", "5.6.7.8", null, null)).isEmpty();   // une autre adresse a son propre compteur
        h.t += 61_000;
        assertThat(p.controler("/api/a", "", "GET", "1.2.3.4", null, null)).isEmpty();   // la fenêtre a glissé
        Map<String, Object> etat = p.etat();
        assertThat(etat.get("mode")).isEqualTo("bloquer");
        assertThat(etat.get("blocages24h")).isEqualTo(Map.of("traversee", 1, "debit", 1));
        assertThat(etat.get("verrousActifs")).isEqualTo(0);

        List<String> observe = new ArrayList<>();
        PareFeu o = pareFeu(PareFeu.Mode.OBSERVER, 0, 10, h, observe);
        assertThat(o.controler("/api/x/../y", "", "GET", "1.2.3.4", null, null)).isEmpty();
        assertThat(observe).containsExactly("securite.waf.observe:traversee");
        for (int i = 0; i < 5; i++) {
            assertThat(o.controler("/api/a", "", "GET", "1.2.3.4", null, null)).isEmpty();   // débit 0 = pas de limite
        }
        PareFeu inactif = pareFeu(PareFeu.Mode.INACTIF, 1, 10, h, new ArrayList<>());
        assertThat(inactif.controler("/api/x/../y", "", "GET", "1.2.3.4", null, null)).isEmpty();
        assertThat(inactif.gardeConnexion("1.2.3.4", "a")).isEmpty();
        inactif.echecConnexion("1.2.3.4", "a");
        assertThat(inactif.etat().get("verrousActifs")).isEqualTo(0);
    }

    @Test
    @DisplayName("verrou de connexion : au seuil, la porte se ferme un quart d'heure, puis se rouvre ; une réussite efface les échecs")
    void verrou() {
        Horloge h = new Horloge();
        List<String> journal = new ArrayList<>();
        PareFeu p = pareFeu(PareFeu.Mode.BLOQUER, 0, 3, h, journal);
        p.echecConnexion("1.2.3.4", "Admin");
        p.echecConnexion("1.2.3.4", "admin");
        p.connexionReussie("1.2.3.4", "ADMIN");
        p.echecConnexion("1.2.3.4", "admin");
        p.echecConnexion("1.2.3.4", "admin");
        assertThat(p.gardeConnexion("1.2.3.4", "admin")).isEmpty();
        p.echecConnexion("1.2.3.4", "admin");
        assertThat(journal).containsExactly("securite.waf.bloque:verrou_pose");
        PareFeu.Refus r = p.gardeConnexion("1.2.3.4", "admin").orElseThrow();
        assertThat(r.code()).isEqualTo(429);
        assertThat(r.message()).contains("réessayez dans 15 min");
        assertThat(p.gardeConnexion("1.2.3.4", "autre")).isEmpty();
        assertThat(p.gardeConnexion("9.9.9.9", "admin")).isEmpty();
        assertThat(p.etat().get("verrousActifs")).isEqualTo(1);
        assertThat(p.etat().get("blocages24h")).isEqualTo(Map.of("verrou_pose", 1, "verrou", 1));
        h.t += 14 * 60_000L;
        assertThat(p.gardeConnexion("1.2.3.4", "admin").orElseThrow().message()).contains("dans 1 min");
        h.t += 2 * 60_000L;
        assertThat(p.gardeConnexion("1.2.3.4", "admin")).isEmpty();
        assertThat(p.etat().get("verrousActifs")).isEqualTo(0);
        // observer : le verrou se journalise mais ne ferme pas
        List<String> obs = new ArrayList<>();
        PareFeu o = pareFeu(PareFeu.Mode.OBSERVER, 0, 1, h, obs);
        o.echecConnexion("1.2.3.4", null);
        assertThat(o.gardeConnexion("1.2.3.4", null)).isEmpty();
        assertThat(obs).containsExactly("securite.waf.observe:verrou_pose", "securite.waf.observe:verrou");
        // seuil 0 : jamais de verrou
        PareFeu sans = pareFeu(PareFeu.Mode.BLOQUER, 0, 0, h, new ArrayList<>());
        sans.echecConnexion("1.2.3.4", "a");
        assertThat(sans.gardeConnexion("1.2.3.4", "a")).isEmpty();
    }

    @Test
    @DisplayName("la mesure de capacité : requêtes par minute et par cinq minutes, p50 et p95 sur cinq minutes, en vol")
    void mesure() {
        PareFeu.Mesure m = new PareFeu.Mesure();
        assertThat(m.lire(0).latenceP95Ms()).isNull();
        long t = 10 * 60_000L;
        m.noter(t - 7 * 60_000L, 5);   // un échantillon ancien : le premier suivant dans la fenêtre l'évince
        for (int i = 1; i <= 20; i++) {
            m.noter(t - i * 10_000L, i * 10L);
        }
        m.noter(t - 6 * 60_000L, 999);   // hors des cinq minutes : oublié
        m.entrer();
        PareFeu.Capacite c = m.lire(t);
        assertThat(c.requetesDerniereMinute()).isEqualTo(6);
        assertThat(c.requetesCinqMinutes()).isEqualTo(20);
        assertThat(c.latenceP50Ms()).isEqualTo(110L);
        assertThat(c.latenceP95Ms()).isEqualTo(200L);
        assertThat(c.enVol()).isEqualTo(1);
        m.sortir();
        assertThat(m.lire(t).enCarte()).containsEntry("enVol", 0);
        // au-delà de 100 000 échantillons, les plus anciens tombent
        PareFeu.Mesure grande = new PareFeu.Mesure();
        for (int i = 0; i < 100_010; i++) {
            grande.noter(t, 1);
        }
        assertThat(grande.lire(t).requetesCinqMinutes()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("les fenêtres glissantes oublient au-delà de la durée et limitent leurs clés")
    void fenetres() {
        PareFeu.Fenetres f = new PareFeu.Fenetres(1000, 2);
        assertThat(f.compter("a", 0)).isEqualTo(1);
        assertThat(f.compter("a", 500)).isEqualTo(2);
        assertThat(f.lire("a", 1200)).isEqualTo(1);
        assertThat(f.lire("b", 0)).isEqualTo(0);
        f.compter("b", 0);
        f.compter("c", 0);   // trois clés : la plus ancienne (a) tombe
        assertThat(f.lire("a", 0)).isEqualTo(0);
        f.oublier("b");
        assertThat(f.lire("b", 0)).isEqualTo(0);
    }
}
