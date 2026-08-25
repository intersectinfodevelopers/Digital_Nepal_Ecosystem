package np.gov.digital.platformsync;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import np.gov.digital.platformsync.batch.SyncBatchItemProcessor;

@SpringBootApplication
@ComponentScan(
        basePackages = "np.gov.digital.platformsync",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SyncBatchItemProcessor.class
        )
)
@EntityScan({
        "np.gov.digital.platformsync.entity",
        "np.gov.digital.citizen.entity"
})
@EnableJpaRepositories({
        "np.gov.digital.platformsync.repository",
        "np.gov.digital.citizen.repository"
})
public class PlatformSyncTestApplication {
}