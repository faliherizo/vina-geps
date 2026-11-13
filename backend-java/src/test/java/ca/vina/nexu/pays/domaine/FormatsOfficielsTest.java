package ca.vina.nexu.pays.domaine;

import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Formats officiels — les replis sur valeur par défaut.
 *
 * <p>Ces tests visent surtout les branches « valeur absente » : période nulle, employeur nul,
 * salarié sans nom, cotisation absente du cumul. Ce ne sont pas des cas d'école — le fichier
 * produit part chez la DGFiP, l'IRS ou la DGI. Un repli qui diffère de celui du Node produit un
 * fichier <em>accepté</em> mais faux, ce qui est pire qu'un refus.
 */
@DisplayName("Formats officiels — replis et bornes")
class FormatsOfficielsTest {

    private final RegistrePays registre = new RegistrePays();

    private List<FormatsOfficiels.Salarie> unSalarie(String code, String nom, String id) {
        return List.of(new FormatsOfficiels.Salarie(nom, id,
                MoteurPaie.calculer(registre.pack(code).orElseThrow(), 48000, 12)));
    }

    @Test
    @DisplayName("un pays sans format porté est refusé, jamais approximé")
    void paysSansFormat() {
        // Le Canada n'a pas de format officiel ici. Produire un fichier « plausible » pour un
        // pays non porté serait le pire des comportements : il partirait quand même.
        assertThatThrownBy(() -> FormatsOfficiels.generer(registre.pack("CA").orElseThrow(), List.of(), Map.of(), "2026-01"))
                .isInstanceOf(FormatsOfficiels.FormatNonPorteException.class)
                .hasMessageContaining("CA");
        assertThat(FormatsOfficiels.aUnFormatOfficiel("CA")).isFalse();
        assertThat(FormatsOfficiels.aUnFormatOfficiel("FR")).isTrue();
    }

    @Nested
    @DisplayName("🇺🇸 IRS Form 941")
    class Form941 {

        @Test
        @DisplayName("sans période ni employeur, les gabarits officiels tiennent lieu de valeur")
        void repliSansPeriodeNiEmployeur() {
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("US").orElseThrow(), unSalarie("US", "Jane Doe", "123-45-6789"), null, null);

            assertThat(r.content()).contains("Quarter: AAAA-Qn");
            assertThat(r.content()).contains("EIN: 00-0000000").contains("Name: EMPLOYER");
            // Le nom de fichier ne doit jamais contenir « null ».
            assertThat(r.filename()).isEqualTo("IRS-941-period.txt").doesNotContain("null");
        }

        @Test
        @DisplayName("une cotisation absente vaut zéro et n'annule pas la ligne")
        void cotisationAbsente() {
            // Aucun salarié : le cumul ne contient ni SS, ni MEDICARE, ni FUTA. Le Node lit
            // `?? 0` ; le Java doit écrire 0.00, pas planter ni omettre la ligne.
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("US").orElseThrow(), List.of(), Map.of("ein", "12-3456789", "name", "ACME"), "2026-Q1");

            assertThat(r.content()).contains("Line 1: Number of employees: 0");
            assertThat(r.content()).contains("Line 5e: Total social security and Medicare taxes: 0.00");
            assertThat(r.content()).contains("FUTA (Form 940, separate): employer 0.00");
            assertThat(r.content()).contains("EIN: 12-3456789").contains("Name: ACME");
            assertThat(r.summary().employeeCount()).isZero();
        }

        @Test
        @DisplayName("les caractères interdits sont retirés du nom de fichier")
        void nomDeFichierAssaini() {
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("US").orElseThrow(), List.of(), Map.of(), "2026/Q1 (rév.)");
            assertThat(r.filename()).isEqualTo("IRS-941-2026Q1rv.txt");
        }
    }

    @Nested
    @DisplayName("🇲🇬 Déclaration DGI / CNaPS")
    class DeclarationMg {

        @Test
        @DisplayName("sans période ni employeur, les gabarits tiennent lieu de valeur")
        void repliSansPeriodeNiEmployeur() {
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("MG").orElseThrow(), unSalarie("MG", "Rakoto Jean", "MG-1"), null, null);

            assertThat(r.content()).contains("Periode: AAAA-MM");
            assertThat(r.content()).contains("NIF: 0000000000").contains("Employeur: EMPLOYEUR");
            assertThat(r.filename()).doesNotContain("null");
            assertThat(r.country()).isEqualTo("MG");
        }

        @Test
        @DisplayName("l'employeur renseigné est repris tel quel")
        void employeurRenseigne() {
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(registre.pack("MG").orElseThrow(),
                    unSalarie("MG", "Rasoa", "MG-2"),
                    Map.of("nif", "1234567890", "cnapsNumber", "998877", "name", "SOCIETE X"), "2026-03");

            assertThat(r.content()).contains("NIF: 1234567890")
                    .contains("N° CNaPS employeur: 998877")
                    .contains("Employeur: SOCIETE X")
                    .contains("Periode: 2026-03");
            assertThat(r.summary().employeeCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("🇫🇷 DSN")
    class Dsn {

        @Test
        @DisplayName("sans période, la DSN retombe sur le gabarit AAAAMM")
        void periodeAbsente() {
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("FR").orElseThrow(), unSalarie("FR", "Martin Claire", "1850578006048"), Map.of(), null);
            assertThat(r.content()).contains("S20.G00.05.003,'AAAAMM'");
        }

        @Test
        @DisplayName("une période vide vaut une période absente")
        void periodeVide() {
            // `"".replace("-","")` donne "" : sans le test d'inanité, `substring(0,6)` sur une
            // chaîne vide donnerait "" et la rubrique partirait vide vers la DGFiP.
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("FR").orElseThrow(), unSalarie("FR", "Martin Claire", "1850578006048"), Map.of(), "");
            assertThat(r.content()).contains("S20.G00.05.003,'AAAAMM'");
        }

        @Test
        @DisplayName("un salarié sans nom ni NIR reçoit les valeurs de repli du Node")
        void salarieSansNomNiNir() {
            List<FormatsOfficiels.Salarie> anonymes = List.of(
                    new FormatsOfficiels.Salarie(null, null, MoteurPaie.calculer(registre.pack("FR").orElseThrow(), 30000, 12)));

            FormatsOfficiels.Remise r = FormatsOfficiels.generer(registre.pack("FR").orElseThrow(), anonymes, Map.of(), "2026-02");

            // « SALARIE 1 » se scinde sur l'espace : nom « SALARIE », prénom « 1 ».
            assertThat(r.content()).contains("S21.G00.30.002,'SALARIE'");
            assertThat(r.content()).contains("S21.G00.30.004,'1'");
            assertThat(r.content()).doesNotContain("null").doesNotContain("undefined");
        }

        @Test
        @DisplayName("un nom en un seul mot reçoit le prénom de repli")
        void nomSansEspace() {
            // `"Prince".split(" ")` rend un seul élément : le prénom est absent. Le Node écrit
            // alors « PRENOM » ; sans ce repli, le gabarit produirait « undefined ».
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(
                    registre.pack("FR").orElseThrow(), unSalarie("FR", "Prince", "1850578006048"), Map.of(), "2026-02");

            assertThat(r.content()).contains("S21.G00.30.002,'Prince'");
            assertThat(r.content()).contains("S21.G00.30.004,'PRENOM'");
        }

        @Test
        @DisplayName("l'employeur renseigné remplace les valeurs de repli")
        void employeurRenseigne() {
            FormatsOfficiels.Remise r = FormatsOfficiels.generer(registre.pack("FR").orElseThrow(),
                    unSalarie("FR", "Martin Claire", "1850578006048"),
                    Map.of("siren", "552100554", "nic", "00024", "ape", "6201Z",
                            "raisonSociale", "ACME SAS", "ville", "PARIS"), "2026-02");

            assertThat(r.content()).contains("S10.G00.00.001,'552100554'");
            assertThat(r.content()).contains("S21.G00.11.007,'PARIS'");
            // Les clés non fournies gardent leur repli.
            assertThat(r.content()).contains("S21.G00.11.006,'00000'");
        }
    }
}
