package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.BizFieldException;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssTeamCardMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.AmssSiteScope;
import com.fc.v2.service.ITAmssTeamCardService;
import com.fc.v2.util.DateUtils;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.SnowflakeIdWorker;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 跨区作业队在机手名录建档卡Service业务层处理。
 *
 * 立账规矩：
 * 1. 卡号系统给号，站里不许手工另编；落档只凭底档字号核对，名称像不算数；
 * 2. (底档字号+机主) 是去重死键，第二回进队旧版摊到历史层、另开新版，摆出来只摆现行版；
 * 3. 覆盖程度系统按三道封顶折算回填——压在界上往上算，冲破三档归三档；
 * 4. 核验串压死那一步才成形、成形不可覆，随名录版本走（含版本号与新版当日）；
 * 5. 必填格随作业类别变，回执点名列在哪一格；
 * 6. 创建人记立卡那一刻的人，重录不挪；承办人办一笔续一笔。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TAmssTeamCardServiceImpl extends ServiceImpl<TAmssTeamCardMapper, TAmssTeamCard> implements ITAmssTeamCardService {

    /** 作业类别 */
    public static final String WORK_HARVEST = "机收";
    public static final String WORK_SEED = "机播";
    public static final String WORK_DRY = "烘干";
    public static final String WORK_HAUL = "转运";
    private static final Set<String> WORK_TYPES =
            new HashSet<>(Arrays.asList(WORK_HARVEST, WORK_SEED, WORK_DRY, WORK_HAUL));

    /** 准驾代号 */
    private static final Set<String> DRIVE_CODES = new HashSet<>(Arrays.asList("G1", "K1", "L", "R", "S"));

    /** 进展 */
    private static final int STATUS_TODO = 0;
    private static final int STATUS_CHECKED = 1;
    private static final int STATUS_SEALED = 2;

    /** 现行/历史版本 */
    private static final int CUR_FLAG_HISTORY = 0;
    private static final int CUR_FLAG_CURRENT = 1;

    @Autowired
    private TAmssCoopDocMapper amssCoopDocMapper;

    @Autowired
    private AmssSiteScope siteScope;

    @Override
    public TAmssTeamCard selectTAmssTeamCardById(Long id) {
        TAmssTeamCard card = this.baseMapper.selectOne(new QueryWrapper<TAmssTeamCard>()
                .eq("id", id)
                .eq("del_flag", 0));
        if (card != null && !canSee(card)) {
            return null;
        }
        return card;
    }

    @Override
    public List<TAmssTeamCard> selectTAmssTeamCardList(Wrapper<TAmssTeamCard> queryWrapper) {
        QueryWrapper<TAmssTeamCard> qw = queryWrapper == null
                ? new QueryWrapper<TAmssTeamCard>()
                : (QueryWrapper<TAmssTeamCard>) queryWrapper;
        qw.eq("del_flag", 0).eq("cur_flag", CUR_FLAG_CURRENT);
        // 可见范围：null=上级全辖；空集=一家也看不见，绝不能退成全量
        siteScope.apply(qw, "site_id");
        qw.orderByDesc("create_time");
        return this.baseMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertTAmssTeamCard(TAmssTeamCard record) {
        return register(record, currentOperator()) != null ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssTeamCard insertTAmssTeamCard(TAmssTeamCard record, String operator) {
        return register(record, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTAmssTeamCard(TAmssTeamCard record) {
        // 不容就地重写既有那版：修改一律走"旧版让开、另摊新版"的保存路子
        return register(record, currentOperator()) != null ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssTeamCard confirm(Long id) {
        TAmssTeamCard card = loadVisible(id, true);
        if (card == null) {
            return null;
        }
        if (card.getStatus() != null && card.getStatus() == STATUS_SEALED) {
            // 成了形谁也覆不了，原样给回
            return card;
        }
        card.setStatus(STATUS_CHECKED);
        card.setHandler(currentOperator());
        this.baseMapper.update(null, new UpdateWrapper<TAmssTeamCard>()
                .eq("id", card.getId()).eq("del_flag", 0)
                .set("status", STATUS_CHECKED)
                .set("handler", card.getHandler())
                .set("update_time", new Date()));
        return card;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssTeamCard seal(Long id, String operator) {
        TAmssTeamCard card = loadVisible(id, true);
        if (card == null) {
            return null;
        }
        if (card.getStatus() != null && card.getStatus() == STATUS_SEALED) {
            // 核验串成形不可覆，重复压死原样给回
            return card;
        }
        if (card.getStatus() == null || card.getStatus() < STATUS_CHECKED) {
            throw new BizFieldException("status", "这张卡还没走到已核对，压不住");
        }
        String code = buildVerifyCode(card, new Date());
        this.baseMapper.update(null, new UpdateWrapper<TAmssTeamCard>()
                .eq("id", card.getId()).eq("del_flag", 0)
                .set("status", STATUS_SEALED)
                .set("content", code)
                .set("handler", operator)
                .set("update_time", new Date()));
        card.setStatus(STATUS_SEALED);
        card.setContent(code);
        card.setHandler(operator);
        return card;
    }

    @Override
    public List<TAmssTeamCard> selectVersions(Long currentId) {
        TAmssTeamCard current = loadVisible(currentId, true);
        if (current == null || StringUtils.isEmpty(current.getBillNo())) {
            return Collections.emptyList();
        }
        return this.baseMapper.selectList(new QueryWrapper<TAmssTeamCard>()
                .eq("bill_no", current.getBillNo())
                .eq("del_flag", 0)
                .orderByDesc("node_no"));
    }

    @Override
    public int deleteTAmssTeamCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr == null || idArr.length == 0) {
            return 0;
        }
        // 注销一张卡=同卡号各版（现行与留档历史）一并标记注销；范围仍圈在看得见的册子里
        List<TAmssTeamCard> seeds = this.baseMapper.selectList(new QueryWrapper<TAmssTeamCard>()
                .in("id", Arrays.asList(idArr)).eq("del_flag", 0));
        List<String> billNos = new ArrayList<>();
        for (TAmssTeamCard seed : seeds) {
            if (canSee(seed) && StringUtils.isNotEmpty(seed.getBillNo())
                    && !billNos.contains(seed.getBillNo())) {
                billNos.add(seed.getBillNo());
            }
        }
        if (billNos.isEmpty()) {
            return 0;
        }
        return this.baseMapper.update(null, new UpdateWrapper<TAmssTeamCard>()
                .in("bill_no", billNos)
                .eq("del_flag", 0)
                .set("del_flag", 1));
    }

    @Override
    public int deleteTAmssTeamCardById(Long id) {
        return deleteTAmssTeamCardByIds(String.valueOf(id));
    }

    // ------------------------------------------------------------------
    // 立账主路子：首版立卡 / 二回进队摊新版
    // ------------------------------------------------------------------

    /** 注意：事务由三个 public 入口包住，同类自调用不走代理，注解不放这里。 */
    protected TAmssTeamCard register(TAmssTeamCard r, String operator) {
        if (r == null) {
            throw new BizFieldException("form", "卡面是空的，立不起来");
        }
        if (StringUtils.isEmpty(operator)) {
            throw new BizFieldException("handler", "取不着本趟承办人，这趟没法记");
        }

        String workType = trimToNull(r.getWorkType());
        if (workType == null) {
            throw new BizFieldException("workType", "作业类别空着没选，卡立不起来");
        }
        if (!WORK_TYPES.contains(workType)) {
            throw new BizFieldException("workType", "作业类别只认机收、机播、烘干、转运四种");
        }

        String ownerName = trimToNull(r.getOwnerName());
        if (ownerName == null) {
            throw new BizFieldException("ownerName", "机主姓名空着，去重死键立不住");
        }

        String driveCode = trimToNull(r.getDriveCode());
        if (driveCode == null || !DRIVE_CODES.contains(driveCode)) {
            throw new BizFieldException("driveCode", "准驾代号只认 G1、K1、L、R、S");
        }

        BigDecimal qty = r.getQty();
        if (qty == null) {
            throw new BizFieldException("qty", "在队服务面积没填，卡立不起来");
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizFieldException("qty", "在队服务面积填零、填负，同样立不起来");
        }

        // 类别一换，非填不可的格子跟着换；用不上的空着
        validateByWorkType(workType, r);

        // 落档凭字号核对：只看名称像不像不认
        TAmssCoopDoc arch = locateArch(r);
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            throw new BizFieldException("siteNo", "底档「" + arch.getSiteNo() + "」已收回封口，新卡不许再往它名下挂");
        }
        if (arch.getTh1Max() == null || arch.getTh2Max() == null || arch.getTh3Max() == null
                || arch.getTh1Max().compareTo(arch.getTh2Max()) >= 0
                || arch.getTh2Max().compareTo(arch.getTh3Max()) >= 0) {
            throw new BizFieldException("siteNo", "底档「" + arch.getSiteNo() + "」三档封顶没立全或顺序不对，套不出覆盖程度");
        }

        // 死键：底档字号 + 机主，不分谁先到谁后到。
        // 修改入口带id时以id锚定上一版（允许改挂到别的底档名下，此时按新字号+机查重键）。
        TAmssTeamCard anchored = null;
        if (r.getId() != null) {
            anchored = this.baseMapper.selectOne(new QueryWrapper<TAmssTeamCard>()
                    .eq("id", r.getId()).eq("del_flag", 0));
            if (anchored != null && (anchored.getCurFlag() == null || anchored.getCurFlag() != CUR_FLAG_CURRENT)) {
                throw new BizFieldException("id", "摊新版只能从当下现行那一处让开，历史版本不许就地摊新");
            }
            // 压死成形的是"那一版"不可覆；变动面积/改挂时另摊新版，旧版（含其核验串）原样留档
        }

        TAmssTeamCard current = this.baseMapper.selectOne(new QueryWrapper<TAmssTeamCard>()
                .eq("site_no", arch.getSiteNo())
                .eq("owner_name", ownerName)
                .eq("cur_flag", CUR_FLAG_CURRENT)
                .eq("del_flag", 0));
        if (current != null && anchored != null && !current.getId().equals(anchored.getId())) {
            // 新字号名下已有别人（其实是同一机主）的现行卡——死键已占
            throw new BizFieldException("ownerName", "底档「" + arch.getSiteNo() + "」名下机主「"
                    + ownerName + "」已有现行卡，账上只保一行");
        }
        if (current == null) {
            current = anchored;
        }

        String billNo;
        int nodeNo;
        String creator;
        if (current != null) {
            if (!canSee(current)) {
                throw new BizFieldException("siteNo", "这一家不在你看得见的册子里，不许往它名下落卡");
            }
            // 旧版从当下让开，另摊一层留着翻检——不删
            this.baseMapper.update(null, new UpdateWrapper<TAmssTeamCard>()
                    .eq("id", current.getId())
                    .eq("cur_flag", CUR_FLAG_CURRENT)
                    .set("cur_flag", CUR_FLAG_HISTORY)
                    .set("update_by", operator)
                    .set("update_time", new Date()));
            billNo = current.getBillNo();
            nodeNo = (current.getNodeNo() == null ? 1 : current.getNodeNo()) + 1;
            creator = current.getCreateBy(); // 创建人记立卡那一刻的人，重录不挪
        } else {
            billNo = nextBillNo();
            nodeNo = 1;
            creator = operator;
        }

        // 覆盖程度系统折算回填，谁也别把手伸进来
        int grade = gradeOf(qty, arch);

        // 新摊一版（无论首版还是重录都新立一行，主键另给）
        r.setId(null);
        r.setBillNo(billNo);
        r.setNodeNo(nodeNo);
        r.setSiteId(arch.getId());
        r.setSiteNo(arch.getSiteNo());
        r.setOwnerName(ownerName);
        r.setDriveCode(driveCode);
        r.setWorkType(workType);
        r.setQty(qty);
        r.setGradeLevel(grade);
        r.setContent(null);   // 串走到压死那一步才成形，传入的不算
        r.setStatus(STATUS_TODO);
        r.setCurFlag(CUR_FLAG_CURRENT);
        r.setDelFlag(0);
        r.setCreateBy(creator);
        r.setCreateTime(new Date());
        r.setHandler(operator);
        clearUnusedFields(workType, r);

        this.baseMapper.insert(r);
        return r;
    }

    /** 必填格子随作业类别换，回执点名到格。 */
    private void validateByWorkType(String workType, TAmssTeamCard r) {
        switch (workType) {
            case WORK_HARVEST:
                if (trimToNull(r.getHarvesterModel()) == null) {
                    throw new BizFieldException("harvesterModel", "写机收得给收割机型");
                }
                if (r.getDailyCapacity() == null) {
                    throw new BizFieldException("dailyCapacity", "写机收得给日作业能力");
                }
                break;
            case WORK_SEED:
                if (r.getSeedRows() == null) {
                    throw new BizFieldException("seedRows", "写机播得给播种行数");
                }
                break;
            case WORK_DRY:
                if (r.getBatchCapacity() == null) {
                    throw new BizFieldException("batchCapacity", "写烘干得给批处理量");
                }
                break;
            case WORK_HAUL:
                if (trimToNull(r.getHaulRange()) == null) {
                    throw new BizFieldException("haulRange", "选了转运就得给承运区间，这一格空着卡立不起来");
                }
                if (trimToNull(r.getTractionTool()) == null) {
                    throw new BizFieldException("tractionTool", "写转运得给牵引机具");
                }
                break;
            default:
                break;
        }
    }

    /** 用不上的类别专属格子清空，不把上一类别的残值带进新版。 */
    private void clearUnusedFields(String workType, TAmssTeamCard r) {
        if (!WORK_HARVEST.equals(workType)) {
            r.setHarvesterModel(null);
            r.setDailyCapacity(null);
        }
        if (!WORK_SEED.equals(workType)) {
            r.setSeedRows(null);
        }
        if (!WORK_DRY.equals(workType)) {
            r.setBatchCapacity(null);
        }
        if (!WORK_HAUL.equals(workType)) {
            r.setHaulRange(null);
            r.setTractionTool(null);
        }
    }

    /**
     * 压在界上就往上算：
     * 不到一档→一档；齐一档封顶→二档；齐二档及以上（含冲破三档）→三档。
     */
    private int gradeOf(BigDecimal qty, TAmssCoopDoc arch) {
        if (qty.compareTo(arch.getTh2Max()) >= 0) {
            return 3;
        }
        if (qty.compareTo(arch.getTh1Max()) >= 0) {
            return 2;
        }
        return 1;
    }

    /** 落档只认字号；字号没带而带了底档id的，按id取档再以它字号为准。 */
    private TAmssCoopDoc locateArch(TAmssTeamCard r) {
        TAmssCoopDoc arch = null;
        String siteNo = trimToNull(r.getSiteNo());
        if (siteNo != null) {
            arch = amssCoopDocMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                    .eq("site_no", siteNo).eq("del_flag", 0));
        } else if (r.getSiteId() != null) {
            arch = amssCoopDocMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                    .eq("id", r.getSiteId()).eq("del_flag", 0));
        }
        if (arch == null) {
            throw new BizFieldException("siteNo", "底档字号核对不上，不许只凭合作社名称像就落档");
        }
        return arch;
    }

    /** 卡号系统给号：JK+年月日时分秒+雪花尾号，站里手工编的一律不认。 */
    private String nextBillNo() {
        String tail = SnowflakeIdWorker.getUUID();
        return "JK" + DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS)
                + tail.substring(tail.length() - 5);
    }

    /**
     * 核验串随名录版本走：含卡号、版本号、新版压死当日，
     * 再压一段版本指纹——两版共用一串算错。
     */
    private String buildVerifyCode(TAmssTeamCard card, Date sealedAt) {
        String day = DateUtils.format(sealedAt, DateUtils.YYYY_MM_DD).replace("-", "");
        String digest = MD5Util.encode(card.getBillNo() + "|" + card.getNodeNo());
        return card.getBillNo() + "-V" + card.getNodeNo() + "-" + day + "-" + digest.substring(0, 8);
    }

    private TAmssTeamCard loadVisible(Long id, boolean currentOnly) {
        if (id == null) {
            return null;
        }
        QueryWrapper<TAmssTeamCard> qw = new QueryWrapper<TAmssTeamCard>()
                .eq("id", id).eq("del_flag", 0);
        if (currentOnly) {
            qw.eq("cur_flag", CUR_FLAG_CURRENT);
        }
        TAmssTeamCard card = this.baseMapper.selectOne(qw);
        if (card != null && !canSee(card)) {
            return null;
        }
        return card;
    }

    private boolean canSee(TAmssTeamCard card) {
        return card.getSiteId() != null && siteScope.canSee(card.getSiteId());
    }

    private String currentOperator() {
        try {
            return com.fc.v2.shiro.util.ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            throw new BizFieldException("handler", "取不着本趟承办人登录账号，这趟没法记");
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
