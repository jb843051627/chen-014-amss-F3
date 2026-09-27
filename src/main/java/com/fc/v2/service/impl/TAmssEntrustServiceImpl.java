package com.fc.v2.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.amss.AmssBizException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssEntrustMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssEntrust;
import com.fc.v2.service.ITAmssEntrustService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 跨站协办受托登记 Service业务层处理
 *
 * @author fuce
 * @date 2026-09-27
 */
@Service
public class TAmssEntrustServiceImpl extends ServiceImpl<TAmssEntrustMapper, TAmssEntrust>
        implements ITAmssEntrustService {

    @Autowired
    private TAmssCoopDocMapper amssCoopDocMapper;

    @Override
    public TAmssEntrust grant(String siteNo, String stationName, String operator) {
        if (StringUtils.isEmpty(siteNo)) {
            throw AmssBizException.required("site_no", "受托底档字号");
        }
        if (StringUtils.isEmpty(stationName)) {
            throw AmssBizException.required("station_name", "受托监理站");
        }
        TAmssCoopDoc doc = amssCoopDocMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("site_no", siteNo).eq("del_flag", 0));
        if (doc == null) {
            throw new AmssBizException("site_no",
                    "受托底档字号（site_no）「" + siteNo + "」查无此档，托无可托");
        }
        Integer dup = this.baseMapper.selectCount(new QueryWrapper<TAmssEntrust>()
                .eq("site_no", siteNo)
                .eq("station_name", stationName)
                .eq("status", 0)
                .eq("del_flag", 0));
        if (dup != null && dup > 0) {
            throw new AmssBizException("station_name",
                    "底档「" + siteNo + "」对「" + stationName + "」已在托，受托只保一条，不重复登记");
        }
        TAmssEntrust record = new TAmssEntrust();
        record.setSiteNo(siteNo);
        record.setStationName(stationName);
        record.setStatus(0);
        record.setDelFlag(0);
        record.setCreateBy(operator);
        this.baseMapper.insert(record);
        return record;
    }

    @Override
    public TAmssEntrust relieve(Long id, String operator) {
        TAmssEntrust record = this.baseMapper.selectOne(new QueryWrapper<TAmssEntrust>()
                .eq("id", id).eq("del_flag", 0));
        if (record == null) {
            throw new AmssBizException("受托登记查无此条：id=" + id + "，卸无可卸");
        }
        if (record.getStatus() != null && record.getStatus() == 1) {
            throw new AmssBizException("该笔受托早已卸下，卸过一回不卸二回");
        }
        record.setStatus(1);
        record.setUpdateBy(operator);
        this.baseMapper.updateById(record);
        return record;
    }

    @Override
    public List<TAmssEntrust> listByStation(String stationName) {
        return this.baseMapper.selectList(new QueryWrapper<TAmssEntrust>()
                .eq("station_name", stationName)
                .eq("status", 0)
                .eq("del_flag", 0));
    }
}
