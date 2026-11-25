// Les primitives de formulaire, partagées par les écrans qui en portent un.
// Elles existent parce que les mêmes classes revenaient à l'identique sur
// cinq pages : un champ y change de bordure, et il doit changer partout.

// Au survol la bordure se renforce, au focus elle passe à l'accent : le champ
// actif se repère d'un coup d'œil, même sans l'anneau, sur une tablette vue de
// biais. Les deux restent au-dessus du 3:1 (bordure-controle l'est, accent aussi).
export const CHAMP = 'mt-2 w-full rounded-[var(--rayon)] border '
  + 'border-bordure-controle bg-surface px-4 py-3 cible '
  + 'transition-[border-color,box-shadow] duration-[var(--duree-courte)] '
  + 'hover:border-sur-fond-doux focus-visible:border-accent '
  + 'focus-visible:outline-2 focus-visible:outline-offset-2 '
  + 'focus-visible:outline-[var(--anneau)]';

export const ETIQUETTE = 'block font-semibold';

export const AIDE = 'mt-2 text-sm leading-relaxed text-sur-fond-doux';

export function Principal({ children, ...reste }) {
  return (
    <button {...reste}
            className="w-full cible rounded-[var(--rayon)] bg-accent px-6 text-lg
                       font-bold text-sur-accent shadow-1
                       [touch-action:manipulation] disabled:opacity-60
                       disabled:cursor-not-allowed
                       transition-[filter,box-shadow,transform]
                       duration-[var(--duree-courte)] ease-[var(--courbe-entree)]
                       hover:shadow-2 hover:brightness-110
                       active:translate-y-px active:shadow-1 active:brightness-95
                       focus-visible:outline-2 focus-visible:outline-offset-2
                       focus-visible:outline-[var(--anneau)]">
      {children}
    </button>
  );
}

export function Erreur({ children }) {
  if (!children) return null;
  return (
    <div role="alert"
         className="rounded-[var(--rayon)] border border-alerte bg-alerte-voile
                    px-4 py-3 leading-relaxed text-alerte">{children}</div>
  );
}

export function Info({ children }) {
  if (!children) return null;
  return (
    <div role="status"
         className="rounded-[var(--rayon)] border border-accent bg-accent-voile
                    px-4 py-3 leading-relaxed text-accent-texte">{children}</div>
  );
}
