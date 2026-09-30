package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.common.exception.ServiceException;
import com.fc.v2.mapper.auto.TAmssOutCardMapper;
import com.fc.v2.mapper.auto.TAmssOutLineMapper;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutLine;
import com.fc.v2.model.custom.OutCardAdvanceBo;
import com.fc.v2.model.custom.OutCardAuditVo;
import com.fc.v2.model.custom.OutCardConsts;
import com.fc.v2.model.custom.OutCardOpenResultVo;
import com.fc.v2.model.custom.OutCardReportVo;

/**
 * 变型拖拉机牌证缴回卡状态机规则单测（不起 Spring，不起库，两张表用内存 Map 顶）。
 *
 * @author fuce
 * @date 2026-09-30
 */
class TAmssOutCardServiceImplTest {

    private TAmssOutCardMapper cardMapper;
    private TAmssOutLineMapper lineMapper;

    private final Map<Long, TAmssOutCard> cards = new LinkedHashMap<Long, TAmssOutCard>();
    private final List<TAmssOutLine> linesStore = new ArrayList<TAmssOutLine>();
    private long seq = 1000L;

    /** 当前登录经办人，可换 */
    private String actor = "张三";

    private TAmssOutCardServiceImpl svc;

    @BeforeEach
    void setUp() {
        cardMapper = mock(TAmssOutCardMapper.class);
        lineMapper = mock(TAmssOutLineMapper.class);
        svc = new TAmssOutCardServiceImpl() {
            @Override
            protected String currentActor() {
                return actor;
            }
        };
        ReflectionTestUtils.setField(svc, "amssOutCardMapper", cardMapper);
        ReflectionTestUtils.setField(svc, "amssOutLineMapper", lineMapper);
        wireMappers();
    }

    @SuppressWarnings("unchecked")
    private void wireMappers() {
        when(cardMapper.selectById(any())).thenAnswer(inv -> cards.get(inv.getArgument(0)));

        when(cardMapper.selectOne(any())).thenAnswer(inv -> {
            Wrapper<TAmssOutCard> w = inv.getArgument(0);
            List<Object> vals = wrapperVals(w);
            // 锁卡查询（FOR UPDATE）：带卡 id（Long）
            for (Object v : vals) {
                if (v instanceof Long) {
                    return cards.get(v);
                }
            }
            // 活卡查询：带车号（String）
            for (Object v : vals) {
                if (v instanceof String) {
                    return activeByVehNo((String) v);
                }
            }
            return null;
        });

        doAnswer(inv -> {
            TAmssOutCard c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(Long.valueOf(++seq));
            }
            if (c.getCreateTime() == null) {
                c.setCreateTime(new Date());
            }
            cards.put(c.getId(), c);
            return 1;
        }).when(cardMapper).insert(any(TAmssOutCard.class));

        when(cardMapper.updateById(any())).thenAnswer(inv -> {
            TAmssOutCard c = inv.getArgument(0);
            cards.put(c.getId(), c);
            return 1;
        });

        when(cardMapper.selectCount(any())).thenAnswer(inv -> {
            Wrapper<TAmssOutCard> w = inv.getArgument(0);
            List<Object> vals = wrapperVals(w);
            int year = -1;
            Integer status = null;
            for (Object v : vals) {
                if (v instanceof Integer) {
                    int iv = (Integer) v;
                    if (iv > 1900) {
                        year = iv;
                    } else if (iv == OutCardConsts.STATUS_ACTIVE
                            || iv == OutCardConsts.STATUS_CLOSED) {
                        // del_flag 的 0 与落定值区分开
                        status = iv;
                    }
                }
            }
            long n = 0;
            for (TAmssOutCard c : cards.values()) {
                if (c.getDelFlag() != null && c.getDelFlag() != 0) {
                    continue;
                }
                if (status != null && (c.getStatus() == null || c.getStatus().intValue() != status)) {
                    continue;
                }
                if (year > 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(c.getCreateTime());
                    if (cal.get(Calendar.YEAR) != year) {
                        continue;
                    }
                }
                n++;
            }
            return Long.valueOf(n).intValue();
        });

        when(lineMapper.selectList(any())).thenAnswer(inv -> {
            Wrapper<TAmssOutLine> w = inv.getArgument(0);
            List<Object> vals = wrapperVals(w);
            Long cardId = null;
            for (Object v : vals) {
                if (v instanceof Long) {
                    cardId = (Long) v;
                }
            }
            List<TAmssOutLine> hit = new ArrayList<TAmssOutLine>();
            for (TAmssOutLine l : linesStore) {
                if ((cardId == null || cardId.equals(l.getCardId()))
                        && (l.getDelFlag() == null || l.getDelFlag() == 0)) {
                    hit.add(l);
                }
            }
            hit.sort(Comparator.comparing(TAmssOutLine::getActTime).thenComparing(TAmssOutLine::getId));
            return hit;
        });

        doAnswer(inv -> {
            TAmssOutLine l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(Long.valueOf(++seq));
            }
            if (l.getCreateTime() == null) {
                l.setCreateTime(new Date());
            }
            if (l.getDelFlag() == null) {
                l.setDelFlag(0);
            }
            linesStore.add(l);
            return 1;
        }).when(lineMapper).insert(any(TAmssOutLine.class));
    }

    private List<Object> wrapperVals(Wrapper<?> w) {
        // MP 3.4.2：eq() 的值先挂在 expression 上，getSqlSegment() 一调才懒合并进 paramNameValuePairs
        w.getSqlSegment();
        Object pairs = ReflectionTestUtils.getField(w, "paramNameValuePairs");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) pairs;
        List<Object> r = new ArrayList<Object>();
        if (map != null) {
            r.addAll(map.values());
        }
        return r;
    }

    private TAmssOutCard activeByVehNo(String vehNo) {
        for (TAmssOutCard c : cards.values()) {
            if (vehNo.equals(c.getVehNo()) && c.getStatus() != null
                    && c.getStatus() == OutCardConsts.STATUS_ACTIVE
                    && (c.getDelFlag() == null || c.getDelFlag() == 0)) {
                return c;
            }
        }
        return null;
    }

    private TAmssOutCard openCard(String vehNo) {
        TAmssOutCard c = svc.open(vehNo);
        Mockito.reset(lineMapper);
        wireLineMapper();
        return c;
    }

    @SuppressWarnings("unchecked")
    private void wireLineMapper() {
        when(lineMapper.selectList(any())).thenAnswer(inv -> {
            Wrapper<TAmssOutLine> w = inv.getArgument(0);
            List<Object> vals = wrapperVals(w);
            Long cardId = null;
            for (Object v : vals) {
                if (v instanceof Long) {
                    cardId = (Long) v;
                }
            }
            List<TAmssOutLine> hit = new ArrayList<TAmssOutLine>();
            for (TAmssOutLine l : linesStore) {
                if ((cardId == null || cardId.equals(l.getCardId()))
                        && (l.getDelFlag() == null || l.getDelFlag() == 0)) {
                    hit.add(l);
                }
            }
            hit.sort(Comparator.comparing(TAmssOutLine::getActTime).thenComparing(TAmssOutLine::getId));
            return hit;
        });

        doAnswer(inv -> {
            TAmssOutLine l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(Long.valueOf(++seq));
            }
            if (l.getCreateTime() == null) {
                l.setCreateTime(new Date());
            }
            if (l.getDelFlag() == null) {
                l.setDelFlag(0);
            }
            linesStore.add(l);
            return 1;
        }).when(lineMapper).insert(any(TAmssOutLine.class));
    }

    private List<TAmssOutLine> linesOf(Long cardId) {
        List<TAmssOutLine> r = new ArrayList<TAmssOutLine>();
        for (TAmssOutLine l : linesStore) {
            if (cardId.equals(l.getCardId())) {
                r.add(l);
            }
        }
        r.sort(Comparator.comparing(TAmssOutLine::getActTime).thenComparing(TAmssOutLine::getId));
        return r;
    }

    private OutCardAdvanceBo checkMaterials(int qty) {
        OutCardAdvanceBo bo = new OutCardAdvanceBo();
        bo.setPlateQty(qty);
        bo.setLicenseStatus("行驶证在、检验在册");
        bo.setClaimClear(OutCardConsts.CLAIM_CLEAR);
        return bo;
    }

    /** 走通：收牌→验车→清账（含缘由）→了结 */
    private TAmssOutCard finishHappyCard(String vehNo) {
        TAmssOutCard c = openCard(vehNo);
        svc.advance(c.getId(), "收牌当面清");
        svc.advance(c.getId(), checkMaterials(2), "验车三样齐");
        svc.addReason(c.getId(), OutCardConsts.REASON_SCRAP, null);
        svc.advance(c.getId(), "清账结");
        svc.advance(c.getId(), "了结");
        return c;
    }

    // ------------------------------------------------------------ 起卡

    @Test
    void open_newCard_startsAtCollect() {
        TAmssOutCard c = svc.open("皖12-0001");
        assertNotNull(c.getId());
        assertNotNull(c.getBizNo());
        assertEquals("皖12-0001", c.getVehNo());
        assertEquals(OutCardConsts.STAGE_COLLECT, c.getStage().intValue());
        assertEquals(OutCardConsts.STATUS_ACTIVE, c.getStatus().intValue());
    }

    @Test
    void open_emptyVehNo_rejected() {
        assertThrows(ServiceException.class, () -> svc.open("  "));
    }

    @Test
    void open_secondActiveCard_blockedAndTellsWhereItStays() {
        TAmssOutCard c = openCard("皖12-0002");
        svc.advance(c.getId(), null);
        ServiceException ex = assertThrows(ServiceException.class, () -> svc.open("皖12-0002"));
        assertTrue(ex.getMessage().contains("皖12-0002"));
        assertTrue(ex.getMessage().contains(c.getBizNo()));
        assertTrue(ex.getMessage().contains("验车"), "要说清压在哪一段：" + ex.getMessage());
    }

    @Test
    void open_afterOldCardClosed_newCardAllowedAndOldUntouched() {
        TAmssOutCard old = finishHappyCard("皖12-0003");
        int oldLines = linesOf(old.getId()).size();
        TAmssOutCard again = svc.open("皖12-0003");
        assertEquals(OutCardConsts.STAGE_COLLECT, again.getStage().intValue());
        assertEquals(oldLines, linesOf(old.getId()).size(), "老卡一个字不许动");
    }

    @Test
    void open_duplicateKeyRace_stillReportedWithStage() {
        // 预检时另一事务还没提交；insert 撞上活车号唯一索引，那一刻对手卡已落库
        TAmssOutCard racer = new TAmssOutCard();
        racer.setId(9001L);
        racer.setBizNo("JH-RACER");
        racer.setVehNo("皖12-0009");
        racer.setStage(OutCardConsts.STAGE_SETTLE);
        racer.setStatus(OutCardConsts.STATUS_ACTIVE);
        racer.setDelFlag(0);

        Mockito.doAnswer(inv -> {
            cards.put(9001L, racer);
            // 对手卡履历已过收牌、验车两段，重放应推出它压在清账段
            linesStore.add(line(9001L, OutCardConsts.STAGE_COLLECT, OutCardConsts.ACTION_FORWARD, 1, "对手"));
            linesStore.add(line(9001L, OutCardConsts.STAGE_CHECK, OutCardConsts.ACTION_FORWARD, 1, "对手", 2));
            throw new DuplicateKeyException("uk_out_card_active_veh");
        }).when(cardMapper).insert(any(TAmssOutCard.class));

        ServiceException ex = assertThrows(ServiceException.class, () -> svc.open("皖12-0009"));
        assertTrue(ex.getMessage().contains("JH-RACER"));
        assertTrue(ex.getMessage().contains("清账"), "撞键后再查也要说清压在哪段：" + ex.getMessage());
    }

    private TAmssOutLine line(long cardId, int stage, int action, int ver, String actorName) {
        return line(cardId, stage, action, ver, actorName, null);
    }

    private TAmssOutLine line(long cardId, int stage, int action, int ver, String actorName, Integer qty) {
        TAmssOutLine l = new TAmssOutLine();
        l.setId(Long.valueOf(++seq));
        l.setCardId(cardId);
        l.setStage(stage);
        l.setAction(action);
        l.setVerNo(ver);
        l.setActor(actorName);
        l.setActTime(new Date());
        l.setDelFlag(0);
        l.setPlateQty(qty);
        return l;
    }

    @Test
    void openBatch_partialRejection() {
        TAmssOutCard first = openCard("皖12-0010");
        assertNotNull(first);
        OutCardOpenResultVo r = svc.openBatch(Arrays.asList("皖12-0010", "皖12-0011", " ", "皖12-0012"));
        assertEquals(2, r.getOpened().size());
        assertEquals(2, r.getRejected().size());
        assertTrue(r.getRejected().get(0).getReason().contains("活卡"));
    }

    // ------------------------------------------------------------ 向前门禁

    @Test
    void advance_collectNoMaterials_movesToCheck() {
        TAmssOutCard c = openCard("皖12-0101");
        TAmssOutCard r = svc.advance(c.getId(), null);
        assertEquals(OutCardConsts.STAGE_CHECK, r.getStage().intValue());
    }

    @Test
    void advance_checkThreeItemsEachMissing_blocked() {
        TAmssOutCard c = openCard("皖12-0102");
        svc.advance(c.getId(), null);

        OutCardAdvanceBo noQty = checkMaterials(1);
        noQty.setPlateQty(null);
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), noQty, null)).getMessage().contains("枚数"));

        OutCardAdvanceBo noLicense = checkMaterials(1);
        noLicense.setLicenseStatus(" ");
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), noLicense, null)).getMessage().contains("在册"));

        OutCardAdvanceBo noClaim = checkMaterials(1);
        noClaim.setClaimClear(null);
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), noClaim, null)).getMessage().contains("罚款"));

        OutCardAdvanceBo uncleared = checkMaterials(1);
        uncleared.setClaimClear(OutCardConsts.CLAIM_UNCLEAR);
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), uncleared, null)).getMessage().contains("未结"));

        // 三样没齐，卡仍压在验车
        assertEquals(OutCardConsts.STAGE_CHECK, cardMapper.selectById(c.getId()).getStage().intValue());
    }

    @Test
    void advance_oldSignatureAtCheck_returnsNull() {
        TAmssOutCard c = openCard("皖12-0103");
        svc.advance(c.getId(), null);
        assertNull(svc.advance(c.getId(), null), "老用法无料，验车口被拒返回 null");
    }

    @Test
    void advance_checkMaterialsComplete_movesToSettle() {
        TAmssOutCard c = openCard("皖12-0104");
        svc.advance(c.getId(), null);
        assertEquals(1, linesOf(c.getId()).size(), "收牌向前已落过一笔履历");
        TAmssOutCard r = svc.advance(c.getId(), checkMaterials(2), null);
        assertEquals(OutCardConsts.STAGE_SETTLE, r.getStage().intValue());
        TAmssOutLine say = linesOf(c.getId()).get(1);
        assertEquals(2, say.getPlateQty().intValue());
        assertEquals(OutCardConsts.CLAIM_CLEAR, say.getClaimClear().intValue());
    }

    @Test
    void advance_materialsForFutureStage_blockedAtCollect() {
        TAmssOutCard c = openCard("皖12-0105");
        // 收牌段就把验车三样递进来——后头的段不能提前结
        ServiceException ex = assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), checkMaterials(2), null));
        assertTrue(ex.getMessage().contains("还没轮到"));
    }

    @Test
    void settle_withoutReason_cannotFinish() {
        TAmssOutCard c = openCard("皖12-0106");
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), checkMaterials(1), null);
        assertNull(svc.advance(c.getId(), null), "缘由没记，老用法了结被拒");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), new OutCardAdvanceBo(), null));
        assertTrue(ex.getMessage().contains("缘由"));
    }

    // ------------------------------------------------------------ 同人同段幂等

    @Test
    void sameActor_passesSameStageTwice_secondFallsSilent() {
        TAmssOutCard c = openCard("皖12-0201");
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), checkMaterials(2), "头一遍");
        int linesAfterFirst = linesOf(c.getId()).size();
        // 同一人、卡已到清账，再把验车三样递一遍
        TAmssOutCard r = svc.advance(c.getId(), checkMaterials(2), "第二遍");
        assertNotNull(r);
        assertEquals(linesAfterFirst, linesOf(c.getId()).size(), "第二遍不添新句");
        assertEquals("头一遍", linesOf(c.getId()).get(1).getRemark(), "头一句不许动");
    }

    @Test
    void differentActor_reachesBackToPassedStage_blocked() {
        TAmssOutCard c = openCard("皖12-0202");
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), checkMaterials(2), null);
        actor = "李四";
        ServiceException ex = assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), checkMaterials(2), null));
        assertTrue(ex.getMessage().contains("够不着"));
    }

    // ------------------------------------------------------------ 退回沉浮

    @Test
    void rollback_qtyMatches_notReturned() {
        TAmssOutCard c = openCard("皖12-0301");
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), checkMaterials(2), null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> svc.rollback(c.getId(), 2, null));
        assertTrue(ex.getMessage().contains("相合"));
    }

    @Test
    void rollback_qtyMismatch_sinksOldVersionAndRedoOpensNew() {
        TAmssOutCard c = openCard("皖12-0302");
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), checkMaterials(2), null); // 验车 v1 记 2 枚

        TAmssOutCard rb = svc.rollback(c.getId(), 1, "实收只 1 枚");
        assertEquals(OutCardConsts.STAGE_CHECK, rb.getStage().intValue());
        List<TAmssOutLine> ls = linesOf(c.getId());
        TAmssOutLine rollbackLine = ls.get(ls.size() - 1);
        assertEquals(OutCardConsts.ACTION_ROLLBACK, rollbackLine.getAction().intValue());
        assertEquals(2, rollbackLine.getVerNo().intValue());

        // 重来一份摆到明面：v3 向前
        svc.advance(c.getId(), checkMaterials(1), "复核后改记1枚");
        TAmssOutLine surface = null;
        int ver = -1;
        for (TAmssOutLine l : linesOf(c.getId())) {
            if (l.getStage() == OutCardConsts.STAGE_CHECK
                    && l.getAction() == OutCardConsts.ACTION_FORWARD && l.getVerNo() > ver) {
                surface = l;
                ver = l.getVerNo();
            }
        }
        assertEquals(3, ver);
        assertEquals(1, surface.getPlateQty().intValue(), "明面上是重来的 1 枚");
    }

    @Test
    void rollback_onlyAtSettle() {
        TAmssOutCard c = openCard("皖12-0303");
        svc.advance(c.getId(), null); // 验车段
        assertNull(svc.rollback(c.getId(), null), "非清账段老用法退回被拒 null");
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.rollback(c.getId(), 1, null)).getMessage().contains("清账段"));
    }

    // ------------------------------------------------------------ 缘由 append-only

    @Test
    void reason_onlyAtSettle_andAppendsWithoutTouchingOld() {
        TAmssOutCard c = openCard("皖12-0401");
        svc.advance(c.getId(), null);
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.addReason(c.getId(), OutCardConsts.REASON_SCRAP, null)).getMessage().contains("清账"));

        svc.advance(c.getId(), checkMaterials(1), null);
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.addReason(c.getId(), "9", null)).getMessage().contains("四种"));

        TAmssOutLine r1 = svc.addReason(c.getId(), OutCardConsts.REASON_SCRAP, null);
        TAmssOutLine r2 = svc.addReason(c.getId(), OutCardConsts.REASON_TRANSFER_OUT, "改主意");
        assertEquals(1, r1.getVerNo().intValue());
        assertEquals(2, r2.getVerNo().intValue());
        assertEquals(OutCardConsts.REASON_SCRAP, linesOf(c.getId()).get(2).getReturnReason(),
                "先前那笔原样搁着");
    }

    // ------------------------------------------------------------ 了结钉死

    @Test
    void finishedCard_isNailedShut() {
        TAmssOutCard c = finishHappyCard("皖12-0501");
        int lineCount = linesOf(c.getId()).size();

        assertEquals(OutCardConsts.STATUS_CLOSED, cardMapper.selectById(c.getId()).getStatus().intValue());
        assertNull(svc.advance(c.getId(), null), "老用法对钉死卡被拒 null");
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.advance(c.getId(), checkMaterials(1), null)).getMessage().contains("钉死"));
        assertNull(svc.rollback(c.getId(), null));
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.rollback(c.getId(), 1, null)).getMessage().contains("钉死"));
        assertTrue(assertThrows(ServiceException.class,
                () -> svc.addReason(c.getId(), OutCardConsts.REASON_LOST, null)).getMessage().contains("钉死"));
        assertEquals(lineCount, linesOf(c.getId()).size(), "钉死后一笔都添不进");
    }

    // ------------------------------------------------------------ 抽卡核验

    @Test
    void audit_happyCardWithRedo_passes() {
        TAmssOutCard c = openCard("皖12-0601");
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), checkMaterials(2), null);
        svc.rollback(c.getId(), 1, null);
        svc.advance(c.getId(), checkMaterials(1), null);
        svc.addReason(c.getId(), OutCardConsts.REASON_SCRAP, null);
        svc.advance(c.getId(), null);
        svc.advance(c.getId(), null);
        OutCardAuditVo vo = svc.audit(c.getId());
        assertTrue(vo.isPassed(), () -> "应一一对得上，实际：" + vo.getProblems());
        assertEquals(OutCardConsts.STAGE_FINISH, vo.getDerivedStage());
        assertTrue(vo.isClosed());
    }

    @Test
    void audit_oldSentenceTampered_detected() {
        TAmssOutCard c = finishHappyCard("皖12-0602");
        // 人手把验车旧句的未结核查改成"尚有未结"
        for (TAmssOutLine l : linesStore) {
            if (l.getCardId().equals(c.getId()) && l.getStage() == OutCardConsts.STAGE_CHECK
                    && l.getAction() == OutCardConsts.ACTION_FORWARD) {
                l.setClaimClear(OutCardConsts.CLAIM_UNCLEAR);
            }
        }
        OutCardAuditVo vo = svc.audit(c.getId());
        assertFalse(vo.isPassed());
        assertTrue(vo.getProblems().stream().anyMatch(p -> p.contains("未结")));
    }

    @Test
    void audit_extraLineAfterFinish_detected() {
        TAmssOutCard c = finishHappyCard("皖12-0603");
        TAmssOutLine hand = new TAmssOutLine();
        hand.setCardId(c.getId());
        hand.setStage(OutCardConsts.STAGE_FINISH);
        hand.setAction(OutCardConsts.ACTION_FORWARD);
        hand.setVerNo(9);
        hand.setActor("人手补");
        hand.setActTime(new Date(System.currentTimeMillis() + 60000));
        hand.setDelFlag(0);
        linesStore.add(hand);
        OutCardAuditVo vo = svc.audit(c.getId());
        assertFalse(vo.isPassed());
        assertTrue(vo.getProblems().stream().anyMatch(p -> p.contains("多出")));
    }

    @Test
    void audit_cardMirrorStageTampered_detected() {
        TAmssOutCard c = finishHappyCard("皖12-0604");
        c.setStage(OutCardConsts.STAGE_COLLECT); // 卡面被人拨回收牌
        OutCardAuditVo vo = svc.audit(c.getId());
        assertFalse(vo.isPassed());
        assertTrue(vo.getProblems().stream().anyMatch(p -> p.contains("卡面")));
    }

    // ------------------------------------------------------------ 年终口径

    @Test
    void yearReport_closedPlusUnderwayEqualsOpened() {
        finishHappyCard("皖12-0701");
        TAmssOutCard halfway = openCard("皖12-0702");
        svc.advance(halfway.getId(), null);
        openCard("皖12-0703");

        int year = Calendar.getInstance().get(Calendar.YEAR);
        OutCardReportVo vo = svc.yearReport(year);
        assertEquals(3, vo.getOpened());
        assertEquals(1, vo.getClosed());
        assertEquals(2, vo.getUnderway());
        assertTrue(vo.isBalanced());
    }

    @Test
    void yearReport_imbalancedFlagged() {
        TAmssOutCard c = openCard("皖12-0704");
        // 造一个既非在办也非了结的脏落定值，两栏就合不上
        c.setStatus(0);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        OutCardReportVo vo = svc.yearReport(year);
        assertFalse(vo.isBalanced());
        assertFalse(vo.getProblems().isEmpty());
    }
}
