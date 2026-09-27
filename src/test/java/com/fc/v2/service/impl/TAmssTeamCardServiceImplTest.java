package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.common.exception.BizFieldException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssTeamCardMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssTeamCard;
import com.fc.v2.service.AmssSiteScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 在机手名录建档卡立账规矩单测：
 * 分级边界（压界往上算、冲破归三档）、版本层、核验串成形不可覆、字段级回执、封口、死键去重。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TAmssTeamCardServiceImplTest {

    @Mock
    private TAmssTeamCardMapper cardMapper;
    @Mock
    private TAmssCoopDocMapper coopDocMapper;
    @Mock
    private AmssSiteScope siteScope;

    @InjectMocks
    private TAmssTeamCardServiceImpl service;

    private TAmssCoopDoc arch;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", cardMapper);
        arch = new TAmssCoopDoc();
        arch.setId(100L);
        arch.setSiteNo("ZZ00");
        arch.setSiteName("临川县支农农机专业合作社");
        arch.setStatus(0);
        arch.setTh1Max(new BigDecimal("80"));
        arch.setTh2Max(new BigDecimal("200"));
        arch.setTh3Max(new BigDecimal("500"));
        when(siteScope.canSee(any())).thenReturn(true);
    }

    private TAmssTeamCard baseCard(String qty) {
        TAmssTeamCard c = new TAmssTeamCard();
        c.setSiteNo("ZZ00");
        c.setOwnerName("李老赶");
        c.setDriveCode("G1");
        c.setWorkType("转运");
        c.setHaulRange("临川—丰城");
        c.setTractionTool("9m 平板挂斗");
        c.setQty(new BigDecimal(qty));
        return c;
    }

    /**
     * 从 wrapper 里抠参数值分辨 mock 在应对哪次查询。
     * 注意：MP 3.4.2 的参数表在 SQL 片段渲染时才回填，先取片段再读参数。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> values(Wrapper<?> w) {
        AbstractWrapper aw = (AbstractWrapper) w;
        aw.getSqlSegment();
        return new ArrayList<>((Collection<Object>) aw.getParamNameValuePairs().values());
    }

    private void stubArchLookup() {
        when(coopDocMapper.selectOne(any())).thenAnswer(inv ->
                values(inv.getArgument(0)).contains("ZZ00") ? arch : null);
        // 首版：查重查不到
        when(cardMapper.selectOne(any())).thenReturn(null);
    }

    // ---------- 覆盖程度：压在界上就往上算，冲破三档归三档 ----------

    @Test
    void grade_boundaries() {
        stubArchLookup();
        assertGrade("79", 1);
        assertGrade("80", 2);    // 齐一档封顶 → 二档
        assertGrade("80.01", 2);
        assertGrade("200", 3);   // 齐二档封顶 → 三档
        assertGrade("500", 3);   // 齐三档封顶 → 三档
        assertGrade("501", 3);   // 冲破三档，统统算三档（不许冒出第4档）
        assertGrade("10000", 3);
    }

    private void assertGrade(String qty, int expect) {
        TAmssTeamCard c = baseCard(qty);
        TAmssTeamCard saved = service.insertTAmssTeamCard(c, "zhangsan");
        assertEquals(expect, saved.getGradeLevel(), "面积 " + qty + " 应折为 " + expect + " 档");
        assertEquals(1, saved.getNodeNo());
        assertEquals(1, saved.getCurFlag());
        assertEquals(0, saved.getStatus());
        assertNotNull(saved.getBillNo());
        assertTrue(saved.getBillNo().startsWith("JK"), "卡号必须系统给号");
        assertEquals("zhangsan", saved.getCreateBy());
        assertEquals("zhangsan", saved.getHandler());
        assertNull(saved.getContent(), "没到压死那一步，核验串不许成形");
    }

    // ---------- 必填格随作业类别换，回执点名到格 ----------

    @Test
    void reject_empty_work_type() {
        TAmssTeamCard c = baseCard("100");
        c.setWorkType(" ");
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(c, "zhangsan"));
        assertEquals("workType", e.getField());
    }

    @Test
    void reject_haul_without_range() {
        TAmssTeamCard c = baseCard("100");
        c.setHaulRange(null);
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(c, "zhangsan"));
        assertEquals("haulRange", e.getField(), "选了转运不给承运栏，回执必须点名承运那一格");
    }

    @Test
    void reject_harvest_without_model() {
        TAmssTeamCard c = baseCard("100");
        c.setWorkType("机收");
        c.setHaulRange(null);
        c.setTractionTool(null);
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(c, "zhangsan"));
        assertEquals("harvesterModel", e.getField());
    }

    @Test
    void reject_seed_without_rows() {
        TAmssTeamCard c = baseCard("100");
        c.setWorkType("机播");
        c.setHaulRange(null);
        c.setTractionTool(null);
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(c, "zhangsan"));
        assertEquals("seedRows", e.getField());
    }

    @Test
    void reject_dry_without_batch() {
        TAmssTeamCard c = baseCard("100");
        c.setWorkType("烘干");
        c.setHaulRange(null);
        c.setTractionTool(null);
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(c, "zhangsan"));
        assertEquals("batchCapacity", e.getField());
    }

    @Test
    void reject_zero_or_negative_qty() {
        stubArchLookup();
        BizFieldException e0 = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(baseCard("0"), "zhangsan"));
        assertEquals("qty", e0.getField());
        BizFieldException en = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(baseCard("-30"), "zhangsan"));
        assertEquals("qty", en.getField());
    }

    @Test
    void reject_bad_drive_code() {
        TAmssTeamCard c = baseCard("100");
        c.setDriveCode("X9");
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(c, "zhangsan"));
        assertEquals("driveCode", e.getField());
    }

    // ---------- 落档凭字号、收回封口 ----------

    @Test
    void reject_unknown_site_no() {
        when(coopDocMapper.selectOne(any())).thenReturn(null);
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(baseCard("100"), "zhangsan"));
        assertEquals("siteNo", e.getField());
    }

    @Test
    void reject_revoked_arch() {
        arch.setStatus(1);
        when(coopDocMapper.selectOne(any())).thenReturn(arch);
        BizFieldException e = assertThrows(BizFieldException.class,
                () -> service.insertTAmssTeamCard(baseCard("100"), "zhangsan"));
        assertEquals("siteNo", e.getField());
        verify(cardMapper, never()).insert(any());
    }

    // ---------- 二回进队：账上只保一行，旧版让开另摊新版，创建人不挪 ----------

    @Test
    @SuppressWarnings("unchecked")
    void second_entry_opens_new_version_keeps_creator() {
        // 首版已立、已由 lisi 承办
        TAmssTeamCard first = baseCard("80");
        first.setId(11L);
        first.setSiteId(100L);
        first.setBillNo("JK2026030112000012345");
        first.setNodeNo(1);
        first.setGradeLevel(2);
        first.setStatus(1);
        first.setCurFlag(1);
        first.setDelFlag(0);
        first.setCreateBy("lisi");
        first.setHandler("lisi");

        when(coopDocMapper.selectOne(any())).thenAnswer(inv ->
                values(inv.getArgument(0)).contains("ZZ00") ? arch : null);
        when(cardMapper.selectOne(any())).thenAnswer(inv -> {
            List<Object> v = values(inv.getArgument(0));
            // 死键查重带 site_no/owner_name/cur_flag/del_flag
            return v.contains("李老赶") && v.contains("ZZ00") ? first : null;
        });

        TAmssTeamCard re = baseCard("210"); // 面积涨了，冲破二档
        TAmssTeamCard v2 = service.insertTAmssTeamCard(re, "wangwu");
        assertNotNull(v2);

        // 旧版从当下让开
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TAmssTeamCard>> uwCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(cardMapper).update(isNull(), uwCaptor.capture());
        String sqlSet = uwCaptor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("cur_flag"), "旧版得从当下让开");
        assertTrue(uwCaptor.getValue().getParamNameValuePairs().containsValue(0), "让开即置 cur_flag=0");

        ArgumentCaptor<TAmssTeamCard> captor = ArgumentCaptor.forClass(TAmssTeamCard.class);
        verify(cardMapper).insert(captor.capture());
        TAmssTeamCard inserted = captor.getValue();

        assertEquals("JK2026030112000012345", inserted.getBillNo(), "二回进队沿用同一张卡号");
        assertEquals(2, inserted.getNodeNo(), "另摊第2版");
        assertEquals(1, inserted.getCurFlag());
        assertEquals(3, inserted.getGradeLevel(), "面积变动重新套封顶");
        assertEquals("lisi", inserted.getCreateBy(), "创建人记立卡那一刻的人，重录不挪");
        assertEquals("wangwu", inserted.getHandler(), "本趟承办人办一笔续一笔");
        assertEquals(0, inserted.getStatus(), "新版从待核对再走一遍");
        assertNull(inserted.getContent());
        assertNotEquals(11L, inserted.getId());
    }

    // ---------- 核验串：压死才成形、成形不可覆、随版本走 ----------

    @Test
    void verify_code_forms_on_seal_and_then_immutable() {
        TAmssTeamCard sealed1 = sealedCard(1);
        when(cardMapper.selectOne(any())).thenReturn(sealed1);

        TAmssTeamCard r1 = service.seal(99L, "zhangsan");
        String code1 = r1.getContent();
        assertNotNull(code1);
        assertTrue(code1.contains("-V1-"), "串随名录版本走");

        // 再压一回：成了形谁也覆不了，连库都不许再写
        reset(cardMapper);
        when(cardMapper.selectOne(any())).thenReturn(sealed1);
        TAmssTeamCard r1again = service.seal(99L, "someoneelse");
        assertEquals(code1, r1again.getContent());
        assertEquals("zhangsan", r1again.getHandler(), "承办人也不许借重复压死挪");
        verify(cardMapper, never()).update(any(), any());
    }

    @Test
    void seal_before_check_rejected() {
        TAmssTeamCard todo = sealedCard(0); // 还在待核对
        when(cardMapper.selectOne(any())).thenReturn(todo);
        BizFieldException e = assertThrows(BizFieldException.class, () -> service.seal(99L, "zhangsan"));
        assertEquals("status", e.getField());
        verify(cardMapper, never()).update(any(), any());
    }

    @Test
    void two_versions_must_have_different_codes() {
        when(cardMapper.selectOne(any())).thenReturn(sealedCard(1));
        String c1 = service.seal(99L, "zhangsan").getContent();
        reset(cardMapper);
        when(cardMapper.selectOne(any())).thenReturn(sealedCard(2));
        String c2 = service.seal(99L, "zhangsan").getContent();
        assertNotEquals(c1, c2, "重录一版串要跟着换，两版共用一串算错");
        assertTrue(c2.contains("-V2-"));
    }

    private TAmssTeamCard sealedCard(int nodeNo) {
        TAmssTeamCard c = new TAmssTeamCard();
        c.setId(99L);
        c.setBillNo("JK2026030112000099999");
        c.setNodeNo(nodeNo);
        c.setSiteId(100L);
        c.setSiteNo("ZZ00");
        c.setOwnerName("李老赶");
        c.setCurFlag(1);
        c.setDelFlag(0);
        c.setStatus(nodeNo == 0 ? 0 : 1);
        c.setHandler("zhangsan");
        return c;
    }
}
