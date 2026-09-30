package com.fc.v2.model.custom;

/**
 * 缴回卡过口带料：验车段要交的三样随这一趟过口一并递进来。
 * 收牌段只是当面收牌、不挂料；三样都归验车段：
 * plateQty（号牌实收几枚）、licenseStatus（行驶证与检验在册情况）、
 * claimClear（名下未结事故认定和罚款是否已清）。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class OutCardAdvanceBo {

    /** 号牌实收几枚（验车段三样之一，清账段拿它与当场实收对） */
    private Integer plateQty;

    /** 行驶证与检验在册情况（验车段料） */
    private String licenseStatus;

    /** 名下未结事故认定与罚款 0尚有未结 1已清（验车段料） */
    private Integer claimClear;

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
}
