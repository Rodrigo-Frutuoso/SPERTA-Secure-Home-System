/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

//Servidor SpertaServer

public class SpertaServer {

	private static final int DEFAULT_PORT = 22345;
	private final AuthService authService;
	private final CommandService commandService;

	public SpertaServer() {
		DataRepository repository = new DataRepository();
		this.authService = new AuthService(repository);
		this.commandService = new CommandService(repository);
	}

	public static void main(String[] args) {
		int port = DEFAULT_PORT;
		if (args.length >= 1) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				System.err.println("Porto invalido: " + args[0] + ". A usar porto por omissao: " + DEFAULT_PORT);
			}
		}
		System.out.println("servidor: main " + port);
		SpertaServer server = new SpertaServer();
		server.startServer(port);
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
}
