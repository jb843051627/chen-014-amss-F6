package com.fc.v2.model.custom;

/**
 * 变型拖拉机牌证缴回卡常量：四段、落定、过口动作、四种缴回缘由。
 * 一张卡四段：0收牌 1验车 2清账 3了结，只许挨段向前。
 *
 * @author fuce
 * @date 2026-09-30
 */
public final class OutCardConsts {

    private OutCardConsts() {
    }

    /** 段：收牌 */
    public static final int STAGE_COLLECT = 0;
    /** 段：验车 */
    public static final int STAGE_CHECK = 1;
    /** 段：清账 */
    public static final int STAGE_SETTLE = 2;
    /** 段：了结 */
    public static final int STAGE_FINISH = 3;

    /** 落定：在办（活卡） */
    public static final int STATUS_ACTIVE = 1;
    /** 落定：已了结（钉死） */
    public static final int STATUS_CLOSED = 2;

    /** 过口动作：向前（本段过了，进下一段） */
    public static final int ACTION_FORWARD = 1;
    /** 过口动作：退回上一段重来（只许清账退验车） */
    public static final int ACTION_ROLLBACK = 2;
    /** 过口动作：清账缘由续记 */
    public static final int ACTION_REASON = 3;

    /** 缘由：报废 */
    public static final String REASON_SCRAP = "1";
    /** 缘由：灭失 */
    public static final String REASON_LOST = "2";
    /** 缘由：转外省 */
    public static final String REASON_TRANSFER_OUT = "3";
    /** 缘由：主动交回 */
    public static final String REASON_VOLUNTARY = "4";

    /** 名下未结事故认定与罚款：尚有未结 */
    public static final int CLAIM_UNCLEAR = 0;
    /** 名下未结事故认定与罚款：已清 */
    public static final int CLAIM_CLEAR = 1;

    /** 缘由码转中文（年报/履历翻阅用） */
    public static String reasonLabel(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case REASON_SCRAP:
                return "报废";
            case REASON_LOST:
                return "灭失";
            case REASON_TRANSFER_OUT:
                return "转外省";
            case REASON_VOLUNTARY:
                return "主动交回";
            default:
                return "未知(" + code + ")";
        }
    }

    /** 段号转段名 */
    public static String stageName(Integer stage) {
        if (stage == null) {
            return "未知";
        }
        switch (stage) {
            case STAGE_COLLECT:
                return "收牌";
            case STAGE_CHECK:
                return "验车";
            case STAGE_SETTLE:
                return "清账";
            case STAGE_FINISH:
                return "了结";
            default:
                return "未知(" + stage + ")";
        }
    }
}
