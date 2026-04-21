/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class SpertaServer {

	private static final int DEFAULT_PORT = 22345;
	private final AuthService authService;
	private final CommandService commandService;

	public SpertaServer(String cipherPassword) {
		DataRepository repository = new DataRepository(cipherPassword);
		this.authService = new AuthService(repository);
		this.commandService = new CommandService(repository);
	}

	public static void main(String[] args) {
		if (args.length < 4) {
			System.out.println("Uso: SpertaServer <port> <cipher-password> <keystore> <keystore-password>");
			return;
		}

		int port = Integer.parseInt(args[0]);
		String cipherPassword = args[1];
		String keystorePath = args[2];
		String keystorePassword = args[3];

		// Codigo da TP
		System.setProperty("javax.net.ssl.keyStore", keystorePath);
		System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);

		SpertaServer server = new SpertaServer(cipherPassword);
		server.startSSLServer(port);
	}

	public void startServer(int port) {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			while (true) {
				Socket inSoc = serverSocket.accept();
				ClientSessionHandler session = new ClientSessionHandler(inSoc, authService, commandService);
				session.start();
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
	}

	public void startSSLServer(int port) {
		try {
			ServerSocketFactory ssf = SSLServerSocketFactory.getDefault();
			SSLServerSocket ss = (SSLServerSocket) ssf.createServerSocket(port);
			System.out.println("Servidor TLS a escutar na porta " + port);

			while (true) {
				SSLSocket clientSocket = (SSLSocket) ss.accept();
				ClientSessionHandler session = new ClientSessionHandler(clientSocket, authService, commandService);
				session.start();
			}
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
	}
}
