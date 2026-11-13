package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port d'écriture des feuilles de temps (lot R9). Une correction désactive une version et en insère une autre : rien ne s'écrase. */
public interface EcritureFeuillesTempsPort {

    record NouvelleFeuille(String id, String ressourceId, String uniteCode, String debut, String fin, int paliersRequis) { }

    /** {@code sourceExterne} : la source tierce d'un segment EXTERNE (E-RF-13), nulle sinon. */
    record NouveauSegment(String placeId, String date, String debut, String fin, String typeHeureCode, TempsEffectifs.Source source, String sourceExterne) {
        public NouveauSegment(String placeId, String date, String debut, String fin, String typeHeureCode, TempsEffectifs.Source source) {
            this(placeId, date, debut, fin, typeHeureCode, source, null);
        }
    }

    /**
     * La place d'un segment dans la chaîne de ses versions ; {@code confirme} : la version est confirmée par qui l'écrit
     * (corriger un temps proposé par une source tierce, c'est l'avoir relu — E-RF-13).
     */
    record Filiation(String id, int version, String remplaceId, String motif, boolean confirme) {
        public Filiation(String id, int version, String remplaceId, String motif) {
            this(id, version, remplaceId, motif, false);
        }

        public static Filiation origine(String id) {
            return new Filiation(id, 1, null, null);
        }
    }

    record NouvelleDecision(String id, String feuilleId, int palier, TempsEffectifs.Decision decision, String motif) { }

    void enregistrerParametres(Contexte ctx, TempsEffectifs.Parametres p);

    void insererFeuille(Contexte ctx, NouvelleFeuille f);

    void insererSegment(Contexte ctx, String feuilleId, NouveauSegment s, Filiation f);

    void desactiverSegment(String tenantId, String id);

    /** Confirme un temps tiré d'un horodatage externe, au nom et à l'heure du contexte ; rien d'autre ne change. */
    void confirmerSegment(Contexte ctx, String id);

    /** Retire un segment ; le motif, s'il est donné, remplace celui de la version. */
    void retirerSegment(String tenantId, String id, String motif);

    /** Désactive les segments générés et pointés jamais corrigés ; rend leur nombre. */
    int desactiverGeneres(String tenantId, String feuilleId);

    void soumettre(Contexte ctx, String id, int paliersRequis, List<TempsEffectifs.Anomalie> instantane);

    void inscrireDecision(Contexte ctx, NouvelleDecision d);

    /** Pose le statut et le palier ; la date d'approbation est celle du contexte si la feuille est approuvée, nulle sinon. */
    void poserIssue(Contexte ctx, String id, TempsEffectifs.Issue issue);
}
