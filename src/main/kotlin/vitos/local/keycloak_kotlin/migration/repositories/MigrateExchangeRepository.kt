package vitos.local.keycloak_kotlin.migration.repositories

import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.JpaRepository
import vitos.local.keycloak_kotlin.migration.models.MigrateExchange

@Repository
interface MigrateExchangeRepository : JpaRepository<MigrateExchange, String>