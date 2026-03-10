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
	private static final String HOUSES_FILE = "src/sperta/data/houses.txt";
	private static final String STATES_DIR  = "src/sperta/data/states/";
	private static final String LOGS_DIR    = "src/sperta/data/logs/";
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
	// Retorna: "OK-USER", "OK-NEW-USER" ou "WRONG-PWD"
	private static String authenticateOrRegister(String user, String passwd) {
		if (user == null || passwd == null || user.isEmpty() || passwd.isEmpty()) {
			return "WRONG-PWD";
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
				return "WRONG-PWD";
			}

			// Verificar se user existe
			if (users.containsKey(user)) {
				if (users.get(user).equals(passwd)) {
					return "OK-USER";
				} else {
					return "WRONG-PWD";
				}
			} else {
				// User novo - registar
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE, true))) {
					writer.write(user + ":" + passwd);
					writer.newLine();
					System.out.println("Novo user registado: " + user);
					return "OK-NEW-USER";
				} catch (IOException e) {
					System.err.println("Erro ao registar novo user: " + e.getMessage());
					return "WRONG-PWD";
				}
			}
		}
	}

	// ─── Handlers de comandos (servidor) ───────────────────────────────────────

	private static void handleCreate(String hm, String owner, ObjectOutputStream out)
			throws IOException {
		// TODO: verificar se hm já existe; se não → criar entrada em HOUSES_FILE com owner
		//       responder OK ou NOK
	}

	private static void handleAdd(String user1, String hm, String s,
			String requester, ObjectOutputStream out) throws IOException {
		// TODO: verificar se requester é owner de hm (senão → NOPERM)
		//       verificar se hm existe (senão → NOHM)
		//       verificar se user1 existe (senão → NOUSER)
		//       adicionar user1 com permissão s em hm → OK
	}

	private static void handleRD(String hm, String s,
			String requester, ObjectOutputStream out) throws IOException {
		// TODO: verificar se requester é owner de hm (senão → NOPERM)
		//       verificar se hm existe (senão → NOHM)
		//       incrementar contador da seção s em hm, registar dispositivo → OK
	}

	private static void handleEC(String hm, String d, String intVal,
			String requester, ObjectOutputStream out) throws IOException {
		// TODO: verificar se hm existe (senão → NOHM)
		//       verificar se d existe em hm (senão → NOD)
		//       verificar permissões do requester (senão → NOPERM)
		//       validar intVal ∈ [0..600] (senão → NOK)
		//       gravar estado atual em STATES_DIR e entrada em LOGS_DIR/<hm>/<d>.csv → OK
	}

	private static void handleRT(String hm, String requester, ObjectOutputStream out)
			throws IOException {
		// TODO: verificar se hm existe (senão → NOHM)
		//       verificar permissões (senão → NOPERM)
		//       ler ficheiro de estados dos dispositivos de hm
		//       se vazio → NODATA; senão → OK + long tamanho + bytes
	}

	private static void handleRH(String hm, String d,
			String requester, ObjectOutputStream out) throws IOException {
		// TODO: verificar se hm existe (senão → NOHM)
		//       verificar se d existe em hm (senão → NOD)
		//       verificar permissões (senão → NOPERM)
		//       ler LOGS_DIR/<hm>/<d>.csv
		//       se vazio/inexistente → NODATA; senão → OK + long tamanho + bytes
	}

	// ─── Helpers ─────────────────────────────────────────────────────────────────

	private static boolean houseExists(String hm) {
		// TODO: verificar em HOUSES_FILE se existe entrada para hm
		return false;
	}

	private static boolean isOwner(String hm, String user) {
		// TODO: verificar em HOUSES_FILE se user é o owner de hm
		return false;
	}

	// s: identificador de seção (letra) ou "all"
	private static boolean hasPermission(String hm, String user, String s) {
		// TODO: verificar em HOUSES_FILE se user é owner de hm,
		//       ou se tem permissão explícita para a seção s (ou "all")
		return false;
	}

	private static boolean deviceExists(String hm, String device) {
		// TODO: verificar em HOUSES_FILE se device existe na casa hm
		return false;
	}

	private static boolean userExists(String user) {
		// TODO: verificar em USER_FILE se user existe
		return false;
	}

	// Threads utilizadas para comunicacao com os clientes
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
				String authResult;
				do {
					try {
						user = (String) inStream.readObject();
						String passwd = (String) inStream.readObject();
						authResult = authenticateOrRegister(user, passwd);
					} catch (ClassNotFoundException e1) {
						e1.printStackTrace();
						authResult = "WRONG-PWD";
					}
					outStream.writeObject(authResult);
					outStream.flush();
					System.out.println("Autenticacao user '" + user + "': " + authResult);
				} while ("WRONG-PWD".equals(authResult));

				try {
					while (true) {
						String command = (String) inStream.readObject();
						if (command == null) break;
						String[] parts = command.split("\\s+");
						String cmd = parts[0].toUpperCase();
						boolean ok;
						switch (cmd) {
							case "CREATE": ok = parts.length >= 2; if (ok) handleCreate(parts[1], user, outStream);                             break;
							case "ADD":    ok = parts.length >= 4; if (ok) handleAdd(parts[1], parts[2], parts[3], user, outStream);            break;
							case "RD":     ok = parts.length >= 3; if (ok) handleRD(parts[1], parts[2], user, outStream);                       break;
							case "EC":     ok = parts.length >= 4; if (ok) handleEC(parts[1], parts[2], parts[3], user, outStream);             break;
							case "RT":     ok = parts.length >= 2; if (ok) handleRT(parts[1], user, outStream);                                 break;
							case "RH":     ok = parts.length >= 3; if (ok) handleRH(parts[1], parts[2], user, outStream);                       break;
							default:       ok = false; break;
						}
						if (!ok) {
							outStream.writeObject("NOK");
							outStream.flush();
						}
					}
				} catch (java.io.EOFException eof) {
					System.out.println("Cliente " + socket.getInetAddress() + " desconectado.");
				} catch (ClassNotFoundException e) {
					System.err.println("Erro de protocolo: " + e.getMessage());
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