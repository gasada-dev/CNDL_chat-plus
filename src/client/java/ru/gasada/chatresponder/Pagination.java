package ru.gasada.chatresponder;

public final class Pagination {
	private Pagination() {
	}

	public static int maxPage(int itemCount, int pageSize) {
		return Math.max(0, (itemCount - 1) / Math.max(1, pageSize));
	}

	public static int clampPage(int page, int itemCount, int pageSize) {
		return Math.max(0, Math.min(page, maxPage(itemCount, pageSize)));
	}
}
