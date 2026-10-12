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
 *   playbedrockanimation <player> <namespace:animations/xxx.animation.json> <动画名>
 *
 * 服务端通过插件消息通道 componentsapi:anim 通知客户端播放第一人称手臂动画。
 * 动画仅在目标玩家的第一人称视角显示，不影响第三人称。
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
            sender.sendMessage("§c用法: /playbedrockanimation <player> <namespace:animations/xxx.animation.json> <动画名>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线: " + args[0]);
            return true;
        }

        String animFile = args[1]; // namespace:animations/xxx.animation.json
        String animName = args[2];

        try {
            byte[] payload = buildPlayPacket(animFile, animName);
            target.sendPluginMessage(plugin, CHANNEL, payload);
            sender.sendMessage("§a已向 " + target.getName() + " 播放动画 " + animName
                    + " (" + animFile + ")");
        } catch (IOException e) {
            sender.sendMessage("§c发送动画包失败: " + e.getMessage());
        }
        return true;
    }

    /** 报文：byte type(0=play) + UTF file + UTF name + int duration(-1=循环) */
    private byte[] buildPlayPacket(String file, String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0); // 0 = play
        out.writeUTF(file);
        out.writeUTF(name);
        out.writeInt(-1); // 循环播放，直到被新动画覆盖或停止
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
            result.add("capi:animations/");
        } else if (args.length == 3) {
            result.add("idle");
            result.add("walk");
            result.add("draw");
            result.add("shoot");
            result.add("reload");
        }
        return result;
    }
}
