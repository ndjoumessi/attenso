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
 * <p>L'adresse de réservation est affichée EN PERMANENCE, et non dans l'état
 * vide : c'est le seul endroit du tableau de bord où la clinique la trouve,
 * et elle en a besoin le plus justement les jours où son agenda se remplit.
 *
 * <p>L'état vide, lui, est le cas courant d'une clinique neuve : le message
 * explique l'absence plutôt que de laisser croire à une panne.
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
      {/* HORS de l'état vide, et c'est tout le correctif. Ce lien vivait dans
          `vide` : il disparaissait donc dès le premier rendez-vous de la
          journée — c'est-à-dire exactement quand la clinique commence à s'en
          servir et à vouloir le communiquer. L'adresse qu'elle doit donner à
          ses clients ne peut pas s'effacer quand la fonctionnalité marche. */}
      {lien && (
        <div className={`${AIDE} mt-2`}>
          <p>Les clients réservent sur&nbsp;:</p>
          {/* Le lien existait et s'ouvrait bien ; ce qui manquait, c'est
              de pouvoir l'EMPORTER — vers un SMS, un site, une affiche. */}
          <Adresse url={lien} href={lien} />
        </div>
      )}
      <Agenda jeton={jeton} titre={false} vide={(
        <div className={AIDE}>
          <p>Aucun rendez-vous aujourd'hui.</p>
          {/* L'état vide NOMME l'action, sans reprendre l'adresse : elle est
              déjà affichée au-dessus, en permanence. Un état vide qui ne dit
              que l'absence se lit comme une panne — et le slug est posé à la
              connexion, retiré seulement par la déconnexion, donc le bloc
              au-dessus est bien là chaque fois que ce message s'affiche. */}
          <p className="mt-2">Les clients réservent sur l'adresse ci-dessus.</p>
          <p className="mt-2">
            Si cette page s'ouvre sans aucun créneau, c'est qu'aucun horaire
            n'est encore ouvert à la réservation pour votre clinique.
          </p>
        </div>
      )} />
    </>
  );
}
