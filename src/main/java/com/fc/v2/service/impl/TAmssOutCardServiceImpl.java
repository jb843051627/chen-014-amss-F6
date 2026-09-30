package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.fc.v2.service.ITAmssOutCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.DateUtils;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;

/**
 * 变型拖拉机牌证缴回事务卡 Service业务层处理。
 *
 * 一张卡四段（收牌/验车/清账/了结），卡停在哪段就只放哪段的流程；
 * 往前与退回各只有 advance/rollback 一对出口，没有现造卡、就地改句的旁路。
 * 段位不由卡面、不由经办人说了算，一律照履历线“已经过了几段”重放推出。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TAmssOutCardServiceImpl implements ITAmssOutCardService {

    @javax.annotation.Resource
    private TAmssOutCardMapper amssOutCardMapper;

    @javax.annotation.Resource
    private TAmssOutLineMapper amssOutLineMapper;

    // ---------------------------------------------------------------- 查（只翻不碰）

    @Override
    public TAmssOutCard selectTAmssOutCardById(Long id) {
        return this.amssOutCardMapper.selectById(id);
    }

    @Override
    public List<TAmssOutCard> selectTAmssOutCardList(QueryWrapper<TAmssOutCard> queryWrapper) {
        return this.amssOutCardMapper.selectList(queryWrapper);
    }

    @Override
    public List<TAmssOutLine> listLines(Long id) {
        return this.amssOutLineMapper.selectList(new QueryWrapper<TAmssOutLine>()
                .eq("card_id", id)
                .eq("del_flag", 0)
                .orderByAsc("act_time", "id"));
    }

    // ---------------------------------------------------------------- 起卡

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TAmssOutCard open(String vehNo) {
        if (StringUtils.isEmpty(vehNo)) {
            throw new ServiceException("车号不能为空，起不了卡");
        }
        String v = vehNo.trim();
        TAmssOutCard alive = findActiveCard(v);
        if (alive != null) {
            throw new ServiceException("车 " + v + " 已压着一张活卡 " + alive.getBizNo()
                    + "，此刻停在" + OutCardConsts.stageName(deriveStage(alive.getId()))
                    + "段；一台车同时只许一张活卡，第二张当场挡回");
        }
        TAmssOutCard card = new TAmssOutCard();
        card.setBizNo("JH" + DateUtils.dateTimeNow() + SnowflakeIdWorker.getUUID());
        card.setVehNo(v);
        card.setStage(OutCardConsts.STAGE_COLLECT);
        card.setStatus(OutCardConsts.STATUS_ACTIVE);
        card.setLastAction("起卡");
        card.setDelFlag(0);
        try {
            this.amssOutCardMapper.insert(card);
        } catch (DuplicateKeyException e) {
            // 并发起卡：活车号唯一索引兜底，再查一次把“压在哪段”说清楚
            TAmssOutCard racer = findActiveCard(v);
            if (racer != null) {
                throw new ServiceException("车 " + v + " 已压着一张活卡 " + racer.getBizNo()
                        + "，此刻停在" + OutCardConsts.stageName(deriveStage(racer.getId())) + "段");
            }
            throw e;
        }
        return card;
    }

    @Override
    public OutCardOpenResultVo openBatch(Collection<String> vehNos) {
        OutCardOpenResultVo result = new OutCardOpenResultVo();
        if (vehNos == null) {
            return result;
        }
        for (String vehNo : vehNos) {
            if (StringUtils.isEmpty(vehNo)) {
                result.getRejected().add(new OutCardOpenResultVo.Rejected(vehNo, "车号为空，未起卡"));
                continue;
            }
            try {
                result.getOpened().add(open(vehNo.trim()));
            } catch (ServiceException e) {
                result.getRejected().add(new OutCardOpenResultVo.Rejected(vehNo.trim(), e.getMessage()));
            }
        }
        return result;
    }

    private TAmssOutCard findActiveCard(String vehNo) {
        return this.amssOutCardMapper.selectOne(new QueryWrapper<TAmssOutCard>()
                .eq("veh_no", vehNo)
                .eq("status", OutCardConsts.STATUS_ACTIVE)
                .eq("del_flag", 0)
                .last("LIMIT 1"));
    }

    // ---------------------------------------------------------------- 往前（唯一向前出口）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssOutCard advance(Long id, String remark) {
        // 老用法：不带料。只在不补料的段口（清账→了结、了结笔）走得通。
        return doAdvance(id, null, remark, currentActor(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssOutCard advance(Long id, OutCardAdvanceBo bo, String remark) {
        if (bo == null) {
            throw new ServiceException("本段过口要交的东西没递进来");
        }
        return doAdvance(id, bo, remark, currentActor(), true);
    }

    private TAmssOutCard doAdvance(Long id, OutCardAdvanceBo bo, String remark, String actor, boolean withMaterials) {
        TAmssOutCard card = lockCard(id);
        if (card == null) {
            return null;
        }
        if (OutCardConsts.STATUS_CLOSED == nz(card.getStatus())) {
            return rejectOrNull(withMaterials,
                    "卡 " + card.getBizNo() + " 已了结钉死：添不进新段、改不了旧句，只许翻不许碰");
        }

        List<TAmssOutLine> lines = loadLines(id);
        int s = replayStage(lines);

        // 带的料只属于验车段（号牌实收几枚、行驶证与检验在册、未结认定罚款三样）。
        // 收牌段不挂料；料递向早先已过的段：同一人把同一段再过一遍的，
        // 只落头一遍——第二遍不添新句、不动头一句；换人则谁也够不着已过的段。
        Integer payloadStage = bo == null ? null : payloadStage(bo);
        if (payloadStage != null) {
            if (payloadStage < s) {
                TAmssOutLine first = surfaceForward(lines, payloadStage);
                if (first != null && actor.equals(first.getActor())) {
                    return card;
                }
                throw new ServiceException("卡 " + card.getBizNo() + " 此刻压在"
                        + OutCardConsts.stageName(s) + "段，" + OutCardConsts.stageName(payloadStage)
                        + "段早过了，那只手够不着这一段；要改旧说法只能由清账段退回重来");
            }
            if (payloadStage > s) {
                throw new ServiceException("卡 " + card.getBizNo() + " 此刻压在"
                        + OutCardConsts.stageName(s) + "段，" + OutCardConsts.stageName(payloadStage)
                        + "段还没轮到，后头的段不能提前结");
            }
        }

        // 段口各自的门禁
        if (s == OutCardConsts.STAGE_CHECK) {
            if (bo == null) {
                // 老用法没递料，按老契约被拒返回 null
                return null;
            }
            if (bo.getPlateQty() == null || bo.getPlateQty() < 0) {
                throw new ServiceException("验车段料不齐：号牌实收枚数还没数");
            }
            if (StringUtils.isEmpty(bo.getLicenseStatus())) {
                throw new ServiceException("验车段料不齐：行驶证与检验在册情况没填");
            }
            if (bo.getClaimClear() == null) {
                throw new ServiceException("验车段料不齐：名下有无未结事故认定和罚款没核");
            }
            if (OutCardConsts.CLAIM_UNCLEAR == bo.getClaimClear()) {
                throw new ServiceException("名下尚有未结的事故认定或罚款，这一段结不了，进不了清账");
            }
        } else if (s == OutCardConsts.STAGE_SETTLE) {
            if (countReasons(lines) < 1) {
                String msg = "清账段还没记一笔缴回缘由（报废/灭失/转外省/主动交回），结不了";
                return withMaterials ? rejectOrNull(true, msg) : null;
            }
        }

        int verNo = nextFrVersion(lines, s) + 1;
        TAmssOutLine line = new TAmssOutLine();
        line.setCardId(card.getId());
        line.setStage(s);
        line.setAction(OutCardConsts.ACTION_FORWARD);
        line.setVerNo(verNo);
        line.setActor(actor);
        line.setActTime(new Date());
        line.setDelFlag(0);
        if (s == OutCardConsts.STAGE_CHECK && bo != null) {
            // 三样只挂在验车段的说法上
            line.setPlateQty(bo.getPlateQty());
            line.setLicenseStatus(StringUtils.isEmpty(bo.getLicenseStatus()) ? null : bo.getLicenseStatus().trim());
            line.setClaimClear(bo.getClaimClear());
        }
        line.setRemark(StringUtils.isEmpty(remark) ? OutCardConsts.stageName(s) + "段过口" : remark);
        this.amssOutLineMapper.insert(line);

        card.setStage(s + 1);
        card.setLastAction("过口:" + OutCardConsts.stageName(s));
        if (s == OutCardConsts.STAGE_FINISH) {
            // 了结这一笔落下去，卡即钉死
            card.setStatus(OutCardConsts.STATUS_CLOSED);
            card.setStage(OutCardConsts.STAGE_FINISH);
            card.setLastAction("了结");
        }
        this.amssOutCardMapper.updateById(card);
        return card;
    }

    // ---------------------------------------------------------------- 退回（唯一向后出口）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssOutCard rollback(Long id, String remark) {
        return doRollback(id, null, remark, currentActor(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssOutCard rollback(Long id, Integer actualPlateQty, String remark) {
        if (actualPlateQty == null || actualPlateQty < 0) {
            throw new ServiceException("退回要带当场复核的号牌实收枚数");
        }
        return doRollback(id, actualPlateQty, remark, currentActor(), true);
    }

    private TAmssOutCard doRollback(Long id, Integer actualPlateQty, String remark, String actor, boolean strict) {
        TAmssOutCard card = lockCard(id);
        if (card == null) {
            return null;
        }
        if (OutCardConsts.STATUS_CLOSED == nz(card.getStatus())) {
            return rejectOrNull(strict, "卡 " + card.getBizNo() + " 已了结钉死，退不回去，只许翻不许碰");
        }
        List<TAmssOutLine> lines = loadLines(id);
        int s = replayStage(lines);
        if (s != OutCardConsts.STAGE_SETTLE) {
            return rejectOrNull(strict, "只许在清账段看出不合、退回验车段重来；卡此刻压在"
                    + OutCardConsts.stageName(s) + "段，没有这处退回口");
        }
        TAmssOutLine checkSay = surfaceForward(lines, OutCardConsts.STAGE_CHECK);
        int recordedQty = checkSay == null || checkSay.getPlateQty() == null ? 0 : checkSay.getPlateQty();
        if (actualPlateQty != null && actualPlateQty.intValue() == recordedQty) {
            return rejectOrNull(strict, "验车记的枚数（" + recordedQty + "枚）与当场实收（"
                    + actualPlateQty + "枚）相合，无须退回");
        }

        // 退回归到验车段：开一条退回笔把旧说法沉下去，验车重来时另开新版摆到明面
        int verNo = nextFrVersion(lines, OutCardConsts.STAGE_CHECK) + 1;
        TAmssOutLine line = new TAmssOutLine();
        line.setCardId(card.getId());
        line.setStage(OutCardConsts.STAGE_CHECK);
        line.setAction(OutCardConsts.ACTION_ROLLBACK);
        line.setVerNo(verNo);
        line.setActor(actor);
        line.setActTime(new Date());
        line.setDelFlag(0);
        line.setRemark("清账段复核实收" + (actualPlateQty == null ? "?" : actualPlateQty)
                + "枚，验车记" + recordedQty + "枚，两处不合，退回验车重来；旧说法第"
                + (checkSay == null ? "?" : checkSay.getVerNo()) + "版沉底"
                + (StringUtils.isEmpty(remark) ? "" : "；" + remark));
        this.amssOutLineMapper.insert(line);

        card.setStage(OutCardConsts.STAGE_CHECK);
        card.setLastAction("退回:验车");
        this.amssOutCardMapper.updateById(card);
        return card;
    }

    // ---------------------------------------------------------------- 清账缘由（改一次续一笔）

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssOutLine addReason(Long id, String reasonCode, String remark) {
        TAmssOutCard card = lockCard(id);
        if (card == null) {
            throw new ServiceException("卡不存在，缘由无处可记");
        }
        if (OutCardConsts.STATUS_CLOSED == nz(card.getStatus())) {
            throw new ServiceException("卡 " + card.getBizNo() + " 已了结钉死，缘由也添不进去，只许翻不许碰");
        }
        String code = reasonCode == null ? null : reasonCode.trim();
        if (!OutCardConsts.REASON_SCRAP.equals(code) && !OutCardConsts.REASON_LOST.equals(code)
                && !OutCardConsts.REASON_TRANSFER_OUT.equals(code)
                && !OutCardConsts.REASON_VOLUNTARY.equals(code)) {
            throw new ServiceException("缴回缘由只认四种：1报废 2灭失 3转外省 4主动交回");
        }
        List<TAmssOutLine> lines = loadLines(id);
        if (replayStage(lines) != OutCardConsts.STAGE_SETTLE) {
            throw new ServiceException("缘由归清账段一笔：卡此刻不在清账段，这笔记不进去");
        }
        int no = countReasons(lines) + 1;
        TAmssOutLine line = new TAmssOutLine();
        line.setCardId(card.getId());
        line.setStage(OutCardConsts.STAGE_SETTLE);
        line.setAction(OutCardConsts.ACTION_REASON);
        line.setVerNo(no);
        line.setReturnReason(code);
        line.setActor(currentActor());
        line.setActTime(new Date());
        line.setDelFlag(0);
        String base = "第" + no + "笔记缘由：" + OutCardConsts.reasonLabel(code);
        line.setRemark(StringUtils.isEmpty(remark) ? base : base + "；" + remark);
        this.amssOutLineMapper.insert(line);

        card.setLastAction("缘由:" + OutCardConsts.reasonLabel(code));
        this.amssOutCardMapper.updateById(card);
        return line;
    }

    // ---------------------------------------------------------------- 抽卡核验（缴回履历）

    @Override
    public OutCardAuditVo audit(Long id) {
        TAmssOutCard card = this.amssOutCardMapper.selectById(id);
        OutCardAuditVo vo = new OutCardAuditVo();
        if (card == null) {
            vo.getProblems().add("卡不存在");
            vo.setPassed(false);
            return vo;
        }
        vo.setCardId(card.getId());
        vo.setBizNo(card.getBizNo());
        List<TAmssOutLine> lines = listLines(id);
        vo.setLines(lines);
        List<String> problems = replayStrict(card, lines);
        vo.setDerivedStage(replayStage(lines));
        vo.setClosed(OutCardConsts.STATUS_CLOSED == nz(card.getStatus()));
        vo.setProblems(problems);
        vo.setPassed(problems.isEmpty());
        return vo;
    }

    // ---------------------------------------------------------------- 年终口径

    @Override
    public OutCardReportVo yearReport(int year) {
        OutCardReportVo vo = new OutCardReportVo();
        vo.setYear(year);
        Integer opened = this.amssOutCardMapper.selectCount(new QueryWrapper<TAmssOutCard>()
                .eq("del_flag", 0)
                .apply("YEAR(create_time) = {0}", year));
        Integer closed = this.amssOutCardMapper.selectCount(new QueryWrapper<TAmssOutCard>()
                .eq("del_flag", 0)
                .eq("status", OutCardConsts.STATUS_CLOSED)
                .apply("YEAR(create_time) = {0}", year));
        Integer underway = this.amssOutCardMapper.selectCount(new QueryWrapper<TAmssOutCard>()
                .eq("del_flag", 0)
                .eq("status", OutCardConsts.STATUS_ACTIVE)
                .apply("YEAR(create_time) = {0}", year));
        long openCnt = opened == null ? 0 : opened.longValue();
        long closedCnt = closed == null ? 0 : closed.longValue();
        long underwayCnt = underway == null ? 0 : underway.longValue();
        vo.setOpened(openCnt);
        vo.setClosed(closedCnt);
        vo.setUnderway(underwayCnt);
        vo.setBalanced(openCnt == closedCnt + underwayCnt);
        if (!vo.isBalanced()) {
            vo.getProblems().add("两栏合不上当年起卡数：已了结" + closedCnt + "＋在办"
                    + underwayCnt + "≠起卡" + openCnt);
        }
        return vo;
    }

    // ---------------------------------------------------------------- 履历重放（权威段位）

    /**
     * 照履历线重放：过了几段就停在几段。只认线，不看卡面 stage、不看经办人怎么认。
     * 返回卡当下压着的段 0..3；四条向前都过齐返回 3 且由调用方结合了结笔认钉死。
     */
    private int replayStage(List<TAmssOutLine> lines) {
        int current = 0;
        for (TAmssOutLine line : lines) {
            int st = nz(line.getStage());
            int act = nz(line.getAction());
            if (act == OutCardConsts.ACTION_FORWARD) {
                if (st == current) {
                    current++;
                }
            } else if (act == OutCardConsts.ACTION_ROLLBACK
                    && st == OutCardConsts.STAGE_CHECK && current == OutCardConsts.STAGE_SETTLE) {
                current = OutCardConsts.STAGE_CHECK;
            }
            // 缘由笔不挪段
        }
        return Math.min(current, OutCardConsts.STAGE_FINISH);
    }

    /**
     * 逐笔严格重放：多出来的、缺掉的、跳段的、料缺的、事后手补的，逐笔记问题。
     */
    private List<String> replayStrict(TAmssOutCard card, List<TAmssOutLine> lines) {
        List<String> problems = new ArrayList<String>();
        int current = 0;
        Map<Integer, Integer> frVer = new HashMap<Integer, Integer>();
        Map<Integer, Integer> rsnVer = new HashMap<Integer, Integer>();
        int reasonsBeforeSettleForward = 0;
        Date lastTime = null;
        TAmssOutLine finishLine = null;
        for (TAmssOutLine line : lines) {
            if (finishLine != null) {
                problems.add("了结笔之后又多出第" + line.getId() + "笔，钉死的卡不许再添");
            }
            if (StringUtils.isEmpty(line.getActor())) {
                problems.add("第" + line.getId() + "笔没有经办人");
            }
            if (line.getActTime() == null) {
                problems.add("第" + line.getId() + "笔没有过口时刻");
            } else if (lastTime != null && line.getActTime().before(lastTime)) {
                problems.add("第" + line.getId() + "笔时刻倒序，疑似事后手补");
            }
            if (line.getActTime() != null) {
                lastTime = line.getActTime();
            }

            int st = nz(line.getStage());
            int act = nz(line.getAction());
            if (act == OutCardConsts.ACTION_FORWARD) {
                if (st != current) {
                    problems.add("第" + line.getId() + "笔是" + OutCardConsts.stageName(st)
                            + "段向前，可卡当时压在" + OutCardConsts.stageName(Math.min(current, 3))
                            + "段（跳段/补笔）");
                }
                Integer expectVer = frVer.get(st);
                int ev = expectVer == null ? 1 : expectVer + 1;
                if (line.getVerNo() == null || line.getVerNo() != ev) {
                    problems.add(OutCardConsts.stageName(st) + "段向前版本号应为" + ev
                            + "，实为" + line.getVerNo() + "（第" + line.getId() + "笔）");
                }
                frVer.put(st, ev);
                if (st == OutCardConsts.STAGE_CHECK) {
                    // 验车第 2 版及以后的说法，必须是上一版退回笔托出来的
                    if (ev > 1 && !hasRollbackBetween(lines, st, ev)) {
                        problems.add("验车段第" + ev + "版说法没有退回笔托着，来路不明");
                    }
                    if (line.getPlateQty() == null) {
                        problems.add("验车第" + ev + "版缺号牌实收枚数");
                    }
                    if (StringUtils.isEmpty(line.getLicenseStatus())) {
                        problems.add("验车第" + ev + "版缺行驶证与检验在册情况");
                    }
                    if (line.getClaimClear() == null) {
                        problems.add("验车第" + ev + "版缺未结事故认定与罚款核查");
                    } else if (line.getClaimClear() != OutCardConsts.CLAIM_CLEAR) {
                        problems.add("验车第" + ev + "版名下尚有未结，不该向前");
                    }
                } else if (st == OutCardConsts.STAGE_SETTLE) {
                    if (reasonsBeforeSettleForward < 1) {
                        problems.add("清账向前之前一笔缴回缘由都没有");
                    }
                } else if (st == OutCardConsts.STAGE_FINISH) {
                    finishLine = line;
                }
                if (st == current && current <= OutCardConsts.STAGE_FINISH) {
                    current++;
                }
            } else if (act == OutCardConsts.ACTION_ROLLBACK) {
                if (st != OutCardConsts.STAGE_CHECK) {
                    problems.add("第" + line.getId() + "笔退回归在" + OutCardConsts.stageName(st)
                            + "段，只许清账退回验车");
                }
                if (current != OutCardConsts.STAGE_SETTLE) {
                    problems.add("第" + line.getId() + "笔退回时卡不在清账段，顺序不对");
                }
                Integer expectVer = frVer.get(st);
                int ev = (expectVer == null ? 0 : expectVer) + 1;
                if (line.getVerNo() == null || line.getVerNo() != ev) {
                    problems.add("验车段退回版本号应为" + ev + "，实为" + line.getVerNo());
                }
                frVer.put(st, ev);
                if (current == OutCardConsts.STAGE_SETTLE) {
                    current = OutCardConsts.STAGE_CHECK;
                }
            } else if (act == OutCardConsts.ACTION_REASON) {
                if (st != OutCardConsts.STAGE_SETTLE) {
                    problems.add("第" + line.getId() + "笔记错了段：缘由只归清账段");
                }
                if (current != OutCardConsts.STAGE_SETTLE) {
                    problems.add("第" + line.getId() + "笔记缘由时卡不在清账段");
                }
                if (!validReason(line.getReturnReason())) {
                    problems.add("第" + line.getId() + "笔缘由码不在四种之内：" + line.getReturnReason());
                }
                int ev = (rsnVer.containsKey(st) ? rsnVer.get(st) : 0) + 1;
                if (line.getVerNo() == null || line.getVerNo() != ev) {
                    problems.add("缘由笔次应为" + ev + "，实为" + line.getVerNo());
                }
                rsnVer.put(st, ev);
                reasonsBeforeSettleForward++;
            } else {
                problems.add("第" + line.getId() + "笔动作码不认：" + act);
            }
        }

        boolean closed = OutCardConsts.STATUS_CLOSED == nz(card.getStatus());
        int derived = replayStage(lines);
        if (nz(card.getStage()) != derived) {
            problems.add("卡面段位(" + nz(card.getStage()) + ")与履历推出的(" + derived
                    + ")对不上，卡面的数不作数");
        }
        if (closed) {
            if (finishLine == null) {
                problems.add("卡标着已了结，履历里却没有了结笔");
            }
            if (current != OutCardConsts.STAGE_FINISH + 1) {
                problems.add("卡标着已了结，履历只走到" + OutCardConsts.stageName(Math.max(0, current - 1))
                        + "段，缺段");
            }
        } else if (nz(card.getStatus()) != OutCardConsts.STATUS_ACTIVE) {
            problems.add("卡落定值不认：" + card.getStatus());
        }
        return problems;
    }

    private boolean hasRollbackBetween(List<TAmssOutLine> lines, int stage, int forwardVer) {
        boolean seenRollback = false;
        for (TAmssOutLine line : lines) {
            if (nz(line.getStage()) != stage) {
                continue;
            }
            int act = nz(line.getAction());
            if (act == OutCardConsts.ACTION_ROLLBACK && nz(line.getVerNo()) == forwardVer - 1) {
                seenRollback = true;
            }
            if (act == OutCardConsts.ACTION_FORWARD && nz(line.getVerNo()) == forwardVer) {
                return seenRollback;
            }
        }
        return false;
    }

    private int deriveStage(Long cardId) {
        return replayStage(loadLines(cardId));
    }

    private List<TAmssOutLine> loadLines(Long cardId) {
        return this.amssOutLineMapper.selectList(new QueryWrapper<TAmssOutLine>()
                .eq("card_id", cardId)
                .eq("del_flag", 0)
                .orderByAsc("act_time", "id"));
    }

    private TAmssOutCard lockCard(Long id) {
        if (id == null) {
            return null;
        }
        return this.amssOutCardMapper.selectOne(new QueryWrapper<TAmssOutCard>()
                .eq("id", id)
                .last("FOR UPDATE"));
    }

    private int nextFrVersion(List<TAmssOutLine> lines, int stage) {
        int max = 0;
        for (TAmssOutLine line : lines) {
            if (nz(line.getStage()) == stage
                    && (nz(line.getAction()) == OutCardConsts.ACTION_FORWARD
                        || nz(line.getAction()) == OutCardConsts.ACTION_ROLLBACK)) {
                max = Math.max(max, nz(line.getVerNo()));
            }
        }
        return max;
    }

    private int countReasons(List<TAmssOutLine> lines) {
        int n = 0;
        for (TAmssOutLine line : lines) {
            if (nz(line.getStage()) == OutCardConsts.STAGE_SETTLE
                    && nz(line.getAction()) == OutCardConsts.ACTION_REASON) {
                n++;
            }
        }
        return n;
    }

    private TAmssOutLine surfaceForward(List<TAmssOutLine> lines, int stage) {
        // 明面上的说法：该段向前/退回序列里，最近一条向前笔
        TAmssOutLine hit = null;
        int hitVer = -1;
        for (TAmssOutLine line : lines) {
            if (nz(line.getStage()) == stage && nz(line.getAction()) == OutCardConsts.ACTION_FORWARD
                    && nz(line.getVerNo()) > hitVer) {
                hit = line;
                hitVer = nz(line.getVerNo());
            }
        }
        return hit;
    }

    private Integer payloadStage(OutCardAdvanceBo bo) {
        // 三样只归验车段；三样都空的“空料”不算料，留给段口门禁去挡
        if (bo.getPlateQty() != null || !StringUtils.isEmpty(bo.getLicenseStatus())
                || bo.getClaimClear() != null) {
            return OutCardConsts.STAGE_CHECK;
        }
        return null;
    }

    private boolean validReason(String code) {
        return OutCardConsts.REASON_SCRAP.equals(code) || OutCardConsts.REASON_LOST.equals(code)
                || OutCardConsts.REASON_TRANSFER_OUT.equals(code)
                || OutCardConsts.REASON_VOLUNTARY.equals(code);
    }

    private TAmssOutCard rejectOrNull(boolean strict, String message) {
        if (strict) {
            throw new ServiceException(message);
        }
        return null;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v.intValue();
    }

    /** 过口经办人：以登录人为准；测试与定时场合落 "Task"。 */
    protected String currentActor() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "Task";
        }
    }
}
