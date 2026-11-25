import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Assemble des noms de classes, la dernière classe Tailwind en conflit
 * l'emportant : `cn('px-2', cond && 'px-4')` rend `px-4`.
 *
 * Réservé au tableau du personnel (front/src/ui/ et accueil/) : les pages des
 * clients restent sans dépendance, tenues par src/test/js/frontiere.test.js.
 */
export function cn(...entrees) {
  return twMerge(clsx(entrees));
}
