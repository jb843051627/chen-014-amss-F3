package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.ITAmssTeamCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 跨区作业队在机手名录建档卡 Controller
 * <p>
 * 卡折到哪一级、核验串给成什么样，均以服务层方法给回为准，
 * 屏上摆的与给回的是同一份；页面上不设绕过服务层判定的捷径——
 * 不提供就地改写（edit）与删除（remove）入口。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "跨区作业队在机手名录建档卡")
@Controller
@RequestMapping("/AmssTeamCardController")
public class AmssTeamCardController extends BaseController {

    private final String prefix = "admin/amssTeamCard";

    @Autowired
    private ITAmssTeamCardService amssTeamCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("amss:amssTeamCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "跨区作业队在机手名录建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询（只出当前版）")
    @GetMapping("/list")
    @RequiresPermissions("amss:amssTeamCard:list")
    @ResponseBody
    public ResultTable list(TAmssTeamCard record) {
        QueryWrapper<TAmssTeamCard> queryWrapper = new QueryWrapper<TAmssTeamCard>();
        if (record != null) {
            queryWrapper.eq(StringUtils.isNotEmpty(record.getBillNo()), "bill_no", record.getBillNo());
            queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
            queryWrapper.like(StringUtils.isNotEmpty(record.getOwnerName()), "owner_name", record.getOwnerName());
            queryWrapper.eq(StringUtils.isNotEmpty(record.getWorkType()), "work_type", record.getWorkType());
            queryWrapper.eq(StringUtils.isNotEmpty(record.getLicenseCode()), "license_code", record.getLicenseCode());
            queryWrapper.eq(record.getStatus() != null, "status", record.getStatus());
        }
        startPage();
        PageInfo<TAmssTeamCard> page = new PageInfo<TAmssTeamCard>(
                amssTeamCardService.selectTAmssTeamCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "跨区作业队在机手名录建档卡翻检", action = "history")
    @ApiOperation(value = "版本翻检", notes = "一张卡的全部版本（含让开的历史版）")
    @GetMapping("/history")
    @RequiresPermissions("amss:amssTeamCard:history")
    @ResponseBody
    public AjaxResult history(String billNo) {
        List<TAmssTeamCard> versions = amssTeamCardService.selectTAmssTeamCardHistory(billNo);
        return retobject(200, versions);
    }

    @Log(title = "跨区作业队在机手名录建档卡立卡", action = "add")
    @ApiOperation(value = "立卡", notes = "立卡（建档卡号系统自动给号，覆盖程度系统折算回填）")
    @PostMapping("/add")
    @RequiresPermissions("amss:amssTeamCard:add")
    @ResponseBody
    public AjaxResult add(TAmssTeamCard record) {
        TAmssTeamCard saved = amssTeamCardService.insertTAmssTeamCard(record, ShiroUtils.getLoginName());
        return success(200, "立卡成功", saved);
    }

    @Log(title = "跨区作业队在机手名录建档卡重录改版", action = "reRecord")
    @ApiOperation(value = "重录改版", notes = "就当前版另起一版，旧版让开留档")
    @PostMapping("/reRecord")
    @RequiresPermissions("amss:amssTeamCard:reRecord")
    @ResponseBody
    public AjaxResult reRecord(Long id, TAmssTeamCard record) {
        TAmssTeamCard next = amssTeamCardService.reRecordTAmssTeamCard(id, record, ShiroUtils.getLoginName());
        return success(200, "改版成功", next);
    }

    @Log(title = "跨区作业队在机手名录建档卡推进", action = "advance")
    @ApiOperation(value = "推进一步", notes = "待核对→已核对→已压死；压死那一步核验串成形")
    @PostMapping("/advance")
    @RequiresPermissions("amss:amssTeamCard:advance")
    @ResponseBody
    public AjaxResult advance(Long id) {
        TAmssTeamCard card = amssTeamCardService.advanceTAmssTeamCard(id, ShiroUtils.getLoginName());
        return success(200, "推进成功", card);
    }
}
