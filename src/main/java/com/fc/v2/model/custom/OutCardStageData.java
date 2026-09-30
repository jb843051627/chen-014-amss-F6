package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 缴回卡过段时提交的一段话。
 *
 * <p>各段只认该段该交的字段：
 * <ul>
 *   <li>收牌段：只走卡，无额外三样；</li>
 *   <li>验车段：号牌实收几枚 plateCount、行驶证与检验在册情况 licenseStatus、
 *       名下有无未结事故认定和罚款 openCaseFlag，三样缺一不可；</li>
 *   <li>清账段：returnReason 缴回缘由（报废/灭失/转外省/主动交回）。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-29
 */
@ApiModel(value = "OutCardStageData", description = "缴回卡过段提交数据")
public class OutCardStageData implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 缴回缘由：报废 */
    public static final String REASON_SCRAP = "SCRAP";
    /** 缴回缘由：灭失 */
    public static final String REASON_LOST = "LOST";
    /** 缴回缘由：转外省 */
    public static final String REASON_OUT_PROV = "OUT_PROV";
    /** 缴回缘由：主动交回 */
    public static final String REASON_VOLUNTARY = "VOLUNTARY";

    /** 号牌实收几枚 */
    @ApiModelProperty(value = "号牌实收几枚（验车段必填）")
    private Integer plateCount;

    /** 行驶证与检验在册情况 */
    @ApiModelProperty(value = "行驶证与检验在册情况（验车段必填）")
    private String licenseStatus;

    /** 名下有无未结事故认定和罚款 0无 1有 */
    @ApiModelProperty(value = "名下有无未结事故认定和罚款 0无 1有（验车段必填）")
    private Integer openCaseFlag;

    /** 缴回缘由 SCRAP/LOST/OUT_PROV/VOLUNTARY */
    @ApiModelProperty(value = "缴回缘由 SCRAP报废 LOST灭失 OUT_PROV转外省 VOLUNTARY主动交回（清账段必填）")
    private String returnReason;

    /** 过口经办人（不传则取当前登录人） */
    @ApiModelProperty(value = "过口经办人（不传则取当前登录人）")
    private String operator;

    /** 附言 */
    @ApiModelProperty(value = "附言")
    private String remark;

    public Integer getPlateCount() {
        return plateCount;
    }

    public void setPlateCount(Integer plateCount) {
        this.plateCount = plateCount;
    }

    public String getLicenseStatus() {
        return licenseStatus;
    }

    public void setLicenseStatus(String licenseStatus) {
        this.licenseStatus = licenseStatus;
    }

    public Integer getOpenCaseFlag() {
        return openCaseFlag;
    }

    public void setOpenCaseFlag(Integer openCaseFlag) {
        this.openCaseFlag = openCaseFlag;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
