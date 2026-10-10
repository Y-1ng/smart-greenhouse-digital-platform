package com.smartgreenhouse.api.modules.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM greenhouse")
    int countGreenhouses();

    @Select("SELECT COUNT(*) FROM device")
    int countDevices();

    @Select("""
            SELECT COUNT(*)
            FROM device
            WHERE online_status = 'ONLINE'
            """)
    int countOnlineDevices();

    @Select("""
            SELECT COUNT(*)
            FROM device
            WHERE online_status <> 'ONLINE'
            """)
    int countOfflineDevices();

    @Select("""
            SELECT COUNT(*)
            FROM trace_code
            WHERE status = 'ACTIVE'
            """)
    int countActiveTraceCodes();

    @Select("""
            SELECT COUNT(*)
            FROM environment_cycle
            WHERE chain_status <> 'CONFIRMED'
            """)
    int countPendingChainProofs();

    @Select("""
            SELECT COUNT(*)
            FROM scan_record
            WHERE risk_result = 'REPEAT_SCAN'
            """)
    int countRepeatScans();
}
