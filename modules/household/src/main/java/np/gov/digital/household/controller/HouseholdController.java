package np.gov.digital.household.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import np.gov.digital.household.dto.HouseholdRequest;
import np.gov.digital.household.dto.HouseholdResponse;
import np.gov.digital.household.service.HouseholdService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Households", description = "Household registration and lookup")
@RestController
@RequestMapping("/v1/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService service;

    @Operation(summary = "Register a household")
    @PostMapping
    public HouseholdResponse create(@RequestBody HouseholdRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Get a household by ID")
    @GetMapping("/{id}")
    public HouseholdResponse getById(@Parameter(description = "Household ID") @PathVariable UUID id) {
        return service.getById(id);
    }

    @Operation(summary = "List households in a ward")
    @GetMapping("/ward/{wardId}")
    public List<HouseholdResponse> getByWard(@Parameter(description = "Ward ID") @PathVariable UUID wardId) {
        return service.getByWardId(wardId);
    }

    @Operation(summary = "List households headed by a citizen")
    @GetMapping("/head/{citizenId}")
    public List<HouseholdResponse> getByHead(
            @Parameter(description = "Head-of-household citizen ID") @PathVariable UUID citizenId) {
        return service.getByHeadCitizenId(citizenId);
    }

    @Operation(summary = "Update a household")
    @PutMapping("/{id}")
    public HouseholdResponse update(
            @Parameter(description = "Household ID") @PathVariable UUID id,
            @RequestBody HouseholdRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Delete a household")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "Household ID") @PathVariable UUID id) {
        service.delete(id);
    }
}
