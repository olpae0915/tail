package com.tailgame.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameCommand implements CommandExecutor {

    private final GameManager gameManager;

    public GameCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/tailgame start|stop|join|team <플레이어> <팀이름>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("플레이어만 사용 가능합니다.");
                    return true;
                }
                if (gameManager.isGameRunning()) {
                    player.sendMessage(ChatColor.RED + "이미 게임이 진행 중입니다!");
                    return true;
                }
                gameManager.addWaitingPlayer(player);
            }
            case "start" -> {
                if (gameManager.isGameRunning()) {
                    sender.sendMessage(ChatColor.RED + "이미 게임이 진행 중입니다!");
                    return true;
                }
                gameManager.startGame();
            }
            case "stop" -> {
                if (!gameManager.isGameRunning()) {
                    sender.sendMessage(ChatColor.RED + "진행 중인 게임이 없습니다!");
                    return true;
                }
                gameManager.stopGame();
            }
            case "team" -> {
                if (gameManager.isGameRunning()) {
                    sender.sendMessage(ChatColor.RED + "게임 중에는 팀을 변경할 수 없습니다!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "사용법: /tailgame team <플레이어> <팀이름>");
                    sender.sendMessage(ChatColor.YELLOW + "팀이름: 빨강, 핑크, 파랑, 초록, 노랑, 하늘, 주황, 하얀");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어 " + args[1] + " 를 찾을 수 없습니다!");
                    return true;
                }
                String teamName = args[2];
                boolean success = gameManager.setPlayerTeamByName(target, teamName);
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + target.getName() + " → [" + teamName + "팀] 설정 완료!");
                    target.sendMessage(ChatColor.GREEN + "[" + teamName + "팀]으로 배정되었습니다!");
                } else {
                    sender.sendMessage(ChatColor.RED + "팀이름이 올바르지 않습니다!");
                    sender.sendMessage(ChatColor.YELLOW + "팀이름: 빨강, 핑크, 파랑, 초록, 노랑, 하늘, 주황, 하얀");
                }
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "/tailgame start|stop|join|team <플레이어> <팀이름>");
        }
        return true;
    }
}
