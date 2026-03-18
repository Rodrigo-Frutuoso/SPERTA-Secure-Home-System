/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientSessionHandler extends Thread {

	private final Socket socket;
	private final AuthService authService;
	private final CommandService commandService;

	public ClientSessionHandler(Socket socket, AuthService authService, CommandService commandService) {
		this.socket = socket;
		this.authService = authService;
		this.commandService = commandService;
		System.out.println("thread do server para cada cliente");
	}

	@Override
	public void run() {
		try (ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
				 ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream())) {

			if (!authService.performAttestation(inStream, outStream)) {
				return;
			}

			String user = authService.authenticate(inStream, outStream);
			if (user == null) {
				return;
			}

			try {
				while (true) {
					String command = (String) inStream.readObject();
					if (command == null) {
						break;
					}
					commandService.handleCommand(command, user, outStream);
				}
			} catch (EOFException eof) {
				System.out.println("Cliente " + socket.getInetAddress() + " desconectado.");
			} catch (ClassNotFoundException e) {
				System.err.println("Erro de protocolo: " + e.getMessage());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}
	}
}
