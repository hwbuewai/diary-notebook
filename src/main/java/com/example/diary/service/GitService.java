package com.example.diary.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GitService {

    private static final DateTimeFormatter COMMIT_DATE_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    private final String repoPath;

    public GitService(@Value("${diary.repo-path}") String repoPath) {
        this.repoPath = repoPath;
    }

    public void initRepository() {
        File repoRoot = getRepoRoot();
        File gitDir = new File(repoRoot, ".git");
        if (gitDir.exists()) {
            log.info("Git repository already exists at {}", repoRoot.getAbsolutePath());
            return;
        }
        try {
            if (!repoRoot.exists() && !repoRoot.mkdirs()) {
                throw new IllegalStateException("Failed to create repository directory: " + repoRoot);
            }
            try (Git git = Git.init().setDirectory(repoRoot).call()) {
                StoredConfig config = git.getRepository().getConfig();
                config.setString("user", null, "name", "Diary App");
                config.setString("user", null, "email", "diary@localhost");
                config.save();
            }
            log.info("Initialized git repository at {}", repoRoot.getAbsolutePath());
        } catch (GitAPIException | IOException e) {
            log.error("Failed to initialize git repository at {}: {}", repoRoot, e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize git repository", e);
        }
    }

    public String commit(String filePath, String content, String message) {
        String normalizedPath = normalizePath(filePath);
        File repoRoot = getRepoRoot();
        File targetFile = new File(repoRoot, normalizedPath);
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Failed to create parent directories for " + targetFile);
            }
            Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
            try (Git git = openGit()) {
                git.add().addFilepattern(normalizedPath).call();
                RevCommit commit = git.commit().setMessage(message).call();
                String commitHash = commit.getName();
                log.info("Committed {} as {}", normalizedPath, commitHash);
                return commitHash;
            }
        } catch (GitAPIException | IOException e) {
            log.error("Failed to commit file {}: {}", normalizedPath, e.getMessage(), e);
            throw new IllegalStateException("Failed to commit file: " + normalizedPath, e);
        }
    }

    public List<Map<String, String>> getHistory(String filePath) {
        String normalizedPath = normalizePath(filePath);
        List<Map<String, String>> history = new ArrayList<>();
        try (Git git = openGit()) {
            Iterable<RevCommit> commits = git.log().addPath(normalizedPath).call();
            for (RevCommit commit : commits) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("commitId", commit.getName());
                entry.put("author", commit.getAuthorIdent().getName());
                entry.put("date", COMMIT_DATE_FORMAT.format(Instant.ofEpochSecond(commit.getCommitTime())));
                entry.put("message", commit.getFullMessage() != null ? commit.getFullMessage().trim() : "");
                history.add(entry);
            }
            log.debug("Loaded {} history entries for {}", history.size(), normalizedPath);
            return history;
        } catch (GitAPIException | IOException e) {
            log.error("Failed to load history for {}: {}", normalizedPath, e.getMessage(), e);
            throw new IllegalStateException("Failed to load file history: " + normalizedPath, e);
        }
    }

    public String getDiff(String commitId1, String commitId2, String filePath) {
        String normalizedPath = normalizePath(filePath);
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            ObjectId oldId = repository.resolve(commitId1);
            ObjectId newId = repository.resolve(commitId2);
            if (oldId == null) {
                throw new IllegalArgumentException("Unknown commit: " + commitId1);
            }
            if (newId == null) {
                throw new IllegalArgumentException("Unknown commit: " + commitId2);
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ObjectReader reader = repository.newObjectReader();
                    DiffFormatter diffFormatter = new DiffFormatter(out)) {
                diffFormatter.setRepository(repository);
                diffFormatter.setContext(3);
                CanonicalTreeParser oldTree = prepareTreeParser(repository, reader, oldId);
                CanonicalTreeParser newTree = prepareTreeParser(repository, reader, newId);
                List<DiffEntry> diffs = diffFormatter.scan(oldTree, newTree);
                boolean found = false;
                for (DiffEntry diff : diffs) {
                    if (matchesPath(diff, normalizedPath)) {
                        diffFormatter.format(diff);
                        found = true;
                    }
                }
                if (!found) {
                    log.warn("No diff found for {} between {} and {}", normalizedPath, commitId1, commitId2);
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (GitAPIException | IOException e) {
            log.error(
                    "Failed to diff {} between {} and {}: {}",
                    normalizedPath,
                    commitId1,
                    commitId2,
                    e.getMessage(),
                    e);
            throw new IllegalStateException("Failed to generate diff for file: " + normalizedPath, e);
        }
    }

    public void createTag(String commitId, String tagName) {
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            ObjectId objectId = repository.resolve(commitId);
            if (objectId == null) {
                throw new IllegalArgumentException("Unknown commit: " + commitId);
            }
            git.tag().setName(tagName).setObjectId(objectId).call();
            log.info("Created tag {} on commit {}", tagName, commitId);
        } catch (GitAPIException | IOException e) {
            log.error("Failed to create tag {} on {}: {}", tagName, commitId, e.getMessage(), e);
            throw new IllegalStateException("Failed to create tag: " + tagName, e);
        }
    }

    public String getFileAtCommit(String commitId, String filePath) {
        String normalizedPath = normalizePath(filePath);
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            ObjectId commitObjectId = repository.resolve(commitId);
            if (commitObjectId == null) {
                throw new IllegalArgumentException("Unknown commit: " + commitId);
            }
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitObjectId);
                RevTree tree = commit.getTree();
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, normalizedPath, tree)) {
                    if (treeWalk == null) {
                        throw new IllegalArgumentException(
                                "File not found at commit " + commitId + ": " + normalizedPath);
                    }
                    ObjectId blobId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(blobId);
                    String content = new String(loader.getBytes(), StandardCharsets.UTF_8);
                    log.debug("Loaded {} at commit {}", normalizedPath, commitId);
                    return content;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read {} at {}: {}", normalizedPath, commitId, e.getMessage(), e);
            throw new IllegalStateException(
                    "Failed to read file at commit " + commitId + ": " + normalizedPath, e);
        }
    }

    private Git openGit() throws IOException {
        File repoRoot = getRepoRoot();
        if (!new File(repoRoot, ".git").exists()) {
            throw new IllegalStateException("Git repository not initialized at " + repoRoot.getAbsolutePath());
        }
        return Git.open(repoRoot);
    }

    private File getRepoRoot() {
        return new File(repoPath).getAbsoluteFile();
    }

    private String normalizePath(String filePath) {
        return filePath.replace('\\', '/');
    }

    private CanonicalTreeParser prepareTreeParser(Repository repository, ObjectReader reader, ObjectId commitId)
            throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = revWalk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, tree.getId());
            return treeParser;
        }
    }

    private boolean matchesPath(DiffEntry diff, String filePath) {
        return filePath.equals(diff.getOldPath()) || filePath.equals(diff.getNewPath());
    }
}