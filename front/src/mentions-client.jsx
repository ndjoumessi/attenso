import { hydrateRoot } from 'react-dom/client';
import { Mentions } from './mentions.jsx';

hydrateRoot(document.getElementById('racine'), <Mentions />);
