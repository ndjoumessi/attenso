package ai.attenso.equipe;

import ai.attenso.utilisateur.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Un compte du personnel, tel que le gérant le voit.
 *
 * <p>Sans empreinte de mot de passe, évidemment, et sans rien d'autre que ce
 * qui sert à décider : qui, quel rôle, actif ou non, depuis quand.
 *
 * @param aChoisiSonMotDePasse faux tant que l'invitation n'a pas été suivie.
 *        C'est l'information qui manque le plus au gérant : un compte créé
 *        mais jamais ouvert ressemble en tout point à un compte qui marche.
 */
public record MembreEquipe(UUID id, String email, Role role, boolean actif,
		boolean aChoisiSonMotDePasse, Instant creeLe) {
}
