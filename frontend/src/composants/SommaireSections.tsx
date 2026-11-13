/**
 * **Sommaire de sections** (gabarit G5 réglages) — une page de paramètres reste une seule page
 * dont toutes les sections sont visibles ; le sommaire, en tête, mène à chacune par une ancre
 * et pose le focus sur son titre (WCAG 2.4.3). Pas d'onglets ici : un réglage se lit d'un
 * coup d'œil, et un formulaire à moitié rempli ne disparaît pas derrière un autre onglet.
 */
import Icone, { type NomIcone } from "./Icone";

export interface SectionSommaire {
  id: string;
  libelle: string;
  icone?: NomIcone;
  testId?: string;
}

interface Props {
  sections: ReadonlyArray<SectionSommaire>;
  libelle?: string;
  testId?: string;
}

export function allerALaSection(id: string): void {
  const cible = document.getElementById(id);
  if (!cible) return;
  cible.scrollIntoView({ block: "start" });
  const titre = cible.matches("h1, h2, h3") ? cible : cible.querySelector<HTMLElement>("h1, h2, h3");
  const focalisable = titre ?? cible;
  if (!focalisable.hasAttribute("tabindex")) focalisable.setAttribute("tabindex", "-1");
  focalisable.focus({ preventScroll: true });
}

export default function SommaireSections({ sections, libelle = "Sections de la page", testId }: Props) {
  return (
    <nav className="sommaire-ecran" aria-label={libelle} data-testid={testId}>
      <ul>
        {sections.map((s) => (
          <li key={s.id}>
            <a href={`#${s.id}`} data-testid={s.testId} onClick={(e) => { e.preventDefault(); allerALaSection(s.id); }}>
              {s.icone && <Icone nom={s.icone} />} {s.libelle}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
