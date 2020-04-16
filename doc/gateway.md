[TOC]

## 目录

- [1. 服务网关在微服务中的应用](#1-服务网关在微服务中的应用)
- [2. 第二代网关Gateway](#2-第二代网关Gateway)
- [3. Gateway快速落地实施](#3-Gateway快速落地实施)
- [4. 路由功能详解](#4-路由功能详解)
- [5. 断言功能详解](#5-断言功能详解)
- [6. 实现断言的配置](#6-实现断言的配置)
- [7. 通过After断言实现定时秒杀](#7-通过After断言实现定时秒杀)
- [8. 过滤器原理和生命周期](#8-过滤器原理和生命周期)
- [9. 自定义过滤器实现接口计时功能](#9-自定义过滤器实现接口计时功能)
- [10. 权限认证方案分析](#10-权限认证方案分析)



# SpringCloud服务网关

# 1. 服务网关在微服务中的应用

## 1.1. 对外服务的难题

微服务的应用系统体系很庞大，光是需要独立部署的基础组件就有注册中心、配置中心、服务总线、Turbine和监控大盘dashboard、调用链追踪和链路聚合，还有kafka和MQ之类的中间件，再加上拆分后的零散微服务，一个系统轻松就有20多个左右部署包

都微服务了，所有的业务对外都是实现单一原则，这就导致服务节点和服务数增多，一个整体的链路需要整合很多服务进行组合使用

还有一个问题就是安全性，如果让所有服务都引入安全验证，把所有的接口都加上安全验证，要更换成OAuth2.0，这个时候让所有的服务提供者都变更？

## 1.2. 微服务的传达室

我们就给微服务引入一层专事专办的中间层-传达室

1、访问控制：看你是否有权进入，拒绝无权来访者

2、引导指路：问你做什么，给你指路，就是路由

网关层作为唯一的对外服务，外部请求不直接访问服务层，由网关层承接所有HTTP请求，我们会将gateway和nginx一同使用

### 1.2.1. 访问控制

- 拦截请求：识别header中的授权令牌信息，如果没有登录信息就返回403
- 鉴权：对令牌进行验证，如果令牌失败或过期就拒绝服务

### 1.2.2. 路由规则

- URL映射：大多数情况下我们给到服务调用方的地址是一个虚拟路由地址，对应的真实地址是由路由规则进行映射
- 服务寻址：URL映射好了之后，如果服务端有多个节点，对于服务集群应该服务访问，需要实现负载均衡策略了（SpringCloud中gateway借助Eureka的服务发现通过Ribbon实现负载均衡）

# 2. 第二代网关Gateway

**Gateway的标签**

- Gateway是Spring官方主推的组件
- 底层是基于Netty构建，一个字概括就是快
- 由spring开源社区直接贡献开源力量的

**Gateway可以做什么**

- 路由寻址
- 负载均衡
- 限流
- 鉴权

Gateway VS zuul（第一代网关是Netflix出品）

|                 | Gateway          | zuul 1.x           | zuul 2.x                         |
| --------------- | ---------------- | ------------------ | -------------------------------- |
| 靠谱性          | 官方背书指出     | 开创者，曾经靠谱   | 一直跳票，千呼万唤始出来         |
| 性能            | Netty            | 同步阻塞，性能慢   | Netty                            |
| QPS             | 超30000          | 20000左右          | 20000-30000                      |
| SpringCloud     | 已整合           | 已整合             | 暂无整合到组件库计划，但可以引用 |
| 长连接keepalive | 支持             | 不支持             | 支持                             |
| 编程体验        | 略复杂           | 同步模型，比较简单 | 略复杂                           |
| 调试&链路追踪   | 异步模型，略复杂 | 同步方式，比较容易 | 异步模型，略复杂                 |

新的项目果断选择Gateway

# 3. Gateway快速落地实施

- 创建gateway项目，引入依赖
- 连接Eureka基于服务发现自动创建路由规则
- 通过Actuator实现动态路由

导入POM依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>spring-cloud-learn</artifactId>
        <groupId>com.icodingedu.springcloud</groupId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>
    <artifactId>gateway-server</artifactId>
    <name>gateway-server</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <!--redis limiter flow-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
    </dependencies>
</project>
```

application启动类

```java
package com.icodingedu.springcloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServerApplication.class,args);
    }
}
```

application.yaml配置

```yaml
spring:
  application:
    name: gateway-server
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
server:
  port: 65000
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:20001/eureka/
management:
  security:
    enabled: false
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

启动服务：eureka-server、feign-client-advanced（启动三个）、gateway-server

启动后访问：http://localhost:65000/actuator/gateway/routes

可以得到动态加载的eureka路由规则

通过自动路由规则负载均衡实现：http://localhost:65000/FEIGN-CLIENT/sayhello

访问服务的路径希望是小写的

```yaml
spring:
  application:
    name: gateway-server
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true # 增加这个
```

gateway动态路由规则配置

```json
# POST
# http://localhost:65000/actuator/gateway/routes/myrouter
{
    "predicates": [
        {
            "name": "Path",
            "args": {
                "_genkey_0": "/myrouter-path/**"
            }
        }
    ],
    "filters": [
        {
            "name": "StripPrefix",
            "args": {
                "_genkey_0": "1"
            }
        }
    ],
    "uri": "lb://FEIGN-CLIENT",
    "order": 0
}
# DELETE删除路由规则
# http://localhost:65000/actuator/gateway/routes/myrouter
```

先删除路由表，再删除服务

# 4. 路由功能详解

## 4.1. 路由的组成结构

Gateway中可以有多个Route，一个Route就是一套包含完整转发规则的路由，主要由三部分组成

- **断言集合** 断言是路由处理的第一个环节，它是路由的匹配规则，它决定了一个网络请求是否可以匹配给当前路由来处理。之所以它是一个集合的原因是我们可以给一个路由添加多个断言，当每个断言都匹配成功以后才算过了路由的第一关。有关断言的详细内容将在下一小节进行介绍
- **过滤器集合** 如果请求通过了前面的断言匹配，那就表示它被当前路由正式接手了，接下来这个请求就要经过一系列的过滤器集合。过滤器的功能就是八仙过海各显神通了，可以对当前请求做一系列的操作，比如说权限验证，或者将其他非业务性校验的规则提到网关过滤器这一层。在过滤器这一层依然可以通过修改Response里的Status Code达到中断效果，比如对鉴权失败的访问请求设置Status Code为403之后中断操作。有关过滤器的详细内容将在后面的小节介绍
- **URI** 如果请求顺利通过过滤器的处理，接下来就到了最后一步，那就是转发请求。URI是统一资源标识符，它可以是一个具体的网址，也可以是IP+端口的组合，或者是Eureka中注册的服务名称

## 4.2. 负载均衡

对最后一步寻址来说，如果采用基于Eureka的服务发现机制，那么在Gateway的转发过程中可以采用服务注册名的方式来调用，后台会借助Ribbon实现负载均衡（可以为某个服务指定具体的负载均衡策略），其配置方式如：`lb://FEIGN-SERVICE-PROVIDER/`，前面的lb就是指代Ribbon作为LoadBalancer。

## 4.3. 工作流程

![image-20200412205111358](./assets/gateway/image-20200412205111358.png)

- **Predicate Handler** （断言）具体承接类是RoutePredicateHandlerMapping。首先它获取所有的路由（配置的routes全集），然后依次循环每个Route，把应用请求与Route中配置的所有断言进行匹配，如果当前Route所有断言都验证通过，Predict Handler就选定当前的路由。这个模式是典型的职责链。
- **Filter Handler** 在前一步选中路由后，由FilteringWebHandler将请求交给过滤器，在具体处理过程中，不仅当前Route中定义的过滤器会生效，我们在项目中添加的全局过滤器（Global Filter）也会一同参与。同学们看到图中有Pre Filter和Post Filter，这是指过滤器的作用阶段，我们在稍后的章节中再深入了解
- **寻址** 这一步将把请求转发到URI指定的地址，在发送请求之前，所有Pre类型过滤器都将被执行，而Post过滤器会在调用请求返回之后起作用。有关过滤器的详细内容将会在稍后的章节里讲到。

# 5. 断言功能详解

Predicate接受一个判断条件，返回true或false的布尔值，告知调用方判断结果，也可以通过and、or、negative（非）三个操作符来将多个Predicate，对所有来的Request进行条件判断

只要网关接收到请求立即触发断言，满足所有的断言后才进入Filter阶段

Gateway给我们提供了十几种内置断言，常用的就下面几种

## 5.1. Path匹配

```java
.router(r -> r.path("/gateway/**"))
  						.uri("lb://FEIGN-CLIENT")
)
.router(r -> r.path("/baidu"))
  						.uri("https://www.baidu.com")
)  
```

## 5.2. Method断言

```java
.router(r -> r.path("/gateway/**"))
  						.and().method(HttpMethod.GET)
  						.uri("lb://FEIGN-CLIENT")
)
```

## 5.3. RequestParam断言

```java
.router(r -> r.path("/gateway/**"))
  						.and().method(HttpMethod.GET)
  						.and().query("name","icodingedu")
  						.and().query("age")
  						.uri("lb://FEIGN-CLIENT")
)
//这里的age仅需要有age这个参数即可，至于值是什么不关心，但name的值必须是icodingedu
```

## 5.4. Header断言

```java
.router(r -> r.path("/gateway/**"))
  						.and().header("Authorization")
  						.uri("lb://FEIGN-CLIENT")
)
//header中必须包含一个Authorization属性，也可以传入两个参数，锁定值
```

## 5.5. Cookie断言

```java
.router(r -> r.path("/gateway/**"))
  						.and().cookie("name","icodingedu")
  						.uri("lb://FEIGN-CLIENT")
)
//cookie是几个参数断言中唯一一个必须指定值的断言
```

## 5.6. 时间片匹配

时间片匹配有三种模式：Before、After、Between，这个指定了在什么时间范围内容路由才生效

```java
.router(r -> r.path("/gateway/**"))
  						.and().after("具体时间")
  						.uri("lb://FEIGN-CLIENT")
)
```

# 6. 实现断言的配置

断言配置可以在yaml和java代码里都能够实现

**在yaml里配置一个，rotues这部分**

```yaml
spring:
  application:
    name: gateway-server
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
      - id: feignclient
        uri: lb://FEIGN-CLIENT
        predicates:
        - Path=/gavinyaml/**
        filters:
        - StripPrefix=1
```

配置完成后：http://localhost:65000/actuator/gateway/routes

**在Java程序里进行配置**

创建一个config包，建立一个配置类

```java
package com.icodingedu.springcloud.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

@Configuration
public class GatewayConfiguration {

    @Bean
    @Order
    public RouteLocator customerRoutes(RouteLocatorBuilder builder){
        return builder.routes()
                .route(r -> r.path("/gavinjava/**")
                        .and().method(HttpMethod.GET)
                        .and().header("name")
                        .filters(f -> f.stripPrefix(1)
                            .addResponseHeader("java-param","gateway-config")
                        )
                        .uri("lb://FEIGN-CLIENT")
                ).build();
    }
}
```

# 7. 通过After断言实现定时秒杀

geteway调用的是feign-client的业务，我们就到feign-client-advanced里创建一个controller实现

这里面要使用到的product需要提前在feign-client-intf中定义好

```java
package com.icodingedu.springcloud.pojo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Product {
    private Long productId;
    private String description;
    private Long stock;
}
```

feign-client-advanced中创建GatewayController

```java
package com.icodingedu.springcloud.controller;

import com.icodingedu.springcloud.pojo.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Slf4j
@RequestMapping("gateway")
public class GatewayController {
    //我们就构建一个简易的数据存储,Product需要在feign-client-intf中定义
    public static final Map<Long, Product> items = new ConcurrentHashMap<>();

    @GetMapping("detail")
    public Product getProduct(Long pid){
        //如果第一次没有先创建一个
        if(!items.containsKey(pid)){
            Product product = Product.builder().productId(pid)
                                .description("very well!")
                                .stock(100L).build();
            //没有才插入数据
            items.putIfAbsent(pid,product);
        }
        return items.get(pid);
    }

    @GetMapping("placeOrder")
    public String buy(Long pid){
        Product product = items.get(pid);
        if(product==null){
            return "Product Not Found";
        }else if(product.getStock()<=0L){
            return "Sold Out";
        }
        synchronized (product){
            if(product.getStock()<=0L){
                return "Sold Out";
            }
            product.setStock(product.getStock()-1);
        }
        return "Order Placed";
    }
}
```

回到Gateway-sever项目，按照时间顺延方式定义

```java
package com.icodingedu.springcloud.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import java.time.ZonedDateTime;

@Configuration
public class GatewayConfiguration {

    @Bean
    @Order
    public RouteLocator cutomerRoutes(RouteLocatorBuilder builder){
        return builder.routes()
                .route(r -> r.path("/gavinjava/**")
                    .and().method(HttpMethod.GET)
                    .and().header("name")
                    .filters(f -> f.stripPrefix(1)
                        .addResponseHeader("java-param","gateway-config")
                    )
                    .uri("lb://FEIGN-CLIENT")
                )
                .route(r -> r.path("/secondkill/**")
                    .and().after(ZonedDateTime.now().plusSeconds(20))
                    .filters(f -> f.stripPrefix(1))
                    .uri("lb://FEIGN-CLIENT")
                )
                .build();
    }
}
```

也可以定义精确的时间节点值

```java
    @Bean
    @Order
    public RouteLocator cutomerRoutes(RouteLocatorBuilder builder){
        LocalDateTime ldt = LocalDateTime.of(2020, 4, 11, 16, 11, 10);
        return builder.routes()
                .route(r -> r.path("/gavinjava/**")
                    .and().method(HttpMethod.GET)
                    .and().header("name")
                    .filters(f -> f.stripPrefix(1)
                        .addResponseHeader("java-param","gateway-config")
                    )
                    .uri("lb://FEIGN-CLIENT")
                )
                .route(r -> r.path("/secondkill/**")
                    .and().after(ZonedDateTime.of(ldt,ZoneId.of("Asia/Shanghai")))
                    .filters(f -> f.stripPrefix(1))
                    .uri("lb://FEIGN-CLIENT")
                )
                .build();
    }
```

# 8. 过滤器原理和生命周期

**过滤器的实现方式**

只需要实现两个接口：GatewayFilter、Ordered

**过滤器类型**

**Header过滤器**：可以增加和减少header里的值

**StripPrefix过滤器**：

```java
.router(r -> r.path("/gateway/**"))
  						.filters(f -> f.stripPrefix(1))
  						.uri("lb://FEIGN-CLIENT")
)
```

假如请求的路径：/gateway/sample/update，如果没有stripPrefix过滤器，http://FEIGN-CLIENT/gateway/sample/update，他的作用就是将第一个路由路径截取掉

**PrefixPath过滤器**：它和StripPrefix作用相反

```java
.router(r -> r.path("/gateway/**"))
  						.filters(f -> f.prefixPath("go"))
  						.uri("lb://FEIGN-CLIENT")
)
```

/gateway/sample/update 变成 /go/gateway/sample/update

**RedirectTo过滤器：**

```java
.filters(f -> f.redirect(303,"https://www.icodingedu.com"))
// 遇到错误是30x的直接过滤跳转
```

# 9. 自定义过滤器实现接口计时功能

 去到gateway-server项目中进行修改，创建一个filter的package

```java
package com.icodingedu.springcloud.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

//Ordered是指定执行顺序的接口
@Slf4j
@Component
public class TimerFilter implements GatewayFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //给接口计时并能打出很漂亮的log
        StopWatch timer = new StopWatch();
        timer.start(exchange.getRequest().getURI().getRawPath());//开始计时
        //我们还可以对调用链进行加工,手工放入请求参数
        exchange.getAttributes().put("requestTimeBegin",System.currentTimeMillis());
        return chain.filter(exchange).then(
            //这里就是执行完过滤进行调用的地方
           Mono.fromRunnable(() -> {
               timer.stop();
               log.info(timer.prettyPrint());
           })
        );
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
```

拿上TimerFilter去到GatewayConfiguration里设置自定义filter

```java
package com.icodingedu.springcloud.config;

import com.icodingedu.springcloud.filter.TimerFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Configuration
public class GatewayConfiguration {

//    @Autowired
//    private TimerFilter timerFilter;

    @Bean
    @Order
    public RouteLocator customerRoutes(RouteLocatorBuilder builder){
        LocalDateTime ldt1 = LocalDateTime.of(2020,4,12,22,6,30);
        LocalDateTime ldt2 = LocalDateTime.of(2020,4,12,23,6,35);
        return builder.routes()
                .route(r -> r.path("/gavinjava/**")
                        .and().method(HttpMethod.GET)
                        .and().header("name")
                        .filters(f -> f.stripPrefix(1)
                            .addResponseHeader("java-param","gateway-config")
//                            .filter(timerFilter)
                        )
                        .uri("lb://FEIGN-CLIENT")
                )
                .route(r -> r.path("/secondkill/**")
                        //.and().after(ZonedDateTime.of(ldt, ZoneId.of("Asia/Shanghai")))
                        .and().between(ZonedDateTime.of(ldt1, ZoneId.of("Asia/Shanghai")),ZonedDateTime.of(ldt2, ZoneId.of("Asia/Shanghai")))
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://FEIGN-CLIENT")
                )
                .build();
    }
}
```

全局Filter就是把filter的继承从GatewayFilter换成GlobalFilter

# 10. 权限认证方案分析

## 10.1. 传统单应用的用户鉴权

从我们开始学JavaEE的时候，就被洗脑式灌输了一种权限验证的标准做法，那就是将用户的登录状态保存到HttpSession中，比如在登录成功后保存一对key-value值到session，key是userId而value是用户后台的真实ID。接着创建一个ServletFilter过滤器，用来拦截需要登录才能访问的资源，假如这个请求对应的服务端session里找不到userId这个key，那么就代表用户尚未登录，这时候可以直接拒绝服务然后重定向到用户登录页面。

大家应该都对session机制比较熟悉，它和cookie是相互依赖的，cookie是存放在用户浏览器中的信息，而session则是存放在服务器端的。当浏览器发起服务请求的时候就会带上cookie，服务器端接到Request后根据cookie中的jsessionid拿到对应的session。

由于我们只启动一台服务器，所以在登录后保存的session始终都在这台服务器中，可以很方便的获取到session中的所有信息。用这野路子，我们一路搞定了各种课程作业和毕业设计。结果一到工作岗位发现行不通了，因为所有应用都是集群部署，在一台机器保存了的session无法同步到其他机器上。那我们有什么成熟的解决方案吗？

## 10.2. 分布式环境下的解决方案

### 10.2.1. 同步Session

- Session复制是最容易先想到的解决方案，我们可以把一台机器中的session复制到集群中的其他机器。比如Tomcat中也有内置的session同步方案，但是这并不是一个很优雅的解决方案，它会带来以下两个问题：

  - **Timing问题** 同步需要花费一定的时间，我们无法保证session同步的及时性，也就是说，当用户发起的两个请求分别落在不同机器上的时候，前一个请求写入session的信息可能还没同步到所有机器，后一个请求就已经开始执行业务逻辑了，这不免引起脏读幻读。
  - **数据冗余** 所有服务器都需要保存一份session全集，这就产生了大量的冗余数据

### 10.2.2. 反向代理：绑定IP或一致性Hash

这个方案可以放在Nignx网关层做的，我们可以指定某些IP段的请求落在某个指定机器上，这样一来session始终只存在一台机器上。不过相比前一种session复制的方法来说，绑定IP的方式有更明显的缺陷：

- **负载均衡** 在绑定IP的情况下无法在网关层应用负载均衡策略，而且某个服务器出现故障的话会对指定IP段的来访用户产生较大影响。对网关层来说该方案的路由规则配置也极其麻烦。
- **IP变更** 很多网络运营商会时不时切换用户IP，这就会导致更换IP后的请求被路由到不同的服务节点处理，这样一来就读不到前面设置的session信息了

为了解决第二个问题，可以通过一致性Hash的路由方案来做路由，比如根据用户ID做Hash，不同的Hash值落在不同的机器上，保证足够均匀的分配，这样也就避免了IP切换的问题，但依然无法解决第一点里提到的负载均衡问题

### 10.2.3. Redis解决方案

这个方案解决了前面提到的大部分问题，session不再保存在服务器上，取而代之的是保存在redis中，所有的服务器都向redis写入/读取缓存信息。

在Tomcat层面，我们可以直接引入tomcat-redis-session-manager组件，将容器层面的session组件替换为基于redis的组件，但是这种方案和容器绑定的比较紧密。另一个更优雅的方案是借助spring-session管理redis中的session，尽管这个方案脱离了具体容器，但依然是基于Session的用户鉴权方案，这类Session方案已经在微服务应用中被淘汰了。

## 10.3. 分布式Session的解决方案

### 10.3.1. OAuth 2.0

OAuth 2.0是一个开放授权标准协议，它允许用户让第三方应用访问该用户在某服务的特定私有资源，但是不提供账号密码信息给第三方应用

拿微信登录第三方应用的例子来说：

- **Auth Grant** 在这一步Client发起Authorization Request到微信系统（比如通过微信内扫码授权），当身份验证成功后获取Auth Grant
- **Get Token** 客户端拿着从微信获取到的Auth Grant，发给第三方引用的鉴权服务，换取一个Token，这个Token就是访问第三方应用资源所需要的令牌
- **访问资源** 最后一步，客户端在请求资源的时候带上Token令牌，服务端验证令牌真实有效后即返回指定资源

我们可以借助Spring Cloud中内置的`spring-cloud-starter-oauth2`组件搭建OAuth 2.0的鉴权服务，OAuth 2.0的协议还涉及到很多复杂的规范，比如角色、客户端类型、授权模式等。

### 10.3.2. JWT鉴权

JWT也是一种基于Token的鉴权机制，它的基本思想就是通过用户名+密码换取一个Access Token

**鉴权流程**

相比OAuth 2.0来说，它的鉴权过程更加简单，其基本流程是这样的：

1. 用户名+密码访问鉴权服务
   - 验证通过：服务器返回一个Access Token给客户端，并将token保存在服务端某个地方用于后面的访问控制（可以保存在数据库或者Redis中）
   - 验证失败：不生成Token
2. 客户端使用令牌访问资源，服务器验证令牌有效性
   - 令牌错误或过期：拦截请求，让客户端重新申请令牌
   - 令牌正确：允许放行

**Access Token中的内容**

JWT的Access Token由三个部分构成，分别是Header、Payload和Signature，我们分别看下这三个部分都包含了哪些信息：

- **Header** 头部声明了Token的类型（JWT类型）和采用的加密算法（HS256）

```json
{
  'typ': 'JWT',
  'alg': 'HS256'
}
```

- **Payload** 这一段包含的信息相当丰富，你可以定义Token签发者、签发和过期时间、生效时间等一系列属性，还可以添加自定义属性。服务端收到Token的时候也同样可以对Payload中包含的信息做验证，比如说某个Token的签发者是“Feign-API”，假如某个接口只能允许“Gateway-API”签发的Token，那么在做鉴权服务时就可以加入Issuer的判断逻辑。
- **Signature** 它会使用Header和Payload以及一个密钥用来生成签证信息，这一步会使用Header里我们指定的加密算法进行加密

目前实现JWT的开源组件非常多，如果决定使用这个方案，只要添加任意一个开源JWT实现的依赖项到项目的pom文件中，然后在加解密时调用该组件来完成

**目前来说应用比较广泛的三种方案就是JWT、OAuth和spring-session+redis**