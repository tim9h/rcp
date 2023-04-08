package dev.tim9h.rcp.core.logging;

import java.lang.reflect.Field;

import org.apache.logging.log4j.Logger;

import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

import dev.tim9h.rcp.logging.InjectLogger;

public class Log4jTypeListener implements TypeListener {

	@Override
	public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
		var clazz = typeLiteral.getRawType();
		while (clazz != null) {
			for (Field field : clazz.getDeclaredFields()) {
				if (field.getType() == Logger.class && field.isAnnotationPresent(InjectLogger.class)) {
					typeEncounter.register(new Log4JMembersInjector<>(field));
				}
			}
			clazz = clazz.getSuperclass();
		}
	}

}
