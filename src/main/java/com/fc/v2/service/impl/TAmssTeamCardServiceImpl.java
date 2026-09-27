package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.exception.amss.AmssBizException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssTeamCardMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.ITAmssTeamCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.MD5Util;
import com.fc.v2.util.SerialNoUtil;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 跨区作业队在机手名录建档卡 Service业务层处理
 * <p>
 * 立卡、改版、压死都只在业务服务层这一处判定，页面上不设绕过它的捷径：
 * <ul>
 *   <li>落档凭底档字号核对，只看名称像不像不认；底档收回即封口，新卡不许再挂、旧卡只查不改。</li>
 *   <li>必填格子随作业类别走：机收要收割机型与日作业能力，机播要播种行数，
 *       烘干要批处理量，转运要承运区间与牵引机具；回执点名出岔的格子。</li>
 *   <li>在册面积填零、填负立不起来；覆盖程度系统折算回填——压在界上就往上算，
 *       冲破三档封顶统统算三档，谁也别把手伸进那一格。</li>
 *   <li>同一底档名下同一机主账上只保一行，认死底档字号加机主，不分谁先到谁后到。</li>
 *   <li>改版另起一行：旧版让开留档，摆出来的只许最新一版；创建人不挪，承办人办一笔续一笔。</li>
 *   <li>核验串压死方成形、成形不可覆，跟着名录版本走，两版共用一串算错。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TAmssTeamCardServiceImpl extends ServiceImpl<TAmssTeamCardMapper, TAmssTeamCard>
        implements ITAmssTeamCardService {

    /** 建档卡号前缀（系统自动给号） */
    public static final String BILL_NO_PREFIX = "JK";

    /** 作业类别四种 */
    public static final Set<String> WORK_TYPES = new HashSet<String>(
            Arrays.asList("机收", "机播", "烘干", "转运"));

    /** 准驾代号：G1、K1、L、R、S 各管各的机型 */
    public static final Set<String> LICENSE_CODES = new HashSet<String>(
            Arrays.asList("G1", "K1", "L", "R", "S"));

    /** 进展：待核对 */
    public static final int STATUS_PENDING = 0;
    /** 进展：已核对 */
    public static final int STATUS_CHECKED = 1;
    /** 进展：已压死（钉住，核验串成形） */
    public static final int STATUS_PINNED = 2;

    @Autowired
    private TAmssCoopDocMapper amssCoopDocMapper;

    @Autowired
    private AmssDataScope amssDataScope;

    @Override
    public TAmssTeamCard selectTAmssTeamCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TAmssTeamCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TAmssTeamCard> selectTAmssTeamCardList(Wrapper<TAmssTeamCard> queryWrapper) {
        QueryWrapper<TAmssTeamCard> wrapper = queryWrapper instanceof QueryWrapper
                ? (QueryWrapper<TAmssTeamCard>) queryWrapper : new QueryWrapper<TAmssTeamCard>();
        // 摆出来的只许是最新的一版
        wrapper.eq("del_flag", 0);
        wrapper.eq("cur_flag", 1);
        Set<String> visible = amssDataScope.visibleSiteNos();
        if (visible != null) {
            if (visible.isEmpty()) {
                return new ArrayList<TAmssTeamCard>();
            }
            wrapper.in("site_no", visible);
        }
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public List<TAmssTeamCard> selectTAmssTeamCardHistory(String billNo) {
        if (StringUtils.isEmpty(billNo)) {
            throw AmssBizException.required("bill_no", "建档卡号");
        }
        QueryWrapper<TAmssTeamCard> wrapper = new QueryWrapper<TAmssTeamCard>();
        wrapper.eq("bill_no", billNo);
        wrapper.eq("del_flag", 0);
        Set<String> visible = amssDataScope.visibleSiteNos();
        if (visible != null) {
            if (visible.isEmpty()) {
                return new ArrayList<TAmssTeamCard>();
            }
            wrapper.in("site_no", visible);
        }
        wrapper.orderByAsc("node_no");
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTAmssTeamCard(TAmssTeamCard record) {
        return insertTAmssTeamCard(record, ShiroUtils.getLoginName()) != null ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssTeamCard insertTAmssTeamCard(TAmssTeamCard record, String operator) {
        if (record == null) {
            throw new AmssBizException("卡面为空：立卡内容一样没有，立不起来");
        }
        // 建档卡号一律系统自动给号，不许手工另编一套
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            throw new AmssBizException("bill_no",
                    "建档卡号（bill_no）由系统自动给号，不许手工另编，填了也不存");
        }
        validateBusiness(record);

        // 落档凭字号核对；只看合作社名称像不像就认定是这一家的，不认
        TAmssCoopDoc arch = requireLiveArchive(record.getSiteNo());
        // 同一底档名下同一机主，账上只保一行
        ensureOwnerUnique(record.getSiteNo(), record.getOwnerName(), null);

        record.setBillNo(nextBillNo());
        record.setNodeNo(1);
        record.setCurFlag(1);
        record.setStatus(STATUS_PENDING);
        record.setContent(null);
        record.setGradeLevel(gradeOf(record.getQty(), arch));
        record.setSiteId(fitsInt(arch.getId()));
        record.setDelFlag(0);
        record.setCreateBy(operatorOrDefault(operator));
        record.setHandleBy(operatorOrDefault(operator));
        this.baseMapper.insert(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssTeamCard reRecordTAmssTeamCard(Long id, TAmssTeamCard changes, String operator) {
        TAmssTeamCard cur = requireCurrentVersion(id);
        // 底档收回即封口：先前挂过的仍旧查得着，回头改又是另一回事
        requireUnsealed(cur.getSiteNo());

        // 这一版要改的格子覆上旧版，空着的承旧
        TAmssTeamCard next = overlay(cur, changes);
        // 机主是这张卡认死的身份，改版不换主
        if (changes != null && StringUtils.isNotEmpty(changes.getOwnerName())
                && !changes.getOwnerName().equals(cur.getOwnerName())) {
            throw new AmssBizException("owner_name",
                    "机主（owner_name）是这张卡认死的身份，改版不换主；换主请另立新卡");
        }
        next.setOwnerName(cur.getOwnerName());
        // 建档卡号跟着卡走，改版不换号
        if (changes != null && StringUtils.isNotEmpty(changes.getBillNo())
                && !changes.getBillNo().equals(cur.getBillNo())) {
            throw new AmssBizException("bill_no",
                    "建档卡号（bill_no）跟着卡走，改版不换号");
        }
        next.setBillNo(cur.getBillNo());
        // 改挂到别的底档名下：凭字号核对，目标底档必须在用
        String targetSiteNo = changes != null && StringUtils.isNotEmpty(changes.getSiteNo())
                ? changes.getSiteNo() : cur.getSiteNo();
        TAmssCoopDoc targetArch = requireLiveArchive(targetSiteNo);
        next.setSiteNo(targetSiteNo);
        next.setSiteId(fitsInt(targetArch.getId()));

        validateBusiness(next);
        // 改挂别家时，那边账上也不能冒出第二行
        ensureOwnerUnique(targetSiteNo, cur.getOwnerName(), cur.getBillNo());

        next.setNodeNo(cur.getNodeNo() == null ? 2 : cur.getNodeNo() + 1);
        next.setCurFlag(1);
        next.setStatus(STATUS_PENDING);
        next.setContent(null);
        // 面积有增减、或者改挂到别的底档名下，都得重新套一次
        next.setGradeLevel(gradeOf(next.getQty(), targetArch));
        next.setDelFlag(0);
        next.setCreateBy(cur.getCreateBy());
        next.setHandleBy(operatorOrDefault(operator));

        // 旧版不删，从当下那一处让开，另摊一层留着事后翻检
        this.baseMapper.update(null, new UpdateWrapper<TAmssTeamCard>()
                .eq("id", cur.getId())
                .set("cur_flag", 0));
        this.baseMapper.insert(next);
        return next;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TAmssTeamCard advanceTAmssTeamCard(Long id, String operator) {
        TAmssTeamCard cur = requireCurrentVersion(id);
        requireUnsealed(cur.getSiteNo());
        int status = cur.getStatus() == null ? STATUS_PENDING : cur.getStatus();
        if (status >= STATUS_PINNED) {
            throw new AmssBizException("status",
                    "卡已压死（status=2），核验串已成形，无路可进");
        }
        cur.setStatus(status + 1);
        if (cur.getStatus() == STATUS_PINNED && StringUtils.isEmpty(cur.getContent())) {
            // 卡走到钉住的那一步核验串才成形；成了形谁也覆不了它
            cur.setContent(buildVerifyCode(cur));
        }
        cur.setHandleBy(operatorOrDefault(operator));
        this.baseMapper.updateById(cur);
        return cur;
    }

    @Override
    public int updateTAmssTeamCard(TAmssTeamCard record) {
        throw new AmssBizException(
                "建档卡不容就地重写既有那一版：面积增减、改挂、换类别都走重录改版（reRecord）");
    }

    @Override
    public int deleteTAmssTeamCardByIds(String ids) {
        throw new AmssBizException("建档卡只进不出：旧版留档翻检，名册不容删除");
    }

    @Override
    public int deleteTAmssTeamCardById(Long id) {
        throw new AmssBizException("建档卡只进不出：旧版留档翻检，名册不容删除");
    }

    /* ------------------------------------------------------------------ */
    /* 立卡与改版共用的判定                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * 必填格子随作业类别在这一处判定；回执点名列在哪一格出的岔。
     */
    private void validateBusiness(TAmssTeamCard record) {
        if (StringUtils.isEmpty(record.getSiteNo())) {
            throw AmssBizException.required("site_no", "所属底档字号");
        }
        if (StringUtils.isEmpty(record.getOwnerName())) {
            throw AmssBizException.required("owner_name", "机主姓名");
        }
        if (StringUtils.isEmpty(record.getWorkType())) {
            throw AmssBizException.required("work_type", "作业类别");
        }
        if (!WORK_TYPES.contains(record.getWorkType())) {
            throw new AmssBizException("work_type",
                    "作业类别（work_type）只认机收、机播、烘干、转运四种，「"
                            + record.getWorkType() + "」不在册");
        }
        // 类别一换，非填不可的格子跟着换
        if ("机收".equals(record.getWorkType())) {
            if (StringUtils.isEmpty(record.getHarvesterModel())) {
                throw AmssBizException.requiredByType("harvester_model", "收割机型", "机收");
            }
            if (record.getDailyCapacity() == null) {
                throw AmssBizException.requiredByType("daily_capacity", "日作业能力", "机收");
            }
        } else if ("机播".equals(record.getWorkType())) {
            if (record.getSeedRows() == null) {
                throw AmssBizException.requiredByType("seed_rows", "播种行数", "机播");
            }
        } else if ("烘干".equals(record.getWorkType())) {
            if (record.getBatchCapacity() == null) {
                throw AmssBizException.requiredByType("batch_capacity", "批处理量", "烘干");
            }
        } else if ("转运".equals(record.getWorkType())) {
            if (StringUtils.isEmpty(record.getCarrierRange())) {
                throw AmssBizException.requiredByType("carrier_range", "承运区间", "转运");
            }
            if (StringUtils.isEmpty(record.getTractionMachine())) {
                throw AmssBizException.requiredByType("traction_machine", "牵引机具", "转运");
            }
        }
        if (StringUtils.isEmpty(record.getLicenseCode())) {
            throw AmssBizException.required("license_code", "机手准驾代号");
        }
        if (!LICENSE_CODES.contains(record.getLicenseCode())) {
            throw new AmssBizException("license_code",
                    "机手准驾代号（license_code）只认 G1、K1、L、R、S，「"
                            + record.getLicenseCode() + "」不在列");
        }
        // 在册面积填零、填负，立不起来
        if (record.getQty() == null) {
            throw AmssBizException.required("qty", "在队服务面积");
        }
        if (record.getQty().signum() <= 0) {
            throw new AmssBizException("qty",
                    "在队服务面积（qty）填零、填负立不起来，实报 " + record.getQty() + " 亩");
        }
        if (record.getFineAmt() == null) {
            record.setFineAmt(BigDecimal.ZERO);
        }
        if (record.getFineAmt().signum() < 0) {
            throw new AmssBizException("fine_amt",
                    "本年度新进队服务面积（fine_amt）填不成负的，实报 " + record.getFineAmt() + " 亩");
        }
    }

    /**
     * 落档凭字号核对：字号查无此档不许挂；底档收回封口，新卡不许再往它名下挂。
     */
    private TAmssCoopDoc requireLiveArchive(String siteNo) {
        TAmssCoopDoc arch = amssCoopDocMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("site_no", siteNo)
                .eq("del_flag", 0));
        if (arch == null) {
            throw new AmssBizException("site_no",
                    "落档凭字号核对：底档字号（site_no）「" + siteNo + "」查无此档；"
                            + "只看合作社名称像不像就认定是这一家的，不认");
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            throw new AmssBizException("site_no",
                    "底档「" + siteNo + "」已收回封口，新卡不许再往它名下挂");
        }
        return arch;
    }

    /**
     * 底档收回即封口：名下旧卡仍旧查得着，回头改又是另一回事——只查不改。
     */
    private void requireUnsealed(String siteNo) {
        TAmssCoopDoc arch = amssCoopDocMapper.selectOne(new QueryWrapper<TAmssCoopDoc>()
                .eq("site_no", siteNo)
                .eq("del_flag", 0));
        if (arch != null && arch.getStatus() != null && arch.getStatus() == 1) {
            throw new AmssBizException("site_no",
                    "底档「" + siteNo + "」已收回封口，名下旧卡只查不改");
        }
    }

    /**
     * 同一底档名下同一机主，账上只保一行：认死底档字号加机主这两样，不分谁先到谁后到。
     *
     * @param excludeBillNo 改版改挂时排除本卡各版，只挡别家的第二行
     */
    private void ensureOwnerUnique(String siteNo, String ownerName, String excludeBillNo) {
        QueryWrapper<TAmssTeamCard> wrapper = new QueryWrapper<TAmssTeamCard>()
                .eq("site_no", siteNo)
                .eq("owner_name", ownerName)
                .eq("cur_flag", 1)
                .eq("del_flag", 0);
        if (StringUtils.isNotEmpty(excludeBillNo)) {
            wrapper.ne("bill_no", excludeBillNo);
        }
        Integer dup = this.baseMapper.selectCount(wrapper);
        if (dup != null && dup > 0) {
            throw new AmssBizException("owner_name",
                    "机主（owner_name）「" + ownerName + "」在底档「" + siteNo
                            + "」名下已立卡在册：账上只保一行，不分谁先到谁后到；要改走重录改版");
        }
    }

    /**
     * 覆盖程度系统折算回填，谁也别把手伸进那一格。
     * 压在界上就往上算：与一档封顶齐平算二档，与二档齐平算三档；
     * 冲破三档封顶的，统统算三档。
     */
    private int gradeOf(BigDecimal qty, TAmssCoopDoc arch) {
        if (arch.getTh1Max() == null || arch.getTh2Max() == null || arch.getTh3Max() == null) {
            throw new AmssBizException("site_no",
                    "底档「" + arch.getSiteNo() + "」三道封顶未配齐，套不了档");
        }
        if (qty.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (qty.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        return 3;
    }

    /**
     * 核验串：卡走到钉住的那一步才成形，跟着名录版本走——
     * 重录一版，串上的日子换成新版那一天，旧版那一条留原样，两版共用一串算错。
     */
    private String buildVerifyCode(TAmssTeamCard card) {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String raw = card.getBillNo() + "|" + card.getNodeNo() + "|" + day + "|" + card.getId();
        String check = MD5Util.encode(raw).substring(0, 6).toUpperCase();
        return "HY-" + card.getBillNo() + "-V" + card.getNodeNo() + "-" + day + "-" + check;
    }

    /**
     * 取当前版：历史版只供翻检，不容就它动手。
     */
    private TAmssTeamCard requireCurrentVersion(Long id) {
        if (id == null) {
            throw AmssBizException.required("id", "卡主键");
        }
        TAmssTeamCard card = this.baseMapper.selectOne(new QueryWrapper<TAmssTeamCard>()
                .eq("id", id)
                .eq("del_flag", 0));
        if (card == null) {
            throw new AmssBizException("id", "建档卡查无此卡：id=" + id);
        }
        if (card.getCurFlag() == null || card.getCurFlag() != 1) {
            throw new AmssBizException("id",
                    "这一行是历史版，只供翻检；要动手就当前版（bill_no=" + card.getBillNo() + "）");
        }
        return card;
    }

    /**
     * 这一版要改的格子覆上旧版，空着的格子承旧版。
     */
    private TAmssTeamCard overlay(TAmssTeamCard cur, TAmssTeamCard changes) {
        TAmssTeamCard next = new TAmssTeamCard();
        next.setWorkType(cur.getWorkType());
        next.setHarvesterModel(cur.getHarvesterModel());
        next.setDailyCapacity(cur.getDailyCapacity());
        next.setSeedRows(cur.getSeedRows());
        next.setBatchCapacity(cur.getBatchCapacity());
        next.setCarrierRange(cur.getCarrierRange());
        next.setTractionMachine(cur.getTractionMachine());
        next.setLicenseCode(cur.getLicenseCode());
        next.setQty(cur.getQty());
        next.setFineAmt(cur.getFineAmt());
        next.setRemark(cur.getRemark());
        if (changes != null) {
            if (StringUtils.isNotEmpty(changes.getWorkType())) {
                next.setWorkType(changes.getWorkType());
            }
            if (StringUtils.isNotEmpty(changes.getHarvesterModel())) {
                next.setHarvesterModel(changes.getHarvesterModel());
            }
            if (changes.getDailyCapacity() != null) {
                next.setDailyCapacity(changes.getDailyCapacity());
            }
            if (changes.getSeedRows() != null) {
                next.setSeedRows(changes.getSeedRows());
            }
            if (changes.getBatchCapacity() != null) {
                next.setBatchCapacity(changes.getBatchCapacity());
            }
            if (StringUtils.isNotEmpty(changes.getCarrierRange())) {
                next.setCarrierRange(changes.getCarrierRange());
            }
            if (StringUtils.isNotEmpty(changes.getTractionMachine())) {
                next.setTractionMachine(changes.getTractionMachine());
            }
            if (StringUtils.isNotEmpty(changes.getLicenseCode())) {
                next.setLicenseCode(changes.getLicenseCode());
            }
            if (changes.getQty() != null) {
                next.setQty(changes.getQty());
            }
            if (changes.getFineAmt() != null) {
                next.setFineAmt(changes.getFineAmt());
            }
            if (StringUtils.isNotEmpty(changes.getRemark())) {
                next.setRemark(changes.getRemark());
            }
        }
        return next;
    }

    /**
     * 建档卡号系统自动给号：JK 顺推。
     */
    private String nextBillNo() {
        List<TAmssTeamCard> all = this.baseMapper.selectList(new QueryWrapper<TAmssTeamCard>()
                .select("bill_no")
                .likeRight("bill_no", BILL_NO_PREFIX));
        List<String> nos = new ArrayList<String>();
        for (TAmssTeamCard c : all) {
            nos.add(c.getBillNo());
        }
        return SerialNoUtil.next(BILL_NO_PREFIX, 4, nos);
    }

    /**
     * site_id 是 int 栏：底档主键放得下才落，放不下只认字号。
     */
    private Integer fitsInt(Long id) {
        if (id == null || id > Integer.MAX_VALUE || id < Integer.MIN_VALUE) {
            return null;
        }
        return id.intValue();
    }

    private String operatorOrDefault(String operator) {
        return StringUtils.isNotEmpty(operator) ? operator : "system";
    }
}
