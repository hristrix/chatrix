package com.hristrix.chatrix.commands;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.hristrix.chatrix.interfaces.IChatConnectionManager;
import com.hristrix.chatrix.interfaces.IBanwordManager;
import com.hristrix.chatrix.messages.MessageSpawner;
import com.hristrix.chatrix.filtering.BlockedUsernameManager;
import com.hristrix.chatrix.utils.Logger;

import java.util.Set;

/**
 * Mod command manager.
 */
public class CommandManager {
    private final IChatConnectionManager chatConnectionManager;
    private final IBanwordManager banwordManager;
    private final BlockedUsernameManager blockedUsernameManager;
    private final MessageSpawner messageSpawner;

    public CommandManager(IChatConnectionManager chatConnectionManager, IBanwordManager banwordManager, BlockedUsernameManager blockedUsernameManager, MessageSpawner messageSpawner) {
        this.chatConnectionManager = chatConnectionManager;
        this.banwordManager = banwordManager;
        this.blockedUsernameManager = blockedUsernameManager;
        this.messageSpawner = messageSpawner;
    }

    public void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildRootCommand("chatrix"));
        });
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> buildRootCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("test")
                    .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            return executeTestCommand(message);
                        })))
                .then(ClientCommandManager.literal("stop")
                    .executes(context -> executeStopCommand()))
                .then(ClientCommandManager.literal("chat")
                    .then(ClientCommandManager.literal("start")
                        .executes(context -> executeChatStartCommand()))
                    .then(ClientCommandManager.literal("stop")
                        .executes(context -> executeChatStopCommand())))
                .then(ClientCommandManager.literal("twitch")
                    .then(ClientCommandManager.literal("start")
                        .executes(context -> executeChatStartCommand()))
                    .then(ClientCommandManager.literal("stop")
                        .executes(context -> executeChatStopCommand())))
                .then(ClientCommandManager.literal("banword")
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("word", StringArgumentType.word())
                            .executes(context -> {
                                String word = StringArgumentType.getString(context, "word");
                                return executeBanwordAddCommand(word);
                            })))
                    .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("word", StringArgumentType.word())
                            .executes(context -> {
                                String word = StringArgumentType.getString(context, "word");
                                return executeBanwordRemoveCommand(word);
                            })))
                    .then(ClientCommandManager.literal("list")
                        .executes(context -> executeBanwordListCommand())))
                .then(ClientCommandManager.literal("blockuser")
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("username", StringArgumentType.word())
                            .executes(context -> {
                                String username = StringArgumentType.getString(context, "username");
                                return executeBlockuserAddCommand(username);
                            })))
                    .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("username", StringArgumentType.word())
                            .executes(context -> {
                                String username = StringArgumentType.getString(context, "username");
                                return executeBlockuserRemoveCommand(username);
                            })))
                    .then(ClientCommandManager.literal("list")
                        .executes(context -> executeBlockuserListCommand())))
                .then(ClientCommandManager.literal("help")
                    .executes(context -> executeHelpCommand()));
    }

    private int executeTestCommand(String message) {
        messageSpawner.setCurrentMessage(message);
        Logger.sendInfoToPlayer("Test message set: " + message);
        return 1;
    }

    private int executeStopCommand() {
        messageSpawner.setCurrentMessage("");
        chatConnectionManager.disconnect();
        Logger.sendInfoToPlayer("Chatrix stopped");
        return 1;
    }

    private int executeChatStartCommand() {
        chatConnectionManager.connect(messageSpawner);
        return 1;
    }

    private int executeChatStopCommand() {
        chatConnectionManager.disconnect();
        return 1;
    }

    private int executeBanwordAddCommand(String word) {
        banwordManager.addBanword(word);
        Logger.sendInfoToPlayer("Blocked word added: " + word);
        return 1;
    }

    private int executeBanwordRemoveCommand(String word) {
        banwordManager.removeBanword(word);
        Logger.sendInfoToPlayer("Blocked word removed: " + word);
        return 1;
    }

    private int executeBanwordListCommand() {
        Set<String> banwords = banwordManager.getBanwords();
        if (banwords.isEmpty()) {
            Logger.sendInfoToPlayer("The blocked word list is empty");
        } else {
            Logger.sendInfoToPlayer("Blocked words (" + banwords.size() + "):");
            for (String banword : banwords) {
                Logger.sendToPlayer("  - " + banword);
            }
        }
        return 1;
    }

    private int executeBlockuserAddCommand(String username) {
        blockedUsernameManager.addBlockedUsername(username);
        Logger.sendInfoToPlayer("Username blocked: " + username);
        return 1;
    }

    private int executeBlockuserRemoveCommand(String username) {
        blockedUsernameManager.removeBlockedUsername(username);
        Logger.sendInfoToPlayer("Username unblocked: " + username);
        return 1;
    }

    private int executeBlockuserListCommand() {
        Set<String> blocked = blockedUsernameManager.getBlockedUsernames();
        if (blocked.isEmpty()) {
            Logger.sendInfoToPlayer("The username blocklist is empty");
        } else {
            Logger.sendInfoToPlayer("Blocked usernames (" + blocked.size() + "):");
            for (String username : blocked) {
                Logger.sendToPlayer("  - " + username);
            }
        }
        return 1;
    }

    private int executeHelpCommand() {
        Logger.sendInfoToPlayer("=== Chatrix commands ===");
        Logger.sendInfoToPlayer("/chatrix test <message> - display a test message");
        Logger.sendInfoToPlayer("/chatrix stop - clear messages and disconnect chats");
        Logger.sendInfoToPlayer("/chatrix chat start - connect to all enabled platforms");
        Logger.sendInfoToPlayer("/chatrix chat stop - disconnect from chats");
        Logger.sendInfoToPlayer("/chatrix banword add|remove|list - blocked words");
        Logger.sendInfoToPlayer("/chatrix blockuser add|remove|list - blocked usernames");
        Logger.sendInfoToPlayer("/chatrix help - show this help");
        return 1;
    }
}
