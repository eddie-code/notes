# 海量数据存储与访问瓶颈解决方案:数据切分-1

## 1. 数据切分方案分析

对于数据库来讲，他永远是系统中最关键的核心环节和瓶颈

- 保护数据库：增加缓存（redis、ES）
- MySQL本身的分区表只是对磁盘进行了高效利用

水平扩展，多数据中心（各个数据中心，互相是一个备份节点）

如果数据库做到了分布式的架构？就需要有一个中间件，对数据进行二次加工合并

## 2. 垂直切分、水平切分方案分析

### 2.1. 垂直切分

通过业务分解将一个数据库中多个表，拆分成多台数据库

解耦：拆分

比如说：后端进行数据报表聚合的时候

各个功能模块之间的交互越统一、越少越好，这样耦合度就降低了

垂直切分后往往还会存在跨库的join访问现象，绝对不允许（业务A-数据库A直接访问业务B的数据库）需要通过接口访问业务B的数据库

优先：

- 业务拆分后规则清晰，业务明确
- 系统之间容易扩展和整合
- 数据维护简单

缺点：

- 部分业务无法join，只能通过接口调用实现，提升了系统的复制度
- 跨库的事务难以处理
- 垂直切分后，某些业务表依旧很大，仍然存在单体性能瓶颈

### 2.2. 水平切分

水平切分要比垂直切分复杂多了，水平拆分时一定要先制定拆分规则

典型的拆分规则：

- 通过id求模
- 按照时间进行拆分
- 其他字段的枚举或范围

优点：

- 解决了单表大数据，高并发的性能问题
- 如果拆分规则封装好，对于应用端几乎是透明的，开发人员无需关心拆分细节
- 提高了系统的稳定性和负载能力

缺点：

- 拆分规则很难抽象
- 分片的事务一致性难以解决
- 二次扩展时，数据迁移维护难度大

## 3. 整体分片方案的总结

无论是垂直还是水平分片都有共同的缺点

- 分布式事务问题
- 跨库的join问题
- 多数据源的管理

针对这些问题，思路有两种

1、客户端模式：在业务应用内，自己管理数据源，直接访问你需要的数据，自己在业务模块内做数据整合

2、中间代理模式：中间代理统一管理所有数据源，数据库层对开发人员完全透明，开发人员无需关注拆分细节，正常访问数据表即可

基于这两种模式，目前成熟的第三方软件中间件

- MyCat：中间代理模式
- sharding-jdbc：客户端模式

## 4. 再看读写分离

- 80%都是查询，slow_sql，20%是数据更新操作
- 对于数据库来讲，其实是数据备份在业务应用上的体现
- MySQL（同步、半同步、异步）在备份库做查询，就将这80%的查询压力引入到备份库上了
- 数据库要实现热切话需要双主结构，两个Master互为主从

## 5. MyCat整体应用分析

### 5.1. 什么是MyCat

MyCat是基于阿里开源产品Cobar用Java研发的数据库中间件，它其实就是一个开源的分布式数据库系统，它直接就可以使用MySQL的命令行访问，不是单纯的MySQL代理，它后端也支持oracle，MSSQL，DB2，无论后端接入什么数据库引擎，MyCat都是一个支持SQL的数据代理

### 5.2. 应用场景

- 支持单纯的读写分离配置，可以做到热切换
- 分库分表，对于超过1000w的数据表价值就体现出来了，最大支持1000亿数据
- 支持多租户

### 5.3. MyCat中的基本概念

- 逻辑库（Schema）

  比如user数据库，后面对接了两个MySQL，user_185，user_186

- 逻辑表（table）

  全局表？所有分片上都有这个表的数据，便于进行数据关联，不同进行跨库操作

- 分片节点（dataNode）

  具体要分配数据的节点

- 节点主机（dataHost）

  具体的物理数据库

- 分配规则（rule）

  以什么方式和规则来进行分片

- 全局序列号（sequence）

## 6. MyCat安装使用

实验环境：

两台centos 7.x主机，分别安装MySQL数据库v5.7

一台centos 7.x主机，安装MyCat服务

```shell
# 0.MyCat的主机要安装jdk
# 1.下载MyCat安装包
wget http://dl.mycat.io/1.6.7.3/20190828135747/Mycat-server-1.6.7.3-release-20190828135747-linux.tar.gz
tar -zxvf Mycat-server-1.6.7.3-release-20190828135747-linux.tar.gz
# 2.进入conf进行配置
vi server.xml
```

先配置用户

```xml
        <user name="root" defaultAccount="true">
                <property name="password">123456</property>
                <property name="schemas">user</property>
        </user>
```

设置dataHost连接数据库

vi schema.xml

```xml
        <dataHost name="DB185" maxCon="1000" minCon="10" balance="0"
                          writeType="0" dbType="mysql" dbDriver="native" switchType="1"  slaveThreshold="100">
                <heartbeat>select user()</heartbeat>
                <!-- can have multi write hosts -->
                <writeHost host="M1" url="192.168.0.185:3306" user="gavin"
                                   password="123456">
                        <!-- can have multi read hosts -->
                        <!-- <readHost host="hostS2" url="192.168.1.200:3306" user="root" password="xxx" /> -->
                </writeHost>
                <!-- <writeHost host="hostS1" url="localhost:3316" user="root"
                                   password="123456" /> -->
                <!-- <writeHost host="hostM2" url="localhost:3316" user="root" password="123456"/> -->
        </dataHost>
```

我们有两个dataHost，所以还需要再copy一个节点

```xml
        <dataHost name="DB186" maxCon="1000" minCon="10" balance="0"
                          writeType="0" dbType="mysql" dbDriver="native" switchType="1"  slaveThreshold="100">
                <heartbeat>select user()</heartbeat>
                <!-- can have multi write hosts -->
                <writeHost host="M2" url="192.168.0.186:3306" user="gavin"
                                   password="123456">
                        <!-- can have multi read hosts -->
                        <!-- <readHost host="hostS2" url="192.168.1.200:3306" user="root" password="xxx" /> -->
                </writeHost>
                <!-- <writeHost host="hostS1" url="localhost:3316" user="root"
                                   password="123456" /> -->
                <!-- <writeHost host="hostM2" url="localhost:3316" user="root" password="123456"/> -->
        </dataHost>
```

设置dataNode

database要和数据库里的名字一致

```xml
<dataNode name="dn185" dataHost="DB185" database="user_185" />
<dataNode name="dn186" dataHost="DB186" database="user_186" />
```

设置schema来进行数据库表的分片，表名要和数据库里的名字一致

```xml
<schema name="TESTDB" checkSQLschema="true" sqlMaxLimit="100">
  <!-- auto sharding by id (long) -->
  <table name="user_info" dataNode="dn185,dn186" rule="auto-sharding-long" />
</schema>
```

启动mycat

```shell
./bin/mycat console
# jvm 1    | Caused by: io.mycat.config.util.ConfigException: Illegal table conf : table [ USER_INFO ] rule function [ rang-long ] partition size : 3 > table datanode size : 2, please make sure table datanode size = function partition size
# 分片规则配置查找
rule="auto-sharding-long"
vi rule.xml
# 这个columns就是数据库分片的列
        <tableRule name="auto-sharding-long">
                <rule>
                        <columns>id</columns>
                        <algorithm>rang-long</algorithm>
                </rule>
        </tableRule>
# 通过rang-long分片方法找分片规则
        <function name="rang-long"
                class="io.mycat.route.function.AutoPartitionByLong">
                <property name="mapFile">autopartition-long.txt</property>
        </function>
vi autopartition-long.txt
# K=1000,M=10000.
0-500M=0
500M-1000M=1
#1000M-1500M=2
# 如果大于这个返回写不进去会报错
```

再次启动

```shell
# jvm 1    | Caused by: io.mycat.config.util.ConfigException: SelfCheck###  schema user refered by user root is not exist!
vi schema.xml
<schema name="user" checkSQLschema="true" sqlMaxLimit="100">
# Caused by: io.mycat.config.util.ConfigException: SelfCheck###  schema TESTDB refered by user user is not exist!
vi server.xml
        <user name="user">
                <property name="password">user</property>
                <property name="schemas">user</property>
                <property name="readOnly">true</property>
        </user>
```

再次启动就ok了

## 7、MyCat分片核心配置

### 7.1. server.xml的核心配置

- 配置了MyCat的用户名，密码，权限，schema
- 相当于给MySQL创建用户
- 如果有多个schema就以csv的形式写入

```xml
        <user name="root" defaultAccount="true">
                <property name="password">123456</property>
                <property name="schemas">user,product,order</property>
        </user>
```

多少个schema就要在schema.xml里配置多少个schema标签

```shell
# 可以直接用mysql客户端进行连接
mysql -uroot -p -P8066 -h192.168.0.184
```

### 7.2. schema.xml的核心配置

- 配置dataNode（包括写host和读host）
- 配置dataHost
- 配置schema进行表的分片规则指定

```shell
<dataHost name="DB186" maxCon="1000" minCon="10" balance="0" writeType="0" dbType="mysql" dbDriver="native" switchType="1"  slaveThreshold="100">
# name:随意命名，只要和dataNode对应即可
# maxCon:最大连接数
# minCon:最小连接数
# balance="0":不开启读写分离，所有的读都发送到writeHost上
# balance="1":开启读写分离，全部的readHost和stand by writeHost参与读操作，如果我们是双主双从的模式（M1->S1,M2->S2,并且M1和M2互为主备，实际在写入的时候M2时不写数据的，只是做备份）M2,S1,S2都会负责均衡读操作
# balance="2":开启读写分离，所有的读操作都随机落在writeHost、readHost上
# balance="3":开启读写分离，所有的读操作都随机落在readHost上
# writeType="0":所有写操作会发送到配置的第一个writeHost上，如果第一个挂了，第二个还活着，就直接写入第二个writeHost，第一个重启后虽然配置在上面但实际顺序会放在下面
# switchType="-1":表示不自动切换writeHost
# switchType="1":自动切换
# switchType="2":根据MySQL的主从同步状态决定是否切换

<dataNode name="dn185" dataHost="DB185" database="user_185" />
# name:随意命名，只要和schema里的table对应即可
# database:数据库里实际的名字

<schema name="user" checkSQLschema="true" sqlMaxLimit="100">
</schema>
# checkSQLschema="true":会将select * from user.user_info更换为select * from user_info
# sqlMaxLimit:为了避免让自己查询负担过重，会对查询结果进行自动加limit，只对分片表有效，如果你SQL自己设置了limit也会失效
<table name="user_info" dataNode="dn185,dn186" rule="auto-sharding-long" />
# 每个表都要写一个table的
# name:这个name就是表名，和dataHost对应的数据库里的表名要一模一样
# dataNode:表明要分片数据节点
# rule:就是分片规则对应rule.xml
```

### 7.3. 生产环境如何动态变更配置

生产环境肯定不能 ./mycat console

```shell
./bin/mycat start
```

mycat针对系统线上运行有一个管理端口，专门做线上配置更新的

```shell
reload @@config;
# 如果更新了数据源必须用config_all
reload @@config_all;
```

