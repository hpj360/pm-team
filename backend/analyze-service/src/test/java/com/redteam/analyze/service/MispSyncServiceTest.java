package com.redteam.analyze.service;

import com.redteam.analyze.client.MispClient;
import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.dto.MispAttribute;
import com.redteam.analyze.dto.MispEvent;
import com.redteam.analyze.entity.IoCEntity;
import com.redteam.analyze.entity.IocType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MISP 同步服务单元测试
 *
 * <p>mock MispClient + IoCService，覆盖：</p>
 * <ul>
 *   <li>syncIocToMisp 的 IP / Domain / Hash / URL 四种类型映射</li>
 *   <li>MISP 禁用时静默返回</li>
 *   <li>pullMispEvents 拉取并写入 IOC 库</li>
 * </ul>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MispSyncServiceTest {

    @Mock
    private MispClient mispClient;

    @Mock
    private MispProperties mispProperties;

    @Mock
    private IoCService ioCService;

    @InjectMocks
    private MispSyncService mispSyncService;

    @BeforeEach
    void setUp() {
        // 默认启用 MISP
        when(mispProperties.isEnabled()).thenReturn(true);
    }

    /**
     * 构建测试 IOC
     */
    private IoCEntity buildIoc(Long id, String type, String value) {
        IoCEntity ioc = new IoCEntity();
        ioc.setId(id);
        ioc.setIocType(type);
        ioc.setIocValue(value);
        ioc.setDescription("test ioc");
        ioc.setThreatLevel("2");
        return ioc;
    }

    /**
     * mock addEvent 返回带 id 的 MispEvent
     */
    private MispEvent mockCreatedEvent(String id) {
        MispEvent e = new MispEvent();
        e.setId(id);
        e.setInfo("created");
        return e;
    }

    // ==================== MISP 禁用场景 ====================

    @Test
    @DisplayName("testSyncIocToMisp_Disabled: MISP 禁用时静默返回 null")
    void testSyncIocToMisp_Disabled() {
        when(mispProperties.isEnabled()).thenReturn(false);

        MispEvent result = mispSyncService.syncIocToMisp(1L);

        assertNull(result);
        verifyNoInteractions(mispClient);
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }

    @Test
    @DisplayName("testSyncAllIocsToMisp_Disabled: MISP 禁用时返回空统计")
    void testSyncAllIocsToMisp_Disabled() {
        when(mispProperties.isEnabled()).thenReturn(false);

        Map<String, Object> stat = mispSyncService.syncAllIocsToMisp();

        assertNotNull(stat);
        assertEquals(0, stat.get("total"));
        assertEquals(0, stat.get("success"));
        assertEquals(0, stat.get("fail"));
        verifyNoInteractions(mispClient);
    }

    @Test
    @DisplayName("testPullMispEvents_Disabled: MISP 禁用时返回空统计")
    void testPullMispEvents_Disabled() {
        when(mispProperties.isEnabled()).thenReturn(false);

        Map<String, Object> stat = mispSyncService.pullMispEvents();

        assertNotNull(stat);
        assertEquals(0, stat.get("events"));
        verifyNoInteractions(mispClient);
    }

    // ==================== syncIocToMisp 类型映射 ====================

    @Test
    @DisplayName("testSyncIocToMisp_IP: IP 类型 IOC 映射为 ip-src 属性")
    void testSyncIocToMisp_IP() {
        IoCEntity ioc = buildIoc(1L, IocType.IP, "1.2.3.4");
        when(ioCService.getById(1L)).thenReturn(ioc);
        when(mispClient.addEvent(any())).thenReturn(mockCreatedEvent("100"));

        MispEvent result = mispSyncService.syncIocToMisp(1L);

        assertNotNull(result);
        assertEquals("100", result.getId());

        // 捕获传入 addEvent 的参数，校验 attribute type
        ArgumentCaptor<MispEvent> captor = ArgumentCaptor.forClass(MispEvent.class);
        verify(mispClient).addEvent(captor.capture());
        MispEvent sent = captor.getValue();
        assertNotNull(sent.getAttributes());
        assertEquals(1, sent.getAttributes().size());
        assertEquals("ip-src", sent.getAttributes().get(0).getType());
        assertEquals("1.2.3.4", sent.getAttributes().get(0).getValue());
        assertEquals("Network activity", sent.getAttributes().get(0).getCategory());

        // 校验回填 mispEventId
        ArgumentCaptor<IoCEntity> iocCaptor = ArgumentCaptor.forClass(IoCEntity.class);
        verify(ioCService).saveOrUpdateIoc(iocCaptor.capture());
        assertEquals("100", iocCaptor.getValue().getMispEventId());
    }

    @Test
    @DisplayName("testSyncIocToMisp_Domain: Domain 类型 IOC 映射为 domain 属性")
    void testSyncIocToMisp_Domain() {
        IoCEntity ioc = buildIoc(2L, IocType.DOMAIN, "evil.com");
        when(ioCService.getById(2L)).thenReturn(ioc);
        when(mispClient.addEvent(any())).thenReturn(mockCreatedEvent("200"));

        MispEvent result = mispSyncService.syncIocToMisp(2L);

        assertNotNull(result);
        ArgumentCaptor<MispEvent> captor = ArgumentCaptor.forClass(MispEvent.class);
        verify(mispClient).addEvent(captor.capture());
        assertEquals("domain", captor.getValue().getAttributes().get(0).getType());
        assertEquals("evil.com", captor.getValue().getAttributes().get(0).getValue());
    }

    @Test
    @DisplayName("testSyncIocToMisp_Hash: MD5 类型 IOC 映射为 md5 属性")
    void testSyncIocToMisp_Hash() {
        String md5 = "d41d8cd98f00b204e9800998ecf8427e";
        IoCEntity ioc = buildIoc(3L, IocType.MD5, md5);
        when(ioCService.getById(3L)).thenReturn(ioc);
        when(mispClient.addEvent(any())).thenReturn(mockCreatedEvent("300"));

        MispEvent result = mispSyncService.syncIocToMisp(3L);

        assertNotNull(result);
        ArgumentCaptor<MispEvent> captor = ArgumentCaptor.forClass(MispEvent.class);
        verify(mispClient).addEvent(captor.capture());
        assertEquals("md5", captor.getValue().getAttributes().get(0).getType());
        assertEquals(md5, captor.getValue().getAttributes().get(0).getValue());
        assertEquals("Artifacts dropped", captor.getValue().getAttributes().get(0).getCategory());
    }

    @Test
    @DisplayName("testSyncIocToMisp_URL: URL 类型 IOC 映射为 url 属性")
    void testSyncIocToMisp_URL() {
        String url = "http://evil.com/payload";
        IoCEntity ioc = buildIoc(4L, IocType.URL, url);
        when(ioCService.getById(4L)).thenReturn(ioc);
        when(mispClient.addEvent(any())).thenReturn(mockCreatedEvent("400"));

        MispEvent result = mispSyncService.syncIocToMisp(4L);

        assertNotNull(result);
        ArgumentCaptor<MispEvent> captor = ArgumentCaptor.forClass(MispEvent.class);
        verify(mispClient).addEvent(captor.capture());
        assertEquals("url", captor.getValue().getAttributes().get(0).getType());
        assertEquals(url, captor.getValue().getAttributes().get(0).getValue());
    }

    @Test
    @DisplayName("testSyncIocToMisp_UnsupportedType: 不支持的 IOC 类型返回 null")
    void testSyncIocToMisp_UnsupportedType() {
        IoCEntity ioc = buildIoc(5L, "UNKNOWN", "foo");
        when(ioCService.getById(5L)).thenReturn(ioc);

        MispEvent result = mispSyncService.syncIocToMisp(5L);

        assertNull(result);
        verify(mispClient, never()).addEvent(any());
    }

    @Test
    @DisplayName("testSyncIocToMisp_NotFound: IOC 不存在返回 null")
    void testSyncIocToMisp_NotFound() {
        when(ioCService.getById(anyLong())).thenReturn(null);

        MispEvent result = mispSyncService.syncIocToMisp(999L);

        assertNull(result);
        verify(mispClient, never()).addEvent(any());
    }

    @Test
    @DisplayName("testSyncIocToMisp_ClientError: MispClient 抛异常时返回 null 不传播")
    void testSyncIocToMisp_ClientError() {
        IoCEntity ioc = buildIoc(1L, IocType.IP, "1.2.3.4");
        when(ioCService.getById(1L)).thenReturn(ioc);
        when(mispClient.addEvent(any())).thenThrow(new RuntimeException("misp down"));

        MispEvent result = mispSyncService.syncIocToMisp(1L);

        assertNull(result);
        // 异常被捕获，不回填
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }

    // ==================== syncAllIocsToMisp ====================

    @Test
    @DisplayName("testSyncAllIocsToMisp: 批量同步统计正确")
    void testSyncAllIocsToMisp() {
        IoCEntity ioc1 = buildIoc(1L, IocType.IP, "1.2.3.4");
        IoCEntity ioc2 = buildIoc(2L, IocType.DOMAIN, "evil.com");
        IoCEntity ioc3 = buildIoc(3L, "UNKNOWN", "foo"); // 不支持类型，失败
        when(ioCService.listAll()).thenReturn(Arrays.asList(ioc1, ioc2, ioc3));
        when(ioCService.getById(1L)).thenReturn(ioc1);
        when(ioCService.getById(2L)).thenReturn(ioc2);
        when(ioCService.getById(3L)).thenReturn(ioc3);
        when(mispClient.addEvent(any())).thenReturn(mockCreatedEvent("999"));

        Map<String, Object> stat = mispSyncService.syncAllIocsToMisp();

        assertNotNull(stat);
        assertEquals(3, stat.get("total"));
        assertEquals(2, stat.get("success"));
        assertEquals(1, stat.get("fail"));
    }

    // ==================== pullMispEvents ====================

    @Test
    @DisplayName("testPullMispEvents: 拉取 MISP 事件并写入 IOC 库")
    void testPullMispEvents() {
        MispEvent event = new MispEvent();
        event.setId("1");
        event.setInfo("misp event");
        event.setThreatLevelId("2");
        when(mispClient.listEvents()).thenReturn(Collections.singletonList(event));

        MispAttribute attr1 = new MispAttribute();
        attr1.setType("ip-src");
        attr1.setValue("1.2.3.4");
        attr1.setComment("c2");
        MispAttribute attr2 = new MispAttribute();
        attr2.setType("domain");
        attr2.setValue("evil.com");
        attr2.setComment("malware");
        MispAttribute attr3 = new MispAttribute();
        attr3.setType("unknown-type"); // 不支持类型，跳过
        attr3.setValue("foo");
        when(mispClient.listAttributes("1")).thenReturn(Arrays.asList(attr1, attr2, attr3));

        Map<String, Object> stat = mispSyncService.pullMispEvents();

        assertNotNull(stat);
        assertEquals(1, stat.get("events"));
        assertEquals(3, stat.get("attributes"));
        assertEquals(2, stat.get("saved"));

        // 验证 saveOrUpdateIoc 被调用 2 次（ip-src + domain）
        ArgumentCaptor<IoCEntity> captor = ArgumentCaptor.forClass(IoCEntity.class);
        verify(ioCService, times(2)).saveOrUpdateIoc(captor.capture());
        List<IoCEntity> savedIocs = captor.getAllValues();
        // 校验保存的 IOC 类型与值
        boolean hasIp = savedIocs.stream().anyMatch(i ->
                IocType.IP.equals(i.getIocType()) && "1.2.3.4".equals(i.getIocValue()));
        boolean hasDomain = savedIocs.stream().anyMatch(i ->
                IocType.DOMAIN.equals(i.getIocType()) && "evil.com".equals(i.getIocValue()));
        assertTrue(hasIp, "应保存 IP 类型 IOC");
        assertTrue(hasDomain, "应保存 Domain 类型 IOC");
        // 校验来源标记为 MISP
        savedIocs.forEach(i -> assertEquals("MISP", i.getSource()));
    }

    @Test
    @DisplayName("testPullMispEvents_EmptyEvents: MISP 无事件时返回空统计")
    void testPullMispEvents_EmptyEvents() {
        when(mispClient.listEvents()).thenReturn(Collections.emptyList());

        Map<String, Object> stat = mispSyncService.pullMispEvents();

        assertNotNull(stat);
        assertEquals(0, stat.get("events"));
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }
}
