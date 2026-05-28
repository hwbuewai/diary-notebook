package com.example.diary.repository;

import com.example.diary.model.DiaryEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {

    List<DiaryEntry> findByTitleContainingOrTagsContaining(String titleKeyword, String tagsKeyword);
}