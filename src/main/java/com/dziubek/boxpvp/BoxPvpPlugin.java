package com.dziubek.boxpvp;

import org.bukkit.plugin.java.JavaPlugin;

public class BoxPvpPlugin extends JavaPlugin {

    private DecentHologramsHook decentHolograms;
    private ArenaManager arenas;
    private GeneratorManager generators;

    @Override
    public void onEnable() {
        decentHolograms = new DecentHologramsHook(this);
        arenas = new ArenaManager(this);
        generators = new GeneratorManager(this);
        generators.start();

        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);

        getCommand("bpvp").setExecutor(new ArenaCommand(this));
        getCommand("bpvp").setTabCompleter(new ArenaTabCompleter(this));

        getLogger().info("BoxPvPManager włączony! Areny: " + arenas.names().size());
    }

    public DecentHologramsHook getDecentHolograms() {
        return decentHolograms;
    }

    public ArenaManager getArenas() {
        return arenas;
    }

    public GeneratorManager getGenerators() {
        return generators;
    }
}
