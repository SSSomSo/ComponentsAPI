package com.componentsapi.command;

import com.componentsapi.ComponentsApiPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * /playbedrockanimation 指令：
 *   playbedrockanimation <player> <namespace:name> <动画名>
 *
 * 同名文件规则：一个 modelKey（namespace:name）对应一个同名 .animation.json，
 * 因此无需再指定动画文件路径。
 *
 * 服务端通过 componentsapi:anim 通道通知客户端播放第一人称动画。
 */
public final class PlayBedrockAnimationCommand implements TabExecutor {

    public static final String CHANNEL = "componentsapi:anim";

    private final ComponentsApiPlugin plugin;

    public PlayBedrockAnimationCommand(ComponentsApiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("componentsapi.admin")) {
            sender.sendMessage("§c你没有权限。");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§c用法: /playbedrockanimation <player> <namespace:name> <动画名>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线: " + args[0]);
            return true;
        }

        String modelKey = args[1];  // namespace:name（同名 geo + animation）
        String animName = args[2];

        try {
            byte[] payload = buildPacket(modelKey, animName);
            target.sendPluginMessage(plugin, CHANNEL, payload);
            sender.sendMessage("§a已向 " + target.getName() + " 播放动画 " + animName
                    + "（模型 " + modelKey + "）");
        } catch (IOException e) {
            sender.sendMessage("§c发送动画包失败: " + e.getMessage());
        }
        return true;
    }

    /** 报文：byte type(0=play) + UTF modelKey + UTF animName + int duration(-1=循环) */
    private byte[] buildPacket(String modelKey, String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0);
        out.writeUTF(modelKey);
        out.writeUTF(name);
        out.writeInt(-1);
        return bytes.toByteArray();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label,
                                      String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            result.add("capi:example");
        } else if (args.length == 3) {
            result.add("idle");
            result.add("draw");
            result.add("shoot");
            result.add("reload");
            result.add("inspect");
        }
        return result;
    }
}
