package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Issue;
import com.codeaudit.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * IssueService 单元测试
 *
 * @author CodeAudit Team
 */
@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private IssueService issueService;

    @Test
    void updateStatus_shouldSucceed_whenStatusValid() {
        Issue issue = Issue.builder()
                .id(1L)
                .status("open")
                .build();

        when(issueRepository.findById(1L)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any())).thenReturn(issue);

        Issue result = issueService.updateStatus(1L, "resolved");

        assertNotNull(result);
        assertEquals("resolved", result.getStatus());
        verify(issueRepository).save(issue);
    }

    @Test
    void updateStatus_shouldThrowBizException_whenStatusInvalid() {
        BizException ex = assertThrows(BizException.class,
                () -> issueService.updateStatus(1L, "invalid_status"));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("无效的状态值"));
        verify(issueRepository, never()).findById(any());
    }

    @Test
    void updateStatus_shouldThrowBizException_whenStatusNull() {
        BizException ex = assertThrows(BizException.class,
                () -> issueService.updateStatus(1L, null));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("无效的状态值"));
    }

    @Test
    void updateStatus_shouldThrowBizException_whenIssueNotFound() {
        when(issueRepository.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> issueService.updateStatus(99L, "resolved"));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("问题不存在"));
    }
}
