package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /wymiana <cena> - wystawia przedmiot z ręki na Rynku na 3 dni. */
public class WymianaCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public WymianaCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage("§cUżycie: /wymiana <cena> §7- wystawia trzymany przedmiot na rynku na 3 dni");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage("§cTrzymaj w ręce przedmiot, który chcesz wystawić.");
            return true;
        }
        double price;
        try {
            price = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cCena musi być liczbą.");
            return true;
        }
        if (price <= 0) {
            player.sendMessage("§cCena musi być większa od zera.");
            return true;
        }

        ItemStack listed = held.clone();
        player.getInventory().setItemInMainHand(null);
        plugin.getMarket().createListing(player, listed, price);
        player.sendMessage("§aWystawiono §f" + MarketManager.describeItem(listed) + " §ana rynku za §f"
                + BankGuiManager.formatMoney(price) + " §amonet na 3 dni. Sprawdź §f/rynek§a.");
        return true;
    }
}
