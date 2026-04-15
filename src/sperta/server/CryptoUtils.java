
/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoUtils {

	private static final String HASH_ALGORITHM = "SHA-256";
	private static final int SALT_SIZE = 16; // 16 bytes

	public static byte[] generateSalt() {
		byte[] salt = new byte[SALT_SIZE];
		new SecureRandom().nextBytes(salt);
		return salt;
	}

	public static byte[] hashPassword(String password, byte[] salt) {
		try {
			MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
			md.update(password.getBytes());
			md.update(salt);
			return md.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Algoritmo " + HASH_ALGORITHM + " nao disponivel", e);
		}
	}

	public static String toBase64(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}

	public static byte[] fromBase64(String encoded) {
		return Base64.getDecoder().decode(encoded);
	}

	public static boolean isHashEqual(byte[] hash1, byte[] hash2) {
		return MessageDigest.isEqual(hash1, hash2);
	}
}
