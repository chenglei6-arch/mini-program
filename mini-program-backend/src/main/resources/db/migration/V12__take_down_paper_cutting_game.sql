-- 剪纸拼图（指尖剪林蛙）暂时下架：首页 games 列表只返回 enabled=1 的行，
-- 此处置 0 仅隐藏入口，页面与历史数据保留，恢复上架时改回 enabled=1 即可。
UPDATE content_game SET enabled = 0 WHERE id = 'paper-cutting';
