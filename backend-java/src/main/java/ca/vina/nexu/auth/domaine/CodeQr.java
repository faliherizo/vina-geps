package ca.vina.nexu.auth.domaine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Générateur de code QR (sans dépendance) — sortie SVG. Port fidèle du {@code qrcode.ts} du Node,
 * lui-même réduit du « qrcode-generator » de Kazuhiko Arase (MIT) au <strong>mode octet</strong> et
 * au niveau de correction <strong>M</strong>, avec sélection automatique de la version (1 à 6) et
 * du meilleur masque. Un octet près de l'original, c'est un code que Google Authenticator,
 * Microsoft Authenticator ou 1Password ne lit plus : chaque table est celle de l'ISO/IEC 18004.
 */
public final class CodeQr {

    private CodeQr() {
    }

    // ── GF(256) ────────────────────────────────────────────────────────────
    private static final int[] EXP = new int[256];
    private static final int[] LOG = new int[256];

    static {
        for (int i = 0; i < 8; i++) {
            EXP[i] = 1 << i;
        }
        for (int i = 8; i < 256; i++) {
            EXP[i] = EXP[i - 4] ^ EXP[i - 5] ^ EXP[i - 6] ^ EXP[i - 8];
        }
        for (int i = 0; i < 255; i++) {
            LOG[EXP[i]] = i;
        }
    }

    private static int glog(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("glog(" + n + ")");
        }
        return LOG[n];
    }

    private static int gexp(int n) {
        while (n < 0) {
            n += 255;
        }
        while (n >= 256) {
            n -= 255;
        }
        return EXP[n];
    }

    // ── Polynôme ───────────────────────────────────────────────────────────
    private record Polynome(int[] num) {
        static Polynome de(int[] brut, int decalage) {
            int offset = 0;
            while (offset < brut.length && brut[offset] == 0) {
                offset++;
            }
            int[] num = new int[brut.length - offset + decalage];
            System.arraycopy(brut, offset, num, 0, brut.length - offset);
            return new Polynome(num);
        }

        int longueur() {
            return num.length;
        }

        Polynome multiplier(Polynome e) {
            int[] r = new int[longueur() + e.longueur() - 1];
            for (int i = 0; i < longueur(); i++) {
                for (int j = 0; j < e.longueur(); j++) {
                    r[i + j] ^= gexp(glog(num[i]) + glog(e.num[j]));
                }
            }
            return de(r, 0);
        }

        Polynome modulo(Polynome e) {
            if (longueur() - e.longueur() < 0) {
                return this;
            }
            int ratio = glog(num[0]) - glog(e.num[0]);
            int[] r = num.clone();
            for (int i = 0; i < e.longueur(); i++) {
                r[i] ^= gexp(glog(e.num[i]) + ratio);
            }
            return de(r, 0).modulo(e);
        }
    }

    private static Polynome polynomeRs(int ecLength) {
        Polynome a = Polynome.de(new int[]{1}, 0);
        for (int i = 0; i < ecLength; i++) {
            a = a.multiplier(Polynome.de(new int[]{1, gexp(i)}, 0));
        }
        return a;
    }

    // ── Tables RS par version (niveau M) : {total, données} par bloc ───────
    private record Bloc(int total, int data) {
    }

    private static final Map<Integer, List<Bloc>> BLOCS_M = Map.of(
            1, List.of(new Bloc(26, 16)),
            2, List.of(new Bloc(44, 28)),
            3, List.of(new Bloc(70, 44)),
            4, List.of(new Bloc(50, 32), new Bloc(50, 32)),
            5, List.of(new Bloc(67, 43), new Bloc(67, 43)),
            6, List.of(new Bloc(43, 27), new Bloc(43, 27), new Bloc(43, 27), new Bloc(43, 27)));

    private static final Map<Integer, int[]> ALIGNEMENT = Map.of(1, new int[]{}, 2, new int[]{6, 18}, 3, new int[]{6, 22}, 4, new int[]{6, 26}, 5, new int[]{6, 30}, 6, new int[]{6, 34});

    // ── Tampon de bits ─────────────────────────────────────────────────────
    private static final class Bits {
        private final List<Integer> tampon = new ArrayList<>();
        private int longueur;

        void mettre(int num, int n) {
            for (int i = 0; i < n; i++) {
                mettreBit(((num >>> (n - i - 1)) & 1) == 1);
            }
        }

        void mettreBit(boolean bit) {
            int index = longueur / 8;
            if (tampon.size() <= index) {
                tampon.add(0);
            }
            if (bit) {
                tampon.set(index, tampon.get(index) | (0x80 >>> (longueur % 8)));
            }
            longueur++;
        }
    }

    private static final int PAD0 = 0xec;
    private static final int PAD1 = 0x11;

    private static int[] creerDonnees(int version, byte[] octets) {
        List<Bloc> blocs = BLOCS_M.get(version);
        Bits b = new Bits();
        b.mettre(4, 4);   // mode octet
        b.mettre(octets.length, 8);   // versions < 10 : compteur sur 8 bits
        for (byte o : octets) {
            b.mettre(o & 0xff, 8);
        }
        int totalBits = blocs.stream().mapToInt(Bloc::data).sum() * 8;
        if (b.longueur > totalBits) {
            throw new IllegalArgumentException("code length overflow (" + b.longueur + " > " + totalBits + ")");
        }
        if (b.longueur + 4 <= totalBits) {
            b.mettre(0, 4);
        }
        while (b.longueur % 8 != 0) {
            b.mettreBit(false);
        }
        while (b.longueur < totalBits) {
            b.mettre(PAD0, 8);
            if (b.longueur >= totalBits) {
                break;
            }
            b.mettre(PAD1, 8);
        }
        return creerOctets(b, blocs);
    }

    private static int[] creerOctets(Bits b, List<Bloc> blocs) {
        int offset = 0;
        int maxDc = 0;
        int maxEc = 0;
        List<int[]> dcdata = new ArrayList<>();
        List<int[]> ecdata = new ArrayList<>();
        for (Bloc bloc : blocs) {
            int dcCount = bloc.data();
            int ecCount = bloc.total() - bloc.data();
            maxDc = Math.max(maxDc, dcCount);
            maxEc = Math.max(maxEc, ecCount);
            int[] dc = new int[dcCount];
            for (int i = 0; i < dc.length; i++) {
                dc[i] = 0xff & b.tampon.get(i + offset);
            }
            offset += dcCount;
            Polynome rs = polynomeRs(ecCount);
            Polynome mod = Polynome.de(dc, rs.longueur() - 1).modulo(rs);
            int[] ec = new int[rs.longueur() - 1];
            for (int i = 0; i < ec.length; i++) {
                int index = i + mod.longueur() - ec.length;
                ec[i] = index >= 0 ? mod.num()[index] : 0;
            }
            dcdata.add(dc);
            ecdata.add(ec);
        }
        List<Integer> data = new ArrayList<>();
        for (int i = 0; i < maxDc; i++) {
            for (int[] dc : dcdata) {
                if (i < dc.length) {
                    data.add(dc[i]);
                }
            }
        }
        for (int i = 0; i < maxEc; i++) {
            for (int[] ec : ecdata) {
                if (i < ec.length) {
                    data.add(ec[i]);
                }
            }
        }
        return data.stream().mapToInt(Integer::intValue).toArray();
    }

    // ── Modules : null = libre, sinon sombre ou clair ──────────────────────
    private static void motifDeReperage(Boolean[][] m, int row, int col) {
        for (int r = -1; r <= 7; r++) {
            for (int c = -1; c <= 7; c++) {
                if (row + r < 0 || m.length <= row + r || col + c < 0 || m.length <= col + c) {
                    continue;
                }
                m[row + r][col + c] = (0 <= r && r <= 6 && (c == 0 || c == 6)) || (0 <= c && c <= 6 && (r == 0 || r == 6)) || (2 <= r && r <= 4 && 2 <= c && c <= 4);
            }
        }
    }

    private static void motifsDeSynchronisation(Boolean[][] m) {
        for (int i = 8; i < m.length - 8; i++) {
            if (m[i][6] == null) {
                m[i][6] = i % 2 == 0;
            }
            if (m[6][i] == null) {
                m[6][i] = i % 2 == 0;
            }
        }
    }

    private static void motifsDAlignement(Boolean[][] m, int version) {
        int[] pos = ALIGNEMENT.get(version);
        for (int r : pos) {
            for (int c : pos) {
                if (m[r][c] != null) {
                    continue;
                }
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        m[r + dr][c + dc] = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0);
                    }
                }
            }
        }
    }

    private static int chiffresBch(int x) {
        int digit = 0;
        while (x != 0) {
            digit++;
            x >>>= 1;
        }
        return digit;
    }

    /** BCH de l'information de format (15 bits) — niveau M = 0. */
    static int formatBch(int data) {
        int d = data << 10;
        int g15 = (1 << 10) | (1 << 8) | (1 << 5) | (1 << 4) | (1 << 2) | (1 << 1) | 1;
        while (chiffresBch(d) - chiffresBch(g15) >= 0) {
            d ^= g15 << (chiffresBch(d) - chiffresBch(g15));
        }
        return ((data << 10) | d) ^ 0b101010000010010;
    }

    private static void informationDeFormat(Boolean[][] m, int masque) {
        int bits = formatBch(masque);   // niveau M (=0) << 3 | masque
        int size = m.length;
        for (int i = 0; i < 15; i++) {
            boolean mod = ((bits >> i) & 1) == 1;
            if (i < 6) {
                m[i][8] = mod;
            } else if (i < 8) {
                m[i + 1][8] = mod;
            } else {
                m[size - 15 + i][8] = mod;
            }
            if (i < 8) {
                m[8][size - i - 1] = mod;
            } else if (i < 9) {
                m[8][15 - i] = mod;
            } else {
                m[8][15 - i - 1] = mod;
            }
        }
        m[size - 8][8] = true;   // module sombre
    }

    static boolean masque(int motif, int i, int j) {
        return switch (motif) {
            case 0 -> (i + j) % 2 == 0;
            case 1 -> i % 2 == 0;
            case 2 -> j % 3 == 0;
            case 3 -> (i + j) % 3 == 0;
            case 4 -> (i / 2 + j / 3) % 2 == 0;
            case 5 -> ((i * j) % 2) + ((i * j) % 3) == 0;
            case 6 -> (((i * j) % 2) + ((i * j) % 3)) % 2 == 0;
            case 7 -> (((i * j) % 3) + ((i + j) % 2)) % 2 == 0;
            default -> throw new IllegalArgumentException("bad mask:" + motif);
        };
    }

    private static void placerDonnees(Boolean[][] m, int[] data, int motif) {
        int inc = -1;
        int row = m.length - 1;
        int bitIndex = 7;
        int byteIndex = 0;
        for (int col = m.length - 1; col > 0; col -= 2) {
            if (col == 6) {
                col--;
            }
            while (true) {
                for (int c = 0; c < 2; c++) {
                    if (m[row][col - c] == null) {
                        boolean dark = byteIndex < data.length && ((data[byteIndex] >>> bitIndex) & 1) == 1;
                        if (masque(motif, row, col - c)) {
                            dark = !dark;
                        }
                        m[row][col - c] = dark;
                        bitIndex--;
                        if (bitIndex == -1) {
                            byteIndex++;
                            bitIndex = 7;
                        }
                    }
                }
                row += inc;
                if (row < 0 || m.length <= row) {
                    row -= inc;
                    inc = -inc;
                    break;
                }
            }
        }
    }

    private static Boolean[][] construire(int version, int[] data, int motif) {
        int size = version * 4 + 17;
        Boolean[][] m = new Boolean[size][size];
        motifDeReperage(m, 0, 0);
        motifDeReperage(m, size - 7, 0);
        motifDeReperage(m, 0, size - 7);
        motifsDeSynchronisation(m);
        motifsDAlignement(m, version);
        informationDeFormat(m, motif);
        placerDonnees(m, data, motif);
        return m;
    }

    /** Pénalité de masque (règles 1 et 2 du standard) : le masque le moins pénalisé est retenu. */
    static int penalite(Boolean[][] m) {
        int size = m.length;
        int penalty = 0;
        for (int r = 0; r < size; r++) {
            for (int dir = 0; dir < 2; dir++) {
                Boolean prev = null;
                int count = 0;
                for (int c = 0; c < size; c++) {
                    Boolean v = dir == 0 ? m[r][c] : m[c][r];
                    if (v.equals(prev)) {
                        count++;
                        if (count == 5) {
                            penalty += 3;
                        } else if (count > 5) {
                            penalty += 1;
                        }
                    } else {
                        prev = v;
                        count = 1;
                    }
                }
            }
        }
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                Boolean v = m[r][c];
                if (v.equals(m[r][c + 1]) && v.equals(m[r + 1][c]) && v.equals(m[r + 1][c + 1])) {
                    penalty += 3;
                }
            }
        }
        return penalty;
    }

    /** La plus grande version produite : 1 à 6 ont été validées par décodage. */
    private static final int VERSION_MAX = 6;

    /** Ce que la plus grande version contient en mode octet : indicateur de mode (4 bits) et longueur (8 bits) déduits. */
    public static int capaciteOctets() {
        return (BLOCS_M.get(VERSION_MAX).stream().mapToInt(Bloc::data).sum() * 8 - 4 - 8) / 8;
    }

    /** Limité aux versions 1 à 6 (validées par décodage) : les charges utiles otpauth compactes y tiennent. */
    static int version(int octets) {
        for (int v = 1; v <= VERSION_MAX; v++) {
            int capacite = BLOCS_M.get(v).stream().mapToInt(Bloc::data).sum() * 8;
            if (4 + 8 + octets * 8 <= capacite) {
                return v;
            }
        }
        throw new IllegalArgumentException("payload too long for QR (max version 6)");
    }

    /** La matrice (mode octet, niveau M) : vrai = module sombre. */
    public static boolean[][] matrice(String texte) {
        byte[] octets = texte.getBytes(StandardCharsets.UTF_8);
        int version = version(octets.length);
        int[] data = creerDonnees(version, octets);
        Boolean[][] meilleur = null;
        int meilleurePenalite = Integer.MAX_VALUE;
        for (int masque = 0; masque < 8; masque++) {
            Boolean[][] m = construire(version, data, masque);
            int p = penalite(m);
            if (p < meilleurePenalite) {
                meilleurePenalite = p;
                meilleur = m;
            }
        }
        boolean[][] grille = new boolean[meilleur.length][];
        for (int r = 0; r < meilleur.length; r++) {
            grille[r] = new boolean[meilleur.length];
            for (int c = 0; c < meilleur.length; c++) {
                grille[r][c] = Boolean.TRUE.equals(meilleur[r][c]);
            }
        }
        return grille;
    }

    /** Le SVG : {@code echelle} = taille d'un module (px), {@code marge} = zone de silence (en modules). */
    public static String svg(String texte, int echelle, int marge) {
        boolean[][] grille = matrice(texte);
        int n = grille.length;
        int dim = (n + marge * 2) * echelle;
        StringBuilder rects = new StringBuilder();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (grille[r][c]) {
                    rects.append("<rect x=\"").append((c + marge) * echelle).append("\" y=\"").append((r + marge) * echelle).append("\" width=\"").append(echelle).append("\" height=\"").append(echelle).append("\"/>");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + dim + "\" height=\"" + dim + "\" viewBox=\"0 0 " + dim + " " + dim + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code TOTP\">"
                + "<rect width=\"" + dim + "\" height=\"" + dim + "\" fill=\"#ffffff\"/><g fill=\"#000000\">" + rects + "</g></svg>";
    }

    /** Le nombre de modules sombres : ce qu'un essai compare à la référence Node sans porter tout le SVG. */
    public static int sombres(boolean[][] grille) {
        return (int) Arrays.stream(grille).flatMapToInt(l -> {
            int[] bits = new int[l.length];
            for (int i = 0; i < l.length; i++) {
                bits[i] = l[i] ? 1 : 0;
            }
            return Arrays.stream(bits);
        }).sum();
    }
}
