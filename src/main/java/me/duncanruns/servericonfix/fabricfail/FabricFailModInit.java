package me.duncanruns.servericonfix.fabricfail;

import net.fabricmc.api.ModInitializer;

public class FabricFailModInit implements ModInitializer {
    @Override
    public void onInitialize() {
        throw new RuntimeException("ServerIconFix is not supposed to be ran as a mod!");
    }
}
