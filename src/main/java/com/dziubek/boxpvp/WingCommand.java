package com.dziubek.boxpvp;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /wings - GUI wyboru odblokowanych skrzydeł.
 * /wings giveitem <gatunek> [gracz] [ilość] - admin: daje przedmiot odblokowujący dane skrzydła,
 * który MOŻNA POTEM RĘCZNIE dołożyć do dowolnej skrzyni przez /crate create (np. "cosmetic") -
 * ten kod nigdy sam nie dotyka konfiguracji żadnej skrzyni.
 * /wings debug [clear] - admin: rozstawia WSZYSTKIE gatunki dookoła gracza (z podpisami nad
 * każdym), żeby porównać na żywo pozycję/rozmiar/teksturę bez zakładania ich po kolei.
 */
public class WingCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";
    private static final double DEBUG_RADIUS = 4.0;
    private static final double DEBUG_LABEL_HEIGHT = 2.3;

    private static final Map<UUID, List<ArmorStand>> debugSpawns = new HashMap<>();

    private final BoxPvpPlugin plugin;

    public WingCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Tej komendy może użyć tylko gracz.");
                return true;
            }
            plugin.getWingGui().open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("giveitem")) {
            return handleGiveItem(sender, args);
        }
        if (args[0].equalsIgnoreCase("debug")) {
            return handleDebug(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleGiveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /wings giveitem <gatunek> [gracz] [ilość] §7- dostępne: "
                    + String.join(", ", WingSpecies.ORDER));
            return true;
        }

        String species = args[1].toLowerCase();
        if (WingSpecies.of(species) == null) {
            sender.sendMessage("§cNieznany gatunek '" + species + "'. Dostępne: " + String.join(", ", WingSpecies.ORDER));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cGracz " + args[2] + " nie jest online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cUżycie: /wings giveitem <gatunek> <gracz> [ilość]");
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                // zostaje 1
            }
        }

        target.getInventory().addItem(WingUnlockItem.create(plugin, species, amount));
        sender.sendMessage("§aDano " + target.getName() + " " + amount + "x przedmiot odblokowujący skrzydła '" + species
                + "'. §7Dołóż go do skrzyni przez /crate create <nazwa>, jeśli chcesz, żeby wypadał jako nagroda.");
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        clearDebug(player);

        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            player.sendMessage("§aPosprzątane.");
            return true;
        }

        if (!BetterModelInstaller.isBetterModelPresent()) {
            player.sendMessage("§cBetterModel nie jest zainstalowany.");
            return true;
        }

        List<String> species = WingSpecies.ORDER;
        Location center = player.getLocation();
        List<ArmorStand> spawned = new ArrayList<>();
        int placed = 0;

        for (int i = 0; i < species.size(); i++) {
            String sp = species.get(i);
            var rendererOpt = BetterModel.model(BetterModelInstaller.wingModelName(sp));
            if (rendererOpt.isEmpty()) {
                player.sendMessage("§cModel skrzydeł '" + sp + "' nie jest wczytany w BetterModel.");
                continue;
            }

            double angle = (2 * Math.PI * i) / species.size();
            double x = center.getX() + DEBUG_RADIUS * Math.cos(angle);
            double z = center.getZ() + DEBUG_RADIUS * Math.sin(angle);
            Location spot = new Location(center.getWorld(), x, center.getY(), z);

            ArmorStand anchor = spot.getWorld().spawn(spot, ArmorStand.class, a -> {
                a.setInvisible(true);
                a.setMarker(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setPersistent(false);
            });
            rendererOpt.get().getOrCreate(BukkitAdapter.adapt(anchor));
            spawned.add(anchor);

            WingSpecies.Info info = WingSpecies.of(sp);
            Location labelSpot = spot.clone().add(0, DEBUG_LABEL_HEIGHT, 0);
            ArmorStand labelHolder = labelSpot.getWorld().spawn(labelSpot, ArmorStand.class, a -> {
                a.setInvisible(true);
                a.setMarker(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setPersistent(false);
                a.setCustomName("§b§l" + (info != null ? info.displayName() : sp) + " §7[" + sp + "]");
                a.setCustomNameVisible(true);
            });
            spawned.add(labelHolder);
            placed++;
        }

        debugSpawns.put(player.getUniqueId(), spawned);
        player.sendMessage("§aRozstawiono " + placed + "/" + species.size()
                + " typów skrzydeł dookoła Ciebie z podpisami. §7/wings debug clear §aby posprzątać.");
        return true;
    }

    private void clearDebug(Player player) {
        List<ArmorStand> old = debugSpawns.remove(player.getUniqueId());
        if (old == null) {
            return;
        }
        for (ArmorStand stand : old) {
            if (stand.isValid()) {
                stand.remove();
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b/wings §7- GUI wyboru odblokowanych skrzydeł");
        sender.sendMessage("§b/wings giveitem <gatunek> [gracz] [ilość] §7- przedmiot odblokowujący skrzydła (admin, "
                + "wrzuć go potem ręcznie do skrzyni przez /crate create)");
        sender.sendMessage("§b/wings debug §7- rozstawia WSZYSTKIE gatunki dookoła Ciebie z podpisami (admin, do porównania)");
        sender.sendMessage("§b/wings debug clear §7- sprząta rozstawione powyżej");
    }
}
