package np.gov.digital.foreignemployment.controller;



import lombok.RequiredArgsConstructor;
import np.gov.digital.foreignemployment.dto.ForeignEmploymentRequest;
import np.gov.digital.foreignemployment.dto.ForeignEmploymentResponse;
import np.gov.digital.foreignemployment.service.ForeignEmploymentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/foreign-employment")
@RequiredArgsConstructor
public class ForeignEmploymentController {

    private final ForeignEmploymentService service;

    @PostMapping
    public ForeignEmploymentResponse create(@RequestBody ForeignEmploymentRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ForeignEmploymentResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/citizen/{citizenId}")
    public List<ForeignEmploymentResponse> getByCitizen(@PathVariable UUID citizenId) {
        return service.getByCitizenId(citizenId);
    }

    @PutMapping("/{id}")
    public ForeignEmploymentResponse update(
            @PathVariable UUID id,
            @RequestBody ForeignEmploymentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
