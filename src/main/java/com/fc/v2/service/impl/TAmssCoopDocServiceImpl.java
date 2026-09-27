package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.BizFieldException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssUserSiteMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssUserSite;
import com.fc.v2.service.AmssSiteScope;
import com.fc.v2.service.ITAmssCoopDocService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 农机服务组织底档 Service业务层处理（名册上层：一家一档）。
 *
 * 底档字号一律系统自动给号，不许站里手工另编；
 * 底档收回即当场封口——新卡不许再挂，旧账照旧查得着（可查不等于可改）。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Service
public class TAmssCoopDocServiceImpl extends ServiceImpl<TAmssCoopDocMapper, TAmssCoopDoc>
        implements ITAmssCoopDocService {

    /** 组织类别：农机合作社、作业队、服务公司各排各队 */
    private static final Set<String> SITE_TYPES =
            new HashSet<>(Arrays.asList("农机合作社", "跨区作业队", "服务公司"));

    /** 底档情形 */
    private static final int STATUS_ACTIVE = 0;
    private static final int STATUS_REVOKED = 1;

    /** 协办 */
    private static final int SCOPE_ASSIST = 1;

    @Autowired
    private TAmssUserSiteMapper amssUserSiteMapper;

    @Autowired
    private AmssSiteScope siteScope;

    @Override
    public TAmssCoopDoc selectTAmssCoopDocById(Long id) {
        TAmssCoopDoc doc = this.baseMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("id", id).eq("del_flag", 0));
        if (doc != null && !siteScope.canSee(id)) {
            return null;
        }
        return doc;
    }

    @Override
    public List<TAmssCoopDoc> selectTAmssCoopDocList(Wrapper<TAmssCoopDoc> queryWrapper) {
        QueryWrapper<TAmssCoopDoc> qw = queryWrapper == null
                ? new QueryWrapper<TAmssCoopDoc>()
                : (QueryWrapper<TAmssCoopDoc>) queryWrapper;
        qw.eq("del_flag", 0);
        siteScope.apply(qw, "id");
        qw.orderByAsc("site_no");
        return this.baseMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssCoopDoc insertTAmssCoopDoc(TAmssCoopDoc r) {
        if (r == null) {
            throw new BizFieldException("form", "底档是空的，立不起来");
        }
        if (StringUtils.isEmpty(r.getSiteName())) {
            throw new BizFieldException("siteName", "服务组织全称空着，立不了档");
        }
        if (StringUtils.isEmpty(r.getSiteType()) || !SITE_TYPES.contains(r.getSiteType().trim())) {
            throw new BizFieldException("siteType", "组织类别只认农机合作社、跨区作业队、服务公司");
        }
        if (StringUtils.isEmpty(r.getRoadName())) {
            throw new BizFieldException("roadName", "管辖监理站得一直写到省—市—县");
        }
        BigDecimal t1 = r.getTh1Max();
        BigDecimal t2 = r.getTh2Max();
        BigDecimal t3 = r.getTh3Max();
        if (t1 == null || t2 == null || t3 == null) {
            throw new BizFieldException("th1Max", "一、二、三档封顶一个也不能空");
        }
        if (t1.compareTo(BigDecimal.ZERO) <= 0 || t2.compareTo(BigDecimal.ZERO) <= 0
                || t3.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizFieldException("th1Max", "三档封顶都得是正数");
        }
        if (t1.compareTo(t2) >= 0 || t2.compareTo(t3) >= 0) {
            throw new BizFieldException("th2Max", "三档封顶得一档比一档高（80 &lt; 200 &lt; 500 这个排法）");
        }

        // 字号系统给号，传入的一律不认
        r.setId(null);
        r.setSiteNo(nextSiteNo());
        r.setSiteType(r.getSiteType().trim());
        r.setStatus(STATUS_ACTIVE);
        r.setDelFlag(0);
        this.baseMapper.insert(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revoke(Long id) {
        TAmssCoopDoc doc = this.baseMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("id", id).eq("del_flag", 0));
        if (doc == null || !siteScope.canSee(id)) {
            return 0;
        }
        return this.baseMapper.update(null, new UpdateWrapper<TAmssCoopDoc>()
                .eq("id", id)
                .eq("status", STATUS_ACTIVE)
                .set("status", STATUS_REVOKED));
    }

    @Override
    public List<TAmssCoopDoc> selectActiveOptions() {
        QueryWrapper<TAmssCoopDoc> qw = new QueryWrapper<TAmssCoopDoc>()
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE);
        siteScope.apply(qw, "id");
        qw.orderByAsc("site_no");
        return this.baseMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantAssist(Long siteId, Long userId) {
        if (siteId == null || userId == null) {
            throw new BizFieldException("siteId", "受托底档与受托账号都得点明");
        }
        TAmssCoopDoc doc = this.baseMapper.selectById(siteId);
        if (doc == null || (doc.getDelFlag() != null && doc.getDelFlag() == 1)) {
            throw new BizFieldException("siteId", "受托底档不存在");
        }
        Integer exists = amssUserSiteMapper.selectCount(new QueryWrapper<TAmssUserSite>()
                .eq("site_id", siteId).eq("user_id", userId).eq("del_flag", 0));
        if (exists != null && exists > 0) {
            return 0;
        }
        TAmssUserSite link = new TAmssUserSite();
        link.setUserId(userId);
        link.setSiteId(siteId);
        link.setScopeType(SCOPE_ASSIST);
        link.setDelFlag(0);
        return amssUserSiteMapper.insert(link);
    }

    /** 字号系统自动给号：ZZ + 两位顺序号（接续库内既有最大号，种子是 ZZ00/ZZ01）。 */
    private synchronized String nextSiteNo() {
        List<TAmssCoopDoc> all = this.baseMapper.selectList(new QueryWrapper<TAmssCoopDoc>()
                .likeRight("site_no", "ZZ").select("site_no"));
        int max = -1;
        for (TAmssCoopDoc doc : all) {
            String no = doc.getSiteNo();
            if (no != null && no.startsWith("ZZ")) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(2)));
                } catch (NumberFormatException ignore) {
                    // 手工混进来的杂号不接续
                }
            }
        }
        return String.format("ZZ%02d", max + 1);
    }
}
