package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Acceptee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConfigConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.DossierEmploye;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.EquipageSaav;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.EtatMessage;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.HorodatageExterne;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Membre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Mode;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Rejetee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Sens;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.TypeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.configDe;
import static ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.issueHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Intégrations des effectifs (lot R10) — le domaine pur</strong>, jumeau de la partie « domaine » de
 * {@code effectifs-integrations-contrat.test.ts} : connecteurs, configuration sans secret, lecture d'une réponse HTTP,
 * événements du bus, versements, données entrantes, clés d'idempotence.
 */
@DisplayName("Intégrations des effectifs — domaine pur")
class IntegrationsEffectifsDomaineTest {

    private static final ConfigConnecteur DEFAUT = ConfigConnecteur.DEFAUT;

    private static Map<String, Object> carte(Object... cv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < cv.length; i += 2) {
            m.put((String) cv[i], cv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("valide connecteur, état et type ; un connecteur entrant ne reçoit rien")
    void validations() {
        assertThat(IntegrationsEffectifs.connecteurDe("PFT")).isEqualTo(CodeConnecteur.PFT);
        assertThatThrownBy(() -> IntegrationsEffectifs.connecteurDe("SAP")).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("connecteur : RAO, SAAV, PFT, BD_CORPO, GRAPH, ESB, ENTREPOT");
        assertThat(IntegrationsEffectifs.etatDe("REJETE")).isEqualTo(EtatMessage.REJETE);
        assertThatThrownBy(() -> IntegrationsEffectifs.etatDe("PERDU")).hasMessage("« etat » : EN_ATTENTE, ENVOYE, REJETE, ECHEC");
        assertThatNoException().isThrownBy(() -> IntegrationsEffectifs.verifierType(CodeConnecteur.PFT, TypeMessage.LOT_PAIE));
        assertThatThrownBy(() -> IntegrationsEffectifs.verifierType(CodeConnecteur.PFT, TypeMessage.EQUIPAGE)).hasMessage("PFT n'accepte que : LOT_PAIE");
        assertThatThrownBy(() -> IntegrationsEffectifs.verifierType(CodeConnecteur.RAO, TypeMessage.EQUIPAGE))
                .hasMessage("RAO est un connecteur entrant : on tire ses données, on ne lui envoie rien");
        assertThat(Arrays.stream(EtatMessage.values()).map(EtatMessage::rejouable)).containsExactly(false, false, true, true);
        assertThat(IntegrationsEffectifs.relaisDe("ESB")).isEqualTo(CodeConnecteur.ESB);
        assertThat(IntegrationsEffectifs.relaisDe("ENTREPOT")).isEqualTo(CodeConnecteur.ENTREPOT);
        assertThatThrownBy(() -> IntegrationsEffectifs.relaisDe("PFT")).hasMessage("relais : ESB ou ENTREPOT");
        assertThat(CodeConnecteur.BD_CORPO.sens()).isEqualTo(Sens.ENTRANT);
        assertThat(CodeConnecteur.SAAV.libelle()).isEqualTo("SAAV — attitrage des véhicules (équipages)");
        assertThat(TypeMessage.LOT_PAIE.chemin()).isEqualTo("/lot-paie");
        assertThat(TypeMessage.VERSEMENT_JOURNAL.chemin()).isEqualTo("/versement-journal");
    }

    @Test
    @DisplayName("configure : https exigé en HTTP, secret jamais en base, valeurs courantes gardées")
    void configuration() {
        assertThat(configDe(Map.of(), DEFAUT)).isEqualTo(DEFAUT);
        ConfigConnecteur http = configDe(carte("mode", "HTTP", "url", " https://paie.exemple.ca/api ", "jetonRef", "PFT_JETON", "actif", true), DEFAUT);
        assertThat(http).isEqualTo(new ConfigConnecteur(Mode.HTTP, "https://paie.exemple.ca/api", "PFT_JETON", true));
        assertThat(configDe(carte("actif", false), http)).isEqualTo(new ConfigConnecteur(Mode.HTTP, "https://paie.exemple.ca/api", "PFT_JETON", false));
        assertThat(configDe(carte("actif", "oui"), http).actif()).isFalse();
        assertThat(configDe(carte("url", ""), DEFAUT).url()).isNull();
        assertThat(configDe(carte("url", 42, "jetonRef", null), DEFAUT)).isEqualTo(DEFAUT);
        assertThat(configDe(carte("mode", "HTTP", "url", "http://localhost:9000"), DEFAUT).url()).isEqualTo("http://localhost:9000");
        assertThat(configDe(carte("mode", "HTTP", "url", "http://127.0.0.1:9"), DEFAUT).url()).isEqualTo("http://127.0.0.1:9");
        assertThatThrownBy(() -> configDe(carte("mode", "FTP"), DEFAUT)).hasMessage("« mode » : SIMULATEUR, HTTP, SFTP");
        assertThatThrownBy(() -> configDe(carte("mode", null), DEFAUT)).hasMessage("« mode » : SIMULATEUR, HTTP, SFTP");
        assertThatThrownBy(() -> configDe(carte("mode", "HTTP"), DEFAUT)).hasMessage("« url » : obligatoire en mode HTTP");
        assertThatThrownBy(() -> configDe(carte("mode", "HTTP", "url", "pas une adresse"), DEFAUT)).hasMessage("« url » : adresse invalide");
        assertThatThrownBy(() -> configDe(carte("mode", "HTTP", "url", "adresse"), DEFAUT)).hasMessage("« url » : adresse invalide");
        for (String refusee : List.of("http://paie.exemple.ca", "ftp://localhost", "mailto:paie@exemple.ca")) {
            assertThatThrownBy(() -> configDe(carte("mode", "HTTP", "url", refusee), DEFAUT)).hasMessage("« url » : un protocole sécurisé (https) est exigé");
        }
        assertThatThrownBy(() -> configDe(carte("jetonRef", "eyJhbGciOi.secret"), DEFAUT))
                .hasMessage("« jetonRef » : le NOM d'une variable d'environnement (MAJUSCULES_ET_SOULIGNÉS), jamais le secret lui-même");
    }

    @Test
    @DisplayName("SFTP : compte, hôte, répertoire et empreinte de l'hôte exigés ; la clé privée est nommée, jamais copiée")
    void configurationSftp() {
        String empreinte = "SHA256:" + "A".repeat(42) + "/";
        String url = "sftp://nexu@sftp.urgences-sante.qc.ca:2222/depot/effectifs?empreinte=" + empreinte;
        ConfigConnecteur sftp = configDe(carte("mode", "SFTP", "url", url, "jetonRef", "ESB_CLE_SFTP"), DEFAUT);
        assertThat(sftp.mode()).isEqualTo(Mode.SFTP);
        assertThat(IntegrationsEffectifs.adresseSftp(url))
                .isEqualTo(new IntegrationsEffectifs.AdresseSftp("nexu", "sftp.urgences-sante.qc.ca", 2222, "/depot/effectifs", empreinte));
        assertThat(IntegrationsEffectifs.adresseSftp("SFTP://nexu@hote?empreinte=" + empreinte))
                .isEqualTo(new IntegrationsEffectifs.AdresseSftp("nexu", "hote", 22, "/", empreinte));
        assertThatThrownBy(() -> configDe(carte("mode", "SFTP", "url", url), DEFAUT))
                .hasMessage("« jetonRef » : obligatoire en mode SFTP — la variable qui porte la clé privée du compte");
        assertThatThrownBy(() -> configDe(carte("mode", "SFTP", "jetonRef", "ESB_CLE_SFTP"), DEFAUT)).hasMessage("« url » : obligatoire en mode SFTP");
        String forme = "« url » : sftp://compte@hote/repertoire?empreinte=SHA256:… attendu";
        for (String refusee : List.of("https://nexu@hote/depot?empreinte=" + empreinte, "sftp://hote/depot?empreinte=" + empreinte, "sftp:depot")) {
            assertThatThrownBy(() -> IntegrationsEffectifs.adresseSftp(refusee)).hasMessage(forme);
        }
        String sansEmpreinte = "« url » : l'empreinte de la clé d'hôte est exigée (?empreinte=SHA256:…, comme ssh-keygen -lf l'écrit)";
        for (String refusee : List.of("sftp://nexu@hote/depot", "sftp://nexu@hote/depot?empreinte=MD5:ab", "sftp://nexu@hote/depot?autre=1")) {
            assertThatThrownBy(() -> IntegrationsEffectifs.adresseSftp(refusee)).hasMessage(sansEmpreinte);
        }
    }

    @Test
    @DisplayName("le jeton se lit dans l'environnement ; une variable absente ou vide est nommée")
    void jeton() {
        SecretsIntegrationPort env = nom -> "PFT_JETON".equals(nom) ? Optional.of("secret") : "VIDE".equals(nom) ? Optional.of("") : Optional.empty();
        assertThat(IntegrationsEffectifs.jetonDe(null, env)).isEmpty();
        assertThat(IntegrationsEffectifs.jetonDe("PFT_JETON", env)).contains("secret");
        assertThatThrownBy(() -> IntegrationsEffectifs.jetonDe("AUTRE", env))
                .hasMessage("la variable d'environnement AUTRE n'est pas définie sur le serveur : le jeton du connecteur est introuvable");
        assertThatThrownBy(() -> IntegrationsEffectifs.jetonDe("VIDE", env)).isInstanceOf(EffectifsInvalideException.class);
    }

    @Test
    @DisplayName("lit une réponse HTTP : acceptée, rejetée avec motif, ou passagère")
    void reponseHttp() {
        assertThat(issueHttp(201, carte("id", 7))).contains(new Acceptee("7"));
        assertThat(issueHttp(200, carte("reference", "R"))).contains(new Acceptee("R"));
        assertThat(issueHttp(204, Map.of())).contains(new Acceptee("HTTP-204"));
        assertThat(issueHttp(422, carte("message", "matricule inconnu"))).contains(new Rejetee("matricule inconnu"));
        assertThat(issueHttp(409, carte("motif", "déjà reçu"))).contains(new Rejetee("déjà reçu"));
        assertThat(issueHttp(400, carte("errors", List.of("a", "b")))).contains(new Rejetee("a ; b"));
        assertThat(issueHttp(400, carte("errors", "pas une liste"))).contains(new Rejetee("rejet 400"));
        assertThat(issueHttp(400, Map.of())).contains(new Rejetee("rejet 400"));
        for (int passager : List.of(500, 503, 401, 199, 300)) {
            assertThat(issueHttp(passager, Map.of())).isEmpty();
        }
    }

    @Test
    @DisplayName("journal → événements du bus ; lots de versement ; curseur")
    void journal() {
        assertThat(IntegrationsEffectifs.evenementDe("place.attribuee")).contains("effectifs.place.attribuee");
        assertThat(IntegrationsEffectifs.evenementDe("temps.approuver")).contains("effectifs.feuille.approuvee");
        assertThat(IntegrationsEffectifs.evenementDe("referentiel.cree")).isEmpty();
        assertThat(IntegrationsEffectifs.lots(List.of(1, 2, 3, 4, 5), 2)).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(IntegrationsEffectifs.lots(List.of(), 2)).isEmpty();
        assertThat(IntegrationsEffectifs.curseurDe(new DonneesIntegrationPort.EntreeJournal("j1", "2031-01-01T00:00:00.000Z", "a", "t", "o", null, null)))
                .isEqualTo("2031-01-01T00:00:00.000Zj1");
    }

    @Test
    @DisplayName("ancienneté reçue : un nombre d'années positif, à deux décimales, converti comme Number() le fait")
    void anciennete() {
        assertThat(IntegrationsEffectifs.ancienneteValide(IntegrationsEffectifs.nombre("12.345"))).isEqualTo(OptionalDouble.of(12.35));
        assertThat(IntegrationsEffectifs.ancienneteValide(0)).isEqualTo(OptionalDouble.of(0));
        for (double refusee : new double[]{-1, 61, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThat(IntegrationsEffectifs.ancienneteValide(refusee)).isEmpty();
        }
        assertThat(IntegrationsEffectifs.nombre(null)).isZero();
        assertThat(IntegrationsEffectifs.nombre(3)).isEqualTo(3.0);
        assertThat(IntegrationsEffectifs.nombre(true)).isEqualTo(1.0);
        assertThat(IntegrationsEffectifs.nombre(false)).isZero();
        assertThat(IntegrationsEffectifs.nombre("  ")).isZero();
        assertThat(IntegrationsEffectifs.nombre("x")).isNaN();
        assertThat(IntegrationsEffectifs.nombre(List.of())).isNaN();
        assertThat(IntegrationsEffectifs.nombreAffiche(4.0)).isEqualTo(4L);
        assertThat(IntegrationsEffectifs.nombreAffiche(3.5)).isEqualTo(3.5);
        assertThat(IntegrationsEffectifs.nombreAffiche(1e30)).isEqualTo(1e30);
    }

    @Test
    @DisplayName("données entrantes : chaque champ en texte, rien de levé sur une ligne bancale")
    void donneesEntrantes() {
        assertThat(IntegrationsEffectifs.horodatageDe(carte("cle", 1, "matricule", "P1", "sens", "ENTREE", "horodatage", null)))
                .isEqualTo(new HorodatageExterne("1", "P1", "ENTREE", ""));
        assertThat(IntegrationsEffectifs.horodatageDe(null)).isEqualTo(new HorodatageExterne("", "", "", ""));
        assertThat(IntegrationsEffectifs.dossierDe(carte("matricule", "P2", "anciennete", 3.5))).isEqualTo(new DossierEmploye("P2", 3.5));
        assertThat(IntegrationsEffectifs.dossierDe(carte("matricule", "P3")).anciennete()).isNaN();
    }

    @Test
    @DisplayName("équipages SAAV : regroupés par équipage, clé écrite comme JSON.stringify")
    void equipages() {
        List<DonneesIntegrationPort.LigneEquipage> lignes = List.of(
                new DonneesIntegrationPort.LigneEquipage("e1", "2031-01-15", 360, 1080, "EST", "m1", "P1", "PARA"),
                new DonneesIntegrationPort.LigneEquipage("e1", "2031-01-15", 360, 1080, "EST", "m1", null, "TECH"),
                new DonneesIntegrationPort.LigneEquipage("e2", "2031-01-15", 420, 900, "EST", "m\"2", "P2", "PARA"));
        List<EquipageSaav> e = IntegrationsEffectifs.equipagesDe(lignes);
        assertThat(e).containsExactly(new EquipageSaav("e1", 360, 1080, "m1", List.of(new Membre("PARA", "P1"), new Membre("TECH", null))),
                new EquipageSaav("e2", 420, 900, "m\"2", List.of(new Membre("PARA", "P2"))));
        assertThat(IntegrationsEffectifs.cleEquipages("EST", "2031-01-15", e)).isEqualTo("EST:2031-01-15:["
                + "{\"id\":\"e1\",\"debut\":360,\"fin\":1080,\"milieuId\":\"m1\",\"membres\":[{\"corpsEmploi\":\"PARA\",\"matricule\":\"P1\"},"
                + "{\"corpsEmploi\":\"TECH\",\"matricule\":null}]},{\"id\":\"e2\",\"debut\":420,\"fin\":900,\"milieuId\":\"m\\\"2\","
                + "\"membres\":[{\"corpsEmploi\":\"PARA\",\"matricule\":\"P2\"}]}]");
        assertThat(IntegrationsEffectifs.json("a\\b\n\r\t\b\f\u0001é")).isEqualTo("\"a\\\\b\\n\\r\\t\\b\\f\\u0001é\"");
        assertThat(IntegrationsEffectifs.cleQuart(new DonneesIntegrationPort.QuartPublie("pl1", "2031-01-15", 360, 1080, "Caserne"), "r1"))
                .isEqualTo("pl1:r1:2031-01-15:360-1080");
    }

    @Test
    @DisplayName("publication SAAV : date AAAA-MM-JJ et unité obligatoires")
    void entreesDePublication() {
        assertThat(IntegrationsEffectifs.dateDe("2031-01-15")).isEqualTo("2031-01-15");
        assertThatThrownBy(() -> IntegrationsEffectifs.dateDe("15/01")).hasMessage("« date » : AAAA-MM-JJ");
        assertThatThrownBy(() -> IntegrationsEffectifs.dateDe(20310115)).hasMessage("« date » : AAAA-MM-JJ");
        assertThat(IntegrationsEffectifs.uniteDe(" EST ")).isEqualTo("EST");
        assertThatThrownBy(() -> IntegrationsEffectifs.uniteDe(" ")).hasMessage("« uniteCode » : l'unité dont on publie les équipages");
        assertThatThrownBy(() -> IntegrationsEffectifs.uniteDe(null)).hasMessage("« uniteCode » : l'unité dont on publie les équipages");
    }

    @Test
    @DisplayName("un connecteur jamais configuré : coupé, en simulateur ; un jeu d'essai garde ses éléments nuls")
    void connecteurParDefaut() {
        ConnecteurEnregistre c = ConnecteurEnregistre.parDefaut(CodeConnecteur.RAO);
        assertThat(List.of(c.config(), c.jeuEssai(), c.curseur() == null)).containsExactly(DEFAUT, List.of(), true);
        assertThat(new ConnecteurEnregistre(CodeConnecteur.RAO, DEFAUT, Arrays.asList(1, null), null).jeuEssai()).containsExactly(1, null);
        assertThat(new SourceIndisponibleException("x")).hasMessage("x");
        assertThat(new DestinataireIndisponibleException("y")).hasMessage("y");
    }
}
