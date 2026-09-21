package org.unipus.unipus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 全局提交节流器（修复“提交速度太快”）。
 *
 * <p>原版程序的控制有两个问题：
 * <ol>
 *   <li>每分钟提交次数（MAX_SUBMIT_PER_MINUTE）和 {@code submitTimestamps} 都是
 *       {@link Learn} 的实例字段，而每个 Task 都会 new 一个 Learn，
 *       所以计数永远从 0 开始，根本拦不住跨任务的连续提交。</li>
 *   <li>两次提交之间没有任何最小间隔，任务一做完整套题就立刻提交，
 *       短时间内连续十几次提交必然触发服务端限流
 *       （返回 600001 / 600002，界面显示“提交速度太快”）。</li>
 * </ol>
 *
 * <p>本类用「进程级静态状态」重做提交节流：所有 Learn 实例共用同一份提交历史，
 * 每次提交前强制等待一个最小间隔，并且对服务端的限流响应做指数退避。
 *
 * <p>所有参数都可以在程序目录下的 {@code unipushelper.properties} 里调整，
 * 文件不存在时使用下面的默认值（默认值偏保守，保证能长时间无人值守跑完）。
 */
public final class SubmitThrottle {

    private static final Logger LOGGER = LogManager.getLogger(SubmitThrottle.class);

    private static final String CONFIG_FILE_NAME = "unipushelper.properties";

    /** 两次提交之间的最小间隔（毫秒）。 */
    private static final long MIN_INTERVAL_MS;
    /** 在最小间隔之上附加的随机抖动上限（毫秒），让节奏不那么机械。 */
    private static final long JITTER_MS;
    /** 滚动 60 秒窗口内允许的最大提交次数（留出余量，低于服务端阈值）。 */
    private static final int MAX_PER_MINUTE;
    /** 被服务端限流后的首次冷却时间（毫秒）。 */
    private static final long BACKOFF_BASE_MS;
    /** 冷却时间翻倍的上限（毫秒）。 */
    private static final long BACKOFF_MAX_MS;
    /** 距离上次限流多久之内再次被限流，就继续翻倍（毫秒）。 */
    private static final long BACKOFF_RESET_MS;

    private static final Deque<Long> SUBMIT_TIMES = new ArrayDeque<>();
    private static final Object LOCK = new Object();
    private static long lastSubmitAt;
    private static long nextAllowedAt;
    private static long backoffMs;
    private static long lastRateLimitedAt;

    static {
        long minInterval = readLong("submit.minIntervalMs", 20000L);
        long jitter = readLong("submit.jitterMs", 8000L);
        int maxPerMinute = (int) readLong("submit.maxPerMinute", 3L);
        long backoffBase = readLong("submit.backoffBaseMs", 120000L);
        long backoffMax = readLong("submit.backoffMaxMs", 900000L);
        long backoffReset = readLong("submit.backoffResetMs", 600000L);

        MIN_INTERVAL_MS = Math.max(0L, minInterval);
        JITTER_MS = Math.max(0L, jitter);
        MAX_PER_MINUTE = Math.max(1, maxPerMinute);
        BACKOFF_BASE_MS = Math.max(1000L, backoffBase);
        BACKOFF_MAX_MS = Math.max(BACKOFF_BASE_MS, backoffMax);
        BACKOFF_RESET_MS = Math.max(BACKOFF_BASE_MS, backoffReset);
        backoffMs = BACKOFF_BASE_MS;

        LOGGER.info("Submit throttle: interval={}ms jitter={}ms maxPerMinute={} "
                        + "(可在 {} 中调整)",
                MIN_INTERVAL_MS, JITTER_MS, MAX_PER_MINUTE, CONFIG_FILE_NAME);
    }

    private SubmitThrottle() {
    }

    /**
     * 计算本次提交前需要等待的毫秒数（不阻塞）。
     *
     * @return 需要等待的毫秒数，0 表示可以立即提交
     */
    public static long delayBeforeSubmit() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            long wait = 0L;

            if (nextAllowedAt > now) {
                wait = nextAllowedAt - now;
            }
            if (lastSubmitAt > 0L) {
                long pacing = lastSubmitAt + MIN_INTERVAL_MS + jitter() - now;
                if (pacing > wait) {
                    wait = pacing;
                }
            }

            prune(now);
            if (SUBMIT_TIMES.size() >= MAX_PER_MINUTE) {
                long window = SUBMIT_TIMES.peekFirst() + 60000L - now;
                if (window > wait) {
                    wait = window;
                }
            }

            if (wait > 0L) {
                LOGGER.info("节流：本次提交前等待 {} ms（保持稳定节奏，避免被判定提交过快）", wait);
            }
            return wait;
        }
    }

    /** 提交完成后记录时间戳。 */
    public static void recordSubmit() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            lastSubmitAt = now;
            SUBMIT_TIMES.addLast(now);
            prune(now);
        }
    }

    /**
     * 服务端返回限流（600001 / 600002）时调用，登记下一次提交前必须等待的时间。
     *
     * @return 本次要等待的毫秒数
     */
    public static long onRateLimited() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (lastRateLimitedAt > 0L && now - lastRateLimitedAt > BACKOFF_RESET_MS) {
                backoffMs = BACKOFF_BASE_MS;
            }
            long wait = backoffMs;
            lastRateLimitedAt = now;
            nextAllowedAt = now + wait;
            backoffMs = Math.min(BACKOFF_MAX_MS, backoffMs * 2L);
            LOGGER.warn("服务端判定提交过快，本次冷却 {} ms，下次再触发将延长至 {} ms",
                    wait, backoffMs);
            return wait;
        }
    }

    /** 成功提交后清空限流退避，恢复正常节奏。 */
    public static void onAccepted() {
        synchronized (LOCK) {
            backoffMs = BACKOFF_BASE_MS;
            nextAllowedAt = 0L;
        }
    }

    private static long jitter() {
        return JITTER_MS <= 0L ? 0L : (long) (Math.random() * JITTER_MS);
    }

    private static void prune(long now) {
        while (!SUBMIT_TIMES.isEmpty() && SUBMIT_TIMES.peekFirst() + 60000L < now) {
            SUBMIT_TIMES.pollFirst();
        }
    }

    /**
     * 读取配置：优先看程序目录下的 {@code unipushelper.properties}，
     * 找不到就回退到系统属性（便于用 -Dsubmit.minIntervalMs=... 临时覆盖）。
     */
    private static long readLong(String key, long defaultValue) {
        String raw = null;
        Path file = Paths.get(CONFIG_FILE_NAME);
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                java.util.Properties props = new java.util.Properties();
                props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                raw = props.getProperty(key);
            }
            catch (IOException e) {
                LOGGER.warn("读取 {} 失败，使用默认配置：{}", CONFIG_FILE_NAME, e.getMessage());
            }
        }
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty(key);
        }
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        }
        catch (NumberFormatException e) {
            LOGGER.warn("配置项 {} 的值 “{}” 不是数字，使用默认值 {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 首次运行时生成一份带注释的配置模板，方便用户自己调节提交节奏。
     * 只在文件不存在时创建，不会覆盖用户已经改过的配置。
     */
    public static void ensureConfigTemplate() {
        Path file = Paths.get(CONFIG_FILE_NAME);
        if (Files.exists(file)) {
            return;
        }
        String template = """
                # ============================================================
                #  UnipusHelperPro 配置（提交节流）
                #  改动后需要重启程序才会生效。删掉本文件即恢复默认值。
                # ============================================================
                #
                # 两次提交之间的最小间隔（毫秒）。
                # 调大 = 更慢更稳，调小 = 更快但可能被服务端判定“提交速度太快”。
                # 实测：3 秒左右的间隔会被服务端限流，所以默认给到 20 秒。
                # 不建议低于 10000。
                submit.minIntervalMs=20000
                #
                # 在最小间隔之上附加的随机抖动上限（毫秒），让提交节奏不那么机械。
                # 默认 8000（0~8 秒随机）。
                submit.jitterMs=8000
                #
                # 滚动 60 秒内最多提交几次。默认 3，留出余量。
                submit.maxPerMinute=3
                #
                # 被服务端限流后的首次冷却时间（毫秒），默认 120000（2 分钟）。
                submit.backoffBaseMs=120000
                #
                # 连续被限流时冷却时间的上限（毫秒），默认 900000（15 分钟）。
                submit.backoffMaxMs=900000
                #
                # 距离上次限流超过这个时间就重置退避（毫秒），默认 600000（10 分钟）。
                submit.backoffResetMs=600000
                """;
        try {
            Files.writeString(file, template, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            LOGGER.info("已生成配置文件 {}，可在此调整提交节奏", CONFIG_FILE_NAME);
        }
        catch (IOException e) {
            LOGGER.debug("生成配置模板失败（可忽略）：{}", e.getMessage());
        }
    }
}
