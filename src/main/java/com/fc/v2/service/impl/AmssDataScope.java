package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssEntrustMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssEntrust;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 名册辖区视野：圈定当前账号瞧得见哪几份底档。
 * <p>
 * 规矩：本站只翻本辖那几家；上级账号看全辖；跨站协办时，
 * 受托哪一家才瞧得见那一家，别站的册不一并列给他挑。
 * <p>
 * 站与辖区的对号：底档 road_name 记「省—市—县」，
 * 账号所在部门名（站名）含得住路名末段（县），或路名里含得住站名，即算本辖。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Component
public class AmssDataScope {

    /** 上级账号（看全辖）登录名 */
    public static final String SUPER_ACCOUNT = "admin";

    @Autowired
    private TAmssCoopDocMapper amssCoopDocMapper;

    @Autowired
    private TAmssEntrustMapper amssEntrustMapper;

    /**
     * 当前账号瞧得见的底档字号集。
     * <p>
     * 视野解析自己要查底档表与受托表，不能把调用方挂好的分页（PageHelper
     * 线程副本）吃掉：先摘下，查完原样挂回。
     *
     * @return null 表示全辖不滤（上级账号或无登录上下文的系统内调用）；
     *         否则为可见字号集合（可能为空集——空集就是哪一家也瞧不见）
     */
    public Set<String> visibleSiteNos() {
        TSysUser user = currentUserOrNull();
        if (user == null || SUPER_ACCOUNT.equals(user.getUsername())) {
            return null;
        }
        com.github.pagehelper.Page<Object> page = com.github.pagehelper.page.PageMethod.getLocalPage();
        if (page != null) {
            com.github.pagehelper.page.PageMethod.clearPage();
        }
        try {
            String station = user.getDepName();
            Set<String> visible = new HashSet<String>();
            // 本辖那几家：站名与底档路名对得上号的
            List<TAmssCoopDoc> all = amssCoopDocMapper.selectList(
                    new QueryWrapper<TAmssCoopDoc>().eq("del_flag", 0));
            for (TAmssCoopDoc doc : all) {
                if (inJurisdiction(station, doc.getRoadName())) {
                    visible.add(doc.getSiteNo());
                }
            }
            // 跨站协办：受托哪一家添哪一家
            if (StringUtils.isNotEmpty(station)) {
                List<TAmssEntrust> entrusted = amssEntrustMapper.selectList(
                        new QueryWrapper<TAmssEntrust>()
                                .eq("station_name", station)
                                .eq("status", 0)
                                .eq("del_flag", 0));
                for (TAmssEntrust e : entrusted) {
                    visible.add(e.getSiteNo());
                }
            }
            return visible;
        } finally {
            if (page != null) {
                com.github.pagehelper.page.PageMethod.startPage(
                        page.getPageNum(), page.getPageSize(), page.getOrderBy());
            }
        }
    }

    /**
     * 站名与管辖路名对号：路名含站名，或站名含路名末段（县），即算本辖。
     */
    public boolean inJurisdiction(String station, String roadName) {
        if (StringUtils.isEmpty(station) || StringUtils.isEmpty(roadName)) {
            return false;
        }
        if (roadName.contains(station)) {
            return true;
        }
        String[] segs = roadName.split("—");
        String county = segs[segs.length - 1].trim();
        return StringUtils.isNotEmpty(county) && station.contains(county);
    }

    /** 取当前登录用户；无登录上下文（定时任务等系统内调用）时回 null */
    private TSysUser currentUserOrNull() {
        try {
            return ShiroUtils.getUser();
        } catch (UnavailableSecurityManagerException e) {
            return null;
        }
    }
}
