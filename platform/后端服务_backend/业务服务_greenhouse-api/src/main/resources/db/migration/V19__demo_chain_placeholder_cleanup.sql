-- 演示种子数据里的合格证、溯源码使用占位交易哈希（0x1111…、0x2222…），
-- 环境周期只有 CONFIRMED 状态而没有交易哈希。这些都表示“并未真正上链”，
-- 一旦链下演示库重建，扫码记录补偿就会因为链上没有对应溯源码而被合约拒绝。
--
-- 统一改回“尚未上链”，由链上补偿任务按
-- 环境周期 → 合格证 → 溯源码 → 扫码记录 → 追溯节点 的顺序补链。
UPDATE certificate
SET chain_tx_hash = NULL
WHERE chain_tx_hash = '0x1111111111111111111111111111111111111111111111111111111111111111';

UPDATE trace_code
SET chain_tx_hash = NULL
WHERE chain_tx_hash = '0x2222222222222222222222222222222222222222222222222222222222222222';

UPDATE environment_cycle
SET chain_status = 'PENDING'
WHERE chain_tx_hash IS NULL;
