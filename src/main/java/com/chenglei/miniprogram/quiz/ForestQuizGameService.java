package com.chenglei.miniprogram.quiz;

import com.chenglei.miniprogram.badge.BadgeService;
import com.chenglei.miniprogram.common.error.BusinessException;
import com.chenglei.miniprogram.common.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForestQuizGameService {

    public static final String GAME_ID = "forest-quiz";
    private static final int QUESTIONS_PER_LEVEL = 3;
    private static final List<Level> LEVELS = new ArrayList<>();
    private static final Map<String, Object> USER_LOCKS = new ConcurrentHashMap<>();

    private final ForestQuizMapper mapper;
    private final ObjectMapper objectMapper;
    private final BadgeService badgeService;
    private final QuizStateJson quizStateJson;

    public ForestQuizGameService(ForestQuizMapper mapper, ObjectMapper objectMapper, BadgeService badgeService,
        QuizStateJson quizStateJson) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.badgeService = badgeService;
        this.quizStateJson = quizStateJson;
        loadLevels();
    }

    @Transactional
    public Map<String, Object> progress(String userId) {
        synchronized (lockFor(userId)) {
            PlayerState state = stateFor(userId);
            return buildProgress(state);
        }
    }

    @Transactional
    public Map<String, Object> answer(String userId, String idempotencyKey, String levelId,
        String questionId, String optionId) {
        synchronized (lockFor(userId)) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Map<String, Object> existing = mapper.selectEvent(userId, idempotencyKey);
                if (existing != null) {
                    Map<String, Object> duplicate = readMap(String.valueOf(existing.get("responseJson")));
                    duplicate.put("duplicated", true);
                    return duplicate;
                }
            }

            PlayerState state = stateFor(userId);
            Level level = levelById(levelId);
            if (level == null) throw new BusinessException(ErrorCode.NOT_FOUND, "问答关卡不存在");
            if (!level.id().equals(state.currentLevelId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "请先完成当前关卡");
            }
            Question question = level.questions().get(state.questionIndex);
            if (!question.id().equals(questionId)) {
                throw new BusinessException(ErrorCode.CONFLICT, "题目已更新，请按当前题目作答");
            }

            boolean correct = question.answerId().equals(optionId);
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("correct", correct);
            action.put("questionId", question.id());
            action.put("message", correct ? "回答正确" : "回答不正确，再试一次");
            action.put("explanation", question.explanation());

            if (correct) {
                state.correctCount++;
                if (state.questionIndex == level.questions().size() - 1) {
                    state.completedLevels.add(level.id());
                    state.questionIndex = 0;
                    state.correctCount = 0;
                    Level next = nextLevel(level);
                    state.currentLevelId = next == null ? null : next.id();
                    action.put("levelCompleted", true);
                    action.put("badge", Map.of("id", level.badgeId(), "name", level.badgeName()));
                    action.put("message", next == null ? "恭喜通关全部关卡" : "关卡完成，已解锁下一主题");
                } else {
                    state.questionIndex++;
                    action.put("levelCompleted", false);
                }
            } else {
                action.put("levelCompleted", false);
            }

            persist(userId, state);
            // 关卡完成时同步徽章流水（形态/分布/食性等 6 枚主题徽章）。
            badgeService.evaluate(userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accepted", true);
            result.put("duplicated", false);
            result.put("gameId", GAME_ID);
            result.put("action", action);
            result.put("progress", buildProgress(state));
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                try {
                    mapper.insertEvent(userId, idempotencyKey, "quiz_answer", objectMapper.writeValueAsString(result));
                } catch (Exception error) {
                    throw new IllegalStateException("问答事件保存失败", error);
                }
            }
            return result;
        }
    }

    private PlayerState stateFor(String userId) {
        Map<String, Object> saved = mapper.selectProgress(userId);
        if (saved == null) {
            PlayerState initial = new PlayerState(LEVELS.get(0).id());
            persist(userId, initial);
            return initial;
        }
        try {
            List<String> completed = quizStateJson.completedLevelIds(
                String.valueOf(saved.get("completedLevelsJson")));
            String currentLevelId = saved.get("currentLevelId") == null ? null : String.valueOf(saved.get("currentLevelId"));
            PlayerState state = new PlayerState(currentLevelId);
            state.completedLevels.addAll(completed);
            state.questionIndex = number(saved.get("questionIndex"));
            state.correctCount = number(saved.get("correctCount"));
            return state;
        } catch (Exception error) {
            throw new IllegalStateException("问答进度读取失败", error);
        }
    }

    private void persist(String userId, PlayerState state) {
        try {
            String completed = quizStateJson.write(new ArrayList<>(state.completedLevels));
            if (mapper.selectProgress(userId) == null) {
                mapper.insertProgress(userId, completed, state.currentLevelId, state.questionIndex, state.correctCount);
            } else {
                mapper.updateProgress(userId, completed, state.currentLevelId, state.questionIndex, state.correctCount);
            }
        } catch (Exception error) {
            throw new IllegalStateException("问答进度保存失败", error);
        }
    }

    private Map<String, Object> buildProgress(PlayerState state) {
        List<Map<String, Object>> levelStates = new ArrayList<>();
        for (int index = 0; index < LEVELS.size(); index++) {
            Level level = LEVELS.get(index);
            boolean completed = state.completedLevels.contains(level.id());
            boolean unlocked = completed || level.id().equals(state.currentLevelId);
            levelStates.add(Map.of("id", level.id(), "title", level.title(), "topic", level.topic(),
                "order", index + 1, "unlocked", unlocked, "completed", completed,
                "badge", Map.of("id", level.badgeId(), "name", level.badgeName())));
        }
        Level current = levelById(state.currentLevelId);
        Map<String, Object> stateMap = new LinkedHashMap<>();
        stateMap.put("completedLevelIds", state.completedLevels);
        stateMap.put("levels", levelStates);
        List<Map<String, Object>> reviewLevels = new ArrayList<>();
        for (Level level : LEVELS) {
            if (state.completedLevels.contains(level.id()) || level.id().equals(state.currentLevelId)) {
                reviewLevels.add(publicLevel(level, state.completedLevels.contains(level.id())));
            }
        }
        stateMap.put("unlockedLevels", reviewLevels);
        stateMap.put("currentLevel", current == null ? null : publicLevel(current, false));
        stateMap.put("currentQuestionIndex", current == null ? 0 : state.questionIndex);
        stateMap.put("correctCount", current == null ? 0 : state.correctCount);
        stateMap.put("totalQuestions", QUESTIONS_PER_LEVEL);
        stateMap.put("badges", badgeStates(state.completedLevels));

        return Map.of("gameId", GAME_ID, "completed", state.completedLevels.size(), "total", LEVELS.size(),
            "finished", state.currentLevelId == null, "version", 1, "updatedAt", Instant.now(), "state", stateMap);
    }

    private List<Map<String, Object>> badgeStates(Set<String> completed) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Level level : LEVELS) {
            Map<String, Object> badge = new LinkedHashMap<>();
            badge.put("id", level.badgeId());
            badge.put("name", level.badgeName());
            badge.put("level", "bronze");
            badge.put("unlocked", completed.contains(level.id()));
            result.add(badge);
        }
        return result;
    }

    private Map<String, Object> publicLevel(Level level, boolean includeAnswers) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Question question : level.questions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", question.id());
            item.put("prompt", question.prompt());
            List<Map<String, Object>> options = new ArrayList<>();
            for (Map<String, String> option : question.options()) {
                Map<String, Object> optionCopy = new LinkedHashMap<>(option);
                if (includeAnswers && question.answerId().equals(option.get("id"))) {
                    optionCopy.put("correct", true);
                }
                options.add(optionCopy);
            }
            item.put("options", options);
            questions.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", level.id());
        result.put("title", level.title());
        result.put("topic", level.topic());
        result.put("order", LEVELS.indexOf(level) + 1);
        result.put("badge", Map.of("id", level.badgeId(), "name", level.badgeName()));
        result.put("knowledgePoints", level.knowledgePoints());
        result.put("questions", questions);
        return result;
    }

    private void loadLevels() {
        if (!LEVELS.isEmpty()) return;
        try (InputStream stream = new ClassPathResource("forest-quiz.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(stream);
            for (JsonNode node : root.path("levels")) {
                List<String> points = objectMapper.convertValue(node.path("knowledgePoints"), new TypeReference<List<String>>() { });
                List<Question> questions = new ArrayList<>();
                for (JsonNode questionNode : node.path("questions")) {
                    List<Map<String, String>> options = objectMapper.convertValue(questionNode.path("options"), new TypeReference<List<Map<String, String>>>() { });
                    questions.add(new Question(questionNode.path("id").asText(), questionNode.path("prompt").asText(), options,
                        questionNode.path("answerId").asText(), questionNode.path("explanation").asText()));
                }
                LEVELS.add(new Level(node.path("id").asText(), node.path("title").asText(), node.path("topic").asText(),
                    node.path("badgeId").asText(), node.path("badgeName").asText(), points, questions));
            }
        } catch (Exception error) {
            throw new IllegalStateException("问答题库加载失败", error);
        }
    }

    private static Object lockFor(String userId) { return USER_LOCKS.computeIfAbsent(userId, ignored -> new Object()); }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }
    private static Level levelById(String id) { return LEVELS.stream().filter(level -> level.id().equals(id)).findFirst().orElse(null); }
    private static Level nextLevel(Level current) { int index = LEVELS.indexOf(current); return index >= 0 && index + 1 < LEVELS.size() ? LEVELS.get(index + 1) : null; }
    private Map<String, Object> readMap(String json) { try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { }); } catch (Exception e) { throw new IllegalStateException("问答事件读取失败", e); } }

    private static final class PlayerState {
        private final Set<String> completedLevels = new LinkedHashSet<>();
        private String currentLevelId;
        private int questionIndex;
        private int correctCount;
        private PlayerState(String currentLevelId) { this.currentLevelId = currentLevelId; }
    }

    private record Level(String id, String title, String topic, String badgeId, String badgeName,
        List<String> knowledgePoints, List<Question> questions) { }
    private record Question(String id, String prompt, List<Map<String, String>> options, String answerId,
        String explanation) { }
}
