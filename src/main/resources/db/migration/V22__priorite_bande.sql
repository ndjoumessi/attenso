-- La priorité d'un ticket est une bande de 0 à 10, pas trois valeurs.
--
-- V1 posait ticket_priorite_connue CHECK (priorite IN (0, 5, 10)) : normal,
-- prioritaire, urgence. V8 a ensuite introduit des motifs paramétrables dont le
-- rang va de 1 à 9 (motif_rang_dans_la_bande) sans relâcher cette contrainte.
-- Résultat : le seul rang qu'un motif pouvait porter sans erreur était 5, et
-- choisir « Chat en caisse » (7) ou « Animal potentiellement contagieux » (8) à
-- l'accueil échouait en base.
--
-- Le code Java traite déjà la priorité comme une bande continue : Priorisation
-- valide tout l'intervalle NORMALE..URGENCE, Ticket copie le rang du motif tel
-- quel, et la file se trie par comparaison (priorite DESC, ordre ASC). Cette
-- migration remet le schéma d'accord avec lui.
ALTER TABLE ticket DROP CONSTRAINT ticket_priorite_connue;
ALTER TABLE ticket ADD CONSTRAINT ticket_priorite_dans_la_bande
  CHECK (priorite BETWEEN 0 AND 10);
