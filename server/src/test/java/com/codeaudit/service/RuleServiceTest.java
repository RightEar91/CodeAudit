package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Rule;
import com.codeaudit.repository.RuleRepository;
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
 * RuleService 单元测试
 *
 * @author CodeAudit Team
 */
@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @InjectMocks
    private RuleService ruleService;

    @Test
    void create_shouldSetBuiltinFalseAndClearId() {
        Rule rule = Rule.builder()
                .id(99L)
                .name("CustomRule")
                .isBuiltin(true)
                .build();

        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Rule result = ruleService.create(rule);

        assertNotNull(result);
        assertNull(result.getId());
        assertFalse(result.getIsBuiltin());
    }

    @Test
    void update_shouldSucceed_whenRuleExists() {
        Rule existing = Rule.builder()
                .id(1L)
                .name("OldName")
                .category("SECURITY")
                .build();

        Rule updated = Rule.builder()
                .name("NewName")
                .build();

        when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any())).thenReturn(existing);

        Rule result = ruleService.update(1L, updated);

        assertEquals("NewName", result.getName());
        assertEquals("SECURITY", result.getCategory()); // 未传入，保持不变
    }

    @Test
    void update_shouldThrowBizException_whenRuleNotFound() {
        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> ruleService.update(99L, new Rule()));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("规则不存在"));
    }

    @Test
    void toggle_shouldToggleEnabled_whenParamIsNull() {
        Rule rule = Rule.builder()
                .id(1L)
                .isEnabled(true)
                .build();

        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any())).thenReturn(rule);

        Rule result = ruleService.toggle(1L, null);

        assertFalse(result.getIsEnabled());
    }

    @Test
    void toggle_shouldSetEnabled_whenParamProvided() {
        Rule rule = Rule.builder()
                .id(1L)
                .isEnabled(false)
                .build();

        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any())).thenReturn(rule);

        Rule result = ruleService.toggle(1L, true);

        assertTrue(result.getIsEnabled());
    }

    @Test
    void delete_shouldSucceed_whenCustomRule() {
        Rule rule = Rule.builder()
                .id(1L)
                .isBuiltin(false)
                .build();

        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

        ruleService.delete(1L);

        verify(ruleRepository).delete(rule);
    }

    @Test
    void delete_shouldThrowBizException_whenBuiltinRule() {
        Rule rule = Rule.builder()
                .id(1L)
                .isBuiltin(true)
                .build();

        when(ruleRepository.findById(1L)).thenReturn(Optional.of(rule));

        BizException ex = assertThrows(BizException.class, () -> ruleService.delete(1L));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("内置规则不可删除"));
        verify(ruleRepository, never()).delete(any());
    }

    @Test
    void delete_shouldThrowBizException_whenRuleNotFound() {
        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> ruleService.delete(99L));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("规则不存在"));
    }
}
