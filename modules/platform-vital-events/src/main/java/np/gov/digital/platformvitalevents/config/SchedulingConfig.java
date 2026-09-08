package np.gov.digital.platformvitalevents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled support for VitalEventAutoEscalationJob.
 *
 * The design doc names Quartz for every scheduled job in the system
 * (CitizenshipEligibilityJob, DeathCascadeReconciliationJob,
 * MerkleAnchorJob, IntegrityVerificationJob, and this one) — but Quartz
 * isn't set up anywhere in this codebase yet (no dependency, no
 * SpringBeanJobFactory wiring for dependency injection into Job
 * instances, no JobDetail/Trigger config), and none of those other jobs
 * are built yet either. Standing up the full Quartz + Spring DI
 * integration is a real infrastructure decision in its own right, bigger
 * than "add one job" — so this uses Spring's built-in @Scheduled instead
 * for now, which needs none of that scaffolding and is more than
 * sufficient for a simple periodic sweep. If a later job genuinely needs
 * what plain @Scheduled doesn't offer (persistent job state surviving a
 * restart mid-run, clustering across multiple app instances, misfire
 * policies), that's the point to introduce Quartz properly — and this
 * job should move onto it too at that point, for consistency.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
