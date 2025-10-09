package vitos.local.keycloak_kotlin.configs

import nl.basjes.parse.useragent.UserAgentAnalyzer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class UserAgentConfiguration {

    companion object {
        private const val CACHE_SIZE = 1000
    }

    @Bean
    fun userAgentAnalyzer(): UserAgentAnalyzer? {
        return UserAgentAnalyzer
            .newBuilder()
            .withCache(CACHE_SIZE)
            .build()
    }
}