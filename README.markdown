# poshi-dev-tools

### Configuration

1. `git clone https://github.com/kenjiheigel/poshi-dev-tools.git`

1. `git clone https://github.com/kenjiheigel/jira-cloud-client.git`

1. Create a file called `custom.properties` file and set:
```
jira.cloud.client.dir=/path/to/jira/cloud/client/dir
portal.dir=/path/to/portal/dir
```
### Intellij

1. In Intellij, _File > New > Project from Existing Sources_

1. Select _Import project from external model_ and choose _Gradle_

1. Check _Use gradle 'wrapper' task configuration_ or in Preferences > Build, Execution, Deployment > Build Tools > Gradle, select _Use Gradle from: 'wrapper' task in Gradle build script_