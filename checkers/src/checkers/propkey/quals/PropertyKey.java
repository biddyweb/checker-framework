package checkers.propkey.quals;

import java.lang.annotation.*;
import checkers.quals.SubtypeOf;
import checkers.quals.TypeQualifier;
import checkers.quals.Unqualified;

/**
 * Indicates that the {@code String} type can be used as key in a
 * property file or resource bundle.
 *
 * @checker_framework_manual #propkey-checker Property File Checker
 */
@TypeQualifier
@SubtypeOf(Unqualified.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface PropertyKey {}