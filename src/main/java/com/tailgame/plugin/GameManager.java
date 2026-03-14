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
