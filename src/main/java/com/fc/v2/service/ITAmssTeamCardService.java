package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAmssTeamCard;

import java.util.List;

/**
 * 跨区作业队在机手名录建档卡 Service接口
 * <p>
 * 建档卡的成立与否只走这一层：必填格子随作业类别在此判定，
 * 卡折到哪一级、核验串给成什么样，均以方法给回为准。
 * 一张卡不在列表里现造第二张，也不容就地重写既有那一版；
 * 旧版不删，另摊一层留档翻检，摆出来的只许是最新一版。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITAmssTeamCardService {

    /** 按主键查询 */
    TAmssTeamCard selectTAmssTeamCardById(Long id);

    /** 按条件查询列表（只出当前版，按辖区视野过滤；分页由调用方统一处理） */
    List<TAmssTeamCard> selectTAmssTeamCardList(Wrapper<TAmssTeamCard> queryWrapper);

    /** 翻检：一张卡的全部版本（含让开的历史版），按版本号排 */
    List<TAmssTeamCard> selectTAmssTeamCardHistory(String billNo);

    /**
     * 新增（原保存方法签名，保持可调用）。
     * 承办人取当前登录名；业务回执以异常点名出岔的格子。
     *
     * @return 立成 1；立不起来抛 AmssBizException 点名格子
     */
    int insertTAmssTeamCard(TAmssTeamCard record);

    /**
     * 立卡（同名重载，多一种入参形式：承办人明示）。
     * 建档卡号系统自动给号；覆盖程度系统折算回填；核验串待压死方成形。
     *
     * @return 立成的那张卡（含给定的卡号与折算的档位），屏上摆的须与这份同一份
     */
    TAmssTeamCard insertTAmssTeamCard(TAmssTeamCard record, String operator);

    /**
     * 重录改版：就当前版另起一版——旧版让开留档，新版为当前版。
     * 创建人不挪，承办人续记；面积增减或改挂别家，档位重新套一次；
     * 核验串随名录版本走，新版未压死不成形。
     *
     * @param id      当前版主键
     * @param changes 这一版要改的格子（空着的格子承旧版）
     * @param operator 办这一趟的承办人
     * @return 新版那一行
     */
    TAmssTeamCard reRecordTAmssTeamCard(Long id, TAmssTeamCard changes, String operator);

    /**
     * 推进一步：待核对→已核对→已压死。压死那一步核验串成形，
     * 成了形谁也覆不了它。
     *
     * @return 推进后的那张卡（含核验串），屏上摆的须与这份同一份
     */
    TAmssTeamCard advanceTAmssTeamCard(Long id, String operator);

    /** 修改（不容就地重写既有那一版：一律抛 AmssBizException，指路重录改版） */
    int updateTAmssTeamCard(TAmssTeamCard record);

    /** 批量删除（名册留痕，建档卡不容删除：一律抛 AmssBizException） */
    int deleteTAmssTeamCardByIds(String ids);

    /** 按主键删除（名册留痕，建档卡不容删除：一律抛 AmssBizException） */
    int deleteTAmssTeamCardById(Long id);
}
