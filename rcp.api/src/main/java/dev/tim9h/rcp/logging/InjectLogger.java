package dev.tim9h.rcp.logging;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RUNTIME)
public @interface InjectLogger {

	/**
	 * Logger name, default is class name
	 */
	String value() default "";

	/**
	 * If true, name is absolute else use class name + name
	 */
	boolean absolute() default false;

}
