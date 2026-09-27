package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.ITAmssCoopDocService;
import com.fc.v2.service.ITAmssTeamCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 跨区作业队在机手名录建档卡 Controller。
 * 卡的成立与否全走业务服务层保存方法：必填格随作业类别在那一处判，
 * 页面上不设绕过它的捷径；分级、核验串均以方法给回为准。
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

    @Autowired
    private ITAmssCoopDocService amssCoopDocService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("amss:amssTeamCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "跨区作业队在机手名录建档卡集合查询", action = "list")
    @ApiOperation(value = "分页查询(只摆现行版)", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("amss:amssTeamCard:list")
    @ResponseBody
    public ResultTable list(TAmssTeamCard record) {
        QueryWrapper<TAmssTeamCard> queryWrapper = new QueryWrapper<TAmssTeamCard>();
        queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.like(StringUtils.isNotEmpty(record.getOwnerName()), "owner_name", record.getOwnerName());
        queryWrapper.eq(StringUtils.isNotNull(record.getGradeLevel()), "grade_level", record.getGradeLevel());
        startPage();
        com.github.pagehelper.PageInfo<TAmssTeamCard> page =
                new com.github.pagehelper.PageInfo<TAmssTeamCard>(
                        amssTeamCardService.selectTAmssTeamCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "立卡可选底档(可见且未收回)", notes = "立卡可选底档")
    @GetMapping("/siteOptions")
    @RequiresPermissions("amss:amssTeamCard:add")
    @ResponseBody
    public AjaxResult siteOptions() {
        // 受托哪一家才瞧得见那一家，别站的册一本也不列给他挑
        List<TAmssCoopDoc> options = amssCoopDocService.selectActiveOptions();
        return AjaxResult.successData(200, options);
    }

    @ApiOperation(value = "新增跳转", notes = "新增跳转")
    @GetMapping("/add")
    @RequiresPermissions("amss:amssTeamCard:add")
    public String add(ModelMap modelMap) {
        modelMap.addAttribute("sites", amssCoopDocService.selectActiveOptions());
        return prefix + "/add";
    }

    @Log(title = "跨区作业队在机手名录建档卡新增", action = "add")
    @ApiOperation(value = "新增(走业务保存方法)", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("amss:amssTeamCard:add")
    @ResponseBody
    public AjaxResult add(TAmssTeamCard record) {
        // 承办人由系统盖本趟登录账号，表单里传来的不认
        TAmssTeamCard saved = amssTeamCardService.insertTAmssTeamCard(record, ShiroUtils.getLoginName());
        // 屏上摆的须与方法给回的同一份
        return AjaxResult.success(200, "立卡已成，卡号与覆盖程度以系统给回为准", saved);
    }

    @ApiOperation(value = "重录跳转", notes = "重录跳转")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("amss:amssTeamCard:edit")
    public String edit(@PathVariable("id") Long id, ModelMap modelMap) {
        TAmssTeamCard card = amssTeamCardService.selectTAmssTeamCardById(id);
        if (card == null) {
            return prefix + "/list";
        }
        List<TAmssCoopDoc> sites = amssCoopDocService.selectActiveOptions();
        // 原底档若刚好已收回封口，下拉里仍把原归属摆出来（标注已收回），方便改挂别家
        if (card.getSiteId() != null) {
            TAmssCoopDoc own = amssCoopDocService.selectTAmssCoopDocById(card.getSiteId().longValue());
            if (own != null && own.getStatus() != null && own.getStatus() == 1
                    && sites.stream().noneMatch(s -> own.getId().equals(s.getId()))) {
                sites.add(0, own);
            }
        }
        modelMap.addAttribute("card", card);
        modelMap.addAttribute("sites", sites);
        return prefix + "/edit";
    }

    @Log(title = "跨区作业队在机手名录建档卡重录(另摊新版)", action = "edit")
    @ApiOperation(value = "重录保存(旧版让开,另摊新版)", notes = "重录保存")
    @PostMapping("/edit")
    @RequiresPermissions("amss:amssTeamCard:edit")
    @ResponseBody
    public AjaxResult editSave(TAmssTeamCard record) {
        // 不容就地重写既有那一版：服务层让旧版摊到历史层，另开一版
        TAmssTeamCard saved = amssTeamCardService.insertTAmssTeamCard(record, ShiroUtils.getLoginName());
        return AjaxResult.success(200, "已另摊第 " + saved.getNodeNo() + " 版，旧版留档可翻检", saved);
    }

    @Log(title = "建档卡核对", action = "confirm")
    @ApiOperation(value = "已核对", notes = "待核对→已核对")
    @PostMapping("/confirm/{id}")
    @RequiresPermissions("amss:amssTeamCard:edit")
    @ResponseBody
    public AjaxResult confirm(@PathVariable("id") Long id) {
        TAmssTeamCard card = amssTeamCardService.confirm(id);
        if (card == null) {
            return error("这张卡不在你看得见的册子里，或已不在现行版上");
        }
        return AjaxResult.success(200, "已核对", card);
    }

    @Log(title = "建档卡压死成形", action = "seal")
    @ApiOperation(value = "压死(核验串成形,不可覆)", notes = "压死")
    @PostMapping("/seal/{id}")
    @RequiresPermissions("amss:amssTeamCard:edit")
    @ResponseBody
    public AjaxResult seal(@PathVariable("id") Long id) {
        TAmssTeamCard card = amssTeamCardService.seal(id, ShiroUtils.getLoginName());
        if (card == null) {
            return error("这张卡不在你看得见的册子里，或已不在现行版上");
        }
        // 核验串以方法给回为准
        return AjaxResult.success(200, "已压死，核验串成形，此后不可覆", card);
    }

    @ApiOperation(value = "翻检历史版本", notes = "翻检历史版本")
    @GetMapping("/versions/{id}")
    @RequiresPermissions("amss:amssTeamCard:list")
    @ResponseBody
    public AjaxResult versions(@PathVariable("id") Long id) {
        return AjaxResult.successData(200, amssTeamCardService.selectVersions(id));
    }

    @Log(title = "跨区作业队在机手名录建档卡删除", action = "remove")
    @ApiOperation(value = "删除(逻辑删)", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("amss:amssTeamCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(amssTeamCardService.deleteTAmssTeamCardByIds(ids));
    }
}
