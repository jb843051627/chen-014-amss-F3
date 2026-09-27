package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssEntrust;
import com.fc.v2.service.ITAmssCoopDocService;
import com.fc.v2.service.ITAmssEntrustService;
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
 * 农机服务组织底档 Controller
 * <p>
 * 底档字号由系统给号，页面只递内容不递号；给回的那一份（含字号）即屏上摆的那一份。
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "农机服务组织底档")
@Controller
@RequestMapping("/AmssCoopDocController")
public class AmssCoopDocController extends BaseController {

    private final String prefix = "admin/amssCoopDoc";

    @Autowired
    private ITAmssCoopDocService amssCoopDocService;

    @Autowired
    private ITAmssEntrustService amssEntrustService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("amss:amssCoopDoc:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "农机服务组织底档集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("amss:amssCoopDoc:list")
    @ResponseBody
    public ResultTable list(TAmssCoopDoc record) {
        QueryWrapper<TAmssCoopDoc> queryWrapper = new QueryWrapper<TAmssCoopDoc>();
        if (record != null) {
            queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
            queryWrapper.like(StringUtils.isNotEmpty(record.getSiteName()), "site_name", record.getSiteName());
            queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteType()), "site_type", record.getSiteType());
            queryWrapper.eq(record.getStatus() != null, "status", record.getStatus());
        }
        startPage();
        PageInfo<TAmssCoopDoc> page = new PageInfo<TAmssCoopDoc>(
                amssCoopDocService.selectTAmssCoopDocList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "农机服务组织底档立档", action = "add")
    @ApiOperation(value = "立档", notes = "立档（底档字号系统自动给号）")
    @PostMapping("/add")
    @RequiresPermissions("amss:amssCoopDoc:add")
    @ResponseBody
    public AjaxResult add(TAmssCoopDoc record) {
        TAmssCoopDoc saved = amssCoopDocService.insertTAmssCoopDoc(record, ShiroUtils.getLoginName());
        return success(200, "立档成功", saved);
    }

    @Log(title = "农机服务组织底档收回", action = "revoke")
    @ApiOperation(value = "收回", notes = "收回（当场封口，单向不回头）")
    @PostMapping("/revoke")
    @RequiresPermissions("amss:amssCoopDoc:revoke")
    @ResponseBody
    public AjaxResult revoke(Long id) {
        TAmssCoopDoc revoked = amssCoopDocService.revokeTAmssCoopDoc(id, ShiroUtils.getLoginName());
        return success(200, "收回封口", revoked);
    }

    @Log(title = "跨站协办受托登记", action = "entrustGrant")
    @ApiOperation(value = "受托登记", notes = "受托哪一家，受托站才瞧得见那一家")
    @PostMapping("/entrust/grant")
    @RequiresPermissions("amss:amssCoopDoc:entrust")
    @ResponseBody
    public AjaxResult entrustGrant(String siteNo, String stationName) {
        TAmssEntrust granted = amssEntrustService.grant(siteNo, stationName, ShiroUtils.getLoginName());
        return success(200, "受托登记成功", granted);
    }

    @Log(title = "跨站协办卸托", action = "entrustRelieve")
    @ApiOperation(value = "卸托", notes = "卸托后受托站即刻瞧不见")
    @PostMapping("/entrust/relieve")
    @RequiresPermissions("amss:amssCoopDoc:entrust")
    @ResponseBody
    public AjaxResult entrustRelieve(Long id) {
        TAmssEntrust relieved = amssEntrustService.relieve(id, ShiroUtils.getLoginName());
        return success(200, "卸托成功", relieved);
    }

    @ApiOperation(value = "受托条目", notes = "某站当前在托的受托条目")
    @GetMapping("/entrust/list")
    @RequiresPermissions("amss:amssCoopDoc:entrust")
    @ResponseBody
    public AjaxResult entrustList(String stationName) {
        List<TAmssEntrust> list = amssEntrustService.listByStation(stationName);
        return retobject(200, list);
    }
}
