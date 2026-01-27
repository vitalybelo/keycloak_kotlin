package vitos.local.keycloak_kotlin.configs

import liquibase.integration.spring.SpringLiquibase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class LiquibaseConfig {

    @Bean
    fun liquibase(dataSource: DataSource): SpringLiquibase {

        val liquibase = SpringLiquibase()

        liquibase.dataSource = dataSource
        liquibase.changeLog = "classpath:db/scheme-keycloakadp-changelog.xml"
        liquibase.defaultSchema = "keycloakadp"
        liquibase.isDropFirst = false
        liquibase.setShouldRun(true)

        liquibase.contexts = "dev"

        val params = HashMap<String, String>()
        params["schema"] = "keycloakadp"
        liquibase.setChangeLogParameters(params)

        return liquibase
    }
}