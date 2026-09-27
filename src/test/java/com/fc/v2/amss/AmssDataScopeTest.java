package com.fc.v2.amss;

import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssEntrustMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssEntrust;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.impl.AmssDataScope;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 辖区视野单测：站名与路名对号，本辖与受托合并，以及分页线程副本不被吃掉。
 */
class AmssDataScopeTest {

    private final AmssDataScope scope = new AmssDataScope();

    @AfterEach
    void cleanThread() {
        PageMethod.clearPage();
        ThreadContext.unbindSubject();
    }

    @Test
    void jurisdiction_countyStationMatchesRoadName() {
        assertTrue(scope.inJurisdiction("临川县农机监理站", "江原省—江原市—临川县"),
                "站名含得住路名末段（县）即本辖");
        assertTrue(scope.inJurisdiction("临川县", "江原省—江原市—临川县"));
        assertFalse(scope.inJurisdiction("丰收县农机监理站", "江原省—江原市—临川县"),
                "别县不对号");
        assertFalse(scope.inJurisdiction(null, "江原省—江原市—临川县"));
        assertFalse(scope.inJurisdiction("临川县", null));
        assertFalse(scope.inJurisdiction("", ""));
    }

    @Test
    void visibleSiteNos_noLoginContext_unrestricted() {
        // 无登录上下文（系统内调用）时不滤，也不致炸
        assertNull(scope.visibleSiteNos());
    }

    @Test
    void visibleSiteNos_stationSeesOwnAndEntrusted_pageUnharmed() {
        TAmssCoopDoc own = new TAmssCoopDoc();
        own.setSiteNo("ZZ00");
        own.setRoadName("江原省—江原市—临川县");
        TAmssCoopDoc other = new TAmssCoopDoc();
        other.setSiteNo("ZZ09");
        other.setRoadName("江原省—江原市—丰收县");

        TAmssCoopDocMapper coopMapper = Mockito.mock(TAmssCoopDocMapper.class);
        TAmssEntrustMapper entrustMapper = Mockito.mock(TAmssEntrustMapper.class);
        when(coopMapper.selectList(any())).thenReturn(Arrays.asList(own, other));
        TAmssEntrust entrust = new TAmssEntrust();
        entrust.setSiteNo("ZZ09");
        when(entrustMapper.selectList(any())).thenReturn(Collections.singletonList(entrust));
        ReflectionTestUtils.setField(scope, "amssCoopDocMapper", coopMapper);
        ReflectionTestUtils.setField(scope, "amssEntrustMapper", entrustMapper);

        TSysUser stationUser = new TSysUser();
        stationUser.setUsername("linchuan");
        stationUser.setDepName("临川县农机监理站");
        Subject subject = Mockito.mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(stationUser);
        ThreadContext.bind(subject);

        PageMethod.startPage(2, 10, "id desc");
        try {
            Set<String> visible = scope.visibleSiteNos();
            assertEquals(new java.util.HashSet<String>(Arrays.asList("ZZ00", "ZZ09")),
                    visible, "本辖那几家加上受托那一家，别站的不并列");
            Page<Object> page = PageMethod.getLocalPage();
            assertNotNull(page, "视野解析不许吃掉调用方挂好的分页");
            assertEquals(2, page.getPageNum());
            assertEquals(10, page.getPageSize());
            assertEquals("id desc", page.getOrderBy());
        } finally {
            PageMethod.clearPage();
        }
    }
}
