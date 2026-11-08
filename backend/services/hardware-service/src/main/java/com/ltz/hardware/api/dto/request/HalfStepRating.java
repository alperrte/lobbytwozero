package com.ltz.hardware.api.dto.request;

import com.ltz.hardware.domain.entity.HardwareReviewRating;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/** Boş (null) kabul edilir; dolu ise 1–5 arasında 0,5 adımlı olmalıdır. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HalfStepRating.Validator.class)
public @interface HalfStepRating {
    String message() default "rating must be between 1 and 5 in steps of 0.5";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };

    class Validator implements ConstraintValidator<HalfStepRating, BigDecimal> {
        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            return value == null || HardwareReviewRating.isValid(value);
        }
    }
}
