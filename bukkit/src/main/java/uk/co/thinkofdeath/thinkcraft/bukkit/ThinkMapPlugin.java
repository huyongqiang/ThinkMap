/*
 * Copyright 2014 Matthew Collins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.co.thinkofdeath.thinkcraft.bukkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.channel.Channel;
import org.apache.commons.io.FileUtils;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.thinkofdeath.command.bukkit.BukkitCommandManager;
import uk.co.thinkofdeath.thinkcraft.bukkit.commands.Commands;
import uk.co.thinkofdeath.thinkcraft.bukkit.config.InvalidConfigFieldException;
import uk.co.thinkofdeath.thinkcraft.bukkit.config.PluginConfiguration;
import uk.co.thinkofdeath.thinkcraft.bukkit.textures.BufferedTexture;
import uk.co.thinkofdeath.thinkcraft.bukkit.textures.BufferedTextureFactory;
import uk.co.thinkofdeath.thinkcraft.bukkit.textures.TextureDetailsSerializer;
import uk.co.thinkofdeath.thinkcraft.bukkit.web.WebHandler;
import uk.co.thinkofdeath.thinkcraft.bukkit.world.ChunkManager;
import uk.co.thinkofdeath.thinkcraft.protocol.Packet;
import uk.co.thinkofdeath.thinkcraft.protocol.ServerPacketHandler;
import uk.co.thinkofdeath.thinkcraft.protocol.packets.TimeUpdate;
import uk.co.thinkofdeath.thinkcraft.textures.*;
import uk.co.thinkofdeath.thinkcraft.textures.mojang.MojangTextureProvider;
import uk.co.thinkofdeath.thinkcraft.textures.mojang.ZipTextureProvider;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ThinkMapPlugin extends JavaPlugin implements Runnable {

    // Only needs to be changed when assets we use update
    public static final String MINECRAFT_VERSION = "1.7.9";
    public static final int RESOURCE_VERSION = 2;
    public static final int WORLD_VERSION = 1;

    private final Map<String, ChunkManager> chunkManagers = new HashMap<>();
    private final WebHandler webHandler = new WebHandler(this);
    private World targetWorld;
    private PluginConfiguration configuration;

    private final BukkitCommandManager commandManager = new BukkitCommandManager(this);

    private File resourceDir;
    private File worldDir;
    private Date startUpDate = new Date((System.currentTimeMillis() / 1000) * 1000);

    @Override
    public void onEnable() {
        // Load configuration
        try {
            configuration = new PluginConfiguration(new File(getDataFolder(), "config.yml"));
            configuration.load();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "Failed to load configuration. Disabling ThinkMap", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (InvalidConfigFieldException e) {
            getLogger().log(Level.SEVERE, "Failed to load configuration. Disabling ThinkMap");
            getLogger().log(Level.SEVERE, e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Register commands
        commandManager.register(new Commands(this));

        getCommand("thinkmap").setExecutor(commandManager);
        getCommand("thinkmap").setTabCompleter(commandManager);

        // Register events
        getServer().getPluginManager().registerEvents(new Events(this), this);
        getServer().getScheduler().runTaskTimer(this, this, 20l, 20 * 2l);

        // Load worlds
        worldDir = new File(getDataFolder(), "worlds");

        for (World world : getServer().getWorlds()) {
            if (targetWorld == null) {
                targetWorld = world;
                getChunkManager(world);
                break; // TODO: Support multiple worlds
            }
        }

        // Clear out old resources
        if (configuration.getResourceVersion() != RESOURCE_VERSION) {
            getLogger().info("Deleting ThinkMap-Resources due to a format update");
            configuration.setResourceVersion(RESOURCE_VERSION);
            try {
                FileUtils.deleteDirectory(new File(getDataFolder(), "resources"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Clear out old Think-worlds
        if (configuration.getWorldVersion() != WORLD_VERSION) {
            getLogger().info("Deleting ThinkMap-Worlds due to a format update");
            configuration.setWorldVersion(WORLD_VERSION);
            try {
                FileUtils.deleteDirectory(worldDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Save the updated config

        try {
            configuration.save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        resourceDir = new File(getDataFolder(),
                "resources/"
                        + (configuration.getResourcePackName().length() == 0 ?
                        "default" : configuration.getResourcePackName())
        );

        // Resource loading
        File blockInfo = new File(resourceDir, "blocks.json");
        if (blockInfo.exists()) {
            webHandler.start();
        } else {
            loadResources();
        }
    }

    private void loadResources() {
        final File blockInfo = new File(resourceDir, "blocks.json");
        String resourcePack = configuration.getResourcePackName();
        final File resourceFile = new File(getDataFolder(), resourcePack + ".zip");
        if (!resourceFile.exists()) {
            getLogger().log(Level.SEVERE, "Unable to find the resource pack "
                    + configuration.getResourcePackName());
            resourcePack = "";
        }
        final String finalResourcePack = resourcePack;
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    if (!blockInfo.getParentFile().exists() && !blockInfo.getParentFile().mkdirs()) {
                        throw new RuntimeException("Failed to create " + blockInfo.getParentFile());
                    }
                    TextureFactory textureFactory = new BufferedTextureFactory();
                    getLogger().info("Downloading textures. This may take some time");
                    TextureProvider textureProvider =
                            new MojangTextureProvider(MINECRAFT_VERSION, textureFactory);

                    try (InputStream in =
                                 getClassLoader().getResourceAsStream("textures/missing_texture.png")) {
                        ((MojangTextureProvider) textureProvider).addTexture("missing_texture",
                                textureFactory.fromInputStream(in));
                    }

                    if (finalResourcePack.length() > 0) {
                        textureProvider = new JoinedProvider(
                                new ZipTextureProvider(
                                        new FileInputStream(resourceFile), textureFactory
                                ),
                                textureProvider
                        );
                    }

                    TextureStitcher stitcher = new TextureStitcher(textureProvider, textureFactory);
                    getLogger().info("Stitching textures. The mapviewer will start after this " +
                            "completes");
                    long start = System.currentTimeMillis();
                    StitchResult result = stitcher.stitch();
                    // Save the result
                    Gson gson = new GsonBuilder()
                            .registerTypeAdapter(TextureDetails.class,
                                    new TextureDetailsSerializer())
                            .create();
                    HashMap<String, Object> info = new HashMap<>();
                    info.put("textures", result.getDetails());
                    info.put("textureImages", result.getOutput().length);
                    info.put("virtualCount", result.getVirtualCount());
                    FileUtils.writeStringToFile(
                            blockInfo,
                            gson.toJson(info)
                    );
                    int i = 0;
                    for (Texture texture : result.getOutput()) {
                        ImageIO.write(((BufferedTexture) texture).getImage(), "PNG",
                                new File(resourceDir, "blocks_" + (i++) + ".png"));
                    }
                    getLogger().info("Stitching complete in " + (System.currentTimeMillis() -
                            start) + "ms");

                    webHandler.start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public void onDisable() {
        getWebHandler().getChannelGroup().close();
        Channel channel = getWebHandler().getChannel();
        if (channel != null) {
            channel.close();
        }
    }

    public File getWorldDir() {
        return worldDir;
    }

    public ChunkManager getChunkManager(World world) {
        synchronized (chunkManagers) {
            if (chunkManagers.containsKey(world.getName())) {
                return chunkManagers.get(world.getName());
            }
            ChunkManager chunkManager = new ChunkManager(this, world);
            chunkManagers.put(world.getName(), chunkManager);
            return chunkManager;
        }
    }

    @Override
    public void run() {
        if (targetWorld == null) return;
        sendAll(new TimeUpdate((int) targetWorld.getTime()));
    }

    public void sendAll(Packet<ServerPacketHandler> frame) {
        getWebHandler().getChannelGroup().writeAndFlush(frame);
    }

    public World getTargetWorld() {
        if (targetWorld == null) {
            targetWorld = getServer().getWorlds().get(0);
        }
        return targetWorld;
    }

    public void setTargetWorld(World targetWorld) {
        this.targetWorld = targetWorld;
    }

    public WebHandler getWebHandler() {
        return webHandler;
    }

    public File getResourceDir() {
        return resourceDir;
    }

    public Date getStartUpDate() {
        return new Date(startUpDate.getTime());
    }

    public PluginConfiguration getConfiguration() {
        return configuration;
    }
}
