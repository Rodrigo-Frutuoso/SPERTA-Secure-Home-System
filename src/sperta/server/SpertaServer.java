/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

//Servidor SpertaServer

public class SpertaServer {

	private static final String USER_FILE = "src/sperta/data/user.txt";
	private static final String ATTESTATION_FILE = "src/sperta/client/attestation.txt";
	private static final Object fileLock = new Object();

	private static final int DEFAULT_PORT = 22345;

	public static void main(String[] args) {
		int port = DEFAULT_PORT;
		if (args.length >= 1) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				System.err.println("Porto inválido: " + args[0] + ". A usar porto por omissão: " + DEFAULT_PORT);
			}
		}
		System.out.println("servidor: main "+ port);
		SpertaServer server = new SpertaServer();
		server.startServer(port);
	}

	public void startServer(int port) {
		ServerSocket sSoc = null;
        
		try {
			sSoc = new ServerSocket(port);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(-1);
		}
         
		while(true) {
			try {
				Socket inSoc = sSoc.accept();
				ServerThread newServerThread = new ServerThread(inSoc);
				newServerThread.start();
		    }
		    catch (IOException e) {
		        e.printStackTrace();
		    }
		    
		}
		//sSoc.close();
	}

	// Lê o tamanho esperado do cliente a partir do ficheiro de atestação
	private static long readExpectedClientSize() {
		try (BufferedReader reader = new BufferedReader(new FileReader(ATTESTATION_FILE))) {
			String line = reader.readLine();
			if (line != null && line.contains(":")) {
				String[] parts = line.split(":", 2);
				return Long.parseLong(parts[1].trim());
			}
		} catch (IOException | NumberFormatException e) {
			System.err.println("Erro ao ler attestation.txt: " + e.getMessage());
		}
		return -1;
	}

	// Autentica user existente ou regista novo user
	private static synchronized boolean authenticateOrRegister(String user, String passwd) {
		if (user == null || passwd == null || user.isEmpty() || passwd.isEmpty()) {
			return false;
		}

		synchronized (fileLock) {
			Map<String, String> users = new HashMap<>();

			// Ler users existentes
			try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) continue;
					String[] lineContent = line.split(":", 2);
					if (lineContent.length == 2) {
						users.put(lineContent[0], lineContent[1]);
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler ficheiro de users: " + e.getMessage());
				return false;
			}

			// Verificar se user existe
			if (users.containsKey(user)) {
				// User existe - verificar password
				return users.get(user).equals(passwd);
			} else {
				// User novo - registar
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE, true))) {
					writer.write(user + ":" + passwd);
					writer.newLine();
					System.out.println("Novo user registado: " + user);
					return true;
				} catch (IOException e) {
					System.err.println("Erro ao registar novo user: " + e.getMessage());
					return false;
				}
			}
		}
	}

	// Threads utilizadas para comunicacao com os clientes
    // 1. Autenticar user + password ✓ (feito)
    // 2. Se bem-sucedido → entrar num loop para receber comandos do cliente
    // 3. Processar os comandos e responder
	class ServerThread extends Thread {

		private Socket socket = null;

		ServerThread(Socket inSoc) {
			socket = inSoc;
			System.out.println("thread do server para cada cliente");
		}
 
		public void run(){
			try {
				ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());

// Atestação: receber tamanho do .class do cliente
				long clientSize = inStream.readLong();
				long expectedSize = readExpectedClientSize();
				System.out.println("Atestação: recebido=" + clientSize + ", esperado=" + expectedSize);
				if (clientSize == expectedSize && expectedSize != -1) {
					outStream.writeObject("ATTESTATION_OK");
					outStream.flush();
				} else {
					outStream.writeObject("ATTESTATION_FAILED");
					outStream.flush();
					socket.close();
					return;
				}
				String user = null;
				String passwd = null;
			
				try {
					user = (String)inStream.readObject();
					passwd = (String)inStream.readObject();
					System.out.println("thread: depois de receber a password e o user");
				}catch (ClassNotFoundException e1) {
					e1.printStackTrace();
				}
 			
				// Autenticar ou registar user
				boolean authenticated = authenticateOrRegister(user, passwd);
				if (authenticated) {
					System.out.println("User autenticado: " + user);
					outStream.writeObject(Boolean.TRUE);

                    //fazer aqui loop para ler comandos do cliente e responder
				} else {
					System.out.println("Falha de autenticacao para user: " + user);
					outStream.writeObject(Boolean.FALSE);
				}

				outStream.close();
				inStream.close();
 			
				socket.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}