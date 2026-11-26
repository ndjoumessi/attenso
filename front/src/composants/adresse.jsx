import { useState } from 'react';
import {
  ANNEAU, PRESSE,
} from './formulaire.jsx';

// Les adresses que la clinique doit TRANSMETTRE — le carnet d'un animal, la
// page de réservation — étaient posées en texte brut. Sur une tablette posée
// sur un comptoir, par quelqu'un qui tient un animal, sélectionner une URL de
// soixante caractères n'arrive pas. Le même raisonnement que les deux boutons
// de déplacement dans la file, plutôt qu'un glisser-déposer.

const DUREE_CONFIRMATION = 2000;

/**
 * Une adresse, et le geste pour l'emporter.
 *
 * <p>Le presse-papiers n'est PAS garanti : hors contexte sécurisé, et sur
 * certains navigateurs embarqués, `navigator.clipboard` est absent. Le bouton
 * dit alors que ça n'a pas marché plutôt que de prétendre le contraire — une
 * confirmation fausse est pire que pas de bouton, parce qu'on colle du vide
 * sans le savoir.
 */
export function Adresse({
  url, href = null, etiquette = "Copier l'adresse", nomAccessible = null,
}) {
  const [etat, setEtat] = useState(null);

  async function copier() {
    try {
      await navigator.clipboard.writeText(url);
      setEtat('copie');
    } catch {
      setEtat('echec');
    }
    setTimeout(() => setEtat(null), DUREE_CONFIRMATION);
  }

  return (
    <div className="mt-2 flex flex-wrap items-center gap-2">
      {/* `href` quand l'adresse vaut aussi la peine d'être OUVERTE — vérifier
          la page de réservation, par exemple. Nouvel onglet : elle est
          publique et n'a rien pour revenir au tableau en pleine
          démonstration. Sinon un simple `code`, parce qu'un lien qu'on ne
          peut qu'ouvrir sur soi-même n'apporte rien. */}
      {href ? (
        <a href={href} target="_blank" rel="noopener"
           className={`cible-min min-w-0 flex-1 inline-flex items-center break-all
                      rounded-[var(--rayon-sm)] bg-surface-douce px-2 py-1
                      font-mono text-sm text-accent-texte underline ${ANNEAU}`}>{url}</a>
      ) : (
        <code className="min-w-0 flex-1 break-all rounded-[var(--rayon-sm)]
                         bg-surface-douce px-2 py-1 font-mono text-sm
                         text-sur-fond-doux">{url}</code>
      )}
      {/* Le libellé visible reste court ; quand plusieurs adresses vivent dans
          la même liste, c'est `nomAccessible` qui dit LAQUELLE. Sans lui, la
          liste des carnets rendait autant de boutons « Copier l'adresse » que
          d'animaux, indiscernables pour qui énumère les éléments focalisables.
          Une adresse seule sur sa page n'en a pas besoin : son libellé suffit,
          et un nom constant répété une fois ne gêne personne. */}
      <button type="button" onClick={copier}
              aria-label={nomAccessible ?? undefined}
              className={`cible-min shrink-0 rounded-[var(--rayon)] border
                         border-bordure-controle px-3 text-sm font-semibold
                         [touch-action:manipulation] hover:bg-surface-douce
                         ${ANNEAU} ${PRESSE}`}>
        {etiquette}
      </button>
      {/* `role="status"` et non un simple texte : la confirmation est la seule
          preuve que le geste a abouti, et elle doit être annoncée. */}
      <p role="status" className="min-h-5 basis-full text-sm text-sur-fond-doux">
        {etat === 'copie' && 'Adresse copiée.'}
        {etat === 'echec' && "La copie n'a pas marché : sélectionnez l'adresse à la main."}
      </p>
    </div>
  );
}
