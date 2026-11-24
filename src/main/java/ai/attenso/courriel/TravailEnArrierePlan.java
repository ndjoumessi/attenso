package ai.attenso.courriel;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/** Le travail part sur l'exécuteur applicatif de Spring. */
@Component
public class TravailEnArrierePlan implements TravailDiffere {

	private final TaskExecutor executeur;

	public TravailEnArrierePlan(@Qualifier("applicationTaskExecutor") TaskExecutor executeur) {
		this.executeur = executeur;
	}

	@Override
	public void plusTard(Runnable travail) {
		executeur.execute(travail);
	}
}
