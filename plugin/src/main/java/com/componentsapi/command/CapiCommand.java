package com.componentsapi.command;

import com.componentsapi.ComponentsApiPlugin;
import com.componentsapi.block.BlockComponents;
import com.componentsapi.component.ComponentRegistry;
import com.componentsapi.entity.EntityComponents;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /capi 指令：
 *   capi block <x y z> set <组件id> <SNBT...>
 *   capi block <x y z> remove <组件id>
 *   capi entity <选择器> set <组件id> <SNBT...>
 *   capi entity <选择器> remove <组件id>
 *   capi reload
 */
public final class CapiCommand implements TabExecutor {

    private final ComponentsApiPlugin plugin;
    private final BlockComponents blocks;
    private final EntityComponents entities;

    public CapiCommand(ComponentsApiPlugin plugin, BlockComponents blocks,
                       EntityComponents entities) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.entities = entities;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("componentsapi.admin")) {
            sender.sendMessage("§c你没有权限。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§eComponentsAPI §7- /capi block|entity|reload");
            return true;
        }

        try {
            switch (args[0]) {
                case "block" -> handleBlock(sender, args);
                case "entity" -> handleEntity(sender, args);
                case "reload" -> {
                    plugin.reloadConfig();
                    sender.sendMessage("§a配置已重载（组件存储为实时读取，无需重载）。");
                }
                default -> sender.sendMessage("§c用法: /capi block|entity|reload");
            }
        } catch (Exception e) {
            sender.sendMessage("§c执行失败: " + e.getMessage());
        }
        return true;
    }

    private void handleBlock(CommandSender sender, String[] args) {
        if (args.length < 5 || !(sender instanceof Player player)) {
            sender.sendMessage("§c用法: /capi block <x y z> set|remove <组件id> [SNBT]");
            return;
        }
        Location base = player.getLocation();
        int x = parseCoord(args[1], base.getBlockX());
        int y = parseCoord(args[2], base.getBlockY());
        int z = parseCoord(args[3], base.getBlockZ());
        Block block = player.getWorld().getBlockAt(x, y, z);
        String action = args[4];

        if (action.equals("set") && args.length >= 7) {
            String componentId = args[5];
            String snbt = joinSnbt(args, 6);
            blocks.setComponent(block, componentId, snbt);
            sender.sendMessage("§a已在方块 " + x + " " + y + " " + z + " 设置 " + componentId);
        } else if (action.equals("remove") && args.length >= 6) {
            boolean removed = blocks.removeComponent(block, args[5]);
            sender.sendMessage(removed ? "§a已移除 " + args[5] : "§e该方块没有此组件。");
        } else {
            sender.sendMessage("§c用法: /capi block <x y z> set|remove <组件id> [SNBT]");
        }
    }

    private void handleEntity(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法: /capi entity <选择器> set|remove <组件id> [SNBT]");
            return;
        }
        List<Entity> targets = plugin.getServer().selectEntities(sender, args[1]);
        if (targets.isEmpty()) {
            sender.sendMessage("§e没有匹配的实体。");
            return;
        }
        String action = args[2];
        if (action.equals("set") && args.length >= 5) {
            String componentId = args[3];
            String snbt = joinSnbt(args, 4);
            for (Entity entity : targets) {
                entities.setComponent(entity, componentId, snbt);
            }
            sender.sendMessage("§a已为 " + targets.size() + " 个实体设置 " + componentId);
        } else if (action.equals("remove") && args.length >= 4) {
            int count = 0;
            for (Entity entity : targets) {
                if (entities.removeComponent(entity, args[3])) {
                    count++;
                }
            }
            sender.sendMessage("§a已从 " + count + " 个实体移除 " + args[3]);
        } else {
            sender.sendMessage("§c用法: /capi entity <选择器> set|remove <组件id> [SNBT]");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label,
                                      String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            addPrefix(args[0], List.of("block", "entity", "reload"), result);
        } else if ((args[0].equals("block") || args[0].equals("entity"))
                && args.length == 2) {
            result.add(args[0].equals("block") ? "~" : "@p");
        } else if (args[0].equals("block") && (args.length == 3 || args.length == 4)) {
            result.add("~");
        } else if (args[0].equals("block") && args.length == 5
                || args[0].equals("entity") && args.length == 3) {
            addPrefix(args[args.length - 1], List.of("set", "remove"), result);
        } else if (isComponentSlot(args)) {
            for (Map.Entry<String, String> entry : ComponentRegistry.DOCS.entrySet()) {
                if (entry.getKey().startsWith(args[args.length - 1])) {
                    result.add(entry.getKey());
                }
            }
        } else if (isValueSlot(args)) {
            String componentId = componentIdAt(args);
            String doc = ComponentRegistry.DOCS.get(componentId);
            if (doc != null) {
                result.add(exampleValue(componentId));
            }
        }
        return result;
    }

    private boolean isComponentSlot(String[] args) {
        if (args[0].equals("block") && args.length == 6) {
            return true;
        }
        return args[0].equals("entity") && args.length == 4;
    }

    private boolean isValueSlot(String[] args) {
        if (args[0].equals("block") && args.length >= 7) {
            return true;
        }
        return args[0].equals("entity") && args.length >= 5;
    }

    private String componentIdAt(String[] args) {
        return args[0].equals("block") ? args[5] : args[3];
    }

    private String exampleValue(String componentId) {
        return switch (componentId) {
            case ComponentRegistry.BLOCK_MODEL -> "\"capi:ruby_block\"";
            case ComponentRegistry.ENTITY_MODEL -> "\"capi:zombie_king\"";
            case ComponentRegistry.BLOCK_LIGHT -> "{eat_environment_light:0b,light:1}";
            case ComponentRegistry.SLIPPERINESS -> "0.98f";
            default -> "";
        };
    }

    private void addPrefix(String prefix, List<String> options, List<String> result) {
        for (String option : options) {
            if (option.startsWith(prefix)) {
                result.add(option);
            }
        }
    }

    private int parseCoord(String input, int current) {
        if (input.equals("~")) {
            return current;
        }
        if (input.startsWith("~")) {
            return current + Integer.parseInt(input.substring(1));
        }
        return Integer.parseInt(input);
    }

    private String joinSnbt(String[] args, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }
}
