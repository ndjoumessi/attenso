import { useEffect, useState } from 'react';
import { appel, flux } from '../../src/main/resources/static/app/attenso.js';
import {
  appel as grandNombre, attente, legende, prochains, RIEN,
} from '../../src/main/resources/static/app/vue-ecran.js';

// Les fonctions de vue sont IMPORTÉES, pas réécrites. Elles ne touchent pas au
// DOM, leurs tests non plus, et ce sont elles qui décident ce que le mur
// annonce — « sont appelés » au pluriel, « consultation en cours » plutôt que
// « personne en salle » quand la file n'est pas vide. Les reproduire en JSX
// aurait fait deux vérités pour une seule règle.

const RAFRAICHISSEMENT = 20000;

export function Ecran({ jeton }) {
  const [vue, setVue] = useState(null);

  useEffect(() => {
    const route = `/api/ecran/jeton/${encodeURIComponent(jeton)}`;
    let vivant = true;
    const relire = async () => {
      try {
        const neuve = await appel(route);
        if (vivant) setVue(neuve);
      } catch {
        // L'écran garde son dernier affichage : un mur vide inquiète la salle
        // plus qu'un numéro de trente secondes.
      }
    };
    relire();
    const fermer = flux(`${route}/flux`, relire);
    const minuterie = setInterval(relire, RAFRAICHISSEMENT);
    return () => {
      vivant = false;
      clearInterval(minuterie);
      if (typeof fermer === 'function') fermer();
    };
  }, [jeton]);

  const suivants = vue ? prochains(vue) : '';
  const delai = vue ? attente(vue) : '';
  // Y a-t-il un numéro à montrer ? Deux cas disaient non et produisaient
  // pourtant la même barre blanche : la page en chargement, et la salle où
  // personne n'est appelé.
  const nombre = vue ? grandNombre(vue) : RIEN;
  const aUnNumero = nombre !== RIEN;

  return (
    <main aria-live="polite" aria-atomic="true"
          className="flex min-h-dvh flex-col items-center justify-center
                     gap-6 px-8 py-10 text-center">
      <p className="text-2xl font-semibold tracking-wide
                    text-[var(--mur-encre-douce)] sm:text-3xl">
        {vue ? vue.clinique : '—'}
      </p>

      {/* Le grand nombre. Dimensionné en vw pour rester lisible à quatre
          mètres quel que soit le téléviseur, et borné pour ne pas déborder
          quand deux salles appellent en même temps.
          RENDU SEULEMENT S'IL Y A UN NUMÉRO. Le cadratin de `RIEN` y était
          mesuré à 281,6 px en gras blanc : à cette taille un tiret n'est plus
          de la ponctuation, c'est une barre pleine au milieu d'un écran noir,
          et du fond de la salle ça se lit « écran cassé ». Aucune garde de
          texte ne pouvait le voir — le texte, lui, était juste. */}
      {aUnNumero && (
        <p className="text-[clamp(5rem,22vw,18rem)] font-bold leading-none
                      tracking-tighter tabular-nums">
          {nombre}
        </p>
      )}
      {/* Sans grand nombre, c'est la légende qui mène. `vue-ecran.js` garde sa
          décision — ne pas annoncer le vide quand des gens attendent —, mais
          il faut un point d'accroche à quatre mètres. */}
      <p className={aUnNumero
        ? 'text-3xl text-[var(--mur-encre-douce)] sm:text-4xl'
        : 'text-5xl font-semibold text-[var(--mur-encre-douce)] sm:text-6xl'}>
        {vue ? legende(vue) : 'en attente'}
      </p>

      {suivants && (
        <p className="mt-4 text-2xl text-[var(--mur-encre-douce)] sm:text-3xl">
          {suivants}
        </p>
      )}
      {delai && (
        <p className="text-xl text-[var(--mur-encre-douce)]">{delai}</p>
      )}
    </main>
  );
}
