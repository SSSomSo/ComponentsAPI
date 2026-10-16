package com.componentsapi.client.bedrock;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * index 按键名到 GLFW 键码的映射，覆盖键盘上几乎所有按键。
 *
 * <p>鼠标键（由 {@link KeyBindHandler} 单独处理）：{@code lc}=左键、{@code rc}=右键、{@code mc}=中键。
 * 语义别名：{@code fp}=第一人称模拟切换（默认绑定 R 键）。
 *
 * <p>键盘键名（index 的 keys 下使用，小写）：
 * <ul>
 *   <li>字母 a-z、数字 0-9、功能键 f1-f25；</li>
 *   <li>空格 space、回车 enter、退出 esc、tab、退格 backspace；</li>
 *   <li>方向 up/down/left/right，insert/delete/home/end/pageup/pagedown；</li>
 *   <li>capslock/scrolllock/numlock/printscreen/pause；</li>
 *   <li>修饰 lshift/rshift、lctrl/rctrl、lalt/ralt、lsuper/rsuper、menu；</li>
 *   <li>小键盘 kp0-kp9、kpdecimal、kpdivide、kpmultiply、kpsubtract、kpadd、kpenter、kpequal；</li>
 *   <li>符号 minus/equal/leftbracket/rightbracket/backslash/semicolon/apostrophe/grave/comma/period/slash。</li>
 * </ul>
 */
public final class KeyNames {

    /** 鼠标键（不映射到 GLFW 键盘码）。 */
    public static final Set<String> MOUSE_KEYS = Set.of("lc", "rc", "mc");

    private static final Map<String, Integer> KEYS = new HashMap<>();

    static {
        // 字母（GLFW 键码与 ASCII 大写连续）
        for (char c = 'a'; c <= 'z'; c++) {
            KEYS.put(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'a'));
        }
        // 顶行数字
        for (char c = '0'; c <= '9'; c++) {
            KEYS.put(String.valueOf(c), GLFW.GLFW_KEY_0 + (c - '0'));
        }
        // 功能键 F1-F25
        for (int i = 1; i <= 25; i++) {
            KEYS.put("f" + i, GLFW.GLFW_KEY_F1 + (i - 1));
        }
        // 小键盘数字
        for (int i = 0; i <= 9; i++) {
            KEYS.put("kp" + i, GLFW.GLFW_KEY_KP_0 + i);
        }

        put("space", GLFW.GLFW_KEY_SPACE);
        put("enter", GLFW.GLFW_KEY_ENTER);
        put("return", GLFW.GLFW_KEY_ENTER);
        put("esc", GLFW.GLFW_KEY_ESCAPE);
        put("escape", GLFW.GLFW_KEY_ESCAPE);
        put("tab", GLFW.GLFW_KEY_TAB);
        put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        put("insert", GLFW.GLFW_KEY_INSERT);
        put("delete", GLFW.GLFW_KEY_DELETE);
        put("del", GLFW.GLFW_KEY_DELETE);
        put("right", GLFW.GLFW_KEY_RIGHT);
        put("left", GLFW.GLFW_KEY_LEFT);
        put("down", GLFW.GLFW_KEY_DOWN);
        put("up", GLFW.GLFW_KEY_UP);
        put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        put("page_up", GLFW.GLFW_KEY_PAGE_UP);
        put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        put("page_down", GLFW.GLFW_KEY_PAGE_DOWN);
        put("home", GLFW.GLFW_KEY_HOME);
        put("end", GLFW.GLFW_KEY_END);
        put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        put("caps", GLFW.GLFW_KEY_CAPS_LOCK);
        put("scrolllock", GLFW.GLFW_KEY_SCROLL_LOCK);
        put("numlock", GLFW.GLFW_KEY_NUM_LOCK);
        put("printscreen", GLFW.GLFW_KEY_PRINT_SCREEN);
        put("prtsc", GLFW.GLFW_KEY_PRINT_SCREEN);
        put("pause", GLFW.GLFW_KEY_PAUSE);

        // 修饰键
        put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        put("lalt", GLFW.GLFW_KEY_LEFT_ALT);
        put("alt", GLFW.GLFW_KEY_LEFT_ALT);
        put("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
        put("lsuper", GLFW.GLFW_KEY_LEFT_SUPER);
        put("win", GLFW.GLFW_KEY_LEFT_SUPER);
        put("rsuper", GLFW.GLFW_KEY_RIGHT_SUPER);
        put("menu", GLFW.GLFW_KEY_MENU);

        // 小键盘运算
        put("kpdecimal", GLFW.GLFW_KEY_KP_DECIMAL);
        put("kpdivide", GLFW.GLFW_KEY_KP_DIVIDE);
        put("kpmultiply", GLFW.GLFW_KEY_KP_MULTIPLY);
        put("kpsubtract", GLFW.GLFW_KEY_KP_SUBTRACT);
        put("kpadd", GLFW.GLFW_KEY_KP_ADD);
        put("kpenter", GLFW.GLFW_KEY_KP_ENTER);
        put("kpequal", GLFW.GLFW_KEY_KP_EQUAL);

        // 顶行/标点符号
        put("minus", GLFW.GLFW_KEY_MINUS);
        put("equal", GLFW.GLFW_KEY_EQUAL);
        put("equals", GLFW.GLFW_KEY_EQUAL);
        put("leftbracket", GLFW.GLFW_KEY_LEFT_BRACKET);
        put("rightbracket", GLFW.GLFW_KEY_RIGHT_BRACKET);
        put("backslash", GLFW.GLFW_KEY_BACKSLASH);
        put("semicolon", GLFW.GLFW_KEY_SEMICOLON);
        put("apostrophe", GLFW.GLFW_KEY_APOSTROPHE);
        put("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
        put("comma", GLFW.GLFW_KEY_COMMA);
        put("period", GLFW.GLFW_KEY_PERIOD);
        put("slash", GLFW.GLFW_KEY_SLASH);

        // 语义别名：第一人称模拟切换，默认绑定 R
        put("fp", GLFW.GLFW_KEY_R);
    }

    private static void put(String name, int code) {
        KEYS.put(name, code);
    }

    /** 返回键名对应的 GLFW 键码；未知键返回 -1。 */
    public static int glfwKey(String name) {
        if (name == null) {
            return -1;
        }
        return KEYS.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), -1);
    }

    public static boolean isMouseKey(String name) {
        return MOUSE_KEYS.contains(name);
    }

    private KeyNames() {}
}
