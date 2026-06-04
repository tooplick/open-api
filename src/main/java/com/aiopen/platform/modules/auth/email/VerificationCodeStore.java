package com.aiopen.platform.modules.auth.email;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱验证码的进程内存存储(本项目无 Redis)。
 * key = purpose:email(小写);6 位数字验证码,TTL 10 分钟,惰性过期;
 * 校验成功即一次性消费(remove)以缩小枚举窗口;另提供按 key 的发送冷却控制。
 * 注意:仅进程内,多实例不共享、重启即失效——邮箱注册按单实例部署。
 */
@Component
public class VerificationCodeStore {

    public static final String PURPOSE_REGISTER = "register";

    private static final Duration TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Entry> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSentAt = new ConcurrentHashMap<>();

    private record Entry(String code, long expireAt) {
    }

    private String key(String purpose, String email) {
        return purpose + ":" + email.trim().toLowerCase();
    }

    /** 距上次发送是否已超过冷却期(从未发送则允许)。 */
    public boolean canSend(String purpose, String email, Duration cooldown) {
        Long last = lastSentAt.get(key(purpose, email));
        return last == null || System.currentTimeMillis() - last >= cooldown.toMillis();
    }

    /** 记录一次成功发送的时间,用于冷却判定。 */
    public void markSent(String purpose, String email) {
        lastSentAt.put(key(purpose, email), System.currentTimeMillis());
    }

    /** 生成并存储 6 位数字验证码(覆盖同 key 旧值),返回该验证码。 */
    public String generate(String purpose, String email) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        codes.put(key(purpose, email), new Entry(code, System.currentTimeMillis() + TTL.toMillis()));
        return code;
    }

    /** 校验验证码:正确且未过期返回 true 并一次性消费;否则返回 false。 */
    public boolean verify(String purpose, String email, String code) {
        String k = key(purpose, email);
        Entry entry = codes.get(k);
        if (entry == null) {
            return false;
        }
        if (System.currentTimeMillis() > entry.expireAt()) {
            codes.remove(k);
            return false;
        }
        if (!entry.code().equals(code)) {
            return false;
        }
        codes.remove(k);
        return true;
    }
}
