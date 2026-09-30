package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TAmssOutLine;

/**
 * 抽卡核验结果：从了结往回收牌逐笔捋履历，多出来的、缺掉的、事后手补的，
 * 抽到一样就算这张卡没走成。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class OutCardAuditVo {

    /** 卡主键 */
    private Long cardId;

    /** 卡号 */
    private String bizNo;

    /** 是否走到了结 */
    private boolean closed;

    /** 履历照线推出来的段序（不看卡面镜像） */
    private int derivedStage;

    /** 逐笔对下来是否一一对得上 */
    private boolean passed;

    /** 对不上的地方（空=全对得上） */
    private List<String> problems = new ArrayList<String>();

    /** 捋出来的全部履历笔（从收牌往了结排） */
    private List<TAmssOutLine> lines = new ArrayList<TAmssOutLine>();

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public int getDerivedStage() {
        return derivedStage;
    }

    public void setDerivedStage(int derivedStage) {
        this.derivedStage = derivedStage;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }

    public List<TAmssOutLine> getLines() {
        return lines;
    }

    public void setLines(List<TAmssOutLine> lines) {
        this.lines = lines;
    }
}
