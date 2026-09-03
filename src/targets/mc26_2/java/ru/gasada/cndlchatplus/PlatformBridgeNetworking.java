package ru.gasada.cndlchatplus;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

final class PlatformBridgeNetworking {
	private static final VnbxBridgeClient CLIENT = new VnbxBridgeClient();
	private static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath("vnbx", "bridge"));
	private static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
			CustomPacketPayload.codec(Payload::write, Payload::read);

	private PlatformBridgeNetworking() { }

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.client().execute(() -> CLIENT.receive(payload.data())));
	}

	static void connected() {
		CLIENT.reset();
		if (!ClientPlayNetworking.canSend(TYPE)) return;
		ClientPlayNetworking.send(new Payload(VnbxBridgeClient.request("hello")));
		ClientPlayNetworking.send(new Payload(VnbxBridgeClient.request("request_snapshot")));
	}

	static void disconnected() {
		CLIENT.reset();
	}

	private record Payload(byte[] data) implements CustomPacketPayload {
		private static Payload read(RegistryFriendlyByteBuf buffer) {
			int size = buffer.readableBytes();
			if (size > VnbxBridgeClient.MAX_PAYLOAD_BYTES) {
				buffer.skipBytes(size);
				return new Payload(null);
			}
			byte[] data = new byte[size];
			buffer.readBytes(data);
			return new Payload(data);
		}

		private void write(RegistryFriendlyByteBuf buffer) {
			buffer.writeBytes(data);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
