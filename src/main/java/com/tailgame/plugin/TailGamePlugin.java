    private static TailGamePlugin instance;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;
        gameManager = new GameManager(this);

        getCommand("tailgame").setExecutor(new GameCommand(gameManager));
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);

        getLogger().info("꼬리잡기 플러그인이 활성화되었습니다!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame();
        }
        getLogger().info("꼬리잡기 플러그인이 비활성화되었습니다!");
    }

    public static TailGamePlugin getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
