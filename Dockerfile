FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY tmt-bootstrap/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC \
  -XX:+UseContainerSupport \
  -Xms256m -Xmx384m \
  -XX:ReservedCodeCacheSize=128m \
  -XX:MaxMetaspaceSize=128m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+UseStringDeduplication \
  -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
