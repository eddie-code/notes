[TOC]

## [模块]

- [微服务架构设计开篇](./微服务架构设计.md)
- [SpringCloud服务治理](#SpringCloud服务治理)
- [SpringCloudf负载均衡与远程调用](./SpringCloudf负载均衡与远程调用.md)



## 目录

- [1. 什么是服务治理](#1-什么是服务治理)
- [2. 服务治理组件选型比较](#2-服务治理组件选型比较)
- [3. 构建Eureka-Server模块](#3-构建Eureka-Server模块)
- [4. 构建Eureka-Client模块](#4-构建Eureka-Client模块)
- [5. 构建Eureka-Consumer模型](#5-构建Eureka-Consumer模型)
- [6. Eureka心跳检测与服务剔除](#6-Eureka心跳检测与服务剔除)
- [7. Eureka服务续约机制](#7-Eureka服务续约机制)
- [8. Eureka服务自保机制](#8-Eureka服务自保机制)
- [9. Eureka启用心跳和健康检查验证](#9-Eureka启用心跳和健康检查验证)
- [10. 服务注册中心的高可用架构](#10-服务注册中心的高可用架构)



# SpringCloud服务治理

## 1. 什么是服务治理

- 高可用性：除了服务本身要可用，服务治理的框架也要高可用
- 分布式调用：异地灾备，服务治理框架还需要在复杂网络环境下做到精确服务的定位
- 生命周期的管理：服务治理的框架还要管理好服务的上下线
- 健康度检查：对于服务的是否能够正常工作要能定期检查

**服务治理的解决方案：**

- 服务注册：服务提供方自报家门
- 服务发现：服务消费者需要拉取服务注册列表
- 心跳检测、服务续约、服务剔除：由服务注册中心和服务提供方配合实现
- 服务下线：服务提供方发起主动下线

## 2. 服务治理组件选型比较

Eureka：Netflix公司

Consul：Spring开源组织直接贡献

Nacos：阿里服务治理中间件

|          | Eureka         | Consul                   | Nacos        |
| -------- | -------------- | ------------------------ | ------------ |
| 一致性   | 弱一致性（AP） | 弱一致性（AP）           | AP/CP        |
| 性能     | 快             | 慢（RAFT协议Leader选举） | 快           |
| 网络协议 | HTTP           | HTTP&DNS                 | HTTP/DNS/UDP |
| 应用广度 | 主流           | 小众一些                 | 发展中       |

## 3. 构建Eureka-Server模块

spring-cloud-learn父工程的POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.icodingedu.springcloud</groupId>
    <artifactId>spring-cloud-learn</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>Hoxton.SR3</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>2.2.5.RELEASE</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.12</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.6.0</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**创建子项目eureka-server的POM文件**

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
    <artifactId>eureka-server</artifactId>
    <name>eureka-server</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
    </dependencies>
</project>
```

application启动类

```java
package com.icodingedu.springcloud;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
//注册中心的服务
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(EurekaServerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

properties的设置

```properties
# 切记一定要加
spring.application.name=eureka-server

server.port=20001

eureka.instance.hostname=localhost
# 是否发起服务器注册，服务端关闭
eureka.client.register-with-eureka=false
# 是否拉取服务注册表，服务端是生成端不用拉取
eureka.client.fetch-registry=false
```

## 4. 构建Eureka-Client模块

创建Eureka-Client的项目模块

引入POM依赖

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
    <artifactId>eureka-client</artifactId>
    <name>eureka-client</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

application启动类

```java
package com.icodingedu.springcloud;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class EurekaClientApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(EurekaClientApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

controller服务提供内容

```java
package com.icodingedu.springcloud.controller;

import com.icodingedu.springcloud.pojo.PortInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@Slf4j
public class EurekaClientController {

    @Value("${server.port}")
    private String port;

    @GetMapping("/sayhello")
    public String sayHello(){
        return "my port is "+port;
    }

    @PostMapping("/sayhello")
    public PortInfo sayPortInfo(@RequestBody PortInfo portInfo){
        log.info("you are "+portInfo.getName()+" is "+portInfo.getPort() );
        return portInfo;
    }
}
```

pojo

```java
package com.icodingedu.springcloud.pojo;

import lombok.Data;

@Data
public class PortInfo {
    private String name;
    private String port;
}
```

application配置

```properties
spring.application.name=eureka-client

server.port=20002

eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/
```

## 5. 构建Eureka-Consumer模型

创建一个Eureka-Consumer的模块

引入POM依赖

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
    <artifactId>eureka-consumer</artifactId>
    <name>eureka-consumer</name>

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
    </dependencies>
</project>
```

启动类

```java
package com.icodingedu.springcloud;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableDiscoveryClient
public class EurekaConsumerApplication {

    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(EurekaConsumerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(args);
    }
}
```

controller实现调用

```java
package com.icodingedu.springcloud.controller;

import com.icodingedu.springcloud.pojo.PortInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@Slf4j
public class ConsumerController {
    //Consumenr+RestTemplate
    @Autowired
    private RestTemplate restTemplate;
    //Eureka+Ribbon
    @Autowired
    private LoadBalancerClient client;

    @GetMapping("/hello")
    public String hello(){
        ServiceInstance instance = client.choose("eureka-client");
        if(instance==null){
            return "No available instance";
        }
        String target = String.format("http://%s:%s/sayhello",instance.getHost(),instance.getPort());
        log.info("url is {}",target);
        return restTemplate.getForObject(target,String.class);
    }

    @PostMapping("/hello")
    public PortInfo portInfo(){
        ServiceInstance instance = client.choose("eureka-client");
        if(instance==null){
            return null;
        }
        String target = String.format("http://%s:%s/sayhello",instance.getHost(),instance.getPort());
        log.info("url is {}",target);
        PortInfo portInfo = new PortInfo();
        portInfo.setName("gavin");
        portInfo.setPort("8888");
        return restTemplate.postForObject(target,portInfo,PortInfo.class);
    }
}
```

pojo

```java
package com.icodingedu.springcloud.pojo;

import lombok.Data;

@Data
public class PortInfo {
    private String name;
    private String port;
}
```

application的配置文件

```properties
spring.application.name=eureka-consumer

server.port=20003

eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/
```

启动进行consumer测试：server、client、consumer

## 6. Eureka心跳检测与服务剔除

### 6.1. 心跳检测的机制

- 客户端发起：心跳是由服务节点根据配置时间主动发起
- 同步状态：还要告知注册中心自己的状态（UP、DOWN、STARTING、OUT_OF_SERVICE，UNKNOW）
- 服务剔除：服务中心剔除，主动剔除长时间没有发心跳的节点
- 服务续约

```shell
# 两个核心的Eureka-Client配置
# 每隔10秒向Eureka-Server发送一次心跳包
eureka.instance.lease-renewal-interval-in-seconds=10
# 如果Eureka-Server在这里配置的20秒没有心跳接收，就代表这个节点挂了
eureka.instance.lease-expiration-duration-in-seconds=20
# 这两个参数是配置在服务提供节点
```

### 6.2. 服务剔除

- 1、启动定时任务来轮询节点是否正常，默认60秒触发一次剔除任务，可以修改间隔

  ```shell
  # 配置在Eureka-Server上
  eureka.server.eviction-interval-timer-in-ms=30000
  ```

- 2、调用evict方法来进行服务剔除

  如果**自保**开启，注册中心就会中断服务剔除操作

- 3、遍历过期服务，如和判断服务是否过期，以下任意一点满足即可

  - 已被标记为过期
  - 最后一次心跳时间+服务端配置的心跳间隔<当前时间

- 4、计算可剔除的服务总数，所有的服务是否能被全部剔除？当然不能！设定了一个稳定系数（默认0.85），这个稳定系数就是只在注册的服务总数里只能剔除：总数*85%个，比如当前100个服务，99个已经over了，只能剔除85个over，剩下的14个over的不会剔除

  ```shell
  eureka.server.renewal-percent-threshold=0.85
  ```

- 5、乱序剔除服务：随机到哪个过期服务就把他踢下线

## 7. Eureka服务续约机制

### 7.1. 续约和心跳的关系

**同步时间：**心跳、续约、剔除

我们先来说说续约和心跳的关系，服务续约分为两步

- **第一步** 是将服务节点的状态同步到注册中心，意思是通知注册中心我还可以继续工作，这一步需要借助客户端的心跳功能来主动发送。
- **第二步** 当心跳包到达注册中心的时候，那就要看注册中心有没有心动的感觉了，他有一套判别机制，来判定当前的续约心跳是否合理。并根据判断结果修改当前instance在注册中心记录的同步时间。

接下来，服务剔除并不会和心跳以及续约直接打交道，而是通过查验服务节点在注册中心记录的同步时间，来决定是否剔除这个节点。

所以说心跳，续约和剔除是一套相互协同，共同作用的一套机制

### 7.2. 发送Renew续约请求

接下来，就是服务节点向注册中心发送续约请求的时候了

1. **服务续约请求** 在前面的章节里我们讲到过，客户端有一个DiscoverClient类，它是所有操作的门面入口。所以续约服务就从这个类的renew方法开始

2. **发送心跳** 

   服务续约借助心跳来实现，因此发给注册中心的参数和上一小节的心跳部分写到的一样，两个重要参数分别是服务的状态（UP）和lastDirtyTimeStamp

   - 如果续约成功，注册中心则会返回200的HTTP code
   - 如果续约不成功，注册中心返回404，这里的404并不是说没有找到注册中心的地址，而是注册中心认为当前服务节点并不存在。这个时候再怎么续约也不灵验了，客户端需要触发一次重新注册操作。

3. 在重新注册之前，客户端会做下面两个小操作，然后再主动调用服务册流程。

   - **设置lastDirtyTimeStamp** 由于重新注册意味着服务节点和注册中心的信息不同步，因此需要将当前系统时间更新到“lastDirtyTimeStamp”
   - 标记自己为脏节点

4. 当注册成功的时候，清除脏节点标记，但是lastDirtyTimeStamp不会清除，因为这个属性将会在后面的服务续约中作为参数发给注册中心，以便服务中心判断节点的同步状态。

### 7.3. 续约校验

注册中心开放了一系列的HTTP接口，来接受四面八方的各种请求，他们都放在com.netflix.eureka.resources这个包下。只要客户端路径找对了，注册中心什么都能帮你办到

1. **接受请求** InstanceResource下的renewLease方法接到了服务节点的续约请求。

2. **尝试续约**

   服务节点发起续约请求。注册中心进行校验，从现在算到下一次心跳间隔时间，如果你没来renew，就当你已经死掉了。注册中心此时会做几样简单的例行检查，如果没有通过，则返回404，不接受反驳

   - 你以前来注册过吗？没有？续约失败！带齐资料工作日前来办理注册！
   - 你是Unknown状态？回去回去，重新注册！

3. **脏数据校验** 如果续约校验没问题，接下来就要进行脏数据检查。到了服务续约最难的地方了，脏数据校验逻辑之复杂，如同这皇冠上的明珠。往细了说，就是当客户端发来的lastDirtyTimeStamp，晚于注册中心保存的lastDirtyTimeStamp时（每个节点在中心都有一个脏数据时间），说明在从服务节点上次注册到这次续约之间，发生了注册中心不知道的事儿（数据不同步）。这可不行，这搞得我注册中心的工作不好有序开展，回去重新注册吧。续约不通过，返回404。

## 8. Eureka服务自保机制

- 以下两个Eureka的服务机制是不能共存的，注册中心在统一时刻只能实行以下一种方法
  - 服务剔除
  - 服务自保

**服务自保**：把当前系统所有的节点保留，一个都不能少，即便服务节点挂了也不剔除

**服务自保的触发机关**

- 服务自保由两个机关来触发
- 自保机制在服务启动的15分钟内自动触发检查，如果成功续约的节点低于限定值（默认85%）就开启自保服务，自保服务开启就中断服务剔除操作

**手动关闭服务自保**

配置强行关闭服务自保，即便上面的自动开关被触发，也不能开启自保功能了

```shell
eureka.server.enable-self-preservation=false
```

## 9. Eureka启用心跳和健康检查验证

在eureka-client里配置

```properties
# 测试设置,生产环境第一个值要比第二个小
eureka.instance.lease-renewal-interval-in-seconds=30
eureka.instance.lease-expiration-duration-in-seconds=5
```

在eureka-server配置

```shell
# 测试设置
eureka.server.enable-self-preservation=false
eureka.server.eviction-interval-timer-in-ms=10000
```

## 10. 服务注册中心的高可用架构

微服务架构中每一个较大业务领域都有自己的注册中心

eureka-server如果挂了，consumer依然可以使用server挂掉前的服务列表进行访问，但新的服务无法进行治理了

如何确保服务中心的高可用呢

如果要实现HA，就是通过镜像节点，我们再copy一个eureka-server

```shell
# eureka-server配置互相注册的节点即可
eureka.client.service-url.defaultZone=http://eurekaserver2:20011/eureka/

# eureka-client和eureka-consumer调用可以用csv确保调用的HA
eureka.client.serviceUrl.defaultZone=http://localhost:20001/eureka/,http://localhost:20002/eureka/
```



