package xyz.staffjoy.common.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.TimeZone;

/**
 * 时区验证
 */
public class TimezoneValidator implements ConstraintValidator<Timezone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return Arrays.asList(TimeZone.getAvailableIDs()).contains(value);
    }
}
