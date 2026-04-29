package com.protogax.hytale.worldjumps.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class WorldJumpsCommand extends AbstractCommandCollection {

    public WorldJumpsCommand() {
        super("worldjumps", "server.commands.worldjumps.desc");
        this.addSubCommand(new WorldJumpsReindexCommand());
    }
}
