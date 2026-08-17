package ru.gasada.cndlchatplus;

public final class ScreenStatus {
	private String text = "";
	private int color = 0xFF75D98B;

	public String text() { return text; }
	public int color() { return color; }
	public boolean empty() { return text.isEmpty(); }
	public void set(String text, int color) { this.text = text; this.color = color; }
	public void clear() { text = ""; }
}
