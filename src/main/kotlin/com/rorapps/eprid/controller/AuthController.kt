package com.rorapps.eprid.controller

import com.rorapps.eprid.dto.auth.AuthResponse
import com.rorapps.eprid.dto.auth.LoginRequest
import com.rorapps.eprid.dto.auth.RegisterRequest
import com.rorapps.eprid.dto.common.ApiResponse
import com.rorapps.eprid.service.auth.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.sql.DataSource

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Register and log in to E-PRid")
class AuthController(
    private val authService: AuthService,
    private val dataSource: DataSource,
    private val environment: Environment
) {

    // TEMPORARY diagnostic endpoint — remove after the persistence investigation is done.
    // Runs a raw query through the app's own live connection pool so we can compare what the
    // app itself sees against what an external client sees on the "same" DB_URL.
    @GetMapping("/_diag")
    fun diag(): ResponseEntity<Map<String, Any?>> {
        dataSource.connection.use { conn ->
            val meta = conn.metaData
            val result = mutableMapOf<String, Any?>(
                "jdbcUrl" to meta.url,
                "jdbcUsername" to meta.userName
            )
            conn.createStatement().use { st ->
                st.executeQuery("SELECT current_database(), inet_server_addr()::text, inet_server_port(), pg_backend_pid(), version()").use { rs ->
                    if (rs.next()) {
                        result["currentDatabase"] = rs.getString(1)
                        result["serverAddr"] = rs.getString(2)
                        result["serverPort"] = rs.getInt(3)
                        result["backendPid"] = rs.getInt(4)
                        result["pgVersion"] = rs.getString(5)
                    }
                }
                st.executeQuery("SELECT count(*) FROM eprid.users").use { rs ->
                    if (rs.next()) result["userCount"] = rs.getLong(1)
                }
                st.executeQuery("SELECT id, email, created_at FROM eprid.users ORDER BY created_at DESC LIMIT 5").use { rs ->
                    val rows = mutableListOf<Map<String, Any?>>()
                    while (rs.next()) {
                        rows.add(mapOf("id" to rs.getString(1), "email" to rs.getString(2), "createdAt" to rs.getString(3)))
                    }
                    result["latestUsers"] = rows
                }
            }
            result["envVars"] = System.getenv()
                .filterKeys {
                    it.contains("DATASOURCE", ignoreCase = true) || it.contains("DB_", ignoreCase = true) ||
                    it == "DATABASE_URL" || it.contains("FLYWAY", ignoreCase = true) || it.contains("NEON", ignoreCase = true)
                }
                .mapValues { (k, v) -> if (k.contains("PASSWORD", ignoreCase = true)) "***" else v }
            result["springDatasourceUrlProperty"] = environment.getProperty("spring.datasource.url")
            result["springFlywayUrlProperty"] = environment.getProperty("spring.flyway.url")
            result["springFlywayUserProperty"] = environment.getProperty("spring.flyway.user")
            result["freshSystemGetenvDbUrl"] = System.getenv("DB_URL")
            result["allEnvVarKeys"] = System.getenv().keys.sorted()
            result["railwayDeploymentId"] = System.getenv("RAILWAY_DEPLOYMENT_ID")
            result["railwayReplicaId"] = System.getenv("RAILWAY_REPLICA_ID")
            result["railwayReplicaRegion"] = System.getenv("RAILWAY_REPLICA_REGION")
            result["railwaySnapshotId"] = System.getenv("RAILWAY_SNAPSHOT_ID")
            result["processStartTime"] = java.lang.management.ManagementFactory.getRuntimeMXBean().startTime
            return ResponseEntity.ok(result)
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.register(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, "Account created"))
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.login(request)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
