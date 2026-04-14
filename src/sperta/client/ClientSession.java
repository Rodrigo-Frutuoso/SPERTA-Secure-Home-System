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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class ClientSession {

	private final String host;
	private final int port;

	public ClientSession(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void authenticateAndRun(String user, String password, String truststore, 
								  String truststorePassword, String keystore, String keystorePassword) {
		// Configurar propriedades TLS antes de criar SSLSocket
		System.setProperty("javax.net.ssl.trustStore", truststore);
		System.setProperty("javax.net.ssl.trustStorePassword", truststorePassword);
		
		try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
				.createSocket(host, port);
			 ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
			 ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
			 Scanner scanner = new Scanner(System.in)) {

			long jarSize = SpertaClient.getAttestationSize();
			if (jarSize < 0) {
				System.out.println("ATTESTATION FAILED");
				System.err.println("Nao foi possivel obter o tamanho do JAR do cliente. Execute via JAR.");
				return;
			}

			outStream.writeLong(jarSize);
			outStream.flush();

			String attestResult = (String) inStream.readObject();
			if ("ATTESTATION_OK".equals(attestResult)) {
				System.out.println("ATTESTATION OK");
			} else {
				System.out.println("ATTESTATION FAILED");
				return;
			}

			String currentPassword = password;
			String authResult;
			do {
				outStream.writeObject(user);
				outStream.writeObject(currentPassword);
				outStream.flush();
				authResult = (String) inStream.readObject();
				if ("WRONG-PWD".equals(authResult)) {
					System.out.println("Password incorreta. Tente novamente.");
					System.out.print("Password: ");
					currentPassword = scanner.nextLine();
				}
			} while ("WRONG-PWD".equals(authResult));

			if ("USER-ALREADY-CONNECTED".equals(authResult)) {
				System.out.println("USER-ALREADY-CONNECTED");
				System.out.println("Este utilizador ja tem uma sessao ativa. Tente novamente mais tarde.");
				return;
			}

			if ("OK-NEW-USER".equals(authResult)) {
				System.out.println("OK-NEW-USER");
			} else {
				System.out.println("OK-USER");
			}

			ClientCommandLoop commandLoop = new ClientCommandLoop(outStream, inStream, scanner);
			commandLoop.run();
		} catch (IOException e) {
			System.err.println("Erro de comunicacao com o servidor: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			System.err.println("Resposta invalida do servidor.");
		}
	}
}
