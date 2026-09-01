package np.gov.digital.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the OpenAPI/Swagger metadata and the JWT bearer security
 * scheme used across every module's controllers. springdoc-openapi scans
 * the whole application context (every @RestController from every
 * business module wired in via {@link np.gov.digital.DigitalNepalEcosystemApplication})
 * and generates the spec at runtime — served at /v3/api-docs and the UI
 * at /swagger-ui.html, both under the server.servlet.context-path (/api).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI digitalNepalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Nepal Ecosystem API")
                        .version("v1")
                        .description("""
                                REST API for the Digital Nepal Ecosystem: citizen registration, \
                                households, employment, disability/ID cards, grievances, \
                                cross-agency offline sync, and audit logging.

                                Authenticate via POST /v1/auth/login, then authorize with the \
                                returned access token using the "Authorize" button below \
                                (Bearer <token>). Endpoints marked public (login, refresh, \
                                ID-card QR verification, grievance tracking) do not require a token.""")
                        .contact(new Contact()
                                .name("Digital Nepal Ecosystem Team")
                                .url("https://digital-nepal.gov.np"))
                        .license(new License().name("Government of Nepal")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token returned by POST /v1/auth/login")));
    }
}
