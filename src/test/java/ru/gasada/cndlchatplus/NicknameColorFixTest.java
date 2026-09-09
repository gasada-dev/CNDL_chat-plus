package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class NicknameColorFixTest {
	@Test
	void replacesBlackStyleWithoutMutatingOriginal() {
		Component original = Component.literal("Steve").withStyle(style -> style
				.withColor(ChatFormatting.BLACK).withBold(true));
		Component fixed = NicknameColorFix.whitenBlack(original);

		assertNotSame(original, fixed);
		assertEquals(0x000000, original.getStyle().getColor().getValue());
		assertEquals(0xFFFFFF, fixed.getStyle().getColor().getValue());
		assertEquals(original.getStyle().isBold(), fixed.getStyle().isBold());
	}

	@Test
	void preservesNonBlackColors() {
		Component fixed = NicknameColorFix.whitenBlack(
				Component.literal("Steve").withStyle(ChatFormatting.BLUE));

		assertEquals(0x5555FF, fixed.getStyle().getColor().getValue());
	}

	@Test
	void replacesOnlyBlackLiteralFormattingCodes() {
		Component original = Component.literal("§0Steve §aGreen §1Blue");
		Component fixed = NicknameColorFix.whitenBlack(original);

		assertEquals("§0Steve §aGreen §1Blue", original.getString());
		assertEquals("§fSteve §aGreen §1Blue", fixed.getString());
	}

	@Test
	void traversesNestedSiblings() {
		Component original = Component.literal("root").append(
				Component.literal("black").withStyle(ChatFormatting.BLACK).append(
						Component.literal("§0nested")));
		Component fixed = NicknameColorFix.whitenBlack(original);
		Component fixedChild = fixed.getSiblings().getFirst();

		assertEquals(0xFFFFFF, fixedChild.getStyle().getColor().getValue());
		assertEquals("§fnested", fixedChild.getSiblings().getFirst().getString());
	}

	@Test
	void handlesNullAndEmptyComponents() {
		assertEquals("", NicknameColorFix.whitenBlack(null).getString());
		assertEquals("", NicknameColorFix.whitenBlack(Component.empty()).getString());
	}
}
