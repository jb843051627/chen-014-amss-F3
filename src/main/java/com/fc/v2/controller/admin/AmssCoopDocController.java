package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.service.ITAmssCoopDocService;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 农机服务组织底档 Controller（名册上层：一家一档）。
 * 底档字号系统给号；收回当场封口，新卡不许再挂，旧账仍旧查得着。
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
        queryWrapper.like(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.like(StringUtils.isNotEmpty(record.getSiteName()), "site_name", record.getSiteName());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        startPage();
        com.github.pagehelper.PageInfo<TAmssCoopDoc> page =
                new com.github.pagehelper.PageInfo<TAmssCoopDoc>(
                        amssCoopDocService.selectTAmssCoopDocList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @GetMapping("/add")
    @RequiresPermissions("amss:amssCoopDoc:add")
    public String add(ModelMap modelMap) {
        return prefix + "/add";
    }

    @Log(title = "农机服务组织底档立档", action = "add")
    @PostMapping("/add")
    @RequiresPermissions("amss:amssCoopDoc:add")
    @ResponseBody
    public AjaxResult add(TAmssCoopDoc record) {
        TAmssCoopDoc saved = amssCoopDocService.insertTAmssCoopDoc(record);
        return AjaxResult.success(200, "底档已立，字号「" + saved.getSiteNo() + "」由系统给号", saved);
    }

    @Log(title = "农机服务组织底档收回封口", action = "revoke")
    @PostMapping("/revoke/{id}")
    @RequiresPermissions("amss:amssCoopDoc:revoke")
    @ResponseBody
    public AjaxResult revoke(@PathVariable("id") Long id) {
        int rows = amssCoopDocService.revoke(id);
        return rows > 0 ? AjaxResult.success("已收回封口，此后新卡不许再挂；旧账仍可查")
                : error("这家不在你辖内，或已经收回过");
    }

    @Log(title = "跨站协办授权", action = "grantAssist")
    @ApiOperation(value = "跨站协办授权(受托哪家才看得见哪家)", notes = "跨站协办授权")
    @PostMapping("/grantAssist")
    @RequiresPermissions("amss:amssCoopDoc:revoke")
    @ResponseBody
    public AjaxResult grantAssist(Long siteId, Long userId) {
        int rows = amssCoopDocService.grantAssist(siteId, userId);
        return rows > 0 ? AjaxResult.success("已授权，该账号只瞧得见这一家")
                : success("这一家本来就已受托，不用重复授权");
    }
}
