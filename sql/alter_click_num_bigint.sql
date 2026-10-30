-- High-priority fix: avoid click_num INT overflow (>2^31-1).
-- Safe on existing data: INT values widen to BIGINT without loss.
ALTER TABLE t_link MODIFY COLUMN click_num BIGINT NOT NULL DEFAULT 0;
