# 问题记录

> 1. springboot 项目打包发布（clean compile packge），使用 java -jar xxx.jar 执行找不到主类（main）

在pom文件加上 

```xml
<properties>
    <start-class>com.scdzyc.springcloud.ConfigBusServerApplication</start-class>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>2.2.5.RELEASE</version>
            <configuration>
                <mainClass>${start-class}</mainClass>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>repackage</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```



​	