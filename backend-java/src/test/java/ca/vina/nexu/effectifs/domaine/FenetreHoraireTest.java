package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La <b>fenêtre de dates</b> d'une lecture d'horaire, et le <b>message</b> d'une règle annoncée.
 *
 * <p>Jumeau de {@code backend/tests/unit/effectifs-fenetre-horaire.test.ts} : les deux piles
 * doivent refuser les mêmes bornes avec les mêmes mots, sinon la même requête obtient deux
 * réponses selon le serveur qui répond — le pire des états, parce qu'il est intermittent.</p>
 */
@DisplayName("Fenêtre d'un horaire — bornes, plafond, et message d'une règle annoncée")
class FenetreHoraireTest {

    @Test
    @DisplayName("sans borne, aucune fenêtre : la lecture garde son sens d'origine")
    void sansBorne() {
        assertThat(FenetreHoraire.demandee(null, null)).isEmpty();
        assertThat(FenetreHoraire.demandee("", "  ")).isEmpty();
    }

    @Test
    @DisplayName("deux bornes valides font une fenêtre, bornes incluses")
    void deuxBornes() {
        assertThat(FenetreHoraire.demandee("2026-11-02", "2026-11-08"))
                .contains(new FenetreHoraire("2026-11-02", "2026-11-08"));
        // Un seul jour est une fenêtre : c'est la lecture d'une journée.
        assertThat(FenetreHoraire.demandee(" 2026-11-02 ", "2026-11-02"))
                .contains(new FenetreHoraire("2026-11-02", "2026-11-02"));
    }

    @Test
    @DisplayName("une borne seule est refusée : compléter l'autre en silence rendrait un horaire que personne n'a demandé")
    void borneSeule() {
        assertThatThrownBy(() -> FenetreHoraire.demandee("2026-11-02", null))
                .isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("« du » et « au » vont de pair : une fenêtre a deux bornes");
        assertThatThrownBy(() -> FenetreHoraire.demandee(null, "2026-11-08"))
                .hasMessage("« du » et « au » vont de pair : une fenêtre a deux bornes");
    }

    @Test
    @DisplayName("un ordre inversé est refusé, et nommé : zéro ligne aurait l'air d'un horaire vide")
    void ordreInverse() {
        assertThatThrownBy(() -> FenetreHoraire.demandee("2026-11-08", "2026-11-02"))
                .hasMessage("« du » (2026-11-08) ne peut suivre « au » (2026-11-02)");
    }

    @Test
    @DisplayName("le plafond est un trimestre, inclusif, et le refus dit combien de jours ont été demandés")
    void plafond() {
        assertThat(FenetreHoraire.demandee("2026-01-01", "2026-04-02"))
                .contains(new FenetreHoraire("2026-01-01", "2026-04-02"));
        assertThatThrownBy(() -> FenetreHoraire.demandee("2026-01-01", "2026-04-03"))
                .hasMessage("la fenêtre ne peut dépasser %d jours (demandé : 93)".formatted(FenetreHoraire.JOURS_MAX));
    }

    @Test
    @DisplayName("une date qui n'existe pas est refusée par son nom de champ, jamais roulée au mois suivant")
    void dateIrreelle() {
        assertThatThrownBy(() -> FenetreHoraire.demandee("02-11-2026", "2026-11-08"))
                .hasMessage("« du » : date AAAA-MM-JJ attendue");
        assertThatThrownBy(() -> FenetreHoraire.demandee("2026-11-02", "2026-13-01"))
                .hasMessage("« au » : date AAAA-MM-JJ attendue");
        // Le 30 février : le Node l'acceptait et le roulait au 2 mars, ce qui changeait la fenêtre
        // demandée sans le dire. Les deux piles le refusent désormais, avec le même message.
        assertThatThrownBy(() -> FenetreHoraire.demandee("2026-02-30", "2026-11-08"))
                .hasMessage("« du » : date AAAA-MM-JJ attendue");
    }

    @Test
    @DisplayName("le message d'une règle s'affiche entier, ou pas du tout")
    void messageAffichable() {
        assertThat(Regles.messageAffichable("Une fin de semaine demande l'accord du chef"))
                .isEqualTo("Une fin de semaine demande l'accord du chef");
        // « {min} » à l'écran se lit comme une panne, et un lecteur qui prend un gabarit pour un
        // défaut cesse de lire le reste.
        assertThat(Regles.messageAffichable("Au moins {quartsParSemaine} quarts par semaine")).isNull();
    }

    @Test
    @DisplayName("une règle annoncée porte sa clé, sa conséquence, son message et sa portée")
    void regleApplicable() {
        VersionRegle v = new VersionRegle("v-1",
                new Regle("r-1", ContexteRegle.DISPONIBILITE, "fins-de-semaine", PorteeRegle.STATUT_EMPLOI, "TPR"), 1,
                new ContenuRegle(new ConditionRegle.Comparaison("finsDeSemaine", OperateurRegle.SUPERIEUR, 0),
                        ActionRegle.EXIGER_APPROBATION, "Une fin de semaine demande l'accord du chef", 0),
                "2026-01-01", null);
        Regles.RegleApplicable a = Regles.RegleApplicable.de(v);
        assertThat(a.cle()).isEqualTo("fins-de-semaine");
        assertThat(a.action()).isEqualTo(ActionRegle.EXIGER_APPROBATION);
        assertThat(a.message()).isEqualTo("Une fin de semaine demande l'accord du chef");
        assertThat(a.porteeType()).isEqualTo(PorteeRegle.STATUT_EMPLOI);
        assertThat(a.porteeCode()).isEqualTo("TPR");
    }

    @Test
    @DisplayName("une ressource porte ses portées de règle : la carte ne se rebâtit pas à chaque jugement")
    void porteesDeRessource() {
        Ressource r = new Ressource("rs-1", "P1", "Julie", "u-1", "EST", "PARA", "TITRE", "TPR", "CSN", 3, true);
        assertThat(r.porteesDeRegle()).containsOnly(
                java.util.Map.entry("convention", "CSN"), java.util.Map.entry("statutEmploi", "TPR"),
                java.util.Map.entry("corpsEmploi", "PARA"), java.util.Map.entry("titreEmploi", "TITRE"));
        // Une ressource sans convention ni titre garde les clés, à null : une clé absente et une
        // clé nulle ne se sélectionnent pas de la même façon.
        Optional<String> sans = Optional.ofNullable(
                new Ressource("rs-2", "P2", "Marc", null, "EST", "PARA", null, null, null, 1, true).porteesDeRegle().get("convention"));
        assertThat(sans).isEmpty();
    }
}
