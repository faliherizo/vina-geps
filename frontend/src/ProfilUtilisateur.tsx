/**
 * **L'identité de la personne connectée, à l'écran** (maquettes P04 et P32).
 *
 * La maquette porte, en haut à droite de chaque page, « Aide · 🔔 · Profil ▾ » et un portrait.
 * Le produit n'avait ni portrait ni menu : l'aide vivait dans un bouton isolé, et l'accès à son
 * propre compte passait par la navigation latérale, c'est-à-dire par le même chemin que les
 * écrans de travail — alors que « mon compte » n'en est pas un.
 *
 * Ce fichier porte les trois pièces partagées : l'avatar (photo ou initiales), le menu, et le
 * dépôt de la photo. Les mettre ensemble évite que l'en-tête et la page de compte divergent sur
 * ce qu'ils montrent de la même personne.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "./AuthContext";
import Avatar from "./composants/Avatar";

/** Les types que le serveur admet. Recopiés ici pour **filtrer le sélecteur de fichier**, pas pour décider. */
export const TYPES_PHOTO = ["image/png", "image/jpeg", "image/webp"];

/**
 * La photo du compte courant, et les deux gestes qui la changent.
 *
 * `null` = pas de photo ; `undefined` = on ne sait pas encore. La distinction compte : afficher
 * les initiales pendant le chargement fait clignoter l'en-tête à chaque navigation.
 */
export function useMaPhoto() {
  const { user, apiFetch } = useAuth();
  /**
   * **L'identifiant, jamais l'objet.**
   *
   * `user` est recréé à chaque rendu du contexte : le mettre en dépendance recrée `relire`, qui
   * relance l'effet, qui rend de nouveau — une boucle de requêtes qui fige la page sans rien
   * afficher d'anormal. C'est le défaut le plus coûteux de cette base de code, et il se reconnaît
   * à ceci : la page ne « plante » pas, elle n'arrive jamais.
   */
  const compteId = user?.id ?? null;
  const [photo, setPhoto] = useState<string | null | undefined>(undefined);

  const relire = useCallback(async () => {
    if (!compteId) { setPhoto(null); return; }
    try {
      const r = await apiFetch("/api/auth/me/photo");
      if (!r.ok) { setPhoto(null); return; }
      const j = await r.json();
      setPhoto(`data:${j.typeMime};base64,${j.contenu}`);
    } catch { setPhoto(null); }
  }, [compteId, apiFetch]);

  useEffect(() => { void relire(); }, [relire]);

  const poser = useCallback(async (fichier: File): Promise<string | null> => {
    const contenu = await new Promise<string>((resoudre, rejeter) => {
      const lecteur = new FileReader();
      lecteur.onload = () => resoudre(String(lecteur.result));
      lecteur.onerror = () => rejeter(new Error("lecture du fichier impossible"));
      lecteur.readAsDataURL(fichier);
    });
    const r = await apiFetch("/api/auth/me/photo", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ typeMime: fichier.type, contenu }),
    });
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      return Array.isArray(j.errors) ? j.errors.join(" · ") : "la photo n'a pas été enregistrée";
    }
    await relire();
    return null;
  }, [apiFetch, relire]);

  const retirer = useCallback(async () => {
    await apiFetch("/api/auth/me/photo", { method: "DELETE" });
    setPhoto(null);
  }, [apiFetch]);

  return { photo, poser, retirer, relire };
}

/**
 * **Le menu « Profil »** de l'en-tête (maquette P04).
 *
 * Un `<details>` plutôt qu'un menu maison : il s'ouvre au clavier sans une ligne de JavaScript,
 * annonce son état déplié, et se referme à `Échap` comme le reste du produit. Les menus
 * reconstruits à la main sont ceux qui piègent le focus (WCAG 2.1.2), et personne ne s'en
 * aperçoit avant un audit.
 */
export function MenuProfil(
  { nom, role, versProfil, versAide }:
  { nom: string; role: string; versProfil: () => void; versAide: () => void },
) {
  const { photo } = useMaPhoto();
  const [ouvert, setOuvert] = useState(false);
  const boite = useRef<HTMLDetailsElement>(null);

  // Refermer en cliquant ailleurs et à Échap : un menu qui reste ouvert recouvre le contenu, et
  // c'est exactement le défaut de superposition que les essais de géométrie traquent.
  useEffect(() => {
    if (!ouvert) return;
    const dehors = (e: MouseEvent) => {
      if (boite.current && !boite.current.contains(e.target as Node)) setOuvert(false);
    };
    const echap = (e: KeyboardEvent) => { if (e.key === "Escape") setOuvert(false); };
    document.addEventListener("mousedown", dehors);
    document.addEventListener("keydown", echap);
    return () => {
      document.removeEventListener("mousedown", dehors);
      document.removeEventListener("keydown", echap);
    };
  }, [ouvert]);

  return (
    <details className="menu-profil" ref={boite} open={ouvert} data-testid="menu-profil">
      <summary data-testid="menu-profil-bouton"
               aria-label={`Mon compte : ${nom} (${role})`}
               onClick={(e) => { e.preventDefault(); setOuvert(!ouvert); }}>
        <Avatar photo={photo} nom={nom} testId="menu-profil-avatar" />
        <span className="menu-profil-nom">{nom}</span>
        <span aria-hidden="true">▾</span>
      </summary>
      <ul className="menu-profil-liste">
        <li>
          <button type="button" data-testid="menu-profil-vers-compte"
                  onClick={() => { setOuvert(false); versProfil(); }}>
            Mon profil et mon compte
          </button>
        </li>
        <li>
          <button type="button" data-testid="menu-profil-vers-aide"
                  onClick={() => { setOuvert(false); versAide(); }}>
            Aide sur cet écran
          </button>
        </li>
      </ul>
    </details>
  );
}

/**
 * **Le dépôt de la photo**, pour la page de compte.
 *
 * La personne est seule à poser et à retirer la sienne : il n'existe aucune route
 * d'administration pour cela. Une photo imposée — ou retirée — par un tiers n'est plus un choix,
 * et le retrait doit rester à portée de celui qu'elle représente (Loi 25 : le consentement se
 * retire aussi facilement qu'il se donne).
 */
export function ChampPhotoProfil({ nom }: { nom: string }) {
  const { photo, poser, retirer } = useMaPhoto();
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");

  return (
    <section className="card" aria-labelledby="photo-profil-titre">
      <h3 id="photo-profil-titre">Photo de profil</h3>
      <p className="muted">
        Elle paraît dans l'en-tête et dans les écrans qui vous nomment. Elle est facultative, et
        vous pouvez la retirer à tout moment — elle est alors effacée, pas masquée.
      </p>

      <div className="photo-profil-rangee">
        <Avatar photo={photo} nom={nom} taille={96} testId="photo-profil-apercu" />
        <div>
          <label htmlFor="photo-profil-fichier">Choisir une image</label>
          <input id="photo-profil-fichier" type="file" data-testid="photo-profil-fichier"
                 accept={TYPES_PHOTO.join(",")}
                 aria-describedby="photo-profil-aide"
                 onChange={async (e) => {
                   const f = e.target.files?.[0];
                   if (!f) return;
                   setMessage(""); setErreur("");
                   const refus = await poser(f);
                   if (refus) setErreur(refus);
                   else setMessage("Photo de profil enregistrée.");
                   e.target.value = "";
                 }} />
          <p className="muted" id="photo-profil-aide">
            PNG, JPEG ou WebP, 512 Ko au plus. L'image est analysée avant d'être rangée.
          </p>
          {photo && (
            <button type="button" className="secondary" data-testid="photo-profil-retirer"
                    onClick={async () => {
                      await retirer();
                      setErreur(""); setMessage("Photo de profil retirée.");
                    }}>
              Retirer ma photo
            </button>
          )}
        </div>
      </div>

      <p role="status" data-testid="photo-profil-succes" hidden={!message}>{message}</p>
      <p role="alert" data-testid="photo-profil-erreur" hidden={!erreur}>{erreur}</p>
    </section>
  );
}
