package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TAmssOutCard;
import com.fc.v2.model.auto.TAmssOutCardStage;
import com.fc.v2.model.custom.OutCardActionResult;
import com.fc.v2.model.custom.OutCardStageData;
import com.fc.v2.model.custom.OutCardYearReport;

/**
 * 变型拖拉机牌证缴回事务卡 Service接口（state-machine 形状：单据流转，不提供增删改查全套）。
 *
 * <p>一张卡四段：收牌(0)→验车(1)→清账(2)→了结(3)，只能由
 * {@link #advance} / {@link #rollback} 这一对出口过段；卡当下走到第几段，
 * 一律照缴回履历里已经过了几段推出来，页面摆的、经办人自己认的均不作数，
 * 动没动成也以方法给回的 {@link OutCardActionResult} 为准。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITAmssOutCardService {

    /** 段：收牌 */
    int STAGE_COLLECT = 0;
    /** 段：验车 */
    int STAGE_INSPECT = 1;
    /** 段：清账 */
    int STAGE_SETTLE = 2;
    /** 段：了结 */
    int STAGE_FINISH = 3;

    /** 段名（段号即下标） */
    String[] STAGE_NAMES = {"收牌", "验车", "清账", "了结"};

    /** 履历动作：段通过 */
    String ACTION_PASS = "PASS";
    /** 履历动作：退回上一段 */
    String ACTION_ROLLBACK = "ROLLBACK";
    /** 履历动作：缘由补记 */
    String ACTION_REASON = "REASON";

    /** 按主键查询单据 */
    TAmssOutCard selectTAmssOutCardById(Long id);

    /** 列表查询（流转台账） */
    List<TAmssOutCard> selectTAmssOutCardList(QueryWrapper<TAmssOutCard> queryWrapper);

    /**
     * 起卡：一车一卡，同一号牌只许压一张活卡；已有活卡当场挡回，
     * 并在回执里说清这台车现压在哪一段。机主日后再办是另一张新卡，老卡不动。
     *
     * @param vehiclePlateNo 车辆号牌（认车凭据，必填）
     * @param bizNo          事务卡号，可空（空则系统编号）
     * @return 起卡回执
     */
    OutCardActionResult open(String vehiclePlateNo, String bizNo);

    /**
     * 往前过一段（新用法，同名重载）：只放当前段该走的流程通行；
     * 验车段三样（实收枚数/行驶证与检验在册情况/未结事故认定与罚款）不齐拒进；
     * 清账段须有四种缘由之一；同一人把同一段过两遍只落头一遍，第二遍不添不改。
     *
     * @param id   卡主键
     * @param data 本段提交的话
     * @return 过口回执
     */
    OutCardActionResult advance(Long id, OutCardStageData data);

    /**
     * 退回上一段（新用法，同名重载）：只退一段；被退段先前明面说法沉底，
     * 重来新说法摆明面，旧说法原样留档。收牌段无前可退，已了结钉死不可退。
     *
     * @param id   卡主键
     * @param data 退回附言（remark）与经办人
     * @return 过口回执
     */
    OutCardActionResult rollback(Long id, OutCardStageData data);

    /**
     * 清账缘由补记：报废/灭失/转外省/主动交回四种各归各；
     * 缘由改一次续一笔，先前各笔原样搁着。只在卡压清账段时可记。
     *
     * @param id           卡主键
     * @param returnReason 缘由码（见 {@link OutCardStageData} 常量）
     * @param operator     经办人（可空，空取当前登录人）
     * @return 过口回执
     */
    OutCardActionResult addReason(Long id, String returnReason, String operator);

    /**
     * 卡当下算走到第几段——系统照履历中已过的明面段推得，不信卡面字段。
     *
     * @return 0..3
     */
    int currentStage(Long id);

    /** 缴回履历：一笔接一笔按发生先后排定，只许翻看 */
    List<TAmssOutCardStage> history(Long id);

    /** 年终报数：已了结栏 + 走到半路栏 = 当年起的卡数 */
    OutCardYearReport yearlyReport(int year);

    /**
     * 推进一档（老用法保留）：委托新重载，被拒返回 null。
     *
     * @deprecated 请改用 {@link #advance(Long, OutCardStageData)}
     */
    @Deprecated
    TAmssOutCard advance(Long id, String remark);

    /**
     * 回退一档（老用法保留）：委托新重载，被拒返回 null。
     *
     * @deprecated 请改用 {@link #rollback(Long, OutCardStageData)}
     */
    @Deprecated
    TAmssOutCard rollback(Long id, String remark);

    /** 修改卡面记事：已了结钉死不得再改，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /**
     * 抽走卡：只有没走完的活卡可软删（履历原样留档），
     * 已了结钉死整张抽不走，被拒返回 false
     */
    boolean remove(Long id);
}
