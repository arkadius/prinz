FROM java:8

WORKDIR /

ADD nussknacker-standalone-app-assembly-0.3.0.jar /

CMD ["java", "-Dconfig.file=/application.conf", "-jar", "nussknacker-standalone-app-assembly-0.3.0.jar"]
