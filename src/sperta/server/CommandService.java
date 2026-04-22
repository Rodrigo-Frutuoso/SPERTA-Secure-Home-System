/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class CommandService {

	private final DataRepository repository;

	public CommandService(DataRepository repository) {
		this.repository = repository;
	}

	public void handleCommand(String command, String requester, ObjectInputStream in, ObjectOutputStream out) throws IOException {
		String[] parts = command.split("\\s+");
		String cmd = parts[0].toUpperCase();
		boolean ok;
		switch (cmd) {
			case "CREATE":
				ok = parts.length >= 2;
				if (ok) handleCreate(parts[1], requester, in, out);
				break;
			case "ADD":
				ok = parts.length >= 4;
				if (ok) handleAdd(parts[1], parts[2], parts[3], requester, in, out);
				break;
			case "RD":
				ok = parts.length >= 3;
				if (ok) handleRD(parts[1], parts[2], requester, out);
				break;
			case "EC":
				ok = parts.length >= 4;
				if (ok) handleEC(parts[1], parts[2], parts[3], requester, in, out);
				break;
			case "RT":
				ok = parts.length >= 2;
				if (ok) handleRT(parts[1], requester, out);
				break;
			case "RH":
				ok = parts.length >= 3;
				if (ok) handleRH(parts[1], parts[2], requester, out);
				break;
			default:
				ok = false;
				break;
		}

		if (!ok) {
			out.writeObject("NOK");
			out.flush();
		}
	}

	private void handleCreate(String hm, String owner, ObjectInputStream in, ObjectOutputStream out) throws IOException {
		if (repository.houseExists(hm)) {
			out.writeObject("NOK");
			out.flush();
			return;
		}
		repository.createHouse(hm, owner);
		out.writeObject("OK");
		out.flush();

		// E2E: Receber chaves de secção wrapped com a chave pública do owner
		try {
			int numKeys = in.readInt();
			for (int i = 0; i < numKeys; i++) {
				String section = (String) in.readObject();
				int keyLen = in.readInt();
				byte[] wrappedKey = new byte[keyLen];
				in.readFully(wrappedKey);
				repository.saveWrappedKey(hm, section, owner, wrappedKey);
			}
			out.writeObject("OK");
			out.flush();
		} catch (ClassNotFoundException e) {
			System.err.println("Erro ao receber chaves de secção: " + e.getMessage());
		}
	}

	private void handleAdd(String user1, String hm, String s, String requester, ObjectInputStream in, ObjectOutputStream out)
			throws IOException {
		String section = s == null ? "" : s.trim().toUpperCase();
		if (!"ALL".equals(section) && !repository.isValidSection(section)) {
			out.writeObject("NOK");
			out.flush();
			return;
		}

		if (!repository.houseExists(hm)) {
			out.writeObject("NOHM");
			out.flush();
			return;
		}
		if (!repository.isOwner(hm, requester)) {
			out.writeObject("NOPERM");
			out.flush();
			return;
		}
		if (!repository.userExists(user1)) {
			out.writeObject("NOUSER");
			out.flush();
			return;
		}
		if (!repository.userHasCertificate(user1)) {
			out.writeObject("NOK");
			out.flush();
			return;
		}

		// E2E: Enviar certificado do user1 e chaves wrapped do owner
		try {
			out.writeObject("OK-KEYS");
			out.flush();

			// Enviar certificado do user1
			byte[] certBytes = repository.getUserCertificate(user1);
			out.writeInt(certBytes.length);
			out.write(certBytes);

			// Determinar secções
			String[] sections;
			if ("ALL".equals(section)) {
				sections = repository.getAllSections();
			} else {
				sections = new String[] { section };
			}

			// Enviar chaves wrapped do owner (requester) para cada secção
			out.writeInt(sections.length);
			for (String sec : sections) {
				byte[] ownerWrappedKey = repository.loadWrappedKey(hm, sec, requester);
				out.writeObject(sec);
				out.writeInt(ownerWrappedKey.length);
				out.write(ownerWrappedKey);
			}
			out.flush();

			// Receber chaves re-wrapped para user1
			int numKeys = in.readInt();
			for (int i = 0; i < numKeys; i++) {
				String sec = (String) in.readObject();
				int keyLen = in.readInt();
				byte[] user1WrappedKey = new byte[keyLen];
				in.readFully(user1WrappedKey);
				repository.saveWrappedKey(hm, sec, user1, user1WrappedKey);
			}

			// Adicionar permissão e confirmar
			repository.addPermission(hm, user1, "ALL".equals(section) ? "all" : section);
			out.writeObject("OK");
			out.flush();
		} catch (ClassNotFoundException e) {
			System.err.println("Erro no protocolo ADD E2E: " + e.getMessage());
		}
	}

	private void handleRD(String hm, String s, String requester, ObjectOutputStream out) throws IOException {
		String section = s == null ? "" : s.trim().toUpperCase();
		if (!repository.isValidSection(section)) {
			out.writeObject("NOK");
			out.flush();
			return;
		}

		if (!repository.houseExists(hm)) {
			out.writeObject("NOHM");
			out.flush();
			return;
		}
		if (!repository.isOwner(hm, requester)) {
			out.writeObject("NOPERM");
			out.flush();
			return;
		}

		repository.registerDevice(hm, section);
		out.writeObject("OK");
		out.flush();
	}

	private void handleEC(String hm, String d, String intVal, String requester, ObjectInputStream in, ObjectOutputStream out)
			throws IOException {
		String device = d == null ? "" : d.trim().toUpperCase();
		if (device.isEmpty()) {
			out.writeObject("NOD");
			out.flush();
			return;
		}

		if (!repository.houseExists(hm)) {
			out.writeObject("NOHM");
			out.flush();
			return;
		}
		if (!repository.deviceExists(hm, device)) {
			out.writeObject("NOD");
			out.flush();
			return;
		}

		String section = repository.getDeviceSection(hm, device);
		if (section == null || !repository.hasPermission(hm, requester, section)) {
			out.writeObject("NOPERM");
			out.flush();
			return;
		}

		// E2E: Enviar wrapped key ao cliente, receber valor cifrado
		try {
			byte[] wrappedKey = repository.loadWrappedKey(hm, section, requester);
			if (wrappedKey == null) {
				out.writeObject("NOK");
				out.flush();
				return;
			}
			out.writeObject("OK-KEY");
			out.writeInt(wrappedKey.length);
			out.write(wrappedKey);
			out.flush();

			// Receber valor cifrado do cliente
			int encLen = in.readInt();
			byte[] encryptedVal = new byte[encLen];
			in.readFully(encryptedVal);
			String encB64 = java.util.Base64.getEncoder().encodeToString(encryptedVal);

			repository.updateStateAndLogEncrypted(hm, device, encB64);
			out.writeObject("OK");
			out.flush();
		} catch (Exception e) {
			System.err.println("Erro no protocolo EC E2E: " + e.getMessage());
		}
	}

	private void handleRT(String hm, String requester, ObjectOutputStream out) throws IOException {
		if (!repository.houseExists(hm)) {
			out.writeObject("NOHM");
			out.flush();
			return;
		}
		if (!repository.hasMembership(hm, requester)) {
			out.writeObject("NOPERM");
			out.flush();
			return;
		}

		byte[] data = repository.readStateFile(hm);
		if (data == null || data.length == 0) {
			out.writeObject("NODATA");
			out.flush();
			return;
		}

		out.writeObject("OK");
		out.writeLong(data.length);
		out.write(data);
		out.flush();
	}

	private void handleRH(String hm, String d, String requester, ObjectOutputStream out) throws IOException {
		String device = d == null ? "" : d.trim().toUpperCase();
		if (device.isEmpty()) {
			out.writeObject("NOD");
			out.flush();
			return;
		}

		if (!repository.houseExists(hm)) {
			out.writeObject("NOHM");
			out.flush();
			return;
		}
		if (!repository.deviceExists(hm, device)) {
			out.writeObject("NOD");
			out.flush();
			return;
		}

		String section = repository.getDeviceSection(hm, device);
		if (section == null || !repository.hasPermission(hm, requester, section)) {
			out.writeObject("NOPERM");
			out.flush();
			return;
		}

		byte[] data = repository.readDeviceLog(hm, device);
		if (data == null || data.length == 0) {
			out.writeObject("NODATA");
			out.flush();
			return;
		}

		out.writeObject("OK");
		out.writeLong(data.length);
		out.write(data);
		out.flush();
	}
}
