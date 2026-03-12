/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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

	private static final String USER_FILE = "src/sperta/data/users.txt";
	private static final String ATTESTATION_FILE = "src/sperta/server/attestation.txt";;
	private static final String HOUSES_FILE = "src/sperta/data/all_houses.txt";
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
		// Garantir que os ficheiros e diretorios necessarios existem
		try {
			new File("src/sperta/data/houses").mkdirs();
			new File("src/sperta/data/states").mkdirs();
			new File("src/sperta/data/logs").mkdirs();
			File housesFile = new File("src/sperta/data/all_houses.txt");
			if (!housesFile.exists()) housesFile.createNewFile();
			File usersFile = new File("src/sperta/data/users.txt");
			if (!usersFile.exists()) usersFile.createNewFile();
		} catch (IOException e) {
			System.err.println("Erro ao inicializar estrutura de dados: " + e.getMessage());
		}
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

	// Retorna: "OK-USER", "OK-NEW-USER" ou "WRONG-PWD"
	private static String authenticateOrRegister(String user, String passwd) {
		if (user == null || passwd == null || user.isEmpty() || passwd.isEmpty()) {
			return "WRONG-PWD";
		}

		synchronized (fileLock) {
			Map<String, String> users = new HashMap<>();

			// Garantir que o ficheiro e diretório existem
			File userFile = new File(USER_FILE);
			if (!userFile.exists()) {
				userFile.getParentFile().mkdirs();
				try {
					userFile.createNewFile();
				} catch (IOException e) {
					System.err.println("Erro ao criar ficheiro de users: " + e.getMessage());
					return "WRONG-PWD";
				}
			}

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
		synchronized (fileLock) {

			if (houseExists(hm)) {
				out.writeObject("NOK");
				out.flush();
				return;
			}


			try (BufferedWriter writer = new BufferedWriter(new FileWriter(HOUSES_FILE, true))) {
				writer.write(hm + "|" + owner);
				writer.newLine();
			}

			File houseFile = new File("src/sperta/data/houses/" + hm + ".txt");
			houseFile.getParentFile().mkdirs();
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(houseFile))) {
				writer.write("[permissions]");
				writer.newLine();
				writer.newLine();
				writer.write("[devices]");
				writer.newLine();
			}

			out.writeObject("OK");
			out.flush();
		}
	}


	private static void handleAdd(String user1, String hm, String s,
			String requester, ObjectOutputStream out) throws IOException {
		synchronized (fileLock) {
			// verificar se hm existe (senão → NOHM)
			if (!houseExists(hm)) {
				out.writeObject("NOHM");
				out.flush();
				return;
			}
			// verificar se requester é owner de hm (senão → NOPERM)
			if (!isOwner(hm, requester)) {
				out.writeObject("NOPERM");
				out.flush();
				return;
			}
			 // verificar se user1 existe (senão → NOUSER)
			if (!userExists(user1)) {
				out.writeObject("NOUSER");
				out.flush();
				return;
			}

			// adicionar user1 com permissão s em hm → OK

			String houseFilePath = "src/sperta/data/houses/" + hm + ".txt";
			File houseFile = new File(houseFilePath);

			// Ler todas as linhas do ficheiro
			java.util.List<String> lines = new java.util.ArrayList<>();
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFile))) {
				String line;
				while ((line = reader.readLine()) != null) lines.add(line);
			}

			// Procurar linha existente do user1 em [permissions] e actualizar ou inserir
			boolean inPermissions = false;
			boolean userFound = false;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.trim().equals("[permissions]")) { inPermissions = true; continue; }
				if (line.trim().equals("[devices]")) break;
				if (!inPermissions || line.trim().isEmpty()) continue;
				String[] parts = line.split("\\|", 2);
				if (parts.length == 2 && parts[0].equals(user1)) {
					// Já tem permissões: adicionar a nova secção se não existir
					if (!parts[1].equals("all") && !s.equals("all")) {
						java.util.Set<String> secs = new java.util.LinkedHashSet<>(
								java.util.Arrays.asList(parts[1].split(",")));
						secs.add(s);
						lines.set(i, user1 + "|" + String.join(",", secs));
					} else {
						lines.set(i, user1 + "|all");
					}
					userFound = true;
					break;
				}
			}

			if (!userFound) {
				// Inserir nova linha antes de [devices]
				int devicesIdx = lines.indexOf("[devices]");
				if (devicesIdx == -1) devicesIdx = lines.size();
				lines.add(devicesIdx, user1 + "|" + s);
			}

			// Reescrever o ficheiro
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(houseFile, false))) {
				for (String line : lines) {
					writer.write(line);
					writer.newLine();
				}
			}

			out.writeObject("OK");
			out.flush();
		}
	}

	private static void handleRD(String hm, String s,
			String requester, ObjectOutputStream out) throws IOException {
		synchronized (fileLock) {
			// verificar se hm existe (senão → NOHM)
			if (!houseExists(hm)) { out.writeObject("NOHM"); out.flush(); return; }
			// verificar se requester é owner de hm (senão → NOPERM)
			if (!isOwner(hm, requester)) { out.writeObject("NOPERM"); out.flush(); return; }

			// incrementar contador da seção s em hm, registar dispositivo → OK
			File houseFile = new File("src/sperta/data/houses/" + hm + ".txt");
			java.util.List<String> lines = new java.util.ArrayList<>();
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFile))) {
				String line;
				while ((line = reader.readLine()) != null) lines.add(line);
			}

			// Contar dispositivos já existentes na secção s para determinar o próximo número
			int counter = 1;
			boolean inDevices = false;
			for (String line : lines) {
				if (line.trim().equals("[devices]")) { inDevices = true; continue; }
				if (!inDevices || line.trim().isEmpty()) continue;
				String[] parts = line.split("\\|", 2);
				if (parts.length == 2 && parts[1].trim().equals(s)) counter++;
			}

			String deviceName = s + counter;
			lines.add(deviceName + "|" + s);

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(houseFile, false))) {
				for (String line : lines) { writer.write(line); writer.newLine(); }
			}

			out.writeObject("OK");
			out.flush();
		}
	}

	private static void handleEC(String hm, String d, String intVal,
			String requester, ObjectOutputStream out) throws IOException {

		// validar intVal ∈ [0..600] (senão → NOK)
		int val;
		try {
			val = Integer.parseInt(intVal);
			if (val < 0 || val > 600) { out.writeObject("NOK"); out.flush(); return; }
		} catch (NumberFormatException e) {
			out.writeObject("NOK"); out.flush(); return;
		}

		synchronized (fileLock) {
			// verificar se hm existe (senão → NOHM)
			if (!houseExists(hm)) { out.writeObject("NOHM"); out.flush(); return; }
			// verificar se d existe em hm (senão → NOD)
			if (!deviceExists(hm, d)) { out.writeObject("NOD"); out.flush(); return; }

			// verificar permissões do requester (senão → NOPERM)
			String section = getDeviceSection(hm, d);
			if (section == null || !hasPermission(hm, requester, section)) {
				out.writeObject("NOPERM"); out.flush(); return;
			}

			// gravar estado atual em STATES_DIR e entrada em LOGS_DIR/<hm>/<d>.csv → OK
			String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
					.format(new java.util.Date());

			// Atualizar estado atual: STATES_DIR/<hm>.txt
			File statesDir = new File(STATES_DIR);
			if (!statesDir.exists()) statesDir.mkdirs();
			File statesFile = new File(STATES_DIR + hm + ".txt");

			java.util.List<String> stateLines = new java.util.ArrayList<>();
			if (statesFile.exists()) {
				try (BufferedReader reader = new BufferedReader(new FileReader(statesFile))) {
					String line;
					while ((line = reader.readLine()) != null) stateLines.add(line);
				}
			}
			boolean found = false;
			for (int i = 0; i < stateLines.size(); i++) {
				String[] parts = stateLines.get(i).split("\\|", 3);
				if (parts.length >= 1 && parts[0].equals(d)) {
					stateLines.set(i, d + "|" + val + "|" + timestamp);
					found = true;
					break;
				}
			}
			if (!found) stateLines.add(d + "|" + val + "|" + timestamp);

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(statesFile, false))) {
				for (String line : stateLines) { writer.write(line); writer.newLine(); }
			}

			// Adicionar entrada no log: LOGS_DIR/<hm>/<d>.csv
			File logsHouseDir = new File(LOGS_DIR + hm);
			if (!logsHouseDir.exists()) logsHouseDir.mkdirs();
			File logFile = new File(LOGS_DIR + hm + "/" + d + ".csv");
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
				writer.write(d + "," + val + "," + timestamp);
				writer.newLine();
			}

			out.writeObject("OK");
			out.flush();
		}
	}

	private static void handleRT(String hm, String requester, ObjectOutputStream out)
			throws IOException {
		if (!houseExists(hm)) { out.writeObject("NOHM"); out.flush(); return; }
		if (!hasMembership(hm, requester)) { out.writeObject("NOPERM"); out.flush(); return; }

		File statesFile = new File(STATES_DIR + hm + ".txt");
		if (!statesFile.exists() || statesFile.length() == 0) {
			out.writeObject("NODATA"); out.flush(); return;
		}

		byte[] data = java.nio.file.Files.readAllBytes(statesFile.toPath());
		out.writeObject("OK");
		out.writeLong(data.length);
		out.write(data);
		out.flush();
	}

	private static void handleRH(String hm, String d,
			String requester, ObjectOutputStream out) throws IOException {
		if (!houseExists(hm)) { out.writeObject("NOHM"); out.flush(); return; }
		if (!deviceExists(hm, d)) { out.writeObject("NOD"); out.flush(); return; }

		String section = getDeviceSection(hm, d);
		if (section == null || !hasPermission(hm, requester, section)) {
			out.writeObject("NOPERM"); out.flush(); return;
		}

		File logFile = new File(LOGS_DIR + hm + "/" + d + ".csv");
		if (!logFile.exists() || logFile.length() == 0) {
			out.writeObject("NODATA"); out.flush(); return;
		}

		byte[] data = java.nio.file.Files.readAllBytes(logFile.toPath());
		out.writeObject("OK");
		out.writeLong(data.length);
		out.write(data);
		out.flush();
	}

	// ─── Helpers ───

	private static boolean houseExists(String hm) {
		if (!new File(HOUSES_FILE).exists()) return false;
		synchronized (fileLock) {
			try (BufferedReader reader = new BufferedReader(new FileReader(HOUSES_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) continue;
					String[] parts = line.split("\\|", 2);
					if (parts[0].equals(hm)) return true;
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler houses: " + e.getMessage());
			}
			return false;
		}
	}

	private static boolean isOwner(String hm, String user) {
		if (!new File(HOUSES_FILE).exists()) return false;
		synchronized (fileLock) {
			try (BufferedReader reader = new BufferedReader(new FileReader(HOUSES_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) continue;
					String[] parts = line.split("\\|", 2);
					if (parts.length == 2 && parts[0].equals(hm) && parts[1].equals(user))
						return true;
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler houses: " + e.getMessage());
			}
			return false;
		}
	}

	private static boolean hasPermission(String hm, String user, String s) {
		// owner tem sempre permissão
		if (isOwner(hm, user)) return true;

		synchronized (fileLock) {
			String houseFilePath = "src/sperta/data/houses/" + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inPermissions = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[permissions]")) { inPermissions = true; continue; }
					if (line.trim().equals("[devices]")) break;
					if (!inPermissions || line.trim().isEmpty()) continue;

					// formato: user1|sala,quarto ou user1|all
					String[] parts = line.split("\\|", 2);
					if (parts.length == 2 && parts[0].equals(user)) {
						String[] sections = parts[1].split(",");
						for (String sec : sections) {
							if (sec.trim().equals("all") || sec.trim().equals(s))
								return true;
						}
					}
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler permissões: " + e.getMessage());
			}
			return false;
		}
	}

	private static boolean deviceExists(String hm, String device) {
		synchronized (fileLock) {
			String houseFilePath = "src/sperta/data/houses/" + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inDevices = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[devices]")) { inDevices = true; continue; }
					if (!inDevices || line.trim().isEmpty()) continue;

					// formato: deviceId|secção
					String[] parts = line.split("\\|", 2);
					if (parts.length >= 1 && parts[0].equals(device))
						return true;
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler dispositivos: " + e.getMessage());
			}
			return false;
		}
	}

	private static String getDeviceSection(String hm, String device) {
		synchronized (fileLock) {
			String houseFilePath = "src/sperta/data/houses/" + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inDevices = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[devices]")) { inDevices = true; continue; }
					if (!inDevices || line.trim().isEmpty()) continue;
					String[] parts = line.split("\\|", 2);
					if (parts.length == 2 && parts[0].equals(device)) return parts[1].trim();
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler secção do dispositivo: " + e.getMessage());
			}
			return null;
		}
	}

	private static boolean hasMembership(String hm, String user) {
		if (isOwner(hm, user)) return true;
		synchronized (fileLock) {
			String houseFilePath = "src/sperta/data/houses/" + hm + ".txt";
			try (BufferedReader reader = new BufferedReader(new FileReader(houseFilePath))) {
				String line;
				boolean inPermissions = false;
				while ((line = reader.readLine()) != null) {
					if (line.trim().equals("[permissions]")) { inPermissions = true; continue; }
					if (line.trim().equals("[devices]")) break;
					if (!inPermissions || line.trim().isEmpty()) continue;
					String[] parts = line.split("\\|", 2);
					if (parts.length >= 1 && parts[0].equals(user)) return true;
				}
			} catch (IOException e) { /* ignore */ }
			return false;
		}
	}

	private static boolean userExists(String user) {
		synchronized (fileLock) {
			try (BufferedReader reader = new BufferedReader(new FileReader(USER_FILE))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) continue;
					String[] parts = line.split(":", 2);
					if (parts.length >= 1 && parts[0].equals(user))
						return true;
				}
			} catch (IOException e) {
				System.err.println("Erro ao ler users: " + e.getMessage());
			}
			return false;
		}
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
