package ca.vina.nexu.habilitations.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.habilitations.application.commandes.GestionnaireHabilitations;
import ca.vina.nexu.habilitations.application.requetes.LectureHabilitations;
import ca.vina.nexu.habilitations.infrastructure.persistance.DepotHabilitations;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/habilitations} au complet — jumeau de {@code backend/src/habilitations/routes.ts}.
 *
 * <p>Le chaînage qui compte : un octroi <strong>borné dans le temps</strong>, la fiche qui annonce
 * son échéance, et la permission qui redevient {@code aucun} le lendemain de son terme — sans
 * qu'aucune tâche de ménage n'ait tourné.
 */
@DisplayName("API Habilitations — effet borné, résolution, consultation centralisée")
class ControleurHabilitationsHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete VOISIN = ClientHttpEssai.session("admin", "t-2");

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        Clock horloge = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
        DepotHabilitations depot = new DepotHabilitations(jdbc);
        LectureHabilitations lecture = new LectureHabilitations(depot, horloge);
        http = new ClientHttpEssai(new ControleurHabilitations(
                new GardeHabilitations(new GardeAcces(new RegistrePolitiqueAcces())), lecture,
                new GestionnaireHabilitations(depot, lecture, horloge)));

        http.put("/api/habilitations/objets", ADMIN, """
                {"type":"rapport","code":"rap-paie","libelle":"Rapport de paie"}""")
                .andExpect(status().isOk());
        http.put("/api/habilitations/objets", ADMIN, """
                {"type":"formulaire","code":"frm-depense","libelle":"Formulaire de dépense"}""")
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("La garde")
    class Garde {

        @Test
        @DisplayName("refuse sans session")
        void sansSession() throws Exception {
            http.get("/api/habilitations/objets", null).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un employé **consulte** ses droits : l'exigence 91 demande de les rendre consultables")
        void lectureOuverte() throws Exception {
            http.get("/api/habilitations/permissions", EMPLOYE).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("aucun"));
            http.get("/api/habilitations/types-objet", EMPLOYE).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4));
            http.get("/api/habilitations/objets", EMPLOYE).andExpect(jsonPath("$.length()").value(2));
            http.get("/api/habilitations/octrois", EMPLOYE).andExpect(status().isOk());
            http.get("/api/habilitations/utilisateurs/u1/fiche", EMPLOYE).andExpect(status().isOk());
        }

        @Test
        @DisplayName("un employé n'octroie pas, ne déclare pas, ne révoque pas")
        void ecritureReservee() throws Exception {
            http.put("/api/habilitations/objets", EMPLOYE, """
                    {"type":"rapport","code":"x","libelle":"X"}""").andExpect(status().isForbidden());
            http.post("/api/habilitations/octrois", EMPLOYE, """
                    {"sujetType":"profil","sujetId":"p","objetCode":"rap-paie","permission":"lecture"}""")
                    .andExpect(status().isForbidden());
            http.delete("/api/habilitations/octrois?sujetType=profil&sujetId=p&objetCode=rap-paie&effectifDu=2026-01-01",
                    EMPLOYE).andExpect(status().isForbidden());
            http.post("/api/habilitations/utilisateurs/u1/profils", EMPLOYE, """
                    {"profilId":"p"}""").andExpect(status().isForbidden());
            http.delete("/api/habilitations/utilisateurs/u1/profils/p", EMPLOYE).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("L'imprévu")
    class Imprevu {

        private ClientHttpEssai avecDepotFautif(RuntimeException faute) {
            ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort depot =
                    new ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort() {
                        @Override
                        public java.util.List<ca.vina.nexu.habilitations.domaine.Habilitations.Objet> listerObjets(String t) {
                            throw faute;
                        }

                        @Override
                        public void poserObjet(String t, ca.vina.nexu.habilitations.domaine.Habilitations.Objet o, String c) {
                            throw faute;
                        }

                        @Override
                        public java.util.List<ca.vina.nexu.habilitations.domaine.Habilitations.Octroi> listerOctrois(String t) {
                            throw faute;
                        }

                        @Override
                        public void poserOctroi(String t, ca.vina.nexu.habilitations.domaine.Habilitations.Octroi o,
                                                String auteurId, String c) {
                            throw faute;
                        }

                        @Override
                        public boolean retirerOctroi(String t, ca.vina.nexu.habilitations.domaine.Habilitations.Sujet s,
                                                     String sujetId, String objetCode, String effectifDu) {
                            throw faute;
                        }

                        @Override
                        public java.util.List<String> profilsDe(String t, String utilisateurId) {
                            throw faute;
                        }

                        @Override
                        public void rattacherProfil(String t, String u, String p, String c) {
                            throw faute;
                        }

                        @Override
                        public boolean detacherProfil(String t, String u, String p) {
                            throw faute;
                        }
                    };
            Clock horloge = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
            LectureHabilitations lecture = new LectureHabilitations(depot, horloge);
            return new ClientHttpEssai(new ControleurHabilitations(
                    new GardeHabilitations(new GardeAcces(new RegistrePolitiqueAcces())), lecture,
                    new GestionnaireHabilitations(depot, lecture, horloge)));
        }

        @Test
        @DisplayName("une panne du dépôt devient **400 avec son motif**, jamais un 500 muet")
        void panneNommee() throws Exception {
            avecDepotFautif(new IllegalStateException("base verrouillée"))
                    .get("/api/habilitations/objets", ADMIN).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("base verrouillée"));
        }

        @Test
        @DisplayName("une panne **sans message** se dit quand même")
        void panneSansMessage() throws Exception {
            avecDepotFautif(new IllegalStateException())
                    .get("/api/habilitations/octrois", ADMIN).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("erreur inattendue"));
        }
    }

    @Nested
    @DisplayName("Les objets protégés")
    class Objets {

        @Test
        @DisplayName("**une composante exige son formulaire parent** : sans lui, l'exigence 90 se devine")
        void composanteSansParent() throws Exception {
            http.put("/api/habilitations/objets", ADMIN, """
                    {"type":"composante","code":"cmp-montant","libelle":"Montant"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(
                            org.hamcrest.Matchers.containsString("une composante déclare le formulaire")));
            http.put("/api/habilitations/objets", ADMIN, """
                    {"type":"composante","code":"cmp-montant","libelle":"Montant","parentCode":"frm-depense"}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentCode").value("frm-depense"));
        }

        @Test
        @DisplayName("refuse un parent inconnu en 404, et un objet son propre parent en 400")
        void parentage() throws Exception {
            http.put("/api/habilitations/objets", ADMIN, """
                    {"type":"composante","code":"cmp","libelle":"C","parentCode":"frm-absent"}""")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("objet parent introuvable : frm-absent"));
            http.put("/api/habilitations/objets", ADMIN, """
                    {"type":"formulaire","code":"frm-depense","libelle":"D","parentCode":"frm-depense"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("« frm-depense » ne peut pas être son propre parent"));
        }

        @Test
        @DisplayName("refuse un type hors de l'ensemble, et un corps vide, en 400 nommé")
        void refus() throws Exception {
            http.put("/api/habilitations/objets", ADMIN, """
                    {"type":"ecran","code":"x","libelle":"X"}""").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(
                            org.hamcrest.Matchers.containsString("type d'objet inconnu : « ecran »")));
            http.put("/api/habilitations/objets", ADMIN, "{}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("« type » est obligatoire"));
        }

        @Test
        @DisplayName("sans libellé, le code fait office de libellé")
        void libelleAbsent() throws Exception {
            http.put("/api/habilitations/objets", ADMIN, """
                    {"type":"fonctionnalite","code":"fct-export"}""").andExpect(status().isOk())
                    .andExpect(jsonPath("$.libelle").value("fct-export"));
        }
    }

    @Nested
    @DisplayName("Les octrois et leur terme")
    class Octrois {

        @Test
        @DisplayName("**un droit borné cesse de lui-même** : la permission retombe à `aucun` le lendemain")
        void cesseDeLuiMeme() throws Exception {
            http.post("/api/habilitations/utilisateurs/u1/profils", ADMIN, """
                    {"profilId":"p-paie"}""").andExpect(status().isCreated())
                    .andExpect(jsonPath("$[0]").value("p-paie"));
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p-paie","objetCode":"rap-paie",
                     "permission":"lecture_ecriture","effectifDu":"2026-01-01","effectifAu":"2026-03-15",
                     "motif":"Remplacement de congé de maternité"}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.permission").value("lecture_ecriture"))
                    .andExpect(jsonPath("$.objetType").value("rapport"));

            http.get("/api/habilitations/utilisateurs/u1/permission?objetCode=rap-paie&le=2026-03-15", ADMIN)
                    .andExpect(jsonPath("$.permission").value("lecture_ecriture"))
                    .andExpect(jsonPath("$.origine").value("profil"));
            http.get("/api/habilitations/utilisateurs/u1/permission?objetCode=rap-paie&le=2026-03-16", ADMIN)
                    .andExpect(jsonPath("$.permission").value("aucun"))
                    .andExpect(jsonPath("$.origine").value("defaut"));
        }

        @Test
        @DisplayName("**la fiche annonce l'échéance** plutôt que de la laisser découvrir le matin même")
        void ficheEtEcheance() throws Exception {
            http.post("/api/habilitations/utilisateurs/u1/profils", ADMIN, """
                    {"profilId":"p-paie"}""").andExpect(status().isCreated());
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p-paie","objetCode":"rap-paie",
                     "permission":"lecture","effectifDu":"2026-01-01","effectifAu":"2026-03-15"}""")
                    .andExpect(status().isCreated());

            http.get("/api/habilitations/utilisateurs/u1/fiche", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.le").value("2026-03-01"))
                    .andExpect(jsonPath("$.profils[0]").value("p-paie"))
                    .andExpect(jsonPath("$.droits.length()").value(1))
                    .andExpect(jsonPath("$.droits[0].objet.code").value("rap-paie"))
                    .andExpect(jsonPath("$.echeances[0].joursRestants").value(14))
                    .andExpect(jsonPath("$.motifs[1]").value(
                            org.hamcrest.Matchers.containsString("expirent dans les 30 jours")));
        }

        @Test
        @DisplayName("**qui peut accéder à ce rapport ?** — la liste, à une date (exigence 90)")
        void quiPeutAcceder() throws Exception {
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p-paie","objetCode":"rap-paie",
                     "permission":"lecture","effectifDu":"2026-01-01"}""").andExpect(status().isCreated());
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"utilisateur","sujetId":"u-exclu","objetCode":"rap-paie",
                     "permission":"aucun","effectifDu":"2026-01-01"}""").andExpect(status().isCreated());

            http.get("/api/habilitations/objets/rap-paie/droits", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.objet.libelle").value("Rapport de paie"))
                    // Le refus explicite n'est pas un accès : il n'apparaît pas dans la liste.
                    .andExpect(jsonPath("$.sujets.length()").value(1))
                    .andExpect(jsonPath("$.sujets[0].sujetId").value("p-paie"));
            http.get("/api/habilitations/objets/rap-absent/droits", ADMIN).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("refuse un octroi sur un objet inconnu en 404, et une fenêtre inversée en 400")
        void refus() throws Exception {
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p","objetCode":"rap-absent","permission":"lecture",
                     "effectifDu":"2026-01-01"}""").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("objet introuvable : rap-absent"));
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p","objetCode":"rap-paie","permission":"lecture",
                     "effectifDu":"2026-06-30","effectifAu":"2026-01-01"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(
                            "l'octroi à « p » se termine (2026-01-01) avant de commencer (2026-06-30)"));
            http.post("/api/habilitations/octrois", ADMIN, "{}").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sans date de prise d'effet, l'octroi part du jour de l'horloge injectée")
        void defautDuJour() throws Exception {
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p","objetCode":"rap-paie","permission":"lecture"}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.effectifDu").value("2026-03-01"))
                    .andExpect(jsonPath("$.effectifAu").doesNotExist());
        }

        @Test
        @DisplayName("révoquer retire l'octroi ; révoquer deux fois est un 404, pas un succès muet")
        void revocation() throws Exception {
            http.post("/api/habilitations/octrois", ADMIN, """
                    {"sujetType":"profil","sujetId":"p-paie","objetCode":"rap-paie",
                     "permission":"lecture","effectifDu":"2026-01-01"}""").andExpect(status().isCreated());
            http.delete("/api/habilitations/octrois?sujetType=profil&sujetId=p-paie&objetCode=rap-paie"
                    + "&effectifDu=2026-01-01", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.revoque").value("p-paie → rap-paie"));
            http.delete("/api/habilitations/octrois?sujetType=profil&sujetId=p-paie&objetCode=rap-paie"
                    + "&effectifDu=2026-01-01", ADMIN).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("octroi introuvable : p-paie → rap-paie"));
        }
    }

    @Nested
    @DisplayName("Les profils")
    class Profils {

        @Test
        @DisplayName("rattacher deux fois n'ajoute pas deux fois ; détacher ce qui n'existe pas est un 404")
        void rattachements() throws Exception {
            http.post("/api/habilitations/utilisateurs/u1/profils", ADMIN, """
                    {"profilId":"p-paie"}""").andExpect(status().isCreated());
            http.post("/api/habilitations/utilisateurs/u1/profils", ADMIN, """
                    {"profilId":"p-paie"}""").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(1));
            http.get("/api/habilitations/utilisateurs/u1/profils", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("p-paie"));
            http.delete("/api/habilitations/utilisateurs/u1/profils/p-paie", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            http.delete("/api/habilitations/utilisateurs/u1/profils/p-paie", ADMIN).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("rattachement introuvable : u1 → p-paie"));
        }

        @Test
        @DisplayName("refuse un profil vide")
        void profilVide() throws Exception {
            http.post("/api/habilitations/utilisateurs/u1/profils", ADMIN, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("« profilId » est obligatoire"));
        }

        @Test
        @DisplayName("les habilitations d'un établissement ne se voient pas chez le voisin")
        void cloisonnement() throws Exception {
            http.get("/api/habilitations/objets", VOISIN).andExpect(jsonPath("$.length()").value(0));
        }
    }
}
