package com.example.diary.controller;

import com.example.diary.dto.CreateDiaryRequest;
import com.example.diary.dto.UpdateContentRequest;
import com.example.diary.dto.UpdateStatusRequest;
import com.example.diary.model.DiaryEntry;
import com.example.diary.repository.DiaryEntryRepository;
import com.example.diary.service.DiaryService;
import com.example.diary.service.GitService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final GitService gitService;
    private final DiaryEntryRepository diaryEntryRepository;

    @PostMapping
    public DiaryEntry create(@RequestBody CreateDiaryRequest request) {
        return diaryService.createDiary(request.title(), request.content(), request.tags());
    }

    @PutMapping("/{id}/content")
    public DiaryEntry updateContent(@PathVariable Long id, @RequestBody UpdateContentRequest request) {
        return diaryService.updateDiaryContent(id, request.content());
    }

    @PutMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        diaryService.changeStatus(id, request.status());
    }

    @GetMapping("/search")
    public List<DiaryEntry> search(@RequestParam String keyword) {
        return diaryService.search(keyword);
    }

    @GetMapping("/{id}/history")
    public List<Map<String, String>> getHistory(@PathVariable Long id) {
        DiaryEntry entry = requireEntry(id);
        return gitService.getHistory(entry.getFilePath());
    }

    @GetMapping("/{id}/diff")
    public Map<String, String> getDiff(
            @PathVariable Long id, @RequestParam String commit1, @RequestParam String commit2) {
        DiaryEntry entry = requireEntry(id);
        String diff = gitService.getDiff(commit1, commit2, entry.getFilePath());
        return Map.of("diff", diff);
    }

    @GetMapping("/{id}/version")
    public Map<String, String> getVersion(@PathVariable Long id, @RequestParam String commitId) {
        DiaryEntry entry = requireEntry(id);
        String content = gitService.getFileAtCommit(commitId, entry.getFilePath());
        return Map.of("commitId", commitId, "content", content);
    }

    private DiaryEntry requireEntry(Long id) {
        return diaryEntryRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found: " + id));
    }
}