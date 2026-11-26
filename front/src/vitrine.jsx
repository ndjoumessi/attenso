import { ApercuFile } from './composants/apercu-file.jsx';
import { BandeLegale } from './composants/chrome.jsx';
import { Marque } from './composants/marque.jsx';
import { Appel, Bande, Colonne, Fleche, Mesure, RYTHME } from './composants/base.jsx';

const ONDE = 'M3 12h4l3-8 4 16 3-8h4';
const BULLE = 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z';
const COURBE = 'M3 3v18h18';

function Icone({ children, chemin }) {
  return (
    <span aria-hidden="true"
          className="inline-flex size-11 shrink-0 items-center justify-center
                     rounded-[var(--rayon)] bg-accent-voile text-accent-texte">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
        {chemin.map((d) => <path key={d} d={d} />)}
      </svg>
      {children}
    </span>
  );
}

function Entete() {
  return (
    // Le lien de connexion vit ICI, et pas seulement au pied de page. Il y
    // était rangé entre les mentions légales et les CGV, sous le nom « Accès
    // clinique » : personne ne cherche à se connecter dans un pied de page
    // juridique, et un client s'est retrouvé devant une vitrine sans porte.
    <header className="sticky top-0 z-20 border-b border-bordure-douce/70
                       bg-fond/85 backdrop-blur-md">
      <Colonne className="flex h-16 items-center justify-between gap-4">
        <span className="inline-flex items-center gap-2 text-xl font-medium tracking-tight">
          <Marque className="text-accent-texte" />
          Attenso
        </span>
        <Appel href="/connexion" variante="discret" taille="petit">Se connecter</Appel>
      </Colonne>
    </header>
  );
}

function Heros() {
  // Le héros n'est pas une `Bande` — il n'a pas de titre de section — mais il
  // respire au MÊME rythme, depuis la même source.
  //
  // Il n'avait d'abord que le haut, par crainte que les deux rembourrages
  // n'ouvrent « 208 px là où le reste de la page en tient 192 ». Les 208
  // venaient de l'ancien `py-28` de Tailwind, soit 112 px : une valeur que le
  // passage aux jetons avait déjà remplacée. Avec `--pas-24`, la somme fait
  // 96 + 96 = 192, c'est-à-dire exactement le rythme. Mesuré : le creux était
  // de 96 px, moitié moins que toutes les autres jointures.
  return (
    <section className={`relative ${RYTHME}`}>
      <Colonne className="relative">
        <p className="mb-4 text-sm text-sur-fond-doux">
          File d'attente virtuelle pour cliniques vétérinaires
        </p>
        {/* Resserré SUR MOBILE SEULEMENT, et typographiquement : la conception
            demandait un titre « plus court à l'œil », pas un texte réécrit.
            Les deux ne sont pas la même chose, et c'est en les confondant que
            la première version n'avait resserré que de 8 px de marge. */}
        <h1 className="text-[2rem] font-medium leading-[1.05] tracking-[-0.02em]
                       sm:text-6xl sm:leading-[1.1]">
          Vos clients attendent où ils veulent.
          <span className="block text-sur-fond-doux">Pas entassés dans votre salle.</span>
        </h1>
        <Mesure className="mt-6">
          <p className="text-xl leading-relaxed text-sur-fond-doux">
            Ils scannent un QR code en arrivant, vont attendre dans leur voiture, et
            reçoivent un SMS quand c'est bientôt à eux. Votre accueil garde la main
            sur l'ordre de passage.
          </p>
        </Mesure>
        <div className="mt-8 flex flex-wrap gap-3">
          <Appel href="/inscription">Inscrire ma clinique <Fleche /></Appel>
          <Appel href="#comment" variante="discret">Voir comment ça marche</Appel>
        </div>
        <p className="mt-4 text-sm text-sur-fond-doux">
          Sans installation, sans matériel. Résiliable à tout moment.
        </p>
        {/* L'écran que l'accueil aura sous les doigts. Il vient APRÈS les
            appels à l'action, et non entre le titre et eux : sur un téléphone,
            s'intercaler les repousserait sous la ligne de flottaison et
            aggraverait ce qu'on vient corriger. */}
        <div className="mt-10 sm:mt-12">
          <ApercuFile />
        </div>
      </Colonne>
    </section>
  );
}

function Cout() {
  return (
    <Bande aria="titre-probleme">
      <h2 id="titre-probleme" className="text-3xl font-medium tracking-tight sm:text-4xl">
        Ce que l'attente vous coûte
      </h2>
      <div className="mt-8 overflow-hidden rounded-[var(--rayon-lg)] border
                      border-bordure-douce bg-surface shadow-1">
        <div className="flex flex-col gap-6 p-8 sm:flex-row sm:items-center sm:p-10">
          <p className="text-6xl font-medium tracking-tight text-alerte sm:text-7xl">400 €</p>
          <p className="max-w-sm text-lg leading-relaxed text-sur-fond-doux">
            perdus chaque mois pour deux clients par semaine qui repartent
            sans être vus.
          </p>
        </div>
        <p className="border-t border-bordure-douce bg-surface-douce px-8 py-6
                      text-sur-fond-doux sm:px-10">
          Personne ne les compte aujourd'hui : ils partent sans rien dire. Attenso
          les compte, et c'est le premier chiffre que vous verrez sur votre tableau.
        </p>
      </div>
    </Bande>
  );
}

const BENEFICES = [
  {
    titre: 'Une salle calme', chemin: [ONDE],
    texte: 'Moins de monde assis, moins de chiens qui se croisent, moins de '
      + 'tension au comptoir. Ceux qui préfèrent rester le peuvent toujours.',
  },
  {
    titre: 'Un SMS, au bon moment', chemin: [BULLE],
    texte: "« C'est presque à vous, merci de revenir. » Envoyé au nom de votre "
      + 'clinique, pas au nôtre. Deux messages par visite, pas davantage.',
  },
  {
    titre: 'Les départs, comptés', chemin: [COURBE, 'M7 15l4-5 3 3 5-7'],
    texte: 'Un bouton « parti sans être vu » distinct de « absent ». À la fin du '
      + "mois, vous savez ce que l'attente vous a réellement coûté.",
  },
];

function Benefices() {
  return (
    <Bande aria="titre-benefices" className="bg-surface-douce">
      <h2 id="titre-benefices" className="text-3xl font-medium tracking-tight sm:text-4xl">
        Ce que ça change
      </h2>
      <ul className="mt-10 grid gap-6 sm:grid-cols-3">
        {BENEFICES.map((b) => (
          <li key={b.titre}
              className="rounded-[var(--rayon-lg)] border border-bordure-douce
                         bg-surface p-6 shadow-1">
            <Icone chemin={b.chemin} />
            <h3 className="mt-4 text-lg font-medium">{b.titre}</h3>
            <p className="mt-2 leading-relaxed text-sur-fond-doux">{b.texte}</p>
          </li>
        ))}
      </ul>
    </Bande>
  );
}

const ETAPES = [
  ['Le client arrive et scanne',
    'Un QR code imprimé sur votre comptoir. Il laisse son mobile, reçoit son '
    + 'numéro. Pas de compte, pas de mot de passe.'],
  ['Il attend où il veut',
    "Dans sa voiture, dehors, ou dans la salle s'il préfère. Une page lui "
    + 'montre son rang, qui se met à jour toute seule.'],
  ['Votre accueil fait avancer la file',
    'Une tablette, de gros boutons. Une urgence passe devant en un geste, et '
    + 'les personnes déjà prévenues en sont informées.'],
  ["L'écran et la voix appellent",
    'Un numéro en grand sur un écran de la salle. Et si le réseau tombe, la '
    + 'tablette continue de fonctionner.'],
];

function Comment() {
  return (
    <Bande id="comment" aria="titre-comment">
      <h2 id="titre-comment" className="text-3xl font-medium tracking-tight sm:text-4xl">
        Une journée avec Attenso
      </h2>
      <ol className="mt-10 space-y-0">
        {ETAPES.map(([titre, texte], i) => (
          <li key={titre} className="relative flex gap-5 pb-10 last:pb-0">
            {/* Le filet qui relie les étapes : il dit « ceci est une suite »
                mieux qu'une liste numérotée seule, et s'arrête à la dernière. */}
            {i < ETAPES.length - 1 && (
              <span aria-hidden="true"
                    className="absolute left-5 top-11 -ml-px h-[calc(100%-2.75rem)]
                               w-0.5 bg-bordure-douce" />
            )}
            <span aria-hidden="true"
                  className="relative z-10 flex size-10 shrink-0 items-center
                             justify-center rounded-full bg-accent text-sur-accent
                             font-medium">
              {i + 1}
            </span>
            <div className="pt-1">
              <h3 className="text-lg font-medium">{titre}</h3>
              <Mesure><p className="mt-1 leading-relaxed text-sur-fond-doux">{texte}</p></Mesure>
            </div>
          </li>
        ))}
      </ol>
    </Bande>
  );
}

function Etat() {
  return (
    <Bande aria="titre-etat" className="bg-bande-contraste text-sur-bande-contraste">
      <h2 id="titre-etat" className="text-3xl font-medium tracking-tight sm:text-4xl">
        Où en est Attenso
      </h2>
      <Mesure className="mt-6 space-y-4 text-sur-bande-contraste-doux">
        <p className="leading-relaxed">
          Le produit est complet et se vend à l'abonnement. Il est jeune : vous
          ne verrez sur cette page ni logo de client ni témoignage, parce qu'il
          n'y en a pas encore à citer et que les inventer serait fabriquer des
          preuves.
        </p>
        <p className="leading-relaxed">
          Ce que vous pouvez vérifier vous-même, en revanche : l'inscription
          prend trois minutes, le premier mois se résilie en un clic, et le
          tableau compte dès le premier jour les clients repartis sans être vus
          — le chiffre qui dit si l'outil vous sert.
        </p>
      </Mesure>
    </Bande>
  );
}

// Noms, prix et volumes repris du §13, à la lettre. Écrits en dur — une page
// d'accueil n'a pas à attendre une API pour trois nombres qui changent une
// fois l'an — et c'est PrixAfficheTest qui les garde alignés sur l'énumération
// Palier. Il mord sur les trois à la fois.
//
// La mise en avant est sur Comptoir, et non sur la formule du milieu comme le
// veut l'usage : à aucun volume Clinique n'est la moins chère (Comptoir plus
// dépassement la bat partout). Mettre en avant la formule du milieu serait
// vendre contre l'intérêt du client.
const TARIFS = [
  { nom: 'Comptoir', prix: '69 €', volume: "Jusqu'à 300 visites par mois", avant: true },
  { nom: 'Clinique', prix: '149 €', volume: "Jusqu'à 800 visites par mois" },
  { nom: 'Centre', prix: '239 €', volume: "Jusqu'à 1 500 visites par mois" },
];

function Tarifs() {
  return (
    <Bande aria="titre-tarifs">
      <h2 id="titre-tarifs" className="text-3xl font-medium tracking-tight sm:text-4xl">
        Ensuite, selon votre volume
      </h2>
      <ul className="mt-10 grid gap-6 sm:grid-cols-3">
        {TARIFS.map((t) => (
          <li key={t.nom}
              className={`relative rounded-[var(--rayon-lg)] border bg-surface p-6
                ${t.avant
                  ? 'border-accent shadow-2 ring-1 ring-accent'
                  : 'border-bordure-douce shadow-1'}`}>
            {t.avant && (
              <span className="absolute -top-3 left-6 rounded-full bg-accent px-3 py-1
                               text-xs font-medium text-sur-accent">
                Le plus avantageux
              </span>
            )}
            <p className="text-sm font-medium text-sur-fond-doux">{t.nom}</p>
            <p className="mt-2 text-4xl font-medium tracking-tight">
              {t.prix}<small className="text-base font-normal text-sur-fond-doux"> / mois</small>
            </p>
            <p className="mt-2 text-sur-fond-doux">{t.volume}</p>
          </li>
        ))}
      </ul>
      <p className="mt-6 text-sm text-sur-fond-doux">
        Au-delà du volume inclus, chaque visite supplémentaire est facturée 0,15 €.
      </p>
      <Mesure className="mt-4">
        <p className="text-sm leading-relaxed text-sur-fond-doux">
          Au volume, pas au nombre de vétérinaires : une clinique à trois
          praticiens qui voit peu de monde ne paie pas pour ses confrères.
          SMS compris dans tous les paliers.
        </p>
      </Mesure>
    </Bande>
  );
}

function Souscrire() {
  return (
    <Bande id="souscrire" aria="titre-souscrire" className="bg-accent-voile">
      <div className="text-center">
        <h2 id="titre-souscrire" className="text-3xl font-medium tracking-tight sm:text-4xl">
          Ouvrir ma file d'attente
        </h2>
        <p className="mx-auto mt-4 max-w-[38rem] leading-relaxed text-sur-fond-doux">
          Trois minutes, aucun matériel à acheter, aucun logiciel à installer.
          Vous repartez avec le QR code à imprimer et poser au comptoir.
        </p>
        <div className="mt-8 flex justify-center">
          <Appel href="/inscription">Inscrire ma clinique <Fleche /></Appel>
        </div>
        <p className="mt-4 text-sm text-sur-fond-doux">
          Une question d'abord&nbsp;?{' '}
          <a className="underline" href="mailto:contact@attenso.fr">contact@attenso.fr</a>,
          réponse sous deux jours ouvrés.
        </p>
      </div>
    </Bande>
  );
}

function Pied() {
  return (
    <footer className="border-t border-bordure-douce py-12">
      <Colonne className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
        <Mesure>
          <p className="text-sm leading-relaxed text-sur-fond-doux">
            Attenso — file d'attente virtuelle pour cabinets à flux libre.
            Les numéros de téléphone servent à prévenir le client le jour même,
            et sont effacés le lendemain.
          </p>
        </Mesure>
        {/* « Se connecter » n'est pas une mention légale : il garde sa
            place, et la bande partagée porte les trois autres. */}
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
          <a href="/connexion" className="cible-min inline-flex items-center
                                          text-accent-texte underline
                                          underline-offset-2">Se connecter</a>
          <BandeLegale variante="rangee" />
        </div>
      </Colonne>
    </footer>
  );
}

export function Vitrine() {
  return (
    <>
      <a href="#contenu"
         className="sr-only focus:not-sr-only focus:absolute focus:z-30 focus:m-3
                    focus:rounded-[var(--rayon)] focus:bg-accent focus:px-4 focus:py-3
                    focus:text-sur-accent">
        Aller au contenu
      </a>
      <Entete />
      <main id="contenu">
        <Heros />
        <Cout />
        <Benefices />
        <Comment />
        <Etat />
        <Tarifs />
        <Souscrire />
      </main>
      <Pied />
    </>
  );
}
