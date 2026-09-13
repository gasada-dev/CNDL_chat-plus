package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

public final class ChatTabBar {
	private static final int BAR_HEIGHT = 12;
	private static final int PAD_X = 4;
	private static final int GAP = 2;
	private static final int LEFT = 2;
	private static final int PAGE_BUTTON_WIDTH = 12;
	// vanilla: нижняя граница чата = screenHeight - 40, а не у input-строки
	private static final int BOTTOM_MARGIN = 40;
	private static final int COLOR_BG = 0xA0000000;
	private static final int COLOR_BG_HOVER = 0xC0404040;
	private static final int COLOR_ACTIVE_OUTLINE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFAAAAAA;
	private static final int COLOR_TEXT_ACTIVE = 0xFFFFFFFF;
	private static final int COLOR_BADGE = 0xFFFF5555;
	private static final String SEARCH_HINT = "Ctrl+F - поиск";
	private static final String BOOKMARKS = "Закладки";
	private static int page;
	private static int lastOverflowWidth = -1;
	private static List<ChatTabDefinition> lastDefinitions = List.of();
	private static List<Integer> lastPageStarts = List.of();
	private static ChatTabDefinition lastActive;

	private ChatTabBar() {
	}

	record TabRect(ChatTabDefinition definition, int x0, int y0, int x1, int y1) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	private record BarLayout(List<TabRect> tabs, ControlRect previous, ControlRect next) { }

	private record ControlRect(int x0, int y0, int x1, int y1, boolean active) {
		private boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	private static int barBottom(Minecraft minecraft, int screenHeight) {
		ChatTabFilterAccess access = (ChatTabFilterAccess) ChatAccess.chat(minecraft);
		int chatHeight = (int) Math.ceil(access.gasada$chatHeight() * access.gasada$chatScale());
		int chatTop = screenHeight - BOTTOM_MARGIN - chatHeight;
		return Math.max(chatTop - 2, BAR_HEIGHT);
	}

	public static int searchBoxY(Minecraft minecraft, int screenHeight) {
		return Math.max(2, barBottom(minecraft, screenHeight) - BAR_HEIGHT - 20);
	}

	public static int searchBoxWidth(Font font, int screenWidth) {
		return Math.max(40, Math.min(240, screenWidth - font.width(BOOKMARKS) - 14));
	}

	static List<Integer> pageStarts(List<Integer> widths, int availableWidth) {
		List<Integer> starts = new ArrayList<>();
		for (int index = 0; index < widths.size();) {
			starts.add(index);
			int used = 0;
			do {
				int next = widths.get(index) + (used == 0 ? 0 : GAP);
				if (used > 0 && used + next > availableWidth) break;
				used += next;
				index++;
			} while (index < widths.size());
		}
		return starts;
	}

	static int pageContaining(List<Integer> starts, int itemCount, int itemIndex, int currentPage) {
		int clamped = Math.max(0, Math.min(currentPage, starts.size() - 1));
		if (itemIndex < 0 || itemIndex >= itemCount || starts.isEmpty()) return clamped;
		int end = clamped + 1 < starts.size() ? starts.get(clamped + 1) : itemCount;
		if (itemIndex >= starts.get(clamped) && itemIndex < end) return clamped;
		for (int candidate = 0; candidate < starts.size(); candidate++) {
			end = candidate + 1 < starts.size() ? starts.get(candidate + 1) : itemCount;
			if (itemIndex >= starts.get(candidate) && itemIndex < end) return candidate;
		}
		return clamped;
	}

	static boolean isActive(ChatTabDefinition active, ChatTabDefinition candidate) {
		return active.equals(candidate);
	}

	private static BarLayout layout(Font font, ChatTabController tabs, int screenWidth, int barBottom) {
		List<TabRect> rects = new ArrayList<>();
		List<ChatTabDefinition> definitions = tabs.definitions();
		int y0 = barBottom - BAR_HEIGHT;
		int x = LEFT;
		if (definitions.isEmpty()) return new BarLayout(rects, null, null);
		List<Integer> widths = definitions.stream().map(definition -> tabWidth(font, tabs, definition)).toList();
		int totalWidth = widths.stream().mapToInt(Integer::intValue).sum() + GAP * (widths.size() - 1);
		if (totalWidth <= screenWidth - LEFT * 2) {
			page = 0;
			rememberLayout(definitions, tabs.activeDefinition(), -1, List.of());
			for (int index = 0; index < definitions.size(); index++) {
				int width = widths.get(index);
				rects.add(new TabRect(definitions.get(index), x, y0, x + width, barBottom));
				x += width + GAP;
			}
			return new BarLayout(rects, null, null);
		}
		if (definitions.size() == 1) {
			int width = Math.min(widths.getFirst(), screenWidth - LEFT * 2);
			rects.add(new TabRect(definitions.getFirst(), x, y0, x + width, barBottom));
			rememberLayout(definitions, tabs.activeDefinition(), -1, List.of());
			return new BarLayout(rects, null, null);
		}

		int allWidth = widths.getFirst();
		rects.add(new TabRect(definitions.getFirst(), x, y0, x + allWidth, barBottom));
		x += allWidth + GAP;
		ControlRect previous = new ControlRect(x, y0, x + PAGE_BUTTON_WIDTH, barBottom, false);
		x += PAGE_BUTTON_WIDTH + GAP;
		int availableWidth = Math.max(1, screenWidth - LEFT - x - PAGE_BUTTON_WIDTH - GAP);
		List<Integer> customWidths = widths.subList(1, widths.size());
		List<Integer> starts = pageStarts(customWidths, availableWidth);
		page = Math.max(0, Math.min(page, starts.size() - 1));
		ChatTabDefinition active = tabs.activeDefinition();
		boolean layoutChanged = availableWidth != lastOverflowWidth || !definitions.equals(lastDefinitions)
				|| !starts.equals(lastPageStarts) || !active.equals(lastActive);
		if (layoutChanged) page = pageContaining(starts, customWidths.size(), definitions.indexOf(active) - 1, page);
		rememberLayout(definitions, active, availableWidth, starts);
		int start = starts.get(page);
		int end = page + 1 < starts.size() ? starts.get(page + 1) : customWidths.size();
		for (int index = start; index < end; index++) {
			int width = Math.min(customWidths.get(index), availableWidth);
			rects.add(new TabRect(definitions.get(index + 1), x, y0, x + width, barBottom));
			x += width + GAP;
		}
		previous = new ControlRect(previous.x0(), previous.y0(), previous.x1(), previous.y1(), page > 0);
		ControlRect next = new ControlRect(screenWidth - LEFT - PAGE_BUTTON_WIDTH, y0,
				screenWidth - LEFT, barBottom, page + 1 < starts.size());
		return new BarLayout(rects, previous, next);
	}

	private static void rememberLayout(List<ChatTabDefinition> definitions, ChatTabDefinition active,
			int overflowWidth, List<Integer> pageStarts) {
		lastDefinitions = definitions;
		lastPageStarts = List.copyOf(pageStarts);
		lastActive = active;
		lastOverflowWidth = overflowWidth;
	}

	private static int tabWidth(Font font, ChatTabController tabs, ChatTabDefinition definition) {
		return font.width(label(tabs, definition)) + PAD_X * 2;
	}

	private static String label(ChatTabController tabs, ChatTabDefinition definition) {
		int unread = definition.builtIn() == ChatTab.ALL ? 0 : tabs.unread(definition);
		return unread > 0 ? definition.displayName() + " " + unread : definition.displayName();
	}

	public static void render(CompatGraphics graphics, Font font, ChatTabController tabs,
			int screenWidth, int screenHeight, int mouseX, int mouseY, Minecraft minecraft) {
		BarLayout layout = layout(font, tabs, screenWidth, barBottom(minecraft, screenHeight));
		for (TabRect rect : layout.tabs()) {
			boolean activeTab = isActive(tabs.activeDefinition(), rect.definition());
			boolean hovered = rect.contains(mouseX, mouseY);
			graphics.fill(rect.x0(), rect.y0(), rect.x1(), rect.y1(),
					hovered ? COLOR_BG_HOVER : COLOR_BG);
			if (activeTab) {
				graphics.outline(rect.x0(), rect.y0(), rect.x1() - rect.x0(), rect.y1() - rect.y0(),
						COLOR_ACTIVE_OUTLINE);
			}
			int innerWidth = Math.max(0, rect.x1() - rect.x0() - PAD_X * 2);
			int unread = rect.definition().builtIn() == ChatTab.ALL ? 0 : tabs.unread(rect.definition());
			String displayName = rect.definition().displayName();
			String badge = unread > 0 ? " " + unread : "";
			if (font.width(displayName + badge) <= innerWidth) {
				graphics.text(font, displayName, rect.x0() + PAD_X, rect.y0() + 2,
						activeTab ? COLOR_TEXT_ACTIVE : COLOR_TEXT);
				if (unread > 0) {
					graphics.text(font, badge,
							rect.x0() + PAD_X + font.width(displayName), rect.y0() + 2, COLOR_BADGE);
				}
			} else {
				graphics.text(font, font.plainSubstrByWidth(displayName + badge, innerWidth),
						rect.x0() + PAD_X, rect.y0() + 2, activeTab ? COLOR_TEXT_ACTIVE : COLOR_TEXT);
			}
		}
		renderControl(graphics, font, layout.previous(), "<", mouseX, mouseY);
		renderControl(graphics, font, layout.next(), ">", mouseX, mouseY);
	}

	private static void renderControl(CompatGraphics graphics, Font font, ControlRect rect, String label,
			int mouseX, int mouseY) {
		if (rect == null) return;
		graphics.fill(rect.x0(), rect.y0(), rect.x1(), rect.y1(),
				rect.active() && rect.contains(mouseX, mouseY) ? COLOR_BG_HOVER : COLOR_BG);
		graphics.text(font, label, rect.x0() + 3, rect.y0() + 2,
				rect.active() ? COLOR_TEXT_ACTIVE : COLOR_TEXT);
	}

	public static void renderSearchHint(CompatGraphics graphics, Font font, ChatSearchState search,
			int screenHeight, Minecraft minecraft) {
		if (search != null && search.enabled() && !search.active()) {
			graphics.text(font, SEARCH_HINT, 6, searchBoxY(minecraft, screenHeight) + 5, COLOR_TEXT);
		}
	}

	public static void renderBookmarks(CompatGraphics graphics, Font font, int screenWidth,
			int screenHeight, int mouseX, int mouseY, Minecraft minecraft) {
		if (CndlChatPlusClient.CHAT_BOOKMARKS == null) return;
		BookmarkRect rect = bookmarkRect(font, screenWidth, screenHeight, minecraft);
		graphics.fill(rect.x0(), rect.y0(), rect.x1(), rect.y1(),
				rect.contains(mouseX, mouseY) ? COLOR_BG_HOVER : COLOR_BG);
		graphics.outline(rect.x0(), rect.y0(), rect.x1() - rect.x0(), rect.y1() - rect.y0(),
				COLOR_ACTIVE_OUTLINE);
		graphics.text(font, BOOKMARKS, rect.x0() + PAD_X, rect.y0() + 3, COLOR_TEXT_ACTIVE);
	}

	public static boolean clickBookmarks(Screen parent, Font font, int screenWidth, int screenHeight,
			double mouseX, double mouseY, Minecraft minecraft) {
		if (CndlChatPlusClient.CHAT_BOOKMARKS == null
				|| !bookmarkRect(font, screenWidth, screenHeight, minecraft).contains(mouseX, mouseY)) {
			return false;
		}
		ClientUi.setScreen(minecraft, new ChatBookmarksScreen(parent, CndlChatPlusClient.CHAT_BOOKMARKS));
		return true;
	}

	private static BookmarkRect bookmarkRect(Font font, int screenWidth, int screenHeight, Minecraft minecraft) {
		int width = font.width(BOOKMARKS) + PAD_X * 2;
		int x = Math.min(searchBoxWidth(font, screenWidth) + 6, screenWidth - width - 2);
		int y = searchBoxY(minecraft, screenHeight) + 2;
		return new BookmarkRect(Math.max(2, x), y, Math.max(2, x) + width, y + 14);
	}

	private record BookmarkRect(int x0, int y0, int x1, int y1) {
		private boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public static boolean click(ChatTabController tabs, Font font, int screenWidth, int screenHeight,
			double mouseX, double mouseY, Minecraft minecraft) {
		BarLayout layout = layout(font, tabs, screenWidth, barBottom(minecraft, screenHeight));
		if (layout.previous() != null && layout.previous().active() && layout.previous().contains(mouseX, mouseY)) {
			page--;
			return true;
		}
		if (layout.next() != null && layout.next().active() && layout.next().contains(mouseX, mouseY)) {
			page++;
			return true;
		}
		for (TabRect rect : layout.tabs()) {
			if (rect.contains(mouseX, mouseY)) {
				tabs.selectTab(rect.definition(), minecraft);
				return true;
			}
		}
		return false;
	}
}
