package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
class ShardingJdbcDemoApplicationTests {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void insert() {
        String sql = "insert into order_info(id,order_amount,order_status,user_id) values(4,188.66,1,3)";
        int update = jdbcTemplate.update(sql);
        System.out.println("insert-----------影响数据行数："+update);
    }

    @Test
    void query(){
        String sql = "select * from order_info";
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        maps.forEach(System.out::print);

    }


    // 广播表测试
    @Test
    void broadCastTable() {
        String sql = "insert into province_info(id,name) values(1,'beijing'),(2,'shanghai')";
        int i = jdbcTemplate.update(sql);
        System.out.println("广播表插入数据，影响行数："+ i);
        // 查询
        sql = "select * from province_info";
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
        maps.forEach(System.out::println);
    }
}
