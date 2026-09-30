package com.fc.v2.amss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAmssOutCardMapper;
import com.fc.v2.mapper.auto.TAmssOutCardStageMapper;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutCardStage;
import com.fc.v2.model.custom.OutCardActionResult;
import com.fc.v2.model.custom.OutCardStageData;
import com.fc.v2.model.custom.OutCardYearReport;
import com.fc.v2.service.ITAmssOutCardService;

/**
 * 缴回卡四段事务线的逐条规矩验证（真 H2 库 + 真唯一约束）。
 */
public class TAmssOutCardServiceTest extends BaseOutCardTest {

    private static final String ZHANG = "张三";
    private static final String LI = "李四";

    private OutCardStageData inspectData() {
        OutCardStageData d = new OutCardStageData();
        d.setPlateCount(2);
        d.setLicenseStatus("行驶证已收、检验在册");
        d.setOpenCaseFlag(0);
        d.setOperator(ZHANG);
        return d;
    }

    private List<TAmssOutCardStage> livePass(TAmssOutCardStageMapper sm, Long cardId) {
        return sm.selectList(new QueryWrapper<TAmssOutCardStage>()
                .eq("card_id", cardId).eq("action_type", "PASS").eq("current_flag", 1)
                .orderByAsc("stage_no"));
    }

    /** 规矩一：一车只许一张活卡，起第二张当场挡回，并说清压在哪一段 */
    @Test
    void open_oneActiveCardPerVehicle_secondRejectedWithStage() {
        ITAmssOutCardService svc = newService();

        OutCardActionResult first = svc.open("赣01-变拖0001", "JH001");
        assertTrue(first.isSuccess());
        assertEquals(0, svc.currentStage(first.getCard().getId()));

        OutCardActionResult dup = svc.open("赣01-变拖0001", "JH002");
        assertFalse(dup.isSuccess());
        assertTrue(dup.getMessage().contains("收牌"), "挡回话须报明压在收牌段：" + dup.getMessage());
        assertNotNull(dup.getCard());

        // 推进到验车后再试，挡回话跟着报验车段
        assertTrue(svc.advance(first.getCard().getId(), collectData(ZHANG)).isSuccess());
        OutCardActionResult dup2 = svc.open("赣01-变拖0001", null);
        assertFalse(dup2.isSuccess());
        assertTrue(dup2.getMessage().contains("验车"));

        // 库级唯一约束兜底：两台车各起各的不打架
        assertTrue(svc.open("赣01-变拖0002", null).isSuccess());
    }

    /** 规矩二：卡停哪段只放哪段的流程；验车三样不齐进不了下一段 */
    @Test
    void advance_stageGate_inspectThreeItemsRequired() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0003", null).getCard().getId();

        // 收牌段无另填项，直接过
        assertTrue(svc.advance(id, collectData(ZHANG)).isSuccess());
        assertEquals(1, svc.currentStage(id));

        OutCardStageData bad = new OutCardStageData();
        bad.setOperator(ZHANG);
        // 三样全缺
        assertFalse(svc.advance(id, bad).isSuccess());
        // 缺一样（未结事故罚款）仍不行
        bad.setPlateCount(2);
        bad.setLicenseStatus("在册");
        assertFalse(svc.advance(id, bad).isSuccess());
        // 枚数不许是负数
        OutCardStageData neg = inspectData();
        neg.setPlateCount(-1);
        assertFalse(svc.advance(id, neg).isSuccess());
        // 三样齐了才放行
        assertTrue(svc.advance(id, inspectData()).isSuccess());
        assertEquals(2, svc.currentStage(id));

        // 清账段缘由不认，拒进
        OutCardStageData noReason = new OutCardStageData();
        noReason.setOperator(ZHANG);
        assertFalse(svc.advance(id, noReason).isSuccess());
        assertFalse(svc.addReason(id, "HUAI_LE", ZHANG).isSuccess());
        // 缘由在清账段另记一笔，记过才许过段
        assertTrue(svc.addReason(id, OutCardStageData.REASON_SCRAP, ZHANG).isSuccess());
        assertTrue(svc.advance(id, collectData(ZHANG)).isSuccess());
        assertEquals(3, svc.currentStage(id));
    }

    /** 规矩三：同一人把同一段过两遍，只落头一遍——库上同卡同段明面笔唯一 */
    @Test
    void advance_sameStageTwice_onlyFirstStrokeEverCurrent() throws Exception {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0004", null).getCard().getId();
        assertTrue(svc.advance(id, collectData(ZHANG)).isSuccess());

        // 绕过流程直接再塞一笔同段明面PASS：唯一约束当场顶回，第二遍落不进卡
        TAmssOutCardStageMapper sm = sqlSession.getMapper(TAmssOutCardStageMapper.class);
        TAmssOutCardStage rogue = new TAmssOutCardStage();
        rogue.setId(System.nanoTime());
        rogue.setCardId(id);
        rogue.setStageNo(0);
        rogue.setRevNo(9);
        rogue.setCurrentFlag(1);
        rogue.setActionType("PASS");
        rogue.setOperator(LI);
        rogue.setHappenTime(new java.util.Date());
        assertThrows(DataIntegrityViolationException.class, () -> sm.insert(rogue));
    }

    /** 规矩四：清账缘由改一次续一笔，先前各笔原样搁着，只有最新一笔在明面 */
    @Test
    void reason_changeAppends_oldStrokesKeptIntact() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0005", null).getCard().getId();
        svc.advance(id, collectData(ZHANG));
        svc.advance(id, inspectData());
        assertEquals(2, svc.currentStage(id));

        assertTrue(svc.addReason(id, OutCardStageData.REASON_SCRAP, ZHANG).isSuccess());
        assertTrue(svc.addReason(id, OutCardStageData.REASON_LOST, ZHANG).isSuccess());
        assertTrue(svc.addReason(id, OutCardStageData.REASON_OUT_PROV, LI).isSuccess());

        List<TAmssOutCardStage> reasons = svc.history(id).stream()
                .filter(s -> "REASON".equals(s.getActionType())).collect(Collectors.toList());
        assertEquals(3, reasons.size(), "改两次共三笔，一笔不丢");
        assertEquals("SCRAP", reasons.get(0).getReturnReason());
        assertEquals(Integer.valueOf(0), reasons.get(0).getCurrentFlag(), "头一笔原样沉底，内容不动");
        assertEquals("LOST", reasons.get(1).getReturnReason());
        assertEquals(Integer.valueOf(0), reasons.get(1).getCurrentFlag());
        assertEquals("OUT_PROV", reasons.get(2).getReturnReason());
        assertEquals(Integer.valueOf(1), reasons.get(2).getCurrentFlag(), "最新一笔摆明面");

        // 缘由只认清账段：人在收牌段记不进去
        Long id2 = svc.open("赣01-变拖0006", null).getCard().getId();
        assertFalse(svc.addReason(id2, OutCardStageData.REASON_LOST, ZHANG).isSuccess());
    }

    /** 规矩五：清账看出验车枚数不合，退回验车——旧验车说法沉底，重来说法摆明面，旧说法留档 */
    @Test
    void rollback_sinkPreviousStatement_redoComesOnTop() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0007", null).getCard().getId();
        svc.advance(id, collectData(ZHANG));
        svc.advance(id, inspectData());          // 验车记 2 枚，过段后坐进清账
        svc.addReason(id, "SCRAP", ZHANG);       // 坐在清账段记缘由（记缘由不移段）
        assertEquals(2, svc.currentStage(id), "仍坐在清账段");

        OutCardStageData rb = new OutCardStageData();
        rb.setOperator(LI);
        rb.setRemark("清账核对：验车记2枚，实收仅1枚，两个地方不合，退回验车重来");
        OutCardActionResult back = svc.rollback(id, rb);
        assertTrue(back.isSuccess());
        assertEquals(1, svc.currentStage(id), "退回上一段：回坐验车");

        // 退回的是验车，清账段先前记的缘由不归这次退水管：原样留在明面
        assertEquals("SCRAP", svc.history(id).stream()
                .filter(s -> "REASON".equals(s.getActionType()) && s.getCurrentFlag() == 1)
                .findFirst().map(TAmssOutCardStage::getReturnReason).orElse(null),
                "缘由笔归清账段自己管，退回验车不动它");

        List<TAmssOutCardStage> hist = svc.history(id);
        TAmssOutCardStage oldInspect = hist.stream()
                .filter(s -> s.getStageNo() == 1 && "PASS".equals(s.getActionType())
                        && s.getRevNo() == 1).findFirst().orElse(null);
        assertNotNull(oldInspect);
        assertEquals(Integer.valueOf(0), oldInspect.getCurrentFlag(), "先前那份沉下去");
        assertEquals(Integer.valueOf(2), oldInspect.getPlateCount(), "旧句一个字不动");

        TAmssOutCardStage rbStroke = hist.stream()
                .filter(s -> "ROLLBACK".equals(s.getActionType())).findFirst().orElse(null);
        assertNotNull(rbStroke);
        assertEquals(Integer.valueOf(0), rbStroke.getCurrentFlag());
        assertTrue(rbStroke.getStageData().contains("两个地方不合"));

        // 收牌段的话仍算着：验车不重结，清账不能提前结（当前段被打回验车）
        // 重新验车，改记 1 枚
        OutCardStageData redo = inspectData();
        redo.setPlateCount(1);
        redo.setOperator(LI);
        assertTrue(svc.advance(id, redo).isSuccess());
        assertEquals(2, svc.currentStage(id));

        TAmssOutCardStageMapper sm = sqlSession.getMapper(TAmssOutCardStageMapper.class);
        List<TAmssOutCardStage> inspectLive = livePass(sm, id).stream()
                .filter(s -> s.getStageNo() == 1).collect(Collectors.toList());
        assertEquals(1, inspectLive.size(), "同一处明面只摆一份说法");
        assertEquals(Integer.valueOf(1), inspectLive.get(0).getPlateCount(), "明面是重来那份");
        assertEquals(Integer.valueOf(2), inspectLive.get(0).getRevNo());

        // 旧收牌说法仍在明面，未被动过
        TAmssOutCardStage collect = livePass(sm, id).stream()
                .filter(s -> s.getStageNo() == 0).findFirst().orElse(null);
        assertNotNull(collect);
        assertEquals(ZHANG, collect.getOperator());
    }

    /** 规矩六：收牌段无前可退；退回只退一段 */
    @Test
    void rollback_atFirstStageRejected_andSingleStepOnly() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0008", null).getCard().getId();
        assertFalse(svc.rollback(id, collectData(ZHANG)).isSuccess());

        svc.advance(id, collectData(ZHANG));
        svc.advance(id, inspectData());
        // 坐在清账，退回后只到验车，不会一步回到收牌
        assertTrue(svc.rollback(id, collectData(LI)).isSuccess());
        assertEquals(1, svc.currentStage(id));
    }

    /** 规矩七：了结一笔落下即钉死——添不进、改不动、退不回、抽不走 */
    @Test
    void finish_sealsCard() {
        ITAmssOutCardService svc = newService();
        Long id = finishOneCard("赣01-变拖0009");
        int histSize = svc.history(id).size();

        assertEquals(2, svc.selectTAmssOutCardById(id).getStatus().intValue());
        assertFalse(svc.advance(id, collectData(ZHANG)).isSuccess());
        assertFalse(svc.rollback(id, collectData(ZHANG)).isSuccess());
        assertFalse(svc.addReason(id, "LOST", ZHANG).isSuccess());
        assertFalse(svc.updateContent(id, "想改旧句"), "就地改不了旧句");
        assertFalse(svc.remove(id), "整张抽不走");
        assertEquals(histSize, svc.history(id).size(), "履历一笔不多");
        // 走完的那一张只许翻
        assertEquals(3, svc.currentStage(id));
    }

    /** 规矩八：半年后再办是另一张卡，新卡从收牌起，老卡一个字不动 */
    @Test
    void reopen_afterFinish_newCardFromCollect_oldUntouched() {
        ITAmssOutCardService svc = newService();
        Long oldId = finishOneCard("赣01-变拖0010");
        List<TAmssOutCardStage> oldHist = svc.history(oldId);
        String oldLast = svc.selectTAmssOutCardById(oldId).getLastAction();

        OutCardActionResult again = svc.open("赣01-变拖0010", "JH-NEW-1");
        assertTrue(again.isSuccess(), "老卡了结后，同一台车允许再起新卡");
        Long newId = again.getCard().getId();
        assertEquals(0, svc.currentStage(newId), "新卡照旧从收牌起");
        assertEquals(oldHist.size(), svc.history(oldId).size());
        assertEquals(oldLast, svc.selectTAmssOutCardById(oldId).getLastAction(), "老卡一个字不动");
    }

    /** 规矩九：只有没走完的卡可抽；抽走后同车可再起 */
    @Test
    void remove_onlyActiveCard_terminalStays() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0011", null).getCard().getId();
        svc.advance(id, collectData(ZHANG));
        assertTrue(svc.remove(id));
        assertNull(svc.selectTAmssOutCardById(id), "软抽后台账翻不到");
        assertTrue(svc.open("赣01-变拖0011", null).isSuccess(), "活卡抽走，允许重新起卡");

        Long doneId = finishOneCard("赣01-变拖0012");
        assertFalse(svc.remove(doneId));
        assertNotNull(svc.selectTAmssOutCardById(doneId));
    }

    /** 规矩十：年终报数——已了结栏 + 走到半路栏 = 当年起的卡数 */
    @Test
    void yearlyReport_twoColumnsSumToOpened() {
        ITAmssOutCardService svc = newService();
        int year = Calendar.getInstance().get(Calendar.YEAR);

        svc.open("赣01-变拖0013", null);
        Long b = svc.open("赣01-变拖0014", null).getCard().getId();
        Long c = svc.open("赣01-变拖0015", null).getCard().getId();
        svc.advance(b, collectData(ZHANG));
        finishOneCard("赣01-变拖0016");

        OutCardYearReport rpt = svc.yearlyReport(year);
        assertEquals(4, rpt.getOpenedCount());
        assertEquals(1, rpt.getFinishedCount(), "向外报的数只算了结的");
        assertEquals(3, rpt.getActiveCount(), "走到半路的单列一栏");
        assertTrue(rpt.isBalanced(), "两栏合起来等于当年起的卡数");
    }

    /** 规矩十一：当前段由系统照履历推——卡面字段被人手篡改也不作数 */
    @Test
    void currentStage_derivedFromHistory_notCardFace() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0017", null).getCard().getId();
        svc.advance(id, collectData(ZHANG));
        svc.advance(id, inspectData());

        TAmssOutCardMapper cm = sqlSession.getMapper(TAmssOutCardMapper.class);
        TAmssOutCard tampered = cm.selectById(id);
        tampered.setStage(99); // 页面上/人手硬摆一个段号
        cm.updateById(tampered);

        assertEquals(2, svc.currentStage(id), "系统只认真履历：验车已过，现压清账");
    }

    /** 规矩十二：走完的卡从了结往回捋，四段笔笔在档、段号 0..3 齐整 */
    @Test
    void history_fullChainFromFinishBackToCollect() {
        ITAmssOutCardService svc = newService();
        Long id = finishOneCard("赣01-变拖0018");
        List<TAmssOutCardStage> passes = livePass(
                sqlSession.getMapper(TAmssOutCardStageMapper.class), id);
        assertEquals(4, passes.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i, passes.get(i).getStageNo().intValue());
            assertEquals(Integer.valueOf(1), passes.get(i).getCurrentFlag());
        }
        // 验车笔带着三样，清账笔带着缘由
        assertEquals(Integer.valueOf(2), passes.get(1).getPlateCount());
        assertEquals("SCRAP", passes.get(2).getReturnReason());
    }

    /** 规矩十三：老方法签名（id, remark）继续可用，委托新事务线 */
    @Test
    void legacyOverloads_stillWork() {
        ITAmssOutCardService svc = newService();
        Long id = svc.open("赣01-变拖0019", null).getCard().getId();
        TAmssOutCard moved = svc.advance(id, "老调用：收牌");
        assertNotNull(moved);
        assertEquals(1, moved.getStage().intValue());
        TAmssOutCard back = svc.rollback(id, "老调用：退回");
        assertNotNull(back);
        assertEquals(0, back.getStage().intValue());
    }

    /** 规矩十四：过口是一笔首尾齐全的事务——中途炸了，卡面与履历一句都不留 */
    @Test
    void advance_transactional_noHalfStrokeLeft() {
        // 走到验车段，再令其在卡面更新之后、返回前抛错：全事务须回滚
        com.fc.v2.service.impl.TAmssOutCardServiceImpl raw =
                new com.fc.v2.service.impl.TAmssOutCardServiceImpl() {
                    @Override
                    public com.fc.v2.model.custom.OutCardActionResult advance(
                            Long id, OutCardStageData data) {
                        com.fc.v2.model.custom.OutCardActionResult r = super.advance(id, data);
                        if (r.isSuccess() && data.getPlateCount() != null) {
                            throw new IllegalStateException("模拟库外故障");
                        }
                        return r;
                    }
                };
        ITAmssOutCardService svc = wrap(raw);
        Long id = svc.open("赣01-变拖0020", null).getCard().getId();
        svc.advance(id, collectData(ZHANG));
        int before = svc.history(id).size();

        assertThrows(IllegalStateException.class, () -> svc.advance(id, inspectData()));

        assertEquals(before, svc.history(id).size(), "炸掉的验车笔不得半留");
        assertEquals(1, svc.currentStage(id), "卡仍坐在验车段，当没过过");
        TAmssOutCard card = svc.selectTAmssOutCardById(id);
        assertEquals(1, card.getStage().intValue(), "卡面也回滚到验车");
    }

    private OutCardStageData collectData(String operator) {
        OutCardStageData d = new OutCardStageData();
        d.setOperator(operator);
        return d;
    }

    /** 一口气把一张卡走到了结钉死 */
    private Long finishOneCard(String plate) {
        ITAmssOutCardService svc = newService();
        Long id = svc.open(plate, null).getCard().getId();
        assertTrue(svc.advance(id, collectData(ZHANG)).isSuccess());
        assertTrue(svc.advance(id, inspectData()).isSuccess());
        assertTrue(svc.addReason(id, OutCardStageData.REASON_SCRAP, ZHANG).isSuccess());
        assertTrue(svc.advance(id, collectData(ZHANG)).isSuccess(), "过清账，坐进了结");
        OutCardActionResult fin = svc.advance(id, collectData(ZHANG));
        assertTrue(fin.isSuccess(), fin.getMessage());
        return id;
    }
}
