import { useEffect, useState } from 'react';
import {
  CLASSES, LIBELLES, actionsPour, deplacementsPour,
} from '../../../src/main/resources/static/app/vue-file.js';
import { duree, minutes } from '../../../src/main/resources/static/app/formats.js';
import { PRESSE } from '../composants/formulaire.jsx';

// Les classes de LIGNE viennent du module de vue, qui est testé. On les
// traduit ici en teintes, et une seule fois : ajouter un état côté serveur
// sans l'habiller se verra, puisque CLASSES déclare toutes ses entrées.
const TEINTES = {
  '': 'border-bordure-douce',
  'ligne--appele': 'border-accent bg-accent-voile',
  'ligne--prevenu': 'border-attention',
  'ligne--absent': 'border-bordure-controle opacity-80',
  'ligne--clos': 'border-bordure-douce opacity-60',
};

const ETIQUETTES = {
  APPELE: 'border-accent bg-accent-voile text-accent-texte',
  PREVENU: 'border-attention bg-attention-voile text-attention',
  ABSENT: 'border-bordure-controle text-sur-fond-doux',
};

function Etiquette({ ton, titre, children }) {
  return (
    <span title={titre}
          className={`inline-flex items-center rounded-full border px-2.5 py-0.5
                      text-xs font-semibold
                      ${ton ?? 'border-bordure-douce text-sur-fond-doux'}`}>
      {children}
    </span>
  );
}

const FORMES = {
  'b-principal': 'bg-accent text-sur-accent border-accent hover:brightness-110',
  // « Parti » garde sa propre forme partout où il apparaît, et n'est jamais le
  // bouton principal : le confondre avec « absent » fausserait le compteur de
  // départs, qui est le seul chiffre justifiant l'abonnement.
  'b-parti': 'bg-transparent text-alerte border-alerte hover:bg-alerte-voile',
  '': 'bg-surface text-sur-fond border-bordure-controle hover:bg-surface-douce',
};

function Action({ forme, onClick, disabled, children }) {
  return (
    <button type="button" onClick={onClick} disabled={disabled}
            className={`cible rounded-[var(--rayon)] border px-4 font-semibold
                        [touch-action:manipulation] disabled:opacity-50
                        ${FORMES[forme] ?? FORMES['']} ${PRESSE}`}>
      {children}
    </button>
  );
}

function Ligne({ donnees, maintenant, horsReseau, surAction, surDeplacement }) {
  const urgence = donnees.priorite >= 10;
  return (
    <li className={`rounded-[var(--rayon-lg)] border bg-surface p-4 shadow-1
                    ${TEINTES[CLASSES[donnees.statut] ?? ''] ?? TEINTES['']}
                    ${urgence ? 'ring-1 ring-alerte' : ''}`}>
      <div className="flex items-start gap-4">
        <div className="shrink-0 text-center">
          <div className="text-4xl font-bold tabular-nums leading-none">
            {donnees.numeroJour}
          </div>
          <div className="mt-1 text-xs text-sur-fond-doux">
            {donnees.position > 0 ? `rang ${donnees.position}` : 'clos'}
          </div>
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-1.5">
            <Etiquette ton={ETIQUETTES[donnees.statut]}>
              {LIBELLES[donnees.statut] ?? donnees.statut}
            </Etiquette>
            {urgence && (
              <Etiquette ton="border-alerte bg-alerte-voile text-alerte">urgence</Etiquette>
            )}
            {/* Le motif est affiché tel qu'il a été copié sur le ticket : quand
                quelqu'un passe devant, l'ASV doit pouvoir dire pourquoi, même
                si la clinique a renommé le motif depuis. */}
            {!urgence && donnees.motifPriorite && (
              <Etiquette ton="border-attention bg-attention-voile text-attention">
                {donnees.motifPriorite}
              </Etiquette>
            )}
            {donnees.enAttenteEnvoi && (
              <Etiquette titre="Ce geste est enregistré sur la tablette et partira au retour du réseau.">
                pas encore envoyé
              </Etiquette>
            )}
            {/* EF-205 : quelqu'un a dit qu'il arrivait. Posé à côté de
                l'attente écoulée, c'est ce qui permet à l'ASV de patienter
                deux minutes plutôt que de marquer un absent. */}
            {donnees.signaleProcheLe && (
              <Etiquette ton="border-accent bg-accent-voile text-accent-texte">
                arrive
              </Etiquette>
            )}
            {!donnees.joignable && (
              <Etiquette titre="Pas de mobile : à appeler à la voix.">muet</Etiquette>
            )}
          </div>
          <p className="mt-2 text-sm text-sur-fond-doux tabular-nums">
            {duree(new Date(donnees.creeLe).getTime(), maintenant)}
            {donnees.attenteMinMinutes != null && (
              <> · {minutes(donnees.attenteMinMinutes, donnees.attenteMaxMinutes)}</>
            )}
          </p>
        </div>

        {/* gap-2 — 8 px, le minimum qu'Apple et Material demandent entre deux
            cibles voisines. Les deux flèches font l'inverse l'une de l'autre :
            trop près, on monte un ticket qu'on voulait descendre. */}
        <div className="flex shrink-0 flex-col gap-2">
          {deplacementsPour(donnees.statut).map(([route, titre, fleche]) => (
            <button key={route} type="button" title={titre} disabled={horsReseau}
                    onClick={() => surDeplacement(donnees.id, route)}
                    className={`cible-min w-11 rounded-[var(--rayon)] border
                               border-bordure-controle bg-surface font-bold
                               disabled:opacity-50 hover:bg-surface-douce ${PRESSE}`}>
              {fleche}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {actionsPour(donnees.statut).map(([route, libelle, forme]) => (
          <Action key={route} forme={forme}
                  onClick={() => surAction(donnees, route)}>{libelle}</Action>
        ))}
      </div>
    </li>
  );
}

export function File({ vue, horsReseau, surAction, surDeplacement }) {
  // Les durées avancent toutes seules : sans cette horloge, « 3 min »
  // resterait affiché un quart d'heure quand rien d'autre ne bouge.
  const [maintenant, setMaintenant] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setMaintenant(Date.now()), 20000);
    return () => clearInterval(t);
  }, []);

  if (!vue) {
    return <p className="mt-4 text-sur-fond-doux">Chargement du tableau…</p>;
  }
  if (vue.lignes.length === 0) {
    // La file vide dit AUSSI par où les tickets arrivent : c'est l'écran sur
    // lequel la journée commence, et un écran qui n'annonce que du vide se lit
    // comme une page cassée. Pas de « bouton ci-dessus » : la liste ne doit
    // rien savoir de ce qui est rendu autour d'elle.
    return (
      <p className="mt-4 leading-relaxed text-sur-fond-doux">
        Personne en salle pour le moment. Les clients prennent leur tour en
        scannant le QR code de l&apos;affiche du comptoir.
      </p>
    );
  }
  return (
    // React redessine par différence : le garde de signature qui existait
    // dans la version écrite à la main — et qui évitait de reconstruire la
    // liste toutes les trente secondes, ce qui reprenait le focus sous le
    // doigt de l'ASV — n'a plus d'objet. La clé par identifiant suffit.
    <ul aria-live="polite" aria-relevant="additions text" className="mt-4 space-y-3">
      {vue.lignes.map((donnees) => (
        <Ligne key={donnees.id} donnees={donnees} maintenant={maintenant}
               horsReseau={horsReseau} surAction={surAction}
               surDeplacement={surDeplacement} />
      ))}
    </ul>
  );
}
