-- Révocation des jetons par COMPTEUR, et non plus par comparaison de dates.
--
-- La version précédente comparait l'instant d'émission du jeton à la date du
-- dernier changement de mot de passe. Deux granularités s'y rencontraient — un
-- JWT porte son émission à la SECONDE, la colonne portait les millisecondes —
-- et cette rencontre a produit deux erreurs successives : d'abord le rejet des
-- reconnexions faites dans la même seconde que le changement, puis, une fois
-- les deux côtés tronqués, la survie d'un jeton émis pendant cette seconde-là.
--
-- Un compteur n'a pas de granularité. Il s'incrémente, on compare pour
-- égalité, et il n'y a plus de fenêtre à raisonner.
-- DEFAULT 0, et les jetons déjà émis ne portent pas encore la revendication :
-- le code la lit alors comme 0, donc ils restent valables jusqu'à leur
-- expiration naturelle, douze heures au plus. C'est voulu — déconnecter toutes
-- les cliniques au milieu de leurs consultations pour installer une mise à
-- jour serait une drôle de façon d'améliorer la sécurité.
ALTER TABLE utilisateur ADD COLUMN jeton_version integer NOT NULL DEFAULT 0;

-- MAIS PAS POUR CEUX QUI ONT DÉJÀ CHANGÉ DE MOT DE PASSE. Entre V20 et cette
-- migration, la révocation reposait sur la date : un compte réinitialisé dans
-- cet intervalle avait bel et bien ses jetons refusés. Les laisser tous à zéro
-- RESSUSCITERAIT ces sessions-là — le jeton du voleur, qui ne porte pas la
-- revendication, serait relu comme une version zéro et retrouverait sa
-- validité pour le reste de ses douze heures. Un correctif de sécurité qui
-- annule un correctif de sécurité.
--
-- Les partir à 1 referme la porte : leurs anciens jetons ne correspondent plus.
-- Les comptes qui n'ont jamais changé de mot de passe restent à zéro et gardent
-- leur session, ce qui est le comportement voulu pour une mise à jour.
UPDATE utilisateur SET jeton_version = 1 WHERE mdp_change_le IS NOT NULL;

COMMENT ON COLUMN utilisateur.jeton_version IS
  'Incrémenté à chaque changement de mot de passe. Un jeton qui ne porte pas '
  'cette valeur exacte est refusé : c''est ce qui ferme les sessions ouvertes.';
