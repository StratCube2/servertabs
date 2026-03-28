package com.servertabs;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerTabsMod implements ModInitializer {

    public static final String MOD_ID = "servertabs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("ServerTabs initialized!");
    }
}
