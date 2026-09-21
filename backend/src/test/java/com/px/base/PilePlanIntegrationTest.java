package com.px.base;

import com.px.base.dto.PilePlanResultDTO;
import com.px.base.dto.PilePlanSubmitDTO;
import com.px.base.entity.AdaptLog;
import com.px.base.entity.Anchor;
import com.px.base.entity.FlightRoute;
import com.px.base.entity.RouteAnchor;
import com.px.base.repository.AdaptLogRepository;
import com.px.base.repository.AnchorRepository;
import com.px.base.repository.FlightRouteRepository;
import com.px.base.repository.RouteAnchorRepository;
import com.px.base.rule.CheckCode;
import com.px.base.service.PilePlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 航线成组配桩端到端验收：
 * 1) 三条独立判定（气流下限/上限 + 等级承重 + 唯一占用 + 总承重预算）；
 * 2) 预演与提交同一套判定；
 * 3) 并发抢占同一稀缺锚点只成一条；
 * 4) 落库失败时缓存与库一致回滚。
 */
@SpringBootTest
class PilePlanIntegrationTest {

    @Autowired private PilePlanService pilePlanService;
    @Autowired private AnchorRepository anchorRepository;
    @Autowired private FlightRouteRepository flightRouteRepository;
    @Autowired private RouteAnchorRepository routeAnchorRepository;
    @Autowired private AdaptLogRepository adaptLogRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private Long breezeRouteId;
    private Long strongRouteId;
    private Long anchorW600;   // 只适配强风 10-15，承重 600
    private Long anchorW400;   // 只适配强风 10-15，承重 400（下限+承重双失败）
    private Long anchorGood;   // 0-20，承重 2600，全合格
    private Long anchorMid;    // 0-12，承重 900
    private Long anchorScarce; // 0-20，承重 2600，用于并发抢占

    @BeforeEach
    void setUp() {
        adaptLogRepository.deleteAll();
        routeAnchorRepository.deleteAll();
        anchorRepository.deleteAll();
        flightRouteRepository.deleteAll();
        // 清掉排序缓存，避免跨用例脏读
        redisTemplate.delete(List.of("anchor:weight", "anchor:wind:min", "anchor:wind:max"));

        FlightRoute breeze = flightRouteRepository.save(FlightRoute.builder()
                .routeCode("RT-BREEZE").routeName("微风航线").routeGroup("训练组")
                .windSpeed(new BigDecimal("2.00")).status(1).build());
        FlightRoute strong = flightRouteRepository.save(FlightRoute.builder()
                .routeCode("RT-STRONG").routeName("强风航线").routeGroup("专业组")
                .windSpeed(new BigDecimal("12.00")).status(1).build());
        breezeRouteId = breeze.getId();
        strongRouteId = strong.getId();

        anchorW600 = saveAnchor("AN-W600", 600, 10, 15);
        anchorW400 = saveAnchor("AN-W400", 400, 10, 15);
        anchorGood = saveAnchor("AN-GOOD", 2600, 0, 20);
        anchorMid = saveAnchor("AN-MID", 900, 0, 12);
        anchorScarce = saveAnchor("AN-SCARCE", 2600, 0, 20);
    }

    private Long saveAnchor(String code, int weight, int minWind, int maxWind) {
        return anchorRepository.save(Anchor.builder()
                .anchorCode(code).maxWeight(new BigDecimal(weight))
                .minWindSpeed(new BigDecimal(minWind)).maxWindSpeed(new BigDecimal(maxWind))
                .status(1).build()).getId();
    }

    private PilePlanSubmitDTO req(Long routeId, Long... ids) {
        return PilePlanSubmitDTO.builder()
                .routeId(routeId).anchorIds(List.of(ids)).operator("test").build();
    }

    /** 验收点1：只适配强风、承重600的锚点配微风航线，必须报“气流下限不匹配”；
     *  承重400的另一条还要同时报“承重不足微风等级要求500kg”，且整体不通过。 */
    @Test
    void preview_reports_wind_lower_bound_and_weight_grade_failures() {
        PilePlanResultDTO result = pilePlanService.preview(req(breezeRouteId, anchorW600, anchorW400));

        assertFalse(result.isValid(), "含不合格锚点时整套方案必须不通过");
        assertEquals(2, result.getFailedCount());

        var w600 = result.getItems().stream().filter(i -> i.getAnchorId().equals(anchorW600)).findFirst().orElseThrow();
        assertTrue(w600.getFailCodes().contains(CheckCode.WIND_LOW), "600kg强风锚点配微风航线必须报气流下限不匹配");
        assertFalse(w600.getFailCodes().contains(CheckCode.WEIGHT), "600kg 已达到微风500kg，不应报承重不足");
        assertTrue(w600.getFailReasons().stream().anyMatch(r -> r.contains("气流下限不匹配")));

        var w400 = result.getItems().stream().filter(i -> i.getAnchorId().equals(anchorW400)).findFirst().orElseThrow();
        assertTrue(w400.getFailCodes().contains(CheckCode.WIND_LOW), "400kg强风锚点也要报气流下限不匹配");
        assertTrue(w400.getFailCodes().contains(CheckCode.WEIGHT), "400kg低于微风等级要求500kg必须报承重不足等级要求");
        assertTrue(w400.getFailReasons().stream().anyMatch(r -> r.contains("承重不足微风等级要求")));
    }

    /** 预演不落库：即便预演不通过，也不得产生绑定与流水。 */
    @Test
    void preview_never_persists() {
        pilePlanService.preview(req(breezeRouteId, anchorW400));
        assertEquals(0, routeAnchorRepository.count());
        assertEquals(0, adaptLogRepository.count());
    }

    /** 合格锚点预演通过，承重按强风等级1800kg查表。 */
    @Test
    void preview_passes_when_three_gates_all_hold() {
        // strong 风 12m/s：good(0-20/2600) 合格；mid(0-12/900) 承重不足强风1800，单独验证承重等级闸
        PilePlanResultDTO both = pilePlanService.preview(req(strongRouteId, anchorGood, anchorMid));
        assertFalse(both.isValid());
        var midItem = both.getItems().stream().filter(i -> i.getAnchorId().equals(anchorMid)).findFirst().orElseThrow();
        assertTrue(midItem.getFailCodes().contains(CheckCode.WEIGHT));
        assertEquals(1800, both.getLevelMinWeight());

        PilePlanResultDTO ok = pilePlanService.preview(req(strongRouteId, anchorGood));
        assertTrue(ok.isValid());
        assertEquals(0, ok.getFailedCount());
        assertTrue(ok.isBudgetEnough());
    }

    /** 占用判定：锚点已绑在别的启用航线上，预演/提交都必须报 OCCUPIED。 */
    @Test
    void occupied_anchor_is_rejected() {
        // 先把 good 绑到微风航线
        pilePlanService.submit(req(breezeRouteId, anchorGood));
        // 再尝试把同一个 good 配到强风航线
        PilePlanResultDTO result = pilePlanService.preview(req(strongRouteId, anchorGood));
        assertFalse(result.isValid());
        var item = result.getItems().get(0);
        assertTrue(item.getFailCodes().contains(CheckCode.OCCUPIED));
        assertEquals("RT-BREEZE", item.getOccupiedByRouteCode());
    }

    /** 验收点2（策略）：整套回退——一套里有的合格有的不合格，提交一条都不落。 */
    @Test
    void submit_all_or_nothing_rolls_back_when_any_invalid() {
        // good 合格，w400 不合格
        PilePlanResultDTO result = pilePlanService.submit(req(breezeRouteId, anchorGood, anchorW400));

        assertFalse(result.isValid(), "整组必须不通过");
        assertEquals(0, routeAnchorRepository.count(), "整组回退：一个绑定都不能落库，包括合格的 good");
        List<AdaptLog> rejects = adaptLogRepository.findAll();
        assertTrue(rejects.stream().anyMatch(l -> "REJECT".equals(l.getOperationType())
                && l.getAnchorCode().equals("AN-W400")), "不合格锚点必须有 REJECT 流水留痕");
    }

    /** 验收点3：两个运营并发把同一个稀缺锚点配进“各自不同的启用航线”，
     *  最终只一条绑定成功，其余全部 OCCUPIED 冲突拒绝，库里该锚点只有一条生效绑定。 */
    @Test
    void concurrent_claims_only_one_wins() throws InterruptedException {
        int n = 24;
        // 每个请求一条独立启用航线，精确模拟“N 个运营各拿同一稀缺锚点配自己的航线”
        java.util.List<Long> routeIds = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            routeIds.add(flightRouteRepository.save(FlightRoute.builder()
                    .routeCode("RT-C" + i).routeName("并发航线" + i).routeGroup("并发组")
                    .windSpeed(new BigDecimal("12.00")).status(1).build()).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        java.util.List<PilePlanResultDTO> results =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(pilePlanService.submit(req(routeIds.get(idx), anchorScarce)));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发请求应在超时内完成");
        pool.shutdown();

        long wins = results.stream().filter(PilePlanResultDTO::isValid).count();
        assertEquals(1, wins, "并发下稀缺锚点只能有一条方案绑定成功，实际成功数=" + wins);

        List<RouteAnchor> active = routeAnchorRepository.findByAnchorIdAndStatus(anchorScarce, 1);
        assertEquals(1, active.size(), "库里该锚点只能有一条生效绑定");

        // 每个失败请求都应得到 OCCUPIED（或极端竞争下的 LOCK_BUSY）拒绝码
        long occupiedRejects = results.stream()
                .filter(r -> !r.isValid())
                .filter(r -> r.getItems().stream().flatMap(it -> it.getFailCodes().stream())
                        .anyMatch(c -> c.equals(CheckCode.OCCUPIED) || c.equals(CheckCode.LOCK_BUSY)))
                .count();
        assertEquals(n - 1, occupiedRejects, "其余 " + (n - 1) + " 个请求都应收到占用/锁冲突拒绝");

        long rejectLogs = adaptLogRepository.findAll().stream()
                .filter(l -> "REJECT".equals(l.getOperationType())
                        && (l.getReason().contains(CheckCode.OCCUPIED) || l.getReason().contains(CheckCode.LOCK_BUSY)))
                .count();
        assertEquals(n - 1, rejectLogs, "失败请求必须在流水里留下冲突/占用拒绝记录, 实际=" + rejectLogs);
    }

    /** 验收点4：提交成功后缓存与库一致；人为制造落库失败，缓存随库一起回退，无半拉子数据。 */
    @Test
    void cache_and_db_roll_back_together_on_persistence_failure() {
        // 先把排序缓存初始化成“提交前”状态
        pilePlanService.preview(req(breezeRouteId, anchorGood)); // 不写缓存
        // 成功路径：提交 good，缓存三 ZSET 都应包含该锚点，库里有绑定
        PilePlanResultDTO ok = pilePlanService.submit(req(breezeRouteId, anchorGood));
        assertTrue(ok.isValid());
        assertEquals(1, routeAnchorRepository.count());
        assertEquals(Double.valueOf(2600d), redisTemplate.opsForZSet().score("anchor:weight", "AN-GOOD"));

        // 失败路径：另一锚点 mid（微风也合格），开 simulateDbFailure
        PilePlanSubmitDTO bad = PilePlanSubmitDTO.builder()
                .routeId(breezeRouteId).anchorIds(List.of(anchorMid))
                .operator("test").simulateDbFailure(true).build();
        PilePlanResultDTO failed = pilePlanService.submit(bad);

        assertFalse(failed.isValid(), "落库失败时提交必须返回失败");
        // 库里仍只有 good 一条绑定，没有 mid
        assertEquals(1, routeAnchorRepository.count(), "落库失败不得产生新绑定");
        assertTrue(routeAnchorRepository.findByAnchorIdAndStatus(anchorMid, 1).isEmpty(),
                "失败锚点不得留下绑定");
        // 缓存里不得出现 AN-MID（脏数据防护）
        assertNull(redisTemplate.opsForZSet().score("anchor:weight", "AN-MID"),
                "落库失败后缓存必须回退，不能留下库中没有的 AN-MID");
        assertNull(redisTemplate.opsForZSet().score("anchor:wind:max", "AN-MID"));
        // good 仍在缓存且分数正确（回退不影响已成功数据）
        assertEquals(Double.valueOf(2600d), redisTemplate.opsForZSet().score("anchor:weight", "AN-GOOD"));
        // 失败也要留痕
        assertTrue(adaptLogRepository.findAll().stream()
                .anyMatch(l -> "REJECT".equals(l.getOperationType())
                        && l.getReason().contains(CheckCode.DB_FAILURE)), "落库失败必须写 REJECT/DB_FAILURE 流水");
    }

    /** 若缓存提交前已有“错误旧分数”，回退要恢复到旧值而不是简单删除。 */
    @Test
    void cache_restore_reverts_to_previous_score() {
        // 人为把 AN-GOOD 的承重缓存写成一个错误旧值
        redisTemplate.opsForZSet().add("anchor:weight", "AN-GOOD", 1d);
        PilePlanSubmitDTO bad = PilePlanSubmitDTO.builder()
                .routeId(breezeRouteId).anchorIds(List.of(anchorGood))
                .operator("test").simulateDbFailure(true).build();
        PilePlanResultDTO failed = pilePlanService.submit(bad);
        assertFalse(failed.isValid());
        assertEquals(Double.valueOf(1d), redisTemplate.opsForZSet().score("anchor:weight", "AN-GOOD"),
                "回退应恢复到提交前的旧分数 1，而不是 2600 或删除");
        assertEquals(0, routeAnchorRepository.count());
    }
}
