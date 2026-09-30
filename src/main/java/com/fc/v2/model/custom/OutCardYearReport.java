package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 缴回卡年终报数：全站只此一种口径——
 * 已了结栏 + 走到半路栏 = 当年起的卡数。
 *
 * @author fuce
 * @date 2026-09-29
 */
@ApiModel(value = "OutCardYearReport", description = "缴回卡年终报数")
public class OutCardYearReport implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "年份")
    private Integer year;

    @ApiModelProperty(value = "当年起的卡数")
    private long openedCount;

    @ApiModelProperty(value = "已了结（向外报的数只算这一栏）")
    private long finishedCount;

    @ApiModelProperty(value = "走到半路，单列一栏")
    private long activeCount;

    /** 两栏合起来要等于当年起的卡数 */
    public boolean isBalanced() {
        return finishedCount + activeCount == openedCount;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public long getOpenedCount() {
        return openedCount;
    }

    public void setOpenedCount(long openedCount) {
        this.openedCount = openedCount;
    }

    public long getFinishedCount() {
        return finishedCount;
    }

    public void setFinishedCount(long finishedCount) {
        this.finishedCount = finishedCount;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(long activeCount) {
        this.activeCount = activeCount;
    }
}
