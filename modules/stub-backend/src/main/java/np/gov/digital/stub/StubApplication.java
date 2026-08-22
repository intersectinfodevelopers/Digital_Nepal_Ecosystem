//package np.gov.digital.stub;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
//import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
//import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@SpringBootApplication(
//        scanBasePackages = {
//                "np.gov.digital.stub",
//                "np.gov.digital.platformsync",
//                "np.gov.digital.citizen"
//        }
////        exclude = {
////                DataSourceAutoConfiguration.class,
////                DataSourceTransactionManagerAutoConfiguration.class,
////                HibernateJpaAutoConfiguration.class
////        }
//)
//public class StubApplication {
//
//    public static void main(String[] args) {
//        SpringApplication.run(StubApplication.class, args);
//    }
//
//    @RestController
//    static class HealthController {
//
//        @GetMapping("/actuator/health")
//        public String health() {
//            return "{\"status\":\"UP\"}";
//        }
//    }
//}

package np.gov.digital.stub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(
        scanBasePackages = {
                "np.gov.digital"
        }
)
public class StubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StubApplication.class, args);
    }

    @RestController
    static class HealthController {

        @GetMapping("/actuator/health")
        public String health() {
            return "{\"status\":\"UP\"}";
        }
    }
}