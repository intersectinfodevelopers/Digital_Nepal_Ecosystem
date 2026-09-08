package np.gov.digital.auth.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(
            ResourceNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "timestamp", LocalDateTime.now(),
                        "status",404,
                        "message",ex.getMessage()
                ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorized(
            UnauthorizedException ex){

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "timestamp",LocalDateTime.now(),
                        "status",401,
                        "message",ex.getMessage()
                ));
    }

    // NOTE: deliberately no catch-all @ExceptionHandler(Exception.class)
    // here. One was tried and reverted: @ExceptionHandler methods in a
    // @RestControllerAdvice run BEFORE Spring's own
    // DefaultHandlerExceptionResolver, so a blanket handler intercepts
    // exceptions Spring already resolves correctly on its own —
    // NoResourceFoundException (404, unmatched route),
    // HttpMessageNotReadableException / malformed JSON (400),
    // MethodArgumentNotValidException (400) — and would misreport all of
    // them as 500.
    //
    // The actual bug that motivated adding a catch-all was elsewhere: any
    // unhandled exception on a public (permitAll) endpoint triggered
    // Spring MVC's internal forward to /error, and /error itself was NOT
    // in SecurityConfig's permitAll list — so that forward got rejected by
    // the same AuthorizationFilter as an unauthenticated request, turning
    // every real status code (400, 404, 500, ...) into an opaque, bodiless
    // 403 for callers who were never authenticated to begin with (login
    // included). Fixed at the actual source in SecurityConfig
    // (.requestMatchers("/error").permitAll()) — Spring Boot's default
    // BasicErrorController then renders the real, correct status and body
    // for anything not already handled above.

}