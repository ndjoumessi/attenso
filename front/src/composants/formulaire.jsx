// Les primitives de formulaire, partagées par les écrans qui en portent un.
// Elles existent parce que les mêmes classes revenaient à l'identique sur
// cinq pages : un champ y change de bordure, et il doit changer partout.

export const CHAMP = 'mt-2 w-full rounded-[var(--rayon)] border '
  + 'border-bordure-controle bg-surface px-4 py-3 cible focus-visible:outline-2 '
  + 'focus-visible:outline-offset-2 focus-visible:outline-[var(--anneau)]';

export const ETIQUETTE = 'block font-semibold';

export const AIDE = 'mt-2 text-sm leading-relaxed text-sur-fond-doux';

export function Principal({ children, ...reste }) {
  return (
    <button {...reste}
            className="w-full cible rounded-[var(--rayon)] bg-accent px-6 text-lg
                       font-bold text-sur-accent shadow-1
                       [touch-action:manipulation] disabled:opacity-60
                       hover:brightness-110 active:brightness-95
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
                    px-4 py-3 text-alerte">{children}</div>
  );
}

export function Info({ children }) {
  if (!children) return null;
  return (
    <div role="status"
         className="rounded-[var(--rayon)] border border-accent bg-accent-voile
                    px-4 py-3 text-accent-texte">{children}</div>
  );
}
