package ai.attenso.pilotage;

import java.util.List;

/**
 * Le tableau de bord d'exploitation (EF-704).
 *
 * @param retardMoyenMinutes temps moyen entre la prise du ticket et l'appel,
 *        sur la période. C'est le « retard » tel que le client le vit : pas la
 *        durée de consultation, pas l'attente estimée, mais ce qu'il a
 *        réellement attendu.
 * @param heuresDePointe une entrée par heure d'ouverture observée, dans
 *        l'ordre. Sert à décider d'ouvrir une seconde salle ou de décaler une
 *        pause, qui est la seule décision que ce tableau doit permettre.
 */
public record TableauDeBord(long enFile, long enConsultation, Integer retardMoyenMinutes,
		long departs, long visites, List<Creneau> heuresDePointe) {

	/** @param heure 0 à 23, dans le fuseau de la clinique. */
	public record Creneau(int heure, long visites, Integer retardMoyenMinutes) {
	}
}
