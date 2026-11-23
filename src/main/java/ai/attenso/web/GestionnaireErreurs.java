package ai.attenso.web;

import ai.attenso.tenant.TenantInconnu;
import ai.attenso.ticket.LimiteNumeroAtteinte;
import ai.attenso.ticket.NumeroInvalide;
import ai.attenso.ticket.TicketInconnu;
import ai.attenso.ticket.TransitionInterdite;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduction des erreurs du domaine en réponses HTTP.
 *
 * <p>Les messages sont destinés à être lus par l'ASV sur une tablette, pas par
 * un développeur : « ce ticket a déjà été traité » plutôt qu'une trace. Rien
 * de ce qui est renvoyé ne révèle l'état interne au-delà de ce que
 * l'utilisateur voit déjà à l'écran.
 */
@RestControllerAdvice
public class GestionnaireErreurs {

	@ExceptionHandler(TicketInconnu.class)
	ProblemDetail ticketInconnu(TicketInconnu erreur) {
		return probleme(HttpStatus.NOT_FOUND, "Ticket introuvable",
				"Ce ticket n'existe pas, ou la journée est terminée.");
	}

	@ExceptionHandler(TenantInconnu.class)
	ProblemDetail cliniqueInconnue(TenantInconnu erreur) {
		return probleme(HttpStatus.NOT_FOUND, "Clinique introuvable",
				"Ce lien ne correspond à aucune clinique active.");
	}

	@ExceptionHandler(TransitionInterdite.class)
	ProblemDetail transitionInterdite(TransitionInterdite erreur) {
		ProblemDetail detail = probleme(HttpStatus.CONFLICT, "Action impossible",
				"Ce ticket est déjà passé à « %s » : rechargez le tableau."
						.formatted(erreur.depuis()));
		detail.setProperty("statutCourant", erreur.depuis().name());
		return detail;
	}

	@ExceptionHandler(NumeroInvalide.class)
	ProblemDetail numeroInvalide(NumeroInvalide erreur) {
		return probleme(HttpStatus.BAD_REQUEST, "Numéro invalide",
				"Entrez un numéro de mobile français, par exemple 06 12 34 56 78.");
	}

	@ExceptionHandler(LimiteNumeroAtteinte.class)
	ProblemDetail limiteNumero(LimiteNumeroAtteinte erreur) {
		return probleme(HttpStatus.TOO_MANY_REQUESTS, "Déjà enregistré",
				"Ce numéro a déjà ses tickets pour aujourd'hui. Voyez l'accueil.");
	}

	@ExceptionHandler(TropDeRequetes.class)
	ProblemDetail tropDeRequetes(TropDeRequetes erreur) {
		return probleme(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives",
				"Patientez quelques instants avant de réessayer.");
	}

	@ExceptionHandler(IdentifiantsRefuses.class)
	ProblemDetail identifiantsRefuses(IdentifiantsRefuses erreur) {
		return probleme(HttpStatus.UNAUTHORIZED, "Connexion refusée",
				"Adresse ou mot de passe incorrect.");
	}

	private ProblemDetail probleme(HttpStatus statut, String titre, String detail) {
		ProblemDetail probleme = ProblemDetail.forStatus(statut);
		probleme.setTitle(titre);
		probleme.setDetail(detail);
		return probleme;
	}
}
