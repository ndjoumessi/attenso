-- L'expéditeur affiché par le SMS est le nom de la CLINIQUE, pas celui de
-- l'éditeur. Conséquence directe, et elle n'est pas technique : ce n'est plus
-- un enregistrement unique chez l'agrégateur, mais un par clinique, avec
-- autorisation écrite et validation opérateur (réglementation en vigueur
-- depuis le 1er mars 2026, cf. docs/note-canal-sms.md).
--
-- D'où une colonne dédiée, et non le nom d'affichage : la réglementation
-- plafonne à 11 caractères alphanumériques latins sans accent.
-- « Clinique des Cerisiers » ne rentre pas, « Cerisiers » oui.
--
-- NULL = pas encore enregistré auprès des opérateurs. Dans cet état, aucun SMS
-- ne part pour cette clinique : la file, l'écran et la page de suivi marchent,
-- les notifications sont marquées en échec avec le motif. C'est volontairement
-- visible plutôt que silencieux.
ALTER TABLE tenant ADD COLUMN expediteur_sms text;

ALTER TABLE tenant ADD CONSTRAINT tenant_expediteur_sms_conforme
  CHECK (expediteur_sms IS NULL OR expediteur_sms ~ '^[A-Za-z0-9]{3,11}$');

-- Coût brut renvoyé par l'agrégateur, tel quel.
--
-- Il est conservé À CÔTÉ de notre propre estimation (segments × tarif) et non
-- à sa place : l'unité du champ `total_cost` de l'API n'est pas documentée —
-- l'exemple officiel montre 5.005 pour un seul contact, ce qui ne peut pas
-- être des euros. Garder les deux chiffres dès le premier jour permet à la
-- première facture réelle de trancher, au lieu de découvrir l'écart quand la
-- marge est déjà annoncée à un client.
ALTER TABLE notification ADD COLUMN cout_fournisseur numeric(12,6);
