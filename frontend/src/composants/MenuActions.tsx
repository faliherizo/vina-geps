/**
 * **Menu d'actions** (`⋯`) d'une ligne ou d'une fiche : les gestes secondaires, hors de la vue,
 * derrière un seul bouton étiqueté. Motif ARIA « menu button » : `aria-haspopup="menu"`,
 * `aria-expanded`, `role="menu"` et `role="menuitem"` ; flèches ↑ ↓ Début Fin, Échap ferme et
 * rend le focus au bouton ; un clic hors du menu le ferme. Chaque action a un libellé textuel.
 */
import { ReactNode, useEffect, useId, useRef, useState } from "react";
import Icone, { type NomIcone } from "./Icone";

export interface ActionMenu {
  id: string;
  libelle: ReactNode;
  onClick: () => void;
  icone?: NomIcone;
  /** Action destructrice : rendue en rouge **et** annoncée « — action irréversible ». */
  danger?: boolean;
  desactive?: boolean;
  raison?: string;
  testId?: string;
}

interface Props {
  actions: ReadonlyArray<ActionMenu>;
  /** Ce que le menu concerne (« Actions sur la facture PAY-001 ») : lu par le lecteur d'écran. */
  libelle: string;
  testId?: string;
  /** Bouton avec texte visible (« Actions ») plutôt que l'icône seule. */
  texte?: string;
  className?: string;
}

export default function MenuActions({ actions, libelle, testId, texte, className }: Props) {
  const [ouvert, setOuvert] = useState(false);
  const bouton = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const idMenu = useId();

  useEffect(() => {
    if (!ouvert) return;
    const items = () => Array.from(menu.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([disabled])') ?? []);
    items()[0]?.focus();
    const onKey = (e: KeyboardEvent) => {
      const liste = items();
      const i = liste.indexOf(document.activeElement as HTMLElement);
      if (e.key === "Escape") { e.preventDefault(); setOuvert(false); bouton.current?.focus(); }
      else if (e.key === "ArrowDown") { e.preventDefault(); liste[(i + 1) % liste.length]?.focus(); }
      else if (e.key === "ArrowUp") { e.preventDefault(); liste[(i - 1 + liste.length) % liste.length]?.focus(); }
      else if (e.key === "Home") { e.preventDefault(); liste[0]?.focus(); }
      else if (e.key === "End") { e.preventDefault(); liste[liste.length - 1]?.focus(); }
      else if (e.key === "Tab") { setOuvert(false); }
    };
    const onClic = (e: MouseEvent) => {
      if (!menu.current?.contains(e.target as Node) && !bouton.current?.contains(e.target as Node)) setOuvert(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onClic);
    return () => { document.removeEventListener("keydown", onKey); document.removeEventListener("mousedown", onClic); };
  }, [ouvert]);

  const choisir = (a: ActionMenu) => { setOuvert(false); bouton.current?.focus(); a.onClick(); };

  return (
    <div className={["menu-actions", className].filter(Boolean).join(" ")}>
      <button ref={bouton} type="button" className={texte ? "menu-actions-bouton" : "menu-actions-bouton menu-actions-icone"} aria-haspopup="menu" aria-expanded={ouvert}
              aria-controls={idMenu} aria-label={texte ? undefined : libelle} title={texte ? libelle : undefined} data-testid={testId} onClick={() => setOuvert((o) => !o)}>
        {texte ? <>{texte} <Icone nom="chevron-bas" /></> : <Icone nom="menu-actions" taille={18} />}
      </button>
      {ouvert && (
        <div ref={menu} id={idMenu} role="menu" aria-label={libelle} className="menu-actions-liste" data-testid={testId ? `${testId}-menu` : undefined}>
          {actions.map((a) => (
            <button key={a.id} type="button" role="menuitem" tabIndex={-1} className={a.danger ? "menu-actions-item menu-actions-danger" : "menu-actions-item"}
                    disabled={a.desactive} aria-disabled={a.desactive || undefined} title={a.desactive ? a.raison : undefined}
                    data-testid={a.testId} onClick={() => choisir(a)}>
              {a.icone && <Icone nom={a.icone} />}
              <span>{a.libelle}</span>
              {a.danger && <span className="sr-only"> — action irréversible</span>}
              {a.desactive && a.raison && <span className="sr-only"> — {a.raison}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
