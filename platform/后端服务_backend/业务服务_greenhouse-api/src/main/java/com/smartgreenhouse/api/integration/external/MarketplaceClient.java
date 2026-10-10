package com.smartgreenhouse.api.integration.external;

/**
 * 京东农场等产销平台适配器：同步在售商品与批次信息。
 */
public interface MarketplaceClient {

    ExternalCallResult syncProducts(String greenhouseCode);

    String mode();
}
