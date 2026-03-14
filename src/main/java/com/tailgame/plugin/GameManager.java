package com.tailgame.plugin;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StructureSearchResult;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class GameManager {

    private final TailGamePlugin plugin;
    private boolean gameRunning = false;
    private final List<UUID> waitingPlayers = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final Map<UUID, Team> playerTeamMap = new HashMap<>();
    private final List<Team> preAssignedTeams = new ArrayList<>();   // 수동 배정 팀 목록
    private final Map<UUID, Team> preAssignedMap = new HashMap<>();  // 수동 배정 플레이어→팀
    private BukkitTask proximityTask;
    private boolean warningBlink = false; // 깜빡임 토글

    // 팀 색깔 목록
    private static final ChatColor[] TEAM_COLORS = {
        ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.BLUE,
        ChatColor.GREEN, ChatColor.YELLOW, ChatColor.AQUA,
        ChatColor.GOLD, ChatColor.WHITE
    };
    private static final String[] TEAM_NAMES = {
        "빨강", "핑크", "파랑", "초록", "노랑", "하늘", "주황", "하얀"
    };

    // 근접 경고 거리 (블록)
    private static final double WARNING_DISTANCE = 15.0;
    // 다이아몬드 파티클 거리
    private static final int PARTICLE_RANGE = 80;

    public GameManager(TailGamePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isGameRunning() { return gameRunning; }

    public void addWaitingPlayer(Player player) {
        if (!waitingPlayers.contains(player.getUniqueId())) {
            waitingPlayers.add(player.getUniqueId());
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + "님이 꼬리잡기에 참가했습니다! (" + waitingPlayers.size() + "명)");
        }
    }

    public void startGame() {
        if (waitingPlayers.size() < 4 || waitingPlayers.size() % 2 != 0) {
            Bukkit.broadcastMessage(ChatColor.RED + "최소 4명 이상, 짝수여야 합니다! (현재 " + waitingPlayers.size() + "명)");
            return;
        }

        gameRunning = true;
        teams.clear();
        playerTeamMap.clear();

        // 수동 배정된 팀 먼저 등록
        for (Team pt : preAssignedTeams) {
            if (!pt.getMemberUUIDs().isEmpty()) {
                teams.add(pt);
                // 팀장 없으면 첫번째 멤버를 팀장으로
                if (pt.getLeaderUUID() == null && !pt.getMemberUUIDs().isEmpty()) {
                    pt.setLeaderUUID(pt.getMemberUUIDs().get(0));
                }
                for (UUID uuid : pt.getMemberUUIDs()) {
                    playerTeamMap.put(uuid, pt);
                }
            }
        }

        // 수동 배정 안된 플레이어들 랜덤으로 2명씩 팀 생성
        List<UUID> unassigned = new ArrayList<>();
        for (UUID uuid : waitingPlayers) {
            if (!preAssignedMap.containsKey(uuid)) unassigned.add(uuid);
        }
        Collections.shuffle(unassigned);

        int extraTeamIdx = teams.size();
        for (int i = 0; i < unassigned.size() - 1; i += 2) {
            ChatColor color = TEAM_COLORS[extraTeamIdx % TEAM_COLORS.length];
            String name = TEAM_NAMES[extraTeamIdx % TEAM_NAMES.length];
            // 이미 사용된 팀 이름 피하기
            while (isTeamNameUsed(name)) {
                extraTeamIdx++;
                color = TEAM_COLORS[extraTeamIdx % TEAM_COLORS.length];
                name = TEAM_NAMES[extraTeamIdx % TEAM_NAMES.length];
            }
            Team team = new Team(name, color);
            team.setLeaderUUID(unassigned.get(i));
            team.addMember(unassigned.get(i + 1));
            playerTeamMap.put(unassigned.get(i), team);
            playerTeamMap.put(unassigned.get(i + 1), team);
            teams.add(team);
            extraTeamIdx++;
        }

        // 월드 가져오기 (첫번째 온라인 플레이어 기준)
        World world = null;
        for (UUID uuid : waitingPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) { world = p.getWorld(); break; }
        }
        if (world == null) world = Bukkit.getWorlds().get(0);

        // 월드 보더 1000x1000 설정
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(1000);
        Bukkit.broadcastMessage(ChatColor.AQUA + "월드 보더가 1000x1000으로 설정되었습니다!");

        // 마을 찾기 시도
        Location villageCenter = null;
        try {
            StructureSearchResult result = world.locateNearestStructure(
                new Location(world, 0, 64, 0),
                StructureType.VILLAGE,
                500,
                false
            );
            if (result != null) {
                villageCenter = result.getLocation();
                Bukkit.broadcastMessage(ChatColor.GREEN + "마을 발견! (" + villageCenter.getBlockX() + ", " + villageCenter.getBlockZ() + ")");
            }
        } catch (Exception e) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "마을을 찾지 못했습니다. 랜덤 스폰으로 진행합니다.");
        }
        if (villageCenter == null) {
            Bukkit.broadcastMessage(ChatColor.RED + "❌ 500칸 범위 안에 마을이 없습니다! 게임을 취소합니다.");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "다른 시드나 위치에서 다시 시도해주세요.");
            gameRunning = false;
            return;
        }

        // 랜덤 스폰 위치 생성 (팀 수만큼)
        List<Location> spawnLocations = generateSpawnLocations(world, teams.size(), 100, 450);

        // 플레이어 섞기 (이미 팀 배정된 상태)
        // 각 팀 플레이어에게 역할 알림 및 스폰
        for (int t = 0; t < teams.size(); t++) {
            Team team = teams.get(t);
            for (UUID uuid : team.getMemberUUIDs()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                boolean isLeader = team.isLeader(player);
                setPlayerHealth(player, isLeader ? 20.0 : 10.0);
                String role = isLeader ? "팀장" : "팀원";
                player.sendMessage(team.getColor() + "당신은 [" + team.getName() + "팀] " + role + "입니다!");
                if (t < spawnLocations.size()) {
                    player.teleport(spawnLocations.get(t));
                }
            }
        }

        // 타겟 체인 설정 (순환)
        for (int i = 0; i < teams.size(); i++) {
            Team current = teams.get(i);
            Team target = teams.get((i + 1) % teams.size());
            current.setTargetTeam(target);
        }

        // 각 팀 전체에게 타겟 알림 및 팀장에게 아이템 지급
        for (Team team : teams) {
            Team target = team.getTargetTeam();
            for (UUID uuid : team.getMemberUUIDs()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.sendMessage(ChatColor.GOLD + "상대팀: " + target.getDisplayName());
                if (team.isLeader(p)) {
                    giveTrackingItem(p);
                }
            }
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "=== 꼬리잡기 게임 시작! ===");

        // 근접 경고 태스크 시작
        startProximityWarningTask();

        waitingPlayers.clear();
    }

    private void giveTrackingItem(Player player) {
        org.bukkit.inventory.ItemStack diamond = new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND);
        org.bukkit.inventory.meta.ItemMeta meta = diamond.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "타겟 추적기 (우클릭)");
        diamond.setItemMeta(meta);
        player.getInventory().addItem(diamond);
    }

    public void handleDiamondUse(Player player) {
        Team myTeam = playerTeamMap.get(player.getUniqueId());
        if (myTeam == null) return;

        Team targetTeam = myTeam.getTargetTeam();
        if (targetTeam == null || targetTeam.isEmpty()) {
            player.sendMessage(ChatColor.RED + "추적할 타겟이 없습니다!");
            return;
        }

        // 타겟팀 리더 찾기
        Player targetPlayer = Bukkit.getPlayer(targetTeam.getLeaderUUID());
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "타겟이 오프라인입니다!");
            return;
        }

        // 일직선 파티클 생성
        spawnDirectionParticle(player, targetPlayer);
        player.sendMessage(ChatColor.AQUA + "타겟 방향을 표시합니다!");
    }

    private void spawnDirectionParticle(Player from, Player to) {
        Location start = from.getLocation().add(0, 1, 0);
        Location end = to.getLocation().add(0, 1, 0);

        double distance = start.distance(end);
        if (distance > PARTICLE_RANGE) distance = PARTICLE_RANGE;

        // 방향 벡터
        double dx = (end.getX() - start.getX()) / start.distance(end);
        double dy = (end.getY() - start.getY()) / start.distance(end);
        double dz = (end.getZ() - start.getZ()) / start.distance(end);

        World world = from.getWorld();
        for (int i = 0; i < (int) distance; i += 2) {
            double x = start.getX() + dx * i;
            double y = start.getY() + dy * i;
            double z = start.getZ() + dz * i;
            Location particleLoc = new Location(world, x, y, z);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 3, 0, 0, 0, 0);
        }
    }

    private boolean isTeamNameUsed(String name) {
        return teams.stream().anyMatch(t -> t.getName().equals(name));
    }

    private List<Location> generateSpawnLocations(World world, int count, int minDistance, int maxRadius) {
        List<Location> locations = new ArrayList<>();
        Random rand = ThreadLocalRandom.current();
        int attempts = 0;

        while (locations.size() < count && attempts < 1000) {
            attempts++;
            double angle = rand.nextDouble() * 2 * Math.PI;
            double radius = minDistance + rand.nextDouble() * (maxRadius - minDistance);
            int x = (int)(Math.cos(angle) * radius);
            int z = (int)(Math.sin(angle) * radius);

            // 보더 안인지 확인
            if (Math.abs(x) > 490 || Math.abs(z) > 490) continue;

            Location loc = new Location(world, x, 0, z);
            int y = world.getHighestBlockYAt(x, z);
            loc.setY(y + 1);

            // 기존 스폰들과 100칸 이상 떨어져 있는지 확인
            boolean tooClose = false;
            for (Location existing : locations) {
                if (existing.distance(loc) < minDistance) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) locations.add(loc);
        }
        return locations;
    }

    private void startProximityWarningTask() {
        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning) {
                    cancel();
                    return;
                }

                warningBlink = !warningBlink; // 매 틱마다 토글 → 깜빡임 효과

                for (Team team : teams) {
                    Team hunterTeam = getHunterTeam(team);
                    if (hunterTeam == null) continue;

                    Player hunter = Bukkit.getPlayer(hunterTeam.getLeaderUUID());
                    if (hunter == null) continue;

                    Player prey = Bukkit.getPlayer(team.getLeaderUUID());
                    if (prey == null) continue;

                    if (hunter.getWorld().equals(prey.getWorld())) {
                        double dist = hunter.getLocation().distance(prey.getLocation());
                        if (dist <= WARNING_DISTANCE) {
                            spawnWarningEffect(prey);
                        } else {
                            // 범위 벗어나면 액션바 비우기
                            prey.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5초마다
    }

    private void spawnWarningEffect(Player prey) {
        // 깜빡임: 토글에 따라 하트 표시/숨김
        String actionBarText = warningBlink
            ? ChatColor.RED + "❤ ❤ ❤"
            : "          "; // 빈칸으로 숨김

        prey.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarText));

        // 빨간 파티클은 유지 (화면 효과)
        Location loc = prey.getLocation();
        World world = prey.getWorld();
        for (int i = 0; i < 10; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = Math.random() * 1.5;
            double offsetZ = (Math.random() - 0.5) * 2;
            Location particleLoc = loc.clone().add(offsetX, offsetY, offsetZ);
            world.spawnParticle(Particle.DUST,
                particleLoc, 3,
                new Particle.DustOptions(Color.RED, 1.5f));
        }
    }

    private Team getHunterTeam(Team preyTeam) {
        for (Team t : teams) {
            if (t.getTargetTeam() == preyTeam) return t;
        }
        return null;
    }

    public void handleCatch(Player catcher, Player caught) {
        Team catcherTeam = playerTeamMap.get(catcher.getUniqueId());
        Team caughtTeam = playerTeamMap.get(caught.getUniqueId());

        if (catcherTeam == null || caughtTeam == null) return;
        if (catcherTeam == caughtTeam) return;

        // 타겟팀이 아니면 공격 불가
        if (catcherTeam.getTargetTeam() != caughtTeam) {
            catcher.sendMessage(ChatColor.RED + "이 플레이어는 적팀이 아닙니다!");
            return;
        }

        // 팀장만 잡으면 팀 전체 흡수
        if (!caughtTeam.isLeader(caught)) {
            catcher.sendMessage(ChatColor.YELLOW + "팀장을 잡아야 합니다!");
            // 데미지는 입히지만 흡수는 안됨
            return;
        }

        // 잡힌 팀 멤버 전체를 잡은 팀으로 흡수
        for (UUID memberUUID : new ArrayList<>(caughtTeam.getMemberUUIDs())) {
            Player member = Bukkit.getPlayer(memberUUID);
            catcherTeam.addMember(memberUUID);
            playerTeamMap.put(memberUUID, catcherTeam);

            if (member != null) {
                // 흡수된 팀장 → 팀원 너프
                if (caughtTeam.isLeader(member)) {
                    applyHealthNerf(member);
                }
                member.sendMessage(catcherTeam.getDisplayName() + ChatColor.WHITE + "팀에 흡수되었습니다!");
            }
        }

        teams.remove(caughtTeam);

        Bukkit.broadcastMessage(catcherTeam.getDisplayName() + ChatColor.WHITE + "팀이 " + caughtTeam.getDisplayName() + ChatColor.WHITE + "팀 팀장을 잡았습니다!");

        checkWinCondition();
    }

    private void applyHealthNerf(Player player) {
        double currentMax = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newMax = Math.max(2.0, currentMax / 2.0);
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMax);
        player.setHealth(Math.min(player.getHealth(), newMax));
    }

    private void setPlayerHealth(Player player, double health) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        player.setHealth(health);
    }

    private void checkWinCondition() {
        if (teams.size() <= 1) {
            if (!teams.isEmpty()) {
                Team winner = teams.get(0);
                Bukkit.broadcastMessage(ChatColor.GOLD + "=== " + winner.getDisplayName() + ChatColor.GOLD + "팀 우승! ===");
            }
            stopGame();
        }
    }

    public void stopGame() {
        gameRunning = false;
        if (proximityTask != null) {
            proximityTask.cancel();
            proximityTask = null;
        }

        // 체력 초기화
        for (UUID uuid : playerTeamMap.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                setPlayerHealth(p, 20.0);
            }
        }

        // 월드 보더 초기화
        World world = Bukkit.getWorlds().get(0);
        world.getWorldBorder().reset();

        teams.clear();
        playerTeamMap.clear();
        waitingPlayers.clear();
        preAssignedTeams.clear();
        preAssignedMap.clear();
        Bukkit.broadcastMessage(ChatColor.RED + "꼬리잡기 게임이 종료되었습니다.");
    }

    public Team getPlayerTeam(Player player) {
        return playerTeamMap.get(player.getUniqueId());
    }

    public boolean setPlayerTeamByName(Player player, String teamName) {
        // 팀 이름으로 색깔 찾기
        ChatColor color = null;
        for (int i = 0; i < TEAM_NAMES.length; i++) {
            if (TEAM_NAMES[i].equals(teamName)) {
                color = TEAM_COLORS[i];
                break;
            }
        }
        if (color == null) return false;

        // 이미 참가 안했으면 먼저 참가 처리
        if (!waitingPlayers.contains(player.getUniqueId())) {
            waitingPlayers.add(player.getUniqueId());
        }

        // 해당 팀이 이미 있으면 합류, 없으면 새로 생성
        Team existingTeam = null;
        for (Team t : preAssignedTeams) {
            if (t.getName().equals(teamName)) {
                existingTeam = t;
                break;
            }
        }
        if (existingTeam == null) {
            existingTeam = new Team(teamName, color);
            preAssignedTeams.add(existingTeam);
        }

        // 기존 팀에서 제거
        preAssignedTeams.forEach(t -> t.removeMember(player.getUniqueId()));

        existingTeam.addMember(player.getUniqueId());
        preAssignedMap.put(player.getUniqueId(), existingTeam);

        Bukkit.broadcastMessage(color + player.getName() + "님이 [" + teamName + "팀]으로 배정되었습니다!");
        return true;
    }

    public List<Team> getTeams() { return teams; }
}
