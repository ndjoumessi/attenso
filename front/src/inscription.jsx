import { useEffect, useState } from 'react';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import { euros, nomPalier } from '../../src/main/resources/static/app/formats.js';

const RETOURS = {
  ok: "Paiement enregistré. Votre clinique s'ouvre dès que notre prestataire "
    + 'nous l\'a confirmé, en général en quelques secondes.',
  abandon: 'Paiement abandonné. Votre clinique est réservée : reprenez quand '
    + 'vous voulez, rien n\'est perdu.',
};

const champ = 'mt-2 w-full rounded-[var(--rayon)] border border-bordure-controle '
  + 'bg-surface px-4 py-3 cible focus-visible:outline-2 '
  + 'focus-visible:outline-offset-2 focus-visible:outline-[var(--anneau)]';
const etiquette = 'block font-semibold';
const aide = 'mt-2 text-sm leading-relaxed text-sur-fond-doux';

function Adresse({ children }) {
  return (
    <code className="break-all rounded-[var(--rayon-sm)] bg-surface-douce px-2 py-1
                     font-mono text-sm">{children}</code>
  );
}

export function Inscription() {
  const [paliers, setPaliers] = useState([]);
  const [choisi, setChoisi] = useState(null);
  const [retour, setRetour] = useState(null);
  const [erreur, setErreur] = useState(null);
  const [envoi, setEnvoi] = useState(false);
  const [resultat, setResultat] = useState(null);

  useEffect(() => {
    // Le retour du prestataire. On ne se fie PAS à ce paramètre pour décider
    // que l'abonnement est actif — seul le webhook signé le fait — mais il
    // faut bien dire quelque chose à qui revient de la page de paiement.
    setRetour(RETOURS[new URLSearchParams(location.search).get('paiement')] ?? null);

    // Les prix viennent du serveur : les recopier ici ferait diverger la page
    // de la facture dès le premier changement de tarif.
    appel('/api/inscription/paliers')
      .then((liste) => {
        setPaliers(liste);
        // Le premier palier est coché par défaut, et c'est délibéré : avec la
        // grille actuelle, rester au plus petit et payer le dépassement
        // revient moins cher que le palier du milieu à tous les volumes.
        // Présélectionner le plus cher serait vendre contre l'intérêt du
        // client.
        if (liste.length > 0) setChoisi(liste[0].nom);
      })
      .catch(() => setErreur('Formules indisponibles : réessayez dans un instant.'));
  }, []);

  async function soumettre(evenement) {
    evenement.preventDefault();
    const formulaire = evenement.currentTarget;
    setEnvoi(true);
    setErreur(null);
    try {
      const reponse = await appel('/api/inscription', {
        method: 'POST',
        body: JSON.stringify({
          nom: formulaire.nom.value.trim(),
          email: formulaire.email.value.trim(),
          motDePasse: formulaire.motDePasse.value,
          palier: choisi,
          avecEcran: formulaire.avecEcran.checked,
          cgvAcceptees: formulaire.cgvAcceptees.checked,
        }),
      });
      // Le mot de passe quitte la mémoire de la page dès qu'il est parti.
      formulaire.motDePasse.value = '';
      setResultat(reponse);
    } catch (echec) {
      setErreur(echec.reseau
        ? 'Serveur injoignable : réessayez dans un instant.'
        : echec.message);
      setEnvoi(false);
    }
  }

  if (resultat) return <Resultat reponse={resultat} />;

  return (
    <main className="mx-auto w-full max-w-lg px-5 py-10">
      <h1 className="text-3xl font-bold tracking-tight">Inscrire ma clinique</h1>

      {retour && (
        <div role="status"
             className="mt-4 rounded-[var(--rayon)] border border-accent
                        bg-accent-voile px-4 py-3 text-accent-texte">{retour}</div>
      )}

      <p className="mt-3 leading-relaxed text-sur-fond-doux">
        Trois minutes, aucun matériel, aucune installation. Vous repartez avec
        l'adresse du QR code à imprimer et poser au comptoir.
      </p>

      <form onSubmit={soumettre} className="mt-8 space-y-6">
        <div>
          <label className={etiquette} htmlFor="nom">Nom de la clinique</label>
          <input id="nom" name="nom" required minLength={2} maxLength={120}
                 autoComplete="organization" placeholder="Clinique des Cerisiers"
                 className={champ} />
        </div>

        <div>
          <label className={etiquette} htmlFor="email">Adresse de connexion</label>
          <input id="email" name="email" type="email" required maxLength={200}
                 autoComplete="email" placeholder="accueil@clinique.fr"
                 className={champ} />
        </div>

        <div>
          <label className={etiquette} htmlFor="mdp">Mot de passe</label>
          <input id="mdp" name="motDePasse" type="password" required minLength={12}
                 maxLength={200} autoComplete="new-password"
                 aria-describedby="aide-mdp" className={champ} />
          <p id="aide-mdp" className={aide}>
            12 caractères au minimum. C'est le seul identifiant de votre
            établissement : notez-le dans votre gestionnaire de mots de passe.
          </p>
        </div>

        <fieldset className="rounded-[var(--rayon-lg)] border border-bordure-douce p-5">
          <legend className="px-2 font-semibold">Votre formule</legend>
          <div className="space-y-2">
            {paliers.map((palier) => (
              <label key={palier.nom}
                     className={`flex cursor-pointer gap-3 rounded-[var(--rayon)]
                       border p-4 transition-colors
                       ${choisi === palier.nom
                         ? 'border-accent bg-accent-voile'
                         : 'border-bordure-douce hover:bg-surface-douce'}`}>
                <input type="radio" name="palier" value={palier.nom}
                       checked={choisi === palier.nom}
                       onChange={() => setChoisi(palier.nom)}
                       className="mt-1 size-5 shrink-0 accent-[var(--accent)]" />
                <span>
                  <strong>{nomPalier(palier.nom)}</strong>
                  {' — '}{euros(palier.prixCentimes)}/mois, jusqu'à{' '}
                  {palier.visitesIncluses.toLocaleString('fr-FR')} visites
                  <span className="block text-sm text-sur-fond-doux">
                    puis {euros(palier.depassementCentimes)} la visite
                  </span>
                </span>
              </label>
            ))}
          </div>
          <p className={aide}>
            La facture suit le nombre de visites, pas le nombre de praticiens.
            SMS inclus. Au-delà du volume, chaque visite supplémentaire est
            facturée au tarif affiché.
          </p>
        </fieldset>

        <label className="flex cible-min cursor-pointer items-center gap-3">
          <input type="checkbox" name="avecEcran"
                 className="size-5 accent-[var(--accent)]" />
          J'ai déjà un écran en salle d'attente
        </label>

        {/* Acceptation explicite, et « required » : une vente à distance sans
            acceptation des conditions est un contrat contestable. Le serveur
            la refuse aussi, car une case HTML se contourne en trois secondes. */}
        <label className="flex cursor-pointer items-start gap-3">
          <input type="checkbox" name="cgvAcceptees" required
                 className="mt-1 size-5 shrink-0 accent-[var(--accent)]" />
          <span>
            J'accepte les{' '}
            <a href="/cgv" target="_blank" rel="noopener"
               className="text-accent-texte underline">conditions générales</a>
            {' '}et la{' '}
            <a href="/confidentialite" target="_blank" rel="noopener"
               className="text-accent-texte underline">politique de confidentialité</a>
          </span>
        </label>

        <details className="text-sm text-sur-fond-doux">
          <summary className="cible-min flex cursor-pointer items-center
                              font-semibold text-sur-fond">
            Vos données et celles de vos clients
          </summary>
          <p className="mt-2 leading-relaxed">
            Vous serez responsable du traitement des données de vos clients.
            Attenso ne collecte d'eux qu'un numéro de mobile et un numéro de
            ticket, effacés le lendemain. Aucune donnée de santé n'est traitée.
          </p>
        </details>

        {erreur && (
          <div role="alert"
               className="rounded-[var(--rayon)] border border-alerte
                          bg-alerte-voile px-4 py-3 text-alerte">{erreur}</div>
        )}

        <button type="submit" disabled={envoi || !choisi}
                className="w-full cible rounded-[var(--rayon)] bg-accent px-6
                           text-lg font-bold text-sur-accent shadow-1
                           [touch-action:manipulation] disabled:opacity-60
                           hover:brightness-110 active:brightness-95">
          {envoi ? 'Un instant…' : 'Créer ma file d’attente'}
        </button>
      </form>
    </main>
  );
}

function Resultat({ reponse }) {
  const base = typeof location === 'undefined' ? '' : location.origin;
  return (
    <main className="mx-auto w-full max-w-lg px-5 py-10">
      <h1 className="text-3xl font-bold tracking-tight">Plus qu'une étape</h1>

      <div role="status"
           className="mt-4 rounded-[var(--rayon)] border border-accent
                      bg-accent-voile px-4 py-3 text-accent-texte">
        Votre clinique est réservée, mais elle <strong>n'est pas encore
        ouverte</strong> : l'abonnement se confirme au paiement. Gardez les
        adresses ci-dessous, elles sont déjà les vôtres.
      </div>

      <a href={reponse.urlPaiement}
         className="mt-6 flex w-full cible items-center justify-center
                    rounded-[var(--rayon)] bg-accent px-6 text-lg font-bold
                    text-sur-accent no-underline shadow-1 hover:brightness-110">
        Payer et ouvrir ma clinique
      </a>
      <p className={aide}>
        Paiement par carte ou prélèvement, sur une page de notre prestataire.
        Nous ne voyons jamais votre numéro de carte. Résiliable à tout moment,
        sans préavis : vous gardez le service jusqu'à la fin de la période payée.
      </p>

      <h2 className="mt-10 text-xl font-bold">À imprimer et poser au comptoir</h2>
      <p className="mt-2">
        Le QR code de vos clients pointe vers&nbsp;:{' '}
        <Adresse>{base + reponse.urlComptoir}</Adresse>
      </p>

      {reponse.urlEcran && (
        <>
          <h2 className="mt-8 text-xl font-bold">Votre écran de salle d'attente</h2>
          <p className="mt-2">
            Collez cette adresse dans le navigateur de l'écran, une seule
            fois&nbsp;: <Adresse>{base + reponse.urlEcran}</Adresse>
            <span className="mt-1 block text-sm text-sur-fond-doux">
              Gardez-la : elle ne sera plus affichée.
            </span>
          </p>
        </>
      )}

      {/* Le §15 du cahier des charges avait repéré cette collision :
          l'expéditeur au nom de la clinique exige une validation opérateur de
          plusieurs jours, ce qu'aucune inscription en ligne ne peut produire.
          On l'annonce ici plutôt que de laisser découvrir le silence. */}
      <h2 className="mt-8 text-xl font-bold">Avant que les SMS partent</h2>
      <p className="mt-2 leading-relaxed">
        Vos SMS porteront le nom <strong>{reponse.expediteurSmsPropose}</strong>,
        qui doit être déclaré auprès des opérateurs&nbsp;: c'est une formalité de
        quelques jours, sur laquelle nous revenons vers vous.
      </p>
      <p className={aide}>
        En attendant, <strong className="text-sur-fond">tout fonctionne sauf les
        SMS</strong> : la file, l'écran de salle d'attente et la page de suivi
        que vos clients ouvrent depuis le QR code.
      </p>

      <p className={aide}>
        Une fois le paiement confirmé, votre tableau d'accueil s'ouvre sur{' '}
        <a href="/accueil" className="text-accent-texte underline">/accueil</a>,
        avec l'adresse et le mot de passe que vous venez de choisir.
      </p>
    </main>
  );
}
