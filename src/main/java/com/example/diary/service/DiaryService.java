package com.example.diary.service;

import com.example.diary.model.DiaryEntry;
import com.example.diary.repository.DiaryEntryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiaryService {

    private static final DateTimeFormatter UPDATE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DiaryEntryRepository diaryEntryRepository;
    private final GitService gitService;

    @Transactional(rollbackFor = Exception.class)
    public DiaryEntry createDiary(String title, String content, String tags) {
        gitService.initRepository();

        LocalDateTime now = LocalDateTime.now();
        String filePath = String.format(
                "diaries/%d/%02d/%s.md", now.getYear(), now.getMonthValue(), UUID.randomUUID());

        DiaryEntry entry = new DiaryEntry();
        entry.setTitle(title);
        entry.setContent(content);
        entry.setTags(tags);
        entry.setFilePath(filePath);
        entry.setStatus("DRAFT");

        DiaryEntry saved = diaryEntryRepository.save(entry);
        gitService.commit(filePath, content, "Create diary: " + title);

        log.info("Created diary id={} path={}", saved.getId(), filePath);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public DiaryEntry updateDiaryContent(Long id, String newContent) {
        DiaryEntry entry = diaryEntryRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diary not found: " + id));

        entry.setContent(newContent);
        DiaryEntry saved = diaryEntryRepository.save(entry);

        String timestamp = LocalDateTime.now().format(UPDATE_TIMESTAMP_FORMAT);
        String message = String.format("Update diary %d at %s", id, timestamp);
        gitService.commit(entry.getFilePath(), newContent, message);

        log.info("Updated diary id={} content", id);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String newStatus) {
        DiaryEntry entry = diaryEntryRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Diary not found: " + id));

        entry.setStatus(newStatus);
        diaryEntryRepository.save(entry);

        if ("FINALIZED".equals(newStatus) || "ARCHIVED".equals(newStatus)) {
            tagLatestCommit(entry, newStatus);
        }

        log.info("Changed diary id={} status to {}", id, newStatus);
    }

    @Transactional(readOnly = true)
    public List<DiaryEntry> search(String keyword) {
        return diaryEntryRepository.findByTitleContainingOrTagsContaining(keyword, keyword);
    }

    private void tagLatestCommit(DiaryEntry entry, String newStatus) {
        List<Map<String, String>> history = gitService.getHistory(entry.getFilePath());
        if (history.isEmpty()) {
            log.warn("No git history for diary id={}, skip tagging", entry.getId());
            return;
        }
        String latestCommitId = history.get(0).get("commitId");
        String tagName = buildStatusTag(newStatus, entry.getTitle());
        gitService.createTag(latestCommitId, tagName);
        log.info("Tagged commit {} as {} for diary id={}", latestCommitId, tagName, entry.getId());
    }

    private String buildStatusTag(String status, String title) {
        String sanitizedTitle = sanitizeForTag(title);
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return status + "/" + sanitizedTitle + "-" + date;
    }

    private String sanitizeForTag(String value) {
        String sanitized = value.trim().replaceAll("[^a-zA-Z0-9\\-_.]", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        if (sanitized.isEmpty()) {
            return "untitled";
        }
        return sanitized;
    }
}