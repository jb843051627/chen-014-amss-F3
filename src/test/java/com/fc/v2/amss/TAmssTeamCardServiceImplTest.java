package com.fc.v2.amss;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.amss.AmssBizException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssTeamCardMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.impl.AmssDataScope;
import com.fc.v2.service.impl.TAmssTeamCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 建档卡业务规则单测：立卡校验、档位折算、版本与核验串。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TAmssTeamCardServiceImplTest {

    @Mock
    private TAmssTeamCardMapper teamCardMapper;
    @Mock
    private TAmssCoopDocMapper coopDocMapper;
    @Mock
    private AmssDataScope amssDataScope;

    private TAmssTeamCardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TAmssTeamCardServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", teamCardMapper);
        ReflectionTestUtils.setField(service, "amssCoopDocMapper", coopDocMapper);
        ReflectionTestUtils.setField(service, "amssDataScope", amssDataScope);
    }

    /* ---------------- 造数 ---------------- */

    private TAmssCoopDoc liveArchive() {
        TAmssCoopDoc a = new TAmssCoopDoc();
        a.setId(0L);
        a.setSiteNo("ZZ00");
        a.setSiteName("临川县支农农机专业合作社");
        a.setSiteType("农机合作社");
        a.setRoadName("江原省—江原市—临川县");
        a.setTh1Max(new BigDecimal("80"));
        a.setTh2Max(new BigDecimal("200"));
        a.setTh3Max(new BigDecimal("500"));
        a.setStatus(0);
        a.setDelFlag(0);
        return a;
    }

    /** 一张合法机收卡 */
    private TAmssTeamCard harvestCard() {
        TAmssTeamCard c = new TAmssTeamCard();
        c.setSiteNo("ZZ00");
        c.setOwnerName("王五");
        c.setLicenseCode("G1");
        c.setWorkType("机收");
        c.setHarvesterModel("久保田PRO988");
        c.setDailyCapacity(new BigDecimal("120"));
        c.setQty(new BigDecimal("50"));
        return c;
    }

    private void stubLiveArchive() {
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive());
    }

    private void stubNoDupNoBills() {
        when(teamCardMapper.selectCount(any())).thenReturn(0);
        when(teamCardMapper.selectList(any())).thenReturn(new ArrayList<TAmssTeamCard>());
    }

    private TAmssTeamCard savedCard() {
        ArgumentCaptor<TAmssTeamCard> captor = ArgumentCaptor.forClass(TAmssTeamCard.class);
        verify(teamCardMapper, atLeastOnce()).insert(captor.capture());
        return captor.getValue();
    }

    /* ---------------- 立卡：给号与署名 ---------------- */

    @Test
    void save_systemFillsNumberCreatorHandler() {
        stubLiveArchive();
        stubNoDupNoBills();
        when(teamCardMapper.insert(any())).thenReturn(1);

        TAmssTeamCard back = service.insertTAmssTeamCard(harvestCard(), "zhao");
        TAmssTeamCard saved = savedCard();

        assertSame(back, saved, "给回的须是落库那一份");
        assertEquals("JK0001", saved.getBillNo(), "建档卡号系统自动给号");
        assertEquals(Integer.valueOf(1), saved.getNodeNo());
        assertEquals(Integer.valueOf(1), saved.getCurFlag());
        assertEquals(Integer.valueOf(0), saved.getStatus(), "立卡待核对");
        assertNull(saved.getContent(), "未压死核验串不成形");
        assertEquals("zhao", saved.getCreateBy(), "立卡那一刻署名存创建人");
        assertEquals("zhao", saved.getHandleBy(), "办这一趟的承办人");
        assertEquals("ZZ00", saved.getSiteNo());
        assertEquals(Integer.valueOf(0), saved.getSiteId());
        assertEquals(BigDecimal.ZERO, saved.getFineAmt(), "新进面积空着按零记");
    }

    @Test
    void save_originalSignatureStillCallable() {
        stubLiveArchive();
        stubNoDupNoBills();
        when(teamCardMapper.insert(any())).thenReturn(1);
        // 原保存方法签名保持可调用；无登录上下文时承办人落 Task
        assertEquals(1, service.insertTAmssTeamCard(harvestCard()));
        assertEquals("Task", savedCard().getCreateBy());
    }

    @Test
    void save_manualBillNo_rejected() {
        TAmssTeamCard c = harvestCard();
        c.setBillNo("JK9999");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e.getMessage().contains("bill_no"), "回执点名卡号那一格");
        verify(teamCardMapper, never()).insert(any());
    }

    /* ---------------- 立卡：必填随类别 ---------------- */

    @Test
    void save_noWorkType_rejected() {
        TAmssTeamCard c = harvestCard();
        c.setWorkType(null);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e.getMessage().contains("work_type"));
    }

    @Test
    void save_unknownWorkType_rejected() {
        TAmssTeamCard c = harvestCard();
        c.setWorkType("植保");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e.getMessage().contains("work_type"));
    }

    @Test
    void save_harvestRequiresModelAndCapacity() {
        TAmssTeamCard noModel = harvestCard();
        noModel.setHarvesterModel(null);
        AmssBizException e1 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(noModel, "zhao"));
        assertTrue(e1.getMessage().contains("harvester_model"), "点名收割机型那一格");

        TAmssTeamCard noCapacity = harvestCard();
        noCapacity.setDailyCapacity(null);
        AmssBizException e2 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(noCapacity, "zhao"));
        assertTrue(e2.getMessage().contains("daily_capacity"), "点名日作业能力那一格");
    }

    @Test
    void save_seedRequiresRows() {
        TAmssTeamCard c = harvestCard();
        c.setWorkType("机播");
        c.setSeedRows(null);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e.getMessage().contains("seed_rows"), "点名播种行数那一格");
    }

    @Test
    void save_dryRequiresBatchCapacity() {
        TAmssTeamCard c = harvestCard();
        c.setWorkType("烘干");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e.getMessage().contains("batch_capacity"), "点名批处理量那一格");
    }

    @Test
    void save_transportRequiresCarrierAndTraction() {
        TAmssTeamCard noCarrier = harvestCard();
        noCarrier.setWorkType("转运");
        AmssBizException e1 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(noCarrier, "zhao"));
        assertTrue(e1.getMessage().contains("carrier_range"), "选了转运不给承运那一栏，立不起来");

        TAmssTeamCard noTraction = harvestCard();
        noTraction.setWorkType("转运");
        noTraction.setCarrierRange("临川—江原");
        AmssBizException e2 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(noTraction, "zhao"));
        assertTrue(e2.getMessage().contains("traction_machine"), "点名牵引机具那一格");

        TAmssTeamCard full = harvestCard();
        full.setWorkType("转运");
        full.setCarrierRange("临川—江原");
        full.setTractionMachine("东方红LX954");
        stubLiveArchive();
        stubNoDupNoBills();
        when(teamCardMapper.insert(any())).thenReturn(1);
        assertDoesNotThrow(() -> service.insertTAmssTeamCard(full, "zhao"), "转运两栏给齐就立得住");
    }

    @Test
    void save_zeroOrNegativeQty_rejected() {
        TAmssTeamCard c = harvestCard();
        c.setQty(BigDecimal.ZERO);
        AmssBizException e1 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e1.getMessage().contains("qty"), "在册面积填零立不起来");

        c.setQty(new BigDecimal("-3"));
        AmssBizException e2 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e2.getMessage().contains("qty"), "在册面积填负立不起来");

        c.setQty(null);
        assertThrows(AmssBizException.class, () -> service.insertTAmssTeamCard(c, "zhao"));
    }

    @Test
    void save_licenseChecked() {
        TAmssTeamCard c = harvestCard();
        c.setLicenseCode(null);
        assertThrows(AmssBizException.class, () -> service.insertTAmssTeamCard(c, "zhao"));
        c.setLicenseCode("A1");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(c, "zhao"));
        assertTrue(e.getMessage().contains("license_code"));
    }

    /* ---------------- 立卡：落档与去重 ---------------- */

    @Test
    void save_archiveMiss_rejected() {
        when(coopDocMapper.selectOne(any())).thenReturn(null);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(harvestCard(), "zhao"));
        assertTrue(e.getMessage().contains("site_no") && e.getMessage().contains("查无此档"),
                "落档凭字号核对，查无此档立不起来");
    }

    @Test
    void save_archiveRevoked_rejected() {
        TAmssCoopDoc revoked = liveArchive();
        revoked.setStatus(1);
        when(coopDocMapper.selectOne(any())).thenReturn(revoked);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(harvestCard(), "zhao"));
        assertTrue(e.getMessage().contains("收回封口"), "底档收回封口，新卡不许再挂");
        verify(teamCardMapper, never()).insert(any());
    }

    @Test
    void save_duplicateOwner_rejected() {
        stubLiveArchive();
        when(teamCardMapper.selectCount(any())).thenReturn(1);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssTeamCard(harvestCard(), "zhao"));
        assertTrue(e.getMessage().contains("owner_name") && e.getMessage().contains("只保一行"),
                "认死底档字号加机主，账上只保一行");
        verify(teamCardMapper, never()).insert(any());
    }

    /* ---------------- 档位：压界上算，破三档算三 ---------------- */

    @Test
    void save_gradeBoundaries() {
        stubLiveArchive();
        stubNoDupNoBills();
        when(teamCardMapper.insert(any())).thenReturn(1);

        BigDecimal[] qtys = {
                new BigDecimal("50"), new BigDecimal("79.99"), new BigDecimal("80"),
                new BigDecimal("150"), new BigDecimal("200"), new BigDecimal("500"),
                new BigDecimal("600")};
        int[] want = {1, 1, 2, 2, 3, 3, 3};
        for (BigDecimal q : qtys) {
            TAmssTeamCard c = harvestCard();
            c.setQty(q);
            service.insertTAmssTeamCard(c, "zhao");
        }
        ArgumentCaptor<TAmssTeamCard> captor = ArgumentCaptor.forClass(TAmssTeamCard.class);
        verify(teamCardMapper, times(qtys.length)).insert(captor.capture());
        List<TAmssTeamCard> all = captor.getAllValues();
        for (int i = 0; i < want.length; i++) {
            assertEquals(want[i], all.get(i).getGradeLevel(),
                    "面积 " + qtys[i] + " 应套 " + want[i] + " 档（压界上算，破三档算三）");
        }
    }

    @Test
    void save_gradeNeverTakenFromPage() {
        stubLiveArchive();
        stubNoDupNoBills();
        when(teamCardMapper.insert(any())).thenReturn(1);
        TAmssTeamCard c = harvestCard();
        c.setGradeLevel(3);
        service.insertTAmssTeamCard(c, "zhao");
        assertEquals(1, savedCard().getGradeLevel(), "覆盖程度系统折算回填，手工伸不进那一格");
    }

    /* ---------------- 改版：新版新行，旧版让开 ---------------- */

    private TAmssTeamCard currentCardV1() {
        TAmssTeamCard c = harvestCard();
        c.setId(10L);
        c.setBillNo("JK0001");
        c.setNodeNo(1);
        c.setCurFlag(1);
        c.setStatus(2);
        c.setContent("HY-JK0001-V1-20260901-ABCDEF");
        c.setGradeLevel(1);
        c.setCreateBy("zhao");
        c.setHandleBy("zhao");
        c.setFineAmt(BigDecimal.ZERO);
        return c;
    }

    @Test
    void reRecord_newVersionKeepsCreatorMovesHandler() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        stubLiveArchive();
        when(teamCardMapper.selectCount(any())).thenReturn(0);
        when(teamCardMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(teamCardMapper.insert(any())).thenReturn(1);

        TAmssTeamCard changes = new TAmssTeamCard();
        changes.setQty(new BigDecimal("250"));
        TAmssTeamCard next = service.reRecordTAmssTeamCard(10L, changes, "li");

        assertEquals("JK0001", next.getBillNo(), "卡号跟着卡走");
        assertEquals(Integer.valueOf(2), next.getNodeNo(), "版本号进一");
        assertEquals(Integer.valueOf(1), next.getCurFlag());
        assertEquals(Integer.valueOf(0), next.getStatus(), "新版重新待核对");
        assertNull(next.getContent(), "新版未压死，核验串不成形");
        assertEquals(Integer.valueOf(3), next.getGradeLevel(), "面积增减重新套档");
        assertEquals(new BigDecimal("250"), next.getQty());
        assertEquals("zhao", next.getCreateBy(), "创建人不跟着挪");
        assertEquals("li", next.getHandleBy(), "办这一趟的承办人续记");
        assertEquals("王五", next.getOwnerName());
        assertEquals("久保田PRO988", next.getHarvesterModel(), "空着的格子承旧版");

        ArgumentCaptor<UpdateWrapper> uw = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(teamCardMapper).update(isNull(), uw.capture());
        assertTrue(uw.getValue().getSqlSet().contains("cur_flag"), "旧版从当下那一处让开");
        assertEquals("HY-JK0001-V1-20260901-ABCDEF", cur.getContent(), "旧版那一条留原样");
    }

    @Test
    void reRecord_historicalVersion_rejected() {
        TAmssTeamCard cur = currentCardV1();
        cur.setCurFlag(0);
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.reRecordTAmssTeamCard(10L, new TAmssTeamCard(), "li"));
        assertTrue(e.getMessage().contains("历史版"), "历史版只供翻检，不容就它动手");
    }

    @Test
    void reRecord_ownerOrBillNoChange_rejected() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive());

        TAmssTeamCard c1 = new TAmssTeamCard();
        c1.setOwnerName("李六");
        AmssBizException e1 = assertThrows(AmssBizException.class,
                () -> service.reRecordTAmssTeamCard(10L, c1, "li"));
        assertTrue(e1.getMessage().contains("owner_name"), "改版不换主");

        TAmssTeamCard c2 = new TAmssTeamCard();
        c2.setBillNo("JK0002");
        AmssBizException e2 = assertThrows(AmssBizException.class,
                () -> service.reRecordTAmssTeamCard(10L, c2, "li"));
        assertTrue(e2.getMessage().contains("bill_no"), "改版不换号");
    }

    @Test
    void reRecord_oldArchiveSealed_rejected() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        TAmssCoopDoc revoked = liveArchive();
        revoked.setStatus(1);
        when(coopDocMapper.selectOne(any())).thenReturn(revoked);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.reRecordTAmssTeamCard(10L, new TAmssTeamCard(), "li"));
        assertTrue(e.getMessage().contains("只查不改"), "底档收回封口，旧卡只查不改");
    }

    @Test
    void reRecord_targetArchiveSealed_rejected() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        TAmssCoopDoc revoked = liveArchive();
        revoked.setSiteNo("ZZ01");
        revoked.setStatus(1);
        // 先查本家（在用），再查目标（已收回）
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive(), revoked);
        TAmssTeamCard changes = new TAmssTeamCard();
        changes.setSiteNo("ZZ01");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.reRecordTAmssTeamCard(10L, changes, "li"));
        assertTrue(e.getMessage().contains("收回封口"), "改挂的目标底档也须在用");
    }

    @Test
    void reRecord_moveToOtherArchive_rechecksDedupAndGrade() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        TAmssCoopDoc other = liveArchive();
        other.setSiteNo("ZZ02");
        other.setTh1Max(new BigDecimal("100"));
        other.setTh2Max(new BigDecimal("300"));
        other.setTh3Max(new BigDecimal("900"));
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive(), other);
        when(teamCardMapper.selectCount(any())).thenReturn(1);
        TAmssTeamCard changes = new TAmssTeamCard();
        changes.setSiteNo("ZZ02");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.reRecordTAmssTeamCard(10L, changes, "li"));
        assertTrue(e.getMessage().contains("只保一行"), "改挂别家，那边也冒不出第二行");
    }

    /* ---------------- 推进与核验串 ---------------- */

    @Test
    void advance_pendingToChecked_noCodeYet() {
        TAmssTeamCard cur = currentCardV1();
        cur.setStatus(0);
        cur.setContent(null);
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive());
        when(teamCardMapper.updateById(any())).thenReturn(1);

        TAmssTeamCard back = service.advanceTAmssTeamCard(10L, "li");
        assertEquals(Integer.valueOf(1), back.getStatus(), "待核对推进为已核对");
        assertNull(back.getContent(), "未到钉住那一步，串不成形");
        assertEquals("li", back.getHandleBy());
    }

    @Test
    void advance_checkedToPinned_formsCode() {
        TAmssTeamCard cur = currentCardV1();
        cur.setStatus(1);
        cur.setContent(null);
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive());
        when(teamCardMapper.updateById(any())).thenReturn(1);

        TAmssTeamCard back = service.advanceTAmssTeamCard(10L, "li");
        assertEquals(Integer.valueOf(2), back.getStatus(), "已核对推进为已压死");
        assertNotNull(back.getContent(), "钉住那一步核验串成形");
        assertTrue(back.getContent().matches("HY-JK0001-V1-\\d{8}-[0-9A-F]{6}"),
                "串上带卡号、版本与日子：" + back.getContent());
    }

    @Test
    void advance_pinned_noFurther() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive());
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.advanceTAmssTeamCard(10L, "li"));
        assertTrue(e.getMessage().contains("已压死"));
    }

    @Test
    void advance_neverOverwritesFormedCode() {
        TAmssTeamCard cur = currentCardV1();
        cur.setStatus(1);
        cur.setContent("HY-JK0001-V1-20260901-ABCDEF");
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        when(coopDocMapper.selectOne(any())).thenReturn(liveArchive());
        when(teamCardMapper.updateById(any())).thenReturn(1);
        TAmssTeamCard back = service.advanceTAmssTeamCard(10L, "li");
        assertEquals("HY-JK0001-V1-20260901-ABCDEF", back.getContent(), "成了形谁也覆不了它");
    }

    @Test
    void reRecord_thenPin_newVersionGetsOwnCode() {
        TAmssTeamCard cur = currentCardV1();
        when(teamCardMapper.selectOne(any())).thenReturn(cur);
        stubLiveArchive();
        when(teamCardMapper.selectCount(any())).thenReturn(0);
        when(teamCardMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);
        when(teamCardMapper.insert(any())).thenReturn(1);

        TAmssTeamCard next = service.reRecordTAmssTeamCard(10L, new TAmssTeamCard(), "li");
        assertNull(next.getContent());
        String oldCode = cur.getContent();

        // 新版走到钉住：成形自己的一串，与旧版不是一串
        next.setId(11L);
        when(teamCardMapper.selectOne(any())).thenReturn(next);
        when(teamCardMapper.updateById(any())).thenReturn(1);
        service.advanceTAmssTeamCard(11L, "li");
        TAmssTeamCard pinned = service.advanceTAmssTeamCard(11L, "li");

        assertNotNull(pinned.getContent());
        assertNotEquals(oldCode, pinned.getContent(), "两版共用一串算错");
        assertTrue(pinned.getContent().contains("-V2-"), "串跟着名录版本走");
        assertEquals("HY-JK0001-V1-20260901-ABCDEF", cur.getContent(), "旧版那一条留原样");
    }

    /* ---------------- 不许绕过的捷径 ---------------- */

    @Test
    void update_inPlaceRewrite_forbidden() {
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.updateTAmssTeamCard(harvestCard()));
        assertTrue(e.getMessage().contains("就地重写"), "不容就地重写既有那一版");
    }

    @Test
    void delete_forbidden() {
        assertThrows(AmssBizException.class, () -> service.deleteTAmssTeamCardByIds("1,2"));
        assertThrows(AmssBizException.class, () -> service.deleteTAmssTeamCardById(1L));
    }

    /* ---------------- 列表与翻检 ---------------- */

    @Test
    void list_emptyScope_seesNothing() {
        when(amssDataScope.visibleSiteNos()).thenReturn(new HashSet<String>());
        List<TAmssTeamCard> list = service.selectTAmssTeamCardList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TAmssTeamCard>());
        assertTrue(list.isEmpty(), "瞧不见就是瞧不见，别站的册不一并列给他挑");
        verify(teamCardMapper, never()).selectList(any());
    }

    @Test
    void list_superAccount_passesThrough() {
        when(amssDataScope.visibleSiteNos()).thenReturn(null);
        when(teamCardMapper.selectList(any())).thenReturn(
                Collections.singletonList(new TAmssTeamCard()));
        List<TAmssTeamCard> list = service.selectTAmssTeamCardList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TAmssTeamCard>());
        assertEquals(1, list.size(), "上级账号看全辖");
    }

    @Test
    void history_requiresBillNo() {
        assertThrows(AmssBizException.class, () -> service.selectTAmssTeamCardHistory(" "));
    }
}
