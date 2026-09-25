package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CrateRollAnimation {

    private static final int GUI_SIZE = 27;
    private static final int TOTAL_STEPS = 24;

    // srodkowy rzad (9-17) - bebenek kreci sie w slotach 11-15, wynik laduje na 13
    private static final int[] REEL_SLOTS = {11, 12, 13, 14, 15};
    private static final int RESULT_SLOT = 13;

    // strzalki nad i pod slotem wyniku, wskazujace gdzie wyladuje wygrana
    private static final int ARROW_TOP_SLOT = 4;
    private static final int ARROW_BOTTOM_SLOT = 22;

    // progi rzadkosci wg CrateReward.chance() (%) - im nizszy procent, tym rzadszy przedmiot
    private static final double LEGENDARY_THRESHOLD = 5.0;
    private static final double RARE_THRESHOLD = 15.0;

    // ile trzyma sie widok wygranej w GUI zanim samo sie zamknie (wariant z animacja)
    private static final int REVEAL_HOLD_TICKS = 20;
    // jak dlugo kamera jest "przytrzymana" na plywajacym przedmiocie nad skrzynia - dopasowane
    // do tego, jak dlugo CrateItemDisplayManager trzyma przedmiot "duzy" po wygranej (spadanie
    // ~1.1s + 2s duzy w miejscu spoczynku), zeby wybuch totemu wypadl dokladnie na koniec
    private static final int CUTSCENE_TICKS = 62;

    public static void play(BoxPvpPlugin plugin, Player player, String crateName, List<CrateReward> rewards,
                             Location crateBlockLocation) {
        String customTitle = Branding.customCrateTitle(crateName, Branding.CrateScreen.ROLL);
        String title = customTitle != null ? customTitle : Branding.accent("Otwieranie:") + " §f" + crateName;
        Inventory inv = Bukkit.createInventory(new CrateRollGuiHolder(), GUI_SIZE, title);
        if (customTitle == null) {
            paintFrame(inv, Material.BLACK_STAINED_GLASS_PANE);
        }

        Random random = new Random();
        List<ItemStack> reel = new ArrayList<>();
        for (int i = 0; i < REEL_SLOTS.length; i++) {
            reel.add(rewards.get(random.nextInt(rewards.size())).item());
        }
        renderReel(inv, reel);

        player.openInventory(inv);
        TitleUtil.show(player, Branding.accent("Losowanie..."), "§7" + crateName);
        step(plugin, player, inv, crateName, rewards, reel, random, 0, crateBlockLocation);
    }

    /**
     * Wariant bez bębenka - od razu losuje i wydaje nagrodę (wybór "Otwórz bez animacji").
     */
    public static void playInstant(BoxPvpPlugin plugin, Player player, String crateName, List<CrateReward> rewards,
                                    Location crateBlockLocation) {
        if (!player.isOnline()) {
            return;
        }
        CrateReward wonReward = pickWeighted(rewards, new Random());
        applyReward(plugin, player, crateName, wonReward, crateBlockLocation);
        playCutscene(plugin, player, crateBlockLocation);
    }

    private static void step(BoxPvpPlugin plugin, Player player, Inventory inv, String crateName,
                              List<CrateReward> rewards, List<ItemStack> reel, Random random, int tick,
                              Location crateBlockLocation) {

        if (!player.isOnline()) {
            return; // gracz sie rozlaczyl - nie da sie kontynuowac
        }

        if (!player.getOpenInventory().getTopInventory().equals(inv)) {
            // gracz zamknal GUI w trakcie losowania - otwieramy z powrotem i kontynuujemy,
            // zamkniecie okna NIE przerywa losowania, tylko je chwilowo chowa
            player.openInventory(inv);
        }

        if (tick < TOTAL_STEPS) {
            // szpula "przesuwa sie" - najstarszy przedmiot wypada, nowy losowy wjezdza z prawej
            reel.remove(0);
            reel.add(rewards.get(random.nextInt(rewards.size())).item());
            renderReel(inv, reel);

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f + (tick * 0.02f));
            if (tick % 2 == 0) {
                player.spawnParticle(Particle.END_ROD, player.getEyeLocation(), 1, 0.15, 0.1, 0.15, 0.01);
            }

            // im blizej konca, tym mocniej zwalnia - buduje napiecie tuz przed odkryciem
            long delay = 2 + (long) ((tick * (double) tick) / 40.0);
            int next = tick + 1;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> step(plugin, player, inv, crateName, rewards, reel, random, next, crateBlockLocation), delay);
        } else {
            CrateReward wonReward = pickWeighted(rewards, random);
            ItemStack won = wonReward.item().clone();

            if (Branding.customCrateTitle(crateName, Branding.CrateScreen.ROLL) == null) {
                paintFrame(inv, frameColorFor(wonReward.chance()));
            }
            inv.setItem(RESULT_SLOT, won);

            applyReward(plugin, player, crateName, wonReward, crateBlockLocation);

            // po chwili pokazywania wyniku w GUI, okno samo sie zamyka i startuje "cutscenka"
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inv)) {
                    player.closeInventory();
                }
                playCutscene(plugin, player, crateBlockLocation);
            }, REVEAL_HOLD_TICKS);
        }
    }

    /**
     * "Zamraża" gracza w miejscu (jak przy tpa) i przez chwilę przymusowo obraca mu kamerę
     * w stronę pływającego przedmiotu nad skrzynią, na koniec odpalając efekt wybuchu totemu.
     */
    private static void playCutscene(BoxPvpPlugin plugin, Player player, Location crateBlockLocation) {
        if (crateBlockLocation == null || !player.isOnline()) {
            return;
        }
        Location anchor = player.getLocation();
        forceLookAt(plugin, player, anchor, crateBlockLocation, CUTSCENE_TICKS);
    }

    /**
     * Co tick na nowo pyta CrateItemDisplayManager, gdzie DOKŁADNIE przedmiot jest teraz
     * renderowany (uwzględnia opadanie/bujanie) - więc kamera realnie za nim podąża,
     * zamiast patrzeć w jeden stały punkt.
     */
    private static void forceLookAt(BoxPvpPlugin plugin, Player player, Location anchor,
                                     Location crateBlockLocation, int ticksLeft) {
        if (!player.isOnline()) {
            return;
        }
        Location target = plugin.getCrateItemDisplays().getVisualLocation(crateBlockLocation);

        if (ticksLeft <= 0) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target, 150, 0.8, 0.8, 0.8, 0.6);
            player.getWorld().playSound(target, Sound.ITEM_TOTEM_USE, 1.5f, 1.0f);
            return;
        }

        CameraUtil.forceLookAt(player, anchor, target);

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> forceLookAt(plugin, player, anchor, crateBlockLocation, ticksLeft - 1), 1L);
    }

    /**
     * Wydaje nagrodę, zapisuje statystyki, wysyła wiadomości/ogłoszenia i podmienia przedmiot
     * nad fizyczną skrzynią na wygrany. Wspólne dla wariantu z animacją i bez.
     */
    private static void applyReward(BoxPvpPlugin plugin, Player player, String crateName, CrateReward wonReward,
                                     Location crateBlockLocation) {
        ItemStack won = wonReward.item().clone();

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(won.clone());
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }

        plugin.getStats().recordCrateOpened(player.getUniqueId(), player.getName());
        plugin.getMissions().addProgress(player, MissionManager.Type.CRATES_OPENED, 1);

        String name = itemDisplayName(won);
        player.sendMessage("§aWygrałeś: §f" + name + " §7(x" + won.getAmount() + ") §7ze skrzyni '" + crateName + "'!");

        announceRarity(plugin, player, crateName, name, wonReward.chance(), crateBlockLocation);

        if (crateBlockLocation != null) {
            plugin.getCrateItemDisplays().highlightWin(crateBlockLocation, won);
        }
    }

    private static void renderReel(Inventory inv, List<ItemStack> reel) {
        for (int i = 0; i < REEL_SLOTS.length; i++) {
            inv.setItem(REEL_SLOTS[i], reel.get(i).clone());
        }
    }

    /**
     * Wypełnia całe GUI ramką z podanego materiału, a potem wstawia strzałki wskazujące
     * slot wyniku (nad i pod środkowym rzędem bębenka).
     */
    private static void paintFrame(Inventory inv, Material frameMaterial) {
        ItemStack frame = borderPane(frameMaterial);
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, frame);
        }
        inv.setItem(ARROW_TOP_SLOT, arrowPane(true));
        inv.setItem(ARROW_BOTTOM_SLOT, arrowPane(false));
    }

    private static ItemStack arrowPane(boolean pointingDown) {
        ItemStack pane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(pointingDown ? "§e§l▼ TU WYPADNIE ▼" : "§e§l▲ TU WYPADNIE ▲");
        pane.setItemMeta(meta);
        return pane;
    }

    private static Material frameColorFor(double chance) {
        if (chance < LEGENDARY_THRESHOLD) {
            return Material.YELLOW_STAINED_GLASS_PANE;
        }
        if (chance < RARE_THRESHOLD) {
            return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        }
        return Material.BLACK_STAINED_GLASS_PANE;
    }

    /**
     * Rzadsze przedmioty (niższa szansa w CrateReward.chance()) dostają lepszą oprawę:
     * LEGENDARY (&lt;5%) - fajerwerk, złoty tytuł, ogłoszenie na czacie całego serwera.
     * RZADKI (&lt;15%) - mniejszy tytuł tylko dla gracza, bez ogłoszenia.
     * Reszta - bez zmian (już obsłużone wyżej: dźwięk levelup + wiadomość na czacie).
     */
    private static void announceRarity(BoxPvpPlugin plugin, Player player, String crateName, String itemName,
                                        double chance, Location crateBlockLocation) {
        if (chance < LEGENDARY_THRESHOLD) {
            TitleUtil.show(player, Branding.accent(" LEGENDARY "), "§f" + itemName);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
            spawnFirework(plugin, player);
            if (crateBlockLocation != null) {
                spawnLegendaryBeacon(plugin, crateBlockLocation);
            }

            String broadcast = Branding.chatPrefix() + Branding.accent("") + " §e" + player.getName()
                    + " §dwylosował(a) RZADKI przedmiot §f" + itemName
                    + " §dze skrzyni '" + crateName + "'! " + Branding.accent("");
            Bukkit.getServer().broadcastMessage(broadcast);
            BossBarUtil.showTimed(plugin, Branding.accent(" LEGENDARY: ") + "§e" + player.getName() + " §7- §f" + itemName,
                    org.bukkit.boss.BarColor.YELLOW, 8L * 20L);
        } else if (chance < RARE_THRESHOLD) {
            TitleUtil.show(player, "§b§lRZADKI!", "§f" + itemName);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    /**
     * Widoczny z daleka snop cząsteczek strzelający w górę ze skrzyni przez kilka sekund -
     * żeby inni gracze na serwerze widzieli, gdzie właśnie wypadła legendarna nagroda.
     */
    private static void spawnLegendaryBeacon(BoxPvpPlugin plugin, Location crateBlockLocation) {
        Location base = crateBlockLocation.clone().add(0.5, 0, 0.5);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 100 || base.getWorld() == null) {
                    cancel();
                    return;
                }
                for (double y = 0; y < 16; y += 0.5) {
                    base.getWorld().spawnParticle(Particle.END_ROD, base.getX(), base.getY() + y, base.getZ(), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private static void spawnFirework(BoxPvpPlugin plugin, Player player) {
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.BURST)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);

        plugin.getServer().getScheduler().runTaskLater(plugin, firework::detonate, 2L);
    }

    private static CrateReward pickWeighted(List<CrateReward> rewards, Random random) {
        double totalWeight = 0;
        for (CrateReward reward : rewards) {
            totalWeight += Math.max(0, reward.chance());
        }
        if (totalWeight <= 0) {
            return rewards.get(random.nextInt(rewards.size()));
        }
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (CrateReward reward : rewards) {
            cumulative += Math.max(0, reward.chance());
            if (roll < cumulative) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private static String itemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().toString();
    }

    private static ItemStack borderPane(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }
}
