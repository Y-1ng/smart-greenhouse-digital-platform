package com.smartgreenhouse.api.demo;

import com.smartgreenhouse.api.SmartGreenhouseApiApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IDEA 演示总控入口：后端在当前 JVM 中启动，其余演示进程由本类统一管理。
 */
public final class DemoAllInOneApplication {

    private static final Duration IOT_READY_TIMEOUT = Duration.ofSeconds(30);
    private static final List<Process> CHILD_PROCESSES = new ArrayList<>();

    /** ngrok 本地管理接口：用来判断隧道是否在线并取回当前公网地址。 */
    private static final String NGROK_TUNNELS_API = "http://127.0.0.1:4040/api/tunnels";
    private static final Duration NGROK_PROBE_TIMEOUT = Duration.ofMillis(800);
    private static final Pattern NGROK_PUBLIC_URL =
            Pattern.compile("\"public_url\"\\s*:\\s*\"(https://[^\"]+)\"");

    private DemoAllInOneApplication() {
    }

    public static void main(String[] args) {
        Path platformRoot = resolvePlatformRoot();
        Path scriptRoot = platformRoot.resolve("开发脚本_scripts");
        Path iotJar = platformRoot.resolve(
                "后端服务_backend/物联网服务_greenhouse-iot/target/"
                        + "greenhouse-iot-0.1.0-SNAPSHOT.jar"
        );
        Path simulatorScript = scriptRoot.resolve("模拟设备_command-simulator.ps1");
        Path frontendRoot = platformRoot.resolve("前端应用_frontend");

        String tracePublicBaseUrl = resolveTracePublicBaseUrl();
        // application-demo.yml 里已经定义了该属性，SpringApplication 的默认属性
        // 优先级低于配置文件；必须写系统属性才能覆盖（命令行参数仍可再覆盖）。
        System.setProperty(
                "app.trace.qr.public-base-url",
                tracePublicBaseUrl
        );

        new SpringApplicationBuilder(SmartGreenhouseApiApplication.class)
                .profiles("demo")
                .run(args);

        System.out.println("演示总控：业务后端已启动，端口 8090");
        System.out.println("演示总控：二维码公开地址 " + tracePublicBaseUrl);
        registerShutdownHook();

        Process iotProcess = startIotService(iotJar, platformRoot, scriptRoot);
        if (iotProcess != null) {
            waitForIotReady(scriptRoot.resolve("运行日志_IDEA物联网.txt"));
        }
        startDeviceSimulator(simulatorScript, scriptRoot);
        startFrontend(frontendRoot, scriptRoot);

        System.out.println("演示总控启动完成：后端 8090，前端 5199，物联网服务，设备模拟器");
    }

    /**
     * 二维码公开地址：固定公网域名优先，隧道不在线时退回当前局域网 IP。
     *
     * <p>隧道在线时用 ngrok 账号绑定的固定域名，手机换任何网络都能扫码打开；
     * 隧道没开时退回局域网地址，至少同一 Wi-Fi 下还能演示。
     * 显式设置了 TRACE_PUBLIC_BASE_URL 时始终以显式配置为准。
     */
    private static String resolveTracePublicBaseUrl() {
        String explicit = System.getenv("TRACE_PUBLIC_BASE_URL");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String tunnelUrl = readNgrokPublicUrl();
        if (tunnelUrl != null) {
            return tunnelUrl;
        }
        return "http://" + LanAddressResolver.resolve() + ":5199";
    }

    private static String readNgrokPublicUrl() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(NGROK_PROBE_TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(NGROK_TUNNELS_API))
                    .timeout(NGROK_PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            Matcher matcher = NGROK_PUBLIC_URL.matcher(response.body());
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException ignored) {
            // 隧道没启动时这里会直接连接失败，属于预期情况，交给局域网地址兜底。
        }
        return null;
    }

    private static Path resolvePlatformRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("platform/后端服务_backend");
            if (Files.isDirectory(candidate)) {
                return current.resolve("platform");
            }
            candidate = current.resolve("后端服务_backend");
            if (Files.isDirectory(candidate)) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "无法定位 platform 目录，请将后端服务_demo工作目录设置为项目根目录。"
        );
    }

    private static Process startIotService(
            Path iotJar,
            Path platformRoot,
            Path scriptRoot
    ) {
        if (!Files.isRegularFile(iotJar)) {
            System.out.println("演示总控：未找到物联网服务 JAR，跳过启动：" + iotJar);
            return null;
        }
        if (hasProcess("greenhouse-iot-0.1.0-SNAPSHOT.jar")) {
            System.out.println("演示总控：物联网服务已在运行，跳过启动");
            return null;
        }

        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable(),
                "-jar",
                iotJar.toString(),
                "--spring.main.web-application-type=none"
        );
        builder.directory(platformRoot.toFile());
        redirectOutput(builder, scriptRoot.resolve("运行日志_IDEA物联网.txt"));
        Process process = start(builder, "物联网服务");
        System.out.println("演示总控：物联网服务已启动，pid=" + process.pid());
        return process;
    }

    private static void waitForIotReady(Path logFile) {
        Instant deadline = Instant.now().plus(IOT_READY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (Files.isRegularFile(logFile)) {
                try {
                    String text = Files.readString(logFile, StandardCharsets.UTF_8);
                    if (text.contains("Started IotApplication")) {
                        return;
                    }
                } catch (IOException ignored) {
                    // 日志文件可能正由子进程写入，下一轮继续检查。
                }
            }
            sleep(500);
        }
        System.out.println("演示总控：物联网服务未在 30 秒内就绪，继续启动设备模拟器");
    }

    private static void startDeviceSimulator(Path simulatorScript, Path scriptRoot) {
        if (!Files.isRegularFile(simulatorScript)) {
            System.out.println("演示总控：未找到设备模拟器脚本，跳过启动");
            return;
        }
        if (hasProcess("模拟设备_command-simulator.ps1")) {
            System.out.println("演示总控：设备模拟器已在运行，跳过启动");
            return;
        }

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                simulatorScript.toString(),
                "-ApiBase",
                "http://localhost:8090",
                "-IntervalSeconds",
                "2"
        );
        builder.directory(simulatorScript.getParent().toFile());
        redirectOutput(builder, scriptRoot.resolve("运行日志_IDEA设备模拟器.txt"));
        Process process = start(builder, "设备模拟器");
        System.out.println("演示总控：设备模拟器已启动，pid=" + process.pid());
    }

    private static void startFrontend(Path frontendRoot, Path scriptRoot) {
        if (!Files.isDirectory(frontendRoot)) {
            System.out.println("演示总控：未找到前端目录，跳过启动");
            return;
        }
        if (isPortOpen("localhost", 5199)) {
            System.out.println("演示总控：前端 5199 已在运行，跳过启动");
            return;
        }

        ProcessBuilder builder = new ProcessBuilder(
                "npm.cmd",
                "run",
                "dev",
                "--",
                // 监听所有网卡：手机扫码访问 http://<电脑局域网IP>:5199 才能打开
                "--host",
                "0.0.0.0",
                "--port",
                "5199"
        );
        builder.directory(frontendRoot.toFile());
        redirectOutput(builder, scriptRoot.resolve("运行日志_IDEA前端.txt"));
        Process process = start(builder, "前端");
        System.out.println("演示总控：前端已启动，pid=" + process.pid());
    }

    private static void redirectOutput(ProcessBuilder builder, Path logFile) {
        try {
            Files.createDirectories(logFile.getParent());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        } catch (IOException exception) {
            builder.inheritIO();
        }
    }

    private static Process start(ProcessBuilder builder, String label) {
        try {
            Process process = builder.start();
            CHILD_PROCESSES.add(process);
            return process;
        } catch (IOException exception) {
            throw new IllegalStateException(label + "启动失败", exception);
        }
    }

    private static boolean hasProcess(String commandMarker) {
        String normalizedMarker = commandMarker.toLowerCase(Locale.ROOT);
        return ProcessHandle.allProcesses()
                .map(process -> process.info().commandLine().orElse(""))
                .map(command -> command.toLowerCase(Locale.ROOT))
                .anyMatch(command -> command.contains(normalizedMarker));
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Process process : CHILD_PROCESSES) {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
            }
        }, "demo-child-process-shutdown"));
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
