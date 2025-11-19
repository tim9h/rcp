package dev.tim9h.rcp.core.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.apache.logging.log4j.Logger;

import com.google.inject.Singleton;

import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.service.CryptoService;

@Singleton
public class CryptoServiceImpl implements CryptoService {

	@InjectLogger
	private Logger logger;

	@Override
	public String gernateApiKey() {
		var bytes = new byte[32];
		var random = new SecureRandom();
		random.nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	@Override
	public String hashSha256(String data) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			var encodedHash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(encodedHash);
		} catch (NoSuchAlgorithmException e) {
			logger.error(() -> "Unable to hash: SHA-256 algorithm not found", e);
		}
		return null;
	}
	

	@Override
	public boolean hashMatches(String providedPassword, String storedHash) {
		return hashSha256(providedPassword).equals(storedHash); 
	}

}
