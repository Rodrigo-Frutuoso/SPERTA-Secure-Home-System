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

	public static void main(String[] args) {
		String user;
		String password;

		if (args.length >= 2) {
			user = args[0];
			password = args[1];
		} else {
			Scanner scanner = new Scanner(System.in);
			System.out.print("User: ");
			user = scanner.nextLine();
			System.out.print("Password: ");
			password = scanner.nextLine();
			scanner.close();
		}

		SpertaClient client = new SpertaClient();
		client.authenticate(user, password);
	}

	public void authenticate(String user, String password) {
		try (Socket socket = new Socket("localhost", 23456);
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
