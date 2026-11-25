import { AIDE } from '../../composants/formulaire.jsx';
import { Adresse } from '../../composants/adresse.jsx';
import { Agenda } from '../Sections.jsx';
import { CLE_SLUG } from '../session.js';

/**
 * La vue Rendez-vous : l'agenda du jour, et quoi dire quand il est vide.
 *
 * <p>Elle enveloppe la section sans la modifier : l'agenda charge de son côté
 * et un échec ne masque jamais la file.
 *
 * <p>L'état vide est le cas courant d'une clinique neuve, et celui de la
 * clinique de démonstration, qui n'a aucun créneau ouvert : la page de
 * réservation s'y ouvre sur un agenda vide. Le message l'explique plutôt que
 * de laisser croire à une panne, et donne le lien à ouvrir pour le constater.
 */
export function VueRendezVous({ jeton }) {
  // Sans slug, pas de lien : `/rdv/null` serait cliquable et mort.
  const slug = typeof location === 'undefined' ? null : localStorage.getItem(CLE_SLUG);
  const lien = slug ? `${location.origin}/rdv/${slug}` : null;

  return (
    <>
      <p className={AIDE}>
        Les rendez-vous pris par vos clients pour aujourd'hui, par heure.
      </p>
      <Agenda jeton={jeton} titre={false} vide={(
        <div className={AIDE}>
          <p>Aucun rendez-vous aujourd'hui.</p>
          {lien && (
            <div className="mt-2">
              <p>Les clients réservent sur&nbsp;:</p>
              {/* Le lien existait et s'ouvrait bien ; ce qui manquait, c'est
                  de pouvoir l'EMPORTER — vers un SMS, un site, une affiche. */}
              <Adresse url={lien} href={lien} />
            </div>
          )}
          <p className="mt-2">
            Si cette page s'ouvre sans aucun créneau, c'est qu'aucun horaire
            n'est encore ouvert à la réservation pour votre clinique.
          </p>
        </div>
      )} />
    </>
  );
}
