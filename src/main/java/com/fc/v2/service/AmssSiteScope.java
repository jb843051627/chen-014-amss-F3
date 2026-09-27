package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TAmssUserSiteMapper;
import com.fc.v2.model.auto.TAmssUserSite;
import com.fc.v2.shiro.util.ShiroUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AMSS 名册数据范围：本站只翻本辖那几家；上级账号看全辖；
 * 跨站协办时受托哪一家才瞧得见那一家。
 *
 * 返回约定：null 表示上级全辖（不加站点条件）；
 * 返回空集合表示一家也看不见——调用方必须收成空结果，绝不能退成全量。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Component
public class AmssSiteScope {

    /** 上级全辖角色名 */
    public static final String ROLE_UPPER = "管理员";

    @Autowired
    private TAmssUserSiteMapper amssUserSiteMapper;

    /** 当前账号看得见的底档 id：null=全辖；空=一家也看不见 */
    public List<Long> visibleSiteIds() {
        try {
            if (ShiroUtils.getSubjct() != null && ShiroUtils.getSubjct().hasRole(ROLE_UPPER)) {
                return null;
            }
            Long userId = ShiroUtils.getUserId();
            List<TAmssUserSite> links = amssUserSiteMapper.selectList(
                    new QueryWrapper<TAmssUserSite>().eq("user_id", userId).eq("del_flag", 0));
            List<Long> ids = new ArrayList<>();
            for (TAmssUserSite link : links) {
                if (link.getSiteId() != null) {
                    ids.add(link.getSiteId());
                }
            }
            return ids;
        } catch (RuntimeException e) {
            // 没有登录上下文（批办、单测）时按"一家也不给"收，不放开全辖
            return Collections.emptyList();
        }
    }

    /** 这一家底档当前账号瞧不瞧得见 */
    public boolean canSee(Long siteId) {
        if (siteId == null) {
            return false;
        }
        List<Long> ids = visibleSiteIds();
        return ids == null || ids.contains(siteId);
    }

    /** 给查询/更新条件上范围：null（全辖）不动；空集强制无行；否则 in 限定 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void apply(com.baomidou.mybatisplus.core.conditions.AbstractWrapper qw, String siteIdColumn) {
        List<Long> ids = visibleSiteIds();
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            qw.apply("1 = 0");
        } else {
            qw.in(siteIdColumn, ids);
        }
    }
}
