package org.unipus;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.unipus.log.BroadcastAppender;
import org.unipus.ui.MainGUI;

/**
 * 程序入口（已修复版）。
 *
 * 修复的问题
 * ----------
 * 症状：重复启动程序时，控制台刷出
 *   main ERROR Unable to delete file ...\logs\latest.log: java.nio.file.FileSystemException
 *   ...\logs\latest.log: 另一个程序正在使用此文件，进程无法访问。
 *
 * 原因有两层：
 *   1. log4j2.xml 里配了 &lt;OnStartupTriggeringPolicy/&gt;，即「每次启动都把现有的
 *      latest.log 滚动/删除」。Windows 上只要还有另一个 JVM 持有这个文件，
 *      删除必然失败并打印上面那行 ERROR。
 *   2. 程序自身没有任何单实例保护，双击两次 run.bat 就会出现两个实例同时跑，
 *      互相抢同一个日志文件，还会同时操作同一个账号。
 *
 * 修复：
 *   * 单实例锁：用 FileChannel.tryLock 独占 logs/.unipushelper.lock，
 *     抢不到锁的第二个实例直接提示并退出，不再产生任何文件冲突。
 *   * 日志按进程隔离：启动时把日志文件名设成 logs/latest-&lt;pid&gt;.log
 *     （log4j2.xml 用 ${sys:uhp.logfile} 读取），实例之间不再共用同一个文件。
 *   * 启动异常时弹窗提示，而不是静默退出。
 */
public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    /** 单实例锁文件：放在 logs 目录下。 */
    private static final Path LOCK_FILE = Paths.get("logs", ".unipushelper.lock");
    private static final Path LOG_DIR = Paths.get("logs");

    /**
     * 持有单实例锁的通道。故意不让它被回收：只要本进程还活着，
     * 这个独占锁就一直有效，第二个实例就无法获得锁。
     */
    private static FileChannel lockChannel;
    private static FileLock instanceLock;

    public static void main(String[] args) {
        if (anotherInstanceRunning()) {
            exitAlreadyRunning();
            return;
        }
        setupLogFile();
        LOGGER.info("Application starting... (pid={})", ProcessHandle.current().pid());
        try {
            setup();
            start();
        }
        catch (Throwable t) {
            LOGGER.error("Application failed to start", t);
            startupFailure(t);
        }
    }

    /**
     * 尝试独占 logs/.unipushelper.lock。
     *
     * @return true 表示已经有一个实例在跑，本次启动应当放弃；
     *         false 表示锁已由本进程持有，可以继续启动。
     */
    private static boolean anotherInstanceRunning() {
        try {
            Files.createDirectories(LOG_DIR);
            lockChannel = FileChannel.open(LOCK_FILE,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
            try {
                instanceLock = lockChannel.tryLock();
            }
            catch (OverlappingFileLockException alreadyHeldInThisJvm) {
                instanceLock = null;
            }
            if (instanceLock == null) {
                closeQuietly(lockChannel);
                lockChannel = null;
                return true;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (instanceLock != null && instanceLock.isValid()) {
                        instanceLock.release();
                    }
                }
                catch (IOException ignored) {
                    // 进程退出时 OS 会释放锁，这里失败无所谓
                }
                closeQuietly(lockChannel);
            }, "uhp-single-instance-unlock"));
            return false;
        }
        catch (IOException cannotCreateOrLock) {
            // 连锁文件都建不了（例如目录只读）时不要拦着用户，放行启动。
            LOGGER.warn("Single-instance lock unavailable, continuing without it", cannotCreateOrLock);
            lockChannel = null;
            instanceLock = null;
            return false;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        }
        catch (IOException ignored) {
            // 忽略关闭异常
        }
    }

    /**
     * 给本次进程指定独立的日志文件。
     *
     * 用相对路径（logs/xxx.log）：log4j2 做 ${sys:...} 插值时会把
     * Windows 绝对路径里的反斜杠当作转义字符，导致插值失败并回退到默认文件名。
     */
    private static void setupLogFile() {
        if (System.getProperty("uhp.logfile") != null) {
            return;
        }
        try {
            String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            String pid = name.contains("@") ? name.substring(0, name.indexOf('@')) : name;
            System.setProperty("uhp.logfile", "logs/latest-" + pid + ".log");
        }
        catch (Throwable t) {
            System.setProperty("uhp.logfile", "logs/latest.log");
        }
    }

    private static void exitAlreadyRunning() {
        String message = "UnipusHelperPro 已经在运行中，请不要重复启动。\n\n"
                + "重复启动会让两个进程抢同一个日志文件（就是你看到的“另一个程序正在使用此文件”报错），"
                + "而且会同时操作同一个账号。\n\n"
                + "请切换到已经打开的那个窗口。如果确实找不到它，"
                + "先在任务管理器里结束所有 java.exe 进程，再重新启动。";
        System.err.println(message);
        scheduleForcedExit(20, 3);
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception ignored) {
            // 用默认外观即可
        }
        try {
            javax.swing.JOptionPane.showMessageDialog(null, message, "UnipusHelperPro",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        }
        catch (Throwable ignored) {
            // 无图形环境时只看控制台输出
        }
        System.exit(3);
    }

    /**
     * 提示窗口会阻塞线程，用户不看就可能留下一个永远不退出的 java 进程。
     * 起一个守护线程兜底：若干秒后强制退出，保证「被拦下的实例」不留残留。
     */
    private static void scheduleForcedExit(int seconds, int code) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
            }
            catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(code);
        }, "uhp-startup-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void startupFailure(Throwable t) {
        String message = "程序启动失败：\n" + t + "\n\n详细日志见程序目录下的 logs 文件夹。";
        scheduleForcedExit(60, 4);
        try {
            javax.swing.JOptionPane.showMessageDialog(null, message, "UnipusHelperPro 启动失败",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
        catch (Throwable ignored) {
            // 无图形环境时只看控制台输出
        }
        System.exit(4);
    }

    private static void setup() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            if (config.getAppenders().get("GuiAppender") == null) {
                BroadcastAppender guiAppender = BroadcastAppender.createAndStart("GuiAppender", 5000, null, null);
                config.addAppender(guiAppender);
                LoggerConfig root = config.getRootLogger();
                root.addAppender(guiAppender, Level.TRACE, null);
                ctx.updateLoggers();
            }
        }
        catch (Throwable t) {
            LOGGER.error("Failed to initialize GUI log appender", t);
        }
    }

    public static void start() {
        MainGUI.getInstance();
    }
}
