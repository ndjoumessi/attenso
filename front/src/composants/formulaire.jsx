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

/**
 * Le retour au pressé, et la SEULE source de ce geste.
 *
 * <p>Vingt-trois des vingt-six boutons écrits à la main n'avaient aucun état
 * pressé : leur seule réaction était `hover:`. Or Tailwind 4 enveloppe
 * lui-même tout `hover:` dans `@media (hover:hover)` — vérifié, 13 règles sur
 * 13 — donc sur la TABLETTE DU COMPTOIR, l'appareil où l'ASV passe sa journée,
 * ces boutons ne répondaient strictement rien à l'appui. On touche, et l'écran
 * ne confirme pas qu'il a entendu.
 *
 * <p>`scale` et non `translate-y-px` : le décalage d'un pixel que portaient les
 * trois boutons déjà pourvus est sous le seuil de perception. Un enfoncement de
 * 3 % se voit, et `scale` emporte le contenu du bouton avec lui.
 *
 * <p>La transition est incluse ici parce que les boutons visés n'en ont aucune.
 * Celui qui en déclare déjà une ne doit PAS poser `PRESSE` — deux utilitaires
 * `transition` sur la même balise se départagent par l'ordre de la feuille,
 * pas par celui des classes : il ajoute `transform` à sa propre liste et
 * `active:scale-[0.97]` à la main.
 *
 * <p>`prefers-reduced-motion` ramène la transition à 0,01 ms par la règle
 * globale : l'enfoncement devient instantané au lieu de disparaître. Le retour
 * reste, le mouvement part — ce qui est exactement la bonne dégradation.
 */
export const PRESSE = 'transition-transform duration-[var(--duree-courte)] '
  + 'ease-[var(--courbe-entree)] active:scale-[0.97]';

/**
 * Le bouton de second plan, et sa SEULE source.
 *
 * <p>Il était recopié à l'octet près dans `Mesure`, `Sections` et
 * `Exploitation`. Trois copies divergent ; celle-ci n'est qu'à un endroit.
 */
export const SECONDAIRE = `cible rounded-[var(--rayon)] border `
  + `border-bordure-controle bg-surface px-4 font-semibold `
  + `hover:bg-surface-douce disabled:opacity-50 ${PRESSE}`;

/**
 * `aria-busy` est posé ICI et non chez l'appelant : ce bouton est désactivé
 * pendant l'envoi, et c'est le seul endroit qui le sait pour tous ses usages.
 */
export function Principal({ children, disabled, ...reste }) {
  return (
    <button {...reste} disabled={disabled} aria-busy={disabled || undefined}
            className={`w-full cible rounded-[var(--rayon)] bg-accent px-6 text-lg
                       font-bold text-sur-accent shadow-1
                       [touch-action:manipulation] ${attenue(disabled)}
                       disabled:cursor-not-allowed
                       transition-[filter,box-shadow,scale]
                       duration-[var(--duree-courte)] ease-[var(--courbe-entree)]
                       hover:shadow-2 hover:brightness-110
                       active:scale-[0.97] active:shadow-1 active:brightness-95
                       focus-visible:outline-2 focus-visible:outline-offset-2
                       focus-visible:outline-[var(--anneau)]`}>
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

/**
 * L'atténuation d'un bouton désactivé — JAMAIS pendant un envoi.
 *
 * `opacity` compose l'élément ENTIER avec la page : fond et texte reçoivent
 * le même mélange. Mesuré sur le bouton principal en thème clair, le texte
 * passe de 7,64:1 à 2,97:1. Or `disabled` servait aussi d'état de chargement,
 * donc « Un instant… » devenait presque illisible au moment précis où il
 * demande d'attendre.
 *
 * WCAG 1.4.3 exempte les contrôles inactifs — mais un bouton qui envoie n'est
 * pas inactif, il travaille. On garde donc l'opacité pleine, et `aria-busy`
 * le dit à la voix.
 */
export const attenue = (envoi) => (envoi ? '' : 'disabled:opacity-60');

export function Info({ children }) {
  if (!children) return null;
  return (
    <div role="status"
         className="rounded-[var(--rayon)] border border-info bg-info-voile
                    px-4 py-3 leading-relaxed text-info-texte">{children}</div>
  );
}
