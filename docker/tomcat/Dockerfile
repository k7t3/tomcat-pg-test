FROM gradle:9.5.1-jdk21 AS builder

WORKDIR /workspace
COPY . .
RUN gradle :app:war --no-daemon

FROM tomcat:9.0-jdk21-temurin

COPY --from=builder /workspace/app/build/libs/app-fat.war /usr/local/tomcat/webapps/ROOT.war
COPY docker/tomcat/run-tomcat.sh /usr/local/bin/run-tomcat.sh

RUN chmod +x /usr/local/bin/run-tomcat.sh

ENTRYPOINT ["/usr/local/bin/run-tomcat.sh"]
