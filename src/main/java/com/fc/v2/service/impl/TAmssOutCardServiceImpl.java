package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fc.v2.mapper.auto.TAmssOutCardMapper;
import com.fc.v2.mapper.auto.TAmssOutCardStageMapper;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutCardStage;
import com.fc.v2.model.custom.OutCardActionResult;
import com.fc.v2.model.custom.OutCardStageData;
import com.fc.v2.model.custom.OutCardYearReport;
import com.fc.v2.service.ITAmssOutCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;

/**
 * 变型拖拉机牌证缴回事务卡 Service业务层处理（state-machine 形状：单据流转）。
 *
 * <p>核心口径：
 * <ul>
 *   <li>一张卡四段：收牌(0)→验车(1)→清账(2)→了结(3)，过段只有
 *       {@link #advance} / {@link #rollback} 两个出口；</li>
 *   <li>当前段不信卡面字段，照缴回履历中已过的明面段推得；</li>
 *   <li>履历只追加：退回只把旧说法沉底（current_flag 置 0），绝不删改；</li>
 *   <li>了结即钉死：添不进新段、改不了旧句、整张抽不走；</li>
 *   <li>一车只许一张活卡，起第二张当场挡回并报明压在何段。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssOutCardServiceImpl implements ITAmssOutCardService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    private static final int FLAG_YES = 1;
    private static final int FLAG_NO = 0;

    private static final ObjectMapper JSON = new ObjectMapper();

    @javax.annotation.Resource
    private TAmssOutCardMapper amssOutCardMapper;

    @javax.annotation.Resource
    private TAmssOutCardStageMapper amssOutCardStageMapper;

    @Override
    public TAmssOutCard selectTAmssOutCardById(Long id) {
        TAmssOutCard card = this.amssOutCardMapper.selectById(id);
        return card != null && Integer.valueOf(FLAG_NO).equals(card.getDelFlag()) ? card : null;
    }

    @Override
    public List<TAmssOutCard> selectTAmssOutCardList(QueryWrapper<TAmssOutCard> queryWrapper) {
        return this.amssOutCardMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutCardActionResult open(String vehiclePlateNo, String bizNo) {
        if (StringUtils.isEmpty(vehiclePlateNo)) {
            return OutCardActionResult.reject("车辆号牌为空，认不出是哪台车，不能起卡").build();
        }
        String plate = vehiclePlateNo.trim();

        TAmssOutCard active = findActiveCard(plate);
        if (active != null) {
            String stageName = STAGE_NAMES[currentStage(active.getId())];
            return OutCardActionResult
                    .reject("号牌 " + plate + " 已压着一张活卡（卡号 " + active.getBizNo()
                            + "），现停在【" + stageName + "】段；一台车只许一张活卡，第二张当场挡回")
                    .withCard(active)
                    .build();
        }

        TAmssOutCard card = new TAmssOutCard();
        card.setBizNo(StringUtils.isNotEmpty(bizNo) ? bizNo.trim() : generateBizNo());
        card.setVehiclePlateNo(plate);
        card.setStage(STAGE_COLLECT);
        card.setStatus(STATUS_ACTIVE);
        card.setDelFlag(FLAG_NO);
        card.setLastAction("起卡");
        try {
            this.amssOutCardMapper.insert(card);
        } catch (DataIntegrityViolationException e) {
            // 并发起卡撞活卡唯一约束：谁先起成算谁的
            TAmssOutCard winner = findActiveCard(plate);
            if (winner != null) {
                String stageName = STAGE_NAMES[currentStage(winner.getId())];
                return OutCardActionResult
                        .reject("号牌 " + plate + " 已压着一张活卡（卡号 " + winner.getBizNo()
                                + "），现停在【" + stageName + "】段；一台车只许一张活卡，第二张当场挡回")
                        .withCard(winner)
                        .build();
            }
            throw e;
        }
        return OutCardActionResult.ok(card, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutCardActionResult advance(Long id, OutCardStageData data) {
        TAmssOutCard card = selectTAmssOutCardById(id);
        if (card == null) {
            return OutCardActionResult.reject("卡不存在或已抽走").build();
        }
        if (Integer.valueOf(STATUS_TERMINAL).equals(card.getStatus())) {
            return OutCardActionResult.reject("此卡已了结钉死：添不进新段，就地改不了旧句").withCard(card).build();
        }
        OutCardStageData input = data == null ? new OutCardStageData() : data;
        String operator = resolveOperator(input.getOperator());

        List<TAmssOutCardStage> lives = livePassStages(id);
        TAmssOutCardStage head = lives.isEmpty() ? null : lives.get(lives.size() - 1);
        // 当前段照履历推：已落明面PASS的最后一段+1；不信卡面字段、不信页面摆的
        int current = head == null ? STAGE_COLLECT : head.getStageNo() + 1;
        if (current > STAGE_FINISH) {
            return OutCardActionResult.reject("四段皆已走完，无路可往前").withCard(card).build();
        }

        String gate = checkStageGate(id, current, input);
        if (gate != null) {
            return OutCardActionResult.reject(gate).withCard(card).build();
        }

        TAmssOutCardStage stroke = newStroke(card.getId(), current, ACTION_PASS, operator);
        stroke.setPlateCount(current == STAGE_INSPECT ? input.getPlateCount() : null);
        stroke.setLicenseStatus(current == STAGE_INSPECT ? trimToNull(input.getLicenseStatus()) : null);
        stroke.setOpenCaseFlag(current == STAGE_INSPECT ? input.getOpenCaseFlag() : null);
        // 清账过段笔快照当时明面那笔缘由；缘由改动全史在 REASON 笔上，一笔不丢
        stroke.setReturnReason(current == STAGE_SETTLE ? liveReason(id) : null);
        stroke.setStageData(toJson(input));

        try {
            this.amssOutCardStageMapper.insert(stroke);
        } catch (DataIntegrityViolationException e) {
            // 并发双击同一段：明面唯一约束拦下，以先落成的那笔为准
            TAmssOutCardStage first = findCurrentPass(id, current);
            OutCardActionResult r = OutCardActionResult.ok(reload(card), first);
            r.setMessage("【" + STAGE_NAMES[current] + "】段已有明面一笔，以头一笔为准");
            return r;
        }

        // 这笔落的是刚走完的 current 段；落完坐到下一段，了结段落完则钉死在【了结】
        if (current == STAGE_FINISH) {
            card.setStage(STAGE_FINISH);
            card.setStatus(STATUS_TERMINAL);
            card.setLastAction("了结钉死");
        } else {
            card.setStage(current + 1);
            card.setStatus(STATUS_ACTIVE);
            card.setLastAction("过段→" + STAGE_NAMES[current + 1]);
        }
        this.amssOutCardMapper.updateById(card);
        return OutCardActionResult.ok(reload(card), stroke);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutCardActionResult rollback(Long id, OutCardStageData data) {
        TAmssOutCard card = selectTAmssOutCardById(id);
        if (card == null) {
            return OutCardActionResult.reject("卡不存在或已抽走").build();
        }
        if (Integer.valueOf(STATUS_TERMINAL).equals(card.getStatus())) {
            return OutCardActionResult.reject("此卡已了结钉死，退不回去").withCard(card).build();
        }
        OutCardStageData input = data == null ? new OutCardStageData() : data;
        String operator = resolveOperator(input.getOperator());

        List<TAmssOutCardStage> lives = livePassStages(id);
        if (lives.isEmpty()) {
            return OutCardActionResult.reject("卡还压在头一段【收牌】，前面没有段可退").withCard(card).build();
        }
        TAmssOutCardStage head = lives.get(lives.size() - 1);
        int backTo = head.getStageNo();

        // 退回只有一处出口：被退段（backTo）先前明面说法沉底，原样留档，绝不删改。
        // 缘由笔归清账段自己管（改一次续一笔），退回别的段不动它。
        head.setCurrentFlag(FLAG_NO);
        this.amssOutCardStageMapper.updateById(head);

        TAmssOutCardStage stroke = newStroke(card.getId(), backTo, ACTION_ROLLBACK, operator);
        stroke.setCurrentFlag(FLAG_NO);
        stroke.setStageData(input.getRemark());
        this.amssOutCardStageMapper.insert(stroke);

        card.setStage(backTo);
        card.setStatus(STATUS_ACTIVE);
        card.setLastAction("退回→" + STAGE_NAMES[backTo]);
        this.amssOutCardMapper.updateById(card);
        return OutCardActionResult.ok(reload(card), stroke);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutCardActionResult addReason(Long id, String returnReason, String operator) {
        TAmssOutCard card = selectTAmssOutCardById(id);
        if (card == null) {
            return OutCardActionResult.reject("卡不存在或已抽走").build();
        }
        if (Integer.valueOf(STATUS_TERMINAL).equals(card.getStatus())) {
            return OutCardActionResult.reject("此卡已了结钉死，缘由也续不进去").withCard(card).build();
        }
        int current = currentStage(id);
        if (current != STAGE_SETTLE) {
            return OutCardActionResult.reject("缴回缘由归【清账】段记，此卡现压在【" + STAGE_NAMES[current]
                    + "】段，记不进去").withCard(card).build();
        }
        String reason = trimToNull(returnReason);
        if (!isValidReason(reason)) {
            return OutCardActionResult.reject("缴回缘由须四选一：SCRAP报废、LOST灭失、OUT_PROV转外省、VOLUNTARY主动交回")
                    .withCard(card).build();
        }
        String op = resolveOperator(operator);

        // 改一次续一笔：先前缘由笔原样留档，只是从明面沉下去
        this.amssOutCardStageMapper.update(null, new UpdateWrapper<TAmssOutCardStage>()
                .eq("card_id", id)
                .eq("stage_no", STAGE_SETTLE)
                .eq("action_type", ACTION_REASON)
                .eq("current_flag", FLAG_YES)
                .set("current_flag", FLAG_NO));

        TAmssOutCardStage stroke = newStroke(card.getId(), STAGE_SETTLE, ACTION_REASON, op);
        stroke.setCurrentFlag(FLAG_YES);
        stroke.setReturnReason(reason);
        this.amssOutCardStageMapper.insert(stroke);

        card.setLastAction("缘由补记→" + reason);
        this.amssOutCardMapper.updateById(card);
        return OutCardActionResult.ok(reload(card), stroke);
    }

    @Override
    public int currentStage(Long id) {
        List<TAmssOutCardStage> lives = livePassStages(id);
        if (lives.isEmpty()) {
            return STAGE_COLLECT;
        }
        int last = lives.get(lives.size() - 1).getStageNo();
        // 了结笔已落：卡就停在【了结】（钉死），不再往后推
        return last >= STAGE_FINISH ? STAGE_FINISH : last + 1;
    }

    @Override
    public List<TAmssOutCardStage> history(Long id) {
        if (id == null) {
            return Collections.emptyList();
        }
        List<TAmssOutCardStage> all = this.amssOutCardStageMapper.selectList(
                new QueryWrapper<TAmssOutCardStage>().eq("card_id", id));
        // 按落下时辰排，同时辰按落档先后
        all.sort(Comparator.comparing(TAmssOutCardStage::getHappenTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TAmssOutCardStage::getId));
        return all;
    }

    @Override
    public OutCardYearReport yearlyReport(int year) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date yearStart = cal.getTime();
        cal.set(year + 1, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date nextYearStart = cal.getTime();

        OutCardYearReport report = new OutCardYearReport();
        report.setYear(year);
        report.setOpenedCount(countCards(yearStart, nextYearStart, null));
        report.setFinishedCount(countCards(yearStart, nextYearStart, STATUS_TERMINAL));
        report.setActiveCount(countCards(yearStart, nextYearStart, STATUS_ACTIVE));
        return report;
    }

    @Override
    @Deprecated
    public TAmssOutCard advance(Long id, String remark) {
        OutCardStageData data = new OutCardStageData();
        data.setRemark(remark);
        OutCardActionResult r = advance(id, data);
        return r.isSuccess() && r.getCard() != null ? r.getCard() : null;
    }

    @Override
    @Deprecated
    public TAmssOutCard rollback(Long id, String remark) {
        OutCardStageData data = new OutCardStageData();
        data.setRemark(remark);
        OutCardActionResult r = rollback(id, data);
        return r.isSuccess() && r.getCard() != null ? r.getCard() : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContent(Long id, String remark) {
        TAmssOutCard card = this.amssOutCardMapper.selectById(id);
        if (card == null || Integer.valueOf(FLAG_YES).equals(card.getDelFlag())) {
            return false;
        }
        // 走完的那一张只许翻不许碰
        if (Integer.valueOf(STATUS_TERMINAL).equals(card.getStatus())) {
            return false;
        }
        card.setContent(remark);
        return this.amssOutCardMapper.updateById(card) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        TAmssOutCard card = this.amssOutCardMapper.selectById(id);
        if (card == null || Integer.valueOf(FLAG_YES).equals(card.getDelFlag())) {
            return false;
        }
        // 了结钉死：整张抽不走；只有没走完的活卡才谈得上动手（软抽，履历原样留档）
        if (Integer.valueOf(STATUS_TERMINAL).equals(card.getStatus())) {
            return false;
        }
        card.setDelFlag(FLAG_YES);
        return this.amssOutCardMapper.updateById(card) > 0;
    }

    // ------------------------------------------------------------------

    private TAmssOutCard findActiveCard(String plate) {
        List<TAmssOutCard> list = this.amssOutCardMapper.selectList(new QueryWrapper<TAmssOutCard>()
                .eq("vehicle_plate_no", plate)
                .eq("status", STATUS_ACTIVE)
                .eq("del_flag", FLAG_NO));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 卡上各段明面通过笔，按段号排死 */
    private List<TAmssOutCardStage> livePassStages(Long cardId) {
        return this.amssOutCardStageMapper.selectList(new QueryWrapper<TAmssOutCardStage>()
                .eq("card_id", cardId)
                .eq("action_type", ACTION_PASS)
                .eq("current_flag", FLAG_YES)
                .orderByAsc("stage_no"));
    }

    /** 该段明面通过笔（同一段第二遍由库上明面唯一约束硬顶，这里仅用于冲突后取回先落那笔） */
    private TAmssOutCardStage findCurrentPass(Long cardId, int stageNo) {
        return this.amssOutCardStageMapper.selectOne(new QueryWrapper<TAmssOutCardStage>()
                .eq("card_id", cardId)
                .eq("stage_no", stageNo)
                .eq("action_type", ACTION_PASS)
                .eq("current_flag", FLAG_YES));
    }

    /** 段门禁：返回 null 放行，否则为挡回的话 */
    private String checkStageGate(Long cardId, int current, OutCardStageData input) {
        if (current == STAGE_INSPECT) {
            StringBuilder missing = new StringBuilder();
            if (input.getPlateCount() == null || input.getPlateCount() < 0) {
                missing.append("号牌实收枚数、");
            }
            if (StringUtils.isEmpty(input.getLicenseStatus())) {
                missing.append("行驶证与检验在册情况、");
            }
            if (input.getOpenCaseFlag() == null
                    || (input.getOpenCaseFlag() != FLAG_NO && input.getOpenCaseFlag() != FLAG_YES)) {
                missing.append("名下有无未结事故认定和罚款、");
            }
            if (missing.length() > 0) {
                return "【验车】段三样没齐（" + missing.substring(0, missing.length() - 1)
                        + "），进不了下一段";
            }
        } else if (current == STAGE_SETTLE) {
            // 缘由在清账段另用 addReason 记（改一次续一笔）；没记过缘由不许过段
            String reason = liveReason(cardId);
            if (!isValidReason(reason)) {
                return "【清账】段须先认准缴回缘由（SCRAP报废/LOST灭失/OUT_PROV转外省/VOLUNTARY主动交回），"
                        + "缘由不落笔进不了下一段";
            }
        }
        return null;
    }

    /** 清账段当前明面那笔缘由（无则 null） */
    private String liveReason(Long cardId) {
        TAmssOutCardStage r = this.amssOutCardStageMapper.selectOne(new QueryWrapper<TAmssOutCardStage>()
                .eq("card_id", cardId)
                .eq("stage_no", STAGE_SETTLE)
                .eq("action_type", ACTION_REASON)
                .eq("current_flag", FLAG_YES));
        return r == null ? null : r.getReturnReason();
    }

    private boolean isValidReason(String reason) {
        return OutCardStageData.REASON_SCRAP.equals(reason)
                || OutCardStageData.REASON_LOST.equals(reason)
                || OutCardStageData.REASON_OUT_PROV.equals(reason)
                || OutCardStageData.REASON_VOLUNTARY.equals(reason);
    }

    private TAmssOutCardStage newStroke(Long cardId, int stageNo, String actionType, String operator) {
        Date now = new Date();
        TAmssOutCardStage stroke = new TAmssOutCardStage();
        stroke.setCardId(cardId);
        stroke.setStageNo(stageNo);
        stroke.setRevNo(this.amssOutCardStageMapper.selectCount(new QueryWrapper<TAmssOutCardStage>()
                .eq("card_id", cardId)
                .eq("stage_no", stageNo)
                .eq("action_type", actionType)) + 1);
        stroke.setCurrentFlag(FLAG_YES);
        stroke.setActionType(actionType);
        stroke.setOperator(operator);
        stroke.setHappenTime(now);
        stroke.setCreateTime(now);
        return stroke;
    }

    private long countCards(Date yearStart, Date nextYearStart, Integer status) {
        QueryWrapper<TAmssOutCard> qw = new QueryWrapper<TAmssOutCard>()
                .eq("del_flag", FLAG_NO)
                .ge("create_time", yearStart)
                .lt("create_time", nextYearStart);
        if (status != null) {
            qw.eq("status", status);
        }
        Integer cnt = this.amssOutCardMapper.selectCount(qw);
        return cnt == null ? 0 : cnt.longValue();
    }

    private String resolveOperator(String operator) {
        String op = trimToNull(operator);
        if (op != null) {
            return op;
        }
        try {
            return ShiroUtils.getLoginName();
        } catch (Exception e) {
            return "Task";
        }
    }

    private TAmssOutCard reload(TAmssOutCard card) {
        TAmssOutCard fresh = this.amssOutCardMapper.selectById(card.getId());
        return fresh == null ? card : fresh;
    }

    private String generateBizNo() {
        return "JH" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String toJson(OutCardStageData input) {
        try {
            return JSON.writeValueAsString(input);
        } catch (Exception e) {
            return null;
        }
    }
}
