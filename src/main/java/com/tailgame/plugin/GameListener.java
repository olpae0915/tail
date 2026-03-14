package com.tailgame.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GameListener implements Listener {

    private final GameManager gameManager;

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    // 다이아몬드 우클릭 → 파티클 방향 표시
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!gameManager.isGameRunning()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.DIAMOND) return;

        org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
            action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {

            event.setCancelled(true);
            gameManager.handleDiamondUse(player);
        }
    }

    // 전투 처리 → 타겟 잡기
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!gameManager.isGameRunning()) return;
        if (!(event.getDamager() instanceof Player catcher)) return;
        if (!(event.getEntity() instanceof Player caught)) return;

        Team catcherTeam = gameManager.getPlayerTeam(catcher);
        Team caughtTeam = gameManager.getPlayerTeam(caught);

        if (catcherTeam == null || caughtTeam == null) return;
        if (catcherTeam == caughtTeam) {
            // 같은 팀끼리 공격 불가
            event.setCancelled(true);
            return;
        }

        // 타겟팀이 아니면 공격 불가
        if (catcherTeam.getTargetTeam() != caughtTeam) {
            event.setCancelled(true);
            catcher.sendMessage(ChatColor.RED + "이 플레이어는 당신의 타겟이 아닙니다!");
            return;
        }

        // 체력이 1 이하면 잡힘 처리
        if (caught.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            caught.setHealth(caught.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            gameManager.handleCatch(catcher, caught);
        }
    }
}
