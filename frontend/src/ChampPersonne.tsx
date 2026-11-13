/**
 * **Champ « personne »** — autocomplétion à partir de trois caractères.
 *
 * Il remplace les champs où il fallait connaître un identifiant interne (`emp-1788110390753-1`)
 * pour désigner quelqu'un. Personne ne connaît ces identifiants ; on les cherchait dans un autre
 * écran, on les recopiait, et une lettre de travers passait la validation sans que rien ne le
 * signale — le formulaire enregistrait alors une ligne rattachée à personne.
 *
 * **Le motif ARIA est celui du combobox**, pas un `datalist`. Trois raisons, dans cet ordre :
 * un `datalist` ne s'annonce pas de la même façon d'un lecteur d'écran à l'autre ; il n'affiche
 * qu'une chaîne, là où distinguer deux homonymes demande le titre d'emploi ; et il ne permet pas
 * de distinguer *ce qui est affiché* de *ce qui est envoyé*. Ici, le champ montre un nom et
 * transmet un identifiant.
 *
 * Ce que l'accessibilité impose et qu'on n'obtient pas gratuitement :
 * `aria-expanded` sur l'entrée, `aria-activedescendant` pointant l'option survolée au clavier,
 * `aria-selected` sur elle, un `role="status"` qui **annonce le nombre de résultats** — sans quoi
 * une personne qui n'a pas l'écran ignore que la liste s'est remplie —, et Échap qui referme
 * sans effacer.
 */
import { useCallback, useEffect, useId, useRef, useState } from "react";

export interface PersonneSuggeree {
  id: string; nom: string; titre: string | null; courriel: string | null; statut: string;
}

/** En deçà, on ne demande rien : trois lettres est le seuil, côté écran comme côté serveur. */
const SEUIL = 3;

/**
 * **D'où viennent les personnes proposées.**
 *
 * Le champ cherchait l'effectif, et lui seul. Déléguer ses approbations désigne un **compte**,
 * pas une fiche d'employé : deux populations voisines mais distinctes — un compte peut n'avoir
 * aucune fiche, une fiche peut n'avoir aucun compte. Plutôt qu'un second combobox, la source
 * devient un paramètre : le motif ARIA, le seuil, le clavier et les annonces restent écrits une
 * fois. Deux implémentations d'un même comportement finissent toujours par en avoir deux.
 */
export interface SourcePersonnes {
  /** Chemin de suggestion ; `?q=` y est ajouté, encodé. */
  suggerer: string;
  /** Chemin de résolution d'un identifiant déjà retenu (`:id`) ; absent, on garde l'identifiant. */
  resoudre?: string;
  /**
   * Convertit la réponse du serveur en suggestions, quand ce n'est pas déjà une personne.
   *
   * Une liste de référence — les cohortes, les catégories salariales — se cherche exactement de la
   * même façon : trois caractères, un combobox, un choix qui transmet un code et affiche un
   * libellé. Écrire un second composant pour ce comportement aurait fini par en donner deux, avec
   * un seul des deux accessible ; le doc d'en-tête le dit déjà pour les deux populations de
   * personnes. L'adaptation de forme suffit.
   */
  adapter?: (brut: unknown) => PersonneSuggeree[];
}

const SOURCE_EFFECTIF: SourcePersonnes = {
  suggerer: "/api/personnel/employes/suggestions",
  resoudre: "/api/personnel/employes/:id",
};

/** Le nom lisible d'une réponse de résolution, quelle que soit la forme servie. */
function nomLisible(donnees: unknown, repli: string): string {
  const d = (donnees ?? {}) as { nom?: unknown; firstName?: unknown; lastName?: unknown };
  if (typeof d.nom === "string" && d.nom.trim()) return d.nom.trim();
  const compose = `${typeof d.firstName === "string" ? d.firstName : ""} ${typeof d.lastName === "string" ? d.lastName : ""}`.trim();
  return compose || repli;
}

export default function ChampPersonne({
  valeur, onChoisir, appel, testId, requis, libelle, placeholder, multiple, source,
}: {
  /** L'identifiant retenu, ou une liste séparée par des virgules si `multiple`. */
  valeur: string;
  onChoisir: (identifiants: string) => void;
  /** L'appel authentifié de l'écran hôte — le champ n'invente pas sa propre session. */
  appel: (chemin: string, init?: RequestInit) => Promise<Response>;
  testId: string;
  requis?: boolean;
  libelle: string;
  placeholder?: string;
  /** Plusieurs personnes : chaque choix s'ajoute, et les retenues s'affichent en jetons. */
  multiple?: boolean;
  /** La population cherchée ; par défaut l'effectif. */
  source?: SourcePersonnes;
}) {
  const ou = source ?? SOURCE_EFFECTIF;
  const idListe = useId();
  const [saisie, setSaisie] = useState("");
  const [suggestions, setSuggestions] = useState<PersonneSuggeree[]>([]);
  const [ouvert, setOuvert] = useState(false);
  const [survol, setSurvol] = useState(-1);
  const [noms, setNoms] = useState<Record<string, string>>({});
  const boite = useRef<HTMLDivElement>(null);

  const retenus = valeur ? valeur.split(",").map((v) => v.trim()).filter(Boolean) : [];

  // Une recherche par frappe inonderait le serveur ; 200 ms est le délai au-delà duquel la
  // saisie paraît traîner, et en deçà duquel on requête pour rien.
  useEffect(() => {
    if (saisie.trim().length < SEUIL) { setSuggestions([]); setOuvert(false); return; }
    let annule = false;
    const minuteur = setTimeout(async () => {
      const r = await appel(`${ou.suggerer}?q=${encodeURIComponent(saisie.trim())}`);
      if (annule) return;
      const v = r.ok ? await r.json().catch(() => []) : [];
      const suggerees = ou.adapter ? ou.adapter(v) : (Array.isArray(v) ? (v as PersonneSuggeree[]) : []);
      setSuggestions(suggerees);
      setOuvert(true);
      setSurvol(-1);
    }, 200);
    return () => { annule = true; clearTimeout(minuteur); };
  }, [saisie, appel, ou.suggerer, ou.adapter]);

  /**
   * Retrouve le nom des identifiants déjà retenus.
   *
   * Sans cela, un formulaire rouvert en modification afficherait des identifiants bruts — soit
   * exactement le défaut qu'on corrige, réapparu par la porte de la relecture.
   */
  useEffect(() => {
    const inconnus = retenus.filter((id) => !noms[id]);
    if (inconnus.length === 0) return;
    let annule = false;
    void (async () => {
      const trouves: Record<string, string> = {};
      for (const id of inconnus) {
        // Sans chemin de résolution, l'identifiant se suffit : rien à relire, donc rien à demander.
        if (!ou.resoudre) { trouves[id] = id; continue; }
        const r = await appel(ou.resoudre.replace(":id", encodeURIComponent(id)));
        if (!r.ok) { trouves[id] = id; continue; }
        trouves[id] = nomLisible(await r.json().catch(() => null), id);
      }
      if (!annule) setNoms((n) => ({ ...n, ...trouves }));
    })();
    return () => { annule = true; };
  }, [valeur, appel]);   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const dehors = (e: MouseEvent) => {
      if (boite.current && !boite.current.contains(e.target as Node)) setOuvert(false);
    };
    document.addEventListener("mousedown", dehors);
    return () => document.removeEventListener("mousedown", dehors);
  }, []);

  const choisir = useCallback((p: PersonneSuggeree) => {
    const suivants = multiple ? [...new Set([...retenus, p.id])] : [p.id];
    setNoms((n) => ({ ...n, [p.id]: p.nom }));
    onChoisir(suivants.join(","));
    setSaisie("");
    setSuggestions([]);
    setOuvert(false);
    setSurvol(-1);
  }, [multiple, retenus, onChoisir]);

  const retirer = (id: string) => onChoisir(retenus.filter((x) => x !== id).join(","));

  const auClavier = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!ouvert || suggestions.length === 0) {
      if (e.key === "ArrowDown" && suggestions.length) setOuvert(true);
      return;
    }
    if (e.key === "ArrowDown") { e.preventDefault(); setSurvol((i) => (i + 1) % suggestions.length); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setSurvol((i) => (i - 1 + suggestions.length) % suggestions.length); }
    else if (e.key === "Enter" && survol >= 0) { e.preventDefault(); choisir(suggestions[survol]); }
    // Échap referme **sans effacer** : perdre sa saisie pour avoir voulu fermer une liste est
    // la façon la plus sûre de faire détester un champ.
    else if (e.key === "Escape") { setOuvert(false); setSurvol(-1); }
  };

  const trop_court = saisie.trim().length > 0 && saisie.trim().length < SEUIL;

  return (
    <div className="champ-personne" ref={boite}>
      <label className="field" htmlFor={`${idListe}-entree`}>
        <span>{libelle} {requis && <span className="req-star" aria-hidden="true">*</span>}</span>
        <input
          id={`${idListe}-entree`}
          data-testid={testId}
          role="combobox"
          aria-expanded={ouvert && suggestions.length > 0}
          aria-controls={`${idListe}-liste`}
          aria-autocomplete="list"
          aria-activedescendant={survol >= 0 ? `${idListe}-opt-${survol}` : undefined}
          autoComplete="off"
          value={saisie}
          placeholder={placeholder ?? "Trois lettres du nom, du titre ou du courriel"}
          onChange={(e) => setSaisie(e.target.value)}
          onKeyDown={auClavier}
          onFocus={() => { if (suggestions.length) setOuvert(true); }}
        />
      </label>

      {/* L'identifiant part au serveur ; il ne s'affiche jamais. */}
      <input type="hidden" name="employeeId" value={valeur} data-testid={`${testId}-valeur`} />

      {/*
        Le décompte est annoncé, pas seulement dessiné : sans ce `status`, une personne qui
        n'a pas l'écran ignore que la liste s'est remplie sous son curseur.
      */}
      <p className="sr-only" role="status" data-testid={`${testId}-annonce`}>
        {trop_court ? `Saisissez au moins ${SEUIL} caractères.`
          : ouvert ? `${suggestions.length} personne(s) proposée(s).` : ""}
      </p>

      {trop_court && (
        <p className="muted champ-personne-indice" data-testid={`${testId}-indice`}>
          Encore {SEUIL - saisie.trim().length} caractère(s).
        </p>
      )}

      {ouvert && suggestions.length > 0 && (
        <ul className="champ-personne-liste" role="listbox" id={`${idListe}-liste`}
            aria-label={`Suggestions pour ${libelle}`} data-testid={`${testId}-liste`}>
          {suggestions.map((p, i) => (
            <li key={p.id} id={`${idListe}-opt-${i}`} role="option" aria-selected={i === survol}
                className={i === survol ? "survol" : undefined}
                data-testid={`${testId}-option-${p.id}`}
                onMouseEnter={() => setSurvol(i)}
                onMouseDown={(e) => { e.preventDefault(); choisir(p); }}>
              <strong>{p.nom}</strong>
              <span className="muted">
                {p.titre ?? "poste non précisé"}
                {p.courriel ? ` · ${p.courriel}` : ""}
                {/* Une personne partie reste trouvable — on consigne encore à son dossier — mais
                    son état est écrit, jamais deviné à une nuance de gris (WCAG 1.4.1). */}
                {p.statut === "terminated" ? " · partie" : p.statut === "on_leave" ? " · en congé" : ""}
              </span>
            </li>
          ))}
        </ul>
      )}

      {ouvert && suggestions.length === 0 && saisie.trim().length >= SEUIL && (
        <p className="muted champ-personne-indice" data-testid={`${testId}-aucun`}>
          Aucune personne ne correspond à « {saisie.trim()} ».
        </p>
      )}

      {retenus.length > 0 && (
        <ul className="champ-personne-jetons" data-testid={`${testId}-retenus`}>
          {retenus.map((id) => (
            <li key={id} data-testid={`${testId}-retenu-${id}`}>
              {noms[id] ?? id}
              <button type="button" className="jeton-retirer"
                      data-testid={`${testId}-retirer-${id}`}
                      aria-label={`Retirer ${noms[id] ?? id}`}
                      onClick={() => retirer(id)}>×</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
