package com.codeaudit.controller;

import com.codeaudit.TestAiChatConfig;
import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.ProjectRepository;
import com.codeaudit.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAiChatConfig.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private Project testProject;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        projectRepository.deleteAll();
        testProject = Project.builder()
                .name("test-project")
                .repoPath("/tmp/test")
                .language("java")
                .build();
        testProject = projectRepository.save(testProject);
    }

    @Test
    @WithMockUser
    void shouldGetEmptyReviewList() throws Exception {
        mockMvc.perform(get("/api/projects/" + testProject.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser
    void shouldListReviewsWithData() throws Exception {
        Review r = Review.builder()
                .project(testProject).title("测试审查")
                .status("completed").fromRef("HEAD~1").toRef("HEAD")
                .totalFiles(3).totalIssues(5)
                .createdAt(LocalDateTime.now())
                .build();
        reviewRepository.save(r);

        mockMvc.perform(get("/api/projects/" + testProject.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].status").value("completed"))
                .andExpect(jsonPath("$.data.content[0].totalFiles").value(3));
    }

    @Test
    @WithMockUser
    void shouldGetReviewDetail() throws Exception {
        Review r = Review.builder()
                .project(testProject).title("审查详情测试")
                .status("processing").fromRef("HEAD~1").toRef("HEAD")
                .totalFiles(5).totalIssues(0)
                .createdAt(LocalDateTime.now())
                .build();
        r = reviewRepository.save(r);

        mockMvc.perform(get("/api/reviews/" + r.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("processing"));
    }

    @Test
    @WithMockUser
    void shouldReturn404ForMissingReview() throws Exception {
        mockMvc.perform(get("/api/reviews/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @WithMockUser
    void shouldDeleteReview() throws Exception {
        Review r = Review.builder()
                .project(testProject).title("待删除审查")
                .status("completed").fromRef("HEAD~1").toRef("HEAD")
                .totalFiles(2).totalIssues(3)
                .createdAt(LocalDateTime.now())
                .build();
        r = reviewRepository.save(r);

        mockMvc.perform(delete("/api/reviews/" + r.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
