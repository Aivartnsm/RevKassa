package ee.revkassa.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An IBAN with a valid ISO 13616 check (mod 97). Spaces and lower case are accepted; {@link Iban#normalize} gives
 * the form to save. null is left to @NotNull.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidIbanValidator.class)
public @interface ValidIban {

    String message() default "is not a valid IBAN";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
