import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.client.protocol.response.BcosBlock;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.transaction.model.dto.TransactionResponse;

import java.io.File;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智慧温室链上适配服务：把 {method, arguments} 转换为 FISCO BCOS 合约交易。
 *
 * 部署：
 *   java -cp "out:conf/:lib/*" ChainAdapter --action deploy --config conf/config.toml \
 *        --abi contract.abi.json --bin contract.bin --grant "DEVICE_SERVICE:0x..."
 * 启动服务：
 *   java -cp "out:conf/:lib/*" ChainAdapter --action serve --config conf/config.toml \
 *        --abi contract.abi.json --bin contract.bin --address 0x... --port 8600
 *
 * HTTP 接口：
 *   GET  /health         适配服务与合约状态
 *   GET  /chain/block    当前区块高度、最新区块时间与近期平均出块间隔（只读，不产生交易）
 *   POST /chain/adapter  {method, arguments} 提交合约交易并返回回执
 */
public class ChainAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Integer> ROLE = new HashMap<>();

    /** 采样多少个区块来估算平均出块间隔（含头尾，得到 N-1 个间隔） */
    private static final int BLOCK_SAMPLE_SIZE = 12;

    static {
        ROLE.put("NONE", 0);
        ROLE.put("ADMIN", 1);
        ROLE.put("DEVICE_SERVICE", 2);
        ROLE.put("QUALITY_ADMIN", 3);
        ROLE.put("BUSINESS_SERVICE", 4);
        ROLE.put("REGULATOR", 5);
    }

    private final AssembleTransactionProcessor processor;
    private final Client client;
    private final String abiContent;
    private final String binContent;
    private String contractAddress;

    public ChainAdapter(
            String configPath,
            String abiPath,
            String binPath,
            int groupId,
            String contractAddress
    ) throws Exception {
        this.abiContent = new String(
                Files.readAllBytes(Paths.get(abiPath)),
                StandardCharsets.UTF_8
        );
        this.binContent = new String(
                Files.readAllBytes(Paths.get(binPath)),
                StandardCharsets.UTF_8
        ).trim();
        this.contractAddress = contractAddress;

        BcosSDK sdk = BcosSDK.build(configPath);
        Client client = sdk.getClient(groupId);
        this.client = client;
        CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();
        this.processor = TransactionProcessorFactory
                .createAssembleTransactionProcessor(client, keyPair);
        System.out.println("[chain-adapter] 已连接群组 " + groupId
                + "，发送账户 " + keyPair.getAddress());
    }

    public String deploy() throws Exception {
        TransactionResponse response = processor.deployAndGetResponse(
                abiContent,
                binContent,
                new ArrayList<Object>()
        );
        TransactionReceipt receipt = requireSuccess(response);
        this.contractAddress = response.getContractAddress();
        System.out.println("[chain-adapter] 合约已部署：" + contractAddress
                + " 交易 " + receipt.getTransactionHash()
                + " 区块 " + blockNumber(receipt));
        return contractAddress;
    }

    public void grantRole(String roleName, String account) throws Exception {
        Integer role = ROLE.get(roleName.toUpperCase());
        if (role == null) {
            throw new IllegalArgumentException("未知角色：" + roleName);
        }
        List<Object> params = new ArrayList<>();
        params.add(account);
        params.add(BigInteger.valueOf(role));
        params.add(Boolean.TRUE);
        Map<String, Object> result = send("setRole", params);
        System.out.println("[chain-adapter] 已授权 " + roleName + " -> "
                + account + " 交易 " + result.get("transactionHash"));
    }

    public Map<String, Object> send(String method, List<Object> params)
            throws Exception {
        TransactionResponse response = processor
                .sendTransactionAndGetResponse(
                        contractAddress,
                        abiContent,
                        method,
                        params
                );
        TransactionReceipt receipt = requireSuccess(response);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transactionHash", receipt.getTransactionHash());
        result.put("blockNumber", blockNumber(receipt));
        result.put("status", "CONFIRMED");
        result.put("values", response.getValues());
        return result;
    }

    private TransactionReceipt requireSuccess(TransactionResponse response) {
        TransactionReceipt receipt =
                response == null ? null : response.getTransactionReceipt();
        if (receipt == null) {
            throw new IllegalStateException("链上未返回交易回执");
        }
        String status = receipt.getStatus();
        if (!"0x0".equals(status) && !"0".equals(status)) {
            String hint = "0x16".equals(status)
                    ? "（合约拒绝：同一业务标识的内容与链上记录不一致，"
                    + "或当前账户缺少对应角色授权）"
                    : "";
            throw new IllegalStateException(
                    "链上交易失败：status=" + status
                            + " msg=" + receipt.getStatusMsg()
                            + hint
            );
        }
        return receipt;
    }

    private long blockNumber(TransactionReceipt receipt) {
        String value = receipt.getBlockNumber();
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        return value.startsWith("0x")
                ? Long.parseLong(value.substring(2), 16)
                : Long.parseLong(value);
    }

    /**
     * 只读查询链上运行指标：当前区块高度、最新区块时间戳、近期平均出块间隔。
     * 业务后端用它展示真实链上数据，不产生任何交易。
     */
    public Map<String, Object> blockInfo() {
        BigInteger latest = client.getBlockNumber().getBlockNumber();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blockNumber", latest.longValue());
        // 实测该节点返回的时间戳是毫秒（十六进制字符串），这里统一成 epoch 毫秒
        result.put("blockTimestampMs", blockTimestampMs(latest));
        result.put("avgBlockIntervalMs", averageBlockIntervalMs(latest));
        result.put("sampleSize", BLOCK_SAMPLE_SIZE);
        return result;
    }

    /**
     * 平均出块间隔：取最近 BLOCK_SAMPLE_SIZE 个区块的时间戳差值求平均。
     * 中间有区块读失败时跳过一个间隔，避免用跨度过大的两点算出失真值。
     */
    private Long averageBlockIntervalMs(BigInteger latest) {
        int span = Math.min(BLOCK_SAMPLE_SIZE, latest.intValue());
        if (span < 2) {
            return null;
        }
        long totalMs = 0L;
        int intervals = 0;
        Long previousMs = null;
        for (int offset = span - 1; offset >= 0; offset--) {
            Long millis = blockTimestampMs(
                    latest.subtract(BigInteger.valueOf(offset))
            );
            if (millis == null) {
                previousMs = null;
                continue;
            }
            if (previousMs != null) {
                long deltaMs = millis - previousMs;
                if (deltaMs > 0) {
                    totalMs += deltaMs;
                    intervals++;
                }
            }
            previousMs = millis;
        }
        return intervals == 0 ? null : totalMs / intervals;
    }

    /**
     * 读区块时间戳并统一成 epoch 毫秒。
     * 节点返回十六进制字符串；量级大于 1e11 说明本身已是毫秒，否则按秒换算，
     * 这样换节点版本时也不会算错一千倍。
     */
    private Long blockTimestampMs(BigInteger number) {
        BcosBlock block = client.getBlockByNumber(number, false);
        String value = block == null || block.getBlock() == null
                ? null
                : block.getBlock().getTimestamp();
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            long raw = value.startsWith("0x")
                    ? Long.parseLong(value.substring(2), 16)
                    : Long.parseLong(value);
            return raw > 100_000_000_000L ? raw : raw * 1000L;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static byte[] toBytes32(String value) throws Exception {
        String text = value == null ? "" : value.trim();
        String hex = text.startsWith("0x") ? text.substring(2) : text;
        if (hex.matches("[0-9a-fA-F]{64}")) {
            byte[] bytes = new byte[32];
            for (int i = 0; i < 32; i++) {
                bytes[i] = (byte) Integer.parseInt(
                        hex.substring(i * 2, i * 2 + 2),
                        16
                );
            }
            return bytes;
        }
        return MessageDigest.getInstance("SHA-256").digest(
                text.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String argument(JsonNode arguments, String name) {
        JsonNode node = arguments.get(name);
        return node == null || node.isNull() ? "" : node.asText();
    }

    public Map<String, Object> dispatch(String method, JsonNode arguments)
            throws Exception {
        List<Object> params = new ArrayList<>();
        switch (method) {
            case "recordEnvironmentRoot":
                params.add(argument(arguments, "cycleId"));
                params.add(toBytes32(argument(arguments, "rootHash")));
                params.add(argument(arguments, "deviceId").isEmpty()
                        ? argument(arguments, "metadata")
                        : argument(arguments, "deviceId"));
                break;
            case "recordCertificate":
                params.add(argument(arguments, "certificateId"));
                params.add(toBytes32(argument(arguments, "contentHash")));
                params.add(argument(arguments, "status"));
                break;
            case "registerTraceCode":
                params.add(argument(arguments, "productCode"));
                params.add(toBytes32(argument(arguments, "signatureHash")));
                params.add(argument(arguments, "batchId"));
                break;
            case "appendScanRecord":
                params.add(argument(arguments, "productCode"));
                params.add(argument(arguments, "channel"));
                params.add(argument(arguments, "result"));
                break;
            case "appendTraceNode":
                params.add(argument(arguments, "fieldNo"));
                params.add(new BigInteger(
                        argument(arguments, "index").isEmpty()
                                ? "0"
                                : argument(arguments, "index")
                ));
                params.add(toBytes32(argument(arguments, "nodeHash")));
                params.add(toBytes32(argument(arguments, "previousHash")));
                params.add(argument(arguments, "stageType").isEmpty()
                        ? "追溯"
                        : argument(arguments, "stageType"));
                break;
            default:
                throw new IllegalArgumentException("不支持的方法：" + method);
        }
        return send(method, params);
    }

    public void serve(int port) throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port),
                0
        );
        server.createContext("/health", exchange -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "UP");
            body.put("mode", "fisco-bcos");
            body.put("contractAddress", contractAddress);
            respond(exchange, 200, body);
        });
        server.createContext("/chain/adapter", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, error("仅支持 POST"));
                return;
            }
            try {
                JsonNode request = MAPPER.readTree(exchange.getRequestBody());
                Map<String, Object> result = dispatch(
                        request.path("method").asText(),
                        request.path("arguments")
                );
                respond(exchange, 200, result);
            } catch (IllegalArgumentException exception) {
                respond(exchange, 400, error(exception.getMessage()));
            } catch (IllegalStateException exception) {
                // 合约回滚属于业务冲突，用 409 与适配服务自身故障区分开。
                respond(exchange, 409, error(exception.getMessage()));
            } catch (Exception exception) {
                respond(exchange, 500, error(exception.getMessage()));
            }
        });
        server.createContext("/chain/block", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, error("仅支持 GET"));
                return;
            }
            try {
                respond(exchange, 200, blockInfo());
            } catch (Exception exception) {
                respond(exchange, 500, error(exception.getMessage()));
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("[chain-adapter] HTTP 适配服务已启动：0.0.0.0:" + port
                + "，合约 " + contractAddress);
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            Map<String, Object> body
    ) throws java.io.IOException {
        byte[] payload = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().add(
                "Content-Type",
                "application/json; charset=utf-8"
        );
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private static String arg(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    public static void main(String[] args) throws Exception {
        String action = arg(args, "--action", "serve");
        String config = arg(args, "--config", "conf/config.toml");
        String abi = arg(args, "--abi", null);
        String bin = arg(args, "--bin", null);
        String address = arg(args, "--address", "");
        int port = Integer.parseInt(arg(args, "--port", "8600"));
        int group = Integer.parseInt(arg(args, "--group", "1"));

        if (abi == null || bin == null
                || !new File(abi).exists() || !new File(bin).exists()) {
            throw new IllegalArgumentException(
                    "需要 --abi 与 --bin 指向合约 ABI/BIN 文件"
            );
        }

        ChainAdapter adapter = new ChainAdapter(
                config,
                abi,
                bin,
                group,
                address
        );

        if ("deploy".equals(action)) {
            String deployed = adapter.deploy();
            String grants = arg(args, "--grant", "");
            if (!grants.isEmpty()) {
                for (String item : grants.split(",")) {
                    String[] pair = item.split(":");
                    if (pair.length == 2) {
                        adapter.grantRole(pair[0], pair[1]);
                    }
                }
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("contractAddress", deployed);
            System.out.println("[chain-adapter] 部署完成 "
                    + MAPPER.writeValueAsString(summary));
            // SDK 内部存在非守护线程，部署完成后显式退出，避免命令行挂起。
            System.exit(0);
        }

        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException(
                    "serve 模式需要 --address 指定合约地址"
            );
        }
        adapter.serve(port);
    }
}
