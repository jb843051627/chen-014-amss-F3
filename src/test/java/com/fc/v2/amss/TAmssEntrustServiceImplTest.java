package com.fc.v2.amss;

import com.fc.v2.common.exception.amss.AmssBizException;
import com.fc.v2.mapper.auto.TAmssCoopDocMapper;
import com.fc.v2.mapper.auto.TAmssEntrustMapper;
import com.fc.v2.model.auto.TAmssCoopDoc;
import com.fc.v2.model.auto.TAmssEntrust;
import com.fc.v2.service.impl.TAmssEntrustServiceImpl;
import com.fc.v2.util.SerialNoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 受托登记与给号工具单测。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TAmssEntrustServiceImplTest {

    @Mock
    private TAmssEntrustMapper entrustMapper;
    @Mock
    private TAmssCoopDocMapper coopDocMapper;

    private TAmssEntrustServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TAmssEntrustServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", entrustMapper);
        ReflectionTestUtils.setField(service, "amssCoopDocMapper", coopDocMapper);
    }

    @Test
    void grant_registersOnce() {
        when(coopDocMapper.selectOne(any())).thenReturn(new TAmssCoopDoc());
        when(entrustMapper.selectCount(any())).thenReturn(0);
        when(entrustMapper.insert(any())).thenReturn(1);
        TAmssEntrust back = service.grant("ZZ00", "邻县农机监理站", "admin");
        assertEquals("ZZ00", back.getSiteNo());
        assertEquals(Integer.valueOf(0), back.getStatus(), "登记即在托");
    }

    @Test
    void grant_archiveMiss_rejected() {
        when(coopDocMapper.selectOne(any())).thenReturn(null);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.grant("ZZ99", "邻县农机监理站", "admin"));
        assertTrue(e.getMessage().contains("site_no"));
    }

    @Test
    void grant_duplicate_rejected() {
        when(coopDocMapper.selectOne(any())).thenReturn(new TAmssCoopDoc());
        when(entrustMapper.selectCount(any())).thenReturn(1);
        AmssBizException e = assertThrows(AmssBizException.class,
                () -> service.grant("ZZ00", "邻县农机监理站", "admin"));
        assertTrue(e.getMessage().contains("只保一条"), "受托哪一家只保一条");
    }

    @Test
    void relieve_twice_rejected() {
        TAmssEntrust record = new TAmssEntrust();
        record.setId(1L);
        record.setStatus(1);
        when(entrustMapper.selectOne(any())).thenReturn(record);
        assertThrows(AmssBizException.class, () -> service.relieve(1L, "admin"));
    }

    @Test
    void serialNo_rollsForward() {
        assertEquals("ZZ02", SerialNoUtil.next("ZZ", 2, Arrays.asList("ZZ00", "ZZ01")));
        assertEquals("JK0001", SerialNoUtil.next("JK", 4, Collections.<String>emptyList()));
        assertEquals("ZZ11", SerialNoUtil.next("ZZ", 2, Arrays.asList("ZZ09", "XX05", "ZZ10")),
                "别的前缀不掺和");
        assertEquals("ZZ01", SerialNoUtil.next("ZZ", 2, null));
        assertEquals("JK10000", SerialNoUtil.next("JK", 4, Arrays.asList("JK9999")),
                "过了位宽自然长个儿");
    }
}
