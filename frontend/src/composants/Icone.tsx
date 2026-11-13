/**
 * **Icônes** — un jeu SVG monochrome (tracés Lucide, licence MIT), à la place des émojis dans
 * les titres, boutons et tuiles. Un émoji change de dessin selon le système, casse le contraste
 * et n'a pas de taille de cible fiable ; un tracé SVG hérite de la couleur du texte
 * (`currentColor`) et se mesure. Toujours décoratif (`aria-hidden`) : le sens est porté par le
 * texte qui l'accompagne, jamais par l'icône seule (WCAG 1.1.1, 1.4.1).
 */
export type NomIcone =
  | "plus" | "fermer" | "rechercher" | "coche" | "chevron-droite" | "chevron-bas" | "chevron-gauche"
  | "menu-actions" | "info" | "avertissement" | "erreur" | "succes" | "boite-vide" | "filtre"
  | "telecharger" | "televerser" | "rafraichir" | "accueil" | "hausse" | "baisse" | "fleche-gauche"
  | "fleche-droite" | "calendrier" | "personnes" | "document" | "modifier" | "supprimer" | "voir"
  | "aide" | "cloche" | "horloge" | "moins" | "banque" | "dossier" | "facture" | "projet" | "pieces" | "reglages"
  | "graphique" | "envoyer" | "cadenas" | "etoile" | "ecole" | "utilisateur" | "courriel" | "telephone" | "lien" | "sortie"
  | "globe" | "copier" | "signature" | "imprimer" | "bouclier" | "carte";

const TRACES: Record<NomIcone, string[]> = {
  plus: ["M5 12h14", "M12 5v14"],
  moins: ["M5 12h14"],
  fermer: ["M18 6 6 18", "m6 6 12 12"],
  rechercher: ["M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z", "m21 21-4.3-4.3"],
  coche: ["M20 6 9 17l-5-5"],
  "chevron-droite": ["m9 18 6-6-6-6"],
  "chevron-bas": ["m6 9 6 6 6-6"],
  "chevron-gauche": ["m15 18-6-6 6-6"],
  "menu-actions": ["M12 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2z", "M19 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2z", "M5 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2z"],
  info: ["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 16v-4", "M12 8h.01"],
  avertissement: ["m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01"],
  erreur: ["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 8v4", "M12 16h.01"],
  succes: ["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "m9 12 2 2 4-4"],
  "boite-vide": ["M22 12h-6l-2 3h-4l-2-3H2", "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"],
  filtre: ["M22 3H2l8 9.46V19l4 2v-8.54L22 3z"],
  telecharger: ["M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3"],
  televerser: ["M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m17 8-5-5-5 5", "M12 3v12"],
  rafraichir: ["M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5"],
  accueil: ["m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M9 22V12h6v10"],
  hausse: ["M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6"],
  baisse: ["M22 17 13.5 8.5 8.5 13.5 2 7", "M16 17h6v-6"],
  "fleche-gauche": ["m12 19-7-7 7-7", "M19 12H5"],
  "fleche-droite": ["M5 12h14", "m12 5 7 7-7 7"],
  calendrier: ["M3 6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M16 2v4", "M8 2v4", "M3 10h18"],
  personnes: ["M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75"],
  document: ["M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8", "M16 13H8", "M16 17H8"],
  modifier: ["M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4"],
  supprimer: ["M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6"],
  voir: ["M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"],
  aide: ["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01"],
  cloche: ["M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0"],
  horloge: ["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 6v6l4 2"],
  banque: ["M3 22h18", "M6 18v-7", "M10 18v-7", "M14 18v-7", "M18 18v-7", "M12 2 20 7H4z"],
  dossier: ["M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"],
  facture: ["M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z", "M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8", "M12 17.5v-11"],
  projet: ["M4 6h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"],
  pieces: ["M8 2a6 6 0 1 0 0 12A6 6 0 0 0 8 2z", "M18.09 10.37A6 6 0 1 1 10.34 18", "M7 6h1v4", "m16.71 13.88.7.71-2.82 2.82"],
  reglages: ["M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4"],
  graphique: ["M3 3v18h18", "M18 17V9", "M13 17V5", "M8 17v-3"],
  envoyer: ["m22 2-7 20-4-9-9-4Z", "M22 2 11 13"],
  cadenas: ["M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4"],
  etoile: ["M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"],
  ecole: ["M14 22v-4a2 2 0 1 0-4 0v4", "m18 10 4 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-8l4-2", "M18 5v17", "m4 6 8-4 8 4", "M6 5v17", "M12 7a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"],
  utilisateur: ["M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"],
  courriel: ["M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"],
  telephone: ["M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"],
  lien: ["M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"],
  sortie: ["M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9"],
  globe: ["M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20"],
  copier: ["M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"],
  signature: ["M12 20h9", "M16.376 3.622a1 1 0 0 1 3.002 3.002L7.368 18.635a2 2 0 0 1-.855.506l-2.872.838a.5.5 0 0 1-.62-.62l.838-2.872a2 2 0 0 1 .506-.854z"],
  imprimer: ["M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2", "M6 9V3h12v6", "M6 14h12v8H6z"],
  bouclier: ["M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"],
  carte: ["M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M2 10h20"],
};

interface Props {
  nom: NomIcone;
  /** Côté en pixels ; 16 dans un bouton ou un badge, 20 dans un titre, 40 dans un état vide. */
  taille?: number;
  className?: string;
}

export default function Icone({ nom, taille = 16, className }: Props) {
  return (
    <svg className={["icone", className].filter(Boolean).join(" ")} width={taille} height={taille} viewBox="0 0 24 24" fill="none"
         stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {TRACES[nom].map((d) => <path key={d} d={d} />)}
    </svg>
  );
}

export const NOMS_ICONES = Object.keys(TRACES) as NomIcone[];
