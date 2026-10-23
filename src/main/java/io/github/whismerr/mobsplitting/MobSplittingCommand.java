package io.github.whismerr.mobsplitting;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/** Handles {@code /mobsplitting info} and {@code /mobsplitting reload}. */
final class MobSplittingCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("info", "reload");

    private final MobSplittingPlugin plugin;

    MobSplittingCommand(MobSplittingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadSplitConfig();
                sender.sendMessage(Component.text("MobSplitting configuration reloaded.", NamedTextColor.GREEN));
            }
            case "info" -> sendInfo(sender);
            default -> sender.sendMessage(Component.text("Usage: /" + label + " <info|reload>", NamedTextColor.RED));
        }
        return true;
    }

    private void sendInfo(CommandSender sender) {
        SplitConfig config = plugin.splitConfig();
        sender.sendMessage(Component.text("MobSplitting " + plugin.getPluginMeta().getVersion(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Enabled: " + config.enabled()
                + " | Scales: stage 2 = " + config.stage2Scale() + ", stage 3 = " + config.stage3Scale(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Excluded mobs: " + config.excludedTypes(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Splits since startup: " + plugin.splitCount()
                + " | Offspring spawned: " + plugin.offspringCount(), NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
