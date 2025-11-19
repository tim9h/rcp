package dev.tim9h.rcp.service;

public interface CryptoService {
	
	public String gernateApiKey();
	
	public String hashSha256(String data);
	
	public boolean hashMatches(String providedPassword, String storedHash);
	
}
