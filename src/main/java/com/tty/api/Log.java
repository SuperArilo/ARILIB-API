package com.tty.api;

import lombok.Getter;
import lombok.Setter;

import java.util.logging.Level;

public class Log {

    private final AbstractJavaPlugin plugin;

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    @Getter
    @Setter
    private volatile boolean debug = false;

    @Getter
    @Setter
    private volatile boolean enableColor = true;

    private static final String ANSI_GREEN = "\u001B[38;5;208m";
    private static final String ANSI_RESET  = "\u001B[0m";

    private static final String PREFIX_DEBUG = "[DEBUG] ";

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

    public void debug(Throwable throwable) {
        if (!this.debug) return;
        this.log(Level.INFO, throwable, PREFIX_DEBUG);
    }

    public void debug(String msg, Object... args) {
        if (!this.debug) return;
        this.log(Level.INFO, PREFIX_DEBUG + wrapCaller(msg), args);
    }

    public void debug(Throwable throwable, String msg, Object... args) {
        if (!this.debug) return;
        this.log(Level.INFO, throwable, PREFIX_DEBUG + wrapCaller(msg), args);
    }

    private void log(Level level, String msg, Object... args) {
        if (this.shouldNotLog(level)) return;
        String message = this.formatMessage(msg, args);
        this.buildLogger(level, message, null);
    }

    private void log(Level level, Throwable throwable, String msg, Object... args) {
        if (this.shouldNotLog(level)) return;
        String message = (msg == null) ? throwable.getMessage() : this.formatMessage(msg, args);
        this.buildLogger(level, message, throwable);
    }

    private boolean shouldNotLog(Level level) {
        return level.intValue() < this.minLevel.intValue();
    }

    private void buildLogger(Level level, String message, Throwable throwable) {
        var logger = this.plugin.getComponentLogger();
        if (throwable != null) {
            if (level == Level.INFO) {
                logger.info(message, throwable);
            } else if (level == Level.WARNING) {
                logger.warn(message, throwable);
            } else if (level == Level.SEVERE) {
                logger.error(message, throwable);
            } else {
                logger.info(message, throwable);
            }
        } else {
            if (level == Level.INFO) {
                logger.info(message);
            } else if (level == Level.WARNING) {
                logger.warn(message);
            } else if (level == Level.SEVERE) {
                logger.error(message);
            } else {
                logger.info(message);
            }
        }
    }

    private String formatMessage(String msg, Object... args) {
        if (msg == null) return "null";
        if (args == null || args.length == 0) return msg;
        return formatBraceStyle(msg, args);
    }

    private String formatBraceStyle(String msg, Object... args) {
        StringBuilder sb = new StringBuilder(msg.length() + args.length * 16);
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
                        sb.append('{').append(indexStr).append('}');
                        i = end;
                        continue;
                    }
                }
            }
            sb.append(c);
        }

        while (seqIndex < args.length) {
            sb.append(' ');
            this.appendArg(sb, args[seqIndex++]);
        }
        return sb.toString();
    }

    private void appendArg(StringBuilder sb, Object arg) {
        String value = String.valueOf(arg);
        if (this.enableColor) {
            sb.append(ANSI_GREEN).append(value).append(ANSI_RESET);
        } else {
            sb.append(value);
        }
    }

    private String wrapCaller(String msg) {
        return "[" + STACK_WALKER.walk(frames -> frames.dropWhile(frame -> frame.getDeclaringClass() == Log.class)
                .findFirst()
                .map(StackWalker.StackFrame::getDeclaringClass)
                .map(Class::getName)
                .orElse("Unknown")) + "] [" + Thread.currentThread().getName() + "] " + msg;
    }
}