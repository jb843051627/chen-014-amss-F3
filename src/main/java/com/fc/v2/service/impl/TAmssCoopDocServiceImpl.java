package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.amss.AmssBizException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.service.ITAmssCoopDocService;
import com.fc.v2.util.SerialNoUtil;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 农机服务组织底档 Service业务层处理
 * <p>
 * 一家一档：底档字号系统自动给号（ZZ 顺推），不许手工另编；
 * 三道封顶一、二、三档必须齐且逐级走高；
 * 收回当场封口、单向不回头，先前挂过的卡仍旧查得着。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Service
public class TAmssCoopDocServiceImpl extends ServiceImpl<TAmssCoopDocMapper, TAmssCoopDoc>
        implements ITAmssCoopDocService {

    /** 底档字号前缀 */
    public static final String SITE_NO_PREFIX = "ZZ";

    /** 组织类别：农机合作社、作业队、服务公司各排各队（「跨区作业队」为在册旧称，一并认） */
    public static final Set<String> SITE_TYPES = new HashSet<String>(
            Arrays.asList("农机合作社", "作业队", "跨区作业队", "服务公司"));

    @Autowired
    private AmssDataScope amssDataScope;

    @Override
    public TAmssCoopDoc selectTAmssCoopDocById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public TAmssCoopDoc selectTAmssCoopDocBySiteNo(String siteNo) {
        if (StringUtils.isEmpty(siteNo)) {
            return null;
        }
        return this.baseMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("site_no", siteNo)
                .eq("del_flag", 0));
    }

    @Override
    public List<TAmssCoopDoc> selectTAmssCoopDocList(Wrapper<TAmssCoopDoc> queryWrapper) {
        QueryWrapper<TAmssCoopDoc> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TAmssCoopDoc>) queryWrapper : new QueryWrapper<TAmssCoopDoc>();
        wrapper.eq("del_flag", 0);
        Set<String> visible = amssDataScope.visibleSiteNos();
        if (visible != null) {
            if (visible.isEmpty()) {
                return new ArrayList<TAmssCoopDoc>();
            }
            wrapper.in("site_no", visible);
        }
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public TAmssCoopDoc insertTAmssCoopDoc(TAmssCoopDoc record, String operator) {
        if (record == null) {
            throw new AmssBizException("底档为空：立档内容一样没有，立不起来");
        }
        // 底档字号一律系统自动给号，不许手工另编一套
        if (StringUtils.isNotEmpty(record.getSiteNo())) {
            throw new AmssBizException("site_no",
                    "底档字号（site_no）由系统自动给号，不许手工另编，填了也不存");
        }
        if (StringUtils.isEmpty(record.getSiteName())) {
            throw AmssBizException.required("site_name", "服务组织全称");
        }
        if (StringUtils.isEmpty(record.getSiteType())) {
            throw AmssBizException.required("site_type", "组织类别");
        }
        if (!SITE_TYPES.contains(record.getSiteType())) {
            throw new AmssBizException("site_type",
                    "组织类别（site_type）只认农机合作社、作业队、服务公司，各排各队，「"
                            + record.getSiteType() + "」不在队里");
        }
        if (StringUtils.isEmpty(record.getRoadName())) {
            throw AmssBizException.required("road_name", "管辖监理站");
        }
        checkCaps(record.getTh1Max(), record.getTh2Max(), record.getTh3Max());

        record.setSiteNo(nextSiteNo());
        record.setStatus(0);
        record.setDelFlag(0);
        record.setCreateBy(operator);
        this.baseMapper.insert(record);
        return record;
    }

    @Override
    public TAmssCoopDoc revokeTAmssCoopDoc(Long id, String operator) {
        TAmssCoopDoc doc = selectTAmssCoopDocById(id);
        if (doc == null) {
            throw new AmssBizException("底档查无此档：id=" + id + "，收无可收");
        }
        if (doc.getStatus() != null && doc.getStatus() == 1) {
            throw new AmssBizException("底档「" + doc.getSiteNo() + "」早已收回封口，收过一回不收二回");
        }
        doc.setStatus(1);
        doc.setUpdateBy(operator);
        this.baseMapper.updateById(doc);
        return doc;
    }

    /** 三道封顶：必须齐、必须为正、必须逐级走高 */
    private void checkCaps(BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        if (th1 == null) {
            throw AmssBizException.required("th1_max", "单卡在队面积一档封顶");
        }
        if (th2 == null) {
            throw AmssBizException.required("th2_max", "单卡在队面积二档封顶");
        }
        if (th3 == null) {
            throw AmssBizException.required("th3_max", "单卡在队面积三档封顶");
        }
        if (th1.signum() <= 0 || th2.compareTo(th1) <= 0 || th3.compareTo(th2) <= 0) {
            throw new AmssBizException("th1_max",
                    "三道封顶（th1_max/th2_max/th3_max）必须为正且一档低过一档："
                            + th1 + "/" + th2 + "/" + th3 + " 排不成队");
        }
    }

    /** 系统自动给底档字号：ZZ 顺推 */
    private String nextSiteNo() {
        List<TAmssCoopDoc> all = this.baseMapper.selectList(new QueryWrapper<TAmssCoopDoc>()
                .select("site_no")
                .likeRight("site_no", SITE_NO_PREFIX));
        List<String> nos = new ArrayList<String>();
        for (TAmssCoopDoc d : all) {
            nos.add(d.getSiteNo());
        }
        return SerialNoUtil.next(SITE_NO_PREFIX, 2, nos);
    }
}
