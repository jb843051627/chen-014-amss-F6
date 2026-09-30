package com.fc.v2.service;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutLine;
import com.fc.v2.model.custom.OutCardAdvanceBo;
import com.fc.v2.model.custom.OutCardAuditVo;
import com.fc.v2.model.custom.OutCardOpenResultVo;
import com.fc.v2.model.custom.OutCardReportVo;

/**
 * 变型拖拉机牌证缴回事务卡 Service接口（state-machine 形状：单据流转，不提供增删改查全套）。
 * 四段：收牌→验车→清账→了结；往前、退回各只有 advance/rollback 一对出口；
 * 卡当下停在哪段，由履历线照“已经过了几段”推出，页面摆的、经办人认的不作数。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAmssOutCardService {

    /** 按主键查询单据（只翻卡，不带动卡） */
    TAmssOutCard selectTAmssOutCardById(Long id);

    /** 列表查询（流转台账） */
    List<TAmssOutCard> selectTAmssOutCardList(QueryWrapper<TAmssOutCard> queryWrapper);

    /**
     * 起一张活卡（单车，从收牌段起）。
     * 一台车同时只许压一张活卡：重复起卡当场挡回，并说清已有活卡卡号与压在哪一段。
     */
    TAmssOutCard open(String vehNo);

    /**
     * 月初一回起几十张卡：逐车起，重复的车收进驳回清单，不连累其余车。
     */
    OutCardOpenResultVo openBatch(Collection<String> vehNos);

    /**
     * 老用法（入参与返回继续可调）：无带料向前推一段。
     * 仅在本段无须补料的段口（清账已记缘由后的了结口）走得通；被拒返回 null。
     */
    TAmssOutCard advance(Long id, String remark);

    /**
     * 新用法（同名重载）：带着本段要交的东西过口。
     * 收牌段交号牌实收枚数；验车段交三样（核对枚数、行驶证与检验在册、未结认定罚款是否已清）。
     * 卡已了结、段口不对、料不齐的，抛 ServiceException 挡回并说明原因。
     */
    TAmssOutCard advance(Long id, OutCardAdvanceBo bo, String remark);

    /**
     * 老用法（入参与返回继续可调）：清账段把卡退回验车段重来。
     * 验车记的枚数与收牌实收相合则无须退回；被拒返回 null。
     */
    TAmssOutCard rollback(Long id, String remark);

    /**
     * 新用法（同名重载）：拿当场复核的实收枚数与验车记的枚数对，对不上才退。
     */
    TAmssOutCard rollback(Long id, Integer actualPlateQty, String remark);

    /**
     * 清账段续记一笔缴回缘由（报废/灭失/转外省/主动交回四码之一）。
     * 只许在清账段记；改一次续一笔，先前那笔原样搁着，返回新落的这一笔。
     */
    TAmssOutLine addReason(Long id, String reasonCode, String remark);

    /** 翻履历（只增不改的一笔一笔，从收牌往了结排） */
    List<TAmssOutLine> listLines(Long id);

    /**
     * 抽一张卡从了结往回收牌逐笔核验：多出来的、缺掉的、事后手补的、旧句被改的，
     * 抽到一样就算这张卡没走成。
     */
    OutCardAuditVo audit(Long id);

    /** 年终清零口径：当年起卡数 = 已了结 + 在办半路，全站只认这一种口径 */
    OutCardReportVo yearReport(int year);
}
