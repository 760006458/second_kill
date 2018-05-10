package com.example.controller;

import com.example.service.RedisDistributedLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xuan
 * @create 2018-05-09 18:56
 **/
@RestController
public class MyController {

    private static HashMap<String, Integer> map = new HashMap();

    static {
        map.put("1001", 100);
    }

    //设置Redis分布式锁的超时时间为10秒
    private static final long TIMEOUT = 10 * 1000;

    private AtomicInteger count = new AtomicInteger(100);

    @Autowired
    private StringRedisTemplate template;

    @Autowired
    private RedisDistributedLock lock;

    /**
     * 减库存
     *
     * @return
     */
    @RequestMapping("/decr1")
    public String decr1() {
        Long stock = template.boundValueOps("secondKill").increment(-1);
        if (stock < 0) {
            return "fail";
        }
        System.out.println(count.decrementAndGet());
        return "success";
    }

    /**
     * 减库存，但好像前半部分没意义，还是得操作Redis
     * 优点：简单省事
     * 缺点：Redis存储的信息量太少，只能确定是否抢光，而不能确定活动是否过期
     *
     * @return
     */
    @RequestMapping("/decr2")
    public String decr2() {
        int stock = Integer.valueOf(template.boundValueOps("secondKill").get());
        if (stock < 0) {
            return "fail";
        }

        Long newStock = template.boundValueOps("secondKill").increment(-1);
        if (newStock < 0) {
            return "fail";
        }
        System.out.println(count.decrementAndGet());
        //将订单插入数据库（前边的Redis缓存已经缓冲过滤了绝大部分秒杀失败的订单）
        return "success";
    }

    /**
     * 使用Redis分布式锁：高效的保证“加锁”和“解锁”之间的业务操作是单线程的
     *
     * @return
     */
    @RequestMapping("/decr3")
    public String decr3(@RequestParam String productId) {
        long value = System.currentTimeMillis() + TIMEOUT;
        //加锁
        if (!lock.lock(productId, String.valueOf(value))) {
//            throw new RuntimeException("秒杀人数太多了");
            return "false";
        }

        //业务操作---如果库存足够，则保存订单到数据库
        Integer stock = map.get("1001");
        if (stock > 0) {
            map.put("1001", stock - 1);
            System.out.println("数据库保存订单，剩余库存：" + (stock - 1));
        }

        //解锁
        lock.unlock(productId, String.valueOf(value));
        return "success";
    }
}
