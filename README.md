## System Requirements

Grizzly is developed by Spring Boot 3.3 and above , it requires Java 17 or above, mongo 7.0 or above.

## Start with Mongo

To start mongo, you can use this **docker-compose.yml** file : <br>

```Shell
 version: '3'
 services:
    mongodb:
        image: mongo:7.0
        container_name:
          mongodb
        ports:
            - "27017:27017"        
        restart:
           always
 ```

Let’s start mongo by spinning up the containers using the **docker-compose** command :<br>

```Shell
    $ docker-compose up -d
 ```

## Run your projects

we’re gonna compile, test & package our Java projects using this command : <br>

```Shell
    $ mvn clean install
 ```

We should launch this command in our projects in this order <br>
1- grizzly api common <br>
2- grizzly api core<br>
3- grizzly api runtime<br>
4- grizzly api gateway<br>

Let’s now start our projects with this command :

```Shell
    $ mvn spring-boot:run -Dspring-boot.run.profiles=local
 ```

Also, you can launch your projects with docker using the command:

 ```Shell
    $ docker build -t grizzly/grizzly-core .
    $ docker run grizzly/grizzly-core
 ```

## Documentation
---
All documentation can be found on [Grizzly API](https://grizzlydoc.codeonce.fr/)
