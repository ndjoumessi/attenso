package ai.attenso.equipe;

import ai.attenso.motdepasse.ServiceReinitialisation;
import ai.attenso.tenant.Tenant;
import ai.attenso.utilisateur.Role;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les comptes du personnel d'une clinique.
 *
 * <p>Jusqu'ici une clinique n'avait qu'un seul compte, celui créé à
 * l'inscription : l'ASV et le vétérinaire partageaient le même mot de passe, et
 * un départ de salarié obligeait à tout réinitialiser. C'est ce que cette
 * classe répare.
 *
 * <p><strong>Deux gardes, et ils comptent autant que le reste.</strong> Il doit
 * rester un gérant actif — sinon la clinique se verrouille dehors en un clic,
 * et la sortie serait une intervention en base un samedi. Et tout changement de
 * rôle ou d'activation invalide les jetons du compte visé : le rôle voyage dans
 * le jeton, donc sans ça une rétrogradation ou un départ ne prendrait effet
 * qu'à l'expiration, jusqu'à douze heures plus tard.
 */
@Service
public class ServiceEquipe {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ServiceEquipe.class);

	private final EquipeDao dao;

	private final ServiceReinitialisation invitations;

	private final Clock horloge;

	public ServiceEquipe(EquipeDao dao, ServiceReinitialisation invitations, Clock horloge) {
		this.dao = dao;
		this.invitations = invitations;
		this.horloge = horloge;
	}

	public List<MembreEquipe> lister(UUID tenantId) {
		return dao.lister(tenantId);
	}

	/**
	 * Crée le compte et envoie l'invitation.
	 *
	 * <p>Le compte naît SANS mot de passe utilisable : il ne s'ouvre que
	 * lorsque son titulaire suit l'invitation. Un mot de passe provisoire
	 * transmis par le gérant ferait circuler un secret par un canal qu'on ne
	 * maîtrise pas, et il resterait valable tant que personne n'y penserait.
	 */
	@Transactional
	public MembreEquipe inviter(Tenant clinique, String email, Role role) {
		String adresse = email.trim().toLowerCase(java.util.Locale.ROOT);
		UUID id = UUID.randomUUID();
		if (!dao.creer(id, clinique.id(), adresse, role, horloge.instant())) {
			throw new AdresseDejaPrise();
		}
		invitations.inviter(clinique, id);
		JOURNAL.info("accès {} ouvert pour la clinique {}", role, clinique.slug());
		return dao.parId(clinique.id(), id).orElseThrow();
	}

	@Transactional
	public void changerRole(UUID tenantId, UUID membreId, Role role) {
		if (role != Role.GERANT && resteSansGerant(tenantId, membreId)) {
			throw new DerniereGerance();
		}
		dao.changerRole(tenantId, membreId, role);
	}

	@Transactional
	public void changerActif(UUID tenantId, UUID membreId, boolean actif) {
		if (!actif && resteSansGerant(tenantId, membreId)) {
			throw new DerniereGerance();
		}
		dao.changerActif(tenantId, membreId, actif);
	}

	/**
	 * Le membre visé est-il le dernier gérant actif ?
	 *
	 * <p>On compte les AUTRES : si aucun autre gérant actif n'existe et que
	 * celui-ci en est un, le retirer laisserait la clinique sans gérance.
	 */
	private boolean resteSansGerant(UUID tenantId, UUID membreId) {
		MembreEquipe membre = dao.parId(tenantId, membreId).orElseThrow(MembreInconnu::new);
		return membre.role() == Role.GERANT && membre.actif()
				&& dao.compterGerantsActifsSauf(tenantId, membreId) == 0;
	}
}
