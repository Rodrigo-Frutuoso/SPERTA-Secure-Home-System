/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.io.InputStream;
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

			// Atestação: enviar tamanho do próprio .class ao servidor
			long classSize = getClassSize();
			outStream.writeLong(classSize);
			outStream.flush();

			String attestResult = (String) inStream.readObject();
			if ("ATTESTATION_OK".equals(attestResult)) {
				System.out.println("ATTESTATION OK");
			} else {
				System.out.println("ATTESTATION FAILED");
				return;
			}

			// Autenticação
			Scanner scanner = new Scanner(System.in);
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

			if ("OK-NEW-USER".equals(authResult)) {
				System.out.println("OK-NEW-USER");
			} else {
				System.out.println("OK-USER");
			}
			commandLoop(user, outStream, inStream, scanner);
		} catch (IOException e) {
			System.err.println("Erro de comunicacao com o servidor: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			System.err.println("Resposta invalida do servidor.");
		}
	}

	private void printMenu() {
		System.out.println("");
		System.out.println("Comandos disponíveis:");
		System.out.println("  CREATE <hm>           # Criar casa <hm>");
		System.out.println("  ADD <user1> <hm> <s>  # Adicionar <user1> à casa <hm>, seção <s>");
		System.out.println("  RD <hm> <s>           # Registar dispositivo na casa <hm>, seção <s>");
		System.out.println("  EC <hm> <d> <int>     # Enviar valor <int> ao dispositivo <d> da casa <hm>");
		System.out.println("  RT <hm>               # Receber estados de todos os dispositivos da casa <hm>");
		System.out.println("  RH <hm> <d>           # Receber histórico (csv) do dispositivo <d> da casa <hm>");
		System.out.println("");
	}

	private void commandLoop(String user, ObjectOutputStream out, ObjectInputStream in, Scanner scanner) {
		printMenu();
		while (true) {
			System.out.print("> ");
			if (!scanner.hasNextLine()) break;
			String line = scanner.nextLine().trim();
			if (line.isEmpty()) continue;
			String[] parts = line.split("\\s+");
			String cmd = parts[0].toUpperCase();
			try {
				switch (cmd) {
					case "CREATE": if (checkArgs(parts, 2, "CREATE <hm>"))            handleCreate(parts[1], out, in);                    break;
					case "ADD":    if (checkArgs(parts, 4, "ADD <user1> <hm> <s>"))   handleAdd(parts[1], parts[2], parts[3], out, in);   break;
					case "RD":     if (checkArgs(parts, 3, "RD <hm> <s>"))            handleRD(parts[1], parts[2], out, in);              break;
					case "EC":     if (checkArgs(parts, 4, "EC <hm> <d> <int>"))      handleEC(parts[1], parts[2], parts[3], out, in);    break;
					case "RT":     if (checkArgs(parts, 2, "RT <hm>"))                handleRT(parts[1], out, in);                        break;
					case "RH":     if (checkArgs(parts, 3, "RH <hm> <d>"))            handleRH(parts[1], parts[2], out, in);              break;
					default:       System.out.println("Comando desconhecido: " + cmd); break;
				}
			} catch (IOException | ClassNotFoundException e) {
				System.err.println("Erro de comunicação: " + e.getMessage());
				break;
			}
		}
	}

	/** Devolve true se parts tem pelo menos 'min' tokens; caso contrário imprime uso e devolve false. */
	private boolean checkArgs(String[] parts, int min, String usage) {
		if (parts.length >= min) return true;
		System.out.println("Uso: " + usage);
		return false;
	}

	// ─── Handlers de comandos (cliente) ────────────────────────────────────────

	private void handleCreate(String hm, ObjectOutputStream out, ObjectInputStream in)
			throws IOException, ClassNotFoundException {
		out.writeObject("CREATE " + hm);
		out.flush();
		String response = (String) in.readObject();
		System.out.println(response);
	}

	private void handleAdd(String user1, String hm, String s,
			ObjectOutputStream out, ObjectInputStream in)
			throws IOException, ClassNotFoundException {
		// TODO: enviar "ADD <user1> <hm> <s>" ao servidor e imprimir a resposta
	}

	private void handleRD(String hm, String s,
			ObjectOutputStream out, ObjectInputStream in)
			throws IOException, ClassNotFoundException {
		// TODO: enviar "RD <hm> <s>" ao servidor e imprimir a resposta
	}

	private void handleEC(String hm, String d, String intVal,
			ObjectOutputStream out, ObjectInputStream in)
			throws IOException, ClassNotFoundException {
		// TODO: enviar "EC <hm> <d> <int>" ao servidor e imprimir a resposta
	}

	private void handleRT(String hm, ObjectOutputStream out, ObjectInputStream in)
			throws IOException, ClassNotFoundException {
		// TODO: enviar "RT <hm>", ler resposta OK + long tamanho + bytes,
		//       guardar ficheiro localmente e imprimir resultado
	}

	private void handleRH(String hm, String d,
			ObjectOutputStream out, ObjectInputStream in)
			throws IOException, ClassNotFoundException {
		// TODO: enviar "RH <hm> <d>", ler resposta OK + long tamanho + bytes,
		//       guardar ficheiro CSV localmente e imprimir resultado
	}

	private long getClassSize() {
		try (InputStream is = SpertaClient.class.getResourceAsStream("SpertaClient.class")) {
			if (is == null) return -1;
			return is.readAllBytes().length;
		} catch (IOException e) {
			return -1;
		}
	}
}
