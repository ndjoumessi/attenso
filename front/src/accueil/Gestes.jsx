import { useEffect, useState } from 'react';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
import {
  AIDE, ANNEAU, CHAMP, ETIQUETTE, Erreur,
} from '../composants/formulaire.jsx';
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle,
  DialogTrigger,
} from '@/ui/dialog';
import { GESTES } from './navigation.js';
import { entete } from './session.js';

/**
 * Les deux gestes de la file : urgence et client sans mobile.
 *
 * <p>La table est dans `navigation.js`, à côté des vues, parce que « geste ou
 * destination » est une seule décision. Ce fichier n'en est que le rendu : deux
 * boutons en tête de la vue file, chacun ouvrant son formulaire dans une boîte
 * de dialogue. C'étaient deux sections sous la liste, qu'il fallait faire
 * défiler sur une tablette — donc perdre la file de vue pour y revenir.
 *
 * <p>L'erreur est affichée DANS la boîte, et non remontée au bandeau de la
 * page : le bandeau vit en haut de l'écran, derrière la boîte, et refermer la
 * boîte pour le lire jetterait le numéro qu'on vient de taper. Un refus du
 * serveur — un mobile mal formé — se corrige là où il a été saisi.
 */
const DECLENCHEUR = 'cible inline-flex items-center justify-center gap-2 '
  + 'rounded-[var(--rayon)] border bg-surface px-5 font-bold shadow-1 '
  + '[touch-action:manipulation] disabled:opacity-50 '
  + 'transition-[filter,box-shadow,scale] duration-[var(--duree-courte)] '
  + 'ease-[var(--courbe-entree)] active:scale-[0.97] '
  + `hover:shadow-2 ${ANNEAU}`;

/**
 * Le ton de chaque geste, à sa clé.
 *
 * <p>L'urgence n'est pas une variante de l'ajout d'un client : elle fait passer
 * quelqu'un devant toute la salle. Elle porte donc l'alerte, du bouton de tête
 * jusqu'à sa validation, et jamais la forme du bouton principal.
 */
const TONS = {
  urgence: {
    declencheur: 'border-alerte text-alerte hover:bg-alerte-voile',
    validation: 'border-alerte bg-transparent text-alerte hover:bg-alerte-voile',
  },
  client: {
    declencheur: 'border-bordure-controle text-sur-fond hover:bg-surface-douce',
    validation: 'border-accent bg-accent text-sur-accent hover:brightness-110',
  },
};

export function Gestes({ jeton, horsReseau, surRafraichir }) {
  // `null` et non `[]` : trois états étaient confondus — pas encore chargé,
  // échec, et aucun motif configuré — et le menu de priorité disparaissait
  // sans un mot dans les deux premiers cas.
  const [motifs, setMotifs] = useState(null);
  const [echecMotifs, setEchecMotifs] = useState(false);

  useEffect(() => {
    // EF-407 : la liste vient du serveur, elle est paramétrée par la clinique.
    // Sans elle, le formulaire reste utilisable sans priorité : c'est le
    // ticket qui compte, pas le motif.
    appel('/api/motifs', { headers: entete(jeton) })
      .then((recus) => { setMotifs(recus); setEchecMotifs(false); })
      // EF-407 est une priorité PARAMÉTRÉE : une clinique peut n'en avoir
      // aucune, et c'est légitime. Un appel qui bronche, non — et les deux
      // produisaient le même écran.
      .catch(() => { setMotifs([]); setEchecMotifs(true); });
  }, [jeton]);

  return (
    <div className="mt-4">
      <div className="flex flex-wrap gap-3">
        {GESTES.map((geste) => (
          <Geste key={geste.cle} geste={geste} motifs={motifs ?? []}
                 echecMotifs={echecMotifs} jeton={jeton}
                 horsReseau={horsReseau} surRafraichir={surRafraichir} />
        ))}
      </div>
      {/* Hors réseau, les boîtes ne s'ouvrent pas : l'explication est donc
          rendue ICI et pas dedans, sinon elle ne serait jamais lue. Un bouton
          grisé sans un mot passe pour une panne de l'outil. */}
      {horsReseau && (
        <ul className={AIDE}>
          {GESTES.map((geste) => (
            <li key={geste.cle}>{geste.libelle} — {geste.aideHorsReseau}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Geste({ geste, motifs, echecMotifs = false, jeton, horsReseau,
    surRafraichir }) {
  const [ouverte, setOuverte] = useState(false);
  const [erreur, setErreur] = useState(null);
  const [envoi, setEnvoi] = useState(false);
  // Un geste sans ton déclaré prend le ton neutre : il doit s'afficher, pas
  // faire tomber l'écran de la journée. Même repli que `FORMES` dans File.jsx.
  const ton = TONS[geste.cle] ?? TONS.client;

  async function envoyer(evenement) {
    evenement.preventDefault();
    // Capturé AVANT l'attente : après un `await`, `currentTarget` est nul.
    const formulaire = evenement.currentTarget;
    const corps = { telephone: formulaire.telephone.value.trim() || null };
    if (geste.avecMotif) corps.motifId = formulaire.motif?.value || null;
    setEnvoi(true);
    setErreur(null);
    try {
      await appel(geste.route, {
        method: 'POST', headers: entete(jeton), body: JSON.stringify(corps),
      });
      formulaire.reset();
      setOuverte(false);
      surRafraichir();
    } catch (echec) {
      setErreur(echec.reseau ? geste.messageReseau : echec.message);
    }
    setEnvoi(false);
  }

  return (
    <Dialog open={ouverte}
            onOpenChange={(etat) => { setOuverte(etat); setErreur(null); }}>
      <DialogTrigger asChild>
        {/* Le libellé reste écrit : l'icône ne paraît jamais seule, ici comme
            dans la barre. */}
        <button type="button" disabled={horsReseau}
                className={`${DECLENCHEUR} ${ton.declencheur} ${ANNEAU}`}>
          <geste.icone aria-hidden="true" className="size-5" />
          {geste.libelle}
        </button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="text-xl font-bold tracking-tight">
            {geste.titre}
          </DialogTitle>
          <DialogDescription className="leading-relaxed">
            {geste.aide}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={envoyer} className="space-y-4">
          <div>
            <label className={ETIQUETTE} htmlFor={`tel-${geste.cle}`}>
              Mobile du propriétaire (facultatif)
            </label>
            <input id={`tel-${geste.cle}`} name="telephone" type="tel"
                   inputMode="tel" autoComplete="off"
                   placeholder="06 12 34 56 78" className={CHAMP} />
          </div>
          {/* Le bloc reste caché tant qu'aucun motif n'est actif — un menu à une
              seule entrée « Aucun » n'apprend rien à personne. */}
          {geste.avecMotif && echecMotifs && (
            <p role="status" className={AIDE}>
              Les motifs de priorité n'ont pas pu être chargés. Le ticket peut
              être créé sans priorité.
            </p>
          )}
          {geste.avecMotif && motifs.length > 0 && (
            <div>
              <label className={ETIQUETTE} htmlFor={`motif-${geste.cle}`}>
                Passage prioritaire
              </label>
              <select id={`motif-${geste.cle}`} name="motif" className={CHAMP}>
                <option value="">Aucun — file normale</option>
                {motifs.map((m) => <option key={m.id} value={m.id}>{m.libelle}</option>)}
              </select>
            </div>
          )}
          <Erreur>{erreur}</Erreur>
          {/* `horsReseau` bascule AUSSI pendant que la boîte est ouverte :
              `etatLiaison` est réévalué à chaque `rafraichir`, donc au moins
              toutes les trente secondes par le filet de sécurité. Sans cette
              garde, la validation resterait active alors que l'explication est
              rendue hors de la boîte, donc derrière le voile : l'ASV
              découvrirait le refus par l'échec du POST. */}
          <button type="submit" disabled={envoi || horsReseau}
                  className={`w-full cible rounded-[var(--rayon)] border px-6
                              text-lg font-bold shadow-1
                              [touch-action:manipulation] disabled:opacity-60
                              disabled:cursor-not-allowed
                              transition-[filter,box-shadow,scale]
                              duration-[var(--duree-courte)]
                              ease-[var(--courbe-entree)] active:scale-[0.97]
                              hover:shadow-2
                              ${ANNEAU}
                              ${ton.validation}`}>
            {envoi ? 'Un instant…' : geste.valider}
          </button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
