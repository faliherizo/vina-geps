package ca.vina.nexu.organisme.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Options de l'organisme (lot R11) — domaine pur</strong>, jumeau de {@code organisme-domaine.test.ts} :
 * contrastes calculés, charte refusée si elle rend un texte illisible, logo sans script, pays ISO, verdict de
 * connexion, règles d'alerte, reconnaissance d'un téléchargement. Mêmes cas, mêmes messages.
 */
@DisplayName("Options de l'organisme — domaine")
class OptionsOrganismeDomaineTest {

    private static final String PNG = "data:image/png;base64," + "A".repeat(40);

    private static Map<String, Object> charte() {
        Map<String, Object> c = new HashMap<>();
        c.put("nomAffiche", "Urgences-santé");
        c.put("couleurPrincipale", "#2563EB");
        c.put("couleurLaterale", "#0f172a");
        c.put("logo", PNG);
        return c;
    }

    private static Map<String, Object> charteAvec(String champ, Object valeur) {
        Map<String, Object> c = charte();
        c.put(champ, valeur);
        return c;
    }

    @Nested
    @DisplayName("couleurs")
    class Couleurs {

        @Test
        @DisplayName("valide, calcule la luminance et le contraste WCAG sans flatter")
        void contrastes() {
            assertThat(Contrastes.couleurDe(" #2563EB ", "c")).isEqualTo("#2563eb");
            assertThatThrownBy(() -> Contrastes.couleurDe("bleu", "couleurPrincipale")).isInstanceOf(OptionInvalideException.class)
                    .hasMessage("« couleurPrincipale » : une couleur #RRGGBB");
            assertThatThrownBy(() -> Contrastes.couleurDe(3, "c")).hasMessageContaining("une couleur #RRGGBB");
            assertThat(List.of(Contrastes.luminance("#ffffff"), Contrastes.luminance("#000000"))).containsExactly(1.0, 0.0);
            assertThat(Contrastes.contraste("#000000", "#ffffff")).isEqualTo(21.0);
            assertThat(Contrastes.contraste("#ffffff", "#000000")).isEqualTo(21.0);
            assertThat(Contrastes.contraste("#777777", "#ffffff")).isEqualTo(4.47);
            assertThat(Contrastes.contraste("#010101", "#010101")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("déduit les nuances, plus sombre ou plus claire, et le voile blanc")
        void nuances() {
            assertThat(Contrastes.nuance("#2563eb", 0.5)).isEqualTo("#133276");
            assertThat(Contrastes.nuance("#2563eb", 2)).isEqualTo("#ffffff");
            assertThat(Contrastes.nuance("#000000", 1.5)).isEqualTo("#808080");
            assertThat(Contrastes.nuance("#000000", 1)).isEqualTo("#000000");
            assertThat(Contrastes.sousVoile("#002b49", 0.12)).isEqualTo("#1f445f");
            assertThat(Contrastes.sousVoile("#000000", 0)).isEqualTo("#000000");
        }

        @Test
        @DisplayName("écrit les nombres comme JavaScript : 21 et non 21.0")
        void nombres() {
            assertThat(Contrastes.nombre(21.0)).isEqualTo("21");
            assertThat(Contrastes.nombre(4.5)).isEqualTo("4.5");
            assertThat(Contrastes.nombre(3.99)).isEqualTo("3.99");
        }
    }

    @Nested
    @DisplayName("charte")
    class Chartes {

        @Test
        @DisplayName("la charte par défaut de l'application passe toutes les exigences")
        void parDefaut() {
            Charte c = Charte.de(charte());
            assertThat(c).isEqualTo(new Charte("Urgences-santé", "#2563eb", "#0f172a", PNG));
            assertThat(Arrays.stream(ExigenceContraste.values()).allMatch(e -> e.mesure(c.couleur(e.couleur())) >= e.minimum())).isTrue();
            Map<String, String> attendues = new LinkedHashMap<>();
            attendues.put("--primary", "#2563eb");
            attendues.put("--primary-hover", "#1f54c8");
            attendues.put("--primary-soft", "#eef3fd");
            attendues.put("--ring", "0 0 0 3px rgba(37, 99, 235, 0.28)");
            attendues.put("--sidebar", "#0f172a");
            assertThat(c.variables()).containsExactlyEntriesOf(attendues);
            assertThat(CharteAppliquee.de(c)).isEqualTo(new CharteAppliquee("Urgences-santé", "#2563eb", "#0f172a", PNG, attendues));
        }

        @Test
        @DisplayName("les six exigences, dans l'ordre du Node, avec le voile de 12 % sur la seule dernière")
        void exigences() {
            assertThat(ExigenceContraste.values()).hasSize(6);
            assertThat(Arrays.stream(ExigenceContraste.values()).map(ExigenceContraste::role)).containsExactly("texte blanc sur les boutons",
                    "liens sur le fond de page", "titre de la barre latérale", "entrées du menu", "pied de la barre latérale", "bloc de l'organisme (voile clair)");
            assertThat(Arrays.stream(ExigenceContraste.values()).map(ExigenceContraste::autre)).containsExactly("#ffffff", "#f4f6fb", "#ffffff", "#cbd5e1", "#94a3b8", "#94a3b8");
            assertThat(Arrays.stream(ExigenceContraste.values()).map(ExigenceContraste::couleur)).containsExactly(CouleurCharte.PRINCIPALE, CouleurCharte.PRINCIPALE,
                    CouleurCharte.LATERALE, CouleurCharte.LATERALE, CouleurCharte.LATERALE, CouleurCharte.LATERALE);
            assertThat(Arrays.stream(ExigenceContraste.values()).map(ExigenceContraste::voile)).containsExactly(0.0, 0.0, 0.0, 0.0, 0.0, 0.12);
            assertThat(ExigenceContraste.PIED_LATERAL.mesure("#002b49")).isGreaterThanOrEqualTo(4.5);
            assertThat(ExigenceContraste.BLOC_ORGANISME.mesure("#002b49")).isEqualTo(3.99);
        }

        @Test
        @DisplayName("refuse une couleur qui rendrait un texte illisible, avec le chiffre mesuré")
        void illisible() {
            assertThatThrownBy(() -> Charte.de(charteAvec("couleurPrincipale", "#7fb2ff"))).isInstanceOf(OptionInvalideException.class)
                    .hasMessageMatching("contraste insuffisant : couleurPrincipale #7fb2ff — texte blanc sur les boutons 2\\.\\d+:1 \\(il faut 4\\.5:1\\).*");
            assertThatThrownBy(() -> Charte.de(charteAvec("couleurLaterale", "#64748b"))).hasMessageContaining("couleurLaterale #64748b — entrées du menu");
            // Le cas trouvé par axe-core : un bleu marine qui passe partout, sauf sous le voile du bloc de l'organisme.
            assertThatThrownBy(() -> Charte.de(charteAvec("couleurLaterale", "#002b49")))
                    .hasMessage("contraste insuffisant : couleurLaterale #002b49 — bloc de l'organisme (voile clair) 3.99:1 (il faut 4.5:1)");
            assertThat(Charte.de(charteAvec("couleurLaterale", "#001a2e")).couleurLaterale()).isEqualTo("#001a2e");
        }

        @Test
        @DisplayName("énumère tous les manques, séparés par « ; »")
        void plusieursManques() {
            assertThatThrownBy(() -> Charte.de(charteAvec("couleurPrincipale", "#ffffff")))
                    .hasMessageStartingWith("contraste insuffisant : couleurPrincipale #ffffff — texte blanc sur les boutons 1:1 (il faut 4.5:1) ; couleurPrincipale #ffffff — liens");
        }

        @Test
        @DisplayName("nom obligatoire et borné ; couleurs validées dans l'ordre ; logo matriciel seulement, 200 Ko au plus")
        void nomEtLogo() {
            assertThatThrownBy(() -> Charte.de(charteAvec("nomAffiche", " "))).hasMessage("« nomAffiche » : de 1 à 60 caractères");
            assertThatThrownBy(() -> Charte.de(charteAvec("nomAffiche", "x".repeat(61)))).hasMessageContaining("de 1 à 60 caractères");
            assertThatThrownBy(() -> Charte.de(charteAvec("nomAffiche", 5))).hasMessageContaining("« nomAffiche »");
            assertThat(Charte.de(charteAvec("nomAffiche", "x".repeat(60))).nomAffiche()).hasSize(60);
            assertThatThrownBy(() -> Charte.de(charteAvec("couleurLaterale", null))).hasMessage("« couleurLaterale » : une couleur #RRGGBB");
            Map<String, Object> sansLogo = charte();
            sansLogo.remove("logo");
            assertThat(Charte.de(sansLogo).logo()).isNull();
            assertThat(Arrays.asList(Charte.logoDe(null), Charte.logoDe(""))).containsExactly(null, null);
            assertThat(Charte.logoDe("data:image/webp;base64,AAAA")).isEqualTo("data:image/webp;base64,AAAA");
            assertThat(Charte.logoDe("data:image/jpeg;base64,AAA=")).isEqualTo("data:image/jpeg;base64,AAA=");
            assertThatThrownBy(() -> Charte.logoDe("data:image/svg+xml;base64,PHN2Zz4=")).hasMessageContaining("le SVG est refusé : il peut contenir du script");
            assertThatThrownBy(() -> Charte.logoDe(42)).hasMessageContaining("« logo »");
            assertThatThrownBy(() -> Charte.logoDe("data:image/png;base64," + "A".repeat(270_000))).hasMessage("« logo » : 200 Ko au plus");
            assertThat(Charte.logoDe("data:image/png;base64," + "A".repeat(266_666))).startsWith("data:image/png");
        }
    }

    @Nested
    @DisplayName("pays et alertes")
    class PaysEtAlertes {

        @Test
        @DisplayName("normalise les codes ISO, refuse le reste en nommant le vide")
        void pays() {
            assertThat(PaysAutorises.paysDe(List.of(" ca", "US", "CA"))).containsExactly("CA", "US");
            assertThat(PaysAutorises.paysDe(List.of())).isEmpty();
            assertThatThrownBy(() -> PaysAutorises.paysDe("CA")).hasMessage("« pays » : une liste de 50 codes ISO au plus (ex. CA, US)");
            assertThatThrownBy(() -> PaysAutorises.paysDe(Collections.nCopies(51, "CA"))).hasMessageContaining("50 codes ISO au plus");
            assertThat(PaysAutorises.paysDe(Collections.nCopies(50, "CA"))).containsExactly("CA");
            List<Object> faux = new ArrayList<>(List.of("CAN", 3));
            assertThatThrownBy(() -> PaysAutorises.paysDe(faux)).hasMessage("« pays » : codes ISO à deux lettres attendus (reçu : CAN, vide)");
        }

        @Test
        @DisplayName("juge une connexion : sans restriction, dans la liste, hors liste, ou pays inconnu")
        void verdicts() {
            assertThat(PaysAutorises.verdict(List.of(), null)).isEqualTo(new PaysAutorises.Verdict(true, null));
            assertThat(PaysAutorises.verdict(List.of("CA"), "ca")).isEqualTo(new PaysAutorises.Verdict(true, null));
            assertThat(PaysAutorises.verdict(List.of("CA"), "fr"))
                    .isEqualTo(new PaysAutorises.Verdict(false, "connexion refusée depuis ce pays (FR) : cet organisme ne l'autorise pas"));
            assertThat(PaysAutorises.verdict(List.of("CA"), null).autorise()).isFalse();
            assertThat(PaysAutorises.verdict(List.of("CA"), null).motif()).contains("n'a pas pu être établi");
            assertThat(PaysAutorises.verdict(List.of("CA"), "").motif()).contains("n'a pas pu être établi");
        }

        @Test
        @DisplayName("lit le pays d'un en-tête : deux lettres, sinon rien")
        void entete() {
            assertThat(PaysAutorises.paysDeLEntete(" ca ")).isEqualTo("CA");
            assertThat(PaysAutorises.paysDeLEntete("Canada")).isNull();
            assertThat(PaysAutorises.paysDeLEntete(null)).isNull();
            assertThat(new ReglagesPays("X-Pays").estConfigure()).isTrue();
            assertThat(new ReglagesPays("").estConfigure()).isFalse();
            assertThat(new ReglagesPays(null).estConfigure()).isFalse();
        }

        @Test
        @DisplayName("valide l'événement et les destinataires ; rédige l'alerte sans le contenu")
        void alertes() {
            assertThat(EvenementAlerte.de("EXPORT_TELECHARGE")).isEqualTo(EvenementAlerte.EXPORT_TELECHARGE);
            assertThat(EvenementAlerte.de("CONNEXION_REFUSEE_PAYS").code()).isEqualTo("CONNEXION_REFUSEE_PAYS");
            assertThatThrownBy(() -> EvenementAlerte.de("TOUT")).hasMessage("« evenement » : EXPORT_TELECHARGE, CONNEXION_REFUSEE_PAYS");
            assertThatThrownBy(() -> EvenementAlerte.de(null)).isInstanceOf(OptionInvalideException.class);
            assertThat(Alertes.destinatairesDe(List.of(" Securite@US.ca ", "securite@us.ca"))).containsExactly("securite@us.ca");
            assertThatThrownBy(() -> Alertes.destinatairesDe(List.of())).hasMessage("« destinataires » : de 1 à 10 adresses courriel");
            assertThatThrownBy(() -> Alertes.destinatairesDe("a@b.c")).hasMessageContaining("de 1 à 10 adresses courriel");
            assertThatThrownBy(() -> Alertes.destinatairesDe(Collections.nCopies(11, "a@b.ca"))).hasMessageContaining("de 1 à 10 adresses courriel");
            List<Object> faux = new ArrayList<>(List.of("pas-une-adresse", 7));
            assertThatThrownBy(() -> Alertes.destinatairesDe(faux)).hasMessage("« destinataires » : adresse invalide (pas-une-adresse, vide)");
            assertThatThrownBy(() -> Alertes.destinatairesDe(List.of("a b@c.ca"))).hasMessageContaining("adresse invalide (a b@c.ca)");
            Alertes.TexteAlerte a = Alertes.texte(EvenementAlerte.EXPORT_TELECHARGE, new Circonstances("julie", "2031-01-15T10:00:00Z", "journal.csv"));
            assertThat(a.sujet()).isEqualTo("[Alerte] Téléchargement d'un export (CSV, PDF, tableur)");
            assertThat(a.texte()).contains("Qui : julie\nQuand : 2031-01-15T10:00:00Z\nQuoi : journal.csv")
                    .startsWith("Téléchargement d'un export (CSV, PDF, tableur).\n\n")
                    .endsWith("\n\nCette alerte suit une règle définie par l'administration de votre organisme.");
            assertThat(Alertes.texte(EvenementAlerte.CONNEXION_REFUSEE_PAYS, new Circonstances("q", "t", "x")).sujet())
                    .isEqualTo("[Alerte] Connexion refusée par la restriction de pays");
        }

        @Test
        @DisplayName("reconnaît un téléchargement à sa réponse, et en lit le nom de fichier")
        void telechargements() {
            assertThat(Telechargements.estTelechargement(200, "attachment; filename=\"x.csv\"")).isTrue();
            assertThat(Telechargements.estTelechargement(200, "  Attachment")).isTrue();
            assertThat(Telechargements.estTelechargement(200, "inline; filename=\"x.pdf\"")).isFalse();
            assertThat(Telechargements.estTelechargement(403, "attachment")).isFalse();
            assertThat(Telechargements.estTelechargement(199, "attachment")).isFalse();
            assertThat(Telechargements.estTelechargement(300, "attachment")).isFalse();
            assertThat(Telechargements.estTelechargement(200, null)).isFalse();
            assertThat(Telechargements.fichierDe("attachment; filename*=UTF-8''paie%20janvier.csv")).isEqualTo("paie janvier.csv");
            assertThat(Telechargements.fichierDe("attachment; filename=\"a+b.csv\"")).isEqualTo("a+b.csv");
            assertThat(Telechargements.fichierDe("attachment; filename=\"%zz.csv\"")).isEqualTo("%zz.csv");
            assertThat(Telechargements.fichierDe("attachment")).isEqualTo("export");
            assertThat(Telechargements.fichierDe(null)).isEqualTo("export");
            assertThat(Telechargements.quoi("attachment; filename*=UTF-8''paie%20janvier.csv", "GET", "/api/x?y=1")).isEqualTo("paie janvier.csv (GET /api/x)");
            assertThat(Telechargements.quoi("attachment", "GET", "/api/x")).isEqualTo("export (GET /api/x)");
        }

        @Test
        @DisplayName("l'état d'un établissement : inconnu, suspendu, résilié ou ouvert")
        void etablissements() {
            assertThat(AccesEtablissement.de(Optional.empty())).isEqualTo(AccesEtablissement.INTROUVABLE);
            assertThat(AccesEtablissement.de(Optional.of("suspended"))).isEqualTo(AccesEtablissement.SUSPENDU);
            assertThat(AccesEtablissement.de(Optional.of("terminated"))).isEqualTo(AccesEtablissement.RESILIE);
            assertThat(AccesEtablissement.de(Optional.of("trial"))).isEqualTo(AccesEtablissement.OUVERT);
            assertThat(AccesEtablissement.de(Optional.of("active"))).isEqualTo(AccesEtablissement.OUVERT);
        }
    }
}
