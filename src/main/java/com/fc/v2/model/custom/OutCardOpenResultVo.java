package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TAmssOutCard;

/**
 * 月初批量起卡结果：起成的卡与被挡回的车各归一摊，驳回要带上原因。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class OutCardOpenResultVo {

    /** 起成的活卡 */
    private List<TAmssOutCard> opened = new ArrayList<TAmssOutCard>();

    /** 被挡回的车 */
    private List<Rejected> rejected = new ArrayList<Rejected>();

    public List<TAmssOutCard> getOpened() {
        return opened;
    }

    public void setOpened(List<TAmssOutCard> opened) {
        this.opened = opened;
    }

    public List<Rejected> getRejected() {
        return rejected;
    }

    public void setRejected(List<Rejected> rejected) {
        this.rejected = rejected;
    }

    /** 一车一条驳回 */
    public static class Rejected {
        private final String vehNo;
        private final String reason;

        public Rejected(String vehNo, String reason) {
            this.vehNo = vehNo;
            this.reason = reason;
        }

        public String getVehNo() {
            return vehNo;
        }

        public String getReason() {
            return reason;
        }
    }
}
