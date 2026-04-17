
/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public class AuthService {

	private static final String ATTESTATION_FILE = "src/sperta/server/attestation.txt";
	private final DataRepository repository;
	private final Set<String> activeUsers;

	public AuthService(DataRepository repository) {
		this.repository = repository;
		this.activeUsers = new HashSet<>();
	}

	public boolean performAttestation(ObjectInputStream inStream, ObjectOutputStream outStream) throws IOException {
		long nonce = new SecureRandom().nextLong();
		outStream.writeLong(nonce);
		outStream.flush();
		int hashLen = inStream.readInt();
		byte[] clientHash = new byte[hashLen];
		inStream.readFully(clientHash);
		// "o servidor o comparará com o hash calculado localmente com base na
		// concatenação entre o mesmo nonce e uma cópia de referência da aplicação
		// SpertaClient"
		byte[] expectedHash = computeAttestationHash(nonce);
		if (expectedHash == null) {
			outStream.writeObject("NOK-ATTEST");
			outStream.flush();
			return false;
		}
		if (MessageDigest.isEqual(clientHash, expectedHash)) {
			outStream.writeObject("OK-ATTEST");
			outStream.flush();
			return true;
		}
		outStream.writeObject("NOK-ATTEST");
		outStream.flush();
		return false;
	}

	public String authenticate(ObjectInputStream inStream, ObjectOutputStream outStream) throws IOException {
		String user = null;
		String authResult;
		do {
			try {
				user = (String) inStream.readObject();
				String passwd = (String) inStream.readObject();
				authResult = authenticateOrRegister(user, passwd);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
				authResult = "WRONG-PWD";
			}

			if ("OK-USER".equals(authResult) || "OK-NEW-USER".equals(authResult)) {
				if (!registerActiveUser(user)) {
					authResult = "USER-ALREADY-CONNECTED";
				}
			}

			outStream.writeObject(authResult);
			outStream.flush();
			System.out.println("Autenticacao user '" + user + "': " + authResult);

			if ("OK-NEW-USER".equals(authResult)) {
				try {
					receiveCertificateFromClient(user, inStream, outStream);
				} catch (Exception e) {
					System.err.println("Erro ao receber certificado do user '" + user + "': " + e.getMessage());
				}
			}
		} while ("WRONG-PWD".equals(authResult));

		if ("USER-ALREADY-CONNECTED".equals(authResult)) {
			return null;
		}
		return user;
	}

	private void receiveCertificateFromClient(String user, ObjectInputStream inStream, ObjectOutputStream outStream)
			throws IOException, ClassNotFoundException {
		outStream.writeObject("SEND-CERT");
		outStream.flush();

		int certLen = inStream.readInt();
		byte[] certBytes = new byte[certLen];
		inStream.readFully(certBytes);

		repository.saveUserCertificate(user, certBytes);
		System.out.println("Certificado do user '" + user + "' guardado (" + certLen + " bytes)");
	}

	private boolean registerActiveUser(String user) {
		if (user == null || user.isEmpty()) {
			return false;
		}
		synchronized (activeUsers) {
			if (activeUsers.contains(user)) {
				return false;
			}
			activeUsers.add(user);
			return true;
		}
	}

	public void unregisterActiveUser(String user) {
		if (user == null || user.isEmpty()) {
			return;
		}
		synchronized (activeUsers) {
			activeUsers.remove(user);
		}
	}

	private String authenticateOrRegister(String user, String passwd) {
		if (user == null || passwd == null || user.isEmpty() || passwd.isEmpty()) {
			return "WRONG-PWD";
		}

		String[] record = repository.getUserRecord(user);
		if (record == null) {
			if (repository.addUser(user, passwd)) {
				System.out.println("Novo user registado: " + user);
				return "OK-NEW-USER";
			}
			return "WRONG-PWD";
		}

		try {
			byte[] storedHash = CryptoUtils.fromBase64(record[1]);
			byte[] salt = CryptoUtils.fromBase64(record[2]);
			byte[] computedHash = CryptoUtils.hashPassword(passwd, salt);

			if (CryptoUtils.isHashEqual(storedHash, computedHash)) {
				return "OK-USER";
			}
		} catch (IllegalArgumentException e) {
			System.err.println("Erro ao descodificar hash/salt do user '" + user + "': " + e.getMessage());
		}

		return "WRONG-PWD";
	}

	private byte[] computeAttestationHash(long nonce) {
		try {
			String jarPath = readReferenceJarPath();
			byte[] jarBytes = Files.readAllBytes(Path.of(jarPath));

			MessageDigest md = MessageDigest.getInstance("SHA-256");
			ByteBuffer bb = ByteBuffer.allocate(8);
			bb.putLong(nonce);
			md.update(bb.array());
			md.update(jarBytes);
			return md.digest();
		} catch (Exception e) {
			System.err.println("Erro na atestação: " + e.getMessage());
			return null;
		}
	}

	private String readReferenceJarPath() {
		try (BufferedReader reader = new BufferedReader(new FileReader(ATTESTATION_FILE))) {
			String line = reader.readLine();
			if (line != null && line.contains(":")) {
				String[] parts = line.split(":", 2);
				return parts[1].trim();
			}
		} catch (IOException e) {
			System.err.println("Erro ao ler attestation.txt: " + e.getMessage());
		}
		return null;
	}
}
