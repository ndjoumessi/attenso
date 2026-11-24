-- EF-205 : le client signale « je suis à quelques minutes ».
--
-- Le signalement NE CHANGE PAS l'ordre de la file, et c'est le point entier.
-- Le cahier des charges dit « signaler », pas « passer devant ». Laisser un
-- bouton réordonner la file donnerait à chacun un moyen de doubler les autres
-- depuis son téléphone, et l'ASV perdrait le contrôle de sa salle. C'est une
-- information posée sur la ligne ; la décision reste à l'accueil.
ALTER TABLE ticket ADD COLUMN signale_proche_le timestamptz;

-- Index partiel : seuls les quelques tickets signalés du jour sont lus, et la
-- colonne est vide pour l'écrasante majorité.
CREATE INDEX ticket_signale_proche ON ticket (tenant_id, jour)
  WHERE signale_proche_le IS NOT NULL;
