package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

/**
 * 年终清零口径：当年起的卡 = 已了结 + 在办（半路单列），全站只认这一种口径。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class OutCardReportVo {

    /** 统计年度 */
    private int year;

    /** 当年起卡数（分母，只认卡行） */
    private long opened;

    /** 已了结束（对外报的数） */
    private long closed;

    /** 走到半路数（单列一栏） */
    private long underway;

    /** 两栏合计是否等于当年起卡数 */
    private boolean balanced;

    /** 不平衡时的说明（正常为空） */
    private List<String> problems = new ArrayList<String>();

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public long getOpened() {
        return opened;
    }

    public void setOpened(long opened) {
        this.opened = opened;
    }

    public long getClosed() {
        return closed;
    }

    public void setClosed(long closed) {
        this.closed = closed;
    }

    public long getUnderway() {
        return underway;
    }

    public void setUnderway(long underway) {
        this.underway = underway;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }
}
