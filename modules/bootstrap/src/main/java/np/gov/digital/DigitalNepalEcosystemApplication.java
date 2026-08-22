package np.gov.digital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * THE single runnable entry point for the whole system.
 *
 * Before this class existed, there was no application anywhere in this
 * codebase that actually booted the citizen-registry, grievance, sync,
 * ID-card, audit, household, or employment modules — they were library
 * JARs full of @RestController / @Service / @Entity classes with nothing
 * to load them into a running server (see BOOTSTRAP_NOTES.md for the full
 * explanation). Only the standalone `auth` service could run on its own.
 *
 * This class is deliberately placed directly in the `np.gov.digital`
 * package (not a sub-package) so Spring Boot's default component scan —
 * which scans the annotated class's package and everything below it —
 * automatically picks up every @Component / @Service / @RestController /
 * @Repository / @Entity across every module: np.gov.digital.auth.*,
 * np.gov.digital.citizen.*, np.gov.digital.platformaudit.*,
 * np.gov.digital.platformgrievance.*, and so on. Do not move this class
 * into a sub-package without adding explicit @ComponentScan /
 * @EntityScan / @EnableJpaRepositories base-package lists — doing so
 * silently stops half the application's beans from being registered,
 * with no error at startup, just missing endpoints at request time.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "np.gov.digital",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
            "np\\.gov\\.digital\\.auth\\.AuthApplication",
            "np\\.gov\\.digital\\.stub\\.StubApplication"
        }
    )
)
public class DigitalNepalEcosystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalNepalEcosystemApplication.class, args);
    }
}
