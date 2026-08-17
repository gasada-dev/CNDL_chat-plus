package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

public final class ChatTabBar {
	private static final int BAR_HEIGHT = 12;
	private static final int PAD_X = 4;
	private static final int GAP = 2;
	private static final int LEFT = 2;
	// vanilla: нижняя граница чата = screenHeight - 40, а не у input-строки
	private static final int BOTTOM_MARGIN = 40;
	private static final int COLOR_BG = 0xA0000000;
	private static final int COLOR_BG_HOVER = 0xC0404040;
	private static final int COLOR_ACTIVE_OUTLINE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFAAAAAA;
	private static final int COLOR_TEXT_ACTIVE = 0xFFFFFFFF;
	private static final int COLOR_BADGE = 0xFFFF5555;
	private static final String SEARCH_HINT = "Ctrl+F - поиск";

	private ChatTabBar() {
	}

	record TabRect(ChatTab tab, int x0, int y0, int x1, int y1) {
		boolean contains(double x, double y) {
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

	static List<TabRect> layout(Font font, ChatTabController tabs, int screenWidth, int barBottom) {
		List<TabRect> rects = new ArrayList<>();
		int y0 = barBottom - BAR_HEIGHT;
		int x = LEFT;
		for (ChatTab tab : ChatTab.values()) {
			int width = font.width(label(tabs, tab)) + PAD_X * 2;
			if (x + width > screenWidth - LEFT) {
				break;
			}
			rects.add(new TabRect(tab, x, y0, x + width, barBottom));
			x += width + GAP;
		}
		return rects;
	}

	private static String label(ChatTabController tabs, ChatTab tab) {
		int unread = tab == ChatTab.ALL ? 0 : tabs.unread(tab);
		return unread > 0 ? tab.displayName() + " " + unread : tab.displayName();
	}

	public static void render(CompatGraphics graphics, Font font, ChatTabController tabs,
			int screenWidth, int screenHeight, int mouseX, int mouseY, Minecraft minecraft) {
		List<TabRect> rects = layout(font, tabs, screenWidth, barBottom(minecraft, screenHeight));
		for (TabRect rect : rects) {
			boolean activeTab = tabs.active() == rect.tab();
			boolean hovered = rect.contains(mouseX, mouseY);
			graphics.fill(rect.x0(), rect.y0(), rect.x1(), rect.y1(),
					hovered ? COLOR_BG_HOVER : COLOR_BG);
			if (activeTab) {
				graphics.outline(rect.x0(), rect.y0(), rect.x1() - rect.x0(), rect.y1() - rect.y0(),
						COLOR_ACTIVE_OUTLINE);
			}
			graphics.text(font, rect.tab().displayName(), rect.x0() + PAD_X, rect.y0() + 2,
					activeTab ? COLOR_TEXT_ACTIVE : COLOR_TEXT);
			int unread = rect.tab() == ChatTab.ALL ? 0 : tabs.unread(rect.tab());
			if (unread > 0) {
				String badge = " " + unread;
				graphics.text(font, badge,
						rect.x0() + PAD_X + font.width(rect.tab().displayName()), rect.y0() + 2, COLOR_BADGE);
			}
		}
	}

	public static void renderSearchHint(CompatGraphics graphics, Font font, ChatSearchState search,
			int screenHeight, Minecraft minecraft) {
		if (search != null && search.enabled() && !search.active()) {
			graphics.text(font, SEARCH_HINT, 6, searchBoxY(minecraft, screenHeight) + 5, COLOR_TEXT);
		}
	}

	public static boolean click(ChatTabController tabs, Font font, int screenWidth, int screenHeight,
			double mouseX, double mouseY, Minecraft minecraft) {
		for (TabRect rect : layout(font, tabs, screenWidth, barBottom(minecraft, screenHeight))) {
			if (rect.contains(mouseX, mouseY)) {
				tabs.selectTab(rect.tab(), minecraft);
				return true;
			}
		}
		return false;
	}
}
