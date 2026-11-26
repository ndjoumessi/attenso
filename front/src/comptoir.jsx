import { useEffect, useRef, useState } from 'react';
import { BandeLegale } from './composants/chrome.jsx';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import {
  ADRESSE_INACTIVE, adresseInactive,
} from '../../src/main/resources/static/app/vue-comptoir.js';
import {
  ANNEAU, PRESSE,
} from './composants/formulaire.jsx';

const GENERIQUE = 'Cette clinique';

/**
 * Prise de ticket au comptoir (EF-101 à EF-105).
 *
 * <p>Le premier rendu ne dépend d'AUCUNE donnée : c'est lui qui est
 * pré-rendu dans le HTML, et le client voit le formulaire avant que le
 * JavaScript soit arrivé. Le nom de la clinique se pose ensuite. Toute valeur
 * initiale venant du réseau ferait diverger l'hydratation.
 */
export function Comptoir() {
  // Chaîne vide et non null : le premier rendu, celui qui est pré-rendu,
  // doit produire EXACTEMENT le même texte des deux côtés. Un espace
  // insécable de remplissage suffisait à faire diverger l'hydratation.
  const [clinique, setClinique] = useState('');
  const [erreur, setErreur] = useState(null);
  const [envoi, setEnvoi] = useState(false);
  // Part de `false` : le premier rendu est pré-rendu et ne doit dépendre
  // d'aucune donnée réseau. Le pré-rendu montre donc le formulaire, et le
  // barrage se pose ensuite, comme le nom de la clinique.
  const [inactive, setInactive] = useState(false);
  // Le slug ne se connaît qu'à l'exécution, et le premier rendu est PRÉ-RENDU :
  // le poser directement dans un href ferait diverger l'hydratation, exactement
  // comme le nom de la clinique. Vide au départ, donc pas de lien au pré-rendu.
  const [slug, setSlug] = useState('');
  const champ = useRef(null);

  useEffect(() => {
    const slug = location.pathname.split('/').filter(Boolean)[1];
    setSlug(slug ?? '');
    // Le nom nomme le RESPONSABLE DE TRAITEMENT dans la mention d'information
    // (EC-04). S'il manque, la mention reste vraie mais générique : on
    // n'affiche jamais un faux nom.
    appel(`/t/${encodeURIComponent(slug)}/clinique`)
      .then(({ nom }) => {
        setClinique(nom);
        document.title = `${nom} — prendre son tour`;
      })
      // L'échec était avalé — `.catch(() => {})` — et la page retombait sur
      // « Cette clinique ». Pour un slug inconnu ou une clinique désactivée,
      // c'est un 404 : le formulaire restait actif, le client donnait son
      // numéro de mobile, et c'est l'envoi qui échouait. Une panne passagère,
      // elle, laisse le comptoir ouvert.
      .catch((echec) => setInactive(adresseInactive(echec)));
  }, []);

  async function soumettre(evenement) {
    evenement.preventDefault();
    const slug = location.pathname.split('/').filter(Boolean)[1];
    setEnvoi(true);
    setErreur(null);
    try {
      const ticket = await appel(`/t/${encodeURIComponent(slug)}/tickets`, {
        method: 'POST',
        body: JSON.stringify({ telephone: champ.current.value.trim() }),
      });
      // On REMPLACE l'entrée d'historique : le bouton « retour » ne doit pas
      // ramener sur un formulaire qui reprendrait un second ticket.
      location.replace(`/s/${ticket.reference}`);
    } catch (echec) {
      setErreur(echec.message);
      setEnvoi(false);
    }
  }

  const nom = clinique || GENERIQUE;

  return (
    <main className="mx-auto w-full max-w-md px-5 py-10">
      <p className="text-sm font-semibold uppercase tracking-wide text-sur-fond-doux">
        {clinique ?? ' '}
      </p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Prendre son tour</h1>
      {inactive ? (
        <div role="alert"
             className="mt-4 rounded-[var(--rayon)] border border-alerte
                        bg-alerte-voile px-4 py-3 text-alerte">
          {ADRESSE_INACTIVE}
        </div>
      ) : (
        <p className="mt-3 leading-relaxed text-sur-fond-doux">
          Laissez votre mobile : vous recevrez un message quand ce sera presque à
          vous, et vous pourrez suivre votre tour depuis votre téléphone.
        </p>
      )}

      <form onSubmit={soumettre} className="mt-8">
        <label htmlFor="telephone" className="block font-semibold">Votre mobile</label>
        <input
          ref={champ} id="telephone" name="telephone" type="tel" inputMode="tel"
          autoComplete="tel" placeholder="06 12 34 56 78" required
          disabled={inactive} aria-describedby="aide"
          className={`mt-2 w-full rounded-[var(--rayon)] border border-bordure-controle
                      bg-surface px-4 py-3 text-lg cible
                      ${ANNEAU}`}
        />
        <p id="aide" className="mt-2 text-sm leading-relaxed text-sur-fond-doux">
          Numéro utilisé uniquement pour vous prévenir aujourd'hui, puis effacé le
          lendemain. Pas de compte, pas de mot de passe.
        </p>

        {erreur && (
          <div role="alert"
               className="mt-4 rounded-[var(--rayon)] border border-alerte
                          bg-alerte-voile px-4 py-3 text-alerte">
            {erreur}
          </div>
        )}

        <button type="submit" disabled={envoi || inactive}
                className={`mt-6 w-full cible rounded-[var(--rayon)] bg-accent px-6
                           text-lg font-bold text-sur-accent shadow-1
                           [touch-action:manipulation] disabled:opacity-60
                           hover:brightness-110 active:brightness-95
                           ${ANNEAU} ${PRESSE}`}>
          {envoi ? 'Un instant…' : "J'y suis"}
        </button>

        {/* EC-04 : information du client AVANT la saisie, et non derrière un
            lien. Trois choses et trois seulement, parce qu'une mention que
            personne ne lit ne vaut pas mieux que pas de mention : qui est
            responsable, pourquoi, combien de temps. Le responsable est la
            CLINIQUE et non l'éditeur. */}
        <details className="mt-8 text-sm text-sur-fond-doux">
          <summary className="cible-min flex cursor-pointer items-center
                              font-semibold text-sur-fond">
            Vos données
          </summary>
          <p className="mt-2 leading-relaxed">
            <strong className="text-sur-fond">{nom}</strong> est responsable de ce
            traitement. Votre numéro de mobile sert uniquement à vous prévenir que
            votre tour approche, aujourd'hui, dans cette clinique. Il est{' '}
            <strong className="text-sur-fond">effacé le lendemain</strong> ; votre
            numéro de ticket est conservé sans votre numéro, pour le décompte des
            passages.
          </p>
          <p className="mt-2 leading-relaxed">
            Aucun compte n'est créé, aucune donnée de santé n'est collectée, et
            rien n'est utilisé à des fins commerciales. Pour y accéder, les
            corriger ou les faire effacer avant le lendemain, adressez-vous à
            l'accueil.
          </p>
        </details>
      </form>
      {/* LE SEUL CHEMIN PUBLIC vers la réservation. L'affiche du comptoir
          encode `/t/{slug}`, cette page-ci : avant, l'adresse `/rdv/{slug}`
          n'existait que dans le tableau de bord de la clinique, et seulement
          tant que son agenda du jour était vide. Aucun client ne pouvait donc
          l'atteindre.

          Secondaire, et délibérément : on est AU COMPTOIR, l'action du jour
          reste « J'y suis ». Le libellé dit « un autre jour » pour qu'on ne le
          confonde pas avec la prise de ticket. */}
      {slug && (
        <p className="mt-8 text-sm text-sur-fond-doux">
          <a href={`/rdv/${encodeURIComponent(slug)}`}
             className={`cible-min inline-flex items-center font-semibold
                         text-accent-texte underline underline-offset-2 ${ANNEAU}`}>
            Prendre rendez-vous pour un autre jour
          </a>
        </p>
      )}
      {/* Joignable depuis toute page, comme la coquille le promet. Cette
          page a pris — ou va prendre — un numéro de mobile ; n'offrir aucun
          chemin vers la politique de confidentialité serait la seule page du
          site à ne pas en avoir. */}
      <BandeLegale />
    </main>
  );
}
