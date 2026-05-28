package com.codeaudit.controller;

import com.codeaudit.common.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 文件系统浏览 REST 控制器
 * <p>
 * 提供本地文件系统目录浏览接口，用于前端目录选择器。
 * 仅列出目录，不列出文件，按名称排序。
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api/filesystem")
public class FilesystemController {

    /**
     * 浏览指定路径下的子目录列表
     * <p>
     * 当 path 为空时返回系统根目录列表（Windows 返回盘符，Linux/Mac 返回 / 下的目录）。
     *
     * @param path 要浏览的父目录路径，为空时返回根目录列表
     * @return 子目录信息列表，按名称排序
     */
    @GetMapping("/browse")
    public Response<List<DirEntry>> browse(@RequestParam(required = false) String path) {
        List<DirEntry> entries = new ArrayList<>();

        if (path == null || path.isBlank()) {
            entries.addAll(getRootEntries());
        } else {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                entries.addAll(getRootEntries());
            } else {
                File[] files = dir.listFiles();
                if (files != null) {
                    Arrays.stream(files)
                            .filter(File::isDirectory)
                            .filter(f -> !f.isHidden())
                            .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                            .forEach(f -> entries.add(new DirEntry(f.getName(), f.getAbsolutePath())));
                }
            }
        }

        return Response.ok(entries);
    }

    /**
     * 获取系统根目录列表
     * <p>
     * Windows 系统返回盘符列表（C:\, D:\ 等），
     * 其他系统返回 / 下的目录列表。
     */
    private List<DirEntry> getRootEntries() {
        List<DirEntry> entries = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                entries.add(new DirEntry(root.getPath(), root.getAbsolutePath()));
            }
        }
        return entries;
    }

    /**
     * 目录条目 DTO
     */
    public static class DirEntry {
        /** 目录名称（显示用） */
        private String name;
        /** 目录绝对路径 */
        private String path;

        public DirEntry() {}

        public DirEntry(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
