import { hydrateRoot } from 'react-dom/client';
import { Page } from './cgv.jsx';

hydrateRoot(document.getElementById('racine'), <Page />);
