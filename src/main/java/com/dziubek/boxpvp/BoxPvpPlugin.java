package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

/**
 * Box PvP: bez aren - jeden wspólny spawn (chroniony regionem WorldGuard "Spawn01" podczas
 * walki, tak jak w SurvivalManager), combat log, TPA (bez homów), kity, daily, sklep i
 * skrzynie 1:1 z SurvivalManager, oraz własne generatory bloków i handlarze-wieśniacy.
 * Pętla gry: gracze kopią bloki z generatorów, sprzedają je w /sklep za monety (Vault),
 * kupują lepszy sprzęt i biją się w jednym wielkim boxie (poza kontrolą pluginu).
 */
public class BoxPvpPlugin extends JavaPlugin {

    private CombatManager combatManager;
    private Location survivalSpawn;
    private TpaManager tpa;
    private KitManager kits;
    private DailyRewardManager daily;
    private CrateManager crates;
    private ShopManager shop;
    private ShopGuiManager shopGui;
    private Economy economy;
    private TeleportDelayManager teleportDelay;
    private ShopConfigManager shopConfig;
    private ShopConfigGuiManager shopConfigGui;
    private DecentHologramsHook decentHolograms;
    private CrateRewardSessionManager crateRewardSessions;
    private StatsManager stats;
    private CratePreviewGuiManager cratePreviewGui;
    private CrateItemDisplayManager crateItemDisplays;
    private CrateOpenChoiceGuiManager crateOpenChoiceGui;
    private GeneratorManager generators;
    private TraderManager traders;
    private TraderEditorGuiManager traderEditorGui;
    private SellManager sell;
    private KillstreakManager killstreaks;
    private PrestigeManager prestige;
    private EventManager events;
    private EnvoyDisplayManager envoy;
    private LeaderboardManager leaderboards;
    private ScoreboardManager scoreboards;
    private StatsGuiManager statsGui;
    private PartyManager party;
    private MissionManager missions;
    private MissionsGuiManager missionsGui;
    private ZombieEventManager zombieEvent;

    @Override
    public void onEnable() {
        combatManager = new CombatManager();
        loadSurvivalSpawn();

        tpa = new TpaManager(this);
        kits = new KitManager(this);
        daily = new DailyRewardManager(this);
        crates = new CrateManager(this);
        shop = new ShopManager(this);
        shopGui = new ShopGuiManager(this);
        teleportDelay = new TeleportDelayManager(this);
        shopConfig = new ShopConfigManager();
        shopConfigGui = new ShopConfigGuiManager(this);
        decentHolograms = new DecentHologramsHook(this);
        crateRewardSessions = new CrateRewardSessionManager();
        stats = new StatsManager(this);
        cratePreviewGui = new CratePreviewGuiManager(this);
        crateItemDisplays = new CrateItemDisplayManager(this);
        crateOpenChoiceGui = new CrateOpenChoiceGuiManager(this);
        generators = new GeneratorManager(this);
        traders = new TraderManager(this);
        traderEditorGui = new TraderEditorGuiManager(this);
        sell = new SellManager(this);
        prestige = new PrestigeManager(this);
        events = new EventManager(this);
        envoy = new EnvoyDisplayManager(this);
        killstreaks = new KillstreakManager(this);
        leaderboards = new LeaderboardManager(this);
        scoreboards = new ScoreboardManager(this);
        statsGui = new StatsGuiManager(this);
        party = new PartyManager(this);
        missions = new MissionManager(this);
        missionsGui = new MissionsGuiManager(this);
        zombieEvent = new ZombieEventManager(this);

        setupEconomy();

        crates.refreshAllHolograms();
        crates.initializeItemDisplays();
        crateItemDisplays.start();
        generators.start();
        traders.initialize();
        envoy.purgeOrphans();
        zombieEvent.purgeOrphans();
        leaderboards.start();
        scoreboards.start();
        events.start();

        getServer().getPluginManager().registerEvents(new CombatDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatKickListener(this), this);
        getServer().getPluginManager().registerEvents(new FirstJoinSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerSessionListener(this), this);
        getServer().getPluginManager().registerEvents(new PvpKillListener(this), this);
        getServer().getPluginManager().registerEvents(new DailyGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new CrateRewardChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopConfigGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GeneratorListener(this), this);
        getServer().getPluginManager().registerEvents(new GeneratorSellListener(this), this);
        getServer().getPluginManager().registerEvents(new TraderListener(this), this);
        getServer().getPluginManager().registerEvents(new TraderChatListener(this), this);
        getServer().getPluginManager().registerEvents(new EnvoyListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PartyQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new MissionsGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ZombieEventListener(this), this);

        getCommand("setsurvivalspawn").setExecutor(new SetSurvivalSpawnCommand(this));
        getCommand("spawn").setExecutor(new LocalSpawnCommand(this));
        getCommand("tpa").setExecutor(new TpaCommand(this));
        getCommand("tpaccept").setExecutor(new TpaAcceptCommand(this));
        getCommand("tpdeny").setExecutor(new TpaDenyCommand(this));
        getCommand("kit").setExecutor(new KitCommand(this));
        getCommand("daily").setExecutor(new DailyCommand(this));
        getCommand("crate").setExecutor(new CrateCommand(this));
        getCommand("crate").setTabCompleter(new CrateTabCompleter(this));
        getCommand("sklep").setExecutor(new ShopCommand(this));
        getCommand("bpvp").setExecutor(new GeneratorCommand(this));
        getCommand("bpvp").setTabCompleter(new GeneratorTabCompleter(this));
        getCommand("prestige").setExecutor(new PrestigeCommand(this));
        getCommand("stats").setExecutor(new StatsCommand(this));
        getCommand("party").setExecutor(new PartyCommand(this));
        getCommand("party").setTabCompleter(new PartyTabCompleter());
        getCommand("missions").setExecutor(new MissionsCommand(this));
        getCommand("gamma").setExecutor(new GammaCommand());

        getServer().getScheduler().runTaskTimer(this, new CombatActionBarTask(this), 20L, 20L);
        new CrateIdleEffectTask(this).runTaskTimer(this, 20L, 3L);

        boolean worldGuardFound = getServer().getPluginManager().getPlugin("WorldGuard") != null;
        if (worldGuardFound) {
            getServer().getPluginManager().registerEvents(new SpawnRegionGuardListener(this), this);
            getLogger().info("Wykryto WorldGuard - blokada regionu '" + getProtectedRegionName() + "' podczas walki aktywna.");
        } else {
            getLogger().warning("WorldGuard nie znaleziony - blokada regionu podczas walki WYŁĄCZONA.");
        }

        if (economy == null) {
            getLogger().warning("Vault + system ekonomii (np. EssentialsX) NIE znaleziony - /sklep nie będzie działać dopóki go nie zainstalujesz.");
        } else {
            getLogger().info("Ekonomia podłączona przez Vault: " + economy.getName());
        }

        getLogger().info("BoxPvPManager włączony! Czas walki: " + getCombatDurationSeconds() + "s, spawn ustawiony: " + hasSurvivalSpawn());
    }

    @Override
    public void onDisable() {
        if (crateItemDisplays != null) {
            crateItemDisplays.shutdown();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public long getCombatDurationSeconds() {
        return getConfig().getLong("combat-duration-seconds", 10);
    }

    public String getProtectedRegionName() {
        return getConfig().getString("protected-region", "Spawn01");
    }

    public String msg(String key, String def) {
        String raw = getConfig().getString("messages." + key, def);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    public boolean hasSurvivalSpawn() {
        return survivalSpawn != null;
    }

    public Location getSurvivalSpawn() {
        return survivalSpawn;
    }

    public void setSurvivalSpawn(Location location) {
        this.survivalSpawn = location;

        FileConfiguration cfg = getConfig();
        cfg.set("survival-spawn.world", location.getWorld().getName());
        cfg.set("survival-spawn.x", location.getX());
        cfg.set("survival-spawn.y", location.getY());
        cfg.set("survival-spawn.z", location.getZ());
        cfg.set("survival-spawn.yaw", location.getYaw());
        cfg.set("survival-spawn.pitch", location.getPitch());
        saveConfig();
    }

    private void loadSurvivalSpawn() {
        FileConfiguration cfg = getConfig();
        String worldName = cfg.getString("survival-spawn.world");
        if (worldName == null) {
            survivalSpawn = null;
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Świat '" + worldName + "' zapisany jako spawn nie istnieje (jeszcze?).");
            survivalSpawn = null;
            return;
        }

        double x = cfg.getDouble("survival-spawn.x");
        double y = cfg.getDouble("survival-spawn.y");
        double z = cfg.getDouble("survival-spawn.z");
        float yaw = (float) cfg.getDouble("survival-spawn.yaw");
        float pitch = (float) cfg.getDouble("survival-spawn.pitch");

        survivalSpawn = new Location(world, x, y, z, yaw, pitch);
    }

    public TpaManager getTpa() {
        return tpa;
    }

    public KitManager getKits() {
        return kits;
    }

    public DailyRewardManager getDaily() {
        return daily;
    }

    public CrateManager getCrates() {
        return crates;
    }

    public ShopManager getShop() {
        return shop;
    }

    public ShopGuiManager getShopGui() {
        return shopGui;
    }

    public TeleportDelayManager getTeleportDelay() {
        return teleportDelay;
    }

    public ShopConfigManager getShopConfig() {
        return shopConfig;
    }

    public ShopConfigGuiManager getShopConfigGui() {
        return shopConfigGui;
    }

    public DecentHologramsHook getDecentHolograms() {
        return decentHolograms;
    }

    public CrateRewardSessionManager getCrateRewardSessions() {
        return crateRewardSessions;
    }

    public StatsManager getStats() {
        return stats;
    }

    public CratePreviewGuiManager getCratePreviewGui() {
        return cratePreviewGui;
    }

    public CrateItemDisplayManager getCrateItemDisplays() {
        return crateItemDisplays;
    }

    public CrateOpenChoiceGuiManager getCrateOpenChoiceGui() {
        return crateOpenChoiceGui;
    }

    public GeneratorManager getGenerators() {
        return generators;
    }

    public TraderManager getTraders() {
        return traders;
    }

    public TraderEditorGuiManager getTraderEditorGui() {
        return traderEditorGui;
    }

    public SellManager getSell() {
        return sell;
    }

    public KillstreakManager getKillstreaks() {
        return killstreaks;
    }

    public PrestigeManager getPrestige() {
        return prestige;
    }

    public EventManager getEvents() {
        return events;
    }

    public EnvoyDisplayManager getEnvoy() {
        return envoy;
    }

    public LeaderboardManager getLeaderboards() {
        return leaderboards;
    }

    public ScoreboardManager getScoreboards() {
        return scoreboards;
    }

    public StatsGuiManager getStatsGui() {
        return statsGui;
    }

    public PartyManager getParty() {
        return party;
    }

    public MissionManager getMissions() {
        return missions;
    }

    public MissionsGuiManager getMissionsGui() {
        return missionsGui;
    }

    public ZombieEventManager getZombieEvent() {
        return zombieEvent;
    }
}
