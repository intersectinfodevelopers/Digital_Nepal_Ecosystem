package np.gov.digital.platformaudit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
@Slf4j
@Tag(name = "Audit Log", description = "Citizen record audit trail")
@RestController
@RequestMapping("/v1/citizens")
@RequiredArgsConstructor
public class AuditLogController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param id       citizen UUID
     * @param page     page number (0-based, default 0)
     * @param size     page size (default 20, max 50)
     */
    @Operation(
            summary = "Get a citizen's audit log",
            description = "Paginated, most-recent-first log of changes to a citizen record. "
                    + "Requires LOCAL_BODY_ADMIN or CENTRAL_ADMIN role. Page size is capped at 50.")
    @GetMapping("/{id}/audit-log")
    @PreAuthorize("hasAnyRole('LOCAL_BODY_ADMIN', 'CENTRAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @Parameter(description = "Citizen ID") @PathVariable("id") UUID id,
            @Parameter(description = "Page number, 0-based") @RequestParam(value = "page", defaultValue = "0")  int page,
            @Parameter(description = "Page size, capped at 50") @RequestParam(value = "size", defaultValue = "20") int size) {

        log.info("AuditLogController: audit-log requested for citizen={}", id);

        // Cap page size at 50
        int cappedSize = Math.min(size, 50);
        int offset = page * cappedSize;

        // Query audit_logs for this citizen — most recent first
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                """
                SELECT
                    id,
                    user_id     AS actor_id,
                    action      AS event_type,
                    entity_type,
                    entity_id,
                    changes,
                    created_at  AS timestamp
                FROM reporting.audit_logs
                WHERE entity_id = ?::uuid
                  AND entity_type = 'CITIZEN'
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """,
                id, cappedSize, offset
        );

        // Total count for pagination metadata
        Integer total = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM reporting.audit_logs
                WHERE entity_id = ?::uuid AND entity_type = 'CITIZEN'
                """,
                Integer.class, id
        );

        int totalCount  = total != null ? total : 0;
        int totalPages  = (int) Math.ceil((double) totalCount / cappedSize);

        Map<String, Object> response = Map.of(
                "citizenId",  id.toString(),
                "events",     events,
                "pagination", Map.of(
                        "page",        page,
                        "size",        cappedSize,
                        "totalEvents", totalCount,
                        "totalPages",  totalPages,
                        "hasNext",     page < totalPages - 1
                )
        );

        return ResponseEntity.ok(response);
    }
}
