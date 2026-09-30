package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 变型拖拉机牌证缴回履历（段流水，只追加）对象 t_amss_out_card_stage
 *
 * <p>每过一道口落一笔：收牌、验车、清账、了结逐段推进各一笔；
 * 退回重来再落一笔，旧笔 current_flag 置 0 沉底，绝不删改；
 * 清账缘由补记也各落各笔。同一卡同一段可有多个版本（rev_no），
 * 仅 current_flag=1 的那笔算明面说法。
 *
 * @author fuce
 * @date 2026-09-29
 */
@TableName("t_amss_out_card_stage")
@ApiModel(value = "TAmssOutCardStage", description = "变型拖拉机牌证缴回履历（段流水，只追加）")
public class TAmssOutCardStage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属缴回事务卡 t_amss_out_card.id */
    @TableField("card_id")
    @ApiModelProperty(value = "所属缴回事务卡")
    private Long cardId;

    /** 段号 0收牌 1验车 2清账 3了结 */
    @TableField("stage_no")
    @ApiModelProperty(value = "段号 0收牌 1验车 2清账 3了结")
    private Integer stageNo;

    /** 本段第几版（从1起；退回重来续一版） */
    @TableField("rev_no")
    @ApiModelProperty(value = "本段第几版（从1起；退回重来续一版）")
    private Integer revNo;

    /** 是否明面版本 1明面 0已沉底 */
    @TableField("current_flag")
    @ApiModelProperty(value = "是否明面版本 1明面 0已沉底")
    private Integer currentFlag;

    /** 过口动作 PASS通过 ROLLBACK退回 REASON缘由补记 */
    @TableField("action_type")
    @ApiModelProperty(value = "过口动作 PASS通过 ROLLBACK退回 REASON缘由补记")
    private String actionType;

    /** 号牌实收枚数 */
    @TableField("plate_count")
    @ApiModelProperty(value = "号牌实收枚数")
    private Integer plateCount;

    /** 行驶证与检验在册情况 */
    @TableField("license_status")
    @ApiModelProperty(value = "行驶证与检验在册情况")
    private String licenseStatus;

    /** 名下有无未结事故认定与罚款 0无 1有 */
    @TableField("open_case_flag")
    @ApiModelProperty(value = "名下有无未结事故认定与罚款 0无 1有")
    private Integer openCaseFlag;

    /** 缴回缘由 SCRAP报废 LOST灭失 OUT_PROV转外省 VOLUNTARY主动交回 */
    @TableField("return_reason")
    @ApiModelProperty(value = "缴回缘由 SCRAP报废 LOST灭失 OUT_PROV转外省 VOLUNTARY主动交回")
    private String returnReason;

    /** 本段提交原话（JSON，原样落档） */
    @TableField("stage_data")
    @ApiModelProperty(value = "本段提交原话（JSON，原样落档）")
    private String stageData;

    /** 过口经办人 */
    @TableField("operator")
    @ApiModelProperty(value = "过口经办人")
    private String operator;

    /** 这笔落下的时辰 */
    @TableField("happen_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "这笔落下的时辰")
    private Date happenTime;

    /** 记账时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "记账时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Integer getStageNo() {
        return stageNo;
    }

    public void setStageNo(Integer stageNo) {
        this.stageNo = stageNo;
    }

    public Integer getRevNo() {
        return revNo;
    }

    public void setRevNo(Integer revNo) {
        this.revNo = revNo;
    }

    public Integer getCurrentFlag() {
        return currentFlag;
    }

    public void setCurrentFlag(Integer currentFlag) {
        this.currentFlag = currentFlag;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

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

    public String getStageData() {
        return stageData;
    }

    public void setStageData(String stageData) {
        this.stageData = stageData;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Date getHappenTime() {
        return happenTime;
    }

    public void setHappenTime(Date happenTime) {
        this.happenTime = happenTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
