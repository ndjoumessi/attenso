package ai.attenso.compte;

import ai.attenso.motdepasse.EtatApresChangement;
import ai.attenso.motdepasse.MotDePasseDao;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ce qu'une personne peut faire sur son propre compte.
 *
 * <p>Jusqu'ici, changer son mot de passe demandait de prétendre l'avoir
 * oublié : se déconnecter, demander un lien, attendre un courriel. Sur une
 * tablette de comptoir partagée, ce détour suffisait à ne jamais le faire —
 * or c'est exactement le geste qu'on veut facile le jour où quelqu'un quitte
 * la clinique en connaissant le mot de passe commun.
 */
@Service
public class ServiceCompte {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceCompte.class);

	private final MotDePasseDao motsDePasseDao;

	private final PasswordEncoder encodeur;

	private final Clock horloge;

	public ServiceCompte(MotDePasseDao motsDePasseDao, PasswordEncoder encodeur,
			Clock horloge) {
		this.motsDePasseDao = motsDePasseDao;
		this.encodeur = encodeur;
		this.horloge = horloge;
	}

	/**
	 * Change le mot de passe, l'actuel ayant été redonné.
	 *
	 * <p><strong>L'actuel est exigé bien que l'appelant soit déjà
	 * authentifié.</strong> Le jeton prouve qu'une session est ouverte, pas que
	 * c'est la bonne personne qui tient la tablette — et une tablette de
	 * comptoir reste ouverte douze heures. Sans cette vérification, n'importe
	 * qui passant derrière le comptoir pourrait s'approprier le compte et en
	 * mettre dehors son titulaire.
	 *
	 * @return l'état du compte APRÈS l'écriture — version et rôle — pour
	 *         réémettre un jeton qui dise ce que la base dit, et non ce que
	 *         disait le jeton d'avant
	 */
	@Transactional
	public EtatApresChangement changerMotDePasse(UUID utilisateurId, String actuel,
			String nouveau) {
		String empreinte = motsDePasseDao.hashDe(utilisateurId);
		// Un compte désactivé entre l'émission du jeton et maintenant n'a plus
		// d'empreinte lisible : même refus qu'un mot de passe faux, et c'est
		// le bon — ce compte n'a rien à changer.
		if (empreinte == null || !encodeur.matches(actuel, empreinte)) {
			throw new MotDePasseActuelFaux();
		}
		if (encodeur.matches(nouveau, empreinte)) {
			// Refusé plutôt qu'accepté sans rien faire : le changement
			// incrémente la version du jeton, donc ferme toutes les autres
			// sessions. Les fermer pour un mot de passe qui n'a pas bougé
			// serait une surprise, et le message « c'est le même » est plus
			// utile qu'un succès silencieux.
			throw new MotDePasseInchange();
		}
		// Si la ligne a disparu ou vient d'être désactivée entre la lecture de
		// l'empreinte et ici, rien n'est écrit et l'on ne rend pas de jeton :
		// ce compte n'a plus d'accès à renouveler.
		EtatApresChangement etat = motsDePasseDao.poserLeMotDePasse(utilisateurId,
				encodeur.encode(nouveau), horloge.instant())
				.orElseThrow(CompteIndisponible::new);
		JOURNAL.info("mot de passe changé depuis le tableau, version de jeton {}",
				etat.version());
		return etat;
	}
}
