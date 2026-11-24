-- EF-601 et EF-603 : l'écran du mur est appairé par jeton, et optionnel.
--
-- Jusqu'ici /ecran/{slug} était public : qui devinait le slug d'une clinique
-- — son nom, le plus souvent — lisait sa file en direct. Rien de nominatif n'y
-- figure, mais l'affluence d'un cabinet est une donnée commerciale, et un flux
-- SSE ouvert à l'inconnu est une ressource offerte.
--
-- La colonne est NULLABLE, et c'est elle qui porte EF-603 : pas de jeton = pas
-- d'écran. Une clinique sans téléviseur en salle d'attente n'a rien à activer,
-- et ses routes d'écran répondent 404.
ALTER TABLE tenant ADD COLUMN jeton_ecran text;

-- Unicité seulement sur les jetons posés : plusieurs cliniques peuvent n'avoir
-- aucun écran, et un index unique ordinaire les ferait entrer en collision sur
-- NULL dans d'autres moteurs. Partiel, la règle est explicite.
CREATE UNIQUE INDEX tenant_jeton_ecran ON tenant (jeton_ecran)
  WHERE jeton_ecran IS NOT NULL;

ALTER TABLE tenant ADD CONSTRAINT tenant_jeton_ecran_longueur
  CHECK (jeton_ecran IS NULL OR length(jeton_ecran) >= 12);
