package com.pos_onlineshop.hybrid.exceptions;

import com.pos_onlineshop.hybrid.security.CashierUserDetailsService;
import com.pos_onlineshop.hybrid.security.JwtService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit's P2 finding: no global exception handler existed, so any exception that escaped a
 * controller uncaught fell through to Spring Boot's default error handling instead of this app's
 * own consistent shape - and, for a truly unexpected exception, could relay e.getMessage() (and
 * the exception's class name) straight to the client. These tests exercise GlobalExceptionHandler
 * end-to-end against ThrowingTestController (a test-only controller that always throws
 * uncaught - this codebase's real controllers almost all catch their own exceptions and shape
 * their own body, see the handler's class comment for why this only covers what escapes uncaught)
 * via @WebMvcTest, which auto-registers any @RestControllerAdvice found on the classpath, exactly
 * like a real request would hit it.
 */
// addFilters = false: this test only cares about GlobalExceptionHandler's behavior, not
// authentication/authorization - without it, @WebMvcTest auto-detects the app's real
// JwtAuthenticationFilter (any servlet Filter bean is swept into the slice) and either fails to
// construct it (its JwtService/UserAccountService/CashierUserDetailsService dependencies aren't
// provided here) or requires a valid token for every request, neither of which this test needs.
@WebMvcTest(controllers = ThrowingTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    // Not exercised (addFilters = false keeps JwtAuthenticationFilter out of the request path),
    // but the filter bean still needs to construct successfully during context refresh.
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserAccountService userAccountService;
    @MockitoBean private CashierUserDetailsService cashierUserDetailsService;

    @Test
    void resourceNotFoundProducesAStandardized404Body() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Widget not found with id: 42"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void insufficientInventoryProducesA409WithItsMessage() throws Exception {
        mockMvc.perform(get("/test/insufficient-inventory"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only 2 units available, 5 requested"));
    }

    @Test
    void anUnexpectedExceptionNeverLeaksItsRawMessage() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message", not(containsString("internal_secret_key"))));
    }

    @Test
    void beanValidationFailureProducesAStandardized400Body() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }
}
