/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

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

		SpertaClient client = new SpertaClient();
		client.authenticate(host, port, user, password);
	}

	public void authenticate(String host, int port, String user, String password) {
		try (Socket socket = new Socket(host, port);
			 ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
			 ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream())) {

			outStream.writeObject(user);
			outStream.writeObject(password);
			outStream.flush();

			Boolean authenticated = (Boolean) inStream.readObject();

			if (Boolean.TRUE.equals(authenticated)) {
				System.out.println("Autenticacao bem sucedida.");
			} else {
				System.out.println("Falha na autenticacao.");
			}
		} catch (IOException e) {
			System.err.println("Erro de comunicacao com o servidor: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			System.err.println("Resposta invalida do servidor.");
		}
	}
}
