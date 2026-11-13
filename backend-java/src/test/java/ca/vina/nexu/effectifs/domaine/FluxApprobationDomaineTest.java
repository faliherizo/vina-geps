package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.DemandesFlux.DemandeApprobation;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.EtapeApprobation;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.StatutApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.Approbateur;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.ObjetFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.OperateurFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.TypeApprobateur;
import ca.vina.nexu.effectifs.domaine.ModificationHeures.NiveauActeur;
import ca.vina.nexu.effectifs.domaine.ModificationHeures.OperationModification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <strong>Flux d'approbation des heures et restriction de leur modification — le domaine pur</strong> (E-GP-6, E-GP-3),
 * jumeau de {@code effectifs-approbations-domaine.test.ts} et du bloc E-GP-3 de {@code effectifs-temps-domaine.test.ts}.
 */
@DisplayName("FluxApprobation, DemandesFlux, ModificationHeures — le pur des flux et des restrictions")
class FluxApprobationDomaineTest {

    private static Map<String, Object> base(Object... kv) {
        Map<String, Object> m = new HashMap<>(Map.of("objet", "FEUILLE", "palier", 2, "champ", "heuresSupplementaires", "operateur", "gte", "valeur", 4,
                "approbateur", Map.of("type", "PERIMETRE", "valeur", "GESTION")));
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> approbateur(String type, Object valeur) {
        Map<String, Object> a = new HashMap<>();
        a.put("type", type);
        a.put("valeur", valeur);
        return a;
    }

    @Test
    @DisplayName("valide une règle : objet, palier borné, champ connu de l'objet, opérateur compatible, valeur")
    void regles() {
        assertThat(FluxApprobation.regleDeFlux(base())).isEqualTo(new RegleFlux(null, ObjetFlux.FEUILLE, 2, "heuresSupplementaires", OperateurFlux.GTE, 4L,
                new Approbateur(TypeApprobateur.PERIMETRE, "GESTION")));
        assertThat(FluxApprobation.regleDeFlux(base("objet", "POINTAGE", "champ", "uniteCode", "operateur", "neq", "valeur", " EST ", "approbateur",
                approbateur("ROLE", " director ")))).extracting(RegleFlux::valeur, RegleFlux::approbateur)
                .containsExactly("EST", new Approbateur(TypeApprobateur.ROLE, "director"));
        assertThat(FluxApprobation.regleDeFlux(base("valeur", 4.5)).valeur()).isEqualTo(4.5);
        assertThat(FluxApprobation.regleDeFlux(base("champ", "uniteCode", "operateur", "eq", "valeur", 12)).valeur()).isEqualTo("12");
        assertThat(FluxApprobation.regleDeFlux(base("champ", "uniteCode", "operateur", "eq", "valeur", 1.5)).valeur()).isEqualTo("1.5");
        assertThatThrownBy(() -> FluxApprobation.objetDe("CONTRAT")).hasMessage("« objet » : FEUILLE, POINTAGE");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("palier", 0))).hasMessage("« palier » : entier de 1 à 10");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("palier", 11))).hasMessage("« palier » : entier de 1 à 10");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("palier", 1.5))).hasMessageContaining("« palier »");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("objet", "POINTAGE"))).hasMessageStartingWith("« champ » : toujours, ecartDebutMinutes");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("operateur", ">"))).hasMessage("« operateur » : gte, lte, eq, neq");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("champ", "uniteCode", "operateur", "lte", "valeur", "EST")))
                .hasMessage("« uniteCode » se compare par eq ou neq");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("valeur", ""))).hasMessage("« valeur » : un nombre pour « heuresSupplementaires »");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("valeur", null))).hasMessageStartingWith("« valeur » : un nombre");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("valeur", "beaucoup"))).hasMessageStartingWith("« valeur » : un nombre");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("champ", "uniteCode", "operateur", "eq", "valeur", "  "))).hasMessage("« valeur » : un texte pour « uniteCode »");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("champ", "uniteCode", "operateur", "eq", "valeur", null))).hasMessageStartingWith("« valeur » : un texte");
    }

    @Test
    @DisplayName("valide l'approbateur selon son type")
    void approbateurs() {
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("approbateur", null))).hasMessage("« approbateur.type » : PERIMETRE, ROLE, PERSONNE");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("approbateur", approbateur("PERIMETRE", "TOUT"))))
                .hasMessage("« approbateur.valeur » : LECTURE, PLANIFICATION, GESTION");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("approbateur", approbateur("ROLE", "Directeur Général")))).hasMessageContaining("le code d'un rôle");
        assertThatThrownBy(() -> FluxApprobation.regleDeFlux(base("approbateur", approbateur("PERSONNE", 7))))
                .hasMessageContaining("l'identifiant de la personne désignée");
        assertThat(FluxApprobation.regleDeFlux(base("approbateur", approbateur("PERSONNE", " u-1 "))).approbateur())
                .isEqualTo(new Approbateur(TypeApprobateur.PERSONNE, "u-1"));
    }

    @Test
    @DisplayName("se traduit vers le moteur générique et en revient sans perte ; le flux par défaut")
    void traduction() {
        RegleApprobation perimetre = FluxApprobation.versRegleApprobation(FluxApprobation.regleDeFlux(base()));
        assertThat(perimetre).isEqualTo(new RegleApprobation(null, "heuresSupplementaires", OperateurFlux.GTE, "4", 2, "perimetre:GESTION", null));
        assertThat(FluxApprobation.versRegleApprobation(FluxApprobation.regleDeFlux(base("approbateur", approbateur("ROLE", "director")))).approverRole())
                .isEqualTo("director");
        RegleApprobation personne = FluxApprobation.versRegleApprobation(FluxApprobation.regleDeFlux(base("approbateur", approbateur("PERSONNE", "u-1"))));
        assertThat(List.of(personne.approverId(), String.valueOf(personne.approverRole()))).containsExactly("u-1", "null");
        assertThat(FluxApprobation.depuisRegleApprobation(ObjetFlux.FEUILLE, new RegleApprobation("r1", "heuresSupplementaires", OperateurFlux.GTE, "4", 2,
                "perimetre:GESTION", null))).isEqualTo(new RegleFlux("r1", ObjetFlux.FEUILLE, 2, "heuresSupplementaires", OperateurFlux.GTE, 4L,
                new Approbateur(TypeApprobateur.PERIMETRE, "GESTION")));
        assertThat(FluxApprobation.depuisRegleApprobation(ObjetFlux.POINTAGE, new RegleApprobation("r2", "uniteCode", OperateurFlux.EQ, "EST", 1, "director", null))
                .valeur()).isEqualTo("EST");
        assertThat(FluxApprobation.depuisRegleApprobation(ObjetFlux.FEUILLE, new RegleApprobation("r3", "anomalies", OperateurFlux.GTE, "x", 1, "d", null))
                .valeur()).isNull();
        assertThat(FluxApprobation.approbateurDe(null, null)).isEqualTo(new Approbateur(TypeApprobateur.ROLE, ""));
        assertThat(FluxApprobation.approbateurDe("director", "")).isEqualTo(new Approbateur(TypeApprobateur.ROLE, "director"));
        assertThat(FluxApprobation.approbateurDe("director", "u-1")).isEqualTo(new Approbateur(TypeApprobateur.PERSONNE, "u-1"));
        assertThat(List.of(ObjetFlux.FEUILLE.typeDocument(), ObjetFlux.POINTAGE.typeDocument())).containsExactly("effectifs.feuille", "effectifs.pointage");
        assertThat(FluxApprobation.fluxParDefaut(2)).containsExactly(
                new RegleApprobation("defaut-1", "toujours", OperateurFlux.EQ, "1", 1, "perimetre:GESTION", null),
                new RegleApprobation("defaut-2", "toujours", OperateurFlux.EQ, "1", 2, "perimetre:GESTION", null));
        assertThat(OperateurFlux.de("gte")).contains(OperateurFlux.GTE);
        assertThat(OperateurFlux.de(">")).isEmpty();
        assertThat(FluxApprobation.nombre(Double.POSITIVE_INFINITY)).isEqualTo(Double.POSITIVE_INFINITY);
        RegleFlux r = FluxApprobation.regleDeFlux(base());
        assertThat(r.avecId("x").id()).isEqualTo("x");
        assertThat(FluxApprobation.TypeChamp.TEXTE.code()).isEqualTo("texte");
    }

    @Test
    @DisplayName("nomme l'approbateur ; dit qui porte une étape — l'administrateur les porte toutes")
    void porteurs() {
        Function<String, String> nom = id -> "Rachel";
        assertThat(DemandesFlux.libelle(new Approbateur(TypeApprobateur.PERIMETRE, "GESTION"), nom)).isEqualTo("le niveau GESTION de l'unité");
        assertThat(DemandesFlux.libelle(new Approbateur(TypeApprobateur.ROLE, "director"), nom)).isEqualTo("le rôle « director »");
        assertThat(DemandesFlux.libelle(new Approbateur(TypeApprobateur.PERSONNE, "u-1"), nom)).isEqualTo("Rachel (personne désignée)");
        DemandesFlux.Acteur gestion = new DemandesFlux.Acteur("u-1", "manager", false, n -> n == Perimetres.Niveau.GESTION);
        DemandesFlux.Acteur lecture = new DemandesFlux.Acteur("u-1", "manager", false, n -> n == Perimetres.Niveau.LECTURE);
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.PERIMETRE, "GESTION"), gestion)).isTrue();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.PERIMETRE, "GESTION"), lecture)).isFalse();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.PERIMETRE, "INCONNU"), gestion)).isFalse();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.ROLE, "director"), new DemandesFlux.Acteur("u-1", "director", false, n -> false))).isTrue();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.ROLE, "director"), gestion)).isFalse();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.PERSONNE, "u-1"), gestion)).isTrue();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.PERSONNE, "u-2"), gestion)).isFalse();
        assertThat(DemandesFlux.porte(new Approbateur(TypeApprobateur.PERSONNE, "u-2"), new DemandesFlux.Acteur("x", "admin", true, n -> false))).isTrue();
    }

    @Test
    @DisplayName("la vue d'une demande : paliers dans l'ordre, statuts traduits, palier courant")
    void vues() {
        EtapeApprobation e1 = new EtapeApprobation("s1", 1, "perimetre:GESTION", null, StatutApprobation.APPROUVE, "u-chef", "t1", null);
        EtapeApprobation e2 = new EtapeApprobation("s2", 3, null, "u-rh", StatutApprobation.EN_ATTENTE, null, null, null);
        DemandesFlux.Flux f = DemandesFlux.vue(new DemandeApprobation("d1", "doc", StatutApprobation.EN_ATTENTE, List.of(e1, e2)), id -> "rh");
        assertThat(f.palierCourant()).isEqualTo(2);
        assertThat(f.etapes()).extracting(DemandesFlux.EtapeFlux::palier, DemandesFlux.EtapeFlux::niveau, DemandesFlux.EtapeFlux::libelle, DemandesFlux.EtapeFlux::statut)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, 1, "le niveau GESTION de l'unité", DemandesFlux.StatutEtape.APPROUVEE),
                        org.assertj.core.groups.Tuple.tuple(2, 3, "rh (personne désignée)", DemandesFlux.StatutEtape.EN_ATTENTE));
        assertThat(f.etapeCourante()).map(DemandesFlux.EtapeFlux::id).contains("s2");
        DemandesFlux.Flux rejete = DemandesFlux.vue(new DemandeApprobation("d2", "doc", StatutApprobation.REJETE, List.of(e2)), id -> id);
        assertThat(List.of(rejete.statut(), String.valueOf(rejete.palierCourant()))).containsExactly(DemandesFlux.StatutFlux.REJETE, "null");
        assertThat(rejete.etapeCourante()).isEmpty();
        DemandesFlux.Flux complet = DemandesFlux.vue(new DemandeApprobation("d3", "doc", StatutApprobation.EN_ATTENTE, List.of(e1)), id -> id);
        assertThat(complet.palierCourant()).isNull();
        assertThat(List.of(StatutApprobation.de("approved"), StatutApprobation.de("rejected"), StatutApprobation.de("??"), StatutApprobation.de("pending")))
                .containsExactly(StatutApprobation.APPROUVE, StatutApprobation.REJETE, StatutApprobation.EN_ATTENTE, StatutApprobation.EN_ATTENTE);
        DemandesFlux.EtapeRefuseeException refus = new DemandesFlux.EtapeRefuseeException(DemandesFlux.RefusEtape.AUTRE, "m");
        assertThat(List.of(refus.refus(), refus.getMessage())).containsExactly(DemandesFlux.RefusEtape.AUTRE, "m");
    }

    @Test
    @DisplayName("une feuille et un pointage exposent leurs champs de routage, dans l'ordre du catalogue")
    void contextes() {
        Map<String, Object> f = DemandesFlux.contexteDeFeuille(new DemandesFlux.FeuilleSoumise("EST", 2190, 250,
                List.of(TempsEffectifs.CodeAnomalie.NON_CORROBORE, TempsEffectifs.CodeAnomalie.SANS_POINTAGE),
                List.of(new DemandesFlux.SegmentCompte(TempsEffectifs.Source.EXTERNE, 1), new DemandesFlux.SegmentCompte(TempsEffectifs.Source.POINTAGE, 2),
                        new DemandesFlux.SegmentCompte(TempsEffectifs.Source.SAISIE, 1)), List.of(-20L, 75L)));
        Map<String, Object> attendu = new LinkedHashMap<>();
        attendu.put("toujours", 1);
        attendu.put("heuresDeclarees", 36.5);
        attendu.put("heuresSupplementaires", 4.17);
        attendu.put("anomalies", 2);
        attendu.put("anomaliesNonCorroborees", 1);
        attendu.put("segmentsExternes", 1);
        attendu.put("segmentsCorriges", 1);
        attendu.put("ecartPlanifieMaxMinutes", 75L);
        attendu.put("uniteCode", "EST");
        assertThat(f).containsExactlyEntriesOf(attendu);
        assertThat(DemandesFlux.contexteDeFeuille(new DemandesFlux.FeuilleSoumise("E", 60, 0, List.of(), List.of(), List.of())).keySet())
                .containsExactlyElementsOf(ObjetFlux.FEUILLE.champs().stream().map(FluxApprobation.ChampFlux::code).toList());
        Map<String, Object> p = DemandesFlux.contexteDePointage(new DemandesFlux.PointageEnAttente("EST", -12, true, true, false));
        assertThat(p).containsEntry("ecartDebutMinutes", 12L).containsEntry("saisieAPosteriori", 1).containsEntry("horsMilieu", 1).containsEntry("corrobore", 0);
        assertThat(p.keySet()).containsExactlyElementsOf(ObjetFlux.POINTAGE.champs().stream().map(FluxApprobation.ChampFlux::code).toList());
        assertThat(DemandesFlux.contexteDePointage(new DemandesFlux.PointageEnAttente("EST", 3, false, false, true)))
                .containsEntry("saisieAPosteriori", 0).containsEntry("horsMilieu", 0).containsEntry("corrobore", 1);
    }

    @Test
    @DisplayName("E-GP-3 : les faits du contexte MODIFICATION_HEURES, délai arrondi au centième et jamais négatif — le contrôle temporel est paramétrable (E-RF-10) ; le niveau de l'acteur")
    void modificationHeures() {
        ModificationHeures.Modification m = new ModificationHeures.Modification(OperationModification.CORRECTION, "2031-03-14T18:00:00.000Z",
                "2031-03-17T19:30:00.000Z", "PUBLIEE", true, "manager", NiveauActeur.GESTION, TempsEffectifs.Source.POINTAGE);
        Map<String, Object> attendu = new LinkedHashMap<>();
        attendu.put("operation", "CORRECTION");
        attendu.put("heuresDepuisLeQuart", 73.5);
        attendu.put("periodeEtat", "PUBLIEE");
        attendu.put("parAutrui", true);
        attendu.put("roleActeur", "manager");
        attendu.put("niveauActeur", "GESTION");
        attendu.put("sourceSegment", "POINTAGE");
        assertThat(ModificationHeures.faits(m)).containsExactlyEntriesOf(attendu);
        assertThat(ModificationHeures.faits(avec(m, "2031-03-14T18:20:00.000Z"))).containsEntry("heuresDepuisLeQuart", 0.33);
        assertThat(ModificationHeures.faits(avec(m, "2031-03-14T08:00:00.000Z"))).containsEntry("heuresDepuisLeQuart", 0L);
        assertThat(ModificationHeures.faits(avec(m, "2031-03-18T18:00:00.000Z"))).containsEntry("heuresDepuisLeQuart", 96L);
        assertThat(NiveauActeur.de(true, n -> false)).isEqualTo(NiveauActeur.ETABLISSEMENT);
        assertThat(NiveauActeur.de(false, n -> false)).isEqualTo(NiveauActeur.AUCUN);
        assertThat(NiveauActeur.de(false, Set.of(Perimetres.Niveau.LECTURE, Perimetres.Niveau.GESTION)::contains)).isEqualTo(NiveauActeur.GESTION);
        assertThat(NiveauActeur.de(false, Set.of(Perimetres.Niveau.LECTURE, Perimetres.Niveau.PLANIFICATION)::contains)).isEqualTo(NiveauActeur.PLANIFICATION);
        List<Regles.Motif> motifs = List.of(new Regles.Motif("r1", 1, "a", ActionRegle.AVERTIR, "attention"), new Regles.Motif("r2", 1, "b", ActionRegle.BLOQUER, "non"),
                new Regles.Motif("r3", 1, "c", ActionRegle.REFUSER, "jamais"));
        assertThat(ModificationHeures.messageDeRefus(motifs)).isEqualTo("modification des heures refusée : non ; jamais");
        assertThat(OperationModification.valueOf("SAISIE_A_POSTERIORI")).isEqualTo(OperationModification.SAISIE_A_POSTERIORI);
    }

    private static ModificationHeures.Modification avec(ModificationHeures.Modification m, String maintenant) {
        return new ModificationHeures.Modification(m.operation(), m.finDuTemps(), maintenant, m.periodeEtat(), m.parAutrui(), m.roleActeur(), m.niveauActeur(),
                m.sourceSegment());
    }
}
