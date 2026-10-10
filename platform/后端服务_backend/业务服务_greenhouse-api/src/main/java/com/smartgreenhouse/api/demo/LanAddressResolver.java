package com.smartgreenhouse.api.demo;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 解析本机当前用于对外通信的局域网 IPv4。
 *
 * <p>二维码公开地址需要随时跟着当前网络走，写死 IP 换网就会失效。
 * 这里先用 UDP connect 查一次系统路由表（不发包），优先拿到默认出口网卡地址；
 * 没有默认路由时再回退到网卡枚举，跳过 VMware / VirtualBox / WSL 等虚拟网段。
 */
public final class LanAddressResolver {

    private static final String ROUTE_PROBE_ADDRESS = "8.8.8.8";
    private static final int ROUTE_PROBE_PORT = 53;

    private LanAddressResolver() {
    }

    public static String resolve() {
        // 手机扫码场景下，手机通常和电脑走同一张无线网卡（同一路由器或电脑连手机热点），
        // 因此无线网卡地址优先；没有无线地址时再按默认路由选择。
        String wireless = resolveByWirelessInterface();
        if (wireless != null) {
            return wireless;
        }
        String routed = resolveByRoute();
        if (routed != null) {
            return routed;
        }
        String enumerated = resolveByInterface();
        return enumerated == null ? "127.0.0.1" : enumerated;
    }

    private static String resolveByWirelessInterface() {
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp()
                        || network.isLoopback()
                        || network.isVirtual()
                        || isVirtualAdapter(network)
                        || !isWirelessAdapter(network)) {
                    continue;
                }
                Enumeration<InetAddress> addresses =
                        network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (isUsableSiteLocalAddress(address)) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // 没有无线网卡时走默认路由回退。
        }
        return null;
    }

    private static String resolveByRoute() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(
                    InetAddress.getByName(ROUTE_PROBE_ADDRESS),
                    ROUTE_PROBE_PORT
            );
            InetAddress local = socket.getLocalAddress();
            if (isUsableSiteLocalAddress(local)) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
            // 没有默认路由时走网卡枚举回退。
        }
        return null;
    }

    private static String resolveByInterface() {
        try {
            List<String> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp()
                        || network.isLoopback()
                        || network.isVirtual()
                        || isVirtualAdapter(network)) {
                    continue;
                }
                Enumeration<InetAddress> addresses =
                        network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!isUsableSiteLocalAddress(address)) {
                        continue;
                    }
                    String host = address.getHostAddress();
                    if (isPreferredAdapter(network)) {
                        return host;
                    }
                    candidates.add(host);
                }
            }
            return candidates.isEmpty() ? null : candidates.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isUsableSiteLocalAddress(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && address.isSiteLocalAddress();
    }

    private static boolean isVirtualAdapter(NetworkInterface network) {
        String name = adapterText(network);
        return name.contains("vmnet")
                || name.contains("virtualbox")
                || name.contains("vbox")
                || name.contains("virtual")
                || name.contains("vethernet")
                || name.contains("hyper-v")
                || name.contains("wsl")
                || name.contains("docker");
    }

    private static boolean isPreferredAdapter(NetworkInterface network) {
        String name = adapterText(network);
        return isWirelessAdapter(network)
                || name.contains("eth")
                || name.startsWith("en");
    }

    private static boolean isWirelessAdapter(NetworkInterface network) {
        String name = adapterText(network);
        return name.contains("wlan")
                || name.contains("wifi")
                || name.contains("wi-fi")
                || name.contains("wireless")
                || name.contains("无线");
    }

    private static String adapterText(NetworkInterface network) {
        String name = network.getName() == null ? "" : network.getName();
        String displayName = network.getDisplayName() == null
                ? ""
                : network.getDisplayName();
        return (name + " " + displayName).toLowerCase(Locale.ROOT);
    }
}
