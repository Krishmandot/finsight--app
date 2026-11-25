package com.finsight.api.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/** Transaction dates must be plausible: not before 1990 and not more than one year in the future. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTransactionDate.Validator.class)
public @interface ValidTransactionDate {

    String message() default "Date must be between 1990 and one year from today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Rules {
        public static final LocalDate EARLIEST = LocalDate.of(1990, 1, 1);

        private Rules() {
        }

        public static LocalDate latest() {
            return LocalDate.now().plusYears(1);
        }

        public static boolean isPlausible(LocalDate date) {
            return date != null && !date.isBefore(EARLIEST) && !date.isAfter(latest());
        }
    }

    class Validator implements ConstraintValidator<ValidTransactionDate, LocalDate> {
        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return value == null || Rules.isPlausible(value);
        }
    }
}
