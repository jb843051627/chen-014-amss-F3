package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TAmssCoopDoc;

import java.util.List;

/**
 * 农机服务组织底档 Service接口
 * <p>
 * 一家一档：底档字号由系统自动给号，站里不许手工另编；
 * 底档一旦收回便当场封口，只查不改。
 *
 * @author fuce
 * @date 2026-09-27
 */
public interface ITAmssCoopDocService {

    /** 按主键查询 */
    TAmssCoopDoc selectTAmssCoopDocById(Long id);

    /** 按底档字号核对落档（只认字号，不认名称像不像） */
    TAmssCoopDoc selectTAmssCoopDocBySiteNo(String siteNo);

    /** 列表（按当前账号辖区视野过滤） */
    List<TAmssCoopDoc> selectTAmssCoopDocList(Wrapper<TAmssCoopDoc> queryWrapper);

    /** 立档：系统自动给底档字号，返回立成的底档（含给定的字号） */
    TAmssCoopDoc insertTAmssCoopDoc(TAmssCoopDoc record, String operator);

    /** 收回：当场封口，单向不回头；返回收回后的底档 */
    TAmssCoopDoc revokeTAmssCoopDoc(Long id, String operator);
}
