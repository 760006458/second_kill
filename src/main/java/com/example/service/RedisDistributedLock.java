package com.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author xuan
 * @create 2018-05-10 16:29
 **/
@Service
@Slf4j
public class RedisDistributedLock {

    @Autowired
    private StringRedisTemplate template;

    /**
     * Redis分布式锁---加锁（内含死锁超时机制）
     * 代码中总共操作3次Redis，而之后死锁超时才会触发第三次（概率极小），所以加锁操作基本就操作两次Redis
     *
     * @param key   秒杀商品ID
     * @param value 当前时间 + 超时时间
     * @return
     */
    public boolean lock(String key, String value) {
        if (template.boundValueOps(key).setIfAbsent(value)) {
            return true;
        }
        //死锁超时机制
        String oldTimeOutValue = template.boundValueOps(key).get(); //之前设置的超时时间
        //当前可能有多条线程同时超时，进入if判断
        if (!StringUtils.isEmpty(oldTimeOutValue) && Long.parseLong(oldTimeOutValue) < System.currentTimeMillis()) {
            //无论此处并发量多大，接下来的几行代码只有第一个线程能getAndSet成功
            String currentValue = template.boundValueOps(key).getAndSet(value);
            if (!StringUtils.isEmpty(currentValue) && currentValue.equals(oldTimeOutValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解锁
     *
     * @param key   秒杀商品ID
     * @param value 当前时间 + 超时时间（即加锁时传入的时间）
     */
    public void unlock(String key, String value) {
        try {
            String oldTimeOutValue = template.boundValueOps(key).get();
            if (!StringUtils.isEmpty(oldTimeOutValue) && oldTimeOutValue.equals(value)) {
                template.opsForValue().getOperations().delete(key);
            }
        } catch (Exception e) {
            log.error("【redis分布式锁】解锁异常，{}", e);
        }
        System.out.println(123);
    }
}
