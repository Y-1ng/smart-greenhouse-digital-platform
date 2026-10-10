-- 演示数据中的追溯节点原先使用占位交易哈希（0x3000…），并不是真实链上回执。
-- 置空后表示“尚未上链”，由追加追溯节点接口或链上补偿任务按指针顺序补链，
-- 保证 trace_node.chain_tx_hash 只保存真实交易哈希。
UPDATE trace_node SET chain_tx_hash = NULL;
