package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CrateCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public CrateCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final String ADMIN_PERMISSION = "boxpvp.crate.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // "list" i "preview" moze kazdy, reszta to admin
        boolean publicSub = sub.equals("list") || sub.equals("preview");
        if (!publicSub && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania skrzyniami.");
            return true;
        }

        switch (sub) {
            case "create":
                return handleCreate(sender, args);
            case "givekey":
                return handleGiveKey(sender, args);
            case "givekeytoall":
                return handleGiveKeyToAll(sender, args);
            case "setkeytexture":
                return handleSetKeyTexture(sender, args);
            case "bind":
                return handleBind(sender, args);
            case "unbind":
                return handleUnbind(sender);
            case "sethologram":
                return handleSetHologram(sender, args);
            case "seteffect":
                return handleSetEffect(sender, args);
            case "setprivate":
                return handleSetPrivate(sender, args);
            case "setfreecooldown":
                return handleSetFreeCooldown(sender, args);
            case "setidleeffect":
                return handleSetIdleEffect(sender, args);
            case "setdisplayheight":
                return handleSetDisplayHeight(sender, args);
            case "purgedisplays":
                return handlePurgeDisplays(sender);
            case "preview":
                return handlePreview(sender, args);
            case "list":
                List<String> names = plugin.getCrates().names();
                if (names.isEmpty()) {
                    sender.sendMessage("§eSkrzynie: §fbrak");
                    return true;
                }
                sender.sendMessage("§eSkrzynie:");
                for (String name : names) {
                    sender.sendMessage("§7 - §f" + name + " §7(" + plugin.getCrates().countLocations(name) + " postawionych, efekt: "
                            + plugin.getCrates().getEffect(name).getDisplayName() + ")");
                }
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Branding.accent("--- /crate ---"));
        sender.sendMessage("§c/crate create <nazwa> §7- konfiguruje nagrody skrzyni (GUI)");
        sender.sendMessage("§c/crate givekey <nazwa> <gracz> [ilość] §7- daje klucz graczowi");
        sender.sendMessage("§c/crate givekeytoall <nazwa> [ilość] §7- daje klucz wszystkim graczom online");
        sender.sendMessage("§c/crate setkeytexture <nazwa> <tekstura|none> §7- customowa tekstura klucza ("
                + String.join(", ", CrateTabCompleter.KEY_TEXTURES) + ")");
        sender.sendMessage("§c/crate bind <nazwa> [model3d] §7- przypina blok, na który patrzysz, jako fizyczną skrzynię"
                + " (opcjonalny model 3D zamienia blok na niewidzialną barierę + model obrócony w kierunku w jakim patrzysz)");
        sender.sendMessage("§c/crate unbind §7- odpina fizyczną skrzynię, na którą patrzysz");
        sender.sendMessage("§c/crate sethologram <nazwa> <tekst> §7- ustawia napis hologramu (obsługuje &kody kolorów)");
        sender.sendMessage("§c/crate seteffect <nazwa> <efekt> §7- ustawia efekt otwarcia: " + CrateEffect.listNames());
        sender.sendMessage("§c/crate setprivate <nazwa> <true|false> §7- wymaga uprawnienia LuckPerms do otwarcia");
        sender.sendMessage("§c/crate setfreecooldown <nazwa> <godziny> §7- darmowe otwarcie bez klucza co X godzin (0 = wyłącz)");
        sender.sendMessage("§c/crate setidleeffect <nazwa> <efekt|none> §7- cichy efekt widoczny gdy nikt nie otwiera skrzyni");
        sender.sendMessage("§c/crate setdisplayheight <wysokość> §7- ile bloków nad skrzynią unosi się pływający przedmiot (na żywo, wszystkie skrzynie)");
        sender.sendMessage("§c/crate purgedisplays §7- usuwa i stawia od nowa WSZYSTKIE pływające przedmioty, bez restartu serwera");
        sender.sendMessage("§c/crate preview <nazwa> §7- podgląd zawartości skrzyni z procentami (dla każdego)");
        sender.sendMessage("§c/crate list §7- lista skrzyń");
    }

    private boolean handleBind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /crate bind <nazwa> [model3d] §7(patrząc na blok skrzyni)");
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];

        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje. Najpierw /crate create " + name);
            return true;
        }

        String modelName = null;
        if (args.length >= 3) {
            modelName = args[2];
            if (!plugin.getCrateModels().exists(modelName)) {
                sender.sendMessage("§cNieznany model '" + modelName + "'. Dostępne: "
                        + String.join(", ", plugin.getCrateModels().names()));
                return true;
            }
        }

        Block target = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            player.sendMessage("§cPatrz na blok, który ma być fizyczną skrzynią (max. 6 bloków).");
            return true;
        }

        if (plugin.getCrates().getCrateNameAt(target.getLocation()) != null) {
            player.sendMessage("§cTen blok jest już przypięty do innej skrzyni.");
            return true;
        }

        if (!plugin.getDecentHolograms().isAvailable()) {
            player.sendMessage("§eUwaga: DecentHolograms nie jest zainstalowany - skrzynia zadziała, ale bez hologramu.");
        }

        // tylko poziomy kierunek (yaw) - patrzysz w gorę/dół nie ma znaczenia dla modelu 3D
        float yaw = player.getLocation().getYaw();
        plugin.getCrates().bindLocation(name, target.getLocation(), modelName, yaw);
        if (modelName != null) {
            player.sendMessage("§aPrzypięto blok jako skrzynię '" + name + "' z modelem 3D '" + modelName
                    + "' (blok zamieniony na niewidzialną barierę). Osobny hologram został tam postawiony.");
        } else {
            player.sendMessage("§aPrzypięto blok jako skrzynię '" + name + "'. Osobny hologram został tam postawiony.");
        }
        return true;
    }

    private boolean handleUnbind(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;
        Block target = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (target == null || target.getType().isAir()) {
            player.sendMessage("§cPatrz na blok fizycznej skrzyni, którą chcesz odpiąć.");
            return true;
        }

        String removed = plugin.getCrates().unbindLocation(target.getLocation());
        if (removed == null) {
            player.sendMessage("§cTen blok nie jest przypięty do żadnej skrzyni.");
        } else {
            player.sendMessage("§aOdpięto blok od skrzyni '" + removed + "' i usunięto jej hologram.");
        }
        return true;
    }

    private boolean handleSetHologram(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate sethologram <nazwa> <tekst>");
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje.");
            return true;
        }

        StringBuilder text = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) {
                text.append(' ');
            }
            text.append(args[i]);
        }

        plugin.getCrates().setHologramTitle(name, text.toString());
        sender.sendMessage("§aZaktualizowano napis hologramu skrzyni '" + name + "' na wszystkich postawionych lokalizacjach.");
        return true;
    }

    private boolean handleSetEffect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate seteffect <nazwa> <efekt> §7- dostępne: " + CrateEffect.listNames());
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje.");
            return true;
        }

        CrateEffect effect = CrateEffect.fromString(args[2]);
        if (effect == null) {
            sender.sendMessage("§cNieznany efekt. Dostępne: " + CrateEffect.listNames());
            return true;
        }

        plugin.getCrates().setEffect(name, effect);
        sender.sendMessage("§aUstawiono efekt otwarcia '" + effect.getDisplayName() + "' dla skrzyni '" + name + "'.");
        return true;
    }

    private boolean handleSetPrivate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate setprivate <nazwa> <true|false>");
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje.");
            return true;
        }

        boolean value = Boolean.parseBoolean(args[2]);
        plugin.getCrates().setPrivate(name, value);
        String permission = plugin.getCrates().getPrivatePermission(name);

        if (value) {
            sender.sendMessage("§aSkrzynia '" + name + "' jest teraz prywatna.");
            sender.sendMessage("§7Nadaj graczom uprawnienie (np. w LuckPerms): §f" + permission);
        } else {
            sender.sendMessage("§aSkrzynia '" + name + "' jest teraz publiczna (bez wymogu uprawnienia).");
        }
        return true;
    }

    private boolean handleSetFreeCooldown(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate setfreecooldown <nazwa> <godziny> §7(0 = wyłącz)");
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje.");
            return true;
        }

        int hours;
        try {
            hours = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cGodziny muszą być liczbą.");
            return true;
        }
        if (hours < 0) {
            sender.sendMessage("§cLiczba godzin nie może być ujemna.");
            return true;
        }

        plugin.getCrates().setFreeCooldownHours(name, hours);
        if (hours == 0) {
            sender.sendMessage("§aWyłączono darmowe otwieranie skrzyni '" + name + "'.");
        } else {
            sender.sendMessage("§aGracze mogą teraz otworzyć skrzynię '" + name + "' za darmo raz na " + hours + "h (bez klucza).");
        }
        return true;
    }

    private boolean handlePurgeDisplays(CommandSender sender) {
        plugin.getCrates().initializeItemDisplays();
        sender.sendMessage("§aOdświeżono pływające przedmioty nad wszystkimi skrzyniami - stare/osierocone usunięte, "
                + "świeże postawione. Bez restartu serwera.");
        return true;
    }

    private boolean handleSetIdleEffect(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate setidleeffect <nazwa> <efekt|none>");
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje.");
            return true;
        }

        if (args[2].equalsIgnoreCase("none")) {
            plugin.getCrates().setIdleEffect(name, null);
            sender.sendMessage("§aWyłączono efekt idle dla skrzyni '" + name + "'.");
            return true;
        }

        CrateEffect effect = CrateEffect.fromString(args[2]);
        if (effect == null) {
            sender.sendMessage("§cNieznany efekt. Dostępne: " + CrateEffect.listNames() + ", none");
            return true;
        }

        plugin.getCrates().setIdleEffect(name, effect);
        sender.sendMessage("§aUstawiono efekt idle '" + effect.getDisplayName()
                + "' dla skrzyni '" + name + "' (widoczny, gdy nikt jej nie otwiera).");
        return true;
    }

    private boolean handleSetDisplayHeight(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /crate setdisplayheight <wysokość> §7(np. 3.5, obecnie: "
                    + plugin.getCrateItemDisplays().getHeight() + ")");
            return true;
        }

        double height;
        try {
            height = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cWysokość musi być liczbą (np. 3.5).");
            return true;
        }
        if (height < 0 || height > 10) {
            sender.sendMessage("§cWysokość musi być między 0 a 10.");
            return true;
        }

        plugin.getCrateItemDisplays().setHeight(height);
        sender.sendMessage("§aUstawiono wysokość pływających przedmiotów na §f" + height
                + " §abloków nad skrzynią - wszystkie postawione skrzynie zaktualizowane od razu, bez restartu.");
        return true;
    }

    private boolean handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /crate preview <nazwa>");
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje.");
            return true;
        }

        plugin.getCratePreviewGui().open((Player) sender, name);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /crate create <nazwa>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];

        Inventory inv = Bukkit.createInventory(new CrateConfigGuiHolder(name), 27, Branding.accent("Konfiguracja:") + " §f" + name);
        List<CrateReward> existing = plugin.getCrates().getRewards(name);
        for (int i = 0; i < existing.size() && i < 27; i++) {
            CrateReward reward = existing.get(i);
            ItemStack display = reward.locked()
                    ? plugin.getCrates().applyChanceTag(reward.item(), reward.chance())
                    : reward.item();
            inv.setItem(i, display);
        }

        player.openInventory(inv);
        player.sendMessage("§aUmieść przedmioty, które mają wypadać z tej skrzyni.");
        player.sendMessage("§7PPM na przedmiot §f= wpisz na czacie procent szansy. Nieoznaczone przedmioty dostają automatycznie resztę do 100%.");
        player.sendMessage("§7Ilość sztuk w slocie to teraz TYLKO ilość wypłaty (nie wpływa już na szansę). Zamknij ekwipunek, aby zapisać.");
        return true;
    }

    private boolean handleGiveKey(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate givekey <nazwa> <gracz> [ilość]");
            return true;
        }

        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje. Najpierw /crate create " + name);
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cGracz '" + args[2] + "' nie jest online.");
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cIlość musi być liczbą.");
                return true;
            }
        }

        ItemStack key = plugin.getCrates().createKey(name, amount);
        target.getInventory().addItem(key);

        sender.sendMessage("§aDano " + amount + "x klucz do '" + name + "' graczowi " + target.getName() + ".");
        target.sendMessage("§aOtrzymujesz " + amount + "x klucz do skrzyni '" + name + "'!");
        return true;
    }

    private boolean handleGiveKeyToAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /crate givekeytoall <nazwa> [ilość]");
            return true;
        }

        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje. Najpierw /crate create " + name);
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cIlość musi być liczbą.");
                return true;
            }
        }

        int count = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            ItemStack key = plugin.getCrates().createKey(name, amount);
            target.getInventory().addItem(key);
            target.sendMessage("§aOtrzymujesz " + amount + "x klucz do skrzyni '" + name + "'!");
            count++;
        }

        sender.sendMessage("§aDano " + amount + "x klucz do '" + name + "' wszystkim graczom online (" + count + ").");
        return true;
    }

    private boolean handleSetKeyTexture(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /crate setkeytexture <nazwa> <tekstura|none>");
            return true;
        }
        String name = args[1];
        if (!plugin.getCrates().exists(name)) {
            sender.sendMessage("§cSkrzynia '" + name + "' nie istnieje. Najpierw /crate create " + name);
            return true;
        }
        String texture = args[2];
        if (texture.equalsIgnoreCase("none")) {
            plugin.getCrates().setKeyTexture(name, null);
            sender.sendMessage("§aUsunięto customową teksturę klucza dla '" + name + "' (wraca do zwykłego wyglądu).");
            return true;
        }
        if (!CrateTabCompleter.KEY_TEXTURES.contains(texture)) {
            sender.sendMessage("§cNieznana tekstura '" + texture + "'. Dostępne: "
                    + String.join(", ", CrateTabCompleter.KEY_TEXTURES));
            return true;
        }
        plugin.getCrates().setKeyTexture(name, texture);
        sender.sendMessage("§aUstawiono teksturę klucza '" + texture + "' dla skrzyni '" + name + "'.");
        return true;
    }
}
