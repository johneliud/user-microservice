package io.github.johneliud.user_microservice.validation;

import io.github.johneliud.user_microservice.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(String username, String email, String password) {
        return validator.validate(new RegisterRequest(username, email, password));
    }

    // ── valid ─────────────────────────────────────────────────────────────────

    @Test
    void validRequest_noViolations() {
        assertTrue(validate("johndoe", "john@example.com", "Secret@123").isEmpty());
    }

    @Test
    void passwordWithAllSpecialChars_noViolations() {
        assertTrue(validate("johndoe", "john@example.com", "Pass1$word").isEmpty());
        assertTrue(validate("johndoe", "john@example.com", "Pass!1word").isEmpty());
        assertTrue(validate("johndoe", "john@example.com", "Pass%1word").isEmpty());
        assertTrue(validate("johndoe", "john@example.com", "Pass*1word").isEmpty());
        assertTrue(validate("johndoe", "john@example.com", "Pass?1word").isEmpty());
        assertTrue(validate("johndoe", "john@example.com", "Pass&1word").isEmpty());
    }

    // ── username ──────────────────────────────────────────────────────────────

    @Test
    void blankUsername_violationReported() {
        assertFalse(validate("", "john@example.com", "Secret@123").isEmpty());
    }

    @Test
    void usernameTooShort_violationReported() {
        assertFalse(validate("ab", "john@example.com", "Secret@123").isEmpty());
    }

    @Test
    void usernameTooLong_violationReported() {
        assertFalse(validate("a".repeat(51), "john@example.com", "Secret@123").isEmpty());
    }

    // ── email ─────────────────────────────────────────────────────────────────

    @Test
    void blankEmail_violationReported() {
        assertFalse(validate("johndoe", "", "Secret@123").isEmpty());
    }

    @Test
    void invalidEmailFormat_violationReported() {
        assertFalse(validate("johndoe", "not-an-email", "Secret@123").isEmpty());
        assertFalse(validate("johndoe", "nodomain@", "Secret@123").isEmpty());
        assertFalse(validate("johndoe", "@nodomain.com", "Secret@123").isEmpty());
    }

    // ── password ──────────────────────────────────────────────────────────────

    @Test
    void passwordTooShort_violationReported() {
        assertFalse(validate("johndoe", "john@example.com", "Sh0rt@").isEmpty());
    }

    @Test
    void passwordMissingUppercase_violationReported() {
        assertFalse(validate("johndoe", "john@example.com", "secret@123").isEmpty());
    }

    @Test
    void passwordMissingDigit_violationReported() {
        assertFalse(validate("johndoe", "john@example.com", "Secret@abc").isEmpty());
    }

    @Test
    void passwordMissingSpecialChar_violationReported() {
        assertFalse(validate("johndoe", "john@example.com", "SecretABC1").isEmpty());
    }

    @Test
    void passwordWithInvalidSpecialChar_violationReported() {
        // '#' is not in the allowed set @$!%*?&
        assertFalse(validate("johndoe", "john@example.com", "Secret#123").isEmpty());
    }

    @Test
    void blankPassword_violationReported() {
        assertFalse(validate("johndoe", "john@example.com", "").isEmpty());
    }
}