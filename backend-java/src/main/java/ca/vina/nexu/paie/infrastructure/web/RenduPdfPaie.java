package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.requetes.DocumentsPaie;
import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.EtatCycle;
import ca.vina.nexu.paie.domaine.Gain;
import ca.vina.nexu.paie.domaine.Retenues;
import ca.vina.nexu.partage.pdf.DocumentPdf;
import ca.vina.nexu.partage.pdf.DocumentPdf.Alignement;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * La mise en page des documents de la paie (ADR 0084), en PDF/A-2b par {@link DocumentPdf} : la fiche de paie d'une
 * personne, en portrait ; le registre d'un cycle, en paysage. Les montants sont au format québécois (1 234,56 $).
 */
public class RenduPdfPaie implements DocumentsPaie.Rendu {

    private static final float MARGE = 40;
    private static final float CORPS = 10;
    private static final float TITRE = 16;
    private static final float INTERLIGNE = 14;
    private static final float ESPACE = 8;
    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final String[] ENTETE_GAINS = {"Gain", "Quantité", "Taux", "Montant"};
    private static final float[] COLONNES_GAINS = {0, 0.5f, 0.66f, 0.82f};
    private static final String[] ENTETE_REGISTRE = {"Personne", "Matricule", "Heures", "Brut", "Retenues", "Net", "Cotisations", "Coût"};
    private static final float[] COLONNES_REGISTRE = {0, 0.24f, 0.36f, 0.45f, 0.55f, 0.65f, 0.75f, 0.87f};
    private static final float[] COLONNES_PAIRE = {0, 0.82f};
    /** L'écart entre deux colonnes, pour qu'un montant aligné à droite ne touche pas la colonne suivante. */
    private static final float GOUTTIERE = 4;

    @Override
    public byte[] fiche(DocumentsPaie.Fiche f) {
        Bulletin b = f.bulletin();
        Retenues r = b.retenues();
        try (DocumentPdf doc = new DocumentPdf(false, MARGE)) {
            Page p = new Page(doc, PDRectangle.A4.getHeight());
            doc.police(DocumentPdf.GRAS, TITRE).texte("Fiche de paie" + (f.estUnProjet() ? " — PROJET (cycle non traité)" : ""));
            doc.police(DocumentPdf.NORMAL, CORPS).texte(b.nom() + " — matricule " + b.matricule());
            doc.texte("Période du " + f.cycle().debut() + " au " + f.cycle().fin() + " — payée le " + b.datePaie()).sauter(ESPACE);
            p.tableau(ENTETE_GAINS, COLONNES_GAINS, b.gains().stream().map(RenduPdfPaie::ligneGain).toList());
            p.paire("Salaire brut", argent(r.brut()), true);
            doc.sauter(ESPACE);
            retenues(p, r);
            p.paire("Net à payer", argent(r.net()), true);
            doc.sauter(ESPACE);
            cumuls(p, f);
            employeur(p, r);
            return doc.octets();
        }
    }

    private static void retenues(Page p, Retenues r) {
        p.titre("Retenues");
        p.paire("Impôt fédéral", argent(r.impotFederal()), false);
        p.paire("Impôt du Québec", argent(r.impotQuebec()), false);
        p.paire("RRQ", argent(r.rrq()), false);
        p.paire("RRQ supplémentaire (RRQ2)", argent(r.rrq2()), false);
        p.paire("Assurance-emploi", argent(r.ae()), false);
        p.paire("RQAP", argent(r.rqap()), false);
        p.paire("Total des retenues", argent(r.totalRetenues()), true);
    }

    private static void cumuls(Page p, DocumentsPaie.Fiche f) {
        p.titre("Cumul de l'année " + f.bulletin().datePaie().substring(0, 4));
        p.paire("Brut", argent(f.cumulAnnuel().brut()), false);
        p.paire("RRQ et RRQ2", argent(f.cumulAnnuel().rrq().add(f.cumulAnnuel().rrq2())), false);
        p.paire("Assurance-emploi", argent(f.cumulAnnuel().ae()), false);
        p.paire("RQAP", argent(f.cumulAnnuel().rqap()), false);
    }

    private static void employeur(Page p, Retenues r) {
        Retenues.Employeur e = r.employeur();
        p.titre("Cotisations de l'employeur");
        p.paire("RRQ et RRQ2", argent(e.rrq().add(e.rrq2())), false);
        p.paire("Assurance-emploi", argent(e.ae()), false);
        p.paire("RQAP", argent(e.rqap()), false);
        p.paire("FSS, CNT et CNESST", argent(e.fss().add(e.cnt()).add(e.cnesst())), false);
        p.paire("Coût total pour l'employeur", argent(r.coutEmployeur()), true);
    }

    @Override
    public byte[] registre(DocumentsPaie.Registre registre) {
        try (DocumentPdf doc = new DocumentPdf(true, MARGE)) {
            Page p = new Page(doc, PDRectangle.A4.getWidth());
            String etat = registre.cycle().etat() == EtatCycle.TRAITE ? "" : " — PROJET (cycle non traité)";
            doc.police(DocumentPdf.GRAS, TITRE).texte("Registre de paie" + etat);
            doc.police(DocumentPdf.NORMAL, CORPS).texte("Période du " + registre.cycle().debut() + " au " + registre.cycle().fin()
                    + " — date de paie " + registre.cycle().datePaie() + " — " + registre.bulletins().size() + " bulletin(s)").sauter(ESPACE);
            List<String[]> lignes = registre.bulletins().stream().map(RenduPdfPaie::ligneRegistre).toList();
            p.tableau(ENTETE_REGISTRE, COLONNES_REGISTRE, lignes);
            p.tableau(new String[0], COLONNES_REGISTRE, List.<String[]>of(totaux(registre.bulletins())));
            return doc.octets();
        }
    }

    private static String[] ligneGain(Gain g) {
        String taux = switch (g.nature()) {
            case PRIME_POURCENTAGE -> nombre(g.taux().multiply(CENT)) + " %";
            case HEURES_REGULIERES, HEURES_SUPPLEMENTAIRES, SALAIRE, PRIME_HORAIRE, FORFAIT -> argent(g.taux());
        };
        String quantite = g.nature() == Gain.Nature.SALAIRE || g.nature() == Gain.Nature.FORFAIT ? "1" : nombre(g.quantite()) + " h";
        return new String[] {g.libelle() + " (" + g.code() + ")", quantite, taux, argent(g.montant())};
    }

    private static String[] ligneRegistre(Bulletin b) {
        Retenues r = b.retenues();
        return new String[] {b.nom(), b.matricule(), nombre(b.heuresRegulieres().add(b.heuresSupplementaires())), argent(r.brut()),
                argent(r.totalRetenues()), argent(r.net()), argent(r.employeur().total()), argent(r.coutEmployeur())};
    }

    private static String[] totaux(List<Bulletin> bulletins) {
        BigDecimal brut = somme(bulletins, b -> b.retenues().brut());
        BigDecimal retenues = somme(bulletins, b -> b.retenues().totalRetenues());
        BigDecimal net = somme(bulletins, b -> b.retenues().net());
        BigDecimal cotisations = somme(bulletins, b -> b.retenues().employeur().total());
        BigDecimal heures = somme(bulletins, b -> b.heuresRegulieres().add(b.heuresSupplementaires()));
        return new String[] {"Total", "", nombre(heures), argent(brut), argent(retenues), argent(net), argent(cotisations), argent(brut.add(cotisations))};
    }

    private static BigDecimal somme(List<Bulletin> bulletins, Function<Bulletin, BigDecimal> champ) {
        return bulletins.stream().map(champ).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 1 234,56 $ — l'espace de groupement est une espace simple : la police incorporée l'a, quelle que soit la JVM. */
    static String argent(BigDecimal v) {
        return format("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP)) + " $";
    }

    static String nombre(BigDecimal v) {
        return format("#,##0.##").format(v);
    }

    private static DecimalFormat format(String motif) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.CANADA_FRENCH);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        return new DecimalFormat(motif, s);
    }

    /** Le curseur d'une page : des tableaux à colonnes et des paires libellé-montant, qui passent à la page suivante au besoin. */
    private record Page(DocumentPdf doc, float hauteur) {

        void titre(String texte) {
            doc.police(DocumentPdf.GRAS, CORPS).texte(texte);
            doc.police(DocumentPdf.NORMAL, CORPS);
        }

        void paire(String libelle, String montant, boolean gras) {
            ligne(new String[] {libelle, montant}, COLONNES_PAIRE, gras);
        }

        void tableau(String[] entete, float[] colonnes, List<String[]> lignes) {
            if (entete.length > 0) {
                ligne(entete, colonnes, true);
            }
            lignes.forEach(l -> ligne(l, colonnes, false));
            doc.sauter(ESPACE);
        }

        /** Une rangée au curseur : la première colonne à gauche, les autres alignées à droite, comme des montants. */
        private void ligne(String[] cellules, float[] colonnes, boolean gras) {
            if (doc.y() - INTERLIGNE < MARGE) {
                doc.sauter(INTERLIGNE);
            }
            doc.police(gras ? DocumentPdf.GRAS : DocumentPdf.NORMAL, CORPS);
            float largeur = doc.largeurUtile();
            float depuisLeHaut = hauteur - doc.y();
            for (int i = 0; i < cellules.length; i++) {
                float debut = colonnes[i] * largeur;
                float fin = i + 1 < colonnes.length ? colonnes[i + 1] * largeur : largeur;
                doc.texteA(MARGE + debut, depuisLeHaut, fin - debut - GOUTTIERE, cellules[i], i == 0 ? Alignement.GAUCHE : Alignement.DROITE);
            }
            doc.police(DocumentPdf.NORMAL, CORPS).sauter(INTERLIGNE);
        }
    }
}
