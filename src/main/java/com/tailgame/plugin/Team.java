package com.tailgame.plugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Team {

    private final String name;
    private final ChatColor color;
    private UUID leaderUUID;
    private final List<UUID> memberUUIDs = new ArrayList<>();
    private Team targetTeam;

    public Team(String name, ChatColor color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public ChatColor getColor() { return color; }

    public UUID getLeaderUUID() { return leaderUUID; }
    public void setLeaderUUID(UUID leaderUUID) {
        this.leaderUUID = leaderUUID;
        if (!memberUUIDs.contains(leaderUUID)) {
            memberUUIDs.add(leaderUUID);
        }
    }

    public List<UUID> getMemberUUIDs() { return memberUUIDs; }

    public void addMember(UUID uuid) {
        if (!memberUUIDs.contains(uuid)) {
            memberUUIDs.add(uuid);
        }
    }

    public void removeMember(UUID uuid) {
        memberUUIDs.remove(uuid);
    }

    public boolean isLeader(Player player) {
        return player.getUniqueId().equals(leaderUUID);
    }

    public boolean hasMember(UUID uuid) {
        return memberUUIDs.contains(uuid);
    }

    public int getSize() { return memberUUIDs.size(); }

    public boolean isEmpty() { return memberUUIDs.isEmpty(); }

    public Team getTargetTeam() { return targetTeam; }
    public void setTargetTeam(Team targetTeam) { this.targetTeam = targetTeam; }

    public String getDisplayName() {
        return color + name;
    }
}
