/***************************************************************************
*   Seguranca e Confiabilidade 2025/26
*
*
***************************************************************************/

import java.io.IOException;
import java.io.ObjectOutputStream;

public class CommandService {

	private final DataRepository repository;

	public CommandService(DataRepository repository) {
		this.repository = repository;
	}

	public void handleCommand(String command, String requester, ObjectOutputStream out) throws IOException {
		String[] parts = command.split("\\s+");
		String cmd = parts[0].toUpperCase();
		boolean ok;
		switch (cmd) {
			case "CREATE":
				ok = parts.length >= 2;
				if (ok) handleCreate(parts[1], requester, out);
				break;
			case "ADD":
				ok = parts.length >= 4;
				if (ok) handleAdd(parts[1], parts[2], parts[3], requester, out);
				break;
			case "RD":
				ok = parts.length >= 3;
				if (ok) handleRD(parts[1], parts[2], requester, out);
				break;
			case "EC":
				ok = parts.length >= 4;
				if (ok) handleEC(parts[1], parts[2], parts[3], requester, out);
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

	private void handleCreate(String hm, String owner, ObjectOutputStream out) throws IOException {
		if (repository.houseExists(hm)) {
			out.writeObject("NOK");
			out.flush();
			return;
		}
		repository.createHouse(hm, owner);
		out.writeObject("OK");
		out.flush();
	}

	private void handleAdd(String user1, String hm, String s, String requester, ObjectOutputStream out)
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

		repository.addPermission(hm, user1, "ALL".equals(section) ? "all" : section);
		out.writeObject("OK");
		out.flush();
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

	private void handleEC(String hm, String d, String intVal, String requester, ObjectOutputStream out)
			throws IOException {
		String device = d == null ? "" : d.trim().toUpperCase();
		if (device.isEmpty()) {
			out.writeObject("NOD");
			out.flush();
			return;
		}
		int val;
		try {
			val = Integer.parseInt(intVal);
			if (val < 0 || val > 600) {
				out.writeObject("NOK");
				out.flush();
				return;
			}
		} catch (NumberFormatException e) {
			out.writeObject("NOK");
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

		repository.updateStateAndLog(hm, device, val);
		out.writeObject("OK");
		out.flush();
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
