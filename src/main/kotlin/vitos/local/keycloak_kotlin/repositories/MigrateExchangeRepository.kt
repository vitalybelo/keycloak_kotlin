package vitos.local.keycloak_kotlin.repositories

import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.JpaRepository
import vitos.local.keycloak_kotlin.models.migrate.MigrateExchange

@Repository
interface MigrateExchangeRepository : JpaRepository<MigrateExchange, String>