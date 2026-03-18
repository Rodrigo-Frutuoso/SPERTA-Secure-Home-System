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

public class AuthService {

	private static final String ATTESTATION_FILE = "src/sperta/server/attestation.txt";
	private final DataRepository repository;

	public AuthService(DataRepository repository) {
		this.repository = repository;
	}

	public boolean performAttestation(ObjectInputStream inStream, ObjectOutputStream outStream) throws IOException {
		long clientSize = inStream.readLong();
		long expectedSize = readExpectedClientSize();
		System.out.println("Atestação: recebido=" + clientSize + ", esperado=" + expectedSize);
		if (clientSize == expectedSize && expectedSize != -1) {
			outStream.writeObject("ATTESTATION_OK");
			outStream.flush();
			return true;
		}

		outStream.writeObject("ATTESTATION_FAILED");
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
			outStream.writeObject(authResult);
			outStream.flush();
			System.out.println("Autenticacao user '" + user + "': " + authResult);
		} while ("WRONG-PWD".equals(authResult));
		return user;
	}

	private String authenticateOrRegister(String user, String passwd) {
		if (user == null || passwd == null || user.isEmpty() || passwd.isEmpty()) {
			return "WRONG-PWD";
		}

		String storedPassword = repository.getUserPassword(user);
		if (storedPassword == null) {
			if (repository.addUser(user, passwd)) {
				System.out.println("Novo user registado: " + user);
				return "OK-NEW-USER";
			}
			return "WRONG-PWD";
		}

		if (storedPassword.equals(passwd)) {
			return "OK-USER";
		}
		return "WRONG-PWD";
	}

	private long readExpectedClientSize() {
		try (BufferedReader reader = new BufferedReader(new FileReader(ATTESTATION_FILE))) {
			String line = reader.readLine();
			if (line != null && line.contains(":")) {
				String[] parts = line.split(":", 2);
				return Long.parseLong(parts[1].trim());
			}
		} catch (IOException | NumberFormatException e) {
			System.err.println("Erro ao ler attestation.txt: " + e.getMessage());
		}
		return -1;
	}
}
