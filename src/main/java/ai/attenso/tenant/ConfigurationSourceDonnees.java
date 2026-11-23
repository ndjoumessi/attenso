package ai.attenso.tenant;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * La source de données de l'application est enveloppée pour porter le tenant.
 * Flyway garde la sienne, en rôle propriétaire : c'est ce qui permet aux
 * migrations de voir toutes les lignes alors que l'application n'en voit
 * qu'un tenant.
 */
@Configuration
public class ConfigurationSourceDonnees {

	@Bean
	@Primary
	DataSource dataSource(DataSourceProperties proprietes) {
		HikariDataSource hikari = proprietes.initializeDataSourceBuilder()
				.type(HikariDataSource.class)
				.build();
		return new SourceDonneesCloisonnee(hikari);
	}
}
