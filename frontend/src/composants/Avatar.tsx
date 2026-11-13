/**
 * **Le visage d'une personne** — sa photo si elle en a posé une, ses initiales sinon.
 *
 * Trois décisions, et chacune vient d'un défaut qu'on voit sur les produits qui ne les prennent
 * pas :
 *
 * **Jamais de silhouette grise.** C'est la seule chose qui ne renseigne personne : deux
 * silhouettes côte à côte se ressemblent. Les initiales, elles, disent de qui il s'agit même
 * quand la photo manque — et elles manquent le plus souvent, parce que poser sa photo est
 * facultatif et le restera (Loi 25 : le consentement se retire).
 *
 * **La couleur ne porte rien** (WCAG 1.4.1). La teinte des initiales est tirée du nom pour que
 * la même personne garde la même pastille d'un écran à l'autre — c'est une aide à la
 * reconnaissance, pas une information : six teintes pour un annuaire entier, deux collègues
 * partagent forcément la leur. Chacune est mesurée à ≥ 5,4:1 avec le texte blanc.
 *
 * **L'image est décorative par défaut.** Le nom est presque toujours écrit à côté ; un `alt` qui
 * le répète fait lire « Marie Tremblay Marie Tremblay ». Quand l'avatar est **seul** — une liste
 * de participants réduite aux visages —, `seul` le fait porter le nom.
 */
import { useState } from "react";

interface Props {
  /**
   * Une photo **déjà en main** — la sienne, chargée par `useMaPhoto`. Elle l'emporte sur
   * `personId` : inutile de redemander au serveur ce que l'écran tient déjà.
   */
  photo?: string | null;
  /** Le compte dont on cherche la photo. Absent, on s'en tient aux initiales. */
  personId?: string | null;
  /** Le nom à afficher et à abréger. Jamais un identifiant : « 3f2a… » n'a pas d'initiales. */
  nom: string;
  /** Diamètre en pixels. 40 par défaut — la taille des listes de la maquette P11. */
  taille?: number;
  /** L'avatar tient seul, sans son nom à côté : il doit alors être annoncé. */
  seul?: boolean;
  testId?: string;
}

/**
 * Six teintes, chacune mesurée avec du texte blanc :
 * `#0a66b5` 5,86:1 · `#0b6e69` 6,09:1 · `#6b3fa0` 7,38:1 · `#a3366b` 6,37:1 ·
 * `#8a5200` 6,39:1 · `#2f6f3e` 6,06:1.
 */
const TEINTES = ["#0a66b5", "#0b6e69", "#6b3fa0", "#a3366b", "#8a5200", "#2f6f3e"];

/**
 * Deux lettres : « Marie Tremblay » → « MT », « Ana » → « AN ».
 *
 * Un seul mot donne ses deux premières lettres, et non une seule : une pastille à une lettre se
 * confond avec toutes celles de la même initiale, ce qui est précisément ce qu'un avatar doit
 * éviter. Les séparateurs couvrent le point et le souligné parce qu'un nom de compte s'écrit
 * souvent `prenom.nom` ou `prenom_nom`.
 */
export function initialesDe(nom: string | null | undefined): string {
  const mots = String(nom ?? "").trim().split(/[\s._-]+/).filter(Boolean);
  if (mots.length === 0) return "?";
  const lettres = mots.length === 1 ? mots[0].slice(0, 2) : `${mots[0][0]}${mots[mots.length - 1][0]}`;
  return lettres.toLocaleUpperCase("fr");
}

/**
 * Une teinte stable pour un nom donné.
 *
 * Somme des codes de caractères plutôt qu'un hachage : la propriété recherchée est la
 * **stabilité**, pas la dispersion — un nom doit retrouver sa pastille au rechargement, et c'est
 * tout ce qu'on lui demande.
 */
export function teinteDe(nom: string): string {
  let somme = 0;
  for (const c of nom) somme = (somme + c.codePointAt(0)!) % 4096;
  return TEINTES[somme % TEINTES.length];
}

export default function Avatar({ photo, personId, nom, taille = 40, seul = false, testId }: Props) {
  // Une photo absente répond 404 : l'image bascule alors sur les initiales, sans secousse et
  // sans appel supplémentaire. C'est pour cela que le serveur ne sert aucune image de
  // remplacement — il ne saurait pas laquelle.
  const [sansPhoto, setSansPhoto] = useState(false);
  const source = photo || (personId ? `/api/auth/personnes/${encodeURIComponent(String(personId))}/photo` : null);
  const afficherPhoto = Boolean(source) && !sansPhoto;
  const style = { width: taille, height: taille, fontSize: Math.round(taille * 0.4) };

  if (afficherPhoto) {
    return (
      <img
        className="avatar avatar-photo"
        style={style}
        src={source!}
        alt={seul ? nom : ""}
        aria-hidden={seul ? undefined : true}
        data-testid={testId}
        onError={() => setSansPhoto(true)}
      />
    );
  }

  return (
    <span
      className="avatar avatar-initiales"
      style={{ ...style, background: teinteDe(nom) }}
      role={seul ? "img" : undefined}
      aria-label={seul ? nom : undefined}
      aria-hidden={seul ? undefined : true}
      data-testid={testId}
    >
      {initialesDe(nom)}
    </span>
  );
}
