package com.codeaudit.service;

import com.codeaudit.dto.DiffBlock;
import com.codeaudit.dto.ReviewFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffFilterServiceTest {

    private DiffFilterService service;

    @BeforeEach
    void setUp() {
        service = new DiffFilterService();
    }

    private DiffBlock block(String path) {
        return new DiffBlock(path, "MODIFY", 5, 3, "diff content of " + path);
    }

    @Test
    void shouldReturnOriginalList_whenFilterIsNull() {
        List<DiffBlock> blocks = Arrays.asList(block("a.java"), block("b.java"));
        List<DiffBlock> result = service.applyFilters(blocks, null);
        assertSame(blocks, result);
    }

    @Test
    void shouldReturnOriginalList_whenFilterHasNoConditions() {
        List<DiffBlock> blocks = Arrays.asList(block("a.java"), block("b.java"));
        ReviewFilter filter = new ReviewFilter();
        List<DiffBlock> result = service.applyFilters(blocks, filter);
        assertSame(blocks, result);
    }

    @Test
    void shouldReturnNull_whenBlocksIsNull() {
        assertNull(service.applyFilters(null, new ReviewFilter()));
    }

    @Test
    void shouldReturnEmptyList_whenBlocksIsEmpty() {
        List<DiffBlock> result = service.applyFilters(Collections.emptyList(), new ReviewFilter());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFilterByIncludePaths() {
        List<DiffBlock> blocks = Arrays.asList(
                block("src/main/java/Foo.java"),
                block("src/main/java/Bar.java"),
                block("src/test/java/Test.java")
        );
        ReviewFilter filter = new ReviewFilter();
        filter.setIncludePaths(Collections.singletonList("src/main/java/**"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(b -> b.filePath().contains("Foo")));
        assertTrue(result.stream().anyMatch(b -> b.filePath().contains("Bar")));
    }

    @Test
    void shouldFilterByExcludePaths() {
        List<DiffBlock> blocks = Arrays.asList(
                block("src/main/java/Foo.java"),
                block("src/test/java/Test.java")
        );
        ReviewFilter filter = new ReviewFilter();
        filter.setExcludePaths(Collections.singletonList("src/test/**"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertEquals(1, result.size());
        assertEquals("src/main/java/Foo.java", result.get(0).filePath());
    }

    @Test
    void shouldFilterByExtension() {
        List<DiffBlock> blocks = Arrays.asList(
                block("App.java"),
                block("config.xml"),
                block("Utils.java")
        );
        ReviewFilter filter = new ReviewFilter();
        filter.setIncludeExtensions(Collections.singletonList(".xml"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertEquals(1, result.size());
        assertEquals("config.xml", result.get(0).filePath());
    }

    @Test
    void shouldCombineFilters() {
        List<DiffBlock> blocks = Arrays.asList(
                block("src/main/java/Foo.java"),
                block("src/main/java/config.xml"),
                block("src/test/java/Test.java")
        );
        ReviewFilter filter = new ReviewFilter();
        filter.setIncludePaths(Collections.singletonList("src/main/**"));
        filter.setIncludeExtensions(Collections.singletonList(".java"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertEquals(1, result.size());
        assertEquals("src/main/java/Foo.java", result.get(0).filePath());
    }

    @Test
    void shouldExcludeThenInclude() {
        List<DiffBlock> blocks = Arrays.asList(
                block("src/main/java/Foo.java"),
                block("src/main/java/Bar.java"),
                block("src/test/Test.java")
        );
        ReviewFilter filter = new ReviewFilter();
        filter.setExcludePaths(Collections.singletonList("src/test/**"));
        filter.setIncludePaths(Collections.singletonList("src/**"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmpty_whenIncludePathsMatchNothing() {
        List<DiffBlock> blocks = Arrays.asList(block("a.java"), block("b.java"));
        ReviewFilter filter = new ReviewFilter();
        filter.setIncludePaths(Collections.singletonList("lib/**"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldExcludeEverything() {
        List<DiffBlock> blocks = Arrays.asList(block("a.java"), block("b.java"));
        ReviewFilter filter = new ReviewFilter();
        filter.setExcludePaths(Collections.singletonList("**"));
        List<DiffBlock> result = service.applyFilters(blocks, filter);

        assertTrue(result.isEmpty());
    }
}
