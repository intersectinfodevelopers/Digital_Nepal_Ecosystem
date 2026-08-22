package np.gov.digital.employment.controller;



import np.gov.digital.employment.dto.EmploymentRequest;
import np.gov.digital.employment.dto.EmploymentResponse;
import np.gov.digital.employment.service.EmploymentService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/employment")
public class EmploymentController {

    private final EmploymentService service;

    public EmploymentController(EmploymentService service) {
        this.service = service;
    }

    @PostMapping
    public EmploymentResponse createOrUpdate(@RequestBody EmploymentRequest request) {
        return service.createOrUpdate(request);
    }

    @GetMapping("/{citizenId}")
    public EmploymentResponse getByCitizen(@PathVariable UUID citizenId) {
        return service.getByCitizenId(citizenId);
    }
}
