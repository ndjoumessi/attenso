import html from '../contenu/cgv.html?raw';
import { ProseLegale } from './legal-prose.jsx';

export function Page() {
  return <ProseLegale titre="Conditions générales de vente" html={html} />;
}
