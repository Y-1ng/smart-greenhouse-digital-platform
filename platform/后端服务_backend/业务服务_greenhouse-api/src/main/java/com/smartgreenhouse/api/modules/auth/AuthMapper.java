package com.smartgreenhouse.api.modules.auth;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthMapper {

    @Select("""
            SELECT
              user_id,
              username,
              password_hash,
              display_name,
              role_code,
              status
            FROM user_account
            WHERE username = #{username}
            LIMIT 1
            """)
    @ConstructorArgs({
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "username", javaType = String.class),
            @Arg(column = "password_hash", javaType = String.class),
            @Arg(column = "display_name", javaType = String.class),
            @Arg(column = "role_code", javaType = String.class),
            @Arg(column = "status", javaType = Integer.class)
    })
    UserRow findByUsername(@Param("username") String username);

    record UserRow(
            Long userId,
            String username,
            String passwordHash,
            String displayName,
            String roleCode,
            Integer status
    ) {
    }
}
