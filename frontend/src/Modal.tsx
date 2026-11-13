/**
 * Composants Modal réutilisables pour toute l'application.
 *
 * Remplace les `window.confirm()`, `window.alert()` et `window.prompt()` natifs
 * par une UI cohérente et testable (data-testid="modal", etc.).
 *
 * Exports :
 *  - <Modal> : conteneur générique (overlay + carte centrée)
 *  - <ConfirmModal> : confirmation oui/non avec variante danger
 *  - useConfirm() : hook impératif (await confirm("Êtes-vous sûr ?"))
 */
import { ReactNode, createContext, useCallback, useContext, useEffect, useState, useRef } from "react";

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  testId?: string;
  size?: "sm" | "md" | "lg";
  /**
   * `tiroir` : le dialogue s'ancre à droite (480 px ; plein écran sous 900 px) et laisse la
   * liste visible — c'est la fiche d'un élément (refonte visuelle, gabarit G2). `centre` : la
   * boîte centrée historique, pour une confirmation ou un formulaire court.
   */
  variante?: "centre" | "tiroir";
  /** Actions de la fiche, à droite du titre (menu `⋯`, boutons). */
  actions?: ReactNode;
}

const FOCUSABLES = 'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

let compteurTitres = 0;
/** Les dialogues ouverts, du plus ancien au plus récent : seul le dernier répond à Échap et piège le focus. */
const pile: number[] = [];

/**
 * Le dialogue : `role="dialog"` + `aria-modal`, **titré** (`aria-labelledby`), focus posé sur le
 * premier champ à l'ouverture, **piégé** dans la boîte (Tab et Maj+Tab bouclent), rendu à
 * l'élément qui l'a ouvert à la fermeture (WCAG 2.4.3, 2.1.2). L'arrière-plan n'est pas marqué
 * `aria-hidden` : un ancêtre masqué rendrait le dialogue lui-même invisible aux lecteurs d'écran.
 */
export function Modal({ open, onClose, title, children, testId = "modal", size = "md", variante = "centre", actions }: ModalProps) {
  const boite = useRef<HTMLDivElement>(null);
  const ouvreur = useRef<HTMLElement | null>(null);
  const [idTitre] = useState(() => `modal-titre-${++compteurTitres}`);

  // `onClose` change à chaque rendu chez la plupart des appelants : on le garde dans une
  // référence, sinon l'effet se réabonnerait — et rendrait le focus à l'ouvreur — à chaque frappe.
  const fermerRef = useRef(onClose);
  fermerRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const moi = ++compteurTitres;
    pile.push(moi);
    ouvreur.current = document.activeElement as HTMLElement | null;
    const premier = boite.current?.querySelector<HTMLElement>(FOCUSABLES);
    (premier ?? boite.current)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (pile[pile.length - 1] !== moi) return;   // un dialogue ouvert par-dessus (confirmation) prend la main
      if (e.key === "Escape") { e.stopPropagation(); fermerRef.current(); return; }
      if (e.key !== "Tab" || !boite.current) return;
      const focusables = Array.from(boite.current.querySelectorAll<HTMLElement>(FOCUSABLES)).filter((el) => el.offsetParent !== null);
      if (focusables.length === 0) { e.preventDefault(); boite.current.focus(); return; }
      const premierF = focusables[0], dernierF = focusables[focusables.length - 1];
      if (e.shiftKey && (document.activeElement === premierF || document.activeElement === boite.current)) { e.preventDefault(); dernierF.focus(); }
      else if (!e.shiftKey && document.activeElement === dernierF) { e.preventDefault(); premierF.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      pile.splice(pile.indexOf(moi), 1);
      ouvreur.current?.focus?.();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div
      className={variante === "tiroir" ? "modal-overlay modal-overlay-tiroir" : "modal-overlay"}
      data-testid={`${testId}-overlay`}
      /* Le voile n'est pas un contrôle : cliquer à côté du dialogue le ferme, comme Échap au clavier
         (géré plus haut) — `role="presentation"` le dit, sans masquer le dialogue qu'il contient. */
      role="presentation"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div ref={boite} className={variante === "tiroir" ? "modal tiroir" : `modal modal-${size}`} data-testid={testId} role="dialog" aria-modal="true" tabIndex={-1}
           aria-labelledby={title ? idTitre : undefined} aria-label={title ? undefined : "Dialogue"}>
        {title && (
          <div className="modal-header">
            <h3 id={idTitre}>{title}</h3>
            {actions && <div className="modal-entete-actions">{actions}</div>}
            <button
              type="button"
              className="modal-close"
              data-testid={`${testId}-close`}
              aria-label="Fermer"
              onClick={onClose}
            >
              ×
            </button>
          </div>
        )}
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

export interface FormulaireModalProps {
  /** Le texte du bouton d'ouverture (« Ajouter un établissement ») — et le titre du dialogue par défaut. */
  libelle: string;
  titre?: string;
  /** Préfixe des `data-testid` : `<testId>-ouvrir` (bouton), `<testId>-modal` (dialogue). */
  testId: string;
  /** Le formulaire ; en fonction, il reçoit `fermer` — à appeler après un enregistrement réussi. */
  children: ReactNode | ((fermer: () => void) => ReactNode);
  size?: "sm" | "md" | "lg";
  /** Contrôle externe (formulaire de modification ouvert depuis une ligne) : `ouvert` + `onFermer`, sans bouton. */
  ouvert?: boolean;
  onFermer?: () => void;
  /** Un bouton d'ouverture désactivé, avec la raison lue par le lecteur d'écran. */
  desactive?: boolean;
  raison?: string;
  icone?: string;
  className?: string;
}

/**
 * **Un formulaire d'ajout ou de modification vit dans un dialogue** : la page reste une liste
 * lisible, le formulaire s'ouvre d'un bouton et se ferme une fois enregistré. Le contenu (le
 * `<form>`) reste celui de l'appelant ; ce composant n'apporte que le bouton, le dialogue et le
 * focus. Appeler `fermer()` — passé par le contexte — après un enregistrement réussi.
 */
export function FormulaireModal({ libelle, titre, testId, children, size = "lg", ouvert, onFermer, desactive, raison, icone = "＋", className }: FormulaireModalProps) {
  const [interne, setInterne] = useState(false);
  const controle = ouvert !== undefined;
  const estOuvert = controle ? ouvert : interne;
  const fermer = useCallback(() => { if (controle) onFermer?.(); else setInterne(false); }, [controle, onFermer]);
  return (
    <>
      {!controle && (
        <button type="button" className={className ?? "primary"} data-testid={`${testId}-ouvrir`} onClick={() => setInterne(true)}
                disabled={desactive} aria-disabled={desactive || undefined} title={desactive ? raison : undefined}>
          <span aria-hidden="true">{icone} </span>{libelle}
        </button>
      )}
      {desactive && raison && !controle && <span className="sr-only" role="status">{raison}</span>}
      <FermetureContexte.Provider value={fermer}>
        <Modal open={estOuvert} onClose={fermer} title={titre ?? libelle} testId={`${testId}-modal`} size={size}>
          {estOuvert ? (typeof children === "function" ? children(fermer) : children) : null}
        </Modal>
      </FermetureContexte.Provider>
    </>
  );
}

const FermetureContexte = createContext<() => void>(() => undefined);
/** Dans un `FormulaireModal` : la fonction qui ferme le dialogue (à appeler après l'enregistrement). */
export function useFermerFormulaire(): () => void { return useContext(FermetureContexte); }

export interface ConfirmModalProps {
  open: boolean;
  title?: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "primary" | "danger";
  onConfirm: () => void | Promise<void>;
  onCancel: () => void;
  testId?: string;
}

export function ConfirmModal({
  open,
  title = "Confirmation",
  message,
  confirmLabel = "Confirmer",
  cancelLabel = "Annuler",
  variant = "primary",
  onConfirm,
  onCancel,
  testId = "confirm-modal",
}: ConfirmModalProps) {
  const [busy, setBusy] = useState(false);
  const handleConfirm = async () => {
    setBusy(true);
    try {
      await onConfirm();
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal open={open} onClose={onCancel} title={title} testId={testId} size="sm">
      <div className="modal-message" data-testid={`${testId}-message`}>{message}</div>
      <div className="modal-actions">
        <button className="secondary" data-testid={`${testId}-cancel`} onClick={onCancel} disabled={busy}>
          {cancelLabel}
        </button>
        <button
          className={variant === "danger" ? "danger-solid" : "primary"}
          data-testid={`${testId}-confirm`}
          onClick={handleConfirm}
          disabled={busy}
        >
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}

/**
 * Hook impératif : `const confirm = useConfirm();` puis
 * `if (await confirm({ message: "Sûr ?" })) { ... }`.
 *
 * Renvoie aussi le JSX <ConfirmHost /> à monter dans l'arbre.
 */
export function useConfirm() {
  const [state, setState] = useState<
    | (Omit<ConfirmModalProps, "onConfirm" | "onCancel" | "open"> & { resolver: (v: boolean) => void })
    | null
  >(null);
  const resolverRef = useRef<((v: boolean) => void) | null>(null);

  const confirm = useCallback(
    (opts: Omit<ConfirmModalProps, "onConfirm" | "onCancel" | "open">) =>
      new Promise<boolean>((resolve) => {
        resolverRef.current = resolve;
        setState({ ...opts, resolver: resolve });
      }),
    [],
  );

  const close = (result: boolean) => {
    resolverRef.current?.(result);
    resolverRef.current = null;
    setState(null);
  };

  const ConfirmHost = state ? (
    <ConfirmModal
      {...state}
      open={true}
      onConfirm={() => close(true)}
      onCancel={() => close(false)}
    />
  ) : null;

  return { confirm, ConfirmHost };
}
