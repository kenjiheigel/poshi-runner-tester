# poshi-dev-tools

### Configuration
1. `git clone https://github.com/kenjiheigel/poshi-dev-tools.git`
2. Create a file called `custom.properties` file and set: 
```
portal.dir=/path/to/portal/dir
```
### Intellij
1. In Intellij, _File > New > Project from Existing Sources_ 
2. Select _Import project from external model_ and choose _Gradle_
3. Check _Use gradle ‘wrapper’ task configuration_ or in Preferences > Build, Execution, Deployment > Build Tools > Gradle, select _Use Gradle from: 'wrapper' task in Gradle build script_

### Temporary Workaround 
1. From the `liferay-portal` directory referenced in `portal.dir` , run: ```ant compile && ant install-portal-snapshots```
2. Navigate to `modules/test/data-guard-connector/build.gradle` 
3. Replace:
```
	compileOnly group: "com.liferay.portal", name: "com.liferay.portal.test", version: "default"
```
with (use the path to your `portal.dir`): 
```    
	compileOnly files("/opt/dev/projects/github/liferay-portal/.m2/com/liferay/portal/com.liferay.portal.test/10.0.0-SNAPSHOT/com.liferay.portal.test-10.0.0-SNAPSHOT.jar")
```