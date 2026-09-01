package np.gov.digital.employment.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import np.gov.digital.employment.dto.EmploymentRequest;
import np.gov.digital.employment.dto.EmploymentResponse;
import np.gov.digital.employment.service.EmploymentService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Employment", description = "Domestic employment records")
@RestController
@RequestMapping("/v1/employment")
public class EmploymentController {

    private final EmploymentService service;

    public EmploymentController(EmploymentService service) {
        this.service = service;
    }

    @Operation(summary = "Create or update a citizen's employment record")
    @PostMapping
    public EmploymentResponse createOrUpdate(@RequestBody EmploymentRequest request) {
        return service.createOrUpdate(request);
    }

    @Operation(summary = "Get the employment record for a citizen")
    @GetMapping("/{citizenId}")
    public EmploymentResponse getByCitizen(
            @Parameter(description = "Citizen ID") @PathVariable UUID citizenId) {
        return service.getByCitizenId(citizenId);
    }
}
