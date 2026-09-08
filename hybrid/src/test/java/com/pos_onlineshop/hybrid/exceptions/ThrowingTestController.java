package com.pos_onlineshop.hybrid.exceptions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller for GlobalExceptionHandlerTest - throws deliberately, uncaught, so the
 * advice's handling of each exception type can be exercised end-to-end via MockMvc. */
@RestController
public class ThrowingTestController {

    @GetMapping("/test/not-found")
    public String notFound() {
        throw new ResourceNotFoundException("Widget not found with id: 42");
    }

    @GetMapping("/test/insufficient-inventory")
    public String insufficientInventory() {
        throw new InsufficientInventoryException("Only 2 units available, 5 requested");
    }

    @GetMapping("/test/boom")
    public String boom() {
        throw new IllegalStateException("column 'internal_secret_key' violates not-null constraint");
    }

    @PostMapping("/test/validated")
    public String validated(@RequestBody @Valid ValidatedBody body) {
        return "ok";
    }

    public static class ValidatedBody {
        @NotBlank
        public String name;
    }
}
