package np.gov.digital.household.controller;



import lombok.RequiredArgsConstructor;
import np.gov.digital.household.dto.HouseholdRequest;
import np.gov.digital.household.dto.HouseholdResponse;
import np.gov.digital.household.service.HouseholdService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService service;

    @PostMapping
    public HouseholdResponse create(@RequestBody HouseholdRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public HouseholdResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/ward/{wardId}")
    public List<HouseholdResponse> getByWard(@PathVariable UUID wardId) {
        return service.getByWardId(wardId);
    }

    @GetMapping("/head/{citizenId}")
    public List<HouseholdResponse> getByHead(@PathVariable UUID citizenId) {
        return service.getByHeadCitizenId(citizenId);
    }

    @PutMapping("/{id}")
    public HouseholdResponse update(
            @PathVariable UUID id,
            @RequestBody HouseholdRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
