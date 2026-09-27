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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 跨区作业队在机手名录建档卡对象 t_amss_team_card
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_amss_team_card")
@ApiModel(value = "TAmssTeamCard", description = "跨区作业队在机手名录建档卡")
public class TAmssTeamCard implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 在队机手建档卡号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "在队机手建档卡号")
    private String billNo;

    /** 本卡名录版本号 */
    @TableField("node_no")
    @ApiModelProperty(value = "本卡名录版本号")
    private Integer nodeNo;

    /** 所属农机服务组织底档 */
    @TableField("site_id")
    @ApiModelProperty(value = "所属农机服务组织底档")
    private Long siteId;

    /** 所属服务组织底档字号 */
    @TableField("site_no")
    @ApiModelProperty(value = "所属服务组织底档字号")
    private String siteNo;

    /** 机主(机手)姓名,与底档字号合成去重死键 */
    @TableField("owner_name")
    @ApiModelProperty(value = "机主(机手)姓名")
    private String ownerName;

    /** 准驾代号 G1/K1/L/R/S */
    @TableField("drive_code")
    @ApiModelProperty(value = "准驾代号 G1/K1/L/R/S")
    private String driveCode;

    /** 作业类别 机收/机播/烘干/转运 */
    @TableField("work_type")
    @ApiModelProperty(value = "作业类别 机收/机播/烘干/转运")
    private String workType;

    /** 收割机型(作业类别=机收时必填) */
    @TableField("harvester_model")
    @ApiModelProperty(value = "收割机型(机收必填)")
    private String harvesterModel;

    /** 日作业能力(亩/日,作业类别=机收时必填) */
    @TableField("daily_capacity")
    @ApiModelProperty(value = "日作业能力(机收必填)")
    private BigDecimal dailyCapacity;

    /** 播种行数(作业类别=机播时必填) */
    @TableField("seed_rows")
    @ApiModelProperty(value = "播种行数(机播必填)")
    private Integer seedRows;

    /** 批处理量(作业类别=烘干时必填) */
    @TableField("batch_capacity")
    @ApiModelProperty(value = "批处理量(烘干必填)")
    private BigDecimal batchCapacity;

    /** 承运区间(作业类别=转运时必填) */
    @TableField("haul_range")
    @ApiModelProperty(value = "承运区间(转运必填)")
    private String haulRange;

    /** 牵引机具(作业类别=转运时必填) */
    @TableField("traction_tool")
    @ApiModelProperty(value = "牵引机具(转运必填)")
    private String tractionTool;

    /** 本卡合并后在队服务面积(亩) */
    @TableField("qty")
    @ApiModelProperty(value = "本卡合并后在队服务面积(亩)")
    private BigDecimal qty;

    /** 本年度新进队服务面积(亩) */
    @TableField("fine_amt")
    @ApiModelProperty(value = "本年度新进队服务面积(亩)")
    private BigDecimal fineAmt;

    /** 核验串（随名录版本走） */
    @TableField("content")
    @ApiModelProperty(value = "核验串（随名录版本走）")
    private String content;

    /** 在队服务覆盖程度 */
    @TableField("grade_level")
    @ApiModelProperty(value = "在队服务覆盖程度")
    private Integer gradeLevel;

    /** 进展 0待核对 1已核对 2已压死 */
    @TableField("status")
    @ApiModelProperty(value = "进展 0待核对 1已核对 2已压死")
    private Integer status;

    /** 现行版本标记 0历史版本 1现行版本 */
    @TableField("cur_flag")
    @ApiModelProperty(value = "现行版本标记 0历史版本 1现行版本")
    private Integer curFlag;

    /** 本趟承办人(办一笔续一笔,与创建人各归各) */
    @TableField("handler")
    @ApiModelProperty(value = "本趟承办人")
    private String handler;

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

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getDriveCode() {
        return driveCode;
    }

    public void setDriveCode(String driveCode) {
        this.driveCode = driveCode;
    }

    public String getWorkType() {
        return workType;
    }

    public void setWorkType(String workType) {
        this.workType = workType;
    }

    public String getHarvesterModel() {
        return harvesterModel;
    }

    public void setHarvesterModel(String harvesterModel) {
        this.harvesterModel = harvesterModel;
    }

    public BigDecimal getDailyCapacity() {
        return dailyCapacity;
    }

    public void setDailyCapacity(BigDecimal dailyCapacity) {
        this.dailyCapacity = dailyCapacity;
    }

    public Integer getSeedRows() {
        return seedRows;
    }

    public void setSeedRows(Integer seedRows) {
        this.seedRows = seedRows;
    }

    public BigDecimal getBatchCapacity() {
        return batchCapacity;
    }

    public void setBatchCapacity(BigDecimal batchCapacity) {
        this.batchCapacity = batchCapacity;
    }

    public String getHaulRange() {
        return haulRange;
    }

    public void setHaulRange(String haulRange) {
        this.haulRange = haulRange;
    }

    public String getTractionTool() {
        return tractionTool;
    }

    public void setTractionTool(String tractionTool) {
        this.tractionTool = tractionTool;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getFineAmt() {
        return fineAmt;
    }

    public void setFineAmt(BigDecimal fineAmt) {
        this.fineAmt = fineAmt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCurFlag() {
        return curFlag;
    }

    public void setCurFlag(Integer curFlag) {
        this.curFlag = curFlag;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
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
