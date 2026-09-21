package com.px.base.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 锚点级 Redis 分布式锁。
 * <p>
 * 作用：两个运营几乎同时把同一个稀缺锚点配进各自航线时，在应用层先把该锚点串行化，
 * 保证只有一条方案能进入“占用判定 + 落库”，另一条会在同一套判定下得到 OCCUPIED 冲突。
 * <p>
 * 释放时用 Lua 脚本做 token 比对，只删自己持有的锁，避免误删别人的锁。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnchorLockService {

    private static final String KEY_PREFIX = "lock:anchor:";
    private static final long LOCK_TTL_SECONDS = 30;
    private static final long WAIT_MAX_MILLIS = 10_000;
    private static final long SPIN_MILLIS = 50;

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class);

    /**
     * 一次性、按锚点ID升序获取整套方案涉及的全部锚点锁（固定顺序，避免两套方案互锁成环）。
     * 任一锚点在等待窗口内抢不到，则释放已抢到的锁并抛 {@link AnchorBusyException}。
     */
    public AnchorLocks lockAll(Collection<Long> anchorIds) {
        List<Long> sorted = anchorIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        String token = UUID.randomUUID().toString();
        List<Long> acquired = new ArrayList<>();
        for (Long anchorId : sorted) {
            String key = KEY_PREFIX + anchorId;
            long deadline = System.currentTimeMillis() + WAIT_MAX_MILLIS;
            boolean locked = false;
            while (System.currentTimeMillis() < deadline) {
                Boolean ok = stringRedisTemplate.opsForValue()
                        .setIfAbsent(key, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(ok)) {
                    locked = true;
                    break;
                }
                try {
                    Thread.sleep(SPIN_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    releaseAll(acquired, token);
                    throw new AnchorBusyException(anchorId, "等待锚点锁时被中断");
                }
            }
            if (!locked) {
                releaseAll(acquired, token);
                throw new AnchorBusyException(anchorId,
                        "锚点" + anchorId + "正被另一个配桩方案占用，请稍后重试或更换锚点");
            }
            acquired.add(anchorId);
        }
        return new AnchorLocks(acquired, token, this);
    }

    private void releaseAll(List<Long> anchorIds, String token) {
        for (Long anchorId : anchorIds) {
            try {
                stringRedisTemplate.execute(RELEASE_SCRIPT,
                        Collections.singletonList(KEY_PREFIX + anchorId), token);
            } catch (Exception e) {
                log.warn("释放锚点锁失败 key={}: {}", anchorId, e.getMessage());
            }
        }
    }

    /** 一套方案持有的全部锚点锁，try-with-resources 自动释放。 */
    public static class AnchorLocks implements AutoCloseable {
        private final List<Long> anchorIds;
        private final String token;
        private final AnchorLockService lockService;

        AnchorLocks(List<Long> anchorIds, String token, AnchorLockService lockService) {
            this.anchorIds = anchorIds;
            this.token = token;
            this.lockService = lockService;
        }

        @Override
        public void close() {
            lockService.releaseAll(anchorIds, token);
        }
    }

    /** 抢锁超时异常，携带争用失败的锚点ID。 */
    public static class AnchorBusyException extends RuntimeException {
        private final Long anchorId;

        public AnchorBusyException(Long anchorId, String message) {
            super(message);
            this.anchorId = anchorId;
        }

        public Long getAnchorId() {
            return anchorId;
        }
    }
}
