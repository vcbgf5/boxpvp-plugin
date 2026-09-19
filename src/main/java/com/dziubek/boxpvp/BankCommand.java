package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * /bank - Kantor: admin stawia/usuwa NPC i ustawia nominały (materiał -> wartość w monetach),
 * gracze wymieniają monety na fizyczne "banknoty" i z powrotem przez PPM na NPC (BankListener).
 */
public class BankCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public BankCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        boolean publicSub = sub.equals("list") || sub.equals("price");
        if (!publicSub && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania Kantorem.");
            return true;
        }

        switch (sub) {
            case "create":
                return handleCreate(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "price":
                return handlePrice(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Branding.accent("--- /bank ---"));
        sender.sendMessage("§c/bank create <nazwa> §7- stawia Kantor w Twojej pozycji");
        sender.sendMessage("§c/bank remove <nazwa> §7- usuwa Kantor");
        sender.sendMessage("§c/bank list §7- lista Kantorów");
        sender.sendMessage("§c/bank price set <materiał> <wartość> §7- ustawia nominał (np. IRON_INGOT 10)");
        sender.sendMessage("§c/bank price remove <materiał> §7- usuwa nominał");
        sender.sendMessage("§c/bank price list §7- lista aktualnych nominałów");
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bank create <nazwa>");
            return true;
        }
        String name = args[1];
        if (plugin.getBanks().exists(name)) {
            sender.sendMessage("§cKantor '" + name + "' już istnieje.");
            return true;
        }
        plugin.getBanks().createBank(name, ((Player) sender).getLocation());
        sender.sendMessage("§aPostawiono Kantor '" + name + "'. PPM otwiera wymianę walut.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bank remove <nazwa>");
            return true;
        }
        boolean removed = plugin.getBanks().removeBank(args[1]);
        sender.sendMessage(removed ? "§aUsunięto Kantor '" + args[1] + "'." : "§cNie znaleziono takiego Kantoru.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> names = plugin.getBanks().names();
        if (names.isEmpty()) {
            sender.sendMessage("§eKantory: §fbrak");
            return true;
        }
        sender.sendMessage("§eKantory: §f" + String.join(", ", names));
        return true;
    }

    private boolean handlePrice(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bank price set <materiał> <wartość>|remove <materiał>|list");
            return true;
        }
        String action = args[1].toLowerCase();
        if (!action.equals("list") && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania nominałami Kantoru.");
            return true;
        }

        switch (action) {
            case "set": {
                if (args.length < 4) {
                    sender.sendMessage("§cUżycie: /bank price set <materiał> <wartość>");
                    return true;
                }
                Material material = Material.matchMaterial(args[2].toUpperCase());
                if (material == null) {
                    sender.sendMessage("§cNieznany materiał: '" + args[2] + "'.");
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cWartość musi być liczbą.");
                    return true;
                }
                if (price <= 0) {
                    sender.sendMessage("§cWartość musi być większa od zera.");
                    return true;
                }
                plugin.getCurrency().setPrice(material, price);
                sender.sendMessage("§aUstawiono nominał " + material + " na " + BankGuiManager.formatMoney(price) + " monet.");
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /bank price remove <materiał>");
                    return true;
                }
                Material material = Material.matchMaterial(args[2].toUpperCase());
                boolean removed = material != null && plugin.getCurrency().removePrice(material);
                sender.sendMessage(removed ? "§aUsunięto nominał." : "§cTen materiał nie ma ustawionego nominału.");
                return true;
            }
            case "list": {
                Map<Material, Double> prices = plugin.getCurrency().all();
                if (prices.isEmpty()) {
                    sender.sendMessage("§eNominały Kantoru: §fbrak");
                    return true;
                }
                sender.sendMessage(Branding.accent("--- Nominały Kantoru ---"));
                for (Material material : plugin.getCurrency().orderedMaterials()) {
                    sender.sendMessage("§7- §f" + material + " §7- §a" + BankGuiManager.formatMoney(prices.get(material)) + " monet");
                }
                return true;
            }
            default:
                sender.sendMessage("§cUżycie: /bank price set <materiał> <wartość>|remove <materiał>|list");
                return true;
        }
    }
}
