FIT2CLOUD CodeDeploy Jenkins Plugin
=============================

Development
----------

1. Typical maven project, if you find it too slow to resolv the dependencies, try add Aliyun Nexus mirror to you ~/.m2/settings.xml

```
    <mirror>
        <id>nexus-aliyun</id>
        <mirrorOf>*</mirrorOf>
        <name>Nexus aliyun</name>
        <url>http://maven.aliyun.com/nexus/content/groups/public</url>
    </mirror> 
```

2. Release .hpi

```
mvn clean package
```

3. Online Debug

**Note**: comment Aliyun mirror since Aliyun Nexus doesn't have all the central repos synced.

```
mvn clean hpi:run
```

License
-------

This plugin is licensed under Apache 2.0. See the LICENSE file for more information.
