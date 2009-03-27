package org.kohsuke;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Indicates that this class name should be listed into the <tt>META-INF/services/CONTRACTNAME</tt>.
 *
 * <p>
 * If the class for which this annotation is placaed only have one base class or one interface,
 * then the CONTRACTNAME is the fully qualified name of that type.
 *
 * <p>
 * Otherwise, the {@link #value()} element is required to specify the contract type name.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(SOURCE)
@Documented
@Target(TYPE)
public @interface MetaInfServices {
    Class value() default void.class;
}
