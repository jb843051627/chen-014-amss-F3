package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAmssTeamCard;

import java.util.List;

/**
 * 跨区作业队在机手名录建档卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITAmssTeamCardService {

    /** 按主键查询（含历史版本行，删除行除外） */
    TAmssTeamCard selectTAmssTeamCardById(Long id);

    /**
     * 按条件查询现行版本列表（分页由调用方统一处理）。
     * 只摆 cur_flag=1 的最新一版；可见哪几家由数据范围圈定。
     */
    List<TAmssTeamCard> selectTAmssTeamCardList(Wrapper<TAmssTeamCard> queryWrapper);

    /**
     * 原签名保存方法（保持可调用）：成立与否、分级、核验串均在此判定，
     * 页面上不设绕过它的捷径。承办人取本趟登录账号。
     * 同一底档字号+机主第二回进队不当新卡另立，而是让旧版摊到历史层、另开一版。
     *
     * @return 立住后系统给回的那一份（卡号/版本/分级/核验串以它为准）；没立住抛业务异常
     */
    int insertTAmssTeamCard(TAmssTeamCard record);

    /**
     * 同名重载：多带一个本趟承办人入参，给无登录上下文的调用（批办、测试）使用。
     * 返回系统给回的同一份卡——屏上摆的须与给回的同一份。
     */
    TAmssTeamCard insertTAmssTeamCard(TAmssTeamCard record, String operator);

    /**
     * 原签名修改方法（保持可调用）：不容就地重写既有那版，
     * 一律走"旧版让开、另摊新版"的保存路子。
     */
    int updateTAmssTeamCard(TAmssTeamCard record);

    /** 已核对：待核对(0) → 已核对(1) */
    TAmssTeamCard confirm(Long id);

    /** 压死：走到压死(2)这一步核验串才成形；成了形谁也覆不了，重复压死原样给回 */
    TAmssTeamCard seal(Long id, String operator);

    /** 翻检某一张卡的各版（现行在前、历史在后，均不删） */
    List<TAmssTeamCard> selectVersions(Long currentId);

    /** 批量删除（逻辑删） */
    int deleteTAmssTeamCardByIds(String ids);

    /** 按主键删除（逻辑删） */
    int deleteTAmssTeamCardById(Long id);
}
