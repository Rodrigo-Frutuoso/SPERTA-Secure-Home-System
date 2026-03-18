/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.io.InputStream;

public class SpertaClient {

	private static final int DEFAULT_PORT = 22345;

	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("Uso: SpertaClient <serverAddress> <user-id> <password>");
			System.err.println("     serverAddress: <IP/hostname>[:porto]");
			System.exit(-1);
		}

		String serverAddress = args[0];
		String user = args[1];
		String password = args[2];

		String host;
		int port = DEFAULT_PORT;
		if (serverAddress.contains(":")) {
			String[] parts = serverAddress.split(":", 2);
			host = parts[0];
			try {
				port = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				System.err.println("Porto inválido: " + parts[1] + ". A usar porto por omissão: " + DEFAULT_PORT);
			}
		} else {
			host = serverAddress;
		}

		ClientSession session = new ClientSession(host, port);
		session.authenticateAndRun(user, password);
	}

	public static long getClassSize() {
		try (InputStream is = SpertaClient.class.getResourceAsStream("SpertaClient.class")) {
			if (is == null) return -1;
			return is.readAllBytes().length;
		} catch (IOException e) {
			return -1;
		}
	}
}
