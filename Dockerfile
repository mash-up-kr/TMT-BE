FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY tmt-bootstrap/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC \
  -XX:+UseContainerSupport \
  -Xms256m -Xmx384m \
  -XX:ReservedCodeCacheSize=96m \
  -XX:MaxMetaspaceSize=192m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+UseStringDeduplication \
  -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
