-- 剧情与拼图进度接入 game_progress/game_event 表后的查询索引。
-- game_progress 的 (user_id, game_id) 查询已由 V1 的唯一键覆盖，无需额外索引。
CREATE INDEX idx_game_event_user_created ON game_event (user_id, created_at);
