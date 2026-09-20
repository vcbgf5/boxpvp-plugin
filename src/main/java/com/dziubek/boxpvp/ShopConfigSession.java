package com.dziubek.boxpvp;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopConfigSession {

    public final String category;
    public final ItemStack icon;
    public ShopItemType type = ShopItemType.COMMAND;
    public double price = 0;
    public String customName = null;
    public String kitName = null;
    public List<String> commands = new ArrayList<>();
    public double boosterMultiplier = 0;
    public int boosterMinutes = 0;

    // null = nic nie czekamy; "price"/"name"/"commands"/"boost" = czekamy na wiadomość na czacie
    public String awaitingChatFor = null;

    public ShopConfigSession(String category, ItemStack icon) {
        this.category = category;
        this.icon = icon;
    }
}
