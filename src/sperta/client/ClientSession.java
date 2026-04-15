/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
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

				String certRequest = (String) inStream.readObject();
				if ("SEND-CERT".equals(certRequest)) {
					sendCertificateToServer(user, keystore, keystorePassword, outStream);
				}
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

	private void sendCertificateToServer(String user, String keystorePath, String keystorePassword,
										 ObjectOutputStream outStream) {
		try {
			KeyStore ks = KeyStore.getInstance("JKS");
			try (FileInputStream fis = new FileInputStream(keystorePath)) {
				ks.load(fis, keystorePassword.toCharArray());
			}

			Certificate cert = ks.getCertificate(user);

			if (cert == null) {
				java.util.Enumeration<String> aliases = ks.aliases();
				if (aliases.hasMoreElements()) {
					String firstAlias = aliases.nextElement();
					cert = ks.getCertificate(firstAlias);
					System.out.println("Certificado encontrado com alias '" + firstAlias + "' (user: " + user + ")");
				}
			}

			if (cert == null) {
				System.err.println("Nao foi possivel encontrar certificado na keystore.");
				outStream.writeInt(0);
				outStream.flush();
				return;
			}

			byte[] certBytes = cert.getEncoded();
			outStream.writeInt(certBytes.length);
			outStream.write(certBytes);
			outStream.flush();
			System.out.println("Certificado enviado ao servidor (" + certBytes.length + " bytes)");
		} catch (Exception e) {
			System.err.println("Erro ao enviar certificado: " + e.getMessage());
			try {
				outStream.writeInt(0);
				outStream.flush();
			} catch (IOException ignored) {
			}
		}
	}
}
