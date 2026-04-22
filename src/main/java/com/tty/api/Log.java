package com.tty.api;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class Log {

    private final AbstractJavaPlugin plugin;

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    @Getter
    @Setter
    private volatile boolean debug = false;

    private final static String PREFIX_DEBUG = "[DEBUG] ";
    private final static String[] ANSI_COLORS = {
            "\u001B[31m",
            "\u001B[32m",
            "\u001B[33m",
            "\u001B[34m",
            "\u001B[35m",
            "\u001B[36m",
            "\u001B[91m",
            "\u001B[92m",
            "\u001B[93m",
            "\u001B[94m"
    };
    private final static String ANSI_RESET = "\u001B[0m";

    @Getter
    @Setter
    private volatile boolean enableColor = true;

    @Getter
    @Setter
    private volatile Level minLevel = Level.INFO;

    Log(AbstractJavaPlugin plugin) {
        this.plugin = plugin;
    }

    Log(AbstractJavaPlugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
    }

    public void info(String msg, Object... args) {
        this.log(Level.INFO, msg, args);
    }

    public void warn(String msg, Object... args) {
        this.log(Level.WARNING, msg, args);
    }

    public void warn(Throwable throwable) {
        this.log(Level.WARNING, throwable, null);
    }

    public void warn(Throwable throwable, String msg, Object... args) {
        this.log(Level.WARNING, throwable, msg, args);
    }

    public void error(String msg, Object... args) {
        this.log(Level.SEVERE, msg, args);
    }

    public void error(Throwable throwable) {
        this.log(Level.SEVERE, throwable, null);
    }

    public void error(Throwable throwable, String msg, Object... args) {
        this.log(Level.SEVERE, throwable, msg, args);
    }

    public void debug(String msg, Object... args) {
        if (!this.debug) return;
        this.log(Level.INFO, PREFIX_DEBUG + this.wrapCaller(msg), args);
    }

    public void debug(Throwable throwable, String msg, Object... args) {
        if (!this.debug) return;
        this.log(Level.INFO, throwable, PREFIX_DEBUG + this.wrapCaller(msg), args);
    }

    private void log(Level level, String msg, Object... args) {
        if (this.shouldNotLog(level)) return;
        this.plugin.getLogger().log(level, this.formatMessage(msg, args));
    }

    private void log(Level level, Throwable throwable, String msg, Object... args) {
        if (this.shouldNotLog(level)) return;

        String message = (msg == null)
                ? this.wrapCaller(throwable.getMessage())
                : this.formatMessage(msg, args);

        this.plugin.getLogger().log(level, message, throwable);
    }

    private boolean shouldNotLog(Level level) {
        return level.intValue() < this.minLevel.intValue();
    }

    private String formatMessage(String msg, Object... args) {
        if (msg == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return msg;
        }
        return this.formatBraceStyle(msg, args);
    }

    private String formatBraceStyle(String msg, Object... args) {
        StringBuilder sb = new StringBuilder();
        int length = msg.length();
        int seqIndex = 0;

        for (int i = 0; i < length; i++) {
            char c = msg.charAt(i);

            if (c == '{' && i + 1 < length) {

                if (i + 3 < length
                        && msg.charAt(i + 1) == '{'
                        && msg.charAt(i + 2) == '}'
                        && msg.charAt(i + 3) == '}') {

                    sb.append("{}");
                    i += 3;
                    continue;
                }

                if (msg.charAt(i + 1) == '}') {
                    if (seqIndex < args.length) {
                        this.appendArg(sb, args[seqIndex++]);
                    } else {
                        sb.append("{}");
                    }
                    i++;
                    continue;
                }

                int end = msg.indexOf('}', i + 1);
                if (end > i + 1) {
                    String indexStr = msg.substring(i + 1, end);
                    try {
                        int index = Integer.parseInt(indexStr);
                        if (index >= 0 && index < args.length) {
                            this.appendArg(sb, args[index]);
                        } else {
                            sb.append('{').append(indexStr).append('}');
                        }
                        i = end;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            sb.append(c);
        }

        while (seqIndex < args.length) {
            sb.append(" ");
            this.appendArg(sb, args[seqIndex++]);
        }

        return sb.toString();
    }

    private void appendArg(StringBuilder sb, Object arg) {
        String value = String.valueOf(arg);
        if (this.enableColor) {
            sb.append(this.randomColor())
                    .append(value)
                    .append(ANSI_RESET);
        } else {
            sb.append(value);
        }
    }

    private String wrapCaller(String msg) {
        return "[" + this.getCallerClassName() + "] " + msg;
    }

    private String getCallerClassName() {
        String className = STACK_WALKER.walk(frames ->
                frames.dropWhile(frame -> frame.getDeclaringClass() == Log.class)
                        .findFirst()
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getName)
                        .orElse("Unknown")
        );
        return this.colorize(className);
    }

    private String randomColor() {
        return ANSI_COLORS[ThreadLocalRandom.current().nextInt(ANSI_COLORS.length)];
    }

    private String colorize(String text) {
        if (!this.enableColor || text == null || text.isEmpty()) {
            return text;
        }
        return this.randomColor() + text + ANSI_RESET;
    }

}
