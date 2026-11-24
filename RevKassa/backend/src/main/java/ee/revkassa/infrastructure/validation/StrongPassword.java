package ee.revkassa.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A new password that follows {@link PasswordRules}. The field error names the first rule it breaks.
 * null is left to @NotNull.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    String message() default "does not follow the password rules";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
