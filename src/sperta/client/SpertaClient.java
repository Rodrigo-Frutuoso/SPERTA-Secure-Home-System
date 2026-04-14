/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

public class SpertaClient {

	private static final int DEFAULT_PORT = 22345;

	public static void main(String[] args) {
		if (args.length < 7) {
			System.err.println("Uso: SpertaClient <serverAddress> <truststore> <truststore-password> <keystore> <keystore-password> <user-id> <password>");
			System.err.println("     serverAddress: <IP/hostname>[:porto]");
			System.exit(-1);
		}

		String serverAddress = args[0];
		String truststore = args[1];
		String truststorePassword = args[2];
		String keystore = args[3];
		String keystorePassword = args[4];
		String user = args[5];
		String password = args[6];

		String host;
		int port = DEFAULT_PORT;
		if (serverAddress.contains(":")) {
			String[] parts = serverAddress.split(":", 2);
			host = parts[0];
			try {
				port = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				System.err.println("Porto invalido: " + parts[1] + ". A usar porto por omissao: " + DEFAULT_PORT);
			}
		} else {
			host = serverAddress;
		}

		ClientSession session = new ClientSession(host, port);
		session.authenticateAndRun(user, password, truststore, truststorePassword, keystore, keystorePassword);
	}

	public static long getAttestationSize() {
		try {
			URL location = SpertaClient.class.getProtectionDomain().getCodeSource().getLocation();
			if (location == null) {
				return -1;
			}

			File executable = new File(location.toURI());
			if (executable.isFile() && executable.getName().toLowerCase().endsWith(".jar")) {
				return executable.length();
			}
		} catch (URISyntaxException | SecurityException e) {
			return -1;
		}

		return -1;
	}
}
