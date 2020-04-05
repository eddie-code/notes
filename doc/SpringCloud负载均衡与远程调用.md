[TOC]

## [模块]

- [微服务架构设计开篇](./微服务架构设计.md)

- [SpringCloud服务治理](./SpringCloud服务治理.md)

- [SpringCloud负载均衡与远程调用](#SpringCloud负载均衡与远程调用)

  

## 目录

- [1. Ribbon体系架构分析](#1-Ribbon体系架构分析)
- [2. 基于Ribbon的应用](#2-基于Ribbon的应用)
- [3. Ribbon负载均衡策略配置](#3-Ribbon负载均衡策略配置)
- [4. Feign进行远程调用的机制](#4-Feign进行远程调用的机制)
- [5. Feign远程调用实例](#5-Feign远程调用实例)
- [6. 理想的Feign风格项目结构](#6-理想的Feign风格项目结构)
- [7. Feign服务调用超时重试机制](#7-Feign服务调用超时重试机制)
- [8. 配置Feign超时重试验证](#8-配置Feign超时重试验证)



# SpringCloud负载均衡与远程调用

## 1. Ribbon体系架构分析

**负载均衡（Load Balance）**

- 客户端负载均衡
  - 由调用方进行负载判断，这就需要一个服务的注册列表来进行选择和访问
  - 通过本地指定的负载均衡策略来调用服务列表中的哪个服务
  - 对开发人员友好，完全由开发来控制，运维成本低，但强强依赖服务注册中心
  - 客户端一般使用微服务框架实现（Ribbon）
- 服务端负载均衡
  - 在客户端和服务端之间架设了一个负载均衡服务组件。通过这个服务组件进行负载均衡
  - 通过是在一个服务应用，进行负载均衡的配置，通常不依赖服务注册中心
  - 通过使用Nginx、HAProxy、Lvs、F5这些负载均衡器来实现

**Ribbon的体系结构分析**

- IPing：是Ribbon的一套健康检查机制
- IRule：这就是Ribbon负载均衡的组件库，所有经过Ribbon的请求都会经过IRule获取负载均衡的机器

## 2. 基于Ribbon的应用

创建一个带ribbon的eureka-consumer应用，可以直接复制之前的eureka-consumer项目

POM里加入ribbon的依赖功能

```xml
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-ribbon</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>
```

在Application里给RestTemplate增加LoadBalance的注解

```java
@SpringBootApplication
@EnableDiscoveryClient
public class RibbonConsumerApplication {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(RibbonConsumerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

Controller进行一下修改,直接调用client服务即可

```java
package com.icodingedu.springcloud.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@Slf4j
public class ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/hello")
    public String hello(){
        return restTemplate.getForObject("http://eureka-client/sayhello",String.class);
    }
}
```

Properties的配置

```properties
spring.application.name=ribbon-consumer

server.port=30099
# 服务提供者连接的注册中心
eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/
```

> Ribbon是在第一次加载的时候才会去初始化LoadBanlancer，第一次不仅包好HTTP连接和业务请求还包含LoadBanlancer的创建耗时，假如你的方法本身就比较耗时，并且你设置的超时时间不是很长，就很有可能导致第一次HTTP调用失败，这是ribbon的懒加载模式导致的，默认就是懒加载的

```shell
# ribbon开启饥饿加载模式，在启动时就加载LoadBanlancer配置
ribbon.eager-load.enabled=true
# 指定饥饿加载的服务名称
ribbon.eager-load.clents=ribbon-consumer
```

## 3. Ribbon负载均衡策略配置

### 3.1. 负载均衡的策略

- 1、轮询（RoundRobinRule）：轮询有一个上限，当轮询了10个服务节点都不可用，轮询个结束
- 2、随机（RandomRule）：使用jdk自带的随机数生产工具，生成一个随机数，当前节点不可用继续随机，直到随机到可用服务为止
- 3、可用过滤策略（AvailabilityFilteringRule）：过滤掉连接失败的和高并发的，然后从健康的节点中使用轮询策略选出一个节点
- 4、轮询失败重试（RetryRule）：使用轮询策略，如果第一个节点失败，会retry下一个节点，如果还失败就返回失败
- 5、并发量最小可用策略（BestAvailableRule）：会轮询所有节点获取并发量，在其中选择一个最小的进行服务调用，优点：将服务打到最小并发节点，缺点：需要获取所有节点的并发量，比较耗时
- 6、响应时间权重策略（WeightedResponseTimeRule）：根据响应时间，分配一个weight权重，最长权重越小，被选中的可能性就越低，服务刚启动由于信息量不足，会使用轮询方式，待信息充足后切换
- 7、ZoneAvoidanceRule：复合判断server所在区域的性能和server可用性来选择server

### 3.2. 负载均衡配置

全局的负载均衡策略

```java
package com.icodingedu.springcloud.config;

import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.RoundRobinRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RibbonConfiguration {

    @Bean
    public IRule defaultLBStrategy(){
        return new RoundRobinRule();
    }
}
```

指定服务的负载均衡配置

方法一：properites里指定服务名对应的负载均衡策略

```shell
eureka-client.ribbon.NFLoadBalancerRuleClassName=com.netflix.loadbalancer.RandomRule
```

方法二：在configuration上注解实现

```java
@Configuration
@RibbonClient(name = "eureka-client",configuration = com.netflix.loadbalancer.RandomRule.class)
public class RibbonConfiguration {

}
```

### 3.3. 负载均衡的选择

在Ribbon里有两个时间和空间密切相关的负载均衡策略：BestAvailableRule（BA）、WeightedResponseTimeRule（WRT）共同目标都是需要负载选择压力小的服务节点，BA选择并发量最小的机器也就是空间选择，WRT根据时间选择响应最快的服务

对于连接敏感型的服务模型，使用BestAvailableRule策略最合适

对于响应时间敏感的服务模型，使用WeightedResponseTimeRule策略最合适

如果使用了熔断器，用AvailabilityFilteringRule进行负载均衡

## 4. Feign进行远程调用的机制

Eureka：http://ip:port/path

Ribbon：http://serviceName/path

引入Fegin组件来进行远程调用，这两个组件也一并被引入

- Ribbon：利用负载均衡策略进行目标机器选择
- Hystrix：根据熔断状态的开启状态，决定是否发起远程调用

- 发送请求时有两个核心的点
  - 重试
  - 降级

## 5. Feign远程调用实例

建立一个feign的文件夹并创建一个feign-consumer的应用

添加POM依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>spring-cloud-project</artifactId>
        <groupId>com.icodingedu</groupId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>
    <artifactId>feign-consumer</artifactId>
    <name>feign-consumer</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
      	<!--服务配置,bus推送都要依赖这个actuator-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

启动类实现

```java
package com.icodingedu.springcloud;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class FeignConsumerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(FeignConsumerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

创建调用的Service接口引用实现

```java
package com.icodingedu.springcloud.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

//eureka服务提供者的service-name
//这个注解的意思是IService这个接口的调用都发到eureka-client这个服务提供者上
@FeignClient("eureka-client")
public interface IService {

    @GetMapping("/sayhello")
    String sayHello();
}
```

创建一个controller实现

```java
package com.icodingedu.springcloud.controller;

import com.icodingedu.springcloud.service.IService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeignController {

    @Autowired
    private IService service;

    @GetMapping("/sayhi")
    public String sayHi(){
        return service.sayHello();
    }
}
```

创建配置properties

```properties
spring.application.name=feigon-consumer
server.port=40001
eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/
```

## 6. 理想的Feign风格项目结构

### 6.1. 抽取一个公共接口层

在feigon目录下创建项目feigon-client-intf

POM里仅保持最低限度依赖，不要添加过多依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>spring-cloud-project</artifactId>
        <groupId>com.icodingedu</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>
    <artifactId>feign-client-intf</artifactId>
    <name>feign-client-intf</name>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
    </dependencies>
</project>
```

建立接口层，并将实体对象放进来

```java
package com.icodingedu.springcloud.service;

import com.icodingedu.springcloud.pojo.PortInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

//这里不能再使用eureka-client的了,需要使用自己的
//如果提供给的下游应用没有使用feign就不加注解@FeignClient,@GetMapping,@PostMapping,就是一个简单的接口,让下游自己实现即可
@FeignClient("feign-client")
public interface IService {
    @GetMapping("/sayhello")
    String sayHello();

    @PostMapping("/sayhello")
    PortInfo sayHello(@RequestBody PortInfo portInfo);
}
```

pojo实体对象

```java
package com.icodingedu.springcloud.pojo;

import lombok.Data;

@Data
public class PortInfo {
    private String name;
    private String port;
}
```

### 6.2. 新建服务提供者

在feign目录中创建项目feign-client-advanced

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>spring-cloud-project</artifactId>
        <groupId>com.icodingedu</groupId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>
    <artifactId>feign-client-advanced</artifactId>
    <name>feign-client-advanced</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>com.icodingedu</groupId>
            <artifactId>feign-client-intf</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

创建application的启动类

```java
package com.icodingedu.springcloud;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class FeignClientAdvancedApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(FeignClientAdvancedApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

在Controller里实现feign-client-intf里的IService

```java
package com.icodingedu.springcloud.controller;

import com.icodingedu.springcloud.pojo.PortInfo;
import com.icodingedu.springcloud.service.IService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class FeignController implements IService {

    @Value("${server.port}")
    private String port;

    @Override
    public String sayHello() {
        return "my port is "+port;
    }

    @Override
    public PortInfo sayHello(@RequestBody PortInfo portInfo) {
        log.info("you are "+portInfo.getName());
        portInfo.setName(portInfo.getName());
        portInfo.setPort(portInfo.getPort());
        return portInfo;
    }
}
```

配置Properties

```properties
spring.application.name=feign-client
server.port=40002
eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/
```

### 6.3. 改进版的消费者

创建feign-consumer-advanced

设置POM文件，可以从feign-client-advanced里取

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>spring-cloud-project</artifactId>
        <groupId>com.icodingedu</groupId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <packaging>jar</packaging>
    <artifactId>feign-consumer-advanced</artifactId>
    <name>feign-consumer-advanced</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>com.icodingedu</groupId>
            <artifactId>feign-client-intf</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

创建启动类

```java
package com.icodingedu.springcloud;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
//这个地方要注意IService所在包路径,默认是扫当前包com.icodingedu.springcloud
//如果接口不在同一个包下就需要把包路径扫进来
//@EnableFeignClients(basePackages = "com.icodingedu.*")
@EnableFeignClients
public class FeignConsumerAdvancedApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(FeignConsumerAdvancedApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

controller实现

```java
package com.icodingedu.springcloud.controller;

import com.icodingedu.springcloud.pojo.PortInfo;
import com.icodingedu.springcloud.service.IService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class FeignController {

    @Autowired
    private IService service;

    @GetMapping("/sayhi")
    public String sayHi(){
        return service.sayHello();
    }

    @PostMapping("/sayhi")
    public PortInfo sayHello(@RequestBody PortInfo portInfo){
        return service.sayHello(portInfo);
    }
}
```

properties的实现配置

```properties
spring.application.name=feign-consumer-advanced
server.port=40003
eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/
```

## 7. Feign服务调用超时重试机制

```shell
# feign的调用超时重试机制是由Ribbon提供的
# feign-server-proivder是指你的服务名
feign-server-proivder.ribbon.OkToRetryOnAllOperations=true
feign-server-proivder.ribbon.ConnectTimeout=1000
feign-server-proivder.ribbon.ReadTimeout=2000
feign-server-proivder.ribbon.MaxAutoRetries=2
feign-server-proivder.ribbon.MaxAutoRetriesNextServer=2
```

上面的参数设置了feign服务的超时重试策略

**OkToRetryOnAllOperations**：比如POST、GET、DELETE这些HTTP METHOD哪些可以Retry，设置为true表示都可以，这个参数是为幂等性设计的，默认是GET可以重试

**ConnectTimeout**：单位ms，创建会话的连接时间

**ReadTimeout**：单位ms，服务的响应时间

**MaxAutoRetries**：当前节点重试次数，访问次数等于首次访问+这里配置的重试次数

**MaxAutoRetriesNextServer**：当前机器重试超时后Feign将连接新的机器节点访问的次数

按照上面配置的参数，最大超时时间是？

(1000+2000) x (1+2) x (1+2) = 27000ms

总结一下极值函数

```shell
MAX(Response Time)=(ConnectTimeout+ReadTimeout)*(MaxAutoRetries+1)*(MaxAutoRetriesNextServer+1)
```

## 8. 配置Feign超时重试验证

在feign-consumer-advanced里进行配置即可

```shell
# feign-client:这个是自己定义的服务名

# 每台机器最大的重试次数
feign-client.ribbon.MaxAutoRetries=2
# 可以重试的机器数量
feign-client.ribbon.MaxAutoRetriesNextServer=2
# 连接请求超时的时间限制ms
feign-client.ribbon.ConnectTimeout=1000
# 业务处理的超时时间ms
feign-client.ribbon.ReadTimeout=2000
# 默认是false,默认是在get上允许重试
# 这里是在所有HTTP Method进行重试,这里要谨慎开启,因为POST,PUT,DELETE如果涉及重试就会出现幂等问题
feign-client.ribbon.OkToRetryOnAllOperations=true
```

配置完毕后进行测试验证

在feign-client-intf里增加接口retry接口

```java
    @GetMapping("/retry")
    String retry(@RequestParam(name = "timeout") int timeout);
```

在feign-client-advanced里实现这个接口

```java
    @Override
    public String retry(@RequestParam(name="timeout") int timeout) {
        try {
            while (timeout-- > 0) {
                Thread.sleep(1000);
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
        log.info("retry is "+port);
        return port;
    }
```

在feign-consumer-advanced的controller里实现

```java
    @GetMapping("/retry")
    public String retry(@RequestParam(name = "timeout") Integer timeout){
        return service.retry(timeout);
    }
```

启动三个feign-client-advanced

进行超时验证并看控制台数据是是每个节点输出三次，重试三个机器