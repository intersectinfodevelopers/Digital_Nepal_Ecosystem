package np.gov.digital.foreignemployment.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import np.gov.digital.foreignemployment.dto.ForeignEmploymentRequest;
import np.gov.digital.foreignemployment.dto.ForeignEmploymentResponse;
import np.gov.digital.foreignemployment.service.ForeignEmploymentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Foreign Employment", description = "Foreign employment records")
@RestController
@RequestMapping("/v1/foreign-employment")
@RequiredArgsConstructor
public class ForeignEmploymentController {

    private final ForeignEmploymentService service;

    @Operation(summary = "Create a foreign employment record")
    @PostMapping
    public ForeignEmploymentResponse create(@RequestBody ForeignEmploymentRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Get a foreign employment record by ID")
    @GetMapping("/{id}")
    public ForeignEmploymentResponse getById(
            @Parameter(description = "Foreign employment record ID") @PathVariable UUID id) {
        return service.getById(id);
    }

    @Operation(summary = "List foreign employment records for a citizen")
    @GetMapping("/citizen/{citizenId}")
    public List<ForeignEmploymentResponse> getByCitizen(
            @Parameter(description = "Citizen ID") @PathVariable UUID citizenId) {
        return service.getByCitizenId(citizenId);
    }

    @Operation(summary = "Update a foreign employment record")
    @PutMapping("/{id}")
    public ForeignEmploymentResponse update(
            @Parameter(description = "Foreign employment record ID") @PathVariable UUID id,
            @RequestBody ForeignEmploymentRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete a foreign employment record")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "Foreign employment record ID") @PathVariable UUID id) {
        service.delete(id);
    }
}
