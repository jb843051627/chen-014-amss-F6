package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
 * 变型拖拉机牌证缴回履历线对象 t_amss_out_line
 * 一笔一笔追加、只增不改不删：抽卡从了结往回收牌捋的每一笔都在这张线上。
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_amss_out_line")
@ApiModel(value = "TAmssOutLine", description = "变型拖拉机牌证缴回履历线")
public class TAmssOutLine implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属缴回事务卡 */
    @TableField("card_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所属缴回事务卡")
    private Long cardId;

    /** 过口落定段 0收牌 1验车 2清账 3了结（退回归目的段、缘由归清账段） */
    @TableField("stage")
    @ApiModelProperty(value = "过口落定段 0收牌 1验车 2清账 3了结")
    private Integer stage;

    /** 过口动作 1向前 2退回重来 3清账缘由续记 */
    @TableField("action")
    @ApiModelProperty(value = "过口动作 1向前 2退回重来 3清账缘由续记")
    private Integer action;

    /** 段内说法版本（退回开新版、旧版沉底）；缘由线记缘由笔次 */
    @TableField("ver_no")
    @ApiModelProperty(value = "段内说法版本（退回开新版、旧版沉底）；缘由线记缘由笔次")
    private Integer verNo;

    /** 号牌实收几枚（验车段三样之一，只挂验车线；清账段拿它与当场实收对） */
    @TableField("plate_qty")
    @ApiModelProperty(value = "号牌实收几枚（验车段三样之一，只挂验车线）")
    private Integer plateQty;

    /** 行驶证与检验在册情况（验车段料） */
    @TableField("license_status")
    @ApiModelProperty(value = "行驶证与检验在册情况（验车段料）")
    private String licenseStatus;

    /** 名下未结事故认定与罚款 0尚有未结 1已清（验车段料） */
    @TableField("claim_clear")
    @ApiModelProperty(value = "名下未结事故认定与罚款 0尚有未结 1已清")
    private Integer claimClear;

    /** 缴回缘由 1报废 2灭失 3转外省 4主动交回 */
    @TableField("return_reason")
    @ApiModelProperty(value = "缴回缘由 1报废 2灭失 3转外省 4主动交回")
    private String returnReason;

    /** 过口经办人 */
    @TableField("actor")
    @ApiModelProperty(value = "过口经办人")
    private String actor;

    /** 过口时刻 */
    @TableField("act_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "过口时刻")
    private Date actTime;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注（退回原因等） */
    @TableField("remark")
    @ApiModelProperty(value = "备注（退回原因等）")
    private String remark;

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

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public Integer getVerNo() {
        return verNo;
    }

    public void setVerNo(Integer verNo) {
        this.verNo = verNo;
    }

    public Integer getPlateQty() {
        return plateQty;
    }

    public void setPlateQty(Integer plateQty) {
        this.plateQty = plateQty;
    }

    public String getLicenseStatus() {
        return licenseStatus;
    }

    public void setLicenseStatus(String licenseStatus) {
        this.licenseStatus = licenseStatus;
    }

    public Integer getClaimClear() {
        return claimClear;
    }

    public void setClaimClear(Integer claimClear) {
        this.claimClear = claimClear;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Date getActTime() {
        return actTime;
    }

    public void setActTime(Date actTime) {
        this.actTime = actTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
