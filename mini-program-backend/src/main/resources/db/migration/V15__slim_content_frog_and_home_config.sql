-- content_frog.source_url 指向的设计说明 DOCX 已随设计源文件移出运行时，接口不再返回该字段。
ALTER TABLE content_frog DROP COLUMN source_url;

-- 首页基金金额与公益摘要同源：删除 home_config 里的重复列，统一取 welfare_summary。
ALTER TABLE home_config DROP COLUMN fund_amount;
ALTER TABLE home_config DROP COLUMN fund_updated_at;
