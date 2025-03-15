package tk.thevalleyy.customcommands;

import com.moandjiezana.toml.Toml;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class registerCustomCommands {
    public boolean createDefaultCommand(Path path) {
        File folder = path.toFile();
        File file = new File(folder, "DefaultCommand.toml");

        if (folder.exists()) return true;

        if (folder.mkdirs()) {
            CustomCommands.logger.info("Creating the default command folder.");
        } else {
            CustomCommands.logger.error("Failed to create the default command folder.");
            return false;
        }

        try {
            InputStream input = getClass().getResourceAsStream("/DefaultCommand.toml");

            if (input == null) {
                CustomCommands.logger.error("Failed to read the default command from the jar file.");
                return false;
            }

            Files.copy(input, file.toPath());
        } catch (IOException e) {
            CustomCommands.logger.error(e.getMessage());
            return false;
        }
        return true;
    }

    // search all toml files in the folder
    public List<Path> searchTomlFiles(Path folder) throws IOException {
        List<Path> tomlFiles = new ArrayList<>();

        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".toml")) {
                        tomlFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            CustomCommands.logger.error(e.getMessage());
        }

        return tomlFiles;
    }

    // load all custom commands
    public boolean loadCustomCommands(Path folder) {
        boolean noErrors = true;
        try {
            List<Path> tomlFiles = searchTomlFiles(folder);
            for (Path file : tomlFiles) {

                Toml toml = new Toml().read(file.toFile());

                if (toml == null) {
                    throw new Exception("Failed to load the toml file: " + file.getFileName());
                }

                if (toml.getBoolean("Enabled").equals(false)) continue;

                // get all the values from the toml file
                String name = toml.getString("Name");
                List<String> aliases = toml.getList("Aliases");
                String description = toml.getString("Description");
                String permission = toml.getString("Permission");
                String response = toml.getString("Response");
                boolean usePrefix = toml.getBoolean("UsePrefix");
                long cooldown = toml.getLong("Cooldown");
                // New: read the optional TargetServer key (defaulting to an empty string if not present)
                String targetServer = toml.getString("TargetServer", "");

                // register the command using the overloaded method
                createBrigadierCommand(name, aliases, description, permission, response, usePrefix, cooldown, targetServer);
            }
        } catch (Exception e) {
            CustomCommands.logger.error(e.getMessage());
            noErrors = false;
        }
        return noErrors;
    }

    // create a brigadier command without target server (for backward compatibility)
    public static void createBrigadierCommand(String name, List<String> aliases, String description, String permission, String response, boolean usePrefix, long cooldown) {
        createBrigadierCommand(name, aliases, description, permission, response, usePrefix, cooldown, "");
    }

    // create a brigadier command with optional target server
    public static void createBrigadierCommand(String name, List<String> aliases, String description, String permission, String response, boolean usePrefix, long cooldown, String targetServer) {
        LiteralCommandNode<CommandSource> command =
                LiteralArgumentBuilder.<CommandSource>literal(name)
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            // Permission check
                            if ((!source.hasPermission(permission) && !permission.isEmpty())
                                    && !source.hasPermission("customcommands.admin")) {
                                source.sendMessage(MiniMessage.miniMessage().deserialize(CustomCommands.NoPermission));
                                return 0;
                            }

                            // If a target server is provided, attempt to connect the player.
                            if (targetServer != null && !targetServer.isEmpty()) {
                                if (source instanceof com.velocitypowered.api.proxy.Player) {
                                    com.velocitypowered.api.proxy.Player player = (com.velocitypowered.api.proxy.Player) source;
                                    Optional<RegisteredServer> serverOptional = CustomCommands.getProxy().getServer(targetServer);
                                    if (serverOptional.isPresent()) {
                                        player.createConnectionRequest(serverOptional.get()).connect();
                                        return Command.SINGLE_SUCCESS;
                                    } else {
                                        player.sendMessage(MiniMessage.miniMessage().deserialize(
                                                CustomCommands.Prefix + "<red>Target server not found."));
                                        return 0;
                                    }
                                } else {
                                    source.sendMessage(MiniMessage.miniMessage().deserialize(
                                            CustomCommands.Prefix + "<red>This command can only be executed by a player."));
                                    return 0;
                                }
                            } else {
                                // Otherwise, send the usual response.
                                source.sendMessage(MiniMessage.miniMessage().deserialize(
                                        (usePrefix ? CustomCommands.Prefix : "") + response));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .build();

        CustomCommands.registerCommand(name, aliases, new BrigadierCommand(command));
    }

    public List<List<String>> getCommandList(Path folder) {
        List<List<String>> commandList = new ArrayList<>();
        boolean noErrors = true;

        try {
            List<Path> tomlFiles = searchTomlFiles(folder);
            for (Path file : tomlFiles) {
                Toml toml = new Toml().read(file.toFile());

                if (toml == null) {
                    throw new Exception("Failed to load the toml file: " + file.getFileName());
                }

                String name = toml.getString("Name");
                boolean enabled = toml.getBoolean("Enabled");
                List<String> aliases = toml.getList("Aliases");
                String description = toml.getString("Description");
                String permission = toml.getString("Permission");
                String response = toml.getString("Response");
                boolean usePrefix = toml.getBoolean("UsePrefix");
                long cooldown = toml.getLong("Cooldown");
                String targetServer = toml.getString("TargetServer", "");

                List<String> commandAttributes = new ArrayList<>();
                commandAttributes.add(name);
                commandAttributes.add(String.valueOf(enabled));
                commandAttributes.add(aliases.toString());
                commandAttributes.add(description);
                commandAttributes.add(permission);
                commandAttributes.add(response);
                commandAttributes.add(String.valueOf(usePrefix));
                commandAttributes.add(String.valueOf(cooldown));
                commandAttributes.add(targetServer);

                commandList.add(commandAttributes);
            }
        } catch (Exception e) {
            CustomCommands.logger.error(e.getMessage());
            noErrors = false;
        }
        return noErrors ? commandList : null;
    }
}
