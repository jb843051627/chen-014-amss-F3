package com.fc.v2.amss;

import com.fc.v2.common.exception.amss.AmssBizException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.service.impl.AmssDataScope;
import com.fc.v2.service.impl.TAmssCoopDocServiceImpl;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 底档业务规则单测：自动给号、类别与三道封顶校验、收回封口、辖区视野。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TAmssCoopDocServiceImplTest {

    @Mock
    private TAmssCoopDocMapper coopDocMapper;
    @Mock
    private AmssDataScope amssDataScope;

    private TAmssCoopDocServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TAmssCoopDocServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", coopDocMapper);
        ReflectionTestUtils.setField(service, "amssDataScope", amssDataScope);
    }

    private TAmssCoopDoc draft() {
        TAmssCoopDoc d = new TAmssCoopDoc();
        d.setSiteName("临川县丰收跨区作业队");
        d.setSiteType("跨区作业队");
        d.setRoadName("江原省—江原市—临川县");
        d.setTh1Max(new BigDecimal("80"));
        d.setTh2Max(new BigDecimal("200"));
        d.setTh3Max(new BigDecimal("500"));
        return d;
    }

    @Test
    void insert_systemAssignsSiteNo() {
        TAmssCoopDoc z0 = new TAmssCoopDoc();
        z0.setSiteNo("ZZ00");
        TAmssCoopDoc z1 = new TAmssCoopDoc();
        z1.setSiteNo("ZZ01");
        when(coopDocMapper.selectList(any())).thenReturn(Arrays.asList(z0, z1));
        when(coopDocMapper.insert(any())).thenReturn(1);

        TAmssCoopDoc back = service.insertTAmssCoopDoc(draft(), "zhao");
        ArgumentCaptor<TAmssCoopDoc> captor = ArgumentCaptor.forClass(TAmssCoopDoc.class);
        verify(coopDocMapper).insert(captor.capture());
        assertSame(back, captor.getValue(), "给回的须是落库那一份");
        assertEquals("ZZ02", back.getSiteNo(), "底档字号顺着在册的往下给");
        assertEquals(Integer.valueOf(0), back.getStatus(), "立档即在用");
        assertEquals("zhao", back.getCreateBy());
    }

    @Test
    void insert_manualSiteNo_rejected() {
        TAmssCoopDoc d = draft();
        d.setSiteNo("ZZ99");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssCoopDoc(d, "zhao"));
        assertTrue(e.getMessage().contains("site_no"), "不许手工另编一套");
        verify(coopDocMapper, never()).insert(any());
    }

    @Test
    void insert_requiredFields_named() {
        TAmssCoopDoc noName = draft();
        noName.setSiteName(null);
        AmssBizException e1 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssCoopDoc(noName, "zhao"));
        assertTrue(e1.getMessage().contains("site_name"));

        TAmssCoopDoc noRoad = draft();
        noRoad.setRoadName(null);
        AmssBizException e2 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssCoopDoc(noRoad, "zhao"));
        assertTrue(e2.getMessage().contains("road_name"));

        TAmssCoopDoc noType = draft();
        noType.setSiteType(null);
        assertThrows(AmssBizException.class, () -> service.insertTAmssCoopDoc(noType, "zhao"));
    }

    @Test
    void insert_unknownSiteType_rejected() {
        TAmssCoopDoc d = draft();
        d.setSiteType("黑合作社");
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.insertTAmssCoopDoc(d, "zhao"));
        assertTrue(e.getMessage().contains("site_type"));
    }

    @Test
    void insert_capsMustAscend() {
        TAmssCoopDoc flat = draft();
        flat.setTh2Max(new BigDecimal("80"));
        AmssBizException e1 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssCoopDoc(flat, "zhao"));
        assertTrue(e1.getMessage().contains("th1_max"), "二档不高过一档排不成队");

        TAmssCoopDoc missing = draft();
        missing.setTh2Max(null);
        AmssBizException e2 = assertThrows(AmssBizException.class,
                () -> service.insertTAmssCoopDoc(missing, "zhao"));
        assertTrue(e2.getMessage().contains("th2_max"), "缺哪一档点名哪一档");
    }

    @Test
    void revoke_sealsOneWay() {
        TAmssCoopDoc doc = draft();
        doc.setId(5L);
        doc.setSiteNo("ZZ05");
        doc.setStatus(0);
        when(coopDocMapper.selectOne(any())).thenReturn(doc);
        when(coopDocMapper.updateById(any())).thenReturn(1);

        TAmssCoopDoc back = service.revokeTAmssCoopDoc(5L, "zhao");
        assertEquals(Integer.valueOf(1), back.getStatus(), "收回当场封口");
        assertEquals("zhao", back.getUpdateBy());

        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.revokeTAmssCoopDoc(5L, "zhao"));
        assertTrue(e.getMessage().contains("不收二回"), "收过一回不收二回");
    }

    @Test
    void list_emptyScope_seesNothing() {
        when(amssDataScope.visibleSiteNos()).thenReturn(new HashSet<String>());
        List<TAmssCoopDoc> list = service.selectTAmssCoopDocList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TAmssCoopDoc>());
        assertTrue(list.isEmpty());
        verify(coopDocMapper, never()).selectList(any());
    }

    @Test
    void list_superAccount_passesThrough() {
        when(amssDataScope.visibleSiteNos()).thenReturn(null);
        when(coopDocMapper.selectList(any())).thenReturn(new ArrayList<TAmssCoopDoc>());
        assertDoesNotThrow(() -> service.selectTAmssCoopDocList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TAmssCoopDoc>()));
        verify(coopDocMapper).selectList(any());
    }
}
