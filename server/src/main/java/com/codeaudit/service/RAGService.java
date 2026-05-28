package com.codeaudit.service;

import com.codeaudit.entity.KnowledgeDocument;
import com.codeaudit.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) service for code knowledge base.
 * Uses keyword-based matching to retrieve relevant project documentation
 * and inject it into the review prompt for context-aware, standards-compliant review.
 *
 * Production would use vector embeddings (e.g. pgvector, ChromaDB, Milvus),
 * but we start with a lightweight keyword-matching implementation for simplicity.
 */
@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);
    private static final int MAX_DOCS = 3;
    private static final int MAX_SNIPPET_LENGTH = 2000;

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public RAGService(KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    /**
     * Retrieve relevant knowledge documents based on code context.
     * Matches keywords extracted from the diff content against document titles and content.
     *
     * @param projectId   project ID
     * @param diffContent diff content to extract keywords from
     * @return formatted knowledge snippet for prompt injection
     */
    public String retrieveKnowledge(Long projectId, String diffContent) {
        List<KnowledgeDocument> docs = knowledgeDocumentRepository.findByProjectId(projectId);
        if (docs.isEmpty()) return "";

        // Extract keywords from diff content
        Set<String> keywords = extractKeywords(diffContent);

        // Score and rank documents
        List<KnowledgeDocument> ranked = docs.stream()
                .filter(d -> d.getContent() != null)
                .sorted(Comparator.comparingDouble(
                        (KnowledgeDocument d) -> scoreDocument(d, keywords)).reversed())
                .limit(MAX_DOCS)
                .toList();

        if (ranked.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n### 项目知识库（相关规范与文档）\n");
        for (KnowledgeDocument doc : ranked) {
            String snippet = doc.getContent().length() > MAX_SNIPPET_LENGTH
                    ? doc.getContent().substring(0, MAX_SNIPPET_LENGTH) + "\n... (truncated)"
                    : doc.getContent();
            String category = doc.getCategory() != null ? "[" + doc.getCategory() + "] " : "";
            sb.append("- ").append(category).append(doc.getTitle()).append(":\n");
            sb.append("  ").append(snippet.replace("\n", "\n  ")).append("\n");
        }

        log.debug("RAG 检索到 {} 篇相关文档", ranked.size());
        return sb.toString();
    }



    private Set<String> extractKeywords(String content) {
        if (content == null || content.isBlank()) return Set.of();
        // Extract meaningful words from class names, method names, annotations
        Set<String> keywords = new LinkedHashSet<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
                trimmed = trimmed.substring(1).trim();
            }
            // Extract identifiers
            for (String token : trimmed.split("[^a-zA-Z0-9_]")) {
                if (token.length() >= 3 && !token.equals("import") && !token.equals("public")
                        && !token.equals("private") && !token.equals("class")) {
                    keywords.add(token.toLowerCase());
                }
            }
        }
        return keywords;
    }

    private double scoreDocument(KnowledgeDocument doc, Set<String> keywords) {
        if (keywords.isEmpty()) return 0;
        String text = (doc.getTitle() + " " + doc.getContent()).toLowerCase();
        int matches = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) matches++;
        }
        return (double) matches / keywords.size();
    }
}
