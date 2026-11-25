import { AIDE } from '../../composants/formulaire.jsx';

/**
 * L'état affiché quand le serveur refuse une zone réservée au gérant.
 *
 * <p>La barre est peinte entière : un compte accueil PEUT ouvrir ces vues. La
 * page ne le sait pas d'avance — elle ne lit pas le rôle dans le jeton, un
 * contrôle qui vit dans la page se contourne — c'est le refus qui l'apprend,
 * et qui vient ici plutôt que s'afficher en erreur brute.
 *
 * @param quoi ce qui est réservé, au féminin ou au masculin, sans majuscule
 *        initiale ni point : « la gestion des comptes de la clinique »
 */
export function ReserveAuGerant({ quoi }) {
  return (
    <div role="status"
         className="mt-4 rounded-[var(--rayon)] border border-bordure-douce
                    bg-surface px-4 py-3">
      <p className="font-semibold">Réservé au gérant</p>
      <p className={AIDE}>
        Cette partie est réservée au gérant de la clinique : {quoi}. Votre
        compte n'y a pas accès. Si vous en avez besoin, demandez-le au gérant.
      </p>
    </div>
  );
}
