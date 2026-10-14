package com.example.safetrade;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SafeTrade extends JavaPlugin implements Listener {
    private final Map<UUID, Trade> active = new HashMap<>();
    private final Map<UUID, UUID> requests = new HashMap<>();

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SafeTrade enabled.");
    }

    @Override public void onDisable() {
        for (Trade trade : new HashSet<>(active.values())) cancelTrade(trade, "Server is stopping.");
        active.clear();
        requests.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length != 1) { p.sendMessage(ChatColor.YELLOW + "Usage: /trade <player>"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage(ChatColor.RED + "That player is not online."); return true; }
        if (target.equals(p)) { p.sendMessage(ChatColor.RED + "You cannot trade with yourself."); return true; }
        if (active.containsKey(p.getUniqueId()) || active.containsKey(target.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "You or that player is already trading."); return true;
        }
        if (target.getUniqueId().equals(requests.get(p.getUniqueId()))) {
            requests.remove(p.getUniqueId());
            Trade trade = new Trade(p, target);
            active.put(p.getUniqueId(), trade); active.put(target.getUniqueId(), trade);
            trade.openBoth();
        } else {
            requests.put(target.getUniqueId(), p.getUniqueId());
            target.sendMessage(ChatColor.AQUA + p.getName() + " wants to trade. Type " +
                    ChatColor.YELLOW + "/trade " + p.getName() + ChatColor.AQUA + " to accept.");
            p.sendMessage(ChatColor.GREEN + "Trade request sent to " + target.getName() + ".");
        }
        return true;
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private final class Trade {
        final Player a, b;
        final ItemStack[] offerA = new ItemStack[18], offerB = new ItemStack[18];
        boolean acceptedA, acceptedB, finishing, cancelling;
        Inventory guiA, guiB;
        Trade(Player a, Player b) { this.a = a; this.b = b; }
        boolean has(Player p) { return p.getUniqueId().equals(a.getUniqueId()) || p.getUniqueId().equals(b.getUniqueId()); }
        boolean isA(Player p) { return p.getUniqueId().equals(a.getUniqueId()); }
        ItemStack[] offer(Player p) { return isA(p) ? offerA : offerB; }
        ItemStack[] other(Player p) { return isA(p) ? offerB : offerA; }
        boolean accepted(Player p) { return isA(p) ? acceptedA : acceptedB; }
        void setAccepted(Player p, boolean v) { if (isA(p)) acceptedA = v; else acceptedB = v; }
        Inventory gui(Player p) { return isA(p) ? guiA : guiB; }
        void openBoth() { guiA = makeGui(a); guiB = makeGui(b); a.openInventory(guiA); b.openInventory(guiB); refresh(); }
        Inventory makeGui(Player p) {
            Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Trade: " + a.getName() + " ↔ " + b.getName());
            for (int i=18;i<27;i++) inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " "));
            for (int i=45;i<54;i++) inv.setItem(i, button(Material.GRAY_STAINED_GLASS_PANE, " "));
            inv.setItem(49, button(Material.LIME_CONCRETE, ChatColor.GREEN + "Accept trade", "Both players must accept."));
            inv.setItem(50, button(Material.RED_CONCRETE, ChatColor.RED + "Cancel trade", "Returns offered items."));
            return inv;
        }
        void refresh() {
            if (finishing || cancelling) return;
            render(guiA, offerA, offerB, acceptedA);
            render(guiB, offerB, offerA, acceptedB);
        }
        void render(Inventory inv, ItemStack[] own, ItemStack[] theirs, boolean accepted) {
            for (int i=0;i<18;i++) {
                inv.setItem(i, cloneOrNull(own[i]));
                inv.setItem(27+i, cloneOrNull(theirs[i]));
            }
            inv.setItem(49, button(accepted ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE,
                    accepted ? ChatColor.GREEN + "Accepted" : ChatColor.YELLOW + "Accept trade",
                    "Any offer change resets both acceptances."));
            inv.setItem(50, button(Material.RED_CONCRETE, ChatColor.RED + "Cancel trade", "Returns offered items."));
        }
    }

    private ItemStack cloneOrNull(ItemStack item) { return item == null ? null : item.clone(); }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Trade t = active.get(p.getUniqueId());
        if (t == null || e.getView().getTopInventory() != t.gui(p)) return;
        e.setCancelled(true);
        if (t.finishing || t.cancelling) return;
        int raw = e.getRawSlot();
        if (raw == 50) { cancelTrade(t, "Trade cancelled."); return; }
        if (raw == 49) {
            t.setAccepted(p, !t.accepted(p));
            if (t.acceptedA && t.acceptedB) completeTrade(t);
            else t.refresh();
            return;
        }
        // Never allow cursor items, number-key swaps, collect-to-cursor, or drag operations.
        if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.DOUBLE_CLICK ||
                e.getClick() == ClickType.SWAP_OFFHAND || e.getClick() == ClickType.DROP ||
                e.getClick() == ClickType.CONTROL_DROP || e.getCursor() != null && e.getCursor().getType() != Material.AIR) return;

        if (raw >= 0 && raw < 18) {
            ItemStack existing = t.offer(p)[raw];
            if (existing != null) {
                t.offer(p)[raw] = null;
                Map<Integer, ItemStack> left = p.getInventory().addItem(existing);
                left.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                t.acceptedA = false; t.acceptedB = false; t.refresh();
            }
        } else if (raw >= 54 && raw < 90 && e.isShiftClick()) {
            ItemStack source = e.getCurrentItem();
            if (source == null || source.getType() == Material.AIR) return;
            int slot = -1;
            for (int i=0;i<18;i++) if (t.offer(p)[i] == null) { slot = i; break; }
            if (slot == -1) { p.sendMessage(ChatColor.RED + "Your offer is full."); return; }
            t.offer(p)[slot] = source.clone();
            e.getClickedInventory().setItem(e.getSlot(), null);
            t.acceptedA = false; t.acceptedB = false; t.refresh();
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Trade t = active.get(p.getUniqueId());
        if (t != null && e.getView().getTopInventory() == t.gui(p)) e.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Trade t = active.get(p.getUniqueId());
        if (t != null && !t.finishing && !t.cancelling && e.getInventory() == t.gui(p))
            Bukkit.getScheduler().runTask(this, () -> {
                if (active.get(p.getUniqueId()) == t && !t.finishing && !t.cancelling)
                    cancelTrade(t, "Trade closed; trade cancelled.");
            });
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Trade t = active.get(e.getPlayer().getUniqueId());
        if (t != null) cancelTrade(t, "Trade cancelled because a player disconnected.");
        requests.remove(e.getPlayer().getUniqueId());
        requests.values().removeIf(id -> id.equals(e.getPlayer().getUniqueId()));
    }

    private void completeTrade(Trade t) {
        if (t.finishing || t.cancelling) return;
        // Mark locked before moving any items; all inventory click events are blocked while finishing.
        t.finishing = true;
        active.remove(t.a.getUniqueId()); active.remove(t.b.getUniqueId());
        List<ItemStack> aGets = nonEmpty(t.offerB), bGets = nonEmpty(t.offerA);
        clear(t.offerA); clear(t.offerB);
        giveOrDrop(t.a, aGets); giveOrDrop(t.b, bGets);
        t.a.sendMessage(ChatColor.GREEN + "Trade completed!");
        t.b.sendMessage(ChatColor.GREEN + "Trade completed!");
        t.a.closeInventory(); t.b.closeInventory();
    }

    private List<ItemStack> nonEmpty(ItemStack[] arr) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack i : arr) if (i != null && i.getType() != Material.AIR) out.add(i.clone());
        return out;
    }
    private void clear(ItemStack[] arr) { Arrays.fill(arr, null); }
    private void giveOrDrop(Player p, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(item);
            overflow.values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    private void cancelTrade(Trade t, String message) {
        if (t == null || t.finishing || t.cancelling) return;
        t.cancelling = true;
        active.remove(t.a.getUniqueId()); active.remove(t.b.getUniqueId());
        List<ItemStack> giveA = nonEmpty(t.offerA), giveB = nonEmpty(t.offerB);
        clear(t.offerA); clear(t.offerB);
        giveOrDrop(t.a, giveA); giveOrDrop(t.b, giveB);
        if (t.a.isOnline()) { t.a.sendMessage(ChatColor.RED + message); if (t.a.getOpenInventory().getTopInventory() == t.guiA) t.a.closeInventory(); }
        if (t.b.isOnline()) { t.b.sendMessage(ChatColor.RED + message); if (t.b.getOpenInventory().getTopInventory() == t.guiB) t.b.closeInventory(); }
    }
}
