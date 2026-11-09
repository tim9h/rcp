package dev.tim9h.rcp.service;

public interface CryptoService {

	String gernateApiKey();

	String hashSha256(String data);

	boolean hashMatches(String providedPassword, String storedHash);

}
