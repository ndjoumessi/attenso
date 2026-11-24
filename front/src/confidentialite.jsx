import html from '../contenu/confidentialite.html?raw';
import { ProseLegale } from './legal-prose.jsx';

export function Page() {
  return <ProseLegale titre="Politique de confidentialité" html={html} />;
}
