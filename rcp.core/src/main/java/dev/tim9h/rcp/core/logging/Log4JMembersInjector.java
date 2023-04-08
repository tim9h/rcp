package dev.tim9h.rcp.core.logging;

import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.MembersInjector;

class Log4JMembersInjector<T> implements MembersInjector<T> {

	private final Field field;

	private final Logger logger;

	@SuppressWarnings("java:S3011")
	Log4JMembersInjector(Field field) {
		this.field = field;
		this.logger = LogManager.getLogger(field.getDeclaringClass());
		field.setAccessible(true);
	}

	@Override
	@SuppressWarnings("java:S3011")
	public void injectMembers(T t) {
		try {
			field.set(t, logger);
		} catch (IllegalAccessException e) {
			logger.error(() -> "Unable to inject logger", e);
		}
	}
}