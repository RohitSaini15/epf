# Settings Files used in CBC Jenkins

Cloudbees core (CBC) Jenkins uses settings files to work with different modules and the shared library itself. All controllers in CBC Jenkins need these settings files to run the shared library. 

Typically new settings files need to be added when performing an upgrade or essentially a JNLP image upgrade which is the underlying image that runs the Jenkins controllers.

Settings files in this directory are labeled to how they should be added in Jenkins. For example if a settings file is named `123` then it should be added into Jenkins as a Global Maven Settings File with the ID as `123`.

## File Inventory

| File Name                            | File Type                 | Notes                                                                                                                                |
|--------------------------------------|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `2c5fdcc7-2376-4dbf-b252-a3a8e83603d9` | Global Maven settings.xml | Use file name as ID of global settings and copy file contents as they exist in file                                                  |
| `artifactory-global-settings-file`     | Global Maven settings.xml | Use file name as ID of global settings and copy file contents as they exist in file                                                  |
| `maven-auth-settings`                  | Global Maven settings.xml | Use file name as ID of global settings and copy file contents as they exist in file                                                  |
| `.pypirc`                              | Properties File           | Use file name as ID called .pypirc,   Properties Credentials:  PropertyKey = password Credentials = deployer-im-devops/****** (SAUP) |

### More Information

https://confluence.sys.cigna.com/display/DvOp/Cloudbees+Core+-+Global+Controller+Settings