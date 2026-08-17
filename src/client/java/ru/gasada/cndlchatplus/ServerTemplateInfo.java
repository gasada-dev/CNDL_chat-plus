package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;

public final class ServerTemplateInfo {
	public String id;
	public String name;
	public List<String> addressPatterns = new ArrayList<>();

	public ServerTemplateInfo() {
	}

	public ServerTemplateInfo(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public ServerTemplateInfo copy() {
		ServerTemplateInfo copy = new ServerTemplateInfo(id, name);
		copy.addressPatterns = new ArrayList<>(addressPatterns == null ? List.of() : addressPatterns);
		return copy;
	}
}
