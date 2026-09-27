package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAmssCoopDoc;

import java.util.List;

/**
 * 农机服务组织底档 Service接口（名册上层：一家一档）
 *
 * @author fuce
 * @date 2026-09-27
 */
public interface ITAmssCoopDocService {

    /** 按主键查（看不见给 null） */
    TAmssCoopDoc selectTAmssCoopDocById(Long id);

    /** 列表（可见范围圈定，分页由调用方统一处理） */
    List<TAmssCoopDoc> selectTAmssCoopDocList(Wrapper<TAmssCoopDoc> queryWrapper);

    /** 立档：字号系统自动给号，三档封顶须为正且递增 */
    TAmssCoopDoc insertTAmssCoopDoc(TAmssCoopDoc record);

    /** 收回封口：往后新卡不许再挂，先前挂过的仍旧查得着 */
    int revoke(Long id);

    /** 立卡页面可选的底档：只给本账号看得见且尚未收回的，别站的一本不列 */
    List<TAmssCoopDoc> selectActiveOptions();

    /** 跨站协办授权：受托哪一家才瞧得见那一家 */
    int grantAssist(Long siteId, Long userId);
}
