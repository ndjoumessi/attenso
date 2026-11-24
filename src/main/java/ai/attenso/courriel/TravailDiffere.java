package ai.attenso.courriel;

/**
 * Exécuter un travail hors du fil de la requête.
 *
 * <p>Une interface d'une ligne plutôt qu'un {@code TaskExecutor} injecté
 * directement, pour deux raisons concrètes. D'abord, Spring en déclare
 * plusieurs — l'exécuteur applicatif et l'ordonnanceur — et l'injection par
 * type échoue au démarrage sur une ambiguïté, ce qui a cassé tous les
 * contextes de test d'un coup. Ensuite et surtout, les tests ont besoin d'un
 * travail SYNCHRONE pour être déterministes : remplacer cette interface-là est
 * net, là où remplacer un bean de Spring par son nom se heurte à l'interdiction
 * de redéfinition.
 */
public interface TravailDiffere {

	void plusTard(Runnable travail);
}
