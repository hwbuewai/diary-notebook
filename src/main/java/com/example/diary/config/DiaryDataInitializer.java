package com.example.diary.config;

import com.example.diary.repository.DiaryEntryRepository;
import com.example.diary.service.DiaryService;
import com.example.diary.service.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiaryDataInitializer implements CommandLineRunner {

    private static final String SAMPLE_TITLE = "欢迎日记";
    private static final String SAMPLE_CONTENT = "这是第一条示例日记，用于演示 Git 版本管理。";
    private static final String SAMPLE_TAGS = "示例,欢迎";

    private final GitService gitService;
    private final DiaryService diaryService;
    private final DiaryEntryRepository diaryEntryRepository;

    @Override
    public void run(String... args) {
        gitService.initRepository();

        if (diaryEntryRepository.count() > 0) {
            log.info("Database already contains diary entries, skip sample data insertion");
            return;
        }

        diaryService.createDiary(SAMPLE_TITLE, SAMPLE_CONTENT, SAMPLE_TAGS);
        log.info("Inserted sample diary: {}", SAMPLE_TITLE);
    }
}