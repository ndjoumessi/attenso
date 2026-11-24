import { PageLegale, Valeur, useEditeur } from './composants/legal.jsx';

const IDENTITE = [
  ['Dénomination', 'denomination'],
  ['Forme juridique', 'formeJuridique'],
  ['Capital social', 'capital'],
  ['Siège social', 'siege'],
  ['SIRET', 'siret'],
  ['RCS', 'rcs'],
  ['TVA intracommunautaire', 'tva'],
  ['Directeur de la publication', 'directeurPublication'],
  ['Contact', 'email'],
  ['Téléphone', 'telephone'],
];

export function Mentions() {
  const editeur = useEditeur();
  return (
    <PageLegale titre="Mentions légales">
      <h2>Éditeur du site</h2>
      <dl>
        {IDENTITE.map(([libelle, champ]) => (
          <div key={champ}>
            <dt>{libelle}</dt>
            <dd><Valeur>{editeur[champ]}</Valeur></dd>
          </div>
        ))}
      </dl>

      <h2>Hébergement</h2>
      <p>
        Le service est hébergé par <strong><Valeur>{editeur.hebergeur}</Valeur></strong>,{' '}
        <Valeur>{editeur.hebergeurAdresse}</Valeur>.
      </p>
      <p className="text-sm text-sur-fond-doux">
        Les serveurs qui exécutent Attenso et la base de données sont situés dans
        l'Union européenne. Le service n'appelle aucun prestataire hors Union
        européenne pour fonctionner, à l'exception du prestataire de paiement
        mentionné dans la <a href="/confidentialite">politique de confidentialité</a>.
      </p>

      <h2>Propriété intellectuelle</h2>
      <p>
        Le code source d'Attenso est publié sur{' '}
        <a href="https://github.com/ndjoumessi/attenso">GitHub</a>. Le contenu de
        ce site — textes, mise en page — reste la propriété de l'éditeur.
      </p>

      <h2>Signaler un contenu</h2>
      <p>
        Pour toute réclamation concernant ce site, écrivez à l'adresse de contact
        ci-dessus. Une réponse est apportée sous deux jours ouvrés.
      </p>
    </PageLegale>
  );
}
