package ru.gasada.chatresponder;

import java.util.List;
import java.util.function.Function;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class StyledCycleButton<T> extends StyledButton {
	private final Function<T, Component> label;
	private final List<T> values;
	private final Component name;
	private final ValueChange<T> onChange;
	private int index;

	interface ValueChange<T> {
		void accept(StyledCycleButton<T> button, T value);
	}

	private StyledCycleButton(int x, int y, int width, int height, Function<T, Component> label,
			List<T> values, T initial, Component name, ValueChange<T> onChange) {
		super(x, y, width, height, Component.empty(), button -> { });
		this.label = label;
		this.values = values;
		this.name = name;
		this.onChange = onChange;
		this.index = Math.max(0, values.indexOf(initial));
		updateMessage();
	}

	static <T> StyledCycleButton<T> of(Function<T, Component> label, T initial, List<T> values,
			int x, int y, int width, int height, Component name, ValueChange<T> onChange) {
		return new StyledCycleButton<>(x, y, width, height, label, values, initial, name, onChange);
	}

	static StyledCycleButton<Boolean> onOff(boolean initial, int x, int y, int width, int height,
			Component name, ValueChange<Boolean> onChange) {
		return of(value -> value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF,
				initial, List.of(Boolean.FALSE, Boolean.TRUE), x, y, width, height, name, onChange);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		index = (index + 1) % values.size();
		updateMessage();
		onChange.accept(this, values.get(index));
		super.onClick(event, doubleClick);
	}

	private void updateMessage() {
		Component value = label.apply(values.get(index));
		setMessage(name.getString().isEmpty() ? value
				: Component.empty().append(name).append(": ").append(value));
	}
}
