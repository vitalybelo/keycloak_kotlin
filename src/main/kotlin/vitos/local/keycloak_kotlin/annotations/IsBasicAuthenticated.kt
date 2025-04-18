package vitos.local.keycloak_kotlin.annotations

import org.springframework.security.access.prepost.PreAuthorize

@PreAuthorize("@basicAuthorization.isAuthorized(#headers)")
annotation class IsBasicAuthenticated
