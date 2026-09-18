package com.dziubek.boxpvp;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopConfigSession {

    public final String category;
    public final ItemStack icon;
    public ShopItemType type = ShopItemType.COMMAND;
    public double price = 0;
    public String kitName = null;
    public List<String> commands = new ArrayList<>();

    // null = nic nie czekamy; "price" albo "commands" = czekamy na wiadomość na czacie
    public String awaitingChatFor = null;

    public ShopConfigSession(String category, ItemStack icon) {
        this.category = category;
        this.icon = icon;
    }
}
