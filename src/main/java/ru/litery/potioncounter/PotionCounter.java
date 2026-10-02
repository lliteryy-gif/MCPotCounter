package ru.litery.potioncounter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class PotionCounter implements ClientModInitializer {
    public static final CounterStore STORE = new CounterStore();
    private static final Map<UUID, PotionEntity> PENDING = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("potioncounter.json");
    public static Config config = new Config();
    public static final class Config { public boolean left = false; public boolean visible = true; }

    @Override public void onInitializeClient() {
        if (Files.exists(CONFIG_PATH)) {
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                if (loaded != null) config = loaded;
            } catch (Exception e) { LoggerFactory.getLogger("potioncounter").warn("Cannot read config", e); }
        }
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof PlayerEntity player) remember(player);
            if (entity instanceof PotionEntity potion) {
                PENDING.put(potion.getUuid(), potion);
                observe(potion);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            observe(entity);
            PENDING.remove(entity.getUuid());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Retry until metadata and the actual owner arrive; never guess nearest player.
            for (PotionEntity potion : List.copyOf(PENDING.values())) {
                if (observe(potion) || potion.isRemoved()) PENDING.remove(potion.getUuid());
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearSession());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("potcounter")
                .executes(ctx -> message(ctx.getSource(), "Команды: /potcounter reset <ник>, resetall, side left|right, show, hide, count <ник>"))
                .then(literal("side")
                    .then(literal("left").executes(ctx -> side(ctx.getSource(), true)))
                    .then(literal("right").executes(ctx -> side(ctx.getSource(), false))))
                .then(literal("show").executes(ctx -> visibility(ctx.getSource(), true)))
                .then(literal("hide").executes(ctx -> visibility(ctx.getSource(), false)))
                .then(literal("resetall").executes(ctx -> {
                    STORE.resetAll(); return message(ctx.getSource(), "Все счётчики сброшены.");
                }))
                .then(literal("reset").then(argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        rememberOnline();
                        STORE.names().stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                        return builder.buildFuture();
                    }).executes(ctx -> playerCommand(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                .then(literal("count").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> playerCommand(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false)))));
        });
    }
    private static void remember(PlayerEntity p) { STORE.remember(p.getUuid(), p.getName().getString()); }
    private static void rememberOnline() {
        var world = MinecraftClient.getInstance().world;
        if (world != null) world.getPlayers().forEach(PotionCounter::remember);
    }
    public static boolean observe(Entity entity) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || !(entity instanceof PotionEntity potion)
                || client.world.getEntityById(entity.getId()) != entity) return false;
        var stack = potion.getStack();
        if (!stack.isOf(Items.SPLASH_POTION)) return false;
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null || !contents.matches(Potions.STRONG_HEALING)) return false;
        if (!(potion.getOwner() instanceof PlayerEntity owner)) return false;
        STORE.record(potion.getUuid(), owner.getUuid(), owner.getName().getString());
        return true;
    }
    public static void observeId(int id) {
        var world = MinecraftClient.getInstance().world;
        if (world != null) observe(world.getEntityById(id));
    }
    public static Text decorate(PlayerEntity player, Text original) {
        if (!config.visible) return original;
        Text counter = Text.literal("[Поты: " + STORE.count(player.getUuid()) + "]").formatted(Formatting.LIGHT_PURPLE);
        return config.left ? Text.empty().append(counter).append(" ").append(original)
            : Text.empty().append(original).append(" ").append(counter);
    }
    private static int playerCommand(FabricClientCommandSource source, String name, boolean reset) {
        rememberOnline();
        var matches = STORE.find(name);
        if (matches.size() != 1) {
            source.sendError(Text.literal(matches.isEmpty() ? "Игрок не найден: " + name : "Ник неоднозначен; переподключись для очистки истории."));
            return 0;
        }
        UUID id = matches.getFirst();
        if (reset) STORE.reset(id);
        return message(source, name + ": " + STORE.count(id) + " зелий" + (reset ? " (сброшено)" : ""));
    }
    private static int side(FabricClientCommandSource source, boolean left) {
        config.left = left;
        save(source);
        return message(source, "Счётчик " + (left ? "слева" : "справа") + " от ника.");
    }
    private static int visibility(FabricClientCommandSource source, boolean visible) {
        config.visible = visible; save(source);
        return message(source, visible ? "Отображение включено." : "Отображение скрыто; подсчёт продолжается.");
    }
    private static void save(FabricClientCommandSource source) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) { source.sendError(Text.literal("Настройка применена, но не сохранена: " + e.getMessage())); }
    }
    private static int message(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal("[Potion Counter] " + message)); return 1;
    }
    private static void clearSession() { PENDING.clear(); STORE.endSession(); }
}
